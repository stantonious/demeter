package com.example.demeterclient;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Looper;
import android.view.View;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.os.Handler;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.widget.Toolbar;
import android.widget.ImageView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.FileProvider;
import androidx.exifinterface.media.ExifInterface;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.TimeUnit;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.HttpUrl;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.UUID;

public class MainActivity extends AppCompatActivity implements SuggestFragment.OnSuggestionSelectedListener {

    private static final String TAG = "MainActivity";
    private static final long SCAN_PERIOD = 10000;

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner bluetoothLeScanner;
    private boolean scanning;
    private Handler handler;
    private BluetoothGatt bluetoothGatt;
    private Handler bleHandler = new Handler(Looper.getMainLooper());

    private SwipeRefreshLayout swipeRefreshLayout;

    public List<byte[]> imageList = new ArrayList<>();
    public byte[] originalImage;

    private ByteArrayOutputStream augmentedImageStream = new ByteArrayOutputStream();
    private int imageReadOffset = 0;

    private static final int REQUEST_IMAGE_CAPTURE = 2;
    private static final int REQUEST_CAMERA_PERMISSION = 3;
    private static final int REQUEST_PREVIEW_IMAGE = 4;
    private String currentPhotoPath;
    private static final String KEY_PHOTO_PATH = "com.example.demeterclient.photo_path";

    public ArrayList<Float> nHistory = new ArrayList<>();
    public ArrayList<Float> pHistory = new ArrayList<>();
    public ArrayList<Float> kHistory = new ArrayList<>();
    public ArrayList<Float> phHistory = new ArrayList<>();
    public ArrayList<Float> humidityHistory = new ArrayList<>();
    public ArrayList<Float> sunHistory = new ArrayList<>();
    public ArrayList<Float> moistureHistory = new ArrayList<>();
    public ArrayList<Float> lightHistory = new ArrayList<>();

    public enum BleConnectionStatus {
        DISCONNECTED,
        SCANNING,
        CONNECTING,
        CONNECTED,
        ERROR
    }

    private BleConnectionStatus currentStatus = BleConnectionStatus.DISCONNECTED;
    private ImageView ledIndicator;

    private StringBuilder suggestionBuilder = new StringBuilder();
    private int llmOffset = 1;

    private Queue<Runnable> writeQueue = new LinkedList<>();
    private boolean isWriting = false;

    private int numSuggestions = 1;
    private int plantType = 0;
    private int augmentSize = 20;
    private ArrayList<Integer> aoiList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            currentPhotoPath = savedInstanceState.getString(KEY_PHOTO_PATH);
        }
        setContentView(R.layout.activity_main);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        ledIndicator = findViewById(R.id.led_indicator);

        BottomNavigationView navView = findViewById(R.id.bottom_nav_view);
        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment);
        NavController navController = navHostFragment.getNavController();
        NavigationUI.setupWithNavController(navView, navController);

        swipeRefreshLayout = findViewById(R.id.swipe_refresh_layout);
        swipeRefreshLayout.setOnRefreshListener(() -> {
            if (bluetoothGatt != null) {
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    return;
                }
                bluetoothGatt.disconnect();
                bluetoothGatt.close();
                bluetoothGatt = null;
            }
            scanLeDevice(true);
            swipeRefreshLayout.setRefreshing(false);
        });

        handler = new Handler();

        final BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();

        startBleScan();

    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1) {
            boolean allGranted = true;
            for (int grantResult : grantResults) {
                if (grantResult != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }

            if (allGranted) {
                startBleScan();
            } else {
                Toast.makeText(this, "All permissions are required to scan for devices.", Toast.LENGTH_LONG).show();
            }
        } else if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                dispatchTakePictureIntent();
            } else {
                Toast.makeText(this, "Camera permission denied.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK) {
            return;
        }

        switch (requestCode) {
            case REQUEST_IMAGE_CAPTURE:
                if (currentPhotoPath != null) {
                    Intent intent = new Intent(this, PreviewActivity.class);
                    intent.putExtra("image_uri", Uri.fromFile(new File(currentPhotoPath)).toString());
                    intent.putExtra("augment_size", augmentSize);
                    startActivityForResult(intent, REQUEST_PREVIEW_IMAGE);
                }
                break;
            case REQUEST_PREVIEW_IMAGE:
                if (data != null) {
                    String imageUriString = data.getStringExtra("image_uri");
                    this.aoiList = data.getIntegerArrayListExtra("aoi_list");
                    if (this.aoiList != null && !this.aoiList.isEmpty()) {
                        handleImageStatusChange(2); // Enable Get Augmented Image button
                    }
                }
                break;
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (currentPhotoPath != null) {
            outState.putString(KEY_PHOTO_PATH, currentPhotoPath);
        }
    }

    private final ActivityResultLauncher<Intent> takePictureLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Intent data = result.getData();
                    String imageUriString = data.getStringExtra("image_uri");

                    if (data.hasExtra("aoi_list")) {
                        this.aoiList = data.getIntegerArrayListExtra("aoi_list");
                    }

                    if (imageUriString != null) {
                        Uri imageUri = Uri.parse(imageUriString);
                        Log.d(TAG, "Final image URI received: " + imageUri.toString());
                    }
                }
            }
    );
    ;
    public void dispatchTakePictureIntent() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            File photoFile = null;
            try {
                photoFile = createImageFile();
            } catch (IOException ex) {
                Log.e(TAG, "Error creating image file", ex);
            }
            if (photoFile != null) {
                Uri photoURI = FileProvider.getUriForFile(this,
                        "com.example.demeterclient.fileprovider",
                        photoFile);
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
                startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);
            }
        }
    }

    private File createImageFile() throws IOException {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        File image = File.createTempFile(imageFileName, ".jpg", storageDir);
        currentPhotoPath = image.getAbsolutePath();
        return image;
    }

    private void startBleScan() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this,
                    new String[]{
                            Manifest.permission.BLUETOOTH_SCAN,
                            Manifest.permission.BLUETOOTH_CONNECT,
                            Manifest.permission.ACCESS_FINE_LOCATION
                    },
                    1);
            return;
        }

        if (bluetoothLeScanner == null) {
            bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
        }
        scanLeDevice(true);
    }

    private void scanLeDevice(final boolean enable) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        if (enable) {
            updateLedIndicator(BleConnectionStatus.SCANNING);
            handler.postDelayed(() -> {
                if (scanning) {
                    scanning = false;
                    bluetoothLeScanner.stopScan(leScanCallback);
                    updateLedIndicator(BleConnectionStatus.DISCONNECTED);
                }
            }, SCAN_PERIOD);

            scanning = true;
            ScanFilter filter = new ScanFilter.Builder().setServiceUuid(new android.os.ParcelUuid(GattAttributes.DEMETER_SERVICE_UUID)).build();
            List<ScanFilter> filters = Collections.singletonList(filter);
            ScanSettings settings = new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build();
            bluetoothLeScanner.startScan(filters, settings, leScanCallback);
        } else {
            scanning = false;
            bluetoothLeScanner.stopScan(leScanCallback);
        }
    }

    private final ScanCallback leScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);
            BluetoothDevice device = result.getDevice();
            connectToDevice(device);
            if (scanning) {
                scanLeDevice(false);
            }
        }
    };

    private void connectToDevice(BluetoothDevice device) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        updateLedIndicator(BleConnectionStatus.CONNECTING);
        bluetoothGatt = device.connectGatt(this, false, gattCallback);
    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED) {
                updateLedIndicator(BleConnectionStatus.CONNECTED);
                bleHandler.postDelayed(() -> {
                    if (bluetoothGatt != null) {
                        if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                            return;
                        }
                        bluetoothGatt.discoverServices();
                    }
                }, 600);
            } else {
                updateLedIndicator(BleConnectionStatus.DISCONNECTED);
            }
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            super.onDescriptorWrite(gatt, descriptor, status);
            currentSubscriptionIndex++;
            if (currentSubscriptionIndex < characteristicsToSubscribe.size()) {
                subscribeNextCharacteristic(gatt);
            } else {
                // All subscriptions are complete, now it's safe to write initial settings.
                Log.d(TAG, "All characteristics subscribed. Writing initial settings.");
                setNumSuggestions(numSuggestions);
                setPlantType(plantType);
                setAugmentSize(augmentSize);
            }
        }
        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (characteristic.getUuid().equals(GattAttributes.UUID_SUGGEST)) {
                    readLlmChunk();
                }
            } else {
                writeQueue.clear();
            }
            isWriting = false;
            processWriteQueue();
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            if (status != BluetoothGatt.GATT_SUCCESS) return;

            if (characteristic.getUuid().equals(GattAttributes.UUID_LLM)) {
                handleLlmRead(characteristic.getValue());
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                subscribeToCharacteristics(gatt);
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, android.bluetooth.BluetoothGattCharacteristic characteristic) {
            if (characteristic.getUuid().equals(GattAttributes.UUID_LLM_STATUS)) {
                handleLlmStatusChange(characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0));
            } else if (characteristic.getUuid().equals(GattAttributes.UUID_IMAGE_STATUS)) {
                handleImageStatusChange(characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0));
            } else {
                updatePlotData(characteristic);
            }
        }
    };

    public void requestSuggestion() {
        if (bluetoothGatt == null) {
            // Offline mode
            suggestionBuilder.setLength(0);
            suggestionBuilder.append("general house plant");
            runOnUiThread(() -> {
                SuggestFragment fragment = getSuggestFragment();
                if (fragment != null) {
                    fragment.setSuggestionText("Suggestion: general house plant");
                    fragment.enableTakePictureButton(true);
                    fragment.enableGetAugmentedImageButton(true);
                }
                Toast.makeText(MainActivity.this, "Using offline suggestion.", Toast.LENGTH_SHORT).show();
            });
            return;
        }

        // Online mode
        requestPlantSuggestionFromApi();
    }

    private void requestPlantSuggestionFromApi() {
        runOnUiThread(() -> {
            SuggestFragment fragment = getSuggestFragment();
            if (fragment != null) {
                fragment.setSuggestionText("Suggestion: Requesting from server...");
            }
        });

        OkHttpClient client = new OkHttpClient();

        // Get the latest sensor data or use defaults
        float n_mgkg = nHistory.isEmpty() ? 0 : nHistory.get(nHistory.size() - 1);
        float p_mgkg = pHistory.isEmpty() ? 0 : pHistory.get(pHistory.size() - 1);
        float k_mgkg = kHistory.isEmpty() ? 0 : kHistory.get(kHistory.size() - 1);
        float ph = phHistory.isEmpty() ? 7.0f : phHistory.get(phHistory.size() - 1);
        float moisture = moistureHistory.isEmpty() ? 50 : moistureHistory.get(moistureHistory.size() - 1);
        float sun_intensity = sunHistory.isEmpty() ? 50000 : sunHistory.get(sunHistory.size() - 1);
        String[] plantTypes = getResources().getStringArray(R.array.plant_types);
        String plantTypeStr = (plantType >= 0 && plantType < plantTypes.length) ? plantTypes[plantType] : "tbd";


        HttpUrl.Builder urlBuilder = new HttpUrl.Builder()
                .scheme("https")
                .host("demeter-dot-heph2-338519.uc.r.appspot.com")
                .addPathSegment("demeter")
                .addPathSegment("plant")
                .addPathSegment("suggest")
                .addQueryParameter("n_mgkg", String.valueOf(n_mgkg))
                .addQueryParameter("p_mgkg", String.valueOf(p_mgkg))
                .addQueryParameter("k_mgkg", String.valueOf(k_mgkg))
                .addQueryParameter("ph", String.valueOf(ph))
                .addQueryParameter("moisture", String.valueOf(moisture))
                .addQueryParameter("sun_intensity", String.valueOf(sun_intensity))
                .addQueryParameter("lat", "39.5186") // Default lat
                .addQueryParameter("lon", "-104.7614") // Default lon
                .addQueryParameter("plant_type", plantTypeStr)
                .addQueryParameter("max_plants", String.valueOf(numSuggestions));

        Request request = new Request.Builder()
                .url(urlBuilder.build())
                .get()
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Plant suggestion API call failed", e);
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, "Failed to get suggestion: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    SuggestFragment fragment = getSuggestFragment();
                    if (fragment != null) {
                        fragment.setSuggestionText("Suggestion: Error");
                    }
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    String errorBody = response.body().string();
                    Log.e(TAG, "Plant suggestion API error: " + errorBody);
                    runOnUiThread(() -> {
                        Toast.makeText(MainActivity.this, "Failed to get suggestion: " + response.message(), Toast.LENGTH_LONG).show();
                        SuggestFragment fragment = getSuggestFragment();
                        if (fragment != null) {
                            fragment.setSuggestionText("Suggestion: Server Error");
                        }
                    });
                    return;
                }

                try {
                    String responseBody = response.body().string();
                    JSONObject json = new JSONObject(responseBody);
                    if (json.getBoolean("success")) {
                        org.json.JSONArray suggestionsArray = json.getJSONArray("suggestions");
                        List<String> suggestionsList = new ArrayList<>();
                        for (int i = 0; i < suggestionsArray.length(); i++) {
                            suggestionsList.add(suggestionsArray.getString(i));
                        }
                        runOnUiThread(() -> {
                            SuggestFragment fragment = getSuggestFragment();
                            if (fragment != null) {
                                fragment.setSuggestions(suggestionsList);
                            }
                        });
                    } else {
                        String reason = json.getString("reason");
                        Log.e(TAG, "Plant suggestion API returned error: " + reason);
                        runOnUiThread(() -> Toast.makeText(MainActivity.this, "Suggestion failed: " + reason, Toast.LENGTH_LONG).show());
                    }
                } catch (JSONException e) {
                    Log.e(TAG, "Failed to parse suggestion response", e);
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, "Failed to parse server response.", Toast.LENGTH_LONG).show());
                }
            }
        });
    }

    private void writeCharacteristicToQueue(BluetoothGattCharacteristic characteristic, byte[] value) {
        writeQueue.add(() -> {
            if (bluetoothGatt != null && characteristic != null) {
                characteristic.setValue(value);
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    isWriting = false;
                    processWriteQueue();
                    return;
                }
                boolean success = bluetoothGatt.writeCharacteristic(characteristic);
                if (!success) {
                    isWriting = false;
                    processWriteQueue();
                }
            } else {
                isWriting = false;
                processWriteQueue();
            }
        });
        processWriteQueue();
    }

    private void processWriteQueue() {
        if (isWriting || writeQueue.isEmpty()) return;
        isWriting = true;
        Runnable runnable = writeQueue.poll();
        if (runnable != null) {
            runnable.run();
        } else {
            isWriting = false;
        }
    }

    private void fetchLlmResponse() {
        suggestionBuilder.setLength(0);
        llmOffset = 1;
        requestLlmChunk();
    }

    private void requestLlmChunk() {
        if (bluetoothGatt == null) return;
        BluetoothGattService service = bluetoothGatt.getService(GattAttributes.DEMETER_SERVICE_UUID);
        if (service == null) return;
        BluetoothGattCharacteristic suggestChar = service.getCharacteristic(GattAttributes.UUID_SUGGEST);
        if (suggestChar == null) return;

        byte[] value = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(llmOffset).array();
        suggestChar.setValue(value);
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        bluetoothGatt.writeCharacteristic(suggestChar);
    }

    private void readLlmChunk() {
        if (bluetoothGatt == null) return;
        BluetoothGattService service = bluetoothGatt.getService(GattAttributes.DEMETER_SERVICE_UUID);
        if (service == null) return;
        BluetoothGattCharacteristic llmChar = service.getCharacteristic(GattAttributes.UUID_LLM);
        if (llmChar == null) return;
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        bluetoothGatt.readCharacteristic(llmChar);
    }
    private List<UUID> characteristicsToSubscribe;
    private int currentSubscriptionIndex = 0;
    private void subscribeToCharacteristics(BluetoothGatt gatt) {
        characteristicsToSubscribe = List.of(
                GattAttributes.UUID_K, GattAttributes.UUID_P, GattAttributes.UUID_N,
                GattAttributes.UUID_PH, GattAttributes.UUID_HUMID, GattAttributes.UUID_SUN,
                GattAttributes.UUID_MOISTURE, GattAttributes.UUID_LIGHT,
                GattAttributes.UUID_LLM_STATUS, GattAttributes.UUID_IMAGE_STATUS
        );
        currentSubscriptionIndex = 0;
        subscribeNextCharacteristic(gatt);
    }

    private void subscribeNextCharacteristic(BluetoothGatt gatt) {
        if (currentSubscriptionIndex < characteristicsToSubscribe.size()) {
            UUID characteristicUuid = characteristicsToSubscribe.get(currentSubscriptionIndex);
            setCharacteristicNotificationInternal(gatt, characteristicUuid, true);
        }
    }

    private void setCharacteristicNotificationInternal(BluetoothGatt gatt, UUID characteristicUuid, boolean enabled) {
        BluetoothGattService service = gatt.getService(GattAttributes.DEMETER_SERVICE_UUID);
        if (service == null) {
            currentSubscriptionIndex++;
            subscribeNextCharacteristic(gatt);
            return;
        }
        BluetoothGattCharacteristic characteristic = service.getCharacteristic(characteristicUuid);
        if (characteristic == null) {
            currentSubscriptionIndex++;
            subscribeNextCharacteristic(gatt);
            return;
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            currentSubscriptionIndex++;
            subscribeNextCharacteristic(gatt);
            return;
        }
        gatt.setCharacteristicNotification(characteristic, enabled);
        UUID cccdUuid = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
        BluetoothGattDescriptor descriptor = characteristic.getDescriptor(cccdUuid);
        if (descriptor != null) {
            byte[] value = enabled ? BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE : BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE;
            descriptor.setValue(value);
            gatt.writeDescriptor(descriptor);
        } else {
            currentSubscriptionIndex++;
            subscribeNextCharacteristic(gatt);
        }
    }

    private void updatePlotData(android.bluetooth.BluetoothGattCharacteristic characteristic) {
        UUID uuid = characteristic.getUuid();
        byte[] data = characteristic.getValue();
        if (data == null || data.length < 4) return;
        final float value = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).getFloat();

        runOnUiThread(() -> {
            if (uuid.equals(GattAttributes.UUID_N)) addHistory(nHistory, value);
            else if (uuid.equals(GattAttributes.UUID_P)) addHistory(pHistory, value);
            else if (uuid.equals(GattAttributes.UUID_K)) addHistory(kHistory, value);
            else if (uuid.equals(GattAttributes.UUID_PH)) addHistory(phHistory, value);
            else if (uuid.equals(GattAttributes.UUID_HUMID)) addHistory(humidityHistory, value);
            else if (uuid.equals(GattAttributes.UUID_SUN)) addHistory(sunHistory, value);
            else if (uuid.equals(GattAttributes.UUID_MOISTURE)) addHistory(moistureHistory, value);
            else if (uuid.equals(GattAttributes.UUID_LIGHT)) addHistory(lightHistory, value);

            StatsFragment fragment = getStatsFragment();
            if (fragment != null) {
                HashMap<String, ArrayList<Float>> historyData = new HashMap<>();
                historyData.put("N", nHistory);
                historyData.put("P", pHistory);
                historyData.put("K", kHistory);
                historyData.put("pH", phHistory);
                historyData.put("Humidity", humidityHistory);
                historyData.put("Sun", sunHistory);
                historyData.put("Moisture", moistureHistory);
                historyData.put("Light", lightHistory);
                fragment.updatePlot(historyData);
            }
        });
    }

    private void addHistory(ArrayList<Float> history, float value) {
        history.add(value);
        if (history.size() > 100) {
            history.remove(0);
        }
    }


    public void fetchAugmentedImage() {
        if (currentPhotoPath == null || aoiList == null || aoiList.isEmpty()) {
            runOnUiThread(() -> Toast.makeText(MainActivity.this, "Please select an image and at least one AOI.", Toast.LENGTH_SHORT).show());
            return;
        }

        runOnUiThread(() -> {
            SuggestFragment fragment = getSuggestFragment();
            if (fragment != null) {
                fragment.setImageSliderVisibility(View.GONE);
                fragment.setAugmentedImageProgressVisibility(View.VISIBLE);
                fragment.setAugmentedImageProgressBarVisibility(View.VISIBLE);
                fragment.setAugmentedImageProgressText("Generating image...");
                fragment.setAugmentedImageProgress(50); // Using as an indeterminate indicator
            }
            Toast.makeText(this, "Generating and fetching augmented image...", Toast.LENGTH_LONG).show();
        });

        String plantName;
        if (suggestionBuilder == null || suggestionBuilder.length() == 0) {
            plantName = "beautiful";
        } else {
            String suggestion = suggestionBuilder.toString();
            // Take the first line and remove leading list markers and numbers.
            String firstLine = suggestion.split("\n")[0].replaceAll("^\\s*\\d*\\.\\s*|^\\s*[-*]\\s*", "").trim();
            // Split into words and take the first one.
            String[] words = firstLine.split("\\s+");
            if (words.length > 0 && !words[0].isEmpty()) {
                // Get the first word and remove any trailing non-alphanumeric characters.
                plantName = words[0].replaceAll("[^a-zA-Z0-9]+$", "");
            } else {
                plantName = "plant"; // Default if parsing fails
            }
        }


        String[] plantTypes = getResources().getStringArray(R.array.plant_types);
        String plantTypeStr = (plantType >= 0 && plantType < plantTypes.length) ? plantTypes[plantType] : "outdoor";

        byte[] imageData;
        try {
            Bitmap bitmap = BitmapFactory.decodeFile(currentPhotoPath);

            InputStream exifInputStream = getContentResolver().openInputStream(Uri.fromFile(new File(currentPhotoPath)));
            ExifInterface exifInterface = new ExifInterface(exifInputStream);
            int orientation = exifInterface.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            Matrix matrix = new Matrix();
            switch (orientation) {
                case ExifInterface.ORIENTATION_ROTATE_90: matrix.postRotate(90); break;
                case ExifInterface.ORIENTATION_ROTATE_180: matrix.postRotate(180); break;
                case ExifInterface.ORIENTATION_ROTATE_270: matrix.postRotate(270); break;
            }
            Bitmap rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
            Bitmap resizedBitmap = Bitmap.createScaledBitmap(rotatedBitmap, 1024, 1024, true);

            ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
            resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 85, byteStream);
            imageData = byteStream.toByteArray();
            originalImage = imageData; // Also update the original image for the slider
        } catch (IOException e) {
            Log.e(TAG, "Failed to process image for upload", e);
            runOnUiThread(() -> Toast.makeText(MainActivity.this, "Failed to read image file.", Toast.LENGTH_SHORT).show());
            return;
        }

        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build();

        HttpUrl.Builder urlBuilder = new HttpUrl.Builder()
                .scheme("https")
                .host("demeter-dot-heph2-338519.uc.r.appspot.com")
                .addPathSegment("demeter")
                .addPathSegment("product")
                .addPathSegment("create")
                .addQueryParameter("plant_name", plantName)
                .addQueryParameter("plant_type", plantTypeStr)
                .addQueryParameter("mask_size", String.valueOf(augmentSize));

        for (int i = 0; i < aoiList.size(); i += 2) {
            urlBuilder.addQueryParameter("aois", aoiList.get(i) + "," + aoiList.get(i + 1));
        }

        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("scene_img", "scene.jpg",
                        RequestBody.create(imageData, MediaType.parse("image/jpeg")))
                .build();

        Request request = new Request.Builder()
                .url(urlBuilder.build())
                .post(requestBody)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "DALL-E service call failed", e);
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, "Failed to generate image: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    SuggestFragment fragment = getSuggestFragment();
                    if (fragment != null) {
                        fragment.setAugmentedImageProgressVisibility(View.GONE);
                        fragment.setAugmentedImageProgressBarVisibility(View.GONE);
                    }
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    String errorBody = response.body().string();
                    Log.e(TAG, "DALL-E service error: " + errorBody);
                    runOnUiThread(() -> {
                        Toast.makeText(MainActivity.this, "Failed to generate image: " + response.message(), Toast.LENGTH_LONG).show();
                        SuggestFragment fragment = getSuggestFragment();
                        if (fragment != null) {
                            fragment.setAugmentedImageProgressVisibility(View.GONE);
                            fragment.setAugmentedImageProgressBarVisibility(View.GONE);
                        }
                    });
                    return;
                }

                try {
                    String responseBody = response.body().string();
                    JSONObject json = new JSONObject(responseBody);
                    if (json.getBoolean("success")) {
                        String assetURI = json.getString("assetURI");
                        downloadAugmentedImage(assetURI);
                    } else {
                        String reason = json.getString("reason");
                        Log.e(TAG, "DALL-E service returned error: " + reason);
                        runOnUiThread(() -> Toast.makeText(MainActivity.this, "Image generation failed: " + reason, Toast.LENGTH_LONG).show());
                    }
                } catch (JSONException e) {
                    Log.e(TAG, "Failed to parse DALL-E response", e);
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, "Failed to parse server response.", Toast.LENGTH_LONG).show());
                }
            }
        });
    }

    private void downloadAugmentedImage(String url) {
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder().url(url).build();

        runOnUiThread(() -> {
            SuggestFragment fragment = getSuggestFragment();
            if (fragment != null) {
                fragment.setAugmentedImageProgressText("Downloading image...");
                fragment.setAugmentedImageProgress(75);
            }
        });

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Failed to download augmented image", e);
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, "Failed to download image: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    SuggestFragment fragment = getSuggestFragment();
                    if (fragment != null) {
                        fragment.setAugmentedImageProgressVisibility(View.GONE);
                        fragment.setAugmentedImageProgressBarVisibility(View.GONE);
                    }
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    String errorBody = response.body().string();
                    Log.e(TAG, "Failed to download augmented image: " + errorBody);
                    runOnUiThread(() -> {
                        Toast.makeText(MainActivity.this, "Failed to download image: " + response.message(), Toast.LENGTH_LONG).show();
                        SuggestFragment fragment = getSuggestFragment();
                        if (fragment != null) {
                            fragment.setAugmentedImageProgressVisibility(View.GONE);
                            fragment.setAugmentedImageProgressBarVisibility(View.GONE);
                        }
                    });
                    return;
                }
                byte[] imageBytes = response.body().bytes();
                finalizeAugmentedImage(imageBytes);
            }
        });
    }

    private Fragment getCurrentFragment() {
        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment);
        if (navHostFragment != null && navHostFragment.getChildFragmentManager().getFragments().size() > 0) {
            return navHostFragment.getChildFragmentManager().getFragments().get(0);
        }
        return null;
    }

    private SuggestFragment getSuggestFragment() {
        Fragment currentFragment = getCurrentFragment();
        if (currentFragment instanceof SuggestFragment) {
            return (SuggestFragment) currentFragment;
        }
        return null;
    }

    private SettingsFragment getSettingsFragment() {
        Fragment currentFragment = getCurrentFragment();
        if (currentFragment instanceof SettingsFragment) {
            return (SettingsFragment) currentFragment;
        }
        return null;
    }

    private StatsFragment getStatsFragment() {
        Fragment currentFragment = getCurrentFragment();
        if (currentFragment instanceof StatsFragment) {
            return (StatsFragment) currentFragment;
        }
        return null;
    }

    private void handleLlmRead(byte[] data) {
        if (data != null && data.length > 0) {
            suggestionBuilder.append(new String(data));
            if (data.length < 225) { // Assuming this indicates the last chunk
                runOnUiThread(() -> {
                    SuggestFragment fragment = getSuggestFragment();
                    if (fragment != null) {
                        fragment.setSuggestionText("Suggestion: " + suggestionBuilder.toString());
                        fragment.enableTakePictureButton(true);
                        fragment.enableGetAugmentedImageButton(false);
                    }
                    Toast.makeText(MainActivity.this, "Suggestion received.", Toast.LENGTH_LONG).show();
                });
            } else {
                llmOffset += data.length;
                requestLlmChunk();
            }
        }
    }

    private void finalizeAugmentedImage(byte[] augmentedImage) {
        if (augmentedImage == null || augmentedImage.length == 0) {
            runOnUiThread(() -> Toast.makeText(MainActivity.this, "Received empty image.", Toast.LENGTH_SHORT).show());
            return;
        }
        runOnUiThread(() -> {
            imageList.clear();
            if (originalImage != null) {
                imageList.add(originalImage);
            }
            imageList.add(augmentedImage);
            SuggestFragment fragment = getSuggestFragment();
            if (fragment != null) {
                fragment.updateImageSlider();
                fragment.setImageSliderVisibility(View.VISIBLE);
                fragment.setAugmentedImageProgressVisibility(View.GONE);
                fragment.setAugmentedImageProgressBarVisibility(View.GONE);
            }
            Toast.makeText(MainActivity.this, "Augmented image received.", Toast.LENGTH_SHORT).show();
        });
    }

    private void handleLlmStatusChange(int llmStatus) {
        runOnUiThread(() -> {
            SuggestFragment fragment = getSuggestFragment();
            if (fragment != null) {
                if (llmStatus == 1) {
                    fragment.setSuggestionText("Suggestion: Generating...");
                } else if (llmStatus == 2) {
                    fetchLlmResponse();
                }
            }
        });
    }

    private void handleImageStatusChange(int imageStatus) {
        runOnUiThread(() -> {
            SuggestFragment fragment = getSuggestFragment();
            switch (imageStatus) {
                case 1: // Processing
                    Toast.makeText(MainActivity.this, "Image received, server is processing...", Toast.LENGTH_SHORT).show();
                    if (fragment != null) fragment.enableGetAugmentedImageButton(false);
                    break;
                case 2: // Success
                    Toast.makeText(MainActivity.this, "Image processed. You can now get the augmented image.", Toast.LENGTH_LONG).show();
                    if (fragment != null) fragment.enableGetAugmentedImageButton(true);
                    break;
                case 3: // Error
                    Toast.makeText(MainActivity.this, "Server failed to process image.", Toast.LENGTH_LONG).show();
                    if (fragment != null) fragment.enableGetAugmentedImageButton(false);
                    break;
            }
        });
    }

    private void updateLedIndicator(BleConnectionStatus status) {
        currentStatus = status;
        runOnUiThread(() -> {
            if (ledIndicator == null) return;
            int drawableId;
            switch (status) {
                case SCANNING:
                    drawableId = R.drawable.led_yellow;
                    break;
                case CONNECTING:
                    drawableId = R.drawable.led_yellow;
                    break;
                case CONNECTED:
                    drawableId = R.drawable.led_green;
                    break;
                case ERROR:
                    drawableId = R.drawable.led_red;
                    break;
                case DISCONNECTED:
                default:
                    drawableId = R.drawable.led_grey;
                    break;
            }
            ledIndicator.setImageResource(drawableId);
        });
    }

    public BleConnectionStatus getCurrentConnectionStatus() {
        return currentStatus;
    }

    public void setNumSuggestions(int numSuggestions) {
        this.numSuggestions = numSuggestions;
        if (bluetoothGatt == null) return;
        BluetoothGattService service = bluetoothGatt.getService(GattAttributes.DEMETER_SERVICE_UUID);
        if (service == null) return;
        BluetoothGattCharacteristic characteristic = service.getCharacteristic(GattAttributes.UUID_NUM_SUGGESTIONS);
        if (characteristic != null) {
            byte[] value = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(numSuggestions).array();
            writeCharacteristicToQueue(characteristic, value);
        }
    }

    public void setPlantType(int plantType) {
        this.plantType = plantType;
        if (bluetoothGatt == null) return;
        BluetoothGattService service = bluetoothGatt.getService(GattAttributes.DEMETER_SERVICE_UUID);
        if (service == null) return;
        BluetoothGattCharacteristic characteristic = service.getCharacteristic(GattAttributes.UUID_PLANT_TYPE);
        if (characteristic != null) {
            byte[] value = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(plantType).array();
            writeCharacteristicToQueue(characteristic, value);
        }
    }

    public void setAugmentSize(int size) {
        this.augmentSize = size;
        if (bluetoothGatt == null) return;
        BluetoothGattService service = bluetoothGatt.getService(GattAttributes.DEMETER_SERVICE_UUID);
        if (service == null) return;

        BluetoothGattCharacteristic augmentSizeChar = service.getCharacteristic(GattAttributes.UUID_AUGMENT_SIZE);
        if (augmentSizeChar != null) {
            byte[] value = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(size).array();
            writeCharacteristicToQueue(augmentSizeChar, value);
        }
    }

    @Override
    public void onSuggestionSelected(String plantName) {
        suggestionBuilder.setLength(0);
        suggestionBuilder.append(plantName);
        runOnUiThread(() -> {
            SuggestFragment fragment = getSuggestFragment();
            if (fragment != null) {
                fragment.enableTakePictureButton(true);
            }
        });
    }
}
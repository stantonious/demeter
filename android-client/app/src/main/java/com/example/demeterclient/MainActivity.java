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
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ImageView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.json.JSONException;
import org.json.JSONObject;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final long SCAN_PERIOD = 10000;

    private AppBarConfiguration appBarConfiguration;
    private NavController navController;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner bluetoothLeScanner;
    private boolean scanning;
    private Handler handler;
    private BluetoothGatt bluetoothGatt;
    private Handler bleHandler = new Handler(Looper.getMainLooper());
    private String currentPhotoPath;
    private static final String KEY_PHOTO_PATH = "com.example.demeterclient.photo_path";

    private SharedViewModel sharedViewModel;

    public enum BleConnectionStatus {
        DISCONNECTED,
        SCANNING,
        CONNECTING,
        CONNECTED,
        ERROR
    }

    private ImageView ledIndicator;
    private ImageView demeterLedIndicator;

    private Queue<Runnable> writeQueue = new LinkedList<>();
    private boolean isWriting = false;

    private OkHttpClient httpClient;

    private List<UUID> characteristicsToSubscribe;
    private int currentSubscriptionIndex = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            currentPhotoPath = savedInstanceState.getString(KEY_PHOTO_PATH);
        }
        setContentView(R.layout.activity_main);

        sharedViewModel = new ViewModelProvider(this).get(SharedViewModel.class);

        httpClient = new OkHttpClient.Builder()
                .connectTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build();

        Toolbar toolbar = findViewById(R.id.toolbar_custom);
        setSupportActionBar(toolbar);

        ledIndicator = findViewById(R.id.led_indicator);
        demeterLedIndicator = findViewById(R.id.demeter_led_indicator);

        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_fragment);
        navController = navHostFragment.getNavController();

        appBarConfiguration = new AppBarConfiguration.Builder(navController.getGraph()).build();
        NavigationUI.setupActionBarWithNavController(this, navController, appBarConfiguration);

        handler = new Handler();
        final BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();
        startBleScan();
        updateDemeterLedIndicator(true);
    }

    @Override
    public boolean onSupportNavigateUp() {
        return NavigationUI.navigateUp(navController, appBarConfiguration) || super.onSupportNavigateUp();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.toolbar_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_logout) {
            navController.navigate(R.id.loginFragment);
            return true;
        }
        return super.onOptionsItemSelected(item);
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
            if (allGranted) startBleScan();
            else Toast.makeText(this, "Permissions are required.", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (currentPhotoPath != null) {
            outState.putString(KEY_PHOTO_PATH, currentPhotoPath);
        }
    }

    private void startBleScan() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.ACCESS_FINE_LOCATION
            }, 1);
            return;
        }
        if (bluetoothLeScanner == null) {
            bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
        }
        scanLeDevice(true);
    }

    private void scanLeDevice(final boolean enable) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) return;
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
            ScanSettings settings = new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build();
            bluetoothLeScanner.startScan(Collections.singletonList(filter), settings, leScanCallback);
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
            if (scanning) scanLeDevice(false);
        }
    };

    private void connectToDevice(BluetoothDevice device) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) return;
        updateLedIndicator(BleConnectionStatus.CONNECTING);
        bluetoothGatt = device.connectGatt(this, false, gattCallback);
    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED) {
                updateLedIndicator(BleConnectionStatus.CONNECTED);
                bleHandler.postDelayed(() -> {
                    if (bluetoothGatt != null && ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                        bluetoothGatt.discoverServices();
                    }
                }, 600);
            } else {
                updateLedIndicator(BleConnectionStatus.DISCONNECTED);
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) subscribeToCharacteristics(gatt);
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            super.onDescriptorWrite(gatt, descriptor, status);
            currentSubscriptionIndex++;
            if (currentSubscriptionIndex < characteristicsToSubscribe.size()) {
                subscribeNextCharacteristic(gatt);
            } else {
                Log.d(TAG, "All characteristics subscribed. Writing initial settings.");
                setInitialCharacteristics();
            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            isWriting = false;
            processWriteQueue();
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            updatePlotData(characteristic);
        }
    };

    private void subscribeToCharacteristics(BluetoothGatt gatt) {
        characteristicsToSubscribe = List.of(
                GattAttributes.UUID_K, GattAttributes.UUID_P, GattAttributes.UUID_N,
                GattAttributes.UUID_PH, GattAttributes.UUID_HUMID, GattAttributes.UUID_SUN,
                GattAttributes.UUID_MOISTURE, GattAttributes.UUID_LIGHT);
        currentSubscriptionIndex = 0;
        subscribeNextCharacteristic(gatt);
    }

    private void subscribeNextCharacteristic(BluetoothGatt gatt) {
        if (currentSubscriptionIndex < characteristicsToSubscribe.size()) {
            setCharacteristicNotificationInternal(gatt, characteristicsToSubscribe.get(currentSubscriptionIndex), true);
        } else {
            Log.d(TAG, "All sensor characteristics subscribed.");
            setInitialCharacteristics();
        }
    }

    private void setCharacteristicNotificationInternal(BluetoothGatt gatt, UUID characteristicUuid, boolean enabled) {
        BluetoothGattService service = gatt.getService(GattAttributes.DEMETER_SERVICE_UUID);
        if (service == null) {
            subscribeNextCharacteristic(gatt);
            return;
        }
        BluetoothGattCharacteristic characteristic = service.getCharacteristic(characteristicUuid);
        if (characteristic == null || ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            subscribeNextCharacteristic(gatt);
            return;
        }
        gatt.setCharacteristicNotification(characteristic, enabled);
        BluetoothGattDescriptor descriptor = characteristic.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"));
        if (descriptor != null) {
            descriptor.setValue(enabled ? BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE : BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
            gatt.writeDescriptor(descriptor);
        } else {
            subscribeNextCharacteristic(gatt);
        }
    }

    private void updatePlotData(BluetoothGattCharacteristic characteristic) {
        UUID uuid = characteristic.getUuid();
        byte[] data = characteristic.getValue();
        if (data == null || data.length < 4) return;
        final float value = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).getFloat();

        HashMap<String, ArrayList<Float>> history = sharedViewModel.getSensorDataHistory().getValue();
        if (history == null) {
            history = new HashMap<>();
        }
        if (uuid.equals(GattAttributes.UUID_N)) addHistory(history, "N", value);
        else if (uuid.equals(GattAttributes.UUID_P)) addHistory(history, "P", value);
        else if (uuid.equals(GattAttributes.UUID_K)) addHistory(history, "K", value);
        else if (uuid.equals(GattAttributes.UUID_PH)) addHistory(history, "pH", value);
        else if (uuid.equals(GattAttributes.UUID_HUMID)) addHistory(history, "Humidity", value);
        else if (uuid.equals(GattAttributes.UUID_SUN)) addHistory(history, "Sun", value);
        else if (uuid.equals(GattAttributes.UUID_MOISTURE)) addHistory(history, "Moisture", value);
        else if (uuid.equals(GattAttributes.UUID_LIGHT)) addHistory(history, "Light", value);
        sharedViewModel.updateSensorDataHistory(history);
    }

    private void addHistory(HashMap<String, ArrayList<Float>> history, String key, float value) {
        ArrayList<Float> series = history.get(key);
        if (series == null) {
            series = new ArrayList<>();
        }
        series.add(value);
        if (series.size() > 100) series.remove(0);
        history.put(key, series);
    }

    private void processWriteQueue() {
        if (isWriting || writeQueue.isEmpty()) return;
        isWriting = true;
        Runnable runnable = writeQueue.poll();
        if (runnable != null) runnable.run();
        else isWriting = false;
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
                if (!bluetoothGatt.writeCharacteristic(characteristic)) isWriting = false;
            } else {
                isWriting = false;
            }
            processWriteQueue();
        });
        processWriteQueue();
    }

    private void updateLedIndicator(BleConnectionStatus status) {
        sharedViewModel.setBleConnectionStatus(status);
        runOnUiThread(() -> {
            if (ledIndicator == null) return;
            int drawableId;
            switch (status) {
                case SCANNING: case CONNECTING: drawableId = R.drawable.led_yellow; break;
                case CONNECTED: drawableId = R.drawable.led_green; break;
                case ERROR: drawableId = R.drawable.led_red; break;
                default: drawableId = R.drawable.led_grey; break;
            }
            ledIndicator.setImageResource(drawableId);
        });
    }

    private void updateDemeterLedIndicator(boolean isConnected) {
        runOnUiThread(() -> {
            if (demeterLedIndicator != null)
                demeterLedIndicator.setImageResource(isConnected ? R.drawable.led_green : R.drawable.led_red);
        });
    }

    private void setInitialCharacteristics() {
        if (sharedViewModel.getAugmentSize().getValue() != null) {
            setAugmentSize(sharedViewModel.getAugmentSize().getValue());
        }
        // Set other initial characteristics if needed
    }

    public void reconnectBle() {
        if (bluetoothGatt != null) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) return;
            bluetoothGatt.disconnect();
            bluetoothGatt.close();
            bluetoothGatt = null;
        }
        scanLeDevice(true);
    }

    private void setAugmentSize(int size) {
        if (bluetoothGatt == null) return;
        BluetoothGattService service = bluetoothGatt.getService(GattAttributes.DEMETER_SERVICE_UUID);
        if (service == null) return;
        BluetoothGattCharacteristic augmentSizeChar = service.getCharacteristic(GattAttributes.UUID_AUGMENT_SIZE);
        if (augmentSizeChar != null) {
            byte[] value = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(size).array();
            writeCharacteristicToQueue(augmentSizeChar, value);
        }
    }

    public void requestPlantSuggestion(String plantType, String subType, String age, int numSuggestions) {
        HashMap<String, ArrayList<Float>> history = sharedViewModel.getSensorDataHistory().getValue();
        float n_mgkg = history != null && history.get("N") != null && !history.get("N").isEmpty() ? history.get("N").get(history.get("N").size() - 1) : 0;
        float p_mgkg = history != null && history.get("P") != null && !history.get("P").isEmpty() ? history.get("P").get(history.get("P").size() - 1) : 0;
        float k_mgkg = history != null && history.get("K") != null && !history.get("K").isEmpty() ? history.get("K").get(history.get("K").size() - 1) : 0;
        float ph = history != null && history.get("pH") != null && !history.get("pH").isEmpty() ? history.get("pH").get(history.get("pH").size() - 1) : 7.0f;
        float moisture = history != null && history.get("Moisture") != null && !history.get("Moisture").isEmpty() ? history.get("Moisture").get(history.get("Moisture").size() - 1) : 50;
        float sun_intensity = history != null && history.get("Sun") != null && !history.get("Sun").isEmpty() ? history.get("Sun").get(history.get("Sun").size() - 1) : 50000;

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
                .addQueryParameter("lat", "39.5186")
                .addQueryParameter("lon", "-104.7614")
                .addQueryParameter("plant_type", plantType)
                .addQueryParameter("sub_type", subType)
                .addQueryParameter("age", age)
                .addQueryParameter("max_plants", String.valueOf(numSuggestions));

        Request request = new Request.Builder()
                .url(urlBuilder.build())
                .get()
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Plant suggestion API call failed", e);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    Log.e(TAG, "Plant suggestion API error: " + response.body().string());
                    return;
                }

                try {
                    String responseBody = response.body().string();
                    JSONObject json = new JSONObject(responseBody);
                    if (json.getBoolean("success")) {
                        org.json.JSONArray suggestionsArray = json.getJSONArray("suggestions");
                        ArrayList<String> suggestionsList = new ArrayList<>();
                        for (int i = 0; i < suggestionsArray.length(); i++) {
                            suggestionsList.add(suggestionsArray.getString(i));
                        }
                        runOnUiThread(() -> {
                            Bundle bundle = new Bundle();
                            bundle.putStringArrayList("suggestions", suggestionsList);
                            navController.navigate(R.id.action_suggestFragment_to_aoiSelectFragment, bundle);
                        });
                    } else {
                        Log.e(TAG, "Plant suggestion API returned error: " + json.getString("reason"));
                    }
                } catch (JSONException e) {
                    Log.e(TAG, "Failed to parse suggestion response", e);
                }
            }
        });
    }

    public void requestFeasibilityAnalysis(String plantName) {
        HashMap<String, ArrayList<Float>> history = sharedViewModel.getSensorDataHistory().getValue();
        float n_mgkg = history != null && history.get("N") != null && !history.get("N").isEmpty() ? history.get("N").get(history.get("N").size() - 1) : 0;
        float p_mgkg = history != null && history.get("P") != null && !history.get("P").isEmpty() ? history.get("P").get(history.get("P").size() - 1) : 0;
        float k_mgkg = history != null && history.get("K") != null && !history.get("K").isEmpty() ? history.get("K").get(history.get("K").size() - 1) : 0;
        float ph = history != null && history.get("pH") != null && !history.get("pH").isEmpty() ? history.get("pH").get(history.get("pH").size() - 1) : 7.0f;
        float moisture = history != null && history.get("Moisture") != null && !history.get("Moisture").isEmpty() ? history.get("Moisture").get(history.get("Moisture").size() - 1) : 50;
        float sun_intensity = history != null && history.get("Sun") != null && !history.get("Sun").isEmpty() ? history.get("Sun").get(history.get("Sun").size() - 1) : 50000;

        HttpUrl.Builder urlBuilder = new HttpUrl.Builder()
                .scheme("https")
                .host("demeter-dot-heph2-338519.uc.r.appspot.com")
                .addPathSegment("demeter")
                .addPathSegment("plant")
                .addPathSegment("feasibility")
                .addQueryParameter("plant_type", plantName)
                .addQueryParameter("n_mgkg", String.valueOf(n_mgkg))
                .addQueryParameter("p_mgkg", String.valueOf(p_mgkg))
                .addQueryParameter("k_mgkg", String.valueOf(k_mgkg))
                .addQueryParameter("ph", String.valueOf(ph))
                .addQueryParameter("moisture", String.valueOf(moisture))
                .addQueryParameter("sun_intensity", String.valueOf(sun_intensity))
                .addQueryParameter("lat", "39.5186")
                .addQueryParameter("lon", "-104.7614");

        Request request = new Request.Builder().url(urlBuilder.build()).get().build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Feasibility API call failed", e);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    Log.e(TAG, "Feasibility API error: " + response.body().string());
                    return;
                }

                try {
                    String responseBody = response.body().string();
                    JSONObject json = new JSONObject(responseBody);
                    if (json.getBoolean("success")) {
                        String feasibilityText = json.getJSONObject("feasibility").toString(2);
                        runOnUiThread(() -> {
                            Intent intent = new Intent(MainActivity.this, FeasibilityActivity.class);
                            intent.putExtra("feasibility_text", feasibilityText);
                            startActivity(intent);
                        });
                    } else {
                        Log.e(TAG, "Feasibility check failed: " + json.getString("reason"));
                    }
                } catch (JSONException e) {
                    Log.e(TAG, "Failed to parse feasibility response", e);
                }
            }
        });
    }

    public void requestAugmentedImage(String imageUriString, ArrayList<String> selectedPlants, ArrayList<Integer> aoiList, String plantType, String subType, String age) {
        byte[] originalImageData;
        try {
            Uri imageUri = Uri.parse(imageUriString);
            Bitmap bitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), imageUri);
            ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, byteStream);
            originalImageData = byteStream.toByteArray();
        } catch (IOException e) {
            Log.e(TAG, "Failed to process image for upload", e);
            sharedViewModel.setIsAugmenting(false);
            return;
        }

        HttpUrl.Builder urlBuilder = new HttpUrl.Builder()
                .scheme("https")
                .host("demeter-dot-heph2-338519.uc.r.appspot.com")
                .addPathSegment("demeter")
                .addPathSegment("product")
                .addPathSegment("create")
                .addQueryParameter("plant_name", String.join(",", selectedPlants))
                .addQueryParameter("plant_type", plantType)
                .addQueryParameter("sub_type", subType)
                .addQueryParameter("age", age)
                .addQueryParameter("mask_size", String.valueOf(sharedViewModel.getAugmentSize().getValue()));

        for (int i = 0; i < aoiList.size(); i += 2) {
            urlBuilder.addQueryParameter("aois", aoiList.get(i) + "," + aoiList.get(i + 1));
        }

        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("scene_img", "scene.jpg",
                        RequestBody.create(originalImageData, MediaType.parse("image/jpeg")))
                .build();

        Request request = new Request.Builder()
                .url(urlBuilder.build())
                .post(requestBody)
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "DALL-E service call failed", e);
                sharedViewModel.setIsAugmenting(false);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    Log.e(TAG, "DALL-E service error: " + response.body().string());
                    sharedViewModel.setIsAugmenting(false);
                    return;
                }

                try {
                    String responseBody = response.body().string();
                    JSONObject json = new JSONObject(responseBody);
                    if (json.getBoolean("success")) {
                        String assetURI = json.getString("assetURI");
                        downloadAugmentedImage(assetURI, originalImageData);
                    } else {
                        Log.e(TAG, "DALL-E service returned error: " + json.getString("reason"));
                        sharedViewModel.setIsAugmenting(false);
                    }
                } catch (JSONException e) {
                    Log.e(TAG, "Failed to parse DALL-E response", e);
                    sharedViewModel.setIsAugmenting(false);
                }
            }
        });
    }

    private void downloadAugmentedImage(String url, byte[] originalImage) {
        Request request = new Request.Builder().url(url).build();
        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Failed to download augmented image", e);
                sharedViewModel.setIsAugmenting(false);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    Log.e(TAG, "Failed to download augmented image: " + response.body().string());
                    sharedViewModel.setIsAugmenting(false);
                    return;
                }
                byte[] augmentedImage = response.body().bytes();
                List<byte[]> images = new ArrayList<>();
                images.add(originalImage);
                images.add(augmentedImage);
                sharedViewModel.setAugmentedResult(images);
            }
        });
    }
}
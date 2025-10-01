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
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final long SCAN_PERIOD = 10000; // Stops scanning after 10 seconds.

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner bluetoothLeScanner;
    private boolean scanning;
    private Handler handler;
    private BluetoothGatt bluetoothGatt;

    private SwipeRefreshLayout swipeRefreshLayout;
    private ImageView ledIndicator;
    private TextView statusTextView;
    private Button getSuggestionButton;
    private TextView suggestionTextView;
    private EditText numSuggestionsEditText;
    private Spinner plantTypeSpinner;
    private PlotView livePlotView;
    private Button takePictureButton;
    private Button getAugmentedImageButton;
    private ImageView augmentedImageView;

    private ByteArrayOutputStream augmentedImageStream = new ByteArrayOutputStream();
    private int imageReadOffset = 0;

    private static final int REQUEST_IMAGE_CAPTURE = 2;
    private static final int REQUEST_CAMERA_PERMISSION = 3;
    private static final int REQUEST_PREVIEW_IMAGE = 4;
    private String currentPhotoPath;
    private static final String KEY_PHOTO_PATH = "com.example.demeterclient.photo_path";

    private static final int MAX_DATA_POINTS = 100;
    private ArrayList<Float> nHistory = new ArrayList<>();
    private ArrayList<Float> pHistory = new ArrayList<>();
    private ArrayList<Float> kHistory = new ArrayList<>();
    private ArrayList<Float> phHistory = new ArrayList<>();
    private ArrayList<Float> humidityHistory = new ArrayList<>();
    private ArrayList<Float> sunHistory = new ArrayList<>();
    private ArrayList<Float> moistureHistory = new ArrayList<>();
    private ArrayList<Float> lightHistory = new ArrayList<>();

    private enum BleConnectionStatus {
        DISCONNECTED,
        SCANNING,
        CONNECTING,
        CONNECTED,
        ERROR
    }

    private StringBuilder suggestionBuilder = new StringBuilder();
    private int llmOffset = 1;

    private Queue<Runnable> writeQueue = new LinkedList<>();
    private boolean isWriting = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            currentPhotoPath = savedInstanceState.getString(KEY_PHOTO_PATH);
        }
        setContentView(R.layout.activity_main);

        swipeRefreshLayout = findViewById(R.id.swipe_refresh_layout);
        ledIndicator = findViewById(R.id.led_indicator);
        statusTextView = findViewById(R.id.status_text_view);
        getSuggestionButton = findViewById(R.id.get_suggestion_button);
        suggestionTextView = findViewById(R.id.suggestion_text_view);
        numSuggestionsEditText = findViewById(R.id.num_suggestions_edit_text);
        plantTypeSpinner = findViewById(R.id.plant_type_spinner);
        livePlotView = findViewById(R.id.live_plot_view);
        takePictureButton = findViewById(R.id.take_picture_button);
        getAugmentedImageButton = findViewById(R.id.get_augmented_image_button);
        augmentedImageView = findViewById(R.id.augmented_image_view);

        // Populate the spinner
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                R.array.plant_types, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        plantTypeSpinner.setAdapter(adapter);

        getSuggestionButton.setOnClickListener(v -> {
            requestSuggestion();
        });

        takePictureButton.setOnClickListener(v -> {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
            } else {
                dispatchTakePictureIntent();
            }
        });

        getAugmentedImageButton.setOnClickListener(v -> {
            fetchAugmentedImage();
        });

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

        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            statusTextView.setText("Bluetooth is not enabled");
            return;
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.ACCESS_FINE_LOCATION},
                    1);
        } else {
            bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
            scanLeDevice(true);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
                scanLeDevice(true);
            } else {
                statusTextView.setText("Permissions not granted.");
            }
        } else if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                dispatchTakePictureIntent();
            } else {
                statusTextView.setText("Camera permission denied.");
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
                    startActivityForResult(intent, REQUEST_PREVIEW_IMAGE);
                }
                break;
            case REQUEST_PREVIEW_IMAGE:
                if (data != null) {
                    String imageUriString = data.getStringExtra("image_uri");
                    if (imageUriString != null) {
                        Uri imageUri = Uri.parse(imageUriString);
                        sendImage(imageUri);
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

    private void dispatchTakePictureIntent() {
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
        File image = File.createTempFile(
                imageFileName,
                ".jpg",
                storageDir
        );
        currentPhotoPath = image.getAbsolutePath();
        return image;
    }

    private void scanLeDevice(final boolean enable) {
        if (enable) {
            handler.postDelayed(() -> {
                if (scanning) {
                    scanning = false;
                    if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                        return;
                    }
                    bluetoothLeScanner.stopScan(leScanCallback);
                    statusTextView.setText("Scan stopped.");
                }
            }, SCAN_PERIOD);

            scanning = true;
            ScanFilter filter = new ScanFilter.Builder()
              .setServiceUuid(new android.os.ParcelUuid(GattAttributes.DEMETER_SERVICE_UUID))
                .build();
            List<ScanFilter> filters = Collections.singletonList(filter);
            ScanSettings settings = new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build();
            bluetoothLeScanner.startScan(filters, settings, leScanCallback);
            statusTextView.setText("Scanning for Demeter...");
            updateLedIndicator(BleConnectionStatus.SCANNING);
        } else {
            scanning = false;
            bluetoothLeScanner.stopScan(leScanCallback);
            statusTextView.setText("Scan stopped.");
            if (bluetoothGatt == null) {
                updateLedIndicator(BleConnectionStatus.DISCONNECTED);
            }
        }
    }

    private final ScanCallback leScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);
            BluetoothDevice device = result.getDevice();
            Log.d(TAG, "Found device: " + device.getAddress());
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
        runOnUiThread(() -> {
            updateLedIndicator(BleConnectionStatus.CONNECTING);
            statusTextView.setText("Connecting to " + device.getAddress());
        });
        bluetoothGatt = device.connectGatt(this, false, gattCallback);
    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (newState == BluetoothGatt.STATE_CONNECTED) {
                    Log.d(TAG, "Connected to GATT server.");
                    runOnUiThread(() -> {
                        updateLedIndicator(BleConnectionStatus.CONNECTED);
                        statusTextView.setText("Connected");
                    });
                    if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                        return;
                    }
                    gatt.discoverServices();
                } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                    Log.d(TAG, "Disconnected from GATT server.");
                    runOnUiThread(() -> {
                        updateLedIndicator(BleConnectionStatus.DISCONNECTED);
                        statusTextView.setText("Disconnected");
                    });
                }
            } else {
                Log.e(TAG, "onConnectionStateChange received error status: " + status);
                runOnUiThread(() -> {
                    updateLedIndicator(BleConnectionStatus.ERROR);
                    statusTextView.setText("Connection Error");
                });
            }
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            super.onDescriptorWrite(gatt, descriptor, status);
            UUID characteristicUuid = descriptor.getCharacteristic().getUuid();
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "Descriptor write successful for: " + characteristicUuid);
            } else {
                Log.e(TAG, "Descriptor write failed for: " + characteristicUuid + " with status: " + status);
            }
            if (characteristicsToSubscribe != null && currentSubscriptionIndex < characteristicsToSubscribe.size()) {
                currentSubscriptionIndex++;
                subscribeNextCharacteristic(gatt);
            }
        }
        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "Successfully wrote to characteristic " + characteristic.getUuid());
                if (characteristic.getUuid().equals(GattAttributes.UUID_SUGGEST)) {
                    readLlmChunk();
                } else if (characteristic.getUuid().equals(GattAttributes.UUID_IMAGE_REQUEST)) {
                    readAugmentedImageChunk();
                }
            } else {
                Log.e(TAG, "Failed to write to characteristic " + characteristic.getUuid() + " status: " + status);
                writeQueue.clear();
            }
            isWriting = false;
            processWriteQueue();
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (characteristic.getUuid().equals(GattAttributes.UUID_LLM)) {
                    byte[] data = characteristic.getValue();
                    if (data != null && data.length > 0) {
                        suggestionBuilder.append(new String(data));
                        if (data.length < 225) {
                            runOnUiThread(() -> suggestionTextView.setText("Suggestion: " + suggestionBuilder.toString()));
                        } else {
                            llmOffset += data.length;
                            requestLlmChunk();
                        }
                    }
                } else if (characteristic.getUuid().equals(GattAttributes.UUID_AUGMENTED_IMAGE)) {
                    byte[] data = characteristic.getValue();
                    if (data != null && data.length > 0) {
                        try {
                            augmentedImageStream.write(data);
                            // Check if this is the last chunk (chunk size is less than max)
                            if (data.length < 512) {
                                byte[] imageData = augmentedImageStream.toByteArray();
                                Bitmap bitmap = BitmapFactory.decodeByteArray(imageData, 0, imageData.length);
                                runOnUiThread(() -> {
                                    augmentedImageView.setImageBitmap(bitmap);
                                    augmentedImageView.setVisibility(View.VISIBLE);
                                    Toast.makeText(MainActivity.this, "Augmented image received.", Toast.LENGTH_SHORT).show();
                                });
                            } else {
                                imageReadOffset += data.length;
                                requestAugmentedImageChunk();
                            }
                        } catch (IOException e) {
                            Log.e(TAG, "Error writing to augmented image stream", e);
                        }
                    } else {
                        // Empty data also signals end of transfer
                        byte[] imageData = augmentedImageStream.toByteArray();
                        if (imageData.length > 0) {
                            Bitmap bitmap = BitmapFactory.decodeByteArray(imageData, 0, imageData.length);
                            runOnUiThread(() -> {
                                augmentedImageView.setImageBitmap(bitmap);
                                augmentedImageView.setVisibility(View.VISIBLE);
                                Toast.makeText(MainActivity.this, "Augmented image received.", Toast.LENGTH_SHORT).show();
                            });
                        }
                    }
                }
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
                int llmStatus = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0);
                runOnUiThread(() -> {
                    if (llmStatus == 1) {
                        suggestionTextView.setText("Suggestion: Generating...");
                    } else if (llmStatus == 2) {
                        fetchLlmResponse();
                    }
                });
            } else {
                updateUI(characteristic);
            }
        }
    };

    private void requestSuggestion() {
        if (bluetoothGatt == null) return;
        BluetoothGattService service = bluetoothGatt.getService(GattAttributes.DEMETER_SERVICE_UUID);
        if (service == null) {
            Log.e(TAG, "Demeter service not found");
            return;
        }

        writeQueue.clear();
        isWriting = false;

        BluetoothGattCharacteristic numSuggestionsChar = service.getCharacteristic(GattAttributes.UUID_NUM_SUGGESTIONS);
        if (numSuggestionsChar != null) {
            String numSuggestionsStr = numSuggestionsEditText.getText().toString();
            int numSuggestions = 1;
            if (!numSuggestionsStr.isEmpty()) {
                try {
                    numSuggestions = Integer.parseInt(numSuggestionsStr);
                } catch (NumberFormatException e) {
                    Log.e(TAG, "Invalid number format for suggestions", e);
                }
            }
            byte[] numValue = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(numSuggestions).array();
            writeCharacteristicToQueue(numSuggestionsChar, numValue);
        } else {
            Log.e(TAG, "Num Suggestions characteristic not found");
        }

        BluetoothGattCharacteristic plantTypeChar = service.getCharacteristic(GattAttributes.UUID_PLANT_TYPE);
        if (plantTypeChar != null) {
            int plantTypeIndex = plantTypeSpinner.getSelectedItemPosition();
            byte[] plantTypeValue = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(plantTypeIndex).array();
            writeCharacteristicToQueue(plantTypeChar, plantTypeValue);
        } else {
            Log.e(TAG, "Plant Type characteristic not found");
        }

        BluetoothGattCharacteristic suggestChar = service.getCharacteristic(GattAttributes.UUID_SUGGEST);
        if (suggestChar != null) {
            byte[] suggestValue = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(0).array();
            writeCharacteristicToQueue(suggestChar, suggestValue);
        } else {
            Log.e(TAG, "Suggest characteristic not found");
        }

        runOnUiThread(() -> suggestionTextView.setText("Suggestion: Requesting..."));
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
                    Log.e(TAG, "Failed to initiate write for characteristic " + characteristic.getUuid());
                    isWriting = false;
                    processWriteQueue();
                }
            } else {
                Log.e(TAG, "GATT or characteristic is null, skipping write.");
                isWriting = false;
                processWriteQueue();
            }
        });
        processWriteQueue();
    }

    private void processWriteQueue() {
        if (isWriting || writeQueue.isEmpty()) {
            return;
        }
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
                GattAttributes.UUID_K,
                GattAttributes.UUID_P,
                GattAttributes.UUID_N,
                GattAttributes.UUID_PH,
                GattAttributes.UUID_HUMID,
                GattAttributes.UUID_SUN,
                GattAttributes.UUID_MOISTURE,
                GattAttributes.UUID_LIGHT,
                GattAttributes.UUID_LLM_STATUS
        );
        currentSubscriptionIndex = 0;
        subscribeNextCharacteristic(gatt);
    }

    private void subscribeNextCharacteristic(BluetoothGatt gatt) {
        if (currentSubscriptionIndex < characteristicsToSubscribe.size()) {
            UUID characteristicUuid = characteristicsToSubscribe.get(currentSubscriptionIndex);
            setCharacteristicNotificationInternal(gatt, characteristicUuid, true);
        } else {
            Log.d(TAG, "All characteristics subscribed.");
        }
    }

    private void setCharacteristicNotificationInternal(BluetoothGatt gatt, UUID characteristicUuid, boolean enabled) {
        BluetoothGattService service = gatt.getService(GattAttributes.DEMETER_SERVICE_UUID);
        if (service == null) {
            Log.e(TAG, "Service not found for " + characteristicUuid);
            currentSubscriptionIndex++;
            subscribeNextCharacteristic(gatt);
            return;
        }
        BluetoothGattCharacteristic characteristic = service.getCharacteristic(characteristicUuid);
        if (characteristic == null) {
            Log.e(TAG, "Characteristic not found: " + characteristicUuid);
            currentSubscriptionIndex++;
            subscribeNextCharacteristic(gatt);
            return;
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "BLUETOOTH_CONNECT permission missing for setCharacteristicNotificationInternal");
            currentSubscriptionIndex++;
            subscribeNextCharacteristic(gatt);
            return;
        }
        boolean notificationSet = gatt.setCharacteristicNotification(characteristic, enabled);
        if (!notificationSet) {
            Log.e(TAG, "setCharacteristicNotification failed for " + characteristicUuid);
            currentSubscriptionIndex++;
            subscribeNextCharacteristic(gatt);
            return;
        }
        UUID cccdUuid = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
        BluetoothGattDescriptor descriptor = characteristic.getDescriptor(cccdUuid);
        if (descriptor != null) {
            byte[] value = enabled ? BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE : BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE;
            descriptor.setValue(value);
            if (!gatt.writeDescriptor(descriptor)) {
                Log.e(TAG, "writeDescriptor failed for " + characteristicUuid);
            }
        } else {
            Log.w(TAG, "CCCD descriptor not found for " + characteristicUuid);
            currentSubscriptionIndex++;
            subscribeNextCharacteristic(gatt);
        }
    }

    private void updateUI(android.bluetooth.BluetoothGattCharacteristic characteristic) {
        UUID uuid = characteristic.getUuid();
        byte[] data = characteristic.getValue();
        if (data == null || data.length < 4) return;
        final float value = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).getFloat();

        runOnUiThread(() -> {
            if (uuid.equals(GattAttributes.UUID_N)) {
                addHistory(nHistory, value);
            } else if (uuid.equals(GattAttributes.UUID_P)) {
                addHistory(pHistory, value);
            } else if (uuid.equals(GattAttributes.UUID_K)) {
                addHistory(kHistory, value);
            } else if (uuid.equals(GattAttributes.UUID_PH)) {
                addHistory(phHistory, value);
            } else if (uuid.equals(GattAttributes.UUID_HUMID)) {
                addHistory(humidityHistory, value);
            } else if (uuid.equals(GattAttributes.UUID_SUN)) {
                addHistory(sunHistory, value);
            } else if (uuid.equals(GattAttributes.UUID_MOISTURE)) {
                addHistory(moistureHistory, value);
            } else if (uuid.equals(GattAttributes.UUID_LIGHT)) {
                addHistory(lightHistory, value);
            }

            HashMap<String, ArrayList<Float>> historyData = new HashMap<>();
            historyData.put("N", nHistory);
            historyData.put("P", pHistory);
            historyData.put("K", kHistory);
            historyData.put("pH", phHistory);
            historyData.put("Humidity", humidityHistory);
            historyData.put("Sun", sunHistory);
            historyData.put("Moisture", moistureHistory);
            historyData.put("Light", lightHistory);
            livePlotView.setData(historyData);
        });
    }

    private void addHistory(ArrayList<Float> history, float value) {
        history.add(value);
        if (history.size() > MAX_DATA_POINTS) {
            history.remove(0);
        }
    }

    private void sendImage(Uri imageUri) {
        if (bluetoothGatt == null || imageUri == null) {
            Log.e(TAG, "Bluetooth not connected or image URI is null.");
            runOnUiThread(() -> Toast.makeText(MainActivity.this, "Failed to send image: Bluetooth not connected.", Toast.LENGTH_SHORT).show());
            return;
        }

        BluetoothGattService service = bluetoothGatt.getService(GattAttributes.DEMETER_SERVICE_UUID);
        if (service == null) {
            Log.e(TAG, "Demeter service not found");
            runOnUiThread(() -> Toast.makeText(MainActivity.this, "Failed to send image: Service not found.", Toast.LENGTH_SHORT).show());
            return;
        }

        final BluetoothGattCharacteristic imageChar = service.getCharacteristic(GattAttributes.UUID_IMAGE);
        if (imageChar == null) {
            Log.e(TAG, "Image characteristic not found");
            runOnUiThread(() -> Toast.makeText(MainActivity.this, "Failed to send image: Characteristic not found.", Toast.LENGTH_SHORT).show());
            return;
        }

        runOnUiThread(() -> Toast.makeText(MainActivity.this, "Sending image...", Toast.LENGTH_SHORT).show());

        new Thread(() -> {
            try {
                InputStream inputStream = getContentResolver().openInputStream(imageUri);
                Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
                Bitmap resizedBitmap = Bitmap.createScaledBitmap(bitmap, 256, 256, true);

                ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
                resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 85, byteStream);
                byte[] imageData = byteStream.toByteArray();
                Log.d(TAG, "Downsampled image size: " + imageData.length + " bytes");

                int chunkSize = 512;
                for (int i = 0; i < imageData.length; i += chunkSize) {
                    int end = Math.min(imageData.length, i + chunkSize);
                    byte[] chunk = Arrays.copyOfRange(imageData, i, end);
                    Log.d(TAG, "Queuing chunk " + (i / chunkSize + 1) + ", size: " + chunk.length);
                    writeCharacteristicToQueue(imageChar, chunk);
                }

                // Send End-Of-Transmission signal
                Log.d(TAG, "Queuing EOT signal.");
                writeCharacteristicToQueue(imageChar, "EOT".getBytes());

                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Image sent successfully.", Toast.LENGTH_SHORT).show());

            } catch (IOException e) {
                Log.e(TAG, "Error reading image file", e);
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Failed to send image: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    private void updateLedIndicator(BleConnectionStatus status) {
        int drawableId;
        switch (status) {
            case SCANNING:
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
        runOnUiThread(() -> ledIndicator.setImageDrawable(ContextCompat.getDrawable(MainActivity.this, drawableId)));
    }

    private void fetchAugmentedImage() {
        if (bluetoothGatt == null) {
            Toast.makeText(this, "Bluetooth not connected", Toast.LENGTH_SHORT).show();
            return;
        }
        augmentedImageStream.reset();
        imageReadOffset = 0;
        augmentedImageView.setVisibility(View.GONE);
        Toast.makeText(this, "Fetching augmented image...", Toast.LENGTH_SHORT).show();
        requestAugmentedImageChunk();
    }

    private void requestAugmentedImageChunk() {
        if (bluetoothGatt == null) return;
        BluetoothGattService service = bluetoothGatt.getService(GattAttributes.DEMETER_SERVICE_UUID);
        if (service == null) {
            Log.e(TAG, "Demeter service not found for augmented image request");
            return;
        }
        BluetoothGattCharacteristic requestChar = service.getCharacteristic(GattAttributes.UUID_IMAGE_REQUEST);
        if (requestChar == null) {
            Log.e(TAG, "Image Request characteristic not found");
            return;
        }
        byte[] value = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(imageReadOffset).array();
        writeCharacteristicToQueue(requestChar, value);
    }

    private void readAugmentedImageChunk() {
        if (bluetoothGatt == null) return;
        BluetoothGattService service = bluetoothGatt.getService(GattAttributes.DEMETER_SERVICE_UUID);
        if (service == null) {
            Log.e(TAG, "Demeter service not found for augmented image read");
            return;
        }
        BluetoothGattCharacteristic imageChar = service.getCharacteristic(GattAttributes.UUID_AUGMENTED_IMAGE);
        if (imageChar == null) {
            Log.e(TAG, "Augmented Image characteristic not found");
            return;
        }
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        bluetoothGatt.readCharacteristic(imageChar);
    }
}
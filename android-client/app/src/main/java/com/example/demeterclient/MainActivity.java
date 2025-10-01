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
import android.os.Handler;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Collections;
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
    private TextView nValue, pValue, kValue, phValue, humidityValue, sunValue, moistureValue, lightValue;
    private Button getSuggestionButton;
    private TextView suggestionTextView;
    private EditText numSuggestionsEditText;
    private Spinner plantTypeSpinner;
    private Button takePictureButton;

    private static final int REQUEST_IMAGE_CAPTURE = 2;
    private static final int REQUEST_CAMERA_PERMISSION = 3;
    private String currentPhotoPath;
    private static final String KEY_PHOTO_PATH = "com.example.demeterclient.photo_path";

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
        nValue = findViewById(R.id.n_value);
        pValue = findViewById(R.id.p_value);
        kValue = findViewById(R.id.k_value);
        phValue = findViewById(R.id.ph_value);
        humidityValue = findViewById(R.id.humidity_value);
        sunValue = findViewById(R.id.sun_value);
        moistureValue = findViewById(R.id.moisture_value);
        lightValue = findViewById(R.id.light_value);
        getSuggestionButton = findViewById(R.id.get_suggestion_button);
        suggestionTextView = findViewById(R.id.suggestion_text_view);
        numSuggestionsEditText = findViewById(R.id.num_suggestions_edit_text);
        plantTypeSpinner = findViewById(R.id.plant_type_spinner);
        takePictureButton = findViewById(R.id.take_picture_button);

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
            // Inform the user that Bluetooth is not enabled
            statusTextView.setText("Bluetooth is not enabled");
            return;
        }

        // Request permissions
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
        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == RESULT_OK) {
            if (currentPhotoPath != null) {
                sendImageInChunks(currentPhotoPath);
                Intent intent = new Intent(this, PreviewActivity.class);
                intent.putExtra("image_uri", Uri.fromFile(new File(currentPhotoPath)).toString());
                startActivity(intent);
            }
        }
    }

    private void sendImageInChunks(String photoPath) {
        if (bluetoothGatt == null) {
            Log.e(TAG, "BluetoothGatt not initialized");
            return;
        }
        BluetoothGattService service = bluetoothGatt.getService(GattAttributes.DEMETER_SERVICE_UUID);
        if (service == null) {
            Log.e(TAG, "Demeter service not found");
            return;
        }
        BluetoothGattCharacteristic imageChar = service.getCharacteristic(GattAttributes.UUID_IMAGE);
        if (imageChar == null) {
            Log.e(TAG, "Image characteristic not found");
            return;
        }

        // 1. Load and resize bitmap
        Bitmap originalBitmap = BitmapFactory.decodeFile(photoPath);
        if (originalBitmap == null) {
            Log.e(TAG, "Failed to decode image file");
            return;
        }
        Bitmap resizedBitmap = Bitmap.createScaledBitmap(originalBitmap, 128, 128, true);

        // 2. Convert to byte array
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream); // Compress as JPEG
        byte[] imageBytes = stream.toByteArray();
        Log.d(TAG, "Resized image to " + imageBytes.length + " bytes.");

        // 3. Signal start of transfer
        writeCharacteristicToQueue(imageChar, new byte[0]);

        // 4. Send in chunks
        int chunkSize = 500;
        for (int i = 0; i < imageBytes.length; i += chunkSize) {
            int end = Math.min(i + chunkSize, imageBytes.length);
            byte[] chunk = new byte[end - i];
            System.arraycopy(imageBytes, i, chunk, 0, chunk.length);
            writeCharacteristicToQueue(imageChar, chunk);
        }
        Log.d(TAG, "Queued all image chunks for sending.");
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
            // Stops scanning after a pre-defined scan period.
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

        // In your gattCallback:
        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            super.onDescriptorWrite(gatt, descriptor, status);
            UUID characteristicUuid = descriptor.getCharacteristic().getUuid(); // Get which characteristic this was for

            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "Descriptor write successful for: " + characteristicUuid);
            } else {
                Log.e(TAG, "Descriptor write failed for: " + characteristicUuid + " with status: " + status);
            }

            // Check if this descriptor write was part of our sequential subscription
            if (characteristicsToSubscribe != null && currentSubscriptionIndex < characteristicsToSubscribe.size()) {
                // Check if the UUID matches the one we were trying to subscribe to,
                // though simply incrementing and trying next is often sufficient
                // if (characteristicUuid.equals(characteristicsToSubscribe.get(currentSubscriptionIndex))) {
                currentSubscriptionIndex++;
                subscribeNextCharacteristic(gatt);
                // }
            }
        }
        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "Successfully wrote to characteristic " + characteristic.getUuid());
                if (characteristic.getUuid().equals(GattAttributes.UUID_SUGGEST)) {
                    // This is a special case for the LLM chunk reading logic
                    readLlmChunk();
                }
            } else {
                Log.e(TAG, "Failed to write to characteristic " + characteristic.getUuid() + " status: " + status);
                writeQueue.clear(); // Clear the queue on failure
            }
            // Process next write in the queue
            isWriting = false;
            processWriteQueue();
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS && characteristic.getUuid().equals(GattAttributes.UUID_LLM)) {
                byte[] data = characteristic.getValue();
                if (data != null && data.length > 0) {
                    suggestionBuilder.append(new String(data));
                    if (data.length < 225) {
                        // End of message
                        runOnUiThread(() -> suggestionTextView.setText("Suggestion: " + suggestionBuilder.toString()));
                    } else {
                        // More data to read
                        llmOffset += data.length;
                        requestLlmChunk();
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
                    if (llmStatus == 1) { // Generating
                        suggestionTextView.setText("Suggestion: Generating...");
                    } else if (llmStatus == 2) { // Ready
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

        // Clear any pending writes
        writeQueue.clear();
        isWriting = false;

        // --- Queue Number of Suggestions ---
        BluetoothGattCharacteristic numSuggestionsChar = service.getCharacteristic(GattAttributes.UUID_NUM_SUGGESTIONS);
        if (numSuggestionsChar != null) {
            String numSuggestionsStr = numSuggestionsEditText.getText().toString();
            int numSuggestions = 1; // Default value
            if (!numSuggestionsStr.isEmpty()) {
                try {
                    numSuggestions = Integer.parseInt(numSuggestionsStr);
                } catch (NumberFormatException e) {
                    Log.e(TAG, "Invalid number format for suggestions", e);
                    // Optionally show a toast to the user
                }
            }
            byte[] numValue = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(numSuggestions).array();
            writeCharacteristicToQueue(numSuggestionsChar, numValue);
        } else {
            Log.e(TAG, "Num Suggestions characteristic not found");
        }

        // --- Queue Plant Type ---
        BluetoothGattCharacteristic plantTypeChar = service.getCharacteristic(GattAttributes.UUID_PLANT_TYPE);
        if (plantTypeChar != null) {
            int plantTypeIndex = plantTypeSpinner.getSelectedItemPosition();
            byte[] plantTypeValue = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(plantTypeIndex).array();
            writeCharacteristicToQueue(plantTypeChar, plantTypeValue);
        } else {
            Log.e(TAG, "Plant Type characteristic not found");
        }

        // --- Queue Trigger Suggestion ---
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
        suggestionBuilder.setLength(0); // Clear previous suggestion
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
            // The actual next subscription will be triggered by onDescriptorWrite
        } else {
            Log.d(TAG, "All characteristics subscribed.");
            // All subscriptions are done
        }
    }

    private void setCharacteristicNotificationInternal(BluetoothGatt gatt, UUID characteristicUuid, boolean enabled) {
        BluetoothGattService service = gatt.getService(GattAttributes.DEMETER_SERVICE_UUID);
        if (service == null) {
            Log.e(TAG, "Service not found for " + characteristicUuid);
            // Potentially move to the next one or handle error
            currentSubscriptionIndex++;
            subscribeNextCharacteristic(gatt);
            return;
        }
        BluetoothGattCharacteristic characteristic = service.getCharacteristic(characteristicUuid);
        if (characteristic == null) {
            Log.e(TAG, "Characteristic not found: " + characteristicUuid);
            // Potentially move to the next one or handle error
            currentSubscriptionIndex++;
            subscribeNextCharacteristic(gatt);
            return;
        }

        // This call is local to the Android device
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            // Handle permission missing - crucial for Android 12+
            Log.e(TAG, "BLUETOOTH_CONNECT permission missing for setCharacteristicNotificationInternal");
            currentSubscriptionIndex++; // Or stop the process
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
        // Now write to the CCCD descriptor to enable notifications on the peripheral
        UUID cccdUuid = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
        BluetoothGattDescriptor descriptor = characteristic.getDescriptor(cccdUuid);
        if (descriptor != null) {
            byte[] value = enabled ? BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE : BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE;
            descriptor.setValue(value);
            // The onDescriptorWrite callback will trigger the next subscription
            if (!gatt.writeDescriptor(descriptor)) {
                Log.e(TAG, "writeDescriptor failed for " + characteristicUuid);
                // Even if writeDescriptor returns false, wait for onDescriptorWrite, it might still come with an error status
                // Or, decide to move to the next one if it consistently fails to even queue
                // For simplicity here, we assume onDescriptorWrite will be called.
                // A more robust handler might retry or skip.
            }
        } else {
            Log.w(TAG, "CCCD descriptor not found for " + characteristicUuid);
            // This characteristic might not support notifications, or something is wrong
            // Move to the next one
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
                nValue.setText(String.format("N: %.2f", value));
            } else if (uuid.equals(GattAttributes.UUID_P)) {
                pValue.setText(String.format("P: %.2f", value));
            } else if (uuid.equals(GattAttributes.UUID_K)) {
                kValue.setText(String.format("K: %.2f", value));
            } else if (uuid.equals(GattAttributes.UUID_PH)) {
                phValue.setText(String.format("pH: %.2f", value));
            } else if (uuid.equals(GattAttributes.UUID_HUMID)) {
                humidityValue.setText(String.format("Humidity: %.2f", value));
            } else if (uuid.equals(GattAttributes.UUID_SUN)) {
                sunValue.setText(String.format("Sun: %.2f", value));
            } else if (uuid.equals(GattAttributes.UUID_MOISTURE)) {
                moistureValue.setText(String.format("Moisture: %.2f", value));
            } else if (uuid.equals(GattAttributes.UUID_LIGHT)) {
                lightValue.setText(String.format("Light: %.2f", value));
            }
        });
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
}
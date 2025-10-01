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
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final long SCAN_PERIOD = 10000; // Stops scanning after 10 seconds.

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner bluetoothLeScanner;
    private boolean scanning;
    private Handler handler;
    private BluetoothGatt bluetoothGatt;

    private TextView statusTextView;
    private TextView nValue, pValue, kValue, phValue, humidityValue, sunValue, moistureValue, lightValue;
    private Button getSuggestionButton;
    private TextView suggestionTextView;

    private StringBuilder suggestionBuilder = new StringBuilder();
    private int llmOffset = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

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

        getSuggestionButton.setOnClickListener(v -> {
            requestSuggestion();
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
        }
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
        } else {
            scanning = false;
            bluetoothLeScanner.stopScan(leScanCallback);
            statusTextView.setText("Scan stopped.");
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
        bluetoothGatt = device.connectGatt(this, false, gattCallback);
    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothGatt.STATE_CONNECTED) {
                Log.d(TAG, "Connected to GATT server.");
                runOnUiThread(() -> statusTextView.setText("Connected"));
                if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    return;
                }
                gatt.discoverServices();
            } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                Log.d(TAG, "Disconnected from GATT server.");
                runOnUiThread(() -> statusTextView.setText("Disconnected"));
            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS && characteristic.getUuid().equals(GattAttributes.UUID_SUGGEST)) {
                readLlmChunk();
            }
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
        if (service == null) return;
        BluetoothGattCharacteristic plantTypeChar = service.getCharacteristic(GattAttributes.UUID_PLANT_TYPE);
        if (plantTypeChar == null) return;

        byte[] value = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(0).array();
        plantTypeChar.setValue(value);
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        bluetoothGatt.writeCharacteristic(plantTypeChar);
        runOnUiThread(() -> suggestionTextView.setText("Suggestion: Requesting..."));
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

    private void subscribeToCharacteristics(BluetoothGatt gatt) {
        setCharacteristicNotification(gatt, GattAttributes.UUID_N, true);
        setCharacteristicNotification(gatt, GattAttributes.UUID_P, true);
        setCharacteristicNotification(gatt, GattAttributes.UUID_K, true);
        setCharacteristicNotification(gatt, GattAttributes.UUID_PH, true);
        setCharacteristicNotification(gatt, GattAttributes.UUID_HUMID, true);
        setCharacteristicNotification(gatt, GattAttributes.UUID_SUN, true);
        setCharacteristicNotification(gatt, GattAttributes.UUID_MOISTURE, true);
        setCharacteristicNotification(gatt, GattAttributes.UUID_LIGHT, true);
        setCharacteristicNotification(gatt, GattAttributes.UUID_LLM_STATUS, true);
    }

    private void setCharacteristicNotification(BluetoothGatt gatt, UUID characteristicUuid, boolean enabled) {
        BluetoothGattService service = gatt.getService(GattAttributes.DEMETER_SERVICE_UUID);
        if (service == null) {
            Log.e(TAG, "Service not found for " + characteristicUuid);
            return;
        }
        android.bluetooth.BluetoothGattCharacteristic characteristic = service.getCharacteristic(characteristicUuid);
        if (characteristic == null) {
            Log.e(TAG, "Characteristic not found: " + characteristicUuid);
            return;
        }
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        gatt.setCharacteristicNotification(characteristic, enabled);

        // This is for BLE notifications to work.
        UUID cccdUuid = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
        BluetoothGattDescriptor descriptor = characteristic.getDescriptor(cccdUuid);
        if (descriptor != null) {
            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            gatt.writeDescriptor(descriptor);
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
}
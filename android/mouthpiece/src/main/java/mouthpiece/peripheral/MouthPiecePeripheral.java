/*
* Copyright 2015 Lyo Kato (lyo.kato@gmail.com)
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
*     http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/

package mouthpiece.peripheral;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.ParcelUuid;
import android.util.Log;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class MouthPiecePeripheral {

    public static final String TAG = MouthPiecePeripheral.class.getSimpleName();

    public enum Event {
        READ,
        WRITE
    }

    public static interface Listener {
        void onAdvertiseSuccess(AdvertiseSettings settingsInEffect);
        void onAdvertiseFailure(int errorCode); 
        void onConnectionStateChange(BluetoothDevice device, int status, int newState);
    }

    private Map<UUID, MouthPieceService> services;
    private BluetoothGattServer rawServer;
    private Context context;
    private boolean running;
    private BluetoothLeAdvertiser advertiser;
    private AdvertiseCallback advertiseCallback;
    private Listener listener;

    private BluetoothManager manager;
    private BluetoothAdapter adapter;

    private int advertiseMode = AdvertiseSettings.ADVERTISE_MODE_BALANCED;
    private int advertiseTxPower = AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM;
    private boolean includeTxPower = false;

    public static MouthPiecePeripheral build(Activity activity, MouthPieceService service) {
        return new MouthPiecePeripheral.Builder(activity).service(service).build();
    }

    MouthPiecePeripheral(Context context, Listener listener, Map<UUID, MouthPieceService> services) {
        this.listener = listener;
        this.running = false;
        this.services = services;
        this.context = context;
        this.manager =
                (BluetoothManager)context.getSystemService(Context.BLUETOOTH_SERVICE);
        if (this.manager != null) {
            this.adapter = manager.getAdapter();
        }
    }

    public void setAdvertiseMode(int mode) {
        this.advertiseMode = mode;
    }

    public void setAdvertiseTxPower(int power) {
        this.advertiseTxPower = power;
    }

    public void setIncludeTxPower(boolean include) {
        this.includeTxPower = include;
    }

    public void updateValue(String serviceUUIDString, String chUUIDString, byte[] value) {

        if (rawServer == null)
            return;

        UUID serviceUUID = UUID.fromString(serviceUUIDString);
        UUID chUUID = UUID.fromString(chUUIDString);
        BluetoothGattService rawService = rawServer.getService(serviceUUID);
        if (rawService == null)
            return;
        BluetoothGattCharacteristic rawCh = rawService.getCharacteristic(chUUID);
        if (rawCh == null)
            return;

        MouthPieceService service = services.get(serviceUUID);
        if (service == null)
            return;

        service.updateValue(rawServer, rawCh, value);
    }

    public boolean systemSupported() {
        if (!context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            return false;
        }

        if (manager == null) {
            return false;
        }
        if (adapter == null) {
            return false;
        }

        if (!adapter.isMultipleAdvertisementSupported()) {
            return false;
        }
        return true;
    }

    public boolean isEnabled() {
        return (adapter != null && adapter.isEnabled());
    }

    public boolean start() {

        if (running) {
            // XXX: should throw exception?
            return false;
        }

        if (!(systemSupported() && isEnabled())) {
            return false;
        }

        rawServer = manager.openGattServer(context, createServerCallback());
        if (rawServer == null) {
            return false;
        }

        for (Map.Entry<UUID, MouthPieceService> e : services.entrySet()) {
            rawServer.addService(e.getValue().createRawService());
        }

        advertiser = adapter.getBluetoothLeAdvertiser();
        if (advertiser == null) {
            return false;
        }

        advertiseCallback = createAdvertiseCallback();
        advertiser.startAdvertising(
                createAdvertiseSettings(), 
                createAdvertiseData(), 
                advertiseCallback);

        running = true;
        return true;
    }

    private AdvertiseData createAdvertiseData() {
        AdvertiseData.Builder builder = new AdvertiseData.Builder();
        builder.setIncludeTxPowerLevel(this.includeTxPower);
        for (UUID uuid : services.keySet()) {
            builder.addServiceUuid(new ParcelUuid(uuid));
        }
        return builder.build();
    }

    private AdvertiseSettings createAdvertiseSettings() {
        AdvertiseSettings.Builder builder = new AdvertiseSettings.Builder();
        builder.setConnectable(true);
        builder.setTxPowerLevel(this.advertiseTxPower);
        builder.setAdvertiseMode(this.advertiseMode);
        return builder.build();
    }

    public boolean isRunning() {
        return running;
    }

    public void stop() {
        if (!running)
            return;

        if (rawServer != null) {
            rawServer.clearServices();
            rawServer.close();
            rawServer = null;
        }

        if (advertiser != null) {
            advertiser.stopAdvertising(advertiseCallback);
        }
        running = false;
    }

    private AdvertiseCallback createAdvertiseCallback() {
        return new AdvertiseCallback() {
            @Override
            public void onStartSuccess(AdvertiseSettings settingsInEffect) {
                if (listener != null) {
                    listener.onAdvertiseSuccess(settingsInEffect);
                }
            }
            @Override
            public void onStartFailure(int errorCode) {
                // need to stop and clear service here?
                if (listener != null) {
                    listener.onAdvertiseFailure(errorCode);
                }
            }
        };
    }

    private BluetoothGattServerCallback createServerCallback() {

        return new BluetoothGattServerCallback() {

            @Override
            public void onServiceAdded(int status, BluetoothGattService service) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    Log.d(TAG, "BLE Service Added");
                } else {
                    Log.d(TAG, "BLE Service Not Added");
                }
            }

            /*
            @Override
            public void onDescriptorReadRequest(BluetoothDevice device, int requestId, 
                    int offset, BluetoothGattDescriptor descriptor) {

            }

            @Override
            public void onNotificationSent(BluetoothDevice device, int status) {

            }

            @Override
            public void onExecuteWrite(BluetoothDevice device, int requestId, boolean execute) {
                if (execute) {
                    // Written
                } else {
                    // Cancelled
                }
            }
            */

            @Override
            public void onDescriptorWriteRequest(BluetoothDevice device, int requestId, BluetoothGattDescriptor descriptor,
                    boolean preparedWrite, boolean responseNeeded, int offset, byte[] value) {

                Log.d(TAG, "onDescriptorWriteRequest");

                if (descriptor.getUuid().equals(UUID.fromString("00002902-0000-1000-8000-00805F9B34FB"))
                            && Arrays.equals(value, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)) {

                    UUID serviceUUID = descriptor.getCharacteristic().getService().getUuid();
                    UUID chUUID = descriptor.getCharacteristic().getUuid();
                    Log.d(TAG, "onDescriptorWriteRequest:" + chUUID.toString());
                    MouthPieceService service = services.get(serviceUUID);
                    service.rememberDeviceForNotification(device, chUUID);
                }

                rawServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value);        
            }

            @Override
            public void onConnectionStateChange(BluetoothDevice device, int status, int newState) {

                Log.d(TAG, "BLE connection state changed");

                if (status == BluetoothProfile.STATE_DISCONNECTED) {
                    for (Map.Entry<UUID, MouthPieceService> e : services.entrySet()) {
                        MouthPieceService service = e.getValue();
                        service.forgetDeviceForNotification(device);
                    }
                }

                if (listener != null) {
                    listener.onConnectionStateChange(device, status, newState);
                }
            }

            @Override
            public void onCharacteristicReadRequest(BluetoothDevice device,
                    int requestId, int offset, BluetoothGattCharacteristic characteristic) {

                Log.d(TAG, "onCharacteristicReadRequest");
                ReadRequest req = new ReadRequest(device, requestId, offset, characteristic);
                ReadResponse res = new ReadResponse(req);

                UUID serviceUUID = characteristic.getService().getUuid();
                MouthPieceService service = services.get(serviceUUID);

                    if (service.canHandle(req.getUuid(), MouthPiecePeripheral.Event.READ)) {
                        service.dispatchReadRequest(req, res);
                        //break;
                    }

                res.finishOn(rawServer);
            }

            @Override
            public void onCharacteristicWriteRequest(BluetoothDevice device,
                    int requestId, BluetoothGattCharacteristic characteristic,
                    boolean preparedWrite, boolean responseNeeded, int offset, byte[] value) {

                Log.d(TAG, "onCharacteristicWriteRequest");
                WriteRequest req = new WriteRequest(device, requestId, characteristic,
                        preparedWrite, responseNeeded, offset, value); 
                WriteResponse res = new WriteResponse(req);

                UUID serviceUUID = characteristic.getService().getUuid();
                MouthPieceService service = services.get(serviceUUID);

                if (service.canHandle(req.getUuid(), MouthPiecePeripheral.Event.WRITE)) {
                    service.dispatchWriteRequest(req, res, rawServer);
                }

                res.finishOn(rawServer);
            }
        };
    }

    public static class Builder {

        private Map<UUID, MouthPieceService> services;
        private Context context;
        private MouthPiecePeripheral.Listener listener;

        public Builder(Context context, MouthPiecePeripheral.Listener listener) {
            this.listener = listener;
            this.services = new HashMap<UUID, MouthPieceService>();
            this.context = context;
        }

        public Builder(Context context) {
            this(context, null);
        }

        public Builder service(MouthPieceService service) {
            service.analyzeCharacteristicsDefinition();
            services.put(service.getUuid(), service);
            return this;
        }

        public MouthPiecePeripheral build() {
            return new MouthPiecePeripheral(this.context, this.listener, this.services);
        }
    }
}

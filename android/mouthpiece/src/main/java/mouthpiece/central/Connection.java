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

package mouthpiece.central;

import android.app.Activity;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.util.Log;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;

import mouthpiece.central.exception.InvalidStateException;

public class Connection {

    private static final String TAG = Connection.class.getSimpleName();

    private static final int STATE_READY      = 0;
    private static final int STATE_CONNECTING = 1;
    private static final int STATE_CONNECTED  = 2;
    private static final int STATE_CLOSING    = 3;
    private static final int STATE_ERROR      = 4;

    public static final int REASON_NORMAL               = 0;
    public static final int REASON_REMOTE               = 1;
    public static final int REASON_CONDITION_MISMATCHED = 2;

    private static final String CONFIG_UUID = "00002902-0000-1000-8000-00805f9b34fb";

    public interface Listener {
        void onCharacteristicChanged(Characteristic characteristic);
        void onCharacteristicRead(boolean success, Characteristic characteristic);
        void onCharacteristicWrite(boolean success, Characteristic characteristic);
        void onConnected();
        void onDisconnected(int reason, String errorMessage);
    }

    private Map<String, BluetoothGattCharacteristic> observableCharacteristicHolder;
    private Map<String, BluetoothGattCharacteristic> writableCharacteristicHolder;
    private Map<String, BluetoothGattCharacteristic> sendableCharacteristicHolder;
    private Map<String, BluetoothGattCharacteristic> readableCharacteristicHolder;

    private Activity activity;
    private Destination destination;
    private Listener listener;
    private BluetoothGatt bluetoothGatt;

    private int state = STATE_READY;
    private int errorReason = 2;
    private String errorMessage = "";

    private boolean initialInteractionDone = false;
    private Queue<BluetoothGattDescriptor> initialDescriptorWriteQueue;

    public Connection(Activity activity, Destination destination, Listener listener) {
        this.activity = activity;
        this.destination = destination;
        this.listener = listener;
        clear();
    }

    private void clear() {
        this.observableCharacteristicHolder = new HashMap<String, BluetoothGattCharacteristic>();
        this.writableCharacteristicHolder = new HashMap<String, BluetoothGattCharacteristic>();
        this.sendableCharacteristicHolder = new HashMap<String, BluetoothGattCharacteristic>();
        this.readableCharacteristicHolder = new HashMap<String, BluetoothGattCharacteristic>();
        this.initialDescriptorWriteQueue = new LinkedList<BluetoothGattDescriptor>();
        this.initialInteractionDone = false;
    }

    public void connect(BluetoothDevice device) {
        if (state != STATE_READY) {
            throw new InvalidStateException("It's not ready to establish new connection.");
        }
        state = STATE_CONNECTING;
        this.bluetoothGatt = device.connectGatt(activity.getApplicationContext(), false, mGattCallback);
    }

    private void closeByError(int reason, String msg) {
        Log.d(TAG, msg);
        state = STATE_ERROR;
        errorReason = reason;
        errorMessage = msg;
        if (bluetoothGatt != null) {
            bluetoothGatt.close();
            bluetoothGatt = null;
        }
        activity = null;
        clear();
    }

    public void close() {
        if (state == STATE_CONNECTED || state == STATE_CONNECTING) {
            bluetoothGatt.close();
            // when mBluetoothGatt.close() called, ConnectionStateChanged listener won't be called.
            if (listener != null) {
                listener.onDisconnected(REASON_NORMAL, "");
            }
        }
        state = STATE_CLOSING;
        bluetoothGatt = null;
        activity = null;
        clear();
    }

    public boolean isConnected() {
        return (state == STATE_CONNECTED);
    }

    public void write(String uuid, byte[] value) {

        if (!isConnected()) {
            throw new InvalidStateException("couldn't write value, because connection is not available.");
        }
        BluetoothGattCharacteristic ch = writableCharacteristicHolder.get(uuid);
        if (ch == null) {
            throw new InvalidStateException("characteristic not found: " + uuid);
        }

        ch.setValue(value);
        bluetoothGatt.writeCharacteristic(ch);
    }

    public void send(String uuid, byte[] value) {

        if (!isConnected()) {
            throw new InvalidStateException("couldn't write value, because connection is not available.");
        }
        BluetoothGattCharacteristic ch = sendableCharacteristicHolder.get(uuid);
        if (ch == null) {
            throw new InvalidStateException("characteristic not found: " + uuid);
        }

        ch.setValue(value);
        bluetoothGatt.writeCharacteristic(ch);
    }

    public void read(String uuid) {
        if (!isConnected()) {
            throw new InvalidStateException("couldn't write value, because connection is not available.");
        }

        BluetoothGattCharacteristic ch = readableCharacteristicHolder.get(uuid);
        if (ch == null) {
            throw new InvalidStateException("characteristic not found: " + uuid);
        }

        bluetoothGatt.readCharacteristic(ch);
    }

    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            Log.d(TAG, "gatt connection state changed.");
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                if (state == STATE_CONNECTING) {
                    gatt.discoverServices();
                }

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                bluetoothGatt = null;
                int reason = REASON_REMOTE;
                String message = "";
                if (state == STATE_CLOSING) {
                    // TODO not come here?
                    reason = REASON_NORMAL;
                } else if (state == STATE_ERROR) {
                    reason = errorReason;
                    message = errorMessage;
                } else {
                    bluetoothGatt = null;
                    activity = null;
                }

                if (listener != null) {
                    listener.onDisconnected(reason, message);
                }

            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                BluetoothGattService service = gatt.getService(UUID.fromString(destination.getService()));
                if (service != null) {

                    /* check observable characteristics */
                    List<String> observableCharacteristicUUIDs = destination.getObservableCharacteristics();
                    for (String chUUID : observableCharacteristicUUIDs) {
                        BluetoothGattCharacteristic ch = observeCharacteristic(service, chUUID);
                        if (ch == null) {
                            // TODO detailed message
                            closeByError(REASON_CONDITION_MISMATCHED, "failed to observer characteristic:" + chUUID);
                            return;
                        }
                        observableCharacteristicHolder.put(chUUID, ch);
                    }

                    /* check readable characteristics */
                    List<String> readableCharacteristicUUIDs = destination.getReadableCharacteristics();
                    for (String chUUID : readableCharacteristicUUIDs) {
                        BluetoothGattCharacteristic ch = service.getCharacteristic(UUID.fromString(chUUID));
                        if (ch == null) {
                            closeByError(REASON_CONDITION_MISMATCHED, "characteristic not found:" + chUUID);
                            return;
                        }
                        int properties = ch.getProperties();
                        if ((properties & BluetoothGattCharacteristic.PROPERTY_READ) != BluetoothGattCharacteristic.PROPERTY_READ) {
                            Log.w(TAG, "this characteristic has no READ property");
                            closeByError(REASON_CONDITION_MISMATCHED, "characteristic has not property READ:" + chUUID);
                            return;
                        }
                        readableCharacteristicHolder.put(chUUID, ch);
                    }

                    /* check writable characteristics */
                    List<String> writableCharacteristicUUIDs = destination.getWritableCharacteristics();
                    for (String chUUID : writableCharacteristicUUIDs) {
                        BluetoothGattCharacteristic ch = service.getCharacteristic(UUID.fromString(chUUID));
                        if (ch == null) {
                            closeByError(REASON_CONDITION_MISMATCHED, "characteristic not found:" + chUUID);
                            return;
                        }
                        int properties = ch.getProperties();
                        if ((properties & BluetoothGattCharacteristic.PROPERTY_WRITE) != BluetoothGattCharacteristic.PROPERTY_WRITE) {
                            Log.w(TAG, "this characteristic has no WRITE property");
                            closeByError(REASON_CONDITION_MISMATCHED, "characteristic has not property WRITE:" + chUUID);
                            return;
                        }
                        writableCharacteristicHolder.put(chUUID, ch);
                    }

                    List<String> sendableCharacteristicUUIDs = destination.getSendableCharacteristics();
                    for (String chUUID : sendableCharacteristicUUIDs) {
                        BluetoothGattCharacteristic ch = service.getCharacteristic(UUID.fromString(chUUID));
                        if (ch == null) {
                            closeByError(REASON_CONDITION_MISMATCHED, "characteristic not found:" + chUUID);
                            return;
                        }
                        int properties = ch.getProperties();
                        if ((properties & BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) {
                            Log.w(TAG, "this characteristic has no WRITE_NO_RESPONSE property");
                            closeByError(REASON_CONDITION_MISMATCHED, "characteristic has not property WRITE_NO_RESPONSE:" + chUUID);
                            return;
                        }
                        sendableCharacteristicHolder.put(chUUID, ch);
                    }

                    state = STATE_CONNECTED;
                    if (listener != null) {
                        listener.onConnected();
                    }

                    writeNextDescriptor();
                } else {
                    Log.w(TAG, "service not found, start to disconnect");
                    closeByError(REASON_CONDITION_MISMATCHED, "service not found:" + destination.getService());
                }
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt,
                                            BluetoothGattCharacteristic characteristic) {
            if (listener != null) {
                listener.onCharacteristicChanged(new Characteristic(characteristic));
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt,
                                         BluetoothGattCharacteristic characteristic,
                                         int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                listener.onCharacteristicRead(true, new Characteristic(characteristic));
            } else {
                listener.onCharacteristicRead(false, new Characteristic(characteristic));
            }
            /*if (!initialInteractionDone) {
                readNextCharacteristic();
            }*/
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt,
                                          BluetoothGattCharacteristic characteristic,
                                          int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                listener.onCharacteristicWrite(true, new Characteristic(characteristic));
            } else {
                listener.onCharacteristicWrite(false, new Characteristic(characteristic));
            }
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt,
                                      BluetoothGattDescriptor descriptor,
                                      int status) {
            if (!initialInteractionDone) {
                writeNextDescriptor();
            }
        }
    };

    /*
    private void readNextCharacteristic() {
        if (!isConnected()) {
            Log.i(TAG, "connection closed. so cancel initialization");
            return;
        }
        String uuid = initialCharacteristicReadQueue.poll();
        if (uuid != null) {
            BluetoothGattCharacteristic ch = observableCharacteristicHolder.get(uuid);
            if (ch == null) {
                throw new InvalidStateException("characteristic not found: " + uuid);
            }
            bluetoothGatt.readCharacteristic(ch);
        } else {
            initialInteractionDone = true;
        }
    }
    */

    private void writeNextDescriptor() {
        BluetoothGattDescriptor d = initialDescriptorWriteQueue.poll();
        if (d != null) {
            bluetoothGatt.writeDescriptor(d);
        }
        /* else {
            readNextCharacteristic();
        }*/
    }

    private BluetoothGattCharacteristic observeCharacteristic(BluetoothGattService service, String characteristicUUID) {


        BluetoothGattCharacteristic ch = service.getCharacteristic(UUID.fromString(characteristicUUID));
        if (ch == null) {
            Log.w(TAG, "characteristic not found: " + characteristicUUID);
            return null;
        }

        int properties = ch.getProperties();

        if ((properties & BluetoothGattCharacteristic.PROPERTY_NOTIFY) != BluetoothGattCharacteristic.PROPERTY_NOTIFY) {
            Log.w(TAG, "characteristic has no NOTIFY property:" + characteristicUUID);
            return null;
        }

        BluetoothGattDescriptor descriptor = ch.getDescriptor(UUID.fromString(CONFIG_UUID.toUpperCase()));

        if (descriptor == null) {
            Log.w(TAG, "characteristic has no config descriptor: " + characteristicUUID);
            return null;
        }

        boolean registered = bluetoothGatt.setCharacteristicNotification(ch, true);
        if (!registered) {
            Log.w(TAG, "failed to register characteristic notification: " + characteristicUUID);
            return null;
        }

        descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
        initialDescriptorWriteQueue.offer(descriptor);
        //initialCharacteristicReadQueue.offer(characteristicUUID);
        return ch;
    }

}

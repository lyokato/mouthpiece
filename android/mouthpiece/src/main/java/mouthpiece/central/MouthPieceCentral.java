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
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.util.Log;

import mouthpiece.utils.ValueTypeConverter;

public class MouthPieceCentral {

    private static final String TAG = MouthPieceCentral.class.getSimpleName();

    public static final int STATE_IDLE      = 0;
    public static final int STATE_SCANNING  = 1;
    public static final int STATE_CONNECTED = 2;
    public static final int STATE_ERROR     = 3;

    private static final int DEFAULT_SCAN_TIMEOUT_MILLIS  = 1000;
    private static final int DEFAULT_SCAN_INTERVAL_MILLIS = 1000;

    public interface Listener {
        public void onCharacteristicReceived(Characteristic characteristic);
        //public void onCharacteristicRead(boolean success, Characteristic characteristic);
        public void onStateChanged(String serviceUUID, int state);
    }

    private static final int REQUEST_CODE = 15873;

    private BluetoothManager bluetoothManager;
    private BluetoothAdapter bluetoothAdapter;
    private Scanner scanner;
    private Connection connection;

    private Activity activity;
    private Listener listener;

    private Destination destination;
    private int scanTimeoutMillis;
    private int scanIntervalMillis;

    private boolean available = false;

    public MouthPieceCentral(Activity activity, Listener listener) {
        this.activity = activity;
        this.listener = listener;
        this.scanTimeoutMillis = DEFAULT_SCAN_TIMEOUT_MILLIS;
        this.scanIntervalMillis = DEFAULT_SCAN_INTERVAL_MILLIS;
    }

    public MouthPieceCentral(Activity activity, Listener listener, int scanTimeoutMillis, int scanIntervalMillis) {
        this.activity = activity;
        this.listener = listener;
        this.scanTimeoutMillis = scanTimeoutMillis;
        this.scanIntervalMillis = scanIntervalMillis;
    }

    public boolean hasFeature() {
        return activity.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE);
    }

    public void initialize() {
        bluetoothManager = (BluetoothManager)activity.getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            Intent i = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            activity.startActivityForResult(i, REQUEST_CODE);
        } else {
            available = true;
            initScanner();
        }
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_CODE) {
            if ((bluetoothAdapter != null || bluetoothAdapter.isEnabled())) {
                available = true;
                initScanner();
            }
        }
    }

    private void initScanner() {
        scanner = new Scanner(bluetoothAdapter, scanTimeoutMillis, scanIntervalMillis, new Scanner.Listener(){
            @Override
            public void onFound(BluetoothDevice device) {
                initConnection(device);
            }
            @Override
            public void onFailure(int errorCode) {
                // TODO what to do?
            }
        });
    }

    private void initConnection(BluetoothDevice device) {
        connection = new Connection(activity, destination, new Connection.Listener() {
            @Override
            public void onConnected() {
                if (listener != null) {
                    listener.onStateChanged(destination.getService(), STATE_CONNECTED);
                }
            }
            @Override
            public void onDisconnected(int result, String errorMessage) {
                switch (result) {
                    case Connection.REASON_NORMAL:
                        connection = null;
                        if (listener != null) {
                            listener.onStateChanged(destination.getService(), STATE_IDLE);
                        }
                        break;
                    case Connection.REASON_CONDITION_MISMATCHED:
                        connection = null;
                        if (listener != null) {
                            listener.onStateChanged(destination.getService(), STATE_ERROR);
                        }
                        break;
                    case Connection.REASON_REMOTE:
                        scanner.start(destination.getService());
                        connection = null;
                        if (listener != null) {
                            listener.onStateChanged(destination.getService(), STATE_SCANNING);
                        }
                        break;
                    default:
                        // do nothing
                }
            }
            @Override
            public void onCharacteristicChanged(Characteristic characteristic) {
                if (listener != null) {
                    listener.onCharacteristicReceived(characteristic);
                }
            }

            @Override
            public void onCharacteristicRead(boolean success, Characteristic characteristic) {
                if (listener != null && success) {
                    listener.onCharacteristicReceived(characteristic);
                }
                // ERROR handling?
            }
        });
        connection.connect(device);
    }

    public void writeInt(String uuid, int value) {
        write(uuid, ValueTypeConverter.bytesFromInt(value));
    }

    public void write(String uuid, byte[] value) {
        if (connection != null && connection.isConnected()) {
            connection.write(uuid, value);
        }
    }

    public void read(String uuid) {
        if (connection != null && connection.isConnected()) {
            connection.read(uuid);
        }
    }

    private boolean stoppedByLifeCycle = false;

    public void resume() {
        if (!available)
            return;

        if (stoppedByLifeCycle && canStart()) {
            start(destination);
        }
    }

    public void pause() {
        if (!available)
            return;
        boolean stopped = stop();
        if (stopped) {
            stoppedByLifeCycle = true;
        }
    }

    public void destroy() {
        stop();
        activity = null;
        available = false;
    }

    public boolean canStart() {
        return (available && !(scanner.isWorking()) && !(connection != null && connection.isConnected()));
    }

    public void start(Destination destination) {
        Log.d(TAG, "remote controller start:" + destination.getService());
        if (scanner.isWorking()) {
            // FIXME throw exception?
            Log.d(TAG, "scanner is working");
            return;
        }
        if (connection != null && connection.isConnected()) {
            Log.d(TAG, "connection is already established, so return.");
            return;
        }
        this.destination = destination;
        scanner.start(destination.getService());
        if (listener != null) {
            listener.onStateChanged(destination.getService(), STATE_SCANNING);
        }
    }

    public boolean stop() {
        Log.d(TAG, "stop");
        boolean stopped = false;
        if (scanner.isWorking()) {
            Log.d(TAG, "scanner is working, so stop it");
            scanner.stop();
            stopped = true;
        }
        if (connection != null && connection.isConnected()) {
            Log.d(TAG, "connection is working, so close it");
            connection.close();
            connection = null;
            stopped = true;
        }
        return stopped;
    }

}

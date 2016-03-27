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

import android.annotation.TargetApi;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelUuid;
import android.util.Log;

import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

/**
 * Created by lyokato on 15/09/14.
 */
public class Scanner {

    private static final String TAG = Scanner.class.getSimpleName();
    private String serviceUUID;

    private static final int STATE_READY = 0;
    private static final int STATE_SCANNING = 1;
    private static final int STATE_INTERVAL = 2;

    private int state = STATE_READY;

    private Timer timeoutTimer;
    private Timer intervalTimer;
    private int timeoutMillis;
    private int intervalMillis;

    private Handler handler = new Handler(Looper.getMainLooper());

    public interface Listener {
        void onFound(BluetoothDevice device);
        void onFailure(int errorCode);
    }

    private Listener listener;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner bluetoothScanner;

    public Scanner(BluetoothAdapter adapter, int timeoutMillis, int intervalMillis, Listener listener) {
        this.bluetoothAdapter = adapter;
        this.listener = listener;
        this.timeoutMillis = timeoutMillis;
        this.intervalMillis = intervalMillis;
    }

    public boolean isWorking() {
        return (state != STATE_READY);
    }

    public void start(String serviceUUID) {
        this.serviceUUID = serviceUUID;

        if (state == STATE_SCANNING) {
            stopTimeoutTimer();
        } else if (state == STATE_INTERVAL) {
            stopIntervalTimer();
        }

        startScanning();
        state = STATE_SCANNING;
        startTimeoutTimer();
    }

    private void startTimeoutTimer() {
        timeoutTimer = new Timer();
        timeoutTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                stopScanning();
                state = STATE_INTERVAL;
                timeoutTimer = null;
                Log.d(TAG, "scan interval");

                intervalTimer = new Timer();
                intervalTimer.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        intervalTimer = null;
                        Log.d(TAG, "scan again");
                        startScanning();
                        startTimeoutTimer();
                    }
                }, intervalMillis);
            }
        }, timeoutMillis);
    }

    private void stopTimeoutTimer() {
        if (timeoutTimer != null) {
            timeoutTimer.cancel();
            timeoutTimer = null;
        }
    }

    private void stopIntervalTimer() {
        if (intervalTimer != null) {
            intervalTimer.cancel();
            intervalTimer = null;
        }
    }

    public void stop() {
        if (state == STATE_SCANNING) {
            stopTimeoutTimer();
            stopScanning();
        } else if (state == STATE_INTERVAL) {
            stopIntervalTimer();
        }
        state = STATE_READY;
    }

    private void startScanning() {
        state = STATE_SCANNING;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            startScanningInternally();
        } else {
            // TODO support later
            //mBluetoothAdapter.startLeScan(mLeScanCallback);
        }
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void startScanningInternally() {
        Log.d(TAG, "startScan");
        bluetoothScanner = bluetoothAdapter.getBluetoothLeScanner();
        bluetoothScanner.startScan(mScanCallback);
    }

    private void stopScanning() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Log.d(TAG, "lollipop");
            stopScanningInternally();
        } else {
            // TODO support later
            //mBluetoothAdapter.stopLeScan(mLeScanCallback);
        }
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void stopScanningInternally() {
        if (bluetoothScanner != null) {
            Log.d(TAG, "stopScan");
            bluetoothScanner.stopScan(mScanCallback);
            bluetoothScanner = null;
        }
    }

    private final ScanCallback mScanCallback = new ScanCallback() {

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            super.onBatchScanResults(results);
            if (state != STATE_SCANNING) {
                return;
            }
            for (ScanResult result : results) {
                List<ParcelUuid> uuids = result.getScanRecord().getServiceUuids();
                if (foundTargetService(uuids)) {
                    Log.d(TAG, "found matched scan result");
                    stop();
                    if (listener != null) {
                        listener.onFound(result.getDevice());
                    }
                    break;
                }
            }
        }
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);
            List<ParcelUuid> uuids = result.getScanRecord().getServiceUuids();
            if (foundTargetService(uuids)) {
                Log.d(TAG, "found matched scan result");
                stop();
                if (listener != null) {
                    listener.onFound(result.getDevice());
                }
            }
        }
        @Override
        public void onScanFailed(int errorCode) {
            Log.d(TAG, "onScanFailed");
            super.onScanFailed(errorCode);
            stop();
            state = STATE_READY;
            if (listener != null) {
                listener.onFailure(errorCode);
            }
        }
    };

    private boolean foundTargetService(List<ParcelUuid> uuids) {
        if (uuids == null)
            return false;
        for(ParcelUuid uuid : uuids) {
            if (uuid.getUuid().compareTo(UUID.fromString(serviceUUID)) == 0) {
                return true;
            }
        }
        return false;
    }

    private boolean foundTargetService(ParcelUuid[] uuids) {
        if (uuids == null)
            return false;
        for(ParcelUuid uuid : uuids) {
            if (uuid.getUuid().compareTo(UUID.fromString(serviceUUID)) == 0) {
                return true;
            }
        }
        return false;
    }
}

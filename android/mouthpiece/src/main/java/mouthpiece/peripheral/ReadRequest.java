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

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;

import java.util.UUID;

public class ReadRequest {

    private BluetoothDevice device;
    private BluetoothGattCharacteristic characteristic;
    private int requestId;
    private int offset;

    ReadRequest(BluetoothDevice device, int requestId, int offset,
            BluetoothGattCharacteristic characteristic) {
        this.device = device;
        this.characteristic = characteristic;
        this.requestId = requestId;
        this.offset = offset;
    }

    public int getRequestId() { return requestId; }
    public int getOffset() { return offset ;}
    public BluetoothDevice getDevice() { return device; }
    public BluetoothGattCharacteristic getCharacteristic() { return characteristic; }
    public UUID getUuid() { return characteristic.getUuid(); }
}

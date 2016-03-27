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

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattServer;

import mouthpiece.utils.ValueTypeConverter;

public class ReadResponse {

    private ReadRequest req;
    private int status;

    ReadResponse(ReadRequest req) {
        this.req = req;
        this.status = BluetoothGatt.GATT_SUCCESS;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public void write(byte[] value) {
        req.getCharacteristic().setValue(value);
    }
    public void writeInt(int value) { write(ValueTypeConverter.bytesFromInt(value)); }

    void finishOn(BluetoothGattServer server) {

        if (this.status == BluetoothGatt.GATT_FAILURE) {
            server.sendResponse(req.getDevice(), req.getRequestId(), this.status,
                    0, null);
        } else {
            server.sendResponse(req.getDevice(), req.getRequestId(), this.status,
                    req.getOffset(), req.getCharacteristic().getValue());
        }
    }
}

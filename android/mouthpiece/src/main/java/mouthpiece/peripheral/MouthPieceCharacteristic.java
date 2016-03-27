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
import android.bluetooth.BluetoothGattDescriptor;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class MouthPieceCharacteristic {

    private static final String TAG = MouthPieceCharacteristic.class.getSimpleName();

    private static final String CONFIG_UUID = "00002902-0000-1000-8000-00805f9b34fb";

    private UUID uuid;
    private Map<MouthPiecePeripheral.Event, Method> handlers;
    private Map<String, BluetoothDevice> devicesForNotification;
    private int properties;
    private int permissions;

    MouthPieceCharacteristic(UUID uuid) {
        this.uuid = uuid;
        this.handlers = new HashMap<MouthPiecePeripheral.Event, Method>();
        this.devicesForNotification = new HashMap<String, BluetoothDevice>();
        this.properties = 0;
        this.permissions = 0;
    }

    Collection<BluetoothDevice> getDevicesToNotify() {
        return this.devicesForNotification.values();
    }

    void rememberDeviceForNotification(BluetoothDevice device) {
        devicesForNotification.put(device.getAddress(), device);
    }

    void forgetDeviceForNotification(BluetoothDevice device) {
        devicesForNotification.remove(device.getAddress());
    }

    void addHandler(MouthPiecePeripheral.Event eventType, Method handler) {
        this.handlers.put(eventType, handler);
    }

    boolean canHandle(MouthPiecePeripheral.Event eventType) {
        return this.handlers.containsKey(eventType);
    }

    void addPermission(int permission) {
        this.permissions |= permission;
    }

    void addProperty(int property) {
        this.properties |= property;
    }

    void handleReadRequest(MouthPieceService parent, ReadRequest req, ReadResponse res) {
        if (canHandle(MouthPiecePeripheral.Event.READ)) {
            Method method = this.handlers.get(MouthPiecePeripheral.Event.READ);
            try {
                method.invoke(parent, req, res);
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            } catch (InvocationTargetException e) {
                e.printStackTrace();
            }
        }
    }

    void handleWriteRequest(MouthPieceService parent, WriteRequest req, WriteResponse res) {
        if (canHandle(MouthPiecePeripheral.Event.WRITE)) {
            Method method  = this.handlers.get(MouthPiecePeripheral.Event.WRITE);
            try {
                method.invoke(parent, req, res);
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            } catch (InvocationTargetException e) {
                e.printStackTrace();
            }
        }
    }

    BluetoothGattCharacteristic createRawCharacteristic() {

        BluetoothGattCharacteristic ch = new BluetoothGattCharacteristic(
                this.uuid, this.properties, this.permissions);
        if ((this.properties & BluetoothGattCharacteristic.PROPERTY_NOTIFY) == BluetoothGattCharacteristic.PROPERTY_NOTIFY) {
            BluetoothGattDescriptor descriptor = new BluetoothGattDescriptor(UUID.fromString(CONFIG_UUID),
                    BluetoothGattCharacteristic.PERMISSION_READ | BluetoothGattDescriptor.PERMISSION_WRITE);
            descriptor.setValue(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
            ch.addDescriptor(descriptor);
        }
        return ch;
    }

}

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
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattService;
import android.util.Log;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import mouthpiece.peripheral.exception.InvalidConfigurationException;
import mouthpiece.peripheral.annotation.Notifiable;
import mouthpiece.peripheral.annotation.OnRead;
import mouthpiece.peripheral.annotation.OnWrite;
import mouthpiece.peripheral.annotation.ResponseNeeded;

public abstract class MouthPieceService {

    private static final String TAG = MouthPieceService.class.getSimpleName();
    
    private UUID uuid;
    private boolean isPrimary = false;

    private Map<UUID, MouthPieceCharacteristic> characteristics;

    public MouthPieceService(String uuid) {
        this(UUID.fromString(uuid));
    }

    public MouthPieceService(UUID uuid) {
        this.uuid = uuid;
        characteristics = new HashMap<UUID, MouthPieceCharacteristic>();
    }

    protected void init() {
        // template method
    }

    public UUID getUuid() { return this.uuid; }

    boolean canHandle(UUID uuid, MouthPiecePeripheral.Event eventType) {
        if (!this.characteristics.containsKey(uuid)) {
            return false;
        }
        MouthPieceCharacteristic ch = this.characteristics.get(uuid);
        return ch.canHandle(eventType);
    }

    void dispatchReadRequest(ReadRequest req, ReadResponse res) {
        if (!canHandle(req.getUuid(), MouthPiecePeripheral.Event.READ)) {
            return;
        }
        MouthPieceCharacteristic ch = this.characteristics.get(req.getUuid());
        ch.handleReadRequest(this, req, res);
    }

    void rememberDeviceForNotification(BluetoothDevice device, UUID characteristicUUID) {
        MouthPieceCharacteristic ch = this.characteristics.get(characteristicUUID);
        ch.rememberDeviceForNotification(device); 
    }

    void forgetDeviceForNotification(BluetoothDevice device) {
        for (Map.Entry<UUID, MouthPieceCharacteristic> e : characteristics.entrySet()) {
            e.getValue().forgetDeviceForNotification(device);
        }
    }

    void dispatchWriteRequest(WriteRequest req, WriteResponse res,
            BluetoothGattServer rawServer) {
        if (!canHandle(req.getUuid(), MouthPiecePeripheral.Event.WRITE)) {
            return;
        }
        MouthPieceCharacteristic ch = this.characteristics.get(req.getUuid());

        byte[] valueBeforeWritten = req.getCharacteristic().getValue();
        ch.handleWriteRequest(this, req, res);
        byte[] valueAfterWritten = req.getCharacteristic().getValue();
        if (!Arrays.equals(valueBeforeWritten, valueAfterWritten)) {
            Collection<BluetoothDevice> devices = ch.getDevicesToNotify();
            for (BluetoothDevice d : devices) {
                rawServer.notifyCharacteristicChanged(d, req.getCharacteristic(), false);
            }
        }
    }

    void updateValue(BluetoothGattServer rawServer, 
            BluetoothGattCharacteristic rawCh, byte[] value) {
        MouthPieceCharacteristic ch = characteristics.get(rawCh.getUuid());
        if (ch == null)
            return;

        if (!Arrays.equals(rawCh.getValue(), value)) {
            rawCh.setValue(value);

            Collection<BluetoothDevice> devices = ch.getDevicesToNotify();
            for (BluetoothDevice d : devices) {
                rawServer.notifyCharacteristicChanged(d, rawCh, false);
            }
        }
    }

    BluetoothGattService createRawService() {
        // TODO make primaryFlag configurable
        BluetoothGattService service = new BluetoothGattService(
               this.uuid, BluetoothGattService.SERVICE_TYPE_PRIMARY);
        for (Map.Entry<UUID, MouthPieceCharacteristic> e : characteristics.entrySet()) {
            service.addCharacteristic(e.getValue().createRawCharacteristic());
        }
        return service;
    }

    void analyzeCharacteristicsDefinition() {
        init();
        Method[] methods = getClass().getMethods();
        for (Method method : methods) {
            OnRead readAnnotation = method.getAnnotation(OnRead.class);
            if (readAnnotation != null) {
                Log.d(TAG, "found a method set @OnRead");
                if (validReadHandler(method)) {
                    MouthPieceCharacteristic ch = getOrCreateCharacteristic(readAnnotation.value());
                    ch.addHandler(MouthPiecePeripheral.Event.READ, method);
                    ch.addProperty(BluetoothGattCharacteristic.PROPERTY_READ);
                    ch.addPermission(BluetoothGattCharacteristic.PERMISSION_READ);
                    Notifiable notifiable = method.getAnnotation(Notifiable.class);
                    if (notifiable != null && notifiable.value()) {
                        ch.addProperty(BluetoothGattCharacteristic.PROPERTY_NOTIFY);
                    }
                } else {
                    throw new InvalidConfigurationException("Method definition is invalid for @OnRead annotation");
                }
                continue;
            }

            OnWrite writeAnnotation = method.getAnnotation(OnWrite.class);
            if (writeAnnotation != null) {
                Log.d(TAG, "found a method set @OnWrite");
                if (validWriteHandler(method)) {
                    MouthPieceCharacteristic ch = getOrCreateCharacteristic(writeAnnotation.value());
                    ch.addHandler(MouthPiecePeripheral.Event.WRITE, method);
                    ch.addPermission(BluetoothGattCharacteristic.PERMISSION_WRITE);
                    ResponseNeeded responseNeeded = method.getAnnotation(ResponseNeeded.class);
                    if (responseNeeded != null) {
                        if (responseNeeded.value()) {
                            ch.addProperty(BluetoothGattCharacteristic.PROPERTY_WRITE);
                        } else {
                            ch.addProperty(BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE);
                        }
                    } else {
                        ch.addProperty(BluetoothGattCharacteristic.PROPERTY_WRITE);
                        ch.addProperty(BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE);
                    }
                } else {
                    throw new InvalidConfigurationException("Method definition is invalid for @OnWrite annotation");
                }
                continue;
            }
        }
    }

    private MouthPieceCharacteristic getOrCreateCharacteristic(String uuidString) {
        UUID uuid = UUID.fromString(uuidString);
        if (!this.characteristics.containsKey(uuid))
            this.characteristics.put(uuid, new MouthPieceCharacteristic(uuid));
        return this.characteristics.get(uuid);
    }

    private boolean validReadHandler(Method method) {
        Class<?>[] argTypes = method.getParameterTypes();
        if (argTypes.length != 2)
            return false;
        return (argTypes[0].equals(ReadRequest.class) && argTypes[1].equals(ReadResponse.class));
    }

    public boolean validWriteHandler(Method method) {
        Class<?>[] argTypes = method.getParameterTypes();
        if (argTypes.length != 2)
            return false;
        return (argTypes[0].equals(WriteRequest.class) && argTypes[1].equals(WriteResponse.class));
    }
}


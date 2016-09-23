package mouthpiece.central;

import android.bluetooth.BluetoothGattCharacteristic;

public class Characteristic {

    private BluetoothGattCharacteristic raw;

    public Characteristic(BluetoothGattCharacteristic raw) {
        this.raw = raw;
    }

    public String getServiceUuid() {
        return raw.getService().getUuid().toString();
    }

    public String getUuid() {
        return raw.getUuid().toString();
    }

    public byte[] getValue() {
        return raw.getValue();
    }

    public int getIntValue() {
        return raw.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT32, 0);
    }

    public BluetoothGattCharacteristic getRawCharacteristic() {
        return this.raw;
    }
}

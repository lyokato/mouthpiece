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

import java.util.ArrayList;
import java.util.List;

public class Destination {

    private String service;
    // Should be type for char-setting (uuid, valueType, other...)
    private List<String> observableCharacteristics;
    private List<String> writableCharacteristics;
    private List<String> readableCharacteristics;

    public Destination(String serviceUUID, List<String> observeUUIDs,
                       List<String> writeUUIDs) {
        this.service = serviceUUID;
        this.observableCharacteristics = observeUUIDs;
        this.writableCharacteristics = writeUUIDs;
        this.readableCharacteristics = new ArrayList<String>();
    }

    public Destination(String serviceUUID, List<String> observeUUIDs,
                       List<String> writeUUIDs, List<String> readUUIDs) {
        this.service = serviceUUID;
        this.observableCharacteristics = observeUUIDs;
        this.writableCharacteristics = writeUUIDs;
        this.readableCharacteristics = readUUIDs;
    }

    public String getService() {
        return this.service;
    }

    public List<String> getObservableCharacteristics() {
        return observableCharacteristics;
    }
    public List<String> getWritableCharacteristics() {
        return writableCharacteristics;
    }
    public List<String> getReadableCharacteristics() {
        return readableCharacteristics;
    }

    public static class Builder {

        private String service;
        // Should be type for char-setting (uuid, valueType, other...)
        private List<String> observableCharacteristics = new ArrayList<String>();
        private List<String> writableCharacteristics = new ArrayList<String>();
        private List<String> readableCharacteristics = new ArrayList<String>();

        public Builder(String serviceUUID) {
            this.service = serviceUUID;
        }

        public void addObservableCharacteristic(String uuid) {
            this.observableCharacteristics.add(uuid);
        }

        public void addWritableCharacteristic(String uuid) {
            this.writableCharacteristics.add(uuid);
        }

        public void addReadableCharacteristic(String uuid) {
            this.readableCharacteristics.add(uuid);
        }

        public Destination build() {
            return new Destination(this.service, this.observableCharacteristics,
                    this.writableCharacteristics, this.readableCharacteristics);
        }
    }
}

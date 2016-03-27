# MouthPiece

## This is not stable version

- Sinatra like Peripheral
- WebSocket like Central

AndroidのSDKのBluetoothのAPIを駆使すれば様々なアプリケーションの構築が可能です。

しかし、シンプルなBLEのサービスを提供したいだけの開発者にとっては、
それは非常に冗長なコードになりがちですし、学習コストもかかります。

MouthPieceは、用途を限定することで非常に簡単にBLEのアプリケーションの構築を可能にするライブラリです。

まずはPeripheral側をみてみましょう。
BLEに詳しくない方は「Peripheralとはサーバーのようなものだ」と考えておけばよいでしょう。

## Peripheral Side

### Synopsis

Peripheralとして提供するサービスの定義をしていきます。
BLEのサービスではUUIDを利用します。

あなたのOSにuuidgenがインストールされているのなら
コンソールで次のようにコマンドを打つだけです。

```
uuidgen
```

次のように生成されたUUIDが表示されますので、
これをサービスのIDとして利用していきます。

```
7F93D614-920A-48B0-8910-B3694E06E0FA
```

```java

private MouthPiecePeripheral peripheral;

private void startPeripheral() {
    this.peripheral = MouthPiecePeripheral.build(this, createService());  
    this.peripheral.start();
}

private MouthPieceService createService() {

    MouthPieceService service = new MouthPieceService("7F93D614-920A-48B0-8910-B3694E06E0FA") {

        @OnWrite("514BC46F-DB59-4710-9DF6-9F5081F27CA4")
        @ResponseNeeded(false)
        public void SwitchPower(WriteRequest req, WriteResponse res) {
            int v = req.getIntValue();
        }

        @OnWrite("06AFE76A-7859-4D78-B918-035AA960ED56")
        @ResponseNeeded(false)
        public void SetDestination(WriteRequest req, WriteResponse res) {
            int v = req.getIntValue();
            displayValue(v);
        }

        @OnRead("9B25E4A9-DB5C-4FE0-BB84-C0BC8517C678")
        @Notifiable(true)
        public void ReadCurrentValue(ReadRequest req, ReadResponse res) {
            Log.d(TAG, "read current value");
            res.writeInt(this.currentValue);
        }
    };

    return service;
}

private void displayValue(int value) {
    runOnUiThread(new Runnable(){
        @Override
        public void run() {
          textView.setText(String.valueOf(value));
        }
    });
}

```

このように非常にシンプルにサービスを定義できます。

もしあなたにWebアプリケーション開発の経験があれば、Sinatra frameworkを思い出すかもしれません。

MouthPieceにおいても、`@OnRead`や`@OnWrite`などのアノテーションを使ったハンドラ定義によって
簡単にサービスを定義することが出来ます。

たとえばエアコンのリモコンを見てみましょう。
`現在の室温`、`設定温度`などが表示されていることでしょう。

こういった値は読み込みリクエストを行うよりも、監視のほうがマッチします。
こういったパラメータを提供したい場合は`@OnRead`ハンドラにさらに`@Notifiable(true)`をつけておくと
自動的に通知用のセッティングを行います。

また、上の例では`@OnWrite`ハンドラに、同時に`@ResponseNeeded(false)`が指定されています。
BLEでは書き込み処理に対し、レスポンスを返すタイプと返さないタイプがあります。

リモコンのような用途では多くの場合、レスポンスを返さないタイプで十分です。

テレビのリモコンでチャンネルを変えるとき、チャンネルがちゃんと変わったかどうか、
フィードバックをどのように確認しますか？
リモコン側ではなく、テレビの画面を見て確認しますよね。
リモコン側に成否の結果を返す必要がないケースというのは多く存在します。

このような用途の場合は`@ResponseNeeded`に`false`を指定しておきます。

以上のようにサービスを定義し、`start`メソッドを呼ぶだけで
サービスを開始することが出来ます。

裏で行われているAdvertiseの設定と開始、GATTサーバーの準備、
通知用のDescriptorをCharacteristicに仕込むなどのBLEの様々な仕事については
MouthPieceが内部で実行してくれるので、意識する必要はありません。

Advertise時の細かい挙動の設定などは次のように設定もできます
(以下は実際にデフォルトとして利用している値なので本来は設定の必要はありません)

```java
private void startPeripheral() {
    this.peripheral = MouthPiecePeripheral.build(this, createService());  
    this.peripheral.setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED;);
    this.peripheral.setAdvertiseTxPower(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM;);
    this.peripheral.setIncludeTxPower(true);
    this.peripheral.start();
}
```

`@Notifiable(true)`に指定したcharacteristicの値を変更するときは
次のようにperipheralの`updateValue`メソッドを使いましょう。

この値を監視しているリモコン側に通知されます。

```java
private void notifyNewValue(int newValue) {

  String serviceUUID        = "7F93D614-920A-48B0-8910-B3694E06E0FA";
  String characteristicUUID = "3A7F1423-171B-4B56-976B-4B2CE5012E62";

  this.peripheral.updateValue(serviceUUID, characteristicUUID, newValue);
}
```

To stop the service, simply call `stop` method.

```java
private void stopPeripheral() {
    if (this.peripheral != null && this.peripheral.isRunning()) {
        this.peripheral.stop();
    }
}
```
## Central Side

次にCentral側を見てみましょう。
Peripheralが提供するサービスを利用する側になります。

BLEでは本来Peripheral側がAdvertiseしているパケットのスキャン処理を行わねばなりません。
その中で発見したデバイスの中に、自分が利用したいサービスを提供しているものがあれば
GATT接続を開始します。

MouthPieceCentralを利用すれば、ScanningやGATT Connectionの細かい挙動について意識する必要はありません。
ほとんどWebSocketを利用するコードのように必要最低限のコードでアプリケーションを作成することが出来ます。

### Synopsis

On your Activity class,

```java
import mouthpiece.central.MouthPieceCentral;
import mouthpiece.central.Destination;

private MouthPieceCentral remoteController;
```

Setup a controller with event listener.

All you have to do is to override `onCharacteristicReceived`, and `onStateChanged`.

```java
private void setupRemoteController() {
    this.remoteController = new MouthPieceCentral(this, new MouthPieceCentral.Listener() {

        @Override
        public void onCharacteristicReceived(Characteristic characteristic) {

            String serviceUUID = characteristic.getServiceUuid();
            String uuid        = characteristic.getUuid();
            int value          = characteristic.getIntValue();

            Log.d(TAG, "found characteristic:" + uuid + ":" + String.valueOf(value));
        }

        @Override
        public void onStateChanged(String serviceUUID, int state) {
            switch (state) {
                case MouthPieceCentral.STATE_CONNECTED:
                    Log.d(TAG, "state changed:" + "connected");
                    break;
                case MouthPieceCentral.STATE_IDLE:
                    Log.d(TAG, "state changed:" + "idle");
                    break;
                case MouthPieceCentral.STATE_SCANNING:
                    Log.d(TAG, "state changed:" + "scanning");
                    break;
                case MouthPieceCentral.STATE_ERROR:
                    Log.d(TAG, "state changed:" + "error");
                    break;
                default:
                    // do nothing
            }
        }
    });
    this.remoteController.initialize();
}


private void startRemoteController(Destination destination) {
    if (this.remoteController.canStart()) {
        this.remoteController.start(destination);
    }
}
```

ここでstartに渡しているDestinationですが、次のように作成します。
このサービス上で、監視や書き込みを行いたいcharacteristicのUUIDは
あらかじめここで指定しておく必要があります。

```java
Destination dest = new Destination.Builder(serviceUUID);
dest.addWritableCharacteristic(chUUID00)
dest.addWritableCharacteristic(chUUID01)
dest.addObservableCharacteristic(chUUID02)
dest.addReadableCharacteristic(chUUID03)
```

To send a value to remote device, use `write` method.

```java
private void sendValueToRemoteDevice(String uuid, int value) {
    if (this.remoteController.isConnected()) {
        this.remoteController.write(uuid, value);
    }
}
```

また、読み込みを行う場合は、readメソッドを使うとよいでしょう。
読み込みが完了したら、Listenerの`onCharacteristicReceived`が呼ばれます。

```java
private void readValueFromRemoteDevice(String uuid, int value) {
    if (this.remoteController.isConnected()) {
        this.remoteController.read(uuid);
    }
}
```

ただ、シンプルなコントローラのような用途においては 
ほとんどの場合、読み込みは必要なく通知で十分です。
DestinationにaddObservableCharacteristicでUUIDを追加して接続しておくと
そのcharacteristicに変化があった場合、自動的に
ListenerのonCharacteristicReceivedメソッドが呼ばれます。


強制的に止めたい場合は次のようにstopを呼びます。

```java
private void stopRemoteController() {
    this.remoteController.stop();
}
```

And override lifecycle event methods like this,
then a controller automatically manages its scanner and connection internally.

```java
@Override
protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    this.remoteController.onActivityResult(requestCode, resultCode, data);
}

@Override
public void onResume() {
    super.onResume();
    this.remoteController.resume();
}

@Override
public void onPause() {
    super.onPause();
    this.remoteController.pause();
}

@Override
public void onDestroy() {
    this.remoteController.destroy();
    super.onDestroy();
}
```



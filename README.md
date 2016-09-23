# MouthPiece

## This is not stable version

- Sinatra like Peripheral API
- WebSocket/Ajax like Central API

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
その中で発見したデバイスの中に、自分が利用したいサービスを提供しているものがあればGATT接続を開始します。

MouthPieceCentralを利用すれば、ScanningやGATT Connectionの細かい挙動について意識する必要はありません。
ほとんどWebSocketを利用するコードのように必要最低限のコードでアプリケーションを作成することが出来ます。

### Synopsis

On your Activity class,

```java
import mouthpiece.central.MouthPieceCentral;
import mouthpiece.central.Destination;

private MouthPieceCentral central;
```

Setup a controller with event listener.

All you have to do is to override `onCharacteristicReceived`, and `onStateChanged`.

```java
private void setupCentral() {
    this.central = new MouthPieceCentral(this, new MouthPieceCentral.Listener() {

        @Override
        public void onCharacteristicReceived(Characteristic characteristic) {

            String serviceUUID = characteristic.getServiceUuid();
            String uuid        = characteristic.getUuid();
            byte[] value       = characteristic.getValue();

            handleChangedValue(uuid, value);
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
    this.central.initialize();
}


private void startCentral() {
    if (this.central.canStart()) {
        this.central.start(buildDestination());
    }
}
```

ここでstartに渡しているDestinationですが、次のように作成します。
このサービス上で、監視や書き込みを行いたいcharacteristicのUUIDは
あらかじめここで指定しておく必要があります。

```java
private Destination buildDestination() {
    Destination dest = new Destination.Builder(serviceUUID);
    dest.addWritableCharacteristic(chUUID00)
    dest.addSendableCharacteristic(chUUID01)
    dest.addObservableCharacteristic(chUUID02)
    dest.addReadableCharacteristic(chUUID03)
    return dest;
}
```

- addWritableCharacteristic: 書き込みを行いたいcharacteristicのUUIDを指定します
- addSendableCharacteristic: 書き込み(ただしレスポンスなし)を行いたいcharacteristicのUUIDを指定します
- addObservableCharacteristic: 値の変更の監視を行いたいcharacteristicのUUIDを指定します
- addReadableCharacteristic: 読み込みを行いたいcharacteristicのUUIDを指定します


書き込み(ただしレスポンスの確認は行わない)は次のようにsendを呼びます。

```java
private void sendValueToRemoteDevice(String uuid, byte[] value) {
    if (this.central.isConnected()) {
        this.central.send(uuid, value);
    }
}
```

成否のレスポンスが必要な場合は次のようにwriteを呼びます。
そのまま引数にコールバックとなるListenerを渡します。

```java
private void sendValueToRemoteDevice(String uuid, byte[] value) {

    if (this.central.isConnected()) {

        this.central.write(uuid, value, new WriteResultListener(){
            @Override
            public void onFinished(bool result) {
              if (result) {
                // success
              } else {
                // error
              }
            }

        });
    }
}
```

また、読み込みを行う場合は、readメソッドを使うとよいでしょう。

```java
private void readValueFromRemoteDevice(String uuid, int value) {
    if (this.central.isConnected()) {
        this.central.read(uuid, new ReadResultListener(){
          @Override
          public void onFinished(bool result, byte[] value) {
             if (result) {
               // success
             } else {
               // error
             }
          }
        });
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
private void stopCentral() {
    this.central.stop();
}
```

onCreateなどで次のように、Deviceのcapabilityのチェックを行います。
必要であれば設定画面へのIntentを飛ばします。

```java
if (!central.hasFeature()) {
    showError("this device doesn't support BLE features");
} else {
    central.initialize();
}
```

BLEの設定後、このActivityに戻ってきたときのためにonActivityResultを呼ぶようにしておきます。

```java
@Override
protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    this.central.onActivityResult(requestCode, resultCode, data);
}
```

次のようにpause/resumeを呼び出すと
スキャンの再開や切断などをActivityのライフサイクルに合わせて処理します。

```java
@Override
public void onResume() {
    super.onResume();
    this.central.resume();
}

@Override
public void onPause() {
    super.onPause();
    this.central.pause();
}

@Override
public void onDestroy() {
    this.central.destroy();
    super.onDestroy();
}
```

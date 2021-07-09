package online.fatmaxxer.publicRelease1;

//
//        MIT License
//
//        Copyright (c) 2020 Mark Kuo
//
//        Permission is hereby granted, free of charge, to any person obtaining a copy
//        of this software and associated documentation files (the "Software"), to deal
//        in the Software without restriction, including without limitation the rights
//        to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
//        copies of the Software, and to permit persons to whom the Software is
//        furnished to do so, subject to the following conditions:
//
//        The above copyright notice and this permission notice shall be included in all
//        copies or substantial portions of the Software.
//
//        THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
//        IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
//        FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
//        AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
//        LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
//        OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
//        SOFTWARE.


import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.AdvertisingSet;
import android.bluetooth.le.AdvertisingSetCallback;
import android.bluetooth.le.AdvertisingSetParameters;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.ParcelUuid;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class CSCService extends Service {
    private static final String TAG = CSCService.class.getSimpleName();
    private static final int ONGOING_NOTIFICATION_ID = 9999;
    private static final String CHANNEL_DEFAULT_IMPORTANCE = "csc_ble_channel";
    private static final String MAIN_CHANNEL_NAME = "CscService";

    // Checks that the callback that is done after a BluetoothGattServer.addService() has been complete.
    // More services cannot be added until the callback has completed successfully
    private boolean btServiceInitialized = false;

    // bluetooth API
    private BluetoothManager mBluetoothManager;
    private BluetoothGattServer mBluetoothGattServer;
    private BluetoothLeAdvertiser mBluetoothLeAdvertiser;
    // notification subscribers
    private Set<BluetoothDevice> mRegisteredDevices = new HashSet<>();

    // last wheel and crank (speed/cadence) information to send to CSCProfile
    private long cumulativeWheelRevolution = 0;
    private long cumulativeCrankRevolution = 0;
    private int lastWheelEventTime = 0;
    private int lastCrankEventTime = 0;

    // for UI updates
    private long lastSpeedTimestamp = 0;
    private long lastCadenceTimestamp = 0;
    private long lastHRTimestamp = 0;
    private long lastSSDistanceTimestamp = 0;
    private long lastSSSpeedTimestamp = 0;
    private long lastSSStrideCountTimestamp = 0;
    private float lastSpeed = 0;
    private int lastCadence = 0;
    public int lastHR = 0;
    private long lastSSDistance = 0;
    private float lastSSSpeed = 0;
    private long lastStridePerMinute = 0;

    // for onCreate() failure case
    private boolean initialised = false;

    // Used to flag if we have a combined speed and cadence sensor and have already re-connected as combined
    private boolean combinedSensorConnected = false;

    // Binder for activities wishing to communicate with this service
    private final IBinder binder = new LocalBinder();


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Service onStartCommand");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel(CHANNEL_DEFAULT_IMPORTANCE, MAIN_CHANNEL_NAME);

            // Create the PendingIntent
            PendingIntent notifyPendingIntent = PendingIntent.getActivity(
                    this,
                    0,
                    new Intent(this.getApplicationContext(),MainActivity.class),
                    PendingIntent.FLAG_UPDATE_CURRENT);

            // build a notification
            Notification notification =
                    new Notification.Builder(this, CHANNEL_DEFAULT_IMPORTANCE)
                            .setContentTitle(getText(R.string.app_name)+".BLESensorEmulatorService")
                            .setContentText("Active")
                            .setSmallIcon(R.drawable.ic_launcher_background)
                            .setAutoCancel(true)
                            .setContentIntent(notifyPendingIntent)
                            .setTicker(getText(R.string.app_name))
                            .build();
            startForeground(ONGOING_NOTIFICATION_ID, notification);
        } else {
            Notification notification = new NotificationCompat.Builder(this, CHANNEL_DEFAULT_IMPORTANCE)
                    .setContentTitle(getString(R.string.app_name)+".BLESensorEmulatorService")
                    .setContentText("Active")
                    .setSmallIcon(R.drawable.ic_launcher_background)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setAutoCancel(true)
                    .build();
            startForeground(ONGOING_NOTIFICATION_ID, notification);
        }
        return Service.START_NOT_STICKY;
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private void createNotificationChannel(String channelId, String channelName) {
        NotificationChannel channel = new NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_LOW);
        channel.setLightColor(Color.BLUE);
        channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        manager.createNotificationChannel(channel);
    }

    private boolean checkBluetoothSupport(BluetoothAdapter bluetoothAdapter) {
        if (bluetoothAdapter == null) {
            Log.w(TAG, "Bluetooth is not supported");
            return false;
        }
        boolean extended = bluetoothAdapter.isLeExtendedAdvertisingSupported();
        boolean periodic = bluetoothAdapter.isLePeriodicAdvertisingSupported();
        boolean le2mphy = bluetoothAdapter.isLe2MPhySupported();
        boolean other = bluetoothAdapter.isLeCodedPhySupported();
        //bluetoothAdapter.setName("fakeSensor");
        Log.d(TAG,"Bluetooth adapter name "+bluetoothAdapter.getName()+" address "+bluetoothAdapter.getAddress());
        Log.d(TAG,"Bluetooth adapter max advertising data length "+bluetoothAdapter.getLeMaximumAdvertisingDataLength());
        Log.d(TAG, "checkBluetoothSupport "+le2mphy+" "+other);
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Log.w(TAG, "Bluetooth LE is not supported");
            return false;
        }
        return true;
    }

    private BroadcastReceiver mBluetoothReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG,"mBluetoothReceiver.onReceive");
            int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.STATE_OFF);
            switch (state) {
                case BluetoothAdapter.STATE_ON:
                    startAdvertising();
                    startServer();
                    break;
                case BluetoothAdapter.STATE_OFF:
                    stopServer();
                    stopAdvertising();
                    break;
            }
        }
    };

    @Override
    public void onCreate() {
        Log.d(TAG, "CSCService BLE sensor emulator started");
        super.onCreate();

        // ANT+
        //initAntPlus();

        // Bluetooth LE
        mBluetoothManager = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
        assert mBluetoothManager != null;
        BluetoothAdapter bluetoothAdapter = mBluetoothManager.getAdapter();
        // continue without proper Bluetooth support
        if (!checkBluetoothSupport(bluetoothAdapter)) {
            Log.e(TAG, "Bluetooth LE isn't supported. This won't run");
            stopSelf();
            return;
        }

        // Register for system Bluetooth events
        IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        registerReceiver(mBluetoothReceiver, filter);
        if (!bluetoothAdapter.isEnabled()) {
            Log.d(TAG, "Bluetooth is currently disabled...enabling");
            bluetoothAdapter.enable();
        } else {
            Log.d(TAG, "Bluetooth enabled...starting services");
            startAdvertising();
            startServer();
        }
        initialised = true;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        Log.d(TAG, "onTaskRemoved called");
        super.onTaskRemoved(rootIntent);
        stopForeground(true);
        stopSelf();
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "BLESensorEmulator Service destroyed");
        super.onDestroy();
        if (initialised) {
            // stop BLE
            BluetoothAdapter bluetoothAdapter = mBluetoothManager.getAdapter();
            if (bluetoothAdapter != null && bluetoothAdapter.isEnabled()) {
                stopServer();
                stopAdvertising();
            }

            unregisterReceiver(mBluetoothReceiver);

            combinedSensorConnected = false;
        }
    }

    public void startAdvertising() {
        startAdvertisingOld();
    }

    public void startAdvertisingNew() {
        BluetoothLeAdvertiser advertiser =
                BluetoothAdapter.getDefaultAdapter().getBluetoothLeAdvertiser();

        AdvertisingSetParameters.Builder parameters = (new AdvertisingSetParameters.Builder())
                .setLegacyMode(false)
                .setInterval(AdvertisingSetParameters.INTERVAL_HIGH)
                .setTxPowerLevel(AdvertisingSetParameters.TX_POWER_HIGH)
                .setPrimaryPhy(BluetoothDevice.PHY_LE_1M)
                .setSecondaryPhy(BluetoothDevice.PHY_LE_2M);

        AdvertiseData data = (new AdvertiseData.Builder())
                .addServiceData(new ParcelUuid(FatMaxxerBLEProfiles.CSC_SERVICE),"FatMaxxer".getBytes())
                .build();


        AdvertisingSetCallback callback = new AdvertisingSetCallback() {
            @Override
            public void onAdvertisingSetStarted(AdvertisingSet advertisingSet, int txPower, int status) {
                Log.d(TAG, "onAdvertisingSetStarted(): txPower:" + txPower + " , status: "
                        + status);
                AdvertisingSet currentAdvertisingSet = advertisingSet;
//
//                // After the set starts, you can modify the data and parameters of currentAdvertisingSet.
//                currentAdvertisingSet.setAdvertisingData((new
//                        AdvertiseData.Builder())
//                        .addServiceData(new ParcelUuid(CSCProfile.CSC_SERVICE), "FatMaxxer".getBytes())
//                        .build());
//
//                // Can also stop and restart the advertising
//                currentAdvertisingSet.enableAdvertising(false, 0, 0);
//                // Wait for onAdvertisingEnabled callback...
//                currentAdvertisingSet.enableAdvertising(true, 0, 0);
//                // Wait for onAdvertisingEnabled callback...
//
//                // Or modify the parameters - i.e. lower the tx power
//                currentAdvertisingSet.enableAdvertising(false, 0, 0);
//                // Wait for onAdvertisingEnabled callback...
//                currentAdvertisingSet.setAdvertisingParameters(parameters.setTxPowerLevel
//                        (AdvertisingSetParameters.TX_POWER_LOW).build());
//                // Wait for onAdvertisingParametersUpdated callback...
//                currentAdvertisingSet.enableAdvertising(true, 0, 0);
//                // Wait for onAdvertisingEnabled callback...

            }

            @Override
            public void onAdvertisingSetStopped(AdvertisingSet advertisingSet) {
                Log.d(TAG, "onAdvertisingSetStopped():");
            }
        };

        AdvertiseData advScanResponse = new AdvertiseData.Builder()
                .setIncludeDeviceName(true)
                //.setIncludeTxPowerLevel(true)
                .addServiceUuid(new ParcelUuid(FatMaxxerBLEProfiles.CSC_SERVICE))
                .build();

        advertiser.startAdvertisingSet(parameters.build(), data, advScanResponse, null, null, callback);

        // Wait for onAdvertisingDataSet callback...

    }

    /**
     * Begin advertising over Bluetooth that this device is connectable
     */
    public void startAdvertisingOld() {
        Log.d(TAG, "CSCService BLEsensor startAdvertising");
        BluetoothAdapter bluetoothAdapter = mBluetoothManager.getAdapter();
        if (bluetoothAdapter == null) {
            Log.e(TAG, "Failed to create bluetooth adapter");
            return;
        }
        mBluetoothLeAdvertiser = bluetoothAdapter.getBluetoothLeAdvertiser();
        if (mBluetoothLeAdvertiser == null) {
            Log.w(TAG, "Failed to create advertiser");
            return;
        } else {
            Log.d(TAG, "startAdvertising: created BLE advertiser");
        }

        AdvertiseSettings settings = new AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setConnectable(true)
                .setTimeout(0)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                .build();

        AdvertiseData advData = new AdvertiseData.Builder()
                .setIncludeTxPowerLevel(true)
//                .addServiceUuid(new ParcelUuid(FatMaxxerBLEProfiles.CSC_SERVICE))
                .addServiceUuid(new ParcelUuid(FatMaxxerBLEProfiles.HR_SERVICE))
//                .addServiceUuid(new ParcelUuid(FatMaxxerBLEProfiles.RSC_SERVICE))
                .build();

        AdvertiseData advScanResponse = new AdvertiseData.Builder()
                .setIncludeDeviceName(true)
                .addServiceUuid(new ParcelUuid(FatMaxxerBLEProfiles.HR_SERVICE))
                .build();

        mBluetoothLeAdvertiser
                .startAdvertising(settings, advData, advScanResponse, mAdvertiseCallback);
        Log.d(TAG,"startAdvertising call completed");
    }

    /**
     * Stop Bluetooth advertisements
     */
    public void stopAdvertising() {
        if (mBluetoothLeAdvertiser == null) return;

        mBluetoothLeAdvertiser.stopAdvertising(mAdvertiseCallback);
    }

    /**
     * Initialize the GATT server
     */
    private void startServer() {
        Log.d(TAG,"CSCService.startServer");
        mBluetoothGattServer = mBluetoothManager.openGattServer(this, mGattServerCallback);
        if (mBluetoothGattServer == null) {
            Log.w(TAG, "Unable to create GATT server");
            return;
        }

        btServiceInitialized = false;
        // TODO: enable either 1 of them or both of them according to user selection
        if (mBluetoothGattServer.addService(FatMaxxerBLEProfiles.createCSCService((byte)(FatMaxxerBLEProfiles.CSC_FEATURE_WHEEL_REV | FatMaxxerBLEProfiles.CSC_FEATURE_CRANK_REV)))) {
            Log.d(TAG, "CSCP enabled!");
        } else {
            Log.d(TAG, "Failed to add csc service to bluetooth layer!");
        }
        // We cannot add another service until the callback for the previous service has completed
        while (!btServiceInitialized);

        btServiceInitialized = false;
        if (mBluetoothGattServer.addService(FatMaxxerBLEProfiles.createHRService())) {
            Log.d(TAG, "HR enabled!");
        } else {
            Log.d(TAG, "Failed to add hr service to bluetooth layer!");
        }
        // We cannot add another service until the callback for the previous service has completed
        while (!btServiceInitialized);

        btServiceInitialized = false;
        if (mBluetoothGattServer.addService(FatMaxxerBLEProfiles.createRscService())) {
            Log.d(TAG, "RSC enabled!");
        } else {
            Log.d(TAG, "Failed to add rsc service to bluetooth layer");
        }
        while (!btServiceInitialized);

        Log.d(TAG, "Enumerating (" +  mBluetoothGattServer.getServices().size() + ") BT services");
        for (BluetoothGattService b : mBluetoothGattServer.getServices()) {
            Log.d(TAG,"Service registered: " +  b.getUuid().toString());
        }

        // start periodicUpdate, sending notification to subscribed device and UI
        handler.post(periodicUpdate);
    }

    /**
     * Shut down the GATT server
     */
    private void stopServer() {
        if (mBluetoothGattServer == null) return;

        // stop periodicUpdate
        handler.removeCallbacksAndMessages(null);
        mBluetoothGattServer.close();
    }

    /**
     * Callback to receive information about the advertisement process
     */
    private AdvertiseCallback mAdvertiseCallback = new AdvertiseCallback() {
        @Override
        public void onStartSuccess(AdvertiseSettings settingsInEffect) {
            Log.i(TAG, "LE Advertise Started:" + settingsInEffect);
        }
        @Override
        public void onStartFailure(int errorCode) {
            Log.w(TAG, "LE Advertise Failed: "+errorCode);
        }
    };

    Handler handler = new Handler();
    private Runnable periodicUpdate = new Runnable () {
        @Override
        public void run() {
            Log.d(TAG,"periodicUpdate.run");
            // scheduled next run in 1 sec
            handler.postDelayed(periodicUpdate, 1000);

            // send to registered BLE devices. It's a no-op if there is no GATT client
            notifyRegisteredDevices();

//            // update UI by sending broadcast to our main activity
//            Intent i = new Intent("online.fatmaxxer.publicrelease1");
//            i.putExtra("speed", lastSpeed);
//            i.putExtra("cadence", lastCadence);
//            i.putExtra("hr", lastHR);
//            i.putExtra("ss_distance", lastSSDistance);
//            i.putExtra("ss_speed", lastSSSpeed);
//            i.putExtra("ss_stride_count", lastStridePerMinute);
//            i.putExtra("speed_timestamp", lastSpeedTimestamp);
//            i.putExtra("cadence_timestamp", lastCadenceTimestamp);
//            i.putExtra("hr_timestamp", lastHRTimestamp);
//            i.putExtra("ss_distance_timestamp", lastSSDistanceTimestamp);
//            i.putExtra("ss_speed_timestamp", lastSSSpeedTimestamp);
//            i.putExtra("ss_stride_count_timestamp", lastSSStrideCountTimestamp);
////            Log.v(TAG, "Updating UI: speed:" + lastSpeed
////                    + ", cadence:" + lastCadence +
////                    ", hr " + lastHR +
////                    ", speed_ts:" + lastSpeedTimestamp +
////                    ", cadence_ts:" + lastCadenceTimestamp +
////                    ", " + lastHRTimestamp +
////                    ", ss_distance: " + lastSSDistance +
////                    ", ss_distance_timestamp: " + lastSSDistanceTimestamp +
////                    ", ss_speed: " + lastSSSpeed +
////                    ", ss_speed_timestamp: " + lastSSSpeedTimestamp +
////                    ", ss_stride_count: " + lastStridePerMinute +
////                    ", ss_stride_count_timestamp: " + lastSSStrideCountTimestamp);
//            sendBroadcast(i);
        }
    };

    /**
     * Send a CSC service notification to any devices that are subscribed
     * to the characteristic
     */
    private void notifyRegisteredDevices() {
        if (mRegisteredDevices.isEmpty()) {
            Log.v(TAG, "No subscribers registered");
//            return;
        }

        byte[] data = FatMaxxerBLEProfiles.getMeasurement(cumulativeWheelRevolution, lastWheelEventTime,
                cumulativeCrankRevolution, lastCrankEventTime);
        cumulativeCrankRevolution++;
        lastWheelEventTime++;
        cumulativeWheelRevolution++;
        lastCrankEventTime++;

        Log.v(TAG, "Sending update to " + mRegisteredDevices.size() + " subscribers");
        for (BluetoothDevice device : mRegisteredDevices) {
            BluetoothGattService service = mBluetoothGattServer.getService(FatMaxxerBLEProfiles.CSC_SERVICE);
            if (service != null) {
                BluetoothGattCharacteristic measurementCharacteristic = mBluetoothGattServer
                        .getService(FatMaxxerBLEProfiles.CSC_SERVICE)
                        .getCharacteristic(FatMaxxerBLEProfiles.CSC_MEASUREMENT);
                if (!measurementCharacteristic.setValue(data)) {
                    Log.w(TAG, "CSC Measurement data isn't set properly!");
                }
                // false is used to send a notification
                mBluetoothGattServer.notifyCharacteristicChanged(device, measurementCharacteristic, false);
            } else {
                Log.v(TAG, "Service " + FatMaxxerBLEProfiles.CSC_SERVICE + " was not found as an installed service");
            }

            service = mBluetoothGattServer.getService(FatMaxxerBLEProfiles.HR_SERVICE);
            if (service != null) {
                Log.v(TAG, "Processing Heart Rate");

                BluetoothGattCharacteristic measurementCharacteristic = mBluetoothGattServer
                        .getService(FatMaxxerBLEProfiles.HR_SERVICE)
                        .getCharacteristic(FatMaxxerBLEProfiles.HR_MEASUREMENT);

                byte[] hrData = FatMaxxerBLEProfiles.getHR(lastHR, lastHRTimestamp);
                if (!measurementCharacteristic.setValue(hrData)) {
                    Log.w(TAG, "HR  Measurement data isn't set properly!");
                }
                mBluetoothGattServer.notifyCharacteristicChanged(device, measurementCharacteristic, false);
            } else {
                Log.v(TAG, "Service " + FatMaxxerBLEProfiles.HR_SERVICE + " was not found as an installed service");
            }

            service = mBluetoothGattServer.getService(FatMaxxerBLEProfiles.RSC_SERVICE);
            if (service != null) {
                Log.v(TAG, "Processing Running Speed and Cadence sensor");

                BluetoothGattCharacteristic measurementCharacteristic = mBluetoothGattServer
                        .getService(FatMaxxerBLEProfiles.RSC_SERVICE)
                        .getCharacteristic(FatMaxxerBLEProfiles.RSC_MEASUREMENT);

                byte[] rscData = FatMaxxerBLEProfiles.getRsc(lastSSDistance, lastSSSpeed, lastStridePerMinute);
                if (!measurementCharacteristic.setValue(rscData)) {
                    Log.w(TAG, "RSC Measurement data isn't set properly!");
                }
                mBluetoothGattServer.notifyCharacteristicChanged(device, measurementCharacteristic, false);
            } else {
                Log.v(TAG, "Service " + FatMaxxerBLEProfiles.RSC_SERVICE + " was not found as an installed service");
            }
        }
    }

    private final BluetoothGattServerCallback mGattServerCallback = new BluetoothGattServerCallback() {

        @Override
        public void onServiceAdded(int status, BluetoothGattService service) {
            Log.i(TAG, "onServiceAdded(): status:" + status + ", service:" + service);
            // Sets up for next service to be added
            btServiceInitialized = true;
        }

        @Override
        public void onConnectionStateChange(BluetoothDevice device, int status, int newState) {
            Log.d(TAG,"onConnectionStateChange "+device+" "+status+" "+newState+" "+device.getName()+" "+device.getBluetoothClass()+" "+device.getAddress());
            if (newState == BluetoothProfile.STATE_CONNECTING) {
                Log.d(TAG, "BluetoothDevice CONNECTING: " + device.getName() + " [" + device.getAddress() + "]");
            }
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.d(TAG, "BluetoothDevice CONNECTED: " + device.getName() + " [" + device.getAddress() + "]");
//                mRegisteredDevices.add(device);
            }
            if (mRegisteredDevices.contains(device)) {
                if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    Log.i(TAG, "BluetoothDevice DISCONNECTED: " + device.getName() + " [" + device.getAddress() + "]");
                    //Remove device from any active subscriptions
                    mRegisteredDevices.remove(device);
                } else {
                    Log.i(TAG, "onConnectionStateChange() status:" + status + "->" + newState + ", device" + device);
                }
            }
        }

        @Override
        public void onNotificationSent(BluetoothDevice device, int status) {
            Log.v(TAG, "onNotificationSent() result:" + status);
        }

        @Override
        public void onMtuChanged(BluetoothDevice device, int mtu) {
            Log.d(TAG, "onMtuChanged:" + device + " =>" + mtu);
        }

        @Override
        public void onCharacteristicReadRequest(BluetoothDevice device, int requestId, int offset,
                                                BluetoothGattCharacteristic characteristic) {
            Log.d(TAG,"onCharacteristicReadRequest");
            if (FatMaxxerBLEProfiles.CSC_MEASUREMENT.equals(characteristic.getUuid())) {
                Log.i(TAG, "Read CSC Measurement");
                //TODO: this should never happen since this characteristic doesn't support read
                mBluetoothGattServer.sendResponse(device,
                        requestId,
                        BluetoothGatt.GATT_SUCCESS,
                        0,
                        null);
            } else if (FatMaxxerBLEProfiles.CSC_FEATURE.equals(characteristic.getUuid())) {
                Log.i(TAG, "Read CSC Feature");
                mBluetoothGattServer.sendResponse(device,
                        requestId,
                        BluetoothGatt.GATT_SUCCESS,
                        0,
                        FatMaxxerBLEProfiles.getFeature());
            } else if (FatMaxxerBLEProfiles.RSC_MEASUREMENT.equals(characteristic.getUuid())) {
                Log.i(TAG, "READ RSC Measurement");
                mBluetoothGattServer.sendResponse(device,
                        requestId,
                        BluetoothGatt.GATT_SUCCESS,
                        0,
                        null);
            } else if (FatMaxxerBLEProfiles.RSC_FEATURE.equals(characteristic.getUuid())) {
                Log.i(TAG, "READ RSC Feature");
                mBluetoothGattServer.sendResponse(device,
                        requestId,
                        BluetoothGatt.GATT_SUCCESS,
                        0,
                        FatMaxxerBLEProfiles.getRscFeature());
            } else {
                // Invalid characteristic
                Log.w(TAG, "Invalid Characteristic Read: " + characteristic.getUuid());
                mBluetoothGattServer.sendResponse(device,
                        requestId,
                        BluetoothGatt.GATT_FAILURE,
                        0,
                        null);
            }
        }

        @Override
        public void onDescriptorReadRequest(BluetoothDevice device, int requestId, int offset,
                                            BluetoothGattDescriptor descriptor) {
            Log.d(TAG,"omDescriptorReadRequest");
            if (FatMaxxerBLEProfiles.CLIENT_CONFIG.equals(descriptor.getUuid())) {
                Log.d(TAG, "Config descriptor read");
                byte[] returnValue;
                if (mRegisteredDevices.contains(device)) {
                    returnValue = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE;
                } else {
                    returnValue = BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE;
                }
                mBluetoothGattServer.sendResponse(device,
                        requestId,
                        BluetoothGatt.GATT_SUCCESS,
                        offset,
                        returnValue);
            } else {
                Log.w(TAG, "Unknown descriptor read request");
                mBluetoothGattServer.sendResponse(device,
                        requestId,
                        BluetoothGatt.GATT_FAILURE,
                        offset,
                        null);
            }
        }

        @Override
        public void onDescriptorWriteRequest(BluetoothDevice device, int requestId,
                                             BluetoothGattDescriptor descriptor,
                                             boolean preparedWrite, boolean responseNeeded,
                                             int offset, byte[] value) {
            Log.d(TAG,"onDescriptorWriteRequest");
            if (FatMaxxerBLEProfiles.CLIENT_CONFIG.equals(descriptor.getUuid())) {
                Log.d(TAG,"onDescriptorWriteRequest: match uuid");
                if (Arrays.equals(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE, value)) {
                    Log.d(TAG, "Subscribe device to notifications: " + device);
                    mRegisteredDevices.add(device);
                } else if (Arrays.equals(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE, value)) {
                    Log.d(TAG, "Unsubscribe device from notifications: " + device);
                    mRegisteredDevices.remove(device);
                }

                if (responseNeeded) {
                    mBluetoothGattServer.sendResponse(device,
                            requestId,
                            BluetoothGatt.GATT_SUCCESS,
                            0,
                            null);
                }
            } else {
                Log.w(TAG, "Unknown descriptor write request");
                if (responseNeeded) {
                    mBluetoothGattServer.sendResponse(device,
                            requestId,
                            BluetoothGatt.GATT_FAILURE,
                            0,
                            null);
                }
            }
        }
    };

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    /**
     * Get the services for communicating with it
     */
    public class LocalBinder extends Binder {
        CSCService getService() {
            return CSCService.this;
        }
    }

}
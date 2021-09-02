package online.fatmaxxer.publicRelease1;

import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

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

/**
 * Implementation of the Bluetooth GATT Cycling Speed and Cadence Profile
 * https://www.bluetooth.com/specifications/gatt/
 */
public class FatMaxxerBLEProfiles {
    private static final String TAG = FatMaxxerBLEProfiles.class.getSimpleName();

    // https://www.bluetooth.com/specifications/gatt/services/
    /** Cycling Speed and Cadence */
    public static UUID CSC_SERVICE = UUID.fromString("00001816-0000-1000-8000-00805f9b34fb");

    // https://www.bluetooth.com/specifications/gatt/characteristics/
    /** Mandatory Characteristic: CSC Measurement */
    public static UUID CSC_MEASUREMENT = UUID.fromString("00002a5b-0000-1000-8000-00805f9b34fb");

    // https://www.bluetooth.com/specifications/gatt/descriptors/
    /** Mandatory Client Characteristic Config Descriptor */
    public static UUID CLIENT_CONFIG = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    /** Mandatory Characteristic: CSC Feature */
    public static UUID CSC_FEATURE = UUID.fromString("00002a5c-0000-1000-8000-00805f9b34fb");

    // generic access service
    public static UUID GENERIC_ACCESS_SERVICE = UUID.fromString("00001800-0000-1000-8000-00805f9b34fb");
    // device information service
    public static UUID DEVICE_INFORMATION_SERVICE = UUID.fromString("0000180A-0000-1000-8000-00805f9b34fb");
    // body location - (Polar H10 == "0002")
    public static UUID BODY_SENSOR_LOCATION = UUID.fromString("00002A38-0000-1000-8000-00805f9b34fb");

    /** Heart Rate */
    public static UUID HR_SERVICE = UUID.fromString("0000180D-0000-1000-8000-00805f9b34fb");
    public static UUID HR_MEASUREMENT = UUID.fromString("00002A37-0000-1000-8000-00805f9b34fb");

    /** Running Speed and Cadence */
    public static UUID RSC_SERVICE = UUID.fromString("00001814-0000-1000-8000-00805f9b34fb");
    public static UUID RSC_MEASUREMENT = UUID.fromString("00002A53-0000-1000-8000-00805f9b34fb");
    public static UUID RSC_FEATURE = UUID.fromString("00002A54-0000-1000-8000-00805f9b34fb");
    private static final byte RSC_NO_FEATURES = 0b00000000;

    /** supported CSC Feature bit: Speed sensor */
    public static final byte CSC_FEATURE_WHEEL_REV = 0x1;
    /** supported CSC Feature bit: Cadence sensor */
    public static final byte CSC_FEATURE_CRANK_REV = 0x2;

    // this feature defined in spec is not supported
    //public static final byte CSC_FEATURE_MULTI_SENSOR_LOC = 0x4;

    // default implemented features
    private static byte currentFeature = CSC_FEATURE_WHEEL_REV | CSC_FEATURE_CRANK_REV;

    private static byte rscFeature = RSC_NO_FEATURES;


    // 2 service ids are advertised (by EW-EN100)
    public static UUID emtbAdvertised1ServiceUuid = UUID.fromString("000018ff-5348-494d-414e-4f5f424c4500");
    //public static UUID advertised2ServiceUuid = UUID.fromString("000018ef-5348-494d-414e-4f5f424c4500");

    public static UUID emtbBatteryServiceUuid = UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb");
    public static UUID emtbBatteryCharacteristicUuid = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb");

    public static UUID emtbModeServiceUuid = UUID.fromString("000018ef-5348-494d-414e-4f5f424c4500");
    public static UUID emtbModeCharacteristicUuid = UUID.fromString("00002ac1-5348-494d-414e-4f5f424c4500");

    public static UUID emtbMACServiceUuid = UUID.fromString("000018fe-1212-efde-1523-785feabcd123");
    public static UUID emtbMACCharacteristicUuid = UUID.fromString("00002ae3-1212-efde-1523-785feabcd123");

    /**
     * Return a configured {@link BluetoothGattService} instance for the
     * Cycling Speed and Cadence Service
     */
    public static BluetoothGattService createEmtbModeService() {
        // Set supported feature: speed and/or cadence
//        currentFeature = feature;

        BluetoothGattService service = new BluetoothGattService(CSC_SERVICE,
                BluetoothGattService.SERVICE_TYPE_PRIMARY);

        // CSC Measurement characteristic
        BluetoothGattCharacteristic cscMeasurement = new BluetoothGattCharacteristic(CSC_MEASUREMENT,
                //Read-only characteristic, supports notifications
                BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                BluetoothGattCharacteristic.PERMISSION_READ);
        BluetoothGattDescriptor configDescriptor = new BluetoothGattDescriptor(CLIENT_CONFIG,
                //Read/write descriptor
                BluetoothGattDescriptor.PERMISSION_READ | BluetoothGattDescriptor.PERMISSION_WRITE);
        cscMeasurement.addDescriptor(configDescriptor);

        // CSC Feature characteristic
        BluetoothGattCharacteristic cscFeature = new BluetoothGattCharacteristic(CSC_FEATURE,
                //Read-only characteristic
                BluetoothGattCharacteristic.PROPERTY_READ,
                BluetoothGattCharacteristic.PERMISSION_READ);

        service.addCharacteristic(cscMeasurement);
        service.addCharacteristic(cscFeature);

        return service;
    }


    /**
     * Return a configured {@link BluetoothGattService} instance for the
     * Cycling Speed and Cadence Service
     */
    public static BluetoothGattService createCSCService(byte feature) {
        // Set supported feature: speed and/or cadence
        currentFeature = feature;

        BluetoothGattService service = new BluetoothGattService(CSC_SERVICE,
                BluetoothGattService.SERVICE_TYPE_PRIMARY);

        // CSC Measurement characteristic
        BluetoothGattCharacteristic cscMeasurement = new BluetoothGattCharacteristic(CSC_MEASUREMENT,
                //Read-only characteristic, supports notifications
                BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                BluetoothGattCharacteristic.PERMISSION_READ);
        BluetoothGattDescriptor configDescriptor = new BluetoothGattDescriptor(CLIENT_CONFIG,
                //Read/write descriptor
                BluetoothGattDescriptor.PERMISSION_READ | BluetoothGattDescriptor.PERMISSION_WRITE);
        cscMeasurement.addDescriptor(configDescriptor);

        // CSC Feature characteristic
        BluetoothGattCharacteristic cscFeature = new BluetoothGattCharacteristic(CSC_FEATURE,
                //Read-only characteristic
                BluetoothGattCharacteristic.PROPERTY_READ,
                BluetoothGattCharacteristic.PERMISSION_READ);

        service.addCharacteristic(cscMeasurement);
        service.addCharacteristic(cscFeature);

        return service;
    }

    /**
     * Return a configured {@link BluetoothGattService} instance for the
     * Heart Rate service
     */
    public static BluetoothGattService createHRService() {
        BluetoothGattService service = new BluetoothGattService(HR_SERVICE,
                BluetoothGattService.SERVICE_TYPE_PRIMARY);

        BluetoothGattCharacteristic hrMeasurement = new BluetoothGattCharacteristic(HR_MEASUREMENT,
                //Read-only characteristic, supports notifications
                BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                BluetoothGattCharacteristic.PERMISSION_READ);
        BluetoothGattDescriptor configDescriptor = new BluetoothGattDescriptor(CLIENT_CONFIG,
                //Read/write descriptor
                BluetoothGattDescriptor.PERMISSION_READ | BluetoothGattDescriptor.PERMISSION_WRITE);
        hrMeasurement.addDescriptor(configDescriptor);
        service.addCharacteristic(hrMeasurement);

        BluetoothGattCharacteristic bodyLocation = new BluetoothGattCharacteristic(BODY_SENSOR_LOCATION,
                //Read-only characteristic, supports notifications
                BluetoothGattCharacteristic.PROPERTY_READ,
                BluetoothGattCharacteristic.PERMISSION_READ);
        service.addCharacteristic(bodyLocation);

        return service;
    }


    public static BluetoothGattService createRscService() {
        BluetoothGattService service = new BluetoothGattService(RSC_SERVICE,
                BluetoothGattService.SERVICE_TYPE_PRIMARY);
        // CSC Measurement characteristic
        BluetoothGattCharacteristic rscMeasurement = new BluetoothGattCharacteristic(RSC_MEASUREMENT,
                //Read-only characteristic, supports notifications
                BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                BluetoothGattCharacteristic.PERMISSION_READ);

        BluetoothGattDescriptor configDescriptor = new BluetoothGattDescriptor(CLIENT_CONFIG,
                //Read/write descriptor
                BluetoothGattDescriptor.PERMISSION_READ | BluetoothGattDescriptor.PERMISSION_WRITE);
        rscMeasurement.addDescriptor(configDescriptor);

        // CSC Feature characteristic
        BluetoothGattCharacteristic rscFeature = new BluetoothGattCharacteristic(RSC_FEATURE,
                //Read-only characteristic
                BluetoothGattCharacteristic.PROPERTY_READ,
                BluetoothGattCharacteristic.PERMISSION_READ);
        service.addCharacteristic(rscFeature);


        service.addCharacteristic(rscMeasurement);
        return service;
    }
    // https://www.bluetooth.com/wp-content/uploads/Sitecore-Media-Library/Gatt/Xml/Characteristics/org.bluetooth.characteristic.csc_measurement.xml
    public static byte[] getMeasurement(long cumulativeWheelRevolution, int lastWheelEventTime,
                                        long cumulativeCrankRevolution, int lastCrankEventTime) {
        List<Byte> data = new ArrayList<>();
        data.add((byte) (currentFeature & 0x3)); // only preserve bit 0 and 1
        if ((currentFeature & CSC_FEATURE_WHEEL_REV) == CSC_FEATURE_WHEEL_REV) {
            // cumulative wheel revolutions (uint32), only take the last 4 bytes
            data.add((byte) cumulativeWheelRevolution);
            data.add((byte) (cumulativeWheelRevolution >> Byte.SIZE));
            data.add((byte) (cumulativeWheelRevolution >> Byte.SIZE * 2));
            data.add((byte) (cumulativeWheelRevolution >> Byte.SIZE * 3));

            // Last Wheel Event Time (uint16),  unit is 1/1024s, only take the last 2 bytes
            data.add((byte) lastWheelEventTime);
            data.add((byte) (lastWheelEventTime >> Byte.SIZE));
        }
        if ((currentFeature & CSC_FEATURE_CRANK_REV) == CSC_FEATURE_CRANK_REV) {
            // Cumulative Crank Revolutions (uint16)
            data.add((byte) cumulativeCrankRevolution);
            data.add((byte) (cumulativeCrankRevolution >> Byte.SIZE));

            // Last Crank Event Time (uint16) uint is 1/1024s
            data.add((byte) lastCrankEventTime);
            data.add((byte) (lastCrankEventTime >> Byte.SIZE));
        }

        // convert to primitive byte array
        byte[] byteArray = new byte[data.size()];
        for (int i = 0; i < data.size(); i++) {
            byteArray[i] = data.get(i);
        }
        //Log.v(TAG, "CSC Measurement: 0x" + bytesToHex(byteArray));
        return byteArray;
    }

    // https://www.bluetooth.com/wp-content/uploads/Sitecore-Media-Library/Gatt/Xml/Characteristics/org.bluetooth.characteristic.heart_rate_measurement.xml
    public static byte[] getHR(int hr, long lastHRTime) {
        List<Byte> data = new ArrayList<>();

        // Add the Flags, for our use they're all 0s
        data.add((byte) (0b00000000));
        data.add((byte) (hr));

        // convert to primitive byte array
        byte[] byteArray = new byte[data.size()];
        for (int i = 0; i < data.size(); i++) {
            byteArray[i] = data.get(i);
        }
        Log.v(TAG, "HR Measurement: 0x" + bytesToHex(byteArray));
        return byteArray;
    }

    // https://www.bluetooth.com/wp-content/uploads/Sitecore-Media-Library/Gatt/Xml/Characteristics/org.bluetooth.characteristic.rsc_measurement.xml
    public static byte[] getRsc(long lastSSDistance, float lastSSSpeed, long stridePerMin) {
        List<Byte> data = new ArrayList<>();

        // Instantanious stride length, total distance and walking or running could be calculated, but are not supported for now
        data.add((byte) 0b00000000);

        // Instantaneous Speed; Unit is in m/s with a resolution of 1/256 s (uint16)
        int wholeNumber = (int) lastSSSpeed;
        byte decimalPlaces = binaryDecimalToByte(lastSSSpeed, wholeNumber);
        data.add(decimalPlaces);
        data.add((byte) (int) wholeNumber);

        // Instantanious Cadence, Unit is in 1/minute (or RPM) with a resolutions of 1 1/min (or 1 RPM) (uint8)
        data.add((byte) stridePerMin);

        // convert to primitive byte array
        byte[] byteArray = new byte[data.size()];
        for (int i = 0; i < data.size(); i++) {
            byteArray[i] = data.get(i);
        }
        return byteArray;
    }

    private static byte binaryDecimalToByte(float lastSSSpeed, int wholeNumber) {
        double number;
        double fraction;
        int integralPart;
        int b = 0;
        fraction = lastSSSpeed - wholeNumber;
        for (int i = 7; i >= 0; i--) {
            integralPart = (int) (fraction * 2);
            if (integralPart == 1) {
                b = b | 0b1 << i;
            }
            number = fraction * 2;
            fraction = number - integralPart;
        }
        return (byte) b;
    }

    private final static char[] hexArray = "0123456789ABCDEF".toCharArray();
    private static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for ( int j = 0; j < bytes.length; j++ ) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }

    // https://www.bluetooth.com/wp-content/uploads/Sitecore-Media-Library/Gatt/Xml/Characteristics/org.bluetooth.characteristic.csc_feature.xml
    public static byte[] getFeature() {
        byte[] data = new byte[2];
        data[0] = currentFeature; // always leave the second byte 0
        return data;
    }

    // https://www.bluetooth.com/wp-content/uploads/Sitecore-Media-Library/Gatt/Xml/Characteristics/org.bluetooth.characteristic.rsc_feature.xml
    public static byte[] getRscFeature() {
        byte[] data = new byte[1];
        data[0] = rscFeature;
        return data;
    }
}
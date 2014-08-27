package com.kcoppock.bluetoothconnector;

import android.bluetooth.BluetoothA2dp;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.content.Intent;
import android.os.Bundle;
import android.app.Activity;
import android.util.Log;
import android.widget.Toast;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;
/**
 *
 * Copyright 2013 Kevin Coppock
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Main activity which handles the flow of connecting to the requested Bluetooth A2DP device
 */
public class BluetoothActivity extends Activity implements BluetoothBroadcastReceiver.Callback, BluetoothA2DPRequester.Callback {
    private static final String TAG = "BluetoothActivity";

    /**
     * This is the name of the device to connect to. You can replace this with the name of
     * your device.
     */
    private static final String BT_DEVICE_NAME = "(5C)Logitech Adapter";
    private static final String BT_DEVICE_ADDRESS = "C8:84:47:03:F6:5C";
    private static final String LAUNCH_PACKAGE = "com.plexapp.android";

    /**
     * Local reference to the device's BluetoothAdapter
     */
    private BluetoothAdapter mAdapter;

    @Override
    protected void onCreate (Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //Store a local reference to the BluetoothAdapter
        mAdapter = BluetoothAdapter.getDefaultAdapter();

        //Already connected, skip the rest
        if (mAdapter.isEnabled()) {
            onBluetoothConnected();
            return;
        }

        //Check if we're allowed to enable Bluetooth. If so, listen for a
        //successful enabling
        if (mAdapter.enable()) {
            BluetoothBroadcastReceiver.register(this, this);
        } else {
            Log.e(TAG, "Unable to enable Bluetooth. Is Airplane Mode enabled?");
        }
    }

    @Override
    public void onBluetoothError () {
        Log.e(TAG, "There was an error enabling the Bluetooth Adapter.");
    }

    @Override
    public void onBluetoothConnected () {
        new BluetoothA2DPRequester(this).request(this, mAdapter);
    }

    @Override
    public void onA2DPProxyReceived (BluetoothA2dp proxy) {
        Method connect = getConnectMethod();
        Method disconnect = getDisonnectMethod();
        BluetoothDevice device = findBondedDeviceByAddress(mAdapter, BT_DEVICE_ADDRESS);

        //If either is null, just return. The errors have already been logged
        if (connect == null || device == null) {
            return;
        }

        if (proxy.getConnectionState(device) == BluetoothProfile.STATE_DISCONNECTED) {
            try {
                connect.setAccessible(true);
                connect.invoke(proxy, device);

                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        Toast.makeText(getApplicationContext(), "Bluetooth Connected", Toast.LENGTH_SHORT).show();
                        Intent launcher = getPackageManager().getLaunchIntentForPackage(LAUNCH_PACKAGE);
                        startActivity(launcher);
                        finish();
                    }
                });
            } catch (InvocationTargetException ex) {
                Log.e(TAG, "Unable to invoke connect(BluetoothDevice) method on proxy. " + ex.toString());
            } catch (IllegalAccessException ex) {
                Log.e(TAG, "Illegal Access! " + ex.toString());
            }
        }

        if(proxy.getConnectionState(device) == BluetoothProfile.STATE_CONNECTED) {
            try {
                disconnect.setAccessible(true);
                disconnect.invoke(proxy, device);

                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        Toast.makeText(getApplicationContext(), "Bluetooth Disconnected", Toast.LENGTH_SHORT).show();
                        finish();
                    }
                });
            } catch (InvocationTargetException ex) {
                Log.e(TAG, "Unable to invoke disconnect(BluetoothDevice) method on proxy. " + ex.toString());
            } catch (IllegalAccessException ex) {
                Log.e(TAG, "Illegal Access! " + ex.toString());
            }
        }
    }

    /**
     * Wrapper around some reflection code to get the hidden 'connect()' method
     * @return the connect(BluetoothDevice) method, or null if it could not be found
     */
    private Method getConnectMethod () {
        try {
            return BluetoothA2dp.class.getDeclaredMethod("connect", BluetoothDevice.class);
        } catch (NoSuchMethodException ex) {
            Log.e(TAG, "Unable to find connect(BluetoothDevice) method in BluetoothA2dp proxy.");
            return null;
        }
    }

    private Method getDisonnectMethod () {
        try {
            return BluetoothA2dp.class.getDeclaredMethod("disconnect", BluetoothDevice.class);
        } catch (NoSuchMethodException ex) {
            Log.e(TAG, "Unable to find disconnect(BluetoothDevice) method in BluetoothA2dp proxy.");
            return null;
        }
    }

    /**
     * Search the set of bonded devices in the BluetoothAdapter for one that matches
     * the given name
     * @param adapter the BluetoothAdapter whose bonded devices should be queried
     * @param name the name of the device to search for
     * @return the BluetoothDevice by the given name (if found); null if it was not found
     */
    private static BluetoothDevice findBondedDeviceByName (BluetoothAdapter adapter, String name) {
        for (BluetoothDevice device : getBondedDevices(adapter)) {
            if (name.matches(device.getName())) {
                Log.v(TAG, String.format("Found device with name %s and address %s.", device.getName(), device.getAddress()));
                return device;
            }
        }
        Log.w(TAG, String.format("Unable to find device with name %s.", name));
        return null;
    }

    private static BluetoothDevice findBondedDeviceByAddress (BluetoothAdapter adapter, String address) {
        for (BluetoothDevice device : getBondedDevices(adapter)) {
            if(device.getAddress().equals(address)) {
                Log.v(TAG, String.format("Found device with name %s and address %s.", device.getName(), device.getAddress()));
                return device;
            }
        }
        Log.w(TAG, String.format("Unable to find device with address %s.", address));
        return null;
    }

    /**
     * Safety wrapper around BluetoothAdapter#getBondedDevices() that is guaranteed
     * to return a non-null result
     * @param adapter the BluetoothAdapter whose bonded devices should be obtained
     * @return the set of all bonded devices to the adapter; an empty set if there was an error
     */
    private static Set<BluetoothDevice> getBondedDevices (BluetoothAdapter adapter) {
        Set<BluetoothDevice> results = adapter.getBondedDevices();
        if (results == null) {
            results = new HashSet<BluetoothDevice>();
        }
        return results;
    }
}

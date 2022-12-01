package com.example.btchat;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.UUID;


public class MainActivity extends AppCompatActivity implements AdapterView.OnItemClickListener {
    private static final String TAG = "MainActivity";

    BluetoothAdapter mBluetoothAdapter;
    Button btnEnableDisable_Discoverable;

    BluetoothChatService mBluetoothChat;

    Button btnStartConnection;
    Button btnSend;

    EditText etSend;

    private static final UUID MY_UUID_SECURE = UUID.fromString("fa87c0d0-afac-11de-8a39-0800200c9a66");

    BluetoothDevice mBTDevice;

    public ArrayList<BluetoothDevice> mBTDevices = new ArrayList<>();

    public DeviceListAdapter mDeviceListAdapter;

    ListView lvNewDevices;


    private static final String[] MY_BLUETOOTH_PERMISSIONS = {
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_PRIVILEGED,
            Manifest.permission.BLUETOOTH_SCAN
    };

    /*

    private void verifyPermissions() {
        Log.d(TAG, "verifyPermissions: Checking Permissions");

        // checkSelfPermission() returns 0 if application has been granted permission
        // permsBluetoothCheck = 0 + 0 + 0 + 0 + 0 + 0 + 0 + 0 = 0 If all permissions have been granted
        int permsBluetoothCheck = ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH)
                + ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADMIN)
                + ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADVERTISE)
                + ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                + ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_PRIVILEGED)
                + ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN)
                + ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                + ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION);

        // check if permsBluetoothCheck = 0 ( 0 = PERMISSION_GRANTED )
        if (permsBluetoothCheck != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, MY_BLUETOOTH_PERMISSIONS, 1);
        }
    }

    */

    // Create a BroadcastReceiver for ACTION_FOUND
    private final BroadcastReceiver mBroadcastReceiver1 = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            // When discovery finds a device
            if (action.equals(mBluetoothAdapter.ACTION_STATE_CHANGED)) {
                final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, mBluetoothAdapter.ERROR);

                switch (state) {
                    case BluetoothAdapter.STATE_OFF:
                        Log.d(TAG, "onReceive: STATE OFF");
                        break;
                    case BluetoothAdapter.STATE_TURNING_OFF:
                        Log.d(TAG, "mBroadcastReceiver1: STATE TURNING OFF");
                        break;
                    case BluetoothAdapter.STATE_ON:
                        Log.d(TAG, "mBroadcastReceiver1: STATE ON");
                        break;
                    case BluetoothAdapter.STATE_TURNING_ON:
                        Log.d(TAG, "mBroadcastReceiver1: STATE TURNING ON");
                        break;
                }
            }
        }
    };

    /**
     * Broadcast Receiver for changes made to bluetooth states such as:
     * 1) Discoverability mode on/off or expire.
     */
    private final BroadcastReceiver mBroadcastReceiver2 = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();

            if (action.equals(BluetoothAdapter.ACTION_SCAN_MODE_CHANGED)) {

                int mode = intent.getIntExtra(BluetoothAdapter.EXTRA_SCAN_MODE, BluetoothAdapter.ERROR);

                switch (mode) {
                    //Device is in Discoverable Mode
                    case BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE:
                        Log.d(TAG, "mBroadcastReceiver2: Discoverability Enabled.");
                        break;
                    //Device not in discoverable mode
                    case BluetoothAdapter.SCAN_MODE_CONNECTABLE:
                        Log.d(TAG, "mBroadcastReceiver2: Discoverability Disabled. Able to receive connections.");
                        break;
                    case BluetoothAdapter.SCAN_MODE_NONE:
                        Log.d(TAG, "mBroadcastReceiver2: Discoverability Disabled. Not able to receive connections.");
                        break;
                    case BluetoothAdapter.STATE_CONNECTING:
                        Log.d(TAG, "mBroadcastReceiver2: Connecting....");
                        break;
                    case BluetoothAdapter.STATE_CONNECTED:
                        Log.d(TAG, "mBroadcastReceiver2: Connected.");
                        break;
                }

            }
        }
    };


    /**
     * Broadcast Receiver for listing devices that are not yet paired
     * -Executed by btnDiscover() method.
     */
    private BroadcastReceiver mBroadcastReceiver3 = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            Log.d(TAG, "onReceive: ACTION FOUND.");

            if (action.equals(BluetoothDevice.ACTION_FOUND)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                mBTDevices.add(device);
                Log.d(TAG, "onReceive: " + device.getName() + ": " + device.getAddress());
                mDeviceListAdapter = new DeviceListAdapter(context, R.layout.device_adapter_view, mBTDevices);
                lvNewDevices.setAdapter(mDeviceListAdapter);
            }
        }
    };

    /**
     * Broadcast Receiver that detects bond state changes (Pairing status changes)
     */
    private final BroadcastReceiver mBroadcastReceiver4 = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();

            if (action.equals(BluetoothDevice.ACTION_BOND_STATE_CHANGED)) {
                BluetoothDevice mDevice = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                //int permBLUETOOTH_SCAN = ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT);

                // request BLUETOOTH_SCAN permission if not granted
                //if (permBLUETOOTH_SCAN != PackageManager.PERMISSION_GRANTED) {
                //    ActivityCompat.requestPermissions((Activity) context, new String[] {"android.permission.BLUETOOTH_CONNECT"}, 1);
                //    return;
                //} else {

                    //3 cases:
                    //case1: bonded already
                    if (mDevice.getBondState() == BluetoothDevice.BOND_BONDED) {
                        Log.d(TAG, "BroadcastReceiver: BOND_BONDED.");

                        mBTDevice = mDevice;
                    }
                    //case2: creating a bond
                    if (mDevice.getBondState() == BluetoothDevice.BOND_BONDING) {
                        Log.d(TAG, "BroadcastReceiver: BOND_BONDING.");
                    }
                    //case3: breaking a bond
                    if (mDevice.getBondState() == BluetoothDevice.BOND_NONE) {
                        Log.d(TAG, "BroadcastReceiver: BOND_NONE.");
                    }
            //    }
            }
        }
    };



    @Override
    protected void onDestroy() {
        Log.d(TAG, "onDestroy: called.");
        super.onDestroy();
        unregisterReceiver(mBroadcastReceiver1);
        unregisterReceiver(mBroadcastReceiver2);
        unregisterReceiver(mBroadcastReceiver3);
        unregisterReceiver(mBroadcastReceiver4);
        //mBluetoothAdapter.cancelDiscovery();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Button btnONOFF = (Button) findViewById(R.id.btnONOFF);
        btnEnableDisable_Discoverable = (Button) findViewById(R.id.btnDiscoverable_on_off);
        lvNewDevices = (ListView) findViewById(R.id.lvNewDevices);
        mBTDevices = new ArrayList<>();

        btnStartConnection = (Button) findViewById(R.id.btnStartConnection);
        btnSend = (Button) findViewById(R.id.btnSend);
        etSend = (EditText) findViewById(R.id.editText);

        //Broadcasts when bond state changes (ie:pairing)
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        registerReceiver(mBroadcastReceiver4, filter);

        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        lvNewDevices.setOnItemClickListener(MainActivity.this);

        // verifyPermissions();

        btnONOFF.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.d(TAG, "onClick: enabling/disabling bluetooth.");
                enableDisableBT();
            }
        });

        // This button will start the connectThread
        btnStartConnection.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startConnection();
            }
        });

        btnSend.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                byte[] bytes = etSend.getText().toString().getBytes(Charset.defaultCharset());
                mBluetoothChat.write(bytes);
            }
        });
    }

    /**
     * create method for starting connection
     * While both devices are running their acceptThread, one of them has to
     * start their connectThread and attempt to make a connection.
     * Remember: the connection will fail and the app will crash if you haven't paired first.
     */
    public void startConnection(){
        startBTConnection(mBTDevice, MY_UUID_SECURE);
    }

    /**
     * starting chat service method
     */
    public void startBTConnection(BluetoothDevice device, UUID uuid){
        Log.d(TAG, "startBluetoothConnection: Initializing RFCOMM Bluetooth Connection");

        mBluetoothChat.startClient(device, uuid);
    }

    public void enableDisableBT(){
        if(mBluetoothAdapter == null){
            Log.d(TAG, "enableDisableBT: Does not have BT capabilities.");
        }
        if(!mBluetoothAdapter.isEnabled()){
            Log.d(TAG, "enableDisableBT: enabling BT.");
            Intent enableBTIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivity(enableBTIntent);

            IntentFilter BTIntent = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
            registerReceiver(mBroadcastReceiver1, BTIntent);
        }
        if(mBluetoothAdapter.isEnabled()){
            Log.d(TAG, "enableDisableBT: disabling BT.");
            mBluetoothAdapter.disable();

            IntentFilter BTIntent = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
            registerReceiver(mBroadcastReceiver1, BTIntent);
        }

    }


    public void btnEnableDisable_Discoverable(View view) {
        Log.d(TAG, "btnEnableDisable_Discoverable: Making device discoverable for 300 seconds.");

        Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
        discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
        startActivity(discoverableIntent);

        IntentFilter intentFilter = new IntentFilter(mBluetoothAdapter.ACTION_SCAN_MODE_CHANGED);
        registerReceiver(mBroadcastReceiver2,intentFilter);

    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    public void btnDiscover(View view) {
        Log.d(TAG, "btnDiscover: Looking for unpaired devices.");

        if(mBluetoothAdapter.isDiscovering()){
            mBluetoothAdapter.cancelDiscovery();
            Log.d(TAG, "btnDiscover: Canceling discovery.");

            //check BT permissions in manifest
            checkBTPermissions();

            mBluetoothAdapter.startDiscovery();
            IntentFilter discoverDevicesIntent = new IntentFilter(BluetoothDevice.ACTION_FOUND);
            registerReceiver(mBroadcastReceiver3, discoverDevicesIntent);
        }
        if(!mBluetoothAdapter.isDiscovering()){

            //check BT permissions in manifest
            checkBTPermissions();

            mBluetoothAdapter.startDiscovery();
            IntentFilter discoverDevicesIntent = new IntentFilter(BluetoothDevice.ACTION_FOUND);
            registerReceiver(mBroadcastReceiver3, discoverDevicesIntent);
        }
    }

    /**
     * This method is required for all devices running API23+
     * Android must programmatically check the permissions for bluetooth. Putting the proper permissions
     * in the manifest is not enough.
     *
     * NOTE: This will only execute on versions > LOLLIPOP because it is not needed otherwise.
     */
    @RequiresApi(api = Build.VERSION_CODES.M)
    private void checkBTPermissions() {
        if(Build.VERSION.SDK_INT > Build.VERSION_CODES.LOLLIPOP){
            int permissionCheck = this.checkSelfPermission("Manifest.permission.ACCESS_FINE_LOCATION")
                    + this.checkSelfPermission("Manifest.permission.ACCESS_COARSE_LOCATION")
                    + this.checkSelfPermission("Manifest.permission.BLUETOOTH")
                    + this.checkSelfPermission("Manifest.permission.BLUETOOTH_ADMIN")
                    + this.checkSelfPermission("Manifest.permission.BLUETOOTH_ADVERTISE")
                    + this.checkSelfPermission("Manifest.permission.BLUETOOTH_CONNECT")
                    + this.checkSelfPermission("Manifest.permission.BLUETOOTH_PRIVILEGED")
                    + this.checkSelfPermission("Manifest.permission.BLUETOOTH_SCAN");


            if (permissionCheck != 0) {

                this.requestPermissions(MY_BLUETOOTH_PERMISSIONS, 1001); //Any number
            }
        }else{
            Log.d(TAG, "checkBTPermissions: No need to check permissions. SDK version < LOLLIPOP.");
        }
    }

    @Override
    public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
        //first cancel discovery because its very memory intensive.
        mBluetoothAdapter.cancelDiscovery();

        Log.d(TAG, "onItemClick: You Clicked on a device.");
        String deviceName = mBTDevices.get(i).getName();
        String deviceAddress = mBTDevices.get(i).getAddress();

        Log.d(TAG, "onItemClick: deviceName = " + deviceName);
        Log.d(TAG, "onItemClick: deviceAddress = " + deviceAddress);

        //create the bond.
        //NOTE: Requires API 17+? I think this is JellyBean
        if(Build.VERSION.SDK_INT > Build.VERSION_CODES.JELLY_BEAN_MR2){
            Log.d(TAG, "Trying to pair with " + deviceName);
            mBTDevices.get(i).createBond();

            mBTDevice = mBTDevices.get(i);
            mBluetoothChat = new BluetoothChatService(MainActivity.this);
        }
    }
}

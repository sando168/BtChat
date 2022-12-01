package com.example.btchat;

import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.UUID;

public class BluetoothChatService {

    // Used for Log debugging statements
    private static final String TAG = "BluetoothChatServ";
    // Name for the SDP record when creating server socket
    private static final String app_name = "BtChatName";
    // Unique UUID for this application
    private static final UUID MY_UUID_SECURE = UUID.fromString("fa87c0d0-afac-11de-8a39-0800200c9a66");

    // AcceptThread class to accept bluetooth connection
    private AcceptThread mSecureAcceptThread;
    // ConnectThread class to establish a bluetooth connection
    private ConnectThread mConnectThread;
    // ConnectedThread class handles communication data from connected devices
    private ConnectedThread mConnectedThread;

    // Create objects for the connecting device
    private BluetoothDevice btDevice;
    private UUID deviceUUID;

    ProgressDialog mProgressDialog;

    private final BluetoothAdapter mBluetoothAdapter;
    Context mContext;

    // BluetoothChatService constructor
    public BluetoothChatService(Context context) {
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        mContext = context;
        start();
    }

    /**
     * AcceptThread class runs on another thread so it doesn't use up the
     * main resources on the MainActivity thread.
     *
     * This thread runs while listening for incoming connections. It behaves
     * like a server-side client. It runs until a connection is accepted
     * (or until cancelled).
     */
    private class AcceptThread extends Thread{
        // The local server socket
        private final BluetoothServerSocket mServerSocket;

        private AcceptThread(){
            BluetoothServerSocket tmp = null;

            try {
                // Create a new listening server socket
                tmp = mBluetoothAdapter.listenUsingRfcommWithServiceRecord(app_name, MY_UUID_SECURE);
                Log.d(TAG, "AcceptThread: Setting up server using: " + MY_UUID_SECURE);
            } catch (IOException e){
                Log.e(TAG, "AcceptThread: IOException: " + e.getMessage() );
            }
            // Initialize class variable mServerSocket
            mServerSocket = tmp;
        }

        public void run(){
            Log.d(TAG, "run: AcceptThread running.");

            BluetoothSocket btSocket = null;

            try {
                Log.d(TAG, "run: RFCOMM server socket start......");

                // This is a blocking call and will only return on
                // a successful connection or an exception
                btSocket = mServerSocket.accept();

                Log.d(TAG, "run: RFCOMM server socket accepted connection.");
            } catch (IOException e){
                Log.e(TAG, "AcceptThread: IOException: " + e.getMessage() );
            }

            if(btSocket != null){
                connected(btSocket, btDevice);
            }

            Log.i(TAG, "AcceptThread ENDED");
        }

        public void cancel(){
            Log.d(TAG, "cancel: Cancelling AcceptThread.");

            try {
                mServerSocket.close();
            } catch (IOException e){
                Log.e(TAG, "cancel: close of AcceptThread server socket failed." + e.getMessage());
            }
        }
    }

    /**
     * This thread runs while trying to make an outgoing connection
     * with a device. It runs straight through; the connection either
     * succeeds or fails.
     */
    private class ConnectThread extends Thread {

        private BluetoothSocket mSocket;

        public ConnectThread(BluetoothDevice device, UUID uuid) {
            Log.d(TAG, "ConnectThread: started");
            btDevice = device;
            deviceUUID = uuid;
        }

        public void run() {
            BluetoothSocket tmp = null;
            Log.d(TAG, "run: ConnectThread running.");

            try {
                Log.d(TAG, "ConnectThread: trying to create a secure RFCOMM socket using UUID: " + MY_UUID_SECURE);

                // Get a BluetoothSocket for a connection with
                // the given BluetoothDevice
                tmp = btDevice.createRfcommSocketToServiceRecord(deviceUUID);

            } catch (IOException e) {
                Log.d(TAG, "ConnectThread: could not create a secure RFCOMM socket. " + e.getMessage());
            }
            // Initialize class variable mSocket
            mSocket = tmp;

            // Always cancel discovery as it is memory intensive
            mBluetoothAdapter.cancelDiscovery();

            // Make a connection to the BluetoothSocket
            // This is a blocking call and will only return on
            // a successful connection or an exception
            try {
                mSocket.connect();

                Log.d(TAG, "run: ConnectThread connected");
            } catch (IOException e) {

                try {
                    // Close the socket
                    mSocket.close();
                    Log.d(TAG, "run: closed socket");
                } catch (IOException e1) {
                    Log.e(TAG, "mConnectThread: run: Unable to close connection in socket" + e1.getMessage());
                }
                Log.e(TAG, "run: ConnectThread: could not connect to UUID: " + MY_UUID_SECURE);
            }
            connected(mSocket, btDevice);
        }

        public void cancel() {
            Log.d(TAG, "cancel: Cancelling ConnectThread.");

            try {
                Log.d(TAG, "cancel: closing client socket.");
                mSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "cancel: close of ConnectThread socket failed." + e.getMessage());
            }
        }
    }

    private class ConnectedThread extends Thread{
        private final BluetoothSocket btSocket1;
        private final InputStream mInputStream;
        private final OutputStream mOutputStream;

        public ConnectedThread(BluetoothSocket socket){
            Log.d(TAG, "ConnectedThread: started.");

            btSocket1 = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            // Dismiss the progressdialog box when connection is established
            try {
                mProgressDialog.dismiss();
            } catch (NullPointerException e){
                e.printStackTrace();
            }

            try {
                tmpIn = btSocket1.getInputStream();
                tmpOut = btSocket1.getOutputStream();
            } catch (IOException e) {
                e.printStackTrace();
            }

            mInputStream = tmpIn;
            mOutputStream = tmpOut;
        }

        public void run(){
            // Byte array object to get the input from the InputStream
            byte[] buffer = new byte[1024];
            // Bytes returned from read()
            int bytes;

            // Keep listening to the InputStream until an exception occurs
            while(true){
                // Read from the InputStream
                try {
                    bytes = mInputStream.read(buffer);
                    String incomingMessage = new String(buffer, 0, bytes);
                    Log.d(TAG, "InputStream: " + incomingMessage);
                } catch (IOException e) {
                    Log.e(TAG, "write: error reading InputStream" + e.getMessage());
                    break;
                }
            }
        }

        // Call this from the MainActivity to write data to the remote device
        public void write(byte[] bytes){
            String text = new String(bytes, Charset.defaultCharset());
            Log.d(TAG, "write: writing to OutputStream: " + text);
            try {
                mOutputStream.write(bytes);
            } catch (IOException e) {
                Log.e(TAG, "write: error writing to OutputStream " + e.getMessage());
            }
        }

        // Call this from the MainActivity to shutdown the connection
        public void cancel(){
            Log.d(TAG, "cancel: cancelling ConnectedThread");
            try {
                btSocket1.close();
            } catch (IOException e) {
                Log.e(TAG, "write: error writing to OutputStream " + e.getMessage());
            }

        }
    }

    /**
     * Start the chat service. Specifically start AcceptThread to begin a
     * session in listening (server) mode. Called by the Activity onResume()
     */
    public synchronized void start(){
        Log.d(TAG, "start");
        // Cancel any thread attempting to make a connection
        if (mConnectThread != null){
            mConnectThread.cancel();
            mConnectThread = null;
        }
        if (mSecureAcceptThread != null){
            mSecureAcceptThread = new AcceptThread();
            mSecureAcceptThread.start();
        }
    }

    /**
     * AcceptThread starts and sits waiting for a connection, then
     * ConnectThread starts and attempts to make a connection with
     * the other device's AcceptThread.
     */
    public void startClient(BluetoothDevice device, UUID uuid){
        Log.d(TAG, "startClient: started.");

        // initprogress dialog
        mProgressDialog = ProgressDialog.show(mContext, "Connecting Bluetooth", "Please wait...", true);

        // Initialize and start ConnectThread class
        mConnectThread = new ConnectThread(device, uuid);
        mConnectThread.start();
    }

    private void connected(BluetoothSocket mSocket, BluetoothDevice btDevice){
        Log.d(TAG, "connected: starting.");

        // Start the thread to manage the connection and perform transmissions
        mConnectedThread = new ConnectedThread(mSocket);
        mConnectedThread.start();
    }

    /**
     * Write to the ConnectedThread in an unsynchronized manner
     *
     * @param out The bytes to write
     * @see ConnectedThread#write(byte[])
     */
    public void write(byte[] out) {
        // Create temporary object
        ConnectedThread r;

        // Synchronize a copy of the ConnectedThread
        Log.d(TAG,"write: write called.");
        // Perform the write unsynchronized
        mConnectedThread.write(out);
    }
}

package com.adlrg.bluetooth;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by Andres on 20/01/2016.
 */
public class BluetoothSerialClient {

    static final private String SERIAL_UUID = "00001101-0000-1000-8000-00805F9B34FB";
    private static BluetoothSerialClient bsc;

    private BluetoothUpListener btUpListener;
    private ScanListener scanListener;

    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private BluetoothStreamingHandler btStreamingHandler;
    private UUID mUUID = UUID.fromString(SERIAL_UUID);

    private BluetoothAdapter adapter;
    private ExecutorService readExecutor;
    private ExecutorService writeExecutor;
    private BluetoothDevice connectedDevice;
    private BluetoothSocket btSocket;

    private InputStream is;
    private OutputStream os;

    private boolean connected;

    /**
     * EL constructor es privado para utilizar el patron
     * de diseño singleton
     */
    private BluetoothSerialClient() {
        adapter = BluetoothAdapter.getDefaultAdapter();
        writeExecutor = Executors.newSingleThreadExecutor();
        readExecutor = Executors.newSingleThreadExecutor();
    }

    /**
     * Este método obtiene una nueva instancia de la clase si
     * no ha sido creada con anterioridad de otra manera regresa
     * la instancia que ya había creado
     *
     * @return una instancia de la clase
     */
    @Nullable
    public static BluetoothSerialClient getInstance() {
        if (bsc == null)
            bsc = new BluetoothSerialClient();
        if (bsc.adapter == null) {
            bsc = null;
        }
        return bsc;
    }

    /**
     * Revisa si el adaptador bluetooth esta habilitado
     * @return <code>true</code> si el adapatador esta habilitado
     * <code>false</code> de otra manera
     */
    public boolean isEnabled() {
        return adapter.isEnabled();
    }

    public void enableBluetooth(Context context, BluetoothUpListener btUpListener) {
        if (!adapter.isEnabled()) {
            Intent intent = new Intent(context, BluetoothUpActivity.class);
            context.startActivity(intent);
        } else {
            btUpListener.onBluetoothUp(true);
        }
    }

    /**
     * Elimina la instancia creada y cierra los ejectores de lectura y escritura
     */
    public void clear() {
        close();
        readExecutor.shutdownNow();
        writeExecutor.shutdownNow();
        bsc = null;
    }

    public boolean connect(final Context context, final BluetoothDevice device, final BluetoothStreamingHandler bluetoothStreamingHandler) {
        if (!isEnabled()) return false;
        connectedDevice = device;
        btStreamingHandler = bluetoothStreamingHandler;
        if (connected) {
            writeExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        connected = false;
                        btSocket.close();
                        Thread.sleep(2000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    connect(context, device, bluetoothStreamingHandler);
                }
            });
        }
        else {
            connected = true;
            connectClient();
        }
        return true;
    }

    public boolean scanDevices(Context context, ScanListener OnScanListener) {
        if (!adapter.isEnabled()) return false;
        if (adapter.isDiscovering()) {
            adapter.cancelDiscovery();
            try {
                context.unregisterReceiver(discoveryReceiver);
            } catch (IllegalArgumentException e) {
                e.printStackTrace();
            }
        }
        scanListener = OnScanListener;
        IntentFilter filterFound = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        filterFound.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
        filterFound.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        context.registerReceiver(discoveryReceiver, filterFound);
        adapter.startDiscovery();
        return true;
    }

    public Set<BluetoothDevice> getPairedDevices() {
        Set<BluetoothDevice> pairedDevices = adapter.getBondedDevices();
        return pairedDevices;
    }

    public void cancelScan(Context context) {
        if (!adapter.isEnabled() || !adapter.isDiscovering()) return;
        adapter.cancelDiscovery();
        try {
            context.unregisterReceiver(discoveryReceiver);
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        }
        if (scanListener != null)
            scanListener.onFinish();
    }

    public BluetoothDevice getConnectedDevice() {
        return connectedDevice;
    }

    private void connectClient() {
        try {
            btSocket = connectedDevice.createRfcommSocketToServiceRecord(mUUID);
        } catch (IOException e) {
            close();
            e.printStackTrace();
            btStreamingHandler.onError(e);
            return;
        }
        writeExecutor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    adapter.cancelDiscovery();
                    btSocket.connect();
                    manageConnectedSocket(btSocket);
                    callConnectedHandlerEvent();
                    readExecutor.execute(readRunnable);
                } catch (final IOException e) {
                    close();
                    e.printStackTrace();
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            btStreamingHandler.onError(e);
                        }
                    });
                    connected = false;
                    try {
                        btSocket.close();
                    } catch (Exception ec) {
                        ec.printStackTrace();
                    }
                }
            }
        });
    }

    private void manageConnectedSocket(BluetoothSocket socket) throws IOException {
        is =  socket.getInputStream();
        os = socket.getOutputStream();
    }

    private void callConnectedHandlerEvent() {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                btStreamingHandler.onConnected();
            }
        });
    }

    private boolean write(final byte[] buffer) {
        if(!connected) return false;
        writeExecutor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    os.write(buffer);
                } catch (Exception e) {
                    close();
                    e.printStackTrace();
                    btStreamingHandler.onError(e);
                }
            }
        });
        return true;
    }

    public boolean isConnected() {
        return connected;
    }

    /**
     * Cierra la conneción con el dispositivo bluetooth actualmente enlazado
     *
     * @return <code>true</code> si hay una conección con un
     * dispositivo bluetooth y se puedo cerrar
     * <code>false</code> si no hay conección con un
     * dispositivo bluetooth
     */
    private boolean close() {
        connectedDevice = null;
        if (connected) {
            connected = false;
            try {
                btSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            //mMainHandler.post(mCloseRunable);
            return true;
        }
        return false;
    }


    public interface BluetoothUpListener {
        void onBluetoothUp(boolean success);
    }

    public interface ScanListener {
        void onStart();

        void onFoundDevice(BluetoothDevice bluetoothDevice);

        void onFinish();
    }

    public abstract static class BluetoothStreamingHandler {
        public abstract void onError(Exception e);

        public abstract void onConnected();

        public abstract void onDisconnected();

        public abstract void onData(byte[] buffer, int length);

        public final boolean close() {
            BluetoothSerialClient bt = getInstance();
            return bt != null && bt.close();
        }

        public final boolean write(byte[] buffer) {
            BluetoothSerialClient bt = getInstance();
            return bt != null && bt.write(buffer);
        }
    }

    public static class BluetoothUpActivity extends Activity {
        private static int REQUEST_ENABLE_BT = 2;

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            getWindow().getDecorView().postDelayed(new Runnable() {
                @Override
                public void run() {
                    upBluetoothDevice();
                }
            }, 100);
        }

        private void upBluetoothDevice() {
            BluetoothAdapter btAdapter = BluetoothAdapter.getDefaultAdapter();
            if (!btAdapter.isEnabled()) {
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            }
        }

        @Override
        protected void onActivityResult(int requestCode, int resultCode, Intent data) {
            super.onActivityResult(requestCode, resultCode, data);
            if (requestCode == REQUEST_ENABLE_BT) {
                BluetoothUpListener onBluetoothEnabledListener = getInstance().btUpListener;
                if (resultCode == Activity.RESULT_OK) {
                    if (onBluetoothEnabledListener != null)
                        onBluetoothEnabledListener.onBluetoothUp(true);
                    finish();
                } else {
                    if (onBluetoothEnabledListener != null)
                        onBluetoothEnabledListener.onBluetoothUp(false);
                    finish();
                }
            }
        }
    }

    private Runnable closeRunnable = new Runnable() {
        @Override
        public void run() {
            if (btStreamingHandler != null) {
                btStreamingHandler.onDisconnected();
            }
        }
    };

    private Runnable readRunnable = new Runnable() {
        @Override
        public void run() {
            try {
                final byte[] buffer = new byte[256];
                final int readBytes = is.read(buffer);
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (btStreamingHandler != null) {
                            btStreamingHandler.onData(buffer, readBytes);
                        }
                    }
                });
                readExecutor.execute(readRunnable);
            } catch (Exception e) {
                close();
                e.printStackTrace();
            }
        }
    };


    private BroadcastReceiver discoveryReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (scanListener != null) scanListener.onFoundDevice(device);
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                if (scanListener != null) scanListener.onFinish();
                try {
                    context.unregisterReceiver(discoveryReceiver);
                } catch (IllegalArgumentException e) {
                    e.printStackTrace();
                }
            } else if (BluetoothAdapter.ACTION_DISCOVERY_STARTED.equals(action)) {
                if (scanListener != null) scanListener.onStart();
            }
        }
    };

}

package com.adlrg.morse;

import android.app.ProgressDialog;
import android.bluetooth.BluetoothDevice;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.method.ScrollingMovementMethod;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.Set;

import com.adlrg.bluetooth.BluetoothSerialClient;
import com.adlrg.bluetooth.BluetoothSerialClient.BluetoothStreamingHandler;
import com.adlrg.bluetooth.BluetoothSerialClient.BluetoothUpListener;
import com.adlrg.bluetooth.BluetoothSerialClient.ScanListener;

import adlrg.com.morse.R;

public class MainActivity extends AppCompatActivity {

    private LinkedList<BluetoothDevice> btDevices = new LinkedList<BluetoothDevice>();
    private ArrayAdapter<String> deviceArrayAdapter;

    private EditText editText;
    private TextView textView;
    private Button btnSend;
    private ProgressDialog pgdLoading;
    private AlertDialog deviceListDialog;
    private Menu menu;

    private BluetoothSerialClient client;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        client = BluetoothSerialClient.getInstance();

        if (client == null) {
            Toast.makeText(getApplicationContext(), "No se puede utilizar el dispositivo bluetooth.", Toast.LENGTH_SHORT).show();
            finish();
        }
        initProgressDialog();
        initDeviceListDialog();
        initWidget();

    }

    private void initProgressDialog() {
        pgdLoading = new ProgressDialog(this);
        pgdLoading.setCancelable(false);
    }

    private void initDeviceListDialog() {
        deviceArrayAdapter = new ArrayAdapter<String>(getApplicationContext(), R.layout.item_device);
        ListView listView = new ListView(getApplicationContext());
        listView.setAdapter(deviceArrayAdapter);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                String item = (String) parent.getItemAtPosition(position);
                for (BluetoothDevice device : btDevices) {
                    if (item.contains(device.getAddress())) {
                        connect(device);
                        deviceListDialog.cancel();
                    }
                }
            }
        });
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Elige el dispositivo Bluetooth");
        builder.setView(listView);
        builder.setPositiveButton("Escanear",
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        scanDevices();
                    }
                });
        deviceListDialog = builder.create();
        deviceListDialog.setCanceledOnTouchOutside(false);
    }

    private void initWidget() {
        textView = (TextView) findViewById(R.id.textViewTerminal);
        textView.setMovementMethod(new ScrollingMovementMethod());
        editText = (EditText) findViewById(R.id.editText);
        btnSend = (Button) findViewById(R.id.btnSend);
        btnSend.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendStringData(editText.getText().toString());
                editText.setText("");
            }
        });
    }

    private void addDeviceToArrayAdapter(BluetoothDevice device) {
        if (btDevices.contains(device)) {
            btDevices.remove(device);
            deviceArrayAdapter.remove(device.getName() + "\n" + device.getAddress());
        }
        btDevices.add(device);
        deviceArrayAdapter.add(device.getName() + "\n" + device.getAddress());
        deviceArrayAdapter.notifyDataSetChanged();

    }

    private void enableBluetooth() {
        client.enableBluetooth(this, new BluetoothUpListener() {
            @Override
            public void onBluetoothUp(boolean success) {
                if (success) {
                    getPairedDevices();
                } else {
                    finish();
                }
            }
        });
    }

    private void addText(String text) {
        textView.append(text);
        final int scrollAmount = textView.getLayout().getLineTop(textView.getLineCount()) - textView.getHeight();
        if (scrollAmount > 0)
            textView.scrollTo(0, scrollAmount);
        else
            textView.scrollTo(0, 0);
    }


    private void getPairedDevices() {
        Set<BluetoothDevice> devices = client.getPairedDevices();
        for (BluetoothDevice device : devices) {
            addDeviceToArrayAdapter(device);
        }
    }

    public void sendStringData(String data) {
        data += '\0';
        byte[] buffer = data.getBytes();
        if (btHandler.write(buffer)) {
            addText("Yo : " + data + '\n');
        }
    }

    private void scanDevices() {
        client.scanDevices(getApplicationContext(), new ScanListener() {
            String message = "";

            @Override
            public void onStart() {
                pgdLoading.show();
                pgdLoading.setMessage("Buscando....");
                pgdLoading.setCancelable(true);
                pgdLoading.setCanceledOnTouchOutside(false);
                pgdLoading.setOnCancelListener(new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialog) {
                        BluetoothSerialClient btSet = client;
                        client.cancelScan(getApplicationContext());
                    }
                });
            }

            @Override
            public void onFoundDevice(BluetoothDevice bluetoothDevice) {
                addDeviceToArrayAdapter(bluetoothDevice);
                message += "\n" + bluetoothDevice.getName() + "\n" + bluetoothDevice.getAddress();
                pgdLoading.setMessage(message);
            }

            @Override
            public void onFinish() {
                pgdLoading.cancel();
                pgdLoading.setCancelable(false);
                pgdLoading.setOnCancelListener(null);
                deviceListDialog.show();
            }
        });
    }

    private void connect(BluetoothDevice device) {
        pgdLoading.setMessage("Connecting....");
        pgdLoading.setCancelable(false);
        pgdLoading.show();
        BluetoothSerialClient btSet =  client;
        btSet.connect(getApplicationContext(), device, btHandler);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        boolean connect = client.isConnected();
            if (!connect) {
                deviceListDialog.show();
            } else {
                btHandler.close();
            }
            return true;

    }

    @Override
    protected void onPause() {
        client.cancelScan(getApplicationContext());
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        enableBluetooth();
    }

    private BluetoothStreamingHandler btHandler = new BluetoothStreamingHandler() {
        ByteBuffer byteBuffer = ByteBuffer.allocate(1024);

        @Override
        public void onError(Exception e) {
            pgdLoading.cancel();
            addText("Mensaje : Error de conexión - " + e.toString() + "\n");
            menu.getItem(0).setTitle(R.string.action_connect);
        }

        @Override
        public void onDisconnected() {
            menu.getItem(0).setTitle(R.string.action_connect);
            pgdLoading.cancel();
            addText("Mensaje : Desconectado.\n");
        }

        @Override
        public void onData(byte[] buffer, int length) {
            if (length == 0) return;
            if (byteBuffer.position() + length >= byteBuffer.capacity()) {
                ByteBuffer newBuffer = ByteBuffer.allocate(byteBuffer.capacity() * 2);
                newBuffer.put(byteBuffer.array(), 0, byteBuffer.position());
                byteBuffer = newBuffer;
            }
            byteBuffer.put(buffer, 0, length);
            if (buffer[length - 1] == '\0') {
                addText(client.getConnectedDevice().getName() + " : " +
                        new String(byteBuffer.array(), 0, byteBuffer.position()) + '\n');
                byteBuffer.clear();
            }
        }

        @Override
        public void onConnected() {
            addText("Mensaje : Conectado. " + client.getConnectedDevice().getName() + "\n");
            pgdLoading.cancel();
            menu.getItem(0).setTitle(R.string.action_disconnect);
        }
    };

}

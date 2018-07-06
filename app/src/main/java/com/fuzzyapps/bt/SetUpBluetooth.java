package com.fuzzyapps.bt;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Set;

public class SetUpBluetooth extends AppCompatActivity {

    private ListView devicelist;
    //variables de Bluetooth
    private BluetoothAdapter myBluetooth = null;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.set_up_bluetooth);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setTitle(R.string.title1);
        devicelist = (ListView)findViewById(R.id.listView);
        myBluetooth = BluetoothAdapter.getDefaultAdapter();
        //cargar dispositivos pareados
        pairedDevicesList();
        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        if(primeraVez()){
            cargarDatos();
        }
        //Accinoes
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                pairedDevicesList();
            }
        });
        if(myBluetooth == null) {
            //Show a mensag. that thedevice has no bluetooth adapter
            Toast.makeText(getApplicationContext(), "Bluetooth Device Not Available", Toast.LENGTH_LONG).show();
            //finish apk
            finish();
        }else{
            if (myBluetooth.isEnabled()){}
            else{
                //Ask to the user turn the bluetooth on
                Intent turnBTon = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(turnBTon,1);
            }
        }
    }
    private void pairedDevicesList(){
        Set<BluetoothDevice> pairedDevice = myBluetooth.getBondedDevices();
        //pairedDevices = myluetooth.getBondedDevices();
        ArrayList list = new ArrayList();

        if (pairedDevice.size() > 0){
            for(BluetoothDevice bt : pairedDevice){
                list.add(bt.getName() + "\n" + bt.getAddress()); //Get the device's name and the address
            }
        }else{
            Toast.makeText(getApplicationContext(), "No Paired Bluetooth Devices Found.", Toast.LENGTH_LONG).show();
        }

        final ArrayAdapter adapter = new ArrayAdapter(this,android.R.layout.simple_list_item_1, list);
        devicelist.setAdapter(adapter);
        devicelist.setOnItemClickListener(myListClickListener); //Method called when the device from the list is clicked

    }
    private AdapterView.OnItemClickListener myListClickListener = new AdapterView.OnItemClickListener()
    {
        public void onItemClick (AdapterView av, View v, int arg2, long arg3)
        {
            // Get the device MAC address, the last 17 chars in the View
            String info = ((TextView) v).getText().toString();
            String address = info.substring(info.length() - 17);
            // Make an intent to start next activity.
            cargarModificaciones(address);
            Intent i = new Intent(SetUpBluetooth.this, ScrollingActivity.class);
            //Change the activity.
            i.putExtra("EXTRA_ADDRESS", address); //this will be received at ledControl (class) Activity
            startActivity(i);
            finish();
        }
    };

    public void cargarModificaciones(String address){
        AdminSQLiteOpenHelper admin = new AdminSQLiteOpenHelper(this,"arduino", null, 1);
        SQLiteDatabase bd = admin.getWritableDatabase();
        Cursor fila = bd.rawQuery("select getStarted from ajustes where id='" + 1 + "'", null);
        if (fila.moveToFirst()) {
            ContentValues oldContent= new ContentValues();
            oldContent.put("address", address);
            oldContent.put("getStarted", "1");
            oldContent.put("intensity", "0");
            bd.update("ajustes", oldContent, "id=1", null);
        }else{
            ContentValues newContent= new ContentValues();
            newContent.put("address", address);
            newContent.put("getStarted", "1");
            newContent.put("intensity", "0");
            bd.insert("ajustes", null, newContent);
        }
        bd.close();
    }
    public boolean primeraVez(){
        AdminSQLiteOpenHelper admin = new AdminSQLiteOpenHelper(this,"arduino", null, 1);
        SQLiteDatabase bd = admin.getWritableDatabase();
        Cursor fila = bd.rawQuery("select getStarted from ajustes where id='" + 1 + "'", null);
        if (fila.moveToFirst()) {
            if(fila.getString(0).equals("")){
                bd.close();
                return false;
            }else{
                bd.close();
                return true;
            }

        }else{
            bd.close();
            return false;
        }
    }
    public void cargarDatos(){
        AdminSQLiteOpenHelper admin = new AdminSQLiteOpenHelper(this,"arduino", null, 1);
        SQLiteDatabase bd = admin.getWritableDatabase();
        Cursor fila = bd.rawQuery("select address from ajustes where id='" + 1 + "'", null);
        if (fila.moveToFirst()) {
            Intent i = new Intent(SetUpBluetooth.this, ScrollingActivity.class);
            //Change the activity.
            //this will be received at ledControl (class) Activity
            i.putExtra("EXTRA_ADDRESS", fila.getString(0));
            startActivity(i);
            finish();
        }
        bd.close();
    }
}

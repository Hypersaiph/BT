package com.fuzzyapps.bt;

import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.util.UUID;

import be.hogent.tarsos.dsp.AudioEvent;
import be.hogent.tarsos.dsp.onsets.OnsetHandler;
import be.hogent.tarsos.dsp.onsets.PercussionOnsetDetector;

public class ScrollingActivity extends AppCompatActivity implements OnsetHandler {
    Button btnOn, btnOff, btnDis;
    Switch mySwitch;
    TextView lumn;
    SeekBar brightness;
    String address = null;
    private ProgressDialog progress;
    BluetoothAdapter myBluetooth = null;
    BluetoothSocket btSocket = null;
    private boolean isBtConnected = false;
    static final UUID myUUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    //Record Variables
    private byte[] buffer2;
    private AudioRecord recorder;
    private boolean mIsRecording;
    private boolean encender;
    private PercussionOnsetDetector mPercussionOnsetDetector;
    private be.hogent.tarsos.dsp.AudioFormat tarsosFormat;
    private int clap;
    static final int SAMPLE_RATE = 8000;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scrolling);
        Bundle bundle = getIntent().getExtras();
        //myUUID = UUID.fromString(b.getString(Homescreen.DEVICE_UUID));
        address = bundle.getString("EXTRA_ADDRESS");
        mIsRecording = false;
        encender = false;
        clap = 0;
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setTitle(R.string.title2);
        //variables
        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        lumn = (TextView) findViewById(R.id.title);
        btnOn = (Button)findViewById(R.id.on);
        btnOff = (Button)findViewById(R.id.off);
        btnDis = (Button)findViewById(R.id.disconnect);
        brightness = (SeekBar)findViewById(R.id.seekbar);
        mySwitch = (Switch) findViewById(R.id.recordSwitch);

        mySwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                // do something, the isChecked will be
                // true if the switch is in the On position
                if(isChecked){
                    //Revisar si hay permisos
                    //Pedir permisos
                    ActivityCompat.requestPermissions(ScrollingActivity.this,
                            new String[]{android.Manifest.permission.RECORD_AUDIO}, 1);
                    mySwitch.setText("Activado");
                }else{
                    mIsRecording = false;
                    mySwitch.setText("Desactivado");
                    recorder.stop();
                }
            }
        });
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent i = new Intent(ScrollingActivity.this, SetUpBluetooth.class);
                startActivity(i);
                finish();
                cargarModificaciones();
            }
        });
        btnOn.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                turnOnLed();      //method to turn on
            }
        });

        btnOff.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v)
            {
                turnOffLed();   //method to turn off
            }
        });

        btnDis.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                Disconnect(); //close connection
            }
        });
        brightness.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser){
                    lumn.setText("Brillo: "+String.valueOf(progress));
                    // integer value
                    // 100 -> 255 || progress -> y
                    // si manda en
                    int converted = progress * 255 / 100;
                    try{
                        btSocket.getOutputStream().write(String.valueOf(converted+"\n").getBytes());
                    }
                    catch (IOException e) {}
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                saveIntensity(""+seekBar.getProgress());
            }
        });
        // STEP 1: setup AudioRecord
        int minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        buffer2 = new byte[minBufferSize];
        recorder = new AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, minBufferSize);
        // END STEP 1
        // STEP 2: create detector
        mPercussionOnsetDetector = new PercussionOnsetDetector(SAMPLE_RATE, (minBufferSize / 2), this, 24, 5);
        // END STEP 2
    }
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[], @NonNull int[] grantResults) {
        switch (requestCode) {
            case 1: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    new Handler().postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            listen();
                        }
                    }, 500);
                } else {
                    msg("Error: no hay permisos para  el microfono.");
                }
            }
        }
    }
    public void cargarModificaciones(){
        AdminSQLiteOpenHelper admin = new AdminSQLiteOpenHelper(this,"arduino", null, 1);
        SQLiteDatabase bd = admin.getWritableDatabase();
        Cursor fila = bd.rawQuery("select getStarted from ajustes where id='" + 1 + "'", null);
        if (fila.moveToFirst()) {
            ContentValues oldContent= new ContentValues();
            oldContent.put("address", "");
            oldContent.put("getStarted", "");
            bd.update("ajustes", oldContent, "id=1", null);
        }
        bd.close();
    }
    private void Disconnect() {
        //If the btSocket is busy
        if (btSocket!=null){
            try{
                btSocket.close(); //close connection
            }
            catch (IOException e){
                msg("Error");
            }
        }
        finish();
    }
    private void turnOffLed()
    {
        if (btSocket!=null)
        {
            try{
                brightness.setProgress(0);lumn.setText("Brillo: "+String.valueOf(0));
                int converted = 0;
                btSocket.getOutputStream().write(String.valueOf(converted+"\n").getBytes());
                saveIntensity("0");
            }
            catch (IOException e)
            {
                msg("Error");
            }
        }
    }
    private void turnOnLed(){
        if (btSocket!=null){
            try {
                brightness.setProgress(100);
                lumn.setText("Brillo: "+String.valueOf(100));
                int converted = 255;
                btSocket.getOutputStream().write(String.valueOf(converted+"\n").getBytes());
                saveIntensity("100");
            }catch (IOException e){
                msg("Error");
            }
        }
    }
    private class ConnectBT extends AsyncTask<Void, Void, Void> {    // UI thread
        private boolean ConnectSuccess = true; //if it's here, it's almost connected

        @Override
        protected void onPreExecute(){}
        @Override
        protected Void doInBackground(Void... devices){
            //while the progress dialog is shown, the connection is done in background
            try{
                if (btSocket == null || !isBtConnected){
                    //get the mobile bluetooth device
                    myBluetooth = BluetoothAdapter.getDefaultAdapter();
                    //connects to the device's address and checks if it's available
                    BluetoothDevice dispositivo = myBluetooth.getRemoteDevice(address);
                    //create a RFCOMM (SPP) connection
                    btSocket = dispositivo.createInsecureRfcommSocketToServiceRecord(myUUID);
                    BluetoothAdapter.getDefaultAdapter().cancelDiscovery();
                    //start connection
                    btSocket.connect();
                }
            }
            catch (IOException e){
                ConnectSuccess = false;//if the try failed, you can check the exception here
            }
            return null;
        }
        @Override
        protected void onPostExecute(Void result){ //after the doInBackground, it checks if everything went fine
            super.onPostExecute(result);
            if (!ConnectSuccess) {
                cargarModificaciones();
                msg("Connection Fallida.");
                dismissProgress();
                Intent i = new Intent(ScrollingActivity.this, SetUpBluetooth.class);
                startActivity(i);
                finish();
            }
            else {
                msg("Connected.");
                dismissProgress();
                getIntensity();
                //Toast.makeText(getApplicationContext(),"Connected",Toast.LENGTH_SHORT).show();
                isBtConnected = true;
            }
        }
    }
    private void msg(String s){
        try {
            Toast.makeText(ScrollingActivity.this, s, Toast.LENGTH_SHORT).show();
        }catch (Exception e){}
    }
    private void Connect(){
        progress = ProgressDialog.show(ScrollingActivity.this, "Connecting...", "Please wait!!!");
        new ConnectBT().execute();
    }

    private void getIntensity() {
        AdminSQLiteOpenHelper admin = new AdminSQLiteOpenHelper(this,"arduino", null, 1);
        SQLiteDatabase bd = admin.getWritableDatabase();
        Cursor fila = bd.rawQuery("select intensity from ajustes where id='" + 1 + "'", null);
        if (fila.moveToFirst()) {
            int intensity = Integer.parseInt(fila.getString(0));
            msg(""+intensity);
            if (btSocket!=null) {
                //try {
                    brightness.setProgress(intensity);
                    lumn.setText("Brillo: "+String.valueOf(intensity));
                    //btSocket.getOutputStream().write(String.valueOf(intensity+"\n").getBytes());
                /*}catch (IOException e){
                    msg("Error");
                }*/
            }
        }
        bd.close();
    }
    private void saveIntensity(String value){
        AdminSQLiteOpenHelper admin = new AdminSQLiteOpenHelper(this,"arduino", null, 1);
        SQLiteDatabase bd = admin.getWritableDatabase();
        Cursor fila = bd.rawQuery("select getStarted from ajustes where id='" + 1 + "'", null);
        if (fila.moveToFirst()) {
            ContentValues oldContent= new ContentValues();
            oldContent.put("intensity", value);
            bd.update("ajustes", oldContent, "id=1", null);
        }
    }
    private void dismissProgress() {
        progress.dismiss();
    }
    @Override
    protected void onStop() {
        super.onStop();
        Disconnect();
        if(mySwitch.isChecked()) {
            try {
                recorder.stop();
            } catch (Exception e) {
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        Connect();
        if(mySwitch.isChecked()){
            try{
                listen();
            }catch (Exception e){}
        }
    }
    //Listening
    @Override
    public void handleOnset(double time, double salience) {
        System.out.println(String.format("%.4f;%.4f", time, salience));
        clap += 1;
        // have we detected a pitch?
        if (clap == 1) {
            encender = !encender;
            // handlePitch will be run from a background thread
            // so we need to run it on the UI thread
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if(encender){
                        //msg("On!");
                        turnOnLed();
                    }else{
                        //msg("Off!");
                        turnOffLed();
                    }
                    clap=0;
                }
            });

        }
    }
    public void listen() {
        mIsRecording = true;
        recorder.startRecording();
        tarsosFormat = new be.hogent.tarsos.dsp.AudioFormat(
                (float) SAMPLE_RATE, // sample rate
                16, // bit depth
                1, // channels
                true, // signed samples?
                false // big endian?
        );
        Thread listeningThread = new Thread(new Runnable() {
            @Override
            public void run() {
                while (mIsRecording) {
                    int bufferReadResult = recorder.read(buffer2, 0, buffer2.length);
                    AudioEvent audioEvent = new AudioEvent(tarsosFormat, bufferReadResult);
                    audioEvent.setFloatBufferWithByteBuffer(buffer2);
                    mPercussionOnsetDetector.process(audioEvent);
                }
                recorder.stop();
                msg("recorder stopped");
            }

        });
        listeningThread.start();
    }
}

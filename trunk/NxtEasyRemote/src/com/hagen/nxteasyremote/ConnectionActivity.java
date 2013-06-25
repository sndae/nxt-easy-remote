package com.hagen.nxteasyremote;

import com.hagen.nxteasyremote.R.id;

import android.opengl.Visibility;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
import android.provider.ContactsContract.Directory;
import android.provider.Settings.System;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager.LayoutParams;
import android.widget.*;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.IntentFilter;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.Log;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.Toast;
import android.widget.ToggleButton;

public class ConnectionActivity extends Activity
								implements android.view.View.OnClickListener, OnItemClickListener, SensorEventListener{

	static String EXTRA_DEVICE_ADDRESS = "device_address";
	static String MACRO_FILENAME= "/data/data/com.hagen.nxteasyremote/Macros.csv";
	
	static final int REQUEST_ENABLE_BT = 2;
	static final int REMOTE_CANCELED = 3;
	
	static final int BTSTATE_DISCONNECTED = -1;
    static final int BTSTATE_CONNECTING = 0;
    static final int BTSTATE_CONNECTED = 1;

    static final int APP_STATE_CONNECTION = 4;
    static final int APP_STATE_CONTROL = 5;
    static final int APP_STATE_MACRO = 6;

    static final int MACRO_STATE_NONE = 7;
    static final int MACRO_STATE_RECORDING = 8;
    static final int MACRO_STATE_PLAYING = 9;
	
	BluetoothAdapter bluetoothAdapter;
    /*Handler handler = new Handler(){
        @Override
        public void handleMessage(Message msg) {
            // TODO Auto-generated method stub
            Log.i(tag, "in handler");
            super.handleMessage(msg);
            switch(msg.what){
            case SUCCESS_CONNECT:
                // DO something
                ConnectedThread connectedThread = new ConnectedThread((BluetoothSocket)msg.obj);
                Toast.makeText(getApplicationContext(), "CONNECT", 0).show();
                String s = "successfully connected";
                connectedThread.write(s.getBytes());
                Log.i(tag, "connected");
                break;
            case MESSAGE_READ:
                byte[] readBuf = (byte[])msg.obj;
                String string = new String(readBuf);
                Toast.makeText(getApplicationContext(), string, 0).show();
                break;
            }
        }
    };*/
    
    String deviceAddress;
    ArrayAdapter<String> listAdapter;
    ListView listView;
    Set<BluetoothDevice> devicesArray;
    ArrayList<String> pairedDevices;
    ArrayList<BluetoothDevice> devices;
    public static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    protected static final int SUCCESS_CONNECT = 0;
    protected static final int MESSAGE_READ = 1;
    IntentFilter filter;
    BroadcastReceiver receiver;
    Button scanButton;
    
    int btState;
    int appState;
    int macroState;
    boolean controlState;
    boolean debugState;
    boolean macroDialogState;

    ConnectThread connectThread;
    ConnectedThread connectedThread;
    
    ToggleButton onOffButton;
    RadioButton leftAButton;
    RadioButton leftBButton;
    RadioButton leftCButton;
    RadioButton rightAButton;
    RadioButton rightBButton;
    RadioButton rightCButton;
    ToggleButton debugButton;
    ToggleButton recordButton;
    
    int leftPort;
    int rightPort;
    
    SensorManager sensorManager;
	Sensor lageSensor;
	ControlPositionView controlPositionView;
	
	CsvReaderWriter csvReaderWriter;
	ArrayList<String> recordValues;
	ArrayList<Macro> macros;
	Macro selectedMacro;
	PlayThread playThread;
	
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.connection_activity_layout);
        
        getWindow().addFlags(LayoutParams.FLAG_KEEP_SCREEN_ON);
        
        setBtState(BTSTATE_DISCONNECTED);
        setAppState(APP_STATE_CONNECTION);
        setControlState(false);
        setDebugState(false);
        macroDialogState = false;
        setMacroState(MACRO_STATE_NONE);
        
        try {
            FileWriter writer = new FileWriter(MACRO_FILENAME,true);
        	FileReader reader = new FileReader(MACRO_FILENAME);
            csvReaderWriter = new CsvReaderWriter(reader, writer);
		} catch (IOException e) {
			toast("Couldn't find file: " + MACRO_FILENAME);
			finish();
		}
        
        devices = new ArrayList<BluetoothDevice>();
        listView = (ListView)findViewById(R.id.DevicesList);
        listView.setOnItemClickListener(this);
        listAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1,0);
        listView.setAdapter(listAdapter);
                
        scanButton = (Button) this.findViewById(id.ScanButton);
        scanButton.setOnClickListener(this);
    }    
    
    public void onClick(View v){
    	int id = v.getId();
    	if(id == R.id.ScanButton)
    	{
    		bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    	       
            if (bluetoothAdapter == null) {
                Toast.makeText(getApplicationContext(), "Bluetooth is not available", Toast.LENGTH_LONG).show();
                return;
            }else{
	            if (!bluetoothAdapter.isEnabled())
	                turnOnBT();

	            listAdapter.clear();
	            devices.clear();
	            receiver = new BroadcastReceiver(){
	                @Override
	                public void onReceive(Context context, Intent intent) {
	                    String action = intent.getAction();
	                     
	                    if(BluetoothDevice.ACTION_FOUND.equals(action)){
	                        BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
	                        if(!devices.contains(device)){
	                        	devices.add(device);
	                        	String s = "";
	                        	for(int a = 0; a < pairedDevices.size(); a++){
		                            if(device.getName().equals(pairedDevices.get(a))){
		                                //append
		                                s = "(Paired)";
		                                break;
		                            }
	                        	}

	                        	listAdapter.add(device.getName()+" "+s+" "+"\n"+device.getAddress());
	                        }
	                    }                     
	                    else if(BluetoothAdapter.ACTION_DISCOVERY_STARTED.equals(action)){
	                    	scanButton.setEnabled(false);
	                    	scanButton.setText("Scanning...");
	                    }
	                    else if(BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)){
	                    	scanButton.setEnabled(true);
	                    	scanButton.setText("Scan");              
	                    }
	                    else if(BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)){
	                        if(bluetoothAdapter.getState() == bluetoothAdapter.STATE_OFF){
	                            turnOnBT();
	                        }
	                    }	               
	                }
	            };
	            
	            filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
	             registerReceiver(receiver, filter);
	            filter = new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
	             registerReceiver(receiver, filter);
	            filter = new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
	             registerReceiver(receiver, filter);
	            filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
	             registerReceiver(receiver, filter);
	            
	            pairedDevices = getPairedDevices();
                startDiscovery();                
            }
    	}
    	if(id == R.id.OnOffButton)
    	{
    		if(onOffButton.getText().equals("ON"))
    		{
        		setControlState(true);
    			leftAButton.setEnabled(false);
    			leftBButton.setEnabled(false);
    			leftCButton.setEnabled(false);
    			rightAButton.setEnabled(false);
        		rightBButton.setEnabled(false);
        		rightCButton.setEnabled(false);
        		recordButton.setEnabled(true);
    		}
    		if(onOffButton.getText().equals("OFF"))
    		{
    			setControlState(false);
    			leftAButton.setEnabled(true);
    			leftBButton.setEnabled(true);
    			leftCButton.setEnabled(true);
    			rightAButton.setEnabled(true);
        		rightBButton.setEnabled(true);
        		rightCButton.setEnabled(true);
        		if(leftAButton.isChecked())
        			rightAButton.setEnabled(false);
        		if(leftBButton.isChecked())
        			rightBButton.setEnabled(false);
        		if(leftCButton.isChecked())
        			rightCButton.setEnabled(false);
        		if(rightAButton.isChecked())
        			leftAButton.setEnabled(false);
        		if(rightBButton.isChecked())
        			leftBButton.setEnabled(false);
        		if(rightCButton.isChecked())
        			leftCButton.setEnabled(false);
        		disableRecordButton();
        		
        		recordButton.setEnabled(false);
    		}
    	}
    	if(id == R.id.DebugButton)
    	{
    		if(debugButton.getText().equals("Debug On"))
    			setDebugState(true);
    		if(debugButton.getText().equals("Debug Off"))
    			setDebugState(false);
    	}
    	if(id == R.id.RecordButton)
    	{
    		if(recordButton.getText().equals("Recording..."))
    			startRecording();
    		if(recordButton.getText().equals("Rec"))
    			stopRecording();
    	}
    	if(id == R.id.LeftA)
    	{
    		rightAButton.setEnabled(false);
    		rightBButton.setEnabled(true);
    		rightCButton.setEnabled(true);
    		leftPort = 0;
    	}
    	if(id == R.id.LeftB)
    	{
    		rightAButton.setEnabled(true);
    		rightBButton.setEnabled(false);
    		rightCButton.setEnabled(true);
    		leftPort = 1;
    	}
    	if(id == R.id.LeftC)
    	{
    		rightAButton.setEnabled(true);
    		rightBButton.setEnabled(true);
    		rightCButton.setEnabled(false);
    		leftPort = 2;
    	}
    	if(id == R.id.RightA)
    	{
    		leftAButton.setEnabled(false);
    		leftBButton.setEnabled(true);
    		leftCButton.setEnabled(true);
    		rightPort = 0;
    	}
    	if(id == R.id.RightB)
    	{
    		leftAButton.setEnabled(true);
    		leftBButton.setEnabled(false);
    		leftCButton.setEnabled(true);
    		rightPort = 1;
    	}
    	if(id == R.id.RightC)
    	{
    		leftAButton.setEnabled(true);
    		leftBButton.setEnabled(true);
    		leftCButton.setEnabled(false);
    		rightPort = 2;
    	}
    }
    
    //In macro state selects the macro, in control state inits the control activity else connects to the selected device 
    public void onItemClick(AdapterView<?> arg0, View arg1, int arg2, long arg3) {                 
        if(getAppState() == APP_STATE_MACRO){
        	selectedMacro = macros.get(arg2);
        	macroDialog();
        }else if(getAppState() == APP_STATE_CONTROL){
        	initControlActivity();
        }else{
	        if(bluetoothAdapter.isDiscovering())
	            bluetoothAdapter.cancelDiscovery();
	        
	        BluetoothDevice selectedDevice = devices.get(arg2);
	        
	        connect(selectedDevice);
        }
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    	switch (requestCode) {
        case REQUEST_ENABLE_BT:
        	if(resultCode == RESULT_OK){
                startDiscovery();
            }else{
            	toast("Bluetooth must be enabled to continue");
            }
            break;
        }    	
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();

        disableRecordButton();
        if(lageSensor != null) {
			sensorManager.unregisterListener(this);
		}
    }
    
    @Override
	protected void onPause() {
		super.onPause();
		
		if (onOffButton != null)
			onOffButton.setChecked(false);
		disableRecordButton();
		if(lageSensor != null)
			sensorManager.unregisterListener(this);
	}
	
    @Override
	protected void onResume() {
		super.onResume();

		if(lageSensor != null) {
		   sensorManager.registerListener(this, lageSensor, SensorManager.SENSOR_DELAY_NORMAL);
		}
	}
            
    @Override
    public void onBackPressed() {
    	if(!macroDialogState){
	    	if(getAppState() == APP_STATE_CONTROL){
	        	setControlState(false);
	        	disableRecordButton();
	    		connectedThread.cancel();            
	            this.runOnUiThread(new Runnable() {			
	    			@Override
	    			public void run() {
	    				setContentView(R.layout.connection_activity_layout);
	    			}
	    		});
	    	}else if(getAppState() == APP_STATE_MACRO){
	    		initControlActivity();
	    		setAppState(APP_STATE_CONTROL);
	    	}else{
	    		finish();
	    	}
    	}
    }
    
    @Override
    public void onSensorChanged(SensorEvent event) {
    	float[] values = event.values.clone();
    	float x = (int)values[1];
    	float y = (int)values[2];
    	float xLeft = 0;
    	float xRight = 0;
    	
        //Convert x (forth and back/180:-180) to percent
    	int maxX = 30;
        if(x > 0){
        	if(x > maxX)
        		x = maxX;
        	x = x * 100 / maxX;
        }else if(x < 0){
        	if(x < -maxX)
        		x = -maxX;
        	x = x * 100 / maxX;
        }        
        
        //Convert y (left and right/-90:90) to percent
        int maxY = 40;
        if(y >= 0){
        	if(y > maxY)
        		y = maxY;
        	y = y * 100 / maxY;
        }else if(y < 0){
        	if(y < -maxY)
        		y = -maxY;
        	y = y * 100 / maxY;
        }

        if(y < 0){
        	xLeft = x;       
            xRight = x / 100 * (100+y);
        }
        if(y >= 0){
        	xLeft = x / 100 * (100-y);
            xRight = x;
        }
        
        /*Total turn by +x to -x
        if(y >= 0){
        	if(x >= 0){	
            	xLeft = x - y;
            	xRight = x;
            }else{
        		xLeft = x + y;
            	xRight = x; 
            }
        }
        if(y < 0){
        	if(x >= 0){	
            	xLeft = x;       
            	xRight = x + y;
            }else{
            	xLeft = x;       
            	xRight = x - y; 
            }
        }*/

        xLeft = trimToTwoDecimals(xLeft);
        xRight = trimToTwoDecimals(xRight);
        
        if(getControlState())
        	motors((byte)xLeft, (byte)xRight);
        
        if(getMacroState() == MACRO_STATE_RECORDING){
        	recordValues.add(String.valueOf(xLeft));
        	recordValues.add(String.valueOf(xRight));
        }
        
    	controlPositionView.update((int)x, (int)y, (int)xLeft, (int)xRight, getDebugState());
    }
    
    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }
    
    public boolean onCreateOptionsMenu(Menu menu) {
    	if(getAppState() == APP_STATE_CONTROL){
	    	MenuInflater inflater = getMenuInflater();
	    	inflater.inflate(R.menu.menu, menu);
	    	return true;
    	}else{
    		return false;
    	}
    }
    
    @Override
    public boolean onMenuOpened(int featureId, Menu menu) {
    	if(getAppState() == APP_STATE_CONTROL && getControlState() == false)
    		return super.onMenuOpened(featureId, menu);
    	
    	return false;
    }
    
    public boolean onOptionsItemSelected(MenuItem item) {
    	switch (item.getItemId()) {
		case R.id.Menu_Macros: 
			try {
				initMacrosActivity();
			} catch (IOException e) {
				e.printStackTrace();
			}
			return true;
    	default : return true;
    	}
    }
    

    //Returns a float with two decimals
    private float trimToTwoDecimals(float number){
    	number = number * 100;
        int temp = (int)number;
        number = (float)temp;
        number = number /100;
        return number;
    }
    
    //Display a little toast message
    public void toast(String text){
    	Toast.makeText(getApplicationContext(), text, Toast.LENGTH_SHORT).show();
    }
    
	//Gets a ArrayList<String> with the paired devices
    private ArrayList<String> getPairedDevices() {
    	devicesArray = bluetoothAdapter.getBondedDevices();
    	ArrayList<String> resultArray = new ArrayList<String>();
    	
    	if(devicesArray.size()>0){
            for(BluetoothDevice device:devicesArray){
            	resultArray.add(device.getName());
            }
        }
    	return resultArray;
    }
    
    //Tries to turn the bluetooth on
    private void turnOnBT(){
    	Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
    }
    
    //Starts the bluetooth discovering
    private void startDiscovery() {
    	bluetoothAdapter.cancelDiscovery();
    	bluetoothAdapter.startDiscovery();
    }
    
    //Set state to DISCONNECTED and show toast
    private void connectionFailed() {
        setBtState(BTSTATE_DISCONNECTED);
        this.runOnUiThread(new Runnable() {			
			@Override
			public void run() {
		        toast("Connection failed");
			}
		});
    }

    //Set state to DISCONNECTED and show toast
    private void connectionLost() {        
        this.runOnUiThread(new Runnable() {			
			@Override
			public void run() {
				toast("Connection lost");
				initConnectionActivity();				
			}
		});        
        setBtState(BTSTATE_DISCONNECTED);
        setAppState(APP_STATE_CONNECTION);
    }
       
    
    //Sets the bluetooth state
    private synchronized void setBtState(int mState) {
        btState = mState;
    }
        
    //Gets the bluetooth state
    public synchronized int getBtState() {
        return btState;
    }
    
    //Sets the app state
    private synchronized void setAppState(int mState) {
        appState = mState;
    }
    
    //Gets the app state
    public synchronized int getAppState() {
        return appState;
    }
    
    //Sets the control state    
    public synchronized void setControlState(boolean activated){
    	if(getAppState() == APP_STATE_CONTROL && !activated)
    		motors((byte)0, (byte)0);
    	
    	controlState = activated;
    }
  
    //Sets the control state
    public synchronized boolean getControlState(){
    	return controlState;
    }

    //Sets the debug state
    public synchronized void setDebugState(boolean activated){
    	debugState = activated;
    }
  
    //Gets the debug state
    public synchronized boolean getDebugState(){
    	return debugState;
    }
    
    //Sets the macro state
    public synchronized void setMacroState(int mState){
    	macroState = mState;
    }
  
    //Gets the macro state
    public synchronized int getMacroState(){
    	return macroState;
    }
    
    
    //Opens the macro options dialog
    public void macroDialog(){
    	macroDialogState = true;
    	new AlertDialog.Builder(this)
    	    .setTitle("Options")
    	    .setMessage("Please select an option.")
    	    .setPositiveButton("Play", new DialogInterface.OnClickListener() {
    	         public void onClick(DialogInterface dialog, int whichButton) {    	        	 
					runOnUiThread(new Runnable() {
						public void run() {
							try {
								playMacro();
							} catch (InterruptedException e) {
								e.printStackTrace();
							}
						}
					});
    	         }
    	    })
    	    .setNeutralButton("Delete", new DialogInterface.OnClickListener() {
   	         	public void onClick(DialogInterface dialog, int whichButton) {
   	         		try {
						deleteMacro();
					} catch (IOException e) {
						e.printStackTrace();
					}
   	         	}
    	    })
    	    .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
    	         public void onClick(DialogInterface dialog, int whichButton) {
    	             macroDialogState = false;
    	         }
    	    }).show();
    }
    
    //Plays the macro
    public void playMacro() throws InterruptedException{
    	setMacroState(MACRO_STATE_PLAYING);
    	
        ProgressDialog playDialog = new ProgressDialog(this);
        playDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        playDialog.setMessage("Playing macro: " + selectedMacro.name);
        playDialog.setCancelable(false);
        playThread = new PlayThread(playDialog);
        playThread.start();
    }
     
    //The thread showing the playing state
    public class PlayThread extends Thread {
    	private ProgressDialog progressDialog;

	    public PlayThread(ProgressDialog progress) {
	    	super();
	    	progressDialog = progress;
	    }
	
	    @Override
	    public void run(){
	    	Looper.prepare();
	    	runOnUiThread(new Runnable() {
				public void run() {
					progressDialog.show();
				}
			});
	    	for (int i = 0; i < selectedMacro.lefts.size(); i++) {
				motors(selectedMacro.lefts.get(i).byteValue(), selectedMacro.rights.get(i).byteValue());
				try {
					Thread.sleep(1000/5);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
			motors((byte)0, (byte)0);
	    	progressDialog.dismiss();
	    	
	    	setMacroState(MACRO_STATE_NONE);
	    	macroDialogState = false;
	    	return;
    	}
    }
        
    //Deletes the selected macro and triggers reload
    public void deleteMacro() throws IOException{
    	if(selectedMacro != null)
    		macros.remove(selectedMacro);
    	
    	writeMacros();
    	loadMacros();
    	macroDialogState = false;
    }    
    
    //Resets the macro file and writes all right macros into it 
    public void writeMacros() throws IOException{
    	try {
            FileWriter writer = new FileWriter(MACRO_FILENAME, false);
        	FileReader reader = new FileReader(MACRO_FILENAME);
            csvReaderWriter = new CsvReaderWriter(reader, writer);
		} catch (IOException e) {
		}
    	
    	for (Macro macro : macros) {
			csvReaderWriter.writeNextLine(macro.toLine());
		}
    	csvReaderWriter.flushWriter();
    }
    
    //Reads line after line and tries to parse it as a macro
    public void loadMacros() throws IOException{
    	String[] nextLine;
    	Macro newMacro = null;
    	macros = new ArrayList<Macro>();
    	do{
    		nextLine = csvReaderWriter.readNext();
    		if(nextLine!= null){
    			if(nextLine.length % 2 != 0){
    				newMacro = new Macro(nextLine[0]);
		    		for (int i = 1; i < nextLine.length; i++) {
		    			try {
							newMacro.add(Float.valueOf(nextLine[i]), Float.valueOf(nextLine[i+1]));
							i = i + 1;
						} catch (Exception e) {
							newMacro = null;
						}
		    		}
		    		if(newMacro != null)
		    			macros.add(newMacro);
    			}
    		}
    	}while(nextLine != null);
    	
    	writeMacros();
    	listAdapter.clear();
    	for (Macro macro : macros) {
        	listAdapter.add(macro.name + "\nDuration: " + macro.lefts.size()/5 + " seconds");
		}
    }
    
    //Stops the recording if activated
    public void disableRecordButton(){
    	if(recordButton != null){
	    	if(recordButton.getText().equals("Recording..."))
	    		stopRecording();
    	}
    }
    
    //Starts the recording with the macro name
    public synchronized void startRecording(){
    	final EditText input = new EditText(this);
   	 	setControlState(false);

    	new AlertDialog.Builder(this)
    	    .setTitle("Create macro")
    	    .setMessage("Please enter a name for the macro.")
    	    .setView(input)
    	    .setPositiveButton("Ok", new DialogInterface.OnClickListener() {
    	         public void onClick(DialogInterface dialog, int whichButton) {
    	        	 setMacroName(input.getText().toString());
    	        	 setControlState(true);
    	         }
    	    })
    	    .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
    	         public void onClick(DialogInterface dialog, int whichButton) {
    	             setMacroName("");
    	        	 setControlState(true);
    	         }
    	    })
    	    .show();    	
    }
    
    //Sets the macro name and starts the recording
    public synchronized void setMacroName(String mMacroName){
    	recordValues = new ArrayList<String>();
    	String macroName = mMacroName;
    	macroName = macroName.replaceAll(";", "");
    	if(macroName.isEmpty()){
    		recordButton.setChecked(false);
    		toast("You must enter a name for the macro!");
    	}else{    			
    		recordValues.add(macroName);
    		setMacroState(MACRO_STATE_RECORDING);    		
    	}
    }
    
    //Writes the recorded values to the csv file
    public synchronized void stopRecording(){
    	csvReaderWriter.writeNextLine(recordValues.toArray(new String[0]));
    	try {
			csvReaderWriter.flushWriter();
		} catch (IOException e) {
		}
		setMacroState(MACRO_STATE_NONE);
    }
    
    //byte[4] == port-out, byte[5] == speed (-100 to 100)
    public void motors(byte left, byte right) {
        byte[] data = { 0x0c, 0x00, (byte) 0x80, 0x04, (byte)leftPort, left, 0x07, 0x00, 0x00, 0x20, 0x00, 0x00, 0x00, 0x00,
                        0x0c, 0x00, (byte) 0x80, 0x04, (byte)rightPort, right, 0x07, 0x00, 0x00, 0x20, 0x00, 0x00, 0x00, 0x00 };
                
        write(data);
    }
    
    //Sends the data array out through the connectedThread
    private void write(byte[] out) {
        ConnectedThread mConnectedThread;
        synchronized (this) {
            if (btState != BTSTATE_CONNECTED) {
                return;
            }
            mConnectedThread = connectedThread;
        }
        mConnectedThread.write(out);
    }
    
    //Connect to the device
    public synchronized void connect(BluetoothDevice device) {        
        if (btState == BTSTATE_CONNECTING) {
            if (connectThread != null) {
            	connectThread.cancel();
            	connectThread = null;
            }
        }
        
        if (connectedThread != null) {
        	connectedThread.cancel();
        	connectedThread = null;
        }
        
        connectThread = new ConnectThread(device);
        connectThread.start();
        setBtState(BTSTATE_CONNECTING);
    }
    
    //Is connected to the device
    public synchronized void connected(BluetoothSocket socket, BluetoothDevice device) {
        if (connectThread != null) {
        	connectThread.cancel();
        	connectThread = null;
        }
        
        if (connectedThread != null) {
        	connectedThread.cancel();
        	connectedThread = null;
        }
        
        connectedThread = new ConnectedThread(socket);
        connectedThread.start();
        
        setBtState(BTSTATE_CONNECTED);
        setAppState(APP_STATE_CONTROL);

        this.runOnUiThread(new Runnable() {			
			@Override
			public void run() {
				initControlActivity();
			}
		});
    }

    //Initializes the control view
    public void initControlActivity(){
    	setContentView(R.layout.nxteasyremote_activity_layout);
		
    	onOffButton = (ToggleButton) this.findViewById(id.OnOffButton);
		onOffButton.setOnClickListener(this);
        leftAButton = (RadioButton) this.findViewById(id.LeftA);
        leftAButton.setOnClickListener(this);
        leftBButton = (RadioButton) this.findViewById(id.LeftB);
        leftBButton.setOnClickListener(this);
        leftCButton = (RadioButton) this.findViewById(id.LeftC);
        leftCButton.setOnClickListener(this);
        rightAButton = (RadioButton) this.findViewById(id.RightA);
        rightAButton.setOnClickListener(this);
        rightBButton = (RadioButton) this.findViewById(id.RightB);
        rightBButton.setOnClickListener(this);
        rightCButton = (RadioButton) this.findViewById(id.RightC);
        rightCButton.setOnClickListener(this);
        debugButton = (ToggleButton) this.findViewById(id.DebugButton);
        debugButton.setOnClickListener(this);
        recordButton = (ToggleButton) this.findViewById(id.RecordButton);
        recordButton.setOnClickListener(this);
        recordButton.setEnabled(false);
        
        leftPort = 0;
        rightPort = 1;
        
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        
        lageSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ORIENTATION);
        
        if(lageSensor == null) {
        	toast("No orientation sensor found!");
        	onBackPressed();
        	return;
        }        
        sensorManager.registerListener(this, lageSensor, SensorManager.SENSOR_DELAY_NORMAL);
        
        controlPositionView = new ControlPositionView(this, onOffButton.getHeight());

        LinearLayout.LayoutParams lparams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
        		                                                          LinearLayout.LayoutParams.WRAP_CONTENT);
        lparams.gravity = Gravity.CENTER_HORIZONTAL;
        controlPositionView.setLayoutParams(lparams);
        LinearLayout layout = (LinearLayout) this.findViewById(R.id.ControlPositionView);
        layout.addView(controlPositionView);
    }

    //Initializes the connection view
    public void initConnectionActivity(){
    	devices = new ArrayList<BluetoothDevice>();
        listView = (ListView)findViewById(R.id.DevicesList);
        listView.setOnItemClickListener(this);
        listAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1,0);
        listView.setAdapter(listAdapter);
        
    	scanButton = (Button) this.findViewById(id.ScanButton);
        scanButton.setOnClickListener(this);
    }
    
    //Initializes the macros view
    public void initMacrosActivity() throws IOException{
    	setAppState(APP_STATE_MACRO);

    	setContentView(R.layout.macros_activity_layout);
    	
        listView = (ListView)findViewById(R.id.MacroList);
        listView.setOnItemClickListener(this);
        listAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1,0);
        listView.setAdapter(listAdapter);

        loadMacros();
    }

////C o n n e c t T h r e a d/////////////////////////////////////////////////////////////////////////////////////////////////////////    
    public class ConnectThread extends Thread {
        private BluetoothSocket bluetoothSocket;
        private final BluetoothDevice device;
        
        public ConnectThread(BluetoothDevice mDevice) {
        	device = mDevice;
        }
        
        public void run() {
            setName("ConnectThread");
            bluetoothAdapter.cancelDiscovery();
            
            try {
            	bluetoothSocket = device.createRfcommSocketToServiceRecord(UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"));
            	bluetoothSocket.connect();
            } catch (IOException e) {
                e.printStackTrace();
                try {
                    Method method = device.getClass().getMethod("createRfcommSocket", new Class[] { int.class });
                    bluetoothSocket = (BluetoothSocket) method.invoke(device, Integer.valueOf(1));
                    bluetoothSocket.connect();
                } catch (Exception e1) {
                    e1.printStackTrace();
                    connectionFailed();
                    try {
                    	bluetoothSocket.close();
                    } catch (IOException e2) {
                        e2.printStackTrace();
                    }
                    return;
                }
            }
            
            synchronized (ConnectionActivity.this) {
                connectThread = null;
            }
            
            connected(bluetoothSocket, device);
        }
        
        public void cancel() {
            try {
                if (bluetoothSocket != null) {
                	bluetoothSocket.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
////C o n n e c t e d T h r e a d///////////////////////////////////////////////////////////////////////////////////////////////////////    
    public class ConnectedThread extends Thread {
        private final BluetoothSocket bluetoothSocket;
        private final InputStream inputStream;
        private final OutputStream outputStream;
        
        public ConnectedThread(BluetoothSocket socket) {
        	bluetoothSocket = socket;
            InputStream in = null;
            OutputStream out = null;
            
            try {
            	in = socket.getInputStream();
            	out = socket.getOutputStream();
            } catch (IOException e) {
                e.printStackTrace();
            }
            
            inputStream = in;
            outputStream = out;
        }
        
        public void run() {
            byte[] buffer = new byte[1024];
            int bytes;
            
            while (true) {
                try {
                    bytes = inputStream.read(buffer);
                    //toast(Integer.toString(bytes) + " bytes read from device");
                } catch (IOException e) {
                    e.printStackTrace();
                    connectionLost();
                    break;
                }
            }
        }
        
        public void write(byte[] buffer) {
            try {
            	outputStream.write(buffer);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        
        public void cancel() {
            try {
            	bluetoothSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}

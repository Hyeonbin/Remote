package com.example.seo.remote;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.nio.ByteBuffer;
import java.util.StringTokenizer;

public class MainActivity extends AppCompatActivity implements View.OnClickListener{
    private static final String TAG = "BluetoothChat";
    private static final boolean D = true;

    // Message types sent from the BluetoothChatService Handler
    public static final int MESSAGE_STATE_CHANGE = 1;
    public static final int MESSAGE_READ = 2;
    public static final int MESSAGE_WRITE = 3;
    public static final int MESSAGE_DEVICE_NAME = 4;
    public static final int MESSAGE_TOAST = 5;

    // Key names received from the BluetoothChatService Handler
    public static final String DEVICE_NAME = "device_name";
    public static final String TOAST = "toast";

    private static final int REQUEST_CONNECT_DEVICE = 1;
    private static final int REQUEST_ENABLE_BT = 2;

    private LinearLayout remote_templayout;
    private LinearLayout remote_moistlayout;
    private LinearLayout remote_dustlayout;
    private ImageView remote_searchbtn;
    private ImageView remote_refreshbtn;
    private TextView remote_temp1;
    private TextView remote_mois1;
    private TextView remote_dust1;
    private TextView remote_temp2;
    private TextView remote_mois2;
    private TextView remote_dust2;

    private Switch remote_allcontrol;

    private String writeMessage;
    private String readMessage;
    private String mConnectedDeviceName = null;
    private StringBuffer mOutStringBuffer;
    private BluetoothAdapter mBluetoothAdapter = null;
    private BluetoothService mBtService = null;

    private int sig = 0;
    int switchsig = 0;

    private final Handler mhandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MESSAGE_STATE_CHANGE:
                    if(D) Log.i(TAG, "MESSAGE_STATE_CHANGE: " + msg.arg1);
                    break;
                case MESSAGE_WRITE:
                    byte[] writeBuf = (byte[]) msg.obj;
                    writeMessage = new String(writeBuf);
                    break;
                case MESSAGE_READ:
                    byte[] readBuf = (byte[]) msg.obj;

                    // construct a string from the valid bytes in the buffer
                    remote_temp1 = (TextView)findViewById(R.id.Remote_Temp1);
                    remote_temp2 = (TextView)findViewById(R.id.Remote_Temp2);
                    remote_mois1 = (TextView)findViewById(R.id.Remote_Mois1);
                    remote_mois2 = (TextView)findViewById(R.id.Remote_Mois2);
                    remote_dust1 = (TextView)findViewById(R.id.Remote_Dust1);
                    remote_dust2 = (TextView)findViewById(R.id.Remote_Dust2);

                    String tempstring = new String(readBuf, 0, readBuf.length);

                    if(sig == 1)
                        remote_temp1.setText(tempstring);
                    else if(sig == 2)
                        remote_mois1.setText(tempstring);
                    else
                        remote_dust1.setText(tempstring);

                    // Todo
                    // 봄, 여름, 가을, 겨울 적정 온도, 습도 찾아보기
                    // 미세먼지 레벨 찾아보기

                    break;
                case MESSAGE_DEVICE_NAME:
                    // save the connected device's name
                    mConnectedDeviceName = msg.getData().getString(DEVICE_NAME);
                    Toast.makeText(getApplicationContext(), "Connected to "
                            + mConnectedDeviceName, Toast.LENGTH_SHORT).show();
                    break;
                case MESSAGE_TOAST:
                    Toast.makeText(getApplicationContext(), msg.getData().getString(TOAST),
                            Toast.LENGTH_SHORT).show();
                    break;
            }
        }
    };

    public static AppCompatActivity mainActivity;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mainActivity = this;

        //상단 버튼들 정의
        remote_searchbtn = (ImageView)findViewById(R.id.Remote_Searchbtn);

        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        remote_searchbtn.setOnClickListener(this);

        setupChat();

        if(mBluetoothAdapter == null) {
            Toast.makeText(this, "블루투스를 연결할 수 없습니다", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

    }

    @Override
    public void onStart() {
        super.onStart();
        if(D) Log.e(TAG, "++ ON START ++");

        // If BT is not on, request that it be enabled.
        // setupChat() will then be called during onActivityResult
        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
            // Otherwise, setup the chat session
        } else {
            if (mBtService == null)
                setupChat();
        }
    }

    @Override
    public synchronized void onResume() {
        super.onResume();
        if(D) Log.e(TAG, "+ ON RESUME +");

        // Performing this check in onResume() covers the case in which BT was
        // not enabled during onStart(), so we were paused to enable it...
        // onResume() will be called when ACTION_REQUEST_ENABLE activity returns.
        if (mBtService != null) {
            // Only if the state is STATE_NONE, do we know that we haven't started already
            if (mBtService.getState() == BluetoothService.STATE_NONE) {
                // Start the Bluetooth chat services
                mBtService.start();
            }
        }
    }

    private void setupChat() {
        Log.d(TAG, "setupChat()");

        remote_templayout = (LinearLayout)findViewById(R.id.Remote_Templayout);
        remote_templayout.setOnClickListener(this);
        remote_moistlayout = (LinearLayout)findViewById(R.id.Remote_Moistlayout);
        remote_moistlayout.setOnClickListener(this);
        remote_dustlayout = (LinearLayout)findViewById(R.id.Remote_Dustlayout);
        remote_dustlayout.setOnClickListener(this);

        remote_allcontrol = (Switch)findViewById(R.id.Remote_Allcontrol);
        SharedPreferences sharedPreferences = getSharedPreferences("switch", Activity.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();

        switchsig = sharedPreferences.getInt("allcontrol", -1);

        if(switchsig == 0){
            remote_allcontrol.setChecked(false);
        } else {
            remote_allcontrol.setChecked(true);
        }

        remote_allcontrol.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                if(b == true){
                    sendMessage("D");
                    switchsig = 1;
                }
                else {
                    sendMessage("E");
                    switchsig = 0;
                }
            }
        });

        mBtService = new BluetoothService(this, mhandler);
        // Initialize the buffer for outgoing messages
        mOutStringBuffer = new StringBuffer("");
    }

    @Override
    public synchronized void onPause() {
        super.onPause();
        if(D) Log.e(TAG, "- ON PAUSE -");
        SharedPreferences sharedPreferences = getSharedPreferences("switch", Activity.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putInt("allcontrol", switchsig);
        editor.commit();
    }

    @Override
    public void onStop() {
        super.onStop();
        if(D) Log.e(TAG, "-- ON STOP --");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // Stop the Bluetooth chat services
        if (mBtService != null) mBtService.stop();
        if(D) Log.e(TAG, "--- ON DESTROY ---");
    }

    private void ensureDiscoverable() {
        if(D) Log.d(TAG, "ensure discoverable");
        if (mBluetoothAdapter.getScanMode() !=
                BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
            Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
            discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
            startActivity(discoverableIntent);
        }
    }

    private void sendMessage(String message) {
        // Check that we're actually connected before trying anything
        if (mBtService.getState() != BluetoothService.STATE_CONNECTED) {
            Toast.makeText(this, R.string.not_connected, Toast.LENGTH_SHORT).show();
            return;
        }

        // Check that there's actually something to send
        if (message.length() > 0) {
            // Get the message bytes and tell the BluetoothChatService to write
            byte[] send = message.getBytes();
            mBtService.write(send);

            // Reset out string buffer to zero and clear the edit text field
            mOutStringBuffer.setLength(0);
        }
    }

    @Override
    public void onClick(View v) {
        switch(v.getId()) {
            case R.id.Remote_Searchbtn:
                Intent intent = new Intent(MainActivity.this, DeviceListActivity.class);
                startActivityForResult(intent, REQUEST_CONNECT_DEVICE);
                break;
            case R.id.Remote_Templayout:
                LinearLayout remote_tdetaillayout = (LinearLayout)findViewById(R.id.Remote_Tdetaillayout);
                ImageView remote_tarrow = (ImageView)findViewById(R.id.Remote_Temparrow);
                remote_tdetaillayout.setVisibility(View.VISIBLE);
                remote_tarrow.setVisibility(View.GONE);
                sendMessage("A");
                sig = 1;
                break;
            case R.id.Remote_Moistlayout:
                LinearLayout remote_mdetaillayout = (LinearLayout)findViewById(R.id.Remote_Mdetaillayout);
                ImageView remote_marrow = (ImageView)findViewById(R.id.Remote_Moisarrow);
                remote_mdetaillayout.setVisibility(View.VISIBLE);
                remote_marrow.setVisibility(View.GONE);
                sendMessage("B");
                sig = 2;
                break;
            case R.id.Remote_Dustlayout:
                LinearLayout remote_ddetaillayout = (LinearLayout)findViewById(R.id.Remote_Ddetaillayout);
                ImageView remote_darrow = (ImageView)findViewById(R.id.Remote_Dustarrow);
                remote_ddetaillayout.setVisibility(View.VISIBLE);
                remote_darrow.setVisibility(View.GONE);
                sendMessage("C");
                sig = 3;
                break;
        }
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_CONNECT_DEVICE:
                if(resultCode == Activity.RESULT_OK) {
                    String address = data.getExtras().getString(DeviceListActivity.EXTRA_DEVICE_ADDRESS);
                    BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
                    mBtService.connect(device);
                }
                break;
            case REQUEST_ENABLE_BT:
                if(resultCode == Activity.RESULT_OK) {
                    setupChat();
                } else {
                    Toast.makeText(MainActivity.this, "블루투스 연결을 할 수 없습니다", Toast.LENGTH_LONG).show();
                    finish();
                }
                break;
        }
    }
}

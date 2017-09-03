package com.example.bluetooth;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    public final static int MESSAGE_DATA = 0x01;

    UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    public static final String TAG = "MainActivity";
    private BroadcastReceiver mRceiver;

    private Button buttonSerch;
    private Button buttonSend;
    private Button buttonClear;
    private Button buttonClearSend;
    private EditText editTextData;
    private ListView listViewDevices;
    private TextView textViewShowData;
    private ArrayList<String> arrayList;
    private ArrayAdapter<String> arrayAdapter;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothDevice bluetoothDevice;
    private BluetoothSocket bluetoothSocket;
    private OutputStream outputStream;
    private InputStream inputStream;

    private final String NAME = "Bluetooth_Socket";

    //接收来自其他线程的消息并处理
    private MyHandler myHandler = new MyHandler();

    //服务端线程任务
    AcceptThread acceptThread;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Log.d(MainActivity.TAG, "on Creat");

        //获得本地蓝牙适配器
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        initView();

        //检查是否支持蓝牙
        checkBluetoothSupport();
        Toast.makeText(MainActivity.this, "开始搜索蓝牙设备,首次搜索时间较长,请耐心等待...", Toast.LENGTH_SHORT).show();

        //搜索附近蓝牙设备
        serchBluetoothDevice();

        //设置广播接收器
        mRceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (BluetoothDevice.ACTION_FOUND.equals(action)){
                    BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
//                    arrayAdapter.add(device.getName()+ ":" + device.getAddress());
                    String dev = device.getName()+":"+device.getAddress();
                    if (!arrayList.contains(dev)){
                        arrayList.add(dev);
                    }
                    arrayAdapter.notifyDataSetChanged(); //通知ListView适配器 数据已经发生改变
                }else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)){
                    Log.d(TAG,"搜索结束!");

                    //在主线程中显示搜索结束信息
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(MainActivity.this, "搜索结束!", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        };
        //注册广播
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothDevice.ACTION_FOUND);
        intentFilter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        registerReceiver(mRceiver, intentFilter);

        //启动接收线程任务
        acceptThread = new AcceptThread();
        acceptThread.start();
    }

    /**
     * 搜索蓝牙设备
     * startDiscovery方法是异步的
     */
    private void serchBluetoothDevice() {
        //搜索设备
        while (!bluetoothAdapter.startDiscovery()){
            Log.d(TAG, "尝试失败");
            try {
                Thread.sleep(100);
            }catch (InterruptedException e){
                e.printStackTrace();
            }
        }
    }

    /**
     * 检测是否支持蓝牙
     */
    private void checkBluetoothSupport() {
        //检测是否支持蓝牙
        if (bluetoothAdapter!=null) {
            //打开蓝牙
            if (!bluetoothAdapter.isEnabled()) {
//                对话框提示
//                startActivity(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE));
                //静默打开
                bluetoothAdapter.enable();
                bluetoothAdapter.cancelDiscovery();
            }
        }else{
            finish();
        }
    }

    /**
     * 初始化布局控件功能
     */
    private void initView() {
        //搜索蓝牙设备,搜索之前断开当前连接,并清空存储蓝牙设备的arrayList
        buttonSerch = (Button)findViewById(R.id.button_serch);
        buttonSerch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                arrayList.clear();
                if (bluetoothSocket != null)
                    try {
                        if (bluetoothSocket.isConnected())
                            bluetoothSocket.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                if (bluetoothAdapter.isDiscovering()) {
                    bluetoothAdapter.cancelDiscovery();
                }

                Toast.makeText(MainActivity.this, "开始搜索蓝牙设备...", Toast.LENGTH_LONG).show();
                serchBluetoothDevice();
            }
        });

        //设置ListView点击事件监听,点击item连接相应的蓝牙设备
        listViewDevices = (ListView)findViewById(R.id.listView_devices);
        arrayList = new ArrayList<>();
//        getPairingBluetoothDevice();
        arrayAdapter = new ArrayAdapter(this, android.R.layout.simple_list_item_1, arrayList);
        listViewDevices.setAdapter(arrayAdapter);
        listViewDevices.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {

                String devices = arrayAdapter.getItem(i);
                String address = devices.substring(devices.indexOf(":") + 1).trim();

                Toast.makeText(MainActivity.this, "正在连接,请稍等...", Toast.LENGTH_SHORT).show();
                Log.d(TAG, "address -->  " + address);

                /**
                 * 连接与断开开启新线程原因:  如果不开新线程,程序在close的时候会闪退
                 */
                //连接新的设备时,断开当前连接
                if (bluetoothSocket != null) {
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            Log.d(TAG, "in  disconnect Thread ");
                            if (bluetoothSocket.isConnected()) {
                                try {
                                    bluetoothSocket.close();
                                    if (acceptThread.getStopReceive() == false)
                                        acceptThread.setStopReceive(true);
                                    inputStream = null;
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            }// end of if
                        }
                    }).start();
                }

                //连接新设备
                bluetoothDevice = bluetoothAdapter.getRemoteDevice(address);
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            //获得设备
                            bluetoothSocket = bluetoothDevice.createRfcommSocketToServiceRecord(uuid);
                            bluetoothSocket.connect();
                            outputStream = bluetoothSocket.getOutputStream();
                            inputStream = bluetoothSocket.getInputStream();
                            acceptThread.setStopReceive(false);

                            //在主线程中显示连接成功提示信息
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    if (bluetoothSocket.isConnected())
                                        Toast.makeText(MainActivity.this, "已连接!", Toast.LENGTH_SHORT).show();
                                    Log.d(MainActivity.TAG, "已连接!");
                                }
                            });
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }).start();
            }
        });

        //点击发送按钮,获得editText的内容,然后发送出去,如果当前没有设备连接,弹出提示.
        editTextData = (EditText)findViewById(R.id.editText_sendData);
        buttonSend = (Button)findViewById(R.id.button_send);
        buttonSend.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String data = editTextData.getText().toString();
                if (outputStream != null) {
                    try {
                        outputStream.write(data.getBytes("ASCII"));
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }else
                    Toast.makeText(MainActivity.this, "当前未连接任何设备!", Toast.LENGTH_SHORT).show();
            }
        });

        //点击清空接收按钮,清空接收显示区域
        textViewShowData = (TextView)findViewById(R.id.textViewReceiveDataShow);
        textViewShowData.setMovementMethod(ScrollingMovementMethod.getInstance());
        buttonClear = (Button)findViewById(R.id.buttonClear);
        buttonClear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                textViewShowData.setText(null);           //清空显示
            }
        });

        //点击清除发送,清空发送区显示区域
        buttonClearSend = (Button)findViewById(R.id.button_clearSend);
        buttonClearSend.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                editTextData.setText(null);
            }
        });
    }

    /**
     * 获得已经配对的蓝牙设备
     */
    private void getPairingBluetoothDevice() {
        //获得已经配对的设备
        Set<BluetoothDevice> deviceSet = bluetoothAdapter.getBondedDevices();
        for (BluetoothDevice device : deviceSet){
            arrayList.add(device.getName()+":"+device.getAddress());
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (bluetoothSocket != null)
                        bluetoothSocket.close();

                    if (inputStream != null)
                        inputStream.close();

                    if (outputStream != null)
                        outputStream.close();

                    //由最低层的线程逐步判断 一步一步的由下往上关闭线程
                    if (acceptThread.isInterrupted() == false){
                        if (acceptThread.getStopReceive() == false){
                            acceptThread.setRunningState(false);
                        }else {
                            acceptThread.setStopReceive(true);
                            inputStream = null;
                            acceptThread.setRunningState(false);
                        }
                    }

                }catch (IOException e){
                    e.printStackTrace();
                }
            }
        }).start();

        //注销广播
        unregisterReceiver(mRceiver);
        //关闭蓝牙
        bluetoothAdapter.disable();
    }

    /**
     * 处理其他线程发来的消息
     */
    class MyHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what){
                //该消息附带接收到的数据,用于更新UI显示
                case MainActivity.MESSAGE_DATA:
                    final String data = msg.obj.toString();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Log.d(TAG, "更新textView显示!");
                            textViewShowData.append(data+"\n");
                            int offset=textViewShowData.getLineCount()*textViewShowData.getLineHeight();
                            if (offset > textViewShowData.getHeight()) {
                                textViewShowData.scrollTo(0,offset - textViewShowData.getHeight());
                            }else {
                                textViewShowData.scrollTo(0, 0);
                            }
                        }
                    });
                    break;
                default:break;
            }
        }
    }

    // 服务端接收信息线程
    private class AcceptThread extends Thread {

        private String line;
        private BufferedReader reader;
        private boolean stopReceive=true;
        private boolean state = true;


        public AcceptThread() {
        }

        /**
         * 用于停止读取数据的阻塞操作
         * @param state
         */
        public void setStopReceive(boolean state){
            this.stopReceive = state;
        }

        /**
         * 获取当前接收数据的状态
         * @return true: 已经停止, false:未停止
         */
        public boolean getStopReceive(){
            return this.stopReceive;
        }

        /**
         * 用于设置当前线程的运行状态,可以用来结束while循环
         * @param state
         */
        public void setRunningState(boolean state){
            this.state = state;
        }

        /**
         * 获得当前线程的运行状态
         * @return
         */
        public boolean getRunningState(){
            return this.state;
        }

        public void run() {

            //其他线程未设置state, 则一直循环检测
            while (state){
                if (inputStream != null){
                    reader = new BufferedReader(new InputStreamReader(inputStream));
                    Log.d(TAG, "开始接收...");
                    try {
                        // 注意:使用readLine方法,接收到的消息必须以回车结束,否则阻塞 所以可以用break来结束这个循环,获得接收到的消息带回车符
                        while ((line=reader.readLine()) != null){
                            Log.d(TAG, line);

                            //发送消息,附带接受到的数据,用于更新UI显示
                            Message msg = new Message();
                            msg.what = MainActivity.MESSAGE_DATA;
                            msg.obj = line;
                            myHandler.sendMessage(msg);

                            //在外界可以设置stopReceive为true, 中断该阻塞读的操作
                            if (stopReceive == false)
                                break;
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }else {
                    Log.d(TAG, "空循环...");
                    try {
                        sleep(1000); //本线程睡眠1秒,等待获得输入流后,继续进行读去数据的操作
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }//end of while(state)
        }//end of while run()
    }// end of private class AcceptThread extends Thread {
}

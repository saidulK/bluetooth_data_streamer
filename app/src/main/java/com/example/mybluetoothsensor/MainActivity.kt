package com.example.mybluetoothsensor

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.Log
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import java.io.IOException
import java.io.Serializable
import java.util.*
import kotlin.collections.ArrayList


class MainActivity : AppCompatActivity() {

    private var m_bluetoothAdapter: BluetoothAdapter? = null
    private var m_bluetoothSocket:BluetoothSocket?= null
    private var connected_device:BluetoothDevice ?= null
    private lateinit var m_pairedDevices: Set<BluetoothDevice>
    private val BT_MODULE_UUID:UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    private val REQUEST_ENABLE_BLUETOOTH = 1
    private val REQUEST_DEVICE = 5

    private var mHandler:Handler ?=null

    private var connect_button: Button?= null
    private var connected_textview: TextView?= null
    private var acc_switch:Switch?=null
    private var gyro_switch:Switch?=null
    private var send_rate_radio:RadioGroup?=null

    private var mSensorThread:StreamThread?=null



    companion object{
        var EXTRA_ARRAYLIST:String = "DEVICE_LIST"
        val MESSAGE_READ = 2 // used in bluetooth handler to identify message update
        private val CONNECTING_STATUS = 3 // used in bluetooth handler to identify message status
        val SENSOR_UPDATE = 4
        val LATENCY_CHANGE = 6
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        connect_button     = findViewById(R.id.connect_button)
        connected_textview = findViewById(R.id.connection_textview)
        acc_switch         = findViewById(R.id.acc_switch)
        gyro_switch        = findViewById(R.id.gyro_switch)
        send_rate_radio    = findViewById(R.id.sending_rate_radio)


        m_bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        if(m_bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth not available", Toast.LENGTH_SHORT).show()
            return
        }
        if(!m_bluetoothAdapter!!.isEnabled) {
            val enableBluetoothIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableBluetoothIntent, REQUEST_ENABLE_BLUETOOTH)
        }

        connect_button!!.setOnClickListener{
            get_device()
        }


        mHandler = object : Handler(Looper.getMainLooper()) {
            override fun handleMessage(msg: Message) {
                if (msg.what == MainActivity.MESSAGE_READ) {

                }
                if (msg.what == MainActivity.CONNECTING_STATUS) {
                    if (msg.arg1 == 1) connected_textview?.setText("Connected to Device: " + msg.obj)
                    else connected_textview?.setText("Connection Failed")
                }
                if (msg.what == MainActivity.LATENCY_CHANGE) {

                }
            }
        }

        acc_switch?.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                try {
                    mSensorThread?.set_include_acc(true)
                } catch (e: Exception) {
                    Log.e("MainActivity", "can't set acc true")
                }
            } else {
                try {
                    mSensorThread?.set_include_acc(false)
                } catch (e: Exception) {
                    Log.e("MainActivity", "can't set acc false")
                }
            }
        }


        gyro_switch?.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                try {
                    mSensorThread?.set_include_gyro(true)
                } catch (e: Exception) {
                    Log.e("MainActivity", "can't set gyro true")
                }
            } else {
                try {
                    mSensorThread?.set_include_gyro(false)
                } catch (e: Exception) {
                    Log.e("MainActivity", "can't set gyro false")
                }
            }
        }

        send_rate_radio?.setOnCheckedChangeListener(RadioGroup.OnCheckedChangeListener { radioGroup, i ->
            Toast.makeText(this, "Radio "+ i, Toast.LENGTH_SHORT).show()
        })
    }


    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_ENABLE_BLUETOOTH) {
            if (resultCode == Activity.RESULT_OK) {
                if (m_bluetoothAdapter!!.isEnabled) {
                    Toast.makeText(this, "Bluetooth has been enabled\"", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Bluetooth has been disabled", Toast.LENGTH_SHORT).show()
                }
            } else if (resultCode == Activity.RESULT_CANCELED) {
                Toast.makeText(this, "Bluetooth enabling has been canceled", Toast.LENGTH_SHORT).show()

            }
        }

        if (requestCode == REQUEST_DEVICE) {
            if (resultCode == Activity.RESULT_OK) {
                //device selected
                val selected_dev_name = data?.getStringExtra("device_name") as String
                val selected_dev_id = data?.getStringExtra("device_id") as String

                Log.d("Act","Device Selected name "+selected_dev_name)
                Log.d("Act","Device Selected ID "+selected_dev_id)

                connected_textview?.setText("Connecting to device "+selected_dev_name)
                connect_device(selected_dev_name,selected_dev_id)


            } else if (resultCode == Activity.RESULT_CANCELED) {

                Log.e("Act","No Device Selected")
            }
        }
    }

    fun connect_device(selected_dev_name:String,selected_dev_id:String){

        Thread(Runnable {
            var fail = false

           val device = m_bluetoothAdapter?.getRemoteDevice(selected_dev_id) as BluetoothDevice
            connected_device = device
            try {
                m_bluetoothSocket = createBluetoothSocket(device)
            } catch (e: IOException) {
                fail = true
                Toast.makeText(baseContext, "Socket creation failed", Toast.LENGTH_SHORT)
                    .show()
            }

            // Establish the Bluetooth socket connection.
            try {
                m_bluetoothSocket?.connect()
                Log.i("Socket Thread","Socket Created")
                //connected_textview?.setText("Connected to "+selected_dev_name)

            } catch (e: IOException) {
                //connected_textview?.setText("Connect to")

                try {
                    fail = true
                    m_bluetoothSocket?.close()
                    Log.e("Socket Thread","Socket Failed")
                } catch (e2: IOException) {
                    //insert code to deal with this
                    Toast.makeText(baseContext, "Socket creation failed", Toast.LENGTH_SHORT)
                        .show()
                    Log.e("Socket Thread","Socket Failed")
                }
            }

            if(!fail) {
                    mSensorThread = StreamThread(this,m_bluetoothSocket as BluetoothSocket,mHandler as Handler)
                    mSensorThread?.start()
                    mHandler?.obtainMessage(CONNECTING_STATUS, 1, -1, selected_dev_name)?.sendToTarget()

            }


        }).start()

    }

    fun createBluetoothSocket(connected_device:BluetoothDevice):BluetoothSocket{
        var bluetoothSocket: BluetoothSocket? = null;
        // Make a connection to the BluetoothSocket
        try {
            bluetoothSocket = connected_device.createRfcommSocketToServiceRecord(BT_MODULE_UUID)
        }
        catch(e:Exception){
            bluetoothSocket?.close()
        }
        return bluetoothSocket as BluetoothSocket
    }

    fun get_device(){
        m_pairedDevices = m_bluetoothAdapter!!.bondedDevices
        val list : ArrayList<String> = ArrayList()

        if (!m_pairedDevices.isEmpty()) {
            for (device: BluetoothDevice in m_pairedDevices) {
                list.add(device.name+"\n"+device.address)
                Log.i("device", ""+device)
            }
            val args = Bundle()
            args.putSerializable("ARRAYLIST", list as Serializable)


            val intent = Intent(this, DeviceListActivity::class.java)
            //intent.putExtra(EXTRA_ARRAYLIST, list)
            intent.putExtra("BUNDLE", args)
            startActivityForResult(intent,REQUEST_DEVICE)
        } else {
            Toast.makeText(this,"no paired bluetooth devices found", Toast.LENGTH_SHORT).show()
        }
    }


}
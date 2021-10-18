package com.example.mybluetoothsensor

import android.bluetooth.BluetoothSocket
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.Log
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.Charset
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.BlockingQueue

class StreamThread constructor(context:MainActivity,socket:BluetoothSocket,mHandler: Handler):Thread(),SensorEventListener {

    private var mContext: MainActivity? = null
    private var mSensorManager: SensorManager? = null
    private var accelerometer: Sensor? = null
    private var gyroscope: Sensor? = null
    private var streamHandler: Handler? = null


    private var mmSocket: BluetoothSocket? = null
    private var mmInStream: InputStream? = null
    private var mmOutStream: OutputStream? = null

    var include_acc:Boolean  = false
    var include_gyro:Boolean = false

    private val LOG_TAG = "SensorThread"

    private var latency:Long = 1

    private class SensorOutput(values: FloatArray, timestamp: Long, sensortype: String) {
        var data: ByteArray
        var msg: String

        init {
            // {acc:{timestamp:n,value:[]}} {gyro:{timestamp:n,value:[]}}
            msg = """{${sensortype}:{${timestamp},value:[${values[0]},${values[1]},${values[2]}]}}"""
            data = msg.toByteArray(Charset.forName("UTF-8"))
        }

    }

    private var mQueue: BlockingQueue<SensorOutput>? = null


    init{
        mContext = context
        mmSocket = socket

        mSensorManager = mContext!!.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = mSensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        gyroscope = mSensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        mSensorManager?.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_FASTEST)
        mSensorManager?.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_FASTEST)

        var tmpIn: InputStream? = null
        var tmpOut: OutputStream? = null


        // Get the input and output streams, using temp objects because
        // member streams are final
        try {
            tmpIn = socket.inputStream
            tmpOut = socket.outputStream
        } catch (e: IOException) {
        }

        mmInStream = tmpIn
        mmOutStream = tmpOut
        mQueue = ArrayBlockingQueue<SensorOutput>(1024)

    }

    fun set_include_acc(value:Boolean){
        include_acc = value
    }

    fun set_include_gyro(value:Boolean){
        include_gyro = value
    }

    fun set_latency(value:Long){
        latency = value
    }

    override public fun run(){
        while (true) {
            try {
                val output = mQueue!!.take() as SensorOutput
                Log.d("StreamThread", "buffer:")
                Thread.sleep(latency)
                mmOutStream!!.write(output.data)
            } catch (e: IOException) {
                e.printStackTrace()
            } catch (e: InterruptedException) {
                e.printStackTrace()
            } catch (e: NullPointerException) {
                e.printStackTrace()
            }
        }
    }

    override fun onSensorChanged(sensorEvent: SensorEvent?) {
        val timestamp: Long = sensorEvent!!.timestamp
        val eachSensor: Sensor = sensorEvent.sensor
        val mAcceMeasure = FloatArray(3)
        val mGyroMeasure = FloatArray(3)
        try {
            when (eachSensor.type) {
                Sensor.TYPE_ACCELEROMETER -> {
                    mAcceMeasure[0] = sensorEvent.values.get(0)
                    mAcceMeasure[1] = sensorEvent.values.get(1)
                    mAcceMeasure[2] = sensorEvent.values.get(2)
                    val AccOutput = SensorOutput(mAcceMeasure, timestamp, "Acc")
                    //mAcceMeasure[0]+"_"+ mAcceMeasure[1]+"_"+ mAcceMeasure[2]+"\n"
                    if (include_acc) mQueue!!.add(AccOutput)
                }
                Sensor.TYPE_GYROSCOPE -> {
                    mGyroMeasure[0] = sensorEvent.values.get(0)
                    mGyroMeasure[1] = sensorEvent.values.get(1)
                    mGyroMeasure[2] = sensorEvent.values.get(2)
                    val GyroOutput = SensorOutput(mGyroMeasure, timestamp, "Gyro")
                    //mAcceMeasure[0]+"_"+ mAcceMeasure[1]+"_"+ mAcceMeasure[2]+"\n"
                    if (include_gyro) mQueue!!.add(GyroOutput)
                }
            }

        } catch (e: Exception) {
            //Log.d(LOG_TAG, "onSensorChanged: Something is wrong.");
            e.printStackTrace()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, num: Int) {

    }

}
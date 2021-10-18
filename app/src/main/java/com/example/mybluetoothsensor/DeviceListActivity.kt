package com.example.mybluetoothsensor

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.ListView
import androidx.appcompat.app.AppCompatActivity


class DeviceListActivity : AppCompatActivity() {

    private lateinit var device_listview: ListView
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_device_list)
        device_listview = findViewById(R.id.device_list_view)


        var args:Bundle ?= intent.getBundleExtra("BUNDLE")
        var device_list = args?.getSerializable("ARRAYLIST") as ArrayList<Object>
        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, device_list)
        device_listview.adapter =adapter

        device_listview.setOnItemClickListener{ _, _, position, _ ->

            val returnIntent = Intent()
            var device_info:String = device_list.elementAt(position) as String
            returnIntent.putExtra("device_name",device_info.substring(0,device_info.length-17))
            returnIntent.putExtra("device_id",device_info.substring(device_info.length-17))
            setResult(Activity.RESULT_OK, returnIntent)
            finish()
        }

    }
}
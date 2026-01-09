package com.mallick.nil.profilemanager

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast

class MyDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Toast.makeText(context, "Device Admin Enabled", Toast.LENGTH_SHORT).show()
    }

    override fun onProfileProvisioningComplete(context: Context, intent: Intent) {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val adminComponentName = ComponentName(context, MyDeviceAdminReceiver::class.java)

        // Enable the work profile
        try {
            dpm.setProfileEnabled(adminComponentName)
        } catch (e: Exception) {
            Log.e("MyDeviceAdminReceiver", "Failed to enable profile", e)
        }

        Toast.makeText(context, "Work Profile Enabled", Toast.LENGTH_SHORT).show()
    }
}

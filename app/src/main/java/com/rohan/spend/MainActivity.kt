package com.rohan.spend

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import com.rohan.spend.bg.Alarms
import com.rohan.spend.ui.AppViewModel
import com.rohan.spend.ui.SpendApp

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    private val askSms = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.onPermission(true)
        // Denied twice: Android stops showing the dialog, so send them to settings.
        else if (!shouldShowRequestPermissionRationale(Manifest.permission.READ_SMS)) {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        Alarms.schedule(this)
        setContent { SpendApp(vm) { askSms.launch(Manifest.permission.READ_SMS) } }
    }

    override fun onResume() {
        super.onResume()
        vm.onResume()
    }
}

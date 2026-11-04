package com.beworkall.nyxo

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import com.beworkall.nyxo.data.WidgetHosting
import com.beworkall.nyxo.system.Sound
import com.beworkall.nyxo.ui.NyxoRoot

class MainActivity : ComponentActivity() {

    private val vm: LauncherViewModel by viewModels()

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            vm.markDirty()
        }
    }

    // Tilt light: a gravity sensor at UI rate, only while the home screen is in front and a glass theme is on.
    private var sensorManager: SensorManager? = null
    private var tiltOn = false
    private val tiltListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            val x = (-event.values[0] / SensorManager.GRAVITY_EARTH).coerceIn(-1f, 1f)
            val y = (event.values[1] / SensorManager.GRAVITY_EARTH).coerceIn(-1f, 1f)
            // Smooth it so the highlight drifts instead of jittering.
            vm.tiltX += (x - vm.tiltX) * 0.15f
            vm.tiltY += (y - vm.tiltY) * 0.15f
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        Sound.init(this)

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(this, packageReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        sensorManager = getSystemService(SensorManager::class.java)

        setContent { NyxoRoot(vm) }
    }

    override fun onStart() {
        super.onStart()
        runCatching { WidgetHosting.host(this).startListening() }
    }

    override fun onStop() {
        runCatching { WidgetHosting.host(this).stopListening() }
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        vm.onResume()
        startTilt()
    }

    override fun onPause() {
        stopTilt()
        super.onPause()
    }

    private fun startTilt() {
        val p = vm.prefs
        val wanted = p.tiltLight.value && p.motion.value && p.panelStyle.value != "solid" && p.themePack.value != "amoled"
        if (!wanted || tiltOn) return
        val sm = sensorManager ?: return
        val sensor = sm.getDefaultSensor(Sensor.TYPE_GRAVITY) ?: sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return
        tiltOn = sm.registerListener(tiltListener, sensor, SensorManager.SENSOR_DELAY_UI)
    }

    private fun stopTilt() {
        if (!tiltOn) return
        sensorManager?.unregisterListener(tiltListener)
        tiltOn = false
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Pressing Home while Nyxo is already showing: close everything.
        if (intent.action == Intent.ACTION_MAIN) vm.onHomePressed()
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(packageReceiver) }
        super.onDestroy()
    }
}

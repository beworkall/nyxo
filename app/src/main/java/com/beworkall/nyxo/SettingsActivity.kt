package com.beworkall.nyxo

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import com.beworkall.nyxo.data.WidgetHosting
import com.beworkall.nyxo.data.WidgetSpec
import com.beworkall.nyxo.system.Actions
import com.beworkall.nyxo.ui.settings.SettingsRoot

/** Nyxo Settings: its own app icon, so the home screen stays free of settings. */
class SettingsActivity : ComponentActivity() {

    private val vm: LauncherViewModel by viewModels()

    private var pendingId = -1
    private var pendingInfo: AppWidgetProviderInfo? = null

    private val bindLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) configureOrSave() else cancelPending()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setContent { SettingsRoot(vm, this) }
    }

    override fun onResume() {
        super.onResume()
        vm.onResume()
    }

    // ---------------------------------------------------------------- widgets

    fun addAppWidget(info: AppWidgetProviderInfo) {
        val host = WidgetHosting.host(this)
        val id = host.allocateAppWidgetId()
        pendingId = id
        pendingInfo = info
        val allowed = runCatching { WidgetHosting.manager(this).bindAppWidgetIdIfAllowed(id, info.provider) }.getOrDefault(false)
        if (allowed) {
            configureOrSave()
        } else {
            val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
            runCatching { bindLauncher.launch(intent) }.onFailure { cancelPending() }
        }
    }

    fun addBuiltInWidget(kind: String) {
        val nextId = (vm.prefs.widgets.value.mapNotNull { WidgetSpec.decode(it) }.maxOfOrNull { it.id } ?: 0) + 1
        vm.prefs.widgets.value = vm.prefs.widgets.value + WidgetSpec(kind, nextId, WidgetSpec.defaultHeight(kind)).encode()
        Actions.toast(this, "Added to your home screen")
    }

    private fun configureOrSave() {
        val info = pendingInfo ?: return
        if (info.configure != null) {
            try {
                WidgetHosting.host(this).startAppWidgetConfigureActivityForResult(this, pendingId, 0, REQ_CONFIGURE, null)
                return
            } catch (e: Exception) {
                // Some widgets don't allow configuring from outside; add it as is.
            }
        }
        savePending()
    }

    private fun savePending() {
        val info = pendingInfo ?: return
        val density = resources.displayMetrics.density
        val heightDp = ((info.minHeight / density).toInt() + 24).coerceIn(90, 420)
        vm.prefs.widgets.value = vm.prefs.widgets.value + WidgetSpec("app", pendingId, heightDp).encode()
        pendingId = -1
        pendingInfo = null
        Actions.toast(this, "Widget added to your home screen")
    }

    private fun cancelPending() {
        if (pendingId != -1) runCatching { WidgetHosting.host(this).deleteAppWidgetId(pendingId) }
        pendingId = -1
        pendingInfo = null
    }

    @Deprecated("Widget configuration still reports through onActivityResult")
    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_CONFIGURE) {
            if (resultCode == Activity.RESULT_OK) savePending() else cancelPending()
        }
    }

    fun removeWidget(spec: WidgetSpec) {
        if (spec.kind == "app") runCatching { WidgetHosting.host(this).deleteAppWidgetId(spec.id) }
        vm.prefs.widgets.value = vm.prefs.widgets.value.filter { WidgetSpec.decode(it)?.key != spec.key }
    }

    companion object {
        private const val REQ_CONFIGURE = 77
    }
}

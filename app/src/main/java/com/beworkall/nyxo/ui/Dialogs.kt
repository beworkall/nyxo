package com.beworkall.nyxo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.beworkall.nyxo.LauncherViewModel
import com.beworkall.nyxo.Screen
import com.beworkall.nyxo.data.AppEntry
import com.beworkall.nyxo.radar.RadarResult
import com.beworkall.nyxo.radar.Risk
import com.beworkall.nyxo.search.Search
import com.beworkall.nyxo.system.Actions

/** Long-press menu for any app. */
@Composable
fun AppMenu(vm: LauncherViewModel, app: AppEntry) {
    val context = LocalContext.current
    val result = vm.radar[app.pkg]
    val close = { vm.menuApp = null }
    Dialog(onDismissRequest = close) {
        val shape = RoundedCornerShape(24.dp)
        Column(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(Panel)
                .border(1.dp, Line, shape)
                .padding(vertical = 14.dp),
        ) {
            Row(Modifier.padding(horizontal = 18.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                AppIconImage(app, 48.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(app.label, color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(app.pkg, color = Faint, fontSize = 11.sp, fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (result != null && result.risk != Risk.CLEAN) {
                val color = riskColor(result.risk) ?: RiskMid
                Row(
                    Modifier
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(color.copy(alpha = 0.10f))
                        .border(1.dp, color.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
                        .clickable {
                            vm.radarDetail = result
                            vm.menuApp = null
                        }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadarDot(result.risk, size = 12.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        result.findings.joinToString(" · ") { it.title },
                        color = Ink,
                        fontSize = 13.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            MenuItem(if (vm.isOnHome(app)) "Remove from home" else "Add to home") {
                vm.toggleHome(app)
                close()
            }
            if (vm.prefs.gridLayout.value) {
                MenuItem(if (vm.isInDock(app)) "Remove from dock" else "Add to dock") {
                    if (!vm.toggleDock(app)) Actions.toast(context, "The dock holds ${LauncherViewModel.MAX_DOCK} apps")
                    close()
                }
            }
            val inStack = vm.stackOf(app)
            if (inStack != null) {
                MenuItem("Remove from stack “${inStack.name}”") {
                    vm.removeFromStack(inStack.id, app.key)
                    close()
                }
            } else {
                MenuItem("Add to a stack…") {
                    vm.stackPickerFor = app
                    close()
                }
            }
            MenuItem(if (vm.isHidden(app)) "Unhide" else "Hide app") {
                val wasHidden = vm.isHidden(app)
                vm.toggleHidden(app)
                Actions.toast(context, if (wasHidden) "${app.label} is visible again" else "${app.label} hidden. Find it at the end of the app list.")
                close()
            }
            MenuItem("Privacy details") {
                vm.radarDetail = result ?: RadarResult(app.pkg, app.label, true, emptyList())
                close()
            }
            MenuItem("App info") {
                Actions.appInfo(context, app.pkg)
                close()
            }
            if (!app.isSystem) {
                MenuItem("Uninstall", color = RiskHigh) {
                    Actions.uninstall(context, app.pkg)
                    close()
                }
            }
        }
    }
}

@Composable
private fun MenuItem(text: String, color: Color = Ink, onClick: () -> Unit) {
    Text(
        text,
        color = color,
        fontSize = 16.sp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 14.dp),
    )
}

/** Choose an existing stack or create a new one for [app]. */
@Composable
fun StackPicker(vm: LauncherViewModel, app: AppEntry) {
    var name by remember { mutableStateOf("") }
    val close = { vm.stackPickerFor = null }
    Dialog(onDismissRequest = close) {
        val shape = RoundedCornerShape(24.dp)
        Column(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(Panel)
                .border(1.dp, Line, shape)
                .padding(18.dp),
        ) {
            Text("Add ${app.label} to a stack", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text("Stacks group apps under one entry on your home screen.", color = Muted, fontSize = 13.sp)
            Spacer(Modifier.height(10.dp))
            for (s in vm.stacks) {
                MenuItem("${s.name}  ·  ${s.keys.size}") {
                    vm.addToStack(s.id, app)
                    close()
                }
            }
            Spacer(Modifier.height(8.dp))
            NameField(name, { name = it }, "New stack name, e.g. Social")
            ActionButton("Create stack", primary = true) {
                vm.createStack(name, app)
                close()
            }
        }
    }
}

/** Rename a stack, take apps out of it, or delete it. */
@Composable
fun StackEditor(vm: LauncherViewModel, stackId: String) {
    val stack = vm.stackById(stackId)
    val close = { vm.editStackId = null }
    if (stack == null) {
        LaunchedEffect(stackId) { vm.editStackId = null }
        return
    }
    var name by remember(stackId) { mutableStateOf(stack.name) }
    val byKey = remember(vm.apps) { vm.apps.associateBy { it.key } }
    Dialog(onDismissRequest = {
        vm.renameStack(stackId, name)
        close()
    }) {
        val shape = RoundedCornerShape(24.dp)
        Column(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(Panel)
                .border(1.dp, Line, shape)
                .padding(18.dp),
        ) {
            Text("Edit stack", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            NameField(name, { name = it }, "Stack name")
            Spacer(Modifier.height(8.dp))
            for (key in stack.keys) {
                val app = byKey[key] ?: continue
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppIconImage(app, 32.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(app.label, color = Ink, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text(
                        "Remove",
                        color = RiskHigh,
                        fontSize = 14.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { vm.removeFromStack(stackId, key) }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }
            ActionButton("Save", primary = true) {
                vm.renameStack(stackId, name)
                close()
            }
            ActionButton("Delete stack (keeps the apps)") { vm.deleteStack(stackId) }
        }
    }
}

@Composable
fun NameField(value: String, onChange: (String) -> Unit, placeholder: String) {
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        textStyle = TextStyle(color = Ink, fontSize = 16.sp),
        cursorBrush = SolidColor(accent()),
        modifier = Modifier.fillMaxWidth(),
        decorationBox = { inner ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(PanelHi)
                    .border(1.dp, Line, RoundedCornerShape(14.dp))
                    .padding(horizontal = 14.dp, vertical = 13.dp),
            ) {
                if (value.isEmpty()) Text(placeholder, color = Faint, fontSize = 15.sp)
                inner()
            }
        },
    )
}

/** Pick an app for a gesture. Passing null clears it. */
@Composable
fun AppPicker(vm: LauncherViewModel, title: String, onPick: (AppEntry?) -> Unit, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val list = remember(query, vm.apps) { if (query.isBlank()) vm.apps else Search.apps(vm.apps, query, 60) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val shape = RoundedCornerShape(24.dp)
        Column(
            Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.8f)
                .clip(shape)
                .background(Panel)
                .border(1.dp, Line, shape)
                .padding(16.dp),
        ) {
            Text(title, color = Ink, fontSize = 19.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp, bottom = 12.dp))
            SearchField(query, { query = it }, focus, Modifier.fillMaxWidth(), placeholder = "Search apps")
            Spacer(Modifier.height(8.dp))
            LazyColumn(Modifier.weight(1f)) {
                item {
                    Text(
                        "Nothing",
                        color = Muted,
                        fontSize = 16.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onPick(null) }
                            .padding(horizontal = 10.dp, vertical = 14.dp),
                    )
                }
                items(list.size, key = { list[it].key }) { i ->
                    val app = list[i]
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onPick(app) }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AppIconImage(app, 36.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(app.label, color = Ink, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
fun HiddenScreen(vm: LauncherViewModel) {
    val byKey = remember(vm.apps) { vm.apps.associateBy { it.key } }
    val hidden = vm.prefs.hidden.value.mapNotNull { byKey[it] }.sortedBy { it.label.lowercase() }
    Column(Modifier.fillMaxSize().systemBarsPadding()) {
        Header("Hidden apps", "Long-press an app to unhide it") { vm.screen = Screen.Drawer }
        if (hidden.isEmpty()) {
            Text(
                "Nothing hidden yet. Long-press any app and choose Hide app.",
                color = Muted,
                modifier = Modifier.padding(24.dp),
            )
        }
        LazyColumn(Modifier.padding(horizontal = 12.dp)) {
            items(hidden.size, key = { hidden[it].key }) { i -> AppRow(vm, hidden[i]) }
        }
    }
}

package com.beworkall.nyxo.ui

import android.app.Activity
import android.content.Intent
import android.provider.ContactsContract
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beworkall.nyxo.LauncherViewModel
import com.beworkall.nyxo.Screen
import com.beworkall.nyxo.data.AppEntry
import com.beworkall.nyxo.data.letterOf
import com.beworkall.nyxo.search.Calculator
import com.beworkall.nyxo.search.Contact
import com.beworkall.nyxo.search.Search
import com.beworkall.nyxo.search.UnitConverter
import com.beworkall.nyxo.system.Actions
import com.beworkall.nyxo.system.Sfx
import com.beworkall.nyxo.system.Sound
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Closes the drawer when the user keeps pulling down at the top of the list. */
private class PullToClose(private val onClose: () -> Unit) : NestedScrollConnection {
    private var pulled = 0f

    @Suppress("DEPRECATION")
    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        if (source == NestedScrollSource.Drag && available.y > 0f) {
            pulled += available.y
            if (pulled > 240f) {
                pulled = 0f
                onClose()
            }
        } else if (consumed.y != 0f) {
            pulled = 0f
        }
        return Offset.Zero
    }
}

@Composable
fun DrawerScreen(vm: LauncherViewModel) {
    val prefs = vm.prefs
    var query by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val hidden = prefs.hidden.value
    val visible = remember(vm.apps, hidden) { vm.apps.filter { it.key !in hidden } }
    val pullToClose = remember { PullToClose { vm.back() } }

    LaunchedEffect(Unit) {
        if (prefs.autoKeyboard.value || vm.drawerFocusSearch) {
            delay(220)
            runCatching { focus.requestFocus() }
        }
        vm.drawerFocusSearch = false
    }

    Column(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
        Box(
            Modifier
                .fillMaxWidth()
                .pointerInput(Unit) {
                    detectVerticalDragGestures { _, dragAmount -> if (dragAmount > 18f) vm.back() }
                }
                .padding(top = 10.dp, bottom = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(width = 42.dp, height = 4.dp).clip(CircleShape).background(Faint))
        }
        Row(Modifier.padding(start = 16.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            SearchField(
                value = query,
                onChange = { query = it },
                focus = focus,
                modifier = Modifier.weight(1f),
                onGo = { Search.apps(visible, query, 1).firstOrNull()?.let { vm.launch(it) } },
            )
            IconBtn({
                keyboard?.hide()
                vm.screen = Screen.Radar
            }) { RadarGlyph(accent()) }
        }
        Spacer(Modifier.height(8.dp))
        if (query.isBlank()) {
            AppListing(vm, visible, pullToClose)
        } else {
            SearchResults(vm, visible, query.trim())
        }
    }
}

@Composable
private fun AppListing(vm: LauncherViewModel, apps: List<AppEntry>, pullToClose: NestedScrollConnection) {
    val prefs = vm.prefs
    val grid = prefs.gridLayout.value
    val letters = remember(apps) { apps.map { letterOf(it.label) }.distinct() }
    val firstIndex = remember(apps) {
        val m = HashMap<Char, Int>()
        apps.forEachIndexed { i, a -> if (!m.containsKey(letterOf(a.label))) m[letterOf(a.label)] = i }
        m
    }
    val gridState = rememberLazyGridState()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val hiddenCount = prefs.hidden.value.size

    Row(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxHeight()) {
            if (grid) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(prefs.columns.value.coerceIn(3, 6)),
                    state = gridState,
                    modifier = Modifier.fillMaxSize().nestedScroll(pullToClose),
                    contentPadding = PaddingValues(start = 10.dp, end = 2.dp, bottom = 28.dp),
                ) {
                    items(apps.size, key = { apps[it].key }) { i -> AppCell(vm, apps[i]) }
                    if (hiddenCount > 0) {
                        item(span = { GridItemSpan(maxLineSpan) }) { HiddenAppsRow(vm, hiddenCount) }
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().nestedScroll(pullToClose),
                    contentPadding = PaddingValues(start = 14.dp, end = 4.dp, bottom = 28.dp),
                ) {
                    items(apps.size, key = { apps[it].key }) { i -> AppRow(vm, apps[i]) }
                    if (hiddenCount > 0) item { HiddenAppsRow(vm, hiddenCount) }
                }
            }
        }
        AlphabetBar(letters) { letter ->
            val index = firstIndex[letter] ?: return@AlphabetBar
            scope.launch {
                if (grid) gridState.scrollToItem(index) else listState.scrollToItem(index)
            }
        }
    }
}

@Composable
private fun HiddenAppsRow(vm: LauncherViewModel, count: Int) {
    val activity = LocalContext.current as? Activity
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 16.dp, end = 8.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable {
                if (activity != null) {
                    Actions.authenticate(activity, "Hidden apps") {
                        Sound.play(Sfx.UNLOCK)
                        vm.screen = Screen.Hidden
                    }
                }
            }
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Lock, contentDescription = null, tint = Muted, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text("Hidden apps ($count)", color = Muted, fontSize = 14.sp)
    }
}

@Composable
private fun AlphabetBar(letters: List<Char>, onPick: (Char) -> Unit) {
    if (letters.size < 2) return
    val haptic = LocalHapticFeedback.current
    var height by remember { mutableIntStateOf(1) }
    var active by remember { mutableStateOf<Char?>(null) }
    val pick = rememberUpdatedState(onPick)

    fun letterAt(y: Float): Char {
        val i = (y / height.coerceAtLeast(1) * letters.size).toInt().coerceIn(0, letters.lastIndex)
        return letters[i]
    }

    fun select(c: Char) {
        if (c != active) {
            active = c
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            pick.value(c)
        }
    }

    Column(
        Modifier
            .fillMaxHeight()
            .width(28.dp)
            .padding(vertical = 12.dp)
            .onSizeChanged { height = it.height }
            .pointerInput(letters) {
                detectTapGestures { pos ->
                    select(letterAt(pos.y))
                    active = null
                }
            }
            .pointerInput(letters) {
                detectVerticalDragGestures(
                    onDragEnd = { active = null },
                    onDragCancel = { active = null },
                ) { change, _ -> select(letterAt(change.position.y)) }
            },
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        for (c in letters) {
            Text(
                c.toString(),
                color = if (c == active) accent() else Faint,
                fontSize = if (c == active) 14.sp else 10.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun SearchResults(vm: LauncherViewModel, apps: List<AppEntry>, q: String) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val calc = remember(q) {
        UnitConverter.convert(q) ?: Calculator.evaluate(q)?.let { "= " + Calculator.format(it) }
    }
    val appHits = remember(q, apps) { Search.apps(apps, q) }
    val settingHits = remember(q) { Search.settings(q) }
    var contacts by remember { mutableStateOf<List<Contact>>(emptyList()) }
    val contactsOn = vm.prefs.searchContacts.value
    LaunchedEffect(q, contactsOn) {
        contacts = if (contactsOn) withContext(Dispatchers.IO) { Search.contacts(context, q) } else emptyList()
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 28.dp),
    ) {
        if (calc != null) {
            item {
                CalcCard(calc) {
                    clipboard.setText(AnnotatedString(calc.substringAfter("= ").substringAfter(" = ")))
                    Actions.toast(context, "Copied")
                }
            }
        }
        if (appHits.isNotEmpty()) {
            item { SectionLabel("Apps") }
            items(appHits.size, key = { "app:" + appHits[it].key }) { i -> AppRow(vm, appHits[i]) }
        }
        if (contacts.isNotEmpty()) {
            item { SectionLabel("Contacts") }
            items(contacts.size, key = { "contact:" + contacts[it].id }) { i ->
                val c = contacts[i]
                ResultRow(c.name, null, { Icon(Icons.Filled.Person, contentDescription = null, tint = Muted) }) {
                    val uri = ContactsContract.Contacts.getLookupUri(c.id, c.lookup)
                    if (Actions.start(context, Intent(Intent.ACTION_VIEW, uri))) vm.returnHomeSoon()
                }
            }
        }
        if (settingHits.isNotEmpty()) {
            item { SectionLabel("Settings") }
            items(settingHits.size, key = { "setting:" + settingHits[it].action }) { i ->
                val s = settingHits[i]
                ResultRow(s.label, "Open settings", { Icon(Icons.Filled.Settings, contentDescription = null, tint = Muted) }) {
                    if (Actions.start(context, Intent(s.action))) vm.returnHomeSoon()
                }
            }
        }
        item {
            Column(Modifier.padding(top = 10.dp)) {
                ResultRow("Search the web for “$q”", "Opens in your browser", {
                    Icon(Icons.Filled.Search, contentDescription = null, tint = Muted)
                }) {
                    Actions.web(context, q)
                    vm.returnHomeSoon()
                }
            }
        }
    }
}

@Composable
private fun CalcCard(text: String, onCopy: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp)
            .clip(shape)
            .background(accent().copy(alpha = 0.10f))
            .border(1.dp, accent().copy(alpha = 0.45f), shape)
            .clickable(onClick = onCopy)
            .padding(horizontal = 18.dp, vertical = 16.dp),
    ) {
        Text(text, color = Ink, fontSize = 28.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text("Tap to copy", color = Muted, fontSize = 12.sp)
    }
}

@Composable
private fun ResultRow(title: String, subtitle: String?, icon: @Composable () -> Unit, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(PanelHi), contentAlignment = Alignment.Center) { icon() }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Ink, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, color = Muted, fontSize = 12.sp)
        }
    }
}

package com.beworkall.nyxo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.beworkall.nyxo.LauncherViewModel
import com.beworkall.nyxo.Screen
import com.beworkall.nyxo.data.AppEntry
import androidx.compose.ui.graphics.Color
import com.beworkall.nyxo.radar.Fix
import com.beworkall.nyxo.radar.PrivacyRadar
import com.beworkall.nyxo.radar.RadarResult
import com.beworkall.nyxo.radar.Risk
import com.beworkall.nyxo.system.Actions

class PrivacyScore(val score: Int, val grade: String, val color: Color, val summary: String, val fixFirst: List<RadarResult>)

/** One number for the whole phone. Hidden helpers and full-access apps weigh the most. */
fun privacyScore(results: Collection<RadarResult>): PrivacyScore {
    val helpers = results.filter { !it.hasIcon && it.risk == Risk.HIGH }
    val high = results.filter { it.hasIcon && it.risk == Risk.HIGH }
    val outside = results.filter { r -> r.findings.any { it.title == PrivacyRadar.NOT_FROM_STORE } }
    val medium = results.count { it.hasIcon && it.risk == Risk.MEDIUM }
    val penalty = helpers.size * 20 + high.size * 8 + outside.size * 4 + medium
    val score = if (results.isEmpty()) 100 else (100 - penalty).coerceIn(0, 100)
    val (grade, color) = when {
        score >= 85 -> "Great" to RiskOk
        score >= 65 -> "Good" to Color(0xFF9BE36D)
        score >= 40 -> "Needs a look" to RiskMid
        else -> "At risk" to RiskHigh
    }
    val summary = when {
        results.isEmpty() -> "Checking your apps…"
        helpers.isEmpty() && high.isEmpty() -> "No apps with full access. ${outside.size} from outside an app store."
        else -> "${high.size + helpers.size} with full access · ${outside.size} from outside an app store"
    }
    val fix = (helpers + high.sortedByDescending { it.findings.size } + outside).distinctBy { it.pkg }.take(3)
    return PrivacyScore(score, grade, color, summary, fix)
}

@Composable
fun RadarScreen(vm: LauncherViewModel) {
    val results = vm.radar.values.toList()
    val byPkg = remember(vm.apps) { vm.apps.associateBy { it.pkg } }
    val withIcon = results.filter { it.hasIcon }
    val high = withIcon.filter { it.risk == Risk.HIGH }.sortedBy { it.label.lowercase() }
    val medium = withIcon.filter { it.risk == Risk.MEDIUM }
        .sortedWith(compareByDescending<RadarResult> { it.findings.size }.thenBy { it.label.lowercase() })
    val clean = withIcon.filter { it.risk == Risk.CLEAN }.sortedBy { it.label.lowercase() }
    val helpers = results.filter { !it.hasIcon && it.risk == Risk.HIGH }.sortedBy { it.label.lowercase() }
    var showClean by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().systemBarsPadding()) {
        Header("Privacy Radar", "What your apps can access right now") { vm.back() }
        val score = privacyScore(results)
        val changed = vm.changes.keys.mapNotNull { vm.radar[it] }.sortedBy { it.label.lowercase() }
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp)) {
            item {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                        .nyxoSurface(RoundedCornerShape(22.dp))
                        .padding(18.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(score.score.toString(), color = score.color, fontSize = 48.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text("Privacy score · ${score.grade}", color = Ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                            Text(score.summary, color = Muted, fontSize = 13.sp, lineHeight = 18.sp)
                        }
                    }
                    if (score.fixFirst.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        Text("FIX THESE FIRST", color = accent(), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                        for (r in score.fixFirst) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { vm.radarDetail = r }
                                    .padding(vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadarDot(r.risk, size = 10.dp)
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    "${r.label}: ${r.findings.first().title.lowercase()}",
                                    color = Ink,
                                    fontSize = 14.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
            item {
                SwitchRow(
                    "Privacy dots on icons",
                    "Red: full access. Amber: sensitive access. White ring: new since you last looked.",
                    vm.prefs.radarDots.value,
                ) { vm.prefs.radarDots.value = it }
            }
            if (changed.isNotEmpty()) {
                item {
                    Column {
                        SectionLabel("Changed since you last looked")
                        Text(
                            "These apps gained access after an update or a permission prompt.",
                            color = Muted,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(start = 6.dp, bottom = 6.dp),
                        )
                    }
                }
                items(changed.size, key = { "n:" + changed[it].pkg }) { i ->
                    RadarRow(byPkg[changed[i].pkg], changed[i], isNew = true) { vm.radarDetail = changed[i] }
                }
                item { ActionButton("Mark all as seen") { vm.markAllSeen() } }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile("Full access", high.size + helpers.size, RiskHigh)
                    StatTile("Sensitive", medium.size, RiskMid)
                    StatTile("Clean", clean.size, RiskOk)
                }
            }
            if (results.isEmpty()) {
                item {
                    Text(
                        if (vm.scanning) "Scanning your apps…" else "No apps found yet.",
                        color = Muted,
                        modifier = Modifier.padding(top = 24.dp),
                    )
                }
            }
            if (helpers.isNotEmpty()) {
                item {
                    Column {
                        SectionLabel("Hidden helpers")
                        Text(
                            "These have powerful access but no icon on your home screen. Make sure you recognise every one.",
                            color = Muted,
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            modifier = Modifier.padding(start = 6.dp, bottom = 6.dp),
                        )
                    }
                }
                items(helpers.size, key = { "h:" + helpers[it].pkg }) { i ->
                    RadarRow(null, helpers[i]) { vm.radarDetail = helpers[i] }
                }
            }
            if (high.isNotEmpty()) {
                item { SectionLabel("Full access") }
                items(high.size, key = { "r:" + high[it].pkg }) { i ->
                    RadarRow(byPkg[high[i].pkg], high[i], isNew = high[i].pkg in vm.changes) { vm.radarDetail = high[i] }
                }
            }
            if (medium.isNotEmpty()) {
                item { SectionLabel("Sensitive access") }
                items(medium.size, key = { "m:" + medium[it].pkg }) { i ->
                    RadarRow(byPkg[medium[i].pkg], medium[i], isNew = medium[i].pkg in vm.changes) { vm.radarDetail = medium[i] }
                }
            }
            if (clean.isNotEmpty()) {
                item {
                    Column(Modifier.padding(top = 10.dp)) {
                        SettingRow(
                            "${clean.size} apps with no sensitive access",
                            if (showClean) "Tap to hide" else "Tap to show",
                        ) { showClean = !showClean }
                    }
                }
                if (showClean) {
                    items(clean.size, key = { "c:" + clean[it].pkg }) { i ->
                        RadarRow(byPkg[clean[i].pkg], clean[i]) { vm.radarDetail = clean[i] }
                    }
                }
            }
            item {
                Column(Modifier.padding(top = 18.dp)) {
                    ActionButton(if (vm.scanning) "Scanning…" else "Scan again") { vm.refresh(immediate = true) }
                }
            }
        }
    }
}

@Composable
private fun RadarRow(app: AppEntry?, result: RadarResult, isNew: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (app != null) AppIconImage(app, 40.dp) else LetterIcon(result.label, 40.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(result.label, color = Ink, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (isNew) {
                    Spacer(Modifier.width(8.dp))
                    NewTag()
                }
            }
            val summary = if (result.findings.isEmpty()) "No sensitive access" else result.findings.joinToString(" · ") { it.title }
            Text(summary, color = Muted, fontSize = 12.sp, lineHeight = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        RadarDot(result.risk, size = 12.dp)
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Faint)
    }
}

@Composable
fun RadarDetailDialog(vm: LauncherViewModel, result: RadarResult) {
    val context = LocalContext.current
    val app = remember(vm.apps, result.pkg) { vm.apps.firstOrNull { it.pkg == result.pkg } }
    Dialog(onDismissRequest = { vm.radarDetail = null }) {
        val shape = RoundedCornerShape(24.dp)
        Column(
            Modifier
                .fillMaxWidth()
                .nyxoSurface(shape)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (app != null) AppIconImage(app, 48.dp) else LetterIcon(result.label, 48.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(result.label, color = Ink, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                    Text(result.pkg, color = Faint, fontSize = 11.sp, fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.height(16.dp))
            if (result.findings.isEmpty()) {
                Text("This app has none of the access Nyxo watches for.", color = Muted, fontSize = 14.sp)
            }
            for (f in result.findings) {
                Row(Modifier.padding(vertical = 8.dp)) {
                    Box(
                        Modifier
                            .padding(top = 6.dp)
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (f.high) RiskHigh else RiskMid)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(f.title, color = Ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text(f.detail, color = Muted, fontSize = 13.sp, lineHeight = 18.sp)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            val fixes = result.findings.map { it.fix }.distinct()
            for (fix in fixes) {
                ActionButton(Actions.fixLabel(fix), primary = fix != Fix.APP) { Actions.openFix(context, result.pkg, fix) }
            }
            if (Fix.APP !in fixes) {
                ActionButton("App settings") { Actions.appInfo(context, result.pkg) }
            }
            ActionButton("Got it") {
                vm.markSeen(result.pkg)
                vm.radarDetail = null
            }
        }
    }
}

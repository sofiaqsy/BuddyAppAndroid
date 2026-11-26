package com.buddy.app.features.business

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.buddy.app.core.designsystem.BuddyColor
import com.buddy.app.core.designsystem.BuddyType

/**
 * Horario de atención de una sede (iOS hoursCard, 26 Nov 2026): 7 días, abierto o
 * cerrado, de/a, "Copiar este horario a los demás días abiertos" y un solo Guardar.
 * Sin horario guardado empieza con Lun–Sáb 8:00 a. m. – 6:00 p. m.
 */
@Composable
fun HoursEditorScreen(
    spotName: String,
    saved: List<DayHours>?,
    modifier: Modifier = Modifier,
    onClose: () -> Unit,
    onSave: (List<DayHours>, done: (Boolean) -> Unit) -> Unit,
) {
    val draft = remember { mutableStateListOf<DayHours>().apply { addAll((saved ?: DayHours.defaults()).sortedBy { it.day }) } }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    BackHandler(enabled = !saving) { onClose() }
    val ctx = LocalContext.current

    fun pickTime(current: Int, set: (Int) -> Unit) {
        android.app.TimePickerDialog(ctx, { _, h, m -> set(h * 60 + m) }, current / 60, current % 60, false).show()
    }

    Column(modifier.fillMaxSize().background(BuddyColor.Canvas).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).shadow(6.dp, CircleShape).clip(CircleShape).background(BuddyColor.Surface)
                    .clickable(enabled = !saving, onClick = onClose).semantics { contentDescription = "Atrás" },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = BuddyColor.Brand, modifier = Modifier.size(20.dp)) }
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Horario de atención", style = BuddyType.Title3, color = BuddyColor.Ink)
                Text(spotName, style = BuddyType.Footnote, color = BuddyColor.InkMuted)
            }
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)
                .shadow(3.dp, RoundedCornerShape(18.dp)).clip(RoundedCornerShape(18.dp)).background(BuddyColor.Surface)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(HoursSummary.text(draft), style = BuddyType.Footnote, color = BuddyColor.InkMuted)
            draft.forEachIndexed { i, d ->
                if (i > 0) HorizontalDivider(color = BuddyColor.Hairline)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(DayHours.names[d.day], style = BuddyType.Headline, color = BuddyColor.Ink, modifier = Modifier.weight(1f))
                    Text(if (d.open) "Abierto" else "Cerrado", style = BuddyType.Footnote, color = BuddyColor.InkMuted)
                    Spacer(Modifier.width(8.dp))
                    Switch(checked = d.open, onCheckedChange = { draft[i] = d.copy(open = it) },
                        colors = SwitchDefaults.colors(checkedTrackColor = BuddyColor.Brand))
                }
                if (d.open) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("De", style = BuddyType.Footnote, color = BuddyColor.InkMuted)
                        TimeChip(HoursSummary.time(d.from)) { pickTime(d.from) { v -> draft[i] = draft[i].copy(from = v) } }
                        Text("a", style = BuddyType.Footnote, color = BuddyColor.InkMuted)
                        TimeChip(HoursSummary.time(d.to)) { pickTime(d.to) { v -> draft[i] = draft[i].copy(to = v) } }
                    }
                    if (i == draft.indexOfFirst { it.open }) Row(
                        Modifier.clickable {
                            for (k in draft.indices) if (draft[k].open) draft[k] = draft[k].copy(from = d.from, to = d.to)
                        }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(Icons.Outlined.ContentCopy, null, tint = BuddyColor.Brand, modifier = Modifier.size(16.dp))
                        Text("Copiar este horario a los demás días abiertos", style = BuddyType.Footnote, color = BuddyColor.Brand)
                    }
                }
            }
        }
        error?.let { Text(it, style = BuddyType.Footnote, color = BuddyColor.ErrorRed, modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) }
        Row(Modifier.padding(20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f).height(48.dp).clip(CircleShape).background(BuddyColor.SageTint)
                .clickable(enabled = !saving, onClick = onClose), contentAlignment = Alignment.Center) {
                Text("Cancelar", style = BuddyType.FootnoteBold, color = BuddyColor.Brand)
            }
            Box(Modifier.weight(1f).height(48.dp).clip(CircleShape)
                .background(if (saving) BuddyColor.BrandDisabled else BuddyColor.Brand)
                .clickable(enabled = !saving) {
                    val bad = draft.firstOrNull { it.open && it.to <= it.from }
                    if (bad != null) { error = "${DayHours.names[bad.day]}: la hora de cierre debe ser después de la de apertura."; return@clickable }
                    error = null; saving = true
                    onSave(draft.toList()) { ok -> saving = false; if (!ok) error = "No pudimos guardar el horario. Revisa tu conexión." }
                }, contentAlignment = Alignment.Center) {
                if (saving) CircularProgressIndicator(Modifier.size(20.dp), color = BuddyColor.InkInverse, strokeWidth = 2.dp)
                else Text("Guardar horario", style = BuddyType.FootnoteBold, color = BuddyColor.InkInverse)
            }
        }
    }
}

@Composable
private fun TimeChip(text: String, onClick: () -> Unit) {
    Text(text, style = BuddyType.Body, color = BuddyColor.Ink,
        modifier = Modifier.clip(CircleShape).background(BuddyColor.Canvas).clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp))
}

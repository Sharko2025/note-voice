package com.sitpro.notevoice.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sitpro.notevoice.R

/** Mockup "Recording": bottom sheet over the list, live level bars, 30 s limit, Cancel / Stop. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordSheet(recording: RecordController, forNotes: Boolean) {
    val c = colors
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = { recording.cancel() }, // back or tap outside = Cancel
        sheetState = state,
        containerColor = c.surface,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val pulse by rememberInfiniteTransition(label = "dot").animateFloat(
                1f, 0.3f, infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "dot",
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(11.dp).alpha(pulse).clip(CircleShape).background(c.danger))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.listening), color = c.fg, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
            }
            val s = recording.elapsedMs / 1000
            Text("%02d:%02d / 00:30".format(s / 60, s % 60), color = c.muted, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
            Row(Modifier.height(52.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                recording.levels.forEach { level ->
                    Box(Modifier.width(6.dp).height((6 + level * 46).dp).clip(RoundedCornerShape(3.dp)).background(c.fin))
                }
            }
            Text(
                stringResource(if (forNotes) R.string.record_hint_notes else R.string.record_hint) + "\n" + stringResource(R.string.record_auto_stop),
                color = c.muted,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PillButton(stringResource(R.string.cancel), filled = false, modifier = Modifier.weight(1f)) { recording.cancel() }
                Row(
                    Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(22.dp)).background(c.fin).clickable { recording.stop() },
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Stop, null, tint = c.onFin, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.stop), color = c.onFin, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                }
            }
        }
    }
}

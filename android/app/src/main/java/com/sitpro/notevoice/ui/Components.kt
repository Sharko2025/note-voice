package com.sitpro.notevoice.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sitpro.notevoice.R

/** The thin blue wave under the My Day title (from the logo). */
@Composable
fun WaveLine(modifier: Modifier = Modifier) {
    val wave = colors.wave
    Canvas(modifier.fillMaxWidth().height(10.dp)) {
        val path = Path()
        val step = 23.dp.toPx()
        val mid = size.height / 2
        path.moveTo(0f, mid)
        var x = 0f
        var up = true
        while (x < size.width) {
            path.quadraticTo(x + step / 2, if (up) 0f else size.height, x + step, mid)
            x += step
            up = !up
        }
        drawPath(path, wave.copy(alpha = 0.7f), style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
    }
}

/** Round completion box. */
@Composable
fun CheckCircle(done: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = colors
    Box(
        modifier
            .size(22.dp)
            .clip(CircleShape)
            .then(if (done) Modifier.background(c.primary) else Modifier.border(2.dp, c.muted, CircleShape))
            .clickable(role = Role.Checkbox, onClickLabel = stringResource(R.string.mark_complete), onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (done) Icon(Icons.Filled.Check, null, tint = c.onPrimary, modifier = Modifier.size(14.dp))
    }
}

@Composable
fun StarButton(on: Boolean, onClick: () -> Unit, size: Int = 22) {
    val c = colors
    Icon(
        if (on) Icons.Filled.Star else Icons.Outlined.StarOutline,
        contentDescription = stringResource(R.string.mark_important),
        tint = if (on) c.star else c.muted,
        modifier = Modifier.size((size + 12).dp).clip(CircleShape).clickable(onClick = onClick).padding(6.dp),
    )
}

/** Navy square with the note-page logo, for the menu header. */
@Composable
fun LogoBadge(size: Int = 38) {
    Box(
        Modifier.size(size.dp).clip(RoundedCornerShape((size * 0.3f).dp)).background(Color(0xFF0B2545)),
        contentAlignment = Alignment.Center,
    ) {
        // The adaptive-icon foreground has a wide safe margin; draw it larger than the box and let the clip crop it.
        Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.requiredSize((size * 1.75f).dp))
    }
}

/** Bottom bar of every list: type to add, or tap the orange mic. While typing the mic becomes a check. */
@Composable
fun AddBar(
    text: String,
    onText: (String) -> Unit,
    placeholder: String,
    onSubmit: () -> Unit,
    onMic: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = colors
    val typing = text.isNotBlank()
    Row(modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier
                .weight(1f)
                .height(50.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(c.surface)
                .border(1.dp, c.line, RoundedCornerShape(14.dp))
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Add, null, tint = c.primary)
            Spacer(Modifier.width(8.dp))
            Box(Modifier.weight(1f)) {
                if (text.isEmpty()) Text(placeholder, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                BasicTextField(
                    value = text,
                    onValueChange = onText,
                    singleLine = true,
                    textStyle = TextStyle(color = c.fg, fontSize = 16.sp),
                    cursorBrush = SolidColor(c.primary),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onSubmit() }),
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = placeholder },
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Box(
            Modifier
                .size(56.dp)
                .shadow(4.dp, RoundedCornerShape(18.dp))
                .clip(RoundedCornerShape(18.dp))
                .background(c.fin)
                .clickable(onClickLabel = stringResource(R.string.record)) { if (typing) onSubmit() else onMic() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (typing) Icons.Filled.Check else Icons.Filled.Mic,
                contentDescription = stringResource(if (typing) R.string.save else R.string.record),
                tint = c.onFin,
                modifier = Modifier.size(28.dp),
            )
        }
    }
}

/** Snackbar with a bold first line and the heard text under it (mockup "Result with Undo"). */
data class AppSnack(
    override val message: String,
    val detail: String? = null,
    override val actionLabel: String? = null,
    override val duration: SnackbarDuration = if (actionLabel != null) SnackbarDuration.Long else SnackbarDuration.Short,
) : SnackbarVisuals {
    override val withDismissAction: Boolean = false
}

@Composable
fun AppSnackbar(data: SnackbarData) {
    val c = colors
    val visuals = data.visuals
    Row(
        Modifier
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .fillMaxWidth()
            .shadow(6.dp, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .background(c.fg)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(visuals.message, color = c.bg, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            (visuals as? AppSnack)?.detail?.let { Text(it, color = c.bg.copy(alpha = 0.75f), fontSize = 12.5.sp) }
        }
        visuals.actionLabel?.let { label ->
            Spacer(Modifier.width(12.dp))
            Text(
                label,
                color = c.fin,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { data.performAction() }.padding(6.dp),
            )
        }
    }
}

/** Fin on the waves, a line of help and sample sentences. */
@Composable
fun EmptyState(title: String, body: String, samples: List<String>, modifier: Modifier = Modifier) {
    val c = colors
    Column(
        modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Image(painterResource(R.drawable.art_empty), null, Modifier.size(width = 120.dp, height = 84.dp))
        Text(title, color = c.fg, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
        Text(body, color = c.muted, fontSize = 14.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        if (samples.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.try_saying), color = c.muted, fontSize = 12.sp, modifier = Modifier.fillMaxWidth())
            samples.forEach {
                Text(
                    it,
                    color = c.muted,
                    fontSize = 13.sp,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(c.surface).padding(10.dp),
                )
            }
        }
    }
}

/** Small icon + text used in task meta lines. */
@Composable
fun MetaItem(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String?, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = color, modifier = Modifier.size(14.dp))
        if (text != null) {
            Spacer(Modifier.width(3.dp))
            Text(text, color = color, fontSize = 12.sp)
        }
    }
}

/** For the "Processing…" placeholder bar. */
@Composable
fun SkeletonBar(fraction: Float) {
    Box(
        Modifier.fillMaxWidth(fraction).height(9.dp).clip(RoundedCornerShape(6.dp)).background(colors.surface2)
    )
}

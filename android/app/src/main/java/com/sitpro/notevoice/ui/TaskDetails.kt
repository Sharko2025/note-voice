package com.sitpro.notevoice.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sitpro.notevoice.R
import com.sitpro.notevoice.data.CachedTask
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneOffset

/** Callbacks of the task details; one set for the bottom sheet and the unfolded right pane. */
class TaskActions(
    val toggleComplete: (CachedTask) -> Unit,
    val toggleStar: (CachedTask) -> Unit,
    val setMyDay: (CachedTask, Boolean) -> Unit,
    val setReminder: (CachedTask, Instant?) -> Unit,
    val setDueDate: (CachedTask, LocalDate?) -> Unit,
    val saveText: (CachedTask, String, String) -> Unit,
    val delete: (CachedTask) -> Unit,
)

private enum class ReminderStep { NONE, QUICK, PICK }

/** Mockup "Task details": title, My Day, reminder, due date, note, delete. Text is saved when it closes. */
@Composable
fun TaskDetails(task: CachedTask, today: LocalDate, actions: TaskActions, modifier: Modifier = Modifier) {
    val c = colors
    val texts = rememberTexts()
    var title by rememberSaveable(task.id) { mutableStateOf(task.title) }
    var note by rememberSaveable(task.id) { mutableStateOf(task.note.orEmpty()) }
    var reminderStep by remember { mutableStateOf(ReminderStep.NONE) }
    var pickDue by remember { mutableStateOf(false) }

    val latestTask by rememberUpdatedState(task)
    val latestTitle by rememberUpdatedState(title)
    val latestNote by rememberUpdatedState(note)
    DisposableEffect(task.id) {
        onDispose { actions.saveText(latestTask, latestTitle, latestNote) }
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(bottom = 6.dp)) {
            CheckCircle(!task.isOpen, { actions.toggleComplete(task) }, Modifier.padding(top = 4.dp))
            Spacer(Modifier.width(12.dp))
            BasicTextField(
                value = title,
                onValueChange = { title = it },
                textStyle = TextStyle(color = c.fg, fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
                cursorBrush = SolidColor(c.primary),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.weight(1f).padding(top = 2.dp),
            )
            StarButton(task.starred, { actions.toggleStar(task) })
        }

        DetailField(
            icon = Icons.Outlined.WbSunny,
            text = stringResource(if (task.myDay) R.string.in_my_day else R.string.add_to_my_day),
            on = task.myDay,
            onClick = { actions.setMyDay(task, !task.myDay) },
            onClear = if (task.myDay) ({ actions.setMyDay(task, false) }) else null,
        )
        val remind = task.remindAtMillis
        DetailField(
            icon = Icons.Outlined.Notifications,
            text = remind?.let { stringResource(R.string.remind_me_at, texts.time(it)) } ?: stringResource(R.string.remind_me),
            value = remind?.let { texts.dueLabel(vnDate(it), today) },
            on = remind != null,
            onClick = { reminderStep = ReminderStep.QUICK },
            onClear = if (remind != null) ({ actions.setReminder(task, null) }) else null,
        )
        val due = task.dueDate?.let(LocalDate::parse)
        DetailField(
            icon = Icons.Outlined.CalendarMonth,
            text = when (due) {
                null -> stringResource(R.string.add_due_date)
                today -> stringResource(R.string.due_today)
                else -> stringResource(R.string.due_on, texts.dueLabel(due, today))
            },
            on = due != null,
            danger = isOverdue(task, today),
            onClick = { pickDue = true },
            onClear = if (due != null) ({ actions.setDueDate(task, null) }) else null,
        )

        Box(
            Modifier
                .padding(top = 10.dp)
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(c.surface2)
                .padding(12.dp)
        ) {
            if (note.isEmpty()) Text(stringResource(R.string.task_note_hint), color = c.muted, fontSize = 14.sp)
            BasicTextField(
                value = note,
                onValueChange = { note = it },
                textStyle = TextStyle(color = c.fg, fontSize = 14.sp),
                cursorBrush = SolidColor(c.primary),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            val created = task.createdAtMillis?.let { texts.whenSaved(it, today) }
            Text(
                when {
                    created == null -> ""
                    task.voiceEntryId != null -> stringResource(R.string.created_voice, created)
                    else -> stringResource(R.string.created_typed, created)
                },
                color = c.muted,
                fontSize = 12.sp,
                modifier = Modifier.weight(1f),
            )
            Icon(
                Icons.Outlined.Delete,
                stringResource(R.string.delete),
                tint = c.danger,
                modifier = Modifier.size(40.dp).clip(CircleShape).clickable { actions.delete(task) }.padding(8.dp),
            )
        }
    }

    when (reminderStep) {
        ReminderStep.QUICK -> ReminderQuickSheet(
            onPick = { actions.setReminder(task, it); reminderStep = ReminderStep.NONE },
            onCustom = { reminderStep = ReminderStep.PICK },
            onDismiss = { reminderStep = ReminderStep.NONE },
        )
        ReminderStep.PICK -> ReminderPickerSheet(
            initial = task.remindAtMillis?.let(Instant::ofEpochMilli),
            onSave = { actions.setReminder(task, it); reminderStep = ReminderStep.NONE },
            onDismiss = { reminderStep = ReminderStep.NONE },
        )
        ReminderStep.NONE -> Unit
    }
    if (pickDue) DueDateDialog(task.dueDate?.let(LocalDate::parse) ?: today, { actions.setDueDate(task, it); pickDue = false }, { pickDue = false })
}

@Composable
private fun DetailField(
    icon: ImageVector,
    text: String,
    on: Boolean,
    onClick: () -> Unit,
    onClear: (() -> Unit)?,
    value: String? = null,
    danger: Boolean = false,
) {
    val c = colors
    val tint = when {
        danger -> c.danger
        on -> c.primary
        else -> c.muted
    }
    Column {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(vertical = 12.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Text(text, color = if (on || danger) tint else c.fg, fontSize = 14.sp, modifier = Modifier.weight(1f))
            if (value != null) Text(value, color = c.muted, fontSize = 13.sp)
            if (onClear != null) {
                Icon(
                    Icons.Filled.Close,
                    stringResource(R.string.remove),
                    tint = c.muted,
                    modifier = Modifier.padding(start = 6.dp).size(28.dp).clip(CircleShape).clickable(onClick = onClear).padding(6.dp),
                )
            }
        }
        HorizontalDivider(color = c.line)
    }
}

/** Mockup "Reminder: quick picks". */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderQuickSheet(onPick: (Instant) -> Unit, onCustom: () -> Unit, onDismiss: () -> Unit) {
    val c = colors
    val texts = rememberTexts()
    val now = remember { nowVn() }
    ModalBottomSheet(onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = c.surface) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            Text(stringResource(R.string.remind_me), color = c.fg, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, modifier = Modifier.padding(bottom = 6.dp))
            laterToday(now)?.let { at -> QuickOption(Icons.Outlined.Schedule, stringResource(R.string.later_today), texts.time(at.toEpochMilli())) { onPick(at) } }
            tomorrowMorning(now).let { at ->
                QuickOption(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.tomorrow_opt), "${texts.shortDate(vnDate(at.toEpochMilli())).substringBefore(',')}, ${texts.time(at.toEpochMilli())}") { onPick(at) }
            }
            nextWeekMorning(now).let { at ->
                QuickOption(Icons.Outlined.CalendarMonth, stringResource(R.string.next_week), "${texts.shortDate(vnDate(at.toEpochMilli())).substringBefore(',')}, ${texts.time(at.toEpochMilli())}") { onPick(at) }
            }
            HorizontalDivider(color = c.line, modifier = Modifier.padding(vertical = 4.dp))
            QuickOption(Icons.Filled.Add, stringResource(R.string.pick_date_time), null, highlight = true, onClick = onCustom)
        }
    }
}

@Composable
private fun QuickOption(icon: ImageVector, label: String, value: String?, highlight: Boolean = false, onClick: () -> Unit) {
    val c = colors
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = if (highlight) c.primary else c.muted, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
        Text(label, color = if (highlight) c.primary else c.fg, fontSize = 14.sp, modifier = Modifier.weight(1f))
        if (value != null) Text(value, color = c.muted, fontSize = 13.sp)
    }
}

/** Mockup "Reminder: date & time": month grid (past days off), hour chips, clock for any time. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ReminderPickerSheet(initial: Instant?, onSave: (Instant) -> Unit, onDismiss: () -> Unit) {
    val c = colors
    val texts = rememberTexts()
    val now = remember { nowVn() }
    val start = initial?.atZone(VN_ZONE)?.takeIf { it.isAfter(now) }
    var date by remember { mutableStateOf(start?.toLocalDate() ?: now.toLocalDate()) }
    var time by remember { mutableStateOf(start?.toLocalTime()?.withSecond(0)?.withNano(0) ?: LocalTime.of(9, 0)) }
    var month by remember { mutableStateOf(YearMonth.from(date)) }
    var clock by remember { mutableStateOf(false) }
    val weekdays = androidx.compose.ui.res.stringArrayResource(R.array.weekday_short)

    ModalBottomSheet(onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = c.surface) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.pick_reminder), color = c.fg, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(texts.monthTitle(month), color = c.fg, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.weight(1f))
                val canGoBack = month > YearMonth.from(now)
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(R.string.previous), tint = c.muted,
                    modifier = Modifier.size(36.dp).clip(CircleShape).clickable(enabled = canGoBack) { month = month.minusMonths(1) }.padding(6.dp).alpha(if (canGoBack) 1f else 0.3f),
                )
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.next), tint = c.muted,
                    modifier = Modifier.size(36.dp).clip(CircleShape).clickable { month = month.plusMonths(1) }.padding(6.dp),
                )
            }
            CalendarGrid(month, weekdays.toList(), selected = date, today = now.toLocalDate(), isEnabled = { isSelectableDay(it, now) }) { date = it }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                reminderChips(date, now).forEach { (hour, enabled) ->
                    Chip(hour.toString(), selected = hour == time, enabled = enabled) { time = hour }
                }
                val custom = time !in REMINDER_HOURS
                Chip(if (custom) time.toString() else null, selected = custom, enabled = true, icon = Icons.Outlined.Schedule) { clock = true }
            }
            val valid = isAllowedReminder(date, time, now)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PillButton(stringResource(R.string.cancel), filled = false, modifier = Modifier.weight(1f), onClick = onDismiss)
                PillButton(stringResource(R.string.save), filled = true, enabled = valid, modifier = Modifier.weight(1f)) { onSave(vnInstant(date, time)) }
            }
        }
    }
    if (clock) {
        val state = rememberTimePickerState(time.hour, time.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { clock = false },
            confirmButton = { TextButton({ time = LocalTime.of(state.hour, state.minute); clock = false }) { Text(stringResource(R.string.save)) } },
            dismissButton = { TextButton({ clock = false }) { Text(stringResource(R.string.cancel)) } },
            title = { Text(stringResource(R.string.pick_time)) },
            text = { TimePicker(state) },
        )
    }
}

@Composable
fun CalendarGrid(
    month: YearMonth,
    weekdays: List<String>,
    selected: LocalDate?,
    today: LocalDate,
    isEnabled: (LocalDate) -> Boolean,
    onPick: (LocalDate) -> Unit,
) {
    val c = colors
    val lead = month.atDay(1).dayOfWeek.value - 1 // Monday first
    val cells = List(lead) { null } + (1..month.lengthOfMonth()).map { month.atDay(it) }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row { weekdays.forEach { Text(it, color = c.muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, modifier = Modifier.weight(1f)) } }
        cells.chunked(7).forEach { week ->
            Row {
                (0 until 7).forEach { i ->
                    val day = week.getOrNull(i)
                    Box(Modifier.weight(1f).aspectRatio(1.25f), contentAlignment = Alignment.Center) {
                        if (day != null) {
                            val enabled = isEnabled(day)
                            val isSelected = day == selected
                            Box(
                                Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .then(if (isSelected) Modifier.background(c.primary) else Modifier)
                                    .clickable(enabled = enabled) { onPick(day) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    day.dayOfMonth.toString(),
                                    color = when {
                                        isSelected -> c.onPrimary
                                        day == today -> c.primary
                                        else -> c.fg
                                    },
                                    fontWeight = if (day == today) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 13.sp,
                                    modifier = Modifier.alpha(if (enabled) 1f else 0.35f),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun Chip(text: String?, selected: Boolean, enabled: Boolean, icon: ImageVector? = null, onClick: () -> Unit) {
    val c = colors
    Row(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .then(if (selected) Modifier.background(c.primary) else Modifier.border(1.5.dp, c.line, RoundedCornerShape(8.dp)))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp)
            .alpha(if (enabled) 1f else 0.4f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) Icon(icon, null, tint = if (selected) c.onPrimary else c.fg, modifier = Modifier.size(16.dp))
        if (icon != null && text != null) Spacer(Modifier.width(4.dp))
        if (text != null) Text(text, color = if (selected) c.onPrimary else c.fg, fontSize = 13.sp)
    }
}

@Composable
fun PillButton(text: String, filled: Boolean, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    val c = colors
    Box(
        modifier
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(if (filled) c.primary else c.surface2)
            .clickable(enabled = enabled, onClick = onClick)
            .alpha(if (enabled) 1f else 0.45f),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (filled) c.onPrimary else c.fg, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DueDateDialog(initial: LocalDate, onPick: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    // DatePicker works in UTC midnight millis.
    val state = rememberDatePickerState(initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton({
                state.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) } ?: onDismiss()
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onDismiss) { Text(stringResource(R.string.cancel)) } },
    ) {
        DatePicker(state, title = { Text(stringResource(R.string.pick_due), Modifier.padding(start = 24.dp, top = 16.dp)) })
    }
}

package com.sitpro.notevoice.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sitpro.notevoice.R
import com.sitpro.notevoice.data.CachedTask
import com.sitpro.notevoice.data.PendingUpload
import java.time.LocalDate

/** State a list screen needs; built in the shell from the view model's flows. */
data class ListChrome(
    val refreshing: Boolean,
    val online: Boolean,
    val updatedAt: Long?,
    val pending: List<PendingUpload>,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskListScreen(
    view: TaskView,
    tasks: List<CachedTask>,
    today: LocalDate,
    highlighted: Set<String>,
    chrome: ListChrome,
    onRefresh: () -> Unit,
    onOpen: (CachedTask) -> Unit,
    onToggleComplete: (CachedTask) -> Unit,
    onToggleStar: (CachedTask) -> Unit,
    modifier: Modifier = Modifier,
) {
    val texts = rememberTexts()
    val content = view.content(tasks, today)
    var showCompleted by rememberSaveable(view) { mutableStateOf(false) }

    PullToRefreshBox(chrome.refreshing, onRefresh, modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 10.dp, end = 10.dp, top = 2.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            listChrome(chrome, texts)

            val row: @Composable (CachedTask) -> Unit = { task ->
                TaskRow(task, view, today, task.id in highlighted, texts, onOpen, onToggleComplete, onToggleStar)
            }
            if (view == TaskView.PLANNED) {
                plannedGroups(content.open, today).forEach { (group, rows) ->
                    item(key = "g-$group") { GroupHeader(group, rows.size) }
                    items(rows, key = { it.id }) { row(it) }
                }
            } else {
                items(content.open, key = { it.id }) { row(it) }
            }

            if (content.completed.isNotEmpty()) {
                item(key = "completed") {
                    Row(
                        Modifier.clip(RoundedCornerShape(8.dp)).clickable { showCompleted = !showCompleted }.padding(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(if (showCompleted) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null, Modifier.size(18.dp), tint = colors.fg)
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.completed), color = colors.fg, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Spacer(Modifier.width(6.dp))
                        Text(content.completed.size.toString(), color = colors.muted, fontSize = 13.sp)
                    }
                }
                if (showCompleted) items(content.completed, key = { it.id }) { row(it) }
            }

            if (content.open.isEmpty() && content.completed.isEmpty() && chrome.pending.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        title = stringResource(if (view == TaskView.MY_DAY) R.string.empty_my_day_title else R.string.empty_list_title),
                        body = stringResource(R.string.empty_body),
                        samples = listOf(stringResource(R.string.sample_1), stringResource(R.string.sample_2)),
                    )
                }
            }
        }
    }
}

/** Offline banner and the "Processing…" / "saved, will send" rows shared by all lists. */
fun LazyListScope.listChrome(chrome: ListChrome, texts: Texts) {
    if (!chrome.online && chrome.updatedAt != null) {
        item(key = "offline") {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(colors.surface2).padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.CloudOff, null, Modifier.size(16.dp), tint = colors.fg)
                Spacer(Modifier.width(8.dp))
                Text(texts.updatedAt(chrome.updatedAt), color = colors.fg, fontSize = 13.sp)
            }
        }
    }
    val waiting = chrome.pending.filterNot { it.blocked }
    if (waiting.isEmpty()) return
    if (!chrome.online) {
        item(key = "queued") {
            PendingCard {
                Icon(Icons.Outlined.Sync, null, Modifier.size(18.dp), tint = colors.muted)
                Spacer(Modifier.width(10.dp))
                Text(texts.queued(waiting.size), color = colors.muted, fontSize = 13.sp)
            }
        }
    } else {
        items(waiting, key = { "p-" + it.entryId }) {
            PendingCard {
                CircularProgressIndicator(Modifier.size(18.dp), color = colors.primary, trackColor = colors.line, strokeWidth = 2.5.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.processing), color = colors.fg, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                    SkeletonBar(0.7f)
                }
            }
        }
    }
}

@Composable
private fun PendingCard(content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surface)
            .border(1.5.dp, colors.line, RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

@Composable
private fun GroupHeader(group: PlannedGroup, count: Int) {
    val label = stringResource(
        when (group) {
            PlannedGroup.OVERDUE -> R.string.group_overdue
            PlannedGroup.TODAY -> R.string.group_today
            PlannedGroup.TOMORROW -> R.string.group_tomorrow
            PlannedGroup.THIS_WEEK -> R.string.group_this_week
            PlannedGroup.LATER -> R.string.group_later
        }
    )
    Row(Modifier.padding(start = 4.dp, top = 8.dp, bottom = 2.dp)) {
        Text(label, color = if (group == PlannedGroup.OVERDUE) colors.danger else colors.primary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        Spacer(Modifier.width(6.dp))
        Text(count.toString(), color = colors.muted, fontSize = 13.sp)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TaskRow(
    task: CachedTask,
    view: TaskView,
    today: LocalDate,
    isNew: Boolean,
    texts: Texts,
    onOpen: (CachedTask) -> Unit,
    onToggleComplete: (CachedTask) -> Unit,
    onToggleStar: (CachedTask) -> Unit,
) {
    val c = colors
    val done = !task.isOpen
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isNew) c.new else c.surface)
            .clickable { onOpen(task) }
            .padding(start = 10.dp, top = 8.dp, bottom = 8.dp, end = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CheckCircle(done, { onToggleComplete(task) })
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f).padding(vertical = 2.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                task.title,
                color = if (done) c.muted else c.fg,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                textDecoration = if (done) TextDecoration.LineThrough else null,
            )
            val showMyDay = task.myDay && view != TaskView.MY_DAY && !done
            val due = task.dueDate?.let(LocalDate::parse)
            if (showMyDay || due != null || task.remindAtMillis != null || task.note != null) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (showMyDay) MetaItem(Icons.Outlined.WbSunny, stringResource(R.string.view_my_day), c.muted)
                    if (due != null) {
                        val color = when {
                            isOverdue(task, today) -> c.danger
                            due == today && !done -> c.primary
                            else -> c.muted
                        }
                        MetaItem(Icons.Outlined.CalendarMonth, texts.dueLabel(due, today), color)
                    }
                    task.remindAtMillis?.let { MetaItem(Icons.Outlined.Notifications, texts.time(it), c.muted) }
                    if (task.note != null) MetaItem(Icons.Outlined.Description, null, c.muted)
                }
            }
        }
        StarButton(task.starred, { onToggleStar(task) })
    }
}

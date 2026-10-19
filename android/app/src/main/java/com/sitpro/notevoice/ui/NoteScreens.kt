package com.sitpro.notevoice.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sitpro.notevoice.R
import com.sitpro.notevoice.data.CachedNote
import java.time.LocalDate

val NOTE_LABELS = listOf("idea", "quote", "note")

private data class LabelStyle(val icon: ImageVector, val bg: Color, val fg: Color)

@Composable
private fun labelStyle(label: String): LabelStyle {
    val c = colors
    return when (label) {
        "idea" -> LabelStyle(Icons.Outlined.Lightbulb, c.ideaBg, c.ideaFg)
        "quote" -> LabelStyle(Icons.Filled.FormatQuote, c.quoteBg, c.quoteFg)
        else -> LabelStyle(Icons.Outlined.Description, c.plainBg, c.plainFg)
    }
}

/** Label pill: Ý tưởng orange, Câu hay blue, Ghi chú grey (SPEC). `off` = an unselected choice. */
@Composable
fun LabelPill(label: String, off: Boolean = false, big: Boolean = false, onClick: (() -> Unit)? = null) {
    val texts = rememberTexts()
    val s = labelStyle(label)
    val c = colors
    Row(
        Modifier
            .clip(CircleShape)
            .then(if (off) Modifier.border(1.5.dp, c.line, CircleShape) else Modifier.background(s.bg))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = if (big) 11.dp else 8.dp, vertical = if (big) 6.dp else 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(s.icon, null, tint = if (off) c.muted else s.fg, modifier = Modifier.size(if (big) 16.dp else 13.dp))
        Spacer(Modifier.width(4.dp))
        Text(texts.label(label), color = if (off) c.muted else s.fg, fontWeight = FontWeight.SemiBold, fontSize = if (big) 13.sp else 11.5.sp)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NoteListScreen(
    notes: List<CachedNote>,
    filter: String?,
    today: LocalDate,
    highlighted: Set<String>,
    chrome: ListChrome,
    onFilter: (String?) -> Unit,
    onRefresh: () -> Unit,
    onOpen: (CachedNote) -> Unit,
    onToggleStar: (CachedNote) -> Unit,
    modifier: Modifier = Modifier,
) {
    val texts = rememberTexts()
    val shown = notes.filter { filter == null || it.label == filter }
    Column(modifier.fillMaxSize()) {
        FlowRow(Modifier.padding(horizontal = 12.dp).padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(stringResource(R.string.filter_all), filter == null) { onFilter(null) }
            NOTE_LABELS.forEach { label -> FilterChip(texts.label(label), filter == label) { onFilter(label) } }
        }
        PullToRefreshBox(chrome.refreshing, onRefresh, Modifier.weight(1f)) {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 10.dp, end = 10.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                listChrome(chrome, texts)
                items(shown, key = { it.id }) { NoteCard(it, today, it.id in highlighted, texts, onOpen, onToggleStar) }
                if (shown.isEmpty() && chrome.pending.isEmpty()) {
                    item(key = "empty") {
                        EmptyState(stringResource(R.string.empty_notes_title), stringResource(R.string.empty_notes_body), emptyList())
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val c = colors
    Text(
        text,
        color = if (selected) c.onPrimary else c.fg,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .clip(CircleShape)
            .then(if (selected) Modifier.background(c.primary) else Modifier.border(1.5.dp, c.line, CircleShape))
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 6.dp),
    )
}

@Composable
private fun NoteCard(
    note: CachedNote,
    today: LocalDate,
    isNew: Boolean,
    texts: Texts,
    onOpen: (CachedNote) -> Unit,
    onToggleStar: (CachedNote) -> Unit,
) {
    val c = colors
    val quote = note.label == "quote"
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isNew) c.new else c.surface)
            .clickable { onOpen(note) }
            .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LabelPill(note.label)
            Spacer(Modifier.width(8.dp))
            Text(note.createdAtMillis?.let { texts.whenSaved(it, today) }.orEmpty(), color = c.muted, fontSize = 11.5.sp, modifier = Modifier.weight(1f))
            StarButton(note.starred, { onToggleStar(note) }, size = 20)
        }
        Text(
            if (quote) "“${note.text.trimEnd('.')}”" else note.text,
            color = c.fg,
            fontSize = if (quote) 15.sp else 14.sp,
            fontWeight = if (quote) FontWeight.Medium else FontWeight.Normal,
            lineHeight = if (quote) 21.sp else 20.sp,
            maxLines = 6,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(end = 8.dp),
        )
        note.author?.let { Text("— $it", color = c.muted, fontSize = 12.5.sp) }
    }
}

class NoteActions(
    val toggleStar: (CachedNote) -> Unit,
    val setLabel: (CachedNote, String) -> Unit,
    val saveText: (CachedNote, String, String) -> Unit,
    val delete: (CachedNote) -> Unit,
)

/** Mockup "Note details": label, words, author for quotes, star, delete. Text is saved when it closes. */
@Composable
fun NoteDetails(note: CachedNote, today: LocalDate, actions: NoteActions, modifier: Modifier = Modifier) {
    val c = colors
    val texts = rememberTexts()
    var text by rememberSaveable(note.id) { mutableStateOf(note.text) }
    var author by rememberSaveable(note.id) { mutableStateOf(note.author.orEmpty()) }
    val latestNote by rememberUpdatedState(note)
    val latestText by rememberUpdatedState(text)
    val latestAuthor by rememberUpdatedState(author)
    DisposableEffect(note.id) {
        onDispose { actions.saveText(latestNote, latestText, latestAuthor) }
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            NOTE_LABELS.forEach { label -> LabelPill(label, off = label != note.label, big = true) { actions.setLabel(note, label) } }
            Spacer(Modifier.weight(1f))
            StarButton(note.starred, { actions.toggleStar(note) })
        }
        Box(
            Modifier.fillMaxWidth().heightIn(min = 96.dp).clip(RoundedCornerShape(10.dp)).background(c.surface2).padding(12.dp)
        ) {
            if (text.isEmpty()) Text(stringResource(R.string.note_text_hint), color = c.muted, fontSize = 15.sp)
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                textStyle = TextStyle(color = c.fg, fontSize = 15.sp, lineHeight = 22.sp),
                cursorBrush = SolidColor(c.primary),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (note.label == "quote" || author.isNotEmpty()) {
            Column {
                Row(Modifier.padding(vertical = 10.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Person, null, tint = c.primary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.author), color = c.primary, fontSize = 14.sp)
                    Spacer(Modifier.width(12.dp))
                    Box(Modifier.weight(1f)) {
                        if (author.isEmpty()) Text(stringResource(R.string.author_hint), color = c.muted, fontSize = 14.sp)
                        BasicTextField(
                            value = author,
                            onValueChange = { author = it },
                            singleLine = true,
                            textStyle = TextStyle(color = c.fg, fontSize = 14.sp),
                            cursorBrush = SolidColor(c.primary),
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                HorizontalDivider(color = c.line)
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            val created = note.createdAtMillis?.let { texts.whenSaved(it, today) }
            Text(
                when {
                    created == null -> ""
                    note.voiceEntryId != null -> stringResource(R.string.created_voice, created)
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
                modifier = Modifier.size(40.dp).clip(CircleShape).clickable { actions.delete(note) }.padding(8.dp),
            )
        }
    }
}

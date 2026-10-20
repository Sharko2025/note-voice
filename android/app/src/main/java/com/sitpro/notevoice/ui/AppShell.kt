package com.sitpro.notevoice.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sitpro.notevoice.R
import kotlinx.coroutines.launch

/** Below this width (cover screen) the menu is a drawer and details are a bottom sheet. */
private const val WIDE_DP = 600

@Composable
private fun Screen.title(): String = stringResource(
    when (this) {
        Screen.MY_DAY -> R.string.view_my_day
        Screen.IMPORTANT -> R.string.view_important
        Screen.PLANNED -> R.string.view_planned
        Screen.ALL -> R.string.view_all
        Screen.NOTES -> R.string.view_notes
        Screen.SETTINGS -> R.string.settings
    }
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppShell(vm: MainViewModel, settingsVm: SettingsViewModel, recording: RecordController, onMic: () -> Unit) {
    val texts = rememberTexts()
    val screen by vm.screen.collectAsState()
    val tasks by vm.tasks.collectAsState()
    val notes by vm.notes.collectAsState()
    val today by vm.today.collectAsState()
    val pending by vm.pending.collectAsState()
    val online by vm.online.collectAsState()
    val updatedAt by vm.updatedAt.collectAsState()
    val refreshing by vm.refreshing.collectAsState()
    val highlighted by vm.highlighted.collectAsState()
    val noteFilter by vm.noteFilter.collectAsState()
    val selectedTaskId by vm.selectedTaskId.collectAsState()
    val selectedNoteId by vm.selectedNoteId.collectAsState()
    val wide = LocalConfiguration.current.screenWidthDp >= WIDE_DP

    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(texts) {
        vm.messages.collect { msg ->
            val (visuals, action) = when (msg) {
                is UiMessage.Text -> AppSnack(if (msg.arg != null) texts.get(msg.res, msg.arg) else texts.get(msg.res)) to {}
                is UiMessage.TaskDeleted ->
                    AppSnack(texts.get(R.string.deleted_task, msg.row.title), actionLabel = texts.get(R.string.undo)) to { vm.restoreTask(msg.row) }
                is UiMessage.NoteDeleted ->
                    AppSnack(texts.get(R.string.deleted_note), actionLabel = texts.get(R.string.undo)) to { vm.restoreNote(msg.row) }
                is UiMessage.VoiceAdded -> AppSnack(
                    texts.resultTitle(msg.tasks, msg.notes),
                    detail = msg.rawText?.let(texts::heard),
                    actionLabel = texts.get(R.string.undo),
                ) to { vm.undoVoice(msg.entryId) }
                UiMessage.NothingHeard -> AppSnack(
                    texts.get(R.string.nothing_heard),
                    detail = texts.get(R.string.nothing_heard_body),
                    actionLabel = texts.get(R.string.say_again),
                ) to onMic
                is UiMessage.NotUnderstood -> AppSnack(
                    texts.get(R.string.not_understood),
                    detail = msg.rawText?.let { texts.get(R.string.heard_label, it) },
                    actionLabel = texts.get(R.string.say_again),
                ) to onMic
            }
            if (snackbar.showSnackbar(visuals) == SnackbarResult.ActionPerformed) action()
        }
    }

    val chrome = ListChrome(refreshing, online, updatedAt, pending)
    val counts = openCounts(tasks, today)
    val selectedTask = tasks.firstOrNull { it.id == selectedTaskId }
    val selectedNote = notes.firstOrNull { it.id == selectedNoteId }
    val taskActions = remember(vm) {
        TaskActions(vm::toggleComplete, vm::toggleStar, vm::setMyDay, vm::setReminder, vm::setDueDate, vm::saveTaskText, vm::deleteTask)
    }
    val noteActions = remember(vm) { NoteActions(vm::toggleNoteStar, vm::setNoteLabel, vm::saveNoteText, vm::deleteNote) }

    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    // Back closes the menu first, then leaves Settings / other lists for My Day, then exits.
    BackHandler(enabled = drawer.isOpen) { scope.launch { drawer.close() } }
    BackHandler(enabled = !drawer.isOpen && screen != Screen.MY_DAY) { vm.open(Screen.MY_DAY) }
    val nav: @Composable ColumnScope.() -> Unit = {
        NavItems(screen, counts, notes.size, pending.count { !it.blocked }, texts) { target ->
            vm.open(target)
            scope.launch { drawer.close() }
        }
    }

    val main: @Composable (Modifier) -> Unit = { mod ->
        MainColumn(
            vm, screen, wide, chrome, today, highlighted, tasks, notes, noteFilter, settingsVm, snackbar,
            onMenu = { scope.launch { drawer.open() } },
            onMic = onMic,
            modifier = mod,
        )
    }

    if (wide) {
        // Fold 8 unfolded: fixed menu | list | details (mockup "Unfolded").
        Row(Modifier.fillMaxSize().background(colors.bg)) {
            Column(Modifier.width(240.dp).fillMaxHeight().background(colors.surface).statusBarsPadding().padding(8.dp)) {
                Row(Modifier.padding(6.dp, 6.dp, 6.dp, 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    LogoBadge(30)
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.app_name), color = colors.fg, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
                nav()
            }
            VerticalDivider(color = colors.line)
            main(Modifier.weight(1f))
            if (selectedTask != null || selectedNote != null) {
                VerticalDivider(color = colors.line)
                Column(Modifier.width(300.dp).fillMaxHeight().background(colors.surface).statusBarsPadding().verticalScroll(rememberScrollState()).padding(14.dp)) {
                    if (selectedTask != null) TaskDetails(selectedTask, today, taskActions)
                    else if (selectedNote != null) NoteDetails(selectedNote, today, noteActions)
                }
            }
        }
    } else {
        ModalNavigationDrawer(
            drawerState = drawer,
            drawerContent = {
                ModalDrawerSheet(drawerContainerColor = colors.surface, modifier = Modifier.width(300.dp)) {
                    Column(Modifier.fillMaxHeight().padding(horizontal = 12.dp, vertical = 16.dp)) {
                        Row(Modifier.padding(start = 8.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                            LogoBadge()
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(stringResource(R.string.app_name), color = colors.fg, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Text(stringResource(R.string.app_tagline), color = colors.muted, fontSize = 12.sp)
                            }
                        }
                        nav()
                    }
                }
            },
        ) { main(Modifier.fillMaxSize()) }

        if (selectedTask != null) {
            ModalBottomSheet({ vm.selectedTaskId.value = null }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = colors.surface) {
                TaskDetails(selectedTask, today, taskActions, Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp).imePadding())
            }
        }
        if (selectedNote != null) {
            ModalBottomSheet({ vm.selectedNoteId.value = null }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = colors.surface) {
                NoteDetails(selectedNote, today, noteActions, Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp).imePadding())
            }
        }
    }

    if (recording.active) RecordSheet(recording, forNotes = screen == Screen.NOTES)
}

@Composable
private fun MainColumn(
    vm: MainViewModel,
    screen: Screen,
    wide: Boolean,
    chrome: ListChrome,
    today: java.time.LocalDate,
    highlighted: Set<String>,
    tasks: List<com.sitpro.notevoice.data.CachedTask>,
    notes: List<com.sitpro.notevoice.data.CachedNote>,
    noteFilter: String?,
    settingsVm: SettingsViewModel,
    snackbar: SnackbarHostState,
    onMenu: () -> Unit,
    onMic: () -> Unit,
    modifier: Modifier,
) {
    var draft by rememberSaveable(screen) { mutableStateOf("") }
    Scaffold(
        modifier = modifier,
        containerColor = colors.bg,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { TopBar(screen, today, showMenu = !wide, onMenu = onMenu, onBack = { vm.open(Screen.MY_DAY) }, onSettings = { vm.open(Screen.SETTINGS) }, onToggleNotes = vm::toggleNotes) },
        bottomBar = {
            if (screen != Screen.SETTINGS) {
                AddBar(
                    text = draft,
                    onText = { draft = it },
                    placeholder = stringResource(if (screen == Screen.NOTES) R.string.add_note else R.string.add_task),
                    onSubmit = {
                        val text = draft
                        if (screen == Screen.NOTES) vm.addNote(text) { draft = "" } else vm.addTask(text) { draft = "" }
                    },
                    onMic = onMic,
                    modifier = Modifier.navigationBarsPadding().imePadding(),
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbar) { AppSnackbar(it) } },
    ) { padding ->
        val inner = Modifier.padding(padding)
        when (screen) {
            Screen.SETTINGS -> SettingsScreen(settingsVm, onConnected = { vm.refresh() }, modifier = inner)
            Screen.NOTES -> NoteListScreen(
                notes, noteFilter, today, highlighted, chrome,
                onFilter = { vm.noteFilter.value = it },
                onRefresh = vm::refresh,
                onOpen = { vm.selectedTaskId.value = null; vm.selectedNoteId.value = it.id },
                onToggleStar = vm::toggleNoteStar,
                modifier = inner,
            )
            else -> TaskListScreen(
                screen.view!!, tasks, today, highlighted, chrome,
                onRefresh = vm::refresh,
                onOpen = { vm.selectedNoteId.value = null; vm.selectedTaskId.value = it.id },
                onToggleComplete = vm::toggleComplete,
                onToggleStar = vm::toggleStar,
                modifier = inner,
            )
        }
    }
}

@Composable
private fun TopBar(
    screen: Screen,
    today: java.time.LocalDate,
    showMenu: Boolean,
    onMenu: () -> Unit,
    onBack: () -> Unit,
    onSettings: () -> Unit,
    onToggleNotes: () -> Unit,
) {
    val c = colors
    val texts = rememberTexts()
    val context = LocalContext.current
    val lang = LocalConfiguration.current.locales[0].language
    var more by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().background(c.bg).statusBarsPadding().padding(horizontal = 8.dp).padding(top = 4.dp, bottom = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            when {
                screen == Screen.SETTINGS -> IconAction(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back), onBack)
                showMenu -> IconAction(Icons.Filled.Menu, stringResource(R.string.menu), onMenu)
            }
            Spacer(Modifier.weight(1f))
            // Tasks / Notes quick switch (Loi, 2026-10-20), same pill style as VI / EN; the open side is highlighted.
            val onNotes = screen == Screen.NOTES
            val onTasks = screen.view != null
            Text(
                buildAnnotatedString {
                    withStyle(androidx.compose.ui.text.SpanStyle(color = if (onTasks) c.primary else c.muted)) { append(stringResource(R.string.switch_tasks)) }
                    append(" · ")
                    withStyle(androidx.compose.ui.text.SpanStyle(color = if (onNotes) c.primary else c.muted)) { append(stringResource(R.string.switch_notes)) }
                },
                color = c.muted,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                modifier = Modifier
                    .clip(CircleShape)
                    .border(1.5.dp, c.line, CircleShape)
                    .clickable(onClickLabel = stringResource(R.string.switch_tasks_notes), onClick = onToggleNotes)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
            Spacer(Modifier.width(8.dp))
            // VI / EN switch (SPEC): Android restarts the screen with the new language.
            Text(
                buildAnnotatedString {
                    withStyle(androidx.compose.ui.text.SpanStyle(color = if (lang == "vi") c.primary else c.muted)) { append("VI") }
                    append(" · ")
                    withStyle(androidx.compose.ui.text.SpanStyle(color = if (lang == "en") c.primary else c.muted)) { append("EN") }
                },
                color = c.muted,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                modifier = Modifier
                    .clip(CircleShape)
                    .border(1.5.dp, c.line, CircleShape)
                    .clickable(onClickLabel = stringResource(R.string.language_switch)) { AppLanguage.set(context, AppLanguage.toggled(lang)) }
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
            if (screen != Screen.SETTINGS) {
                Box {
                    IconAction(Icons.Filled.MoreVert, stringResource(R.string.settings)) { more = true }
                    DropdownMenu(more, { more = false }, containerColor = c.surface) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.settings), color = c.fg) },
                            leadingIcon = { Icon(Icons.Outlined.Settings, null, tint = c.muted) },
                            onClick = { more = false; onSettings() },
                        )
                    }
                }
            } else {
                Spacer(Modifier.width(8.dp))
            }
        }
        Column(Modifier.padding(start = 6.dp, top = 2.dp)) {
            Text(screen.title(), color = c.fg, fontWeight = FontWeight.Bold, fontSize = 24.sp)
            if (screen == Screen.MY_DAY) Text(texts.myDaySubtitle(today), color = c.muted, fontSize = 13.sp)
        }
        if (screen == Screen.MY_DAY) WaveLine(Modifier.padding(horizontal = 6.dp, vertical = 4.dp))
    }
}

@Composable
private fun IconAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    Icon(icon, label, tint = colors.fg, modifier = Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onClick).padding(10.dp))
}

@Composable
private fun ColumnScope.NavItems(
    screen: Screen,
    counts: Map<TaskView, Int>,
    notesCount: Int,
    pendingCount: Int,
    texts: Texts,
    onOpen: (Screen) -> Unit,
) {
    val c = colors
    NavItem(Icons.Outlined.WbSunny, c.sun, Screen.MY_DAY.title(), counts[TaskView.MY_DAY], screen == Screen.MY_DAY) { onOpen(Screen.MY_DAY) }
    NavItem(Icons.Outlined.StarOutline, c.star, Screen.IMPORTANT.title(), counts[TaskView.IMPORTANT], screen == Screen.IMPORTANT) { onOpen(Screen.IMPORTANT) }
    NavItem(Icons.Outlined.CalendarMonth, c.danger, Screen.PLANNED.title(), counts[TaskView.PLANNED], screen == Screen.PLANNED) { onOpen(Screen.PLANNED) }
    NavItem(Icons.AutoMirrored.Outlined.FormatListBulleted, c.primary, Screen.ALL.title(), counts[TaskView.ALL], screen == Screen.ALL) { onOpen(Screen.ALL) }
    HorizontalDivider(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), color = c.line)
    NavItem(Icons.Outlined.Lightbulb, c.ideaFg, Screen.NOTES.title(), notesCount, screen == Screen.NOTES) { onOpen(Screen.NOTES) }
    HorizontalDivider(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), color = c.line)
    NavItem(Icons.Outlined.Settings, c.fg, Screen.SETTINGS.title(), null, screen == Screen.SETTINGS) { onOpen(Screen.SETTINGS) }
    if (pendingCount > 0) {
        Spacer(Modifier.weight(1f))
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.surface2).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Sync, null, tint = c.fg, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(texts.queued(pendingCount), color = c.fg, fontSize = 12.sp)
        }
    }
}

@Composable
private fun NavItem(icon: ImageVector, tint: Color, label: String, count: Int?, selected: Boolean, onClick: () -> Unit) {
    val c = colors
    Row(
        Modifier
            .fillMaxWidth()
            .height(46.dp)
            .clip(CircleShape)
            .then(if (selected) Modifier.background(c.surface2) else Modifier)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
        Text(label, color = if (selected) c.primary else c.fg, fontWeight = FontWeight.Medium, fontSize = 14.sp, modifier = Modifier.weight(1f))
        if (count != null) Text(count.toString(), color = c.muted, fontSize = 13.sp)
    }
}

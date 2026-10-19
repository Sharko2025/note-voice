# Reuse from expense-voice

Source repo: `../expense-voice` (C:\vibecoding\ClaudeAgent\expense-voice).
Path prefixes used below:
- Backend: `backend/app/...` (tests in `backend/tests/`)
- Android: `android/app/src/main/java/com/sitpro/expensevoice/...` (tests in `android/app/src/test/...`)
- Deploy: `deploy/...`

Workflow: brainstorm `SPEC.md` first, then `CLAUDE.md`, then `docs/plans/phase-1.md`, then copy and adapt files with TDD.


### Copy as-is (only rename the package or app name)
- **Backend:**
  - `app/auth.py` (`require_token`, `hmac.compare_digest`)
  - `app/config.py`, `app/db.py`, `app/timeutil.py` (keep `VN_TZ`, `today_vn`, `day_range`)
  - `routers/health.py`, `services/errors.py`
  - `Dockerfile`, `requirements.txt`, `pyproject.toml` (the `openai` marker), `alembic.ini`, `alembic/env.py`, `.dockerignore`
- **Android:**
  - `audio/Recorder.kt` and `audio/SilenceDetector.kt` drive in-app recording from a mic button (see "How the app opens" below)
  - `sync/Workers.kt` (WorkManager upload/undo queue)
  - `data/SettingsStore.kt` (encrypted token; change `DEFAULT_URL`)
  - `ui/AppLanguage.kt` (VI/EN)
  - Gradle wrapper, `libs.versions.toml`, root `build.gradle.kts`, `gradle.properties`
  - Tests: `SilenceDetectorTest`, `AppLanguageTest`, `StringsCompletenessTest`
- **Deploy:** `docker-compose.dev.yml`, `initdb/create-test-db.sh`
- **Docs:** `docs/fold8-setup.md` (wireless adb and device setup)

### Adapt (keep the structure, swap the expense parts)
- **`services/voice.py`:** keep `process_voice`, `_claim` (idempotent `entry_id`), `_replay`, `_transcribe` (audio is always deleted), `STALE_AFTER`. Change only the stage that stores rows.
- **`services/stt.py`:** replace `STT_PROMPT` (money words → task and date words). Keep the empty `OPENAI_STT_LANGUAGE` default (auto-detect VI/EN).
- **`services/parser.py`:**
  - Keep `OpenAIParser.parse` (strict json_schema, temperature 0).
  - Rewrite `SYSTEM_PROMPT` and `JSON_SCHEMA` → `title`, `due_date`, `remind_at`, `starred`.
  - Put the current VN date and time in the prompt so "mai" and "9 giờ sáng" resolve.
- **`models.py`:** keep `VoiceEntry` (with `stt_ms`/`parse_ms`/`total_ms`). Replace `Transaction` with a `Task` model: title, done, starred, due_date, remind_at, note, deleted_at, voice_entry_id.
- **`routers/voice.py`:** keep the upload contract. Swap the types.
- **`routers/transactions.py` → `tasks.py`:** keep the CRUD, soft delete, `/restore` and `/voice/{id}/undo` patterns. Add toggles for done and star, and filters for My Day, Important and Planned.
- **`main.py`, `schemas.py`, `alembic/versions/0001`:** reuse the `voice_entries` DDL. Rename things.
- **Backend tests:**
  - `conftest.py` (fixtures, `FakeParser`), `test_voice.py` (the idempotency, 409, 502 and stale cases), `test_auth.py`
  - `test_transactions.py` → `test_tasks.py`
- **Android:**
  - `AppContainer.kt`, `MainActivity.kt`
  - `api/ApiService.kt` and `Dto.kt` (keep `postVoice`, `undoVoice`, `AuthInterceptor`, `parseError`)
  - `data/AppDatabase.kt` (keep `PendingUpload` and `PendingDao`)
  - `sync/UploadProcessor.kt` (keep the HTTP code handling; swap the DTOs)
  - `notify/ResultNotifier.kt` (keep the result notification and Undo; add a reminder channel)
  - `ui/Texts.kt` (VI + EN tables), `ui/ViewModels.kt` (`SettingsViewModel`), `ui/ScreenLogic.kt` (`vnInstant`, the patch-diff idea)
  - `SettingsScreen` and the date picker from `Screens.kt`
  - Tests: `ApiServiceTest`, `UploadProcessorTest`, `DaoTest`
- **Manifest:** `MainActivity` is the only LAUNCHER (normal app). Do **not** copy expense-voice's `RecordActivity`-first launcher, `showWhenLocked`/`turnScreenOn`, or the separate "Record" icon. Keep the Undo receiver. Add `SCHEDULE_EXACT_ALARM` and `RECEIVE_BOOT_COMPLETED` for reminders.
- **Deploy:**
  - `docker-compose.yml`: name `note-voice`, port **`127.0.0.1:8001`** (8000 is already used), its own Postgres.
  - `backup.sh`: change the file name prefix.
  - nginx conf: new `server_name`, a **unique `limit_req_zone` name**, log paths, port 8001.

### Drop or rewrite (expense-specific)
- **Backend:** `services/normalize.py`, `routers/summary.py`, `routers/categories.py` (or turn it into task lists later)
- **Android:** `ui/AppTheme.kt` (dynamic colour → fixed navy/orange scheme from the logo), `ExpenseRepository.kt` (becomes `TaskRepository`), most of `Screens.kt`, `Format.kt` money helpers
- **Tests:** the expense tests (`test_summary`, `test_normalize`, `test_categories`, `test_parse_openai`, and `ResultTextTest`/`FormatTest`/`ScreenLogicTest` on Android)
- **Branding:** the "Ví Cá Mập"/"Sharko Wallet" strings, SPEC.md, CLAUDE.md (new logo: option C "note page", see SPEC)

### Names to rename everywhere
- App name **Note Cá Mập** / **Sharko Notes** (launcher label always Note Cá Mập); domain **`notecamap.s-itpro.com`** (`DEFAULT_URL`, nginx `server_name`, certbot)
- `com.sitpro.expensevoice` → e.g. `com.sitpro.notevoice` (package, namespace, applicationId, taskAffinity, Room schema folder)
- `ExpenseApp`, `Theme.ExpenseVoice`, `expense.db`, logger `expense_voice`, the FastAPI title
- The domain in `SettingsStore`, `.env.example` (`POSTGRES_DB=expense`, test DB, backup S3 URI), compose `name`

## New things expense-voice doesn't have
- **Local reminders:** AlarmManager and a BroadcastReceiver, rescheduled after boot. These run on the phone, with no server push.
- **How the app opens:** like a normal app (as Microsoft To Do does). The launcher icon opens the task list, and a mic button in the list starts recording, with the result shown in the list. There is no side key, no record-first launcher and no lock-screen recording. `capture/RecordActivity.kt` serves only as a reference for the record flow (entryId UUID, save `.m4a` to `pendingDir`, `submitRecording`); its overlay/lock-screen setup is not needed. A Quick Settings tile or widget (`capture/Triggers.kt`) is optional and can wait until after the MVP.
- **To Do views:** My Day, Important, Planned. Typed add and edit as well as voice.


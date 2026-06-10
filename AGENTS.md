# Project Context

## Overview
JCdocs — Android document editor app with formatted text editing, rich text spans, and Office-like ribbon UI.

## Key Files
- `app/src/main/java/com/example/ui/DocEditorScreen.kt` — Main ~7600-line file with all UI (ribbon, editor, metrics, bottom nav, dialogs, paste special)
- `app/src/main/java/com/example/ui/DocUndoRedoManager.kt` — Undo/redo snapshot stack
- `app/src/main/java/com/example/viewmodel/DocViewModel.kt` — ViewModel with draft content management
- `app/src/main/java/com/example/db/` — Room database (DocEntity, DocDatabase, DocDao, converters)
- `app/src/main/java/com/example/ai/` — AI chat (GeminiAiProvider, OpenRouterApi, AIChatPanel)
- `app/src/main/java/com/example/ui/theme/Color.kt` — Colors: DocWordColor (#E2574C), DocSheetColor (#4DA06F), DocSlideColor (#E08B3A), OnlyOfficePrimary (#DF4A32)
- `AGENTS.md` — This file (project context for AI)
- `logs/CHANGELOG.md` — Full changelog
- `logs/SESSION_LOG_*.md` — Per-session logs

## Bottom Navigation (Home Screen)
4 tabs: Home, Files, Shared, Settings — uses Material3 NavigationBar
- `activeTab` state var (line ~2680): "home", "files", "shared", "settings"
- Items defined at line ~5248

## Ribbon Tabs (Document Editor)
5 tabs at bottom of editor: Home, Insert, AI, Layout, Review
- `activeRibbonTab` state var (line ~2685): "Home", "Insert", "AI", "Layout", "Review"
- `isRibbonExpanded` state var (line ~2686)
- Active tab shows colored bottom indicator bar (3dp) + tinted background (via drawBehind)
- AI tab shows AIChatPanel composable (line ~2939-2944)
- Ribbon expanded panel above shows tool cards grouped by category (LazyColumn)
- Ribbon tabs defined at line ~4385
- bottomNavBarHeight = 68.dp (line ~2760)

## Paste Special
- Compact floating Card with two icon buttons: "Tt" (plain text) and "B" icon (source formatting)
- Source formatting re-applies CopiedFormattedData.spans at cursor via DocFormatRepository.applySpan
- Cursor position captured at click time via pendingPasteSelStart/End
- Data class: CopiedFormattedData (text, relative-offset spans, sourceOffset)
- DocFormatRepository: mutableMapOf<Int, SnapshotStateList<DocFormatSpan>>

## Undo/Redo
- captureSnapshot uses editorTextFieldValue.text (not lagging draftContent parameter)
- DocUndoRedoManager with pushState/undo/redo
- DocEditorSnapshot data class

## Build
- App ID: com.aistudio.jcdocs.ywtqka, package: com.example
- minSdk 24, targetSdk 36
- Build command: `.\gradlew.bat assembleDebug`
- Install: `adb install -r app\build\outputs\apk\debug\app-debug.apk`
- Device: G6IR4565CANRLVX4
- SDK: C:\Users\chand\AppData\Local\Android\Sdk

## GitHub
- Main: https://github.com/chandan191224-bit/jcdocsnewfor-antigravity.git (branch: main)
- Ref: https://github.com/chandan191224-bit/jcdocs-ref.git (branch: main)
- Working dir: C:\Users\chand\Downloads\jcdocs (5)
- Ref dir: C:\Users\chand\Downloads\jcdocs-ref
- Latest commit: 773aa93 (main), 40f85ae (ref)

## Session History (2026-06-10)
- Fixed undo/redo: captureSnapshot now uses editorTextFieldValue.text
- Removed shortcut hints from Editing tools, removed Lines/Read/Speak from Metrics
- Added Paste Special with compact icon toggle (plain text / source formatting)
- Paste inserts at cursor position, source formatting re-applies spans via DocFormatRepository
- Redesigned bottom nav with Material3 NavigationBar (Home/Files/Shared/Settings)
- Fixed ribbon tabs: professional icons, colored indicator bar, adaptive layout
- Renamed "AI Assistant" tab → "AI" and fixed AIChatPanel reference
- Created AGENTS.md for future session memory

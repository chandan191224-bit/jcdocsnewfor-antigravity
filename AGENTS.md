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

## Bottom Navigation (Home Screen)
4 tabs: Home, Files, Shared, Settings — uses Material3 NavigationBar

## Ribbon Tabs (Document Editor)
5 tabs at bottom of editor: Home, Insert, AI, Layout, Review
- Active tab shows colored bottom indicator bar + tinted background
- AI tab shows AIChatPanel composable
- Ribbon expanded panel above shows tool cards grouped by category

## Paste Special
- Compact floating Card with two icon buttons: "Tt" (plain text) and "B" icon (source formatting)
- Source formatting re-applies CopiedFormattedData.spans at cursor via DocFormatRepository.applySpan

## Undo/Redo
- captureSnapshot uses editorTextFieldValue.text (not lagging draftContent parameter)
- DocUndoRedoManager with pushState/undo/redo

## Build
- App ID: com.aistudio.jcdocs.ywtqka, package: com.example
- minSdk 24, targetSdk 36
- Build command: `.\gradlew.bat assembleDebug`
- Install: `adb install -r app\build\outputs\apk\debug\app-debug.apk`
- Device: G6IR4565CANRLVX4
- SDK: C:\Users\chand\AppData\Local\Android\Sdk

## GitHub
- Remote: https://github.com/chandan191224-bit/jcdocsnewfor-antigravity.git
- Branch: main

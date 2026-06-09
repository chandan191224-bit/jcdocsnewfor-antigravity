# JCdocs - Project Understanding

## Overview
**JCdocs** is a premium, real-time, reactive offline office suite for Android.
Built with Kotlin + Jetpack Compose, targeting API 24-36.

## Capabilities
- **Word documents** — rich text editing (bold, italic, underline, alignment, themes, margins, columns, page numbers)
- **Spreadsheets** — dynamic formula evaluation (`=SUM`, `=AVG`, arithmetic, cell references)
- **Slide presentations** — interactive slideshows with themes and full-screen present mode
- **AI conversational assistant** — chatbot powered by Gemini + OpenRouter with multi-provider support

## Architecture (MVVM)
```
MainActivity
  └─ DocEditorScreen (Composable)
       ├─ WorkspacePane (document editing surface)
       ├─ SidebarExplorer (file list, search, filter)
       ├─ AIChatPanel (AI assistant panel)
       └─ FullscreenPresentationView (slideshow mode)
  └─ DocViewModel (AndroidViewModel)
       ├─ DocRepository → DocDao → Room DB
       ├─ AiChatRepository → ChatDao → Room DB
       └─ AiProvider (interface)
            ├─ GeminiAiProvider
            ├─ OpenRouterAiProvider
            └─ CustomAiProvider
```

## Tech Stack
| Layer | Technology |
|-------|-----------|
| Language | Kotlin |
| UI | Jetpack Compose + Material 3 |
| Database | Room (SQLite) |
| Networking | Retrofit + Moshi + OkHttp |
| DI | None (manual) |
| Build | Gradle 9.x, AGP 9.1.1, KSP |
| Testing | JUnit, Robolectric, Roborazzi |

## Key File Locations
| File | Purpose |
|------|---------|
| `app/src/main/java/com/example/ui/MainActivity.kt` | Entry point |
| `app/src/main/java/com/example/ui/DocEditorScreen.kt` | Main UI (~2100+ lines) |
| `app/src/main/java/com/example/ui/AIChatPanel.kt` | AI chat panel |
| `app/src/main/java/com/example/viewmodel/DocViewModel.kt` | Central ViewModel |
| `app/src/main/java/com/example/db/DocEntity.kt` | Document Room entity |
| `app/src/main/java/com/example/db/DocDao.kt` | Document DAO |
| `app/src/main/java/com/example/db/DocDatabase.kt` | Room database |
| `app/src/main/java/com/example/db/DocRepository.kt` | Document repository |
| `app/src/main/java/com/example/db/ChatEntities.kt` | Chat entities |
| `app/src/main/java/com/example/db/ChatDao.kt` | Chat DAO |
| `app/src/main/java/com/example/db/AiChatRepository.kt` | AI chat repository |
| `app/src/main/java/com/example/ai/AiProvider.kt` | AI provider interface |
| `app/src/main/java/com/example/ai/GeminiAiProvider.kt` | Gemini integration |
| `app/src/main/java/com/example/ai/OpenRouterAiProvider.kt` | OpenRouter integration |
| `app/src/main/java/com/example/ai/OpenRouterApi.kt` | OpenRouter API DTOs |
| `app/src/main/java/com/example/ai/CustomAiProvider.kt` | Custom OpenAI-compatible provider |
| `app/src/main/java/com/example/ui/ui/theme/Color.kt` | Theme colors |
| `app/src/main/java/com/example/ui/ui/theme/Theme.kt` | Material 3 theme |
| `app/src/main/java/com/example/ui/ui/theme/Type.kt` | Typography |
| `app/src/main/java/com/example/ui/DocUndoRedoManager.kt` | Undo/redo manager |

## Database Schema
- **documents** table: id, title, type, content, createdAt, updatedAt, isFavorite
- **chat_conversations** table: conversation metadata
- **chat_messages** table: individual messages with foreign key to conversation
- **ai_provider_configs** table: AI provider configuration (name, API key, base URL, model)

## AI Providers
1. **Gemini** — via `generativelanguage.googleapis.com`, model: `gemini-3.5-flash`
2. **OpenRouter** — via `openrouter.ai/api`, with automatic fallback across 12+ free models
3. **Custom** — OpenAI-compatible API endpoint, user-configurable base URL, API key, model

## Build Configuration
- compileSdk: 36, minSdk: 24, targetSdk: 36
- Debug signing enabled
- Secrets plugin reads `.env` for API keys
- KSP for Room + Moshi codegen

# `:app` Source — Architecture Guide

Package root: `com.example.llamaapp`

---

## Layer Map

| Package | Role | Key Files |
|---------|------|-----------|
| `di/` | Composition root — all Hilt bindings | `AppModule.kt` |
| `navigation/` | Single `NavHost`, all routes | `AppNavigation.kt` |
| `ui/chat/` | Chat screen + ViewModel | `ChatScreen.kt`, `ChatViewModel.kt` |
| `ui/models/` | Model picker screen + ViewModel | `ModelPickerScreen.kt`, `ModelPickerViewModel.kt` |
| `ui/settings/` | Settings screen + ViewModel | `SettingsScreen.kt`, `SettingsViewModel.kt` |
| `domain/usecase/` | Business logic — one class per action | `SendMessageUseCase.kt`, `LoadModelUseCase.kt`, … |
| `data/repository/` | Repository impls — Room + DataStore | `ChatRepositoryImpl.kt`, `SettingsRepositoryImpl.kt` |
| `data/db/` | Room DB, DAOs, entities | `AppDatabase.kt`, `ChatDao.kt`, `Entities.kt` |
| `engine/` | **Qwen3-only** prompt formatting + think-tag parsing | `Qwen3ChatTemplate.kt`, `ThinkTagParser.kt` |
| `storage/` | Model file discovery on external storage | `ModelStorageManager.kt` |
| `service/` | Foreground inference service | `InferenceService.kt` |
| `worker/` | WorkManager model download task | `ModelDownloadWorker.kt` |

---

## Data Flow (inference path)

```
ChatScreen
  → ChatViewModel.sendMessage()
    → SendMessageUseCase
      → Qwen3ChatTemplate.format()   // engine/ — prompt building
      → LlamaEngine.generate()       // :lib JNI bridge
        → Flow<GenerationEvent>
      → ThinkTagParser                // engine/ — mid-token <think> split handling
    → ChatRepository.saveMessage()   // data/repository/
```

---

## Adding Features — Recipe

1. **UseCase** in `domain/usecase/` — one class, one `operator fun invoke()`
2. **Bind** in `AppModule.kt` — `@Binds` or `@Provides`
3. **Inject** into ViewModel — not into screens directly
4. **Screen** in `ui/<feature>/` — Compose only, no business logic
5. **Route** in `AppNavigation.kt` — add `composable("route") { … }`

---

## High-Fanout Files — Touch Carefully

- **`LlamaEngine.kt`** (`:lib`): imported in 13+ sites. Its sealed types (`GenerationEvent`, `ModelLoadState`, `InferenceStats`, `ModelConfig`) propagate everywhere — interface changes break broadly.
- **`ChatViewModel.kt`**: 6 injected dependencies, consumed by 3 screens. It **directly injects `LlamaEngine`** in addition to use cases — this is a known arch violation; don't extend the pattern.
- **`AppModule.kt`**: composition root — incorrect bindings here cause runtime crashes, not compile errors.

---

## `engine/` Package

`Qwen3ChatTemplate.kt` — builds the `<|im_start|>` / `<|im_end|>` chat markup required by Qwen3 instruct models.
`ThinkTagParser.kt` — handles `<think>`/`</think>` tags that may arrive **split across token boundaries** in the streaming flow.

**Do not use `engine/` for non-Qwen3 models.** Changing model families means replacing this entire package.
**Do not duplicate `<think>` tag detection** — `ThinkTagParser` is the single source of truth in `:app`; `:lib` handles it at the C++ level independently.

---

## Anti-Patterns

- **Don't call `LlamaEngine` outside use cases** — `ChatViewModel` already does this; that's technical debt, not a pattern to follow.
- **Don't add Room schema changes without a migration** — DB uses `fallbackToDestructiveMigration()`; all chat history is wiped on schema change.
- **Don't add `@Singleton` bindings outside `AppModule.kt`** — Hilt scope bugs are silent at compile time.
- **Don't put business logic in Compose screens** — screens are pure rendering; logic belongs in ViewModels or use cases.

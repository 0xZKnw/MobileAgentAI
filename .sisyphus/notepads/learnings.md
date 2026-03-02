
## T16: Domain Use Cases

- `ModelRepository.updateLastUsed(id: Long)` — takes `id`, not `path`. Must call `getModelByPath()` first to resolve the id.
- `LlamaEngine.generate()` signature: `(conversationHistory: List<Pair<String,String>>, userMessage: String, config: ModelConfig): Flow<GenerationEvent>`
- `zipWithNext` used in `SendMessageUseCase` to build `(user, assistant)` pairs from flat message list — filter non-system first, then pair consecutive user+assistant.
- Use cases: no `@Module` needed, plain constructor injection with `@Inject constructor`.
- `SendMessageUseCase` deliberately has NO `saveMessage` call — ViewModel responsibility after stream completes.

## ModelPickerViewModel (2026-03-02)
- `ModelDownloadWorker.KEY_PROGRESS = "download_progress"` — this is the **data key** for reading progress from WorkInfo, NOT the work tag
- Work tag for observing downloads is the literal string `"model_download"`
- Worker progress is stored as Int (0–100); divide by 100f for Float UI state
- `LlamaEngine.loadState` is `StateFlow<ModelLoadState>`; `Loaded` carries `modelName: String` (not path)
- `engine.getModelName()` returns the currently loaded model name
- `getWorkInfosByTagFlow(tag)` is the coroutines-friendly WorkManager API (no LiveData needed)
- `ImportModelUseCase.invoke` returns `Result<GgufModel>`; progress callback is `(Float) -> Unit`
- Use `viewModelScope.launch(Dispatchers.IO)` for all suspend calls in ViewModel
- `@ApplicationContext` qualifier needed when injecting Context into `@HiltViewModel`

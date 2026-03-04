# `:lib` — JNI Bridge Module

Package: `com.example.llama`

This module has **one job**: wrap `llama.cpp` C++ calls into a Kotlin coroutine API. It is not a general-purpose library.

---

## File Map

```
lib/src/main/
├── java/com/example/llama/
│   ├── LlamaEngine.kt              ← public API + all sealed types
│   └── internal/
│       └── LlamaEngineImpl.kt      ← implementation; calls JNI; internal visibility
└── cpp/
    ├── CMakeLists.txt              ← builds llama.cpp submodule + ai_chat.cpp → llama_jni.so
    └── ai_chat.cpp                 ← all JNI function implementations (228 lines)
```

---

## Public API (`LlamaEngine.kt`)

All sealed types the rest of the app depends on live here:

| Type | Purpose |
|------|---------|
| `ModelLoadState` | `Loading`, `Loaded`, `Error` |
| `GenerationEvent` | `Token(text)`, `ThinkingToken(text)`, `Done`, `Error` |
| `InferenceStats` | Tokens/sec, prompt eval time, etc. |
| `ModelConfig` | Context size, thread count, batch size |

Interface changes here break `:app` broadly — 13+ import sites.

---

## Threading — Non-Negotiable

```kotlin
private val llamaDispatcher = Dispatchers.IO.limitedParallelism(1)
```

**All** `llama.cpp` calls must run on `llamaDispatcher`. The native library is single-threaded by design.
Never dispatch llama calls to `Dispatchers.Default` or an unbounded pool — it will crash or corrupt state.

---

## Generation Pattern

Generation uses a **polling loop**, not callbacks:

```kotlin
while (true) {
    val token = nativeNextToken(contextPtr) ?: break
    emit(GenerationEvent.Token(token))
}
```

`nativeNextToken()` returns `null` when the model signals EOS. Do not refactor to callbacks — the C++ side has no callback mechanism.

---

## Think Token Detection in C++

`ai_chat.cpp` auto-detects `<think>`/`</think>` token IDs from the loaded model's vocabulary at load time:

```cpp
// In nativeLoadModel, after model is loaded:
int n = llama_tokenize(vocab, "<think>", 7, tmp, 4, false, true);
g_state.thinkStartToken = (n == 1) ? tmp[0] : -1;  // -1 = not a single token, detection disabled
```

**Result**: works with any model family that registers `<think>`/`</think>` as single special tokens in its vocabulary.
Token IDs logged at load time: `Think tokens: start=N, end=N`.
If tokenization returns != 1 token (e.g. plain-text model), detection is disabled (set to -1) and no think routing occurs — the model still generates normally.

`LlamaEngineImpl.kt` handles think routing at the **string level** (`token == "<think>"`) independently — that layer already works for any model.

---

## Adding a JNI Function

1. Declare `external fun` in `LlamaEngineImpl.kt`
2. Implement in `ai_chat.cpp` with the exact JNI name:
   ```cpp
   extern "C" JNIEXPORT jtype JNICALL
   Java_com_example_llama_internal_LlamaEngineImpl_nativeYourFunction(
       JNIEnv* env, jobject thiz, /* params */)
   ```
3. Call only from `llamaDispatcher` coroutine context.

---

## Build

CMake builds `llama.cpp` as a static library and links it into `llama_jni.so`.
ABI targets: `arm64-v8a`, `x86_64`. C++ standard: C++17.
NDK r27c (`27.2.12479018`) required — version is exact.

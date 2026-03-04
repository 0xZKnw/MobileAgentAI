# MobileAgentAI — Project Agent Guide

## What This Is

Android app that runs GGUF language models **locally on-device** via a JNI bridge to `llama.cpp`.
Optimized for Samsung Galaxy S24 (arm64-v8a). Currently hard-wired for **Qwen3 models only** — see Gotchas.

---

## Module Map

| Module | Role |
|--------|------|
| `:app` | Full Android application (`com.example.llamaapp`), Compose UI, clean-arch layers |
| `:lib` | JNI bridge only (`com.example.llama`) — wraps `llama.cpp` C++ calls into Kotlin coroutine API |
| `llama.cpp/` | Git submodule — upstream `ggml-org/llama.cpp`. **Do not modify.** Has its own `AGENTS.md`. |
| `.sisyphus/` | AI agent tooling state (boulder, plans, evidence). **Not application code.** |

---

## Build

**Prerequisites**: JDK 17, Android SDK (compileSdk 35), NDK r27c (`27.2.12479018`)

```bash
# First time only — submodule must be initialized
git submodule update --init --recursive

# Create local.properties (if not present)
echo "ndk.dir=/path/to/ndk/27.2.12479018" >> local.properties

# Build debug APK
./gradlew :app:assembleDebug
```

APK lands at `app/build/outputs/apk/debug/app-debug.apk`.

ABI filters: `arm64-v8a`, `x86_64`. JVM target: 17. minSdk: 33.

---

## CI (`.github/workflows/build.yml`)

| Trigger | Output |
|---------|--------|
| Push to `main`, `master`, `dev`, `copilot/**` | Debug APK as build artifact |
| Tag matching `v*.*.*` | GitHub Release with APK attached |

CI writes `local.properties` automatically — do not commit `local.properties`.

---

## Tech Stack

- Kotlin 2.1.0, AGP 8.7.2
- Jetpack Compose (BOM 2025.02.00)
- Hilt 2.54 (KSP) — DI
- Room 2.7 — chat history DB (`"llama_db"`, destructive migration)
- DataStore — settings (`"settings"` file)
- WorkManager — model downloads
- NDK r27c, C++17

---

## Where to Look

| Goal | Path |
|------|------|
| UI screens / ViewModels | `app/.../llamaapp/ui/` |
| Business logic | `app/.../llamaapp/domain/usecase/` |
| DB entities / DAOs | `app/.../llamaapp/data/db/` |
| Model file management | `app/.../llamaapp/storage/ModelStorageManager.kt` |
| Qwen3 prompt formatting | `app/.../llamaapp/engine/` |
| JNI Kotlin API | `lib/.../llama/LlamaEngine.kt` |
| C++ inference bridge | `lib/src/main/cpp/ai_chat.cpp` |
| DI wiring | `app/.../llamaapp/di/AppModule.kt` |

Full app architecture → `app/src/main/java/com/example/llamaapp/AGENTS.md`
JNI bridge details → `lib/AGENTS.md`

---

## Tests

**None** in `:app` or `:lib`. Zero test infrastructure. `llama.cpp` submodule has its own test suites.

---

## Gotchas

**Qwen3-only C++ hardcoding**: `ai_chat.cpp` hardcodes token IDs `151667` (`<think>`) and `151668` (`</think>`). Using any non-Qwen3 model will silently produce incorrect thinking-state behavior. Changing model families requires editing `ai_chat.cpp`.

**Submodule required**: Build fails silently if `llama.cpp/` is not initialized. Always run `git submodule update --init --recursive` after cloning.

**NDK version is exact**: The CMake config targets NDK r27c (`27.2.12479018`). Other NDK versions may fail to compile.

**No migration strategy**: Room is configured with `fallbackToDestructiveMigration()` — schema changes wipe the chat DB.

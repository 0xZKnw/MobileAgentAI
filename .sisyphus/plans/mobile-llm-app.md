# Native Android LLM App — Samsung S24 Exynos / Qwen3

## TL;DR

> **Quick Summary**: Build a production-quality native Android (Kotlin + Jetpack Compose) app that runs GGUF models locally via llama.cpp NDK/JNI, optimized for maximum tokens/second on the Samsung Galaxy S24 (Exynos 2400), with full Markdown + `<think>` block rendering.
>
> **Deliverables**:
> - Complete Android Studio project at `/home/0xzknw/Desktop/MobileAgentAI/`
> - `:lib` NDK module — C++ JNI bridge (`ai_chat.cpp`) + Kotlin engine (`LlamaEngineImpl.kt`)
> - `:app` module — 4 screens, Room DB, ViewModels, Hilt DI, WorkManager download, Foreground Service
> - `llama.cpp` git submodule reference in `.gitmodules`
> - All source files for a buildable, runnable Android app
> - `.github/workflows/build.yml` — GitHub Actions CI/CD: debug APK on every push, GitHub Release on version tags
>
> **Estimated Effort**: XL
> **Parallel Execution**: YES — 8 waves (Wave 1 now has 6 parallel tasks)
> **Critical Path**: Task 1 → Task 7 → Task 8 → Task 17 → Task 25 → Task 29 → Task 30 → F1–F4

---

## Context

### Original Request
> "you are a senior software manager, make me a fully functionnal mobile app, to execute gguf on my samsung s24 exynos, use llama.cpp, make a native mobile app fully optimized for to/s, the ui is compatible for markdown, the `<think>` or `<thinking>` balise, to execute qwen3.5 models, make a lot of research to don't make any bugs"

### Interview Summary

**Key Discussions**:
- Native Kotlin + Jetpack Compose only — NOT React Native
- Samsung Galaxy S24 (Exynos 2400, 12 GB RAM) as primary target
- Maximum tokens/second is the primary performance goal
- Qwen3 models (0.6B, 1.7B, 4B) as primary targets
- Full Markdown rendering + `<think>` / `</think>` animated collapsible sections

**Research Findings (4 agents, synthesized)**:
- Vulkan on Xclipse 940 is UNUSABLE for LLM inference — CPU-only confirmed faster (community reports + llama.cpp issues)
- `GGML_BACKEND_DL=ON` + `GGML_CPU_ALL_VARIANTS=ON` is the correct 2026 build approach (runtime SIMD dispatch)
- `@FastNative` on JNI `external fun` reduces hot-path transition cost
- SAF FD-to-JNI (no file copy) is impossible on Android 11+ — PR #18870 closed unmerged
- Markwon 4.6.2 + Prism4j is the only mature Android markdown library with code highlighting
- `android:extractNativeLibs="true"` CRITICAL — without it, ggml backend `.so` files can't be found by `dlopen()`
- Separate `streamingText: MutableStateFlow` from the messages list prevents LazyColumn full recomposition on every token
- Qwen3 `</think>` token ID is 151668

### Metis Review
**Identified Gaps (all resolved)**:
- `minSdk` unspecified → **Resolved**: `minSdk=33` (Android 13, S24 ships with Android 14)
- SAF FD-to-JNI approach is impossible → **Resolved**: File-copy-on-import to `getExternalFilesDir("models")`
- Thread count unspecified → **Resolved**: User slider with default `max(2, min(6, nProcessors-2))`
- x86_64 ABI decision → **Resolved**: Keep both (arm64-v8a + x86_64 for emulator support)
- Context overflow behavior → **Resolved**: KV-cache shifting (existing llama.cpp behavior) + UI notification on truncation
- 3 silent failure modes identified → **Resolved**: All guarded in acceptance criteria (extractNativeLibs, GGML_NATIVE, nativeLibDir)

---

## Work Objectives

### Core Objective
Build a complete, buildable native Android application that runs GGUF quantized models locally via llama.cpp, with a polished streaming chat UI that renders Markdown and Qwen3 thinking blocks.

### Concrete Deliverables
- `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `.gitmodules`, `gradle/libs.versions.toml`
- `lib/` module: `CMakeLists.txt`, `ai_chat.cpp`, `logging.h`, `LlamaEngine.kt`, `LlamaEngineImpl.kt`
- `app/` module: all Kotlin source files for 4 screens + ViewModels + DI + DB + Service + Worker
- `AndroidManifest.xml` for both modules

### Definition of Done
- [ ] `./gradlew :lib:assembleRelease` completes without errors (native `.so` built)
- [ ] `./gradlew :app:assembleDebug` completes without errors
- [ ] App installs on Android 13+ device/emulator
- [ ] A `.gguf` file can be imported via SAF picker and loaded into llama.cpp
- [ ] Chat generation streams tokens to the UI in real-time
- [ ] Markdown renders correctly in AI messages
- [ ] `<think>` blocks appear as collapsible animated sections

### Must Have
- `android:extractNativeLibs="true"` in app manifest (CRITICAL — prevents silent SIMD fallback)
- `GGML_BACKEND_DL=ON` + `GGML_CPU_ALL_VARIANTS=ON` in CMake (runtime dispatch)
- `Dispatchers.IO.limitedParallelism(1)` for all JNI calls (thread safety)
- `@FastNative` on all JNI `external fun` declarations
- File-copy-on-import pattern (SAF → `getExternalFilesDir("models")`)
- Separate `streamingText` StateFlow from finalized messages list
- `PARTIAL_WAKE_LOCK` during inference
- `FOREGROUND_SERVICE_DATA_SYNC` permission (Android 14 requirement)
- Qwen3 chat template with `enable_thinking` toggle

### Must NOT Have (Guardrails)
- NO `GGML_VULKAN=ON` — confirmed poor performance on Xclipse 940
- NO `GGML_NATIVE=ON` — incompatible with `GGML_BACKEND_DL`, breaks runtime dispatch silently
- NO hardcoded `-march=armv9-a` flags in Gradle — use `GGML_CPU_ALL_VARIANTS=ON` instead
- NO `android:extractNativeLibs="false"` — causes silent SIMD fallback to armv8.0 baseline
- NO passing SAF Uri directly to JNI — always copy to filesystem first
- NO `use_mlock=true` on mobile — triggers Android OOM killer
- NO updating `messages` list on every token — `streamingText` must be separate
- NO cloud/network LLM API calls
- NO RAG, tools, multimodal, or function calling
- NO root-required features
- NO over-engineering: no unnecessary abstraction layers, no premature generalization
- NO `callbackFlow` for token streaming — use `flow { while { nativeNextToken() } }` polling

---

## Verification Strategy

> **ZERO HUMAN INTERVENTION** — ALL verification is agent-executed. No exceptions.

### Test Decision
- **Infrastructure exists**: NO (greenfield project)
- **Automated tests**: NO — no unit/integration test files (not requested, app is greenfield)
- **Framework**: none
- **Agent-Executed QA**: ALWAYS (mandatory for all tasks)

### QA Policy
Every task includes agent-executed QA scenarios. Evidence saved to `.sisyphus/evidence/task-{N}-{slug}.{ext}`.

- **Build verification**: `./gradlew assembleDebug` — `bash` tool
- **Code review**: `grep` / `read` tool to verify critical flags and patterns
- **JNI loading**: Verify correct `System.loadLibrary` and `@FastNative` declarations
- **Manifest checks**: `read` to verify `extractNativeLibs`, permissions, service declaration

---

## Execution Strategy

### Parallel Execution Waves

```
Wave 1 (Start Immediately — project scaffolding, all independent):
├── Task 1: Root project scaffolding (settings/build.gradle.kts, libs.versions.toml, .gitmodules) [quick]
├── Task 2: :lib module build config (lib/build.gradle.kts, lib/AndroidManifest.xml) [unspecified-high]
├── Task 3: :app module build config + manifest (app/build.gradle.kts, AndroidManifest.xml) [unspecified-high]
├── Task 4: Shared type definitions (data classes, sealed states, enums — no Android deps) [quick]
├── Task 5: Material3 theme (Theme.kt, Color.kt, Typography.kt) [visual-engineering]
└── Task 30: GitHub Actions CI/CD workflow (.github/workflows/build.yml — debug APK + tag releases) [quick]

Wave 2 (After Wave 1 — core NDK engine):
├── Task 6: CMakeLists.txt + logging.h (NDK build, GGML flags, llama.cpp submodule link) [unspecified-high]
├── Task 7: ai_chat.cpp JNI wrapper (full C++ engine: init/load/prepare/generate/cancel/unload) [unspecified-high]
├── Task 8: LlamaEngine.kt + LlamaEngineImpl.kt (Kotlin JNI bridge, Flow streaming, @FastNative) [unspecified-high]
└── Task 9: Qwen3ChatTemplate.kt + ThinkTagParser.kt (template formatting + think tag stream parsing) [quick]

Wave 3 (After Wave 1 — data layer, parallel with Wave 2):
├── Task 10: Room DB (AppDatabase.kt, all 3 entities, all DAOs) [unspecified-high]
├── Task 11: ModelStorageManager.kt (SAF copy-on-import, getExternalFilesDir, model listing) [unspecified-high]
├── Task 12: Repository implementations (ChatRepositoryImpl.kt, ModelRepositoryImpl.kt) [unspecified-high]
└── Task 13: LlamaApplication.kt + AppModule.kt (Hilt DI setup, singleton engine binding) [unspecified-high]

Wave 4 (After Wave 2+3 — services and workers):
├── Task 14: InferenceService.kt (Foreground service, PARTIAL_WAKE_LOCK, PerformanceHintManager) [unspecified-high]
├── Task 15: ModelDownloadWorker.kt (WorkManager, Range header resume, progress via setProgress) [unspecified-high]
└── Task 16: All Use Cases (LoadModelUseCase, SendMessageUseCase, DownloadModelUseCase, CancelGenerationUseCase, ImportModelUseCase) [unspecified-high]

Wave 5 (After Wave 4 — ViewModels):
├── Task 17: ChatViewModel.kt (streaming state, perf stats, messages finalization) [unspecified-high]
├── Task 18: ModelPickerViewModel.kt (SAF import flow, download progress, model management) [unspecified-high]
└── Task 19: SettingsViewModel.kt (DataStore persistence, ModelConfig, thread count detection) [unspecified-high]

Wave 6 (After Wave 5 — UI components):
├── Task 20: ThinkingBlock.kt (animated pulsing header, AnimatedVisibility collapse, auto-collapse on complete) [visual-engineering]
├── Task 21: MarkdownRenderer.kt (Markwon 4.6.2 + Prism4j, AndroidView bridge, streaming buffer strategy) [visual-engineering]
├── Task 22: MessageBubble.kt + ChatInputBar.kt (M3 chat bubble shapes, stop button, keyboard handling) [visual-engineering]
└── Task 23: PerformanceHUD.kt (Box overlay, t/s + TTFT + tokens, monospace, toggle) [visual-engineering]

Wave 7 (After Wave 6 — screens):
├── Task 24: ConversationListScreen.kt + ConversationCard.kt (swipe-to-delete, pin, new chat FAB) [visual-engineering]
├── Task 25: ChatScreen.kt (LazyColumn, streaming item isolation, scroll guard, HUD overlay) [visual-engineering]
├── Task 26: ModelPickerScreen.kt (installed list, SAF import, download UI, metadata display) [visual-engineering]
└── Task 27: SettingsScreen.kt (parameter sliders, context length discrete selector, system prompt) [visual-engineering]

Wave 8 (After Wave 7 — integration):
├── Task 28: AppNavigation.kt (NavHost, typed routes, back stack) [quick]
└── Task 29: MainActivity.kt (Hilt entry, setSustainedPerformanceMode, window flags) [quick]

Wave FINAL (After ALL — 4 parallel independent reviews):
├── Task F1: Plan compliance audit [oracle]
├── Task F2: Code quality + build verification [unspecified-high]
├── Task F3: Real manual QA (build + simulate load flow) [unspecified-high]
└── Task F4: Scope fidelity check [deep]

Critical Path: T1 → T2,T6 → T7 → T8 → T16 → T17 → T25 → T28 → T29 → F1-F4
Parallel Speedup: ~75% faster than sequential
Max Concurrent: 5 (Waves 1 & 3 overlap)
```

### Dependency Matrix

| Task | Depends On | Blocks |
|------|-----------|--------|
| 1–5  | — | 6–13 |
| 30   | — | — (independent, no app code deps) |
| 6    | 1,2 | 7 |
| 7    | 6 | 8 |
| 8    | 7,4 | 14,16 |
| 9    | 4 | 17 |
| 10   | 1,4 | 12,13 |
| 11   | 1,4 | 12,15 |
| 12   | 8,10,11 | 16 |
| 13   | 10,12 | 14,17 |
| 14   | 8,13 | 29 |
| 15   | 11,13 | 18 |
| 16   | 8,12 | 17,18,19 |
| 17   | 9,16,13 | 25 |
| 18   | 15,16,13 | 26 |
| 19   | 16,13 | 27 |
| 20–23 | 5,17 | 25 |
| 24   | 5,17 | 28 |
| 25   | 20,21,22,23,17 | 28 |
| 26   | 18,5 | 28 |
| 27   | 19,5 | 28 |
| 28   | 24,25,26,27 | 29 |
| 29   | 28,14 | F1–F4 |

### Agent Dispatch Summary

- **Wave 1**: 6 agents — T1 `quick`, T2 `unspecified-high`, T3 `unspecified-high`, T4 `quick`, T5 `visual-engineering`, T30 `quick`
- **Wave 2+3**: 7 agents — T6,T7,T8 `unspecified-high`, T9 `quick`, T10,T11,T12,T13 `unspecified-high`
- **Wave 4**: 3 agents — T14,T15,T16 `unspecified-high`
- **Wave 5**: 3 agents — T17,T18,T19 `unspecified-high`
- **Wave 6**: 4 agents — T20,T21,T22,T23 `visual-engineering`
- **Wave 7**: 4 agents — T24,T25,T26,T27 `visual-engineering`
- **Wave 8**: 2 agents — T28,T29 `quick`
- **FINAL**: 4 agents — F1 `oracle`, F2 `unspecified-high`, F3 `unspecified-high`, F4 `deep`

---

## TODOs

> Implementation + QA = ONE Task. Every task MUST have QA Scenarios.
> Working directory for all tasks: `/home/0xzknw/Desktop/MobileAgentAI/`

---

- [ ] 1. Root project scaffolding

  **What to do**:
  - Create `settings.gradle.kts` — include `:app` and `:lib` modules, add `google()`, `mavenCentral()`, and `maven("https://jitpack.io")` to `dependencyResolutionManagement`
  - Create root `build.gradle.kts` — only `plugins {}` block with `com.android.application`, `com.android.library`, `org.jetbrains.kotlin.android`, `com.google.dagger.hilt.android` all `apply false`
  - Create `gradle.properties` — set `org.gradle.jvmargs=-Xmx4g -XX:MaxMetaspaceSize=512m`, `android.useAndroidX=true`, `android.nonTransitiveRClass=true`, `kotlin.code.style=official`
  - Create `.gitmodules` — declare `[submodule "llama.cpp"]` with `path = llama.cpp` and `url = https://github.com/ggml-org/llama.cpp.git`
  - Create `gradle/libs.versions.toml` with ALL version catalog entries:
    ```toml
    [versions]
    agp = "8.7.3"
    kotlin = "2.1.0"
    compose-bom = "2025.02.00"
    room = "2.7.0"
    navigation-compose = "2.8.7"
    hilt = "2.54"
    lifecycle = "2.8.7"
    markwon = "4.6.2"
    prism4j = "2.0.0"
    coil3 = "3.1.0"
    datastore-preferences = "1.1.2"
    work-runtime = "2.10.0"
    ndk-version = "29.0.13113456"

    [libraries]
    androidx-core-ktx = { group = "androidx.core", name = "core-ktx", version = "1.15.0" }
    androidx-activity-compose = { group = "androidx.activity", name = "activity-compose", version = "1.10.1" }
    compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "compose-bom" }
    compose-ui = { group = "androidx.compose.ui", name = "ui" }
    compose-ui-tooling-preview = { group = "androidx.compose.ui", name = "ui-tooling-preview" }
    compose-ui-tooling = { group = "androidx.compose.ui", name = "ui-tooling" }
    compose-material3 = { group = "androidx.compose.material3", name = "material3" }
    compose-material-icons = { group = "androidx.compose.material", name = "material-icons-extended" }
    navigation-compose = { group = "androidx.navigation", name = "navigation-compose", version.ref = "navigation-compose" }
    lifecycle-viewmodel-compose = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-compose", version.ref = "lifecycle" }
    lifecycle-runtime-compose = { group = "androidx.lifecycle", name = "lifecycle-runtime-compose", version.ref = "lifecycle" }
    room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "room" }
    room-ktx = { group = "androidx.room", name = "room-ktx", version.ref = "room" }
    room-compiler = { group = "androidx.room", name = "room-compiler", version.ref = "room" }
    hilt-android = { group = "com.google.dagger", name = "hilt-android", version.ref = "hilt" }
    hilt-compiler = { group = "com.google.dagger", name = "hilt-android-compiler", version.ref = "hilt" }
    hilt-navigation-compose = { group = "androidx.hilt", name = "hilt-navigation-compose", version = "1.2.0" }
    markwon-core = { group = "io.noties.markwon", name = "core", version.ref = "markwon" }
    markwon-ext-tables = { group = "io.noties.markwon", name = "ext-tables", version.ref = "markwon" }
    markwon-ext-strikethrough = { group = "io.noties.markwon", name = "ext-strikethrough", version.ref = "markwon" }
    markwon-syntax-highlight = { group = "io.noties.markwon", name = "syntax-highlight", version.ref = "markwon" }
    prism4j = { group = "io.noties", name = "prism4j", version.ref = "prism4j" }
    prism4j-bundler-rainbowcsv = { group = "io.noties", name = "prism4j-bundler-rainbowcsv", version.ref = "prism4j" }
    coil3-compose = { group = "io.coil-kt.coil3", name = "coil-compose", version.ref = "coil3" }
    datastore-preferences = { group = "androidx.datastore", name = "datastore-preferences", version.ref = "datastore-preferences" }
    work-runtime-ktx = { group = "androidx.work", name = "work-runtime-ktx", version.ref = "work-runtime" }
    hilt-work = { group = "androidx.hilt", name = "hilt-work", version = "1.2.0" }
    hilt-work-compiler = { group = "androidx.hilt", name = "hilt-compiler", version = "1.2.0" }

    [plugins]
    android-application = { id = "com.android.application", version.ref = "agp" }
    android-library = { id = "com.android.library", version.ref = "agp" }
    kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
    kotlin-kapt = { id = "org.jetbrains.kotlin.kapt", version.ref = "kotlin" }
    hilt = { id = "com.google.dagger.hilt.android", version.ref = "hilt" }
    ```

  **Must NOT do**:
  - Do NOT put any implementation dependencies in the root `build.gradle.kts`
  - Do NOT use the old `buildscript {}` block style — use plugins block only

  **Recommended Agent Profile**:
  - **Category**: `quick`
    - Reason: Pure config file creation, no logic, no Android APIs
  - **Skills**: none

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 1 (with Tasks 2, 3, 4, 5)
  - **Blocks**: Tasks 2–13 (all subsequent waves depend on these Gradle configs)
  - **Blocked By**: None — start immediately

  **References**:
  - Pattern: https://github.com/ggml-org/llama.cpp/tree/master/examples/llama.android — root `settings.gradle.kts` structure
  - Pattern: https://github.com/shubham0204/SmolChat-Android — `gradle/libs.versions.toml` catalog layout for similar LLM chat app

  **Acceptance Criteria**:
  - [ ] `settings.gradle.kts` includes `:app` and `:lib` with `include(":app", ":lib")`
  - [ ] `gradle/libs.versions.toml` contains all listed libraries (grep: `markwon`, `room`, `hilt`, `prism4j`)
  - [ ] `.gitmodules` contains `[submodule "llama.cpp"]` entry
  - [ ] `gradle.properties` contains `org.gradle.jvmargs=-Xmx4g`

  **QA Scenarios**:
  ```
  Scenario: Version catalog completeness check
    Tool: Bash (grep)
    Steps:
      1. grep -c 'version' gradle/libs.versions.toml
      2. Assert count > 15
      3. grep 'markwon' gradle/libs.versions.toml — assert match found
      4. grep 'prism4j' gradle/libs.versions.toml — assert match found
      5. grep 'hilt' gradle/libs.versions.toml — assert match found
    Expected Result: All critical libs present
    Evidence: .sisyphus/evidence/task-1-catalog-check.txt

  Scenario: Submodule declaration check
    Tool: Bash (cat)
    Steps:
      1. cat .gitmodules
      2. Assert contains 'llama.cpp'
      3. Assert contains 'ggml-org'
    Expected Result: Submodule correctly declared
    Evidence: .sisyphus/evidence/task-1-gitmodules.txt
  ```

  **Commit**: YES (with Tasks 2, 3)
  - Message: `chore(gradle): init project with version catalog and llama.cpp submodule`
  - Files: `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `.gitmodules`, `gradle/libs.versions.toml`

---

- [ ] 2. `:lib` module build config

  **What to do**:
  - Create `lib/build.gradle.kts`:
    ```kotlin
    plugins {
        alias(libs.plugins.android.library)
        alias(libs.plugins.kotlin.android)
    }
    android {
        namespace = "com.example.llama"
        compileSdk = 36
        defaultConfig {
            minSdk = 33
            ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
            externalNativeBuild {
                cmake {
                    arguments("-DANDROID_STL=c++_shared",
                               "-DBUILD_SHARED_LIBS=ON",
                               "-DLLAMA_BUILD_COMMON=ON",
                               "-DGGML_BACKEND_DL=ON",
                               "-DGGML_CPU_ALL_VARIANTS=ON",
                               "-DGGML_NATIVE=OFF",
                               "-DGGML_LLAMAFILE=OFF",
                               "-DGGML_VULKAN=OFF",
                               "-DLLAMA_OPENSSL=OFF",
                               "-DCMAKE_BUILD_TYPE=Release")
                    cppFlags("-std=c++17 -ffast-math -funroll-loops")
                }
            }
        }
        externalNativeBuild { cmake { path = file("src/main/cpp/CMakeLists.txt") } }
        ndkVersion = libs.versions.ndk.version.get()
        compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
        kotlinOptions { jvmTarget = "17" }
    }
    dependencies {
        implementation(libs.androidx.core.ktx)
        implementation(libs.lifecycle.viewmodel.compose)
    }
    ```
  - Create `lib/src/main/AndroidManifest.xml` — minimal library manifest with `package="com.example.llama"`

  **Must NOT do**:
  - Do NOT add `GGML_NATIVE=ON` under any circumstances — breaks runtime SIMD dispatch
  - Do NOT add `GGML_VULKAN=ON` — poor on Xclipse 940
  - Do NOT set `extractNativeLibs` here (that goes in the `:app` manifest)

  **Recommended Agent Profile**:
  - **Category**: `unspecified-high`
    - Reason: Requires precise NDK/CMake flag knowledge — wrong flags cause silent performance degradation
  - **Skills**: none

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 1 (with Tasks 1, 3, 4, 5)
  - **Blocks**: Task 6 (CMakeLists.txt needs lib module to exist)
  - **Blocked By**: None — start immediately

  **References**:
  - NDK flags reference: `GGML_BACKEND_DL=ON` + `GGML_CPU_ALL_VARIANTS=ON` — documented in `llama.cpp/docs/android.md`
  - Android STL: must be `c++_shared` when `BUILD_SHARED_LIBS=ON` — see https://developer.android.com/ndk/guides/cpp-support

  **Acceptance Criteria**:
  - [ ] `lib/build.gradle.kts` contains `GGML_BACKEND_DL=ON`
  - [ ] `lib/build.gradle.kts` contains `GGML_VULKAN=OFF`
  - [ ] `lib/build.gradle.kts` does NOT contain `GGML_NATIVE=ON`
  - [ ] `DANDROID_STL=c++_shared` present
  - [ ] `ndkVersion` references catalog (not hardcoded)

  **QA Scenarios**:
  ```
  Scenario: Critical CMake flags present
    Tool: Bash (grep)
    Steps:
      1. grep 'GGML_BACKEND_DL=ON' lib/build.gradle.kts
      2. Assert match found
      3. grep 'GGML_VULKAN=OFF' lib/build.gradle.kts
      4. Assert match found
      5. grep 'GGML_NATIVE' lib/build.gradle.kts
      6. Assert does NOT contain '=ON'
    Expected Result: All performance-critical flags correctly set
    Evidence: .sisyphus/evidence/task-2-cmake-flags.txt
  ```

  **Commit**: YES (with Tasks 1, 3)
  - Message: `chore(gradle): init project with version catalog and llama.cpp submodule`
  - Files: `lib/build.gradle.kts`, `lib/src/main/AndroidManifest.xml`

---

- [ ] 3. `:app` module build config + manifest

  **What to do**:
  - Create `app/build.gradle.kts`:
    ```kotlin
    plugins {
        alias(libs.plugins.android.application)
        alias(libs.plugins.kotlin.android)
        alias(libs.plugins.kotlin.kapt)
        alias(libs.plugins.hilt)
    }
    android {
        namespace = "com.example.llamaapp"
        compileSdk = 36
        defaultConfig {
            applicationId = "com.example.llamaapp"
            minSdk = 33
            targetSdk = 36
            versionCode = 1
            versionName = "1.0"
        }
        buildFeatures { compose = true }
        composeOptions { kotlinCompilerExtensionVersion = "1.5.15" }
        compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
        kotlinOptions { jvmTarget = "17" }
        packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }
    kapt { correctErrorTypes = true }
    dependencies {
        implementation(project(":lib"))
        implementation(platform(libs.compose.bom))
        implementation(libs.compose.ui)
        implementation(libs.compose.material3)
        implementation(libs.compose.material.icons)
        implementation(libs.compose.ui.tooling.preview)
        implementation(libs.navigation.compose)
        implementation(libs.lifecycle.viewmodel.compose)
        implementation(libs.lifecycle.runtime.compose)
        implementation(libs.room.runtime)
        implementation(libs.room.ktx)
        kapt(libs.room.compiler)
        implementation(libs.hilt.android)
        kapt(libs.hilt.compiler)
        implementation(libs.hilt.navigation.compose)
        implementation(libs.markwon.core)
        implementation(libs.markwon.ext.tables)
        implementation(libs.markwon.ext.strikethrough)
        implementation(libs.markwon.syntax.highlight)
        implementation(libs.prism4j)
        implementation(libs.prism4j.bundler.rainbowcsv)
        implementation(libs.datastore.preferences)
        implementation(libs.work.runtime.ktx)
        implementation(libs.hilt.work)
        kapt(libs.hilt.work.compiler)
        debugImplementation(libs.compose.ui.tooling)
    }
    ```
  - Create `app/src/main/AndroidManifest.xml` with:
    - `android:extractNativeLibs="true"` on `<application>` (CRITICAL)
    - `android:name=".LlamaApplication"`
    - All 5 permissions: INTERNET, FOREGROUND_SERVICE, FOREGROUND_SERVICE_DATA_SYNC, WAKE_LOCK, POST_NOTIFICATIONS
    - `<service android:name=".service.InferenceService" android:exported="false" android:foregroundServiceType="dataSync" />`
    - `<activity android:name=".MainActivity" android:exported="true" android:theme="@style/Theme.AppCompat.DayNight.NoActionBar">` with MAIN/LAUNCHER intent filter

  **Must NOT do**:
  - Do NOT set `android:extractNativeLibs="false"` — causes silent fallback to armv8.0 baseline
  - Do NOT forget `FOREGROUND_SERVICE_DATA_SYNC` — required Android 14+ or service crashes at runtime

  **Recommended Agent Profile**:
  - **Category**: `unspecified-high`
    - Reason: Manifest flags have silent runtime failure modes — extractNativeLibs and service type are critical
  - **Skills**: none

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 1 (with Tasks 1, 2, 4, 5)
  - **Blocks**: Tasks 10–13 (app data layer needs app module), Task 29 (MainActivity)
  - **Blocked By**: None — start immediately

  **References**:
  - `extractNativeLibs` critical flag: https://developer.android.com/guide/topics/manifest/application-element#extractNativeLibs
  - `foregroundServiceType="dataSync"` Android 14: https://developer.android.com/about/versions/14/changes/fgs-types-required

  **Acceptance Criteria**:
  - [ ] `android:extractNativeLibs="true"` present in manifest
  - [ ] `FOREGROUND_SERVICE_DATA_SYNC` permission present
  - [ ] `android:foregroundServiceType="dataSync"` on InferenceService declaration
  - [ ] `POST_NOTIFICATIONS` permission present
  - [ ] All 5 Compose BOM dependencies resolved via `platform(libs.compose.bom)` (no hardcoded versions)

  **QA Scenarios**:
  ```
  Scenario: Critical manifest flags check
    Tool: Bash (grep)
    Steps:
      1. grep 'extractNativeLibs' app/src/main/AndroidManifest.xml
      2. Assert value is 'true'
      3. grep 'FOREGROUND_SERVICE_DATA_SYNC' app/src/main/AndroidManifest.xml
      4. Assert match found
      5. grep 'foregroundServiceType' app/src/main/AndroidManifest.xml
      6. Assert contains 'dataSync'
    Expected Result: All critical manifest flags set correctly
    Evidence: .sisyphus/evidence/task-3-manifest-check.txt
  ```

  **Commit**: YES (with Tasks 1, 2)
  - Message: `chore(gradle): init project with version catalog and llama.cpp submodule`
  - Files: `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`

---

- [ ] 4. Shared type definitions

  **What to do**:
  - Create `lib/src/main/java/com/example/llama/LlamaEngine.kt` — define the public interface + sealed states:
    ```kotlin
    package com.example.llama

    import kotlinx.coroutines.flow.Flow

    // Sealed UI state for loading lifecycle
    sealed class ModelLoadState {
        object Idle : ModelLoadState()
        data class Loading(val progress: Float) : ModelLoadState()
        data class Loaded(val modelName: String) : ModelLoadState()
        data class Error(val message: String) : ModelLoadState()
    }

    // Token generation state emitted from Flow
    sealed class GenerationEvent {
        data class Token(val text: String) : GenerationEvent()
        data class ThinkToken(val text: String) : GenerationEvent()
        object ThinkStart : GenerationEvent()
        object ThinkEnd : GenerationEvent()
        data class Done(val tokensPerSecond: Float, val totalTokens: Int) : GenerationEvent()
        data class Error(val message: String) : GenerationEvent()
    }

    // Performance stats
    data class InferenceStats(
        val tokensPerSecond: Float = 0f,
        val timeToFirstToken: Long = 0L,
        val totalTokens: Int = 0,
        val promptTokens: Int = 0
    )

    // Model config (persisted via DataStore)
    data class ModelConfig(
        val nThreads: Int = 4,
        val nCtx: Int = 4096,
        val nBatch: Int = 512,
        val temperature: Float = 0.7f,
        val topP: Float = 0.8f,
        val topK: Int = 20,
        val repeatPenalty: Float = 1.1f,
        val enableThinking: Boolean = true,
        val thinkingTemperature: Float = 0.6f,
        val thinkingTopP: Float = 0.95f,
        val maxNewTokens: Int = 2048
    )

    // LlamaEngine public interface
    interface LlamaEngine {
        val loadState: kotlinx.coroutines.flow.StateFlow<ModelLoadState>
        suspend fun loadModel(modelPath: String, config: ModelConfig): Result<Unit>
        fun generate(conversationHistory: List<Pair<String, String>>, userMessage: String, config: ModelConfig): Flow<GenerationEvent>
        fun cancelGeneration()
        fun unloadModel()
        fun isModelLoaded(): Boolean
        fun getModelName(): String?
    }
    ```
  - Create `app/src/main/java/com/example/llamaapp/data/model/Entities.kt` — pure data classes (no Room annotations — those go in T10):
    ```kotlin
    data class GgufModel(val id: Long, val name: String, val filePath: String, val fileSize: Long, val paramCount: String, val quantization: String, val addedAt: Long, val lastUsedAt: Long)
    data class ChatMessage(val id: Long, val conversationId: Long, val role: String, val content: String, val thinkingContent: String?, val createdAt: Long, val generationTimeMs: Long?, val tokensPerSecond: Float?)
    data class Conversation(val id: Long, val title: String, val modelId: Long?, val systemPrompt: String?, val createdAt: Long, val updatedAt: Long, val totalTokens: Int, val isPinned: Boolean)
    enum class MessageRole { USER, ASSISTANT, SYSTEM }
    ```

  **Must NOT do**:
  - Do NOT import any Android-specific classes (Context, Activity, etc.) — these files have ZERO Android deps
  - Do NOT use `callbackFlow` — Flow interface is fine here, implementation uses `flow { while {} }` (in T8)

  **Recommended Agent Profile**:
  - **Category**: `quick`
    - Reason: Pure Kotlin data classes and interfaces, no logic
  - **Skills**: none

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 1 (with Tasks 1, 2, 3, 5)
  - **Blocks**: Tasks 8, 9, 10, 12, 16, 17, 18, 19 (all layers depend on these types)
  - **Blocked By**: None — start immediately

  **References**:
  - SmolChat-Android engine interface pattern: https://github.com/shubham0204/SmolChat-Android/blob/main/app/src/main/java/io/github/shubham0204/smolchat/llm/SmolLM.kt

  **Acceptance Criteria**:
  - [ ] `LlamaEngine` interface has `loadModel`, `generate`, `cancelGeneration`, `unloadModel`
  - [ ] `GenerationEvent` sealed class has `Token`, `ThinkToken`, `ThinkStart`, `ThinkEnd`, `Done`, `Error`
  - [ ] `ModelConfig` has `enableThinking`, `nThreads`, `nCtx`, `temperature`
  - [ ] No Android imports in either file

  **QA Scenarios**:
  ```
  Scenario: No Android imports in type files
    Tool: Bash (grep)
    Steps:
      1. grep -n 'import android' lib/src/main/java/com/example/llama/LlamaEngine.kt
      2. Assert zero matches
      3. grep 'ThinkStart\|ThinkEnd\|ThinkToken' lib/src/main/java/com/example/llama/LlamaEngine.kt
      4. Assert all 3 found
    Expected Result: Clean type definitions, no Android coupling
    Evidence: .sisyphus/evidence/task-4-types-check.txt
  ```

  **Commit**: YES (group with T5)
  - Message: `feat(lib): add engine interface, type definitions, and Material3 theme`
  - Files: `lib/src/main/java/com/example/llama/LlamaEngine.kt`, `app/src/main/java/com/example/llamaapp/data/model/Entities.kt`

---

- [ ] 5. Material3 theme

  **What to do**:
  - Create `app/src/main/java/com/example/llamaapp/ui/theme/Color.kt`:
    - Define dark-first color palette suitable for a chat app (dark background, teal accent)
    - Surface: `#1A1A2E`, Background: `#0F0F1A`, Primary: `#00BFA5` (teal), Secondary: `#7B61FF`
    - `UserBubbleColor`, `AiBubbleColor`, `ThinkingBlockBackground` as custom token colors
  - Create `app/src/main/java/com/example/llamaapp/ui/theme/Typography.kt`:
    - Use `Inter` via `FontFamily.Default` (no font download needed)
    - Override `bodyLarge` for chat bubbles: `fontSize=15.sp`, `lineHeight=22.sp`
    - Override `labelSmall` for performance HUD: monospace font, `fontSize=11.sp`
  - Create `app/src/main/java/com/example/llamaapp/ui/theme/Theme.kt`:
    - `LlamaAppTheme` composable — force dark theme always (`darkColorScheme`)
    - Apply `darkColorScheme(primary=Primary, surface=Surface, background=Background, ...)`
    - No dynamic color (Material You) — consistent dark experience

  **Must NOT do**:
  - Do NOT use `dynamicDarkColorScheme` — forces consistent look regardless of wallpaper
  - Do NOT add light theme support (not requested, complicates maintenance)

  **Recommended Agent Profile**:
  - **Category**: `visual-engineering`
    - Reason: Color design and typography decisions for chat app aesthetics
  - **Skills**: [`tailwind-design-system`]
    - `tailwind-design-system`: Color token naming and typography scale concepts apply directly

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 1 (with Tasks 1, 2, 3, 4)
  - **Blocks**: Tasks 20–27 (all UI components need theme)
  - **Blocked By**: None — start immediately

  **Acceptance Criteria**:
  - [ ] `LlamaAppTheme` composable exists in `Theme.kt`
  - [ ] `UserBubbleColor` and `AiBubbleColor` defined in `Color.kt`
  - [ ] `ThinkingBlockBackground` token defined
  - [ ] Dark-only — no `isSystemInDarkTheme()` conditional

  **QA Scenarios**:
  ```
  Scenario: Theme structure check
    Tool: Bash (grep)
    Steps:
      1. grep 'LlamaAppTheme' app/src/main/java/com/example/llamaapp/ui/theme/Theme.kt
      2. Assert match found
      3. grep 'ThinkingBlockBackground' app/src/main/java/com/example/llamaapp/ui/theme/Color.kt
      4. Assert match found
      5. grep 'dynamicDarkColorScheme' app/src/main/java/com/example/llamaapp/ui/theme/Theme.kt
      6. Assert ZERO matches (must NOT use dynamic color)
    Expected Result: Theme correctly structured, no dynamic color
    Evidence: .sisyphus/evidence/task-5-theme-check.txt
  ```

  **Commit**: YES (with Task 4)
  - Message: `feat(lib): add engine interface, type definitions, and Material3 theme`
  - Files: `app/src/main/java/com/example/llamaapp/ui/theme/Color.kt`, `Typography.kt`, `Theme.kt`

---

- [ ] 6. CMakeLists.txt + logging.h

  **What to do**:
  - Create `lib/src/main/cpp/logging.h`:
    ```cpp
    #pragma once
    #include <android/log.h>
    #define TAG "LlamaEngine"
    #define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
    #define LOGW(...) __android_log_print(ANDROID_LOG_WARN, TAG, __VA_ARGS__)
    #define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)
    ```
  - Create `lib/src/main/cpp/CMakeLists.txt`:
    ```cmake
    cmake_minimum_required(VERSION 3.22.1)
    project(llama_android)

    # llama.cpp submodule root (relative to this CMakeLists.txt)
    set(LLAMA_ROOT ${CMAKE_SOURCE_DIR}/../../../../../llama.cpp)

    # Per-ABI KleidiAI + OpenMP (arm64-v8a only)
    if(ANDROID_ABI STREQUAL "arm64-v8a")
        set(GGML_CPU_KLEIDIAI ON)
        set(GGML_OPENMP ON)
    endif()

    # Include llama.cpp build system
    add_subdirectory(${LLAMA_ROOT} llama_build)

    # JNI bridge library
    add_library(llama_jni SHARED ai_chat.cpp)

    target_include_directories(llama_jni PRIVATE
        ${LLAMA_ROOT}/include
        ${LLAMA_ROOT}/ggml/include
        ${LLAMA_ROOT}/common
        ${CMAKE_SOURCE_DIR}
    )

    target_link_libraries(llama_jni
        llama
        ggml
        android
        log
    )

    target_compile_options(llama_jni PRIVATE
        -O3
        -ffast-math
        -funroll-loops
    )
    ```

  **Must NOT do**:
  - Do NOT set `GGML_NATIVE=ON` anywhere in CMakeLists.txt — breaks runtime dispatch
  - Do NOT hardcode `-march=armv9-a` — use `GGML_CPU_ALL_VARIANTS=ON` from Gradle instead
  - Do NOT call `find_package(OpenMP)` manually — set `GGML_OPENMP=ON` and let ggml handle it

  **Recommended Agent Profile**:
  - **Category**: `unspecified-high`
    - Reason: CMake NDK cross-compilation with llama.cpp submodule — path resolution and flag interactions are subtle
  - **Skills**: none

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 2 (with Tasks 7, 8, 9, 10, 11, 12, 13)
  - **Blocks**: Task 7 (C++ JNI wrapper needs CMake)
  - **Blocked By**: Tasks 1, 2

  **References**:
  - llama.cpp Android example CMakeLists.txt: https://github.com/ggml-org/llama.cpp/blob/master/examples/llama.android/app/src/main/cpp/CMakeLists.txt
  - KleidiAI enablement: https://gitlab.arm.com/kleidi/kleidiai — `GGML_CPU_KLEIDIAI=ON` flag

  **Acceptance Criteria**:
  - [ ] `add_subdirectory` path resolves to `llama.cpp` submodule root
  - [ ] `target_link_libraries` includes `llama`, `ggml`, `android`, `log`
  - [ ] `GGML_CPU_KLEIDIAI=ON` inside `if(arm64-v8a)` block
  - [ ] `logging.h` has LOGI, LOGW, LOGE macros

  **QA Scenarios**:
  ```
  Scenario: CMakeLists structure check
    Tool: Bash (grep)
    Steps:
      1. grep 'GGML_CPU_KLEIDIAI' lib/src/main/cpp/CMakeLists.txt
      2. Assert match found
      3. grep 'GGML_NATIVE' lib/src/main/cpp/CMakeLists.txt
      4. Assert ZERO matches (must not be set here)
      5. grep 'add_subdirectory' lib/src/main/cpp/CMakeLists.txt
      6. Assert contains 'llama.cpp' in path
    Expected Result: CMakeLists.txt correctly configured
    Evidence: .sisyphus/evidence/task-6-cmake-check.txt
  ```

  **Commit**: YES (with Tasks 7, 8, 9)
  - Message: `feat(lib): add NDK engine with CMake, JNI C++ wrapper, and Kotlin bridge`
  - Files: `lib/src/main/cpp/CMakeLists.txt`, `lib/src/main/cpp/logging.h`

---

- [ ] 7. ai_chat.cpp — JNI C++ engine

  **What to do**:
  - Create `lib/src/main/cpp/ai_chat.cpp` with the full JNI engine:
    - JNI function naming: `Java_com_example_llama_internal_LlamaEngineImpl_nativeInit`, etc.
    - **Global state struct** (single static instance, NOT shared across threads):
      ```cpp
      struct LlamaState {
          llama_model* model = nullptr;
          llama_context* ctx = nullptr;
          llama_sampler* sampler = nullptr;
          llama_sampler* thinkSampler = nullptr;
          std::vector<llama_token> tokens;
          std::atomic<bool> cancelled{false};
          bool isThinking = false;
      } g_state;
      ```
    - `nativeInit(nativeLibDir: String)` — call `llama_backend_init()`, pass `nativeLibDir` so ggml can find backend `.so` files via `dlopen`
    - `nativeLoadModel(modelPath: String, nThreads: Int, nCtx: Int, nBatch: Int)` — `llama_model_load_from_file`, `llama_new_context_with_model`
    - `nativePreparePrompt(formattedPrompt: String)` — tokenize, `llama_decode` for prompt eval
    - `nativeNextToken(): String?` — sample one token, return null on EOS or cancellation:
      ```cpp
      // Check cancellation
      if (g_state.cancelled.load()) return nullptr;
      // Sample with appropriate sampler (thinking vs chat)
      llama_token id = llama_sampler_sample(sampler, ctx, -1);
      if (llama_token_is_eog(model, id)) return nullptr;
      // Check think-end token (Qwen3: 151668)
      if (id == 151668) { g_state.isThinking = false; return "</think>"; }
      // Detokenize and return
      char buf[256]; int len = llama_token_to_piece(model, id, buf, sizeof(buf), 0, true);
      return env->NewStringUTF(std::string(buf, len).c_str());
      ```
    - `nativeCancelGeneration()` — set `g_state.cancelled = true`
    - `nativeUnloadModel()` — free sampler, ctx, model; reset state
    - `nativeGetModelName(): String` — return `llama_model_meta_val_str(model, "general.name")`
    - Two samplers: chat sampler (`temp=0.7, top_p=0.8, top_k=20`) and think sampler (`temp=0.6, top_p=0.95, top_k=20`)
    - Use `use_mlock = false` ALWAYS — mobile OOM killer
    - n_gpu_layers = 0 ALWAYS (CPU only)

  **Must NOT do**:
  - Do NOT use `use_mlock=true` — triggers Android OOM killer
  - Do NOT set `n_gpu_layers > 0` — Vulkan confirmed poor, CPU only
  - Do NOT use `callbackFlow` or any Kotlin-side threading in this file (pure C++)
  - Do NOT allocate large buffers on stack — use heap or llama.cpp APIs

  **Recommended Agent Profile**:
  - **Category**: `unspecified-high`
    - Reason: Low-level llama.cpp C++ API, memory management, JNI string handling, atomic cancellation
  - **Skills**: none

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 2 (with Tasks 6, 8, 9, 10, 11, 12, 13)
  - **Blocks**: Task 8 (Kotlin JNI bridge declares these native funs)
  - **Blocked By**: Task 6 (CMakeLists.txt defines include paths)

  **References**:
  - llama.cpp Android `ai_chat.cpp`: https://github.com/ggml-org/llama.cpp/blob/master/examples/llama.android/app/src/main/cpp/ai_chat.cpp
  - llama.cpp C API: `llama_model_load_from_file`, `llama_new_context_with_model`, `llama_sampler_sample` — see `llama.cpp/include/llama.h`
  - Qwen3 think token ID 151668: confirmed in Qwen3 tokenizer config

  **Acceptance Criteria**:
  - [ ] All 7 native functions declared with correct JNI naming convention
  - [ ] `use_mlock = false` in model params
  - [ ] `n_gpu_layers = 0` in context params
  - [ ] Think token 151668 checked in `nativeNextToken`
  - [ ] `cancelled` is `std::atomic<bool>` (not plain bool)

  **QA Scenarios**:
  ```
  Scenario: Critical safety flags in C++ code
    Tool: Bash (grep)
    Steps:
      1. grep 'use_mlock' lib/src/main/cpp/ai_chat.cpp
      2. Assert value is 'false'
      3. grep 'n_gpu_layers' lib/src/main/cpp/ai_chat.cpp
      4. Assert value is '0'
      5. grep '151668' lib/src/main/cpp/ai_chat.cpp
      6. Assert match found (think-end token check)
      7. grep 'atomic' lib/src/main/cpp/ai_chat.cpp
      8. Assert match found (cancelled is atomic)
    Expected Result: All safety flags correctly set in C++ engine
    Evidence: .sisyphus/evidence/task-7-cpp-flags.txt
  ```

  **Commit**: YES (with Tasks 6, 8, 9)
  - Message: `feat(lib): add NDK engine with CMake, JNI C++ wrapper, and Kotlin bridge`
  - Files: `lib/src/main/cpp/ai_chat.cpp`

---

- [ ] 8. LlamaEngineImpl.kt — Kotlin JNI bridge

  **What to do**:
  - Create `lib/src/main/java/com/example/llama/internal/LlamaEngineImpl.kt`:
    ```kotlin
    package com.example.llama.internal

    import android.content.Context
    import androidx.annotation.FastNative
    import com.example.llama.*
    import kotlinx.coroutines.*
    import kotlinx.coroutines.flow.*

    class LlamaEngineImpl(private val context: Context) : LlamaEngine {

        companion object {
            init { System.loadLibrary("llama_jni") }
        }

        private val llamaDispatcher = Dispatchers.IO.limitedParallelism(1)
        private val _loadState = MutableStateFlow<ModelLoadState>(ModelLoadState.Idle)
        override val loadState: StateFlow<ModelLoadState> = _loadState.asStateFlow()

        // All @FastNative — reduces JNI transition overhead on hot token loop
        @FastNative external fun nativeInit(nativeLibDir: String)
        @FastNative external fun nativeLoadModel(modelPath: String, nThreads: Int, nCtx: Int, nBatch: Int): Boolean
        @FastNative external fun nativePreparePrompt(formattedPrompt: String)
        @FastNative external fun nativeNextToken(): String?
        @FastNative external fun nativeCancelGeneration()
        @FastNative external fun nativeUnloadModel()
        @FastNative external fun nativeGetModelName(): String
        @FastNative external fun nativeGetTokensPerSecond(): Float
        @FastNative external fun nativeGetPromptTokenCount(): Int

        init {
            val nativeLibDir = context.applicationInfo.nativeLibraryDir
            nativeInit(nativeLibDir)
        }

        override suspend fun loadModel(modelPath: String, config: ModelConfig): Result<Unit> {
            return withContext(llamaDispatcher) {
                _loadState.value = ModelLoadState.Loading(0f)
                runCatching {
                    val ok = nativeLoadModel(modelPath, config.nThreads, config.nCtx, config.nBatch)
                    if (!ok) throw IllegalStateException("Failed to load model: $modelPath")
                    _loadState.value = ModelLoadState.Loaded(nativeGetModelName())
                }.onFailure { _loadState.value = ModelLoadState.Error(it.message ?: "Unknown error") }
            }
        }

        override fun generate(
            conversationHistory: List<Pair<String, String>>,
            userMessage: String,
            config: ModelConfig
        ): Flow<GenerationEvent> = flow {
            val template = Qwen3ChatTemplate()
            val formatted = template.format(conversationHistory, userMessage, config.enableThinking)
            withContext(llamaDispatcher) { nativePreparePrompt(formatted) }
            val startTime = System.currentTimeMillis()
            var totalTokens = 0
            val parser = ThinkTagParser()
            while (true) {
                currentCoroutineContext().ensureActive()
                val token = withContext(llamaDispatcher) { nativeNextToken() } ?: break
                totalTokens++
                val events = parser.feed(token)
                events.forEach { emit(it) }
            }
            val elapsed = System.currentTimeMillis() - startTime
            val tps = if (elapsed > 0) totalTokens * 1000f / elapsed else 0f
            emit(GenerationEvent.Done(tps, totalTokens))
        }.flowOn(llamaDispatcher)

        override fun cancelGeneration() { nativeCancelGeneration() }
        override fun unloadModel() { runBlocking(llamaDispatcher) { nativeUnloadModel() }; _loadState.value = ModelLoadState.Idle }
        override fun isModelLoaded() = loadState.value is ModelLoadState.Loaded
        override fun getModelName() = (loadState.value as? ModelLoadState.Loaded)?.modelName
    }
    ```

  **Must NOT do**:
  - Do NOT use `callbackFlow` — use `flow { while { nativeNextToken() } }` polling
  - Do NOT call JNI functions from any dispatcher other than `llamaDispatcher` (`limitedParallelism(1)`)
  - Do NOT use `@CriticalNative` (native allocates Java objects) — `@FastNative` only

  **Recommended Agent Profile**:
  - **Category**: `unspecified-high`
    - Reason: Coroutines threading model with `limitedParallelism(1)`, `@FastNative`, flow cancellation semantics — subtle bugs cause crashes
  - **Skills**: none

  **Parallelization**:
  - **Can Run In Parallel**: YES (parallel with 6, 7, 9, 10, 11, 12, 13)
  - **Parallel Group**: Wave 2
  - **Blocks**: Tasks 14, 16 (service and use cases need engine interface)
  - **Blocked By**: Tasks 4 (types), 7 (native funs to declare)

  **References**:
  - llama.cpp Android `Llm.kt`: https://github.com/ggml-org/llama.cpp/blob/master/examples/llama.android/app/src/main/java/com/example/llama/Llm.kt
  - `@FastNative` documentation: https://developer.android.com/training/articles/perf-jni#jni_tips
  - `limitedParallelism(1)` pattern for JNI: ensures single-threaded access to global C state

  **Acceptance Criteria**:
  - [ ] All 9 `external fun` declarations have `@FastNative`
  - [ ] `llamaDispatcher = Dispatchers.IO.limitedParallelism(1)`
  - [ ] `nativeInit(nativeLibDir)` called in `init {}` block with `context.applicationInfo.nativeLibraryDir`
  - [ ] `generate()` uses `flow { while { nativeNextToken() } }` (NOT `callbackFlow`)
  - [ ] `currentCoroutineContext().ensureActive()` inside the while loop

  **QA Scenarios**:
  ```
  Scenario: JNI bridge critical patterns
    Tool: Bash (grep)
    Steps:
      1. grep '@FastNative' lib/src/main/java/com/example/llama/internal/LlamaEngineImpl.kt
      2. Assert count >= 7 (one per external fun)
      3. grep 'limitedParallelism(1)' lib/src/main/java/com/example/llama/internal/LlamaEngineImpl.kt
      4. Assert match found
      5. grep 'callbackFlow' lib/src/main/java/com/example/llama/internal/LlamaEngineImpl.kt
      6. Assert ZERO matches
      7. grep 'ensureActive' lib/src/main/java/com/example/llama/internal/LlamaEngineImpl.kt
      8. Assert match found
    Expected Result: All critical JNI bridge patterns correct
    Evidence: .sisyphus/evidence/task-8-jni-bridge.txt
  ```

  **Commit**: YES (with Tasks 6, 7, 9)
  - Message: `feat(lib): add NDK engine with CMake, JNI C++ wrapper, and Kotlin bridge`
  - Files: `lib/src/main/java/com/example/llama/internal/LlamaEngineImpl.kt`

---

- [ ] 9. Qwen3ChatTemplate.kt + ThinkTagParser.kt

  **What to do**:
  - Create `app/src/main/java/com/example/llamaapp/engine/Qwen3ChatTemplate.kt`:
    ```kotlin
    class Qwen3ChatTemplate {
        companion object {
            private const val IM_START = "<|im_start|>"
            private const val IM_END = "<|im_end|>"
            private const val DEFAULT_SYSTEM = "You are a helpful assistant."
        }

        fun format(
            history: List<Pair<String, String>>, // (user, assistant) pairs
            userMessage: String,
            enableThinking: Boolean,
            systemPrompt: String = DEFAULT_SYSTEM
        ): String = buildString {
            append(IM_START).append("system\n").append(systemPrompt).append(IM_END).append("\n")
            for ((user, assistant) in history) {
                append(IM_START).append("user\n").append(user).append(IM_END).append("\n")
                append(IM_START).append("assistant\n").append(assistant).append(IM_END).append("\n")
            }
            append(IM_START).append("user\n").append(userMessage).append(IM_END).append("\n")
            append(IM_START).append("assistant\n")
            if (enableThinking) append("<think>\n") else append("\n\n")
        }
    }
    ```
  - Create `app/src/main/java/com/example/llamaapp/engine/ThinkTagParser.kt`:
    ```kotlin
    // Parses the raw token stream from LlamaEngineImpl and emits GenerationEvents.
    // Handles <think>, </think> boundaries that may arrive mid-token.
    class ThinkTagParser {
        private var inThink = false
        private var buffer = StringBuilder()

        fun feed(rawToken: String): List<GenerationEvent> {
            val events = mutableListOf<GenerationEvent>()
            buffer.append(rawToken)
            val text = buffer.toString()

            when {
                text.contains("<think>") && !inThink -> {
                    inThink = true
                    events.add(GenerationEvent.ThinkStart)
                    buffer.clear()
                }
                text.contains("</think>") && inThink -> {
                    inThink = false
                    val before = text.substringBefore("</think>")
                    if (before.isNotEmpty()) events.add(GenerationEvent.ThinkToken(before))
                    events.add(GenerationEvent.ThinkEnd)
                    buffer.clear()
                    val after = text.substringAfter("</think>")
                    if (after.isNotEmpty()) { buffer.append(after) }
                }
                // Partial tag may still be building in buffer — hold
                text.endsWith("<") || text.endsWith("</") || text.endsWith("</t") -> { /* hold */ }
                else -> {
                    if (buffer.isNotEmpty()) {
                        if (inThink) events.add(GenerationEvent.ThinkToken(buffer.toString()))
                        else events.add(GenerationEvent.Token(buffer.toString()))
                        buffer.clear()
                    }
                }
            }
            return events
        }

        fun reset() { inThink = false; buffer.clear() }
    }
    ```

  **Must NOT do**:
  - Do NOT use regex for streaming parsing — too expensive on every token
  - Do NOT assume think tags arrive on exact token boundaries (Qwen3 tokenizer may split them)

  **Recommended Agent Profile**:
  - **Category**: `quick`
    - Reason: Pure Kotlin string parsing with no Android deps
  - **Skills**: none

  **Parallelization**:
  - **Can Run In Parallel**: YES (Wave 2, parallel with 6, 7, 8, 10, 11, 12, 13)
  - **Blocks**: Task 17 (ChatViewModel uses ThinkTagParser via engine)
  - **Blocked By**: Task 4 (needs `GenerationEvent` types)

  **Acceptance Criteria**:
  - [ ] `format()` produces `<|im_start|>assistant\n<think>\n` when `enableThinking=true`
  - [ ] `format()` produces `<|im_start|>assistant\n\n\n` when `enableThinking=false`
  - [ ] `ThinkTagParser.feed()` handles partial tag boundaries (e.g., `<` alone doesn't flush)
  - [ ] Both `ThinkStart` and `ThinkEnd` events are emitted correctly

  **QA Scenarios**:
  ```
  Scenario: Chat template output format
    Tool: Bash (kotlin REPL or unit test)
    Steps:
      1. Instantiate Qwen3ChatTemplate()
      2. Call format(emptyList(), "Hello", enableThinking=true)
      3. Assert result contains '<|im_start|>assistant'
      4. Assert result ends with '<think>\n'
      5. Call format(emptyList(), "Hello", enableThinking=false)
      6. Assert result does NOT contain '<think>'
    Expected Result: Chat template correctly formats Qwen3 prompts
    Evidence: .sisyphus/evidence/task-9-template-check.txt

  Scenario: ThinkTagParser boundary handling
    Tool: Bash (kotlin REPL or unit test)
    Steps:
      1. Create ThinkTagParser()
      2. Feed tokens: ["<think", ">", "reasoning", "</think", ">", "answer"]
      3. Assert ThinkStart emitted after '<think>'+'>'
      4. Assert ThinkEnd emitted after '</think>'+'>'
      5. Assert final 'answer' emits as Token (not ThinkToken)
    Expected Result: Think boundaries correctly parsed across partial tokens
    Evidence: .sisyphus/evidence/task-9-parser-check.txt
  ```

  **Commit**: YES (with Tasks 6, 7, 8)
  - Message: `feat(lib): add NDK engine with CMake, JNI C++ wrapper, and Kotlin bridge`
  - Files: `app/src/main/java/com/example/llamaapp/engine/Qwen3ChatTemplate.kt`, `ThinkTagParser.kt`

---

- [ ] 10. Room database — entities and DAOs

  **What to do**:
  - Create `app/src/main/java/com/example/llamaapp/data/db/AppDatabase.kt` with `@Database` (entities, version=1, exportSchema=false)
  - Create entity files with Room annotations:
    - `ConversationEntity.kt`: `@Entity(tableName="conversations")` with fields: id (PK autoGenerate), title, modelId (nullable), systemPrompt (nullable), createdAt, updatedAt, totalTokens=0, isPinned=false
    - `MessageEntity.kt`: `@Entity(tableName="messages")` with ForeignKey on conversations.id (CASCADE delete), fields: id, conversationId, role (String), content (TEXT), **thinkingContent** (TEXT nullable — separate column for think block), createdAt, generationTimeMs (nullable), tokensPerSecond (nullable Float)
    - `ModelEntity.kt`: `@Entity(tableName="models")` with fields: id (PK), name, filePath (UNIQUE), fileSize, paramCount, quantization, addedAt, lastUsedAt
  - Create DAO interfaces:
    - `ConversationDao.kt`: `getAllConversations(): Flow<List<ConversationEntity>>`, `getConversationById(id)`, `insert(): Long`, `update()`, `deleteById()`, `updatePinned(id, isPinned)`, `updateTimestamp(id, updatedAt)`
    - `MessageDao.kt`: `getMessagesForConversation(conversationId): Flow<List<MessageEntity>>`, `insert(): Long`, `deleteByConversationId()`, `getLastNMessages(conversationId, n): List<MessageEntity>`
    - `ModelDao.kt`: `getAllModels(): Flow<List<ModelEntity>>`, `getModelByPath(path): ModelEntity?`, `insert(): Long`, `delete()`, `updateLastUsed(id, lastUsedAt)`
  - Add `Converters.kt` if needed (none needed — all primitives + String)

  **Must NOT do**:
  - Do NOT use `@TypeConverters` for List/Map — keep schema flat (no JSON blobs)
  - Do NOT forget `thinkingContent` column on MessageEntity — it's a separate column, NOT concatenated into content

  **Recommended Agent Profile**:
  - **Category**: `unspecified-high`
    - Reason: Room annotation precision, ForeignKey cascade setup, Flow DAOs
  - **Skills**: [`prisma-expert`]
    - `prisma-expert`: Schema design principles (normalization, foreign keys) apply directly to Room

  **Parallelization**:
  - **Can Run In Parallel**: YES (Wave 2+3, parallel with 6, 7, 8, 9, 11, 12, 13)
  - **Blocks**: Tasks 12, 13 (repositories and DI need DB)
  - **Blocked By**: Tasks 1, 4

  **References**:
  - Room with Kotlin coroutines: https://developer.android.com/training/data-storage/room/async-queries#flow
  - ForeignKey with CASCADE delete in Room: `@ForeignKey(entity=ConversationEntity::class, parentColumns=["id"], childColumns=["conversationId"], onDelete=CASCADE)`

  **Acceptance Criteria**:
  - [ ] `@Database` lists all 3 entities
  - [ ] `MessageEntity` has `thinkingContent: String?` field
  - [ ] ForeignKey with CASCADE delete on `MessageEntity.conversationId`
  - [ ] All DAO query functions returning Flow (for live updates)
  - [ ] `ModelDao.getModelByPath()` returns nullable (not crash on missing)

  **QA Scenarios**:
  ```
  Scenario: thinkingContent column exists in schema
    Tool: Bash (grep)
    Steps:
      1. grep 'thinkingContent' app/src/main/java/com/example/llamaapp/data/db/MessageEntity.kt
      2. Assert match found
      3. grep 'ForeignKey' app/src/main/java/com/example/llamaapp/data/db/MessageEntity.kt
      4. Assert contains 'CASCADE'
      5. grep 'exportSchema' app/src/main/java/com/example/llamaapp/data/db/AppDatabase.kt
      6. Assert value is 'false'
    Expected Result: Schema correctly declared
    Evidence: .sisyphus/evidence/task-10-room-schema.txt
  ```

  **Commit**: YES (with Tasks 11, 12, 13)
  - Message: `feat(app): add data layer (Room DB, storage manager, repositories, Hilt DI)`
  - Files: `app/src/main/java/com/example/llamaapp/data/db/`

---

- [ ] 11. ModelStorageManager.kt

  **What to do**:
  - Create `app/src/main/java/com/example/llamaapp/storage/ModelStorageManager.kt`:
    ```kotlin
    @Singleton
    class ModelStorageManager @Inject constructor(@ApplicationContext private val context: Context) {

        private val modelsDir: File get() =
            context.getExternalFilesDir("models") ?: context.filesDir.resolve("models")

        // Copy SAF Uri to app-private storage — never pass Uri to JNI
        suspend fun importModel(uri: Uri, onProgress: (Float) -> Unit = {}): File =
            withContext(Dispatchers.IO) {
                val fileName = resolveFileName(uri)
                val destFile = modelsDir.also { it.mkdirs() }.resolve(fileName)
                val totalSize = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    cursor.moveToFirst()
                    cursor.getLong(cursor.getColumnIndexOrThrow(OpenableColumns.SIZE))
                } ?: -1L
                var copied = 0L
                context.contentResolver.openInputStream(uri)!!.use { input ->
                    destFile.outputStream().use { output ->
                        val buf = ByteArray(8 * 1024 * 1024) // 8 MB buffer
                        var n: Int
                        while (input.read(buf).also { n = it } != -1) {
                            output.write(buf, 0, n)
                            copied += n
                            if (totalSize > 0) onProgress(copied.toFloat() / totalSize)
                        }
                    }
                }
                destFile
            }

        fun listModels(): List<File> =
            modelsDir.listFiles { f -> f.extension.lowercase() == "gguf" }?.toList() ?: emptyList()

        fun deleteModel(file: File) { file.delete() }

        fun getModelSize(file: File): Long = file.length()

        private fun resolveFileName(uri: Uri): String {
            var name = "model.gguf"
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) name = cursor.getString(idx)
                }
            }
            return name.ifEmpty { "model_${System.currentTimeMillis()}.gguf" }
        }
    }
    ```

  **Must NOT do**:
  - Do NOT pass the Uri directly to JNI — ALWAYS copy first (Android 11+ blocks FD-to-path resolution)
  - Do NOT use `context.filesDir` as primary location — use `getExternalFilesDir("models")` for large GGUF files

  **Recommended Agent Profile**:
  - **Category**: `unspecified-high`
    - Reason: SAF file copying, progress tracking, Android storage APIs — multiple silent failure modes
  - **Skills**: none

  **Parallelization**:
  - **Can Run In Parallel**: YES (Wave 2+3)
  - **Blocks**: Tasks 12, 15 (repositories and download worker need storage manager)
  - **Blocked By**: Tasks 1, 4

  **References**:
  - SAF copy pattern: `contentResolver.openInputStream(uri)` + `file.outputStream()` — standard Android approach since API 30
  - `getExternalFilesDir("models")` vs internal storage: external supports up to 12 GB GGUF files without storage permission (app-private)

  **Acceptance Criteria**:
  - [ ] `importModel()` copies to `getExternalFilesDir("models")`
  - [ ] 8 MB buffer used (not default 8 KB)
  - [ ] `onProgress` callback called during copy
  - [ ] `listModels()` filters by `.gguf` extension only
  - [ ] Fallback to `filesDir` if `getExternalFilesDir` returns null

  **QA Scenarios**:
  ```
  Scenario: Storage location and buffer size
    Tool: Bash (grep)
    Steps:
      1. grep 'getExternalFilesDir' app/src/main/java/com/example/llamaapp/storage/ModelStorageManager.kt
      2. Assert match found
      3. grep '8 \* 1024 \* 1024' app/src/main/java/com/example/llamaapp/storage/ModelStorageManager.kt
      4. Assert match found (8 MB buffer)
      5. grep 'Uri directly\|uri.*JNI' app/src/main/java/com/example/llamaapp/storage/ModelStorageManager.kt
      6. Assert ZERO matches (should copy, never pass Uri)
    Expected Result: Storage manager uses correct location and buffer
    Evidence: .sisyphus/evidence/task-11-storage.txt
  ```

  **Commit**: YES (with Tasks 10, 12, 13)
  - Message: `feat(app): add data layer (Room DB, storage manager, repositories, Hilt DI)`
  - Files: `app/src/main/java/com/example/llamaapp/storage/ModelStorageManager.kt`

---

- [ ] 12. Repository implementations

  **What to do**:
  - Create `app/src/main/java/com/example/llamaapp/data/repository/ChatRepository.kt` (interface) and `ChatRepositoryImpl.kt`:
    - `getConversations(): Flow<List<Conversation>>`
    - `getMessages(conversationId): Flow<List<ChatMessage>>`
    - `createConversation(title, modelId, systemPrompt): Long`
    - `saveMessage(conversationId, role, content, thinkingContent, generationTimeMs, tokensPerSecond): Long`
    - `deleteConversation(id)`
    - `updateConversationTitle(id, title)`
    - `pinConversation(id, isPinned)`
    - `getLastNMessages(conversationId, n): List<ChatMessage>` — for context window building
  - Create `ModelRepository.kt` (interface) and `ModelRepositoryImpl.kt`:
    - `getInstalledModels(): Flow<List<GgufModel>>`
    - `registerModel(file: File): Long` — reads file metadata, inserts to DB
    - `getModelByPath(path: String): GgufModel?`
    - `deleteModel(id: Long)` — deletes DB record AND file via ModelStorageManager
    - `updateLastUsed(id: Long)`
  - Map between DB entities and domain data classes (Entity → domain model mappers as extension functions)

  **Must NOT do**:
  - Do NOT expose Room entities outside the data layer — always map to/from domain models
  - Do NOT do file I/O in the repository (delegate to ModelStorageManager)

  **Recommended Agent Profile**:
  - **Category**: `unspecified-high`
    - Reason: Repository pattern, entity↔domain mapping, Flow composition
  - **Skills**: none

  **Parallelization**:
  - **Can Run In Parallel**: YES (Wave 2+3)
  - **Blocks**: Task 16 (use cases depend on repositories)
  - **Blocked By**: Tasks 8 (domain types via LlamaEngine), 10 (DB entities), 11 (storage manager)

  **References**:
  - Repository + entity mapping pattern: SmolChat-Android — https://github.com/shubham0204/SmolChat-Android

  **Acceptance Criteria**:
  - [ ] `saveMessage()` accepts `thinkingContent: String?` parameter
  - [ ] `getLastNMessages()` exists for context window building
  - [ ] `deleteModel()` in ModelRepositoryImpl calls `ModelStorageManager.deleteModel()`
  - [ ] No Room entities in function signatures (domain models only)

  **QA Scenarios**:
  ```
  Scenario: thinkingContent parameter propagated
    Tool: Bash (grep)
    Steps:
      1. grep 'thinkingContent' app/src/main/java/com/example/llamaapp/data/repository/ChatRepositoryImpl.kt
      2. Assert match found (parameter in saveMessage)
      3. grep 'ConversationEntity\|MessageEntity' app/src/main/java/com/example/llamaapp/data/repository/ChatRepositoryImpl.kt
      4. Assert these are only in mapping extension functions, not in public signatures
    Expected Result: Repository correctly abstracts entities behind domain models
    Evidence: .sisyphus/evidence/task-12-repo-check.txt
  ```

  **Commit**: YES (with Tasks 10, 11, 13)
  - Message: `feat(app): add data layer (Room DB, storage manager, repositories, Hilt DI)`
  - Files: `app/src/main/java/com/example/llamaapp/data/repository/`

---

- [ ] 13. Hilt DI setup — LlamaApplication + AppModule

  **What to do**:
  - Create `app/src/main/java/com/example/llamaapp/LlamaApplication.kt`:
    ```kotlin
    @HiltAndroidApp
    class LlamaApplication : Application()
    ```
  - Create `app/src/main/java/com/example/llamaapp/di/AppModule.kt`:
    ```kotlin
    @Module
    @InstallIn(SingletonComponent::class)
    object AppModule {

        @Provides @Singleton
        fun provideLlamaEngine(@ApplicationContext ctx: Context): LlamaEngine =
            LlamaEngineImpl(ctx)

        @Provides @Singleton
        fun provideDatabase(@ApplicationContext ctx: Context): AppDatabase =
            Room.databaseBuilder(ctx, AppDatabase::class.java, "llama_db")
                .fallbackToDestructiveMigration()
                .build()

        @Provides fun provideConversationDao(db: AppDatabase) = db.conversationDao()
        @Provides fun provideMessageDao(db: AppDatabase) = db.messageDao()
        @Provides fun provideModelDao(db: AppDatabase) = db.modelDao()

        @Provides @Singleton
        fun provideModelStorageManager(@ApplicationContext ctx: Context) = ModelStorageManager(ctx)

        @Provides @Singleton
        fun provideChatRepository(dao: ConversationDao, msgDao: MessageDao): ChatRepository =
            ChatRepositoryImpl(dao, msgDao)

        @Provides @Singleton
        fun provideModelRepository(dao: ModelDao, storage: ModelStorageManager): ModelRepository =
            ModelRepositoryImpl(dao, storage)

        @Provides @Singleton
        fun provideDataStore(@ApplicationContext ctx: Context): DataStore<Preferences> =
            PreferenceDataStoreFactory.create { ctx.preferencesDataStoreFile("settings") }
    }
    ```

  **Must NOT do**:
  - Do NOT create a `WorkerModule` here — HiltWorker uses `@HiltWorker` + `@AssistedInject` (handled in T15)
  - Do NOT instantiate `LlamaEngineImpl` more than once — `@Singleton` is critical (single C++ state)

  **Recommended Agent Profile**:
  - **Category**: `unspecified-high`
    - Reason: Hilt component scoping, singleton engine binding — duplicate instances cause C++ state corruption
  - **Skills**: none

  **Parallelization**:
  - **Can Run In Parallel**: YES (Wave 2+3)
  - **Blocks**: Tasks 14, 17, 18, 19 (all ViewModels/Service inject from this module)
  - **Blocked By**: Tasks 10, 12 (need DB + repos to provide)

  **Acceptance Criteria**:
  - [ ] `LlamaApplication` has `@HiltAndroidApp`
  - [ ] `LlamaEngine` provided as `@Singleton`
  - [ ] `AppDatabase` provided as `@Singleton`
  - [ ] DataStore provided as `@Singleton`
  - [ ] `android:name=".LlamaApplication"` in manifest (already set in T3)

  **QA Scenarios**:
  ```
  Scenario: Singleton annotations on critical providers
    Tool: Bash (grep)
    Steps:
      1. grep -c '@Singleton' app/src/main/java/com/example/llamaapp/di/AppModule.kt
      2. Assert count >= 5 (engine, db, storage, chatRepo, modelRepo, datastore)
      3. grep 'LlamaEngineImpl' app/src/main/java/com/example/llamaapp/di/AppModule.kt
      4. Assert exactly 1 match (single instantiation point)
      5. grep '@HiltAndroidApp' app/src/main/java/com/example/llamaapp/LlamaApplication.kt
      6. Assert match found
    Expected Result: DI wiring is correct, engine is singleton
    Evidence: .sisyphus/evidence/task-13-hilt-check.txt
  ```

  **Commit**: YES (with Tasks 10, 11, 12)
  - Message: `feat(app): add data layer (Room DB, storage manager, repositories, Hilt DI)`
  - Files: `app/src/main/java/com/example/llamaapp/LlamaApplication.kt`, `app/src/main/java/com/example/llamaapp/di/AppModule.kt`

---

- [ ] 14. InferenceService.kt — Foreground Service

  **What to do**:
  - Create `app/src/main/java/com/example/llamaapp/service/InferenceService.kt`:
    - `@AndroidEntryPoint` foreground service
    - Holds a `PowerManager.WakeLock` (PARTIAL_WAKE_LOCK) acquired on `startForeground()`, released on stop
    - Uses `PerformanceHintManager` (Android 12+) with `createHintSession()` targeting big core cluster: detect performance cores via `Runtime.getRuntime().availableProcessors() / 2` heuristic
    - Exposes a bound service interface via `LocalBinder` for ViewModels to observe generation state
    - Actions: `ACTION_START_INFERENCE`, `ACTION_CANCEL_INFERENCE`, `ACTION_STOP_SERVICE`
    - Creates a persistent notification with cancel action (NotificationCompat, channel ID `inference_channel`)
    - Notification shows model name + "Generating..." or "Ready" text
    - Injects `LlamaEngine` via Hilt `@Inject`
    - Calls `startForeground(NOTIF_ID, notification, FOREGROUND_SERVICE_TYPE_DATA_SYNC)`
    ```kotlin
    // Key structure:
    @AndroidEntryPoint
    class InferenceService : Service() {
        @Inject lateinit var engine: LlamaEngine
        private lateinit var wakeLock: PowerManager.WakeLock
        private var hintSession: PerformanceHintManager.Session? = null

        inner class LocalBinder : Binder() { fun getService() = this@InferenceService }
        private val binder = LocalBinder()
        override fun onBind(intent: Intent) = binder

        override fun onCreate() {
            super.onCreate()
            val pm = getSystemService(PowerManager::class.java)
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "LlamaApp::InferenceLock")
            if (Build.VERSION.SDK_INT >= 31) {
                val phm = getSystemService(PerformanceHintManager::class.java)
                val tids = intArrayOf(android.os.Process.myTid())
                hintSession = phm?.createHintSession(tids, 50_000_000L) // 50ms target
            }
        }
        // ... startForeground, actions, wakeLock.acquire/release
    }
    ```

  **Must NOT do**:
  - Do NOT use `FOREGROUND_SERVICE_TYPE_LOCATION` or other types — use `dataSync` only
  - Do NOT hold wakeLock when not inferencing — acquire on start, release on done/cancel
  - Do NOT start inference directly in the service — delegate to `LlamaEngine` (injected)

  **Recommended Agent Profile**:
  - **Category**: `unspecified-high`
    - Reason: Android Foreground Service lifecycle, WakeLock, PerformanceHintManager API 31+, Hilt AndroidEntryPoint
  - **Skills**: none

  **Parallelization**:
  - **Can Run In Parallel**: YES (Wave 4, with Task 15, 16)
  - **Blocks**: Task 29 (MainActivity starts the service)
  - **Blocked By**: Tasks 8 (engine), 13 (Hilt module)

  **References**:
  - Android 14 foreground service types: https://developer.android.com/about/versions/14/changes/fgs-types-required
  - PerformanceHintManager: https://developer.android.com/reference/android/os/PerformanceHintManager
  - `FOREGROUND_SERVICE_TYPE_DATA_SYNC` constant value: `android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC`

  **Acceptance Criteria**:
  - [ ] `startForeground()` called with `FOREGROUND_SERVICE_TYPE_DATA_SYNC`
  - [ ] WakeLock acquired on inference start, released on done/cancel
  - [ ] `@AndroidEntryPoint` present
  - [ ] `PerformanceHintManager` usage gated behind `Build.VERSION.SDK_INT >= 31`
  - [ ] Notification channel created (required for Android 8+)

  **QA Scenarios**:
  ```
  Scenario: Foreground service type declaration
    Tool: Bash (grep)
    Steps:
      1. grep 'FOREGROUND_SERVICE_TYPE_DATA_SYNC' app/src/main/java/com/example/llamaapp/service/InferenceService.kt
      2. Assert match found
      3. grep 'PARTIAL_WAKE_LOCK' app/src/main/java/com/example/llamaapp/service/InferenceService.kt
      4. Assert match found
      5. grep 'SDK_INT >= 31' app/src/main/java/com/example/llamaapp/service/InferenceService.kt
      6. Assert match found (PerformanceHintManager gated)
    Expected Result: Service correctly uses foreground type and WakeLock
    Evidence: .sisyphus/evidence/task-14-service-check.txt
  ```

  **Commit**: YES (with Tasks 15, 16)
  - Message: `feat(app): add inference service, download worker, and use cases`
  - Files: `app/src/main/java/com/example/llamaapp/service/InferenceService.kt`

---

- [ ] 15. ModelDownloadWorker.kt — WorkManager download

  **What to do**:
  - Create `app/src/main/java/com/example/llamaapp/worker/ModelDownloadWorker.kt`:
    ```kotlin
    @HiltWorker
    class ModelDownloadWorker @AssistedInject constructor(
        @Assisted appContext: Context,
        @Assisted workerParams: WorkerParameters,
        private val storageManager: ModelStorageManager,
        private val modelRepository: ModelRepository
    ) : CoroutineWorker(appContext, workerParams) {

        companion object {
            const val KEY_URL = "download_url"
            const val KEY_FILENAME = "filename"
            const val KEY_PROGRESS = "download_progress"
            const val KEY_ERROR = "error_message"
        }

        override suspend fun doWork(): Result {
            val url = inputData.getString(KEY_URL) ?: return Result.failure()
            val filename = inputData.getString(KEY_FILENAME) ?: "model.gguf"
            val destFile = applicationContext.getExternalFilesDir("models")!!.resolve(filename)
            return try {
                downloadWithResume(url, destFile)
                modelRepository.registerModel(destFile)
                Result.success()
            } catch (e: Exception) {
                Result.failure(workDataOf(KEY_ERROR to e.message))
            }
        }

        private suspend fun downloadWithResume(url: String, dest: File) {
            val existingBytes = if (dest.exists()) dest.length() else 0L
            val connection = URL(url).openConnection() as HttpURLConnection
            if (existingBytes > 0) connection.setRequestProperty("Range", "bytes=$existingBytes-")
            connection.connect()
            val totalSize = connection.contentLengthLong + existingBytes
            var downloaded = existingBytes
            connection.inputStream.use { input ->
                FileOutputStream(dest, existingBytes > 0).use { output ->
                    val buf = ByteArray(8 * 1024 * 1024) // 8 MB buffer
                    var n: Int
                    while (input.read(buf).also { n = it } != -1) {
                        output.write(buf, 0, n)
                        downloaded += n
                        val progress = if (totalSize > 0) (downloaded * 100 / totalSize).toInt() else 0
                        setProgress(workDataOf(KEY_PROGRESS to progress))
                    }
                }
            }
        }
    }
    ```
  - Register worker with `@HiltWorkerFactory` — override `Configuration.Provider` in `LlamaApplication`:
    ```kotlin
    // In LlamaApplication:
    @Inject lateinit var workerFactory: HiltWorkerFactory
    override fun getWorkManagerConfiguration() = Configuration.Builder()
        .setWorkerFactory(workerFactory).build()
    ```
  - Add `WorkManager` initialization: in `LlamaApplication` implement `androidx.work.Configuration.Provider`

  **Must NOT do**:
  - Do NOT use default `WorkManager.initialize()` — Hilt requires custom factory via `Configuration.Provider`
  - Do NOT use 1 KB buffer for download — use 8 MB minimum for large GGUF files

  **Recommended Agent Profile**:
  - **Category**: `unspecified-high`
    - Reason: `@HiltWorker` + `@AssistedInject` pattern, Range header HTTP resume, WorkManager configuration
  - **Skills**: none

  **Parallelization**:
  - **Can Run In Parallel**: YES (Wave 4, with Tasks 14, 16)
  - **Blocks**: Task 18 (ModelPickerViewModel shows download progress)
  - **Blocked By**: Tasks 11, 13 (storage manager + Hilt)

  **References**:
  - HiltWorker pattern: https://developer.android.com/training/dependency-injection/hilt-jetpack#workmanager
  - Range header HTTP resume: `setRequestProperty("Range", "bytes=$existingBytes-")`
  - WorkManager + Hilt Configuration.Provider: https://developer.android.com/topic/libraries/architecture/workmanager/advanced/custom-configuration

  **Acceptance Criteria**:
  - [ ] `@HiltWorker` + `@AssistedInject` on constructor
  - [ ] Range header added when partial file exists
  - [ ] 8 MB buffer used
  - [ ] `LlamaApplication` implements `Configuration.Provider`
  - [ ] `modelRepository.registerModel(destFile)` called on completion

  **QA Scenarios**:
  ```
  Scenario: HiltWorker annotations present
    Tool: Bash (grep)
    Steps:
      1. grep '@HiltWorker' app/src/main/java/com/example/llamaapp/worker/ModelDownloadWorker.kt
      2. Assert match found
      3. grep '@AssistedInject' app/src/main/java/com/example/llamaapp/worker/ModelDownloadWorker.kt
      4. Assert match found
      5. grep 'Range.*bytes' app/src/main/java/com/example/llamaapp/worker/ModelDownloadWorker.kt
      6. Assert match found (resume support)
      7. grep 'Configuration.Provider' app/src/main/java/com/example/llamaapp/LlamaApplication.kt
      8. Assert match found
    Expected Result: WorkManager correctly configured with Hilt and resume support
    Evidence: .sisyphus/evidence/task-15-worker-check.txt
  ```

  **Commit**: YES (with Tasks 14, 16)
  - Message: `feat(app): add inference service, download worker, and use cases`
  - Files: `app/src/main/java/com/example/llamaapp/worker/ModelDownloadWorker.kt`

---

- [ ] 16. Use Cases

  **What to do**:
  - Create `app/src/main/java/com/example/llamaapp/domain/usecase/` with 5 use case classes:

  1. **`LoadModelUseCase.kt`**:
     ```kotlin
     class LoadModelUseCase @Inject constructor(private val engine: LlamaEngine, private val modelRepo: ModelRepository) {
         suspend operator fun invoke(modelPath: String, config: ModelConfig): Result<Unit> {
             modelRepo.updateLastUsed(modelPath)
             return engine.loadModel(modelPath, config)
         }
     }
     ```

  2. **`SendMessageUseCase.kt`**:
     ```kotlin
     class SendMessageUseCase @Inject constructor(
         private val engine: LlamaEngine,
         private val chatRepo: ChatRepository
     ) {
         operator fun invoke(
             conversationId: Long,
             history: List<ChatMessage>,
             userMessage: String,
             config: ModelConfig
         ): Flow<GenerationEvent> {
             val historyPairs = history
                 .filter { it.role != "system" }
                 .zipWithNext { a, b -> if (a.role == "user" && b.role == "assistant") Pair(a.content, b.content) else null }
                 .filterNotNull()
             return engine.generate(historyPairs, userMessage, config)
         }
     }
     ```

  3. **`ImportModelUseCase.kt`**:
     ```kotlin
     class ImportModelUseCase @Inject constructor(
         private val storageManager: ModelStorageManager,
         private val modelRepo: ModelRepository
     ) {
         suspend operator fun invoke(uri: Uri, onProgress: (Float) -> Unit): Result<GgufModel> = runCatching {
             val file = storageManager.importModel(uri, onProgress)
             val id = modelRepo.registerModel(file)
             modelRepo.getModelByPath(file.absolutePath)!!
         }
     }
     ```

  4. **`CancelGenerationUseCase.kt`**:
     ```kotlin
     class CancelGenerationUseCase @Inject constructor(private val engine: LlamaEngine) {
         operator fun invoke() = engine.cancelGeneration()
     }
     ```

  5. **`UnloadModelUseCase.kt`**:
     ```kotlin
     class UnloadModelUseCase @Inject constructor(private val engine: LlamaEngine) {
         operator fun invoke() = engine.unloadModel()
     }
     ```

  **Must NOT do**:
  - Do NOT put business logic in ViewModels — use cases are the logic layer
  - Do NOT save messages to DB inside `SendMessageUseCase` — that's done in ViewModel after stream completes

  **Recommended Agent Profile**:
  - **Category**: `unspecified-high`
    - Reason: Clean architecture use case pattern, Flow composition, history pair building for context window
  - **Skills**: none

  **Parallelization**:
  - **Can Run In Parallel**: YES (Wave 4, with Tasks 14, 15)
  - **Blocks**: Tasks 17, 18, 19 (ViewModels inject use cases)
  - **Blocked By**: Tasks 8 (engine interface), 12 (repositories)

  **References**:
  - Clean Architecture use case pattern: one public `operator fun invoke()`, injected via Hilt
  - Conversation history building: `zipWithNext` to pair consecutive user/assistant messages

  **Acceptance Criteria**:
  - [ ] All 5 use cases created with `@Inject constructor`
  - [ ] `SendMessageUseCase` builds history pairs from message list
  - [ ] `ImportModelUseCase` calls both `storageManager.importModel()` AND `modelRepo.registerModel()`
  - [ ] No `saveMessage()` call in `SendMessageUseCase` (ViewModel responsibility)

  **QA Scenarios**:
  ```
  Scenario: Use case files exist with correct patterns
    Tool: Bash (grep)
    Steps:
      1. ls app/src/main/java/com/example/llamaapp/domain/usecase/
      2. Assert 5 files present (LoadModel, SendMessage, ImportModel, CancelGeneration, UnloadModel)
      3. grep 'operator fun invoke' app/src/main/java/com/example/llamaapp/domain/usecase/LoadModelUseCase.kt
      4. Assert match found
      5. grep 'saveMessage\|insertMessage' app/src/main/java/com/example/llamaapp/domain/usecase/SendMessageUseCase.kt
      6. Assert ZERO matches (saving is ViewModel's job)
    Expected Result: All use cases correctly structured
    Evidence: .sisyphus/evidence/task-16-usecases.txt
  ```

  **Commit**: YES (with Tasks 14, 15)
  - Message: `feat(app): add inference service, download worker, and use cases`
  - Files: `app/src/main/java/com/example/llamaapp/domain/usecase/`

---

- [ ] 17. ChatViewModel.kt

  **What to do**:
  - Create `app/src/main/java/com/example/llamaapp/ui/chat/ChatViewModel.kt`:
    ```kotlin
    @HiltViewModel
    class ChatViewModel @Inject constructor(
        private val sendMessage: SendMessageUseCase,
        private val cancelGeneration: CancelGenerationUseCase,
        private val chatRepo: ChatRepository,
        private val engine: LlamaEngine
    ) : ViewModel() {

        // SEPARATE StateFlows — never mix streaming text with messages list
        private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
        val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

        private val _streamingContent = MutableStateFlow("")  // recomposes freely
        val streamingContent: StateFlow<String> = _streamingContent.asStateFlow()

        private val _streamingThinkContent = MutableStateFlow("")
        val streamingThinkContent: StateFlow<String> = _streamingThinkContent.asStateFlow()

        private val _isGenerating = MutableStateFlow(false)
        val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

        private val _isThinking = MutableStateFlow(false)
        val isThinking: StateFlow<Boolean> = _isThinking.asStateFlow()

        private val _inferenceStats = MutableStateFlow(InferenceStats())
        val inferenceStats: StateFlow<InferenceStats> = _inferenceStats.asStateFlow()

        private val _error = MutableStateFlow<String?>(null)
        val error: StateFlow<String?> = _error.asStateFlow()

        var currentConversationId: Long = -1L

        fun sendMessage(userText: String, config: ModelConfig) {
            viewModelScope.launch {
                _isGenerating.value = true
                _streamingContent.value = ""
                _streamingThinkContent.value = ""
                _isThinking.value = false
                val history = _messages.value
                // Save user message to DB
                chatRepo.saveMessage(currentConversationId, "user", userText, null, null, null)
                sendMessage(currentConversationId, history, userText, config)
                    .catch { e -> _error.value = e.message }
                    .collect { event ->
                        when (event) {
                            is GenerationEvent.Token -> _streamingContent.value += event.text
                            is GenerationEvent.ThinkToken -> _streamingThinkContent.value += event.text
                            is GenerationEvent.ThinkStart -> _isThinking.value = true
                            is GenerationEvent.ThinkEnd -> _isThinking.value = false
                            is GenerationEvent.Done -> {
                                // Atomically move streaming buffers to final message
                                val finalContent = _streamingContent.value
                                val finalThink = _streamingThinkContent.value.ifEmpty { null }
                                chatRepo.saveMessage(
                                    currentConversationId, "assistant",
                                    finalContent, finalThink,
                                    event.totalTokens.toLong(), event.tokensPerSecond
                                )
                                _streamingContent.value = ""
                                _streamingThinkContent.value = ""
                                _isGenerating.value = false
                                _inferenceStats.value = InferenceStats(
                                    tokensPerSecond = event.tokensPerSecond,
                                    totalTokens = event.totalTokens
                                )
                                loadMessages()
                            }
                            is GenerationEvent.Error -> {
                                _error.value = event.message
                                _isGenerating.value = false
                            }
                        }
                    }
            }
        }

        fun cancelGeneration() { cancelGeneration.invoke(); _isGenerating.value = false }

        fun loadMessages() { viewModelScope.launch {
            chatRepo.getMessages(currentConversationId).collect { _messages.value = it }
        }}
    }
    ```

  **Must NOT do**:
  - Do NOT update `_messages` on every token — only update after stream completes (`GenerationEvent.Done`)
  - Do NOT append to `_messages` during streaming — use separate `_streamingContent` StateFlow

  **Recommended Agent Profile**:
  - **Category**: `unspecified-high`
    - Reason: StateFlow separation for streaming, atomic message finalization, coroutine lifecycle in viewModelScope
  - **Skills**: none

  **Parallelization**:
  - **Can Run In Parallel**: YES (Wave 5, with Tasks 18, 19)
  - **Blocks**: Task 25 (ChatScreen needs this ViewModel)
  - **Blocked By**: Tasks 9 (template), 16 (use cases), 13 (Hilt)

  **References**:
  - Streaming pattern: `_streamingContent` (MutableStateFlow) separate from `messages` list — prevents LazyColumn recomposition
  - `viewModelScope.launch` + `flow.collect` with `catch` operator for error handling

  **Acceptance Criteria**:
  - [ ] `_streamingContent` and `_messages` are separate StateFlows
  - [ ] `_messages` only updated in `GenerationEvent.Done` handler (not on every Token)
  - [ ] `saveMessage()` called for both user message AND final assistant message
  - [ ] `cancelGeneration()` sets `_isGenerating.value = false`
  - [ ] `_isThinking` StateFlow tracks think block state

  **QA Scenarios**:
  ```
  Scenario: Streaming separation pattern enforced
    Tool: Bash (grep)
    Steps:
      1. grep '_streamingContent' app/src/main/java/com/example/llamaapp/ui/chat/ChatViewModel.kt
      2. Assert at least 2 matches (declaration + update)
      3. grep 'GenerationEvent.Token.*_messages\|_messages.*GenerationEvent.Token' app/src/main/java/com/example/llamaapp/ui/chat/ChatViewModel.kt
      4. Assert ZERO matches (messages list not updated on Token events)
      5. grep 'isThinking' app/src/main/java/com/example/llamaapp/ui/chat/ChatViewModel.kt
      6. Assert match found (think state tracking)
    Expected Result: Streaming separation correctly implemented
    Evidence: .sisyphus/evidence/task-17-viewmodel.txt
  ```

  **Commit**: YES (with Tasks 18, 19)
  - Message: `feat(app): add ViewModels for chat, model picker, and settings`
  - Files: `app/src/main/java/com/example/llamaapp/ui/chat/ChatViewModel.kt`

---

- [ ] 18. ModelPickerViewModel.kt

  **What to do**:
  - Create `app/src/main/java/com/example/llamaapp/ui/models/ModelPickerViewModel.kt`:
    - `@HiltViewModel` with injected `ImportModelUseCase`, `ModelRepository`, `WorkManager`
    - `installedModels: StateFlow<List<GgufModel>>` — from `modelRepository.getInstalledModels()`
    - `importState: StateFlow<ImportState>` — sealed: `Idle`, `Importing(progress: Float)`, `Success(model: GgufModel)`, `Error(msg: String)`
    - `downloadState: StateFlow<DownloadState>` — sealed: `Idle`, `Downloading(progress: Int)`, `Success`, `Error(msg: String)`
    - `importModel(uri: Uri)` — calls `importModelUseCase(uri)` with progress callback, updates `importState`
    - `downloadModel(url: String, filename: String)` — enqueues `ModelDownloadWorker` via WorkManager, observes `WorkInfo` live data as StateFlow
    - `deleteModel(model: GgufModel)` — calls `modelRepository.deleteModel(model.id)`, updates list
    - `selectModel(model: GgufModel)` — emits selected model to a `SharedFlow<GgufModel>` for ChatScreen to observe

  **Must NOT do**:
  - Do NOT start downloads on main thread
  - Do NOT hold a reference to `Activity` context in ViewModel

  **Recommended Agent Profile**:
  - **Category**: `unspecified-high`
    - Reason: WorkManager LiveData → StateFlow conversion, sealed import/download states, SAF import with progress
  - **Skills**: none

  **Parallelization**:
  - **Can Run In Parallel**: YES (Wave 5, with Tasks 17, 19)
  - **Blocks**: Task 26 (ModelPickerScreen needs this VM)
  - **Blocked By**: Tasks 15, 16, 13

  **Acceptance Criteria**:
  - [ ] `installedModels` is a StateFlow (from repo Flow)
  - [ ] `importModel()` uses `viewModelScope.launch`
  - [ ] `downloadModel()` enqueues `ModelDownloadWorker` via WorkManager
  - [ ] `deleteModel()` removes from both DB and file system (via repo)

  **QA Scenarios**:
  ```
  Scenario: ViewModel structure check
    Tool: Bash (grep)
    Steps:
      1. grep '@HiltViewModel' app/src/main/java/com/example/llamaapp/ui/models/ModelPickerViewModel.kt
      2. Assert match found
      3. grep 'ModelDownloadWorker' app/src/main/java/com/example/llamaapp/ui/models/ModelPickerViewModel.kt
      4. Assert match found (WorkManager enqueue)
      5. grep 'viewModelScope.launch' app/src/main/java/com/example/llamaapp/ui/models/ModelPickerViewModel.kt
      6. Assert at least 2 matches (import + download)
    Expected Result: ModelPickerViewModel correctly wired
    Evidence: .sisyphus/evidence/task-18-modelpicker-vm.txt
  ```

  **Commit**: YES (with Tasks 17, 19)
  - Message: `feat(app): add ViewModels for chat, model picker, and settings`
  - Files: `app/src/main/java/com/example/llamaapp/ui/models/ModelPickerViewModel.kt`

---

- [ ] 19. SettingsViewModel.kt

  **What to do**:
  - Create `app/src/main/java/com/example/llamaapp/ui/settings/SettingsViewModel.kt`:
    - `@HiltViewModel` with injected `DataStore<Preferences>` and `LlamaEngine`
    - `modelConfig: StateFlow<ModelConfig>` — read from DataStore, initialized with smart defaults:
      - `nThreads = max(2, min(6, Runtime.getRuntime().availableProcessors() - 2))`
      - Other defaults from `ModelConfig` data class
    - Preference keys (DataStore):
      ```kotlin
      object PreferenceKeys {
          val N_THREADS = intPreferencesKey("n_threads")
          val N_CTX = intPreferencesKey("n_ctx")
          val TEMPERATURE = floatPreferencesKey("temperature")
          val TOP_P = floatPreferencesKey("top_p")
          val TOP_K = intPreferencesKey("top_k")
          val REPEAT_PENALTY = floatPreferencesKey("repeat_penalty")
          val ENABLE_THINKING = booleanPreferencesKey("enable_thinking")
          val MAX_NEW_TOKENS = intPreferencesKey("max_new_tokens")
          val SYSTEM_PROMPT = stringPreferencesKey("system_prompt")
      }
      ```
    - `updateThreadCount(n: Int)`, `updateTemperature(f: Float)`, `updateTopP(f: Float)`, etc. — each writes to DataStore
    - `resetToDefaults()` — clears all preferences

  **Must NOT do**:
  - Do NOT use `SharedPreferences` — use DataStore only
  - Do NOT store `ModelConfig` as a JSON blob — flat individual keys only

  **Recommended Agent Profile**:
  - **Category**: `unspecified-high`
    - Reason: DataStore Preferences, StateFlow from Flow mapping, thread count auto-detection
  - **Skills**: none

  **Parallelization**:
  - **Can Run In Parallel**: YES (Wave 5, with Tasks 17, 18)
  - **Blocks**: Task 27 (SettingsScreen needs this VM)
  - **Blocked By**: Tasks 16, 13

  **References**:
  - DataStore Preferences: https://developer.android.com/topic/libraries/architecture/datastore#preferences-datastore
  - Thread count heuristic: `max(2, min(6, nProcessors - 2))` — leaves headroom for Android system processes

  **Acceptance Criteria**:
  - [ ] `nThreads` default uses `availableProcessors()` heuristic
  - [ ] All settings read/written via DataStore (no SharedPreferences)
  - [ ] `modelConfig` is a `StateFlow<ModelConfig>` derived from DataStore Flow
  - [ ] `resetToDefaults()` implemented

  **QA Scenarios**:
  ```
  Scenario: DataStore usage (no SharedPreferences)
    Tool: Bash (grep)
    Steps:
      1. grep 'SharedPreferences' app/src/main/java/com/example/llamaapp/ui/settings/SettingsViewModel.kt
      2. Assert ZERO matches
      3. grep 'DataStore\|dataStore' app/src/main/java/com/example/llamaapp/ui/settings/SettingsViewModel.kt
      4. Assert match found
      5. grep 'availableProcessors' app/src/main/java/com/example/llamaapp/ui/settings/SettingsViewModel.kt
      6. Assert match found (thread count heuristic)
    Expected Result: Settings use DataStore with smart defaults
    Evidence: .sisyphus/evidence/task-19-settings-vm.txt
  ```

  **Commit**: YES (with Tasks 17, 18)
  - Message: `feat(app): add ViewModels for chat, model picker, and settings`
  - Files: `app/src/main/java/com/example/llamaapp/ui/settings/SettingsViewModel.kt`

---

- [ ] 20. ThinkingBlock.kt — animated collapsible UI component

  **What to do**:
  - Create `app/src/main/java/com/example/llamaapp/ui/components/ThinkingBlock.kt`:
    ```kotlin
    @Composable
    fun ThinkingBlock(
        content: String,
        isStreaming: Boolean,       // true = still receiving tokens
        modifier: Modifier = Modifier
    ) {
        var isExpanded by remember { mutableStateOf(true) } // auto-expand while streaming, user can collapse

        // Auto-collapse when streaming finishes
        LaunchedEffect(isStreaming) {
            if (!isStreaming && content.isNotEmpty()) {
                delay(800)
                isExpanded = false
            }
        }

        Surface(
            modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
            shape = RoundedCornerShape(12.dp),
            color = ThinkingBlockBackground, // from Color.kt
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                // Header row
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { isExpanded = !isExpanded },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isStreaming) {
                            // Pulsing brain icon
                            val infiniteTransition = rememberInfiniteTransition("thinking")
                            val alpha by infiniteTransition.animateFloat(
                                initialValue = 0.4f, targetValue = 1f,
                                animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
                                label = "pulse"
                            )
                            Icon(Icons.Default.Psychology, contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = alpha),
                                modifier = Modifier.size(16.dp))
                        } else {
                            Icon(Icons.Default.Psychology, contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                modifier = Modifier.size(16.dp))
                        }
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = if (isStreaming) "Thinking..." else "Thought process",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        modifier = Modifier.size(16.dp)
                    )
                }
                // Collapsible content
                AnimatedVisibility(visible = isExpanded) {
                    Text(
                        text = content,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 18.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        modifier = Modifier.padding(top = 8.dp).fillMaxWidth()
                    )
                }
            }
        }
    }
    ```

  **Must NOT do**:
  - Do NOT render thinking content using `MarkdownRenderer` — plain monospace text only
  - Do NOT hardcode color hex — always use theme tokens (`ThinkingBlockBackground`)

  **Recommended Agent Profile**:
  - **Category**: `visual-engineering`
    - Reason: Animated Compose UI with infinite transitions, AnimatedVisibility, Material3 surface styling
  - **Skills**: [`react-best-practices`]
    - `react-best-practices`: Composable component design principles (single responsibility, prop-driven state) apply directly to Compose

  **Parallelization**:
  - **Can Run In Parallel**: YES (Wave 6, with Tasks 21, 22, 23)
  - **Blocks**: Task 22 (MessageBubble embeds ThinkingBlock)
  - **Blocked By**: Tasks 5 (theme), 17 (ViewModel provides isStreaming/content)

  **Acceptance Criteria**:
  - [ ] `isExpanded` auto-collapses 800ms after `isStreaming` becomes false
  - [ ] Pulsing animation only shown when `isStreaming=true`
  - [ ] `AnimatedVisibility` used for collapse/expand
  - [ ] No hardcoded hex colors — uses `ThinkingBlockBackground` and `MaterialTheme.colorScheme`

  **QA Scenarios**:
  ```
  Scenario: ThinkingBlock component structure
    Tool: Bash (grep)
    Steps:
      1. grep 'AnimatedVisibility' app/src/main/java/com/example/llamaapp/ui/components/ThinkingBlock.kt
      2. Assert match found
      3. grep 'rememberInfiniteTransition' app/src/main/java/com/example/llamaapp/ui/components/ThinkingBlock.kt
      4. Assert match found (pulsing animation)
      5. grep 'ThinkingBlockBackground' app/src/main/java/com/example/llamaapp/ui/components/ThinkingBlock.kt
      6. Assert match found (no hardcoded colors)
      7. grep 'MarkdownRenderer' app/src/main/java/com/example/llamaapp/ui/components/ThinkingBlock.kt
      8. Assert ZERO matches (plain text only)
    Expected Result: ThinkingBlock correctly structured with animation and theming
    Evidence: .sisyphus/evidence/task-20-thinkblock.txt
  ```

  **Commit**: YES (with Tasks 21, 22, 23)
  - Message: `feat(app): add UI components (ThinkingBlock, MarkdownRenderer, MessageBubble, PerformanceHUD)`
  - Files: `app/src/main/java/com/example/llamaapp/ui/components/ThinkingBlock.kt`

---

- [ ] 21. MarkdownRenderer.kt — Markwon AndroidView bridge

  **What to do**:
  - Create `app/src/main/java/com/example/llamaapp/ui/components/MarkdownRenderer.kt`:
    ```kotlin
    @Composable
    fun MarkdownRenderer(
        text: String,
        modifier: Modifier = Modifier
    ) {
        val context = LocalContext.current
        val markwon = remember(context) {
            Markwon.builder(context)
                .usePlugin(TablePlugin.create(context))
                .usePlugin(StrikethroughPlugin.create())
                .usePlugin(SyntaxHighlightPlugin.create(
                    Prism4j(GrammarLocatorDef()),
                    ColorScheme.DEFAULT // dark-compatible color scheme
                ))
                .build()
        }

        // Streaming buffer strategy: debounce updates to avoid re-inflating TextView on every token
        val debouncedText = remember { mutableStateOf(text) }
        LaunchedEffect(text) {
            delay(50) // 50ms debounce — balances visual freshness vs render cost
            debouncedText.value = text
        }

        AndroidView(
            factory = { ctx ->
                TextView(ctx).apply {
                    setTextColor(android.graphics.Color.WHITE)
                    textSize = 15f
                    setLineSpacing(0f, 1.4f)
                    setTextIsSelectable(true)
                    movementMethod = android.text.method.LinkMovementMethod.getInstance()
                }
            },
            update = { tv -> markwon.setMarkdown(tv, debouncedText.value) },
            modifier = modifier.fillMaxWidth()
        )
    }
    ```

  **Must NOT do**:
  - Do NOT call `markwon.setMarkdown()` on every single token emission — debounce by 50ms
  - Do NOT create a new `Markwon` instance on every recomposition — `remember(context)` is essential
  - Do NOT use Compose Text() for Markdown — it can't handle code blocks or tables

  **Recommended Agent Profile**:
  - **Category**: `visual-engineering`
    - Reason: Markwon AndroidView bridge in Compose, debouncing for performance, Prism4j syntax highlighting
  - **Skills**: none

  **Parallelization**:
  - **Can Run In Parallel**: YES (Wave 6, with Tasks 20, 22, 23)
  - **Blocks**: Task 22 (MessageBubble uses MarkdownRenderer for AI content)
  - **Blocked By**: Tasks 5 (theme), 17 (VM provides text)

  **References**:
  - Markwon 4.6.2 API: https://noties.io/Markwon/docs/v4/getting-started.html
  - Prism4j bundler: `GrammarLocatorDef` from `prism4j-bundler-rainbowcsv`
  - AndroidView in Compose: https://developer.android.com/jetpack/compose/migrate/interoperability-apis/views-in-compose

  **Acceptance Criteria**:
  - [ ] `Markwon` built with `TablePlugin`, `StrikethroughPlugin`, `SyntaxHighlightPlugin`
  - [ ] `remember(context)` used for Markwon instance
  - [ ] 50ms debounce applied via `LaunchedEffect + delay`
  - [ ] `setTextIsSelectable(true)` on TextView

  **QA Scenarios**:
  ```
  Scenario: Markwon instance memoization and debounce
    Tool: Bash (grep)
    Steps:
      1. grep 'remember(context)' app/src/main/java/com/example/llamaapp/ui/components/MarkdownRenderer.kt
      2. Assert match found (memoized Markwon)
      3. grep 'delay(50)' app/src/main/java/com/example/llamaapp/ui/components/MarkdownRenderer.kt
      4. Assert match found (debounce)
      5. grep 'SyntaxHighlightPlugin\|TablePlugin\|StrikethroughPlugin' app/src/main/java/com/example/llamaapp/ui/components/MarkdownRenderer.kt
      6. Assert 3 matches (all plugins present)
    Expected Result: Markwon correctly configured with all plugins and performance optimization
    Evidence: .sisyphus/evidence/task-21-markwon.txt
  ```

  **Commit**: YES (with Tasks 20, 22, 23)
  - Message: `feat(app): add UI components (ThinkingBlock, MarkdownRenderer, MessageBubble, PerformanceHUD)`
  - Files: `app/src/main/java/com/example/llamaapp/ui/components/MarkdownRenderer.kt`

---

- [ ] 22. MessageBubble.kt + ChatInputBar.kt

  **What to do**:
  - Create `app/src/main/java/com/example/llamaapp/ui/components/MessageBubble.kt`:
    - `MessageBubble(message: ChatMessage, modifier: Modifier)` — renders a complete chat message
    - User messages: right-aligned bubble with `UserBubbleColor`, plain `Text()`
    - AI messages: left-aligned, no bubble background, contains:
      - If `message.thinkingContent != null`: `ThinkingBlock(content=thinkingContent, isStreaming=false)`
      - `MarkdownRenderer(text=message.content)`
      - Bottom row: tokens/sec badge + copy button (Icon)
    - `StreamingMessageBubble(content: String, thinkContent: String, isThinking: Boolean)` — for in-progress generation:
      - Shows `ThinkingBlock(content=thinkContent, isStreaming=isThinking)`
      - Shows `MarkdownRenderer(text=content)` for streamed content so far
      - Animated typing cursor at end of content
  - Create `app/src/main/java/com/example/llamaapp/ui/components/ChatInputBar.kt`:
    - `ChatInputBar(onSend: (String) -> Unit, onCancel: () -> Unit, isGenerating: Boolean)`
    - Row with `OutlinedTextField` (multiline, max 6 lines, `ImeAction.Default`)
    - When `isGenerating=false`: Send button (Icon: `Icons.Default.Send`, enabled only when text non-empty)
    - When `isGenerating=true`: Cancel/Stop button (Icon: `Icons.Default.Stop`, red tint)
    - Clear input after send
    - Handle soft keyboard `Done` action as send (when not generating)

  **Must NOT do**:
  - Do NOT use `BasicTextField` — use `OutlinedTextField` for proper Material3 styling
  - Do NOT call `MarkdownRenderer` for user messages — plain `Text()` only (user can't write markdown)

  **Recommended Agent Profile**:
  - **Category**: `visual-engineering`
    - Reason: Complex Compose layouts, conditional rendering, animated cursor, Material3 chat bubble shapes
  - **Skills**: [`react-best-practices`]
    - `react-best-practices`: Component composition and conditional rendering patterns

  **Parallelization**:
  - **Can Run In Parallel**: YES (Wave 6, with Tasks 20, 21, 23)
  - **Blocks**: Task 25 (ChatScreen uses these components)
  - **Blocked By**: Tasks 5, 17, 20, 21 (theme + VMs + sub-components)

  **Acceptance Criteria**:
  - [ ] AI messages render `MarkdownRenderer`, user messages render plain `Text()`
  - [ ] `ThinkingBlock` embedded in AI messages when `thinkingContent != null`
  - [ ] `StreamingMessageBubble` composable exists for live generation
  - [ ] Stop button shown when `isGenerating=true`, Send button when false
  - [ ] Input cleared after successful send

  **QA Scenarios**:
  ```
  Scenario: Conditional rendering in MessageBubble
    Tool: Bash (grep)
    Steps:
      1. grep 'MarkdownRenderer' app/src/main/java/com/example/llamaapp/ui/components/MessageBubble.kt
      2. Assert match found (AI messages use Markwon)
      3. grep 'ThinkingBlock' app/src/main/java/com/example/llamaapp/ui/components/MessageBubble.kt
      4. Assert match found (thinking content)
      5. grep 'Icons.Default.Stop\|Icons.Default.Send' app/src/main/java/com/example/llamaapp/ui/components/ChatInputBar.kt
      6. Assert both found (conditional send/stop)
    Expected Result: Message components correctly structured
    Evidence: .sisyphus/evidence/task-22-msgbubble.txt
  ```

  **Commit**: YES (with Tasks 20, 21, 23)
  - Message: `feat(app): add UI components (ThinkingBlock, MarkdownRenderer, MessageBubble, PerformanceHUD)`
  - Files: `app/src/main/java/com/example/llamaapp/ui/components/MessageBubble.kt`, `ChatInputBar.kt`

---

- [ ] 23. PerformanceHUD.kt

  **What to do**:
  - Create `app/src/main/java/com/example/llamaapp/ui/components/PerformanceHUD.kt`:
    ```kotlin
    @Composable
    fun PerformanceHUD(
        stats: InferenceStats,
        isVisible: Boolean,
        modifier: Modifier = Modifier
    ) {
        AnimatedVisibility(
            visible = isVisible,
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it }
        ) {
            Surface(
                modifier = modifier.padding(8.dp),
                shape = RoundedCornerShape(8.dp),
                color = Color.Black.copy(alpha = 0.7f),
                tonalElevation = 0.dp
            ) {
                Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    HudStat(label = "t/s", value = if (stats.tokensPerSecond > 0) "%.1f".format(stats.tokensPerSecond) else "—")
                    HudStat(label = "tokens", value = stats.totalTokens.toString())
                    HudStat(label = "TTFT", value = if (stats.timeToFirstToken > 0) "${stats.timeToFirstToken}ms" else "—")
                    HudStat(label = "prompt", value = stats.promptTokens.toString())
                }
            }
        }
    }

    @Composable
    private fun HudStat(label: String, value: String) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold), color = Color.White)
            Text(label, style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, fontSize = 9.sp), color = Color.White.copy(alpha = 0.6f))
        }
    }
    ```

  **Must NOT do**:
  - Do NOT use a full-screen overlay that blocks input — transparent surface that passes through touches
  - Do NOT show HUD permanently — toggle via long press on stats area or dedicated button in toolbar

  **Recommended Agent Profile**:
  - **Category**: `visual-engineering`
    - Reason: Overlay composable with AnimatedVisibility, monospace typography, semi-transparent surface
  - **Skills**: none

  **Parallelization**:
  - **Can Run In Parallel**: YES (Wave 6, with Tasks 20, 21, 22)
  - **Blocks**: Task 25 (ChatScreen overlays HUD)
  - **Blocked By**: Tasks 5 (theme), 17 (VM provides InferenceStats)

  **Acceptance Criteria**:
  - [ ] Shows t/s, tokens, TTFT, prompt token count
  - [ ] Uses `FontFamily.Monospace` for stat values
  - [ ] `AnimatedVisibility` for show/hide transition
  - [ ] Semi-transparent surface (`alpha=0.7f`), not fully opaque

  **QA Scenarios**:
  ```
  Scenario: HUD structure check
    Tool: Bash (grep)
    Steps:
      1. grep 'FontFamily.Monospace' app/src/main/java/com/example/llamaapp/ui/components/PerformanceHUD.kt
      2. Assert match found
      3. grep 'AnimatedVisibility' app/src/main/java/com/example/llamaapp/ui/components/PerformanceHUD.kt
      4. Assert match found
      5. grep 'tokensPerSecond\|t/s' app/src/main/java/com/example/llamaapp/ui/components/PerformanceHUD.kt
      6. Assert match found (displays t/s)
    Expected Result: HUD correctly shows performance stats
    Evidence: .sisyphus/evidence/task-23-hud.txt
  ```

  **Commit**: YES (with Tasks 20, 21, 22)
  - Message: `feat(app): add UI components (ThinkingBlock, MarkdownRenderer, MessageBubble, PerformanceHUD)`
  - Files: `app/src/main/java/com/example/llamaapp/ui/components/PerformanceHUD.kt`

---

- [ ] 24. ConversationListScreen.kt + ConversationCard.kt

  **What to do**:
  - Create `app/src/main/java/com/example/llamaapp/ui/conversations/ConversationListScreen.kt`:
    - `@HiltViewModel ConversationListViewModel` (can be inline or separate file):
      - `conversations: StateFlow<List<Conversation>>` from `chatRepo.getConversations()`
      - `deleteConversation(id: Long)`
      - `pinConversation(id: Long, isPinned: Boolean)`
    - `ConversationListScreen(onNewChat: () -> Unit, onOpenChat: (Long) -> Unit)`:
      - `Scaffold` with `TopAppBar` ("Conversations" + settings icon navigating to SettingsScreen)
      - `FloatingActionButton` (+ icon) calling `onNewChat()`
      - `LazyColumn` of `ConversationCard` items
      - Empty state: if list is empty, centered text "No conversations yet" + icon
  - Create `app/src/main/java/com/example/llamaapp/ui/conversations/ConversationCard.kt`:
    - `SwipeToDismiss` with red delete background on swipe-left
    - Card shows: conversation title, model name (or "No model"), last message preview (first 60 chars), relative timestamp ("2 min ago")
    - Pin icon (filled if pinned)
    - `combinedClickable`: tap = open, long press = context menu (pin/delete)

  **Must NOT do**:
  - Do NOT implement search/filter (not requested)
  - Do NOT use deprecated `SwipeToDismiss` API — use `SwipeToDismissBox` from Material3

  **Recommended Agent Profile**:
  - **Category**: `visual-engineering`
    - Reason: Compose LazyColumn with swipe-to-dismiss, FAB, TopAppBar, empty state UI
  - **Skills**: [`react-best-practices`]
    - `react-best-practices`: List rendering patterns and component decomposition

  **Parallelization**:
  - **Can Run In Parallel**: YES (Wave 7, with Tasks 25, 26, 27)
  - **Blocks**: Task 28 (AppNavigation wires all screens)
  - **Blocked By**: Tasks 5, 17

  **Acceptance Criteria**:
  - [ ] `SwipeToDismissBox` used (not deprecated `SwipeToDismiss`)
  - [ ] FAB present with new chat action
  - [ ] Empty state shown when conversation list is empty
  - [ ] Pinned conversations displayed (pin icon visible)

  **QA Scenarios**:
  ```
  Scenario: ConversationListScreen structure
    Tool: Bash (grep)
    Steps:
      1. grep 'SwipeToDismissBox\|SwipeToDismiss' app/src/main/java/com/example/llamaapp/ui/conversations/ConversationListScreen.kt
      2. Assert 'SwipeToDismissBox' found, NOT 'SwipeToDismiss(' without 'Box'
      3. grep 'FloatingActionButton' app/src/main/java/com/example/llamaapp/ui/conversations/ConversationListScreen.kt
      4. Assert match found
      5. grep 'isPinned\|pinned' app/src/main/java/com/example/llamaapp/ui/conversations/ConversationCard.kt
      6. Assert match found
    Expected Result: List screen uses correct Material3 APIs
    Evidence: .sisyphus/evidence/task-24-convlist.txt
  ```

  **Commit**: YES (with Tasks 25, 26, 27)
  - Message: `feat(app): add all 4 screens (conversations, chat, model picker, settings)`
  - Files: `app/src/main/java/com/example/llamaapp/ui/conversations/ConversationListScreen.kt`, `ConversationCard.kt`

---

- [ ] 25. ChatScreen.kt — main chat screen

  **What to do**:
  - Create `app/src/main/java/com/example/llamaapp/ui/chat/ChatScreen.kt`:
    - `ChatScreen(conversationId: Long, onNavigateUp: () -> Unit, onSelectModel: () -> Unit)`
    - `collectAsStateWithLifecycle()` for all ViewModel StateFlows
    - Layout: `Scaffold` with `TopAppBar` (back button + conversation title + model chip + HUD toggle)
    - **LazyColumn message list** with `rememberLazyListState()`:
      - Each finalized `ChatMessage` rendered with `MessageBubble(key = message.id)` — stable keys
      - Active streaming: ONE `StreamingMessageBubble(content=streamingContent, thinkContent=streamingThinkContent, isThinking=isThinking)` at bottom
      - NOT added to messages list — rendered conditionally: `if (isGenerating) { item(key="streaming") { StreamingMessageBubble(...) } }`
    - **Auto-scroll guard**: `LaunchedEffect(streamingContent)` — scroll to bottom only if user is at bottom (within 200dp):
      ```kotlin
      val atBottom by remember { derivedStateOf {
          listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ==
          listState.layoutInfo.totalItemsCount - 1
      }}
      LaunchedEffect(streamingContent) { if (atBottom) listState.animateScrollToItem(messages.size) }
      ```
    - `PerformanceHUD` overlaid as `Box` over LazyColumn, positioned top-end, toggled by toolbar button
    - `ChatInputBar` at bottom
    - Empty state: if no messages and no model loaded, show "Select a model to start" button

  **Must NOT do**:
  - Do NOT use `key(message.id)` incorrectly — use `LazyColumn { items(messages, key = { it.id }) }`
  - Do NOT auto-scroll if user has scrolled up (respect the `atBottom` guard)
  - Do NOT put `StreamingMessageBubble` in the messages list (separate `item(key="streaming")` block)

  **Recommended Agent Profile**:
  - **Category**: `visual-engineering`
    - Reason: LazyColumn with stable keys, auto-scroll guard with derivedStateOf, overlay Box composition, lifecycle-aware state collection
  - **Skills**: [`react-best-practices`]
    - `react-best-practices`: LazyColumn is equivalent to React's virtualized list — stable key concepts apply

  **Parallelization**:
  - **Can Run In Parallel**: YES (Wave 7, with Tasks 24, 26, 27)
  - **Blocks**: Task 28 (AppNavigation)
  - **Blocked By**: Tasks 20, 21, 22, 23, 17

  **References**:
  - `derivedStateOf` for scroll guard: https://developer.android.com/jetpack/compose/side-effects#derivedstateof
  - Stable keys in LazyColumn: https://developer.android.com/jetpack/compose/lists#item-keys
  - `collectAsStateWithLifecycle`: https://developer.android.com/kotlin/flow/stateflow-and-sharedflow#compose

  **Acceptance Criteria**:
  - [ ] `items(messages, key = { it.id })` — stable keys on LazyColumn
  - [ ] `StreamingMessageBubble` rendered as separate `item(key="streaming")` (not in messages list)
  - [ ] `derivedStateOf { atBottom }` for scroll guard
  - [ ] `PerformanceHUD` overlaid as Box at top-end
  - [ ] `collectAsStateWithLifecycle()` used (not `collectAsState()`)

  **QA Scenarios**:
  ```
  Scenario: ChatScreen critical Compose patterns
    Tool: Bash (grep)
    Steps:
      1. grep 'key = { it.id }' app/src/main/java/com/example/llamaapp/ui/chat/ChatScreen.kt
      2. Assert match found (stable keys)
      3. grep 'derivedStateOf' app/src/main/java/com/example/llamaapp/ui/chat/ChatScreen.kt
      4. Assert match found (scroll guard)
      5. grep 'key=\"streaming\"\|key = \"streaming\"' app/src/main/java/com/example/llamaapp/ui/chat/ChatScreen.kt
      6. Assert match found (streaming item isolation)
      7. grep 'collectAsStateWithLifecycle' app/src/main/java/com/example/llamaapp/ui/chat/ChatScreen.kt
      8. Assert at least 3 matches (multiple StateFlows collected)
    Expected Result: ChatScreen uses all correct Compose patterns for streaming
    Evidence: .sisyphus/evidence/task-25-chatscreen.txt
  ```

  **Commit**: YES (with Tasks 24, 26, 27)
  - Message: `feat(app): add all 4 screens (conversations, chat, model picker, settings)`
  - Files: `app/src/main/java/com/example/llamaapp/ui/chat/ChatScreen.kt`

---

- [ ] 26. ModelPickerScreen.kt

  **What to do**:
  - Create `app/src/main/java/com/example/llamaapp/ui/models/ModelPickerScreen.kt`:
    - `ModelPickerScreen(onModelSelected: (GgufModel) -> Unit, onNavigateUp: () -> Unit)`
    - Collects `installedModels`, `importState`, `downloadState` from `ModelPickerViewModel`
    - Layout: `Scaffold` + `TopAppBar` ("Models" + close button)
    - **Installed models section**: `LazyColumn` of model cards showing name, quantization badge, file size, last used
      - Each card: tap = `onModelSelected(model)` (returns to chat and loads model)
      - Swipe-to-delete with confirmation `AlertDialog`
    - **Import section** (bottom Sheet or section below list):
      - "Import from device" button — launches SAF picker for `.gguf` files:
        ```kotlin
        val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let { viewModel.importModel(it) }
        }
        Button(onClick = { launcher.launch(arrayOf("application/octet-stream", "*/*")) }) {
            Text("Import GGUF file")
        }
        ```
      - Import progress: `LinearProgressIndicator` shown when `importState is Importing`
    - **Download section** (collapsible):
      - URL input field + filename field + "Download" button
      - Download progress: `LinearProgressIndicator` with percentage

  **Must NOT do**:
  - Do NOT filter SAF to `.gguf` MIME type only — use `arrayOf("application/octet-stream", "*/*")` (GGUF has no standard MIME type)
  - Do NOT load the model directly in this screen — emit via `onModelSelected` callback

  **Recommended Agent Profile**:
  - **Category**: `visual-engineering`
    - Reason: SAF launcher, progress indicators, model metadata cards, AlertDialog confirmation
  - **Skills**: none

  **Parallelization**:
  - **Can Run In Parallel**: YES (Wave 7, with Tasks 24, 25, 27)
  - **Blocks**: Task 28 (AppNavigation)
  - **Blocked By**: Tasks 18, 5

  **Acceptance Criteria**:
  - [ ] SAF launcher uses `ActivityResultContracts.OpenDocument()`
  - [ ] MIME type array includes `"*/*"` (not just gguf-specific)
  - [ ] `LinearProgressIndicator` shown during import and download
  - [ ] Delete confirmation dialog before deletion

  **QA Scenarios**:
  ```
  Scenario: SAF launcher and model list
    Tool: Bash (grep)
    Steps:
      1. grep 'OpenDocument\|rememberLauncherForActivityResult' app/src/main/java/com/example/llamaapp/ui/models/ModelPickerScreen.kt
      2. Assert match found (SAF launcher)
      3. grep 'LinearProgressIndicator' app/src/main/java/com/example/llamaapp/ui/models/ModelPickerScreen.kt
      4. Assert match found (progress indicator)
      5. grep 'AlertDialog' app/src/main/java/com/example/llamaapp/ui/models/ModelPickerScreen.kt
      6. Assert match found (delete confirmation)
    Expected Result: ModelPickerScreen has import, download, and delete with correct patterns
    Evidence: .sisyphus/evidence/task-26-modelpicker.txt
  ```

  **Commit**: YES (with Tasks 24, 25, 27)
  - Message: `feat(app): add all 4 screens (conversations, chat, model picker, settings)`
  - Files: `app/src/main/java/com/example/llamaapp/ui/models/ModelPickerScreen.kt`

---

- [ ] 27. SettingsScreen.kt

  **What to do**:
  - Create `app/src/main/java/com/example/llamaapp/ui/settings/SettingsScreen.kt`:
    - `SettingsScreen(onNavigateUp: () -> Unit)`
    - Collects `modelConfig` from `SettingsViewModel`
    - Layout: `Scaffold` + `TopAppBar` ("Settings" + back button)
    - **Performance section** (expandable `ListItem`):
      - Thread count slider: `Slider(value=nThreads, valueRange=1f..8f, steps=6)` with label "Threads: N"
      - Context length: `DropdownMenu` selector — options: 1024, 2048, 4096, 8192, 16384 tokens
      - Batch size: `Slider(value=nBatch, valueRange=64f..1024f, steps=15)`
    - **Generation section**:
      - Temperature: `Slider(valueRange=0f..2f)` with 2-decimal display
      - Top-P: `Slider(valueRange=0f..1f)`
      - Top-K: `Slider(valueRange=1f..100f, steps=98)` (integer steps)
      - Repeat penalty: `Slider(valueRange=1f..1.5f)`
      - Max new tokens: `Slider(valueRange=128f..4096f, steps=30)`
    - **Thinking section**:
      - Enable thinking: `Switch` toggle linked to `enableThinking`
      - (Disabled when enableThinking=false): thinking temperature and top_p sliders
    - **System prompt section**:
      - `OutlinedTextField` multiline, placeholder "You are a helpful assistant."
    - **Reset button**: `OutlinedButton` — calls `viewModel.resetToDefaults()`

  **Must NOT do**:
  - Do NOT use continuous `onValueChange` to write to DataStore on every slider drag — write only on `onValueChangeFinished`
  - Do NOT add features not in ModelConfig (no RAG, no context compression UI)

  **Recommended Agent Profile**:
  - **Category**: `visual-engineering`
    - Reason: Multiple sliders, dropdown selector, switch toggles, nested sections with conditional enabling
  - **Skills**: none

  **Parallelization**:
  - **Can Run In Parallel**: YES (Wave 7, with Tasks 24, 25, 26)
  - **Blocks**: Task 28 (AppNavigation)
  - **Blocked By**: Tasks 19, 5

  **Acceptance Criteria**:
  - [ ] Thread count, temperature, top-p, top-k, repeat penalty sliders present
  - [ ] `onValueChangeFinished` used (not `onValueChange`) for DataStore writes
  - [ ] Enable thinking `Switch` present and toggleable
  - [ ] `resetToDefaults()` button present
  - [ ] System prompt `OutlinedTextField` present

  **QA Scenarios**:
  ```
  Scenario: Slider write strategy (onValueChangeFinished)
    Tool: Bash (grep)
    Steps:
      1. grep 'onValueChangeFinished' app/src/main/java/com/example/llamaapp/ui/settings/SettingsScreen.kt
      2. Assert at least 5 matches (one per slider)
      3. grep 'enableThinking\|Enable thinking' app/src/main/java/com/example/llamaapp/ui/settings/SettingsScreen.kt
      4. Assert match found (think toggle)
      5. grep 'resetToDefaults\|Reset' app/src/main/java/com/example/llamaapp/ui/settings/SettingsScreen.kt
      6. Assert match found (reset button)
    Expected Result: Settings screen correctly structured with efficient DataStore writes
    Evidence: .sisyphus/evidence/task-27-settings.txt
  ```

  **Commit**: YES (with Tasks 24, 25, 26)
  - Message: `feat(app): add all 4 screens (conversations, chat, model picker, settings)`
  - Files: `app/src/main/java/com/example/llamaapp/ui/settings/SettingsScreen.kt`

---

- [ ] 28. AppNavigation.kt

  **What to do**:
  - Create `app/src/main/java/com/example/llamaapp/navigation/AppNavigation.kt`:
    ```kotlin
    // Sealed route definitions
    sealed class Screen(val route: String) {
        object ConversationList : Screen("conversations")
        object Chat : Screen("chat/{conversationId}") {
            fun createRoute(id: Long) = "chat/$id"
        }
        object ModelPicker : Screen("models")
        object Settings : Screen("settings")
    }

    @Composable
    fun AppNavigation() {
        val navController = rememberNavController()
        NavHost(navController = navController, startDestination = Screen.ConversationList.route) {
            composable(Screen.ConversationList.route) {
                ConversationListScreen(
                    onNewChat = {
                        // Create new conversation in VM, then navigate
                        navController.navigate(Screen.Chat.createRoute(-1L))
                    },
                    onOpenChat = { id -> navController.navigate(Screen.Chat.createRoute(id)) }
                )
            }
            composable(Screen.Chat.route, arguments = listOf(navArgument("conversationId") { type = NavType.LongType })) { backStack ->
                val conversationId = backStack.arguments!!.getLong("conversationId")
                ChatScreen(
                    conversationId = conversationId,
                    onNavigateUp = { navController.popBackStack() },
                    onSelectModel = { navController.navigate(Screen.ModelPicker.route) }
                )
            }
            composable(Screen.ModelPicker.route) {
                ModelPickerScreen(
                    onModelSelected = { navController.popBackStack() },
                    onNavigateUp = { navController.popBackStack() }
                )
            }
            composable(Screen.Settings.route) {
                SettingsScreen(onNavigateUp = { navController.popBackStack() })
            }
        }
    }
    ```

  **Must NOT do**:
  - Do NOT use string literals for route names in composable() calls — always use `Screen.X.route`
  - Do NOT pass ViewModel instances through nav arguments — Hilt handles injection at each destination

  **Recommended Agent Profile**:
  - **Category**: `quick`
    - Reason: Straightforward NavHost wiring — no complex logic
  - **Skills**: none

  **Parallelization**:
  - **Can Run In Parallel**: YES (Wave 8, with Task 29)
  - **Blocks**: Task 29 (MainActivity sets content to AppNavigation)
  - **Blocked By**: Tasks 24, 25, 26, 27 (all screens must exist)

  **References**:
  - Navigation Compose typed routes: https://developer.android.com/jetpack/compose/navigation
  - `navArgument` with Long type: `NavType.LongType`

  **Acceptance Criteria**:
  - [ ] All 4 screens registered in NavHost
  - [ ] `conversationId: Long` passed as nav argument to ChatScreen
  - [ ] `Screen` sealed class used (no raw strings in `composable()` calls)
  - [ ] `startDestination = Screen.ConversationList.route`

  **QA Scenarios**:
  ```
  Scenario: All routes registered
    Tool: Bash (grep)
    Steps:
      1. grep 'composable(' app/src/main/java/com/example/llamaapp/navigation/AppNavigation.kt
      2. Assert 4 matches (ConversationList, Chat, ModelPicker, Settings)
      3. grep 'NavType.LongType' app/src/main/java/com/example/llamaapp/navigation/AppNavigation.kt
      4. Assert match found (conversationId typed argument)
      5. grep '"conversations"\|"chat"\|"models"\|"settings"' app/src/main/java/com/example/llamaapp/navigation/AppNavigation.kt
      6. Assert only in Screen sealed class, not in composable() calls
    Expected Result: Navigation correctly defined with typed routes
    Evidence: .sisyphus/evidence/task-28-navigation.txt
  ```

  **Commit**: YES (with Task 29)
  - Message: `feat(app): add navigation and MainActivity — app is wired end-to-end`
  - Files: `app/src/main/java/com/example/llamaapp/navigation/AppNavigation.kt`

---

- [ ] 29. MainActivity.kt — app entry point

  **What to do**:
  - Create `app/src/main/java/com/example/llamaapp/MainActivity.kt`:
    ```kotlin
    @AndroidEntryPoint
    class MainActivity : ComponentActivity() {
        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            // Sustained performance mode — prevents thermal throttling during long inference
            if (Build.VERSION.SDK_INT >= 24) {
                window.setSustainedPerformanceMode(true)
            }
            // Edge-to-edge display
            enableEdgeToEdge()
            // Request POST_NOTIFICATIONS at runtime (Android 13+)
            if (Build.VERSION.SDK_INT >= 33) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
            }
            setContent {
                LlamaAppTheme {
                    AppNavigation()
                }
            }
        }
    }
    ```
  - Ensure `android:name=".MainActivity"` in manifest matches class (set in T3)

  **Must NOT do**:
  - Do NOT use `AppCompatActivity` — use `ComponentActivity` for Compose
  - Do NOT call `setSustainedPerformanceMode` without the API 24 version check

  **Recommended Agent Profile**:
  - **Category**: `quick`
    - Reason: Simple entry point with 3 API calls
  - **Skills**: none

  **Parallelization**:
  - **Can Run In Parallel**: YES (Wave 8, with Task 28)
  - **Blocks**: F1–F4 (final verification)
  - **Blocked By**: Tasks 28 (AppNavigation), 14 (InferenceService binding)

  **Acceptance Criteria**:
  - [ ] `@AndroidEntryPoint` present
  - [ ] `setSustainedPerformanceMode(true)` behind `SDK_INT >= 24` check
  - [ ] `enableEdgeToEdge()` called
  - [ ] `LlamaAppTheme` wrapping `AppNavigation()`
  - [ ] POST_NOTIFICATIONS runtime permission request for API 33+

  **QA Scenarios**:
  ```
  Scenario: MainActivity critical flags
    Tool: Bash (grep)
    Steps:
      1. grep '@AndroidEntryPoint' app/src/main/java/com/example/llamaapp/MainActivity.kt
      2. Assert match found
      3. grep 'setSustainedPerformanceMode' app/src/main/java/com/example/llamaapp/MainActivity.kt
      4. Assert match found
      5. grep 'SDK_INT >= 24' app/src/main/java/com/example/llamaapp/MainActivity.kt
      6. Assert match found (version guard)
      7. grep 'enableEdgeToEdge' app/src/main/java/com/example/llamaapp/MainActivity.kt
      8. Assert match found
    Expected Result: MainActivity correctly configured for performance and runtime permissions
    Evidence: .sisyphus/evidence/task-29-mainactivity.txt
  ```

  **Commit**: YES (with Task 28)
  - Message: `feat(app): add navigation and MainActivity — app is wired end-to-end`
  - Files: `app/src/main/java/com/example/llamaapp/MainActivity.kt`

---

- [ ] 30. GitHub Actions CI/CD workflow

  **What to do**:
  - Create directory `.github/workflows/` and file `.github/workflows/build.yml`:
    ```yaml
    name: Build Debug APK

    on:
      push:
        branches: [main, master, dev]
        tags:
          - 'v*.*.*'
      pull_request:
        branches: [main, master]

    jobs:
      build:
        runs-on: ubuntu-latest

        steps:
          - name: Checkout repository (with llama.cpp submodule)
            uses: actions/checkout@v4
            with:
              submodules: recursive
              fetch-depth: 0

          - name: Set up JDK 17
            uses: actions/setup-java@v4
            with:
              java-version: '17'
              distribution: 'temurin'
              cache: 'gradle'

          - name: Cache NDK C++ build outputs (avoids 20-40 min full recompile)
            uses: actions/cache@v4
            with:
              path: |
                lib/.cxx
                app/.cxx
              key: ndk-build-${{ runner.os }}-${{ hashFiles('lib/src/main/cpp/CMakeLists.txt', 'llama.cpp/CMakeLists.txt') }}
              restore-keys: |
                ndk-build-${{ runner.os }}-

          - name: Install NDK 29.0.13113456
            run: |
              sdkmanager --install "ndk;29.0.13113456"
              echo "ANDROID_NDK_ROOT=$ANDROID_HOME/ndk/29.0.13113456" >> $GITHUB_ENV

          - name: Make gradlew executable
            run: chmod +x ./gradlew

          - name: Build Debug APK
            run: ./gradlew assembleDebug --no-daemon --stacktrace

          - name: Upload APK artifact (every push)
            uses: actions/upload-artifact@v4
            with:
              name: debug-apk-${{ github.sha }}
              path: app/build/outputs/apk/debug/*.apk
              retention-days: 30
              if-no-files-found: error

          - name: Create GitHub Release (version tags only)
            if: startsWith(github.ref, 'refs/tags/')
            uses: softprops/action-gh-release@v2
            with:
              files: app/build/outputs/apk/debug/*.apk
              name: MobileAgentAI ${{ github.ref_name }}
              body: |
                ## MobileAgentAI ${{ github.ref_name }}
                Debug APK — runs GGUF models locally on Android (Samsung Galaxy S24 optimized).

                ### Install
                1. Enable **Install unknown apps** for your browser/Files app
                2. Download the `.apk` file below
                3. Tap to install

                ### First use
                - Tap the model icon → import a `.gguf` file (Qwen3 0.6B–4B recommended)
                - Start chatting
              draft: false
              prerelease: ${{ contains(github.ref_name, '-') }}
            env:
              GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
    ```
  - The `lib/.cxx` + `app/.cxx` cache is the most critical optimization — it stores all compiled llama.cpp `.o` files. Without it, each CI run recompiles 600+ C++ files (~20–40 min). With it, incremental builds take 2–5 min.
  - Cache key hashes BOTH `CMakeLists.txt` files — any NDK code change invalidates and forces a full rebuild.
  - `submodules: recursive` is MANDATORY — forgetting it causes the build to fail with `llama.h not found`.
  - `softprops/action-gh-release@v2` uses `GITHUB_TOKEN` (auto-provided by GitHub) — zero extra secrets needed.
  - `prerelease: ${{ contains(github.ref_name, '-') }}` auto-marks tags like `v1.0.0-beta` or `v2.0.0-rc1` as pre-releases.

  **Must NOT do**:
  - Do NOT add a separate `actions/cache` step for `~/.gradle/caches` — `setup-java` with `cache: 'gradle'` handles this automatically; double-caching causes conflicts
  - Do NOT use `./gradlew build` — runs lint + tests + release builds; use `assembleDebug` only
  - Do NOT add `GGML_VULKAN=ON` anywhere in the workflow
  - Do NOT store a keystore or signing secrets — debug APK uses Gradle’s auto-generated debug keystore, no secrets needed
  - Do NOT use `actions/checkout@v3` — use `@v4` (significantly faster shallow clone support)
  - Do NOT set `fetch-depth: 1` — `fetch-depth: 0` is needed so `github.ref_name` resolves correctly for release creation

  **Recommended Agent Profile**:
  - **Category**: `quick`
    - Reason: Single YAML file, well-known GitHub Actions syntax, no Android runtime knowledge required
  - **Skills**: none
  - **Skills Evaluated but Omitted**:
    - `docker-expert`: Not needed — ubuntu-latest runner has Android SDK pre-installed

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 1 (alongside Tasks 1–5) — creates only `.github/workflows/build.yml`, zero Android code dependencies
  - **Blocks**: Nothing — workflow evaluated by GitHub CI, not referenced by any Kotlin/CMake file
  - **Blocked By**: Nothing — can start immediately

  **References**:

  **Pattern References**:
  - `.gitmodules` (T1) — confirms submodule path is `llama.cpp`; must match `submodules: recursive` checkout
  - `gradle/libs.versions.toml` (T1) — `ndk-version = "29.0.13113456"` must match the `sdkmanager --install` version in the workflow
  - `lib/src/main/cpp/CMakeLists.txt` (T6) — hashed for the NDK build cache key

  **External References**:
  - `actions/checkout@v4`: https://github.com/actions/checkout
  - `actions/setup-java@v4` with Gradle cache: https://github.com/actions/setup-java#caching-packages-dependencies
  - `actions/cache@v4`: https://github.com/actions/cache
  - `actions/upload-artifact@v4`: https://github.com/actions/upload-artifact
  - `softprops/action-gh-release@v2`: https://github.com/softprops/action-gh-release
  - GitHub Actions Android guide: https://docs.github.com/en/actions/use-cases-and-examples/building-and-testing/building-and-testing-android

  **WHY Each Reference Matters**:
  - `setup-java` with `cache: 'gradle'` caches `~/.gradle/caches` AND `~/.gradle/wrapper` automatically — no duplicate cache step needed
  - NDK cache path `lib/.cxx` is the AGP-managed CMake build output directory (standard since AGP 4.1+); hashing both CMakeLists files ensures full invalidation on any C++ change
  - `softprops/action-gh-release@v2` auto-reads `GITHUB_TOKEN` from env — default `contents: write` permission on the repo is sufficient, no `permissions: write-all` needed

  **Acceptance Criteria**:

  **QA Scenarios (MANDATORY)**:

  ```
  Scenario: Workflow file exists and is valid YAML with all critical fields
    Tool: Bash
    Preconditions: Task 30 complete, .github/workflows/build.yml created
    Steps:
      1. python3 -c "import yaml; yaml.safe_load(open('.github/workflows/build.yml')); print('VALID YAML')"
      2. Assert output is 'VALID YAML' (no ParseError thrown)
      3. grep 'submodules: recursive' .github/workflows/build.yml
      4. Assert match found — submodule checkout enabled
      5. grep 'ndk;29.0.13113456' .github/workflows/build.yml
      6. Assert match found — correct NDK version
      7. grep 'lib/.cxx' .github/workflows/build.yml
      8. Assert match found — NDK build cache present
      9. grep 'upload-artifact' .github/workflows/build.yml
      10. Assert match found — APK artifact upload step present
      11. grep 'action-gh-release' .github/workflows/build.yml
      12. Assert match found — release creation step present
      13. grep "startsWith(github.ref, 'refs/tags/')" .github/workflows/build.yml
      14. Assert match found — release only fires on tags
      15. grep 'assembleDebug' .github/workflows/build.yml
      16. Assert match found (NOT 'build' or 'assembleRelease')
    Expected Result: VALID YAML confirmed, all 7 critical fields present
    Failure Indicators: yaml.safe_load throws ParseError, or any grep returns no match
    Evidence: .sisyphus/evidence/task-30-workflow-validation.txt

  Scenario: Workflow trigger configuration is correct
    Tool: Bash
    Preconditions: .github/workflows/build.yml exists
    Steps:
      1. python3 -c "import yaml; w=yaml.safe_load(open('.github/workflows/build.yml')); on=w['on']; print('branches:', on['push']['branches']); print('tags:', on['push']['tags']); print('pr_branches:', on['pull_request']['branches'])"
      2. Assert output line 1: branches: ['main', 'master', 'dev']
      3. Assert output line 2: tags: ['v*.*.*']
      4. Assert output line 3: pr_branches: ['main', 'master']
    Expected Result: All 3 trigger types with correct patterns
    Failure Indicators: KeyError, wrong branch/tag values
    Evidence: .sisyphus/evidence/task-30-workflow-triggers.txt
  ```

  **Evidence to Capture**:
  - [ ] `.sisyphus/evidence/task-30-workflow-validation.txt` — YAML validation + all grep results
  - [ ] `.sisyphus/evidence/task-30-workflow-triggers.txt` — Python trigger structure verification

  **Commit**: YES (standalone)
  - Message: `ci: add GitHub Actions workflow for debug APK build and tag releases`
  - Files: `.github/workflows/build.yml`
  - Pre-commit: none (validation done in QA scenarios above)

---


## Final Verification Wave (after ALL Tasks 1–30)

> 4 review agents run in PARALLEL. ALL must APPROVE. Rejection → fix → re-run.

- [ ] F1. Plan Compliance Audit — `oracle`

  Read every "Must Have" from Work Objectives. For each:
  - Verify the implementation exists: search codebase via `grep` or `read`
  - If a Must Have is a CMake flag: `grep` the flag in `CMakeLists.txt` AND `lib/build.gradle.kts`
  - If a Must Have is a Kotlin pattern: `grep` across all `.kt` files in relevant directory

  Read every "Must NOT Have" from Work Objectives. For each:
  - Search codebase for the forbidden pattern — if found, report file:line and REJECT

  Verify evidence files exist in `.sisyphus/evidence/` for every task (T1–T29).

  Compare deliverables list against actual files created.

  Output: `Must Have [N/N] | Must NOT Have [N/N] | Tasks [N/N] | Evidence [N/N] | VERDICT: APPROVE/REJECT`

- [ ] F2. Code Quality + Build Verification — `unspecified-high`

  Run these commands from `/home/0xzknw/Desktop/MobileAgentAI/`:
  ```bash
  ./gradlew :lib:assembleRelease 2>&1 | tail -20
  ./gradlew :app:assembleDebug 2>&1 | tail -20
  ```

  If build fails: read the full error, identify the failing file, attempt to fix, re-run.

  Review all changed `.kt` files for:
  - `as Any` or `as Any?` unsafe casts
  - `@Suppress("UNCHECKED_CAST")` without justification
  - Empty `catch {}` blocks
  - `Log.d` / `println` statements in production paths
  - Commented-out code blocks (3+ lines)
  - Unused imports (`import` lines for symbols not used in the file)
  - AI slop: overly generic variable names (`data`, `result`, `item`, `temp`, `value` without context)

  Output: `Build :lib [PASS/FAIL] | Build :app [PASS/FAIL] | Code issues [N] | VERDICT: APPROVE/REJECT`

- [ ] F3. Real QA — `unspecified-high`

  Execute EVERY QA scenario from EVERY task (T1–T29). For each scenario:
  1. Follow exact steps listed in the task's QA Scenarios section
  2. Run the grep/check command
  3. Assert the expected result
  4. Save evidence to `.sisyphus/evidence/` with the exact filename specified

  Cross-task integration checks:
  - Verify `LlamaEngineImpl` is correctly registered in `AppModule` (grep `AppModule.kt` for `LlamaEngineImpl`)
  - Verify `ThinkingBlock` is used in `MessageBubble` (grep `MessageBubble.kt` for `ThinkingBlock`)
  - Verify `MarkdownRenderer` is used in `MessageBubble` (grep `MessageBubble.kt` for `MarkdownRenderer`)
  - Verify `ChatViewModel` uses `streamingContent` StateFlow separate from `messages` (both present in same file)
  - Verify `InferenceService` is declared in `AndroidManifest.xml` with correct service type

  Count and report: `Scenarios [N/N pass] | Integration [5/5] | VERDICT: APPROVE/REJECT`

- [ ] F4. Scope Fidelity Check — `deep`

  For each of the 29 tasks:
  1. Read the task's "What to do" specification
  2. Read the actual files created (use `read` tool)
  3. Verify everything specified was implemented — flag missing items
  4. Verify nothing BEYOND the spec was added — flag scope creep
  5. Check "Must NOT do" compliance: search for each forbidden pattern

  Specific checks:
  - T7 (`ai_chat.cpp`): `use_mlock=false` present, `n_gpu_layers=0` present, `151668` present
  - T8 (`LlamaEngineImpl.kt`): `@FastNative` on all 9 external funs, `limitedParallelism(1)` present, no `callbackFlow`
  - T3 (manifest): `extractNativeLibs="true"` present, NOT `false`
  - T2 (lib gradle): `GGML_VULKAN=OFF` present, `GGML_NATIVE` NOT `=ON`
  - T17 (ChatViewModel): `_messages` NOT updated in `Token` event handler

  Output: `Tasks [N/29 compliant] | Must-NOT violations [N] | Scope creep [N files] | VERDICT: APPROVE/REJECT`

---

## Commit Strategy

| Wave | Commit Message | Files |
|------|---------------|-------|
| 1 | `chore(gradle): init project with version catalog and llama.cpp submodule` | settings.gradle.kts, build.gradle.kts, gradle.properties, .gitmodules, libs.versions.toml, lib/build.gradle.kts, app/build.gradle.kts, manifests |
| CI | `ci: add GitHub Actions workflow for debug APK build and tag releases` | .github/workflows/build.yml |
| 2+3a | `feat(lib): add engine interface, type definitions, and Material3 theme` | LlamaEngine.kt, Entities.kt, Theme/Color/Typography.kt |
| 2+3b | `feat(lib): add NDK engine with CMake, JNI C++ wrapper, and Kotlin bridge` | CMakeLists.txt, logging.h, ai_chat.cpp, LlamaEngineImpl.kt, Qwen3ChatTemplate.kt, ThinkTagParser.kt |
| 2+3c | `feat(app): add data layer (Room DB, storage manager, repositories, Hilt DI)` | All files in data/, storage/, di/ |
| 4 | `feat(app): add inference service, download worker, and use cases` | InferenceService.kt, ModelDownloadWorker.kt, all use cases |
| 5 | `feat(app): add ViewModels for chat, model picker, and settings` | ChatViewModel.kt, ModelPickerViewModel.kt, SettingsViewModel.kt |
| 6 | `feat(app): add UI components (ThinkingBlock, MarkdownRenderer, MessageBubble, PerformanceHUD)` | All files in ui/components/ |
| 7 | `feat(app): add all 4 screens (conversations, chat, model picker, settings)` | All screen files |
| 8 | `feat(app): add navigation and MainActivity — app is wired end-to-end` | AppNavigation.kt, MainActivity.kt |

---

## Success Criteria

### Verification Commands
```bash
# Build both modules
./gradlew :lib:assembleRelease --info 2>&1 | grep -E 'BUILD|FAILED|arm64'
./gradlew :app:assembleDebug 2>&1 | grep -E 'BUILD|FAILED'

# Verify critical flags
grep 'extractNativeLibs' app/src/main/AndroidManifest.xml  # Expected: true
grep 'GGML_BACKEND_DL=ON' lib/build.gradle.kts              # Expected: match
grep 'GGML_VULKAN=OFF' lib/build.gradle.kts                 # Expected: match
grep '@FastNative' lib/src/main/java/com/example/llama/internal/LlamaEngineImpl.kt | wc -l  # Expected: >= 7
grep 'limitedParallelism(1)' lib/src/main/java/com/example/llama/internal/LlamaEngineImpl.kt  # Expected: match
grep 'callbackFlow' lib/src/main/java/com/example/llama/internal/LlamaEngineImpl.kt  # Expected: no match
grep '151668' lib/src/main/cpp/ai_chat.cpp                  # Expected: match
grep 'use_mlock' lib/src/main/cpp/ai_chat.cpp               # Expected: false
grep 'n_gpu_layers' lib/src/main/cpp/ai_chat.cpp            # Expected: 0
# Verify workflow file
python3 -c "import yaml; yaml.safe_load(open('.github/workflows/build.yml')); print('VALID YAML')"  # Expected: VALID YAML
grep 'submodules: recursive' .github/workflows/build.yml  # Expected: match
grep 'ndk;29.0.13113456' .github/workflows/build.yml        # Expected: match
grep 'assembleDebug' .github/workflows/build.yml            # Expected: match
```

### Final Checklist
- [ ] `./gradlew :lib:assembleRelease` — BUILD SUCCESSFUL
- [ ] `./gradlew :app:assembleDebug` — BUILD SUCCESSFUL
- [ ] `android:extractNativeLibs="true"` in manifest
- [ ] `GGML_BACKEND_DL=ON`, `GGML_CPU_ALL_VARIANTS=ON` in lib build config
- [ ] `GGML_VULKAN=OFF`, `GGML_NATIVE` not set to ON anywhere
- [ ] `@FastNative` on all JNI external funs
- [ ] `limitedParallelism(1)` for JNI dispatcher
- [ ] `flow { while { nativeNextToken() } }` (NOT callbackFlow)
- [ ] Qwen3 think token 151668 handled in C++
- [ ] `use_mlock=false`, `n_gpu_layers=0` in C++
- [ ] `_streamingContent` separate from `_messages` in ChatViewModel
- [ ] `ThinkingBlock` with pulsing animation + auto-collapse
- [ ] `MarkdownRenderer` with Markwon + Prism4j + 50ms debounce
- [ ] SAF model import copies file (never passes Uri to JNI)
- [ ] WorkManager download worker with Range header resume
- [ ] Room schema has `thinkingContent` column on messages
- [ ] `PerformanceHUD` shows t/s, TTFT, total tokens, prompt tokens
- [ ] `.github/workflows/build.yml` exists and is valid YAML
- [ ] Workflow has `submodules: recursive`, NDK 29 install, `lib/.cxx` cache, `assembleDebug`, `upload-artifact`, and `action-gh-release` steps

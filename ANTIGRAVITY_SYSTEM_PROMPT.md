# Android Project Setup & Workflow Guide

## Project Overview
- **Name:** HukmAi — an Islamic jurisprudence (Fiqh) encyclopedia Android app with Gemini AI chat assistant
- **Language:** 100% Kotlin
- **UI:** Jetpack Compose + Material Design 3 (RTL Arabic layout)
- **Architecture:** MVVM (ViewModel + Repository + DAO pattern)
- **Database:** Room with a pre-packaged SQLite database (`feqhia.db`, ~89 MB in `app/src/main/assets/databases/`)
- **AI:** Gemini REST API via Retrofit (multi-stage routing pipeline)
- **Build system:** Gradle with Kotlin DSL + version catalog (`gradle/libs.versions.toml`)
- **Testing:** JUnit + Robolectric + Roborazzi (Compose screenshot tests)

## Tech Stack & Key Libraries
| Component | Library |
|-----------|---------|
| UI | Jetpack Compose + Material 3 |
| Navigation | Compose Navigation (manual tab switching, no nav host) |
| Database | Room (with KSP) |
| HTTP Client | Retrofit + OkHttp + Logging Interceptor |
| JSON | Moshi (with Kotlin codegen via KSP) |
| AI | Gemini REST API (custom prompt pipeline) |
| DI | Manual constructor injection (ViewModel Factory) |
| Icons | Material Icons Core + Extended |
| Fonts | Google Fonts (IBM Plex Sans Arabic) |

## Project Structure (Key Files)
```
HukmAi/
├── app/
│   ├── build.gradle.kts          # App module config
│   ├── src/main/
│   │   ├── AndroidManifest.xml
│   │   ├── assets/databases/feqhia.db   # Pre-packaged SQLite (~89MB)
│   │   └── java/com/example/
│   │       ├── MainActivity.kt
│   │       ├── ui/
│   │       │   ├── theme/Theme.kt
│   │       │   └── HomeScreen.kt         # ALL screens in one file
│   │       ├── viewmodel/FeqhViewModel.kt
│   │       ├── data/
│   │       │   ├── api/AILogicEngine.kt    # Gemini prompt pipeline
│   │       │   ├── api/GeminiApiService.kt # Retrofit API
│   │       │   ├── api/RetrofitClient.kt
│   │       │   ├── dao/FeqhDao.kt
│   │       │   ├── db/FeqhDatabase.kt
│   │       │   ├── model/ (TreeNode, Article, ChatMessage)
│   │       │   └── repository/FeqhRepository.kt
│   │       └── ...
├── build.gradle.kts
├── settings.gradle.kts
├── gradle/
│   └── libs.versions.toml          # Version catalog
└── .github/workflows/build.yml     # CI/CD
```

## Git & GitHub Setup
- **Remote:** `git@github.com:ahmedio3/HukmAi.git` (public repo)
- **Branch:** `main` is the default + only branch
- **GitHub CLI (`gh`):** Authenticated and ready to use
- **GitHub Secrets** (set in repo settings → Secrets and variables → Actions):
  - `API_KEY_ROUTER_MAIN` — primary Gemini API key
  - `API_KEY_ROUTER_FALLBACK` — fallback Gemini API key
  - `API_KEY_ANSWER` — Gemini API key for final answer generation
- **Important:** API keys are read from environment variables at build time (`System.getenv("API_KEY_...")`). If env vars are empty, `BuildConfig.API_KEY_...` defaults to `""`, which disables AI calls gracefully.

## Essential Commands

### Build APK Locally
```bash
# Debug APK (requires Java 17+, Android SDK)
API_KEY_ROUTER_MAIN="your_key" API_KEY_ROUTER_FALLBACK="your_key" API_KEY_ANSWER="your_key" ./gradlew assembleDebug --stacktrace --no-daemon

# Without API keys (app will build but AI features will be disabled)
./gradlew assembleDebug --stacktrace --no-daemon
```

### Build APK via GitHub Actions (CI/CD)
```bash
# Push to main triggers automatic build
git add -A && git commit -m "message" && git push origin main

# Or manually trigger from CLI:
gh workflow run "Build Debug APK" --repo ahmedio3/HukmAi
```

### Check Build Status
```bash
gh run list -R ahmedio3/HukmAi -L 3 --json headBranch,status,conclusion,headCommit --jq '.[] | {branch: .headBranch, status: .status, conclusion: .conclusion}'

# Watch latest build in real-time:
gh run watch -R ahmedio3/HukmAi
```

### Download APK After Build
```bash
gh run download --repo ahmedio3/HukmAi --name app-debug --dir ./apk
# APK will be at: ./apk/app-debug.apk
```

### View Build Error Logs
```bash
# Get the specific compilation error:
gh run view --repo ahmedio3/HukmAi --log --job build 2>&1 | grep -E "e:|error:|ERROR|Unresolved|Cannot resolve|FAIL" | head -20

# Or from a failed step:
gh run view --repo ahmedio3/HukmAi --log-failed
```

### Install APK on Connected Device
```bash
adb install -r ./apk/app-debug.apk
```

### View App Logs (Logcat)
```bash
# Filter by app package:
adb logcat -c && adb logcat | grep -E "com.example|com.aistudio.hukmai"

# Or use the app PID:
adb logcat --pid=$(adb shell pidof -s com.aistudio.hukmai.fkahqr)
```

### Database Debugging
```bash
# Pull database from device:
adb exec-out run-as com.aistudio.hukmai.fkahqr cat databases/feqhia.db > /tmp/feqhia_pulled.db

# Or with root/emulator:
adb root && adb pull /data/data/com.aistudio.hukmai.fkahqr/databases/feqhia.db /tmp/
```

## How CI/CD Works (GitHub Actions)
1. On every push to `main`, GitHub Actions runs the workflow in `.github/workflows/build.yml`
2. The workflow:
   - Checks out code
   - Sets up Java 17 (Temurin)
   - Sets up Gradle 9.3.1 with caching
   - Injects API keys from GitHub Secrets → environment variables
   - Runs `gradle assembleDebug --stacktrace --no-daemon`
   - Uploads the APK as a build artifact named `app-debug`
3. You need **zero local SDK/Java** — the entire build runs on GitHub servers

## Common Build Failures & Fixes

### 1. "Unresolved reference" compilation errors
Usually missing imports or wrong API usage:
```bash
# Read the full error more carefully:
gh run view --repo ahmedio3/HukmAi --log 2>&1 | grep -A 3 "e: file:///home/runner"
```
**Fix:** Add the missing import, change API call, or adjust Compose version.

### 2. "This annotation is not repeatable"
A `@Composable` or other annotation appears twice on the same function.
**Fix:** Remove the duplicate annotation line.

### 3. Empty API keys
If `System.getenv("API_KEY_...")` returns null, `BuildConfig.API_KEY_...` becomes `""` (empty string). The app builds but AI calls silently return null.
**Fix:** Set the secrets in GitHub repo Settings → Secrets and variables → Actions.

### 4. Room schema validation errors
Pre-packaged databases must match Room entity schemas exactly.
**Fix:** Ensure all `@Entity` classes have matching `@Index` annotations and column definitions.

## Workflow Rules for AI Agents
1. **Always generate complete, runnable code** — never leave placeholders or TODOs
2. **Single file for screens:** All UI composables reside in `HomeScreen.kt` (it's large but intentional)
3. **Material 3 + Compose** — never use XML layouts or Views
4. **RTL support:** Use `Arrangement.Start`/`End` (not Left/Right); avoid `AutoMirrored` icons unless mirroring is desired
5. **Pre-packaged DB:** Never modify the database file; it's read-only. Schema changes require a database version bump with `fallbackToDestructiveMigration()`
6. **API keys:** Use `BuildConfig.API_KEY_...` (not a secrets plugin). These are read from env vars at compile time
7. **When build fails:** Read the full log, identify the specific error, fix the code, commit, and push again
8. **After successful build:** Download APK, install with adb, verify on device
9. **Commit messages:** Write clear, descriptive commit messages in English

## Summary of Workflow Process
```
Code changes → git add → git commit → git push
                                         ↓
                              GitHub Actions builds
                                         ↓
                              Build succeeds? ──No──→ Read logs → Fix code → Push again
                                         │
                                        Yes
                                         ↓
                              Download APK via gh CLI
                                         ↓
                              Install via adb
                                         ↓
                              Test on device
```

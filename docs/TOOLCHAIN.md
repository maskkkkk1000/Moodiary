# Development toolchain

The project uses JDK 17, Gradle 8.13, Android API 35, and Android SDK Build Tools 35.0.0. The checked-in Gradle wrapper pins the distribution SHA-256. All portable tools and dependency caches are ignored under `.tooling/`; they are not shipped in the app.

On the current Windows workspace, activate the installed portable tools in a PowerShell terminal:

```powershell
. .\scripts\use-local-toolchain.ps1
java -version
.\gradlew.bat :domain:test :app:assembleDebug :app:testDebugUnitTest
```

The activation script only changes the current process environment. It does not change system settings. Android Studio can instead use a normal JDK 17 and API 35 SDK installation. Set `JAVA_HOME` and `ANDROID_HOME` accordingly, or configure `sdk.dir` in an ignored `local.properties` file.

## Verified portable archives

Downloaded 2026-09-27 from official project endpoints and SHA-256 verified before extraction:

| Component | Version | SHA-256 |
|---|---|---|
| Eclipse Temurin Windows x64 JDK | 17.0.20.1+1 | `e53a79c3c3d86865bd7e787903884331068e71321714ffd44f145785affc7cb0` |
| Gradle binary distribution | 8.13 | `20f1b1176237254a6fc204d8434196fa11a4cfb387567519c61556e8710aed78` |
| Android command-line tools Windows | 15859902 / CLI 22.0 | `90ae805d20434428bffcb699c290860f19bb5f66a67e6b330067e3de801fb04a` |

Official sources: [Temurin archive installation](https://adoptium.net/installation/archives), [Temurin release](https://github.com/adoptium/temurin17-binaries/releases/tag/jdk-17.0.20.1%2B1), [Gradle checksums](https://gradle.org/release-checksums/), and [Android tools](https://developer.android.com/studio#command-line-tools-only).

## SDK installation

The SDK manager initially confirmed that API 35, Build Tools 35, and Platform Tools required acceptance of `android-sdk-license`. The user explicitly authorized accepting the SDK license in this development session before it was accepted. On a fresh machine, the developer must personally review and accept the SDK terms when prompted:

```powershell
. .\scripts\use-local-toolchain.ps1
sdkmanager.bat --sdk_root=$env:ANDROID_HOME 'platforms;android-35' 'build-tools;35.0.0' 'platform-tools'
```

The current command-line package reports `sdkmanager` as deprecated in favor of `android sdk`, but still supports the commands above. No advertising or analytics runtime is added by development tooling.

## Device verification

Instrumented Room and Compose tests need an Android emulator or USB/Wi-Fi connected device:

```powershell
adb devices -l
.\gradlew.bat :app:connectedDebugAndroidTest
```

An installed MuMu ADB executable was found at `D:\MuMuPlayer\nx_main\adb.exe`, but an executable alone does not establish that an Android device is running or that instrumentation tests passed. The task report records actual build and test outcomes separately.

Restricted execution environments must permit network access for official Maven/Google/Gradle dependency downloads and local sockets for Gradle workers/ADB. Dependency or license failures must be reported as setup failures, not successful application verification.

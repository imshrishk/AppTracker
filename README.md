# AppTracker

A powerful Android security and privacy tool that gives you deep visibility into every app installed on your device. AppTracker inspects permissions, monitors battery and network usage, calculates risk scores, and alerts you to suspicious app behaviour — all without requiring root access.

> **Minimum Android:** 8.0 (API 26) &nbsp;·&nbsp; **Target Android:** 15 (API 35) &nbsp;·&nbsp; **Language:** Kotlin &nbsp;·&nbsp; **UI:** Jetpack Compose + Material 3

---

## Features

### Permission Inspection
- Lists every permission each app holds with protection level (normal, dangerous, signature, privileged)
- Tracks **App Ops** runtime access for 21+ operations: camera, microphone, GPS, contacts, call logs, SMS, storage, overlays, and more
- Shows granted/denied status with human-friendly descriptions and risk concern levels (Critical / High / Moderate / Low)
- Expandable permission cards with contextual explanations of why each permission can be risky

### Battery Analysis
- Foreground and background time breakdown per app
- Wakelock count, alarm count, and CPU time tracking
- Battery optimisation status (optimised, unrestricted, restricted)
- Historical battery usage stored for 90 days

### Network Monitoring
- Per-app Wi-Fi and mobile data usage (bytes sent/received)
- Historical network data stored in Room database for trend analysis

### Risk Scoring
- Automated risk score (0–100) calculated per app across four weighted categories:
  - **Permission risk (40%)**: dangerous grants, background location, contacts + call log combos
  - **Behaviour risk (30%)**: overlays, accessibility abuse, device admin, permission creep
  - **Network risk (20%)**: high data usage, unusual send/receive ratios
  - **Battery risk (10%)**: high background activity, battery optimisation exemptions
- Colour-coded severity: Low · Medium · High · Critical

### App Comparison
- Side-by-side comparison of any two installed apps
- Highlights the "safer" app for each metric (risk score, permissions, battery, network)

### Watchlist
- Bookmark any app as "watched" with a single tap from the app list or detail screen
- Watched apps appear in a dedicated Dashboard section

### Background Monitoring & Notifications
- WorkManager periodic scan every 6 hours (no foreground service needed)
- Push notification when a high-risk app (score >= 70) is detected
- Summarised scan result notification after each refresh

### Export & Share
- Generate a shareable plain-text security report for any app
- Share via email, messaging, or cloud storage with the system share sheet

### Permission Timeline
- Live feed of the last 200 App Ops events with timestamps
- Filter by category: All, Location, Camera, Microphone, Contacts, Storage

### Settings & Data Management
- Clear history while keeping the last 7 or 30 days — with snackbar confirmation
- Quick links to grant Usage Access, Battery Optimisation, and Notification permissions

### Onboarding
- 3-page first-launch guide to help users grant the required Usage Access permission
- Completion state persisted via DataStore — shown only once

---

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Language | Kotlin 2.1.0 |
| UI | Jetpack Compose + Material 3 (BOM 2024.12.01) |
| Architecture | Clean Architecture (data / domain / ui) + MVVM |
| DI | Hilt 2.52 |
| Database | Room 2.6.1 |
| Background | WorkManager 2.9.1 |
| Navigation | Navigation Compose 2.8.4 |
| Image Loading | Coil 2.7.0 |
| Charts | Vico 1.13.1 |
| Preferences | DataStore Preferences 1.1.2 |
| Build | AGP 8.7.2, Gradle 8.11.1, JDK 17 |

---

## Required Permissions

| Permission | Purpose |
|-----------|---------|
| `PACKAGE_USAGE_STATS` | App battery and usage stats (must be granted manually in Settings) |
| `QUERY_ALL_PACKAGES` | Enumerate all installed apps |
| `BATTERY_STATS` | Per-app battery breakdown |
| `ACCESS_NETWORK_STATE` | Network connectivity state |
| `READ_PHONE_STATE` | Network stats on some devices |
| `POST_NOTIFICATIONS` | Risk alert notifications (Android 13+) |
| `FOREGROUND_SERVICE` | Allow WorkManager background scans |
| `RECEIVE_BOOT_COMPLETED` | Re-schedule the scan after device reboot |

> **Important:** Usage Access must be granted manually via `Settings > Apps > Special app access > Usage access > AppTracker > Allow`.

---

## Getting Started

### Prerequisites
- Android Studio Ladybug (2024.2) or newer
- Android SDK 35 installed
- JDK 17+
- A device or emulator running Android 8.0+

### Build & Run

```bash
# 1. Clone the repository
git clone https://github.com/imshrishk/AppTracker.git
cd AppTracker

# 2. Build a debug APK
./gradlew assembleDebug

# 3. Install on a connected device
./gradlew installDebug
```

### Release Build

```bash
./gradlew assembleRelease
```

Output: `app/build/outputs/apk/release/app-release.apk`

To sign the release build, add a `signingConfigs` block to `app/build.gradle.kts` pointing to your keystore file.

---

## Project Structure

```
app/src/main/java/com/apptracker/
├── AppTrackerApplication.kt        # Hilt + WorkManager initialisation
├── MainActivity.kt                 # Entry point, onboarding + usage access gate
├── data/
│   ├── db/                         # Room database, DAOs, entities
│   ├── model/                      # Domain models (AppInfo, BatteryUsageInfo, …)
│   ├── repository/                 # BatteryRepository, NetworkRepository, PermissionRepository
│   └── util/                       # PermissionDescriptions (50+ permissions mapped)
├── di/
│   └── AppModule.kt                # Hilt singleton providers
├── domain/
│   ├── model/                      # RiskScore, RiskFlag, RiskSeverity
│   └── usecase/                    # CalculateRiskScore, GenerateReport, GetAppDetail, GetInstalledApps
├── ui/
│   ├── components/                 # AppIcon, PermissionCard, RiskBadge, Charts, AppActionsBottomSheet
│   ├── navigation/                 # AppNavigation, Screen
│   ├── screens/
│   │   ├── appcompare/             # Side-by-side app comparison
│   │   ├── appdetail/              # 5-tab deep dive (Permissions, Ops, Battery, Network, Risk)
│   │   ├── applist/                # Searchable, sortable, filterable app list with pull-to-refresh
│   │   ├── dashboard/              # Overview stats, watchlist, high-risk apps, charts
│   │   ├── onboarding/             # First-launch 3-page guide
│   │   ├── settings/               # Permissions, data management, about
│   │   └── timeline/               # Live App Ops permission event feed
│   └── theme/                      # Color, Type, Theme (dark + light)
├── util/
│   ├── NotificationHelper.kt       # Notification channel + alert builders
│   └── OnboardingPreferences.kt    # DataStore onboarding flag
└── worker/
    └── DataRefreshWorker.kt        # 6-hour periodic background scan
```

---

## Non-Root Data Coverage

AppTracker covers approximately **80% of available system data** on a standard unrooted device.

| Data | Available | Notes |
|------|-----------|-------|
| Declared permissions | ✅ | All apps |
| Runtime permission grants | ✅ | Via App Ops |
| App Ops access events | ✅ | Last 200 events |
| Battery foreground/background time | ✅ | Via UsageStatsManager |
| Per-app network bytes | ✅ | Via NetworkStatsManager |
| Storage usage | ✅ | Via StorageStatsManager (API 26+) |
| Kernel-level wakelocks | ❌ | Requires root |
| Sub-component power draw | ❌ | Requires root |

# AppTracker

A powerful Android security and privacy tool that gives you deep visibility into every app installed on your device. AppTracker inspects permissions, monitors battery and network usage, calculates risk scores, and alerts you to suspicious app behaviour — all without requiring root access and with zero cloud uploads.

## Current Release

- **Version:** v1.1.0
- **Release Code:** 2
- **Market Artifact:** `app/build/outputs/apk/release/app-tracker-v1.1.0.apk`

> **Minimum Android:** 8.0 (API 26) &nbsp;·&nbsp; **Target Android:** 15 (API 35) &nbsp;·&nbsp; **Language:** Kotlin &nbsp;·&nbsp; **UI:** Jetpack Compose + Material 3

---

## Features

### Permission Inspection
- Lists every permission each app holds with protection level (normal, dangerous, signature, privileged)
- Tracks **App Ops** runtime access for 21+ operations: camera, microphone, GPS, contacts, call logs, SMS, storage, overlays, and more
- Shows granted/denied status with human-friendly descriptions and risk concern levels (Critical / High / Moderate / Low)
- Expandable permission cards with contextual explanations of why each permission can be risky
- **Permission Access Audit Log** — timestamped log of when each App Op was last accessed, with access count and reject count

### Battery Analysis
- Foreground and background time breakdown per app
- Wakelock count, alarm count, and CPU time tracking
- Battery optimisation status (optimised, unrestricted, restricted)
- Historical battery usage stored for 90 days
- **Usage Trend Chart** — sparkline chart showing foreground + background activity over the selected time period

### Network Monitoring
- Per-app Wi-Fi and mobile data usage (bytes sent/received)
- Historical network data stored in Room database for trend analysis
- **Network Trend Chart** — bar chart of Wi-Fi and mobile download over the selected period
- Anomaly warning when send/receive ratio exceeds 2×

### Risk Scoring
- Automated risk score (0–100) calculated per app across four weighted categories:
  - **Permission risk (40%)**: dangerous grants, background location, contacts + call log combos
  - **Behaviour risk (30%)**: overlays, accessibility abuse, device admin, permission creep
  - **Network risk (20%)**: high data usage, unusual send/receive ratios
  - **Battery risk (10%)**: high background activity, battery optimisation exemptions
- Colour-coded severity: Low · Medium · High · Critical
- "Why this score?" explainer card for beginners; full per-component breakdown for experts

### App Categorization
- Automatic category inference from package name (Social, Finance, Games, Health, Productivity, Tools, System, Browser, Media, Shopping, and more)
- Category chip shown on the app detail header
- Filter apps by category in the app list

### Device Health Score
- Aggregate privacy & security rating (0–100) computed from all installed apps
- Displayed as a prominent card on the Dashboard with a letter grade (A–F)
- Factors: average risk score, proportion of high-risk apps, dangerous permission count, high-background app count

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
- Generate a shareable plain-text security report for any app (disabled in Privacy Mode)
- **Export to CSV or JSON** — full app permission + battery + network summary exported through the Android share sheet
- Share via email, messaging, or cloud storage with the system share sheet

### Permission Timeline
- Live feed of App Ops events, filtered by time period (24 h / 7 d / 30 d / 90 d or custom)
- Filter by category: All, Location, Camera, Microphone, Contacts, Storage

### Custom Time Ranges
- Dashboard, App Detail Battery/Network tabs, and Timeline all support selecting **24 h, 7 d, 30 d, 90 d, or a custom 1–365 day window**
- Default period saved per device in DataStore

### Beginner / Expert Mode
- **Beginner Mode**: plain-language explanations on every tab, contextual tip cards, simplified labels
- **Expert Mode**: raw packet counts, sub-score breakdown tables, full App Ops history, export controls
- Toggle in Settings → Privacy & Experience

### Privacy Mode (On-Device Only)
- When enabled, sharing and cloud upload are fully disabled
- Privacy Mode status shown as a persistent banner on the App Detail screen
- All data stays on-device regardless of mode; this toggle only governs what you can _export_

### Settings & Data Management
- Clear history while keeping the last 7 or 30 days — with snackbar confirmation
- Quick links to grant Usage Access, Battery Optimisation, and Notification permissions
- Default usage period selector, Beginner Mode toggle, On-Device Only toggle

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
| DI | Hilt 2.57.2 |
| Database | Room 2.6.1 |
| Background | WorkManager 2.9.1 |
| Navigation | Navigation Compose 2.8.4 |
| Image Loading | Coil 2.7.0 |
| Charts | Vico 1.13.1 |
| Preferences | DataStore Preferences 1.1.2 |
| Build | AGP 8.7.2, Gradle 8.11.1, JDK 20 |

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
- JDK 20 (or JDK 17+)
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

Outputs:
- `app/build/outputs/apk/release/app-release.apk`
- `app/build/outputs/apk/release/app-tracker-v1.1.0.apk` (versioned copy)

Release signing is already configured through `keystore.properties` + `signingConfigs.release` in `app/build.gradle.kts`.

### Verify Signed APK

```bash
apksigner verify --verbose --print-certs app/build/outputs/apk/release/app-release.apk
```

Expected verification for market-standard signing:
- `v2 = true`
- `v3 = true`

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
| App Ops access events | ✅ | Time-filtered timeline (24h/7d/30d/90d/custom) |
| Battery foreground/background time | ✅ | Via UsageStatsManager |
| Per-app network bytes | ✅ | Via NetworkStatsManager |
| Storage usage | ✅ | Via StorageStatsManager (API 26+) |
| Kernel-level wakelocks | ❌ | Requires root |
| Sub-component power draw | ❌ | Requires root |

# AppTracker

A powerful Android security and privacy tool that gives you deep visibility into every app installed on your device. AppTracker inspects permissions, monitors battery and network usage, calculates risk scores, and alerts you to suspicious app behaviour — all without requiring root access and with zero cloud uploads.

## Current Release

- **Version:** v2.0.0
- **Release Code:** 3
- **Market Artifact:** `app/build/outputs/apk/release/app-tracker-v2.0.0.apk`

> **Minimum Android:** 8.0 (API 26) &nbsp;·&nbsp; **Target Android:** 15 (API 35) &nbsp;·&nbsp; **Language:** Kotlin &nbsp;·&nbsp; **UI:** Jetpack Compose + Material 3

---

## Features
- **Permission Inspection**
- Lists every permission each app holds with protection level (normal, dangerous, signature, privileged)
- Tracks **App Ops** runtime access for 21+ operations: camera, microphone, GPS, contacts, call logs, SMS, storage, overlays, and more
- **Sensor access tracker (App Ops)**: sensitive App Ops monitoring now also captures sensor access events (e.g., body sensors) alongside camera/mic/location/clipboard
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
- **Health trend history**: periodic snapshots retained locally to show recent device-health movement and a simple benchmark vs recent average
- **Peer benchmark (fully local)**: Dashboard compares your device against built-in low, medium, and high-risk archetypes using risk, dangerous permissions, background-heavy usage, and tracker-hit signals
- **Onboarding baseline snapshot**: first successful health calculation is stored as a local baseline so later scans can be compared against your original device state
- **Guided health checklist**: Dashboard now surfaces the most actionable cleanup items based on current risks, background activity, sideloaded apps, DNS tracker hits, and data-hoarding apps
- **Risk surface radar chart**: Dashboard visualizes overall exposure across Permission, Behavior, Network, Battery, and Data dimensions

### App Comparison
- Side-by-side comparison of any two installed apps
- Highlights the "safer" app for each metric (risk score, permissions, battery, network)
- **Safer overall summary**: comparison now calls out the currently safer overall app and explains the strongest reasons behind that result

### Watchlist
- Bookmark any app as "watched" with a single tap from the app list or detail screen
- Watched apps appear in a dedicated Dashboard section

### Utility Enhancements
- **Threshold-based analysis** in Settings for high-risk cutoff and heavy background usage hours
- Dashboard now highlights **high-background apps**, **newly installed apps (7 days)**, and **camera/mic/location-sensitive apps**
- App list high-risk filtering respects the configured threshold for personalized triage

### Local Security Monitoring (Implemented)
- **Permission delta alerts (local)**: background scan compares dangerous permissions across snapshots and alerts on new additions
- **Night activity anomaly alerts (local)**: detects unusual network/background usage between 12am–5am
- **Sensitive App Ops alerts (local)**: alerts on camera/mic/location operation changes detected in scan windows
- **Sensitive clipboard access alerts (local)**: clipboard read operations are treated as sensitive App Ops and surfaced in local alerts plus App Detail risk analysis
- **Realtime App Ops monitor (local)**: while AppTracker is running, camera/mic/location/clipboard access signals trigger immediate local alerts
- **Permission auto-revoke/reset tracker (local heuristic)**: flags likely auto-revoked permission resets when dangerous permissions disappear without an app update
- **Dark pattern detector (local heuristic)**: flags suspicious re-request/re-grant patterns when sensitive permissions return without a version change
- **Install source audit**: captures installer source and flags sideloaded/unknown-source apps
- **Fake GPS detection (heuristic)**: flags apps with mock-location capability signals
- **Accessibility abuse watchdog (heuristic)**: records apps exposing AccessibilityService-level control signals
- **Accessibility abuse watchdog (realtime local listener)**: watches enabled accessibility services and logs unexpected activations
- **Screen recording detector (heuristic)**: flags MediaProjection/screen-capture capability signals
- **Keylogger risk scorer (composite heuristic)**: critical alert when accessibility + overlay/input-capture style signals combine
- **App impersonation detector (heuristic)**: flags leetspeak/brand-mimic naming patterns with package mismatches
- **Cross-app collusion detection (heuristic)**: flags non-system app pairs sharing signing cert and overlapping dangerous permissions
- **Hidden process scanner (heuristic)**: flags non-system apps with a boot-receiver + background-execution permission combination and either a suspicious service-style name or heavy background-only runtime — logged as `hidden_process` events and surfaced in the dashboard heuristic alert count
- **Certificate pinning quick verification (local APK scan)**: high-data apps holding 3+ dangerous permissions get a local APK scan for network-security config and common pinning markers; missing evidence is surfaced in App Info and can trigger a local heuristic alert
- **Category baseline comparison (risk flag)**: per-app dangerous-permission count is compared against category averages (Finance, Social, Communication, etc.) — apps significantly above their category baseline receive a `category_baseline_exceeded` risk flag
- **Watchlist change notifications**: when a watched app's risk score shifts ≥ 5 points or its dangerous permission count changes, a local notification is fired
- **Permission grant spike alert**: triggers when 3+ dangerous permissions are added to a single app within a 24 h window — logged as `permission_spike` event
- **New app install alert**: detects apps installed since the last 6 h scan cycle and sends an immediate local notification (all on-device)
- **Immediate install receiver alert**: local broadcast receiver triggers install alert as soon as a package is added
- **Device health degradation alert**: if the computed device health score drops 10+ points vs the previous scan, a local push notification is sent
- **Burst network detector**: alerts when an app's network usage spikes >10× vs the prior 6 h snapshot and exceeds 50 MB total
- **Non-streaming burst anomaly tuning**: burst alerts are deprioritized for media/browser categories to reduce normal-streaming false positives
- **Dormant app detector**: surfaces apps installed 60+ days ago with no usage in the past 90 days that still hold dangerous permissions — shown on dashboard + logged as security events
- **Data hoarding score (0–100)**: per-app sensitive permission combination score factoring contacts, location, camera, mic, call logs, SMS, body sensors, calendar — shown in Risk Analysis tab and Top Data Collectors dashboard section
- **Shared UID group map**: app detail now shows Linux UID plus peer apps sharing the same UID sandbox on-device
- **SDK/library fingerprint (heuristic)**: app detail performs a local bounded APK scan for known analytics/ads SDK namespace markers
- **Threat feed cards** on Dashboard: weekly updated-app count plus grouped local alert counts for permission deltas, night activity, sensitive ops, auto-revokes, dark-pattern re-requests, watchlist changes, permission spikes, burst-network events, app installs, DNS tracker hits, explicit DNS leak/bypass signals, and aggregated heuristic flags
- **Custom rules engine (local)**: define threshold rules (risk score, dangerous permissions, background hours, mobile MB) and trigger on-device alerts when apps match
- **Guided remediation flow**: dedicated screen with top risky apps and actionable mitigation steps
- **Guided remediation flow**: dedicated screen now highlights why each app is risky, surfaces a top-3 action plan, shows safer alternatives when available, and links directly to permissions/settings review
- **Safe alternatives suggester**: remediation hints include privacy-first alternatives for high-risk communication/browser/social/media/tool apps
- **One-tap permission audit** from app detail: deep-link to Android app settings
- **Risk score explainer in plain English**: app detail risk tab now includes a concise sentence summary of why the score is high/moderate/low
- **Health score breakdown drill-down**: dashboard shows top apps currently dragging the Device Health Score and estimated impact points
- **Background vs foreground network split**: app detail Network tab now highlights foreground/background percentages with risk hinting
- **Cumulative data cost estimator (local heuristic)**: app detail estimates monthly mobile-data cost using selected-period usage extrapolation
- **Local DNS monitor (VPN-mode, on-device)**: optional local VPN service intercepts DNS queries to common resolvers, logs recent lookups, and relays responses without any cloud upload
- **Potential DNS leak / bypass detector (heuristic)**: repeated DNS queries that cannot be attributed to an app package are flagged locally as possible resolver bypass, competing VPN traffic, or unattributed OS DNS
- **Resolver-drift leak heuristics (local)**: DNS leak detection now also evaluates unattributed-query ratio, distinct resolver count, and non-monitored resolver usage to surface stronger bypass signals
- **Tracker domain classifier (embedded blocklist)**: DNS queries are matched locally against an embedded tracker suffix list covering advertising, analytics, social tracking, session recording, crash reporting, and fingerprinting domains
- **DNS Activity screen**: dedicated screen to start/stop DNS monitoring, review recent queries, inspect tracker hits, surface unattributed DNS leak signals, inspect resolver diversity/non-monitored resolver activity, and see top tracker domains over the last 24 hours
- **Leak-check explainability panel**: DNS Activity shows each leak condition (unattributed ratio, non-monitored resolver usage, resolver diversity) with current values versus active sensitivity thresholds
- **One-tap DNS investigation filters**: tapping a leak-check condition switches the DNS log to focused evidence views (Unattributed or Non-Monitored) for faster triage
- **Resolver + attribution evidence rows**: DNS query log now shows resolver IP and mapped app package, with unattributed queries clearly marked for faster leak triage
- **Contextual DNS focus chips**: DNS Activity can further narrow the active log view to the busiest resolvers or app packages so leak evidence is easier to isolate
- **Inline DNS suspicion badges**: DNS rows now tag tracker, unattributed, and non-monitored-resolver evidence directly in the log for faster scanning
- **Clickable resolver summaries**: tapping a top-resolver card jumps the DNS log to that resolver so the summary panel becomes a direct investigation shortcut
- **Clickable tracker-domain summaries**: tapping a top tracker-domain card jumps to tracker log evidence for that specific domain
- **Focus navigation controls**: DNS focus panel now provides one-tap Clear Focus and Restore Previous actions for faster repeated triage
- **Active focus labeling**: DNS focus panel shows human-readable Active/Previous focus labels (resolver, app, domain, unattributed) to reduce triage ambiguity
- **Copy active focus shortcut**: DNS focus panel can copy the current focus label to clipboard for quick incident notes and reporting
- **APK inspector**: app detail now shows signer metadata, SHA-256 certificate digests, exported component exposure, and local APK diff history across version updates
- **Intent inspector**: exported activities, services, receivers, and providers are surfaced with permission guards so exposed entry points can be audited locally
- **Risk radar chart**: app detail Risk Analysis tab visualizes the score shape across permission, behavior, network, battery, and data-hoarding dimensions
- **Sensitive file scanner (local heuristic)**: File Manager can scan current-folder files for common sensitive filename indicators (credentials, keys, identity, finance, medical, backup tokens)
- **Duplicate finder (local hash pass)**: File Manager can detect duplicate file clusters using size + content hash and filter the current view to duplicate-only results
- **Secure delete audit**: optional overwrite-before-delete flow for files (best effort) logs a local security event when secure deletion succeeds
- **App trust labels**: Trusted / Suspicious / Unknown tags persisted locally and shown in app list/detail
- **Permission creep index**: per-app count of dangerous permission additions across update history on this device
- **Global search upgrade**: app list + global search now cover app name/package, permissions, category, install source, trust labels, and risk keywords
- **Composable search filters**: quick chips can be combined (multi-token query), with active-filter summaries, one-tap per-token removal, and clear-all actions
- **Search memory controls**: query/filter memory can be toggled on/off in Settings; when disabled, saved filters are cleared and both search screens show a local privacy banner with inline re-enable

> All telemetry and analysis remain on-device. AppTracker does not upload security events, permission snapshots, trust labels, or any other data.

### File Manager (Hidden Files + Search + Delete)
- New **Files** tab for browsing device storage with hidden files support
- Search by file/folder name or path for quick discovery
- Clear classification by type: Folder, Document, Media, Archive, APK, Code, Hidden, Other
- Sort options: Name, Largest, Recent
- File preview for text/code/doc formats (safe size-limited preview)
- Long-press or delete action to remove files/folders directly
- One-tap **Sensitive Scan** and **Duplicate Scan** actions with on-screen counters and filter chips
- **Secure delete toggle** in delete confirmation (overwrite-before-delete for regular files)
- **24-hour file access telemetry**: local summary of directory opens, previews, sensitive previews, and deletes, with top accessed directories and burst-detection signals

### Background Monitoring & Notifications
- WorkManager periodic scan every 6 hours (no foreground service needed)
- Push notification when a high-risk app (score >= 70) is detected
- Summarised scan result notification after each refresh
- Weekly digest notification rolls up the last 7 days of local alerts, tracker DNS hits, DNS leak/bypass signals, new installs, and current device health

### Export & Share
- Generate a shareable plain-text security report for any app (disabled in Privacy Mode)
- **Export to CSV or JSON** — full app permission, battery, and network summary exported through the Android share sheet, including granted/dangerous permission names, install source, battery optimization state, and detailed network breakdowns
- Share via email, messaging, or cloud storage with the system share sheet

### Permission Timeline
- Unified replay feed across App Ops, security alerts, and file-audit events, filtered by time period (24 h / 7 d / 30 d / 90 d or custom)
- Filter by category: All, Location, Camera, Microphone, Contacts, Storage, Security, Files
- **Replay summary**: timeline now highlights total events, number of affected apps, latest activity time, and top packages in the selected window

### Custom Time Ranges
- Dashboard, App Detail Battery/Network tabs, and Timeline all support selecting **24 h, 7 d, 30 d, 90 d, or a custom 1–365 day window**
- Default period saved per device in DataStore

### Beginner / Expert Mode
- **Beginner Mode**: plain-language tips on Dashboard, App List, and key App Detail tabs (Battery, Network, Risk) to explain what the numbers mean
- **Expert Mode**: reveals raw packet counts and extra risk-score component context in App Detail, while App Ops history remains available in both modes
- Toggle in Settings → Privacy & Experience

### Privacy Mode (On-Device Only)
- When enabled, sharing and cloud upload are fully disabled
- Privacy Mode status shown as a persistent banner on the App Detail screen
- All data stays on-device regardless of mode; this toggle only governs what you can _export_

### Settings & Data Management
- Clear history while keeping the last 7 or 30 days — with snackbar confirmation
- Quick links to grant Usage Access, Battery Optimisation, and Notification permissions
- Default usage period selector, Beginner Mode toggle, On-Device Only toggle
- DNS leak sensitivity control (Low / Balanced / High) for resolver-drift and unattributed DNS heuristics
- **Remember search filters toggle**: control whether App List/Global Search filters persist across restarts (with explicit snackbar feedback)
- **Reset search defaults**: clear saved App List/Global Search filters and restore search memory in one tap
- **Rule builder UI**: create, enable/disable, and delete local custom monitoring rules without leaving Settings

### Onboarding
- 4-step first-launch guide covering Usage Access setup, privacy model, feature overview, and a local risk snapshot before entering the Dashboard
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
| `FOREGROUND_SERVICE_CONNECTED_DEVICE` | Run the optional local DNS VPN monitor foreground service |
| `RECEIVE_BOOT_COMPLETED` | Re-schedule the scan after device reboot |
| `MANAGE_EXTERNAL_STORAGE` | Broad file browsing/deletion for File Manager (Android 11+) |

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
│   │   ├── filemanager/            # File browser with search, classification, preview, delete
│   │   ├── onboarding/             # First-launch 4-step guide with risk snapshot
│   │   ├── settings/               # Permissions, data management, about
│   │   └── timeline/               # Live App Ops permission event feed
│   └── theme/                      # Color, Type, Theme (dark + light)
├── util/
│   ├── NotificationHelper.kt       # Notification channel + alert builders
│   └── OnboardingPreferences.kt    # DataStore onboarding + experience + analysis thresholds
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

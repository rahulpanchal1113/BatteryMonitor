# Battery Monitor ⚡🔋

A modern, privacy-focused Android application for comprehensive battery health diagnostics, live charging power flow telemetry, thermal safety tracking, stage-by-stage charging speed analytics, and detailed discharge cycle telemetry with per-app battery consumption dissection.

> **Note**: This application was designed and built using **[Google AI Studio](https://ai.studio/)**.

---

## ✨ Features

### 1. 📊 Real-Time Monitor (Dashboard)
- **Live Battery Status**: Instant readout of battery percentage, charging status (AC, USB, Wireless, Discharging on Battery), cell voltage, temperature, technology, and connection speed.
- **Empirical Absolute Battery Health (%)**:
  - Replaces vague status strings ("Good") with true **health percentage (%)** and **usable capacity (e.g., 2950 / 3591 mAh)** relative to original factory specifications.
  - Calculated dynamically using your phone's real charging energy intake and on-battery discharge telemetry.
  - **Learning & Calibration State**: Upon first install or before sufficient data is recorded, gracefully displays a `Calibrating...` status and progress indicator until 1–2 charge/discharge cycles are tracked.
  - **Comprehensive Diagnostics Dialog**: One-tap dialog detailing current usable capacity, factory rated capacity (when new), capacity degradation (-mAh and % loss), equivalent cycle count, and average operating temperature.
- **Live Charging Power Flow**: Visual breakdown of total wall charger power supplied vs. net battery intake power and active device draw (in Watts and mA).
- **Power Consumption & Standby**: Accurate discharge rate and standby draw tracking when running unplugged on battery power.
- **Hardware Fault & Cable Diagnostics**: Real-time detection of unstable connections, intermittent contacts, and faulty cables or adapters.
- **Recent Power Events**: Clean, focused feed showing the latest power events with one-tap deep dive into detailed session metrics and temperature curves.

### 2. 📉 Discharging Cycle Analytics & Per-App Breakdown
- **Discharge Event Tracking**: Automatically tracks on-battery discharge cycles with start/end levels, total percentage drained, duration on battery, and discharge velocity (%/hour).
- **Top Battery Consuming Apps**:
  - **Absolute Battery Drain**: Displays the estimated absolute battery percentage consumed by each app during that specific discharge cycle.
  - **Foreground vs. Background Dissection (100% Sum)**: Breaks down each app's active energy footprint into foreground vs. background proportions that always sum to 100%, visualized with a two-tone progress indicator.
  - **Accurate Active Runtime**: Precise duration tracking formatted with seconds for short bursts (e.g., `45s`, `1m 23s`) and hours/minutes for longer use (e.g., `1h 7m`).
  - **App Thermal Attribution**: Displays the **Peak Temperature** and **Average Temperature** reached during each app's active period (e.g., `Peak: 38.2°C • Avg: 34.5°C`), with color-coded alerts for thermal throttling thresholds (≥42°C).
  - **Hardware Component Power Profile**: Realistic power modeling calibrated for high-drain hardware (camera sensor, ISP, video encoder, and flash), 3D games, video streaming (YouTube/Netflix), GPS navigation, web browsers, and background audio.
  - **Strict Session Duration Clamping**: Uses Android `UsageEvents` with lookback intersection to strictly bound app usage within the discharge cycle interval, eliminating daily bucket leakage.
- **Thermal Drain Analysis**: "How Overheating Accelerates Battery Drain & Degrades Cell Health" panel detailing electrochemical self-discharge and CPU throttling across 3 equal-height thermal stages (`<35°C Cool`, `36–42°C Warm`, and `≥43°C High Heat`).
- **Interactive Dual-Axis Discharge Graph**: Visualizes battery level drop together with cell temperature progression over time.

### 3. 📈 Stage-by-Stage Charging Speed & Insights
- **Full-Spectrum Multi-Stage Progression**: When charging across percentage boundaries (e.g., from 15% to 85%), every traversed stage (`0–20%`, `20–40%`, `40–60%`, `60–80%`, and `80–100%`) is captured, analyzed, and updated in the Insights dashboard.
- **Dynamic Speed Ranking**: Color-coded spectrum ranging from the fastest stage (Emerald Green) to top-capacity trickle charging (Warm Orange).
- **Dual Speed Metrics**: Switch seamlessly between **Minutes per Stage** and **Charge Rate (%/hr)**.
- **Thermal Progression Curve**: Continuous temperature tracking throughout charging stages, excluding unrecorded ranges for clean, accurate trends.
- **Thermal Safety & Charging Throttle Protection**: Monitored peak temperatures and 3-stage charging speed impact cards (`<36°C Cool 100% Speed`, `38–44°C Hot ~55% Speed`, and `≥45°C Overheat ~20% Speed`).

### 4. 🗓️ Daily History & Session Details
- **Date-Filtered Timeline**: Review all past charging events and completed discharge cycles categorized by day.
- **Detailed Session Bottom Sheet**: Deep dive into any historical charge or discharge event with start/end levels, net percentage change, duration, average/peak temperatures, wattage, and speed.
- **Smart Unplugged Event Reconstruction**: Reconstructs the exact start of a discharge cycle using the system's latest `UNPLUGGED` event if the app was closed when disconnected from the charger.
- **Data Integrity & Monotonic Verification**: Guarantees consistent records and eliminates negative end-level artifacts.

### 5. ⚙️ Settings, Backup & Customization
- **Temperature Units**: Toggle effortlessly between **Celsius (°C)** and **Fahrenheit (°F)** across all screens, charts, and per-app thermal chips.
- **Local & Google Drive Backup**: Export and import full historical charging sessions and events via structured JSON.
- **Battery Optimization Tips**: Best practices to maintain lithium-ion battery longevity and prevent premature capacity degradation.

### 6. 📱 Home Screen Widget & Modern Icon
- **Instant Live Widget**: Glanceable Android AppWidget displaying current battery percentage, live plug/unplug status, and session charge gain with zero latency.
- **Modern Sleek Adaptive Icon**: High-contrast obsidian slate canvas with glowing neon emerald and cyan energy cell and precision electric bolt.

---

## 🛠️ Architecture & Tech Stack

- **Language**: 100% [Kotlin](https://kotlinlang.org/)
- **UI Framework**: [Jetpack Compose](https://developer.android.com/jetpack/compose) with **Material Design 3 (M3)**
- **Architecture**: MVVM (Model-View-ViewModel) with unidirectional data flow
- **State Management**: Kotlin Coroutines & `StateFlow`
- **Local Persistence**: [Room Database](https://developer.android.com/training/data-storage/room) for offline-first data storage
- **Background Telemetry**: Android Foreground Service (`BatteryMonitorService`) with battery change `BroadcastReceiver`
- **Hardware & Usage Telemetry**: Android `BatteryManager`, `UsageStatsManager`, and `UsageEvents`
- **Widgets**: Android AppWidget Provider
- **Build System**: Gradle Kotlin DSL (`build.gradle.kts`) with Version Catalog (`libs.versions.toml`)

---

## 🚀 Getting Started

### Prerequisites
- **Android Studio** Ladybug (2024.2.1) or newer
- **JDK 17** or newer
- Android SDK with **API Level 34 (Compile & Target SDK)**, minimum **API Level 26** (Android 8.0)

### Clone & Build
```bash
# Clone the repository
git clone https://github.com/<your-username>/<your-repo-name>.git
cd <your-repo-name>

# Open in Android Studio, or build directly with Gradle
./gradlew assembleDebug
```

### Running Tests
```bash
# Run unit and Robolectric tests
./gradlew testDebugUnitTest
```

---

## 🔒 Privacy & Permissions

- **100% Offline-First**: All battery records, events, and metrics are stored locally on your device in a private SQLite database.
- **No Telemetry Tracking**: No analytics SDKs, trackers, or third-party ads.
- **Permissions Used**:
  - `FOREGROUND_SERVICE` & `FOREGROUND_SERVICE_SPECIAL_USE`: Required to sample charging rate, discharge telemetry, and thermals in the background.
  - `POST_NOTIFICATIONS`: To keep the ongoing battery status notification active while charging and alert about unstable connections.
  - `PACKAGE_USAGE_STATS` *(Optional)*: User-granted permission to compute exact per-app foreground minutes and battery attribution for discharge cycles without root.

---

## 🤖 Built With Google AI Studio

This project was built and iterated with the assistance of **[Google AI Studio](https://ai.studio/)**, leveraging Gemini models to architect the data pipelines, develop the Jetpack Compose Material 3 UI, and implement battery hardware telemetry.

---

## 📄 License

This project is licensed under the [MIT License](LICENSE) (or your preferred license).

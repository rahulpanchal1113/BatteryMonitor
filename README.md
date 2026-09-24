# Battery Monitor ⚡🔋

A modern, privacy-focused Android application for comprehensive battery health diagnostics, live charging power flow telemetry, thermal safety tracking, and stage-by-stage charging speed analytics.

> **Note**: This application was designed and built using **[Google AI Studio](https://ai.studio/)**.

---

## ✨ Features

### 1. 📊 Real-Time Monitor (Dashboard)
- **Live Battery Status**: Instant readout of battery percentage, charging status (AC, USB, Wireless, Battery), cell voltage, temperature, battery health, and battery technology.
- **Live Charging Power Flow**: Visual breakdown of total wall charger power supplied vs. net battery intake power and active device draw (in Watts and mA).
- **Power Consumption & Standby**: Accurate discharge rate and standby draw tracking when running unplugged on battery power.
- **Hardware Fault & Cable Diagnostics**: Real-time detection of unstable connections, intermittent contacts, and faulty cables or adapters.
- **Recent Power Events**: Clean, focused feed showing the last 5 charging events with one-tap deep dive into detailed metrics and temperature curves.

### 2. 📈 Stage-by-Stage Charging Speed & Insights
- **Full-Spectrum Multi-Stage Progression**: When charging across percentage boundaries (e.g., from 15% to 85%), every traversed stage (`0–20%`, `20–40%`, `40–60%`, `60–80%`, and `80–100%`) is captured, analyzed, and updated in the Insights dashboard.
- **Dynamic Speed Ranking**: Color-coded spectrum ranging from the fastest stage (Emerald Green) to top-capacity trickle charging (Warm Orange).
- **Dual Speed Metrics**: Switch seamlessly between **Minutes per Stage** and **Charge Rate (%/hr)**.
- **Thermal Progression Curve**: Continuous temperature tracking throughout charging stages, excluding unrecorded ranges for clean, accurate trends.
- **Thermal Safety & Overheating Records**: Monitored peak temperatures, thermal throttling indicators, and overheat warnings.
- **Background Battery Impact**: Identifies idle consumption patterns and app impact.

### 3. 🗓️ Daily History & Session Details
- **Date-Filtered Timeline**: Review all past charging events and completed charge cycles by day.
- **Detailed Session Bottom Sheet**: Start/end battery level, net percentage gained, duration, average/peak temperatures, and wattage.
- **Data Integrity & Clamping**: Strict monotonic charge verification guarantees non-negative gains across all history items and graphs.
- **Daily Metrics Summary**: Aggregate stats for each day including temperature variance and total charging time.

### 4. ⚙️ Settings, Backup & Customization
- **Temperature Units**: Toggle effortlessly between **Celsius (°C)** and **Fahrenheit (°F)**.
- **Local & Google Drive Backup**: Export and import full historical charging sessions and events via structured JSON.
- **Battery Optimization Tips**: Best practices to maintain lithium-ion battery longevity and prevent premature capacity degradation.

### 5. 📱 Home Screen Widget & Modern Icon
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
  - `FOREGROUND_SERVICE` & `FOREGROUND_SERVICE_SPECIAL_USE`: Required to sample charging rate and thermals while charging in the background.
  - `POST_NOTIFICATIONS`: To keep the ongoing battery status notification active while charging and alert about unstable connections.

---

## 🤖 Built With Google AI Studio

This project was built and iterated with the assistance of **[Google AI Studio](https://ai.studio/)**, leveraging Gemini models to architect the data pipelines, develop the Jetpack Compose Material 3 UI, and implement battery hardware telemetry.

---

## 📄 License

This project is licensed under the [MIT License](LICENSE) (or your preferred license).

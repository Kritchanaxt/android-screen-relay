# Android Screen Relay

Android Screen Relay is a high-performance, system-level Android application designed for peer-to-peer screen mirroring and remote control over a local Wi-Fi network. It operates without an intermediate server or internet connection, utilizing an embedded WebSocket server for low-latency communication.

## ✨ Key Features

### 🖥️ Screen Mirroring & Control
*   **Local Network Streaming:** Stream your screen directly to another device on the same WLAN.
*   **Remote Control (Touch/Input):** Control the Host device remotely from the Client device using accessibility services (clicks, swipes).
*   **Bi-directional Communication:** WebSocket-based architecture handling both video stream output and command input simultaneously.

### 🛡️ System-Level Persistence
*   **Always-On Background Service:** Runs continuously in the background even when the screen is off or the app is closed.
*   **Auto-Start on Boot:** Automatically launches the service when the device boots up using `BootReceiver`.
*   **High Resilience:** Configured with `START_STICKY` and Battery Optimization exemptions to prevent the system from killing the process.

### 📲 User Experience (UI/UX)
*   **Modern Interface:** Built entirely with **Jetpack Compose** and **Material 3**.
*   **Smart Indicators:** Floating "On Air" pill overlay to indicate active broadcasting status.
*   **Easy Connection:**
    *   **Host:** Generates a dynamic QR Code for connection.
    *   **Client:** Integrated CameraX-based QR Scanner for instant pairing.

## 🛠 Tech Stack

*   **Language:** Kotlin
*   **Architecture:** MVVM / Component-based
*   **UI:** Jetpack Compose (Material 3)
*   **Networking:** [Java-WebSocket](https://github.com/TooTallNate/Java-WebSocket) (Embedded Server & Client)
*   **Core Android APIs:**
    *   `MediaProjectionManager`: For efficient screen capture.
    *   `AccessibilityService`: For programmatically dispatching touch gestures.
    *   `ForegroundService`: For maintaining process priority.
    *   `BootReceiver`: For system startup events.
    *   `CameraX` & `ZXing`: For QR code scanning and generation.

## 📱 How to Use

### 1. Prerequisites
*   Ensure both devices are on the **same Wi-Fi network**.
*   Minimum Android SDK: 24 (Android 7.0).

### 2. Setup (One-time)
To enable advanced system features, go to the **Me > System Permissions** tab:
*   **Remote Control (Touch):** Enable Accessibility Service to allow remote clicks.
*   **Run in Background:** Disable Battery Optimization to keep the app running indefinitely.

### 3. Host Device (Sender)
1.  Tap **"Start Broadcasting"** on the Home screen.
2.  Approve the screen recording permission.
3.  The green "On Air" overlay will appear.
4.  Share your **QR Code** by tapping your IP Address.

### 4. Client Device (Receiver)
1.  Tap the **Scan Icon** (Top Right) and scan the Host's QR Code.
2.  The screen stream will appear.
3.  **Interact:** Tap on the client screen to send click commands back to the host.

## ⚙️ Installation

1.  Clone this repository.
    ```bash
    git clone https://github.com/yourusername/android-screen-relay.git
    ```
2.  Open in **Android Studio**.
3.  Sync Gradle and Run.

---
*Developed as a high-performance system Utility using modern Android Standards.*

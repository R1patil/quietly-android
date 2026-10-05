# Quietly AI Ecosystem: Desktop Extension & Android Mobile App

Welcome to the **Quietly** project repository. Quietly is a cross-platform personal AI productivity suite engineered to eliminate digital distractions and draft intelligent communication across desktop and mobile.

---

## 📂 Repository Contents

| Component | Platform | Primary Technologies | Directory |
| :--- | :--- | :--- | :--- |
| **Quietly Browser Extension** | Chrome / Brave / Edge / Kiwi (PC & Android) | Vanilla JS (ES Modules), Manifest V3, CSS3 | [`quietly/`](./quietly/) |
| **Quietly Mobile App** | Android Native (API 24 to 35) | Java 21 LTS, Gradle 8.10.2, Android SDK 35, OkHttp | [`quietly-android/`](./quietly-android/) |
| **Compiled Android APK** | Android Devices (e.g. Vivo Y72 5G) | Signed APK (v1 & v2 signatures, 7.4 MB) | [`app-debug.apk`](./app-debug.apk) |
| **Full Technical Documentation** | Cross-Platform Reference | Markdown (ready for export to PDF) | [`PROJECT_DOCUMENTATION.md`](./PROJECT_DOCUMENTATION.md) |

---

## 🚀 Quick Start

### 1. Quietly Chrome Extension (Desktop)
1. Navigate to `chrome://extensions` in Google Chrome and toggle **Developer mode** on.
2. Click **Load unpacked** and select the [`quietly/`](./quietly/) directory.
3. Open the toolbar popup, input your **Groq API Key** (`gsk_...`), select `qwen/qwen3.8-27b`, and enable the YouTube filter.
4. Enjoy 1-tap replies in WhatsApp Web and a distraction-free, academic YouTube feed.

### 2. Quietly Mobile on Android (Universal Compatibility: Samsung, OnePlus, Vivo, Pixel, Xiaomi, etc.)
1. Copy [`app-debug.apk`](./app-debug.apk) to your Android device.
2. Tap the APK file and select **Install**.
3. Launch **Quietly AI Keyboard**, test your Groq API key, and enable the keyboard in system settings.
4. Access one-tap AI replies in WhatsApp and tap **"📺 Open Clean YouTube"** for ad-free, Shorts-free educational learning.

---

## 📱 Device Compatibility: Universal Android vs. Apple iOS

| Platform / Brand | Compatibility | Technical Details |
| :--- | :---: | :--- |
| **All Android Devices** (Samsung, OnePlus, Google Pixel, Vivo, Xiaomi, Motorola, Oppo, Realme, etc.) |  **Fully Supported** | Works on **Android 7.0 (API 24) to Android 15 (API 35)**. Uses native Android `InputMethodService` framework. The same `.apk` runs on any Android phone. |
| **Vivo Phones** (e.g. Vivo Y72 5G / V21) |  **Lab Verified** | Specifically tested in our lab environment with custom vector icons and Funtouch OS battery optimization safeguards. |
| **Apple iPhone (iOS)** | ❌ **Not Supported (Android APK)** | Android APK files cannot be installed on iOS. Running on iPhone requires a native rewrite in Swift/SwiftUI using Apple's Xcode keyboard extensions. |

---

## 📖 Complete Technical Documentation

For an exhaustive technical breakdown, architectural diagrams, API contracts, root cause analyses, and troubleshooting procedures, please consult **[`PROJECT_DOCUMENTATION.md`](./PROJECT_DOCUMENTATION.md)**.

---

## 📦 Releases & Version Archive

Every release is permanently archived with ready-to-install Android APKs, SHA-256 checksums, and Chrome extension ZIP bundles. You can download current and all previous historical versions at:
👉 **[Quietly Releases on GitHub](https://github.com/R1patil/quietly-android/releases)**


## 👥 Credits & Open-Source Attribution

* **Original Desktop Concept & Chrome Extension:** Created by **Mritunjoy Das** (IIT Madras / ISRO) under the open-source **MIT License**, providing the desktop DOM filtering and OpenRouter triage foundation.
* **Mobile Ecosystem Architecture (`quietly-android`):** Engineered, designed, and ported to native Android by **Rahul Patil ([@R1patil](https://github.com/R1patil))**. This includes the custom Android IME Keyboard service, asynchronous Groq AI client, hardware-accelerated distraction-free YouTube player, and cross-OEM Android compatibility layer.

---

## 📄 License

This project is open-source and distributed under the **[MIT License](LICENSE)**.

# 🎧 Hearing Aid

**A standalone, low-latency Android hearing assistant — turning any smartphone into a real-time, customizable hearing aid.**

[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Android](https://img.shields.io/badge/Platform-Android-3DDC84?logo=android&logoColor=white)](https://developer.android.com)
[![Min SDK](https://img.shields.io/badge/minSdk-21-blue)](https://developer.android.com)
[![Target SDK](https://img.shields.io/badge/targetSdk-36-blue)](https://developer.android.com)
[![License](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)

> Built to help people with hearing difficulties amplify, shape, and clean up the sounds around them — without needing dedicated hardware.

---

## 📖 Overview

**Hearing Aid Pro** is a native Android application that captures live audio from the device microphone, processes it in real time through a configurable digital signal processing (DSP) pipeline, and plays the enhanced audio back through headphones with minimal delay. It's designed as an accessible, low-cost alternative/companion to hardware hearing aids — giving users fine-grained control over amplification, frequency shaping, and background noise suppression.

This isn't a toy amplifier — it implements genuine audio engineering concepts: **biquad parametric EQ filters, STFT Wiener-filter noise cancellation with decision-directed SNR estimation, dynamic range compression, a noise gate, and a real Cooley-Tukey FFT** for live spectrum visualization.

### Why this matters
Millions of people with mild-to-moderate hearing loss don't own a hearing aid due to cost or accessibility barriers. This project explores how far commodity Android hardware + smart software can go in closing that gap.

---

## ✨ Features

| Category | Details |
|---|---|
| 🔊 **Real-Time Amplification** | Ultra-low-latency capture → process → playback pipeline (down to ~128-sample frames, using `VOICE_COMMUNICATION` input + `PERFORMANCE_MODE_LOW_LATENCY` output) |
| 🎛️ **Dynamic Parametric EQ** | Add/remove unlimited EQ bands on the fly. Supports **Peaking, Low-Pass, High-Pass, Band-Pass, Notch, Low-Shelf, and High-Shelf** filters with logarithmic Q factor controls, octave bandwidth hints, and live response curves |
| 🎧 **Adaptive Noise Cancellation** | Decision-directed Wiener-filter gain smoothing; automatically learns ambient background noise and suppresses continuous background hum |
| 🚫 **Noise Gate** | Suppresses low-level background hiss below a configurable amplitude threshold |
| 📉 **Dynamic Range Compression** | Softens loud transients so amplified sound stays comfortable, not painful |
| 📊 **Live Visualization** | Custom-drawn **waveform (time domain)** and **64-band FFT spectrum analyzer (frequency domain)** views, rendered from a real recursive FFT implementation |
| 💾 **Preset Management** | Save, load, rename, and delete custom EQ/volume/gate/compression configurations, persisted locally via `SharedPreferences` (JSON-serialized) |
| 🧩 **Built-in Presets** | Ships with **Flat, Speech Clarity, Bass Boost, High-Frequency Loss,** and **Music** profiles tuned for common hearing-loss patterns |
| 🔐 **Permission-Aware UX** | Graceful microphone permission flow with rationale dialogs and a direct link to system settings if denied |

---

## 🧠 How It Works (Signal Chain)

```
Microphone (AudioRecord, VOICE_COMMUNICATION)
        │
        ▼
 Noise Cancellation  →  Spectral subtraction + Wiener filter gain (learns noise profile)
        │
        ▼
   Noise Gate         →  Attenuates samples below amplitude threshold
        │
        ▼
  Parametric EQ        →  Cascaded biquad filters (N user-defined bands)
        │
        ▼
  Compression          →  Soft-knee style ratio/threshold limiting
        │
        ▼
  Amplification         →  Master gain (0.5x–4.0x) + hard clip guard
        │
        ▼
Speaker/Headphones (AudioTrack, LOW_LATENCY, ACCESSIBILITY usage)
```

In parallel, every Nth frame is pushed into a **512-point FFT processor** to drive the waveform and spectrum visualizations without adding latency to the main audio path.

---

## 🏗️ Architecture & Tech Stack

- **Language:** Kotlin (100%)
- **UI:** Classic Android Views (XML layouts + ViewBinding) with Material Components; Jetpack Compose scaffolding included for future migration
- **Audio:** `android.media.AudioRecord` / `AudioTrack` low-level APIs (no third-party audio libraries — full manual control over latency and buffers)
- **Persistence:** `SharedPreferences` + `org.json` for preset serialization
- **Build System:** Gradle Kotlin DSL (`build.gradle.kts`) with Version Catalogs (`libs.versions.toml`)

### Project Structure
```
app/src/main/java/com/example/hearingaidapp/
├── AudioEngine.kt        # Core DSP pipeline: EQ, noise cancel, gate, compression, biquad math
├── FFTProcessor.kt        # Recursive Cooley-Tukey FFT implementation
├── EQPreset.kt            # Preset data model + JSON (de)serialization
├── PresetManager.kt       # SharedPreferences-backed CRUD for presets
├── MainActivity.kt        # UI orchestration, dialogs, permission handling
├── WaveformView.kt        # Custom Canvas view — time-domain waveform
├── SpectrumView.kt        # Custom Canvas view — 64-band frequency spectrum
└── ui/theme/              # Compose theming (Color, Type, Theme)
```

---

## 🚀 Getting Started

### Prerequisites
- Android Studio **Ladybug** or newer
- JDK 11+
- An Android device or emulator running **API 21+** (physical device strongly recommended — audio latency behavior is not representative on emulators)
- **Wired or Bluetooth headphones** (mandatory to avoid feedback/howling from mic + speaker loopback)

### Clone & Build
```bash
git clone https://github.com/sushantshetty09/HearingAidSoftware.git
cd HearingAidSoftware
./gradlew assembleDebug
```

### Run
```bash
./gradlew installDebug
```
Or simply open the project in Android Studio and hit **Run ▶**.

### First Launch
1. Grant the **microphone permission** when prompted.
2. Plug in headphones (required — this is a live open-mic pipeline).
3. Tap **START HEARING AID**.
4. Adjust **Master Volume**, add EQ filters, or enable **Noise Canceling** from the dashboard.
5. Save your tuned settings as a custom **preset** for next time.

---

## 🎚️ Usage Guide

| Action | How |
|---|---|
| Add an EQ band | Tap **Add Filter** → choose type, frequency, gain, Q → **Add** |
| Edit a band | Tap **Edit** on any filter card → adjust → **Update** |
| Remove a band | Tap **Remove** on a filter card → confirm |
| Enable noise cancellation | Toggle the **Enable** switch under Noise Canceling → stay quiet for a few seconds while it learns the room |
| Reset noise learning | Tap **Reset Noise Profile** if your environment changes |
| Save a configuration | **Save** → name it → confirm |
| Load a configuration | **Load** → tap a preset (long-press for rename/delete options) |

---

## 🗺️ Roadmap

- [ ] Bluetooth Low Energy (BLE) hearing-aid device pairing support
- [ ] Cloud backup/sync of presets across devices
- [ ] Automatic ambient-environment detection (quiet room vs. street vs. café → auto-preset switching)
- [ ] Directional/beamforming mic support on multi-mic devices
- [ ] Full migration to Jetpack Compose UI
- [ ] On-device audiogram-based auto-EQ calibration wizard
- [ ] Wear OS companion app for quick controls

---

## ⚠️ Disclaimer

This application is an **assistive audio amplification tool** and is **not a certified medical hearing aid device**. It is not intended to diagnose, treat, or replace professional audiological care. Always use headphones (never the device's own speaker while its own mic is active) to avoid acoustic feedback, and consult a hearing care professional for clinical hearing loss management.

---

## 📄 License

Distributed under the **MIT License**. See `LICENSE` for more information.

---

## 🙏 Acknowledgments

Built with a focus on real-world accessibility — for anyone who needs a little help hearing the world more clearly.

# Hearing Aid Pro — Comprehensive System & User Guide

**Hearing Aid Pro** is an Android digital hearing assistant that captures live audio via the microphone, processes it in real time through a zero-allocation Digital Signal Processing (DSP) pipeline, and delivers amplified, noise-reduced sound through headphones with ultra-low latency.

---

## Table of Contents
1. [Landing Page Features & Unique Controls](#1-landing-page-features--unique-controls)
2. [Audio Concepts & Terminology](#2-audio-concepts--terminology)
3. [Equalizer Architecture & Tuning](#3-equalizer-architecture--tuning)
4. [Noise Cancellation Mechanism & Best Practices](#4-noise-cancellation-mechanism--best-practices)
5. [Comfort & Protection Tools](#5-comfort--protection-tools)
6. [Meeting & Conversation Recorder](#6-meeting--conversation-recorder)
7. [Personal Hearing Profile Flow](#7-personal-hearing-profile-flow)
8. [Recommended Settings & Operating Criteria](#8-recommended-settings--operating-criteria)
9. [Project Architecture & Source Files](#9-project-architecture--source-files)

---

## 1. Landing Page Features & Unique Controls

Most commercial hearing aid companion apps only provide a generic volume wheel. Hearing Aid Pro provides a real-time audio analysis suite on the landing page:

* **Live Waveform Oscilloscope:** Renders real-time sound vibrations in the time domain with smooth attack/release auto-gain and a manual zoom chip selector (1x, 2x, 4x).
* **48-Band Frequency Spectrum Analyzer:** Visualizes the acoustic energy across 20 Hz to 20,000 Hz using a logarithmic FFT mapping, divided into three distinct color zones: Low (<250 Hz, Violet), Mid (250 Hz – 4 kHz, Teal), and High (>4 kHz, Amber).
* **Meeting & Conversation Recorder:** A one-tap recording card allowing users who struggle to understand spoken words in conversations or lectures to record the processed audio to a 16-bit WAV file and review/share it immediately.
* **Bluetooth Anti-Feedback Safety Gate:** Checks for connected Bluetooth audio (headphones/earbuds) before starting processing, preventing acoustic feedback screeching between the phone's microphone and onboard speakers.
* **Simplified 3-Tone Voice EQ:** Provides immediate adjustment for Voice Clarity, Bass, and Treble without technical complexity, while allowing power users to expand the full Biquad Parametric Filter bank.

---

## 2. Audio Concepts & Terminology

### Master Volume
* **Definition:** The overall linear multiplier applied to the complete audio signal across all frequencies simultaneously.
* **Implementation:** `AudioEngine.setAmplification(gain)` (0.5x to 4.0x / -6 dB to +12 dB).
* **Guideline:** Set between 50% and 75% for typical indoor listening.

### Sound Wave (Time Domain)
* **Definition:** A visual graph showing the physical vibration of sound pressure over time.
  * **Horizontal Axis:** Time (milliseconds). Shows the rhythm and duration of sounds.
  * **Vertical Axis:** Amplitude (loudness). Whispers appear as subtle ripples; loud speech appears as tall peaks.
* **Implementation:** `WaveformView.kt` drawing from a real-time decimated circular buffer.

### Frequency Spectrum (Frequency Domain)
* **Definition:** A graph that breaks down incoming audio into individual pitches from lowest rumble (20 Hz) to highest sparkle (20,000 Hz) using the Fast Fourier Transform (FFT).
* **Implementation:** `SpectrumView.kt` and `FFTProcessor.kt`.

### Low Sound vs. High Sound
* **Pitch Distinction:**
  * **Low Sound (Low Pitch):** Slower vibration frequencies (e.g., bass instruments, engine rumble, 20–250 Hz).
  * **High Sound (High Pitch):** Rapid vibration frequencies (e.g., whistling, birds, consonant sounds like 's' and 'th', 4,000–16,000 Hz).
* **Loudness Distinction:**
  * "Low sound" is also colloquially used to mean *quiet* sound (low amplitude).
  * "High sound" is colloquially used to mean *loud* sound (high amplitude).

### Bass
* **Definition:** Low-frequency audio ranging from 20 Hz to 500 Hz. Provides body, warmth, and resonance.
* **Guideline:** Keep at or near 0 dB (neutral). Over-boosting bass creates a boomy, muffled experience that obscures human speech.

### Treble
* **Definition:** High-frequency audio ranging from 3,000 Hz to 16,000 Hz. Provides brightness, presence, and consonant articulation.
* **Guideline:** Most age-related hearing decline (Presbycusis) manifests in the high-frequency range. Boosting Treble by +3 dB to +5 dB makes whispered words and soft speech crisp and intelligible.

### Voice Clarity (Speech Presence)
* **Definition:** The human vocal range between 500 Hz and 3,000 Hz. This region carries the fundamental intelligibility of human speech (vowels and primary consonants).
* **Guideline:** Boost to +2 dB to +4 dB in social environments or noisy rooms to bring speakers forward.

---

## 3. Equalizer Architecture & Tuning

Hearing Aid Pro uses a dual-tier approach:

### Primary Sliders (Casual Users)
1. **Voice Clarity (500 Hz – 3,000 Hz):** Focuses specifically on speech frequencies.
2. **Bass (< 500 Hz):** Adjusts low-end fullness.
3. **Treble (> 3,000 Hz):** Adjusts brightness and air.

### Quick Presets
* **Speech Clarity:** Boosts speech presence (+2 dB to +4 dB) and attenuates extreme low/high ends to eliminate distractions.
* **Restaurant:** Moderate noise gating with focused speech presence to isolate nearby companions.
* **Home:** Relaxed, natural curve for TV and quiet indoor environments.
* **Music:** Enhanced bass (+3 dB) and high treble (+3 dB) for rich acoustic playback.
* **Flat:** Neutral 0 dB reference across all bands.

### Expandable Expert EQ (Biquad Filter Bank)
Allows adding unlimited custom filter bands with configurable:
* **Filter Types:** Peaking, Low-Pass, High-Pass, Band-Pass, Notch, Low-Shelf, High-Shelf.
* **Q Factor:** Logarithmic mapping (0.1 to 10.0) with real-time octave bandwidth calculation ($BW \approx 2 \cdot \operatorname{arsinh}(1 / (2Q)) / \ln 2$).
* **Live Curve Preview:** Draws the continuous transfer function $H(z)$ dynamically as Q, gain, or frequency change.

---

## 4. Noise Cancellation Mechanism & Best Practices

The app employs a real-time **Short-Time Fourier Transform (STFT) with Decision-Directed Wiener Filtering (Ephraim-Malah algorithm)**.

```
Incoming Audio (16-bit PCM)
           │
           ▼
[Hann Window & 512-pt FFT (50% Overlap)]
           │
     ┌─────┴──────────┐
     ▼                ▼
Magnitude |Y|     Phase arg(Y)
     │                │
     ▼                │
[Noise Profile Learning] (50 initial quiet frames)
     │                │
     ▼                │
[Decision-Directed a priori SNR (Ephraim-Malah)]
     │                │
     ▼                │
[Wiener Gain G_k = ξ_k / (ξ_k + 1)] (Floor = 0.16)
     │                │
     ▼                │
Filtered Magnitude ───┴──► [Inverse FFT & Overlap-Add] ──► Output
```

### Operational Steps:
1. **Turn on Noise Cancellation:** Toggle the switch on the Home tab.
2. **Learning Period (2–3 seconds):** Keep quiet for a few seconds. The engine analyzes 50 frames of ambient sound to establish the baseline stationary noise spectrum (fans, traffic, air conditioning).
3. **Active State:** The status changes to *"Active (Speech Protected)"*. The Wiener filter suppresses stationary background frequencies while preserving conversational transients.
4. **Reset:** If moving to a different room, tap **Reset Noise Profile** to retrain the baseline.

### Recommended Limits:
* **Recommended Strength:** **60% to 75%**.
* **Limit Warning:** Do not leave strength at 100% permanently. Over-subtraction can introduce subtle "musical noise" (slight watery artifacts) in quiet speech.

---

## 5. Comfort & Protection Tools

* **Noise Gate:** Mutes or attenuates low-level microphone hiss when the user is in a completely silent room.
  * *Threshold range:* 0% to 100% (0.001 to 0.200 amplitude).
  * *Recommended:* 10% to 20%.
* **Dynamic Range Compression (Limiter):** Prevents sudden loud noises (dropped plates, sirens, clapping) from reaching painful amplification levels by applying soft-knee curve compression.
  * *Ratio range:* 1:1 to 8:1.
  * *Recommended:* 30% to 50%.

---

## 6. Meeting & Conversation Recorder

* Located on the Home tab below the Spectrum Analyzer.
* Tapping **Record** writes processed 16-bit 44.1 kHz PCM audio directly to a standard `.wav` file in the app's external files directory.
* When finished, tap **Stop**. The app automatically opens an Android system share/review dialog to play back, save, or share the recording.

---

## 7. Personal Hearing Profile Flow

1. Adjust the Master Volume, Tone Sliders, and Noise Cancellation on the Home screen until the sound is comfortable for your ears.
2. Tap **"Save as My Profile"**.
3. The configuration is permanently written to your profile.
4. Navigate to the **Profile** tab at any time: your profile name, age, and picture are shown alongside a single-tap **"Apply My Profile"** button, restoring your optimal settings instantly without manual re-adjustment.

---

## 8. Recommended Settings & Operating Criteria

| Parameter | Recommended Setting | Purpose |
|---|---|---|
| **Master Volume** | 50% – 70% | Clear listening without background overload |
| **Voice Clarity** | +2 dB to +4 dB | Sharpens conversational speech |
| **Bass** | 0 dB (Neutral) | Prevents boomy or muffled audio |
| **Treble** | +3 dB to +5 dB | Compensates for typical high-frequency loss |
| **Noise Canceling** | Enabled @ 65% | Suppresses fan/AC noise without audio distortion |
| **Noise Gate** | 15% | Cuts microphone hiss in quiet rooms |
| **Compression** | 35% | Protects ears against sudden loud bursts |

---

## 9. Project Architecture & Source Files

* [`AudioEngine.kt`](file:///c:/Users/susha/OneDrive/Desktop/HearingAid/app/src/main/java/com/example/hearingaidapp/AudioEngine.kt): Core zero-allocation DSP engine, AudioRecord/AudioTrack loop, Biquad filter implementations, and STFT Wiener noise reduction.
* [`HomeFragment.kt`](file:///c:/Users/susha/OneDrive/Desktop/HearingAid/app/src/main/java/com/example/hearingaidapp/HomeFragment.kt): Landing page controller wiring tone sliders, quick presets, visualizers, and recorder.
* [`ProfilesFragment.kt`](file:///c:/Users/susha/OneDrive/Desktop/HearingAid/app/src/main/java/com/example/hearingaidapp/ProfilesFragment.kt): Manages user profile identity and the personal hearing profile restore flow.
* [`WaveformView.kt`](file:///c:/Users/susha/OneDrive/Desktop/HearingAid/app/src/main/java/com/example/hearingaidapp/WaveformView.kt): Time-domain oscilloscope view with multi-gain scaling.
* [`SpectrumView.kt`](file:///c:/Users/susha/OneDrive/Desktop/HearingAid/app/src/main/java/com/example/hearingaidapp/SpectrumView.kt): 48-band logarithmic FFT frequency visualizer.
* [`RecordingManager.kt`](file:///c:/Users/susha/OneDrive/Desktop/HearingAid/app/src/main/java/com/example/hearingaidapp/RecordingManager.kt): Meeting audio recorder with WAV header serialization.
* [`EQPreset.kt`](file:///c:/Users/susha/OneDrive/Desktop/HearingAid/app/src/main/java/com/example/hearingaidapp/EQPreset.kt): Preset data model and JSON serialization.

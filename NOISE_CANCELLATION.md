# 🎧 STFT Noise Cancellation Engine & Architecture

## Overview

The noise cancellation subsystem in **Hearing Aid Pro** implements a real-time, zero-allocation Short-Time Fourier Transform (STFT) spectral enhancement engine. It uses a **Decision-Directed A Priori SNR Estimation algorithm (Ephraim-Malah style)** combined with a **Wiener Filter** to suppress continuous background noise (such as fans, air conditioning, traffic hum, or equipment noise) while keeping speech natural and clear.

---

## 🏗️ Signal Processing Block Diagram

```
                 ┌─────────────────────────────┐
                 │  Input PCM Audio (16-bit)   │
                 └──────────────┬──────────────┘
                                │ (512-sample frame accumulator, 256 hop)
                                ▼
                 ┌─────────────────────────────┐
                 │  Hann Window & 512-pt FFT   │
                 └──────────────┬──────────────┘
                                │
                        ┌───────┴───────┐
                        │               │
                        ▼               ▼
                ┌──────────────┐ ┌──────────────┐
                │ Magnitude |Y|│ │  Phase arg(Y)│
                └───────┬───────┘ └───────┬──────┘
                        │                 │
                        ▼                 │
      ┌─────────────────────────────────┐ │
      │ Ambient Noise Profile Estimation │ │
      │ (Averages 50 silent/quiet frames)│ │
      └─────────────────┬───────────────┘ │
                        │                 │
                        ▼                 │
      ┌─────────────────────────────────┐ │
      │ Decision-Directed SNR Estimate │ │
      │   γ_k = |Y_k|^2 / N_k^2         │ │
      │   ξ_k = α (P_prev / N_k^2) +    │ │
      │         (1-α) max(γ_k - 1, 0)   │ │
      └─────────────────┬───────────────┘ │
                        │                 │
                        ▼                 │
      ┌─────────────────────────────────┐ │
      │      Wiener Filter Gain         │ │
      │      G_k = ξ_k / (ξ_k + 1)      │ │
      │   G_final = (G_k)^strength       │ │
      └─────────────────┬───────────────┘ │
                        │                 │
                        ▼                 │
            ┌──────────────────────┐      │
            │ Enhanced Magnitude   │      │
            │ |X_clean| = |Y| * G  │      │
            └───────────┬──────────┘      │
                        │                 │
                        └────────┬────────┘
                                 │
                                 ▼
                 ┌─────────────────────────────┐
                 │  512-pt Inverse FFT (IFFT)  │
                 └──────────────┬──────────────┘
                                │
                                ▼
                 ┌─────────────────────────────┐
                 │ 50% Overlap-Add (COLA) FIFO │
                 └─────────────────────────────┘
```

---

## 📊 Engine Parameters Reference

| Parameter | Default Value | Description |
|---|---|---|
| **`stftSize`** | `512` | FFT frame length in samples (~10.6 ms at 48 kHz) |
| **`stftHop`** | `256` | Hop size in samples (50% overlap for Constant Overlap-Add reconstruction) |
| **`stftBins`** | `257` | Number of unique positive frequency bins (`512 / 2 + 1`) |
| **`maxNoiseProfileSamples`** | `50` | Number of initial frames averaged to build the noise floor estimate (~2 seconds) |
| **`alphaDD`** | `0.92` | Temporal decision-directed smoothing factor ($\alpha$) for a priori SNR stability |
| **`overSubtractionFactor`** | `2.0` | Over-subtraction multiplier on noise power estimate to eliminate noise bleed |
| **`spectralFloor`** | `0.01` | Minimum allowable Wiener filter gain floor (-40 dB) to prevent musical noise artifacts |
| **`adaptRate`** | `0.01` | Continuous background adaptation rate during low-energy quiet pauses |

---

## 🧠 Algorithmic Details

1. **Windowing & Transformation:**
   Each 512-sample input frame is multiplied by a pre-computed **Hann window** $w[n] = 0.5 \left(1 - \cos\left(\frac{2\pi n}{N}\right)\right)$ before running in-place complex FFT to eliminate boundary discontinuities and spectral leakage.

2. **Noise Profile Calibration:**
   Upon enabling or resetting noise cancellation, the engine captures the magnitude spectrum $|Y_k|$ across the first 50 frames while the user remains quiet. The average magnitude per bin $k$ forms `noiseProfile[k]`.

3. **Decision-Directed A Priori SNR Estimation:**
   To prevent single-frame magnitude fluctuations from causing musical noise artifacts:
   $$\gamma_k = \frac{|Y_k|^2}{\text{noisePower}_k}$$
   $$\xi_k = \alpha_{DD} \frac{|X_{\text{prev}, k}|^2}{\text{noisePower}_k} + (1 - \alpha_{DD}) \max(\gamma_k - 1, 0)$$

4. **Wiener Gain Computation:**
   Optimal linear mean-square error Wiener gain is computed per frequency bin:
   $$G_k = \frac{\xi_k}{\xi_k + 1}$$
   The final gain $G_{\text{final}} = (G_k)^{\text{strength}}$ is clamped to `spectralFloor` (0.01) and smoothed over time.

5. **Inverse FFT & Overlap-Add (OLA):**
   The cleaned magnitude is recombined with original frame phase $\angle Y_k$, synthesized via Inverse FFT, and combined using a 256-sample overlap-add buffer FIFO.

---

## ⚡ Performance & Constraints

- **Zero Memory Allocation:** All STFT buffers (`stftInputBuf`, `stftWorkReal`, `stftWorkImag`, `stftMag`, `stftPhase`, `olaBuffer`) are pre-allocated during engine initialization. No garbage collection triggers occur on the audio thread.
- **Latency:** Adds exactly 1 hop of delay (`256 samples` = **~5.3 ms at 48 kHz**).
- **Target Noise Types:** Highly effective against stationary background noise (AC units, fans, engine hum, road noise).
- **Known Limitations:** Non-stationary transient noises (clapping, sudden drops) or background multi-speaker chatter (babble noise) require spatial beamforming or deep learning models beyond single-channel STFT.

---

## 📈 Real-Time Noise Reduction Frequency Spectrum (`NoiseSpectrumView`)

To give users transparency into the spectral subtraction process, the Noise Reduction card features a live dual-spectrum graph rendered via `NoiseSpectrumView.kt`:

1. **Spectral Layers Visualized:**
   - **Input (Before) Spectrum:** Pre-reduction FFT magnitude curve $|Y_k|$ (muted violet `#9E9EB4`).
   - **Output (After) Spectrum:** Post-Wiener gain FFT magnitude curve $|X_k| = |Y_k| \cdot G_k$ (primary cyan `#00E5FF`).
   - **Noise Floor Profile:** Learned environmental background noise spectrum (dashed amber `#FFAB00`), visible once calibrated.
   - **Reduction Shading:** Optional toggle chip ("Show reduction") that shades the attenuation gap between before and after curves.
2. **Frequency & Amplitude Axes:**
   - **X Axis:** Logarithmic scale spanning 50 Hz to 16,000 Hz, with grid markers and labels at 100 Hz, 500 Hz, 1 kHz, 5 kHz, and 10 kHz.
   - **Y Axis:** dBFS magnitude from -90 dBFS to 0 dBFS ($20 \log_{10}(\text{mag} + 10^{-9})$) with horizontal grid lines every 20 dB.
3. **Lock-Free Audio Thread Data Path:**
   - Pre-allocated double-buffered snapshots (`snapBefore`, `snapAfter`, `snapNoise`, `snapGain`) published every 6 STFT frames (~31 fps).
   - Zero lock, zero memory allocation on the real-time audio thread (`AudioEngine.copyNoiseSpectrum`).
   - UI polls via `Choreographer` on the main thread throttled to ~30 fps, automatically stopped in background or when noise reduction is disabled to conserve battery.

---

## 🎚️ Extended Equalizer Gain Limit (+30 dB)

The per-filter EQ gain range has been widened from -20 dB .. +20 dB to **-20 dB .. +30 dB**:
- **UI Mapping:** 50-step slider with 0 dB centered at progress 20 (`GAIN_MIN_DB = -20f`, `GAIN_MAX_DB = 30f`).
- **Safety & Limiter:** The soft-knee tanh limiter (`applySoftLimiter`) and automatic headroom scaling compress extreme multi-band boosts to prevent clipping and protect hearing.
- **Acoustic Feedback Protection:** Single-bin peak howling detection ducks master gain by -12 dB within 40 ms if high-gain acoustic loop feedback is detected.
- **High-Gain User Guidance:** A non-blocking hint alerts the user when dragging gain above +20 dB: *"High gain can cause feedback. Increase slowly."*


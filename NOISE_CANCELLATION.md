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

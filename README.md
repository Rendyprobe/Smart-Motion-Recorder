Motion-triggered video recorder built with Kotlin, Jetpack Compose, and CameraX. The app monitors the camera feed, detects motion on-device, and records clips when movement is present.

⚠️ Peringatan: proyek ini milik saya. Jangan gunakan, unggah ulang, atau distribusikan ulang repo ini tanpa izin tertulis dari pemilik.

## Requirements
- Android 7.0+ (API 24) minimum; targetSdk 34.
- Kamera wajib, mikrofon diminta untuk rekam suara (opsi audio selalu aktif). Post-notifications diminta hanya di Android 13+.
- Tidak perlu WRITE_EXTERNAL_STORAGE; penyimpanan memakai folder aplikasi atau Movies/MediaStore (Android 10+).

## How to run (Android Studio)
1. Open this project in Android Studio (Giraffe or newer).
2. Build/Run on a device (USB or Wi‑Fi debug). Android 7/7.1 devices are supported.
3. Pada peluncuran pertama beri izin Kamera, Mikrofon, dan Notifikasi (Android 13+).
4. Tekan **Start Monitoring**: aplikasi memantau & merekam otomatis. Notifikasi foreground tetap terlihat; gunakan tombol **Stop Monitoring** atau aksi di notifikasi untuk berhenti.

## Behavior
- Activity mengelola CameraX Preview + ImageAnalysis + VideoCapture; monitoring memakai notifikasi foreground transparan.
- Channel notifikasi dibuat hanya di API 26+. Pada Android 7/7.1 notifikasi memakai NotificationCompat tanpa channel. Aksi “Stop” menghentikan monitoring.
- Motion detection uses a frame-difference algorithm on the Y plane:
  - Downsamples pixels by `downsampleStride`, samples every `analyzeEveryNthFrame`.
  - Counts pixels whose luminance changes more than `pixelDiffThreshold`.
  - Computes ratio and a 3-frame moving average; motion triggers when the average exceeds `motionRatioThreshold` for `consecutiveMotionFrames`.
  - Very high spikes (>0.6) are clamped/ignored on the first occurrence to reduce exposure-jump noise.
- Recording state machine:
  - IDLE → MONITORING when service starts.
  - MONITORING → RECORDING on motion (respecting cooldown).
  - RECORDING → MONITORING after `stopDelayMs` without motion; cooldown prevents immediate retrigger.
- Penyimpanan:
  - Mode default: `context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)/<folder>/motion_yyyyMMdd_HHmmss.mp4` (aman di Android 7–9, tanpa izin storage).
  - Mode MediaStore (Android 10+): Movies/<folder>/motion_yyyyMMdd_HHmmss.mp4 langsung ke galeri.
- UI (Compose):
  - Live preview (CameraX PreviewView)
  - Start/Stop monitoring toggle, status (IDLE/MONITORING/RECORDING), motion ratio display, last saved file name.
  - Event log shows monitoring/recording events and errors.
  - Settings panel di layar utama untuk threshold sensivitas, stride, analyzeEveryNthFrame, dan opsi penyimpanan.
- Upload (opsional): pilih folder melalui Storage Access Framework (mis. folder di Google Drive). Tidak perlu OAuth GCP; rekaman disalin ke folder yang dipilih via WorkManager sesuai preferensi jaringan (Wi‑Fi only/apa saja).
- Penyimpanan “Custom (SAF/MediaStore)” di Android 10+ menaruh file ke `Movies/<folder>` sesuai nama folder yang dipilih; di Android 7–9 tetap di folder aplikasi.

## Tuning tips
- Increase `motionRatioThreshold` and `pixelDiffThreshold` to reduce false triggers in noisy lighting.
- Raise `consecutiveMotionFrames` to require more consistent movement.
- Increase `stopDelayMs` if recordings stop too quickly; increase `cooldownMs` to reduce frequent short clips.
- Use a larger `downsampleStride` or `analyzeEveryNthFrame` for lower CPU usage on older devices.

## Known limitations
- Preview and processing share the same camera session; binding errors stop monitoring and require restarting the service.
- No background upload/sharing pipeline; sharing must be manual using the saved file path.
- The simple frame-diff detector can misfire with dramatic exposure or focus shifts; thresholds may need tuning per environment.

## Privasi & kepatuhan
- Semua analisis gerakan berlangsung di perangkat; tidak ada data kamera/mikrofon yang dikirim ke server.
- Notifikasi foreground aktif saat monitoring/rekam untuk transparansi.
- Sertakan tautan kebijakan privasi di listing Play Store dan pastikan form Data Safety menyebut penggunaan kamera/mikrofon dan penyimpanan lokal.

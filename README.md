# Download Manager

A native Android download manager built with Kotlin and Jetpack Compose. It accelerates
downloads with multi-connection segmented transfers, supports true pause/resume, and
detects download links handed off from browsers, the share sheet, and the clipboard.

## Features

- **Accelerated downloads** — files are split into up to 16 byte ranges downloaded in
  parallel over HTTP Range requests, each written directly into its region of a single
  pre-allocated file (no merge step). Per-segment retry with exponential backoff means a
  hiccup on one connection never kills the whole download.
- **Pause / resume** — segment offsets are persisted to Room every 500 ms, so downloads
  survive app kills and reboots. Resume revalidates with `If-Range` (ETag/Last-Modified)
  and restarts cleanly if the remote content changed.
- **Download detection**
  - *Browser hand-off*: the app registers for `ACTION_VIEW` on common file links and
    binary MIME types, so "Open with" from a browser lands here.
  - *Share sheet*: share any link (or text containing one) to the app.
  - *Clipboard*: copying a URL and opening the app offers to download it.
  - *System downloads*: downloads started by other apps through Android's
    `DownloadManager` are listed in a read-only "System" tab.
- **Live download list** — progress, speed (3-second rolling window), ETA, and status for
  every download; a detail screen visualizes per-segment progress.
- **Foreground service** — active downloads run in a `dataSync` foreground service with
  per-download notifications offering Pause/Cancel actions.
- **Settings** — parallel downloads (1–10), connections per download (1–16), Wi-Fi-only
  (auto-pauses on metered networks), clipboard detection toggle.
- Completed files are published to the public **Downloads/** collection via MediaStore
  (API 29+) or the legacy external storage path (API 26–28).

## Tech stack

| Concern | Choice |
|---|---|
| Language / UI | Kotlin, Jetpack Compose (Material 3) |
| Architecture | MVVM + Repository, single activity |
| Networking | OkHttp |
| Persistence | Room (downloads + segments), DataStore (settings) |
| DI | Hilt |
| Async | Coroutines + Flow |

Min SDK 26 (Android 8.0) · Target SDK 35.

## Module layout

```
app/src/main/java/com/genesiscruz/downloadmanager/
├── data/        Room entities/DAO, repository, settings DataStore
├── engine/      DownloadEngine, DownloadTask, SegmentDownloader,
│                Segmentation, SpeedTracker, NetworkMonitor, FileFinalizer
├── service/     DownloadService (foreground), SystemDownloadObserver
├── detect/      UrlUtils, ClipboardMonitor
├── receiver/    NotificationActionReceiver, BootReceiver
└── ui/          Compose screens: list, add sheet, detail, settings
```

## How the engine works

1. **Probe** — `HEAD` (falling back to a 1-byte ranged `GET`) learns the size,
   `Accept-Ranges` support, ETag/Last-Modified, and filename (Content-Disposition).
2. **Plan** — `Segmentation.plan` splits known-size, range-capable files ≥ 2 MB into N
   contiguous ranges; otherwise a single (possibly unbounded) segment.
3. **Transfer** — each segment runs `GET Range: bytes=from-to` with `If-Range` on its own
   coroutine, writing via `RandomAccessFile` at the segment offset.
4. **Persist** — a flusher coroutine saves per-segment offsets and total progress every
   500 ms and on pause/cancel.
5. **Finalize** — the temp file is moved into the public Downloads collection and the
   download is marked completed.

## Building

```bash
./gradlew assembleDebug        # build the APK
./gradlew testDebugUnitTest    # run unit tests
```

Unit tests exercise the full engine against OkHttp `MockWebServer` with real HTTP
Range/If-Range semantics: multi-segment assembly, single-connection fallback, resume
offsets, and changed-content restart.

CI (GitHub Actions) builds the APK and runs the tests on every push.

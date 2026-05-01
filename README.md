# Android Signage Media Player System

A two-app system for Android TV / Signage displays with WiFi-based media uploading from your phone.

---

## System Overview

```
[Your Phone]  ──WiFi──►  [TV/Signage Android Device]
 PhoneUploader             SignagePlayer
 (upload app)              (continuous playback)
```

---

## App 1: SignagePlayer (TV / Signage Display)

### What it does
- Plays **videos and photos** continuously in a loop (no gaps, no user input needed)
- **Auto-starts on boot** — plug in the TV and it plays
- Runs a **built-in HTTP server on port 8080** to receive new files over WiFi
- Shows IP address on screen so you know where to upload
- When new files are uploaded, the playlist **auto-updates live**
- Works on Android TV, Fire TV, Android Signage tablets, and regular Android

### Features
| Feature | Detail |
|---|---|
| Video formats | MP4, MKV, AVI, MOV, WMV, WebM, 3GP, M4V |
| Image formats | JPG, PNG, GIF, BMP, WebP, HEIC |
| Image display time | 8 seconds per image |
| Video | Plays full duration, then next |
| Loop | Continuous — goes back to start after last file |
| Boot autostart | Yes — starts playing on device reboot |
| Upload web UI | Built-in at http://[TV-IP]:8080 |
| Screen | Fullscreen, landscape, HUD bar at bottom |

### HUD Bar (bottom of screen)
```
● LIVE    [current filename.mp4]    5 files    http://192.168.1.100:8080
```

---

## App 2: PhoneUploader (Your Phone)

### What it does
- Simple app to **pick videos/photos** from your phone gallery
- Sends them to the TV over WiFi
- Tests connection before uploading
- Shows upload progress per file

---

## Setup Instructions

### Step 1 — Install SignagePlayer on TV/Display
1. Open the `SignagePlayer` project in **Android Studio**
2. Connect your TV/display device via USB (enable Developer Options + USB Debugging)
3. Click **Run** — it will install and launch
4. Note the IP address shown at the bottom of the screen (e.g. `http://192.168.1.100:8080`)
5. Make sure your TV and phone are on the **same WiFi network**

### Step 2 — Install PhoneUploader on your Phone
1. Open the `PhoneUploader` project in **Android Studio**
2. Connect your phone via USB
3. Click **Run**

### Step 3 — Upload and Play
1. Open **Signage Upload** on your phone
2. Enter the TV's IP address (shown on the TV screen) and tap **Connect**
3. Tap **Pick Videos & Photos** → select your files
4. Tap **Upload to TV**
5. Files will appear on TV instantly and start playing in the loop

---

## File Storage Location (on TV)

Uploaded files are saved to:
```
/sdcard/Android/data/com.signage.player/files/SignageMedia/
```
You can also copy files here manually via USB.

---

## Web Upload (Alternative — No Phone App Needed)

Open a browser on any device on the same WiFi and go to:
```
http://[TV-IP-ADDRESS]:8080
```
You'll see a drag-and-drop upload page. Works from laptop, phone browser, tablet, etc.

---

## Project Structure

```
SignagePlayer/
├── app/src/main/java/com/signage/player/
│   ├── ui/
│   │   ├── MainActivity.kt       ← Main screen with HUD
│   │   └── PlayerFragment.kt     ← Video/Image playback loop
│   ├── service/
│   │   ├── FileServerService.kt  ← HTTP server (NanoHTTPD)
│   │   └── MediaScannerService.kt← Watches folder for new files
│   ├── model/
│   │   └── MediaFile.kt          ← Data model
│   ├── utils/
│   │   └── MediaFileManager.kt   ← File scanning & management
│   └── receiver/
│       └── BootReceiver.kt       ← Auto-start on reboot
└── app/build.gradle              ← Includes NanoHTTPD dependency

PhoneUploader/
├── app/src/main/java/com/signage/uploader/
│   ├── ui/
│   │   └── MainActivity.kt       ← Upload UI
│   └── network/
│       └── UploadManager.kt      ← HTTP multipart upload
└── app/build.gradle
```

---

## Dependencies

### SignagePlayer
- `org.nanohttpd:nanohttpd:2.3.1` — embedded HTTP server
- `org.nanohttpd:nanohttpd-webserver:2.3.1`
- AndroidX AppCompat, Core KTX

### PhoneUploader
- AndroidX AppCompat, Core KTX
- `kotlinx-coroutines-android` — for async uploads

---

## Android Studio Requirements
- Android Studio Hedgehog or later
- Kotlin 1.9+
- AGP 8.1+
- Min SDK 21 (Android 5.0+)
- Target SDK 34

---

## Tips

- **Image duration**: Change `imageDuration = 8000L` in `PlayerFragment.kt` to adjust (ms)
- **Order**: Files play in the order they were added. Rename files with `01_`, `02_` prefix to control order
- **Delete files**: Use a file manager app to navigate to the SignageMedia folder and delete
- **Multiple displays**: Install SignagePlayer on each TV — they each get their own upload URL
- **No internet needed**: Everything works over local WiFi only

---

## Troubleshooting

| Problem | Solution |
|---|---|
| Can't connect from phone | Make sure both devices are on same WiFi. Check TV IP on screen. |
| Video doesn't play | Check format — MP4 H.264 is most compatible |
| App crashes on start | Grant storage permissions in Android Settings |
| Files not showing | Check `/SignageMedia` folder has files. Restart app. |
| Server not starting | Port 8080 conflict — reboot TV device |

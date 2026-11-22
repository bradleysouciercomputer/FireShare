# FireShare

FireShare is a local-first media hub for Amazon Fire TV. It lets you upload photos and videos from a phone or computer on the same Wi-Fi network, then browse and play them on your television.

## Features

- Native Android TV / Fire TV interface designed for a remote control.
- Built-in local Wi-Fi upload page with a QR code and a one-time six-digit PIN.
- Multi-file photo and video uploads from a phone or PC browser.
- Media library stored privately in the app's storage on the Fire TV.
- Photo viewing and video playback.
- Open and delete controls directly on each TV library item, with a confirmation before permanent deletion.
- Delete files from the TV in the browser upload page or directly with the Fire TV remote.
- No cloud account and no third-party media hosting.

## Install on Fire TV

1. Download **FireShare.apk** from the [latest GitHub Release](https://github.com/bradleysouciercomputer/FireShare/releases/latest).
2. Open the Downloader app on the Fire TV and enter the direct APK URL:

   `https://github.com/bradleysouciercomputer/FireShare/releases/latest/download/FireShare.apk`

3. Allow Downloader to install unknown apps when Fire TV asks.
4. Open FireShare from your Apps library.

## Manage media on the TV

Use the Fire TV remote to move between each library item's **Open** and **Delete** buttons. Selecting Delete opens a confirmation dialog; the item is only removed after you confirm. Deletion removes the file from FireShare's private media folder on the TV.

## Send media to the TV

1. Keep the Fire TV and the phone/computer on the same Wi-Fi network.
2. Open FireShare on the TV. Sharing starts automatically.
3. Scan the QR code with your phone camera, or type the displayed `http://IP:8765` address into a browser.
4. Enter the six-digit PIN displayed on the TV.
5. Choose or drag photos and videos into the upload area.

Uploads are saved in FireShare's private app storage on the Fire TV. Keep FireShare open while transferring files. The upload page is available only while the in-app sharing server is running. The server uses a random PIN for each app session and is intended for trusted devices on your home network.

## Build

The repository includes a GitHub Actions workflow at `.github/workflows/android.yml`.

- Every push to `main` builds a debug APK and uploads a 30-day Actions artifact.
- Successful builds on `main` also update the rolling **latest** GitHub Release.
- Pushing a version tag such as `v1.0.1` creates a versioned release.

The APK is built with the Android Gradle Plugin and Kotlin. To build locally, install JDK 17, Android SDK platform 35, and Gradle 8.9, then run:

```bash
gradle assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Notes

- This first version stores media in app-private storage. Clearing FireShare's app data or uninstalling it removes those uploads.
- Playback depends on the codecs supported by the Fire TV device. MP4/H.264 is the safest video format for broad compatibility.
- The upload server is local-network only; it does not expose files through a cloud service.

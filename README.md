# MinnalStream — Hotspot Preview

A free, open-source Android-to-Windows screen streaming prototype designed for a laptop-hosted Wi-Fi hotspot. It does **not** use the phone's built-in Cast/Miracast discovery. Instead, the Android sender asks Android for screen-capture permission and sends hardware-encoded H.264 over TCP to the laptop's local hotspot IP.

> **Preview status:** Source project and Windows receiver launcher are provided. An APK is not included in this ZIP because this environment does not have the Android SDK/Gradle build environment available. Push the project to GitHub and the included Actions workflow can build a debug APK, or open it in Android Studio and build `app`.

## Requirements

- Windows 10/11 with Mobile hotspot support.
- Android 8+; iQOO Z7 5G is the target test phone.
- FFmpeg's `ffplay.exe` installed on Windows. Run `Setup-Windows.bat` to try installing it via winget.
- OBS Studio is optional for recording the receiver window.

## Windows setup

1. Extract the ZIP.
2. Double-click `Setup-Windows.bat` to install FFmpeg, or install FFmpeg manually and ensure `ffplay.exe` is on PATH.
3. Turn on **Settings → Network & internet → Mobile hotspot**.
4. Run `windows/Run-Receiver.bat`.
5. Click **Start receiver**. Allow Windows Firewall if it asks. The receiver listens on TCP port 5000.
6. Confirm the laptop hotspot IPv4 address. Windows commonly uses `192.168.137.1`; if yours differs, use the actual address displayed by the receiver or run `ipconfig` and inspect the Microsoft Wi-Fi Direct Virtual Adapter / hotspot adapter.

## Android app setup

1. Build the debug APK with Android Studio, or download the workflow artifact after pushing this repository to GitHub and running **Actions → Build Android preview APK**.
2. Install the APK on the iQOO. Android may ask you to permit installing apps from the source you used.
3. Connect the phone to the laptop hotspot.
4. Open MinnalStream, enter the laptop hotspot IP (often `192.168.137.1`) and port `5000`.
5. Choose 60 FPS and 1080p first. Tap **Start wireless streaming** and approve Android's screen-capture prompt.
6. In Windows Firewall, allow the receiver on the hotspot network if prompted.

## Architecture

- Android `MediaProjection` for user-approved screen capture.
- Android `MediaCodec` H.264 hardware encoder.
- TCP socket over the laptop hotspot LAN (port 5000).
- FFplay decodes and displays the H.264 stream.
- OBS can capture the FFplay window for recording.

## Important limitations

- **This is an experimental preview, not a verified gaming-ready build.** It has not been compiled or tested on the iQOO/Windows hardware in this environment.
- The current prototype streams video only; audio forwarding and built-in recording are not implemented. Use OBS to record the receiver window.
- The receiver uses FFplay and raw H.264 over TCP. TCP retransmissions can increase latency under packet loss. It is a simple first MVP, not the final low-latency transport.
- 1080p/120 FPS is a target only. The Android encoder, display refresh rate, receiver and network must all support it. Start at 720p/60 or 1080p/60, then measure.
- The default hotspot IP `192.168.137.1` is common on Windows but not guaranteed. Verify with `ipconfig`.
- This sender uses Android's standard screen capture permission prompt and persistent foreground notification. It does not require USB debugging or Developer Options.
- Android may restrict capture of protected content or some secure screens.

## Repository publishing

This is a source project. Copy its contents into `KevinLeve/MinnalStream` and push to `main`; GitHub Actions will build the Android debug APK. Do not commit signing keys or private credentials.

## License

MIT. See `LICENSE`.

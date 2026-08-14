# HABS Broadcaster Android — 1.0.0 Alpha

Native Android companion to HABS Broadcaster.

## Implemented in this alpha

- HABS backend activation login (`sanctuarychatphilippines.com/backend/api/v1`)
- Locked studio until account is active
- Local Android music library using Storage Access Framework
- Deck A / Deck B independent local playback
- Crossfader and responsive mobile DJ workspace
- AutoDJ using the local library with configurable 2–20 second crossfade
- Microphone capture and program-mix gain
- Android 10+ AudioPlaybackCapture / MediaProjection device-audio capture
- MP3 encoding using a pure-Java LAME implementation (jump3r)
- Icecast / AzuraCast / Zeno.fm compatible source output
- SHOUTcast-compatible source mode
- Multiple simultaneous streaming destinations
- Per-output reconnect workers and bounded live queues
- Foreground broadcast service and Android notification
- Red OFF AIR / amber CONNECTING / pulsing green ON AIR status

## Android platform limitation

Android requires explicit MediaProjection consent before an application can capture device playback. HABS cannot legally or technically bypass that OS dialog. In addition, source applications can opt out of playback capture, so some protected/DRM applications may remain silent even after permission is granted.

## Security

- Activation uses HTTPS.
- Activation token is process-memory only.
- Stream passwords are not written to saved target profiles.
- No root, VPN, virtual audio driver, accessibility service, or device-admin access is used.

## Build

GitHub Actions builds the installable debug APK from `android-app/`.

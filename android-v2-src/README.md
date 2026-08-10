# HABS Broadcaster Android Desktop-Parity Build

This branch contains the Android v1.1 desktop-parity source as base64 chunks plus a GitHub Actions build workflow. The app reuses the actual HABS Broadcaster desktop v0.8.9 web UI and adds an Android native layer for activation, local music storage, WebView permissions, Android AudioPlaybackCapture / MediaProjection, microphone mixing, native MP3 encoding, multi-output Icecast/AzuraCast/Zeno/SHOUTcast-compatible streaming, reconnect workers, Spotify PKCE library access, YouTube/linked-music UI, and touch-first responsive layouts.

CI reconstructs the source archive from `android-v2-src/chunks/*.b64` and builds the installable APK.

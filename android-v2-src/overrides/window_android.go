//go:build android

package main

// Android owns the visible WebView in MainActivity. The embedded Go engine must
// never try to launch a desktop browser/window itself.
func openAppWindow(appURL, dataDir, iconPath string, onClose func()) error { return nil }

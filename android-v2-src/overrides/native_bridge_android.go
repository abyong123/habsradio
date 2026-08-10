//go:build android

package main

import (
	"encoding/json"
	"io"
	"net/http"
)

// These endpoints preserve the native-bridge contract introduced by the Mac host.
// Android's primary playback-capture path uses /api/system-audio/push (PCM16), but
// keeping these routes live means the shared desktop frontend can use either bridge.
func (a *App) handleNativeSystemAudioPush(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		methodNotAllowed(w)
		return
	}
	if a.nativeBridgeToken == "" || r.Header.Get("X-HABS-Native-Bridge") != a.nativeBridgeToken {
		http.Error(w, "native bridge unauthorized", http.StatusUnauthorized)
		return
	}
	body, err := io.ReadAll(io.LimitReader(r.Body, 2*1024*1024))
	if err != nil || len(body) == 0 {
		http.Error(w, "invalid audio packet", http.StatusBadRequest)
		return
	}
	if s, ok := any(a.systemAudio).(*SystemLoopback); ok {
		s.PushFramed(body)
	}
	w.WriteHeader(http.StatusNoContent)
}

func (a *App) handleNativeSystemAudioStatus(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		methodNotAllowed(w)
		return
	}
	if a.nativeBridgeToken == "" || r.Header.Get("X-HABS-Native-Bridge") != a.nativeBridgeToken {
		http.Error(w, "native bridge unauthorized", http.StatusUnauthorized)
		return
	}
	var v struct {
		Error string `json:"error"`
	}
	_ = json.NewDecoder(io.LimitReader(r.Body, 64*1024)).Decode(&v)
	if s, ok := any(a.systemAudio).(*SystemLoopback); ok {
		s.SetError(v.Error)
	}
	w.WriteHeader(http.StatusNoContent)
}

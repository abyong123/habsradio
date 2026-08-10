//go:build android

package main

import (
	"encoding/binary"
	"errors"
	"math"
	"sync"
	"time"
)

// Android SystemLoopback is a small broker. The native Android host owns
// MediaProjection/AudioPlaybackCapture and pushes PCM16 here over localhost.
// The unchanged HABS WebAudio frontend then consumes the same framed float32
// stream that the Windows WASAPI implementation exposes.
type SystemLoopback struct {
	mu          sync.RWMutex
	running     bool
	sampleRate  int
	channels    int
	lastPacket  time.Time
	lastError   string
	subscribers map[chan []byte]struct{}
}

func NewSystemLoopback(logf func(string)) *SystemLoopback {
	return &SystemLoopback{subscribers: make(map[chan []byte]struct{}), sampleRate: 48000, channels: 2}
}

func (s *SystemLoopback) Start() error {
	s.mu.Lock()
	s.running = true
	s.lastError = ""
	s.mu.Unlock()
	return nil
}

func (s *SystemLoopback) Stop() {
	s.mu.Lock()
	s.running = false
	s.mu.Unlock()
}

func (s *SystemLoopback) Restart() error { return s.Start() }

func (s *SystemLoopback) Info() NativeSystemAudioInfo {
	s.mu.RLock()
	defer s.mu.RUnlock()
	active := s.running && !s.lastPacket.IsZero() && time.Since(s.lastPacket) < 3*time.Second
	msg := s.lastError
	if s.running && !active && msg == "" {
		msg = "Waiting for Android playback-capture permission/audio"
	}
	return NativeSystemAudioInfo{
		Supported:  true,
		Running:    active,
		SampleRate: s.sampleRate,
		Channels:   2,
		Format:     "float32le",
		Device:     "Android AudioPlaybackCapture",
		LastError:  msg,
	}
}

func (s *SystemLoopback) Subscribe() (<-chan []byte, func()) {
	ch := make(chan []byte, 48)
	s.mu.Lock()
	s.subscribers[ch] = struct{}{}
	s.mu.Unlock()
	once := sync.Once{}
	return ch, func() {
		once.Do(func() {
			s.mu.Lock()
			if _, ok := s.subscribers[ch]; ok {
				delete(s.subscribers, ch)
				close(ch)
			}
			s.mu.Unlock()
		})
	}
}

func (s *SystemLoopback) PushPCM16(raw []byte, sampleRate, channels int) error {
	if sampleRate < 8000 || sampleRate > 192000 {
		return errors.New("invalid Android playback sample rate")
	}
	if channels < 1 || channels > 8 {
		return errors.New("invalid Android playback channel count")
	}
	frameBytes := channels * 2
	frames := len(raw) / frameBytes
	if frames <= 0 {
		return nil
	}
	payload := make([]byte, frames*2*4)
	for i := 0; i < frames; i++ {
		base := i * frameBytes
		l := float32(int16(binary.LittleEndian.Uint16(raw[base:base+2]))) / 32768.0
		r := l
		if channels > 1 {
			r = float32(int16(binary.LittleEndian.Uint16(raw[base+2:base+4]))) / 32768.0
		}
		l = float32(math.Max(-1, math.Min(1, float64(l))))
		r = float32(math.Max(-1, math.Min(1, float64(r))))
		binary.LittleEndian.PutUint32(payload[(i*2)*4:], math.Float32bits(l))
		binary.LittleEndian.PutUint32(payload[(i*2+1)*4:], math.Float32bits(r))
	}
	packet := frameNativePCM(sampleRate, 2, frames, payload)
	s.mu.Lock()
	s.running = true
	s.sampleRate = sampleRate
	s.channels = 2
	s.lastPacket = time.Now()
	s.lastError = ""
	for ch := range s.subscribers {
		cp := append([]byte(nil), packet...)
		select {
		case ch <- cp:
		default:
			select {
			case <-ch:
			default:
			}
			select {
			case ch <- cp:
			default:
			}
		}
	}
	s.mu.Unlock()
	return nil
}

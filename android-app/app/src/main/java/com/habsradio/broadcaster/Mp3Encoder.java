package com.habsradio.broadcaster;

import de.sciss.jump3r.mp3.*;
import de.sciss.jump3r.mpg.Common;
import de.sciss.jump3r.mpg.Interface;
import de.sciss.jump3r.mpg.MPGLib;

/** Thin Android-safe wrapper around jump3r's pure-Java LAME core. */
public final class Mp3Encoder implements AutoCloseable {
    private final Lame lame;
    private final LameGlobalFlags gfp;
    private final ID3Tag id3;

    public Mp3Encoder(int sampleRate, int channels, int bitrateKbps) {
        lame = new Lame();
        GetAudio gaud = new GetAudio();
        GainAnalysis ga = new GainAnalysis();
        BitStream bs = new BitStream();
        Presets presets = new Presets();
        QuantizePVT qupvt = new QuantizePVT();
        Quantize qu = new Quantize();
        VBRTag vbr = new VBRTag();
        Version ver = new Version();
        id3 = new ID3Tag();
        Reservoir rv = new Reservoir();
        Takehiro tak = new Takehiro();
        Parse parse = new Parse();
        MPGLib mpg = new MPGLib();
        Interface intf = new Interface();
        Common common = new Common();

        lame.setModules(ga, bs, presets, qupvt, qu, vbr, ver, id3, mpg);
        bs.setModules(ga, mpg, ver, vbr);
        id3.setModules(bs, ver);
        presets.setModules(lame);
        qu.setModules(bs, rv, qupvt, tak);
        qupvt.setModules(tak, rv, lame.enc.psy);
        rv.setModules(bs);
        tak.setModules(qupvt);
        vbr.setModules(lame, bs, ver);
        gaud.setModules(parse, mpg);
        parse.setModules(ver, id3, presets);
        mpg.setModules(intf, common);
        intf.setModules(vbr, common);

        gfp = lame.lame_init();
        gfp.num_channels = channels;
        gfp.in_samplerate = sampleRate;
        gfp.out_samplerate = sampleRate;
        gfp.brate = bitrateKbps;
        gfp.mode = channels == 1 ? MPEGMode.MONO : MPEGMode.STEREO;
        gfp.quality = 5;
        gfp.write_id3tag_automatic = false;
        gfp.findReplayGain = false;
        id3.id3tag_init(gfp);
        int rc = lame.lame_init_params(gfp);
        if (rc < 0) throw new IllegalArgumentException("LAME initialization failed: " + rc);
    }

    /** Encodes interleaved signed PCM16. Returns a new MP3 frame buffer (possibly empty). */
    public byte[] encode(short[] pcm, int sampleCountPerChannel) {
        int channels = gfp.num_channels;
        int[] left = new int[sampleCountPerChannel];
        int[] right = new int[sampleCountPerChannel];
        if (channels == 1) {
            for (int i = 0; i < sampleCountPerChannel; i++) {
                int v = pcm[i] << 16;
                left[i] = right[i] = v;
            }
        } else {
            for (int i = 0; i < sampleCountPerChannel; i++) {
                left[i] = pcm[i * 2] << 16;
                right[i] = pcm[i * 2 + 1] << 16;
            }
        }
        byte[] out = new byte[(int) (1.25 * sampleCountPerChannel) + 7200];
        int n = lame.lame_encode_buffer_int(gfp, left, right, sampleCountPerChannel, out, 0, out.length);
        if (n < 0) throw new IllegalStateException("MP3 encoding failed: " + n);
        byte[] exact = new byte[n];
        System.arraycopy(out, 0, exact, 0, n);
        return exact;
    }

    public byte[] flush() {
        byte[] out = new byte[7200];
        int n = lame.lame_encode_flush(gfp, out, 0, out.length);
        if (n < 0) return new byte[0];
        byte[] exact = new byte[n];
        System.arraycopy(out, 0, exact, 0, n);
        return exact;
    }

    @Override public void close() { lame.lame_close(gfp); }
}

# -*- coding: utf-8 -*-
"""合成原创 8-bit 循环背景音乐（方波音色，无版权问题）。
运行：python scripts/gen_bgm.py
输出：app/src/main/res/raw/bgm.wav
"""
import os
import math
import struct
import wave

SR = 22050
BPM = 140
BEAT = 60.0 / BPM  # 每拍秒数


def note(freq, dur, vol=0.30, duty=0.5):
    """方波音符，带快速起音 + 指数衰减包络"""
    n = int(SR * dur)
    out = []
    for i in range(n):
        t = i / SR
        ph = (freq * t) % 1.0
        s = 1.0 if ph < duty else -1.0
        attack = min(t / 0.008, 1.0)
        env = attack * math.exp(-t * 2.8)
        out.append(s * vol * env)
    return out


def rest(dur):
    return [0.0] * int(SR * dur)


def concat(*parts):
    out = []
    for p in parts:
        out.extend(p)
    return out


def mix(a, b):
    n = max(len(a), len(b))
    out = [0.0] * n
    for i in range(n):
        va = a[i] if i < len(a) else 0.0
        vb = b[i] if i < len(b) else 0.0
        out[i] = (va + vb) * 0.6
    return out


# 音名频率
C4, D4, E4, F4, G4, A4, B4 = 261.63, 293.66, 329.63, 349.23, 392.0, 440.0, 493.88
C5, D5, E5, F5, G5, A5, B5 = 523.25, 587.33, 659.25, 698.46, 783.99, 880.0, 987.77
C6 = 1046.5


def melody_bar(notes, dur=BEAT):
    return concat(*[note(f, dur) for f in notes])


def bass_bar(freqs, dur=BEAT):
    """低音：每小节两个二分音符"""
    return concat(*[note(f, dur * 2, vol=0.22) for f in freqs])


def main():
    # 主旋律（8 小节，每小节 4 拍）
    mel = concat(
        melody_bar([C5, E5, G5, E5]),
        melody_bar([A5, G5, E5, C5]),
        melody_bar([D5, F5, A5, F5]),
        melody_bar([G5, F5, D5, B4]),
        melody_bar([C5, E5, G5, C6]),
        melody_bar([A5, G5, E5, G5]),
        melody_bar([F5, A5, C6, A5]),
        melody_bar([G5, F5, E5, D5]),
    )
    # 低音（每小节根音）
    bass = concat(
        bass_bar([C4]), bass_bar([A4]), bass_bar([F4]), bass_bar([G4]),
        bass_bar([C4]), bass_bar([A4]), bass_bar([F4]), bass_bar([G4]),
    )
    track = mix(mel, bass)

    out = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
                       "app", "src", "main", "res", "raw", "bgm.wav")
    with wave.open(out, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        frames = bytearray()
        for s in track:
            v = max(-32767, min(32767, int(s * 32767)))
            frames += struct.pack("<h", v)
        w.writeframes(bytes(frames))
    print("written", out)
    print("BGM_DONE, 时长 %.1f 秒" % (len(track) / SR))


if __name__ == "__main__":
    main()

# -*- coding: utf-8 -*-
"""生成游戏音效 WAV（纯合成，无需外部素材）。
运行：python scripts/gen_sounds.py
输出：app/src/main/res/raw/*.wav
"""
import os
import math
import random
import struct
import wave

SR = 22050  # 采样率


def write_wav(path, samples):
    with wave.open(path, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        frames = bytearray()
        for s in samples:
            v = max(-32767, min(32767, int(s * 32767)))
            frames += struct.pack("<h", v)
        w.writeframes(bytes(frames))


def tone(freq, dur, vol, shape="sine", decay=8.0):
    n = int(SR * dur)
    out = []
    for i in range(n):
        t = i / SR
        env = math.exp(-t * decay)
        if shape == "square":
            s = 1.0 if math.sin(2 * math.pi * freq * t) >= 0 else -1.0
        elif shape == "saw":
            s = 2.0 * ((freq * t) % 1.0) - 1.0
        else:
            s = math.sin(2 * math.pi * freq * t)
        out.append(s * vol * env)
    return out


def sweep(f0, f1, dur, vol, shape="sine", decay=5.0):
    n = int(SR * dur)
    out = []
    phase = 0.0
    for i in range(n):
        t = i / SR
        f = f0 + (f1 - f0) * (t / dur)
        phase += 2 * math.pi * f / SR
        env = math.exp(-t * decay)
        if shape == "square":
            s = 1.0 if math.sin(phase) >= 0 else -1.0
        else:
            s = math.sin(phase)
        out.append(s * vol * env)
    return out


def noise(dur, vol, decay=20.0, lowpass=0.0):
    n = int(SR * dur)
    out = []
    last = 0.0
    for i in range(n):
        t = i / SR
        env = math.exp(-t * decay)
        w = random.uniform(-1, 1)
        if lowpass > 0:
            last = last * lowpass + w * (1 - lowpass)
            w = last
        out.append(w * vol * env)
    return out


def concat(*parts):
    out = []
    for p in parts:
        out.extend(p)
    return out


def silence(dur):
    return [0.0] * int(SR * dur)


def main():
    outdir = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
                          "app", "src", "main", "res", "raw")
    os.makedirs(outdir, exist_ok=True)

    sounds = {}

    # 射击：短促高频下滑
    sounds["shoot"] = sweep(950, 480, 0.07, 0.35, "square", decay=28.0)

    # 命中：短噪声脉冲
    sounds["hit"] = noise(0.04, 0.45, decay=40.0)

    # 爆炸：低频噪声 + 轰鸣
    sounds["explosion"] = concat(
        noise(0.28, 0.6, decay=14.0, lowpass=0.6),
        sweep(180, 60, 0.28, 0.5, "sine", decay=10.0),
    )

    # 升级：上行三音 C5 E5 G5
    sounds["levelup"] = concat(
        tone(523.25, 0.10, 0.45, decay=6.0),
        tone(659.25, 0.10, 0.45, decay=6.0),
        tone(783.99, 0.18, 0.5, decay=5.0),
    )

    # 金币：清脆双音 B5 -> E6
    sounds["coin"] = concat(
        tone(987.77, 0.06, 0.4, decay=10.0),
        tone(1318.51, 0.10, 0.45, decay=10.0),
    )

    # 血包：柔和上行
    sounds["heal"] = concat(
        tone(440.0, 0.08, 0.4, decay=6.0),
        tone(659.25, 0.12, 0.45, decay=6.0),
    )

    # 受伤：低沉方波
    sounds["hurt"] = sweep(200, 120, 0.18, 0.5, "square", decay=14.0)

    # 技能：扫频
    sounds["skill"] = sweep(300, 1000, 0.30, 0.5, "saw", decay=6.0)

    # 游戏结束：下行三音
    sounds["gameover"] = concat(
        tone(659.25, 0.16, 0.45, decay=4.0),
        tone(587.33, 0.16, 0.45, decay=4.0),
        tone(523.25, 0.30, 0.5, decay=3.0),
    )

    for name, samples in sounds.items():
        path = os.path.join(outdir, name + ".wav")
        write_wav(path, samples)
        print("written", path)

    print("SOUNDS_DONE")


if __name__ == "__main__":
    main()

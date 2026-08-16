# -*- coding: utf-8 -*-
"""生成狐狸图标 PNG（多密度 fallback，用于 API 24-25）。
运行：python scripts/gen_icon.py
"""
import os
from PIL import Image, ImageDraw


def draw_fox(size):
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    s = size
    cx = s / 2
    cy = s * 0.56
    r = s * 0.26
    # 背景圆角方块（草地绿）
    d.rounded_rectangle([0, 0, s, s], radius=s * 0.2, fill=(143, 207, 90, 255))
    # 耳朵
    ear = (255, 158, 74, 255)
    d.polygon([(cx - r * 1.0, cy - r * 0.45), (cx - r * 0.55, cy - r * 1.55), (cx - r * 0.05, cy - r * 0.65)], fill=ear)
    d.polygon([(cx + r * 1.0, cy - r * 0.45), (cx + r * 0.55, cy - r * 1.55), (cx + r * 0.05, cy - r * 0.65)], fill=ear)
    # 脸
    d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=(255, 158, 74, 255))
    # 肚皮
    d.ellipse([cx - r * 0.55, cy + r * 0.15, cx + r * 0.55, cy + r * 0.8], fill=(255, 255, 255, 255))
    # 眼睛
    d.ellipse([cx - r * 0.5, cy - r * 0.22, cx - r * 0.1, cy - r * 0.02], fill=(255, 255, 255, 255))
    d.ellipse([cx + r * 0.1, cy - r * 0.22, cx + r * 0.5, cy - r * 0.02], fill=(255, 255, 255, 255))
    d.ellipse([cx - r * 0.34, cy - r * 0.16, cx - r * 0.14, cy - r * 0.02], fill=(30, 30, 30, 255))
    d.ellipse([cx + r * 0.14, cy - r * 0.16, cx + r * 0.34, cy - r * 0.02], fill=(30, 30, 30, 255))
    # 鼻子
    d.ellipse([cx - r * 0.1, cy + r * 0.05, cx + r * 0.1, cy + r * 0.25], fill=(30, 30, 30, 255))
    return img


def main():
    base = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    res = os.path.join(base, "app", "src", "main", "res")
    for name, size in [("mdpi", 48), ("hdpi", 72), ("xhdpi", 96), ("xxhdpi", 144), ("xxxhdpi", 192)]:
        out = os.path.join(res, "mipmap-" + name)
        os.makedirs(out, exist_ok=True)
        draw_fox(size).save(os.path.join(out, "ic_launcher.png"))
        print("written", os.path.join(out, "ic_launcher.png"))
    print("ICON_DONE")


if __name__ == "__main__":
    main()

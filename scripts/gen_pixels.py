# -*- coding: utf-8 -*-
"""生成像素风动物贴图（原创，16x16 逻辑像素放大 4x -> 64x64）。
运行：python scripts/gen_pixels.py
输出：app/src/main/res/drawable-nodpi/*.png
"""
import os
from PIL import Image, ImageDraw

S = 16          # 逻辑像素
SCALE = 4       # 放大倍数

WHITE = (255, 255, 255, 255)
BLACK = (38, 50, 56, 255)
SHADOW = (0, 0, 0, 60)

# 动物配色
FOX = (255, 158, 74, 255)
WOLF = (158, 158, 158, 255)
BEAR = (141, 110, 99, 255)
BOAR = (109, 76, 65, 255)
SNAKE = (102, 187, 106, 255)
HEDGEHOG = (84, 110, 122, 255)
BAT = (126, 87, 194, 255)
ELITE = (183, 28, 28, 255)
BOSS = (123, 31, 162, 255)


def canvas():
    return Image.new("RGBA", (S, S), (0, 0, 0, 0))


def save(img, path):
    img = img.resize((S * SCALE, S * SCALE), Image.NEAREST)
    img.save(path)


def draw_round_body(d, color, ear="point", feature=None, dy=0):
    """圆身体类动物：身体椭圆 + 耳朵 + 肚皮 + 眼睛"""
    oy = dy
    # 耳朵
    if ear == "point":
        d.polygon([(4, 3 + oy), (3, 1 + oy), (6, 4 + oy)], fill=color)
        d.polygon([(12, 3 + oy), (13, 1 + oy), (10, 4 + oy)], fill=color)
    elif ear == "round":
        d.ellipse([3, 1 + oy, 7, 5 + oy], fill=color)
        d.ellipse([9, 1 + oy, 13, 5 + oy], fill=color)
    elif ear == "tiny":
        d.ellipse([4, 2 + oy, 6, 4 + oy], fill=color)
        d.ellipse([10, 2 + oy, 12, 4 + oy], fill=color)
    # 身体
    d.ellipse([3, 4 + oy, 13, 14 + oy], fill=color)
    # 肚皮
    d.ellipse([5, 9 + oy, 11, 14 + oy], fill=WHITE)
    # 眼睛
    d.rectangle([5, 7 + oy, 6, 8 + oy], fill=BLACK)
    d.rectangle([10, 7 + oy, 11, 8 + oy], fill=BLACK)
    # 鼻子
    d.rectangle([7, 10 + oy, 8, 11 + oy], fill=BLACK)
    # 特征
    if feature == "tusk":  # 野猪獠牙
        d.rectangle([4, 11 + oy, 4, 12 + oy], fill=WHITE)
        d.rectangle([11, 11 + oy, 11, 12 + oy], fill=WHITE)
    elif feature == "spike":  # 刺猬背刺
        for k in range(5):
            d.rectangle([3 + k * 2, 2 + oy, 4 + k * 2, 4 + oy], fill=HEDGEHOG)
            d.rectangle([3 + k * 2, 1 + oy, 3 + k * 2, 1 + oy], fill=(55, 71, 79, 255))
    elif feature == "wing":  # 蝙蝠翅膀
        d.polygon([(2, 6 + oy), (0, 4 + oy), (3, 10 + oy)], fill=BAT)
        d.polygon([(14, 6 + oy), (16, 4 + oy), (13, 10 + oy)], fill=BAT)


def draw_snake(d, color, dy=0):
    """蛇：横向长条身体"""
    oy = dy
    d.ellipse([1, 5 + oy, 15, 11 + oy], fill=color)
    d.ellipse([11, 4 + oy, 15, 12 + oy], fill=color)  # 头
    d.ellipse([3, 6 + oy, 9, 10 + oy], fill=(178, 235, 181, 255))  # 肚纹
    d.rectangle([12, 6 + oy, 13, 7 + oy], fill=WHITE)
    d.rectangle([12, 9 + oy, 13, 10 + oy], fill=WHITE)
    d.rectangle([12, 6 + oy, 12, 7 + oy], fill=BLACK)


def draw_orb(d, color, dy=0):
    """环绕能量体：圆 + 高光"""
    d.ellipse([2, 2, 14, 14], fill=color)
    d.ellipse([4, 4, 9, 9], fill=(255, 255, 255, 200))


def draw_exp(d, dy=0):
    """能量球掉落物"""
    d.ellipse([4, 4, 12, 12], fill=(76, 175, 80, 255))
    d.ellipse([6, 6, 8, 8], fill=(185, 246, 202, 255))


def main():
    out = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
                       "app", "src", "main", "res", "drawable-nodpi")
    os.makedirs(out, exist_ok=True)

    # 圆身体动物：名称 -> (颜色, 耳朵, 特征)
    animals = {
        "fox": (FOX, "point", None),
        "wolf": (WOLF, "point", None),
        "bear": (BEAR, "round", None),
        "boar": (BOAR, "tiny", "tusk"),
        "hedgehog": (HEDGEHOG, "tiny", "spike"),
        "bat": (BAT, "point", "wing"),
        "elite": (ELITE, "point", None),
        "boss": (BOSS, "round", None),
    }
    for name, (color, ear, feat) in animals.items():
        for f in range(2):
            img = canvas()
            d = ImageDraw.Draw(img)
            draw_round_body(d, color, ear, feat, dy=0 if f == 0 else 1)
            save(img, os.path.join(out, "%s_%d.png" % (name, f)))
        print("written", name)

    # 蛇（2 帧）
    for f in range(2):
        img = canvas()
        d = ImageDraw.Draw(img)
        draw_snake(d, SNAKE, dy=0 if f == 0 else 1)
        save(img, os.path.join(out, "snake_%d.png" % f))
    print("written snake")

    # 环绕武器能量体（五阶变色）
    orb_colors = {
        "white": (240, 240, 240, 255),
        "green": (76, 175, 80, 255),
        "blue": (33, 150, 243, 255),
        "purple": (156, 39, 176, 255),
        "red": (244, 67, 54, 255),
    }
    for name, color in orb_colors.items():
        img = canvas()
        d = ImageDraw.Draw(img)
        draw_orb(d, color)
        save(img, os.path.join(out, "orb_%s.png" % name))
        print("written orb_" + name)

    # 能量球掉落物
    img = canvas()
    draw_exp(ImageDraw.Draw(img))
    save(img, os.path.join(out, "exp.png"))
    print("written exp")

    print("PIXELS_DONE")


if __name__ == "__main__":
    main()

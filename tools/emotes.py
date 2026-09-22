#!/usr/bin/env python3
"""Generates Slate Chat's pixel emote sheet.

Output: common/chat/src/main/resources/assets/slate_chat/textures/font/emotes.png - 8 columns x 3 rows of
16x16 cells, in the order of Emotes.NAMES (dev.fallingcloud.slate.chat.client.Emotes) which is also the
code point order (U+E100 + index) declared in assets/slate_chat/font/emotes.json. Add an emote: append a
drawing function to EMOTES, its name to Emotes.NAMES and its code point to the font json's chars rows.

Faces are drawn procedurally on a 16x16 grid (one yellow disc, features on top); icons use small character
maps. Needs Pillow.
"""
from __future__ import annotations

import os
import sys

try:
    from PIL import Image
except ImportError:  # pragma: no cover
    sys.exit("Pillow is required: pip install pillow")

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "common", "chat", "src", "main", "resources", "assets", "slate_chat", "textures", "font", "emotes.png")

CELL = 16
COLS = 8

# ---- palette
T = (0, 0, 0, 0)
YEL = (255, 204, 77, 255)
YEL_D = (214, 160, 40, 255)
YEL_L = (255, 228, 140, 255)
BRN = (101, 67, 33, 255)
BLK = (30, 30, 30, 255)
WHT = (255, 255, 255, 255)
RED = (221, 46, 68, 255)
RED_D = (160, 24, 44, 255)
PNK = (255, 140, 170, 255)
BLU = (85, 172, 238, 255)
BLU_D = (40, 110, 190, 255)
GRN = (120, 177, 89, 255)
GRN_D = (54, 105, 42, 255)
GRN_L = (170, 220, 130, 255)
GRY = (170, 170, 170, 255)
GRY_D = (90, 90, 90, 255)
ORG = (244, 144, 12, 255)
ORG_D = (200, 80, 10, 255)
CYN = (120, 220, 255, 255)
CYN_D = (40, 150, 200, 255)
CYN_L = (210, 245, 255, 255)
SKN = (255, 214, 160, 255)
SLV = (225, 228, 235, 255)
SLV_D = (150, 155, 170, 255)
GLD = (240, 190, 60, 255)


class Grid:
    def __init__(self):
        self.p = [[T] * CELL for _ in range(CELL)]

    def px(self, x, y, c):
        if 0 <= x < CELL and 0 <= y < CELL:
            self.p[y][x] = c

    def rect(self, x0, y0, x1, y1, c):
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                self.px(x, y, c)

    def disc(self, cx, cy, r, c, outline=None):
        for y in range(CELL):
            for x in range(CELL):
                d = (x + 0.5 - cx) ** 2 + (y + 0.5 - cy) ** 2
                if d <= r * r:
                    self.px(x, y, c)
        if outline:
            for y in range(CELL):
                for x in range(CELL):
                    if self.p[y][x] == c:
                        edge = any(not (0 <= x + dx < CELL and 0 <= y + dy < CELL) or self.p[y + dy][x + dx] == T
                                   for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
                        if edge:
                            self.p[y][x] = outline

    def line(self, x0, y0, x1, y1, c):
        dx, dy = abs(x1 - x0), -abs(y1 - y0)
        sx, sy = (1 if x0 < x1 else -1), (1 if y0 < y1 else -1)
        err = dx + dy
        while True:
            self.px(x0, y0, c)
            if x0 == x1 and y0 == y1:
                break
            e2 = 2 * err
            if e2 >= dy:
                err += dy
                x0 += sx
            if e2 <= dx:
                err += dx
                y0 += sy

    def map(self, rows, key):
        for y, row in enumerate(rows):
            for x, ch in enumerate(row):
                if ch != "." and ch in key:
                    self.px(x, y, key[ch])


# ---- faces

def face(g: Grid, color=YEL, outline=YEL_D):
    g.disc(8, 8, 7.4, color, outline)


def eyes(g, y=6, dot=BLK):
    g.rect(5, y, 5, y + 1, dot)
    g.rect(10, y, 10, y + 1, dot)


def smile(g, y=10, c=BLK):
    g.px(4, y, c)
    g.px(5, y + 1, c)
    g.rect(6, y + 2, 9, y + 2, c)
    g.px(10, y + 1, c)
    g.px(11, y, c)


def frown(g, y=12, c=BLK):
    g.px(4, y, c)
    g.px(5, y - 1, c)
    g.rect(6, y - 2, 9, y - 2, c)
    g.px(10, y - 1, c)
    g.px(11, y, c)


def e_smile(g):
    face(g)
    eyes(g)
    smile(g)


def e_grin(g):
    face(g)
    eyes(g)
    g.rect(4, 9, 11, 12, BLK)
    g.rect(5, 9, 10, 10, WHT)
    g.px(4, 9, BLK)
    g.px(11, 9, BLK)


def e_laugh(g):
    face(g)
    # squinted happy eyes (^ ^)
    for x0 in (4, 9):
        g.px(x0, 6, BLK)
        g.px(x0 + 1, 5, BLK)
        g.px(x0 + 2, 6, BLK)
    g.rect(4, 9, 11, 12, BLK)
    g.rect(5, 9, 10, 9, WHT)
    g.rect(6, 12, 9, 12, PNK)
    g.px(13, 4, BLU)
    g.rect(13, 5, 13, 6, BLU)


def e_wink(g):
    face(g)
    g.rect(5, 6, 5, 7, BLK)
    g.rect(9, 6, 11, 6, BLK)
    smile(g)


def e_sad(g):
    face(g)
    eyes(g)
    frown(g)


def e_cry(g):
    face(g)
    eyes(g)
    frown(g, y=13)
    g.rect(5, 8, 5, 11, BLU)
    g.rect(10, 8, 10, 11, BLU)
    g.px(5, 12, BLU_D)
    g.px(10, 12, BLU_D)


def e_angry(g):
    face(g, color=(255, 170, 60, 255), outline=(200, 110, 20, 255))
    g.line(3, 4, 6, 6, BLK)
    g.line(12, 4, 9, 6, BLK)
    g.rect(5, 7, 5, 8, BLK)
    g.rect(10, 7, 10, 8, BLK)
    frown(g, y=13)


def e_surprised(g):
    face(g)
    g.rect(4, 5, 6, 7, WHT)
    g.rect(9, 5, 11, 7, WHT)
    g.px(5, 6, BLK)
    g.px(10, 6, BLK)
    g.disc(8, 11.5, 1.6, BLK)


def e_thinking(g):
    face(g)
    g.rect(4, 4, 6, 4, BLK)        # raised brow
    g.rect(5, 6, 5, 7, BLK)
    g.rect(10, 6, 10, 7, BLK)
    g.line(5, 11, 9, 10, BLK)      # slanted mouth
    g.rect(9, 12, 13, 14, SKN)     # hand on chin
    g.rect(10, 11, 12, 11, SKN)
    g.px(9, 12, (220, 170, 110, 255))


def e_cool(g):
    face(g)
    g.rect(2, 5, 13, 5, BLK)
    g.rect(3, 6, 6, 8, BLK)
    g.rect(9, 6, 12, 8, BLK)
    g.px(7, 6, BLK)
    g.px(8, 6, BLK)
    g.px(4, 6, GRY_D)
    g.px(10, 6, GRY_D)
    g.rect(6, 11, 10, 11, BLK)
    g.px(11, 10, BLK)


def e_heart(g, fill=RED, dark=RED_D):
    rows = [
        "................",
        "...aa....aa.....",
        "..aaaa..aaaa....",
        ".aaaaaaaaaaaa...",
        ".aabaaaaaaaaa...",
        ".aaaaaaaaaaaa...",
        ".aaaaaaaaaaaa...",
        "..aaaaaaaaaa....",
        "...aaaaaaaa.....",
        "....aaaaaa......",
        ".....aaaa.......",
        "......aa........",
        "................",
    ]
    g.map(rows, {"a": fill, "b": (255, 150, 170, 255)})
    # bottom shading
    for y in range(7, 12):
        for x in range(CELL):
            if g.p[y][x] == fill and (x + y) % 3 == 0:
                g.px(x, y, dark)


def e_broken_heart(g):
    e_heart(g)
    crack = [(6, 2), (7, 3), (6, 4), (7, 5), (6, 6), (7, 7), (6, 8), (7, 9), (6, 10)]
    for x, y in crack:
        g.px(x, y, T)
        g.px(x + 1, y, T) if y % 2 == 0 else None


def e_thumbs_up(g):
    rows = [
        "................",
        "........aa......",
        ".......abba.....",
        ".......abba.....",
        ".......abbaaaa..",
        "..aaaaabbbbbbba.",
        ".abbbbbbbbbbbba.",
        ".abbbbbbbbbbbba.",
        ".abbbbabbbbbbba.",
        ".abbbbbbbbbbbba.",
        ".abbbbabbbbbba..",
        ".abbbbbbbbbbba..",
        "..aaaaabbbbbba..",
        ".......aaaaaa...",
        "................",
        "................",
    ]
    g.map(rows, {"a": YEL_D, "b": YEL})


def e_thumbs_down(g):
    rows = [
        "................",
        "................",
        ".......aaaaaa...",
        "..aaaaabbbbbba..",
        ".abbbbbbbbbbba..",
        ".abbbbabbbbbba..",
        ".abbbbbbbbbbbba.",
        ".abbbbabbbbbbba.",
        ".abbbbbbbbbbbba.",
        ".abbbbbbbbbbbba.",
        "..aaaaabbbbbbba.",
        ".......abbaaaa..",
        ".......abba.....",
        ".......abba.....",
        "........aa......",
        "................",
    ]
    g.map(rows, {"a": YEL_D, "b": YEL})


def e_fire(g):
    rows = [
        "................",
        ".......r........",
        "......rr........",
        "......rrr.......",
        ".....rrrr..r....",
        ".....rrrrr.rr...",
        "....rrrrrrrrr...",
        "....rrooorrrr...",
        "...rrooooorrr...",
        "...rroyyyoorrr..",
        "...rroyyyyorrr..",
        "...rrooyyoorrr..",
        "....rroooorrr...",
        ".....rrrrrrr....",
        "......rrrrr.....",
        "................",
    ]
    g.map(rows, {"r": RED, "o": ORG, "y": YEL_L})
    for y in range(CELL):
        for x in range(CELL):
            if g.p[y][x] == RED and x < 6:
                g.px(x, y, RED_D)


def e_skull(g):
    rows = [
        "................",
        ".....wwwwww.....",
        "....wwwwwwww....",
        "...wwwwwwwwww...",
        "...wwwwwwwwww...",
        "...wkkwwwwkkw...",
        "...kkkkwwkkkk...",
        "...kkkkwwkkkk...",
        "...wkkwwwwkkw...",
        "....wwwkkwww....",
        "....wwwwwwww....",
        ".....wwwwww.....",
        ".....wkwkwk.....",
        ".....wwwwww.....",
        "................",
        "................",
    ]
    g.map(rows, {"w": SLV, "k": BLK})
    for y in range(CELL):
        for x in range(CELL):
            if g.p[y][x] == SLV and (x == 3 or y >= 12) and (x + y) % 2 == 0:
                g.px(x, y, SLV_D)


def e_wave(g):
    rows = [
        "................",
        "....b.b.b.......",
        "...b..b..b......",
        "...ab.ab.ab.....",
        "...abbabbabb....",
        "...abbabbabbaa..",
        "...abbbbbbbbbba.",
        "...abbbbbbbbbba.",
        "..aabbbbbbbbbba.",
        ".abbbbbbbbbbbba.",
        ".abbbbbbbbbbba..",
        "..abbbbbbbbbba..",
        "...abbbbbbbba...",
        "....aabbbbaa....",
        "......aaaa......",
        "................",
    ]
    g.map(rows, {"a": YEL_D, "b": YEL})
    # motion lines
    g.px(14, 3, GRY)
    g.px(15, 4, GRY)
    g.px(14, 6, GRY)


def e_clap(g):
    rows = [
        "....y......y....",
        ".....y....y.....",
        "..y...y..y...y..",
        "...y..........y.",
        "....aab..baa....",
        "...abbbb.bbbba..",
        "...abbbb.bbbba..",
        "..abbbbb.bbbbba.",
        "..abbbbb.bbbbba.",
        "..abbbbb.bbbbba.",
        "...abbbb.bbbba..",
        "...abbbb.bbbba..",
        "....abbb.bbba...",
        ".....aab.baa....",
        "................",
        "................",
    ]
    g.map(rows, {"a": YEL_D, "b": YEL, "y": YEL_L})


def e_eyes(g):
    for cx in (4.5, 11.5):
        g.disc(cx, 8, 3.6, WHT, GRY)
        g.disc(cx + 0.7, 8.3, 1.8, BLU_D)
        g.disc(cx + 0.7, 8.3, 1.0, BLK)
        g.px(int(cx), 6, WHT)


def e_sparkles(g):
    def star(cx, cy, r, c, tip=None):
        g.rect(cx - r, cy, cx + r, cy, c)
        g.rect(cx, cy - r, cx, cy + r, c)
        g.px(cx, cy, tip or WHT)
        if r >= 3:
            for dx, dy in ((1, 1), (-1, 1), (1, -1), (-1, -1)):
                g.px(cx + dx, cy + dy, c)
    star(6, 7, 4, YEL_L)
    star(12, 3, 2, YEL)
    star(12, 12, 2, YEL)
    star(3, 13, 1, WHT)


def e_sword(g):
    rows = [
        "..........ss....",
        ".........sws....",
        "........swws....",
        ".......swws.....",
        "......swws......",
        ".....swws.......",
        "....swws........",
        "..g.sws.........",
        "...gsg..........",
        "..gggg..........",
        ".bbgg...........",
        "bbb.gg..........",
        ".b..............",
        "................",
        "................",
        "................",
    ]
    g.map(rows, {"s": SLV_D, "w": SLV, "g": GLD, "b": BRN})


def e_pickaxe(g):
    rows = [
        "......cccccc....",
        "....cccdddccc...",
        "...cdd.....ddc..",
        "..cd........dc..",
        "..cd.....b...c..",
        "........bb......",
        ".......bb.......",
        "......bb........",
        ".....bb.........",
        "....bb..........",
        "...bb...........",
        "..bb............",
        ".bb.............",
        "bb..............",
        "................",
        "................",
    ]
    g.map(rows, {"c": CYN_D, "d": CYN, "b": BRN})


def e_creeper(g):
    g.rect(1, 1, 14, 14, GRN)
    for y in range(1, 15):
        for x in range(1, 15):
            if (x * 7 + y * 13) % 5 == 0:
                g.px(x, y, GRN_L)
            elif (x * 3 + y * 11) % 7 == 0:
                g.px(x, y, GRN_D)
    g.rect(3, 4, 6, 6, BLK)
    g.rect(9, 4, 12, 6, BLK)
    g.rect(6, 7, 9, 8, BLK)
    g.rect(5, 9, 10, 11, BLK)
    g.rect(5, 12, 6, 12, BLK)
    g.rect(9, 12, 10, 12, BLK)


def e_diamond(g):
    rows = [
        "................",
        "................",
        "....dddddddd....",
        "...dlddddddcd...",
        "..dlldddddddcd..",
        ".dllddddddddccd.",
        ".ddddddddddddcd.",
        "..ddddddddddcd..",
        "...ddddddddcd...",
        "....dddddddd....",
        ".....dddddd.....",
        "......dddd......",
        ".......dd.......",
        "................",
        "................",
        "................",
    ]
    g.map(rows, {"d": CYN, "l": CYN_L, "c": CYN_D})


EMOTES = [
    ("smile", e_smile), ("grin", e_grin), ("laugh", e_laugh), ("wink", e_wink),
    ("sad", e_sad), ("cry", e_cry), ("angry", e_angry), ("surprised", e_surprised),
    ("thinking", e_thinking), ("cool", e_cool), ("heart", e_heart), ("broken_heart", e_broken_heart),
    ("thumbs_up", e_thumbs_up), ("thumbs_down", e_thumbs_down), ("fire", e_fire), ("skull", e_skull),
    ("wave", e_wave), ("clap", e_clap), ("eyes", e_eyes), ("sparkles", e_sparkles),
    ("sword", e_sword), ("pickaxe", e_pickaxe), ("creeper", e_creeper), ("diamond", e_diamond),
]


def main():
    rows = (len(EMOTES) + COLS - 1) // COLS
    sheet = Image.new("RGBA", (COLS * CELL, rows * CELL), T)
    for i, (name, draw) in enumerate(EMOTES):
        g = Grid()
        draw(g)
        ox, oy = (i % COLS) * CELL, (i // COLS) * CELL
        for y in range(CELL):
            for x in range(CELL):
                sheet.putpixel((ox + x, oy + y), g.p[y][x])
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    sheet.save(OUT)
    print(f"wrote {OUT} ({COLS}x{rows} cells, {len(EMOTES)} emotes)")
    print("names:", ", ".join(n for n, _ in EMOTES))


if __name__ == "__main__":
    main()

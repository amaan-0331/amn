# Usage: python3 tool/make_icon.py android/app/src/main/res
"""Renders the AMN 'Drift' icon: the adaptive icon's background layer (all the
art: night gradient, three screen-blended orbs, grain) at every density."""
import math, random, sys, os
from PIL import Image

OUT = sys.argv[1]
S = 432  # xxxhdpi: 108 dp * 4
BG = ((0x15, 0x17, 0x35), (0x2A, 0x1F, 0x5C))
ORBS = [(.36, .40, .33, 0xFFB257, 0xFF5E62), (.64, .60, .33, 0x7FD6FF, 0x4B63F0), (.63, .33, .20, 0xE39BFF, 0x9B5CFF)]

def rgb(h): return ((h >> 16) & 255, (h >> 8) & 255, h & 255)
def lerp(a, b, t): return tuple(x + (y - x) * t for x, y in zip(a, b))
def px(u): return (18 + u * 72) / 108 * S  # design unit -> px; art fills the 72 dp visible zone

def stops(t, a, b):
    """Colour and alpha at t for stops 0 white, .18 a, .75 b, 1 b@0."""
    W = (255, 255, 255)
    if t < .18: return lerp(W, a, t / .18), 1.0
    if t < .75: return lerp(a, b, (t - .18) / .57), 1.0
    return b, 1 - (t - .75) / .25

def conical_t(p, f, c, r):
    qx, qy = p[0] - f[0], p[1] - f[1]
    dx, dy = c[0] - f[0], c[1] - f[1]
    A = dx * dx + dy * dy - r * r
    B = qx * dx + qy * dy
    C = qx * qx + qy * qy
    return (B - math.sqrt(max(B * B - A * C, 0))) / A

rnd = random.Random(11)
NOISE = [[rnd.random() for _ in range(64)] for _ in range(64)]
grain_px = 2  # fine grain: one noise cell per 2 px at xxxhdpi

img = Image.new('RGB', (S, S))
data = []
orbs = []
for x, y, r, a, b in ORBS:
    cx, cy, rr = px(x), px(y), r * 72 / 108 * S
    orbs.append(((cx - rr * .25, cy - rr * .3), (cx, cy), rr, rgb(a), rgb(b)))
gx0, gy0 = px(0), px(0)
gdx, gdy = px(.3) - gx0, px(1) - gy0
glen = gdx * gdx + gdy * gdy
for j in range(S):
    for i in range(S):
        p = (i + .5, j + .5)
        t = max(0, min(1, ((p[0] - gx0) * gdx + (p[1] - gy0) * gdy) / glen))
        c = [v / 255 for v in lerp(BG[0], BG[1], t)]
        for f, ctr, rr, a, b in orbs:
            tt = conical_t(p, f, ctr, rr)
            if tt < 0 or tt >= 1: continue
            col, al = stops(tt, a, b)
            col = [v / 255 * al for v in col]
            c = [1 - (1 - x) * (1 - y) for x, y in zip(c, col)]  # screen
        n = NOISE[int(j / grain_px) % 64][int(i / grain_px) % 64]
        ov = [2 * x * n if x < .5 else 1 - 2 * (1 - x) * (1 - n) for x in c]  # overlay
        c = [x + (o - x) * .12 for x, o in zip(c, ov)]
        data.append(tuple(max(0, min(255, round(v * 255))) for v in c))
img.putdata(data)
for d, k in [('mdpi', 1), ('hdpi', 1.5), ('xhdpi', 2), ('xxhdpi', 3), ('xxxhdpi', 4)]:
    os.makedirs(f'{OUT}/mipmap-{d}', exist_ok=True)
    n = round(108 * k)
    (img if n == S else img.resize((n, n), Image.LANCZOS)).save(f'{OUT}/mipmap-{d}/ic_launcher_background.png', optimize=True)
# A preview (72 dp visible zone, rounded) next to this script.
crop = img.crop((S // 6, S // 6, S - S // 6, S - S // 6)).resize((288, 288), Image.LANCZOS)
mask = Image.new('L', (288, 288), 0)
from PIL import ImageDraw
ImageDraw.Draw(mask).rounded_rectangle((0, 0, 287, 287), radius=86, fill=255)
prev = Image.new('RGBA', (288, 288)); prev.paste(crop, (0, 0), mask); prev.save(os.path.join(os.path.dirname(__file__), "icon_preview.png"))

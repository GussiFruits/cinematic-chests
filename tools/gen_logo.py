"""Generates the project logo: an open chest with light spilling out, framed by a strip of film.

Original pixel art (no Minecraft or Cinematic Interactions textures), drawn on a 40x40 grid and scaled 10x to 400x400.

Run from the project root:  python tools/gen_logo.py
Writes logo.png (for CurseForge / Modrinth) and src/main/resources/logo.png (shown in the in-game mod list).
"""
import math
from PIL import Image

N, SCALE = 40, 10

FILM = (20, 20, 22)
HOLE = (214, 214, 206)
BG_TOP, BG_BOTTOM = (28, 46, 86), (62, 36, 92)
GLOW = (255, 206, 110)
OUTLINE = (40, 24, 12)
WOOD, WOOD_LIGHT, WOOD_DARK = (138, 88, 42), (172, 114, 58), (104, 64, 30)
LID_INSIDE, LID_INSIDE_DARK = (98, 60, 28), (80, 48, 22)
INSIDE = (46, 28, 14)
IRON, IRON_LIGHT = (58, 58, 68), (96, 96, 108)
GOLD, GOLD_LIGHT = (226, 170, 52), (255, 222, 120)
GEM_CYAN, GEM_GOLD, GEM_RED = (90, 232, 222), (255, 214, 70), (232, 72, 80)


def mix(a, b, t):
    t = max(0.0, min(1.0, t))
    return tuple(round(a[i] + (b[i] - a[i]) * t) for i in range(3))


img = Image.new("RGB", (N, N))
px = img.load()

# background: a vertical gradient between the film bands, with a warm glow round the chest's opening
for y in range(N):
    for x in range(N):
        c = mix(BG_TOP, BG_BOTTOM, (y - 6) / 27)
        d = math.hypot(x - 19.5, (y - 20) * 1.25)
        c = mix(c, GLOW, max(0.0, 1 - d / 17) ** 2 * 0.95)
        px[x, y] = c

# the lid, swung back: strong perspective (narrow at the top), its inside lit from below by what is in the chest
for y in range(8, 19):
    k = (y - 8) / 10
    left, right = round(15 - 4 * k), round(24 + 4 * k)
    for x in range(left, right + 1):
        if x in (left, right) or y == 8:
            px[x, y] = OUTLINE
        else:
            c = LID_INSIDE_DARK if y in (11, 15) else LID_INSIDE
            px[x, y] = mix(c, GLOW, max(0.0, (y - 12) / 6) * 0.55)
    if 8 < y < 18:
        px[left + 1, y] = IRON
        px[right - 1, y] = IRON

# warm rays fanning out past the lid's sides, and a few sparkles
for y in range(9, 20):
    t = (19 - y) / 10
    for x in range(N):
        for start, lean in ((11.0, -7.0), (28.0, 7.0)):
            cx = start + lean * t
            if abs(x - cx) <= 0.8 + 1.5 * t:
                # stays warm all the way: stronger near the chest, gone before it would turn grey over the blue
                px[x, y] = mix(px[x, y], (255, 214, 120), 0.75 * (1 - t) ** 0.7)
for x, y in ((6, 9), (33, 8), (4, 15), (35, 14), (9, 7), (30, 11)):
    px[x, y] = (255, 240, 190)
    for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        px[x + dx, y + dy] = mix(px[x + dx, y + dy], (255, 226, 150), 0.6)

# the opening: blazing with light, treasure at the rim
for x in range(10, 30):
    px[x, 19] = OUTLINE if x in (10, 29) else (255, 236, 170)
    px[x, 20] = OUTLINE if x in (10, 29) else GLOW
    px[x, 21] = OUTLINE if x in (10, 29) else mix(GLOW, WOOD_LIGHT, 0.5)
for x, c in ((13, GEM_CYAN), (14, GEM_CYAN), (17, GEM_GOLD), (18, GEM_GOLD), (21, GEM_RED), (24, GEM_GOLD), (25, GEM_CYAN)):
    px[x, 20] = c

# the body: front face with planks, iron corners, a gold latch
for y in range(22, 33):
    for x in range(10, 30):
        if x in (10, 29) or y in (22, 32):
            c = OUTLINE
        elif y == 23:
            c = WOOD_LIGHT
        elif y in (26, 29):
            c = WOOD_DARK
        elif x >= 27:
            c = WOOD_DARK
        else:
            c = WOOD
        px[x, y] = c
for x in (11, 12, 27, 28):
    for y in (23, 24, 30, 31):
        px[x, y] = IRON_LIGHT if (x, y) in ((11, 23), (27, 23)) else IRON
for y in range(22, 26):
    for x in (18, 19, 20, 21):
        px[x, y] = OUTLINE if x in (18, 21) or y == 25 else GOLD
px[19, 23] = px[20, 23] = GOLD_LIGHT
px[19, 24] = OUTLINE

# film strip top and bottom, with sprocket holes
for y in list(range(0, 6)) + list(range(34, 40)):
    for x in range(N):
        px[x, y] = FILM
for start in (2, 36):
    for x0 in range(2, N, 6):
        for y in (start, start + 1):
            for x in (x0, x0 + 1, x0 + 2):
                if x < N:
                    px[x, y] = HOLE

big = img.resize((N * SCALE, N * SCALE), Image.NEAREST)
big.save("logo.png")
big.save("src/main/resources/logo.png")
print("wrote logo.png and src/main/resources/logo.png", big.size)

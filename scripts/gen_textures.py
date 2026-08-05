"""One-off generator for every Trading Post texture.

Run with `python scripts/gen_textures.py` from the mod root; not part of the build.
Covers: the Trading Post block, the delivery crate block, the freight plane and airdrop
entity atlases, and the trading GUI background.


RESOLUTION
----------
Everything is authored in *logical* units (16 for a block face; declared atlas units for
entities) and rendered at SCALE x that. Raising SCALE needs NO code changes anywhere:

  * Block model JSON `uv` values are in 0-16 space and are mapped onto the whole image
    regardless of its pixel size.
  * Entity UVs are normalised by the declared texWidth/texHeight in ModelPart.Polygon
    (`u / texWidth`), so a 2x PNG against an unchanged `LayerDefinition.create(mesh, 128, 128)`
    simply yields twice the texel density. This is exactly how HD resource packs work.

So keep `LayerDefinition` sizes and every `texOffs` in the model classes as they are; only
this file changes when you want more detail.


ENTITY UV LAYOUT
----------------
For a box of size (w, h, d) placed at texOffs(u, v) the atlas footprint is 2*(d+w) wide by
(d+h) tall, laid out as:

      (u+d,     v)     top     (w x d)
      (u+d+w,   v)     bottom  (w x d)
      (u,       v+d)   east    (d x h)
      (u+d,     v+d)   north   (w x h)   <- model's -Z face ("front")
      (u+d+w,   v+d)   west    (d x h)
      (u+d+w+d, v+d)   south   (w x h)

`box_regions` encodes exactly that, so painting works in terms of named faces. Keep these
offsets in sync with the texOffs calls in DeliveryDroneModel / DeliveryPackageModel.


STYLE
-----
The goal is to read as Minecraft pixel art rather than flat vector shapes. Every surface is
built from a 5-step tonal ramp scattered per pixel by a deterministic hash (so runs are
reproducible), then given an implied top-left light, a darkened outer edge, and hand-placed
features (plank seams, knots, rivets, bevels). Flat fills are avoided almost everywhere -
that flatness was what made the first pass look un-Minecraft-y.
"""
from PIL import Image, ImageDraw

ASSETS = "src/main/resources/assets/trading_post/textures"

#: Texel multiplier. 2 -> 32x32 blocks, 256x256 plane atlas, 128x128 airdrop atlas.
SCALE = 2

# --- shared Trading Post palette: slate + brass + oxblood + aged paper ---------
SLATE_DEEP = (13, 17, 25)
SLATE = (27, 34, 51)
SLATE_MID = (38, 48, 70)
SLATE_LIGHT = (56, 70, 100)
BRASS = (198, 157, 74)
BRASS_DARK = (120, 90, 36)
PAPER = (226, 214, 186)
OXBLOOD = (150, 48, 42)

WOOD = (150, 108, 62)
WOOD_DEEP = (96, 68, 38)
METAL = (112, 116, 124)
HULL = (226, 229, 234)
ACCENT = (176, 54, 46)
GLASS = (68, 96, 120)
ROTOR = (54, 56, 62)
CANOPY_A = (228, 225, 214)
CANOPY_B = (176, 54, 46)
CORD = (208, 196, 168)


# =============================================================================
# colour + noise helpers
# =============================================================================
def shade(c, amt):
    """Lighten (amt>0) / darken (amt<0) a colour, clamped."""
    return tuple(max(0, min(255, int(v + 255 * amt))) for v in c[:3])


def ramp(base, steps=5, spread=0.13):
    """A tonal ramp centred on `base`, dark -> light."""
    return [shade(base, (i / (steps - 1) - 0.5) * 2 * spread) for i in range(steps)]


def hsh(x, y, seed=0):
    """Deterministic per-pixel value in [0,1). Stable across runs, unlike random()."""
    n = (x * 374761393 + y * 668265263 + seed * 1442695041) & 0xFFFFFFFF
    n = ((n ^ (n >> 13)) * 1274126177) & 0xFFFFFFFF
    return ((n ^ (n >> 16)) & 0xFFFF) / 65536.0


# Bias the scatter toward the middle tones, the way hand-drawn MC textures sit.
_WEIGHTS = (0.10, 0.30, 0.62, 0.88)


def _tone(t, n):
    for i, w in enumerate(_WEIGHTS[:n - 1]):
        if t < w:
            return i
    return n - 1


def px_rect(region):
    """Logical (x, y, w, h) -> pixel bounds (x0, y0, x1, y1), ends exclusive."""
    x, y, w, h = region
    return x * SCALE, y * SCALE, (x + w) * SCALE, (y + h) * SCALE


def grain(px, region, base, seed=0, spread=0.13, light=0.16, steps=5):
    """Core surface fill: per-pixel tonal scatter plus an implied top-left light."""
    r = ramp(base, steps, spread)
    x0, y0, x1, y1 = px_rect(region)
    w, h = max(1, x1 - x0 - 1), max(1, y1 - y0 - 1)
    for yy in range(y0, y1):
        for xx in range(x0, x1):
            c = r[_tone(hsh(xx, yy, seed), steps)]
            if light:
                g = ((xx - x0) / w + (yy - y0) / h) * 0.5
                c = shade(c, light * (0.5 - g))
            px[xx, yy] = c + (255,)


def bevel(px, region, light=0.12, dark=0.16):
    """Highlight the top/left pixel run, darken the bottom/right - reads as depth."""
    x0, y0, x1, y1 = px_rect(region)
    for xx in range(x0, x1):
        px[xx, y0] = shade(px[xx, y0], light) + (255,)
        px[xx, y1 - 1] = shade(px[xx, y1 - 1], -dark) + (255,)
    for yy in range(y0, y1):
        px[x0, yy] = shade(px[x0, yy], light) + (255,)
        px[x1 - 1, yy] = shade(px[x1 - 1, yy], -dark) + (255,)


def edge(px, region, amt=0.20):
    """Darken the outer ring so the face reads as a discrete block face."""
    x0, y0, x1, y1 = px_rect(region)
    for xx in range(x0, x1):
        px[xx, y0] = shade(px[xx, y0], -amt) + (255,)
        px[xx, y1 - 1] = shade(px[xx, y1 - 1], -amt) + (255,)
    for yy in range(y0, y1):
        px[x0, yy] = shade(px[x0, yy], -amt) + (255,)
        px[x1 - 1, yy] = shade(px[x1 - 1, yy], -amt) + (255,)


def solid(px, region, c):
    x0, y0, x1, y1 = px_rect(region)
    for yy in range(y0, y1):
        for xx in range(x0, x1):
            px[xx, yy] = c + (255,)


def hline(px, x, y, w, c, thick=1):
    """Logical-space horizontal run."""
    solid(px, (x, y, w, thick), c)


def vline(px, x, y, h, c, thick=1):
    solid(px, (x, y, thick, h), c)


def seams(px, region, base, rows, horizontal=True):
    """Plank seams: a dark groove with a lit lip on its far side."""
    x, y, w, h = region
    for r in rows:
        if horizontal:
            solid(px, (x, y + r, w, 1), shade(base, -0.22))
            if r + 1 < h:
                solid(px, (x, y + r + 1, w, 1), shade(base, 0.09))
        else:
            solid(px, (x + r, y, 1, h), shade(base, -0.22))
            if r + 1 < w:
                solid(px, (x + r + 1, y, 1, h), shade(base, 0.09))


def knot(px, cx, cy, base, seed=0):
    """A small wood knot: dark core with a lighter ring, drawn in pixel space."""
    x0, y0 = cx * SCALE, cy * SCALE
    rr = max(1, SCALE)
    for dy in range(-rr, rr + 1):
        for dx in range(-rr, rr + 1):
            dist = (dx * dx + dy * dy) ** 0.5
            if dist > rr + 0.4:
                continue
            xx, yy = x0 + dx, y0 + dy
            try:
                c = shade(base, -0.26) if dist <= rr * 0.55 else shade(base, 0.10)
                px[xx, yy] = c + (255,)
            except IndexError:
                pass


def rivet(px, cx, cy, base):
    """A 1-logical-pixel stud: lit top-left, shadowed bottom-right."""
    x0, y0 = cx * SCALE, cy * SCALE
    for dy in range(SCALE):
        for dx in range(SCALE):
            lit = (dx + dy) < SCALE
            try:
                px[x0 + dx, y0 + dy] = shade(base, 0.20 if lit else -0.22) + (255,)
            except IndexError:
                pass


def box_regions(u, v, w, h, d):
    """Named face rectangles (x, y, w, h) for a Minecraft box UV unwrap."""
    return {
        "top": (u + d, v, w, d),
        "bottom": (u + d + w, v, w, d),
        "east": (u, v + d, d, h),
        "north": (u + d, v + d, w, h),
        "west": (u + d + w, v + d, d, h),
        "south": (u + d + w + d, v + d, w, h),
    }


def new_block():
    img = Image.new("RGBA", (16 * SCALE, 16 * SCALE), (0, 0, 0, 0))
    return img, img.load(), (0, 0, 16, 16)


def save(img, path):
    img.save(f"{ASSETS}/{path}")


# =============================================================================
# 1. Trading Post block textures
# =============================================================================
# --- worktop: wood border framing a chart blotter with a plotted route --------
img, px, full = new_block()
grain(px, full, WOOD, seed=1)
seams(px, full, WOOD, (5, 11))
knot(px, 3, 8, WOOD)
knot(px, 12, 3, WOOD)
grain(px, (2, 2, 12, 12), SLATE, seed=2, spread=0.10, light=0.10)
edge(px, (2, 2, 12, 12), 0.26)
bevel(px, (2, 2, 12, 12), 0.05, 0.10)
# plotted route between two brass station pins
for (x, y) in ((4, 10), (5, 9), (6, 8), (7, 7), (8, 7), (9, 8), (10, 8), (11, 8)):
    solid(px, (x, y, 1, 1), SLATE_LIGHT)
rivet(px, 3, 10, BRASS)
rivet(px, 11, 8, BRASS)
edge(px, full, 0.22)
save(img, "block/trading_post_top.png")

# --- cabinet side: panelled wood with a recessed inset ------------------------
img, px, full = new_block()
grain(px, full, shade(WOOD, -0.05), seed=3)
seams(px, full, WOOD, (4, 9, 14), horizontal=False)
knot(px, 7, 11, WOOD)
# Recessed panel: a dark rebate ring first, then the lighter panel inside it, so the
# inset reads from every side rather than only where the bevel happens to fall.
solid(px, (2, 1, 12, 14), shade(WOOD, -0.30))
grain(px, (3, 2, 10, 12), shade(WOOD, 0.07), seed=4)
bevel(px, (3, 2, 10, 12), -0.14, -0.12)   # inverted: recessed
edge(px, full, 0.22)
save(img, "block/trading_post_side.png")

# --- cabinet front: two drawers, brass pulls, brass nameplate -----------------
img, px, full = new_block()
grain(px, full, shade(WOOD, -0.07), seed=5)
for top in (1, 9):
    dr = (2, top, 12, 6)
    grain(px, dr, shade(WOOD, 0.05), seed=6 + top)
    seams(px, dr, WOOD, (2,))
    edge(px, dr, 0.26)
    bevel(px, dr, 0.11, 0.15)
    solid(px, (6, top + 2, 4, 2), BRASS)
    bevel(px, (6, top + 2, 4, 2), 0.16, 0.20)
# nameplate between the drawers
solid(px, (5, 7, 6, 1), BRASS)
bevel(px, (5, 7, 6, 1), 0.14, 0.18)
edge(px, full, 0.22)
save(img, "block/trading_post_front.png")

# --- hutch: pigeonholes stuffed with rolled manifests -------------------------
# The model's hutch face is only 14x6 logical px (uv [1,1,15,7]), so the whole
# cubby row must live inside rows 1-7 or it gets sliced unreadably.
img, px, full = new_block()
grain(px, full, shade(WOOD, -0.04), seed=8)
for i, cx in enumerate((1, 6, 11)):
    hole = (cx, 1, 4, 6)
    grain(px, hole, SLATE_DEEP, seed=9 + i, spread=0.16, light=0.0)
    edge(px, hole, 0.18)
    bevel(px, hole, -0.14, -0.10)   # inverted: recessed, not raised
    if i != 1:
        solid(px, (cx + 1, 3, 2, 3), PAPER)
        bevel(px, (cx + 1, 3, 2, 3), 0.10, 0.16)
    else:
        solid(px, (cx + 1, 4, 2, 2), OXBLOOD)
        bevel(px, (cx + 1, 4, 2, 2), 0.10, 0.16)
solid(px, (0, 7, 16, 1), shade(WOOD, -0.24))
edge(px, full, 0.22)
save(img, "block/trading_post_hutch.png")

# --- ledger: open manifest book, ruled pages, brass clasp ---------------------
img, px, full = new_block()
grain(px, full, OXBLOOD, seed=12, spread=0.10)
grain(px, (1, 2, 13, 12), PAPER, seed=13, spread=0.07, light=0.10)
solid(px, (7, 2, 2, 12), shade(PAPER, -0.12))     # spine gutter
for row in range(4, 13, 2):
    solid(px, (2, row, 5, 1), shade(PAPER, -0.16))
    solid(px, (9, row, 5, 1), shade(PAPER, -0.16))
solid(px, (7, 7, 2, 2), BRASS)
bevel(px, (7, 7, 2, 2), 0.16, 0.20)
edge(px, (1, 2, 13, 12), 0.14)
edge(px, full, 0.24)
save(img, "block/trading_post_ledger.png")

# --- brass fitting (stamp block / desk lamp base) -----------------------------
img, px, full = new_block()
grain(px, full, BRASS, seed=14, spread=0.14, light=0.20)
# Machined ridges: alternating lit/shadowed bands so it reads as turned metal rather
# than a flat gold field, which is what the first pass looked like.
for row in range(1, 15, 3):
    solid(px, (0, row, 16, 1), shade(BRASS, 0.16))
    solid(px, (0, row + 1, 16, 1), shade(BRASS, -0.15))
solid(px, (0, 0, 16, 2), shade(BRASS, 0.20))
solid(px, (0, 14, 16, 2), shade(BRASS, -0.22))
for i in (2, 8, 13):
    rivet(px, i, 7, BRASS)
bevel(px, full, 0.18, 0.22)
edge(px, full, 0.20)
save(img, "block/trading_post_brass.png")

# =============================================================================
# 2. Delivery crate block textures
# =============================================================================
# rows 0-2 are the lid rim (used by the lid element); rows 3-15 the crate body.
# The x 0-1 columns double as the corner-post elements' texture (uv [0,3,2,16]).
img, px, full = new_block()
grain(px, full, WOOD, seed=20)
seams(px, full, WOOD, (6, 10, 14))
knot(px, 5, 12, WOOD)
knot(px, 11, 5, WOOD)
# corner posts
for cx in (0, 14):
    post = (cx, 3, 2, 13)
    grain(px, post, shade(WOOD, -0.06), seed=21 + cx)
    edge(px, post, 0.20)
    bevel(px, post, 0.12, 0.14)
# metal strapping
strap = (0, 8, 16, 2)
grain(px, strap, METAL, seed=23, spread=0.12, light=0.10)
bevel(px, strap, 0.16, 0.20)
for i in range(1, 16, 4):
    rivet(px, i, 8, METAL)
# lid rim
rim = (0, 0, 16, 3)
grain(px, rim, shade(WOOD, -0.08), seed=24)
solid(px, (0, 0, 16, 1), shade(METAL, 0.05))
bevel(px, rim, 0.12, 0.16)
crate_side_img = img
save(img, "block/delivery_crate_side.png")

img, px, full = new_block()
grain(px, full, shade(WOOD, -0.03), seed=25)
seams(px, full, WOOD, (4, 9, 14))
knot(px, 12, 12, WOOD)
band = (7, 0, 2, 16)
grain(px, band, METAL, seed=26, spread=0.12, light=0.10)
bevel(px, band, 0.16, 0.20)
# shipping label with an oxblood Trading Post stamp
label = (2, 5, 4, 5)
grain(px, label, PAPER, seed=27, spread=0.06, light=0.08)
edge(px, label, 0.20)
solid(px, (3, 6, 2, 1), shade(PAPER, -0.24))
solid(px, (3, 8, 2, 1), shade(PAPER, -0.24))
solid(px, (3, 7, 1, 1), OXBLOOD)
for cx, cy in ((1, 1), (14, 1), (1, 14), (14, 14)):
    rivet(px, cx, cy, METAL)
edge(px, full, 0.22)
crate_top_img = img
save(img, "block/delivery_crate_top.png")

img, px, full = new_block()
grain(px, full, WOOD_DEEP, seed=28)
seams(px, full, WOOD_DEEP, (3, 7, 11))
edge(px, full, 0.22)
crate_bottom_img = img
save(img, "block/delivery_crate_bottom.png")

# =============================================================================
# 3. Freight plane entity atlas (declared 128x128)
#    body(6,6,40)@0,0  nose(4,4,4)@92,0  wing(48,2,12)@0,48  hstab(22,2,8)@0,64
#    vfin(2,10,10)@62,64  nacelle(5,5,14)@0,76  blade_h(14,1,2)@40,96
#    blade_v(1,14,2)@74,96  strut(11,1,2)@84,96
# =============================================================================
plane = Image.new("RGBA", (128 * SCALE, 128 * SCALE), (0, 0, 0, 0))
pp = plane.load()

body = box_regions(0, 0, 6, 6, 40)
for name in ("top", "north", "south"):
    grain(pp, body[name], HULL, seed=30, spread=0.05, light=0.10)
grain(pp, body["bottom"], shade(HULL, -0.08), seed=31, spread=0.05, light=0.08)
for name in ("east", "west"):
    r = body[name]
    grain(pp, r, HULL, seed=32, spread=0.05, light=0.10)
    solid(pp, (r[0], r[1] + 4, r[2], 2), shade(HULL, -0.10))       # belly shadow
    solid(pp, (r[0], r[1] + 2, r[2], 1), ACCENT)                    # cheatline
    solid(pp, (r[0] + 2, r[1] + 1, 5, 2), GLASS)                    # cockpit
    for i in range(10, 34, 4):                                      # cabin windows
        solid(pp, (r[0] + i, r[1] + 1, 1, 1), GLASS)
    for i in range(8, 38, 6):                                       # panel lines
        solid(pp, (r[0] + i, r[1] + 4, 1, 1), shade(HULL, -0.16))
    solid(pp, (r[0] + 30, r[1] + 3, 3, 1), BRASS)                   # registration
    edge(pp, r, 0.16)
bb = body["bottom"]
grain(pp, (bb[0] + 1, bb[1] + 14, bb[2] - 2, 10), shade(HULL, -0.16), seed=33, spread=0.06, light=0.06)
solid(pp, (bb[0] + 1, bb[1] + 19, bb[2] - 2, 1), shade(METAL, -0.10))
rt = body["top"]
solid(pp, (rt[0] + 2, rt[1] + 6, 2, 28), shade(HULL, -0.08))        # spine
edge(pp, rt, 0.16)

nose = box_regions(92, 0, 4, 4, 4)
for name in ("top", "east", "west", "south"):
    grain(pp, nose[name], HULL, seed=34, spread=0.05, light=0.12)
grain(pp, nose["bottom"], shade(HULL, -0.08), seed=35, spread=0.05)
grain(pp, nose["north"], ACCENT, seed=36, spread=0.09)
for name in nose:
    edge(pp, nose[name], 0.16)

wing = box_regions(0, 48, 48, 2, 12)
grain(pp, wing["top"], HULL, seed=37, spread=0.05, light=0.10)
grain(pp, wing["bottom"], shade(HULL, -0.08), seed=38, spread=0.05)
for name in ("north", "south", "east", "west"):
    grain(pp, wing[name], shade(HULL, -0.06), seed=39, spread=0.05)
wt = wing["top"]
grain(pp, (wt[0], wt[1], 6, wt[3]), ACCENT, seed=40, spread=0.09)
grain(pp, (wt[0] + wt[2] - 6, wt[1], 6, wt[3]), ACCENT, seed=41, spread=0.09)
solid(pp, (wt[0], wt[1] + 8, wt[2], 1), shade(HULL, -0.12))
for i in range(8, 44, 6):
    solid(pp, (wt[0] + i, wt[1] + 1, 1, wt[3] - 2), shade(HULL, -0.10))
rivet(pp, wt[0] + 1, wt[1] + 1, BRASS)
edge(pp, wt, 0.16)
edge(pp, wing["bottom"], 0.16)

hstab = box_regions(0, 64, 22, 2, 8)
grain(pp, hstab["top"], HULL, seed=42, spread=0.05, light=0.10)
grain(pp, hstab["bottom"], shade(HULL, -0.08), seed=43, spread=0.05)
for name in ("north", "south", "east", "west"):
    grain(pp, hstab[name], shade(HULL, -0.06), seed=44, spread=0.05)
ht = hstab["top"]
grain(pp, (ht[0], ht[1], 4, ht[3]), ACCENT, seed=45, spread=0.09)
grain(pp, (ht[0] + ht[2] - 4, ht[1], 4, ht[3]), ACCENT, seed=46, spread=0.09)
edge(pp, ht, 0.16)

vfin = box_regions(62, 64, 2, 10, 10)
for name in ("east", "west"):
    r = vfin[name]
    grain(pp, r, ACCENT, seed=47, spread=0.09, light=0.12)
    grain(pp, (r[0], r[1], r[2], 3), HULL, seed=48, spread=0.05)    # white cap stripe
    solid(pp, (r[0], r[1] + 6, r[2], 1), shade(ACCENT, -0.16))
    rivet(pp, r[0] + 4, r[1] + 4, BRASS)                            # tail emblem
    edge(pp, r, 0.16)
for name in ("top", "bottom", "north", "south"):
    grain(pp, vfin[name], shade(ACCENT, -0.10), seed=49, spread=0.08)

nac = box_regions(0, 76, 5, 5, 14)
for name in ("top", "east", "west", "south"):
    grain(pp, nac[name], shade(HULL, -0.06), seed=50, spread=0.06, light=0.12)
grain(pp, nac["bottom"], shade(HULL, -0.16), seed=51, spread=0.06)
grain(pp, nac["north"], shade(METAL, -0.10), seed=52, spread=0.10)
for name in ("east", "west"):
    r = nac[name]
    solid(pp, (r[0], r[1], 2, r[3]), shade(METAL, -0.10))           # intake ring
    edge(pp, r, 0.16)

bh = box_regions(40, 96, 14, 1, 2)
for name in bh:
    grain(pp, bh[name], ROTOR, seed=53, spread=0.10, light=0.10)
bv = box_regions(74, 96, 1, 14, 2)
for name in bv:
    grain(pp, bv[name], ROTOR, seed=54, spread=0.10, light=0.10)
st = box_regions(84, 96, 11, 1, 2)
for name in st:
    grain(pp, st[name], shade(HULL, -0.14), seed=55, spread=0.07, light=0.10)

plane.save(f"{ASSETS}/entity/delivery_drone.png")

# =============================================================================
# 4. Airdrop entity atlas (declared 128x128)
#    body(14,13,14)@0,0  lid(16,3,16)@0,28  post(2,13,2)@64,0
#    cap(16,2,16)@0,48   tier1(18,1,13)@0,68  tier2(18,1,16)@0,84
#    cord(1,16,1)@70,68
#
# The falling crate must be indistinguishable from the DeliveryCrateBlock it turns
# into on landing, so it mirrors that block exactly: same full-block size (16 units,
# not the half-block box it used to be), same body/lid/corner-post construction, and
# - critically - its faces are COPIED PIXEL-FOR-PIXEL out of the very block textures
# generated above, cropped at the same uv windows the block model JSON uses. Hand-
# painting a lookalike would drift out of sync the moment either side changed.
# =============================================================================
pack = Image.new("RGBA", (128 * SCALE, 128 * SCALE), (0, 0, 0, 0))


def blit_logical(src_img, src_region, dst_region):
    """Copy a logical-space region from a block texture into the atlas, 1:1 at SCALE."""
    sx0, sy0, sx1, sy1 = px_rect(src_region)
    dx0, dy0, dx1, dy1 = px_rect(dst_region)
    crop = src_img.crop((sx0, sy0, sx1, sy1))
    assert crop.size == (dx1 - dx0, dy1 - dy0),         f"crate face size mismatch: src {crop.size} vs dst {(dx1 - dx0, dy1 - dy0)}"
    pack.paste(crop, (dx0, dy0))


# Body: side faces use rows 3-16 of the side texture, exactly like the block model's
# uv [1,3,15,16]; underside uses the bottom texture at uv [1,1,15,15].
body = box_regions(0, 0, 14, 13, 14)
for name in ("north", "south", "east", "west"):
    blit_logical(crate_side_img, (1, 3, 14, 13), body[name])
blit_logical(crate_top_img, (1, 1, 14, 14), body["top"])
blit_logical(crate_bottom_img, (1, 1, 14, 14), body["bottom"])

# Lid: sides use the metal-banded rim rows 0-3 (block uv [0,0,16,3]).
lid = box_regions(0, 28, 16, 3, 16)
for name in ("north", "south", "east", "west"):
    blit_logical(crate_side_img, (0, 0, 16, 3), lid[name])
blit_logical(crate_top_img, (0, 0, 16, 16), lid["top"])
blit_logical(crate_bottom_img, (0, 0, 16, 16), lid["bottom"])

# Corner posts: the side texture's own post column (block uv [0,3,2,16]).
post = box_regions(64, 0, 2, 13, 2)
for name in ("north", "south", "east", "west"):
    blit_logical(crate_side_img, (0, 3, 2, 13), post[name])
blit_logical(crate_bottom_img, (0, 0, 2, 2), post["top"])
blit_logical(crate_bottom_img, (0, 0, 2, 2), post["bottom"])

kp = pack.load()


def canopy(region, base, seed, seam_every=3):
    grain(kp, region, base, seed=seed, spread=0.09, light=0.12)
    x, y, w, h = region
    for i in range(seam_every, w, seam_every):
        solid(kp, (x + i, y, 1, h), shade(base, -0.13))
    edge(kp, region, 0.14)


# Canopy scaled up to stay in proportion with the now full-block crate.
cap = box_regions(0, 48, 16, 2, 16)
canopy(cap["top"], CANOPY_A, 74, 4)
canopy(cap["bottom"], shade(CANOPY_A, -0.12), 75, 5)
for name in ("north", "south", "east", "west"):
    canopy(cap[name], CANOPY_A, 76, 4)
ctop = cap["top"]
grain(kp, (ctop[0] + 7, ctop[1] + 7, 2, 2), shade(CANOPY_A, -0.16), seed=77, spread=0.08)

for (u, v, w, h, dep), base, seed in (((0, 68, 18, 1, 13), CANOPY_A, 78),
                                       ((0, 84, 18, 1, 16), CANOPY_B, 80)):
    panel = box_regions(u, v, w, h, dep)
    canopy(panel["top"], base, seed, 4)
    canopy(panel["bottom"], shade(base, -0.12), seed + 1, 4)
    for name in ("north", "south", "east", "west"):
        grain(kp, panel[name], shade(base, -0.10), seed=seed + 2, spread=0.08)

cord = box_regions(70, 68, 1, 16, 1)
for name in cord:
    grain(kp, cord[name], CORD, seed=82, spread=0.10, light=0.10)

pack.save(f"{ASSETS}/entity/delivery_package.png")

# =============================================================================
# 5. Trading GUI background (512x512 atlas, 326x316 panel at 0,0)
#    Kept at 1:1 - vanilla GUIs are 1:1 pixel art and this sits next to the
#    vanilla inventory, so matching that beats out-resolving it. Frame/header/
#    watermark only: the list viewport and trade divider are drawn procedurally
#    by TradingPostScreen because their Y shifts when filter rows wrap.
# =============================================================================
GW, GH = 326, 316
gui = Image.new("RGBA", (512, 512), (0, 0, 0, 0))
gp = gui.load()
gd = ImageDraw.Draw(gui)

for row in range(GH):
    t = row / GH
    c = tuple(int(SLATE[i] + (SLATE_DEEP[i] - SLATE[i]) * t * 0.55) for i in range(3))
    for col in range(GW):
        n = hsh(col, row, 90)
        gp[col, row] = shade(c, 0.020 if n > 0.72 else (-0.020 if n < 0.28 else 0.0)) + (255,)

# faint compass-rose watermark, kept low contrast so panel text stays readable
import math

cx, cy, R = GW // 2, GH - 92, 54
for ang in range(0, 360, 45):
    rad = math.radians(ang)
    gd.line([(cx, cy), (cx + int(math.cos(rad) * R), cy + int(math.sin(rad) * R))], fill=SLATE_MID + (255,))
for rr in (R, int(R * 0.62)):
    gd.ellipse([cx - rr, cy - rr, cx + rr, cy + rr], outline=SLATE_MID + (255,))

# header band
gd.rectangle([0, 0, GW - 1, 15], fill=SLATE_DEEP + (255,))
gd.line([(0, 15), (GW - 1, 15)], fill=shade(SLATE_DEEP, 0.05) + (255,))
gd.line([(0, 16), (GW - 1, 16)], fill=BRASS + (255,))
gd.line([(0, 17), (GW - 1, 17)], fill=BRASS_DARK + (255,))

# outer brass frame with a bevelled reveal
gd.rectangle([0, 0, GW - 1, GH - 1], outline=BRASS_DARK + (255,))
gd.rectangle([1, 1, GW - 2, GH - 2], outline=BRASS + (255,))
gd.rectangle([2, 2, GW - 3, GH - 3], outline=SLATE_DEEP + (255,))
for xx in range(2, GW - 2):
    gp[xx, 2] = shade(SLATE_DEEP, -0.04) + (255,)
for yy in range(2, GH - 2):
    gp[2, yy] = shade(SLATE_DEEP, -0.04) + (255,)

# corner brackets + rivets
for ox, oy, sx, sy in ((3, 3, 1, 1), (GW - 4, 3, -1, 1), (3, GH - 4, 1, -1), (GW - 4, GH - 4, -1, -1)):
    for i in range(11):
        gp[ox + sx * i, oy] = BRASS + (255,)
        gp[ox, oy + sy * i] = BRASS + (255,)
    gp[ox + sx * 3, oy + sy * 3] = shade(BRASS, 0.16) + (255,)
    gp[ox + sx * 4, oy + sy * 4] = shade(BRASS, -0.18) + (255,)

gd.line([(6, GH - 9), (GW - 7, GH - 9)], fill=SLATE_MID + (255,))

gui.save(f"{ASSETS}/gui/trading_post_background.png")

print(f"SCALE={SCALE}: blocks {16 * SCALE}px, plane {128 * SCALE}px, airdrop {128 * SCALE}px, gui 1:1")

# =============================================================================
# 6. Mod list icon (logo.png, 128x128, sits at the resources root)
#    A crate under a canopy over the brass frame - the mod's whole loop in one tile.
# =============================================================================
L = 128
logo = Image.new("RGBA", (L, L), (0, 0, 0, 0))
lp = logo.load()
ld = ImageDraw.Draw(logo)

for row in range(L):
    t = row / L
    c = tuple(int(SLATE[i] + (SLATE_DEEP[i] - SLATE[i]) * t) for i in range(3))
    for col in range(L):
        n = hsh(col, row, 300)
        lp[col, row] = shade(c, 0.02 if n > 0.75 else (-0.02 if n < 0.25 else 0.0)) + (255,)

# canopy: a filled arc of alternating panels
CX, CY, RAD = L // 2, 52, 40
for x in range(CX - RAD, CX + RAD + 1):
    dx = (x - CX) / RAD
    if abs(dx) > 1:
        continue
    h = int((1 - dx * dx) ** 0.5 * 26)
    band = CANOPY_B if ((x - CX + RAD) // 10) % 2 else CANOPY_A
    for y in range(CY - h, CY + 4):
        lp[x, y] = shade(band, 0.10 * (1 - (y - (CY - h)) / max(1, h))) + (255,)
ld.arc([CX - RAD, CY - 30, CX + RAD, CY + 30], 180, 360, fill=shade(CANOPY_A, -0.22) + (255,))

# shroud lines
for sx in (-30, -12, 12, 30):
    ld.line([(CX + sx, CY + 2), (CX + sx // 3, 82)], fill=CORD + (255,))

# crate
ld.rectangle([CX - 22, 82, CX + 22, 116], fill=WOOD + (255,))
for row in range(86, 116, 9):
    ld.line([(CX - 22, row), (CX + 22, row)], fill=shade(WOOD, -0.22) + (255,))
ld.rectangle([CX - 22, 82, CX + 22, 116], outline=shade(WOOD, -0.30) + (255,))
ld.rectangle([CX - 22, 96, CX + 22, 100], fill=METAL + (255,))
ld.rectangle([CX - 24, 78, CX + 24, 84], fill=shade(WOOD, -0.10) + (255,))
ld.rectangle([CX - 24, 78, CX + 24, 84], outline=shade(WOOD, -0.30) + (255,))

# brass frame
ld.rectangle([0, 0, L - 1, L - 1], outline=BRASS_DARK + (255,))
ld.rectangle([1, 1, L - 2, L - 2], outline=BRASS + (255,))

logo.save("src/main/resources/logo.png")
print("wrote logo.png (128x128)")

#!/usr/bin/env python3
"""BiliBox app icon — the TV mark.

Design
  The reference mark in design/icon/source/bilibili-tv.jpg: a white TV outline
  (rounded screen frame, two antennae, two eyes) on flat Bilibili pink
  (#FA729A). The artwork is re-drawn here as geometry measured from that
  reference, so the icon stays sharp at any density and the themed icon can
  reuse the same silhouette instead of a second bitmap.

Geometry
  All constants below are in reference pixels (1280x1280, glyph centred on
  639.5, 638.7). They are mapped onto the Android adaptive-icon grid: 108x108
  canvas, 72x72 visible viewport (18..90), 66x66 safe circle (r=33 around
  54,54). The mark is 56.5 dp wide and its furthest point is 32.7 dp from the
  centre, so no launcher mask crops it.

  Frame weight is not uniform in the reference (92.4 px on the sides, 89.3 px
  on the top and bottom), so the frame is built as an outer plus an inner
  rounded rectangle with an even-odd fill rather than a stroked path.

Outputs
  design/icon/icon.svg                  full-bleed 108x108 artwork
  design/icon/icon-monochrome.svg       themed-icon silhouette
  design/icon/icon-squircle.svg         legacy (masked) artwork
  design/icon/icon-circle.svg           legacy (round-masked) artwork
  design/icon/layer-*.svg               adaptive layers, for the showcase
  design/icon/showcase.html|preview.png showcase sheet
  design/icon/icon-512.png|icon-192.png store icon
  app/src/main/res/drawable/ic_launcher_{background,foreground,monochrome}.xml
  app/src/main/res/mipmap-*/ic_launcher{,_round}.webp

The previous "crate" design is preserved in design/icon/archive/crate/.
"""
import math
import os
import shutil
import subprocess
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
RES = os.path.join(ROOT, "app", "src", "main", "res")
CHROME = "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"

# ----------------------------------------------------------------- palette ---
PINK = "#FA729A"
WHITE = "#FFFFFF"

# ------------------------------------------------- reference glyph geometry --
# Measured from source/bilibili-tv.jpg (alpha threshold at 50%).
SRC_CX, SRC_CY = 639.495, 638.670

FRAME = (223.51, 385.03, 1055.48, 1009.34, 184.93)  # x0, y0, x1, y1, corner r
SCREEN = (315.96, 474.35, 963.06, 919.89, 92.10)

EYE_W, EYE_H = 92.55, 133.50
EYE_CY = 674.85
EYE_CX = (500.88, 778.10)

ANT_W = 90.70
ANT_TIP = (426.85, 313.35)
ANT_DIR = (0.7170, 0.6972)  # unit vector, tip -> body (down-right)
ANT_LEN = 150.0             # runs under the frame, so the join is invisible

# ----------------------------------------------------- adaptive-icon mapping --
GLYPH_W = 56.5              # dp, sized to the 66 dp safe circle
K = GLYPH_W / (FRAME[2] - FRAME[0])


def X(x):
    return 54.0 + (x - SRC_CX) * K


def Y(y):
    return 54.0 + (y - SRC_CY) * K


def S(v):
    return v * K


def f(v):
    return ("%.2f" % v).rstrip("0").rstrip(".")


def rrect(x0, y0, x1, y1, r):
    """Rounded rectangle path, clockwise."""
    return " ".join([
        f"M {f(x0 + r)} {f(y0)}",
        f"L {f(x1 - r)} {f(y0)}",
        f"A {f(r)} {f(r)} 0 0 1 {f(x1)} {f(y0 + r)}",
        f"L {f(x1)} {f(y1 - r)}",
        f"A {f(r)} {f(r)} 0 0 1 {f(x1 - r)} {f(y1)}",
        f"L {f(x0 + r)} {f(y1)}",
        f"A {f(r)} {f(r)} 0 0 1 {f(x0)} {f(y1 - r)}",
        f"L {f(x0)} {f(y0 + r)}",
        f"A {f(r)} {f(r)} 0 0 1 {f(x0 + r)} {f(y0)}",
        "Z",
    ])


# ------------------------------------------------------------------- pieces ---
def frame_path():
    return f"{rrect(*[X(v) if i % 2 == 0 else Y(v) for i, v in enumerate(FRAME[:4])] + [S(FRAME[4])])} " \
           f"{rrect(*[X(v) if i % 2 == 0 else Y(v) for i, v in enumerate(SCREEN[:4])] + [S(SCREEN[4])])}"


def antennae():
    ax, ay = X(ANT_TIP[0]), Y(ANT_TIP[1])
    bx, by = ax + ANT_DIR[0] * S(ANT_LEN), ay + ANT_DIR[1] * S(ANT_LEN)
    left = f"M {f(ax)} {f(ay)} L {f(bx)} {f(by)}"
    right = f"M {f(2 * 54 - ax)} {f(ay)} L {f(2 * 54 - bx)} {f(by)}"
    return [left, right]


def eyes():
    out = []
    for cx in EYE_CX:
        half = (EYE_H - EYE_W) / 2.0
        out.append(
            f"M {f(X(cx))} {f(Y(EYE_CY - half))} L {f(X(cx))} {f(Y(EYE_CY + half))}"
        )
    return out


FRAME_D = frame_path()
ANT_D = antennae()
EYE_D = eyes()
ANT_STROKE = f(ANT_W * K)
EYE_STROKE = f(EYE_W * K)

# (path, stroke width or None for a filled path)
GLYPH_PATHS = [(FRAME_D, None), (ANT_D[0], ANT_STROKE), (ANT_D[1], ANT_STROKE),
               (EYE_D[0], EYE_STROKE), (EYE_D[1], EYE_STROKE)]


# --------------------------------------------------------------------- svg ---
def glyph_svg(indent="  "):
    """The mark as an SVG fragment: frame ring, antennae, eyes."""
    parts = []
    for d, stroke in GLYPH_PATHS:
        if stroke is None:
            parts.append(f'{indent}<path d="{d}" fill="{WHITE}" fill-rule="evenodd"/>')
        else:
            parts.append(f'{indent}<path d="{d}" fill="none" stroke="{WHITE}" '
                         f'stroke-width="{stroke}" stroke-linecap="round"/>')
    return "\n".join(parts)


SVG_FULL = f"""<svg xmlns="http://www.w3.org/2000/svg" width="108" height="108" viewBox="0 0 108 108">
  <rect width="108" height="108" fill="{PINK}"/>
{glyph_svg()}
</svg>
"""

SVG_MONO = f"""<svg xmlns="http://www.w3.org/2000/svg" width="108" height="108" viewBox="0 0 108 108">
{glyph_svg()}
</svg>
"""

def superellipse_path(cx, cy, r, n=4.0, steps=720):
    pts = []
    for i in range(steps):
        t = 2 * math.pi * i / steps
        ct, st = math.cos(t), math.sin(t)
        pts.append((cx + r * math.copysign(abs(ct) ** (2 / n), ct),
                    cy + r * math.copysign(abs(st) ** (2 / n), st)))
    d = [f"M {f(pts[0][0])} {f(pts[0][1])}"]
    d += [f"L {f(x)} {f(y)}" for x, y in pts[1:]]
    return " ".join(d) + " Z"


MASK_SQUIRCLE = superellipse_path(54, 54, 54, n=4.0)
MASK_CIRCLE = "M 0 54 A 54 54 0 1 0 108 54 A 54 54 0 1 0 0 54 Z"


def masked_svg(mask_d):
    return f"""<svg xmlns="http://www.w3.org/2000/svg" width="108" height="108" viewBox="0 0 108 108">
  <defs><clipPath id="mask"><path d="{mask_d}"/></clipPath></defs>
  <g clip-path="url(#mask)">
    <rect width="108" height="108" fill="{PINK}"/>
{glyph_svg("    ")}
  </g>
</svg>
"""


# ---------------------------------------------------------------- rendering ---
def chrome_shot(html_path, png_path, w, h, scale=1, transparent=False):
    profile = tempfile.mkdtemp(prefix="biliicon-chrome-")
    cmd = [
        CHROME, "--headless=new", "--no-sandbox", "--disable-gpu",
        "--disable-dev-shm-usage", "--disable-breakpad", "--disable-crash-reporter",
        "--no-first-run", "--hide-scrollbars",
        # Wait for the page (and its SVG images) before the screenshot,
        # otherwise the capture can come back blank.
        "--virtual-time-budget=3000", "--run-all-compositor-stages-before-draw",
        "--user-data-dir=" + profile,
        f"--force-device-scale-factor={scale}",
        f"--window-size={w},{h}",
        f"--screenshot={png_path}",
    ]
    if transparent:
        cmd.append("--default-background-color=00000000")
    cmd.append("file://" + html_path)
    try:
        subprocess.run(cmd, capture_output=True, check=False)
    finally:
        shutil.rmtree(profile, ignore_errors=True)


def html_for(svg_path, w, h):
    src = "file://" + os.path.abspath(svg_path)
    return f"""<!doctype html><html><head><meta charset="utf-8"><style>
  html,body {{ margin:0; padding:0; background:transparent; }}
  img {{ display:block; width:{w}px; height:{h}px; }}
</style></head><body><img src="{src}"></body></html>"""


# ---------------------------------------------------------------- showcase ---
SHOWCASE = """<!doctype html><html><head><meta charset="utf-8"><style>
  :root { color-scheme: dark; }
  * { box-sizing: border-box; }
  body { margin:0; width:1500px; font:14px/1.45 -apple-system,"SF Pro Text","Helvetica Neue",sans-serif;
         background:
           radial-gradient(900px 620px at 12% -10%, #2a1a26 0%, rgba(42,26,38,0) 60%),
           radial-gradient(760px 520px at 92% 8%, #1c2230 0%, rgba(28,34,48,0) 62%),
           #0a0b0e;
         color:#e9ebef; padding:40px 52px 34px; }
  .eyebrow { font-size:11px; letter-spacing:.22em; text-transform:uppercase; color:#8d95a1; }
  h1 { font-size:26px; letter-spacing:-.01em; margin:8px 0 4px; font-weight:600; }
  h1 span { color:#f87ba0; }
  .sub { font-size:13px; color:#98a0ab; margin:0 0 28px; }
  .top { display:flex; gap:34px; align-items:center; }
  .hero { display:flex; gap:30px; }
  .hero > div { text-align:center; }
  .hero-tile { width:226px; height:226px; border-radius:30%; overflow:hidden;
               box-shadow:0 26px 60px rgba(0,0,0,.55), 0 0 0 1px rgba(255,255,255,.05); }
  .hero-round { width:226px; height:226px; border-radius:50%; overflow:hidden;
                box-shadow:0 26px 60px rgba(0,0,0,.55), 0 0 0 1px rgba(255,255,255,.05); }
  .hero-tile img, .hero-round img { width:100%; height:100%; display:block; }
  .caption { font-size:10px; color:#7f8793; margin-top:10px; letter-spacing:.12em; }
  .panel { background:rgba(255,255,255,.035); border:1px solid rgba(255,255,255,.06);
           border-radius:20px; padding:24px 28px 18px; flex:1; }
  .sizes { display:flex; align-items:flex-end; justify-content:space-between; gap:10px; }
  .sizes .cell { text-align:center; }
  .masked { overflow:hidden; border-radius:26%; display:block; }
  .masked.round { border-radius:50%; }
  .masked img { display:block; width:100%; height:100%; }
  .num { font-size:10px; color:#7f8793; margin-top:7px; letter-spacing:.08em; }
  .divider { height:1px; background:linear-gradient(90deg,rgba(255,255,255,.09),rgba(255,255,255,0)); margin:26px 0 0; }
  .bottom { display:flex; gap:30px; margin-top:24px; align-items:flex-start; }
  .layers { display:flex; gap:14px; }
  .layer { background:#12141a; border:1px solid rgba(255,255,255,.06); border-radius:16px;
           padding:12px; text-align:center; }
  .layer .chk { width:92px; height:92px; border-radius:14px; overflow:hidden; display:grid; place-items:center;
                background-image:linear-gradient(45deg,#1a1d24 25%,transparent 25%,transparent 75%,#1a1d24 75%),
                                 linear-gradient(45deg,#1a1d24 25%,transparent 25%,transparent 75%,#1a1d24 75%);
                background-size:16px 16px; background-position:0 0,8px 8px; }
  .layer img { width:92px; height:92px; }
  .layer .cap { font-size:9px; color:#838b97; margin-top:8px; letter-spacing:.1em; }
  .mono { display:flex; gap:14px; }
  .mono .bg { width:92px; height:92px; border-radius:24%; display:grid; place-items:center; }
  .mono .bg img { width:72%; height:72%; }
  .walls { display:flex; gap:16px; }
  .wall { width:236px; height:124px; border-radius:22px; position:relative; overflow:hidden;
          display:flex; flex-direction:column; align-items:center; justify-content:center; gap:8px;
          box-shadow:inset 0 0 0 1px rgba(255,255,255,.08); }
  .wall.light { background:
      radial-gradient(160px 120px at 20% 10%, #ffe3ec 0%, rgba(255,227,236,0) 70%),
      radial-gradient(200px 160px at 90% 90%, #d8e6ff 0%, rgba(216,230,255,0) 70%),
      linear-gradient(150deg,#f7f0f3,#e9eef8); }
  .wall.dark { background:
      radial-gradient(180px 140px at 15% 0%, #3a2233 0%, rgba(58,34,51,0) 70%),
      radial-gradient(200px 160px at 95% 100%, #1e2c44 0%, rgba(30,44,68,0) 70%),
      linear-gradient(150deg,#1b1a20,#0e1118); }
  .wall .masked { width:58px; height:58px; box-shadow:0 8px 18px rgba(0,0,0,.35); }
  .wall .name { font-size:11px; letter-spacing:.01em; }
  .wall.light .name { color:#20242b; }
  .wall.dark .name { color:#eef1f5; }
  .footer { margin-top:26px; display:flex; gap:10px; align-items:center; color:#7c8490; font-size:11px; }
  .swatch { width:14px; height:14px; border-radius:50%; display:inline-block; }
</style></head><body>
  <div class="eyebrow">BiliBox &middot; launcher icon</div>
  <h1>A pink box, <span>tuned in</span>.</h1>
  <p class="sub">The reference mark redrawn as geometry &mdash; a white TV outline on #FA729A, sized to the 66&nbsp;dp safe circle so no launcher mask crops it.</p>

  <div class="top">
    <div class="hero">
      <div>
        <div class="hero-tile"><img src="icon-squircle.svg"></div>
        <div class="caption">SQUIRCLE MASK</div>
      </div>
      <div>
        <div class="hero-round"><img src="icon-squircle.svg"></div>
        <div class="caption">CIRCLE MASK</div>
      </div>
    </div>
    <div class="panel">
      <div class="eyebrow" style="margin-bottom:18px">Size ladder &middot; squircle mask</div>
      <div class="sizes">__SIZES__</div>
    </div>
  </div>

  <div class="divider"></div>
  <div class="bottom">
    <div>
      <div class="eyebrow" style="margin-bottom:12px">Adaptive layers</div>
      <div class="layers">
        <div class="layer"><div class="chk"><img src="layer-background.svg"></div><div class="cap">BACKGROUND</div></div>
        <div class="layer"><div class="chk"><img src="layer-foreground.svg"></div><div class="cap">FOREGROUND</div></div>
      </div>
    </div>
    <div>
      <div class="eyebrow" style="margin-bottom:12px">Themed icon</div>
      <div class="mono">
        <div class="bg" style="background:#e8def8"><img src="icon-monochrome.svg" style="filter:brightness(0) opacity(.78)"></div>
        <div class="bg" style="background:#2b2930"><img src="icon-monochrome.svg"></div>
      </div>
    </div>
    <div>
      <div class="eyebrow" style="margin-bottom:12px">On the home screen</div>
      <div class="walls">
        <div class="wall light"><div class="masked"><img src="icon-squircle.svg"></div><div class="name">BiliBox</div></div>
        <div class="wall dark"><div class="masked"><img src="icon-squircle.svg"></div><div class="name">BiliBox</div></div>
      </div>
    </div>
  </div>

  <div class="footer">
    <span class="swatch" style="background:#FA729A"></span>
    <span class="swatch" style="background:#FF9BB7"></span>
    <span class="swatch" style="background:#FFFFFF"></span>
    <span style="margin-left:6px">reference mark &rarr; geometry &middot; 108&times;108 grid &middot; 56.5 dp mark inside the 66 dp safe circle</span>
  </div>
</body></html>"""


# ------------------------------------------------------------------- build ---
def build():
    from PIL import Image

    # 1. vector sources -----------------------------------------------------
    open(os.path.join(HERE, "icon.svg"), "w").write(SVG_FULL)
    open(os.path.join(HERE, "icon-monochrome.svg"), "w").write(SVG_MONO)
    open(os.path.join(HERE, "icon-squircle.svg"), "w").write(masked_svg(MASK_SQUIRCLE))
    open(os.path.join(HERE, "icon-circle.svg"), "w").write(masked_svg(MASK_CIRCLE))
    open(os.path.join(HERE, "layer-background.svg"), "w").write(
        f'<svg xmlns="http://www.w3.org/2000/svg" width="108" height="108" viewBox="0 0 108 108">'
        f'<rect width="108" height="108" fill="{PINK}"/></svg>'
    )
    open(os.path.join(HERE, "layer-foreground.svg"), "w").write(
        f'<svg xmlns="http://www.w3.org/2000/svg" width="108" height="108" viewBox="0 0 108 108">\n'
        f'{glyph_svg()}\n</svg>\n'
    )

    # 2. Android vector drawables -------------------------------------------
    drawable = os.path.join(RES, "drawable")
    os.makedirs(drawable, exist_ok=True)

    open(os.path.join(drawable, "ic_launcher_background.xml"), "w").write(f"""<?xml version="1.0" encoding="utf-8"?>
<!-- BiliBox launcher icon: pink tile. Generated by design/icon/build_icons.py -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">

    <path
        android:pathData="M0,0h108v108h-108z"
        android:fillColor="{PINK.upper()}" />
</vector>
""")

    def glyph_xml(indent, fill):
        lines = [
            f'{indent}<!-- Screen frame, antennae and eyes. The frame is an outer plus an',
            f'{indent}     inner rounded rectangle (even-odd) because its sides are 6.66 dp',
            f'{indent}     and its top/bottom 6.44 dp, as in the reference. -->',
            f'{indent}<path',
            f'{indent}    android:pathData="{FRAME_D}"',
            f'{indent}    android:fillColor="{fill}"',
            f'{indent}    android:fillType="evenOdd" />',
        ]
        for d, w in GLYPH_PATHS[1:]:
            lines += [
                f'{indent}<path',
                f'{indent}    android:pathData="{d}"',
                f'{indent}    android:strokeColor="{fill}"',
                f'{indent}    android:strokeWidth="{w}"',
                f'{indent}    android:strokeLineCap="round" />',
            ]
        return "\n".join(lines)

    open(os.path.join(drawable, "ic_launcher_foreground.xml"), "w").write(f"""<?xml version="1.0" encoding="utf-8"?>
<!-- BiliBox launcher icon: the TV mark. Generated by design/icon/build_icons.py -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">

{glyph_xml("    ", WHITE.upper())}
</vector>
""")

    open(os.path.join(drawable, "ic_launcher_monochrome.xml"), "w").write(f"""<?xml version="1.0" encoding="utf-8"?>
<!-- BiliBox themed icon: the same silhouette, single colour. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">

{glyph_xml("    ", WHITE.upper())}
</vector>
""")

    # adaptive icon manifests
    for name in ("ic_launcher.xml", "ic_launcher_round.xml"):
        open(os.path.join(RES, "mipmap-anydpi", name), "w").write(
            """<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
    <monochrome android:drawable="@drawable/ic_launcher_monochrome" />
</adaptive-icon>
"""
        )

    # 3. raster masters -----------------------------------------------------
    tmpdir = tempfile.mkdtemp(prefix="biliicon-")
    tmp_html = os.path.join(tmpdir, "_render.html")
    masters = {}
    for name, key in (("icon-squircle.svg", "ic_launcher"),
                      ("icon-circle.svg", "ic_launcher_round")):
        png = os.path.join(tmpdir, key + ".png")
        open(tmp_html, "w").write(html_for(os.path.join(HERE, name), 1024, 1024))
        chrome_shot(tmp_html, png, 1024, 1024, transparent=True)
        master = Image.open(png).convert("RGBA")
        if len(master.getcolors(1 << 20) or []) < 2:
            raise RuntimeError("chrome did not render the icon: " + png)
        masters[key] = master

    # 4. webp density buckets ----------------------------------------------
    buckets = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
    for name, master in masters.items():
        for bucket, px in buckets.items():
            d = os.path.join(RES, f"mipmap-{bucket}")
            os.makedirs(d, exist_ok=True)
            master.resize((px, px), Image.LANCZOS).save(
                os.path.join(d, f"{name}.webp"), "WEBP", quality=95, method=6
            )
    masters["ic_launcher"].resize((512, 512), Image.LANCZOS).save(
        os.path.join(HERE, "icon-512.png"), "PNG"
    )
    masters["ic_launcher"].resize((192, 192), Image.LANCZOS).save(
        os.path.join(HERE, "icon-192.png"), "PNG"
    )

    # 5. showcase -----------------------------------------------------------
    cells = []
    for s in (192, 128, 96, 64, 48, 32, 24):
        cells.append(
            f'<div class="cell"><div class="masked" style="width:{s}px;height:{s}px">'
            f'<img src="icon-squircle.svg"></div><div class="num">{s} px</div></div>'
        )
    open(os.path.join(HERE, "showcase.html"), "w").write(
        SHOWCASE.replace("__SIZES__", "".join(cells))
    )
    chrome_shot(os.path.join(HERE, "showcase.html"), os.path.join(HERE, "preview.png"),
                1500, 748, scale=2, transparent=True)
    shutil.rmtree(tmpdir, ignore_errors=True)


if __name__ == "__main__":
    build()
    print("icons built")

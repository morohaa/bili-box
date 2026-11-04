#!/usr/bin/env python3
"""BiliBox app icon — single source of truth.

Design
  A cream "crate" (lid slab + body) with a play button knocked out of the
  front, tilted 6 degrees, on a deep-rose gradient tile with a soft halo.
  Crate = "Box", play = video, pink = the app's primary colour.

Outputs
  design/icon/icon.svg                  full-bleed 108x108 artwork
  design/icon/icon-monochrome.svg      themed-icon silhouette
  design/icon/icon-squircle.svg        launcher-masked (legacy) artwork
  design/icon/preview.png              showcase sheet
  design/icon/icon-512.png             512px store icon
  app/src/main/res/drawable/ic_launcher_background.xml
  app/src/main/res/drawable/ic_launcher_foreground.xml
  app/src/main/res/drawable/ic_launcher_monochrome.xml
  app/src/main/res/mipmap-*/ic_launcher{,_round}.webp

Geometry is authored on the adaptive-icon grid: 108x108 canvas, 72x72 visible
viewport (18..90), 66x66 safe zone (r=33 around 54,54). The legacy icons are a
72x72 centre crop of the same artwork, masked to a squircle / circle.
"""
import math
import os
import shutil
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
RES = os.path.join(ROOT, "app", "src", "main", "res")
PY = "/Users/owl/.dsh/dsh-runtimes/dsh-primary-runtime/dependencies/python/bin/python3"
CHROME = "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
C = 54.0

# ----------------------------------------------------------------- palette ---
TILE_TOP = "#EF6E91"
TILE_MID = "#CB3D66"
TILE_BOTTOM = "#8E2144"
HALO = "#FFA8C0"
CREAM_TOP = "#FFFDFC"
CREAM_BOTTOM = "#FFE4EC"

# ----------------------------------------------------------------- geometry --
LID_W, LID_H, LID_R_TOP, LID_R_BOTTOM = 48.0, 9.6, 4.5, 1.9
GAP = 1.7
BODY_W, BODY_H, BODY_R_TOP, BODY_R_BOTTOM = 44.0, 32.0, 1.9, 9.6
MARK_TOP = 32.4
LID_CY = MARK_TOP + LID_H / 2
BODY_TOP = MARK_TOP + LID_H + GAP
BODY_CY = BODY_TOP + BODY_H / 2
MARK_BOTTOM = BODY_TOP + BODY_H
TRI_W, TRI_H, TRI_R = 14.6, 17.0, 2.9
TRI_CY = BODY_CY + 0.4
TILT = -6.0


def f(v):
    return ("%.2f" % v).rstrip("0").rstrip(".")


def rrect(cx, cy, w, h, tl, tr, br, bl):
    """Rounded rectangle path with independent corner radii."""
    x0, y0, x1, y1 = cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2
    d = [f"M {f(x0 + tl)} {f(y0)}", f"H {f(x1 - tr)}"]
    if tr:
        d.append(f"A {f(tr)} {f(tr)} 0 0 1 {f(x1)} {f(y0 + tr)}")
    d.append(f"V {f(y1 - br)}")
    if br:
        d.append(f"A {f(br)} {f(br)} 0 0 1 {f(x1 - br)} {f(y1)}")
    d.append(f"H {f(x0 + bl)}")
    if bl:
        d.append(f"A {f(bl)} {f(bl)} 0 0 1 {f(x0)} {f(y1 - bl)}")
    d.append(f"V {f(y0 + tl)}")
    if tl:
        d.append(f"A {f(tl)} {f(tl)} 0 0 1 {f(x0 + tl)} {f(y0)}")
    d.append("Z")
    return " ".join(d)


def rounded_triangle(cx, cy, w, h, r):
    pts = [(cx - w / 2, cy - h / 2), (cx - w / 2, cy + h / 2), (cx + w / 2, cy)]
    out = []
    for i in range(3):
        p, a, b = pts[i], pts[i - 1], pts[(i + 1) % 3]

        def unit(q):
            dx, dy = q[0] - p[0], q[1] - p[1]
            dd = math.hypot(dx, dy)
            return dx / dd, dy / dd

        ua, ub = unit(a), unit(b)
        rr = min(r, math.dist(a, p) / 2, math.dist(b, p) / 2)
        p1 = (p[0] + ua[0] * rr, p[1] + ua[1] * rr)
        p2 = (p[0] + ub[0] * rr, p[1] + ub[1] * rr)
        out.append(f"{'M' if i == 0 else 'L'} {f(p1[0])} {f(p1[1])}")
        out.append(f"Q {f(p[0])} {f(p[1])} {f(p2[0])} {f(p2[1])}")
    return " ".join(out) + " Z"


LID_PATH = rrect(C, LID_CY, LID_W, LID_H, LID_R_TOP, LID_R_TOP, LID_R_BOTTOM, LID_R_BOTTOM)
BODY_PATH = rrect(C, BODY_CY, BODY_W, BODY_H, BODY_R_TOP, BODY_R_TOP, BODY_R_BOTTOM, BODY_R_BOTTOM)
TRI_PATH = rounded_triangle(C, TRI_CY, TRI_W, TRI_H, TRI_R)
MARK_PATH = f"{LID_PATH} {BODY_PATH} {TRI_PATH}"

# --------------------------------------------------------------------- svg ---
DEFS = f"""  <defs>
    <linearGradient id="tile" x1="0" y1="0" x2="1" y2="1">
      <stop offset="0" stop-color="{TILE_TOP}"/>
      <stop offset="0.55" stop-color="{TILE_MID}"/>
      <stop offset="1" stop-color="{TILE_BOTTOM}"/>
    </linearGradient>
    <radialGradient id="halo" cx="0.34" cy="0.14" r="0.72">
      <stop offset="0" stop-color="{HALO}" stop-opacity="0.5"/>
      <stop offset="1" stop-color="{HALO}" stop-opacity="0"/>
    </radialGradient>
    <linearGradient id="cream" x1="0" y1="0" x2="0.06" y2="1">
      <stop offset="0" stop-color="{CREAM_TOP}"/>
      <stop offset="1" stop-color="{CREAM_BOTTOM}"/>
    </linearGradient>
  </defs>"""

ART = f"""  <rect width="108" height="108" fill="url(#tile)"/>
  <rect width="108" height="108" fill="url(#halo)"/>
  <g transform="rotate({TILT:g} {C:g} {C:g})">
    <path d="{MARK_PATH}" fill="url(#cream)" fill-rule="evenodd"/>
  </g>"""

SVG_FULL = f"""<svg xmlns="http://www.w3.org/2000/svg" width="108" height="108" viewBox="0 0 108 108">
{DEFS}
{ART}
</svg>
"""

SVG_MONO = f"""<svg xmlns="http://www.w3.org/2000/svg" width="108" height="108" viewBox="0 0 108 108">
  <g transform="rotate({TILT:g} {C:g} {C:g})">
    <path d="{MARK_PATH}" fill="#FFFFFF" fill-rule="evenodd"/>
  </g>
</svg>
"""


def superellipse_path(cx, cy, r, n=4.0, steps=720):
    pts = []
    for i in range(steps):
        t = 2 * math.pi * i / steps
        ct, st = math.cos(t), math.sin(t)
        x = cx + r * math.copysign(abs(ct) ** (2 / n), ct)
        y = cy + r * math.copysign(abs(st) ** (2 / n), st)
        pts.append((x, y))
    d = [f"M {f(pts[0][0])} {f(pts[0][1])}"]
    d += [f"L {f(x)} {f(y)}" for x, y in pts[1:]]
    return " ".join(d) + " Z"


def masked_svg(mask_d):
    """72x72 centre crop, clipped to a launcher-style mask."""
    return f"""<svg xmlns="http://www.w3.org/2000/svg" width="108" height="108" viewBox="18 18 72 72">
  <defs>
    <clipPath id="mask"><path d="{mask_d}"/></clipPath>
{DEFS}
  </defs>
  <g clip-path="url(#mask)">
{ART}
  </g>
</svg>
"""


# ------------------------------------------------------------------ render ---
def chrome_shot(html_path, png_path, w, h, scale=1, transparent=False):
    cmd = [
        CHROME, "--headless=new", "--no-sandbox", "--disable-gpu",
        "--disable-dev-shm-usage", "--disable-breakpad", "--disable-crash-reporter",
        "--no-first-run", "--hide-scrollbars",
        "--user-data-dir=/tmp/biliicon-chrome",
        f"--force-device-scale-factor={scale}",
        f"--window-size={w},{h}",
        f"--screenshot={png_path}",
    ]
    if transparent:
        cmd.append("--default-background-color=00000000")
    cmd.append("file://" + html_path)
    subprocess.run(cmd, capture_output=True, check=False)


def html_for(svg_name, w, h, bg="transparent", css=""):
    return f"""<!doctype html><html><head><meta charset="utf-8"><style>
  html,body {{ margin:0; padding:0; background:{bg}; }}
  img {{ display:block; width:{w}px; height:{h}px; }}
  {css}
</style></head><body><img src="{svg_name}"></body></html>"""


# ---------------------------------------------------------------- showcase ---
SHOWCASE = """<!doctype html><html><head><meta charset="utf-8"><style>
  :root { color-scheme: dark; }
  * { box-sizing: border-box; }
  body { margin:0; width:1500px; font:14px/1.45 -apple-system,"SF Pro Text","Helvetica Neue",sans-serif;
         background:
           radial-gradient(900px 620px at 12% -10%, #2a1a26 0%, rgba(42,26,38,0) 60%),
           radial-gradient(760px 520px at 92% 8%, #1c2230 0%, rgba(28,34,48,0) 62%),
           #0a0b0e;
         color:#e9ebef; padding:44px 52px 40px; }
  .eyebrow { font-size:11px; letter-spacing:.22em; text-transform:uppercase; color:#8d95a1; }
  h1 { font-size:26px; letter-spacing:-.01em; margin:8px 0 4px; font-weight:600; }
  h1 span { color:#ef7196; }
  .sub { font-size:13px; color:#98a0ab; margin:0 0 32px; }
  .top { display:flex; gap:34px; align-items:center; }
  .hero { display:flex; gap:30px; }
  .hero > div { text-align:center; }
  .hero-tile { width:230px; height:230px; border-radius:30%; overflow:hidden;
               box-shadow:0 26px 60px rgba(0,0,0,.55), 0 0 0 1px rgba(255,255,255,.05); }
  .hero-round { width:230px; height:230px; border-radius:50%; overflow:hidden;
                box-shadow:0 26px 60px rgba(0,0,0,.55), 0 0 0 1px rgba(255,255,255,.05); }
  .hero-tile img, .hero-round img { width:100%; height:100%; display:block; }
  .caption { font-size:10px; color:#7f8793; margin-top:10px; letter-spacing:.12em; }
  .panel { background:rgba(255,255,255,.035); border:1px solid rgba(255,255,255,.06);
           border-radius:20px; padding:26px 28px 20px; flex:1; }
  .sizes { display:flex; align-items:flex-end; justify-content:space-between; gap:10px; }
  .sizes .cell { text-align:center; }
  .masked { overflow:hidden; border-radius:26%; display:block; }
  .masked.round { border-radius:50%; }
  .masked img { display:block; width:100%; height:100%; }
  .num { font-size:10px; color:#7f8793; margin-top:7px; letter-spacing:.08em; }
  .divider { height:1px; background:linear-gradient(90deg,rgba(255,255,255,.09),rgba(255,255,255,0)); margin:30px 0 0; }
  .bottom { display:flex; gap:34px; margin-top:26px; align-items:flex-start; }
  .layers { display:flex; gap:14px; }
  .layer { background:#12141a; border:1px solid rgba(255,255,255,.06); border-radius:16px;
           padding:12px; text-align:center; }
  .layer .chk { width:96px; height:96px; border-radius:14px; overflow:hidden; display:grid; place-items:center;
                background-image:linear-gradient(45deg,#1a1d24 25%,transparent 25%,transparent 75%,#1a1d24 75%),
                                 linear-gradient(45deg,#1a1d24 25%,transparent 25%,transparent 75%,#1a1d24 75%);
                background-size:16px 16px; background-position:0 0,8px 8px; }
  .layer img { width:96px; height:96px; }
  .layer .cap { font-size:9px; color:#838b97; margin-top:8px; letter-spacing:.1em; }
  .mono { display:flex; gap:14px; }
  .mono .bg { width:96px; height:96px; border-radius:24%; display:grid; place-items:center; }
  .mono .bg img { width:72%; height:72%; }
  .walls { display:flex; gap:16px; }
  .wall { width:250px; height:130px; border-radius:22px; position:relative; overflow:hidden;
          display:flex; flex-direction:column; align-items:center; justify-content:center; gap:9px;
          box-shadow:inset 0 0 0 1px rgba(255,255,255,.08); }
  .wall.light { background:
      radial-gradient(160px 120px at 20% 10%, #ffe3ec 0%, rgba(255,227,236,0) 70%),
      radial-gradient(200px 160px at 90% 90%, #d8e6ff 0%, rgba(216,230,255,0) 70%),
      linear-gradient(150deg,#f7f0f3,#e9eef8); }
  .wall.dark { background:
      radial-gradient(180px 140px at 15% 0%, #3a2233 0%, rgba(58,34,51,0) 70%),
      radial-gradient(200px 160px at 95% 100%, #1e2c44 0%, rgba(30,44,68,0) 70%),
      linear-gradient(150deg,#1b1a20,#0e1118); }
  .wall .masked { width:62px; height:62px; box-shadow:0 8px 18px rgba(0,0,0,.35); }
  .wall .name { font-size:11px; letter-spacing:.01em; }
  .wall.light .name { color:#20242b; }
  .wall.dark .name { color:#eef1f5; }
  .footer { margin-top:30px; display:flex; gap:10px; align-items:center; color:#7c8490; font-size:11px; }
  .swatch { width:14px; height:14px; border-radius:50%; display:inline-block; }
</style></head><body>
  <div class="eyebrow">BiliBox · launcher icon</div>
  <h1>The box that <span>plays</span>.</h1>
  <p class="sub">A cream crate with a play window, tilted 6°, on the app's deep-rose tile — one silhouette that still reads at 24 px.</p>

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
      <div class="eyebrow" style="margin-bottom:18px">Size ladder · squircle mask</div>
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
    <span class="swatch" style="background:#EF6E91"></span>
    <span class="swatch" style="background:#CB3D66"></span>
    <span class="swatch" style="background:#8E2144"></span>
    <span class="swatch" style="background:#FFE4EC"></span>
    <span style="margin-left:6px">palette pulled from the app's primary #EF7196</span>
  </div>
</body></html>"""

MASK_SQUIRCLE = superellipse_path(54, 54, 36, n=4.0)
MASK_CIRCLE = "M 18 54 A 36 36 0 1 0 90 54 A 36 36 0 1 0 18 54 Z"


def build():
    # 1. svg sources --------------------------------------------------------
    open(os.path.join(HERE, "icon.svg"), "w").write(SVG_FULL)
    open(os.path.join(HERE, "icon-monochrome.svg"), "w").write(SVG_MONO)
    open(os.path.join(HERE, "icon-squircle.svg"), "w").write(masked_svg(MASK_SQUIRCLE))
    open(os.path.join(HERE, "icon-circle.svg"), "w").write(masked_svg(MASK_CIRCLE))

    # adaptive layers, for the showcase only
    open(os.path.join(HERE, "layer-background.svg"), "w").write(
        f'<svg xmlns="http://www.w3.org/2000/svg" width="108" height="108" viewBox="0 0 108 108">'
        f'<defs>{DEFS.split("</defs>")[0].split("<defs>")[1]}</defs>'
        f'  <rect width="108" height="108" fill="url(#tile)"/>'
        f'  <rect width="108" height="108" fill="url(#halo)"/></svg>'
    )
    open(os.path.join(HERE, "layer-foreground.svg"), "w").write(
        f'<svg xmlns="http://www.w3.org/2000/svg" width="108" height="108" viewBox="0 0 108 108">'
        f'<g transform="rotate({TILT:g} {C:g} {C:g})">'
        f'<path d="{MARK_PATH}" fill="#FFFDFC" fill-rule="evenodd"/></g></svg>'
    )

    # 2. Android vector drawables ------------------------------------------
    drawable = os.path.join(RES, "drawable")
    os.makedirs(drawable, exist_ok=True)

    bg_xml = f"""<?xml version="1.0" encoding="utf-8"?>
<!-- BiliBox launcher icon: tile. Generated by design/icon/build_icons.py -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:aapt="http://schemas.android.com/aapt"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">

    <!-- Deep-rose diagonal gradient. -->
    <path android:pathData="M0,0h108v108h-108z">
        <aapt:attr name="android:fillColor">
            <gradient
                android:type="linear"
                android:startX="0"
                android:startY="0"
                android:endX="108"
                android:endY="108">
                <item android:offset="0" android:color="{TILE_TOP}" />
                <item android:offset="0.55" android:color="{TILE_MID}" />
                <item android:offset="1" android:color="{TILE_BOTTOM}" />
            </gradient>
        </aapt:attr>
    </path>

    <!-- Soft halo so the tile never reads as a flat swatch. -->
    <path android:pathData="M0,0h108v108h-108z">
        <aapt:attr name="android:fillColor">
            <gradient
                android:type="radial"
                android:centerX="36.7"
                android:centerY="15.1"
                android:gradientRadius="77.8">
                <item android:offset="0" android:color="#80{HALO[1:]}" />
                <item android:offset="1" android:color="#00{HALO[1:]}" />
            </gradient>
        </aapt:attr>
    </path>
</vector>
"""
    open(os.path.join(drawable, "ic_launcher_background.xml"), "w").write(bg_xml)

    fg_xml = f"""<?xml version="1.0" encoding="utf-8"?>
<!-- BiliBox launcher icon: cream crate with a play window. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:aapt="http://schemas.android.com/aapt"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">

    <group
        android:pivotX="54"
        android:pivotY="54"
        android:rotation="{TILT:g}">
        <!-- Lid, body and play window share one evenOdd path so the window is
             knocked out rather than painted over. -->
        <path
            android:pathData="{MARK_PATH}"
            android:fillType="evenOdd">
            <aapt:attr name="android:fillColor">
                <gradient
                    android:type="linear"
                    android:startX="54"
                    android:startY="{MARK_TOP:g}"
                    android:endX="54"
                    android:endY="{MARK_BOTTOM:g}">
                    <item android:offset="0" android:color="{CREAM_TOP}" />
                    <item android:offset="1" android:color="{CREAM_BOTTOM}" />
                </gradient>
            </aapt:attr>
        </path>
    </group>
</vector>
"""
    open(os.path.join(drawable, "ic_launcher_foreground.xml"), "w").write(fg_xml)

    mono_xml = f"""<?xml version="1.0" encoding="utf-8"?>
<!-- BiliBox themed icon: single-colour silhouette, play window knocked out. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">

    <group
        android:pivotX="54"
        android:pivotY="54"
        android:rotation="{TILT:g}">
        <path
            android:pathData="{MARK_PATH}"
            android:fillColor="#FFFFFFFF"
            android:fillType="evenOdd" />
    </group>
</vector>
"""
    open(os.path.join(drawable, "ic_launcher_monochrome.xml"), "w").write(mono_xml)

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

    # 3. raster masters ----------------------------------------------------
    tmp_html = os.path.join(HERE, "_render.html")
    for name, w, h, out in (
        ("icon-squircle.svg", 1024, 1024, "master-squircle.png"),
        ("icon-circle.svg", 1024, 1024, "master-circle.png"),
    ):
        open(tmp_html, "w").write(html_for(name, w, h))
        chrome_shot(tmp_html, os.path.join(HERE, out), w, h, transparent=True)

    # 4. webp density buckets ---------------------------------------------
    from PIL import Image

    buckets = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
    masters = {
        "ic_launcher": Image.open(os.path.join(HERE, "master-squircle.png")).convert("RGBA"),
        "ic_launcher_round": Image.open(os.path.join(HERE, "master-circle.png")).convert("RGBA"),
    }
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

    # 5. showcase ----------------------------------------------------------
    cells = []
    for s in (192, 128, 96, 64, 48, 32, 24):
        cells.append(
            f'<div class="cell"><div class="masked" style="width:{s}px;height:{s}px">'
            f'<img src="icon-squircle.svg"></div><div class="num">{s} px</div></div>'
        )
    open(os.path.join(HERE, "showcase.html"), "w").write(
        SHOWCASE.replace("__SIZES__", "".join(cells))
    )
    chrome_shot(
        os.path.join(HERE, "showcase.html"),
        os.path.join(HERE, "preview.png"),
        1500, 762, scale=2,
        transparent=True,
    )
    os.remove(tmp_html)


if __name__ == "__main__":
    build()
    print("icons built")

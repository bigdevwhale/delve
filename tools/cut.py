"""Cuts the Codex sheets in art/sheets into app drawables (res/drawable-nodpi).

    python tools/cut.py

beast_<id>, item_<n> (index as in domain/Content.kt), room_<n> (map symbols as black ink with alpha),
hero_<class>_<m|f>, camp_<building>, banner_camp, banner_deep.
"""
from pathlib import Path
import sys

import numpy as np
from PIL import Image
from scipy import ndimage

sys.path.insert(0, str(Path(__file__).parent))
from gen_art import BEASTS, BEASTS2

ROOT = Path(__file__).resolve().parent.parent
SHEETS = ROOT / "art" / "sheets"
OUT = ROOT / "app" / "src" / "main" / "res" / "drawable-nodpi"
MAX = 360


def dist(arr):
    """How far each pixel is from pure magenta (0 = exactly #FF00FF)."""
    r, g, b = arr[..., 0].astype(float), arr[..., 1].astype(float), arr[..., 2].astype(float)
    return np.sqrt((255 - r) ** 2 + g ** 2 + (255 - b) ** 2)


def key(im, flood=True):
    arr = np.array(im.convert("RGBA"))
    d = dist(arr)
    near = d < 150          # could be background
    if flood:
        lab, _ = ndimage.label(near)
        edge = set(np.unique(np.concatenate([lab[0], lab[-1], lab[:, 0], lab[:, -1]]))) - {0}
        bg = np.isin(lab, list(edge))
        # background shut inside a shape (between an arm and a bag): pure magenta patches of some size
        lab2, n2 = ndimage.label(d < 70)
        if n2:
            sizes = ndimage.sum(np.ones_like(lab2), lab2, range(n2 + 1))
            holes = np.isin(lab2, [k for k in range(1, n2 + 1) if sizes[k] >= 25])
            bg = bg | ndimage.binary_dilation(holes, iterations=2) & near
    else:
        bg = d < 110
    alpha = arr[..., 3].astype(float)
    alpha[bg & (d < 95)] = 0
    fringe = bg & (d >= 95)
    # pull the pink out of the fringe and fade it
    k = np.clip((150 - d) / 55, 0, 1)
    for c in (0, 2):
        ch = arr[..., c].astype(float)
        arr[..., c] = np.where(fringe, ch - (ch - arr[..., 1]) * k, ch).astype(np.uint8)
    alpha = np.where(fringe, alpha * (1 - k), alpha)
    # pixels next to the cut-out that still look pink lose their tint too
    grown = ndimage.binary_dilation(alpha == 0, iterations=1) & (alpha > 0) & (d < 200)
    for c in (0, 2):
        ch = arr[..., c].astype(float)
        arr[..., c] = np.where(grown, np.minimum(ch, arr[..., 1] + 40), ch).astype(np.uint8)
    arr[..., 3] = alpha.astype(np.uint8)
    return Image.fromarray(arr)


def border_clean(cell):
    """Drops bits of the neighbouring cells: anything opaque that touches the cell's edge, except the biggest piece."""
    arr = np.array(cell)
    lab, n = ndimage.label(arr[:, :, 3] > 30)
    if n == 0:
        return cell
    edge = set(np.unique(np.concatenate([lab[0], lab[-1], lab[:, 0], lab[:, -1]]))) - {0}
    sizes = ndimage.sum(np.ones_like(lab), lab, range(n + 1))
    big = max(range(1, n + 1), key=lambda k: sizes[k])
    for k in edge:
        if k != big:
            arr[lab == k, 3] = 0
    # specks far smaller than the creature are noise
    for k in range(1, n + 1):
        if sizes[k] < sizes[big] * .004:
            arr[lab == k, 3] = 0
    return Image.fromarray(arr)


def frames(sheet, cols, rows):
    src = Image.open(sheet).convert("RGBA")
    w, h = src.size
    cw, ch = w / cols, h / rows
    out = []
    for j in range(rows):
        for i in range(cols):
            cell = src.crop((int(i * cw), int(j * ch), int((i + 1) * cw), int((j + 1) * ch)))
            cell = border_clean(key(cell))
            box = cell.getchannel("A").point(lambda v: 255 if v > 30 else 0).getbbox()
            cell = cell.crop(box) if box else cell
            s = MAX / max(cell.size)
            if s < 1:
                cell = cell.resize((max(1, round(cell.width * s)), max(1, round(cell.height * s))), Image.LANCZOS)
            out.append(cell)
    return out


def save(im, name, q=88):
    im.save(OUT / f"{name}.webp", "WEBP", quality=q, method=6)


def ink(sheet, cols, rows):
    """Map symbols: black ink on magenta becomes black with the ink's darkness as alpha."""
    src = np.array(Image.open(sheet).convert("RGB")).astype(float)
    a = np.clip((1 - src[..., 0] / 255) * 1.25, 0, 1)
    rgba = np.zeros(src.shape[:2] + (4,), np.uint8)
    rgba[..., 3] = (a * 255).astype(np.uint8)
    im = Image.fromarray(rgba)
    w, h = im.size
    cw, ch = w / cols, h / rows
    out = []
    for j in range(rows):
        for i in range(cols):
            cell = im.crop((int(i * cw) + 4, int(j * ch) + 4, int((i + 1) * cw) - 4, int((j + 1) * ch) - 4))
            box = cell.getchannel("A").point(lambda v: 255 if v > 40 else 0).getbbox()
            cell = cell.crop(box) if box else cell
            s = 160 / max(cell.size)
            cell = cell.resize((max(1, round(cell.width * s)), max(1, round(cell.height * s))), Image.LANCZOS)
            out.append(cell)
    return out


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    ids = [b[0] for b in BEASTS]
    ids2 = [b[0] for b in BEASTS2]
    for sheet, cols, rows, names in (("beasts1", 4, 4, ids[:16]), ("beasts2", 4, 4, ids[16:32]), ("beasts3", 4, 2, ids[32:40]),
                                     ("beasts4", 4, 4, ids2[:16]), ("beasts5", 4, 4, ids2[16:32]), ("beasts6", 4, 2, ids2[32:40])):
        if not (SHEETS / f"{sheet}.png").exists():
            continue
        for n, fr in zip(names, frames(SHEETS / f"{sheet}.png", cols, rows)):
            save(fr, f"beast_{n}")
        print(sheet)
    # single redraws of creatures replace their sheet cells
    for f in sorted(SHEETS.glob("beast_*.png")):
        fr = frames(f, 1, 1)[0]
        save(fr, f.stem)
        print("redraw", f.stem)
    k = 0
    for sheet, count in (("items1", 16), ("items2", 16), ("items3", 14), ("items4", 16)):
        for fr in frames(SHEETS / f"{sheet}.png", 4, 4)[:count]:
            save(fr.resize((max(1, fr.width * 200 // max(fr.size)), max(1, fr.height * 200 // max(fr.size))), Image.LANCZOS), f"item_{k}")
            k += 1
        print(sheet)
    for n, fr in enumerate(ink(SHEETS / "rooms.png", 4, 4)):
        save(fr, f"room_{n}", 92)
    print("rooms")
    heroes = [f"hero_{c}_{g}" for g in "mf" for c in ("warrior", "ranger", "rogue", "mage")]
    for n, fr in zip(heroes, frames(SHEETS / "heroes.png", 4, 2)):
        save(fr, n)
    print("heroes")
    for n, fr in zip(["forge", "alchemist", "cartographer", "chandler", "chapel", "menagerie"], frames(SHEETS / "camp.png", 3, 2)):
        save(fr, f"camp_{n}")
    if (SHEETS / "camp_depth.png").exists():
        save(frames(SHEETS / "camp_depth.png", 1, 1)[0], "camp_depth")
    print("camp")
    if (SHEETS / "stats.png").exists():
        for n, fr in enumerate(frames(SHEETS / "stats.png", 4, 3)):
            save(fr.resize((max(1, fr.width * 160 // max(fr.size)), max(1, fr.height * 160 // max(fr.size))), Image.LANCZOS), f"stat_{n}")
        print("stats")
    ids = []
    for line in (ROOT / "app/src/main/java/app/delve/domain/Content.kt").read_text(encoding="utf-8").splitlines():
        for part in line.split('sk("')[1:]:
            ids.append(part.split('"')[0])
    k = 0
    for sheet, cols, rows in (("skills1", 4, 4), ("skills2", 4, 4), ("skills3", 4, 2), ("skills4", 4, 4), ("skills5", 4, 4), ("skills6", 4, 2)):
        if not (SHEETS / f"{sheet}.png").exists():
            continue
        for fr in frames(SHEETS / f"{sheet}.png", cols, rows):
            if k < len(ids):
                save(fr.resize((max(1, fr.width * 200 // max(fr.size)), max(1, fr.height * 200 // max(fr.size))), Image.LANCZOS), f"skill_{ids[k]}")
            k += 1
        print(sheet)
    for b in ("banner_camp", "banner_deep", "banner_descent", "banner_chronicle", "banner_bag", "banner_shrine"):
        if not (SHEETS / f"{b}.png").exists():
            continue
        g = Image.open(SHEETS / f"{b}.png").convert("RGB")
        g = g.resize((1200, round(1200 * g.height / g.width)), Image.LANCZOS)
        g.save(OUT / f"{b}.webp", "WEBP", quality=84, method=6)
    print("banners")


if __name__ == "__main__":
    main()

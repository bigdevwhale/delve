"""Draws app/build/graph.json (written by GraphTest) to app/build/graph.png: roads grey, open corridors black, caved-in red."""
import json
from pathlib import Path
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent
g = json.loads((ROOT / "app/build/graph.json").read_text())
k = 1.2
im = Image.new("RGB", (int(g["w"] * k), int(g["h"] * k)), "white")
d = ImageDraw.Draw(im)
for r in g["roads"]:
    d.line([(r[i] * k, r[i + 1] * k) for i in range(0, len(r), 2)], fill=(215, 215, 215), width=1)
for e in g["edges"]:
    p = e["p"]
    col = (0, 0, 0) if e["open"] else ((220, 60, 60) if e["in"] else (150, 170, 220))
    d.line([(p[i] * k, p[i + 1] * k) for i in range(0, len(p), 2)], fill=col, width=2)
cols = {"DOWN": (0, 160, 0), "UP": (0, 90, 255), "START": (255, 140, 0), "LAIR": (160, 0, 160), "CHEST": (200, 160, 0)}
for i, (x, y) in enumerate(g["nodes"]):
    c = cols.get(g["rooms"].get(str(i)), (90, 90, 90))
    r = 5 if g["rooms"].get(str(i)) in ("DOWN", "UP", "START") else 2
    d.ellipse([x * k - r, y * k - r, x * k + r, y * k + r], fill=c)
im.save(ROOT / "app/build/graph.png")

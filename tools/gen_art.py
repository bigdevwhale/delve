"""Generates Delve's art with the Codex CLI image tool.

Codex saves images into ~/.codex/generated_images/<session>/; the image of the session named in
the run's output is copied to art/sheets/<id>.png. Already present sheets are skipped.

    python tools/gen_art.py                 # everything missing
    python tools/gen_art.py beasts1 items1  # only these

The look: an old tabletop-adventure module. Ink line art with hatching and a light watercolour wash,
friendly and a little funny rather than scary. The map itself is drawn in code (ui/art/MapPainter.kt).
"""
import re
import shutil
import subprocess
import sys
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "art" / "sheets"
GEN = Path.home() / ".codex" / "generated_images"

INK = (
    "Style: hand-inked illustration like the pages of an old tabletop fantasy adventure module and its bestiary: "
    "confident black ink linework, fine cross-hatching for shadow, a light transparent watercolour wash in muted earthy colours "
    "(ochre, sepia, slate blue, moss green, rust red, bone white). Friendly, characterful and a little funny, never gory or scary; "
    "chunky clear silhouettes that read well small. Original designs, not from any existing game or book."
)
OBJ = INK + " These are plain inanimate objects: NO faces, NO eyes, nothing alive about them. No letters, numbers or text anywhere."
BG = "on a flat solid pure magenta (#FF00FF) background. Nothing touches the cell borders. No text, no letters, no grid lines, no frames, no shadows on the background."
HEAD = "Use your image generation tool to create ONE image. Do nothing else (no file copying).\n\n"


def sheet(cols, rows, what, things, style=INK, extra=""):
    lst = "\n".join(f"{i + 1}) {t}" for i, t in enumerate(things))
    return (HEAD + f"Image: a sprite sheet of {what} for a cozy fantasy walking game where the streets of your town are a dungeon underneath.\n"
            + style + "\n"
            + f"Layout: {cols} columns x {rows} rows of equal square cells {BG} One subject per cell, centered, full body, with clear margin.\n"
            + extra + f"Subjects in order (left to right, top to bottom):\n{lst}")


# The order is the order of Beast ids in domain/Content.kt (BEASTS).
BEASTS = [
    ("rat", "a fat cellar rat wearing a thimble as a helmet, holding a crust of bread"),
    ("slime", "a wobbly green slime with a few swallowed keys and buttons floating inside"),
    ("bat", "a little bat hanging upside down, wrapped in its wings, sleepy and grumpy"),
    ("goblin", "a goblin junk-dealer with big ears and a huge backpack stuffed with junk, exactly two arms"),
    ("spider", "a round hairy spider knitting a web with knitting needles"),
    ("kobold", "a small lizard-like kobold miner with a lantern on a pole and a too-big helmet, exactly two arms"),
    ("mimic", "a wooden treasure chest with teeth on the lid and a long tongue, standing on stubby legs"),
    ("snail", "a big cave snail with a shell made of stacked bricks, like a little tower"),
    ("skeleton", "a skeleton doorkeeper in a battered helmet leaning on a rusty halberd, bored, exactly two arms"),
    ("draught", "a draught ghost: a pale wispy ghost blowing out a candle, cheeky, swirling tail"),
    ("ghoul", "a small hunched ghoul in a ragged cloak gnawing a bone, more pitiful than scary, exactly two arms"),
    ("candlewraith", "a ghost made of dripping candle wax with a flame on its head, holding a candlestick"),
    ("bonedog", "a skeleton dog wagging its bony tail, carrying a bone in its mouth"),
    ("shroomling", "a walking mushroom with a broad spotted cap, tiny arms, carrying a basket of spores, exactly two arms"),
    ("mole", "a mole miner with tiny round spectacles and a pickaxe over its shoulder"),
    ("mossman", "a squat man-shaped heap of moss and ferns with little flowers and a beetle on its head"),
    ("firefly", "a big glowing firefly with a lantern-like belly, gentle and small"),
    ("toad", "an old hermit toad sitting on a mushroom, with a long beard of moss and a tiny pipe"),
    ("crab", "a cave crab with pale shell, one huge claw, carrying a pearl"),
    ("eel", "a long eel with a glowing lure dangling from its forehead, coiled like an S"),
    ("waterman", "a pond spirit: a plump bearded water-man with weed hair, sitting in a puddle, exactly two arms"),
    ("axolotl", "a blind pale cave axolotl with feathery gills, curious"),
    ("golem", "a stone golem built from street cobblestones and a manhole cover, exactly two arms"),
    ("troll", "a big lumpy cave troll with a cudgel made from a lamppost, exactly two arms"),
    ("basilisk", "a basilisk lizard wearing dark round sunglasses so it does not petrify anyone"),
    ("minotaur", "a lost minotaur holding an upside-down map, confused, exactly two arms"),
    ("gargoyle", "a stone gargoyle with small wings crouching on a broken drainpipe"),
    ("cube", "a translucent jelly cube with a boot, a sword and coins stuck inside"),
    ("apprentice", "a lich's apprentice: a young skeleton mage in a too-big robe, reading a spellbook upside down, exactly two arms"),
    ("deepgnome", "a grey deep gnome with a long beard and a glowing crystal on a staff, exactly two arms"),
    ("wyrmling", "a small cave dragon curled on a pile of copper coins, puffing a smoke ring"),
    ("shadow", "a living shadow of a man peeling off a wall, long thin arms, a little mischievous, exactly two arms"),
    ("crystalspider", "a spider made of glassy pale-blue crystal shards"),
    ("salamander", "a fire salamander lizard with small flames along its back"),
    ("hydra", "a small three-headed hydra, each head with its own mood (sleepy, angry, curious)"),
    ("ratking", "the rat king: a big rat in a crown made of a tin can lid, sitting on a throne of boots, holding a fork as a sceptre"),
    ("cryptkeeper", "the crypt keeper: a tall skeleton in a long dark robe with a ring of huge keys and a lantern, exactly two arms"),
    ("spiderqueen", "the spider queen: a huge elegant spider with a lace web veil and a tiny crown"),
    ("olddragon", "an old grey dragon asleep on a hoard, with reading glasses and a nightcap"),
    ("cityworm", "the worm under the city: a gigantic segmented worm bursting through brick sewer pipes and old tram rails"),
]

ITEMS = [  # order = Base ids in domain/Content.kt (BASES), then extras
    "a short dagger", "a short sword", "a spiked mace", "a hand axe", "a spear", "a wooden wizard staff with a crystal", "a leather sling with a stone", "a miner's pickaxe",
    "a leather jerkin", "a chain mail shirt", "a hooded travelling cloak", "an iron breastplate", "a round wooden shield with an iron boss", "an iron helmet",
    "a silver ring with a stone", "an amulet on a chain", "a fang charm on a cord", "a brooch shaped like a leaf", "a string of wooden prayer beads", "an old coin on a string",
    "a burning torch", "a brass lantern", "a candle stuck in a skull", "a glowing blue crystal",
    "a round red healing potion", "a rolled scroll with a red seal", "a rolled parchment map", "a small smoke bomb with a fuse",
    "a little sack of treats with a bone sticking out", "a lock pick on a ring",
    "a small pile of bones", "a cluster of cave mushrooms", "a pearl in an open shell", "an iron ingot", "a raw purple crystal", "a lump of candle wax",
    "a small heap of gold coins", "a small wooden treasure chest",
    "a key on a ribbon", "a pouch tied with string", "an hourglass", "a feather quill and an ink pot",
    "a small brass compass", "a coil of rope", "a bundle of torches", "a crown",
]

# Room symbols for the map, drawn like the symbols on a hand-drawn dungeon map (black ink only, no wash).
ROOMS = [
    "a spiral stair going down", "a ladder going up out of a round well", "a small stone altar with a candle", "a little spring bubbling from a stone basin",
    "a merchant's stall with a striped awning", "a stone sarcophagus", "a cluster of tall mushrooms", "an iron-bound vault door",
    "a deep shaft with a rope and pulley", "a rowing boat on water ripples", "a treasure chest", "a spiked pressure-plate trap",
    "a pair of glowing eyes in darkness", "a campfire with a bedroll", "a pile of rubble stones", "a crown on a cushion (boss lair)",
]
MAP_STYLE = ("Style: black ink map symbols like those hand-drawn on an old dungeon map, bold clean ink lines with a little hatching, "
             "NO colour at all (only black ink), simple and iconic, readable when tiny. No letters, numbers or text anywhere.")

HEROES = [
    "a young warrior with a round shield and a short sword, sturdy and cheerful, simple leather and mail, exactly two arms",
    "a ranger in a green hooded cloak with a bow and a lantern on the belt, alert, exactly two arms",
    "a rogue in a dark scarf with a lockpick and a small sack of loot, sly grin, exactly two arms",
    "a mage in a patched blue robe holding a staff with a small glowing crystal, a scroll tucked in the belt, exactly two arms",
    "a woman warrior with a round shield and a short sword, sturdy and cheerful, simple leather and mail, exactly two arms",
    "a woman ranger in a green hooded cloak with a bow and a lantern on the belt, alert, exactly two arms",
    "a woman rogue in a dark scarf with a lockpick and a small sack of loot, sly grin, exactly two arms",
    "a woman mage in a patched blue robe holding a staff with a small glowing crystal, a scroll tucked in the belt, exactly two arms",
]

CAMP = [
    "a blacksmith's forge: an anvil, a stone hearth with glowing coals, hammers on a rack",
    "an alchemist's table with flasks, a small copper still and drying herbs",
    "a cartographer's desk with rolled maps, a compass and an oil lamp",
    "a chandler's shelf with candles of many sizes and bundles of torches",
    "a small stone chapel shrine with a candle and a bell",
    "a cosy animal pen with straw, a water bowl and a little wooden house",
]


STATS = [
    "a red heart with a small bandage", "an archery target with an arrow in the bullseye", "a sword striking with a small impact burst", "a round wooden shield with an iron rim",
    "a brass lantern glowing", "a leather boot in mid-stride with motion lines", "a four-leaf clover", "an open treasure chest with gold",
    "a burning torch", "an iron bear trap", "a bowl of green healing herbs", "a brass balance scale with coins",
]

# order = skills in domain/Content.kt (CLASSES → tiers → first, second)
SKILLS = [
    "a patch of thick tough leather hide with stitches", "an armoured iron gauntlet clenched into a fist", "a big kite shield with a lion emblem (no letters)", "a curved war horn",
    "a pair of sturdy marching boots", "a roll of bandage around a red heart", "the head of a battering ram with curled horns", "a section of a thick stone wall",
    "a medal on a ribbon", "a heavy war hammer cracking a stone",
    "a brass spyglass", "a feather floating over a footprint", "an arrow piercing the centre of a target", "animal paw tracks beside a small wooden signpost (no letters)",
    "an owl feather and a crescent moon", "a long torch with a tall flame", "a bow with a nocked arrow", "a leaf swirling in a gust",
    "a swirl of wind with leaves", "a four-leaf clover with an acorn",
    "a leather glove holding a lock pick", "brass scales weighing coins", "a cat paw print", "a curved dagger half in shadow",
    "a monocle over a pile of gold coins", "a puff of smoke with a dark cloak vanishing into it", "a pair of dice", "a coin purse lifted by a little hook",
    "a dark hooded mask", "a small pouch spilling pepper dust",
    "a bright magic spark star", "a glowing orb of light floating", "a candle with a very long wick", "a bundle of healing herbs with a soft green glow",
    "a stone tablet carved with a rune", "a horseshoe with sparkles", "an old open book with an eye drawn on the page", "a fireball",
    "an eternal flame in an iron brazier", "a pointed wizard hat with a star",
]


def banner(desc):
    return (HEAD + "Image: a wide banner illustration for a cozy fantasy walking game, " + desc + "\n" + INK
            + " Wide panorama, aspect ratio 3:1. No people, no creatures, no letters, numbers or text anywhere.")


JOBS = {
    "beasts1": sheet(4, 4, "16 dungeon creatures", [d for _, d in BEASTS[:16]]),
    "beasts2": sheet(4, 4, "16 dungeon creatures", [d for _, d in BEASTS[16:32]]),
    "beasts3": sheet(4, 2, "8 dungeon creatures", [d for _, d in BEASTS[32:40]]),
    "items1": sheet(4, 4, "16 adventurer's items", ITEMS[:16], OBJ),
    "items2": sheet(4, 4, "16 adventurer's items", ITEMS[16:32], OBJ),
    "items3": sheet(4, 4, "14 adventurer's items", ITEMS[32:46], OBJ, "The last two cells stay empty magenta.\n"),
    "rooms": sheet(4, 4, "16 map symbols", ROOMS, MAP_STYLE),
    "heroes": sheet(4, 2, "8 adventurer portraits, from the knees up, facing three-quarters right", HEROES),
    "camp": sheet(3, 2, "6 places of an adventurers' camp, each a small separate vignette", CAMP, OBJ),
    "banner_camp": banner("an adventurers' camp in an old courtyard of a town at dusk: a tent, a campfire, a forge, a cart, laundry lines, and in the middle a heavy iron hatch in the cobblestones leading down."),
    "stats": sheet(4, 3, "12 game icons for hero statistics", STATS, OBJ),
    "skills1": sheet(4, 4, "16 skill emblems", SKILLS[:16], OBJ, "Each emblem is a single bold symbolic object, no frame, no circle behind it. "),
    "skills2": sheet(4, 4, "16 skill emblems", SKILLS[16:32], OBJ, "Each emblem is a single bold symbolic object, no frame, no circle behind it. "),
    "skills3": sheet(4, 2, "8 skill emblems", SKILLS[32:40], OBJ, "Each emblem is a single bold symbolic object, no frame, no circle behind it. "),
    "banner_descent": HEAD + "Image: an illustration for a cozy fantasy walking game: at night in an old town courtyard, a heavy round iron hatch lies open in the cobblestones; worn stone steps spiral down into darkness; warm orange torchlight glows up from below; a lantern stands on the cobbles beside the hatch; thin mist; a lamppost and house walls faintly in the background. " + INK + " Landscape, aspect ratio 2:1. No people, no creatures, no letters, numbers or text anywhere.",
    "banner_deep": banner("a cross-section under a town street: houses and a lamppost on top, then layers of earth, old sewer brick tunnels, a crypt, a mushroom cave and an underground river going deeper and deeper."),
}


def image_of(output):
    m = re.search(r"session id:\s*([0-9a-f-]+)", output)
    if not m:
        return None
    pngs = sorted((GEN / m.group(1)).glob("*.png"), key=lambda p: p.stat().st_mtime)
    return pngs[-1] if pngs else None


def run(job):
    name, prompt = job
    dst = OUT / f"{name}.png"
    if dst.exists():
        print("skip", name, flush=True)
        return
    r = None
    for _ in range(2):
        r = subprocess.run([shutil.which("codex.cmd") or "codex", "exec", "--skip-git-repo-check", "--sandbox", "read-only", "-"],
                           input=prompt, text=True, encoding="utf-8", errors="replace", capture_output=True, cwd=str(ROOT))
        p = image_of((r.stdout or "") + (r.stderr or ""))
        if p is not None:
            shutil.copy(p, dst)
            print("ok", name, flush=True)
            return
    print("FAIL", name, (r.stdout or "")[-300:], (r.stderr or "")[-300:], flush=True)


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    want = sys.argv[1:]
    jobs = [j for j in JOBS.items() if not want or j[0] in want]
    with ThreadPoolExecutor(4) as ex:
        list(ex.map(run, jobs))


if __name__ == "__main__":
    main()

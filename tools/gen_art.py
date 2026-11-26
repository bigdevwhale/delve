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


ITEMS2 = [  # order = the bases added after "crystal" in domain/Content.kt (EXTRA_BASES), art 46..61
    "a slender rapier with a swept hilt", "a heavy two-handed war hammer", "a small wooden crossbow", "a spiked flail on a chain",
    "a short magic wand with a glowing tip", "a shirt of overlapping scale armour", "a padded quilted gambeson jacket", "a tall wooden tower shield with iron bands",
    "an iron helmet with two curved horns", "a pair of leather and iron gauntlets", "a silver bracelet with small gems", "a smooth stone talisman carved with a glowing rune",
    "a brass pocket watch on a chain", "a feather charm on a leather cord", "a glass jar full of glowing fireflies", "a miner's leather helmet with an oil lamp on the front",
]
SKILLS2 = [  # order = skills in EXTRA_TIERS (domain/Content.kt): warrior, ranger, rogue, mage; first then second per tier
    "an iron heart on an anvil", "two crossed battle axes", "a stone castle tower", "a burning red war banner",
    "a pair of marching boots on a winding road", "a field medic satchel with a red cross-shaped flower", "a heavy iron chest overflowing with gold", "a granite boulder that cannot be cracked",
    "a golden laurel wreath around a sword", "a champion's golden belt buckle",
    "a soaring falcon", "an animal trail of paw prints into a forest", "two arrows flying side by side", "a cloak fading into leaves",
    "a constellation of stars in a night sky", "a spare candle wick coiled with a match", "an old treasure map with an X", "a kite flying in the wind",
    "an oak tree with a shield-shaped crown", "an arrow splitting another arrow in a target",
    "a ring of many keys", "a felt slipper sneaking", "a dagger dripping green poison", "a ghostly cloak floating",
    "a pair of golden hands", "a hooded merchant's scale in shadow", "a black cat with a lucky coin", "a mask and a vanishing puff of smoke",
    "a thieves' crown made of lock picks", "a dagger striking from a shadow",
    "a crescent moon over a lantern", "a glowing flask of life potion", "a lightning bolt", "a shimmering magic shield of runes",
    "an hourglass with glowing sand", "a crystal ball with stars inside", "a crackling thunder orb", "an everburning oil lamp",
    "an old sage's book with a candle", "a phoenix flame in a brazier",
]


# order = BEASTS2 in domain/Content.kt
BEASTS2 = [
    ("mousebard", "a tiny mouse bard with a lute made from a walnut shell"),
    ("sockgoblin", "a goblin wearing odd socks on its ears, carrying a bundle of lost socks"),
    ("lampmoth", "a big fluffy moth with lantern-like glowing wing spots"),
    ("puddlefrog", "a frog in a little paper boat hat sitting in a puddle"),
    ("pigeonknight", "a city pigeon wearing a bottle-cap helmet and holding a toothpick lance"),
    ("drainimp", "a small imp climbing out of a drain grate, soot-covered, grinning"),
    ("tincan", "a living tin can creature on spring legs with a pull-tab mouth"),
    ("cobweb", "a ball of grey cobwebs with two bright eyes and dust bunnies around it"),
    ("bellghost", "a ghost wearing a small church bell as a hood, ringing softly"),
    ("gravedigger", "a skeleton gravedigger with a shovel and a straw hat, exactly two arms"),
    ("mourner", "a weeping wraith in a long veil holding a wilted flower bouquet"),
    ("ossuary", "a pile of skulls stacked into a little tower with a candle on top"),
    ("crowkeeper", "a crow in a black cloak holding a small iron key in its beak"),
    ("lichling", "a tiny lich child in a too-big crown playing with a glowing marble"),
    ("sporecap", "a tall slender mushroom with a lantern cap glowing purple"),
    ("beetle", "a large shiny rhinoceros beetle with moss on its back"),
    ("rootling", "a little creature made of tangled tree roots with a sprout on its head"),
    ("glowworm", "a long glowworm curled like a necklace of light"),
    ("fungalbear", "a small bear covered in shelf mushrooms, sleepy"),
    ("pixie", "a cave pixie with dragonfly wings holding a dewdrop lantern, exactly two arms"),
    ("pikeeel", "a toothy pike fish with whiskers wearing a tiny captain's cap"),
    ("selkie", "a seal pup with a glowing pearl collar"),
    ("ferryman", "a hooded ferryman in a small boat with a pole, a lantern on the bow, exactly two arms"),
    ("jellyfish", "a pale glowing jellyfish floating in dark water"),
    ("snapturtle", "an old snapping turtle with a mossy shell and a grumpy face"),
    ("kraken", "a small kraken peeking out of a manhole with three tentacles"),
    ("ironmole", "a mole wearing an iron diving helmet and riveted claws"),
    ("lavaslug", "a slug made of glowing orange lava with a crusty rock shell"),
    ("mirror", "a living ornate mirror on legs reflecting a grinning face"),
    ("clockwork", "a clockwork knight with a big wind-up key in its back, exactly two arms"),
    ("bonedragon", "a small skeleton dragon with ragged wings"),
    ("echo", "a translucent blue echo spirit shaped like several overlapping faces"),
    ("geode", "a crystal geode turtle with amethyst spikes on its shell"),
    ("stormimp", "an imp crackling with blue lightning sitting on a thundercloud"),
    ("gloomowl", "a large owl with glowing violet eyes and feathers like smoke"),
    ("rustgolem", "a golem made of rusty pipes and gears with steam vents, exactly two arms"),
    ("abyssfish", "a deep-sea anglerfish swimming in air with a glowing lure"),
    ("tramghost", "the ghost of an old tram rattling along floating rails"),
    ("queenmoth", "a huge regal moth queen with patterned wings and a tiny crown"),
    ("heartstone", "the heart of the city: a giant glowing crystal heart wrapped in roots and old pipes"),
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
    # single redraws of one creature (cut.py prefers them over the sheet cell)
    "beast_spider": HEAD + "Image: one creature for a cozy fantasy walking game: a round hairy brown spider sitting and knitting a small web scarf with two long straight knitting needles held in two front legs; the needles are perfectly straight, smooth, parallel-ish wooden rods with round knobs at the ends, crossing neatly; a ball of grey yarn beside it. " + INK + " Centered, full body, on a flat solid pure magenta (#FF00FF) background, nothing touching the edges. No text, no frame.",
    "items4": sheet(4, 4, "16 adventurer's items", ITEMS2, OBJ),
    "skills4": sheet(4, 4, "16 skill emblems", SKILLS2[:16], OBJ, "Each emblem is a single bold symbolic object, no frame, no circle behind it. "),
    "skills5": sheet(4, 4, "16 skill emblems", SKILLS2[16:32], OBJ, "Each emblem is a single bold symbolic object, no frame, no circle behind it. "),
    "skills6": sheet(4, 2, "8 skill emblems", SKILLS2[32:40], OBJ, "Each emblem is a single bold symbolic object, no frame, no circle behind it. "),
    "camp_depth": HEAD + "Image: one vignette for a cozy fantasy walking game: an underground adventurers' resting camp deep in a cave: a small campfire, a bedroll, a rope ladder going up into darkness, a wooden sign post with no letters, glowing mushrooms. " + INK + " Centered, on a flat solid pure magenta (#FF00FF) background, nothing touching the edges. No people, no creatures, no text.",
    "beasts4": sheet(4, 4, "16 dungeon creatures", [d for _, d in BEASTS2[:16]]),
    "beasts5": sheet(4, 4, "16 dungeon creatures", [d for _, d in BEASTS2[16:32]]),
    "beasts6": sheet(4, 2, "8 dungeon creatures", [d for _, d in BEASTS2[32:40]]),
    "banner_chronicle": banner("an adventurers' chronicle room: a big wooden desk with an open journal full of sketched dungeon maps, a quill and ink, candles, rolled maps, a shelf of trophies from below: a skull, a crystal, a pearl, a rusty key, warm candlelight."),
    "banner_bag": banner("an adventurer's worn leather satchel lying open on a stone dungeon floor, loot spilling out: gold coins, a red potion, a rolled scroll, a glowing crystal, mushrooms, a pearl, a key; a burning torch leaning beside it, warm light."),
    "banner_shrine": banner("a small underground shrine: a stone altar with many burning candles, a bell, wax drips, offerings of coins and flowers, soft golden glow in the dark."),
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

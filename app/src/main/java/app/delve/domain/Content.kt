package app.delve.domain

/** Everything that adds up into the hero's numbers: class, level, skills, gear, camp, companion, blessings. */
data class Mods(
    val hp: Int = 0,
    /** To hit, added to a d20. */
    val atk: Int = 0,
    /** Armour: what a monster's d20 must reach. */
    val def: Int = 0,
    /** Damage per hit. */
    val dmg: Int = 0,
    /** Lantern radius, metres. */
    val light: Int = 0,
    /** A chase is this many per cent shorter. */
    val chase: Int = 0,
    /** Extra torch minutes. */
    val torch: Int = 0,
    val luck: Int = 0,
    /** Per cent chance to step round a trap. */
    val traps: Int = 0,
    /** Per cent more loot. */
    val loot: Int = 0,
    /** Per cent more healing. */
    val heal: Int = 0,
    /** Per cent better prices at the merchant. */
    val sell: Int = 0,
) {
    operator fun plus(o: Mods) = Mods(hp + o.hp, atk + o.atk, def + o.def, dmg + o.dmg, light + o.light, chase + o.chase, torch + o.torch,
        luck + o.luck, traps + o.traps, loot + o.loot, heal + o.heal, sell + o.sell)

    fun times(k: Double) = Mods((hp * k).toInt(), (atk * k).toInt(), (def * k).toInt(), (dmg * k).toInt(), (light * k).toInt(), (chase * k).toInt(),
        (torch * k).toInt(), (luck * k).toInt(), (traps * k).toInt(), (loot * k).toInt(), (heal * k).toInt(), (sell * k).toInt())

    /** The non-zero parts as short lines, e.g. "+2 к атаке". */
    fun lines(ru: Boolean): List<String> = buildList {
        fun s(v: Int) = if (v > 0) "+$v" else "$v"
        if (hp != 0) add(s(hp) + if (ru) " здоровья" else " health")
        if (atk != 0) add(s(atk) + if (ru) " к меткости" else " to hit")
        if (dmg != 0) add(s(dmg) + if (ru) " к урону" else " damage")
        if (def != 0) add(s(def) + if (ru) " к защите" else " armour")
        if (light != 0) add(s(light) + if (ru) " м света" else " m of light")
        if (chase != 0) add(s(chase) + if (ru) "% быстрее погоня" else "% shorter chases")
        if (torch != 0) add(s(torch) + if (ru) " мин факела" else " torch min")
        if (luck != 0) add(s(luck) + if (ru) " к удаче" else " luck")
        if (traps != 0) add(s(traps) + if (ru) "% обойти ловушку" else "% dodge traps")
        if (loot != 0) add(s(loot) + if (ru) "% добычи" else "% loot")
        if (heal != 0) add(s(heal) + if (ru) "% лечения" else "% healing")
        if (sell != 0) add(s(sell) + if (ru) "% к ценам" else "% better prices")
    }
}

class Skill(val id: String, val name: Tx, val mods: Mods)

class HeroClass(val id: String, val name: Tx, val about: Tx, val mods: Mods, val tiers: List<Pair<Skill, Skill>>)

/** Levels at which a pair of skills opens. */
val SKILL_LEVELS = listOf(2, 4, 6, 8, 10)

private fun sk(id: String, ru: String, en: String, m: Mods) = Skill(id, Tx(ru, en), m)

val CLASSES = listOf(
    HeroClass("warrior", Tx("Воин", "Warrior"), Tx("Крепок и бьёт сильно. Бою лицом к лицу рад больше, чем погоне.", "Sturdy and hits hard. Happier to fight face to face than to run."),
        Mods(hp = 12, atk = 2, def = 1), listOf(
            sk("w_hide", "Крепкая шкура", "Thick hide", Mods(hp = 15)) to sk("w_hand", "Тяжёлая рука", "Heavy hand", Mods(dmg = 2)),
            sk("w_shield", "Щитоносец", "Shield-bearer", Mods(def = 2)) to sk("w_cry", "Боевой клич", "War cry", Mods(atk = 2)),
            sk("w_tireless", "Неутомимый", "Tireless", Mods(chase = 15)) to sk("w_breath", "Второе дыхание", "Second wind", Mods(heal = 30)),
            sk("w_ram", "Таран", "Battering ram", Mods(atk = 3)) to sk("w_wall", "Стена", "Wall", Mods(def = 3)),
            sk("w_veteran", "Ветеран", "Veteran", Mods(hp = 25)) to sk("w_crusher", "Сокрушитель", "Crusher", Mods(dmg = 4)),
        )),
    HeroClass("ranger", Tx("Следопыт", "Ranger"), Tx("Видит дальше всех и легко уходит от погони.", "Sees farthest and slips away from any chase."),
        Mods(light = 25, chase = 20, atk = 1), listOf(
            sk("r_eye", "Зоркий глаз", "Keen eye", Mods(light = 20)) to sk("r_step", "Лёгкий шаг", "Light step", Mods(chase = 15)),
            sk("r_hunter", "Охотник", "Hunter", Mods(atk = 2)) to sk("r_tracker", "Знаток троп", "Pathfinder", Mods(loot = 15)),
            sk("r_night", "Ночное зрение", "Night sight", Mods(light = 30)) to sk("r_wick", "Длинный факел", "Long torch", Mods(torch = 5)),
            sk("r_aim", "Меткость", "Sharpshooter", Mods(dmg = 3)) to sk("r_dodge", "Ловкач", "Dodger", Mods(def = 2)),
            sk("r_wind", "Ветер", "Wind", Mods(chase = 25)) to sk("r_luck", "Лесная удача", "Wood luck", Mods(luck = 3)),
        )),
    HeroClass("rogue", Tx("Вор", "Rogue"), Tx("Чует ловушки, вскрывает замки и находит больше добычи.", "Smells traps, opens locks and finds more loot."),
        Mods(traps = 35, loot = 20, luck = 2), listOf(
            sk("o_fingers", "Чуткие пальцы", "Light fingers", Mods(traps = 30)) to sk("o_fence", "Скупщик", "Fence", Mods(sell = 25)),
            sk("o_cat", "Кошачий шаг", "Cat's step", Mods(chase = 15)) to sk("o_back", "Удар в спину", "Backstab", Mods(dmg = 3)),
            sk("o_gold", "Глаз на золото", "Eye for gold", Mods(loot = 25)) to sk("o_slip", "Увёртливый", "Slippery", Mods(def = 2)),
            sk("o_lucky", "Везунчик", "Lucky", Mods(luck = 3)) to sk("o_pocket", "Карманник", "Pickpocket", Mods(loot = 20)),
            sk("o_shadow", "Тень", "Shadow", Mods(chase = 25)) to sk("o_trick", "Грязный приём", "Dirty trick", Mods(atk = 3)),
        )),
    HeroClass("mage", Tx("Маг", "Mage"), Tx("Факел горит дольше, раны заживают лучше, а искра жжёт больно.", "The torch lasts longer, wounds heal better, and the spark stings."),
        Mods(torch = 5, dmg = 2, heal = 20), listOf(
            sk("m_spark", "Искра", "Spark", Mods(dmg = 3)) to sk("m_palm", "Свет на ладони", "Light in the palm", Mods(light = 20)),
            sk("m_wick", "Сухой фитиль", "Dry wick", Mods(torch = 5)) to sk("m_heal", "Целитель", "Healer", Mods(heal = 30)),
            sk("m_stone", "Каменная кожа", "Stone skin", Mods(def = 2)) to sk("m_charm", "Чары удачи", "Luck charm", Mods(luck = 3)),
            sk("m_deep", "Знание глубин", "Lore of the deep", Mods(light = 30)) to sk("m_fire", "Огненный шар", "Fireball", Mods(dmg = 4)),
            sk("m_flame", "Вечный огонь", "Everflame", Mods(torch = 10)) to sk("m_arch", "Архимаг", "Archmage", Mods(atk = 3)),
        )),
)

fun heroClass(id: String) = CLASSES.firstOrNull { it.id == id } ?: CLASSES[0]

enum class Hab { ANY, CRYPT, GROTTO, RIVER, DEEP, BOSS }

/**
 * A creature of the dungeon. [tier] sets its numbers (see [Foe]); [min] is the shallowest floor it lives on.
 * Tamed creatures follow the hero and lend [pet]; [art] is its index in the bestiary sheets.
 */
class Beast(
    val id: String, val name: Tx, val hab: Hab, val min: Int, val tier: Int, val about: Tx,
    val pet: Mods? = null, val art: Int = 0,
)

private var beastArt = 0
private fun b(id: String, ru: String, en: String, hab: Hab, min: Int, tier: Int, aboutRu: String, aboutEn: String, pet: Mods? = null) =
    Beast(id, Tx(ru, en), hab, min, tier, Tx(aboutRu, aboutEn), pet, beastArt++)

/** In the order of the Codex sheets (tools/gen_art.py BEASTS). */
val BEASTS = listOf(
    b("rat", "Подвальная крыса", "Cellar rat", Hab.ANY, 1, 1, "Носит напёрсток как шлем и считает, что вся корка города принадлежит ей.", "Wears a thimble for a helmet and believes every crust in town is hers.", Mods(loot = 10)),
    b("slime", "Слизень", "Slime", Hab.ANY, 1, 1, "Внутри плавают чьи-то ключи и пуговицы. Вернуть их он не согласен.", "Somebody's keys and buttons float inside. He won't give them back.", Mods(hp = 8)),
    b("bat", "Нетопырь", "Bat", Hab.ANY, 1, 1, "Спит днём, ворчит ночью, а в промежутках кусается.", "Sleeps by day, grumbles by night and bites in between.", Mods(light = 15)),
    b("goblin", "Гоблин-старьёвщик", "Junk goblin", Hab.ANY, 2, 2, "Тащит на себе полгорода и всё ещё ищет, что бы прихватить.", "Carries half the town on his back and is still looking for more.", Mods(sell = 15)),
    b("spider", "Паук-вязальщик", "Knitting spider", Hab.ANY, 2, 2, "Вяжет паутину спицами. Петли ровные, а выход из неё один.", "Knits its web with needles. Neat stitches, one way out.", Mods(traps = 20)),
    b("kobold", "Кобольд с фонарём", "Lantern kobold", Hab.ANY, 1, 2, "Шахтёр в шлеме не по размеру. Фонарь ему дороже жизни.", "A miner in a helmet too big for him. Loves his lantern more than life.", Mods(light = 20)),
    b("mimic", "Мимик", "Mimic", Hab.ANY, 2, 3, "Притворяется сундуком. Плохо: у сундуков не бывает языка.", "Pretends to be a chest. Badly: chests don't have tongues.", Mods(loot = 20)),
    b("snail", "Кирпичная улитка", "Brick snail", Hab.ANY, 1, 1, "Её раковина сложена из кирпичей, как маленькая башня. Догонит не скоро.", "Her shell is bricks stacked into a tiny tower. She won't catch you soon.", Mods(def = 1)),
    b("skeleton", "Скелет-привратник", "Skeleton doorkeeper", Hab.CRYPT, 1, 2, "Стережёт дверь третью сотню лет и очень устал.", "Has kept the door for three hundred years and is very tired.", Mods(def = 2)),
    b("draught", "Сквозняк", "Draught", Hab.CRYPT, 1, 2, "Призрак, который задувает свечи. Только ради смеха.", "A ghost who blows out candles. Just for a laugh."),
    b("ghoul", "Упырёк", "Little ghoul", Hab.CRYPT, 3, 3, "Скорее жалок, чем страшен. Грызёт кость и косится на твой мешок.", "More pitiful than scary. Gnaws a bone and eyes your bag."),
    b("candlewraith", "Свечной призрак", "Candle wraith", Hab.CRYPT, 2, 3, "Сделан из воска и тоски. Если угостить, светит в дороге.", "Made of wax and longing. Feed it and it lights your way.", Mods(torch = 4)),
    b("bonedog", "Костяной пёс", "Bone dog", Hab.CRYPT, 2, 2, "Виляет хвостом, хоть хвост давно костяной. Приносит что угодно.", "Wags a tail that's long been bone. Fetches anything.", Mods(chase = 10)),
    b("shroomling", "Грибовик", "Shroomling", Hab.GROTTO, 1, 1, "Ходит с корзинкой спор и сеет их, где придётся.", "Walks about with a basket of spores and sows them anywhere.", Mods(heal = 20)),
    b("mole", "Крот-рудокоп", "Miner mole", Hab.GROTTO, 2, 2, "Очки, кирка и твёрдое мнение о любой породе.", "Spectacles, a pickaxe and firm views on every kind of rock.", Mods(loot = 15)),
    b("mossman", "Моховик", "Mossman", Hab.GROTTO, 3, 3, "Ходячая кочка. На голове живёт жук, и они дружат.", "A walking hummock. A beetle lives on his head; they're friends.", Mods(hp = 12)),
    b("firefly", "Светляк", "Firefly", Hab.GROTTO, 1, 1, "Тихий фонарик с крыльями. Обидеть его невозможно.", "A quiet lantern with wings. Impossible to stay cross with.", Mods(light = 25)),
    b("toad", "Жаба-отшельница", "Hermit toad", Hab.GROTTO, 2, 2, "Сидит на грибе, курит трубку и знает все ходы.", "Sits on a mushroom, smokes a pipe and knows every passage.", Mods(luck = 2)),
    b("crab", "Пещерный краб", "Cave crab", Hab.RIVER, 1, 2, "Одна клешня огромная, вторая для жемчуга.", "One claw huge, the other for pearls.", Mods(def = 2)),
    b("eel", "Угорь-фонарщик", "Lamplighter eel", Hab.RIVER, 2, 2, "Носит на лбу огонёк и заманивает к воде любопытных.", "Wears a little light on its brow to lure the curious to the water.", Mods(light = 15)),
    b("waterman", "Водяной", "Water-man", Hab.RIVER, 3, 3, "Толстый, бородатый, в тине. Обижается, если наступить в его лужу.", "Fat, bearded, weedy. Takes offence if you step in his puddle."),
    b("axolotl", "Слепой аксолотль", "Blind axolotl", Hab.RIVER, 1, 1, "Ничего не видит, но всех чувствует. Улыбается всегда.", "Sees nothing, senses everyone. Always smiling.", Mods(heal = 15)),
    b("golem", "Булыжный голем", "Cobble golem", Hab.DEEP, 4, 4, "Сложен из мостовой и крышки люка. Медленный, но тяжёлый.", "Built of cobbles and a manhole cover. Slow, but heavy."),
    b("troll", "Подземный тролль", "Under-troll", Hab.DEEP, 5, 5, "Вместо дубины у него фонарный столб. Фонарь ещё горит.", "His club is a lamppost. The lamp still works."),
    b("basilisk", "Василиск в очках", "Basilisk in shades", Hab.DEEP, 5, 4, "Носит тёмные очки из вежливости, чтобы никого не окаменить.", "Wears dark glasses out of politeness, so as not to petrify anyone.", Mods(atk = 2)),
    b("minotaur", "Заблудший минотавр", "Lost minotaur", Hab.DEEP, 6, 5, "Держит карту вверх ногами и уверен, что идёт правильно.", "Holds his map upside down and is sure he's going the right way.", Mods(hp = 20)),
    b("gargoyle", "Горгулья", "Gargoyle", Hab.DEEP, 4, 4, "Спустилась с водосточной трубы и обратно не собирается.", "Came down from a drainpipe and isn't going back.", Mods(def = 3)),
    b("cube", "Студенистый куб", "Jelly cube", Hab.DEEP, 5, 5, "Внутри сапог, меч и горсть монет. Предыдущие гости?", "A boot, a sword and a handful of coins inside. Previous guests?"),
    b("apprentice", "Ученик лича", "Lich's apprentice", Hab.DEEP, 6, 5, "Читает книгу заклинаний вверх ногами. Иногда получается.", "Reads his spellbook upside down. Sometimes it works.", Mods(torch = 5)),
    b("deepgnome", "Глубинный гном", "Deep gnome", Hab.DEEP, 4, 4, "Серый, бородатый, с кристаллом на посохе. Торгуется до хрипоты.", "Grey, bearded, a crystal on his staff. Haggles till he's hoarse.", Mods(sell = 20)),
    b("wyrmling", "Пещерный дракончик", "Cave wyrmling", Hab.DEEP, 8, 6, "Спит на медяках и пускает кольца дыма.", "Sleeps on coppers and blows smoke rings.", Mods(dmg = 3)),
    b("shadow", "Тень", "Shadow", Hab.DEEP, 7, 6, "Чья-то тень, которая отклеилась от стены и гуляет сама по себе.", "Somebody's shadow that peeled off a wall and goes about on its own.", Mods(chase = 15)),
    b("crystalspider", "Хрустальный паук", "Crystal spider", Hab.DEEP, 8, 6, "Звенит, когда бежит. Поэтому никогда не подкрадывается.", "Tinkles when it runs, so it can never sneak up."),
    b("salamander", "Саламандра", "Salamander", Hab.DEEP, 9, 7, "Греет коридор лучше камина. Хвостом не наступать.", "Warms a corridor better than a fireplace. Don't step on the tail.", Mods(torch = 6)),
    b("hydra", "Гидрёнок", "Little hydra", Hab.DEEP, 10, 7, "Три головы: сонная, злая и любопытная. Решают голосованием.", "Three heads: sleepy, cross and curious. They decide by vote."),
    b("ratking", "Крысиный король", "Rat king", Hab.BOSS, 5, 5, "Корона из крышки консервной банки, трон из сапог, скипетр – вилка.", "A crown from a tin-can lid, a throne of boots, a fork for a sceptre."),
    b("cryptkeeper", "Хранитель склепов", "Crypt keeper", Hab.BOSS, 10, 8, "Звенит связкой ключей от всех дверей под городом.", "Jingles the keys to every door under the town."),
    b("spiderqueen", "Паучья королева", "Spider queen", Hab.BOSS, 15, 10, "Кружевная вуаль из паутины и маленькая корона. Очень вежлива.", "A lace veil of web and a tiny crown. Very polite."),
    b("olddragon", "Старый дракон", "Old dragon", Hab.BOSS, 20, 12, "Спит в ночном колпаке и очках. Проснувшись, ворчит про молодёжь.", "Sleeps in a nightcap and glasses. Wakes up grumbling about the young."),
    b("cityworm", "Червь под городом", "The worm beneath", Hab.BOSS, 25, 14, "Тот, кто прокладывал коридоры до трамвайных путей. Огромен и древен.", "The one who dug the corridors before the tram rails. Huge and ancient."),
)

fun beast(id: String) = BEASTS.firstOrNull { it.id == id } ?: BEASTS[0]

/** The boss that guards the stairs of a floor divisible by five. */
fun bossFor(depth: Int): Beast {
    val bosses = BEASTS.filter { it.hab == Hab.BOSS }
    return bosses.lastOrNull { it.min <= depth } ?: bosses[0]
}

enum class Slot { WEAPON, ARMOR, CHARM, LIGHT }

/** A kind of gear: Russian grammatical gender for the adjectives ('m', 'f', 'n', 'p'), its numbers at tier 1, art index. */
class Base(val id: String, val slot: Slot, val name: Tx, val g: Char, val mods: Mods, val art: Int)

val BASES = listOf(
    Base("dagger", Slot.WEAPON, Tx("Кинжал", "Dagger"), 'm', Mods(atk = 2, dmg = 2), 0),
    Base("sword", Slot.WEAPON, Tx("Короткий меч", "Short sword"), 'm', Mods(atk = 1, dmg = 4), 1),
    Base("mace", Slot.WEAPON, Tx("Булава", "Mace"), 'f', Mods(dmg = 5), 2),
    Base("axe", Slot.WEAPON, Tx("Топорик", "Hand axe"), 'm', Mods(dmg = 5, atk = -1), 3),
    Base("spear", Slot.WEAPON, Tx("Копьё", "Spear"), 'n', Mods(atk = 2, dmg = 3), 4),
    Base("staff", Slot.WEAPON, Tx("Посох", "Staff"), 'm', Mods(dmg = 2, torch = 3), 5),
    Base("sling", Slot.WEAPON, Tx("Праща", "Sling"), 'f', Mods(atk = 3, dmg = 1, chase = 5), 6),
    Base("pickaxe", Slot.WEAPON, Tx("Кирка", "Pickaxe"), 'f', Mods(dmg = 3, loot = 10), 7),
    Base("jerkin", Slot.ARMOR, Tx("Кожанка", "Leather jerkin"), 'f', Mods(def = 1, chase = 5), 8),
    Base("mail", Slot.ARMOR, Tx("Кольчуга", "Chain mail"), 'f', Mods(def = 3), 9),
    Base("cloak", Slot.ARMOR, Tx("Плащ", "Cloak"), 'm', Mods(def = 1, chase = 10), 10),
    Base("plate", Slot.ARMOR, Tx("Нагрудник", "Breastplate"), 'm', Mods(def = 4, chase = -5), 11),
    Base("shield", Slot.ARMOR, Tx("Щит", "Shield"), 'm', Mods(def = 2, hp = 4), 12),
    Base("helmet", Slot.ARMOR, Tx("Шлем", "Helmet"), 'm', Mods(def = 1, hp = 6), 13),
    Base("ring", Slot.CHARM, Tx("Перстень", "Ring"), 'm', Mods(luck = 2), 14),
    Base("amulet", Slot.CHARM, Tx("Амулет", "Amulet"), 'm', Mods(hp = 8), 15),
    Base("fang", Slot.CHARM, Tx("Клык на шнурке", "Fang charm"), 'm', Mods(atk = 1, dmg = 1), 16),
    Base("brooch", Slot.CHARM, Tx("Брошь", "Brooch"), 'f', Mods(loot = 10), 17),
    Base("beads", Slot.CHARM, Tx("Чётки", "Prayer beads"), 'p', Mods(heal = 20), 18),
    Base("coin", Slot.CHARM, Tx("Монета на шнурке", "Coin on a string"), 'f', Mods(sell = 15), 19),
    Base("torch", Slot.LIGHT, Tx("Факел", "Torch"), 'm', Mods(light = 10, torch = 2), 20),
    Base("lantern", Slot.LIGHT, Tx("Фонарь", "Lantern"), 'm', Mods(light = 25), 21),
    Base("skull", Slot.LIGHT, Tx("Свеча в черепе", "Skull candle"), 'f', Mods(light = 15, torch = 3), 22),
    Base("crystal", Slot.LIGHT, Tx("Светящийся кристалл", "Glow crystal"), 'm', Mods(light = 35), 23),
)

fun base(id: String) = BASES.firstOrNull { it.id == id } ?: BASES[0]

/** An adjective an item can carry, in the four Russian forms (m, f, n, plural). */
class Affix(val id: String, val ru: List<String>, val en: String, val mods: Mods)

val AFFIXES = listOf(
    Affix("bone", listOf("Костяной", "Костяная", "Костяное", "Костяные"), "Bone", Mods(atk = 1)),
    Affix("moss", listOf("Грибной", "Грибная", "Грибное", "Грибные"), "Mossy", Mods(heal = 15)),
    Affix("pearl", listOf("Жемчужный", "Жемчужная", "Жемчужное", "Жемчужные"), "Pearl", Mods(luck = 2)),
    Affix("deep", listOf("Глубинный", "Глубинная", "Глубинное", "Глубинные"), "Deep", Mods(hp = 10)),
    Affix("swift", listOf("Быстрый", "Быстрая", "Быстрое", "Быстрые"), "Swift", Mods(chase = 10)),
    Affix("bright", listOf("Светлый", "Светлая", "Светлое", "Светлые"), "Bright", Mods(light = 10)),
    Affix("sturdy", listOf("Крепкий", "Крепкая", "Крепкое", "Крепкие"), "Sturdy", Mods(def = 1)),
    Affix("lucky", listOf("Счастливый", "Счастливая", "Счастливое", "Счастливые"), "Lucky", Mods(loot = 10)),
)

fun affix(id: String) = AFFIXES.firstOrNull { it.id == id }

enum class Rarity(val mult: Double, val title: Tx) {
    COMMON(1.0, Tx("обычное", "common")),
    FINE(1.3, Tx("добротное", "fine")),
    RARE(1.65, Tx("редкое", "rare")),
    LEGEND(2.1, Tx("легендарное", "legendary")),
}

/** Things that stack: potions and scrolls, then materials. Index = art index in the items sheets. */
class Stuff(val id: String, val name: Tx, val about: Tx, val art: Int, val price: Int, val material: Boolean = false)

val STUFF = listOf(
    Stuff("potion", Tx("Зелье лечения", "Healing potion"), Tx("Возвращает половину здоровья.", "Restores half your health."), 24, 18),
    Stuff("recall", Tx("Свиток возврата", "Recall scroll"), Tx("Мгновенно поднимает в лагерь со всей добычей.", "Takes you up to camp at once, with all your loot."), 25, 40),
    Stuff("mapscroll", Tx("Карта этажа", "Floor map"), Tx("Показывает все коридоры этажа и где лестница.", "Shows every corridor of the floor and where the stairs are."), 26, 30),
    Stuff("smoke", Tx("Дымовая шашка", "Smoke bomb"), Tx("Обрывает погоню: чудовище теряет след.", "Ends a chase: the creature loses your trail."), 27, 22),
    Stuff("treat", Tx("Лакомство", "Treat"), Tx("Угости существо, и оно, может быть, пойдёт с тобой.", "Offer it to a creature and it may come along with you."), 28, 15),
    Stuff("lockpick", Tx("Отмычка", "Lock pick"), Tx("Открывает запертую сокровищницу.", "Opens a locked vault."), 29, 20),
    Stuff("bone", Tx("Кости", "Bones"), Tx("Для лакомств и часовни. Много в склепах.", "For treats and the chapel. Plenty in crypts."), 30, 3, true),
    Stuff("mushroom", Tx("Грибы", "Mushrooms"), Tx("Для зелий. Растут в гротах под парками.", "For potions. They grow in grottoes under parks."), 31, 3, true),
    Stuff("pearl", Tx("Жемчуг", "Pearls"), Tx("Ценится у картографа. Ищи у подземной реки.", "Prized by the cartographer. Look by the underground river."), 32, 8, true),
    Stuff("iron", Tx("Железо", "Iron"), Tx("Для кузницы. Выпадает из доспехов и решёток.", "For the forge. Drops from armour and grates."), 33, 4, true),
    Stuff("crystal", Tx("Кристаллы", "Crystals"), Tx("Глубинная порода. Нужна для лучших построек.", "Deep stone. Needed for the best buildings."), 34, 10, true),
    Stuff("wax", Tx("Воск", "Wax"), Tx("Для свечной лавки: факел горит дольше.", "For the chandler: the torch burns longer."), 35, 3, true),
)

fun stuff(id: String) = STUFF.firstOrNull { it.id == id }

/** Camp buildings: each level costs coins and a material and lends [per] times its level. */
class Building(val id: String, val name: Tx, val about: Tx, val material: String, val per: Mods, val art: Int, val max: Int = 5)

val BUILDINGS = listOf(
    Building("forge", Tx("Кузница", "Forge"), Tx("Закаляет снаряжение: каждая ступень – +1 к уровню заточки вещей.", "Tempers gear: each level allows one more upgrade on your things."), "iron", Mods(), 0),
    Building("alchemist", Tx("Алхимик", "Alchemist"), Tx("Варит зелья, лакомства и дымовые шашки. Лечение сильнее.", "Brews potions, treats and smoke bombs. Healing gets stronger."), "mushroom", Mods(heal = 10), 1),
    Building("cartographer", Tx("Картограф", "Cartographer"), Tx("Учит видеть дальше в темноте.", "Teaches you to see farther in the dark."), "pearl", Mods(light = 10), 2),
    Building("chandler", Tx("Свечная лавка", "Chandler"), Tx("Факел горит дольше.", "The torch burns longer."), "wax", Mods(torch = 3), 3),
    Building("chapel", Tx("Часовня", "Chapel"), Tx("Благословение на дорогу: больше здоровья.", "A blessing for the road: more health."), "bone", Mods(hp = 6), 4),
    Building("menagerie", Tx("Зверинец", "Menagerie"), Tx("Приручённым тут уютно. Приручать легче, спутник сильнее.", "The tamed feel at home here. Taming is easier, companions are stronger."), "crystal", Mods(), 5),
)

fun building(id: String) = BUILDINGS.first { it.id == id }

/** Coins and material for raising a building from [level] to the next. */
fun buildCost(level: Int): Pair<Int, Int> = (30 + 45 * level * level) to (3 + 3 * level)

/** What the alchemist can brew. */
class Recipe(val out: String, val needs: Map<String, Int>, val coins: Int, val minLevel: Int)

val RECIPES = listOf(
    Recipe("potion", mapOf("mushroom" to 3), 5, 0),
    Recipe("treat", mapOf("bone" to 2, "mushroom" to 1), 0, 0),
    Recipe("smoke", mapOf("wax" to 2, "crystal" to 1), 5, 1),
    Recipe("mapscroll", mapOf("pearl" to 2, "wax" to 1), 10, 2),
    Recipe("lockpick", mapOf("iron" to 2), 5, 1),
    Recipe("recall", mapOf("crystal" to 2, "pearl" to 2), 20, 3),
)

/** Rooms of the dungeon. */
enum class RoomKind(val title: Tx, val art: Int) {
    START(Tx("Спуск", "Way down"), 1),
    DOWN(Tx("Лестница вниз", "Stairs down"), 0),
    UP(Tx("Колодец наверх", "Well up"), 1),
    SHRINE(Tx("Алтарь", "Altar"), 2),
    SPRING(Tx("Источник", "Spring"), 3),
    MERCHANT(Tx("Торговец", "Merchant"), 4),
    CRYPT(Tx("Склеп", "Crypt"), 5),
    GROTTO(Tx("Грибной грот", "Mushroom grotto"), 6),
    VAULT(Tx("Сокровищница", "Vault"), 7),
    SHAFT(Tx("Шахта", "Shaft"), 8),
    RIVER(Tx("Подземная река", "Underground river"), 9),
    CHEST(Tx("Сундук", "Chest"), 10),
    TRAP(Tx("Ловушка", "Trap"), 11),
    LAIR(Tx("Логово", "Lair"), 12),
    REST(Tx("Стоянка", "Campsite"), 13),
    EMPTY(Tx("Пустой зал", "Empty hall"), 13),
    BOSS(Tx("Тронный зал", "Throne hall"), 15),
}

/** Short words a hero mutters on entering an empty hall. */
val EMPTY_LINES = listOf(
    Tx("Пыль, тишина и чья-то забытая перчатка.", "Dust, silence and somebody's forgotten glove."),
    Tx("На стене нацарапано: «Здесь был Вася». Давно.", "Scratched on the wall: \"Kilroy was here\". Long ago."),
    Tx("С потолка капает. Ровно в такт шагам.", "Water drips from the ceiling, in time with your steps."),
    Tx("Под ногами хрустят старые билеты на трамвай.", "Old tram tickets crunch underfoot."),
    Tx("Тут кто-то ночевал: зола ещё тёплая.", "Someone slept here: the ashes are still warm."),
    Tx("Сквозь трещину сверху слышно, как едет машина.", "Through a crack above you hear a car going by."),
    Tx("Эхо отвечает с небольшой задержкой. Вежливое.", "The echo answers after a polite pause."),
    Tx("На полу мелом нарисованы классики.", "Hopscotch squares drawn in chalk on the floor."),
    Tx("Здесь пахнет хлебом. Видимо, сверху пекарня.", "It smells of bread. There must be a bakery above."),
    Tx("Стены покрыты корнями: над тобой растёт дерево.", "Roots cover the walls: there's a tree above you."),
    Tx("Ржавая табличка: номер дома, которого нет.", "A rusty plate: the number of a house that isn't there."),
    Tx("В нише стоит банка с огурцами. Не трогай.", "A jar of pickles stands in a niche. Leave it."),
)

/** Shrine blessings for the rest of the expedition. */
val BLESSINGS = listOf(
    Tx("Благословение воина", "Warrior's blessing") to Mods(atk = 2, dmg = 2),
    Tx("Благословение щита", "Shield's blessing") to Mods(def = 2),
    Tx("Благословение странника", "Wanderer's blessing") to Mods(chase = 20),
    Tx("Благословение света", "Blessing of light") to Mods(light = 25),
    Tx("Благословение удачи", "Blessing of luck") to Mods(luck = 3, loot = 15),
)

// ——— traits: what makes each creature a different fight ———

enum class Trait(val title: Tx, val hint: Tx) {
    NONE(Tx("", ""), Tx("", "")),
    ARMOR(Tx("Панцирь", "Armoured"), Tx("Режущее оружие почти не берёт, дробящее – бьёт вдвойне.", "Blades barely bite; blunt weapons hit twice as hard.")),
    SOFT(Tx("Мягкотелый", "Soft"), Tx("Дробящее вязнет, режущее – рассекает.", "Blunt weapons sink in; blades cut deep.")),
    SWIFT(Tx("Юркий", "Quick"), Tx("По нему трудно попасть ударом. Хитрость работает лучше.", "Hard to hit with a blow. Tricks work better.")),
    BRUTE(Tx("Громила", "Brute"), Tx("Часто замахивается со всей силы – вовремя защищайся.", "Often swings with all its might: guard in time.")),
    SHY(Tx("Боится света", "Light-shy"), Tx("Если твой свет дальше 60 м, бьёт слабее.", "If your light reaches past 60 m, it hits softer.")),
    GHOST(Tx("Бесплотный", "Ghostly"), Tx("Удары проходят насквозь. Помогает только хитрость… или защита.", "Blows pass right through. Only tricks help… or guarding.")),
}

val TRAITS: Map<String, Trait> = mapOf(
    "rat" to Trait.SWIFT, "slime" to Trait.SOFT, "bat" to Trait.SHY, "goblin" to Trait.SWIFT, "spider" to Trait.SWIFT,
    "kobold" to Trait.NONE, "mimic" to Trait.ARMOR, "snail" to Trait.ARMOR, "skeleton" to Trait.ARMOR, "draught" to Trait.GHOST,
    "ghoul" to Trait.SHY, "candlewraith" to Trait.GHOST, "bonedog" to Trait.SWIFT, "shroomling" to Trait.SOFT, "mole" to Trait.NONE,
    "mossman" to Trait.SOFT, "firefly" to Trait.SWIFT, "toad" to Trait.SOFT, "crab" to Trait.ARMOR, "eel" to Trait.SWIFT,
    "waterman" to Trait.BRUTE, "axolotl" to Trait.SOFT, "golem" to Trait.ARMOR, "troll" to Trait.BRUTE, "basilisk" to Trait.NONE,
    "minotaur" to Trait.BRUTE, "gargoyle" to Trait.ARMOR, "cube" to Trait.SOFT, "apprentice" to Trait.GHOST, "deepgnome" to Trait.NONE,
    "wyrmling" to Trait.BRUTE, "shadow" to Trait.SHY, "crystalspider" to Trait.ARMOR, "salamander" to Trait.SWIFT, "hydra" to Trait.BRUTE,
    "ratking" to Trait.SWIFT, "cryptkeeper" to Trait.ARMOR, "spiderqueen" to Trait.SWIFT, "olddragon" to Trait.BRUTE, "cityworm" to Trait.BRUTE,
)

fun Beast.trait(): Trait = TRAITS[id] ?: Trait.NONE

/** Weapons that crush rather than cut. */
val BLUNT = setOf("mace", "staff", "sling", "pickaxe")

/** What the creature means to do this round: shown above it, so the hero can answer. */
enum class Intent(val title: Tx, val hint: Tx) {
    ATTACK(Tx("Атакует", "Attacks"), Tx("обычный удар", "a normal blow")),
    HEAVY(Tx("Замахивается!", "Winds up!"), Tx("двойной урон – лучше защититься", "double damage: better guard")),
    GUARD(Tx("Прикрылся", "Guards"), Tx("удар отскочит – попробуй хитрость", "a blow will bounce off: try a trick")),
}

enum class Move(val title: Tx, val hint: Tx) {
    STRIKE(Tx("Удар", "Strike"), Tx("урон оружием", "weapon damage")),
    GUARD(Tx("Защита", "Guard"), Tx("урон по тебе ×¼, ответный тычок", "damage to you ×¼, a counter-jab")),
    TRICK(Tx("Хитрость", "Trick"), Tx("оглушить и ударить в щель", "stun it and hit the gap")),
}

// ——— the guild's board: orders for an expedition ———

/**
 * An order: [kind] is what to count (depth, beaten, tamed, metres, room:KIND, stuff:id), [n] how much.
 * It pays only when the hero comes back up alive.
 */
class Contract(val id: Long, val kind: String, val n: Int, val coins: Int, val stuffId: String, val stuffN: Int, val xp: Int) {
    fun text(ru: Boolean): String = when {
        kind == "depth" -> if (ru) "Спустись на $n-й этаж" else "Reach floor $n"
        kind == "beaten" -> if (ru) "Одолей существ: $n" else "Beat $n creatures"
        kind == "tamed" -> if (ru) "Приручи кого-нибудь" else "Tame a creature"
        kind == "metres" -> if (ru) "Пройди под городом $n м" else "Walk $n m under the town"
        kind.startsWith("room:") -> runCatching { RoomKind.valueOf(kind.substring(5)) }.getOrNull()?.let { k ->
            if (ru) "Найди: ${k.title.ru.lowercase()}" + (if (n > 1) " ×$n" else "") else "Find: ${k.title.en.lowercase()}" + (if (n > 1) " ×$n" else "")
        } ?: kind
        kind.startsWith("stuff:") -> stuff(kind.substring(6))?.let { st -> if (ru) "Принеси: ${st.name.ru.lowercase()} ×$n" else "Bring ${st.name.en.lowercase()} ×$n" } ?: kind
        else -> kind
    }

    /** Where to go for it: a word that points the walk. */
    fun hint(ru: Boolean): String = when {
        kind == "stuff:mushroom" || kind == "room:GROTTO" -> if (ru) "грибы растут в гротах под парками" else "mushrooms grow in grottoes under parks"
        kind == "stuff:pearl" || kind == "room:RIVER" -> if (ru) "река течёт там, где наверху пруд, канал или набережная" else "a river runs where there is a pond, canal or embankment above"
        kind == "stuff:bone" || kind == "room:CRYPT" -> if (ru) "склепы – под церквями и кладбищами" else "crypts lie under churches and graveyards"
        kind == "room:MERCHANT" -> if (ru) "торговцы сидят под магазинами" else "merchants sit under shops"
        kind == "room:SPRING" -> if (ru) "источники бьют под фонтанами" else "springs well up under fountains"
        kind == "stuff:crystal" -> if (ru) "кристаллы – с третьего этажа" else "crystals: from floor three down"
        else -> ""
    }

    fun toJson(): org.json.JSONObject = org.json.JSONObject().put("i", id).put("k", kind).put("n", n).put("c", coins).put("s", stuffId).put("sn", stuffN).put("x", xp)

    companion object {
        fun fromJson(o: org.json.JSONObject) = Contract(o.getLong("i"), o.getString("k"), o.getInt("n"), o.getInt("c"), o.getString("s"), o.getInt("sn"), o.getInt("x"))
    }
}

/** Depth stake: what everything carried up is multiplied by. */
fun depthMult(maxDepth: Int): Double = (1 + .25 * (maxDepth - 1)).coerceAtMost(4.0)

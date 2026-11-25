# Tarnished Relics

Diablo-style loot for Minecraft Forge 1.20.1.

Loot chests sometimes hold an **unidentified relic**: a darkened silhouette of a weapon, piece of armor or tool, named
only by its kind ("Unidentified Sword"). You can't use it until a **villager of the right profession** identifies it. Then it turns
into the real item, with a tier, random affixes and a name like **"Champion's Iron Sword of Smiting"**, or for a
Unique, **"Sturdy Netherite Pickaxe of Ithireal"**. The rarest are **Legendaries**: named, with lore, enchantments above
their normal maximum and effects no ordinary relic gets.

- Four tiers (Magic, Rare, Unique, Legendary), 76 built-in affixes and 10 hand-made legendaries, including a set with
  a set bonus.
- Weapons, armor, tools and shields from **other mods become relics automatically**, by what they are.
- Everything is **datapack-driven**: item categories, tiers, affixes and their effects, names, material levels.
- An **in-game editor** (`/tarnishedrelics editor`) changes all of it without touching a file, for one world or a
  whole modpack.
- Crafted gear is never touched: only relics found in loot have affixes.

## How it plays

1. **Find a relic.** It's plain white, with a silhouette of its kind and *"Take it to a blacksmith to identify it."*
   Nothing on it tells you its tier. Fire and lava don't destroy it (like netherite). It can't be equipped, used,
   enchanted, repaired or renamed.
2. **Take it to the right smith:** a toolsmith for tools, an armorer for armor and shields, a weaponsmith for weapons,
   a fletcher for bows and crossbows; an axe goes to a toolsmith or a weaponsmith. The wrong villager tells you who to
   see. Higher tiers need a more experienced smith: one who isn't skilled enough shakes their head and says which
   level you need. That's the only hint of the tier you get.
3. **Pay.** The smith names a price in emeralds; use the relic on them again within 10 seconds to pay. The real item
   replaces the relic in your hand, and the smith earns trade XP (they can level up from it).
4. **Use it.** Affixes work while the item is in its own slot (a sword in the main hand, a helmet on your head). The
   tooltip shows the tier and every effect; attribute bonuses show in the normal stat lines. An effect that only works
   some of the time (a chance, at night, below 30% health...) says "(sometimes)". Hold **Shift** to see each rolled
   number's range and how good the roll is ("+3.4 Attack Damage (+2 to +5, 47% roll)").

## Tiers

| Tier | How often | Base items | Affixes | Smith | Emeralds |
|---|---|---|---|---|---|
| Magic | 70 | material level 1-2 (stone, iron...) | 1-2 | Novice | 2-4 |
| Rare | 25 | level 2-3 (iron, diamond) | 3-4 | Journeyman | 8-12 |
| Unique | 10 | level 3 and up, plus bows and crossbows | 2-3 unique + 1-2 normal, and a name ("of Ithireal") | Master | 20-30 |
| Legendary | 5 (+1 per point of Luck) | level 3 and up | see below | Master | 40-64 |

**Legendaries** come in two kinds. Most are **generated**: a base item with 2 legendary affixes, 1 unique and 1-2
normal ones, a generated name and lore ("Grimfang, the Last Light"), 2-3 enchantments that fit the item, put on 1-3
levels above their normal maximum (any that go on the item, even two that normally exclude each other, like Sharpness
and Smite), and a 35% chance of a drawback. The other 30% are **hand-made**: Dawnbreaker (with its set piece, Crest of
the Dawn), Widow's Kiss, Glass Heart (huge damage, half your max health), Gravewarden's Aegis, Skyhook, Deepdelver,
Tidebreaker, Windstride and The Hollow Crown (riches, but cursed with Binding). Dawnbreaker and its Crest are a **set**:
wearing both (the sword in hand, the crest on your head) adds more damage against undead and extra health.

## Built-in affixes

- **Normal (30)**, the name parts of Magic, Rare and Unique relics. Prefixes: Champion's (damage), Heavy (more
  damage, slower swings), Swift (attack speed), Keen (crit chance), Vicious (crit damage), Sharpshooter's (bow damage),
  Reinforced (armor), Hardened (armor toughness), Vital (health), Efficient (mining speed), Far-Reaching (block reach),
  Lucky (Luck), Sturdy and Brittle (durability). Suffixes: of Smiting and of the Bane (damage against undead /
  arthropods), of Embers (sets on fire), of Frost and of Venom (slowness, poison on hit), of the Leech (lifesteal), of
  Thorns, of Arrow / Blast / Fire Warding (less damage of that kind), of Feathers (less fall damage), of Fleetness
  (movement speed), of Night Sight and of the Miner (night vision, haste), of Plunder (looting), of Wisdom (XP).
- **Unique (14)**, no name part, stronger effect sets: Aegis, Berserker (more damage at low health), Deep Delver,
  Executioner, Glass Cannon, Hunter's Mark, Phoenix, Prospector (Luck and night vision on tools), Soulreaver, Tireless
  (mining speed and regeneration), Titan, Vampiric, Windwalker, Withering.
- **Legendary (27)**: big effects, many tied to a condition, for example Skyfall (bow damage against targets in the
  air), Deepcore (mining speed and night vision below y 0), Hellforged (in the Nether), Nightstalker (at night),
  Stormstride (speed and jump boost by day), Last Stand and Second Wind (when nearly dead), Featherfall, Rampart,
  Retribution, Reaper, Wrath, Titan and more.
- **Drawbacks (5)** a legendary can get: Blood Price, Brittle, Exposed, Glass, Leaden.

## Modded gear and other mods

**Categories** pick up items by what they are, so a mod's swords, bows, armor or shields become relics without being
listed: a sword is anything built on Minecraft's sword class (Spartan Weaponry's and Better Survival's blades are), a
helmet is any helmet armor piece, a shield is anything that can block, and so on. Only single, damageable items count.
Anything else that deals melee damage is a `weapon` ("Unidentified Weapon"), other projectile weapons are `ranged`.

Each item has a **material level**: its tool tier, its armor value, or a material word in its id ("iron",
"diamond"...). Tiers pick base items by level, so a modded iron katana is a Magic or Rare relic like an iron sword, and
a netherite one can be Unique. The category is picked first (by its weight), then an item in it, so a mod that adds
300 swords doesn't make nearly every relic a sword. To keep items out, use an exclusions file (whole mods with
`"somemod:*"`) or the editor's Mods tab.

**Thrown weapons.** A thrown relic (a trident, or a modded javelin or throwing knife) hits with its own effects, as if
it were still in your hand, plus your armor's; what's in your hands at that moment doesn't count. Arrows don't carry
the bow, so a bow's damage and crit bonuses count from your hand.

**Melee hits.** On-hit effects, lifesteal and the like work on melee hits: a hit dealt by the attacker with a damage
type in `#tarnishedrelics:melee`. That's vanilla's player and mob attacks and a bee's sting, plus, when they're
installed, Spartan Weaponry's armor-piercing hits, some combat mods' own melee types, and everything in Progressive
Mechanics Library's `#pml:is_melee_attack` tag. A combat mod that gives melee hits other damage types needs those
added to the tag.

**Optional integrations** (none are needed):

- **Quality Equipment Forked**: an identified relic rolls its quality like crafted gear, and the quality word starts
  its name.
- **Tooltip Overhaul**: the rating line under the name shows the relic's tier ("Unique", in the tier's color) instead
  of the item's vanilla rarity, and the tooltip body then leaves out its own tier line.
- **JEI**: the unidentified item is hidden (an empty one has nothing inside).
- **Just a Lot More Enchantments (JLME)**: generated legendaries can roll its enchantments, at their raised levels,
  but never the ones it keeps out of loot (they come from other recipes), and its item restrictions are kept.
  Lifesteal heals from the damage left after its protection enchantments, which apply after armor.
- Other mods can add their own rolls (sockets...) and a word in the name through the API (see For other mods).

## Name format

`[Quality word] [name prefix from other mods] [affix prefix] [base item] [affix suffix | of <Unique name>]`

The first rolled prefix and the first rolled suffix name the item; Uniques end with "of" + a generated name.
Legendaries keep their own name with nothing added. The whole name is in the tier's color. A name given in an anvil
(after identifying) replaces it.

## Config (`config/tarnishedrelics-common.toml`)

| Option | Default | Meaning |
|---|---|---|
| `[loot] enabled` | `true` | Relics can turn up in loot |
| `[loot] chance` | `0.03` | Chance that one relic is added each time loot of these kinds is generated |
| `[loot] lootTypes` | `["chest"]` | Kinds of loot that can get one: `chest`, `entity` (mob drops: rolled on every kill, so even a few percent adds up fast), `fishing`, `archaeology`, `gift`, `barter`. Any other word is refused (the default comes back) |
| `[loot] entityNeedsPlayerKill` | `true` | With `entity`: mob drops only count for kills by a player (no mob farms) |
| `[loot] excludeTables` | the spawn bonus chest | Loot tables that never get relics (`*` wildcards) |
| `[loot] luckRollBonus` | `0.25` | How much each point of the looter's Luck pushes a found relic's affix values toward their best end (Bad Luck: toward their worst). At `0.25`, Luck 1 makes the average roll about 56% instead of 50%, Luck 4 about 67%. `0` turns it off. See "Luck" below |
| `[identify] confirmSeconds` | `10` | Time to use the relic on the smith again to pay |
| `[display] showRarityColorUnidentified` | `false` | Unidentified relics show their tier's color as a hint. On a server, the server's value counts for every player |

The editor's Settings tab edits the same file.

### Luck

The looter's Luck (Luck potions, luck gear, Luck of the Sea when fishing; vanilla passes the killer's Luck for mob
drops and the opener's for chests) counts twice when a relic drops:

- **Rarity**: each rarity's `quality` adds that much weight per point of Luck (the built-in Legendary has 1).
- **Affix values**: every rolled value is pushed toward its good end by `luckRollBonus` per point. The good end
  depends on the effect: a higher number for most (more damage, a smaller penalty), a lower one for `damage_taken`.
  An affix's effects share one roll, so it goes the way most of them agree on, and an affix whose effects pull
  opposite ways is left alone. Drawbacks get milder the same way. Bad Luck does the reverse.

Relics made by commands have no looter, so Luck doesn't count for them.

## A relic for sure: the `tarnishedrelics:relic` loot entry

The config's chance is the same for every loot table. To make a loot table always drop a relic (a boss, a quest
reward chest), put a `tarnishedrelics:relic` entry in one of its pools. It makes a relic exactly like the ones found
in loot, usually one, or a legendary with the rest of its set:

```json
{ "rolls": 1, "entries": [ { "type": "tarnishedrelics:relic" } ] }
```

`rarities` limits it to some tiers, picked among them by their usual weights (and Luck), e.g. rare or better:

```json
{ "type": "tarnishedrelics:relic",
  "rarities": ["tarnishedrelics:rare", "tarnishedrelics:unique", "tarnishedrelics:legendary"] }
```

It takes the other entry fields too (`weight`, `quality`, `conditions`, `functions`), so it can be one choice among
others in a pool, or depend on a condition (one entry for strong mobs, another for the rest). A tier's own
`loot_tables` limit still applies. Tiers that don't exist are skipped with a warning in the log; when none of the
listed tiers can make anything, the entry drops nothing. It works whether or not `[loot]` is enabled.

## Commands (op level 2)

- `/tarnishedrelics give [rarity] [category] [count]`: random relics.
- `/tarnishedrelics legendary <id>`: a specific legendary (and the rest of its set).
- `/tarnishedrelics identify`: identifies the relic in your main hand.
- `/tarnishedrelics errors`: problems in the datapack files (see Errors).
- `/tarnishedrelics editor`: opens the in-game editor.

## In-game editor

`/tarnishedrelics editor` (op level 2) opens a screen for everything in the datapack format below, the mod's tags and
the config.

- **Tabs**: categories, rarities, affixes, legendaries, names, levels, exclusions, tags, settings, mods. The file tabs
  list every file (from the mod, KubeJS, datapacks...); pick one to edit it as a form. Hover a field's name for what it
  does. `+ field` adds optional fields; `Raw JSON` edits the text directly.
- **Pickers** (`...` buttons) list what's actually loaded: items with icons, tags, whole mods, mobs, villager
  professions, blocks, attributes, potion effects, damage types, biomes, dimensions, categories and rarities,
  filterable by mod. Entries for mods that aren't loaded are shown in yellow (kept, they just do nothing here); unknown
  ids in red.
- Each file shows what it does in practice ("Matches 359 items", "Can be 491 base items"), with a **Show** list of the
  items: each with its category, level, tiers and whether any recipe makes it, and a button to exclude it. **Exclude
  no-recipe items** excludes every relic item in the list that nothing crafts (often gear for a material from a mod
  that isn't installed).
- Rarities, categories and legendaries have **Give** and **Identified** buttons that roll one for you (from the saved
  data: save first to try your changes).
- The **Mods** tab lists every loaded mod with gear that can be relics, with one button to exclude or include it.
- **Tags**: the blacksmith professions, the `undead` and `arthropods` mob lists the affixes use, the list of melee
  damage types, and any new tag in the `tarnishedrelics` namespace (name it like `entity_types/dragons`). Tags from
  every pack add up, so the editor shows them merged; taking out something another pack adds saves the tag with
  `"replace": true`.
- Changes are a working copy until **Save & Reload** (Ctrl+S). The server checks them first: nothing is written if any
  changed file has an error. **Save to** picks where they go:
  - **This world**: the world's `datapacks/tarnishedrelics_editor` datapack, moved to the top of the pack list.
  - **Whole modpack**: `config/tarnishedrelics/datapack`, a datapack every world loads (old ones too). A modpack ships
    its changes just by including its config folder: no KubeJS or datapack-loader mod needed. Saving a file here
    removes this world's copy of it, so the world doesn't hide the change. Files can be put there by hand too.
- **Turn off** switches a file off (`"disabled": true`), also files from the mod or other packs. **Use original** drops
  the editor's copy of a file so the one underneath is used again. **Revert** throws away unsaved changes.

## Datapack format

Everything lives in `data/<namespace>/tarnished_relics/<folder>/`. Files from all datapacks are merged; a file with
the same id replaces the earlier one. `/reload` applies changes. Any file with `"disabled": true` is ignored, which
switches off a file another pack (or the mod) ships.

Item lists accept an item id (`"minecraft:iron_sword"`), an item tag (`"#minecraft:swords"`), every item of a mod
(`"somemod:*"`) or every item of a category (`"@tarnishedrelics:sword"`). Rarities also take `"*"`: every item that has
a category.

### `categories/<name>.json`: what kind of item it is

```json
{
  "auto": ["sword"],
  "items": ["#minecraft:swords", "somemod:cool_blade"],
  "exclude": ["minecraft:wooden_sword"],
  "slot": "mainhand",
  "silhouette": "sword",
  "priority": 0,
  "weight": 10,
  "smiths": ["#tarnishedrelics:weaponsmiths"]
}
```

| Field | Meaning |
|---|---|
| `auto` | Item kinds picked up from every mod: `sword`, `axe`, `pickaxe`, `shovel`, `hoe`, `bow`, `crossbow`, `trident`, `shield`, `helmet`, `chestplate`, `leggings`, `boots`, `melee` (anything with attack damage), `ranged` (any projectile weapon) |
| `items`, `exclude` | Items added to / kept out of the category by hand |
| `slot` | Where the item's affixes work: `mainhand`, `offhand`, `head`, `chest`, `legs`, `feet` |
| `silhouette` | The unidentified icon: `generic`, `sword`, `axe`, `pickaxe`, `shovel`, `hoe`, `bow`, `crossbow`, `trident`, `helmet`, `chestplate`, `leggings`, `boots`, `shield` |
| `priority` | If an item is in several categories, the highest priority wins |
| `weight` | How often a relic is this category, relative to the others (default 10) |
| `name` | Optional: the unidentified name, as a lang key or plain text |
| `smiths` | Optional: villager professions (ids or `#tags`) that identify its relics. Without it, any in `#tarnishedrelics:blacksmiths` |

The unidentified name is the lang key `item.<namespace>.unidentified.<name>` ("Unidentified Sword") unless `name` is
set; with no lang entry either, it's made from the file name (`war_hammer` -> "Unidentified War Hammer").

Built in: `sword`, `axe`, `pickaxe`, `shovel`, `hoe`, `bow`, `crossbow`, `trident`, `shield`, `helmet`, `chestplate`,
`leggings`, `boots`, plus `weapon` (`melee`) and `ranged` at priority -10, so they only get what nothing else took.

### `rarities/<name>.json`: the tiers

```json
{
  "weight": 25,
  "items": ["*", "minecraft:trident"],
  "levels": [2, 3],
  "rolls": { "normal": [3, 4] },
  "identify_level": 3,
  "cost": [8, 12],
  "villager_xp": 15,
  "color": "#FFFF55",
  "unique_name": false
}
```

| Field | Meaning |
|---|---|
| `weight` | How often this tier is picked, relative to the others |
| `items`, `exclude` | Which base items this tier can be (they must also be in a category). `"*"` = anything with a category |
| `levels` | `[min, max]` material levels for the `"*"` and `"@category"` entries (items named directly always count) |
| `name` | Optional: display name, as a lang key or plain text |
| `rolls` | How many affixes to roll from each pool, e.g. `{ "unique": [2, 3], "normal": [1, 2] }` |
| `identify_level` | Villager level needed: 1 Novice, 2 Apprentice, 3 Journeyman, 4 Expert, 5 Master |
| `cost` | Emeralds to identify (rolled once per relic) |
| `villager_xp` | Trade XP the blacksmith earns |
| `color` | Name color once identified, six hex digits (`"#FF5555"`) |
| `unique_name` | `true`: the name ends with "of" + a generated name (see `names/`) |
| `quality` | Extra weight per point of the looter's Luck (Luck potions, luck gear), like loot table quality |
| `loot_tables` | Only drops in these loot tables (`*` wildcards); empty = any |
| `legendary` | `true`: makes legendaries (below) |
| `handmade_chance` | Legendary: share that are hand-made (default 0.3) |
| `signature_enchantments`, `enchantment_bonus` | Legendary: how many enchantments that fit the item, and how many levels above their max (single-level ones stay at 1). Never a curse or one kept out of loot (Soul Speed, Swift Sneak) |
| `drawback_chance`, `drawback_pool` | Legendary: chance of one affix from that pool (default `drawback`) |
| `lore_lines` | Legendary: how many generated lore lines |

The tier's display name is the lang key `tarnishedrelics.rarity.<namespace>.<name>` unless `name` is set (with no lang
entry either, it's made from the file name). A generated legendary gets a generated name and lore from `names/` and
nothing else in its name.

### `affixes/<name>.json`: prefixes, suffixes and hidden effects

```json
{
  "kind": "prefix",
  "name": "affix.mypack.champions",
  "pool": "normal",
  "applies_to": ["tarnishedrelics:sword", "tarnishedrelics:axe"],
  "weight": { "magic": 10, "rare": 6 },
  "group": "flat_damage",
  "effects": [
    { "type": "attribute", "attribute": "minecraft:generic.attack_damage", "operation": "add",
      "value": { "magic": [1, 2], "rare": [2, 4], "unique": [3, 5] } }
  ]
}
```

| Field | Meaning |
|---|---|
| `kind` | `prefix`, `suffix`, or `hidden` (effects but no name part) |
| `name` | The name part ("Champion's", "of the Bear"): a lang key, or plain text |
| `pool` | The pool tiers roll it from (`rolls` in the rarity). Built-in pools: `normal`, `unique`, `legendary`, `drawback` |
| `applies_to` | Category ids, or `"*"` for any |
| `weight` | A number, or per tier (`{ "magic": 10, "rare": 6 }`; tiers not listed can't roll it) |
| `group` | Only one affix of a group per item (two flat-damage prefixes can't stack) |
| `effects` | What it does (below) |

**Values.** A `value` can be a number (`3`), a range (`[2, 5]`), or per tier (`{ "magic": [1, 2], "rare": [2, 4],
"default": 1 }`). A tier the list leaves out gets `default`, or 0 without one; an affix a tier rolls with no value for
it would do nothing there, so that's reported as an error. Negative values are drawbacks. An item stores *where in the
range* it rolled (0-1), not the final number, so changing the numbers in a datapack also changes items already found,
and a perfect roll stays perfect. Affixes removed from the datapack simply stop working on old items.

#### Effect types

| `type` | Fields | Does |
|---|---|---|
| `attribute` | `attribute`, `operation` (`add`, `multiply_base`, `multiply_total`), `value` | Any attribute from any mod, shown in the normal stat lines. Ignores conditions |
| `damage_vs` | `value`, `target` (optional entity id/#tag or list) | ±% damage dealt with your weapons: melee hits, shots and thrown relics (against matching targets, or everything). Not your thorns, explosions you set off or potions |
| `damage_taken` | `value`, `damage` (optional damage type id/#tag or list) | ±% damage taken (`-0.2` = 20% less): from the listed damage types, or without a list from anything but falling out of the world and `/kill` |
| `on_hit_effect` | `effect`, `duration` (ticks), `amplifier`, `apply_to` (`target`/`self`) | Potion effect on a melee hit (or a hit by the thrown relic) |
| `on_hit_fire` | `value` (seconds) | Sets the target on fire on a melee hit (or a hit by the thrown relic) |
| `lifesteal` | `value` | Heals that fraction of melee damage dealt (the final damage: after armor and every other mod's change to it), or the thrown relic's |
| `thorns` | `value` | Sends that fraction of damage taken back to the attacker (never answers thorns with thorns) |
| `on_hurt_effect` | `effect`, `duration`, `amplifier`, `apply_to` (`attacker`/`self`) | Potion effect when you're hit |
| `passive_effect` | `effect`, `amplifier` | Kept on a player while held/worn (checked once a second; a stronger potion isn't replaced) |
| `crit_chance` | `value` | Extra chance to crit: melee hits, and arrows, bolts and thrown relics (1.5x damage). A crit it rolls happens even if another mod refused the crit |
| `crit_damage` | `value` | Crits hit harder (`0.5` = +50%). On projectiles, only crits from `crit_chance` |
| `break_speed` | `value`, `blocks` (optional block id/#tag) | ±% mining speed |
| `durability` | `value` | Chance each point of wear is ignored (negative: sometimes wears twice) |
| `xp_bonus` | `value` | ±% XP from kills |
| `looting` | `value` | Extra Looting levels |
| `function` | `function`, `trigger` (`hit`, `kill`, `hurt`, `tick`), `as` (`wielder`/`target`) | Runs a datapack function: the catch-all for anything else. `tick` runs it once a second while held/worn |

Every type but `on_hit_effect`, `on_hurt_effect`, `passive_effect` and `function` needs a `value`. Every effect can
also have:

- `"conditions"`: all must pass. `chance` (0-1), `health_below` / `health_above` (fraction of the wielder's max health),
  `in_water`, `is_night`, `is_day` (only in a dimension with days: neither is true in the Nether or the End),
  `sneaking`, `on_fire`, `y_below`, `y_above`, `dimensions` (list), `biomes` (ids/#tags), `target` (entity ids/#tags),
  `target_in_air` (the target isn't standing on anything, swimming or riding: flying, jumping, falling or knocked up).
  `break_speed` ignores `chance` (the client works out the mining speed too, and a roll on each side would disagree).
- `"slot"`: works while the item is in that slot instead of its category's (`offhand` for a dagger that helps from the
  offhand).
- `"tooltip"`: its own tooltip line, a lang key or plain text (`%1$s` = value as a percent, `%2$s` = plain number).
- `"hidden": true`: no tooltip line.

Damage type and mob names in tooltips use the lang key `tarnishedrelics.target.<namespace>.<path>` if there is one,
otherwise the id's path.

### `legendaries/<name>.json`: hand-made legendaries

A hand-made legendary always has the same name, lore, enchantments and effects; only the numbers vary (each copy gets
one roll for all its value ranges).

```json
{
  "name": "Widow's Kiss",
  "lore": ["One kiss is all it takes.", "Held close, it keeps its bearer alive."],
  "items": ["minecraft:iron_sword", "minecraft:diamond_sword"],
  "category": "tarnishedrelics:sword",
  "enchantments": { "minecraft:sharpness": 6, "minecraft:unbreaking": 4 },
  "effects": [
    { "type": "on_hit_effect", "effect": "minecraft:poison", "duration": 100, "amplifier": 2 },
    { "type": "attribute", "attribute": "minecraft:generic.max_health", "operation": "multiply_total",
      "value": [0.15, 0.25], "slot": "offhand" }
  ],
  "weight": 10,
  "companions": []
}
```

| Field | Meaning |
|---|---|
| `name`, `lore` | Its name and the lines under it (lang keys or plain text). Nothing is added to the name |
| `items`, `exclude` | Base items, one is picked (ids, #tags, `mod:*`) |
| `category` | Optional: the category (slot, silhouette) if the base item has none |
| `enchantments` | Put on as they are: above the normal max, any combination |
| `effects` | Like an affix's effects |
| `weight` | How often it's picked among the hand-made legendaries; `0` = only drops with its set |
| `companions` | Other legendaries that drop together with it, as a set |
| `set_effects` | Optional, needs `companions`: a set bonus. Effects (like `effects`) that work while the whole set, this legendary and its companions, is on, each piece in its own slot. Values use this legendary's roll. Attributes in a set bonus go on the player once a second while the set is complete |

### `names/<any>.json`: words for generated names

```json
{
  "names": ["Ithireal", "Grimfang"],
  "starts": ["Ith", "Mor", "Vael"],
  "ends": ["ireal", "goth", "andor"],
  "titles": ["the Last Light", "Bane of Kings"],
  "lore": ["{name} was never meant to be found.", "Kings have bled for less."]
}
```

All files are merged. A Unique gets a whole name or a start + end, about half the time each. Generated legendaries
use the same names, half the time with ", <title>", and lore lines (`{name}` is replaced by the name).

### `levels/<any>.json`: material levels

```json
{
  "items": { "minecraft:trident": 3, "#somemod:fancy_bows": 3, "othermod:*": 2 },
  "keywords": { "iron": 2, "diamond": 3, "dragonbone": 4 },
  "default": 2
}
```

All files are merged. An item named in `items` (id, #tag, or whole mod) gets that level. Otherwise its level is the
highest of its tool tier (wood/gold 0, stone 1, iron 2, diamond 3, netherite 4; modded tiers give their own), its
armor (from its material's chestplate: leather 0, gold/chain 1, iron 2, diamond 3, netherite 4) and a `keywords` word
in its id (whole words between underscores: `iron_longbow` has `iron`). Items none of those apply to get `default`.
Built-in keywords cover vanilla materials and the common modded metals (copper, tin, bronze, silver, steel...).

### `exclusions/<any>.json`: items that are never relics

```json
{ "items": ["somemod:*", "othermod:silly_hat", "#somemod:joke_weapons"] }
```

All files are merged. The editor writes its own exclusions to `tarnishedrelics:exclusions/editor`. `"*"` can't be used
here (it means "every item that has a category", which the exclusions are part of working out).

### Tags

In `data/tarnishedrelics/tags/` (any pack can add to them, or use the editor's Tags tab):

| Tag | Built in | Used for |
|---|---|---|
| `villager_profession/toolsmiths` | toolsmith | pickaxe, shovel, hoe, axe |
| `villager_profession/armorers` | armorer | helmet, chestplate, leggings, boots, shield |
| `villager_profession/weaponsmiths` | weaponsmith | sword, axe, trident, weapon |
| `villager_profession/fletchers` | fletcher | bow, crossbow, ranged |
| `villager_profession/blacksmiths` | armorer, weaponsmith, toolsmith | categories without `smiths` (a pack's own) |
| `entity_types/undead`, `entity_types/arthropods` | vanilla's undead and arthropods, and every mob the game counts as undead or an arthropod (mods' too, the same check as Smite and Bane of Arthropods) | of Smiting, of the Bane and other damage bonuses |
| `damage_type/melee` | see Melee hits | what counts as a melee hit |

Add a mod's smith to a profession tag to let it identify that kind of relic. A mod's undead only needs adding to
`undead` if the mod doesn't mark it as undead itself.

### Errors

`/tarnishedrelics errors` (and the editor, when saving) lists every problem; a bad file is skipped and the rest still
load. Reported: invalid JSON; a key a file of that kind doesn't have (only the keys in this README, plus `"disabled"`);
an effect field or condition that doesn't exist; an effect that needs a `value` without one; a word that isn't one of
the choices (`kind`, `apply_to`, `as`, `trigger`, `operation`, `slot`); a bad item entry or id (ids are lowercase,
like `minecraft:iron_sword`); a category id that names no category (a legendary's `category`, an affix's
`applies_to`, an `@category` entry); a profession that doesn't exist; a whole-number range (`cost`, `levels`,
`rolls`...) with its min above its max; an
`identify_level` above 5; a color that isn't six hex digits; and an affix a tier rolls with no value for that tier.

## For other mods

- `com.flankrat.tarnishedrelics.api.RelicGeneratedEvent` (Forge event bus) fires with the real item right after it is
  rolled, before it is hidden in the relic: add your own loot rolls (sockets...) there. `getRarity()` is its tier,
  `getSource()` where it came from (`CHEST`, `ENTITY`, `FISHING`, `ARCHAEOLOGY`, `GIFT`, `BARTER`, `OTHER_LOOT`,
  `COMMAND`) and `getLootContext()` the loot roll (null for commands), so a mod can use its own odds.
- Put a JSON text component in the item's `TarnishedRelicsNamePrefix` NBT string to add a word to the name (after
  the quality word).
- An identified relic's data is in the `TarnishedRelics` NBT compound (rarity, category, slot, color, name parts,
  affixes with their rolls).

## Building and testing

`./gradlew build` makes the mod's jar in `build/libs/`. Bugs are reproduced with a test before they're fixed, and the
tests use the mod's real built-in datapack files.

- `./gradlew test`: JUnit tests (`src/test`) with Minecraft bootstrapped but no world: datapack loading and its error
  reports, value rolls, item levels, wildcards, weighted picks, packet splitting, names, tooltips, config values and
  the editor's file checks.
- `./gradlew runGameTestServer`: in-world GameTests (`src/gametest`) on a test server with only this mod: identifying
  at a blacksmith, every effect type and condition, passive effects, relic rolls, the built-in legendaries,
  RelicGeneratedEvent from every kind of loot table, and editor saves. It ends with "All N required tests passed"
  (check the log, not only the exit code).
- The optional integrations: put the mods' jars in `libs/` as `<name>-1.jar` (`quality-1.jar`,
  `tooltipoverhaul-1.jar`...) and add `-PoptionalMods` (Tooltip Overhaul needs `-Pforge_version=47.3.0`).
- In a real modpack: `./gradlew gametestJar` builds `build/libs/*-gametest.jar` (only when asked for), the mod plus its
  GameTests, reobfuscated like the release jar. Put it in the pack's `mods/` instead of the mod's jar, start the server
  with `-Dforge.enabledGameTestNamespaces=tarnishedrelics` and run `/test runall` from the console or RCON (the tests
  are built at the command's position). Forge only turns GameTests on in dev runs, so `ProductionGameTests` registers
  the compatibility tests (`CompatGameTests`) and the `/test` command there and ticks the test runner; it also picks up
  a `PackLocalGameTests` class if a checkout has one. Failures are logged as they happen, and "[Tarnished Relics test]
  GameTests finished" marks the end. Never ship this jar.

## License

All Rights Reserved. See [LICENSE](LICENSE). Content packs made for Tarnished Relics belong to their authors.

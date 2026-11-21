# Tarnished Relics

Diablo-style loot for Minecraft Forge 1.20.1.

Every time a loot chest is generated there is a small chance (3% by default) that an **unidentified relic** is added: a darkened silhouette of a weapon, piece of armor or tool, named only by its type
("Unidentified Sword"). You can't use it, enchant it, repair it or rename it until a **blacksmith villager** identifies
it. Then it turns into the real item, with a tier, random affixes and a name like
**"Champion's Iron Sword of Smiting"**, or for a Unique, **"Sturdy Netherite Pickaxe of Ithireal"**. The rarest
are **Legendaries**: hand-made relics with their own name, lore, signature enchantments and effects.

Everything is datapack-driven: item categories, tiers, affixes and their effects, and name lists. Weapons, armor,
tools and shields from **other mods are picked up automatically**, and an **in-game editor** (`/tarnishedrelics editor`)
can change all of it without touching a file.

## How it plays

1. **Find a relic.** It's plain white, with a silhouette of its category and *"Take it to a blacksmith to identify
   it."* Nothing on it tells you its tier. Fire and lava don't destroy it (like netherite).
2. **Use it on the right smith:** a toolsmith for tools, an armorer for armor (and shields), a weaponsmith for
   weapons, a fletcher for bows and crossbows; an axe goes to a toolsmith or a weaponsmith. The wrong villager tells you who to see. Higher tiers need
   a higher villager level. If the smith isn't skilled enough they
   shake their head and say which level you need. That's the only way to find out.
3. **Pay.** The smith names a price in emeralds; use the relic on them again within 10 seconds to pay. The real item
   replaces the relic in your hand and the smith earns trade XP (they can level up from it).
4. **Use it.** Affixes only work while the item is in its own slot (a sword in the main hand, a helmet on your head).
   A thrown relic weapon (a trident, a modded javelin) hits with its own effects, as if it were still in your hand.
   The tooltip shows the tier and every affix effect; attribute bonuses show in the normal stat lines.

Crafted gear is never affected: only relics found in loot have affixes.

## Modded gear

Categories pick up items by what they are (`"auto"`, see below), so a mod's swords, bows, armor or shields become
relics without being listed: a sword is anything built on Minecraft's sword class (Spartan Weaponry's and Better
Survival's blades are), a helmet is any helmet armor piece, a shield is anything that can block, and so on. Only single,
damageable items count. Anything else that deals melee damage lands in the `weapon` category ("Unidentified Weapon"),
other projectile weapons in `ranged`.

Each item has a **material level** (see `levels/`): its tool tier, its armor value, or a material word in its id
("iron", "diamond"...). Tiers pick base items by level, so an iron katana from a mod is a Magic or Rare relic like an
iron sword, and a netherite one can be Unique.

To keep items out, use an **exclusions** file (whole mods with `"somemod:*"`), or the editor's Mods tab.

When picking a base item, the category is chosen first (by its `weight`), then an item in it, so a mod that adds 300
swords doesn't make nearly every relic a sword.

## Name format

`[Quality word] [name prefix from other mods] [affix prefix] [base item] [affix suffix | of <Unique name>]`

- The **quality word** comes from Quality Equipment Forked if it is installed (optional). Like Quality's own tooltip,
  a quality without attributes ("normal") adds no word, and a quality with no translation is left out.
- With **Tooltip Overhaul** installed (optional), the rating under the name shows the relic's tier ("Unique", in the
  tier's color) instead of the item's vanilla rarity ("Common"). While that rating line is on (`SHOW_RATING`), the
  tooltip body leaves out its own tier line so the tier isn't shown twice.
- The **name prefix from other mods** is any JSON text component another mod puts in the item's
  `TarnishedRelicsNamePrefix` NBT string (for example a socket mod).
- The first rolled prefix affix and first rolled suffix affix name the item. Uniques end with "of" + a generated name.
- The whole name is in the tier's color. A name given in an anvil (after identifying) replaces it.

## Datapack format

Everything lives in `data/<namespace>/tarnished_relics/`. Files from all datapacks are merged; a file with the same
id replaces the earlier one. `/reload` applies changes. `/tarnishedrelics errors` lists files that failed to load, including
ones with an item entry that isn't a valid id (ids are lowercase, like `minecraft:iron_sword`).
Any file with `"disabled": true` is ignored, which switches off a file another pack (or the mod) ships.

Item lists anywhere accept an item id (`"minecraft:iron_sword"`), an item tag (`"#minecraft:swords"`), every item of a
mod (`"somemod:*"`) or every item of a category (`"@tarnishedrelics:sword"`). Rarities also take `"*"`: every item that
has a category.

### `categories/<name>.json`: what kind of item it is

```json
{
  "auto": ["sword"],
  "items": ["#minecraft:swords", "somemod:cool_blade"],
  "exclude": ["minecraft:wooden_sword"],
  "slot": "mainhand",
  "silhouette": "sword",
  "priority": 0,
  "weight": 10
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

The unidentified name is the lang key `item.<namespace>.unidentified.<name>` ("Unidentified Sword") unless `name` is set.
With no lang entry either, it's made from the file name (`war_hammer` -> "Unidentified War Hammer").

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
| `items` | Which base items this tier can be (they must also be in a category). `"*"` = anything with a category |
| `levels` | `[min, max]` material levels for the `"*"` and `"@category"` entries (items named directly always count) |
| `name` | Optional: display name, as a lang key or plain text |
| `rolls` | How many affixes to roll from each pool, e.g. `{ "unique": [2, 3], "normal": [1, 2] }` |
| `identify_level` | Villager level needed: 1 Novice, 2 Apprentice, 3 Journeyman, 4 Expert, 5 Master (1-5) |
| `cost` | Emeralds to identify (rolled once per relic) |
| `villager_xp` | Trade XP the blacksmith earns |
| `color` | Name color once identified, six hex digits (`"#FF5555"`) |
| `unique_name` | `true`: the name ends with "of" + a generated name (see `names/`) |
| `legendary` | `true`: makes legendaries (see Legendaries) |
| `handmade_chance` | Legendary: share that are hand-made (default 0.3) |
| `signature_enchantments`, `enchantment_bonus` | Legendary: how many fitting enchantments, and how many levels above their max (single-level ones stay at 1) |
| `drawback_chance`, `drawback_pool` | Legendary: chance of one affix from that pool (default `drawback`) |
| `lore_lines` | Legendary: how many generated lore lines |
| `quality` | Extra weight per point of the looter's Luck (Luck potions, luck gear), like loot table quality |
| `loot_tables` | Only drops in these loot tables (`*` wildcards); empty = any |

The tier's display name is the lang key `tarnishedrelics.rarity.<namespace>.<name>` unless `name` is set (with no lang
entry either, it's made from the file name).

Built in: `magic` (weight 70, levels 1-2, 1-2 affixes, Novice, 2-4 emeralds), `rare` (25, levels 2-3, 3-4 affixes,
Journeyman, 8-12), `unique` (10, levels 3 and up plus the vanilla bow and crossbow, 2-3 unique-pool + 1-2 normal
affixes, Master, 20-30, generated name), `legendary` (5, quality 1, levels 3 and up, 2 legendary-pool + 1 unique + 1-2 normal affixes, 2-3 signature
enchantments 1-3 levels above max, 35% drawback, 30% hand-made instead, Master, 40-64 emeralds).

### `affixes/<name>.json`: prefixes and suffixes

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
| `kind` | `prefix`, `suffix`, or `hidden` (gives effects but no name part) |
| `name` | The name part ("Champion's", "of the Bear"): a lang key, or plain text |
| `pool` | The pool tiers roll it from (`rolls` in the rarity). Built-in pools: `normal`, `unique` |
| `applies_to` | Category ids, or `"*"` for any |
| `weight` | A number, or per tier (`{ "magic": 10, "rare": 6 }`; tiers not listed can't roll it) |
| `group` | Only one affix of a group per item (e.g. two flat-damage prefixes can't stack) |
| `effects` | What it does (below) |

**Values.** Any `value` can be a number (`3`), a range (`[2, 5]`), or per tier (`{ "magic": [1, 2], "rare": [2, 4],
"default": 1 }`). Negative values are drawbacks. An item stores *where in the range* it rolled (0-1), not the final
number, so changing the numbers in a datapack also changes items already found, and a perfect roll stays perfect.
Affixes removed from the datapack simply stop working on old items.

#### Effect types

| `type` | Fields | Does |
|---|---|---|
| `attribute` | `attribute`, `operation` (`add`, `multiply_base`, `multiply_total`), `value` | Any attribute from any mod. Shown in the normal stat lines. Ignores conditions. |
| `damage_vs` | `value`, `target` (optional entity id/#tag or list) | ±% damage dealt (to matching targets, or to everything) |
| `damage_taken` | `value`, `damage` (optional damage type id/#tag or list) | ±% damage taken (`-0.2` = 20% less) |
| `on_hit_effect` | `effect`, `duration` (ticks), `amplifier`, `apply_to` (`target`/`self`) | Potion effect on a melee hit (or a hit by the thrown relic) |
| `on_hit_fire` | `value` (seconds) | Sets the target on fire on a melee hit (or a hit by the thrown relic) |
| `lifesteal` | `value` | Heals that fraction of melee damage dealt (or the thrown relic's) |
| `thorns` | `value` | Sends that fraction of damage taken back to the attacker |
| `on_hurt_effect` | `effect`, `duration`, `amplifier`, `apply_to` (`attacker`/`self`) | Potion effect when you're hit |
| `passive_effect` | `effect`, `amplifier` | Kept on you while held/worn |
| `crit_chance` | `value` | Extra chance for your hits to crit |
| `crit_damage` | `value` | Crits hit harder (`0.5` = +50%) |
| `break_speed` | `value`, `blocks` (optional block id/#tag) | ±% mining speed |
| `durability` | `value` | Chance each point of wear is ignored (negative: sometimes wears twice) |
| `xp_bonus` | `value` | ±% XP from kills |
| `looting` | `value` | Extra Looting levels |
| `function` | `function`, `trigger` (`hit`, `kill`, `hurt`, `tick`), `as` (`wielder`/`target`) | Runs a datapack function: the catch-all for anything else |

Every effect can also have:

- `"conditions"`: all must pass. `chance` (0-1), `health_below` / `health_above` (fraction of the wielder's max
  health), `in_water`, `is_night`, `is_day`, `sneaking`, `on_fire`, `y_below`, `y_above`, `dimensions` (list),
  `biomes` (ids/#tags), `target` (entity ids/#tags). `break_speed` ignores `chance` (the client works out the
  mining speed too, and a roll on each side would disagree).
- `"slot"`: works while the item is in that slot instead of its category's (`offhand` for a dagger that helps from
  the offhand).
- `"tooltip"`: its own tooltip line, a lang key or plain text (`%1$s` = value as a percent, `%2$s` = plain number).
- `"hidden": true`: no tooltip line.

Damage and effect names in tooltips use the lang key `tarnishedrelics.target.<namespace>.<path>` if it exists,
otherwise the id's path.

### Legendaries

A rarity with `"legendary": true` makes legendaries, in two kinds:

- **Generated** (most of them): a base item and affixes like any rarity (`items`, `levels`, `rolls`; the built-in one
  rolls from the strong `legendary` affix pool plus `unique` and `normal`), and on top: a generated name
  ("Grimfang, the Last Light") and lore lines from `names/`, signature enchantments that fit the item put on above
  their normal max (`signature_enchantments`, `enchantment_bonus`), and a chance of one drawback from the `drawback`
  pool (`drawback_chance`, `drawback_pool`). The name is shown as it is, with nothing added.
- **Hand-made** (`handmade_chance` of them, when any can drop): one from `legendaries/`, below.

### `legendaries/<name>.json`: hand-made legendary relics

A hand-made legendary always has the same name, lore, signature enchantments and effects; only the numbers vary
(each copy gets one roll for all its value ranges).

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
| `name`, `lore` | Its name and the lines under it (lang keys or plain text). Nothing else is added to the name |
| `items`, `exclude` | Base items, one is picked (ids, #tags, `mod:*`); `exclude` takes some back out |
| `category` | Optional: the category (slot, silhouette) if the base item has none |
| `enchantments` | Put on as they are: above the normal max level, and any combination |
| `effects` | Like an affix's effects. `"slot"` on an effect makes it work from another slot (offhand bonus) |
| `weight` | How often it's picked among the legendaries; `0` = only drops with its set |
| `companions` | Other legendaries that drop together with it, as a set |

Built in: Dawnbreaker (with its set piece, Crest of the Dawn), Widow's Kiss, Glass Heart (huge damage, half your max
health), Gravewarden's Aegis, Skyhook, Deepdelver, Tidebreaker, Windstride and The Hollow Crown (riches, but cursed
with Binding). `/tarnishedrelics legendary <id>` gives one (and the rest of its set).

### `names/<any>.json`: words for Unique names

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
highest of: its tool tier (wood/gold 0, stone 1, iron 2, diamond 3, netherite 4; modded tiers give their own), its
armor (from its material's chestplate armor: leather 0, gold/chain 1, iron 2, diamond 3, netherite 4) and a `keywords`
word in its id (whole words between underscores: `iron_longbow` has `iron`). Items none of those apply to get `default`.
Built-in keywords cover vanilla materials and the common modded metals (copper, tin, bronze, silver, steel...).

### `exclusions/<any>.json`: items that are never relics

```json
{ "items": ["somemod:*", "othermod:silly_hat", "#somemod:joke_weapons"] }
```

All files are merged. The editor writes its own exclusions to `tarnishedrelics:exclusions/editor`. `"*"` can't be used
here (it means "every item that has a category", which the exclusions are part of working out).

### Blacksmiths

Each category's `smiths` says who identifies its relics. The built-in ones use three profession tags; add a mod's
smith to one (`data/<namespace>/tags/villager_profession/...`, or the editor's Tags tab) to let it work on that kind:

| Tag | By default | Categories |
|---|---|---|
| `#tarnishedrelics:toolsmiths` | toolsmith | pickaxe, shovel, hoe, axe |
| `#tarnishedrelics:armorers` | armorer | helmet, chestplate, leggings, boots, shield |
| `#tarnishedrelics:weaponsmiths` | weaponsmith | sword, axe, trident, weapon |
| `#tarnishedrelics:fletchers` | fletcher | bow, crossbow, ranged (modded longbows and heavy crossbows are bows and crossbows) |

Categories without `smiths` (a pack's own) go to any profession in `#tarnishedrelics:blacksmiths` (armorer,
weaponsmith and toolsmith by default).

### Thrown weapons

A projectile that carries a relic weapon (a thrown trident, or a modded javelin or throwing knife built on the
arrow class) hits with that weapon's effects, as if it were in its slot, plus your armor's. What's in your hands at
that moment doesn't count for that hit. Arrows don't carry the bow, so a bow's bonus damage counts from your hand as
usual, and on-hit effects stay melee (and thrown relic) only.

## Config (`config/tarnishedrelics-common.toml`)

| Option | Default | Meaning |
|---|---|---|
| `[loot] enabled` | `true` | Relics can turn up in loot |
| `[loot] chance` | `0.03` | Chance that one relic is added each time loot is generated |
| `[loot] lootTypes` | chest | Kinds of loot that can get one: `chest`, `entity` (mob drops: rolled on every kill, so even a few percent adds up fast), `fishing`, `archaeology`, `gift`, `barter` |
| `[loot] entityNeedsPlayerKill` | `true` | If `entity` is on: mob drops only count for kills by a player (no mob farms) |
| `[loot] excludeTables` | the spawn bonus chest | Loot tables that never get relics (`*` wildcards) |
| `[identify] confirmSeconds` | `10` | Time to use the relic on the smith again to pay |
| `[display] showRarityColorUnidentified` | `false` | Unidentified relics show their tier's color as a hint. On a server, the server's value is used for every player |

## Commands (op)

- `/tarnishedrelics give [rarity] [category] [count]`: random relics.
- `/tarnishedrelics legendary <id>`: a specific legendary (and the rest of its set).
- `/tarnishedrelics identify`: identifies the relic in your main hand.
- `/tarnishedrelics errors`: lists datapack files that failed to load or have a problem: invalid JSON, a bad item
  entry, or a category id that names no category (a legendary's `category`, an affix's `applies_to`, an
  `@category` entry), or a misspelled key or value in an affix or legendary (an effect field or condition that
  doesn't exist, an `apply_to` other than target/attacker/self, an `as` other than wielder/target, a `kind` other
  than prefix/suffix/hidden). A category switched off with `"disabled": true` isn't an error.
- `/tarnishedrelics editor`: opens the in-game editor.

## In-game editor

`/tarnishedrelics editor` (op level 2) opens a screen for everything above: the datapack folders (legendaries
included), the mod's tags and the config.

- **Tabs**: categories, rarities, affixes, names, levels, exclusions, tags, settings, mods. The file tabs list their
  files (from the mod, KubeJS, datapacks...); pick one to edit it as a form. Hover a field's name for what it does.
  `+ field` adds optional fields; `Raw JSON` edits the text directly. New files get a readable name from their file
  name.
- **Tags**: the blacksmith professions (`villager_profession/blacksmiths`), the `undead`, `arthropods` and `airborne`
  (flying mobs) lists the affixes use, and any new tag in the `tarnishedrelics` namespace (name it like
  `entity_types/dragons`). Tags from every pack add up, so the editor shows them all together; taking out something
  another pack adds saves the tag with `"replace": true`.
- **Settings**: the config file's options. Saving writes `config/tarnishedrelics-common.toml` at once.
- **Pickers** (`...` buttons) list what's actually loaded: items with icons, tags, whole mods, mobs, villager
  professions, blocks, attributes, potion effects, damage types, biomes, dimensions, categories and rarities,
  filterable by mod. Entries for mods that aren't loaded are shown in yellow (they're kept, they just do nothing here);
  unknown ids in red.
- Each file shows what it does in practice ("Matches 359 items", "Can be 491 base items"), with a **Show** list of the
  items: each with its category, level, rarities and whether any recipe makes it, and a button to exclude it.
  **Exclude no-recipe items** excludes every relic item in the list that nothing crafts (often gear for a material
  from a mod that isn't installed).
- Rarities and categories have **Give** and **Identified** buttons that roll one for you (from the saved data).
- The **Mods** tab lists every loaded mod with gear that can be relics: how many items, and one button to exclude or
  include the whole mod.
- Changes are a working copy until **Save & Reload** (Ctrl+S). The server checks them first; nothing is written if any
  changed file has an error. **Save to** picks where they go:
  - **This world**: the world's `datapacks/tarnishedrelics_editor` datapack, moved to the top of the pack list.
  - **Whole modpack**: `config/tarnishedrelics/datapack`, a datapack every world loads (also old worlds). A modpack
    ships its changes just by including its config folder: no KubeJS or datapack-loader mod needed. Saving a file
    here removes this world's copy of it, so the world doesn't hide the change. Files can be put in this folder by
    hand too (`data/<namespace>/tarnished_relics/...`, `data/<namespace>/tags/...`).
- **Turn off** switches a file off (`"disabled": true`), also for files from the mod or other packs. **Use original**
  drops the editor's copy of a file so the version underneath is used again. **Revert** throws away unsaved changes.

## For other mods

- `com.flankrat.tarnishedrelics.api.RelicGeneratedEvent` (Forge event bus) fires with the real item right after it is
  rolled, before it is hidden in the relic. Add your own loot rolls (sockets etc.) to it there. `getSource()` says where
  it came from (`CHEST`, `ENTITY`, `FISHING`, `ARCHAEOLOGY`, `GIFT`, `BARTER`, `OTHER_LOOT`, `COMMAND`) and
  `getLootContext()` gives the loot roll (null for commands), so a mod can use its own odds per source.
- Put a JSON text component in the item's `TarnishedRelicsNamePrefix` NBT string to add a word to the name.
- The unidentified item is hidden from JEI (an empty one has nothing inside).
- An identified relic's data is in the `TarnishedRelics` NBT compound (rarity, category, slot, color, name parts,
  affixes with their rolls).

## Testing

Bugs are reproduced with a test before they're fixed, and the tests use the mod's real built-in datapack files.

- `./gradlew test`: JUnit tests (`src/test`) with Minecraft bootstrapped but no world: datapack parsing and its
  error reports (including the real loader reading a datapack folder), value rolls, item levels, wildcards, weighted
  picks, packet splitting, name order, and odd modded items.
- `./gradlew runGameTestServer`: in-world GameTests (`src/gametest`) on a test server with the mod loaded: blacksmith
  identifying, effect slots and conditions, passive effects, relic rolls, RelicGeneratedEvent from every kind of loot
  table, and editor saves to "This world" and "Whole modpack". It ends with "All N required tests passed" (check the
  log, not only the exit code).
- With no options both runs have none of the optional mods, which checks nothing needs them. To test the
  integrations, put the mods' jars in `libs/` as `<name>-1.jar` (`quality-1.jar`, `tooltipoverhaul-1.jar`, ...) and add
  `-PoptionalMods` (Tooltip Overhaul needs `-Pforge_version=47.3.0`).

## License

All Rights Reserved. See [LICENSE](LICENSE). Content packs made for Tarnished Relics belong to their authors.

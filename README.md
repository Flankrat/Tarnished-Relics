# Tarnished Relics

Diablo-style loot for Minecraft Forge 1.20.1.

Every time loot is generated (chests, mob drops, fishing, archaeology) there is a small chance (1% by default) that an
**unidentified relic** is added: a darkened silhouette of a weapon, piece of armor or tool, named only by its type
("Unidentified Sword"). You can't use it, enchant it, repair it or rename it until a **blacksmith villager** identifies
it. Then it turns into the real item, with a tier, random affixes and a name like
**"Champion's Iron Sword of Smiting"**, or for a Unique, **"Sturdy Netherite Pickaxe of Ithireal"**.

Everything is datapack-driven: item categories, tiers, affixes and their effects, and name lists.

## How it plays

1. **Find a relic.** It's plain white, with a silhouette of its category and *"Take it to a blacksmith to identify
   it."* Nothing on it tells you its tier.
2. **Use it on a blacksmith** (by default an armorer, weaponsmith or toolsmith: tag
   `#tarnishedrelics:blacksmiths`). Higher tiers need a higher villager level. If the smith isn't skilled enough they
   shake their head and say which level you need. That's the only way to find out.
3. **Pay.** The smith names a price in emeralds; use the relic on them again within 10 seconds to pay. The real item
   replaces the relic in your hand and the smith earns trade XP (they can level up from it).
4. **Use it.** Affixes only work while the item is in its own slot (a sword in the main hand, a helmet on your head).
   The tooltip shows the tier and every affix effect; attribute bonuses show in the normal stat lines.

Crafted gear is never affected: only relics found in loot have affixes.

## Name format

`[Quality word] [name prefix from other mods] [affix prefix] [base item] [affix suffix | of <Unique name>]`

- The **quality word** comes from Quality Equipment Forked if it is installed (optional).
- The **name prefix from other mods** is any JSON text component another mod puts in the item's
  `TarnishedRelicsNamePrefix` NBT string (for example a socket mod).
- The first rolled prefix affix and first rolled suffix affix name the item. Uniques end with "of" + a generated name.
- The whole name is in the tier's color. A name given in an anvil (after identifying) replaces it.

## Datapack format

Everything lives in `data/<namespace>/tarnished_relics/`. Files from all datapacks are merged; a file with the same
id replaces the earlier one. `/reload` applies changes. `/tarnishedrelics errors` lists files that failed to load.

Item lists anywhere accept an item id (`"minecraft:iron_sword"`), an item tag (`"#minecraft:swords"`) or every item of
a category (`"@tarnishedrelics:sword"`).

### `categories/<name>.json`: what kind of item it is

```json
{
  "items": ["#minecraft:swords", "somemod:cool_blade"],
  "exclude": ["minecraft:wooden_sword"],
  "slot": "mainhand",
  "silhouette": "sword",
  "priority": 0
}
```

| Field | Meaning |
|---|---|
| `items`, `exclude` | Which items belong to the category |
| `slot` | Where the item's affixes work: `mainhand`, `offhand`, `head`, `chest`, `legs`, `feet` |
| `silhouette` | The unidentified icon: `generic`, `sword`, `axe`, `pickaxe`, `shovel`, `hoe`, `bow`, `crossbow`, `trident`, `helmet`, `chestplate`, `leggings`, `boots` |
| `priority` | If an item is in several categories, the highest priority wins |

The unidentified name is the lang key `item.<namespace>.unidentified.<name>` ("Unidentified Sword").

Built in: `sword`, `axe`, `pickaxe`, `shovel`, `hoe`, `bow`, `crossbow`, `trident`, `helmet`, `chestplate`, `leggings`,
`boots` (vanilla and Forge tags). To add a mod's weapons, add their ids or tags to a category, or make a new one.

### `rarities/<name>.json`: the tiers

```json
{
  "weight": 25,
  "items": ["minecraft:iron_sword", "#minecraft:swords", "@tarnishedrelics:helmet"],
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
| `items` | Which base items this tier can be (they must also be in a category) |
| `rolls` | How many affixes to roll from each pool, e.g. `{ "unique": [2, 3], "normal": [1, 2] }` |
| `identify_level` | Villager level needed: 1 Novice, 2 Apprentice, 3 Journeyman, 4 Expert, 5 Master |
| `cost` | Emeralds to identify (rolled once per relic) |
| `villager_xp` | Trade XP the blacksmith earns |
| `color` | Name color once identified |
| `unique_name` | `true`: the name ends with "of" + a generated name (see `names/`) |

The tier's display name is the lang key `tarnishedrelics.rarity.<namespace>.<name>`.

Built in: `magic` (weight 70, 1-2 affixes, Novice, 2-4 emeralds), `rare` (25, 3-4 affixes, Journeyman, 8-12),
`unique` (5, 2-3 unique-pool + 1-2 normal affixes, Master, 20-30, generated name).

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
| `name` | Lang key of the name part ("Champion's", "of the Bear") |
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
| `on_hit_effect` | `effect`, `duration` (ticks), `amplifier`, `apply_to` (`target`/`self`) | Potion effect on a melee hit |
| `on_hit_fire` | `value` (seconds) | Sets the target on fire on a melee hit |
| `lifesteal` | `value` | Heals that fraction of melee damage dealt |
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
  `biomes` (ids/#tags), `target` (entity ids/#tags).
- `"tooltip"`: a lang key for its own tooltip line (`%1$s` = value as a percent, `%2$s` = plain number).
- `"hidden": true`: no tooltip line.

Damage and effect names in tooltips use the lang key `tarnishedrelics.target.<namespace>.<path>` if it exists,
otherwise the id's path.

### `names/<any>.json`: words for Unique names

```json
{
  "names": ["Ithireal", "Grimfang"],
  "starts": ["Ith", "Mor", "Vael"],
  "ends": ["ireal", "goth", "andor"]
}
```

All files are merged. A Unique gets a whole name or a start + end, about half the time each.

### Blacksmiths

`data/<namespace>/tags/villager_profession/blacksmiths.json` (tag `#tarnishedrelics:blacksmiths`). Add a mod's smith
profession to let it identify relics.

## Config (`config/tarnishedrelics-common.toml`)

| Option | Default | Meaning |
|---|---|---|
| `[loot] enabled` | `true` | Relics can turn up in loot |
| `[loot] chance` | `0.01` | Chance that one relic is added each time loot is generated |
| `[loot] lootTypes` | chest, entity, fishing, archaeology | Kinds of loot that can get one (also `gift`, `barter`) |
| `[loot] entityNeedsPlayerKill` | `true` | Mob drops only count for kills by a player (no mob farms) |
| `[loot] excludeTables` | the spawn bonus chest | Loot tables that never get relics (`*` wildcards) |
| `[identify] confirmSeconds` | `10` | Time to use the relic on the smith again to pay |
| `[display] showRarityColorUnidentified` | `false` | Unidentified relics show their tier's color as a hint |

## Commands (op)

- `/tarnishedrelics give [rarity] [category] [count]`: random relics.
- `/tarnishedrelics identify`: identifies the relic in your main hand.
- `/tarnishedrelics errors`: lists datapack files that failed to load.

## For other mods

- `com.flankrat.tarnishedrelics.api.RelicGeneratedEvent` (Forge event bus) fires with the real item right after it is
  rolled, before it is hidden in the relic. Add your own loot rolls (sockets etc.) to it there.
- Put a JSON text component in the item's `TarnishedRelicsNamePrefix` NBT string to add a word to the name.
- An identified relic's data is in the `TarnishedRelics` NBT compound (rarity, category, slot, color, name parts,
  affixes with their rolls).

## License

All Rights Reserved. See [LICENSE](LICENSE). Content packs made for Tarnished Relics belong to their authors.

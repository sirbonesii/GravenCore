# GravenCore

## Changelog

All notable changes to the core system of GravenCore are documented here.
Going forward, every change is logged under one of: **Added**, **Removed**, **Changed**.

### [Unreleased]

#### Added
- **Vanish (2026-10-22):** new `/vanish [player]` and `/unvanish [player]`
  commands (permission gravencore.admin, player-name tab completion). A
  vanished player is hidden from every non-admin player (entity and
  player-list entry, including later joiners) while admins keep seeing
  them with a green `[👻] Vanished` tag on their nameplate and a short
  green `[👻]` after their name in the TAB list. The vanished player gets a repeating green action-bar
  reminder ("You are [👻] Vanished"). State is stored in the player's PDC
  so it survives relogs. For the TAB plugin (which formats the list
  itself), GravenCore registers a PlaceholderAPI expansion when PAPI is
  installed: `%gravencore_vanish%` returns `&a[👻]` while vanished and
  nothing otherwise — append it to the name format in TAB's config.
- **InvSee (2026-10-22):** new `/invsee <player>` command (permission
  gravencore.admin) opening a live, editable 54-slot view of the target's
  inventory, laid out per Aaron's diagram: armor (purple placeholders when
  empty), main hand (lime) and off hand (orange) across the top, a full
  gray-pane divider row, then the 27 main inventory slots and the hotbar.
  Admins can take items out and put items in — a reconcile pass twice a
  second pushes admin edits into the target's real inventory and pulls
  the target's own changes into the view; divider panes and the labeled
  placeholder panes (PDC-marked) can never enter the real inventory,
  shift-clicking from the admin's own inventory into the view is blocked,
  and the view closes itself if the target logs out. Tab completion
  offers online player names.
- **Alter of Despair (2026-10-22):** new placeable altar dropper, given with
  `/gravencore alter <username> [count]` (count defaults to 1, clamped 1–64). The dropper item carries the configured name and color
  (`alter.display_name` / `alter.hex_code`, default "Alter of Despair" in
  the red shake color #F8F854) and a PDC marker, and renders as the configured block
  (`alter.block`, default `minecraft:respawn_anchor`) via the item_model
  component (the placed block stays a dropper); placing it spawns the glinting
  ItemDisplay of that block inside the dropper's block (enchantment glint
  override, display scale and forced brightness from the new `alter` config
  section), and breaking the dropper removes the display and drops a fresh
  alter item so the cycle survives. It composes with the Redline Altar: the
  dropper is still the ritual bench, just with the display over it.
- **`/gravencore packreload` (2026-10-22):** pushes the server resource pack to
  every online player live — no server restart, no relog. The pack URL, SHA1,
  required flag and prompt text live in the new `resource-pack` config section
  (update the SHA1 after each MinePack upload, `/gravencore reload`, then
  `/gravencore packreload`). The command refuses to send when the URL is unset
  or the SHA1 isn't a valid 40-character hash.
- **Redline Altar upgrade ritual (2026-10-21):** a dropper ringed by exactly 8 lodestone
  pedestals is an altar. The bench is the dropper's own vanilla UI (no custom GUI):
  place the recipe (4x Dragon's Breath corners, Heavy Core top, 2x Netherite Ingots
  sides, Ghast Tear bottom, base Redline center) slot by slot like a normal dropper —
  a correct item locks in — a deep creaking sound plays to nearby players (it is stored in the dropper block itself, its slot becomes
  a green glass pane, and it appears spinning on the matching pedestal — the spear
  itself stands on the dropper using its big in-hand model, tip to the sky,
  also spinning), wrong items are left untouched like any dropper, and locked items
  can be clicked back out. The altar never sends chat messages. When all 9
  slots lock, lightning strikes the altar, a flame circle bursts from the
  spear and expands outward across the ground, and the ritual starts: the
  pedestal items orbit the center and spiral into the spear (level
  throughout — nothing rises, nothing shrinks), easing to a stop as they
  reach it, ending in an explosion that is lethal to anyone standing within 8 blocks
  (a held totem of undying still saves them) — a sphere of explosion
  particles bursting outward with a ring of smoke blasting out at orbit
  height. After a 1.5-second pause — the planted spear spinning through the
  blast and the pause, vanishing only as its replacement lands — the
  upgraded spear drops at the center
  with a small blood-red poof and an anvil sound (outside that finish burst
  and the pedestal enchant sparkles, the altar has no particles)
  (`upgrading.redline-altar` config: enabled, ritual-duration, start/complete/anvil/lock-in sounds).
- **Celerity Boots: freeze duration on the action bar + block system (2026-10-19):**
  the freeze duration now shows as `❄ 6s` next to the `⏳` cooldown on the action bar;
  boots also gain the block system (new `armor.mythical-boots.block-rate`/`block-power`
  keys, default 25%/4.0, plus `sound-effects.block`; per-item PDC overrides; ✦/🔥 tooltip
  lines; `blockrate`/`blockpower` added to `/gravencore config time_boots`).
- **Renamed Mythical Boots → Celerity Boots (2026-10-19):** display name now uses the
  chromatic-aberration text effect (`#F8F8BC`); weapon command key is now `celerity`.
  Still Mythical tier; class/PDC/config identifiers unchanged (existing boots keep working).
- **Command rework: `/gravencore armor` + `time_boots` config (2026-10-19):** new
  `/gravencore armor <username> <tier>` gives a full armor set to a player (console-safe,
  overflow drops at their feet) with "You have given <name> a <tier> armor set." /
  "You have received <tier> armor set." messages; `/gravencore config` gains the
  `time_boots` item (duration, cooldown, speedboost, freezeplayers, weaknesslevel as
  per-item PDC overrides, like the other items); `/gravencore reload` now also syncs
  worn armor, not just inventory contents. The old `/gravencore give` subcommand was
  removed entirely.
- **New `/gravencore weapon <username> <weapon_name>` (2026-10-19):** mirrors the armor
  command for single items — redline (optional 4th arg base|upgraded, default upgraded),
  dracula, mythicalboots — with "You have given <name> a <weapon>." /
  "You have received a <weapon>." messages. New weapons plug into one switch.
- **Time Slow reworked: tick freeze (2026-10-19):** the boots now freeze the world
  (`ServerTickManager.setFrozen`) instead of lowering the tick rate — tick freeze
  natively exempts players, so the wearer is *truly* unaffected (plus a configurable
  `speed-boost`, default Speed I) while everything else stops. Same 6s/45s tuning,
  same trigger, particles, sound, and cooldown; `tick-rate`/`compensation-*` keys
  replaced by `speed-boost`. Overlap guard and `onDisable` safety net now guard the
  frozen flag instead of the tick rate.
- **Time Slow freezes other players (2026-10-19):** new `freeze-players` key (default
  true) — while frozen, every non-activator is fully pinned (no moving, no looking
  around) and gets Weakness (`weakness-level`, default 5, 0 = off); only the wearer
  keeps moving.
- **Mythical Boots + Time Slow ability (2026-10-19):** standalone Mythical boots
  (like Dracula's Plate — own item, own `armor.mythical-boots` config section, not part
  of the armor set; `/gravencore give mythicalboots`). Shift + left-click drops the
  **server tick rate** (default 4 tps for 6s, 45s cooldown) — the tick rate is
  server-global, so the wearer is compensated with Speed/Haste to feel unaffected while
  the world crawls. Witch-particle ring follows the wearer for the whole slow, trigger
  sound is configurable (`sound-effects.time-slow`), action-bar cooldown via
  `CooldownManager` (⏳ icon), ABILITY section on the boots tooltip, live durability
  lore via `DurabilityLoreListener`.
- **Block sound effects config (2026-10-19):** block proc sounds are now configurable as
  `[sound-name, volume, pitch]` lists — same args as `/playsound` (default
  `minecraft:block.anvil.land`, 100, 1.9). Applies to both the Mythical set
  (`armor.mythical.sound-effects.block`) and Dracula's Plate
  (`weapons.dracula.sound-effects.block`). The hardcoded shield-clang is gone;
  `GravenConfig` parses the lists with safe fallbacks.
- **Enchants tooltip section (2026-10-18):** vanilla enchantment lines no longer float at the
  top of armor tooltips — `HIDE_ENCHANTS` suppresses the client's automatic block and the
  enchantments render under their own "Enchants" divider (after Durability, before ATTRIBUTES),
  using vanilla's display names. Only shows when the piece actually has enchantments. Applies
  to the Mythical set and Dracula's Plate.
- **Divider auto-fit (2026-10-18):** `TooltipBuilder.fitDividers()` resizes every divider and
  section-header line to span the widest content line, so dividers always match the tooltip
  box width (previously fixed at 17 dashes, which looked short once Block Rate/Power widened
  the ATTRIBUTES section). Applied in all three item sync methods; never shrinks below the
  classic 17-dash width.
- **Dracula's Plate block system (2026-10-18):** the plate now has the same blocking mechanic
  as the Mythical set — `weapons.dracula.block-rate`% chance (default 25.0) to trigger on an
  incoming attack while wearing it, subtracting `weapons.dracula.block-power` flat damage
  (default 4.0), with shield-clang sound and crit particles. Per-plate PDC values
  (`dracula_blockrate`/`dracula_blockpower`), tunable via `/gravencore config dracula
  <blockrate|blockpower>` (tab-completed); Block Rate (✦ teal) and Block Power (🔥 amber)
  shown in the ATTRIBUTES tooltip section.
- **Mythical armor set prototype (2026-10-17):** new `MythicalArmorSet` module — a full
  netherite armor set (helmet/chestplate/leggings/boots) sharing the Mythical tier tooltip
  chrome (tier line, indented Durability: Unbreakable, ATTRIBUTES — no "Created by" footer). ATTRIBUTES
  shows Defense (🛡 light-blue), ArT (💎 sky blue, armor toughness) and KbR
  (⚓ soft blue, knockback resistance) as real attribute modifiers with per-item PDC overrides, synced from the new
  `armor.mythical.pieces.*` config keys (netherite defaults: 3/8/6/3 defense, 3.0 toughness,
  0.1 knockback resistance). Piece names use the `#F8F8A0` aurora tier color.
  `/gravencore give armor mythical <helmet|chestplate|leggings|boots|set>` (category + tier
  structure, ready for more tiers later), `/gravencore config
  mythical <defense|toughness|kbresist|blockrate|blockpower> <value>`, and `/gravencore reload` all cover the set.
- **Mythical armor set tweaks (2026-10-17):** piece names are now the vanilla item name in
  gray (e.g. "Netherite Chestplate") instead of the aurora "Mythical ..." names; the set is
  no longer unbreakable — each piece has `armor.mythical.durability` max durability
  (default 2096); the tooltip's Durability line shows actual/max (e.g. `2096/2096`).
- **Section headers embedded in dividers (2026-10-17):** `TooltipBuilder.section()` now
  renders the title centered inside the divider line (e.g. `——————ATTRIBUTES——————`, dashes
  sized to match the plain divider width) instead of a separate header line; the plain
  dividers that used to sit right before each header were removed.
- **Embedded header sizing fix (2026-10-17):** the embedded section headers were overflowing the
  tooltip's right edge — bold capitals run wider than first estimated, so the dash budget was
  retuned (one bold title char ≈ 0.7 em-dashes; ATTRIBUTES now gets 5 dashes per side).
- **Live durability lore (2026-10-17):** new `DurabilityLoreListener` refreshes the
  `Durability: <actual>/<max>` line on `PlayerItemDamageEvent` as pieces take damage. Item sets
  register their PDC id key (`track(...)`); the max is read from the item's own Damageable meta,
  so future armor sets get it for free. `TooltipBuilder.isDurabilityLine()` locates the line.
- **Tab completion fix (2026-10-17):** `/gravencore config` was suggesting `armor` as the second
  word, but the command expects `mythical` — now completes `redline|dracula|mythical` (give keeps
  `redline|dracula|armor` → `mythical` → `helmet|chestplate|leggings|boots|set`).
- **Mythical block system (2026-10-18):** while wearing at least one set piece, incoming attacks
  have an `armor.mythical.block-rate`% chance (default 25.0) to trigger a block that subtracts
  `armor.mythical.block-power` flat damage (default 4.0), with a shield-clang sound and crit
  particles on proc. `MythicalArmorSet` is now also a Listener, registered in `onEnable`. Block
  Rate (✦ teal, shown as %) and Block Power (🔥 amber, +flat) now appear in the ATTRIBUTES section;
  `TooltipBuilder.attribute()` gained a no-plus-prefix overload for percent stats.
- **Block stats per-item tunable (2026-10-18):** block rate/power are now per-piece PDC values
  (defaulting from `armor.mythical.block-rate`/`block-power`), editable in-game via
  `/gravencore config mythical <blockrate|blockpower> <value>` while holding the piece (tab-completed).
  When hit, the worn piece with the highest block rate does the blocking; the tooltip shows each
  piece's own values.
- **Reload message fix (2026-10-18):** `/gravencore reload` printed raw `TextComponentImpl{...}`
  garbage — `GravenTheme.prefix()` returns a Component and was being string-concatenated, which
  called `toString()` on it. Now properly appended as a component.
- **Lore hardcoded (2026-10-18):** item lore text is no longer read from the config — the
  Redline spear lore, Dracula ABILITY lines and Dracula EFFECTS lines now live as constants in
  `RedlineSpear`/`DraculaPlate`. Stat numbers still come from config (placeholders keep working),
  but the text itself can't be broken by config edits. Dead `lore:`/`effects:` keys removed.
- **Tooltip system prototype (2026-10-17):** new `TooltipBuilder` utility that composes sectioned
  item tooltips (tier line, dividers, `ATTRIBUTES` / `EFFECTS` headers, creator footer) so item
  classes only declare content. New `ItemTier` enum (currently only `MYTHICAL`; more tiers later).
- **DraculaPlate ATTRIBUTES section:** Defense (+8.0 armor) and Attack Damage (+2.0 while worn,
  stored per-item in PDC like other overrides, new `weapons.dracula.attack-damage` key).
- **DraculaPlate lifesteal:** heals `weapons.dracula.lifesteal-percent` (default 20%) of damage
  dealt while wearing the plate, with a small red particle burst on proc.
- **DraculaPlate EFFECTS section:** Passive Regeneration I (Bat Form) and Lifesteal lines
  (new `weapons.dracula.effects` key, `<lifesteal>` placeholder).
- **DraculaPlate tier line:** shows `Tier: Mythical` (new `weapons.dracula.tier` key, read via
  `ItemTier.fromId`, ready for the full tier system later).
- `src/main/resources/config.yml.backup` — verbatim backup of the pre-migration config file.

#### Changed
- **Altar spear position is configurable (2026-10-22):** the planted
  spear's display offset on the Redline Altar is now
  `upgrading.redline-altar.spear-offset` (x / y / z, in blocks, relative
  to the spear's own facing; defaults 0 / 0 / 0.120, Aaron's measured
  values), so its position can be tuned live without a code change.
- **Sound config format (2026-10-22):** sound effects in config.yml are
  now written as `sound:` / `volume:` / `pitch:` maps instead of
  `[name, volume, pitch]` lists (the mixed-type lists tripped IDE type
  warnings). `GravenConfig.readSoundList` reads the map form first and
  still accepts the legacy list form, so existing live configs keep
  working unchanged.
- **Redline warden fire sounds (2026-10-22):** the charge-up's expanding
  pulse rings stay as they were (a fixed-hoop variant from Aaron's reference
  video was tried and reverted the same day), and the per-beat charge chime
  is unchanged. The new `weapons.redline.sound-effects.pre-fire` sound
  (default warden sonic_charge, 100, 0.6) plays to everyone on the 1st
  pulse of the charge-up, and at the end of the charge the beam fires
  with the new `weapons.redline.sound-effects.fire` sound (default warden
  sonic_boom, 100, 0.6) ahead of the existing blast sounds. The end-of-beam
  flame rings still expand as before.
- **packreload API fix:** the command now calls Paper's `setResourcePack`
  (URL, byte-hash, prompt, force) — the `sendResourcePack` overloads it was
  written against don't exist in the Paper 1.21.4 API, so the first build
  failed to compile. The config SHA1 is converted from hex to bytes with
  `HexFormat`.
- **Enchantments now apply live (2026-10-22):** the Mythical set, Dracula's
  Plate and Celerity Boots bake their enchantments into the lore's
  Enchantments section, which used to go stale — enchant a piece and the
  tooltip only caught up after a `/gravencore reload`. A new
  `EnchantSyncListener` refreshes the piece the moment its enchantments
  change (enchanting table, anvil, grindstone, plus a click/pickup
  backstop), re-running its sync only when the section is actually stale
  and preserving anvil renames.
- **Redline blast beam + beam circles (2026-10-21):** the ability beam is now
  tighter and cleaner (crimson/orange dust spreads cut from 0.35/0.45 to
  0.10/0.16, smaller dust, finer steps; the mid-beam lava/explosion accents
  are gone). At the end of the beam, three expanding flame rings ripple
  outward in sequence toward the tip — each one identical to the first
  charge-up ring, replayed at the far end.
- **Redline charge-up sound (2026-10-21):** the PulseBeat charge now plays
  `minecraft:block.end_portal_frame.fill` (config list
  `weapons.redline.sound-effects.charge`, default 100 / 0.9) at each pulse,
  heard only by players within 30 blocks; replaces the note-block chime/pling
  + firecharge layer and its rising-pitch ladder. The altar's spear-spawn
  sound (`minecraft:block.end_portal.spawn` to everyone) can be toggled with
  `upgrading.redline-altar.spawn-sound`.
- **Redline custom model data (2026-10-21):** base spear gets `custom_model_data` 2000,
  upgraded gets 2001 (set in `syncSpearStatsAndLore`, so reloads re-apply it).
- **Teldaria-style dividers v3 (2026-10-19):** reverted to dashes (the ── character didn't
  render in-game) sized to content width instead of full-bleed; kept the section colors
  (white ATTRIBUTES, aqua EFFECTS, gold ENCHANTMENTS) with centered non-bold titles.
- **Divider length fix (2026-10-19):** section headers now use exact character counts
  (left + title + right = width) so the Enchantments/ATTRIBUTES header dividers are
  precisely the same length as the plain bottom divider — fixes the off-by-one from
  integer division.
- **Config migration (2026-10-17):** `src/main/resources/config.yml` migrated from the old flat
  "TheRedline" format to the nested `settings.*` / `weapons.*` structure that `GravenConfig`
  actually reads. Carried over: `settings.creator`, the full `weapons.redline` section
  (melee/ability damage, range, cooldown, attack speed, launch velocity, pierce-blocks, lore),
  plus a new `weapons.dracula` section populated from the code defaults and fallback lore
  (the old file had no dracula section). Retired key `use-animation` kept as a comment for reference.
  - Note: the old file's values were dormant (the code only read nested paths), so this migration
    activates them: melee-damage.upgraded 6.5, ability-cooldown.base 30s,
    ability-damage.upgraded 200.0 (intended value; the 22.0 code default was a testing placeholder),
    launch-velocity -2.85 (intentional backwards recoil dash).
- **DraculaPlate lore layout (2026-10-17):** `weapons.dracula.lore` is now content-only (ability
  body); section chrome moved into `TooltipBuilder`. Added a "Sneak + Jump" hint line; the
  old `PASSIVE: Regeneration` line moved to the new EFFECTS section.
- **DraculaPlate tooltip polish (2026-10-17):** ATTRIBUTES labels restyled — "Atk Damage" in
  green, "Defense" in light-blue (`TooltipBuilder.attribute` now takes a label color). Added a
  "Durability: Unbreakable" line under the tier line. Removed the "Sneak + Jump" hint from the
  ability lore and "(Bat Form)" from the EFFECTS passive line.
- **DraculaPlate tooltip polish 2 (2026-10-17):** attribute ":" separators now gray (labels keep
  their colors); Durability line slightly indented. Lifesteal is now chance-based — new
  `weapons.dracula.lifesteal-chance` key (default 85%), shown in the EFFECTS line via the
  `<lifesteal_chance>` placeholder ("85% chance to heal 20.0% of damage dealt").
- **DraculaPlate tooltip polish 3 (2026-10-17):** Lifesteal EFFECTS line shortened back to
  "20.0% of damage dealt" so it fits within the dividers (the 85% proc chance stays in the
  code, just no longer shown in the tooltip).
- **Redline Spear tooltip fix (2026-10-17):** the Cooldown lore line used the literal text
  `:fire:`, which nothing renders as a flame — swapped to the real 🔥 emoji, matching the
  code fallback (the original config had the `:fire:` text; it was carried over verbatim in
  the migration).
- **Mythical tier color (2026-10-17):** tier name now renders in `#F8F8A0` (was purple
  `0xAA00AA`).

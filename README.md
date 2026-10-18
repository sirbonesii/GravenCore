# GravenCore

## Changelog

All notable changes to the core system of GravenCore are documented here.
Going forward, every change is logged under one of: **Added**, **Removed**, **Changed**.

### [Unreleased]

#### Added
- **Mythical armor set prototype (2026-10-17):** new `MythicalArmorSet` module — a full
  netherite armor set (helmet/chestplate/leggings/boots) sharing the Mythical tier tooltip
  chrome (tier line, indented Durability: Unbreakable, ATTRIBUTES — no "Created by" footer). ATTRIBUTES
  shows Defense (🛡 light-blue), Armor Toughness (💎 sky blue) and Knockback Resistance
  (⚓ soft blue) as real attribute modifiers with per-item PDC overrides, synced from the new
  `armor.mythical.pieces.*` config keys (netherite defaults: 3/8/6/3 defense, 3.0 toughness,
  0.1 knockback resistance). Piece names use the `#F8F8A0` aurora tier color.
  `/gravencore give armor mythical <helmet|chestplate|leggings|boots|set>` (category + tier
  structure, ready for more tiers later), `/gravencore config
  mythical <defense|toughness|kbresist> <value>`, and `/gravencore reload` all cover the set.
- **Mythical armor set tweaks (2026-10-17):** piece names are now the vanilla item name in
  gray (e.g. "Netherite Chestplate") instead of the aurora "Mythical ..." names; the set is
  no longer unbreakable — each piece has `armor.mythical.durability` max durability
  (default 2096), shown in the tooltip's Durability line.
- **Section headers embedded in dividers (2026-10-17):** `TooltipBuilder.section()` now
  renders the title centered inside the divider line (e.g. `——————ATTRIBUTES——————`, dashes
  sized to match the plain divider width) instead of a separate header line; the plain
  dividers that used to sit right before each header were removed.
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

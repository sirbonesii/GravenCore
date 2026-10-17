# GravenCore

## Changelog

All notable changes to the core system of GravenCore are documented here.
Going forward, every change is logged under one of: **Added**, **Removed**, **Changed**.

### [Unreleased]

#### Added
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

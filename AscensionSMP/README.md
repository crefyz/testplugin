# Ascension SMP

An original elemental SMP plugin (Spigot/Paper 1.21+).

## Concept
- On first join you're assigned a random **element** and a bound **Core** item.
- **Passive** effects are always active; right-click the Core for the **active ability**.
- **Kill players** to gain levels (max 5): stronger abilities, shorter cooldowns. Dying costs a level.
- Kills may drop a **Rift Shard**. Right-click it to reroll your element.

| Element | Passive | Ability |
|---|---|---|
| Ember | Fire Resistance | Inferno Burst (AoE fire + knockback) |
| Tide | Water Breathing, Dolphin's Grace | Tidal Surge (push, slow, regen) |
| Gale | Speed, no fall damage | Gale Dash (launch) |
| Terra | Extra hearts | Stone Skin (Resistance + Absorption) |
| Void | Night Vision | Blink (teleport) |

## Commands
- `/element` - your info
- `/element core` - restore a lost Core
- `/element set <player> <element>` (admin)
- `/element level <player> <n>` (admin)
- `/element shard` (admin)

## Build
`mvn clean package` -> `target/AscensionSMP-1.0.0.jar` into your server's `plugins/` folder (Java 21).

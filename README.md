# Dragon Mine Z: Multiverse

Dragon Mine Z: Multiverse is a client-and-server addon for Dragon Mine Z. It adds a complete divine Saiyan progression and a craftable Multiversal Compass for safe travel among unlocked vanilla and Dragon Mine Z worlds.

## Divine Saiyan forms

The addon adds two progression branches for Saiyan characters:

| Form | TP cost | Mastery requirement |
| --- | ---: | --- |
| Super Saiyan God | 5,000,000 | None |
| Super Saiyan Blue | 10,000,000 | Super Saiyan God 100 |
| Super Saiyan Evolved | 15,000,000 | Super Saiyan Blue 100 |
| Ultra Instinct | 30,000,000 | Super Saiyan Blue 100 |
| Ultra Ego | 50,000,000 | Super Saiyan Evolved 100 |
| Mastered Ultra Instinct | 55,000,000 | Ultra Instinct 100 |

Blue and Evolved automatically use their rose/dark-rose palettes when alignment is 40 or lower. Their progression and mastery remain attached to the canonical forms. Ultra Instinct, Ultra Ego, and Mastered Ultra Instinct have their requested aura, outline, scale, hair, eye, and lightning effects, plus tail-color overrides for characters that already have a Saiyan tail.

All six forms use the custom divine transformation sound. Charging an aura in one of these forms uses the custom looping charge sound with a short fade in and fade out.

On first launch, the addon creates editable form files under `config/dragonminez/races/saiyan/forms/` and adds the required skill costs to Dragon Mine Z's existing configuration. Existing generated form files are never overwritten, so server owners retain control of their balance settings.

## Compatibility

| Component | Supported version |
| --- | --- |
| Minecraft | 1.20.1 |
| Forge | 47.4.10 or newer 47.x build |
| Dragon Mine Z | 2.1.3 |
| Java | 17 |

Dragon Mine Z and its required dependencies - GeckoLib, TerraBlender, and Curios API - must also be installed on both the client and server.

## Using the Multiversal Compass

1. Craft the compass with a vanilla compass, an Eye of Ender, two Kikono Shards, and a Nether Star.
2. Sneak and use the compass to cycle through destinations that your Dragon Mine Z character has unlocked.
3. Use it normally to travel to a safe arrival point near that destination's canonical coordinates.

The compass reads Dragon Mine Z's reloadable Space Pod destination registry. Quest locks, server datapacks, disabled worlds, and Dragon Mine Z's death state are respected. The Nether and The End are included as additional vanilla destinations.

The default successful-travel cooldown is 10 seconds. Server owners can change it, along with the safe-arrival search radius, in `config/dmz_multiverse-common.toml`.

## Admin command

Operators with permission level 2 can max every race-applicable Dragon Mine Z skill, including movement, utility, ki, and strike skills. Transformations and stack forms are excluded.

- `/dmzmultiverse maxskills` maxes the executing player's applicable skills.
- `/dmzmultiverse maxskills <targets>` maxes selected online players, such as `/dmzmultiverse maxskills @a`.

Forms continue to use Dragon Mine Z's own `/dmzform` command. For example, `/dmzform set dmz_multiverse_god_tiers 3` and `/dmzform set dmz_multiverse_ultraforms 3` unlock the two Multiverse form tracks for testing.

## Building

The repository includes a pinned Gradle wrapper and a Java 17 toolchain definition. On Windows, build with:

```powershell
.\gradlew.bat build
```

The distributable JAR is written to `build/libs/`. The development runtime resolves Dragon Mine Z 2.1.3 from its pinned CurseForge file ID; no upstream JAR is committed to this repository.

## Testing in Minecraft

On Windows, double-click `Launch Minecraft Test Client.bat` in the project folder. It opens the Minecraft 1.20.1 Forge development client with Dragon Mine Z, its required dependencies, and Dragon Mine Z: Multiverse already loaded. The first launch may take several minutes while Gradle prepares the test environment.

For a command smoke test, create a Dragon Mine Z character and run `/dmzmultiverse maxskills`. Confirm that movement and utility skills reach their configured caps, predefined ki and strike attacks appear in the technique list, and form and stack-form entries remain unchanged. Run `/dmzmultiverse maxskills @a` as an operator to verify target selection and multiplayer synchronization.

## License and attribution

Copyright (c) 2026 HeyImSoap. Code and original project artwork are licensed under the [GNU General Public License v3.0 or later](LICENSE).

The two custom audio recordings were supplied and cleared for redistribution by the project owner. Details are recorded in [ASSET_NOTICES.md](ASSET_NOTICES.md).

This is an independent addon and is not affiliated with or endorsed by the Dragon Mine Z team, Mojang Studios, Microsoft, or the owners of Dragon Ball. Dragon Mine Z is available from its [official CurseForge page](https://www.curseforge.com/minecraft/mc-mods/dragonminez) and [source repository](https://github.com/DragonMineZ/dragonminez).

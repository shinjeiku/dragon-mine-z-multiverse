# Dragon Mine Z: Multiverse

Dragon Mine Z: Multiverse is a client-and-server addon for Dragon Mine Z. Its first release adds a craftable **Multiversal Compass** for safe travel among unlocked vanilla and Dragon Mine Z worlds.

## Compatibility

| Component | Supported version |
| --- | --- |
| Minecraft | 1.20.1 |
| Forge | 47.4.10 or newer 47.x build |
| Dragon Mine Z | 2.1.3 through 2.1.x |
| Java | 17 |

Dragon Mine Z and its required dependencies—GeckoLib, TerraBlender, and Curios API—must also be installed on both the client and server.

## Using the Multiversal Compass

1. Craft the compass with a vanilla compass, an Eye of Ender, two Kikono Shards, and a Nether Star.
2. Sneak and use the compass to cycle through destinations that your Dragon Mine Z character has unlocked.
3. Use it normally to travel to a safe arrival point near that destination's canonical coordinates.

The compass reads Dragon Mine Z's reloadable Space Pod destination registry. Quest locks, server datapacks, disabled worlds, and Dragon Mine Z's death state are respected. The Nether and The End are included as additional vanilla destinations.

The default successful-travel cooldown is 10 seconds. Server owners can change it, along with the safe-arrival search radius, in `config/dmz_multiverse-common.toml`.

## For developers

The repository includes a pinned Gradle wrapper and a Java 17 toolchain definition. Build with:

```shell
./gradlew build
```

On Windows:

```powershell
.\gradlew.bat build
```

The distributable JAR is written to `build/libs/`. The development runtime resolves Dragon Mine Z 2.1.3 from its pinned CurseForge file ID; no upstream JAR is committed to this repository.

## License and attribution

Copyright © 2026 HeyImSoap. Licensed under the [GNU General Public License v3.0 or later](LICENSE).

This is an independent addon and is not affiliated with or endorsed by the Dragon Mine Z team, Mojang Studios, Microsoft, or the owners of Dragon Ball. Dragon Mine Z is available from its [official CurseForge page](https://www.curseforge.com/minecraft/mc-mods/dragonminez) and [source repository](https://github.com/DragonMineZ/dragonminez).

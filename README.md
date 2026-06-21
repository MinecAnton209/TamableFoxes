# Tamable Foxes (Community Port)

[![SpigotMC](https://img.shields.io/badge/SpigotMC-Coming_Soon-FF6600?logo=spigotmc&logoColor=white&style=flat-square)](https://www.spigotmc.org/resources/69537/)
[![Modrinth](https://img.shields.io/badge/Modrinth-Coming_Soon-1BD96A?logo=modrinth&logoColor=white&style=flat-square)](https://modrinth.com/)
[![GitHub](https://img.shields.io/badge/GitHub-MinecAnton209/TamableFoxes-181717?logo=github&logoColor=white&style=flat-square)](https://github.com/MinecAnton209/TamableFoxes)
[![License: GPLv3](https://img.shields.io/badge/License-GPLv3-blue?style=flat-square)](LICENSE)
[![Java 25+](https://img.shields.io/badge/Java-25%2B-ED8B00?logo=openjdk&logoColor=white&style=flat-square)](https://adoptium.net/)

A Spigot/Paper plugin that adds tamable foxes to Minecraft.

> **Warning:** Do not reload the plugin at runtime — you may lose your tamed foxes. Use `/tamablefoxes reload` or restart the server instead.

## Features

- Tame wild foxes using raw chicken (33% chance)
- Breed tamed foxes with sweet berries
- Tamed foxes follow their owner and sleep when the owner sleeps
- Shift + right-click to give items to your fox
- Right-click to toggle sit
- Shift + right-click with empty hand to toggle sleep
- Foxes attack the owner's targets and things that attack the owner
- Foxes holding a Totem of Undying will consume it and revive
- Wild foxes pick berry bushes and pounce on chickens and rabbits
- Natural spawning in the same biomes as vanilla foxes
- Snow and red fox variants
- Configurable messages via `language.yml`
- Death notification when a tamed fox dies
- bStats metrics (opt-out in `bStats/config.yml`)

## Supported Versions

| Minecraft | Java |
|-----------|------|
| 1.14 – 1.16.5 | 8+ |
| 1.17 – 1.17.1 | 16+ |
| 1.18 – 1.21.11 | 17+ |
| 26.1, 26.2 | 25+ |

## Commands

| Command | Description |
|---------|-------------|
| `/spawntamablefox [red/snow]` | Spawn a tamable fox at your location |
| `/givefox <player>` | Give a fox to another player |
| `/tamablefoxes reload` | Reload the plugin config |

## Permissions

| Permission | Default | Description |
|------------|---------|-------------|
| `tamablefoxes.tame` | everyone | Tame foxes |
| `tamablefoxes.tame.unlimited` | op | Bypass tame limit |
| `tamablefoxes.tame.anywhere` | op | Tame in banned worlds |
| `tamablefoxes.spawn` | op | Use `/spawntamablefox` |
| `tamablefoxes.givefox.give` | everyone | Use `/givefox` |
| `tamablefoxes.givefox.give.others` | op | Give another player's fox |
| `tamablefoxes.givefox.receive` | everyone | Receive foxes |
| `tamablefoxes.reload` | op | Reload config |

## Configuration

- [`config.yml`](Plugin/src/main/resources/config.yml) — main settings
- [`language.yml`](Plugin/src/main/resources/language.yml) — message customization (set any message to `disabled` to hide it)

## Building

**Requirements:** JDK 21 (JDK 25 for the 26.x module)

```bash
mvn clean package -DskipTests
```

The output plugin JAR will be in `Plugin/target/`.

> **Note:** Older NMS modules require their corresponding Spigot versions in your local Maven cache. Run `compileSpigotVersions.sh` (with the appropriate JDKs installed) to populate the cache, or build with `mvn package` alone — it will work if network conditions are good.

## Screenshots

![Foxes sleeping with player](Screenshots/foxes-sleeping-with-player.png)
![Foxes sitting with player holding sword](Screenshots/foxes-sitting-sword.png)
![Foxes with baby looking at player](Screenshots/foxes-baby-looking-at-player.png)
![Giving fox item](Screenshots/giving-fox-item.gif)
![Fox pouncing](Screenshots/fox-pouncing.gif)

## License & Credits

This project is a community-maintained port of the original Tamable Foxes plugin created by [SeanOMik](https://github.com/SeanOMik).

**Original work:** Licensed under the [MIT License](https://github.com/SeanOMik/TamableFoxes/blob/master/LICENSE).

**Current port:** Licensed under the [GNU General Public License v3.0 (GPLv3)](LICENSE).

The original source code and MIT license terms can be found at <https://github.com/SeanOMik/TamableFoxes>.

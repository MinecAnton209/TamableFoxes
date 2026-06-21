# Tamable Foxes (Community Port) v1.0.0

## What's New
- **Full rewrite from scratch** — community-maintained fork of SeanOMik's TamableFoxes
- **Adapter pattern architecture** — all 26 NMS modules share a single logic core (`TamableFoxLogic`), making future development 26x faster

## Supported Versions
- Minecraft **1.14 through 26.2**
- Works on **Spigot, Paper, and Purpur**

## Features
- Tame any fox by feeding it your favorite food
- Tamed foxes follow you, attack your enemies, and protect you
- Name your fox with a right-click AnvilGUI
- Transfer fox ownership to other players
- Configurable tame limits per player
- SQLite-backed tame tracking
- Customizable messages via language config
- Smoke particles when a fox rejects you
- Heart particles when taming succeeds

## Bug Fixes (from original)
- Fixed entity metadata corruption causing `ArrayIndexOutOfBoundsException` on vanilla clients
- Fixed fox disappearance due to sit goal race condition
- Fixed `getOwner()` NPE crash in `die()` method
- Fixed fox walking while sitting
- Fixed double `mobInteract()` call

## Technical
- bStats integration (plugin ID 32133)
- Java 21+ (Java 25 for MC 26.x)
- AnvilGUI 1.10.13-SNAPSHOT
- Maven shade plugin 3.6.2

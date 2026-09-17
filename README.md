# ⚔️ TovekCombat

<div align="center">

[![Platform](https://img.shields.io/badge/Platform-Paper%20%7C%20Purpur-F78C40?style=flat-square&logo=minecraft&logoColor=white)](https://papermc.io)
[![Java](https://img.shields.io/badge/Java-21%2B-ED8B00?style=flat-square&logo=openjdk&logoColor=white)](https://www.oracle.com/java/)
[![License](https://img.shields.io/badge/License-MIT-green?style=flat-square)](LICENSE)
[![Version](https://img.shields.io/badge/Version-1.21.x-blue?style=flat-square)](https://papermc.io)

**Asynchronous Anti-Combat Logging and PVP Tagging engine for Paper 1.21+.**

[📦 Download Latest Release](https://github.com/toprakeker/TovekCombat/releases/latest) • [🌐 Developer Portfolio](https://toprakeker.com)

</div>

---

## Technical Architecture & Performance

TovekCombat is built specifically for modern Paper servers with strict execution guarantees:
- **Non-blocking Tag Lookups:** All active combat tags are stored in an in-memory `ConcurrentHashMap`, ensuring $O(1)$ state lookups on hit events without blocking the main tick.
- **Asynchronous Expiration & Actionbar Tickers:** Combat cooldowns and remaining second updates are handled via lightweight tick tasks avoiding main thread I/O stalls.
- **Defensive Combat-Log Handling:** Handles edge-cases during player disconnects (survival inventory dropping, potion effects, and clean un-tagging to prevent memory leaks).
- **Sanitized Command Interception:** Uses normalized root command validation to prevent namespace bypasses (e.g. `minecraft:tp`, `/essentials:spawn`).

---

## Features

- **PVP Tagging Engine:** Triggers on direct player attacks, projectile impacts, and lingering/splash potions.
- **Actionbar Countdown:** Real-time remaining seconds ticker on actionbar (`⚔ COMBAT | 14.5s`).
- **Combat-Log Punishment:** Eliminates players who disconnect while in combat, dropping items naturally and notifying the server.
- **Elytra Glide Prevention:** Disables elytra opening while tagged to prevent combat evasion.
- **Configurable Command Blacklist:** Blocks escape commands like `/spawn`, `/home`, `/tp`, `/fly`, etc.

---

## Commands & Permissions

| Command | Permission | Description |
| :--- | :--- | :--- |
| `/combat` | *Everyone* | View your current combat tag status and timer |
| `/combat reload` | `combat.admin` | Reload configuration settings |
| *Bypass Tag* | `combat.bypass` | Bypass command restrictions while in combat |

---

## Building from Source

```bash
git clone https://github.com/toprakeker/TovekCombat.git
cd TovekCombat
./gradlew build
```

Compiled jar will be located in `build/libs/`.

---

## License

MIT License. Free for public server networks and modification.

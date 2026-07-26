# Fork Notice — `lowern1ght/burg`

Burg is a fork of [**TheGoldenWorld/OnceUponATown**](https://github.com/DawnOfTimeMC/onceuponatown), a Minecraft mod by TheGoldenWorld (DawnOfTimeMC).

## Lineage

```
TheGoldenWorld/OnceUponATown (upstream)
        │
        │  forked 2026
        ▼
lowern1ght/oncepope (this repository)
        │
        │  rebranded 2026-07-26
        ▼
lowern1ght/burg (current name)
```

The fork was created on the `1.20.1-reborn` branch in mid-2026 and continues the upstream's lineage of the "Once Upon a Town" mod. In July 2026 the fork was rebranded to **Burg** and its repository renamed accordingly.

## License inheritance

Both the upstream mod and this fork are licensed under the **GNU General Public License v3.0**. See [`LICENSE`](LICENSE) for the full text.

Under GPL-3, the fork is required to:

1. **Retain the original license.** ✅ — `LICENSE` is unchanged.
2. **Mark the work as modified.** ✅ — this file and the README make the fork's status explicit.
3. **Preserve the original authorship notice.** ✅ — TheGoldenWorld is credited above and in the README.
4. **Make the source available.** ✅ — this repository is the source.
5. **License derivative works under GPL-3.** ✅ — this fork is GPL-3.

## Original mod

- **Name:** Once Upon a Town
- **Author:** TheGoldenWorld (DawnOfTimeMC)
- **Repository:** [github.com/DawnOfTimeMC/onceuponatown](https://github.com/DawnOfTimeMC/onceuponatown)
- **License:** GPL-3.0
- **CurseForge project ID:** 1545001 (this is the **upstream's** project — the fork will not republish under this ID)

The original Discord and other upstream-distribution channels belong to the upstream project and are not maintained by the fork.

## What has diverged from upstream

| Area | Upstream | Fork |
|---|---|---|
| Mod loader | Forge 1.20.1 + Fabric 1.20.1 (multiloader) | NeoForge 1.21.1 (single target, planned) |
| Display name | "Once Upon a Town" | "Burg" |
| Branding | Original badge, Discord, etc. | Rebranded, original credits retained |
| Philosophy emphasis | Mod feature breadth | "Player as helper, not leader" (see `docs/PHILOSOPHY.md`) |
| Build setup | `legacyforge` + `fabric-loom` multiloader | NeoForge Gradle plugin (planned) |

Code-level changes relative to upstream are visible in the git log; key future changes are tracked in the [issues list](https://github.com/lowern1ght/burg/issues).

## No upstream pull requests

This fork does **not** currently send pull requests upstream. The two projects have diverged in philosophy (see `docs/PHILOSOPHY.md`) and target different Minecraft versions. If you find a bug that affects both, please report it in both repositories.

## License compatibility

GPL-3 is a copyleft license. Any derivative work — including mods that bundle, depend on, or dynamically link with Burg — must also be GPL-3 compatible. Pack authors who include Burg in a modpack must ensure their pack's license terms are compatible.

## Contact

For issues specific to this fork: [github.com/lowern1ght/burg/issues](https://github.com/lowern1ght/burg/issues).
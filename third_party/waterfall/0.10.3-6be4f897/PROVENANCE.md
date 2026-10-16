# Waterfall 0.10.3 engine-effect provenance

## Identity

- Canonical project URL: https://github.com/post-kerbin-mining-corporation/Waterfall
- Authors/copyright holders: Waterfall contributors; the radiant-drive config names AstroX; the engine configs are distributed with KSP Interstellar Extended.
- Upstream version and commit: installed Waterfall `0.10.3`; inspected source commit `6be4f897ad86577c6ce2c17e6da0848b14d5250a`.
- Game/mod version: Kerbal Space Program 1.12.5; Waterfall supports KSP 1.8.1 through 1.12.x.
- Retrieved on: 2026-08-15 from the user's local KSP installation and a read-only source clone.
- Upstream archive SHA-256: no single archive was inspected; every consumed file is hashed below and preserved under `upstream/`.

## Use in Wildfires

- Classification: adapted shaders/config integration and derived mesh/texture resources.
- Scope and purpose: the Waterfall antimatter radiant-drive and Daedalus v1/v2 effects used by their three one-block orbit test engines.
- Wildfires paths: all destinations are enumerated in `files.csv`.
- Upstream paths: the three preserved engine configs; `Additive Dynamic.shader`, `Billboard Additive.shader`, `Billboard Directional.shader` and `WaterfallMaterial.cs`; Waterfall's two `.mu` meshes and all consumed FX textures.
- Modifications: ShaderLab/Cg expressions were translated to Forge-managed GLSL 150; material values and Unity Hermite controller tangents were retained; Unity coordinates were mapped to Minecraft's fixed south exhaust; DDS textures were converted to PNG without pixel editing; `.mu` geometry was decoded to compact runtime meshes. Daedalus v1 uniformly maps its widest 8-unit DetonationPoint core to 2.5 blocks (`2.5 / 8` radial and axial unit), retaining three DetonationPoint, two Outer-Expansion, four Inner-Expansion and four Directional Flare layers. This yields a 2.5-block widest core, 4.722217-block main Outer start, 147.353008-block geometric endpoint diameter and 334.375-block length; source `FadeOut=0.671389401` softens the visible edge. All forty v1 IgnitionBeam effects are excluded at the user's request, and its Unity point-light settings are recorded but not replaced by persistent Minecraft block light. Daedalus v1 has no presentation brightness multiplier. Daedalus v2 uses radial unit `2.5 / 6` so its widest `scale=3` core is exactly 2.5 blocks across, while a separate `128 / 658` axial unit keeps the complete effect within the requested 128-block length; a strictly uniform 2.5-block-core conversion would make the 300-unit core 125 blocks and the complete endpoint 274.1667 blocks. Its duplicated ignition-beam effects are intentionally excluded until a final engine model exists. Daedalus v2's eleven dynamic plume layers share one 2.5 presentation-brightness multiplier, retaining all Waterfall relative brightness ratios; its two billboard flares are excluded from that multiplier to avoid the previously observed white-block saturation. Unity `_Time.x` is supplied as elapsed seconds divided by 20, default billboard direction remains local `+Z`, and object-space camera direction includes all three source scale axes. GLSL zero-length normalization and zero direction exponents are made finite where Cg/Unity did not produce the Forge driver artifacts seen during testing. Minecraft's blend JSON uses the parser-valid spelling `one_minus_src_color` for Waterfall's `One / OneMinusSrcColor`; the superficially similar `1-src-color` parses to OpenGL enum `-1` and is invalid. Waterfall's per-material constant `Random.Range(-1f, 1f)` seed behavior is retained only where configs set `randomizeSeed = True`. The radiant-drive random controller remains per render update, and only its OuterBeam receives the user-requested `1.35` brightness multiplier. The previously prototyped antimatter-catalyzed engine and its exclusive resources remain removed.

## Rights and notices

- Project-level license: CC BY-NC-SA 4.0, stated by upstream `LICENSE.md` and confirmed by the user for this use.
- File/asset-level exceptions: none were supplied for the consumed shaders, meshes, textures or config.
- Required attribution: credit Waterfall and KSP Interstellar Extended contributors, link the license, identify modifications, prohibit commercial use, and retain CC BY-NC-SA 4.0 on adaptations.
- License text included at: `upstream/LICENSE.md`; canonical legal terms are linked at https://creativecommons.org/licenses/by-nc-sa/4.0/.
- Release status: this partition is non-commercial and share-alike. It must not be represented as Wildfires ARR or used in a commercial distribution.

## Preserved upstream material

- `upstream/LICENSE.md`: `a5ac524dd78927985e9a862e8cf5e3c3e5373710b7125379505169d222cc28c1`
- `upstream/README.md`: `6f792dcbe6d009b27d67d593e5c930d71db800e45d40f40d454569534964e95f`
- `upstream/Source/ShaderLab/Additive Dynamic.shader`: `4f6802ebd8b1f45127075d60828952341aa7c2ef4c31ad3e1e6a03632eac549e`
- `upstream/Source/ShaderLab/Billboard Additive.shader`: `24f81a414d44a9be2f11211824ac65dd91473748f9e94c87bde71ca315913d83`
- `upstream/Source/ShaderLab/Billboard Directional.shader`: `7f9e3dd558a2bf9912fe246fda61365e564446c54ff6470e23d595593506a3a9`
- `upstream/Source/Waterfall/Effects/WaterfallMaterial.cs`: `e099d15767c19288af57ce1e1ff3f6eb5fa77c3a045a21ad3f41fafc64517ade`
- `upstream/GameData/Waterfall/Versioning/Waterfall.version`: `d6c4ae9b306d4d5bb918a42021787f5db43cb54147c9f66747e8f35eb9b49b06`
- `upstream/GameData/Waterfall/FX/fx-cylinder.mu`: `10b474ddbbd1edbd7dc84cfe358ccc1514f0ef63d0e76c47a7d96ca1cf42a8c9`
- `upstream/GameData/Waterfall/FX/fx-billboard-generic-1.mu`: `2a80a464b9f1befe772c6f289ee6d42e6fd734c8bd1c83725a1c7a0ff36cd744`
- `upstream/GameData/Waterfall/FX/fx-noise-5.dds`: `ba0716768abf07adc9a76776735c94ba00537ce7bccb89ebf36864f4c795c48a`
- `upstream/GameData/Waterfall/FX/fx_flarelamp-1.dds`: `88767c5613d8c2d9d5f5157d1e3d973fd107babb15c9f761ab2f05c1d177f92a`
- `upstream/GameData/WarpPlugin/Parts/Engines/AntimatterRadiantDrive/AntimatterEngine.cfg`: `c82322fd1831e657ec7bc87374cf94e1017380ba7a5093fdeefc5ca990b8f57`
- `upstream/GameData/WarpPlugin/Parts/Engines/Deadalus2/Daedalus.cfg`: `785118b21e1ff8159c7b7a19f7466e4d04219eac4ef50eacbc0735350432b45e`
- `upstream/GameData/Waterfall/FX/fx-ion-noise.png`: `6a70d955bf9e648a27a1d9c1ded0735d5ff79af74bcceb458f1af852ede1a301`
- `upstream/GameData/Waterfall/FX/fx-noise-4.dds`: `f00d287d48ef000260aea3c6bb44d650cce8845080fc99414aab8584418efb1b`
- `upstream/GameData/Waterfall/FX/fx_flareglow-1.dds`: `fb5e92005e9b1b00d16e2ece5bf19a67a4980f5646b129bbff799ad052bf3d61`
- `upstream/GameData/Waterfall/FX/fx_flarelens01.dds`: `08caf1a9d276dd2374904df92797f267afcf1b7a1b2e6158e4cdb4948b352155`
- `upstream/GameData/Waterfall/FX/fx_flarelens02.dds`: `9ed873e6d9fd708bbd9bdf1c432cadea2807270a9ddcf5e52dcfd7de66a207e9`
- `upstream/GameData/WarpPlugin/Parts/Engines/Daedalus/Deadalus.cfg`: `715050892fe1c8f91a7a70c3bb1bedb3729372f5e4945714717a1de27c8ba5ae`

Byte-fidelity correction (2026-10-16): the six repository-derived snapshot files above were originally preserved through Windows tooling, so they carried CRLF line endings and a UTF-8 BOM where upstream does not. They were re-fetched from `https://raw.githubusercontent.com/post-kerbin-mining-corporation/Waterfall/6be4f897ad86577c6ce2c17e6da0848b14d5250a/` and now match the pinned commit byte for byte; the hashes above are the upstream values. Two related defects were fixed at the same time: `upstream/README.md` had a one-character transcription error in its recorded hash, and `LICENSE.md`'s recorded hash described the converted copy rather than the upstream file.

The `GameData/**` entries (the two `.mu` meshes, the DDS/PNG textures, the three engine configurations and `Waterfall.version`) still come from the user's local Kerbal Space Program installation rather than from that repository, so they cannot be re-verified against a public revision. They are binary or CRLF-native KSP configuration files, and their recorded hashes describe exactly the bytes preserved here.

## Verification

- [x] `files.csv` covers every adapted or derived destination.
- [x] Preserved upstream file hashes were compared to the read-only sources.
- [x] Root acknowledgement and `THIRD_PARTY_NOTICES.md` are current.
- [x] Adapted Java and GLSL source files retain CC BY-NC-SA 4.0 headers.
- [ ] Public release compatibility remains subject to the non-commercial/share-alike partition and the repository's other recorded release blockers.

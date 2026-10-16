# Third-party notices

This file records third-party work incorporated into, adapted by, or materially consulted by Wildfires. Acknowledgement does not imply endorsement by the original authors. Upstream copyrights remain with their respective owners.

## License model

Wildfires is a multi-license distribution. The root [`LICENSE`](LICENSE) reserves all rights in original Wildfires code, assets, and documentation. Its scope is deliberately narrow — it covers only files that are original Wildfires work and are not listed in the license matrix below — and the boundary runs both ways: the open licenses of the third-party partitions do not extend to that original content, so reusing it still requires written permission. That also means the open-source status of the bundled partitions does not make the whole project open source. The root notice does not relicense third-party material. Every bundled third-party item remains governed by the license and copyright notice recorded below and in [`third_party/`](third_party/README.md).

The built JAR carries `LICENSE`, this notice, and the complete versioned `third_party/` evidence directory. Presence of those files preserves notices but does not by itself resolve an incompatible or ambiguous license. Items explicitly marked unresolved remain release blockers.

## Release status

No partition currently carries unresolved rights, so none is blocked for publication. Two were listed here earlier and both were resolved on 2026-10-16:

| Partition | Earlier blocker | Resolution |
| --- | --- | --- |
| TFC Caelum `1.2` | Which BSD variant governs, with no license text anywhere in the release | Project-owner decision: satisfy the stricter three-clause obligation set, which also satisfies BSD-2-Clause; the partition is identified as `LicenseRef-TFCCaelum-BSD` instead of a guessed standard identifier. See [the provenance record](third_party/tfc-caelum/1.2/PROVENANCE.md). |
| User-supplied nebula set `2026-08-24` | Licenses of the sources the supplied files reference | Reclassified as a known non-commercial / share-alike partition under Shadertoy's platform default: the `fl2Bzd` star routine is not compiled into our shader at all, and the adapted grading is attributed to sonicether's "Gargantua With HDR Bloom". See [the provenance record](third_party/nebula/user-supplied-2026-08-24/PROVENANCE.md). |

Both partitions are now closed on the rights side and on the paperwork side, and the Caelum row below has since been enumerated the same way. No partition currently has an open item in this file.

Three partitions are distributable only under the terms they carry, because their licenses are non-commercial and share-alike: the Mattenii aurora shader (CC BY-NC-SA 3.0), the Waterfall engine effects (CC BY-NC-SA 4.0), and the user-supplied nebula set (Shadertoy platform default, CC BY-NC-SA 3.0). Wildfires is distributed free of charge and is never sold, so the non-commercial condition is satisfied by the project's own distribution policy rather than by a per-file exemption. The share-alike and attribution terms remain in force: none of the three may be relicensed as All Rights Reserved, and no file under them may be relicensed to permit commercial use by anyone else.

| Material | Governing terms |
| --- | --- |
| Original Wildfires code, assets, and documentation | All Rights Reserved |
| Caelum-derived code/mechanisms | Caelum MIT notice, subject to deeper file-level sources |
| BSC5P-JSON-XYZ stellar catalog data | CC BY 4.0 |
| Mattenii-derived aurora shader | CC BY-NC-SA 3.0 |
| TFC Caelum-derived behavior and 31 PNG files | `LicenseRef-TFCCaelum-BSD` — BSD-family permissive terms as declared by the package; the clause variant is undetermined (`BSD 3` in the JAR, `BSD-2-Clause` on Modrinth, generic `BSD License` on CurseForge) and Wildfires satisfies the stricter three-clause reading. See the release decision in the provenance record |
| VS: Genesis-derived cubemap, mesh and shaders | Apache-2.0 |
| NTM: Space-derived orbit/capsule/navigation code, models and textures | LGPL-3.0-only |
| Waterfall/KSP Interstellar Extended engine-effect shaders, meshes, textures and configs | CC BY-NC-SA 4.0 |
| ArcaneVortex Starlink/cosmic/Black World/`star_sky` visual adaptation | ErChien ARR plus the user's project-specific modification/integration/redistribution authorization; tesseracts and attack-damage mechanisms excluded |
| User-supplied nebula shader set | CC BY-NC-SA 3.0 (Shadertoy platform default) — non-commercial and share-alike; `fl2Bzd` and sonicether attributions preserved. Not ARR |

## Caelum

- Project: [Caelum](https://github.com/z0phka/Caelum)
- Author: Nuparu00 / z0phka
- Referenced version: `1.20.1-2.0.0.0`
- Verified source revision: `59c03cc665cb91291b1b3d3a591304be702fbc05`
- Upstream license: MIT
- Use in Wildfires: sky and latitude design, stellar loading/rendering behavior, and related implementation guidance.
- Adapted material, enumerated in `files.csv`: the star data chain (`StarDataManager`, `StarTableLoader`), the star appearance and latitude-driven celestial rotation (`CelestialVisualRules`, `CelestialRenderer`), the client sky and fog time bridges (`CelestialClientLevelMixin`, `CelestialFogRendererMixin`) and the star option set in `CelestialConfig`. Each carries an MIT notice with its upstream class and modification summary.
- Verbatim material: `src/main/resources/assets/wildfires/stars/stars.json`; its deeper catalog provenance and CC BY 4.0 terms are recorded below.
- Deliberately not attributed to Caelum: the geometric moon phase (upstream's `MoonController` and its `DimensionType` mixin are not ported), the TFE/TFC-calendar latitude and year, the debug overlay, and everything above the star pass in `CelestialRenderer`.
- Preserved upstream documents: [`third_party/caelum/1.20.1-2.0.0.0/`](third_party/caelum/1.20.1-2.0.0.0/PROVENANCE.md)

Copyright (c) 2023 Nuparu00. The complete MIT notice is preserved with the upstream snapshot.

## TFC Caelum

- Project: [TFC Caelum on Modrinth](https://modrinth.com/mod/tfc-caelum) / [CurseForge](https://www.curseforge.com/minecraft/mc-mods/tfc-caelum)
- Author: Verph
- Referenced release: `TFCCaelum-1.20.1-1.2.jar`, Modrinth version `wSasbQOH`
- Use in Wildfires: TFC calendar integration concepts, planets and satellites, eclipses, blood moons, rainbows, aurora behavior, configuration semantics, and sky resources.
- Verbatim material: 31 PNG files under `src/main/resources/assets/wildfires/textures/sky/`; see the preserved file manifest.
- Adapted material: portions of the aurora shader and celestial behavior. The shader has a separate CC BY-NC-SA 3.0 attribution below.
- Preserved upstream metadata, project description and the CurseForge license field: [`third_party/tfc-caelum/1.2/`](third_party/tfc-caelum/1.2/PROVENANCE.md)

**Cleared for redistribution by the project owner's decision of 2026-10-16, taken under the stricter-variant policy.** Every declaration found is BSD-family, so redistribution and adaptation are permitted with notice; what remained undetermined is *which* variant governs, and therefore which text we must ship. The release JAR declares `BSD 3`, the matching Modrinth project/version declares `BSD-2-Clause`, and the CurseForge project page (ID `999031`) shows the generic `BSD License`, which names no variant. The release contains no license text, no README and no source link. The author appears to have stopped developing the project and is difficult to reach, so rather than leave the partition idle, Wildfires proceeds under the reading that satisfies both candidates at once: it complies with the BSD-3-Clause obligation set, which satisfied BSD-2-Clause as well. No standard SPDX BSD identifier is asserted. The partition is identified as `LicenseRef-TFCCaelum-BSD`, defined as "BSD-family permissive terms as declared by TFC Caelum `1.2`; the clause variant is undetermined; Wildfires satisfies the stricter three-clause interpretation".

Obligations in force: the upstream copyright notice and declaration travel with the JAR (see `upstream/mods.toml`); the files Wildfires adapted are identified; neither Verph's name nor any other contributor's name is used to endorse or promote Wildfires; and the standard BSD warranty disclaimer is carried. The 31 verbatim PNGs are held under the same package-level declaration, because the full-artifact scan found no per-file author or license marker and no separate asset terms.

Because the author's own license file does not exist, the two clauses both variants require are carried here in their canonical wording rather than reproduced from upstream:

> THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

> Neither the name of the copyright holder nor the names of its contributors may be used to endorse or promote products derived from this software without specific prior written permission.

The partition is closed: all fifteen adapted destinations (planet and satellite parameters, period/eclipse/supermoon/config semantics, the blood-moon spawn chain and its three mixins plus accessor, rainbow, aurora and the aurora shader) are enumerated in `files.csv` with destination hashes and each carries a `LicenseRef-TFCCaelum-BSD` header naming the upstream class it adapts and what Wildfires changed. The record also states what is deliberately *not* attributed to this partition — the three-dimensional orbit and eclipse geometry, the event machinery and the sky-ownership bridge are Wildfires work that replaced upstream's empirical approximations. If the author ever supplies the license text, quote it verbatim and drop the `LicenseRef`.

Do not invent an SPDX identifier, do not write a substitute license file, and do not publish a JAR that still contains this partition while the conflict is open.

The archived release was re-inspected in full on 2026-10-16 and is byte-identical to the copy recorded here. It contains no license, readme, notice, credits or authors file at all, and the only license text anywhere inside it is `license = "BSD 3"` in `META-INF/mods.toml`, so the complete text still has to come from the author. Note that the bundled aurora shader is outside this question: its own in-file notice names Mattenii and `CC BY-NC-SA 3.0`, and that is the license it is distributed under regardless of what the mod-level declaration says.

## BSC5P-JSON-XYZ stellar catalog

- Project: [BSC5P-JSON-XYZ](https://github.com/frostoven/BSC5P-JSON-XYZ)
- Creator: aggregate1166877 / Frostoven
- Material: generated catalog data, indirectly obtained through Caelum and distributed as `src/main/resources/assets/wildfires/stars/stars.json`
- License for generated catalogs: [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/)
- Upstream acknowledgements: the catalog is based on BSC5P/HEASARC data and uses SIMBAD and VizieR data; their terms and acknowledgements remain applicable as described by the upstream project.
- Preserved license and provenance: [`third_party/bsc5p-json-xyz/`](third_party/bsc5p-json-xyz/PROVENANCE.md)

`BSC5P-JSON-XYZ Catalog Data (c) by aggregate1166877` is licensed under CC BY 4.0. Wildfires has renamed/packaged the Caelum table but has not altered its bytes; SHA-256 is `46f2b21d8205562d5918322fbf9217c51454d2fddf05c77223e9053584abc227`.

## Mattenii aurora shader

- Original work: [Aurora shader on Shadertoy](https://www.shadertoy.com/view/MsjfRG)
- Creator: Mattenii
- License: [CC BY-NC-SA 3.0](https://creativecommons.org/licenses/by-nc-sa/3.0/)
- Path in Wildfires: `src/main/resources/assets/wildfires/shaders/core/aurora.fsh`
- Changes: adapted from the TFC Caelum split shader to Minecraft's Forge-managed GLSL 150 pipeline and the `wildfires` namespace; further rendering fixes are documented in the source history.

The attribution and license marker must remain in the shader source. `aurora.fsh` is a non-ARR file: it is offered under CC BY-NC-SA 3.0, so the adaptation must keep the Mattenii and Shadertoy attribution, must not be used commercially, and any further derivative of that file must stay under the same license.

## VS: Genesis

- Project: [VS: Genesis](https://github.com/jamesgreen26/genesis)
- Referenced snapshot/version: `1.20.1-0.7.3`; supplied local tree had no Git metadata
- Copyright notice in upstream Apache license: Copyright 2025 jamesgreen26, JSJBDEV, Verquinox, jcm236
- License: Apache License 2.0
- Use in Wildfires: adapted 3x2 square-planet face contract, static cube mesh, center-star directional surface shader, ray-box atmosphere shader, data-bound surface-ascent cube presentation and authoritative-transfer/client-transition separation; verbatim upstream Earth and Moon cubemaps.
- Wildfires partition: `src/main/java/first/wildfires/thirdparty/genesisadapt/` and `src/main/resources/assets/wildfires/shaders/core/genesis_planet_*`.
- Verbatim texture partition: `src/main/resources/assets/wildfires/textures/third_party/vs_genesis/overworld.png` and `moon.png`.
- Runtime dependencies intentionally not imported: Valkyrien Skies 2, Lodestone, Genesis and physical ship interfaces.
- Preserved source evidence and exact file mapping: [`third_party/vs-genesis/1.20.1-0.7.3-local-snapshot/`](third_party/vs-genesis/1.20.1-0.7.3-local-snapshot/PROVENANCE.md)

Each modified source/shader carries an Apache-2.0 notice and change description. The complete upstream Apache license is distributed with the evidence snapshot.

## NTM: Space

- Project: [NTM: Space](https://github.com/JameH2/Hbm-s-Nuclear-Tech-GIT)
- Referenced snapshot/version: `1.0.27_X5778`; supplied local tree had no Git metadata
- Copyright holders: NTM: Space contributors; the complete contributor and texture/model credit list is preserved in the upstream `gradle.properties` snapshot
- License: GNU Lesser General Public License version 3, stated in the upstream README and accompanied by GPLv3 `LICENSE` plus `LICENSE.LESSER`
- Adapted code/resources: orbit visuals and illumination, including the underfoot-body reveal, surface-atmosphere fade and strict rotated outer-cube departure boundary; reusable-pod item deployment, player-only direct seating and launch-effect gates, launch/landing/bottom docking/passenger transfer and stable recovery; synchronized door/airbrake/leg/thruster animation, particles, and a shared pod/passenger attitude that keeps the mounted body inside the pitched hull; Forge OBJ renderers for the docking core and pod; data-bound existing-dimension round trips; the exact one-root-plus-forty-nine-proxy `5x5x2` docking-core volume, top-centre boarding/tape/dismount interaction and look overlay; and the station-ID tape's NTM navigation-drive contract, with persistent UUIDs replacing orbit-grid coordinates. The capsule uses a real internal one-tape slot, NTM-style pre-ignition door close, multiple reservable docking cores, and the upstream ignition/light-flight/stage OGG sounds. The permission-2 tape command uses the same canonical UUID writer rather than accepting arbitrary item NBT.
- Adapted models/bindings: upstream `docking_port.obj` and `rp_drop_pod.obj` with Forge material selectors, plus Forge MTL and model JSON bindings.
- Verbatim textures: `night.png`, `kerbol.png`, `sunspike.png`, `planet.png`, `docking_port.png`, `rp_drop_pod.png`, the upstream `votv_f.png` bytes redistributed as `station_id_tape.png`, and `particle_base.png` used by the NTM six-quad shock smoke.
- Preserved source evidence, corresponding-source/relinking statement and exact hashes: [`third_party/ntm-space/1.0.27_X5778-local-snapshot/`](third_party/ntm-space/1.0.27_X5778-local-snapshot/PROVENANCE.md)

The root ARR license expressly excludes this LGPL partition. Its modified Java source, exact PNGs, build scripts, GPL/LGPL texts and attribution records are provided with the source distribution and evidence is included in the JAR. Wildfires does not claim authorship of the NTM textures; the inspected upstream snapshot did not provide per-PNG author or license exceptions, so the project-level LGPL claim and complete contributor credits are retained without narrowing them.

Every adapted Java destination in this partition carries the `LGPL-3.0-only` notice naming the upstream class it adapts and what Wildfires changed. The six station-core classes (`StationCoreBlock`, `StationCoreBlockEntity`, `StationCoreService`, `StationDockCoreItem`, `StationStructureBlock`, `StationServerEvents`) had been adapted without that notice; it was added on 2026-10-16, and the two stale destination hashes in `files.csv` were recomputed in the same change.

## Waterfall engine effects

- Projects: [Waterfall](https://github.com/post-kerbin-mining-corporation/Waterfall) and KSP Interstellar Extended engine configurations
- Authors/copyright holders: Waterfall contributors; AstroX and KSP Interstellar Extended contributors for the engine configs
- Referenced version/commit: Waterfall `0.10.3`, source commit `6be4f897ad86577c6ce2c17e6da0848b14d5250a`
- License: [CC BY-NC-SA 4.0](https://creativecommons.org/licenses/by-nc-sa/4.0/)
- Adapted material: Additive Dynamic, Billboard Additive and Billboard Directional shaders plus the antimatter radiant-drive and Daedalus v1/v2 material/controller configurations, translated to Forge GLSL 150. Daedalus v1 is uniformly scaled by `2.5 / 8`, giving a 2.5-block widest core, 147.353-block geometric endpoint diameter and 334.375-block length; Daedalus v2 keeps its 2.5-block widest core and independent axial compression to a 128-block endpoint. Both Daedalus ignition-beam systems are intentionally excluded until final engine models exist.
- Derived resources: `fx-cylinder.mesh`, `fx-billboard-generic-1.mesh` and the PNG files under `textures/effect/`, decoded or format-converted from the preserved Waterfall assets.
- Evidence, source snapshots, changes and exact hashes: [`third_party/waterfall/0.10.3-6be4f897/`](third_party/waterfall/0.10.3-6be4f897/PROVENANCE.md)

This non-commercial share-alike partition is excluded from the root ARR claim. Redistribution and adaptations must retain attribution, the CC BY-NC-SA 4.0 terms and the recorded indication of changes.

## ArcaneVortex

- Project: ArcaneVortex
- Author/copyright holder: ErChien
- Referenced release: `0.6.8`, Minecraft 1.20.1 / Forge 47
- Public metadata license: All Rights Reserved
- Project-specific authorization: on 2026-08-23 the user explicitly confirmed authorization to modify, integrate and redistribute ArcaneVortex shaders, rendering code and visual implementation. Attack-damage mechanisms are explicitly outside that authorization.
- Adapted material: Forge 1.20.1 port of Sky Ripper's real-position Starlink emission cadence, random drift/roll/growth, 400-particle radius-10-to-26 first-stage shell, 6-block neighbor search, maximum degree three, 15-tick refresh and per-frame camera-facing connection quads. Wildfires uses a brighter deep-blue constellation palette, full-bright nodes, additive node/line bloom and a 120-tick stable plus 30-tick fade impact variant with user-specified narrow pulse flicker. The ten animated cosmic PNG sprites and `.mcmeta` files are redistributed byte-for-byte; `cosmic_0` remains on the incoming core and is also sampled through an additive ParticleEngine batch for homing cross-star bodies. That batch adapts the render organization of ArcaneVortex `FragmentParticle/FragmentParticleRenderTypes`; its blue palette, three-tick real-segment interpolation, hover jitter and projectile binding are Wildfires-owned. Hover shedding, dense homing trails, homing-star collision and collapsed-shard endpoints share one shrinking unlinked blue mote type. Black World vertex/fragment programs remain byte-for-byte; Wildfires adapts registration while restoring the source 60-tick full-strength envelope, absolute projectile-position binding and entity-render-equivalent inverse view at `AFTER_ENTITIES`. The exact upstream `star_sky.json/.vsh/.fsh` evidence is preserved; forty polygon windows use thinner blue outlines, stay planar and world-space, grow to their original linear size over each shard's own explosive ease-out, take the same duration again to reach double size, and cannot begin collapsing before 120 ticks. They continue with low residual drift/spin after explosive deceleration, while their opaque interiors use camera direction and camera-position parallax without autonomous time animation. Radius-60/base-5 shake remains distance attenuated and follows the source START-tick duration update, cubic fade equation and per-camera-event `new Random()` yaw/pitch sampling exactly. The single impact-frame 64-star volley, second-second target gate, distance-weighted random target choice, sixth-second untargeted expiry, curved light ribbons, additive bloom, tracking/collision and independent 200/20 damage are Wildfires-owned.
- Excluded material: both Sky Ripper arrow/impact tesseracts, bow item/UI, damage values and algorithms, range attacks, piercing, knockback, lightning, true damage, Shockwave, BlackHole and entity-hit attack behavior. Previously preserved Nebula Bow files remain immutable historical evidence but no longer map to runtime code.
- Preserved release evidence, exact hashes and destination mapping: [`third_party/arcanevortex/0.6.8/`](third_party/arcanevortex/0.6.8/PROVENANCE.md)

The inspected release did not contain a standalone license text, README or canonical project URL. Its ARR metadata and the user's explicit project-specific permission statement are retained together; any later written authorization should be archived as additional immutable evidence.

## User-supplied nebula shader set

The 2026-08-24 local set is adapted into the impact-frame Galaxy Hymn point-to-nebula volume. Its Forge GLSL 150 implementation directly maps the active `1.txt` cloud-strength, reddening/saturation and `0.03R` march plus the active `5.txt` grading sequence onto a world-anchored raymarch. Shadertoy history feedback is replaced with deterministic first-step jitter; source absorption alpha stays internal. The source's five-layer star field and opaque black backdrop are excluded from the runtime shader at source-code level: neither `stars()`, its hash helper nor a background call is compiled into the Minecraft nebula program. Each raymarch step separately accumulates transported cloud emission before final composition; only that cloud-body accumulator can create premultiplied alpha, while an empty ray remains exactly transparent and exposes the existing world framebuffer. There is no render-then-color-key stage. The premultiplied cloud body and additive highlight are registered as separate `ShaderInstance` configurations whose JSON files explicitly own `ONE/ONE_MINUS_SRC_ALPHA` and `SRC_ALPHA/ONE` blending respectively; this prevents Minecraft's shader application from replacing the intended composite with its default opaque state. Minecraft-local presentation now applies a 2.0 opacity gain capped at 0.84 only to visible cloud-derived coverage and a bounded 1.35 post-grade saturation boost; source absorption alpha remains excluded. The pass is dispatched at Forge `AFTER_WEATHER`, after vanilla clouds and precipitation but before hand/GUI rendering. The `2/3/4.txt` downsample/Gaussian bloom chain is preserved in the source record but is not claimed as active because `5.txt` comments out its `GetBloom` contribution; the spell's independent bloom request is a separate thresholded additive pass.

Attribution and terms: the cloud equations come from the supplied `1.txt`, and the tone mapping and grading come from `5.txt`, which states that Buffer B/C/D and the image pass are sonicether's “Gargantua With HDR Bloom”. The `stars()` routine inside `1.txt` is credited there to Shadertoy `fl2Bzd`; that routine, its hash helper and the opaque backdrop are not compiled into this program, so the credit is preserved rather than relied on. The set is treated as carrying Shadertoy's platform default license (CC BY-NC-SA 3.0): its non-commercial condition is satisfied by the project's free-distribution policy, while the attribution and share-alike terms travel with the adapted files, which are not part of the ARR partition. The supplied files themselves are preserved as an immutable snapshot under `third_party/`. Exact hashes and mappings are in [`third_party/nebula/user-supplied-2026-08-24/`](third_party/nebula/user-supplied-2026-08-24/PROVENANCE.md).

## Dependencies and APIs

Wildfires also depends on or integrates with Minecraft, Forge, TerraFirmaCraft, TerraFirmaEarth, Create and other projects. Dependency status alone does not mean their code or assets are redistributed under Wildfires' license. Any future copied or adapted material from those projects must be added here and to `third_party/` before it enters the repository.

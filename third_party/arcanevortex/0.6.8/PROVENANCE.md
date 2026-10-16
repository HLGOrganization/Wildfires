# ArcaneVortex 0.6.8 visual-rendering provenance

## Identity

- Canonical project URL: not included in the inspected release and not supplied with the local authorized copy.
- Author/copyright holder: ErChien, as declared by `META-INF/mods.toml` and `META-INF/MANIFEST.MF`.
- Upstream version: `0.6.8`.
- Minecraft/mod-loader version: Minecraft 1.20.1 / Forge 47.
- Release/file ID: local user-supplied expanded release at `E:\wildfirecore\ArcaneVortex [Alpha]-0.6.8-1.20.1-Forge`.
- Retrieved on: 2026-08-23.
- Upstream archive SHA-256: no single archive was inspected; every consumed class, shader and metadata file is preserved and hashed below.
- Snapshot filename note: preserved Java bytecode has the suffix `.class.bin` inside Wildfires so ModLauncher does not treat evidence under the version directory `0.6.8` as executable module packages. Only the filename suffix changed; the bytes and recorded SHA-256 values are identical to the upstream `.class` files.

## Use in Wildfires

- Classification: `adapted` visual/rendering implementation; preserved upstream evidence is `verbatim` and is not executed by Wildfires.
- Scope and purpose: the active adaptation ports Sky Ripper's `starlink_particle` visual chain plus its Black World impact pass and distance-scaled random camera impulse. The incoming core emits Starlinks only along its real pre-move trajectory, and impact sends the source-style 400-Starlink radius-10-to-26 first-stage constellation shell plus one Wildfires-owned 64-projectile-star volley and forty planar space shards in the same hit frame. All 64 stars become target-eligible at tick 40; each independently samples legal enemies with a positive inverse-distance weight, so nearer targets are likelier without forcing the nearest coordinate. A star with no target remains hovering and retries until tick 120, then uses the same blue spark burst as collision and disappears. Wildfires recolors linked sprites/quads to a brighter deep-blue palette, forces node sprites to full light and renders node/connection batches as additive, non-depth-writing bloom so daylight does not wash them out. Homing projectile bodies adapt `FragmentParticle/FragmentParticleRenderTypes` only for their ParticleEngine GPU-batch structure, while using the authorized transparent `cosmic_0` cross sprite and Wildfires-owned lifetime, blue palette, real previous-to-current tick interpolation, slow pulse, hover noise, block shatter, collision-box homing and independent 20 damage; their real-position curved ribbon remains separate. Hover shedding, dense real-segment homing trails, block/entity collision, sixth-second untargeted expiry and every collapsed shard endpoint share the same shrinking unlinked blue mote particle. The former center six-point afterimage is removed. The triangular/quadrilateral windows adapt the authorized `star_sky` volume algorithm; geometry stays world-space while the opaque interior responds to camera direction and camera-position parallax, with no autonomous time animation. Forty-window population, maximum double linear size, thinner blue outline, planar morphing, random orientation, varied explosive speed, smoothly ramped residual drift/spin and moving collapse endpoints are Wildfires additions. Each window reaches its original linear size only when its own 36-to-54-tick explosive speed reaches zero, takes the same duration again to reach double size, and cannot begin collapsing before 120 ticks. The single hit event uses the source Black World 60-tick full-strength envelope and one radius-60/base-5 camera impulse; START-tick duration advancement, the source cubic fade equation and per-camera-event `new Random()` yaw/pitch sampling are restored without seed stabilization or frame interpolation. Core explosion damage remains Wildfires' independent 200-point contract.
- Wildfires paths: every adapted destination is enumerated in `files.csv`.
- Upstream paths for the active adaptation: `SkyRipper`, `SkyRipperArrow`, `SkyRipperArrowDeadEffect0`, `SkyRipperArrowDeadEffect0Renderer`, `ScreenShakeHelper`, `ModParticles`, `StarlinkParticle`, `StarlinkParticleRenderTypes`, `FragmentParticle`, `FragmentParticleRenderTypes`, `assets/arcanevortex/shaders/core/black_world.*`, `assets/arcanevortex/shaders/core/star_sky.*`, `assets/arcanevortex/particles/starlink_particle.json` and the ten `assets/arcanevortex/textures/particle/star/cosmic_*.png` sprites with their `.mcmeta` animation metadata.
- Explicit visual exclusions: `SkyRipperArrowRenderer` and `SkyRipperArrowDeadEffect1Renderer` tesseracts remain excluded. Shockwave and BlackHole entities, bow item/UI and all upstream attack mechanics also remain excluded. The previously inspected Nebula Bow ribbon, layered cube, ring, spirals, Mobius surfaces, octahedron and `cosmic_neo` shader are retained below only as immutable historical evidence of the rejected baseline and no longer map to runtime Wildfires files.
- Modifications: Mojang-obfuscated Java bytecode was inspected with `javap` and CFR 0.152. Names, mappings, registry wiring and namespace were ported to Wildfires/Forge 1.20.1; the Starlink lifetime, drift/roll/growth, connection search, camera-facing connection quad, core emission cadence and 400-star first-stage distribution remain aligned with the source, while their color/opacity is deliberately raised, node/line blending is unified as additive bloom, nodes are full-bright, and an unlinked small-spark mode was added. `GalaxyHymnGpuStarParticle` adapts only FragmentParticle's custom ParticleEngine render-batch organization; its cross sprite, blue color, three-tick segment-interpolated sampling, pulse and projectile attachment are specific to Wildfires. The Black World vertex/fragment programs remain byte-identical; its JSON changes only the namespace/program name and omits declarations optimized out by GLSL. After bytecode re-audit, the client pass restores the source renderer's full 0..1 envelope, absolute `projectilePos`, entity-translation-cancelled inverse ModelView and entity-adjacent `AFTER_ENTITIES` submission. The original same-process shake map is replaced only at the transport boundary by the existing dedicated client packet; local START tick, pause/player gate, duration/tickCounter advancement, cubic fade and per-camera-event RANDOM sampling now match `ScreenShakeHelper` without the rejected stabilization layer. The one complete impact packet simultaneously triggers the dark pass, shake, space shards and nebula. The `star_sky` iteration/volume field is adapted to opaque dynamic triangle/quad meshes: Java fixes camera-relative vertices in the event world-view matrix; the fragment program uses projected screen rays, camera yaw/pitch and camera-position volume offset so the interior behaves like an End portal without autonomous time animation. Every polygon remains independently planar and all movement/shape timing is new. No upstream bow item, attack, damage, piercing, knockback, lightning, true-damage, range-attack, Shockwave, BlackHole or target-hit algorithm is used.

## Rights and notices

- Project-level license and source of that claim: `All Rights Reserved`, declared by the preserved `META-INF/mods.toml`.
- Project-specific permission: the user explicitly confirmed on 2026-08-23 that they hold authorization to modify, integrate and redistribute ArcaneVortex shaders, rendering code and visual implementation. The user explicitly excluded attack-damage mechanisms from that authorization.
- File/asset-level exceptions: none were included in the inspected release.
- Required attribution or notices: retain credit to ArcaneVortex 0.6.8 and ErChien, identify the visual adaptation, and keep the authorization boundary excluding damage mechanisms.
- Redistribution/modification conditions: governed by the user's project-specific authorization rather than the upstream public ARR declaration. The original signed authorization text or other durable proof was not present in the inspected release; the repository records the user's explicit confirmation and should archive any later-supplied document without replacing this snapshot.
- License text included at: no standalone LICENSE file was included in the inspected release; the ARR declaration is preserved in `upstream/META-INF/mods.toml`.
- Release status: permitted for the project-specific visual use described above according to the user's explicit authorization. Damage-mechanism adaptation remains prohibited.

## Preserved upstream material

- `upstream/META-INF/mods.toml`: `3da77dde153239b4f614fc76385448ab36c7c4e19080eab9a3fbed809025c792`
- `upstream/META-INF/MANIFEST.MF`: `655c0d13c6e9efefa2b99b9358a2941c51971e8656ec9f82eaa47b31d4a81491`
- `upstream/pack.mcmeta`: `9ab540623c7d5307edad2671c1f1bf4168b38662a81e5a731dd36b7056c3b64e`
- `upstream/classes/com/erchien/arcanevortex/Content/Item/Bow/NebulaBow/NebulaBow.class.bin`: `c2dfda81795cbaa6cc388b3ca38cfc870fd506038216f583f3381f75e4b7243b`
- `upstream/classes/com/erchien/arcanevortex/Content/Item/Bow/NebulaBow/NebulaBowArrow.class.bin`: `a975c99bbf66aa260b9c349ccacb5c2d9bc0669f00235fbb08ef37251d618f07`
- `upstream/classes/com/erchien/arcanevortex/Content/Item/Bow/NebulaBow/NebulaBowArrowRenderer.class.bin`: `2082e9f5f6da1f73dc8e3212242753402d5ea4565e27e6dc65af6e5c1aa46e91`
- `upstream/classes/com/erchien/arcanevortex/Content/Item/Bow/NebulaBow/NebulaBowArrowDeadEffect0.class.bin`: `2d00529e96cc72620ff3302a22a19adfdcb68b098a380493d784f69d708a3c61`
- `upstream/classes/com/erchien/arcanevortex/Content/Item/Bow/NebulaBow/NebulaBowArrowDeadEffect0Renderer.class.bin`: `d92941fb6afab2be85d4216bf5c02022324959458e30585b8793999812bfa20f`
- `upstream/classes/com/erchien/arcanevortex/Content/Item/Bow/NebulaBow/NebulaBowArrowDeadEffect1.class.bin`: `bd1151549969ac9999ab91234fed722edb0db9d5c5416535ddeb9b68c9f5b707`
- `upstream/classes/com/erchien/arcanevortex/Content/Item/Bow/NebulaBow/NebulaBowArrowDeadEffect1Renderer.class.bin`: `a620da48a0b210edc4df3e3b1e97d5000563a870bddc7bcd07a5a4a4ddea77d3`
- `upstream/classes/com/erchien/arcanevortex/Content/ModRenderType.class.bin`: `eca39ae2b4cf78859515454bcff0f3672c513127846462608a5d8fd5bef7d406`
- `upstream/assets/arcanevortex/shaders/core/cosmic_neo.vsh`: `9a7befc2402a9dc3a4990cacb9867e97f384d01959ff75a88c98919081b852d2`
- `upstream/assets/arcanevortex/shaders/core/cosmic_neo.fsh`: `cc59a6e2c9190cf4ae9169e28e6ecb90fe20a3fec8bcb9d20c015144167bf4c2`
- `upstream/assets/arcanevortex/shaders/core/cosmic_neo.json`: `047adf8ded8ee8dff94eb0314dd067249685e9649f59fadd1b21dc1d75de3a1a`
- `upstream/classes/com/erchien/arcanevortex/Content/Item/Bow/SkyRipper/SkyRipper.class.bin`: `3655244022a881cc5dcd5ccef7d66b09771f319f48bc60b8c19c86af1f42e9c7`
- `upstream/classes/com/erchien/arcanevortex/Content/Item/Bow/SkyRipper/SkyRipperArrow.class.bin`: `4c50adf8ae7cfa413f2d82c05d9d04033967c102a207fae9a9e95201c679ecca`
- `upstream/classes/com/erchien/arcanevortex/Content/Item/Bow/SkyRipper/SkyRipperArrowDeadEffect0.class.bin`: `2cea8debb19b0db67cba5f3f26e9a0a98165f7d3bafd75a6da68df034ff238ad`
- `upstream/classes/com/erchien/arcanevortex/Content/Item/Bow/SkyRipper/SkyRipperArrowDeadEffect0Renderer.class.bin`: `0334c498077c858a6fd934f6460294f8eeb0065eb96f729c249c611db3e8562d`
- `upstream/classes/com/erchien/arcanevortex/Help/ScreenShakeHelper.class.bin`: `1df28d64f4d853175560be5296a6f3a608116ec173f25778f33c98ecb5fac7c3`
- `upstream/classes/com/erchien/arcanevortex/Help/ScreenShakeHelper$1.class.bin`: `0f6aecb5d92ef8f102a37fc33f407f29b63ff36c05a7d11fcabbf1c81265a89b`
- `upstream/classes/com/erchien/arcanevortex/Help/ScreenShakeHelper$ShakeData.class.bin`: `89957cf61de715f2c410847e8c3e2d99631aa6bed46c2d503722696ec1c25b66`
- `upstream/classes/com/erchien/arcanevortex/Help/ScreenShakeHelper$ShakeType.class.bin`: `8d09e9e2cd3a2da4fc63b0d7205928ec66f20beebd1e4305169950a311afc3a3`
- `upstream/classes/com/erchien/arcanevortex/Help/ScreenShakeHelper$ClientHandler.class.bin`: `b8d4955bf7702731d009d5345070c7e2dde707e9fd63c68c3a20a82ddaeca76c`
- `upstream/assets/arcanevortex/shaders/core/black_world.json`: `deee5472e542faab9dfa31ea1d2cb7fc19ec11d06f68a5751c20fbc8a037b88d`
- `upstream/assets/arcanevortex/shaders/core/black_world.vsh`: `42d2672d234619f0c4a59c2a42e418717de30e0fcd7a29cb9809594655023c86`
- `upstream/assets/arcanevortex/shaders/core/black_world.fsh`: `2b53e987608e3a2f871ffc9b388a91d2f7cea8e880813354e3e32b386b7dd6ed`
- `upstream/assets/arcanevortex/shaders/core/star_sky.json`: `8b6d490662b5bef30aa126cd4254e6d56553433df06fcc12f846d66540e83271`
- `upstream/assets/arcanevortex/shaders/core/star_sky.vsh`: `370c46845aeede77ad35405ebcc2372427da3a2026c8e76a28311eef9e1f22da`
- `upstream/assets/arcanevortex/shaders/core/star_sky.fsh`: `207c94d29b3258c64eea976dc305ab2c250fa162daee4dd2bda959cbe52ffb65`
- `upstream/classes/com/erchien/arcanevortex/Content/ModParticles.class.bin`: `a5f8f181051e7a890bbdf00b3e8e1286cd7fdd892f26fba41a341a0da7511f86`
- `upstream/classes/com/erchien/arcanevortex/Content/Particle/World/StarlinkParticle/StarlinkParticle.class.bin`: `2ac9c87e21713bfffc9b3c31660845f1f6ae60f2aed070da859bdd0b1363d4a0`
- `upstream/classes/com/erchien/arcanevortex/Content/Particle/World/StarlinkParticle/StarlinkParticleRenderTypes.class.bin`: `73cc10ed2396d7abf1ec103ec616630bd90eec6918487b81e150969838be03be`
- `upstream/classes/com/erchien/arcanevortex/Content/Particle/World/FragmentParticle/FragmentParticle.class.bin`: `6e0f268bdead6435881b3765960b159edd88f3685619c3735a24af32424b8af4`
- `upstream/classes/com/erchien/arcanevortex/Content/Particle/World/FragmentParticle/FragmentParticleRenderTypes.class.bin`: `fb21f19fab4f5e6dc5565904aea7fb34e31fa03b8d25b7e02fbc2b5c0cf372b5`
- `upstream/assets/arcanevortex/particles/starlink_particle.json`: `1d6d1b4d303ea75b47653d291f2f6175a29be98fce8473dcaf1836403b804e03`
- `upstream/assets/arcanevortex/textures/particle/star/cosmic_0.png`: `d31cee25652db7f9cc33b17eaa704f81c604342137817e177d77ab9dac74c7ff`
- `upstream/assets/arcanevortex/textures/particle/star/cosmic_0.png.mcmeta`: `d1a5f4fde6178b3efad3af9d0763c03c97227b1a4ce1539e1ffbe2c535a7f914`
- `upstream/assets/arcanevortex/textures/particle/star/cosmic_1.png`: `b93b798237b67339a0d753c71308195a838c7dd6bbbadd12bbeec0df9781c5b7`
- `upstream/assets/arcanevortex/textures/particle/star/cosmic_1.png.mcmeta`: `17e4a41daf8bf37a724176c28f16dd02b3289aca8c583295466ee1869231ebe1`
- `upstream/assets/arcanevortex/textures/particle/star/cosmic_2.png`: `f65b02ee0ee9c98ae787eb2cb87d4f8fa2b88b83ab05d3b49af7cd7d1050e633`
- `upstream/assets/arcanevortex/textures/particle/star/cosmic_2.png.mcmeta`: `d6d7204959cf67df88351d8bb7f5653cda39b868f81a2e4e3efeb007b606cc87`
- `upstream/assets/arcanevortex/textures/particle/star/cosmic_3.png`: `d053db8e0d1145ff3ba8046d30b2817abf7cebfdfb84e16f7c22657f2facbfdb`
- `upstream/assets/arcanevortex/textures/particle/star/cosmic_3.png.mcmeta`: `5fe6bf675a209759ccf4c71bdd4fd5ea776b978a3f49bcbe725e339c7b47d8d1`
- `upstream/assets/arcanevortex/textures/particle/star/cosmic_4.png`: `fd3bf78941a46aa33b32fbf4457ab1ffa7312f10bb8e566ea433e47953498f2e`
- `upstream/assets/arcanevortex/textures/particle/star/cosmic_4.png.mcmeta`: `1c8aed02a9357edf4e7eaa76ce3cb5d0af8705db0a2321765f11afc74689e334`
- `upstream/assets/arcanevortex/textures/particle/star/cosmic_5.png`: `7e3e1abbec28ae16a39297cd9356a8388e5ee45edd24fac17f9a4cafe094768b`
- `upstream/assets/arcanevortex/textures/particle/star/cosmic_5.png.mcmeta`: `ca05f0b3c603bd753455a1c9c1506fe736d5f6066e8c0fcf9682b70c27764134`
- `upstream/assets/arcanevortex/textures/particle/star/cosmic_6.png`: `8b632f18a0d3004e7ed731a6fcf971a6c7ec13d9991ce326fb2c8f6134281222`
- `upstream/assets/arcanevortex/textures/particle/star/cosmic_6.png.mcmeta`: `d947e6371a5bb46ab733722fac72f7aae2832fc94ecc8a1ab55c3e1471cbf4f4`
- `upstream/assets/arcanevortex/textures/particle/star/cosmic_7.png`: `9ba882f2f4edb5f66e1eee7d1ce44a1ffaf4302a079946707376df349a13678e`
- `upstream/assets/arcanevortex/textures/particle/star/cosmic_7.png.mcmeta`: `e9b8025fde1b09b0a4d2a38427b04f98f28a593045be6d177eb2b29164c872b0`
- `upstream/assets/arcanevortex/textures/particle/star/cosmic_8.png`: `de0e3bb5af4bb55cba11abaf214ce25137c19efa9889341194fa43be7cd2f900`
- `upstream/assets/arcanevortex/textures/particle/star/cosmic_8.png.mcmeta`: `a7a4c140322c27611036f7b7f4e2f3483bda6873babd072d4a862c8fba77e4d9`
- `upstream/assets/arcanevortex/textures/particle/star/cosmic_9.png`: `10bfd7907124e9432496c2cb2535b0090f3e5a2e7bacdce60770ce901a132d9e`
- `upstream/assets/arcanevortex/textures/particle/star/cosmic_9.png.mcmeta`: `e9b8025fde1b09b0a4d2a38427b04f98f28a593045be6d177eb2b29164c872b0`

The inspected release did not include an upstream README, standalone LICENSE, NOTICE, source link or project URL.

## Verification

- [x] `files.csv` lists every planned adapted destination.
- [x] Preserved upstream hashes were compared to the read-only local release.
- [x] Root acknowledgement and `THIRD_PARTY_NOTICES.md` identify the authorization boundary.
- [x] Every adapted source file now retains an ArcaneVortex credit and modification notice. All twenty verbatim sprite/animation files, two verbatim Black World programs and all three preserved `star_sky` source resources match their registered upstream hashes.
- [x] No ArcaneVortex damage mechanism is included in the adapted scope.

## Corrections

- `2026-10-16`: the verification line above previously asserted that adapted source files retained the credit and modification notice without a per-row check of `files.csv`. Two destinations — `src/main/java/first/wildfires/compats/irons_spellbooks/entity/GalaxyHymnHomingStar.java` and `src/main/java/first/wildfires/compats/irons_spellbooks/GalaxyHymnVisualMath.java` — were added on 2026-09-01, in the same commit as this record, without one; they had not been modified since. Both now carry the notice, and their destination hashes in `files.csv` were refreshed. Only comments were added: no adaptation scope, exclusion, authorization boundary, constant or behavior changed, and no verbatim asset was affected.

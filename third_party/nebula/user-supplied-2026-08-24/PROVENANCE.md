# User-supplied nebula shader set

## Identity

- Material: five Shadertoy-style GLSL text files (`1.txt`–`5.txt`) and one 165x87 PNG reference, supplied in `E:\wildfirecore\nebula`.
- Supplier and retrieval date: project user, 2026-08-24.
- Public project URL, package version, original README and standalone license: not supplied. The set was never published as a package, so there is no release archive or version identifier to pin.
- Immediate deeper sources, indicated in the files themselves: `1.txt` line 175 attributes its `stars()` routine to Shadertoy `https://www.shadertoy.com/view/fl2Bzd`; `5.txt` line 1 states that Buffer A holds the rendering and that Buffer B/C/D plus the image pass come from sonicether's “Gargantua With HDR Bloom”.
- Verified on: 2026-10-16 (snapshot preserved and re-hashed).

## Use in Wildfires

- Classification: `adapted` for the volume-noise, raymarch and tone-mapping code. The `fl2Bzd` star routine is **not** used, so that relationship is `inspiration` plus a preserved attribution, not an adaptation.
- Project-specific authorization: the user states that they hold authorization for the visual/shader material and expressly requested its integration into Wildfires. This authorization does not cover attack-damage mechanisms; none are used here.
- The supplied background star field was inspected but is excluded from the runtime adaptation.
- Wildfires changes: the current implementation directly adapts the active Buffer A cloud equations from `1.txt`: `SinNoise`, `S1/Sf/eff_dis`, all `strengthw0/w1/wa/0/1` terms, dark-orange/blue/cyan layering, reddening `0.6`, saturation `0.3` and `0.03R` marching. It also directly adapts the active tone-mapping/color-grading sequence from `5.txt`. Camera rays intersect a radius-14 world-anchored proxy and are transformed onto the supplied `normalize(-1,1,0.3)` target direction; `iChannel3` temporal feedback is replaced with deterministic first-step jitter because the localized Forge pass has no Shadertoy history buffer. The five-layer `stars()` routine, its hash helper and the opaque black backdrop are physically absent from the runtime shader rather than rendered and filtered afterward. During raymarch, each `cloudcolor` step separately accumulates transported cloud RGB into `cloudEmission`; only this structured cloud-body value can create Minecraft opacity. The supplied `sourceColor.a` remains internal to cloud absorption and reddening. The colored body uses premultiplied-alpha transparency as the spell's final, non-depth-writing world composite; an empty ray is exactly transparent and the existing Minecraft framebuffer is the background. Thresholded bloom remains additive. The base and glow passes share the adapted GLSL program but use two Forge shader JSON configurations and two `ShaderInstance` objects: base explicitly applies `ONE/ONE_MINUS_SRC_ALPHA`, while glow explicitly applies `SRC_ALPHA/ONE`. This is a Minecraft composition fix required because `ShaderInstance.apply()` otherwise replaces Java's transient blend state with the default opaque mode; it does not alter the supplied cloud equations. The Minecraft presentation layer dispatches this proxy at Forge `AFTER_WEATHER` so vanilla clouds/precipitation are behind it, applies a 2.0 gain with a 0.84 cap only to visible cloud-derived world opacity, and adds a bounded 1.35 saturation boost after the supplied grading. These changes do not restore the excluded backdrop or route `sourceColor.a` into world opacity. Files `2/3/4.txt` define the supplied downsample and Gaussian chain, but `5.txt` leaves `color += GetBloom(uv) * 0.08` commented out; Wildfires does not claim that chain is active. The proxy begins at 1.2% radius at the impact-frame center point, expands in 18 ticks, then fades after 82 ticks. The spell's server mechanics, 200/20 damage, targets and collisions are independent.

## Rights and notices

- Governing terms: Shadertoy's published default license for shader sources — a non-commercial, share-alike, attribution license (CC BY-NC-SA 3.0 in the platform's own wording) — applies to the two indicated sources unless their own pages state something different. The remaining supplied code carries no separate declaration.
- Evidence basis, stated plainly: this build machine could not fetch the platform pages (Shadertoy returns HTTP 403 to both `/terms` and the shader views, and the archive mirror was unreachable), and no license file or author document accompanied the supplied set. The classification therefore rests on (a) the platform default, (b) the project owner's statement that the material is clear to use, and (c) the in-file attributions. It is written as the strictest plausible reading, so it holds even if a page turns out to be more permissive.
- Obligations carried:
  1. **Attribution** — name sonicether's “Gargantua With HDR Bloom” for the adapted grading, and preserve the `fl2Bzd` URL for the routine we deliberately do not compile. Both appear in `THIRD_PARTY_NOTICES.md` and in the adapted source headers.
  2. **Non-commercial** — satisfied by the project policy recorded in the skill (`发布政策与许可判断前提`): Wildfires is never sold and never commercially distributed. The constraint still binds anyone who redistributes this partition.
  3. **Share-alike** — this partition is not ARR. The adapted files and anything derived from them stay under the same license and must not be relicensed to permit commercial use by others.
  4. **No warranty** — the platform's disclaimer position is passed through.
- Partition root: `src/main/java/first/wildfires/client/spell/GalaxyHymnNebulaShader.java`, `GalaxyHymnNebulaVisuals.java` and `src/main/resources/assets/wildfires/shaders/core/galaxy_hymn_nebula*`.
- ARR dependency direction: the root `LICENSE` (ARR) expressly does not cover this partition; nothing here is a dependency of the ARR files.
- Asset-level note: the PNG travelled with the set as a visual reference. It is preserved as evidence only and is not compiled into the runtime resource tree.
- JAR license paths: this record, `THIRD_PARTY_NOTICES.md` and the preserved `upstream/` snapshot inside `third_party/`.
- Release status: **cleared as a known non-commercial / share-alike partition.** It is no longer a rights-unknown release blocker.
- Reopen triggers: either indicated shader page turns out to carry a different explicit license (for example CC0 or MIT, which would *narrow* these obligations); the platform default changes; or Wildfires is ever distributed commercially, which this partition forbids.

## Preserved supplied material

- `upstream/1.txt`: 17239 bytes, SHA-256 `03644B24ECB0ED5BEC74AEB1E9ADF3E811EC3EE1805A3F01BBF658C9EAE778B9`
- `upstream/2.txt`: 4052 bytes, SHA-256 `D5EC5012AF562B015FFFAABFEA90E39E0D67603B2635E87271173D4A6B135177`
- `upstream/3.txt`: 1212 bytes, SHA-256 `36140A018230C96A95BE79318A5BAE0A9108DDDC75853E976A4CBE42F281B73B`
- `upstream/4.txt`: 1210 bytes, SHA-256 `629C4B235C56BD87975490E2B39940FA90FAA8A34BBEA6F1BBE1CBD8BC585D5B`
- `upstream/5.txt`: 3387 bytes, SHA-256 `9BAC9480871E672936335C5AA43F97F7E938708CAD555524D8236FCC1B7F0346`
- `upstream/下载 (3).png`: 42603 bytes, SHA-256 `1F3F57F38BF62948CEB8A5012BDC72217A3695966208964C9A69643CD69C4EC7`

The snapshot is byte-identical to the supplied files and is immutable evidence: if the set is ever revised, add a new dated directory instead of editing this one. The external path `E:\wildfirecore\nebula` is no longer the only locator.

## Verification

- [x] Every supplied file is preserved under `upstream/` and its SHA-256 matches the value recorded at supply time.
- [x] `files.csv` uses the standard `wildfires_path,upstream_path,sha256,classification` columns and lists every adapted destination.
- [x] The `fl2Bzd` routine really is absent from the runtime shader: `galaxy_hymn_nebula.fsh` contains `SinNoise` and `RandomStep` from the non-star code plus the `5.txt` grading, and no `stars()` or `hash43x`.
- [x] Root `README.md`, `THIRD_PARTY_NOTICES.md` and `third_party/README.md` describe this partition as a non-ARR non-commercial/share-alike partition rather than a release blocker.

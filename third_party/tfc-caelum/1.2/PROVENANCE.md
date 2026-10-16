# TFC Caelum provenance

## Identity

- Canonical project pages: https://modrinth.com/mod/tfc-caelum and https://www.curseforge.com/minecraft/mc-mods/tfc-caelum
- Author: Verph
- Release: `TFCCaelum-1.20.1-1.2.jar`
- Modrinth project/version: `WYvV2Kci` / `wSasbQOH`
- CurseForge project ID: `999031`
- Minecraft/loader: Minecraft 1.20.1 / Forge
- Published on Modrinth: 2024-08-28
- Verified on: 2026-08-11
- Published archive SHA-1: `114e10e497503f5ff4cb13850a8dec9bb9f4d625`
- Published archive SHA-512: `f1c7153fc07da59677183f9967ca16e189bcf38d961b03931f100b141e9a10ef684dd1d71a6cf8d27cd3d8ceef7670db25d3b6c398f835387ca8ee8936ea0303`

The inspected release is an extracted compiled JAR, not a source checkout. It contains no original README, LICENSE, COPYING, NOTICE, CREDITS or AUTHORS file and declares no source repository. `upstream/PROJECT_DESCRIPTION.md` preserves the Modrinth project body rather than pretending it was a release README. `upstream/mods.toml` is copied from the release JAR.

- `upstream/PROJECT_DESCRIPTION.md` SHA-256: `1e1eb2900f6b1f08e918627e1eaa42add6b031b0befff6ea178949f5d8bb4dfc`.
- `upstream/mods.toml` SHA-256: `23ca1f9589c3f2b1a8fcba128804c31f843e9d5cb3dab0d24655ce13f93aa167`; byte-identical to the inspected release file.
- `upstream/CURSEFORGE_PROJECT_DESCRIPTION.md` SHA-256: `01bee4cad3c4b04c457e3bffc281e8f9fd6878d42324e682a136f23c7055e2a6`; records the CurseForge license field as shown on 2026-10-16 (`BSD License`, project ID `999031`), supplied by the project user because the build machine is offline.

## Use in Wildfires

- Classification: `adapted` behavior plus 31 `verbatim` PNG resources.
- Adapted scope, now enumerated file by file in `files.csv` (2026-10-16). Every listed destination carries a `LicenseRef-TFCCaelum-BSD` header naming the upstream class it adapts and what Wildfires changed:
  - planet and satellite parameter set — `celestial/CelestialBodies.java`, `celestial/CelestialPlanetSettings.java`;
  - period, eclipse, supermoon and configuration semantics — `celestial/CelestialRuntimeSettings.java`, `celestial/CelestialConfig.java`, `client/celestial/CelestialClientTime.java`, `client/celestial/CelestialVisualRules.java`;
  - blood-moon gameplay and its spawn-chain mixins — `celestial/BloodMoonEvents.java`, `celestial/CelestialGameplayRules.java`, `mixin/minecraft/MonsterMixin.java`, `mixin/minecraft/LocalMobCapCalculatorMixin.java`, `mixin/minecraft/LocalMobCountsAccessor.java`, and the TFC-side gate `mixin/tfc/ForgeEventHandlerCelestialMixin.java` (from `tfccaelum/mixin/ForgeEventHandlerMixin`, which relaxed TFC's own spawn check during a blood moon);
  - rainbow and aurora rendering — `client/celestial/RainbowRenderer.java`, `client/celestial/AuroraRenderer.java`, `assets/wildfires/shaders/core/aurora.fsh`.
- Classification boundary — what is deliberately **not** in this partition, and stays Wildfires `inspiration` or original work: the three-dimensional orbit and ephemeris mathematics, the 8x8 square-disc eclipse geometry and its coverage arithmetic, moon-phase derivation from geometry, the event predicate/scan machinery, the planetarium screen, the Oculus/Photon sky-ownership bridge and the star table loading. Those replace upstream's empirical approximations rather than continuing them, and the migration index records why (`工程文档/移植索引/TFCCaelum-1.20.1-1.2/01-天体事件与TFC联动.md`). Listing them here would misstate who wrote them.
- Terms note for the shader: `aurora.fsh` is adapted from the release's `assets/minecraft/shaders/aurora.frag`, but that file's own in-file notice makes it a Mattenii / Shadertoy derivative under CC BY-NC-SA 3.0. Its governing terms are Mattenii's, not the BSD variant question, and it is recorded in full at `third_party/mattenii-aurora/`.
- Verbatim scope: the PNG destinations listed in `files.csv`; all 31 were byte-compared with the extracted release on 2026-08-11.
- File-level exception: `src/main/resources/assets/wildfires/shaders/core/aurora.fsh` is adapted from the release's shader and retains the original Mattenii / Shadertoy attribution and CC BY-NC-SA 3.0 marker. That shader is governed by Mattenii's terms, not by whatever the TFC Caelum release declares, and it is recorded separately at `third_party/mattenii-aurora/`.
- Partition root: `src/main/resources/assets/wildfires/textures/sky/` and the TFC-calendar/celestial behavior classes listed for this partition in the root `README.md` license matrix.
- ARR dependency direction: the root `LICENSE` (ARR) expressly does not cover this partition. Nothing in this partition is a dependency of the ARR files; it depends on them.
- Source availability: the adapted Java source and the copied resources ship in full with the repository. The upstream release JAR itself is not redistributed.
- JAR license paths: this record and `upstream/mods.toml`. There is no upstream license text to ship, because none exists.

## Rights and notices — BSD variant undetermined

- The release JAR's `META-INF/mods.toml` says `license = "BSD 3"`.
- The matching Modrinth project/version says `BSD-2-Clause`.
- The CurseForge project page shows `BSD License` (project ID `999031`), which is the platform's generic BSD option: it names no variant.
- All three declarations are therefore BSD-family. What they disagree on is the variant, and neither variant-bearing source supplies the complete text; no source repository is linked.
- Because every declaration is a permissive, attribution-style license, the open question was **which license text has to travel with our derivative**, not whether redistribution and adaptation are allowed at all. Wildfires does not select a variant, does not infer one from another, and never writes a standard SPDX BSD identifier for this partition: the difference is a real obligation (BSD-3-Clause adds the non-endorsement clause), and shipping the wrong text is itself a notice defect. See the release decision below for what replaced the blocked state.
- Obligations once the variant is confirmed: retain the license text and the upstream copyright notice, identify the modified files, and — if the governing variant is BSD-3-Clause — also honor its non-endorsement clause. Asset-level terms for the 31 PNGs must be confirmed separately; the release supplies no per-file author or license markers.
- The aurora shader is **not** part of the BSD question. The release's own `assets/minecraft/shaders/aurora.frag` and `aurora.vert` carry an in-file notice naming Mattenii, the Shadertoy URL and `CC BY-NC-SA 3.0`, so that file's terms come from the original Shadertoy author and are already known. A BSD clarification from Verph would say nothing about it. Our adaptation keeps the attribution, is non-commercial and share-alike, which is what that license requires; no author action is needed for this one file.
- Release status: **cleared for redistribution by the project owner's decision of 2026-10-16**, taken under the stricter-variant policy described below. The variant itself remains formally undetermined and is identified as `LicenseRef-TFCCaelum-BSD`; no standard SPDX BSD identifier is claimed.

## Release decision (2026-10-16)

- **Decision maker:** the project owner, recorded here as a written instruction. This is the party that carries the consequence, which is what makes the partition publishable without the author's answer.
- **Stated reason:** the author appears to have stopped developing the project and is difficult or impossible to reach, while all three declarations found are permissive BSD-family grants. BSD grants are irrevocable, so any one of the three declarations is already sufficient permission for the redistribution and adaptation Wildfires performs; the only thing missing is certainty about which text accompanies it.
- **Decision:** proceed with redistribution while satisfying the **superset** of the two candidate obligations — that is, comply with the BSD-3-Clause set, which also satisfies BSD-2-Clause. Do not assert a standard SPDX BSD identifier anywhere, because none of them is confirmed.
- **Custom identifier:** `LicenseRef-TFCCaelum-BSD` — "BSD-family permissive terms as declared by TFC Caelum `1.2`; the clause variant is undetermined; Wildfires satisfies the stricter three-clause interpretation." This is the SPDX-documented way to reference a license that has no confirmed standard identifier. If the author later names the variant, redefine this reference to point at it.
- **Obligations now in force:**
  1. keep the upstream copyright notice and declaration (`upstream/mods.toml`) and this record inside the distribution;
  2. identify the files Wildfires modified or adapted;
  3. do not use Verph's name, or the name of any contributor, to endorse or promote Wildfires or its derivatives — the non-endorsement clause of the stricter variant;
  4. carry the standard BSD disclaimer of warranty, which both variants require.
- Clauses 3 and 4 are carried verbatim, in canonical wording, in the `TFC Caelum` section of `THIRD_PARTY_NOTICES.md`, which ships inside the JAR. That file is the distribution-facing copy; this record explains why the text is canonical rather than a reproduction of an upstream file.
- **The 31 PNGs** are held under the same package-level declaration. The full-artifact scan found no per-file author or license marker and no separate asset terms, so the package claim is retained without narrowing — the same treatment already recorded for the NTM: Space textures.
- **Residual risk, stated plainly:** the variant is still unknown. Compliance with the stricter reading means no actual obligation is missed under either candidate, but the shipped notice cannot quote the author's own text because none exists in the artifact. If the author ever supplies it, quote it verbatim and drop this license reference.
- **Revisit triggers:** the author replies; any fourth declaration surfaces (for example on a site not inspected yet); a declaration turns out not to be BSD-family at all; or Wildfires is ever distributed commercially, in which case the non-commercial files (the Mattenii aurora shader and the Waterfall effects) become the binding constraint regardless of this partition.
- This is a documented project-owner judgment with the evidence preserved. It is not legal advice and it is not an author confirmation.

## Independent re-verification (2026-10-16)

The release was re-inspected directly from an on-disk copy rather than from memory: `E:\minecraft_server\MSL\Server1\mods\TFCCaelum-1.20.1-1.2.jar` (a second identical copy sits in `Server2`). It is byte-identical to the archive this record was written against — SHA-1 `114e10e497503f5ff4cb13850a8dec9bb9f4d625`, SHA-512 `f1c7153fc07da59677183f9967ca16e189bcf38d961b03931f100b141e9a10ef684dd1d71a6cf8d27cd3d8ceef7670db25d3b6c398f835387ca8ee8936ea0303` — so every statement above applies to that exact artifact.

- The archive holds 102 entries. A name scan for `licen`, `copying`, `notice`, `readme`, `credits` and `authors` returns **nothing**: there is no license file, no readme and no notice document of any kind.
- A content scan of every entry, compiled classes included, for `BSD`, `Licen{c,s}e`, `Copyright`, `CC BY`, `Creative Commons` and `All Rights Reserved` returns exactly three hits: `META-INF/mods.toml` line 3 (`license = "BSD 3"`), and line 4 of `assets/minecraft/shaders/aurora.frag` and `aurora.vert` (the Mattenii `CC BY-NC-SA 3.0` notice).
- `META-INF/MANIFEST.MF` and `pack.mcmeta` carry no license field.

So the only license declaration the release contains anywhere is the `mods.toml` string, and the complete text it refers to exists nowhere in the artifact. The upstream header also shows the shader was already a Mattenii derivative that Verph split into vertex and fragment stages — the same relationship our adaptation preserves.

## If the author ever clarifies

The partition no longer waits on this, but a confirmed answer is still worth folding in:

1. If the complete license text of `TFCCaelum-1.20.1-1.2.jar` arrives, or Verph names the governing variant (and the full three-clause text if it is BSD-3-Clause), preserve it under `upstream/` as a new immutable file without rewriting the existing snapshot entries.
2. Redefine `LicenseRef-TFCCaelum-BSD` to point at the confirmed license, quote the text verbatim in the JAR-carried notices, and update `THIRD_PARTY_NOTICES.md` and the root `README.md` matrix.
3. Nothing else changes: the obligations already in force are the superset, so a clarification can only narrow them.

Two evidence steps remain open and can be done at any time:

- If https://www.curseforge.com/minecraft/mc-mods/tfc-caelum/license renders a text for the `BSD License` option, capture it here with its retrieval date. It is CurseForge's own text for a generic option, not a file the author supplied, so it supplements the record rather than settling the variant.
- Ask the author to make the Modrinth, CurseForge and in-JAR declarations agree, so a future version does not reopen the same question.

Request to send to the author:

> Subject: TFCCaelum 1.20.1-1.2 license clarification
>
> Hello Verph,
>
> `TFCCaelum-1.20.1-1.2.jar` (Modrinth version `wSasbQOH`, archive SHA-1 `114e10e497503f5ff4cb13850a8dec9bb9f4d625`) declares `license = "BSD 3"` in `META-INF/mods.toml`, while the matching Modrinth project/version declares `BSD-2-Clause`. The release contains no license text, README or source link, so I cannot tell which one governs it.
>
> Could you confirm which license applies to this release and provide its text? I am redistributing 31 sky textures and an adaptation of the celestial behavior from that release in another mod, so I need the notice and attribution conditions in writing before publishing.
>
> If it helps, the three places I can see disagree slightly: the JAR's `mods.toml` says `BSD 3`, the Modrinth listing says `BSD-2-Clause`, and the CurseForge project page says `BSD License`. Aligning them in a future release would save the next person the same question.
>
> Thank you.

## Verification

- [x] `files.csv` lists every adapted and verbatim destination: the 31 PNGs plus the fifteen enumerated adapted files, each with a destination SHA-256.
- [x] All 31 verbatim PNG destinations were byte-compared with the extracted release on 2026-08-11.
- [x] The two conflicting license declarations are preserved verbatim rather than normalized.
- [x] The undetermined variant and the `LicenseRef-TFCCaelum-BSD` identifier are stated consistently in `README.md`, `THIRD_PARTY_NOTICES.md` and `third_party/README.md`.
- [x] Release decision (2026-10-16) recorded, with the superset obligations carried by the JAR and the reopen conditions listed.
- [x] Every adapted destination carries its per-file modification notice, so the "identify the files Wildfires modified" obligation is discharged file by file.
- [x] The classification boundary records what is intentionally excluded from the partition, so no Wildfires-owned implementation is misattributed to the upstream author.

No paperwork remains open for this partition. Future edits to a listed file must refresh its `files.csv` hash in the same change, and any new adaptation must be added here before it enters the tree.

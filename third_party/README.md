# Third-party material management

This directory is the durable source-of-truth archive for external material used by Wildfires. `THIRD_PARTY_NOTICES.md` is the concise distribution-facing index; this directory stores the evidence behind it.

Wildfires' original content is All Rights Reserved. That status does not apply to anything registered here. The production JAR includes this complete directory together with the root `LICENSE` and `THIRD_PARTY_NOTICES.md`, so the applicable third-party terms travel with the files they govern.

## Required workflow

Before copying or adapting code, data, textures, models, sounds, shaders, documentation or configuration from another project:

1. Classify the use as `dependency-only`, `inspiration`, `adapted`, or `verbatim`.
2. Create `third_party/<project>/<version-or-commit>/PROVENANCE.md` from `TEMPLATE.md`.
3. Put the upstream `README`, `LICENSE`, `COPYING`, `NOTICE`, `CREDITS` and `AUTHORS` files in that version's `upstream/` directory, unchanged. Preserve their original filenames. If a release does not contain one, record `not included in the inspected release`; do not invent an upstream README or license.
4. Record the canonical URL, author, mod/game version, release ID or commit, retrieval date and SHA-256 values. Project pages saved as Markdown must be named `PROJECT_DESCRIPTION.md`, not `README.md`.
5. For every `adapted` or `verbatim` item, record source and destination paths in `files.csv`. Keep copyright/license headers in the destination file. Directory globs may supplement but never replace the manifest for exact copies.
6. Check asset-level and file-level licenses separately from the mod's general code license. Data sets, shaders, fonts, music and commissioned art commonly have different terms.
7. Update the root `README.md` acknowledgement when the project materially influences Wildfires, and always update `THIRD_PARTY_NOTICES.md` when material is distributed or adapted.
8. Resolve unclear, missing, non-commercial, no-derivatives, copyleft or mutually conflicting terms before release. A credit line is not a substitute for permission.

Snapshots under `upstream/` are immutable evidence. When upstream changes, add a new version/commit directory rather than editing an old snapshot.

## Hash convention

`files.csv` hashes are SHA-256 over the destination file exactly as it exists in the working tree when the row is written. This repository runs with `core.autocrlf=true` and has no `.gitattributes`, so the working tree mixes line endings: files git has checked out are CRLF, while files written in place by tooling stay LF. A fresh checkout can therefore rewrite a tool-written text file to CRLF and invalidate its recorded hash without any content change.

Two consequences for maintainers:

- Recompute a hash from the same form it was recorded in. For a java/json/shader destination that was recorded from the LF form, `git hash-object`-style LF comparison or a re-read after checkout will differ from a raw file hash; compare like with like before declaring a mismatch.
- Pinning the repository's line endings (for example an `.gitattributes` with `eol=lf` for text files, followed by one renormalization pass and a full manifest refresh) would remove this fragility. It is a repository-wide decision and has not been applied here.

## Current register

| Project/material | Use | Evidence | Status |
| --- | --- | --- | --- |
| Caelum `1.20.1-2.0.0.0` | seven adapted files + verbatim star table | README and MIT license re-verified byte-exact against the pinned revision, enumerated manifest, per-file MIT notices, source hashes | Recorded and closed |
| TFC Caelum `1.2` | 15 adapted files + 31 verbatim PNGs | JAR metadata, project description, CurseForge license field, release hashes, fully enumerated file manifest, per-file notices | `LicenseRef-TFCCaelum-BSD`: BSD-family, variant undetermined; released under the stricter three-clause reading by the project owner's decision of 2026-10-16. Closed |
| BSC5P-JSON-XYZ catalog | verbatim data through Caelum | upstream license, catalog hash | CC BY 4.0 attribution recorded |
| Mattenii aurora shader | adapted shader | source URL and in-file notice | CC BY-NC-SA 3.0 release compatibility must be reviewed |
| VS: Genesis `1.20.1-0.7.3` | adapted cubemap/mesh/shaders/ascent body + 2 verbatim planet PNGs | local source-tree digest, README, Apache-2.0 license, metadata and file manifest | Recorded |
| NTM: Space `1.0.27_X5778` | adapted orbit/capsule/navigation/ascent code and models + 8 verbatim PNGs | local source-tree digest, README, GPL/LGPL texts, contributor credits, corresponding-source statement and file hashes | LGPL-3.0-only partition recorded; every adapted Java file carries the notice |
| Waterfall `0.10.3` / commit `6be4f897` | adapted radiant-drive and Daedalus v1/v2 shaders/configs + derived meshes and textures | README, CC BY-NC-SA 4.0 statement, source/config snapshots and file manifest | Non-commercial share-alike partition recorded |
| ArcaneVortex `0.6.8` | Sky Ripper Starlink/Black World visual port + cosmic star sprites + `star_sky` volume adapted to planar space shards | JAR metadata, exact Sky Ripper/Starlink/Black World/shake evidence, byte-exact `star_sky` source resources, hashes and user-confirmed project-specific authorization | Visual use recorded; tesseracts and attack-damage mechanisms excluded |
| User-supplied nebula set `2026-08-24` | adapted fourth-second point-to-nebula volume | preserved upstream snapshot, file hashes, in-file source attributions | CC BY-NC-SA 3.0 partition under the Shadertoy platform default; not ARR; cleared for free distribution by the project policy |

This register is about incorporated or adapted material. Ordinary dependencies may be documented in build files without snapshotting their entire source repositories.

# Caelum provenance

## Identity

- Canonical source: https://github.com/z0phka/Caelum
- Project page: https://www.curseforge.com/minecraft/mc-mods/caelum
- Author/copyright holder: Nuparu00 / z0phka
- Version: `1.20.1-2.0.0.0`
- Verified revision: `59c03cc665cb91291b1b3d3a591304be702fbc05`
- Minecraft/loader: Minecraft 1.20.1 / Forge
- Verified on: 2026-08-11

The preserved upstream README and LICENSE are byte-identical to the files at the verified revision.

## Use in Wildfires

- Classification: `adapted` mechanisms plus one `verbatim` data file.
- Adapted scope, enumerated file by file in `files.csv` (2026-10-16). Each listed destination carries an MIT notice naming the upstream class it adapts and what Wildfires changed:
  - star data chain — `client/celestial/StarDataManager.java`, `client/celestial/StarTableLoader.java` (from `client.data.StarDataManager` and `client.event.ClientReloadListeners`);
  - star appearance and sky rendering — `client/celestial/CelestialVisualRules.java` (logarithmic magnitude mapping, once-per-buffer brightness), `client/celestial/CelestialRenderer.java` (custom star buffer and the latitude-driven rotation of the celestial sphere, from `client.SkyUtils` and `mixin.MixinLevelRenderer`);
  - client integration mixins — `mixin/minecraft/CelestialClientLevelMixin.java` (from `mixin.MixinClientLevel`) and `mixin/minecraft/CelestialFogRendererMixin.java` (from `mixin.MixinFogRenderer`);
  - configuration — `celestial/CelestialConfig.java` (star option set from `config.ClientConfig`).
- Verbatim destination: `src/main/resources/assets/wildfires/stars/stars.json`, byte-identical to the upstream table with SHA-256 `46f2b21d8205562d5918322fbf9217c51454d2fddf05c77223e9053584abc227` (upstream path `src/main/resources/assets/cealum/stars/stars.json`).
- Classification boundary — what is deliberately **not** attributed to Caelum, and stays Wildfires work: the independent moon phase derived from three-dimensional geometry (upstream's `MoonController` and its `DimensionType` moon-phase mixin are not ported at all), the latitude source itself (TFE's hemisphere scale on the TFC calendar rather than Caelum's world-border or constant-Z poles), the annual sky rotation driven by the TFC calendar year rather than `earth.orbit_period`, the debug overlay, and everything above the star pass in `CelestialRenderer` — planets, moon, eclipse layers, aurora, rainbow and the sky-ownership gate.
- Partition root: the seven adapted paths above, `assets/wildfires/stars/stars.json`, and the `first/wildfires/client/celestial/` star classes they live in.
- ARR dependency direction: the root `LICENSE` (ARR) expressly does not cover this partition. The MIT-licensed destinations are consumed by ARR code; no ARR file is consumed by them.
- JAR license paths: this record, `THIRD_PARTY_NOTICES.md` and `upstream/LICENSE`.

Caelum's README identifies `frostoven/BSC5P-JSON-XYZ` as the stellar-data source. That generated catalog has separate CC BY 4.0 terms recorded under `third_party/bsc5p-json-xyz/`; the Caelum MIT license must not be used to erase the deeper data attribution.

## Rights and preserved material

- Project license: MIT.
- `upstream/README.md`: original file, SHA-256 `7c73b8ae969c736dbc01fdf39123c1aaca918b393bbbcad1ebbe229c3e466126`.
- `upstream/LICENSE`: original file, SHA-256 `d0ae4cfb3ccd36e52c3cdac4768f471f30cde91c37bbab59ec1f21e30d36952f`.
- `files.csv`: destination/source/hash/classification manifest, standard columns.
- Obligations: retain the MIT notice and the upstream copyright line, and identify the modified files — done through the per-file headers above and this record. No share-alike or non-commercial term applies to this partition.

## Verification

- [x] Both preserved upstream files were re-fetched from `https://raw.githubusercontent.com/z0phka/Caelum/59c03cc665cb91291b1b3d3a591304be702fbc05/` on 2026-10-16 and match byte for byte, so the snapshot is exact rather than merely self-consistent.
- [x] Every adapted destination is enumerated with a destination SHA-256, and each carries its MIT header.
- [x] The star table's verbatim status and the deeper CC BY 4.0 catalog terms are both recorded.
- [x] The classification boundary states which closely related mechanisms are Wildfires' own, so the upstream author is not credited for them.

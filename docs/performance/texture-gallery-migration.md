# Retained texture gallery migration

The lazy-texture branch already writes external PNG assets when a map loads. Its loader, however, deserializes those external manifests as embedded textures and loses the PNG references. Current pack textures are replaced during pack loading, masking the problem; retained textures absent from the current pack can become missing textures. This change restores external assets through the configured MapStorage, preserves array ordinals and color/alpha/animation metadata, and accepts legacy embedded galleries unchanged.

External paths are restricted to the generated content-addressed directory and their SHA-256 identity is checked. Missing/corrupt references fail the map load instead of silently resetting the gallery and overwriting its material IDs. Re-export checks deduplicated existing PNG assets against expected bytes and repairs incomplete earlier writes. The complete manifest is serialized before publication. File storage with atomic writes enabled (the default) stages/compresses whole payloads and replaces the prior file only after successful close; failed writes leave the previous manifest in place. Non-atomic storage configuration retains its explicitly selected semantics.

## Explicit metadata-only migration

Build the CLI artifact, then run from the server working directory with exclusive access to the configured BlueMap storage and a storage backup:

```sh
java -jar bluemap-<build>-cli.jar -c config/bluemap --migrate-textures -m world,world_the_nether
```

Omit `-m` to migrate every configured map; unknown selections fail before writes. This uses the configured file/SQL storage and compression, loads neither world geometry nor resource packs, schedules no tile rendering, and does not reset material IDs. PNGs are published before the smaller manifest. It is safe to rerun against the same valid gallery. Nothing in this PR runs that command against production.

The migration reuses the existing lazy frontend's textureUrl format; it does not imply that production has already been migrated or that startup timing has improved. Validate a retained storage copy, correct ordinals/visuals and external asset delivery before applying it to the live storage.

Validation before the draft: five gallery tests cover embedded-to-external round-trip without pack textures, ordinal/PNG/metadata retention, corrupt/missing assets, interrupted-asset repair, failure-before-manifest publication, and path validation. Two file-storage tests cover failed compression close retaining the previous payload and compressed successful replacement. CLI compiles. A captured production-size gallery measurement, broader storage integration and full NeoForge/CLI packaging follow after the initial draft deliverable.

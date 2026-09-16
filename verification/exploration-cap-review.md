# Exploration growth regression verification — 2026-09-17

## Change

CURRENT-profile chunk generation now uses an immutable empty declaration witness rather than the complete world's structure-carrier snapshot. Actual located-map loot resolution remains at container open. Existing committed chunks are replayed without rewriting their carriers or fingerprints. Publication retains the world lock, profile validation, and first-writer-wins behavior. Ordinary immutable commit conflict checks remain in place. No database schema, student source, assignment, original source, or producer identity was changed.

This deliberately changes new map-declaration witness receipts; it does not claim byte-identical new loot declarations. Actual selected destinations are resolved again by the current producer's late-loot path. The existing profile is the only supported profile.

## Verification

- Engine check/jar/sourcesJar and solved isolated application's bootJar succeeded.
- 67 tests passed, no failures/errors/skips, including real producer geometry comparison and existing carrier replay.
- Independent review found no concrete blocker in the changed generation/publication path.
- Restored the exact 409 original canonical carriers (67,374,859 snapshot bytes, above the 67,108,864-byte old limit).
- The archived reproduction contained only canonical rows, not the corresponding runtime side tables. First raw restore correctly failed lane-recovery checks; its logs were retained. For the valid fixture, only publication lane bookkeeping was reset before entry, allowing normal side-table activation. Carrier/fingerprint bytes were unchanged. This is a QA fixture preparation step, not a product migration.
- Two isolated Docker Spring apps, MySQL and Redis; host application ports 18871 and 18872. No host database ports used.
- Real headless browser entry, rendering and movement; normal chunk snapshot requests to previously absent coordinates.
- Final frozen database: 514 canonical chunks and 87,242,578 aggregate snapshot bytes. All original 409 final carriers, structure carriers and fingerprints retained their hashes.
- Both apps were killed uncleanly, restarted, and the player re-entered through the other server. New chunks (24,22), (25,22), (-24,-22) committed afterward.
- Post-restart active-runtime logs had zero ERROR lines and zero snapshot-bound errors at inspection. Tick-budget warnings occurred; this was functional verification, not performance acceptance. Reusing the registered nickname produced expected HTTP 409 responses handled by the login UI.
- Browser nativePointerLocked=false and forbidden=[]; owned browser and infrastructure cleaned up after evidence collection.

## Scope at the time of this verification

At this checkpoint, the separate late located-map loot preparation/historical replay path still loaded a full-world reference snapshot and retained its aggregate cap. See [the subsequent streaming review](snapshot-streaming-review.md) for its removal. It was not exercised or repaired here. This change removes the cumulative cap from terrain exploration/generation, not every world operation. Do not claim all gameplay is globally unbounded or that map-open replay was integration-tested.

## Artifacts

- Engine candidate: 2.2.2-SNAPSHOT, not publicly deployed.
- JAR SHA-256: 777ec8ae92fc4b8ae790f93690b5bb05504702e200a591378a52dfafed8e9174
- Runtime evidence, hashes, logs, screenshot and full valid-world backup: /private/tmp/webcraft-exploration-_84tormu

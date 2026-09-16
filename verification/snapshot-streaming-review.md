# Cumulative snapshot limit removal — 2026-09-17

## Change

The active structure-reference, late-loot and historical replay paths no longer serialize the entire world into one bounded frame or require every structure BLOB in memory. Metadata identifies immutable rows; each carrier is loaded when its bounded upload batch is sent. Reference and claim messages are paged. Stored historical membership and claims use 1 MiB database pages in the same settlement transaction.

The former aggregate 64 MiB and 1,000,000-row checks are removed from active paths. Individual carrier and transport-frame validation remains: these limits bound one unit of work, not accumulated exploration. Metadata and reference/claim maps still scale with world size. This is not a claim of constant memory or unlimited physical storage.

The exact V1 logical bytes, SHA-256 receipts and snapshot identities are preserved. V1 history remains readable; new history stores a versioned page envelope. World deletion and authority write-fence coverage include the new page table. A typed projection avoids Spring converting large BLOB arrays element by element.

The original published producer archive and producer identity remain unchanged. The worker adapter is reproducibly compiled separately, fingerprinted and extracted into its own cache location. Existing producer caches are not overwritten.

## Automated verification

- `check jar sourcesJar` succeeded; 80 tests, zero failures/errors/skips.
- Old single-frame and streaming structure reference receipts match; failed uploads release their state.
- A valid authenticated container fixture executes both the original shipped worker and the new streamed worker with byte-identical results. The streamed route also completes with more than 64 MiB of synthetic lazy rows containing valid producer carriers.
- History streams larger than 64 MiB, missing/corrupt page detection, legacy logical hashes and bundle extraction/integrity checks pass.
- Persistence read-only review found no additional concrete defect.
- Final engine JAR SHA-256: `c301f6f44e2b565f1f8e8c02e4a0cc27ff6af16199c7b21d05f149a9cad5e0d9`.

## Runtime evidence

- A full existing-world backup was restored to an isolated MySQL database, with Redis on isolated ports. No student, original source or public release was changed.
- 514 real canonical rows represent 87,242,578 aggregate structure-snapshot bytes, above the former 67,108,864-byte limit.
- Actual JPA metadata and lazy BLOB queries, native history page writes, parent persistence and V1/V2 history restoration passed. Receipt: `912f87e9647ef117fae524437db7ce0edc8ec3ab0d02d7c4a817a591622462bb`.
- The first database harness run omitted the required world-authority lease and was rejected correctly. It was corrected without weakening production checks. An initial successful run exposed costly array conversion; projection-based queries then passed the same test in 14.672 seconds (25 seconds for the Gradle invocation). These are functional run timings, not controlled performance claims.
- The candidate booted and rendered the existing world through an owned headless browser. WebSocket welcome, world events and heartbeat responses were observed. Native pointer capture remained false; forbidden input events remained empty.
- Runtime log inspection found no ERROR lines or snapshot-bound failures. Tick-budget warnings occurred during world activation; no performance acceptance is claimed.

## Separate reproduced map defect

Subsequently fixed by the [map result conversion change](map-loot-fix-review.md). The following records the original finding.

An authenticated shipwreck-map fixture fails in both the original published worker and the streaming worker. `Mc263ContainerLootResolver` emits a buried-treasure map with maximum stack size 1, while the V2 stored-result contract requires 64. `CanonicalLootStoredResolution.Slot` rejects the mismatch. This is independent of cumulative snapshot size and was not silently changed in the immutable producer. Successful map gameplay must not be claimed from this verification. Reproduction source and old/new failure evidence are retained with the runtime evidence.

Natural camp fixture attempts had no map-bearing loot declaration and therefore were not accepted as positive map tests.

## Evidence location

`/private/tmp/webcraft-snapshot-_50gpym2`: database test source/XML, logs, history page dump, browser screenshot and server log. Candidate version: `2.2.2-SNAPSHOT`; not publicly deployed.

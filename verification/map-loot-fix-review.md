# Map loot result conversion fix — 2026-09-17

## Student impact

- Student Java source changes: not required. No assignment source, tests or requirements were changed.
- Applying the fix requires changing the pinned `webcraft-engine` dependency version in the student's `build.gradle` after release. The currently distributed version is 2.1.5.
- This candidate remains 2.2.2-SNAPSHOT and is not publicly deployed.

## Cause and fix

The immutable producer generates a buried-treasure map stack with maximum size 1, while V2 persisted loot requires 64. The previous worker called `fromV2` directly and rejected the result. The same authenticated shipwreck-map request fails through the original shipped worker.

The worker now converts only recognized V2 map items whose legacy maximum is exactly 1 and whose V2 maximum is 64. It validates the original components through the pinned V1 converter and constructs the slot through the pinned V2 validator. All other slots use the original V2 converter. Item counts, components, slot order, empty slots and random continuation are retained.

No producer class is shadowed or changed. The original producer archive, source graph, world identity and persisted V1/V2 codecs remain unchanged. Only the separately fingerprinted worker adapter changes.

## Verification

- `check jar sourcesJar`: 85 tests, no failures/errors/skips. Bundle integrity checks pass.
- Regression fixture: original shipped worker rejects the request; corrected worker generates a real located-map target, a 128×128 preview and a V2 map stack with count 1 / maximum 64, retaining target components.
- The same map request succeeds with more than 64 MiB of lazy structure carriers and returns the same result/context/preview.
- Existing V1 map bytes still decode and re-encode identically. Already valid V2 results, including random continuation, remain byte-identical. Mixed legacy/V2 maps work; invalid counts remain rejected.
- The first unit-test harness used an unsupported two-slot container. It was corrected to a supported 27-slot chest; production container validation was not weakened.
- Independent read-only review found no concrete defect.
- The solved isolated application's bootJar contains the exact candidate engine JAR. An owned headless browser entered and rendered the restored existing world; welcome, world events and pong were received. Native pointer capture remained false and forbidden input events stayed empty. Expected nickname-registration HTTP 409 responses were handled by the UI; server logs had no ERROR entries. Tick-budget warnings occurred during activation, so this is not performance certification.

The map test is an authenticated fixture using real locator and preview implementations, not a claim that a naturally generated shipwreck was opened in the browser.

Engine JAR SHA-256: `9bc8ddfb87ef7e50e7e508f36d859258f9bc551ac99d561a3e2c8315331a337f`.
Evidence: `/private/tmp/webcraft-map-fix-e5fr3yr5`.

# Shared terrain loading budget

Every LOD previously had an independent loading loop. A three-LOD map plus high-resolution layer could start 32 tile requests immediately, then continue adding requests once per second even while all previous reads stalled. A shared queue now allows two low-resolution and two high-resolution tile loads across all managers/maps. Coarse coverage wins low-resolution priority; high-resolution work has its own slots and eventually retains full detail. A manager keeps at most two outstanding jobs, current camera position determines queued distance priority, and unloaded/out-of-view queued jobs are rejected before starting IO.

This bounds tile work, not all browser requests: lazy PNG hydration, live feeds and addon geometry remain separate. Live addons have no dependency on the terrain queue. Already-started Three.js loader requests retain their normal network lifecycle; queued obsolete requests are cancelled. This is not a new request-abort transport or a frame-time improvement claim.

High/low-resolution async callbacks now propagate parse, hydration and blocker failures through their returned promises. Previously exceptions inside an async loader callback could strand the outer promise and its loading count. Failed hires geometry and lowres textures/materials are disposed.

## Initial validation

Twelve Node tests pass, including map readiness, shared class budgets, eventual queue completion, obsolete work, failure-slot recovery, updated camera priorities and actual loader failure cleanup. The Vite production build passes.

`node common/webapp/benchmarks/terrain-request-burst.mjs` evaluates the actual before/after TileManager with a deterministic clock, four layers and stalled IO. Against `f548cd8`, at 250 ms the old code started **32** tile requests; the candidate started **4**, with four bounded jobs waiting. This proves the concurrency change under the fixture; it does not measure cold mobile presentation or GPU performance. Same-camera browser timing and visual checks follow the draft deliverable.

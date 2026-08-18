# nanojson (vendored)

This module contains a vendored copy of **nanojson** (Apache License 2.0,
Copyright 2011 The nanojson Authors — see the license header in each source file).

- Original project: https://github.com/mmilicevic/nanojson
- Fork used upstream by NewPipe / this project: https://github.com/TeamNewPipe/nanojson
- Pinned commit: `1d9e1aea9049fc9f85e68b43ba39fe7be1c1f751` (2020-04-17)

**Why vendored (instead of the upstream `com.grack:nanojson:1.8`):**
the TeamNewPipe fork makes `JsonObject.getObject(String)` / `getArray(String)`
null-safe (they return an empty container for missing keys instead of `null`).
The NewPipe extractor relies on that behavior (chained access), so the plain
upstream artifact breaks it (verified by `YoutubeMetadataFallbackTest`).
Vendoring the fork removes the last build-time dependency of the app on the
TeamNewPipe GitHub organization, making the fork fully self-contained.

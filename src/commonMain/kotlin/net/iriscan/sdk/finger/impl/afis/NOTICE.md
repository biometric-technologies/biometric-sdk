# SourceAFIS port

The code in this folder is a Kotlin port of the fingerprint engine of
[SourceAFIS for Java](https://github.com/robertvazan/sourceafis-java) 3.18.1:

> Robert Važan's SourceAFIS for Java
> https://sourceafis.machinezoo.com/java
> Copyright 2009-2025 Robert Važan and contributors
> Distributed under Apache License 2.0.

The Apache License 2.0 text is at https://www.apache.org/licenses/LICENSE-2.0.

## Changes from the original

- Translated from Java to Kotlin common code, so that it runs on Android, iOS and the JVM.
- Kept: feature extraction (image scaling, segmentation, equalization, orientation, smoothing,
  binarization, thinning, skeleton tracing and filters, minutia filters), the edge tables and
  hashes, and the matcher with its scoring. The parameters are unchanged.
- Java's `Math.round` (half up) and `java.util.PriorityQueue` (tie order) are reproduced, so the
  minutiae and scores are the same as those of SourceAFIS for Java, bit for bit.
- Left out: transparency logging, image decoders, the CBOR template format and the ISO/ANSI
  template importers, thread-local matcher state and object pools.
- Templates are stored in the SDK's own format, see `FingerTemplate.kt`.

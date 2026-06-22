# Benchmarks

Local JMH harness for LowMemoryAhoCorasick. Measures parse throughput, build time, and allocation rate.
Not run in CI — run it manually before/after an optimization and compare.

## What is measured

- `ParseBenchmark` — parse throughput (ops/sec) over the `Stream` API.
- `BuildBenchmark` — time to add all entries + `build()` (single-shot).
- `DecodeBenchmark` — `byte[]`→`String` decode cost vs parse, isolating the decode tax (Axis 3).

All three sweep a `@Param` matrix: `dictionarySize` ∈ {1000, 100000, 1000000}, `alphabet` ∈ {ASCII, UNICODE},
`density` ∈ {SPARSE, DENSE}. Corpora are generated deterministically (fixed seed) by `CorpusGenerator`.

## Running

Full matrix (slow — minutes, and the 1M-dictionary build needs a large heap):

```bash
./gradlew :benchmarks:jmh
```

Subset while iterating (via the `jmhIncludes` property wired into `build.gradle.kts`):

```bash
./gradlew :benchmarks:jmh -PjmhIncludes=ParseBenchmark
```

Allocation rate (`gc.alloc.rate.norm`, bytes/op) is collected by the `gc` profiler, configured in
`build.gradle.kts`. Results are written as JSON to `benchmarks/build/results/jmh/results.json`.

## Regression-guard ritual

1. On the baseline commit, run the harness and save `baseline.json`.
2. Apply the optimization.
3. Re-run, save `after.json`, and diff throughput + bytes/op per `@Param` combination.

Map of optimization → signal:

- Raw-`int[]` hot loop → `ParseBenchmark` throughput, all combos.
- Allocation-free API → `ParseBenchmark` + `gc` bytes/op (add a `sinkParse` method when the API lands).
- Prefilter → `ParseBenchmark` SPARSE.
- `byte[]` matcher → `DecodeBenchmark` delta (and a future `ByteParseBenchmark`).

## Out of scope

Retained-heap / footprint: JMH's `gc` profiler reports allocation *rate*, not live-set size. Measuring the
final retained structure size is a separate exercise (the README memory plots were gathered by constraining
JVM heap).

# ThreadTerminationBenchmark

```plain
Benchmark                                                                        Mode  Cnt           Score           Error  Units
ThreadTerminationBenchmark.benchmarkThreadWithAtomicBoolean:iterations          thrpt   40  4510770523.563 ± 111129214.723  ops/s
ThreadTerminationBenchmark.benchmarkThreadWithInterruptedFlag:iterations        thrpt   40  4144573429.617 ±  99808093.486  ops/s
ThreadTerminationBenchmark.benchmarkThreadWithPaddedVolatileBoolean:iterations  thrpt   40  4476741480.984 ± 114770830.770  ops/s
ThreadTerminationBenchmark.benchmarkThreadWithVolatileBoolean:iterations        thrpt   40  4129140567.919 ± 110736514.971  ops/s
```

# FastActorMaxIdleLoopsCountBenchmark

```plain
Benchmark                                             (maxIdleLoopsCount)   Mode  Cnt         Score         Error  Units
FastActorMaxIdleLoopsCountBenchmark.benchmark:total                     8  thrpt   25  15957383.789 ± 1104068.229  ops/s
FastActorMaxIdleLoopsCountBenchmark.benchmark:total                    16  thrpt   25  15271480.984 ± 1131072.275  ops/s
FastActorMaxIdleLoopsCountBenchmark.benchmark:total                    32  thrpt   25  14680881.286 ± 1862004.151  ops/s
FastActorMaxIdleLoopsCountBenchmark.benchmark:total                    64  thrpt   25  14044021.110 ± 1458918.534  ops/s
FastActorMaxIdleLoopsCountBenchmark.benchmark:total                   128  thrpt   25  14414517.763 ±  978949.643  ops/s
FastActorMaxIdleLoopsCountBenchmark.benchmark:total                   512  thrpt   25  14505883.825 ±  927224.663  ops/s
FastActorMaxIdleLoopsCountBenchmark.benchmark:total                  2048  thrpt   25  13616731.095 ± 1506130.179  ops/s
FastActorMaxIdleLoopsCountBenchmark.benchmark:total                  8192  thrpt   25  13830680.435 ± 1047193.780  ops/s
FastActorMaxIdleLoopsCountBenchmark.benchmark:total                 32768  thrpt   25  13335554.053 ± 1437614.025  ops/s
FastActorMaxIdleLoopsCountBenchmark.benchmark:total                131072  thrpt   25   9631986.237 ±  950173.764  ops/s
FastActorMaxIdleLoopsCountBenchmark.benchmark:total                524288  thrpt   25   6598295.657 ± 1061736.820  ops/s
```

Conclusion:

The higher the `maxIdleLoopsCount` is, the less performant the actors become. Use low value
for this parameter. Let's design another test for low values only (under 32).
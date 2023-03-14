{:title "Benchmarking"
 :page-index 0
 :layout :kb-page}

Benchmarking, in simple terms, stands for measuring how long it takes for some
code to run. The granularity of benchmarks can be very different, here are a
non-exhausitve list of various types of benchmarks:

- Macrobenchmark — when you measure how long it takes for your whole program to
run from start to finish.
- End-to-end benchmark of software module or a feature.
- Benchmark of a single function (benchmarking version of a unit-test).
- Microbenchmark — when you measure the performance of a low-level function, a
  language facility, or a core library utility.
- Nanobenchmark — extreme case of microbenchmark, when the code being measured
  doesn't take more than a couple nanoseconds. Applies to the very basic
  constructs like arithmetic operations, array access, etc.

Even though, conceptually, those categories only differ in the length of the
work being measured, the latter types of benchmarks require a precise usage of
dedicated tools to be carried out accurately. The naive method of just running
the code and watching the clock may (and will) yield incorrect results when
applied to micro- and nanobenchmarks.

Pages in this section describe how to properly use different benchmarking tools
and explain some benchmarking basics and pitfalls to watch for.

{:title "Tips and tricks"
 :page-index 6
 :layout :kb-page
 :toc true}

#### Collect enough samples

The profiling library under the hood of clj-async-profiler,
[async-profiler](https://github.com/jvm-profiling-tools/async-profiler), is a
sampling profiler. It means that it is not 100% accurate; instead, it derives
the profile picture statistically. When it comes to statistics, you need enough
data samples to be confident about the results. So, make sure that when you
profile anything with clj-async-profiler, you get at least 1000 samples. The
empirically optimal number of samples for `:cpu` event type is 5000-10000. This
roughly corresponds to 5-10 seconds of running time when the CPU is fully
loaded; so when you want to measure something that finishes faster, then repeat
it in a loop that runs at least for a few seconds.

```clj
;; 1000 iterations is an example. You'll have to tune the number for your
;; particular code to achieve the 5-10 seconds runtime.

(prof/profile
 (dotimes [_ 1000]
   <my-code>))
```

When profiling other events (e.g., `:alloc`, `:lock`), you might not be able to
get that many samples, but that is alright.

You can learn how many samples the profiler collected by hovering over the
bottom stackframe in the flamegraph. The number of samples is also displayed on
the UI page next to the flamegraph name.

#### Trigger JIT compiler before profiling

When you profile some piece of code, it is a good idea to run it on its own once
or even twice without profiling. If the form is too quick to execute and you
wrap it in `dotimes`, run the whole `dotimes` before profiling. This ensures
that the underlying code is compiled to native instructions by the JIT compiler.
Otherwise, you might be partially profiling the interpreted version of the code
that may have a completely different performance behavior than the final
C2-compiled version.

#### Supply custom agent binary

clj-async-profiler ships with the [following agent
binaries](https://github.com/clojure-goes-fast/clj-async-profiler#platform-support).
To use clj-async-profiler on other [supported
platforms](https://github.com/jvm-profiling-tools/async-profiler#supported-platforms),
you should do the following:

1. [Build](https://github.com/jvm-profiling-tools/async-profiler#building)
   async-profiler for the desired platform.
2. Put the resulting libasyncProfiler.so in a place accessible by your JVM
   process (and which also allows code execution from).
3. Execute from Clojure:
   ```clj
   (reset! prof/async-profiler-agent-path "/path/to/libasyncProfiler.so")
   ```

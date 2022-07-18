{:title "Using JMH with Clojure - part 1"
 :date-published "2018-10-22 11:00:00"
 :reddit-link "https://www.reddit.com/r/Clojure/comments/9qbtaw/using_jmh_with_clojure/"}

Hello, performance junkies, long time no see. Today we will learn to access the
tool that every JVM gofaster should (mis)use at least once in their lives —
Aleksey Shipilёv's [Java Microbenchmarking
Harness](http://openjdk.java.net/projects/code-tools/jmh/).

Earlier, we reviewed
[Criterium](http://clojure-goes-fast.com/blog/benchmarking-tool-criterium/)
which is an easy to use benchmarking tool for Clojure. Criterium is a library
you include and run directly from the REPL. It calculates some statistics on the
results and ensures that the function you run is warmed up properly, but beyond
that, it's quite trivial.

JMH, on the other hand, is much more intricate. It provides a toolset to fight
against common benchmarking enemies, such as dead code elimination, constant
folding, coordinated omission, and many others. The goal of this post is to give
you an idea of how to use JMH in your project and what benefits that can
potentially bring.

### What is the problem?

As long as you measure relatively slow operations (milliseconds or slower) with
Criterium or plain time/dotimes, you are most likely fine. The problems start
when you decide to measure something minuscule. For example, let's check how
fast is long multiplication on JVM:

```clj
(time
 (dotimes [_ 1e9]
   (unchecked-multiply 123 456)))

;; "Elapsed time: 337.849181 msecs"
```

338 milliseconds for 1 billion iterations makes it ~0.3 nanoseconds per
multiplication. Modern CPUs are fast! We can ensure that the compiled code is
correct with
[clj-java-decompiler](http://clojure-goes-fast.com/blog/introspection-tools-java-decompilers/#live-decompilation-in-the-repl):

```clj
(decompile
 (dotimes [_ 1e9]
   (unchecked-multiply 123 456)))
```

```java
public static Object invokeStatic() {
    for (long n__5742__auto__15409 = RT.longCast(1.0E9), _ = 0L; _ < n__5742__auto__15409; ++_) {
        Numbers.unchecked_multiply(123L, 456L);
    }
    return null;
}
```

Looks reasonable. So, is it really that fast? Let's double-check with Criterium:

```clj
(crit/quick-bench (unchecked-multiply 123 456))

;; Execution time mean : 6.694309 ns
```

That's quite different! What if we ask JMH? (You will learn how to run such
examples later.)

```java
@Benchmark
public long mul() {
    return 123L * 456L;
}

@Benchmark
public void mulWrong() {
    long x = 123L * 456L;
}

// Multiply.mul       2.445 ± 0.126 ns/op
// Multiply.mulWrong  0.329 ± 0.021 ns/op
```

`Multiply.mul` appears to be faster than the Criterium result but still slower
than the initial 0.3 nanoseconds. What's going on here? Turns out, in the case
of time/dotimes benchmark, and in `Multiply.mulWrong`, the JVM is smart enough
to remove the whole body of the loop since its result is not being used by
anyone. This optimization is called dead code elimination, and it's quite easy
to trigger when doing careless benchmarks. Criterium guards against it by
consuming the result of each iteration, and so does JMH.

Why use JMH then if Criterium already does fine? Let's consider another example.
We want to measure how long it takes to walk an array of 100000 elements
sequentially:

```clj
(let [sz 100000
      ^ints arr (int-array sz)]
  (crit/quick-bench
   (dotimes [i sz]
     (aget arr i))))

;; Execution time mean : 57.017862 µs
```

That's really fast, just 60 microseconds for the whole array, caches and all be
praised! But you are already feeling suspicious, aren't you?

```java
@State(Scope.Thread)
public static class BenchState {
    int[] arr = new int[size];
}

@Benchmark
public void walk(BenchState state, Blackhole bh) {
    for (int i = 0; i < size; i++)
        bh.consume(state.arr[i]);
}

// WalkArray.walk  3019.442 ± 426.008 us/op
```

Now, three milliseconds look much more convincing. We can believe that it's the
actual result, not the 60 microseconds we got earlier. Why did Criterium fail us
here? Indeed, Criterium prevents DCE for values that are returned by each
iteration, but it has no control over the internal loop — the one run by our
code. And JMH gives us this Blackhole object that can be used to forcefully
consume any intermediate value.

### How to use JMH

The setup I'm going to suggest is not very REPL-friendly. JMH can be used as a
library, but it is designed to act more like a framework. For example, JMH forks
a process for each benchmark to isolate them and minimize the effects that one
benchmark-related JIT and JVM behavior can have on other benchmarks.

Having said that, I'm far from being sure that my setup is the most optimal and
effective. [jmh-clojure](https://github.com/jgpc42/jmh-clojure) is another
effort to make JMH usage more similar to standard Clojure workflows. Perhaps,
someday I will write about my experience with it too.

#### Leiningen

Feel free to create a new project with `lein new`, but for the example I'm
giving, it is enough to make an empty directory.

The `project.clj` has to look like this:

```clj
(defproject jmh-lein "0.1.0-SNAPSHOT"
  :java-source-paths ["src/"]
  :dependencies [[org.clojure/clojure "1.9.0"]
                 [org.openjdk.jmh/jmh-core "1.21"]
                 [org.openjdk.jmh/jmh-generator-annprocess "1.21"]]
  :aliases {"jmh" ["exec" "-ep" "(bench.Multiply/run)"]})
```

Another file you need is `src/bench/Multiply.java`:

```java
package bench;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.*;
import org.openjdk.jmh.runner.options.*;
import java.util.*;
import java.util.concurrent.*;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 5, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 5, timeUnit = TimeUnit.SECONDS)
@Fork(value = 1)
public class Multiply {

    @Benchmark
    public long mul() {
        return 123L * 456L;
    }

    @Benchmark
    public void mulWrong() {
        long x = 123L * 456L;
    }

    public static void run() throws RunnerException {
        Options opt = new OptionsBuilder()
            .include(Multiply.class.getSimpleName())
            .build();

        new Runner(opt).run();
    }
}
```

This is our benchmarking class. By using different JMH annotations, we configure
how long to spend warming up the benchmark, for how much time to run it, which
units to output the results in. Each method in the class that is marked with
`@Benchmark` annotation will be run many times in a special JMH-managed loop.
The static method `run` is our entrypoint to the benchmark where we can inject
some extra configuration through `OptionsBuilder` object. This is the method that
our `jmh` alias is calling.

You can now run `lein jmh` in the terminal. Some debugging info will get
printed, and then the benchmarking iterations will proceed. In the end, you
should get something like this:

```
Benchmark          Mode  Cnt  Score   Error  Units
Multiply.mul       avgt    5  3.452 ± 0.574  ns/op
Multiply.mulWrong  avgt    5  0.583 ± 0.338  ns/op
```

#### Boot

Again, just make an empty directory and put this `build.boot` in it:

```clj
(set-env! :source-paths #{"src"}
          :dependencies '[[org.clojure/clojure "1.9.0"]
                          [org.openjdk.jmh/jmh-core "1.21"]
                          [org.openjdk.jmh/jmh-generator-annprocess "1.21"]])

(deftask jmh []
  (comp (javac)
        (with-pass-thru fs
          (let [cp (->> (conj (output-dirs fs)
                              (System/getProperty "fake.class.path")
                              *compile-path*)
                        (clojure.string/join java.io.File/pathSeparator))]
            (System/setProperty "java.class.path" cp)))
        (call :eval ['(bench.Multiply/run)])))
```

The main difference from Leiningen is that Boot doesn't automatically set the
correct `java.class.path` property, and JMH expects that. The `with-pass-thru`
step in the middle does two things — it combines `fake.class.path` property
(this is where Boot keeps the list of all dependencies) and also the output dirs
from the `(javac)` step, and then it sets it all into the `java.class.path`
property.

You should also create `src/bench/Multiply.java` file with the same content as
in Leiningen version. You can now run `boot jmh` from the terminal.

### A more interesting example

Now that we've dealt with setting up the environment, let's make a benchmark
that utilizes more JMH features. How about observing the effects of branch
prediction? We are going to reproduce one very popular StackOverflow question —
[Why is it faster to process a sorted array than an unsorted
array?](https://stackoverflow.com/questions/11227809/why-is-it-faster-to-process-a-sorted-array-than-an-unsorted-array)

Branch prediction is a mechanism inside the CPU that can speculatively pick a
more traveled branch of the condition before the actual check has been made.
[This article](https://danluu.com/branch-prediction/) provides an in-depth look
at the history of branch prediction.

Anyway, here's the code of the benchmark:

```java
package bench;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.*;
import org.openjdk.jmh.runner.options.*;
import java.util.*;
import java.util.concurrent.*;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 5, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 5, timeUnit = TimeUnit.SECONDS)
@State(Scope.Benchmark)
@Fork(value = 1)
public class BranchPrediction {

    @Param({"1000", "10000", "100000"})
    public static int size;

    @State(Scope.Thread)
    public static class BenchState {

        int[] unsorted, sorted;

        @Setup(Level.Iteration)
        public void prepare() {
            // Create an array and fill it with random numbers in range 0..size.
            unsorted = new int[size];
            for (int i = 0; i < size; i++)
                unsorted[i] = ThreadLocalRandom.current().nextInt(size);

            // Make a sorted array from the unsorted array.
            sorted = unsorted.clone();
            Arrays.sort(sorted);
        }
    }

    public long sumArray(int[] a) {
        long sum = 0;
        // Threshold is the median value in the array.
        int thresh = size / 2;
        for (int el : a)
            // Sum all array elements that are lower than the median.
            if (el < thresh)
                sum += el;
        return sum;
    }

    @Benchmark
    public long unsortedArray(BenchState state) {
        return sumArray(state.unsorted);
    }

    @Benchmark
    public long sortedArray(BenchState state) {
        return sumArray(state.sorted);
    }

    public static void run() throws RunnerException {
        Options opt = new OptionsBuilder()
            .include(BranchPrediction.class.getSimpleName())
            .build();

        new Runner(opt).run();
    }
}
```

A couple of new things are introduced in this benchmark. The `@Param` annotation
above `size` will make the benchmark run separately for each provided value of
`size`. The internal `BenchState` class is used as a holder of state that should
be initialized once. We use it to create two arrays — one with random numbers in
0..size range and the other with the same numbers but sorted.

The two benchmarks are doing the same thing — walking over the array and adding
the elements that are lower than size/2. Our hypothesis is that doing this on a
sorted array should be faster because of the branch prediction. Let's run and
see:

```
Benchmark                       (size)  Mode  Cnt    Score    Error  Units
BranchPrediction.sortedArray      1000  avgt    5    0.560 ±  0.185  us/op
BranchPrediction.sortedArray     10000  avgt    5    4.694 ±  0.526  us/op
BranchPrediction.sortedArray    100000  avgt    5   48.540 ± 23.480  us/op
BranchPrediction.unsortedArray    1000  avgt    5    0.760 ±  0.072  us/op
BranchPrediction.unsortedArray   10000  avgt    5   13.669 ±  2.409  us/op
BranchPrediction.unsortedArray  100000  avgt    5  370.335 ± 11.986  us/op
```

Except for `size=1000`, we see that the sorted array is consistently faster in
this benchmark.

### JMH profilers

JMH killer feature is the ability to attach different profilers to the
benchmark. One profiler we'll try to use today is `perfnorm` which measures
different CPU performance counters and normalizes the results per benchmark
iteration.

First, you'll need to install `perf` (sorry, Linux only). Then, change the
`Options` declaration to:

```java
Options opt = new OptionsBuilder()
    .include(BranchPrediction.class.getSimpleName())
    .addProfiler("perfnorm")
    .build();
```

And just run the benchmark again. Here's what I got (redacted):

```
Benchmark                                     (size)  Mode  Cnt        Score   Error  Units
BranchPrediction.sortedArray                    1000  avgt    5        0.593 ± 0.041  us/op
BranchPrediction.sortedArray:branch-misses      1000  avgt             2.034           #/op
BranchPrediction.unsortedArray                  1000  avgt    5        0.696 ± 0.026  us/op
BranchPrediction.unsortedArray:branch-misses    1000  avgt             0.288           #/op

BranchPrediction.sortedArray                  100000  avgt    5       53.436 ± 5.329  us/op
BranchPrediction.sortedArray:branch-misses    100000  avgt            13.268           #/op
BranchPrediction.unsortedArray                100000  avgt    5      378.581 ± 0.818  us/op
BranchPrediction.unsortedArray:branch-misses  100000  avgt         49880.070           #/op
```

Two observations are to be made here. For `size=100000`, the unsorted array
benchmark indeed has much more branch misses per iteration than the sorted
variant (49880 vs. 13). This explains why the sorted array benchmark is so much
faster.

But for `size=1000`, the difference in performance is almost negligible. What's
even more surprising is that the unsorted array has fewer branch misses than the
sorted one (0.3 vs. 2.0)! I hypothesize that the branch prediction machinery was
able to "learn" the whole unsorted array since it's not too big. At the same
time, the prediction for the sorted array has not become as sophisticated and it
consistently mispredicts the two pivots in the sorted array (at the beginning
and in the middle).

### Conclusions and future work

You've probably noticed that we haven't actually benchmarked any Clojure code
this time. It is because JMH is quite complicated and I want to immerse the
readers into it gradually. I will certainly get to benchmarking Clojure in the
following posts.

JMH is too big for me to describe all its features. Thankfully, there are plenty
of other materials (linked to in References) that will help you customize JMH
and write your own sophisticated benchmarks.

### References

- Aleksey Shipilёv's
  [slides](https://shipilev.net/talks/devoxx-Nov2013-benchmarking.pdf) and
  [talk](https://vimeo.com/78900556) about JMH. He has many of those, check
  Google for more.
- [JMH
  samples](http://hg.openjdk.java.net/code-tools/jmh/file/tip/jmh-samples/src/main/java/org/openjdk/jmh/samples/)
  is full of nicely commented examples of what JMH is capable of.
- Nitsan Wakart has a whole page dedicated to different [JMH
  resources](http://psy-lob-saw.blogspot.com/p/jmh-related-posts.html) on the web.

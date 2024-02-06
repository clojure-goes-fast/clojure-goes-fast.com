{:title "time+"
 :page-index 1
 :layout :kb-page}

The most naive (but oh-so-convenient) way to benchmark something you've just
wrote is to wrap your code in a
[time](https://clojuredocs.org/clojure.core/time) macro. You'll also have to
wrap the code in [dotimes](https://clojuredocs.org/clojure.core/dotimes) so that
it gets executed enough times for JIT to trigger and for the measurement setup
overhead to be amortized:

```clj
user=> (time
        (dotimes [_ 1e6]
          (reduce + (map #(/ % 100.0) (range 100)))))
"Elapsed time: 1718.769042 msecs"
nil
```

This works; however, doing it this way causes two mild annoyances:

- How to choose the argument for `dotimes`? We usually have an idea of how long
  we want the benchmark to run (say, a few seconds), but don't know from the
  get-go how many times should the benchmarked code be executed to achieve that
  running time.
- The final result has to be manually divided by the number of iterations to
  obtain the running time of a single invocation of the benchmarked code.

Both of these irritations can be solved by the following custom macro that I use
for quick-and-dirty benchmarking when I'm in the active process of modifying the
code. Adding it to the system-wide profile in Leiningen or tools.deps (see [this
guide](/clojure-goes-fast.com/blog/system-wide-user-clj/)) will make it readily
available during the development of any project of yours.

*NB: you should absolutely use Criterium or JMH to obtain accurate results once
you are done changing the code. The following macro adds convenience during
active development but does not replace proper benchmarking tools. It is also
not suitable for any kind of microbenchmarking.*

```clj
(let [time*
      (fn [^long duration-in-ms f]
        (let [^com.sun.management.ThreadMXBean bean (java.lang.management.ManagementFactory/getThreadMXBean)
              bytes-before (.getCurrentThreadAllocatedBytes bean)
              duration (* duration-in-ms 1000000)
              start (System/nanoTime)
              first-res (f)
              delta (- (System/nanoTime) start)
              deadline (+ start duration)
              tight-iters (max (quot (quot duration delta) 10) 1)]
          (loop [i 1]
            (let [now (System/nanoTime)]
              (if (< now deadline)
                (do (dotimes [_ tight-iters] (f))
                    (recur (+ i tight-iters)))
                (let [i' (double i)
                      bytes-after (.getCurrentThreadAllocatedBytes bean)
                      t (/ (- now start) i')]
                  (println
                   (format "Time per call: %s   Alloc per call: %,.0fb   Iterations: %d"
                           (cond (< t 1e3) (format "%.0f ns" t)
                                 (< t 1e6) (format "%.2f us" (/ t 1e3))
                                 (< t 1e9) (format "%.2f ms" (/ t 1e6))
                                 :else (format "%.2f s" (/ t 1e9)))
                           (/ (- bytes-after bytes-before) i')
                           i))
                  first-res))))))]
  (defmacro time+
    "Like `time`, but runs the supplied body for 2000 ms and prints the average
  time for a single iteration. Custom total time in milliseconds can be provided
  as the first argument. Returns the returned value of the FIRST iteration."
    [?duration-in-ms & body]
    (let [[duration body] (if (integer? ?duration-in-ms)
                            [?duration-in-ms body]
                            [2000 (cons ?duration-in-ms body)])]
      `(~time* ~duration (fn [] ~@body)))))
```

It may look like there's a lot going on here, but the logic is quite
straightforward:

1. Run the supplied code once and measure how long it took.
2. Estimate how many more times this code should be run to achieve the desired
   total time (2000 ms or an explicit argument).
3. Repeatedly run the code, periodically checking if we've hit the deadline.
4. Also, calculate how many bytes were allocated while the benchmark was
   running.
5. Divide the total time and allocated bytes by the number of iterations we
   managed to squeeze in.

Using `time+` is identical to how `time` is used, except that the repetition
is performed automatically:

```clj
user=> (time+ (reduce + (map #(/ % 100.0) (range 100))))

Time per call: 1.78 us   Alloc per call: 6,320b   Iterations: 1122541
```

After running the code for 2 seconds, `time+` prints the time and allocated
bytes per iteration, and the number of iterations. You can specify the total
time in milliseconds as the first argument to the macro:


```clj
user=> (time+ 5000 (Thread/sleep 10))

Time per call: 12.26 ms   Alloc per call: 1b   Iterations: 430
```

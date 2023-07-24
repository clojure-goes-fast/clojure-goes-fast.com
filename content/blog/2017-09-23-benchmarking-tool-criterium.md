{:title "Benchmarking tool: Criterium"
 :date-published "2017-09-23"
 :date-updated "2023-06-09"
 :reddit-link "https://www.reddit.com/r/Clojure/comments/7241ls/benchmarking_tool_criterium/"}

_Updated on 2023-06-09: revised numbers, added section about
[time+](http://clojure-goes-fast.com/kb/benchmarking/time-plus/)._  
_Runnable code for this post can be found
[here](https://github.com/clojure-goes-fast/clojure-goes-fast.com/tree/master/code/criterium)._

The most basic question a young performance acolyte may ask is "How long does it
take to run my code"? Clojure is capable of answering such questions out of the
box with the [time](http://clojuredocs.org/clojure.core/time) macro. `time`
takes a single expression and executes it. When the expression finishes running,
it will print the time it took in milliseconds.

Let's see how quickly Clojure can calculate the mean value of a sequence (using
a terribly inefficient algorithm). A **wrong** way to do this would be:

```clojure-repl
user=> (time (reduce + (map #(/ % 100.0) (range 100))))
"Elapsed time: 0.252375 msecs"
49.5
```

Looks like a hundred sums and divisions take roughly 250 microseconds. But this
is very far from the truth. Unless the complete execution takes a few seconds,
the results of `time` will include many things besides your actual expression.
First of all, it will count in the overall execution overhead. If the running
time of the expression you measure is small, that overhead time will contribute
to the majority of the time spent. For example, on my machine just `(time nil)`
finishes in approximately 15 microseconds.

Second inaccuracy comes from JVM itself, exactly from
its [JIT](https://en.wikipedia.org/wiki/Just-in-time_compilation) step. Since
you've just entered the code in the REPL, it is the first time the JVM has seen
it. JVM doesn't know yet whether this code will be executed again, so the code
gets interpreted. Interpretation is slower than running the compiled code, but
JIT-compiling an expression means paying some cost upfront. JVM wants to be sure
that the investment gonna pay out. After running the same code certain number of
times, the JVM will decide it had enough and proceed to JIT-compiling it and
running the compiled version from there on.

The factors mentioned above lead us to a conclusion that we should run the
expression inside `time` multiple times to get a more meaningful result:

```clojure-repl
user=> (time (dotimes [_ 1e6] (reduce + (map #(/ % 100.0) (range 100)))))
"Elapsed time: 1133.6695 msecs"
nil
```

If we divide the resulting time by 10<sup>6</sup>, we'll get the rough time of
each iteration to be 1.1 microseconds. Two hundred times less than in the
previous measurement!

Using this simple combo of `time`
and [dotimes](https://clojuredocs.org/clojure.core/dotimes), you can quite
accurately estimate how long your functions work. However, you have to divide
the final time by the number of iterations, and remember to do a warm-up run for
JIT to trigger, and do a few more runs to consider the deviation.

## time+

As a remedy to `time/dotimes` two inconveniences (guessing the adequate number
of iterations and having to divide the final time by it), one can use the macro
I called [time+](http://clojure-goes-fast.com/kb/benchmarking/time-plus/). It is
a simple paste-and-go macro that you can include into your [system-wide Clojure
profile](https://gist.github.com/alexander-yakushev/63515455759e66bfa19dbaa126fccf56)
and then use in any project that you start locally.

Using `time+` is identical to how `time` is used, except that the repetition
is performed automatically:

```clojure-repl
user=> (time+ (reduce + (map #(/ % 100.0) (range 100))))
Time per call: 1.78 us   Alloc per call: 6,320b   Iterations: 1122541
nil
```

After running the code for 2 seconds, `time+` prints the time and allocated
bytes per iteration, and the number of iterations. You can specify the total
time in milliseconds as the first argument to the macro:


```clojure-repl
user=> (time+ 5000 (Thread/sleep 10))
Time per call: 12.26 ms   Alloc per call: 1b   Iterations: 430
nil
```

## Criterium

[Criterium](https://github.com/hugoduncan/criterium) is an advanced but easy to
use benchmarking tool for Clojure. It is designed as a robust replacement for
`time` that takes into account some common benchmarking pitfalls. To use
Criterium, you should add this to your dependencies:

```clojure
[criterium "0.4.6"] ; Per the time of writing
```

And require it in the namespace where you want to measure:

```clojure
(require '[criterium.core :as criterium])
```

*If you find yourself using Criterium often, you ought to add it to your
system-wide development profile
in
[Leiningen](https://github.com/technomancy/leiningen/blob/master/doc/PROFILES.md#default-profiles) or
[tools.deps](https://github.com/boot-clj/boot/wiki/Configuring-Boot#configuring-your-clojure-project).*

`quick-bench` is the fastest way to benchmark an expression:

```clojure-repl
user=> (criterium/quick-bench (reduce + (map #(/ % 100.0) (range 100))))

Evaluation count : 450582 in 6 samples of 75097 calls.
             Execution time mean : 1.469145 µs
    Execution time std-deviation : 332.754413 ns
   Execution time lower quantile : 1.317431 µs ( 2.5%)
   Execution time upper quantile : 2.044992 µs (97.5%)
                   Overhead used : 1.891462 ns
```

That "fastest way" surely took a while. Criterium spent some time warming up the
JIT, and then ran our expression 450582 times, and the average execution time
was 1.32 microseconds. That's approximately what we figured with our
time+dotimes approach.

Let's check how precise the JVM is when going to sleep for short timespans:

```clojure-repl
user=> (criterium/quick-bench (Thread/sleep 10))

Evaluation count : 48 in 6 samples of 8 calls.
             Execution time mean : 12.353675 ms
    Execution time std-deviation : 258.636937 µs
   Execution time lower quantile : 11.857332 ms ( 2.5%)
   Execution time upper quantile : 12.521358 ms (97.5%)
                   Overhead used : 1.891462 ns

Found 1 outliers in 6 samples (16.6667 %)
	low-severe	 1 (16.6667 %)
 Variance from outliers : 13.8889 % Variance is moderately inflated by outliers
```

By now you may have noticed yet another nice feature of Criterium — it
automatically adjusts the benchmark run time according to the execution time of
the measured expression. In other words, if the expression is fast, Criterium
will run it plenty of times, but if a single iteration is quite slow, it will be
executed fewer times so that you don't get too bored waiting.

The `bench` macro is claimed to be more accurate than `quick-bench`, but in
practice, it runs for much longer and doesn't yield significantly different
results most of the time. You should consider using it only when you need to
evaluate something tiny and volatile. Criterium also has many other facilities
for controlling the output and customizing the benchmarking process; you should
check the [API documentation](https://cljdoc.org/d/criterium/criterium/0.4.6/api/criterium.core)
to learn what else is in there.

## Common mistakes

You will get wrong benchmarking results if the evaluated expression contains
unrealized laziness. Criterium is also susceptible to this:

```clojure-repl
user=> (criterium/quick-bench (map #(Math/tan %) (range 1000)))

Evaluation count : 48000000 in 6 samples of 8000000 calls.
             Execution time mean : 9.986185 ns
    Execution time std-deviation : 1.408634 ns
   Execution time lower quantile : 8.914700 ns ( 2.5%)
   Execution time upper quantile : 11.779724 ns (97.5%)
                   Overhead used : 1.891462 ns

user=> (criterium/quick-bench (doall (map #(Math/tan %) (range 1000))))

Evaluation count : 5844 in 6 samples of 974 calls.
             Execution time mean : 102.158922 µs
    Execution time std-deviation : 1.315074 µs
   Execution time lower quantile : 100.751104 µs ( 2.5%)
   Execution time upper quantile : 104.011188 µs (97.5%)
                   Overhead used : 1.891462 ns
```

10ns versus 102us (the latter is correct) — quite a difference! Also, keep in
mind that `doall/dorun` do not traverse complex data structures. Let's look at
this slightly convoluted example (remember
that [repeatedly](http://clojuredocs.org/clojure.core/repeatedly) returns a lazy
sequence):

```clojure
(defn full-lazy []
  (repeatedly 10 (fn [] (map #(Math/tan %) (range 1000)))))

(defn outer-doall []
  (doall
   (repeatedly 10 (fn [] (map #(Math/tan %) (range 1000))))))

(defn full-eager []
  (doall
   (repeatedly 10 (fn [] (doall
                          (map #(Math/tan %) (range 1000)))))))

(doseq [f [#'full-lazy #'outer-doall #'full-eager]]
  (let [result (criterium/quick-benchmark* f {})]
    (criterium/report-point-estimate (str f) (:mean result))))

;                #'demo/full-lazy : 9.938479 ns
;              #'demo/outer-doall : 590.588240 ns
;               #'demo/full-eager : 1.083767 ms
```

As you can see, it is not enough to force only the top-level evaluation, you
must make sure that no lazy sequences hide internally.

In the last example, we demonstrated one of Clojure's main powers — it's
hackability. We are not only able to benchmark Clojure code, but also use other
Clojure code to facilitate benchmarking. Instead of running all three benchmarks
manually and then visually trying to track the relevant output, we used
Criterium's `quick-benchmark*` function (which returns the result as data) and
molded the output into what we needed.

## Conclusion

Criterium is a convenient tool to approximate the performance of your code. It
might seem that it doesn't offer much over `time`, but Criterium actually
produces more reliable results. Still, it shouldn't be the only tool in your
performance arsenal — there are many others that we will discuss very soon.

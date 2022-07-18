{:title "Benchmarking tool: Criterium"
 :date-published "2017-09-23"
 :reddit-link "https://www.reddit.com/r/Clojure/comments/7241ls/benchmarking_tool_criterium/"}

The most basic question a young performance acolyte may ask is "How long does it
take to run my code"? Clojure is capable of answering such questions out of the
box with the [time](http://clojuredocs.org/clojure.core/time) macro. `time`
takes a single expression and executes it. When the expression finishes running,
it will print the time it took in milliseconds.

Let's see how quickly Clojure can calculate the mean value of a sequence (using
a terribly inefficient algorithm). A **wrong** way to do this would be:

```clojure-repl
user=> (time (reduce + (map #(/ % 100.0) (range 100))))
"Elapsed time: 2.380569 msecs"
49.5
```

Looks like a hundred sums and divisions take roughly 2.5 milliseconds. But this
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
"Elapsed time: 3458.404229 msecs"
nil
```

If we divide the resulting time by 10<sup>6</sup>, we'll get the rough time of
each iteration to be 3.5 microseconds. Almost a thousand times less than in the
previous measurement!

Using this simple combo of `time`
and [dotimes](https://clojuredocs.org/clojure.core/dotimes), you can quite
accurately estimate how long your functions work. However, you have to divide
the final time by the number of iterations, and remember to do a warm-up run for
JIT to trigger, and do a few more runs to consider the deviation. Fortunately,
there is a Clojure library that can do all this for you.

## Criterium

[Criterium](https://github.com/hugoduncan/criterium) is an advanced but easy to
use benchmarking tool for Clojure. It is designed as a robust replacement for
`time` that takes into account some common benchmarking pitfalls. To use
Criterium, you should add this to your dependencies:

```clojure
[criterium "0.4.4"] ; Per the time of writing
```

And require it in the namespace where you want to measure:

```clojure
(require '[criterium.core :as criterium])
```

*If you find yourself using Criterium often, you ought to add it to your
system-wide development profile
in
[Leiningen](https://github.com/technomancy/leiningen/blob/master/doc/PROFILES.md#default-profiles) or
[Boot](https://github.com/boot-clj/boot/wiki/Configuring-Boot#configuring-your-clojure-project).*

`quick-bench` is the fastest way to benchmark an expression:

```clojure-repl
user=> (criterium/quick-bench (reduce + (map #(/ % 100.0) (range 100))))

Evaluation count : 192036 in 6 samples of 32006 calls.
             Execution time mean : 3.279129 µs
    Execution time std-deviation : 97.343802 ns
   Execution time lower quantile : 3.172668 µs ( 2.5%)
   Execution time upper quantile : 3.411585 µs (97.5%)
                   Overhead used : 1.821686 ns
```

That "fastest way" surely took a while. Criterium spent some time warming up the
JIT, and then ran our expression 192036 times, and the average execution time
was 3.28 microseconds. That's approximately what we figured with our
time+dotimes approach.

Let's check how precise the JVM is when going to sleep for short timespans:

```clojure-repl
user=> (criterium/quick-bench (Thread/sleep 10))

Evaluation count : 60 in 6 samples of 10 calls.
             Execution time mean : 11.789785 ms
    Execution time std-deviation : 193.731985 µs
   Execution time lower quantile : 11.550902 ms ( 2.5%)
   Execution time upper quantile : 12.082700 ms (97.5%)
                   Overhead used : 1.572016 ns

Found 2 outliers in 6 samples (33.3333 %)
	low-severe	 1 (16.6667 %)
	low-mild	 1 (16.6667 %)
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
check the [API documentation](http://hugoduncan.github.com/criterium/0.4/api/)
to learn what else is in there.

## Common mistakes

You will get wrong benchmarking results if the evaluated expression contains
unrealized laziness. Criterium is also susceptible to this:

```clojure-repl
user=> (criterium/quick-bench (map #(Math/tan %) (range 1000)))

Evaluation count : 24038142 in 6 samples of 4006357 calls.
             Execution time mean : 24.094502 ns
    Execution time std-deviation : 0.765800 ns
   Execution time lower quantile : 22.932468 ns ( 2.5%)
   Execution time upper quantile : 24.905587 ns (97.5%)
                   Overhead used : 1.572016 ns

user=> (criterium/quick-bench (doall (map #(Math/tan %) (range 1000))))

Evaluation count : 5268 in 6 samples of 878 calls.
             Execution time mean : 112.170485 µs
    Execution time std-deviation : 1.424520 µs
   Execution time lower quantile : 110.919695 µs ( 2.5%)
   Execution time upper quantile : 114.289706 µs (97.5%)
                   Overhead used : 1.572016 ns
```

24ns versus 112us (the latter is correct) — quite a difference! Also, keep in
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

;           #'boot.user/full-lazy : 21.647082 ns
;         #'boot.user/outer-doall : 1.158899 µs
;          #'boot.user/full-eager : 1.210917 ms
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

{:title "Clojure's deadly sin"
 :date-published "2023-07-27 18:30:00"
 :og-image "/img/posts/laziness-they-have-no-idea.webp"
 :reddit-link "https://reddit.com/r/Clojure/comments/15b5dj9/clojures_deadly_sin/"}

This article is about laziness in Clojure. It is intended to be a comprehensive
and objective (however possible) critique of lazy sequences as a _feature_. In
no way do I want this to be a judgment of the _decision_ to make Clojure lazy.
Clojure the language is by no means formulaic; creating it involved making a
plethora of impactful choices. We can judge by Clojure's longevity that the
total package has been a success, but it's only natural that some decisions
proved to be more accurate than others. Finally, this is not meant to criticize
the _people_ behind Clojure. Hindsight doesn't need glasses; it is incredibly
tough to write a language (let alone a successful one) and easy to pick on its
perceived shortcomings. Yet, I'm still willing to do the latter because I
believe my diatribe can be helpful.

My goal is to align the Clojure community's stance on laziness. Times and times
again, the developers find themselves grappling with the complexities of the
lazy approach, attributing their struggles to the lack of understanding of the
Clojure Way[[1]](#fn1)<a name="bfn1"></a>. I want to prove that many things make
up the Clojure Way, and laziness doesn't have to be a defining characteristic. I
want programmers to reduce or eliminate their reliance on laziness and not feel
guilty about it just because laziness has been deeply ingrained in Clojure since
its inception.

Only time will tell if writing this is beneficial, but I'm willing to try. Also,
sorry about the [clickbait
title](https://en.wikipedia.org/wiki/Sloth_(deadly_sin)), it was too juicy to
pass up.

### What is laziness?

Lazy evaluation, also known as deferred execution, is a programming language
feature that delays producing the result of computation until the value is
explicitly needed by some other evaluation. There are several different
perspectives on how one can look at it, they are all similar, but each can give
you some fresh insight:

1. **Separation of declaration and execution in time.** Writing an expression
   does not immediately produce the result. There are now two stages of
   computing the value that a programmer has to be aware of (whether it's good
   or bad will be discussed later).
2. **Declarative vs imperative**. The separation above encourages the developer
   to think about the program more in declarative terms. A program is not a
   line-by-line instruction for the computer to execute but more of a flexible
   recipe. This makes programming a bit closer to math, where writing a formula
   on paper does not immediately force you to solve it (and make your brain
   hurt). For the compiler, the declarative approach enables additional
   optimizations because it is free to reorder or even eliminate some steps of
   the execution.
3. **Program as a tree of evaluations.** With all values being lazy and
   dependent upon one another, the whole program becomes this declarative tree
   just waiting to be executed. Tap the root — the entrypoint — and the tree
   will recursively walk itself, compute from the leaves downwards[[2]](#fn2)<a
   name="bfn2"></a>, and collapse into a single result. Leaves and branches that
   are not connected to the root are left unevaluated.
4. **Pull vs push.** If you like this analogy more, lazy evaluation is a pull
   approach. Nothing gets produced until it is explicitly called for (pulled).

In pervasively lazy languages like Haskell, every expression produces a lazy
value. It won't even compute `2 + 3` for you unless something else needs it. By
invoking the "program as a tree" reasoning, it becomes apparent that "something
else" ultimately has to affect the outer world somehow, to have a **side
effect** — print to screen, write to a file, etc. Without side effects, a lazy
program is a festive cookbook recipe you never bake.

The _principles_ of lazy evaluation are easy to simulate in any language that
supports wrapping arbitrary code into a block and giving it a name (anonymous
functions, anonymous classes — they all work). In Clojure, that would be a plain
lambda or a dedicated `delay` construct:

```clj
(fn [] (+ 2 3)) ;; As lazy as it gets

(delay (+ 2 3)) ;; Similar, but the result is computed once and cached.
```

What distinguishes laziness as a language feature rather than a technique is
that it occurs automatically and transparently for the user. The code that
consumes a value doesn't have to know whether the value is lazy or not, the API
is exactly the same, and there is no way to tell (actually, there sometimes is,
but it's rarely necessary). In contrast, a `delay` also represents a deferred
computation, but it has to be explicitly dereferenced with a `@`.

### Laziness in Clojure

While Clojure was inspired by Haskell in multiple ways, its approach to laziness
is much more pragmatic. Laziness in Clojure is limited only to **lazy
sequences**. Note that we don't say "lazy collections" because a sequence is the
only collection that is lazy. For example, updating a hashmap is eager in
Clojure, while in Haskell, it would be lazy:

```clj
(assoc m :foo "bar") ;; Happens immediately
```

There are several sources whence a developer can obtain a lazy sequence:

1. The most common are the sequence-processing functions like `map`, `filter`,
   `concat`, `take`, `partition`, and so on. Such functions are _lazy-friendly_
   (they can accept lazy sequences and don't enforce their evaluation) and
   return a lazy sequence themselves (even if the supplied collection was not
   lazy).
2. Functions that produce infinite sequences: `iterate`, `repeat`, `range`.
3. Functions that provide a pull-based API to a usually limited resource:
   `line-seq`, `file-seq`.
4. Low-level lazy sequence constructors: `lazy-seq`, `lazy-cat`. Rarely used
   outside of the `clojure.core` namespace, where they serve as building blocks
   for higher-level sequence functions.

Let's look at the example code that involves lazy sequences:

```clj
(let [seq1 (iterate inc 1)      ; Infitite sequence of natural numbers,
                                ; obviously lazy.

      seq2 (map #(* % 2) seq1)  ; Arithmetic progression with step=2, still
                                ; infinite, lazy.

      seq3 (take 100 seq2)      ; Sequence of 100 items from the previous
                                ; sequence, lazy, nothing has happened yet.

      seq4 (map inc [1 2 3 4])  ; The result is lazy, even though the input is
                                ; a realized (non-lazy) vector.

      seq5 (concat seq3 seq4)]  ; Lazy inputs and lazy result.

  (vec seq5))                   ; The actual work finally starts here because we
                                ; convert sequence to a vector, and vector is
                                ; not a lazy collection.
```

The example above shows how some functions produce lazy sequences, some consume
them and retain the laziness, and some enforce the evaluation like `vec` does.
It takes some time to wrap your head around all that.

The reason why Clojure didn't go all lazy is that laziness is _expensive_. For
each value with a postponed computation, the runtime has to keep track of it and
remember the code to be executed and its context (local variables). A value is
internally replaced with a wrapper that holds all that information, a **thunk**
(Haskell term). Thunks often trigger additional memory allocations, occupy space
in memory, and introduce indirections that slow down the program execution. Many
of those inefficiencies can be alleviated by an advanced compiler like the one
in Haskell. But the design of Clojure prompts for a simple, straightforward
compiler, so a full-lazy approach in Clojure would likely have caused
performance problems.

But that is not enough. Even with just lazy sequences, the cost of creating a
thunk for each successor would be prohibitively high. To counter that, Clojure
employs a concept called **sequence chunking**. In simple terms, it means that
the "unit of laziness", the number of elements that get realized at a time, is
not 1 but 32. What this achieves is that when processing a large collection, the
overhead from the laziness machinery gets better amortized per item. Here's a
classic example of the chunking behavior:

```clj
(let [seq1 (range 100)
      seq2 (map #(do (print % " ") (* % 2)) seq1)]
  (first seq2))

;; Print output:
;; 0  1  2  3  4  5  6  7  8  9  10  11  12  13  14  15  16
;; 17  18  19  20  21  22  23  24  25  26  27  28  29  30  31
```

In this example, we introduced a side effect to the `map` step to observe how
many items it operates on. In the final step, we ask only for the first item of
the lazy collection, so it is natural to assume that just one item will get
printed inside the `map`, but no, we see 32 items printed. This is the effect of
chunking because when the lazy collection runs out of "realized" items, it
forces the evaluation of the next 32 items at once. All 32 are evaluated
regardless if the next step requires 1, 5, or 32 items. If we ask for 33 items,
64 items will get evaluated, and so on.

Lazy sequences in Clojure are **cached**, which means that the deferred values
are computed exactly once. The saved result is returned on subsequent access,
and the value is not computed anew. A short demonstration:

```clj
(let [s (time (map #(Thread/sleep %) (range 0 200 10)))]
  (time (doall s))
  (time (doall s)))

;; Elapsed: 0.1287 msecs   - map produces a lazy sequence, nothing has happened yet
;; Elapsed: 1970.8 msecs   - doall forced the evaluation
;; Elapsed: 0.0039 msecs   - values are already computed, not reevaluated the second time
```

This differentiates lazy collections from plain lambdas that don't retain the
evaluation result and make them closer to `delay`, which does.

Now you should know enough about the specifics of lazy evaluation in Clojure to
appreciate the following chapters. We'll start by enumerating the undisputable
benefits of lazy collections and follow with the unfortunate drawbacks and
complications.

### The good parts of laziness

#### Avoiding unnecessary work

The main value proposition of lazy sequences in Clojure (and the laziness in
other languages overall) is only computing what's needed. You can write the code
without thinking upfront about how much of the result the consumers will need
later. The inversion of control allows one to write code like this:

```clj
(defn parse-numbers-from-file [rdr]
  (->> (line-seq rdr)
       (map parse-long)))

(take 10 (parse-numbers-from-file (io/reader "some-file.txt")))
```

The function `parse-numbers-from-file` doesn't have to know how many lines will
ever be needed, it doesn't have to wonder whether it's wasteful to parse all the
lines. The code is written as if it parses everything, and the calling code will
later decide how much will actually be parsed.

#### Infinite sequences

We can't represent an infinite sequence using any eager collection since it
would take an infinite time to compute it. Instead, there are other ways an
infinite sequence could be represented — a streaming API of some sort or an
iterator. In the case of Clojure, lazy sequences serve as a fitting abstraction
for an infinite collection.

This makes for a great party trick — one of those language features that make
people go "wow" and get them interested[[3]](#fn3)<a name="bfn3"></a>. You write
`(iterate inc 1)` and get _all_ natural numbers, how cool is that[[4]](#fn4)<a
name="bfn4"></a>? And since most sequence processing functions are
lazy-friendly, you get to derive new infinite sequences which can stay infinite
up until you demand some bounded result. Wanna make an infinite Fibonacci
sequence? Go for it:

```clj
;; The API of `iterate` already recoils a little. We have to store each item
;; as a tuple of two numbers, and later drop the second number. This is because
;; `iterate` gives us access only to the immediate previous item.
(->> (iterate (fn [[a b]] [b (+ a b)]) [0 1])
     (map first)
     (drop 30) ;; This and the next step gives us 30th-40th elements of the sequence.
     (take 10))

=> (832040 1346269 2178309 3524578 5702887 9227465 14930352 24157817 39088169 63245986)
```

#### Acting like you have infinite memory

Because a lazy sequence can act as a stream and only hold a single element of
the sequence in memory (or, rather, a 32-item chunk), it can be used to process
large files or network payloads using familiar sequence-processing functions
without paying attention to the size of the dataset. We've already looked at
this example:

```clj
(->> (line-seq (io/reader "very-large-file.txt"))
     (map parse-long)
     (reduce +))
```

The file we pass to it may not fit into memory if loaded completely, but it
doesn't concern us and doesn't change the way we write code. `line-seq` returns
a sequence of _all_ lines in the file, and the laziness ensures that not all of
them are resident in memory at once. This gives the developer one less hurdle to
think about. In case they didn't think about it, the program might be more
robust because of laziness; say, the developer only tested such code on small
files, and the laziness assured the correct execution on large files too.

#### Unified sequence API

Lazy and non-lazy sequences, infinite sequences, larger-than-memory sequences
can all be worked with using the same collection of functions. Clojure designers
didn't have to implement individual functions for each subtype; therefore, you
didn't have to learn them. The language core is thus smaller, and there are
fewer potential bugs.

### The bad parts of laziness

In this section, I'll enumerate the problems that are either inherent to
laziness or incidental to its implementation in Clojure. Their order here is
arbitrary, but for each issue I'll give my personal opinion on how critical it
is.

Many problems arise from the fact that for laziness to be completely seamless,
it demands full referential transparency. But Clojure is a pragmatic language
that allows side effects anywhere and does not make it a priority to replace the
side-effecting approaches with purely functional wrappers. Lazy sequences don't
play well with side effects, as we will see many times in this section.

#### Error handling

In Haskell, error handling is achieved through special return values that may
contain either a successful result or an error (e.g.,
[Either](https://hackage.haskell.org/package/base-4.18.0.0/docs/Data-Either.html)).
Thus, errors in Haskell are first-class citizens of the regular evaluation flow
and don't conflict with the laziness of the language.

Clojure, however, uses Java's stack-based exception infrastructure as its
primary error-handling mechanism. This is the most pragmatic choice since any
other solution would require repackaging all exceptions that could be thrown by
the underlying Java code. This could have had tremendous performance
implications. Besides, in a dynamically typed Clojure, result-based error
handling would just not be as convenient as in a statically typed language.

So, we're stuck with exceptions. And we have lazy sequences. What can go wrong?
Consider the following bug that I'm sure 99% of all Clojure programmers ran into
at least once:

```clj
;; We need to generate a list of numbers 1/x.
(defn invert-seq [s]
  (map #(/ 1 %) s))

(invert-seq (range 10))
;; java.lang.ArithmeticException: Divide by zero
```

We wrote a function that accepts a list of numbers and produces a list of
numbers `1/x`. All's good until somebody gives us a sequence that contains a
zero. We want to protect ourselves from that, so we fix our function:

```clj
(defn safe-invert-seq [s]
  (try (map #(/ 1 %) s)
       ;; For the sake of the example, let's return an empty list if we
       ;; encounter a division by zero.
       (catch ArithmeticException _ ())))

(safe-invert-seq (range 10))
;; java.lang.ArithmeticException: Divide by zero
```

Turns out, the function did not get any safer from our fix. Even though it looks
like we wrapped the dangerous code in a `try-catch` block, the code inside
merely returns a lazy sequence. There's nothing to `catch` just yet. But by the
time the values are "pulled" from the lazy collection, the mapping code has
already left the `try-catch` block, and the raised exception crashes the
program. To truly fix this example, we have to watch for exceptions inside each
`map` iteration:

```clj
(defn really-safe-invert-seq [s]
  (map #(try (/ 1 %)
             (catch ArithmeticException _ ##Inf))
       s))

(really-safe-invert-seq (range 10))
;; => (##Inf 1 1/2 1/3 1/4 1/5 1/6 1/7 1/8 1/9)
```

Of all the problems created by laziness, I consider this to be a really serious
one. The Java approach to exception handling teaches us that wrapping code in
`try-catch` should handle the exceptions raised inside[[5]](#fn5)<a
name="bfn5"></a>. In the presence of lazy sequences, you can no longer rely on
that unless you enforce all lazy evaluation (the woes of which I'll mention
later). This problem is unexpected, it is frequent, and it causes anxiety. And
you can only deal with it by either ensuring there is no laziness in the wrapped
code or catching all exceptions within each lazy iteration step.

#### Dynamic bindings

Similar to exception handling, dynamic bindings in Clojure are stack-based. As
soon as the execution exits the `binding` form, the dynamic variable returns to
its previous value.

```clj
(def ^:dynamic *multiplier* 1)

(let [seq1 (binding [*multiplier* 100]
             (map #(* % *multiplier*) (range 10)))]
  (vec seq1))

;; => [0 1 2 3 4 5 6 7 8 9]
```

The root value of the dynamic variable `*multiplier*` is 1. We bind it to 100
and multiply a bunch of numbers by that variable. We would expect that the
numbers in `map` will be multiplied by 100. However, due to laziness, the actual
execution of `map` only happens at `(vec seq1)` step, and the binding is long
lost by that point.

One way to combat this is to wrap the function that would be executed lazily in
a special `bound-fn*` function. `bound-fn*` captures all dynamic values it sees
at the moment of its invocation and passes those values to the wrapped function.
It doesn't matter at which point of time the wrapped function will be executed;
it will receive the dynamic variables as if it ran immediately.

```clj
(let [seq1 (binding [*multiplier* 100]
             (map (bound-fn* #(* % *multiplier*))
                  (range 10)))]
  (vec seq1))

;; => [0 100 200 300 400 500 600 700 800 900]
```

In my opinion, this interaction with laziness significantly reduces the
usefulness of dynamic variables for anything important. Sure, multi-threading
also "breaks" dynamic variables, so laziness is not the only one to blame. But
this is something to be constantly aware of, and most of the time, it is easier
and more sensible to forgo dynamic variables in your code altogether.

#### Releasing resources

Somewhat similar to the previous two problems, freeing a previously obtained
resource (e.g., a file handle, a network socket) happens at a specific point in
time, and all the execution delays that come with laziness don't play well with
that _at all_. Here's another bug that should be familiar to pretty much
everyone:

```clj
(defn read-words []
  (with-open [rdr (io/reader "/usr/share/dict/words")]
    (line-seq rdr)))

(count (read-words))
;; java.io.IOException: Stream closed
```

The implementation of `with-open` opens the specified resource, executes the
body with that resource available, and finally closes the resource. Using
`with-open` particularly is not significant — you could write out the exact
steps manually, and the outcome would stay the same. Such resource management
implies that all the operations on the resource have to happen within that open
window, so you must be absolutely sure that no lazy code that still wants to use
the resource remains unexecuted after the resource is freed.

This kind of bug doesn't creep into my code often, but when it does, I despise
it and go on another sweep to clear the program of all possible laziness. I'd
call it a medium-sized problem.

#### Brittle for "big data" processing

Lazy sequences are convenient for processing "larger than memory" datasets.
However, speaking from personal experience, this approach is robust only as long
as the data access pattern is linear and straightforward. Back in the days when
I was enamored with laziness, I developed a program that had to chew through
multiple large files containing nested data, and I heavily relied on lazy
sequences to achieve it. The program eventually grew more complex, and the data
access moved from the trivial "fly-by streaming" to requiring aggregation,
transposition, and flattening. I ended up having zero understanding of how much
memory the program needed at any given point in time and virtually no confidence
that the already processed items would be promptly discarded to make room for
new items.

There is even an officially recognized mistake of [holding onto one's
head](https://clojure.org/reference/lazy#_dont_hang_onto_your_head) which is
relatively easy to make. If you retain the reference to the head of a large
sequence, the runtime would have to hold the entire sequence in memory as you
walk over it. Eventually, you'll run out of memory. Clojure compiler
aggressively nils out all local variables as soon as they are not used anymore,
a feature called [locals
clearing](https://groups.google.com/g/clojure/c/FLrtjyYJdRU/m/1gzChYsmTpsJ). It
exists solely to prevent some of the holding on the head bugs.

This is a major issue for me because it directly contradicts lazy sequences'
most exciting value prop. If a tool becomes unreliable under the conditions it
specifically claims to address, then the suitability of that tool should be
questioned.

#### Large/infinite sequence is a powder keg

You should exercise extreme caution when returning unbounded lazy sequences as
part of your API. The consumer has no way to tell if the result is safe to load
into the memory entirely except for relying on documentation. The consumer must
also know which functions are safe to invoke on a lazy collection and which can
blow up. Frequently, they will not know or think about these implications, and
things will go sour.

<center>
<figure class="figure">
<img class="img-responsive" src="/img/posts/laziness-they-have-no-idea.webp" style="max-height: 350px;">
<figcaption class="figure-caption text-center">
    I hope nobody touches me.
</figcaption>
</figure>
</center>

Even when you are certain that the sequence you return is not infinite or overly
large, there are still ways to screw up the users of your API. A good example
would be [Cheshire](https://github.com/dakrone/cheshire), whose function
[parse-stream](https://cljdoc.org/d/cheshire/cheshire/5.11.0/api/cheshire.core#parse-stream)
returns a lazy sequence if the top-level JSON object is an array. Combine that
with the problem of [releasing resources](#releasing-resources) and, God forbid,
asynchronous processing, and you get a bug that I once spent a literal hour to
figure out[[6]](#fn6)<a name="bfn6"></a>.

This issue deserves at least medium importance. Absent-minded handling of lazy
collections can lead to lurking problems that may remain hidden for years and
then surprise you in the most baffling ways.

#### Confusing side effects

As I said before, laziness is not a problem when the code is referentially
transparent. If you can freely substitute the evaluation with its result and
nothing changes for the observer, then laziness is okay. Thankfully, Clojure
does not enforce referential transparency, and you are allowed to add side
effects anywhere in your code. Then, all of a sudden, you witness this:

```clj
(defn sum-numbers [nums]
  (map println nums) ;; Let's print each number for debugging.
  (reduce + nums))

(sum-numbers (range 10))

;; => 45
;; But where are the printlns?
```

After fifteen minutes of distrustful debugging, googling, and/or asking on
[Clojurians](https://clojurians.slack.com/), you find out that the `map` call
never executes due to laziness and the fact that nobody consumes its return
value. Around that point, you also learn what you should have done differently
and carry that lesson through the rest of your life:

- Wrap the printing form in [`dorun`](https://clojuredocs.org/clojure.core/dorun)
  or [`doall`](https://clojuredocs.org/clojure.core/doall): `(dorun (map println
  nums))`.
- Use [`run!`](https://clojuredocs.org/clojure.core/run!) instead of `map`:
  `(run! println nums)`.

In my book, this is a manageable problem. It is feasible to remember whether you
might be dealing with laziness when you want to trigger side effects. This is
not to say that you'll succeed in it every time; bugs related to side effects
and laziness happen to experienced programmers too.

#### Convoluted benchmarking and profiling

Regardless of how functionally pure a programming language is, every function
will always have at least one side effect — the time spent to execute it (also,
memory allocations, disk I/O, and any other resource usage). By deferring the
execution to another point in time, the language makes it harder for the
programmer to understand where those CPU cycles are spent. Simple example using
[Criterium](https://clojure-goes-fast.com/blog/benchmarking-tool-criterium/):

```clj
(crit/quick-bench (map inc (range 10000)))

;;    Evaluation count : 34285704 in 6 samples of 5714284 calls.
;; Execution time mean : 16.188222 ns
;;                 ...
```

This result of 16 nanoseconds does not prove that Clojure is so amazingly swift,
but rather that you should be vigilant when benchmarking code that potentially
involves laziness. This is the result that you should have obtained:

```clj
(crit/quick-bench (doall (map inc (range 10000))))

;;    Evaluation count : 2088 in 6 samples of 348 calls.
;; Execution time mean : 299.631257 µs
;;                 ...
```

The same goes for profiling. Laziness and all the execution ambiguity that comes
with it make the hierarchical profiler view quite useless. Consider this example
and the flamegraph obtained with
[clj-async-profiler](/kb/profiling/clj-async-profiler/):

```clj
(defn burn [n]
  (reduce + (range n)))

(defn actually-slow-function [coll]
  (map burn coll))

(defn seemingly-fast-function [coll]
  (count coll))

(prof/profile
 (let [seq1 (repeat 10000 100000)
       seq2 (actually-slow-function seq1)]
   (seemingly-fast-function seq2)))
```

<center>
<figure class="figure">
<div class="downscale-iframe-66" style="height:300px">
<iframe src="/img/posts/laziness-fg1.html?hide-sidebar=true" style="height:450px"></iframe>
</div>
<figcaption class="figure-caption text-center">
    Profiler gets confused by laziness. <a href="/img/posts/laziness-fg1.html?hide-sidebar=false"
target="_blank">Click</a> to open.
</figcaption>
</figure>
</center>

On the flamegraph, you can see that most CPU time is attributed to
`seemingly-fast-function`, but `actually-slow-function` is nowhere to be found.
By now, it should be crystal clear to you what has happened —
`actually-slow-function` returned a lazy sequence and didn't do any work, while
`seemingly-fast-function` by calling the innocuous `count` triggered the entire
computation to be executed. This might be easy to interpret in a toy example,
but in real life, such execution migrations will surely bamboozle you.

If you don't measure the execution time of your programs often (too bad!), then
this drawback will not impact you much. I personally do that a lot, so for me,
this is a medium-to-large source of headache and another solid reason to avoid
laziness.

#### Inefficient iteration with sequence API

This problem is not caused by laziness directly. Instead, Clojure's sequence API
has to accommodate lazy collections, among other things, so it is quite
restrictive in what it can offer. Basically, Clojure's sequence interface
[ISeq](https://github.com/clojure/clojure/blob/master/src/jvm/clojure/lang/ISeq.java)
defines human-readable replacements for [car and
cdr](https://en.wikipedia.org/wiki/CAR_and_CDR). You can iterate pretty much
everything with this abstraction, but it is far from efficient for anything but
linked lists. Let's measure it using [time+](/kb/benchmarking/time-plus/):

```clj
;;;; Classic hand-rolled iteration with loop.

(let [v (vec (range 10000))]
  (time+
   (loop [[c & r :as v] (seq v)]
     (if v
       (recur r)
       nil))))

;; Time per call: 238.92 us   Alloc per call: 400,080b


;;;; doseq

(let [v (vec (range 10000))]
  (time+ (doseq [x v] nil)))

;; Time per call: 41.50 us   Alloc per call: 20,032b


;;;; run!

(let [v (vec (range 10000))]
  (time+ (run! identity v)))

;; Time per call: 42.65 us   Alloc per call: 24b
```

In the first snippet, we perform a basic, most flexible iteration with `loop`.
You usually resort to it in any non-trivial iteration scenario (when you have to
accumulate multiple different results at once or walk through the sequence in a
non-obvious manner). We see that it takes us 240 microseconds to merely iterate
over that vector, and 400KB worth of objects gets allocated along the way. The
second snippet uses `doseq`, which contains multiple chunking optimizations.
Iteration with `doseq` is **6 times faster** than with `loop`, producing **20
times less garbage** on the heap. Finally, the reduce-based `run!` offers the
same speed as `doseq` in this example while not allocating anything as it runs.

How big of a problem this is depends on how much you care about the performance.
For Clojure creators, it is important enough that more and more
collection-processing functions are using the
[IReduce](https://github.com/clojure/clojure/blob/master/src/jvm/clojure/lang/IReduce.java)
abstraction over ISeq.

#### Performance overhead

Like I said before, laziness is not free, and neither is it particularly cheap.
Consider an example[[7]](#fn7)<a name="bfn7"></a>:

```clj
;;;; Lazy map

(time+
 (->> (repeat 1000 10)
      (map inc)
      (map inc)
      (map #(* % 2))
      (map inc)
      (map inc)
      doall))

;; Time per call: 410.22 us   Alloc per call: 480,296b


;;;; Eager mapv

(time+
 (->> (repeat 1000 10)
      (mapv inc)
      (mapv inc)
      (mapv #(* % 2))
      (mapv inc)
      (mapv inc)))

;; Time per call: 63.66 us   Alloc per call: 28,456b


;;;; Transducers+into

(time+
 (into []
       (comp (map inc)
             (map inc)
             (map #(* % 2))
             (map inc)
             (map inc))
       (repeat 1000 10)))

;; Time per call: 43.95 us   Alloc per call: 6,264b


;;;; Transducers+sequence

(time+
 (doall
  (sequence (comp (map inc)
                  (map inc)
                  (map #(* % 2))
                  (map inc)
                  (map inc))
            (repeat 1000 10))))

;; Time per call: 86.16 us   Alloc per call: 102,776b
```

The lazy version in the example takes 410 µs and 480KB of trash to perform
several mappings over a sequence. The eager version utilizing `mapv` is **6.5
times faster** and allocates **16 times less** for the same result. And that is
with all the intermediate vectors being generated on each step. The
[transducer](https://clojure.org/reference/transducers) version is even faster
at 44 µs and even less garbage spawned because it fuses all the mappings into a
single step. As a last snippet, I show that composing the transformation steps
with transducers and producing a lazy sequence with `sequence` is still much
faster and allocation-efficient than building a processing pipeline with lazy
sequences directly.

I wanted to show the profiler results for the above examples, but with such
performance disparity, there is nothing to gain from them. The profile for the
lazy version is dominated by creating intermediate lazy sequences and walking
over them. The `mapv` version is mostly about updating TransientVectors.

Could it be that the lazy version is more efficient on shorter sequences? Let's
find out:

```clj
(time+ (doall (map inc (repeat 3 10))))
;; Time per call: 181 ns   Alloc per call: 440b

(time+ (mapv inc (repeat 3 10)))
;; Time per call: 159 ns   Alloc per call: 616b
```

As you can see, with the size of the input sequence as small as 3, `mapv` shows
comparable performance to `map`. Don't be afraid to use `mapv` where you don't
need laziness.

This downside of laziness is significant. A lot of Clojure code involves
walking over and modifying sequences, and 95% of those have no business being
lazy, so it's leaking performance on the floor for no reason.

#### No way to force everything

While `doall` makes sure that a lazy sequence you pass to it gets evaluated, it
only operates on the top level. If sequence elements are lazy sequences
themselves, they would not be evaluated. An artificial example:

```clj
(let [seq1 (map #(Thread/sleep %) (repeat 100 10))]
  (time (doall seq1)))

;; "Elapsed time: 1220.941875 msecs"
;; As expected - doall forced the lazy evaluation.

(let [seq1 (map (fn [outer]
                  (map #(Thread/sleep %) (repeat 100 10)))
                [1 2])]
  (time (doall seq1)))

;; "Elapsed time: 0.139 msecs"
;; Because lazy sequences were inside another sequence, doall did not force them.
```

The same is also true when lazy sequences are a part of some other data
structure, e.g., a hashmap.

```clj
(let [m1 {:foo (map #(Thread/sleep %) (repeat 100 10))
          :bar (map #(Thread/sleep %) (repeat 100 10))}]
  (time (doall m1)))

;; "Elapsed time: 0.01775 msecs"
;; Doall does not work on hashmaps and is not recursive.
```

You may encounter a scenario like this sometimes, and it is very annoying when
it happens. If you want to force immediate evaluation in such cases, your only
options are:

- If you have access to the code that produces those constituent lazy seqs,
  force them there.
- Use `clojure.walk` to walk the nested structure recursively and call `doall`
  on everything.
- Call `(with-out-str (pr my-nested-structure))` and discard the result.
  Printing the structure will walk it for you and realize any lazy sequences
  inside. This is the dirtiest and the most inefficient approach.

This is a medium-sized problem. It's not too frequent, but if you do run into
it, it will spoil your day.

#### Chunking is unpredictable

I've already mentioned that Clojure evaluates lazy collections in chunks of 32
items to amortize the cost of laziness. At the same time, this makes lazy
sequences unsuitable for cases when you want to control the production of every
single element of the sequence. Yes, you can hand-craft a sequence with
`lazy-seq` and then make sure to never call any function on it that uses
chunking internally. To me, this looks like another way to make your program
fragile.

To be honest, I have no idea how and when chunking works. As I was writing this
post, I stumbled upon this:

```clj
(let [seq1 (range 10)
      seq2 (map #(print % " ") seq1)]
  (first seq2))

;; 0  1  2  3  4  5  6  7  8  9
;; Uses chunking.


(let [seq1 (take 10 (range))
      seq2 (map #(print % " ") seq1)]
  (first seq2))

;; 0
;; Doesn't use chunking.
```

In the first example, we used `(range 10)` to produce a bounded lazy sequence,
and mapping over it used chunking. In the second example, we made an infinite
sequence of numbers with `(range)`, took a bounded slice of it with `take`, and
there was no chunking when being mapped over. I'm sure the veil would be lifted
if I read enough docs and the implementation code. But I have no desire to do
that. Instead, I don't use laziness anywhere where chunking could make a
difference, so this trouble doesn't bother me.

#### Duplicate functions

While Clojure's [sequence abstraction](https://clojure.org/reference/sequences)
greatly reduces code duplication and the need for type-specialized functions,
some repetitiveness still has made it into the language. For the large part, I
attribute that to laziness and the frequent need to avoid it.

- To map over a sequence, there is `map` and `mapv` (and also `run!`, but it is
  useful on its own, beyond discussing laziness). To filter, there is `filter`
  and `filterv`, and so on. Later versions of Clojure added a bunch of these
  v-suffix functions because, apparently, programmers often want to ensure eager
  evaluation (and receive the result as a PersistentVector).
- There are two list comprehension macros:
  [`for`](https://clojuredocs.org/clojure.core/for) and
  [`doseq`](https://clojuredocs.org/clojure.core/doseq). Yes, they are
  semantically different (`doseq` does not form a resulting sequence and should
  only be used for side effects, like `run!`). But I'd argue that if not for the
  requirement to consume and produce lazy sequences, those two macros could have
  had a common and much simpler implementation.
- Having to know and remember about `doall` and `dorun` also adds mental
  overhead.

None of this is a deal breaker, just something mildly irritating from a
perfectionist's perspective.

#### Mismatch between REPL and final program

In order to have an effective REPL experience, it is crucial for the programmer
to be confident that the REPL and the normal program execution behave the same.
The classic Clojure workflow presumes that you do most of your exploration in
the REPL, test the code, verify it, and finally incorporate it into the program.
This is the main feature of Clojure, its alpha and omega, its cornerstone. And
laziness compromises that, even if slightly.

The problem with laziness in the REPL is that you always implicitly consume the
result of the evaluation. REPL prints the result; hence, any lazy sequences,
even the nested ones, will be realized before being presented. But copy that
expression into the final program, and it might no longer be the case. In the
REPL, it is very easy to forget that you may be dealing with lazy sequences —
unless you treat everything in round parens as a potential hazard (perhaps, you
should!).

To me, this is a minor issue that you grow out of. There are other things to
keep track of when transitioning REPL code to the final program (dirty REPL
state, order of definitions, and so on), and you learn to accept that. Still,
every time you have to tell a beginner: "in the REPL, it's different" — a bit of
trust is eroded.

#### Enormous bytecode footprint of `for` and `doseq`

This is my personal silly gripe that should not be relevant to anyone else. List
comprehension macros `for` and `doseq` are sometimes very practical for mapping
over a collection, even without advanced features like filtering and splicing
nested iterations. But because they have to deal with laziness and chunking,
their expansion is absolutely massive. By using
[clj-java-decompiler](https://github.com/clojure-goes-fast/clj-java-decompiler)'s
`disassemble` facility, we can verify that and compare how much bigger a `for`
expansion is to a hand-rolled iterator-based loop. Alternatively, we can do it
by manually enabling AOT and comparing file sizes.

```clj
(binding [*compile-files* true
          *compile-path* "/tmp/test/"]
  (eval '(defn using-for [coll]
           (for [x coll]
             (inc x)))))

;; /tmp/test/ contains 4 files totalling 6030 bytes.


(binding [*compile-files* true
          *compile-path* "/tmp/test/"]
  (eval '(defn using-iterator [coll]
           (when coll
             (let [it (.iterator ^Iterable coll)]
               (loop [res (transient [])]
                 (if (.hasNext it)
                   (recur (conj! res (.next it)))
                   (persistent! res))))))))

;; /tmp/test/ contains 1 file with the size of 1832 bytes.
```

That extra bytecode will eventually be JIT-compiled to native code, further
polluting the instruction cache and hindering iTLB. Again, this is an incredibly
minor issue compared to everything listed above, but it makes me reluctant to
use `for` in situations where it would fit well otherwise.

### How to live with it

This article is already longer than anything I've ever written, and I still have
to provide guidance on what to do next. It is evident that _I don't like
laziness_. If I managed to prove my point to you, the reader, then take the
following suggestions as my personal mitigation strategy to reduce the negative
impact of laziness.

The most straightforward advice is to **avoid laziness when it's not needed**.
To achieve that, you need to follow these steps:

- Prefer v-suffixed functions (`mapv`, `filterv`) over lazy counterparts.
- Use [transducers](https://clojure.org/reference/transducers) and `(into []
  <xform> <coll>)` for complex multi-step processing.
- If you still like a `->>`-threaded pipeline better, finish it off with an
  eager last step or `doall` or `vec`.

If you are a library author, **don't return lazy sequences** in your public
functions. If you want to let the user control and limit the amount of data
processed by your code, consider having a transducer arity or returning an
[`eduction`](https://clojuredocs.org/clojure.core/eduction).

Refrain from building a processing paradigm around lazy sequences. It may seem
tempting to return a lazy sequence thinking that the user can save some
execution time by not consuming the full result. It almost never happens. First,
the result is rarely used only partially. Second, good performance is never
accidental. If the user is conscious of program performance and measures it,
they will find ways to cut down unnecessary work anyway.

In cases when you deal with infinite or exceedingly large sequences, regardless
if you are working on an application or a library, choose **explicit
representations** for them. It could again be an eduction, a Java stream, even
an Iterator, a cursor. Anything that more clearly signals the non-finite and
piecewise nature of the collection will evade most of the laziness problems I
described.

Transducers are overall an adequate replacement for lazy sequences. Perhaps they
are somewhat less convenient to experiment with interactively, but the benefits
they offer are solid. You may even move back from them into the lazy-land with
[`sequence`](https://clojuredocs.org/clojure.core/sequence) if needed.

If you agree with this article, **share it with others**. Show it to your
coworkers, discuss it, change the common perception. Make adjustments to your
code quality standards, weed out laziness during code review. Admit that it is
easier to fight lazy sequences in the codebase than to scold yourself for not
utilizing them properly.

Clojure will never drop lazy sequences because of backward compatibility, and
that is a good thing. It is in our power and control to not suffer from them
existing, and acknowledging the problem is the first step to overcoming it. I
hope I made my point clear; please tell me your opinion on this and if I missed
anything (since you might be too lazy to do that, I explicitly _ask_ for
feedback). Cheers.

#### Footnotes

1. <a name="fn1"></a><span> Yes, I'm projecting.</span>[↑](#bfn1)
2. <a name="fn2"></a><span> Upwards? Why are the trees in CS always upside
   down?</span>[↑](#bfn2)
3. <a name="fn3"></a><span> Many people are initially hooked by things that look
   spectacular (even if those things don't help much in everyday work) but stay
   for mundane benefits. Maximizing that first impression is vital for language
   adoption.</span>[↑](#bfn3)
4. <a name="fn4"></a><span> Despite `(range)` being shorter and more efficient,
   it doesn't look as magical.</span>[↑](#bfn4)
5. <a name="fn5"></a><span> Sure, multi-threading and callbacks already break
   this premise in both Java and Clojure. However, you are usually more aware
   when you use those. Laziness is more pervasive and
   incidental.</span>[↑](#bfn5)
6. <a name="fn6"></a><span> I tried to reproduce this for the blogpost but
   couldn't trigger it. Either something has been fixed in Cheshire, or the bug
   only surfaces under certain conditions. But I am 100% positive it happened to
   me!</span>[↑](#bfn6)
7. <a name="fn7"></a><span> The numbers in the example are picked in a way that
   all computed boxed numbers stay within the [Java Integer
   Cache](https://www.geeksforgeeks.org/java-integer-cache/). Otherwise, the
   execution time and allocations would be dominated by producing new Long
   objects.</span>[↑](#bfn7)

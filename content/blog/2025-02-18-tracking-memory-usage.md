{:title "Tracking memory usage with clj-memory-meter.trace"
 :date-published "2025-02-18 13:00:00"
 :og-image "/img/posts/cmm-trace-thumb.png"
 :reddit-link
 "https://old.reddit.com/r/Clojure/comments/1isen17/tracking_memory_usage_with_cljmemorymetertrace/?"}

Automatic memory management is probably JVM's biggest selling point. You don't
need to remember to clean up the stuff you've allocated — the garbage collector
will take care of it for you. You can "leak" memory if you leave live references
to allocated objects (e.g. store objects in static fields), making them
unreclaimable by the GC. Thus, as long as you don't do that, you can treat
memory as infinite and never run out of it. Right?

Definitely not. Memory is a limited resource, and you can't fill it with more
data than its capacity allows if all of that data is needed _at the same time_.
So, it becomes crucial not only to know how much memory your data structures
occupy but also the access patterns to those data structures. Does the algorithm
process one item at a time or expect the whole list to be in memory at once? Is
the result gradually written to disk, or is it accumulated in memory first?
These questions may not matter when the data size is small and doesn't ever get
close to memory limits. But when they do...

Running out of memory is a nasty failure mode on JVM because it's often not
immediately apparent that it happens. Remember how the GC exists to clean up
unused objects? GC is a relatively expensive process, so the JVM usually runs it
when the remaining memory space is low. As we occupy more and more heap with
live (unreclaimable) objects, JVM starts to run the GC much more often. But
those GC cycles cannot free much memory if most of the allocated stuff is
"needed". So, at a certain point, JVM will just run the GC back-to-back, each
cycle freeing some measly bytes of heap. As the runtime is occupied with
constantly running GC, our application makes almost no progress. From the
outside, the program looks very busy (100% CPU usage), but no useful work is
being done. This may even last for minutes until the JVM finally graces us with
an
[OutOfMemoryError](https://docs.oracle.com/javase/8/docs/api/java/lang/OutOfMemoryError.html).
Oh, the humanity!

Clojure programmers have different memory-debugging tools at their disposal.
Let's briefly mention these tools:

- [jstat](https://docs.oracle.com/en/java/javase/17/docs/specs/man/jstat.html)
  and [VisualVM](https://visualvm.github.io/) for general memory stats: see how
  much total heap is used, the heap limit, breakdown of different GC areas, etc.
  Useful for getting the overall picture.
- [clj-memory-meter](https://github.com/clojure-goes-fast/clj-memory-meter) to
  measure how much heap an individual object occupies. Helps you reduce memory
  footprint for things that need to be in memory all/most of the time (caches,
  static resources, etc.).
- [clj-async-profiler](https://github.com/clojure-goes-fast/clj-async-profiler)
  in `:alloc` mode to understand and reduce how much garbage the program
  allocates.
- [Eclipse MAT](https://eclipse.dev/mat/), a heap analyzer, is used to explore
  heap dumps and detect memory leaks.

Despite such a variety, none of these tools answer the question: "at most, how
much memory does the program require to run"?[[1]](#fn1)<a name="bfn1"></a>
That's why I came up with yet another instrument that I want to present to you
today.

### clj-memory-meter.trace

Version **0.4.0** of
[clj-memory-meter](https://github.com/clojure-goes-fast/clj-memory-meter)
introduces a new namespace,
[clj-memory-meter.trace](https://github.com/clojure-goes-fast/clj-memory-meter?tab=readme-ov-file#memory-usage-tracing),
to facilitate figuring out the memory requirements of an algorithm. It allows
you to instrument functions in your program to report the memory usage before
and after the invocation. There are no hard rules or suggestions on which
functions you should trace, but it's a good idea to go from top to bottom. Note
that this tracing significantly slows down your program as every invocation of
the traced function will, by default, force the GC twice. It will also measure
arguments and the returned value with `clj-memory-meter.core/measure`.

We'll learn how it works by solving a toy problem. The task is the following:
given a set of integers, count the total number of subsets such that
<code><span>∏(subset) <i>mod</i> 7 = 1</span></code>. Let's get cracking:

```clj
(ns example
  (:require [clj-memory-meter.trace :as cmm.trace]))

(defn matches? [s]
  (= (mod (reduce * s) 7) 1))

;; Attach 10 megabytes of useless metadata to a subset to make it more
;; pronounced when it is kept in memory.
(defn add-deadweight [s]
  (with-meta s {:deadweight (byte-array (* 10 1024 1024))}))
```

We'll call `add-deadweight` on generated subsets to inflate them for didactic
purposes. Now, our first attempt will be a naive eager solution:

```clj
(defn subsets-eager [[x & rst :as coll]]
  (if (empty? coll)
    [#{}]
    (let [rest-subsets (subsets-eager rst)]
      (into rest-subsets
            (map #(add-deadweight (conj % x)) rest-subsets)))))

(count (filter matches? (subsets-eager (range 2 7))))
=> 6
```

It finishes in a reasonable time. You can repeatedly run this till the cows come
home, and it will work perfectly fine. So, is the eager version good enough? Try
running it with a slightly larger input set:

```
(count (filter matches? (subsets-eager (range 2 11))))
=> java.lang.OutOfMemoryError
```

Nah, not good. Let's trace a few functions and see how much heap is used at different steps of
the algorithm:

```clj
(cmm.trace/trace-var #'matches?)
(cmm.trace/trace-var #'subsets-eager)

(cmm.trace/with-relative-usage
  (count (filter matches? (subsets-eager (range 2 7)))))
```

```text
Initial used heap: 444.3 MiB (10.8%)
│ (example/subsets-eager <56 B>) | Heap: -1.8 KiB (-0.0%)
│ │ (example/subsets-eager <56 B>) | Heap: -13.0 KiB (-0.0%)
│ │ │ (example/subsets-eager <56 B>) | Heap: -11.9 KiB (-0.0%)
│ │ │ │ (example/subsets-eager <56 B>) | Heap: -11.5 KiB (-0.0%)
│ │ │ │ │ (example/subsets-eager <56 B>) | Heap: -11.0 KiB (-0.0%)
│ │ │ │ │ │ (example/subsets-eager <0 B>) | Heap: -9.7 KiB (-0.0%)
│ │ │ │ │ │ └─→ <320 B> | Heap: -9.7 KiB (-0.0%)
│ │ │ │ │ └─→ <10.0 MiB> | Heap: +12.0 MiB (+0.3%)
│ │ │ │ └─→ <30.0 MiB> | Heap: +36.0 MiB (+0.9%)
│ │ │ └─→ <70.0 MiB> | Heap: +84.0 MiB (+2.1%)
│ │ └─→ <150.0 MiB> | Heap: +180.0 MiB (+4.4%)
│ └─→ <310.0 MiB> | Heap: +372.0 MiB (+9.1%)
│
│ (example/matches? <10.0 MiB>) | Heap: +372.0 MiB (+9.1%)
│ └─→ <16 B> | Heap: +372.0 MiB (+9.1%)
│
│ (example/matches? <10.0 MiB>) | Heap: +372.0 MiB (+9.1%)
│ └─→ <16 B> | Heap: +372.0 MiB (+9.1%)
│
│ ...
│
│ (example/matches? <10.0 MiB>) | Heap: +372.1 MiB (+9.1%)
│ └─→ <16 B> | Heap: +372.1 MiB (+9.1%)
Final used heap: +75.6 KiB (+0.0%)
```

You can see how each returned recursive call to `subsets-eager` increases heap
usage (12MB, then 36MB, 84MB, and so on). Once all subsets are constructed
(occupying 372MB), all that memory is retained to the very end of the algorithm.
All those lines saying `+372.0 MiB` mean the heap difference compared to the
_beginning of the algorithm_ (established by `with-relative-usage`) macro. It
doesn't mean that those 372 megabytes are added between each call to `matches?`.
The final line says that the used heap did not increase _after_ the algorithm
had run — which is to be expected because we didn't store any data anywhere.

It is easy to make this naive implementation lazy — just by swapping `into` for
`concat`. Let's do it and run it with the tracer:

```clj
(defn subsets-lazy [[x & rst :as coll]]
  (if (empty? coll)
    [#{}]
    (let [rest-subsets (subsets-lazy rst)]
      (concat rest-subsets
              (map #(add-deadweight (conj % x)) rest-subsets)))))

(cmm.trace/trace-var #'subsets-lazy)

(cmm.trace/with-relative-usage
  (count (filter matches? (subsets-lazy (range 2 7)))))
```

```text
Initial used heap: 444.5 MiB (10.9%)
│ (example/subsets-lazy <56 B>) | Heap: +544 B (+0.0%)
│ │ (example/subsets-lazy <56 B>) | Heap: +1.8 KiB (+0.0%)
│ │ │ (example/subsets-lazy <56 B>) | Heap: +3.2 KiB (+0.0%)
│ │ │ │ (example/subsets-lazy <56 B>) | Heap: +4.5 KiB (+0.0%)
│ │ │ │ │ (example/subsets-lazy <56 B>) | Heap: +5.4 KiB (+0.0%)
│ │ │ │ │ │ (example/subsets-lazy <0 B>) | Heap: +6.7 KiB (+0.0%)
│ │ │ │ │ │ └─→ <320 B> | Heap: +6.7 KiB (+0.0%)
│ │ │ │ │ └─→ <576 B> | Heap: +8.1 KiB (+0.0%)
│ │ │ │ └─→ <832 B> | Heap: +15.2 KiB (+0.0%)
│ │ │ └─→ <1.1 KiB> | Heap: +2.9 KiB (+0.0%)
│ │ └─→ <1.3 KiB> | Heap: +9.9 KiB (+0.0%)
│ └─→ <1.6 KiB> | Heap: -16.7 KiB (-0.0%)
│
│ (example/matches? <10.0 MiB>) | Heap: +12.0 MiB (+0.3%)
│ └─→ <16 B> | Heap: +12.0 MiB (+0.3%)
│
│ (example/matches? <10.0 MiB>) | Heap: +24.0 MiB (+0.6%)
│ └─→ <16 B> | Heap: +24.0 MiB (+0.6%)
│
│ (example/matches? <10.0 MiB>) | Heap: +36.0 MiB (+1.2%)
│ └─→ <16 B> | Heap: +48.0 MiB (+1.2%)
│
│ ...
│
│ (example/matches? <10.0 MiB>) | Heap: +180.0 MiB (+4.4%)
│ └─→ <16 B> | Heap: +180.0 MiB (+4.4%)
│
│ (example/matches? <10.0 MiB>) | Heap: +192.0 MiB (+4.7%)
│ └─→ <16 B> | Heap: +192.0 MiB (+4.7%)
│
│ (example/matches? <10.0 MiB>) | Heap: +180.0 MiB (+4.4%)
│ └─→ <16 B> | Heap: +180.0 MiB (+4.4%)
│
│ (example/matches? <10.0 MiB>) | Heap: +168.0 MiB (+4.1%)
│ └─→ <16 B> | Heap: +168.0 MiB (+4.1%)
│
│ ...
│
│ (example/matches? <10.0 MiB>) | Heap: +36.0 MiB (+0.9%)
│ └─→ <16 B> | Heap: +36.0 MiB (+0.9%)
│
│ (example/matches? <10.0 MiB>) | Heap: +24.0 MiB (+0.6%)
│ └─→ <16 B> | Heap: +24.1 MiB (+0.6%)
Final used heap: +26.4 KiB (+0.0%)
```

This implementation exhibits peculiar behavior. First of all, `subsets-lazy`
alone didn't produce anything that takes memory — because it is lazy. As
`matches?` gets called, the memory usage starts to grow upwards to the maximum
of 192 megabytes, and then it linearly drops all the way to zero. Still, it uses
only ~50% memory of the eager solution at its maximum which is an improvement.

How about a linear lazy implementation that doesn't use recursion? The next
algorithm generate the subsets according to bit patterns. We'll also use a
larger input set to demonstrate some laziness effects.

```clj
(defn subsets-lazy2 [coll]
  (let [v (vec coll)
        n (count v)]
    (map (fn [i]
           (->> v
                (keep-indexed (fn [idx item]
                                (when (bit-test i idx)
                                  item)))
                set
                add-deadweight))
         (range (bit-shift-left 1 n)))))

(cmm.trace/trace-var #'subsets-lazy2)

(cmm.trace/with-relative-usage
  (count (filter matches? (subsets-lazy2 (range 2 9)))))
```

```text
Initial used heap: 432.5 MiB (10.6%)
│ (example/subsets-lazy2 <56 B>) | Heap: +544 B (+0.0%)
│ └─→ <624 B> | Heap: +42.2 KiB (+0.0%)
│
│ (example/matches? <10.0 MiB>) | Heap: +384.0 MiB (+9.4%)
│ └─→ <16 B> | Heap: +384.0 MiB (+9.4%)
│
│ (example/matches? <10.0 MiB>) | Heap: +384.0 MiB (+9.4%)
│ └─→ <16 B> | Heap: +384.0 MiB (+9.4%)
│
│ (example/matches? <10.0 MiB>) | Heap: +384.0 MiB (+9.4%)
│ └─→ <16 B> | Heap: +384.0 MiB (+9.4%)
│
│ ...
│
│ (example/matches? <10.0 MiB>) | Heap: +456.0 MiB (+11.1%)
│ └─→ <16 B> | Heap: +456.0 MiB (+11.1%)
│
│ (example/matches? <10.0 MiB>) | Heap: +456.0 MiB (+11.1%)
│ └─→ <16 B> | Heap: +456.0 MiB (+11.1%)
Final used heap: +8.5 KiB (+0.0%)
```

As expected, `subsets-lazy2` doesn't take any memory by itself, but what about
those whopping 384 megabytes that appear after the very first call to
`matches?`? You observe the effect of
[chunking](https://clojure-goes-fast.com/blog/clojures-deadly-sin/#chunking-is-unpredictable)
— Clojure realizes chunked lazy sequences not one-by-one, but 32 elements at a
time. 32*10MB = 320MB; I'm not sure why it is 384MB and even grows to 456MB
sometime later, but chunking certainly has to do with it.

Nowadays, Clojure provides a more predictable deferred evaluation mechanism —
transducers and reducible contexts. Let's have one final implementation that
returns an eduction instead of a lazy sequence:

```clj
(defn subsets-eduction [coll]
  (let [v (vec coll)
        n (count v)]
    (eduction
     (map (fn [i]
            (->> v
                 (keep-indexed (fn [idx item]
                                 (when (bit-test i idx)
                                   item)))
                 set
                 add-deadweight)))
     (range (bit-shift-left 1 n)))))

(cmm.trace/trace-var #'subsets-eduction)

(cmm.trace/with-relative-usage
  (count (filter matches? (subsets-eduction (range 2 9)))))
```

```text
Initial used heap: 432.5 MiB (10.6%)
│ (example/subsets-eduction <56 B>) | Heap: -4.5 KiB (-0.0%)
│ └─→ <568 B> | Heap: -3.3 KiB (-0.0%)
│
│ (example/matches? <10.0 MiB>) | Heap: +396.0 MiB (+9.7%)
│ └─→ <16 B> | Heap: +396.0 MiB (+9.7%)
│
│ (example/matches? <10.0 MiB>) | Heap: +396.0 MiB (+9.7%)
│ └─→ <16 B> | Heap: +396.0 MiB (+9.7%)
│
│ ...
│
│ (example/matches? <10.0 MiB>) | Heap: +456.0 MiB (+11.1%)
│ └─→ <16 B> | Heap: +456.0 MiB (+11.1%)
│
│ (example/matches? <10.0 MiB>) | Heap: +456.0 MiB (+11.1%)
│ └─→ <16 B> | Heap: +456.0 MiB (+11.1%)
Final used heap: +20.2 KiB (+0.0%)
```

Wait, what? Why does it use _even more_ memory? Turns out, we we've fallen into
a trap. `filter` doesn't know how to consume an eduction efficiently, so it
calls `seq` on it, turning it into a regular lazy sequence and losing all the
benefits. To properly consume an eduction, we need to use `reduce` or
`transduce`:

```clj
(cmm.trace/with-relative-usage
  (reduce #(if (matches? %2) (inc %1) %1) 0 (subsets-eduction (range 2 9))))
```

```text
Initial used heap: 432.5 MiB (10.6%)
│ (example/subsets-eduction <56 B>) | Heap: +568 B (+0.0%)
│ └─→ <568 B> | Heap: +1.7 KiB (+0.0%)
│
│ (example/matches? <10.0 MiB>) | Heap: +12.0 MiB (+0.3%)
│ └─→ <16 B> | Heap: +5.9 KiB (+0.0%)
│
│ (example/matches? <10.0 MiB>) | Heap: +12.0 MiB (+0.3%)
│ └─→ <16 B> | Heap: +2.6 KiB (+0.0%)
│
│ (example/matches? <10.0 MiB>) | Heap: +12.0 MiB (+0.3%)
│ └─→ <16 B> | Heap: +2.8 KiB (+0.0%)
│
│ ...
│
│ (example/matches? <10.0 MiB>) | Heap: +12.0 MiB (+0.3%)
│ └─→ <16 B> | Heap: +5.3 KiB (+0.0%)
│
│ (example/matches? <10.0 MiB>) | Heap: +12.0 MiB (+0.3%)
│ └─→ <16 B> | Heap: +5.4 KiB (+0.0%)
│
│ (example/matches? <10.0 MiB>) | Heap: +12.0 MiB (+0.3%)
│ └─→ <16 B> | Heap: +4.5 KiB (+0.0%)
Final used heap: +4.1 KiB (+0.0%)
```

At last, we got what we wanted. Calling `matches?` on the eduction realizes one
element at a time, so our high-memory mark constantly sits at 12 megabytes. I
can't tell why it's 12MB and not 10, but otherwise, the algorithm behaves as
expected.

### Conclusion

Working with memory ain't easy, even in a runtime that promises to take care of
it for you. We saw it first-hand in this post by running into surprises with
recursive vs. linear laziness, chunking, interaction between lazy functions and
eduction. It's always good to have an extra instrument in your toolbelt that
helps you study and understand those behaviors, look inside the black box, and
become wiser from that. I hope this new tool makes at least one day of your life
happier. Till next time!

_I thank [Clojurists Together](https://www.clojuriststogether.org) for
sponsoring my open-source work in 2025 and making this release possible._

#### Footnotes

1. <a name="fn1"></a><span>MAT can answer the question "why did my program run
OOM?" Running the program with `-XX:+HeapDumpOnOutOfMemoryError` creates a heap
dump on OOM which you can feed to heap analyzer. Studying the dominator object
trees may then reveal the largest data structures that filled up the memory. But
it is often hard to associate those data structures with the exact functions and
lines of code that produced it.</span>[↑](#bfn1)

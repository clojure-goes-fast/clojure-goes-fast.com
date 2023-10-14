{:title "Java arrays and unchecked math"
 :date-published "2018-04-09 18:00:00"
 :reddit-link "https://www.reddit.com/r/Clojure/comments/8az2og/java_arrays_and_unchecked_math/"}

The design of Clojure explicitly promotes usage of efficient data structures.
Unlike some other functional languages where the standard libraries revolve
around lists, Clojure puts vectors and maps front and forward.

Clojure vectors are an excellent data structure for sequential data. They
provide almost O(1) lookups and updates[[1]](#fn1)<a name="bfn1"></a>, so it is
natural to use them for large collections where speed is essential. However,
vectors still have their overhead and might not be the fit for the tightest and
most demanding performance spots.

For example, when you are tasked to represent mathematical matrices and their
multiplication, you might initially reach for vectors. [Matrix
multiplication](https://en.wikipedia.org/wiki/Matrix_multiplication) is an
O(n<sup>3</sup>) algorithm; hence every performance flaw will rapidly multiply
as the matrix grows in size. Let's start with this simple implementation:

```clj
;; Allows both random and single-value initialization
(defn make-matrix-v [n & [val]]
  (vec
   (repeatedly n #(vec
                   (repeatedly n (fn []
                                   (long (if val
                                           val
                                           (rand-int 10000)))))))))

(defn mult-matrix-v [a b]
  (let [n (count a)]
    (vec
     (for [i (range n)]
       (vec
        (for [j (range n)]
          (reduce (fn [sum k]
                    (+ sum (* (nth (nth a i) k)
                              (nth (nth b k) j))))
                  0 (range n))))))))
```

Now, let's use [Criterium](/blog/benchmarking-tool-criterium/) to benchmark this
implementation:

```clj
user=> (let [a (make-matrix-v 100)
             b (make-matrix-v 100)]
         (crit/quick-bench (mult-matrix-v a b)))
...
             Execution time mean : 214.090921 ms
    Execution time std-deviation : 11.914304 ms
```

For multiplication of two 100x100 matrices, 214 milliseconds is quite a lot.
Remember, it increases cubically, so if you take 200x200 matrices, the time to
multiply them will become 2 seconds. Not only representing matrices as vectors
is embarrassingly slow, but it is also a woeful squander of
[memory](/blog/introspection-tool-object-memory-meter/) too:

```clj
user=> (mm/measure (make-matrix-v 100))
"308.5 KB"
```

But it is unfair to blame vectors for slowness and memory overhead. They are
designed to be immutable and at the same time efficient to update; we just don't
need these properties in our task. But JVM has something much lower-level that
would suit our problem perfectly, and that something is arrays.

### Java arrays

Clojure standard library provides certain
[facilities](https://clojure.org/reference/java_interop#_arrays) for us to be
able to use Java arrays. With them, we can rewrite our implementation to be much
more efficient and lean:

```clj
(set! *warn-on-reflection* true) ;; To avoid accidental reflection

;; Saves us some typing further on
(defmacro aget2l [a i j]
  `(aget ^"[J" (aget ~a ~i) ~j))

(defmacro aset2l [a i j v]
  `(aset ^"[J" (aget ~a ~i) ~j ~v))

(defn make-matrix-a [n & [val]]
  (let [^"[[J" a (make-array Long/TYPE n n)]
    (dotimes [i n]
      (dotimes [j n]
        (aset2l a i j (if val
                        (long val)
                        (long (rand-int 10000))))))
    a))

(defn mult-matrix-a [^"[[J" a, ^"[[J" b]
  (let [n (alength a)
        ^"[[J" c (make-matrix-a n 0)]
    (dotimes [i n]
      (dotimes [j n]
        (loop [k 0, sum 0]
          (if (< k n)
            (recur (inc k) (+ sum (* (aget2l a i k)
                                     (aget2l b k j))))

            (aset2l c i j sum)))))
    c))
```

A few things in the code above probably require an explanation:

- `^"[[J"` syntax hints the next symbol to `long[][]`. This weird notation
  spawns from the way how JVM represents the array types internally. `[` stands
  for an array in JVM, followed by the label for the type of its content. Thus,
  an array of integers is marked as `[I`, array of longs — `[J`, array of
  strings — `[Ljava.lang.String;` (note the `L` which stands for objects in JVM,
  for reasons unknown). Then, double `[[` means that the array is
  two-dimensional. So, a Java array `double[][][]` would be represented in JVM
  as `[[[D`.
- In `mult-matrix-a` we use `dotimes` for two outer loops because they compile
  directly into stateless Java `for` loops. For the inner loop, we use Clojure's
  `loop` because we also need to accumulate the sum between iterations.

We can now verify that the array implementation is better in terms of both
performance and memory occupancy:

```clj
user=> (let [a (make-matrix-a 100)
             b (make-matrix-a 100)]
         (crit/quick-bench (mult-matrix-a a b)))
...
             Execution time mean : 27.353802 ms
    Execution time std-deviation : 1.238835 ms

user=> (mm/measure (make-matrix-a 100))
"80.1 KB"
```

As you can see, the array implementation is ~8 times faster and takes 4 times
less memory to store the matrices. This can become very substantial as the
matrix grows.

#### Dirty little secret of multi-dimensional aget/aset

An informed reader could notice and become confused why we write our own
two-dimensional aget/aset implementations — `aget2l` and `aset2l`. This looks
redundant because Clojure's `aget` and `aset` support variable number of
indices. The problem with them, though, is that they are terribly slow for more
than one dimension:

```clj
user=> (let [^longs arr (make-array Long/TYPE 10)]
         (crit/quick-bench (aget arr 0)))
Execution time mean : 9.781887 ns

user=> (let [^"[[J" arr (make-array Long/TYPE 10 10)]
         (crit/quick-bench (aget arr 0 0)))
Execution time mean : 16.279467 µs
```

That's 1660 times slower! How did that happen? The answer lies in the
implementation of
[aget](https://github.com/clojure/clojure/blob/clojure-1.9.0/src/clj/clojure/core.clj#L3878):

```clj
(defn aget
  {:inline (fn [a i] `(. clojure.lang.RT (aget ~a (int ~i))))
   :inline-arities #{2}}
  ([array idx]
   (clojure.lang.Reflector/prepRet (.getComponentType (class array)) (. Array (get array idx))))
  ([array idx & idxs]
   (apply aget (aget array idx) idxs)))
```

You will see that the standard one-dimensional two-arity version of `aget` is
inlined by Clojure compiler into an interop call to `clojure.lang.RT/aget` (you
can read more about Clojure inlines
[here](http://bytopia.org/2014/07/07/inline-functions-in-clojure/)). The `RT`
class contains multiple overloaded `aget` implementations for each primitive
type, so the call is almost as fast as using `arr[idx]` notation in Java (and
becomes equally fast after JIT kicks in).

For any other arity, the reflection will be used, at runtime, to navigate
through the array. We have already discussed before that reflection is a
[performance killer](/blog/performance-nemesis-reflection/). The worst thing
about aget/aset reflection is that it won't produce any warnings since the
reflection is not caused by the compiler — it is encoded directly in the
function implementation.

### Unchecked math

Clojure's default mathematical functions (`+`, `*`, `inc`, etc.) are not
precisely equal to their Java counterparts. You see, in Java the mathematical
operations are **unchecked**, meaning that they will silently produce an
incorrect value in the case of an overflow. Now, Clojure's functions will throw
an exception in such case. Checking for overflow makes your code somewhat safer
but comes at a price of lower performance. After all, the CPU doesn't do such
checks internally, so they must be done in the code, spending CPU cycles.

However, Clojure has a way to drop to the fast-but-dangerous versions of
mathematical operations with functions that have `unchecked-` prefix, e.g.,
`unchecked-inc` or `unchecked-multiply`. You can compare the behavior of checked
and unchecked functions yourself:

```clj
user=> (crit/quick-bench (* 42 57))
Execution time mean : 15.097586 ns

user=> (crit/quick-bench (unchecked-multiply 42 57))
Execution time mean : 1.017635 ns

user=> (* 10000000000 10000000000)
java.lang.ArithmeticException: integer overflow

user=> (unchecked-multiply 10000000000 10000000000)
7766279631452241920
```

If you are sure that your code won't be dealing with numbers too big, you can
recover that lost performance by replacing all mathematical operations with
their `unchecked-` variants. However, there is a slightly more convenient way.
By binding the dynamic var `*unchecked-math*` to true you will tell Clojure to
use unchecked operations in place of checked ones. This var can be set at the
beginning of the file (so that each function inside will use unchecked math) or
turned on and off just for a single function definition.

Note that, unlike Clojure's checked operations, unchecked math cannot work with
boxed values. So, if the compiler encounters a boxed value or values of
mismatching types, it won't be able to use the unchecked operation there. To
know of such cases, we can set `*unchecked-math*` to `:warn-on-boxed`, which
will both enable the unchecked math and print warnings in places where unchecked
operation substitution was not possible.

The updated solution will thus become:

```clj
(set! *unchecked-math* true)

(defn mult-matrix-a-unchecked [^"[[J" a, ^"[[J" b]
  (let [n (alength a)
        ^"[[J" c (make-matrix-a n 0)]
    (dotimes [i n]
      (dotimes [j n]
        (loop [k 0, sum 0]
          (if (< k n)
            (recur (inc k) (+ sum (* (aget2l a i k)
                                     (aget2l b k j))))

            (aset2l c i j sum)))))
    c))

(set! *unchecked-math* false)
```

It is identical to the previous one, except for turning `*unchecked-math` var on
and off. Now, to the benchmarks:

```clj
user=> (let [a (make-matrix-a 100)
             b (make-matrix-a 100)]
         (crit/quick-bench (mult-matrix-a-unchecked a b)))
...
             Execution time mean : 5.368327 ms
    Execution time std-deviation : 275.268655 µs
```

Very good, we managed to speed up the matrix multiplication almost five times
further. We can finally use
[clj-java-decompiler](/blog/introspection-tools-java-decompilers/) to make sure
the generated code matches what we expect[[2]](#fn2)<a name="bfn2"></a>:

```clj
(set! *unchecked-math* true)

(decompile ;; <=== This is what changed
 (defn mult-matrix-a-unchecked [^"[[J" a, ^"[[J" b]
   (let [n (alength a)
         ^"[[J" c (make-matrix-a n 0)]
     (dotimes [i n]
       (dotimes [j n]
         (loop [k 0, sum 0]
           (if (< k n)
             (recur (inc k) (+ sum (* (aget2l a i k)
                                      (aget2l b k j))))

             (aset2l c i j sum)))))
     c)))

...

public static Object invokeStatic(final Object a, final Object b) {
    final int n = ((Object[])a).length;
    final Object c = matrix$make_matrix_a.invokeStatic((Object)n, (ISeq)ArraySeq.create(new Object[] { const__2 }));
    for (long n__5742__auto__19241 = n, i = 0L; i < n__5742__auto__19241; ++i) {
        for (long n__5742__auto__2 = n, j = 0L; j < n__5742__auto__2; ++j) {
            long k = 0L;
            long sum = 0L;
            while (k < n) {
                final long n2 = k + 1L;
                sum += ((long[])RT.aget((Object[])a, (int)i))[(int)k] * ((long[])RT.aget((Object[])b, (int)k))[(int)j];
                k = n2;
            }
            RT.aset((long[])RT.aget((Object[])c, (int)i), (int)j, sum);
        }
    }
    return c;
}
```

This is almost how we would write it in Java, minus the ugly names and
superfluous casts, of course :).

### Conclusion

Clojure's idiomatic approach is not necessarily the fastest, but you can still
reach for any low-level JVM tool if the performance requires it. Arrays are very
understandable and predictable, and there is no reason to ignore them even if
Clojure's API for arrays is somewhat awkward. And if you know what you are
doing, it is possible to accelerate number crunching with unchecked mathematical
operations. That's all for today, and until next time, go fast!

##### Footnotes

1. <a name="fn1"></a><span> O(log<sub>32</sub>n) to be precise, which can be at most 6
for the vector of a maximum size.</span>[↑](#bfn1)
2. <a name="fn2"></a><span> To make this demo less noisy, I have disabled [locals
clearing](https://clojuredocs.org/clojure.core/*compiler-options*) and enabled
[direct linking](https://clojure.org/reference/compilation#directlinking). By
default, your decompiler output will look slightly different.</span>[↑](#bfn2)

{:title "Performance tidbit: runtime type checks"
 :date-published "2024-05-16 17:00:00"
 :reddit-link "https://old.reddit.com/r/Clojure/comments/1ctg6av/performance_tidbit_runtime_type_checks/?"}

_Clojure Goes Fast now offers [consulting services](/consulting/). Get in touch
if you need personalized help with your performance challenges._

In this post, we'll analyze the cost of checking whether an instance belongs to
a certain class or interface at runtime. While this operation is relatively
fast, it might still contribute a significant amount of execution time if
performed at the hottest paths of your program.

We are going to use [JMH](/blog/using-jmh-with-clojure-part1/) as a benchmarking
tool. We can be confident about its accuracy and also utilize its profiling
capabilities to gain deeper insights into how the underlying type-checking
facilities work.

All the benchmarks in this post were conducted on a Macbook Pro M1 2020 running
MacOS 14.4.1.

### Java's instanceof

The setup of the benchmark is the following. There are four classes — `A`, `B`,
`C`, and `D`. Class `A` implements interface `Iface1`, the other three implement
`Iface2`. A single instance of class A is created and checked using `instanceof`
for being of class `A`, interface `Iface1`, and interface `Iface2`. In addition,
I want to test if the performance of method
[Class.isInstance](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Class.html#isInstance(java.lang.Object))
is any different from `instanceof`. The whole dance of having one interface with
one implementor and another one with three implementors is done to check out the
hypothesis whether Class Hierarchy Analysis the performance of the type checks.

I went through several setups for the benchmark before I settled on this one. I
tried creating a large array of random objects so that `instanceof` calls return
different results each time. I think I couldn't eliminate pipelining in the for
loop, even with
[Blackhole](https://javadoc.io/doc/org.openjdk.jmh/jmh-core/1.23/org/openjdk/jmh/infra/Blackhole.html),
so normalized values appeared too low. I also tried calling `instanceof` at
least once on each object of the four classes. It also didn't make the results
more believable.

So, anyway, this is the main part of the benchmark (the entire project is
[here](https://github.com/clojure-goes-fast/clojure-goes-fast.com/tree/master/code/instanceof-bench)):

```java
Object a;

@Setup(Level.Iteration)
public void setup() {
    a = new A();
}

@Benchmark
public boolean instanceof_A() {
    return a instanceof A;
}

@Benchmark
public boolean instanceof_Iface1() {
    return a instanceof Iface1;
}

@Benchmark
public boolean instanceof_Iface2() {
    return a instanceof Iface2;
}

@Benchmark
public boolean isInstance_A() {
    return A.class.isInstance(a);
}

@Benchmark
public boolean isInstance_Iface1() {
    return Iface1.class.isInstance(a);
}

@Benchmark
public boolean isInstance_Iface2() {
    return Iface2.class.isInstance(a);
}
```

I ran it while using JDK 11 and got these results:

```shell
$ java -version
openjdk version "11.0.19" 2023-04-18
OpenJDK Runtime Environment Temurin-11.0.19+7 (build 11.0.19+7)
OpenJDK 64-Bit Server VM Temurin-11.0.19+7 (build 11.0.19+7, mixed mode)

$ clojure -T:build javac && clojure -m jmh bench Java
Benchmark               Mode  Cnt  Score   Error  Units
Java.instanceof_A       avgt    5  1.871 ± 0.131  ns/op
Java.instanceof_Iface1  avgt    5  1.860 ± 0.035  ns/op
Java.instanceof_Iface2  avgt    5  2.461 ± 0.020  ns/op
Java.isInstance_A       avgt    5  2.102 ± 0.703  ns/op
Java.isInstance_Iface1  avgt    5  2.185 ± 0.286  ns/op
Java.isInstance_Iface2  avgt    5  2.469 ± 0.064  ns/op
```

We see that the operation of checking the type at runtime is pretty much
instant. Testing whether the object implements `Iface2` is indeed a little bit
slower than a class or a monomorphic interface. The time difference between
`instanceof` and `Class.isInstance` is within error margins.

Now let's repeat the benchmark on the fresh JDK 22:

```shell
$ java -version
openjdk version "22" 2024-03-19
OpenJDK Runtime Environment Temurin-22+36 (build 22+36)
OpenJDK 64-Bit Server VM Temurin-22+36 (build 22+36, mixed mode)

$ clojure -T:build javac && clojure -m jmh bench Java
Benchmark               Mode  Cnt  Score   Error  Units
Java.instanceof_A       avgt    5  0.674 ± 0.017  ns/op
Java.instanceof_Iface1  avgt    5  0.688 ± 0.120  ns/op
Java.instanceof_Iface2  avgt    5  0.681 ± 0.038  ns/op
Java.isInstance_A       avgt    5  0.701 ± 0.084  ns/op
Java.isInstance_Iface1  avgt    5  0.818 ± 0.110  ns/op
Java.isInstance_Iface2  avgt    5  1.182 ± 0.072  ns/op
```

Either the performance of type checks since JDK 11 got much faster, or JMH is
not able to prevent the compiler from optimizing the benchmark. In any case, the
Java 22 benchmark only reinforces the idea that `instanceof` is incredibly
cheap.

It is peculiar to see the JIT output for each of the three checks in order to
understand what exactly the runtime is doing. For that, wel'll need to have
**hsdis** binary compiled for the specific platform and JDK version. I took one
for JDK 17 from [here](https://chriswhocodes.com/hsdis/). The command to launch
the benchmark with the profiler enabled:

```sh
$ clojure -T:build javac && sudo clojure -m jmh bench Java profiler dtraceasm
```

The redacted output for `instanceof A` check:

```arm
;; c2, level 4, bench.jmh_generated.Java_instanceof_A_jmhTest
;; In ARM assembly, w00 is 32-bit and x00 is 64-bit,
;; but they are the same register (like AX, RAX, EAX in amd64).
...
; x14 contains the address of "this" (current object)
ldr   w13, [x14, #12]     ; w13 <- this.a (field "a" is at offset 12 in this)
lsl   x12, x13, #3        ; x12 <- shift x13 three bits left (unpack compressed OOP)
ldr   w13, [x12, #8]      ; w13 <- a.getClass()
cmp   w13, w11            ; Apparently, x11 contained the pointer to class A. Compare them.
b.ne  0x00000001125a48b8  ; The rest is branching and benchmark-generated code.
...
```

In other words, checking if an object is of some concrete class X is as easy as
comparing its classword (the pointer to the static class object) to the
classword of that class X. Now let's see what JIT outputs for `instanceof Iface1`:

```arm
;; c2, level 4, bench.jmh_generated.Java_instanceof_Iface1_jmhTest
...
ldr   w13, [x14, #12]
lsl   x12, x13, #3
ldr   w13, [x12, #8]
cmp   w13, w11
b.ne  0x00000001124efe38
...
```

Surprisingly (or not at all), the generated assembly for `instanceof Iface1` is
precisely the same as for `instanceof A`! The Java runtime figured out that
since only a single class implements `Iface1`, then checking if the object
implements that interface is the same as checking if it is an instance of that
class.

Finally, the assembly for `instanceof Iface2`:

```arm
c2, level 4, bench.jmh_generated.Java_instanceof_Iface2_jmhTest::instanceof_Iface2_avgt_jmhStub, version 4, compile id 546

↗    ↗                      ; begin
│    │                       ; ...
│    │  0x0000000115d145f8:  ldr   w11, [x13, #12]         ; w11 <- this.a
│    │  0x0000000115d145fc:  lsl   x11, x11, #3            ; unpack compressed OOP
│    │  0x0000000115d14600:  ldr   w12, [x11, #8]          ; w12 <- classword
│    │  0x0000000115d14604:  eor   x4, x12, #0x800000000   ; x4 <- address of memory region with superclass info of class in x12
│    │  0x0000000115d14608:  ldr   x11, [x4, #32]          ; x11 <- [x4+32]. Offset 32 contains a special "cache" field of last checked superclass.
│    │  0x0000000115d1460c:  cmp   x11, x0                 ; cmp x11 and x0. x0 contains the address of interface Iface2.
╰    │  0x0000000115d14610:  b.eq  0x0000000115d145e0      ; If match, then loop back onto the next iteration of benchmark.
     │  0x0000000115d14618:  ldr   x5, [x4, #40]           ; x5 <- [x4+40]
     │  0x0000000115d1461c:  ldr   w2, [x5], #8            ; x2 <- [x5]; x5 += 8. x2 now contains the length of the array of superclasses for x12
 ╭   │  0x0000000115d14628:  cbz   x2, 0x0000000115d14640  ; | The next 6 lines loop over the array comparing each value to x0.
 │ ↗ │  0x0000000115d1462c:  ldr   x8, [x5], #8            ; |
 │ │ │  0x0000000115d14630:  cmp   x0, x8                  ; |
 │╭│ │  0x0000000115d14634:  b.eq  0x0000000115d14640      ; | Match exists the loop early.
 │││ │  0x0000000115d14638:  sub   x2, x2, #0x1            ; |
 ││╰ │  0x0000000115d1463c:  cbnz  x2, 0x0000000115d1462c  ; | Otherwise, the loop ends here when the array is exhausted.
 ↘↘ ╭│  0x0000000115d14644:  b.ne  0x0000000115d1464c      ; If no match (remember that equality flag is retained from last CMP), don't cache.
    ││  0x0000000115d14648:  str   x0, [x4, #32]           ; Store x0 (address of Iface2) into the fast comparison cache for class x12.
    ↘╰  0x0000000115d1464c:  b     0x0000000115d145e0      ; Loop back to the beginning of benchmark
```

This assembly is much more complicated (hence, slower execution). I commented
each line so that you can follow along, but overall this code linearly goes
through all superclasses of the object class and compares them with `Iface2`.

### Clojure type predicates

As the next step, we'll benchmark Clojure's own utilities for runtime type
checking. Namely, we will measure the performance of:

- [`instance?`](https://clojuredocs.org/clojure.core/instance_q) — the most
  common predicate that directly mimics `instanceof`.
- [`isa?`](https://clojuredocs.org/clojure.core/isa_q) — a quite obscure
  predicate for working primarily with Clojure hierarchies (made with
  [`derive`](https://clojuredocs.org/clojure.core/derive)) but can also tell if
  one class is a parent of another.
- [`satisfies?`](https://clojuredocs.org/clojure.core/satisfies_q) — special
  predicate for checking if an object implements a Clojure protocol.
- [`extends?`](https://clojuredocs.org/clojure.core/extends_q) — a less known
  tool for checking if a class implements a Clojure protocol.
- `(= (class o) Someclass)` — obviously, there is little reason to use this
  directly, but it can be encountered in manual dispatch forms like:
  ```clj
  (condp = (class o)
    ClassA ...
    ClassB ...)
  ```

The benchmark command and the results are listed below.

```shell
$ clojure -T:build javac && clojure -m jmh bench Java profiler gc
Benchmark                Mode  Cnt     Score     Error  Units  Alloc B/op
Clj.instancePred_A       avgt    5     3.730 ±   0.549  ns/op           0
Clj.instancePred_Iface1  avgt    5     3.463 ±   0.601  ns/op           0
Clj.instancePred_Iface2  avgt    5     3.748 ±   0.254  ns/op           0
Clj.isaPred_A            avgt    5     4.563 ±   0.021  ns/op           0
Clj.isaPred_Iface1       avgt    5    10.747 ±   0.617  ns/op           0
Clj.isaPred_Iface2       avgt    5   442.268 ±   5.335  ns/op        1880
Clj.equalClass           avgt    5     5.294 ±   0.373  ns/op           0
Clj.satisfiesPred_hit    avgt    5    27.324 ±   1.985  ns/op           0
Clj.satisfiesPred_miss   avgt    5   673.392 ±  56.192  ns/op        3096
Clj.extendsPred_hit      avgt    5    82.221 ±   7.522  ns/op           0
Clj.extendsPred_miss     avgt    5    74.518 ±  14.641  ns/op           0
```

We can learn the following from the benchmark results:

1. `instance?` is in the same ballpark as `instanceof` — fast, stable,
   efficient. It is marginally slower because of an extra function invocation
   (unless you enable [direct
   linking](https://clojure.org/reference/compilation#directlinking) — then
   there would be no difference). There is no necessity to replace `instance?`
   to `Class.isInstance` unless you optimize the hottest loop in your
   program/library. Using `(= (class o) Someclass)` is also acceptable from the
   performance standpoint, even if it's syntactically awkward.
2. The temptingly short `isa?` should not be used to check against the class
   hierarchy. It's performance is OK if the classes are related, but degrades
   dramatically in the opposite case.
3. `satisfies?`, the default instrument for checking whether an object belongs
   to a protocol, is surprisingly slow in the negative case — when the object
   doesn't satisfy a protocol. It allocates garbage on that codepath too. This
   is a known issue that was filed as
   [CLJ-1814](https://clojure.atlassian.net/browse/CLJ-1814) back in 2015.
   Unfortunately, it has not been resolved to this day.
4. `extends?` is slower than `satisfies?` in the positive case but doesn't
   degrade in the negative case, and thus is much more predictable. It never
   allocates anything too. However, before using it, you should investigate if
   `extends?` works in all cases where `satisfies?` does. For example, neither
   returns `true` for objects that extend a protocol via metadata, but it might
   change in the future.

### Conclusions

The speed and efficiency of runtime type checks are likely not something you
should sweat about in your generic application code. When those are one-off
actions dwarfed by the other important work your program is doing, you are fine
with whichever looks nicer to you. But if such checks become a part of a hot
loop, or if you add them into a library (that you don't know upfront how other
people will be using), it is nice to understand how they are implemented and
their relative costs. You've learned that `satisfies?` is the offender that you
are most likely to run into and now know to watch for that in advance.

While `instance?` checks are very fast by themselves, they introduce a hidden
cost of an extra branch that makes the code less inlinable and has a few other
second-order effects. This is, however, a topic for another day. Neither we have
addressed the question of polymorphic virtual calls that is closely related to
type checks. If you are interested to learn more about this, check this great
resource: [The Black Magic of Java Method
Dispatch](https://shipilev.net/blog/2015/black-magic-method-dispatch).

Feel free to leave a comment below if such low-level topics appeal to you and if
you want to see more of such comment. Thanks for reading!

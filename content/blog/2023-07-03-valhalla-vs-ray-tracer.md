{:title "Project Valhalla vs. ray tracer: will it go faster?"
 :date-published "2023-07-03 15:00:00"
 :og-image "/img/posts/valhalla-spheres.png"
 :reddit-link "https://old.reddit.com/r/java/comments/14pjcu2/project_valhalla_vs_ray_tracer_will_it_go_faster/?"
 :hn-link "https://news.ycombinator.com/item?id=36572760"}

_TL;DR: Project Valhalla is an effort make Java more efficient when dealing with
primitive-like objects. While still being experimental, it significantly cuts
down object allocations and achieves up to a 2x speed increase (on some
platforms) in our example task. Yet, in its current state, Valhalla should be
applied together with careful benchmarking because performance regressions are
also possible. For more details, please read on._

Recently, I've discovered [this
guide](https://raytracing.github.io/books/RayTracingInOneWeekend.html) which
walks you through implementing a toy ray tracer from scratch. I took the
implementation written in
[Clojure](https://gist.github.com/jeaye/77e1d8874c8e76e7335ccf71ef53785c) by
Jeaye Wilkerson and wrote my own in Java to see if the two would have comparable
performance. As I went through the task, it became clear that the bulk of
computational work involves performing math operations with geometric vectors.
Considering that the JVM allocates these vector objects on the heap and then
furiously garbage-collects them, I couldn't help but wonder if this is an
excellent opportunity to showcase the potential of [Project
Valhalla](https://openjdk.org/projects/valhalla/). Equipped with this insight
and my regular performance tools, I conducted a couple experiments, and now I'm
ready to share the results with you.

### What is Valhalla (and how far are we from it)?

In the current version of Java, each object has an identity. Identity serves as
an abstract theoretical concept that lets us tell apart two distinct objects
without looking at their contents. This is really a single property that having
an identity _guarantees_; everything else that comes with it is an
implementation detail. But most of the time, we are more interested exactly in
those implementation details and practical implications of having an identity,
and those are:

1. Objects can be quickly checked if they are the same object by using `==`.
2. Objects have `System.identityHashCode()`.
3. Any object can be used as a mutex within a `synchronized` block.
4. The object's class is known at runtime.

Feature **1** is attained by objects being reference types, each with its own
pointer. If two pointers are equal, they point at the same object. Features
**2-4** are fulfilled by each object having a header field.

Project Valhalla is a major effort to introduce **value objects** to Java.
However, the term _value_ is heavily overloaded and ambiguous. A more accurate
name for these objects would be __identityless__, as the core objective is to
eliminate their identity. For what purpose? Here are some specific optimizations
you could do to such objects then:

1. Omit object header.
2. Allocate them on the stack instead of heap[[1]](#fn1)<a
   name="bfn1"></a>.
3. When in an array of such objects, store their content flattened and tightly
   packed together instead of having an array of references.

Naturally, these changes may be beneficial for program performance due to better
memory locality (_3_), improved cache utilization (_1, 3_), reduced strain on
the garbage collector (_2_). Interestingly, none of the above optimizations are
explicitly guaranteed by the spec. Valhalla allows developers to define value
objects but then lets the virtual machine determine how each object will be
presented in memory and which optimizations can be applied. This gives a lot of
flexibility to the designers of the language and the VM implementers. For the
users, however, it could feel clunky because there is no way to enforce or
ascertain at compile time whether a particular object will be inlined, stripped
of its header, or scalarized. Consequently, additional runtime introspection
becomes necessary to validate our assumptions.

Project Valhalla has been in development for several years and is shipped
piecewise. Some of its prerequisites have already landed into the JDK proper,
but Valhalla itself is not in the upstream yet. Fortunately for us, early access
builds are available for everyone to experiment with the new features, which is
precisely what we are going to do.

### The prep

First, we need to download an early access build from
[here](https://jdk.java.net/valhalla/). It is a `tar.gz` archive; all you need
is to unpack it, set the full path as `JAVA_HOME` env variable, and add the
`bin` subdirectory to your `PATH`. The best way to do it depends on your
platform/distro. On MacOS, I move the extracted build to
`/Library/Java/JavaVirtualMachines/` and execute the following command in the
shell to activate it:

```shell
$ export JAVA_HOME=$(/usr/libexec/java_home -v 20)
$ java -version
openjdk version "20-valhalla" 2023-03-21
OpenJDK Runtime Environment (build 20-valhalla+1-75)
OpenJDK 64-Bit Server VM (build 20-valhalla+1-75, mixed mode, sharing)
```

Sweet, with the Valhalla-enabled JDK installed, we are ready to proceed.

### The objects and the values

You can find the source code for my ray tracer implementation
[here](https://github.com/clojure-goes-fast/clojure-goes-fast.com/tree/master/code/java-ray-tracer).
It uses [POJOs](https://en.wikipedia.org/wiki/Plain_old_Java_object) to
represent all entities in the program, namely:

- [Vec3](https://github.com/clojure-goes-fast/clojure-goes-fast.com/blob/master/code/java-ray-tracer/src/raytracer/Vec3.java).
  A three-dimensional vector (or a point in 3D). Also (ab)used to represent RGB
  colors.
- [Ray](https://github.com/clojure-goes-fast/clojure-goes-fast.com/blob/master/code/java-ray-tracer/src/raytracer/Ray.java).
  Consists of two vectors — origin and direction. An infinite one-directional
  line passing through two points.
- [Sphere](https://github.com/clojure-goes-fast/clojure-goes-fast.com/blob/master/code/java-ray-tracer/src/raytracer/Sphere.java).
  The only kind of geometric objects this ray tracer handles. Defined by its
  center and radius and has a material.
- [Material](https://github.com/clojure-goes-fast/clojure-goes-fast.com/blob/master/code/java-ray-tracer/src/raytracer/Material.java).
  Can either be glass, metal, or matte, each having innate parameters. The
  material determines how a ray of light scatters after being reflected from a
  surface.
- [Scatter](https://github.com/clojure-goes-fast/clojure-goes-fast.com/blob/master/code/java-ray-tracer/src/raytracer/Scatter.java).
  Represents a ray that has been reflected from a surface, along with the
  acquired attenuation (in simpler words, coloring).
- [HitRecord](https://github.com/clojure-goes-fast/clojure-goes-fast.com/blob/master/code/java-ray-tracer/src/raytracer/HitRecord.java).
  Holds information about the collision between a ray of light and an object:
  intersection point, surface's normal vector at that point, etc.

You can download the project and then run the ray tracer like this:

```shell
$ ./run.sh 500 20 out.png
render: imageWidth=500 samplesPerPixel=20 file=out.png
Elapsed: 13,711 ms
Allocated: 3,388 MB
```

<center>
<figure class="figure">
<img class="img-responsive" src="/img/posts/valhalla-spheres.png" style="max-height: 300px;">
<figcaption class="figure-caption text-center">
    Midway dopamine kick.
</figcaption>
</figure>
</center>

<!-- Here's the result for some in-between dophamine kick: -->

On my _Macbook Pro M1 2020_, it takes ~13.7 seconds to produce this image with
the supplied arguments, and the program allocates 3.4 Gb worth of
objects[[2]](#fn2)<a name="bfn2"></a>. Let's figure out what kind of workload
dominates in the tracer. I'm going to use
[clj-async-profiler](https://github.com/clojure-goes-fast/clj-async-profiler);
for any non-Clojurists reading this, you'll want to use
[async-profiler](https://github.com/async-profiler/async-profiler) directly. In
my case, I start a REPL with the Java classes already compiled and do this:

```clj
(require '[clj-async-profiler.core :as prof])
(prof/profile (raytracer.Render/render 500 20 "out.ong"))
```

<center>
<figure class="figure">
<div class="downscale-iframe-66" style="height:300px">
<iframe src="/img/posts/valhalla-m1-cpu-pojo.html?hide-sidebar=true" style="height:450px"></iframe>
</div>
<figcaption class="figure-caption text-center">
    CPU flamegraph, POJO version. <a href="/img/posts/valhalla-m1-cpu-pojo.html?hide-sidebar=false"
target="_blank">Click</a> to open.
</figcaption>
</figure>
</center>

The profiler tells us, without much surprise, that the biggest contributor to
execution time is calculating intersections between rays and spheres. This
portion of the code heavily relies on performing mathematical operations such as
vector dot products, subtractions, and other mathematical computations. Let's
see what types of objects are being allocated and where.

```clj
(prof/profile {:event :alloc} (raytracer.Render/render 500 20 "out.ong"))
```

<center>
<figure class="figure">
<div class="downscale-iframe-66" style="height:300px">
<iframe src="/img/posts/valhalla-m1-alloc-pojo.html?hide-sidebar=false" style="height:450px"></iframe>
</div>
<figcaption class="figure-caption text-center">
    Allocation flamegraph, POJO version. <a href="/img/posts/valhalla-m1-alloc-pojo.html?hide-sidebar=false"
target="_blank">Click</a> to open.
</figcaption>
</figure>
</center>

Click "Reversed" and "Apply" to discover that Vec3 objects account for 77% of
all allocations. Again, not surprising so far. It is time to put Valhalla into
action.

In theory, it should be enough to mark the class with a new special keyword
`value` to start reaping the benefits. Importantly, you must not use the
identity of objects of that class anywhere (i.e., synchronize on them or use
them as keys in `IdentityHashTable`). Let's start small and make `Vec3` a value
object since it is the primary cause of allocations. Here's what its definition
should look like:

```java
public value class Vec3 {
```

```shell
$ ./run.sh 500 20 out.png
render: imageWidth=500 samplesPerPixel=20 file=out.png
Elapsed: 15,415 ms
Allocated: 1,965 MB
```

Ain't that weird. We shaved off 1.5 Gb of allocations, but the elapsed time went
up instead? At least, let's analyze where the allocations disappeared. To
achieve that, I profiled the allocations for this version and then generated a
[diffgraph](/kb/profiling/clj-async-profiler/diffgraphs/) between the two
flamegraphs; here it is:

<center>
<figure class="figure">
<div class="downscale-iframe-66" style="height:320px">
<iframe src="/img/posts/valhalla-m1-alloc-diff-pojo-vec3value.html?hide-sidebar=false" style="height:480px"></iframe>
</div>
<figcaption class="figure-caption text-center">
    Allocation diffgraph between POJO version (before) and Vec3 being value
    object (after). <a href="/img/posts/valhalla-m1-alloc-diff-pojo-vec3value.html?hide-sidebar=false"
target="_blank">Click</a> to open.
</figcaption>
</figure>
</center>

Frames colored in blue represent the code where fewer allocations happen after
our change; red frames stand for code with increased allocations. Intuitively,
there shouldn't be any of the latter, so I attribute the red parts of the
diffgraph to either profiler inaccuracies or JVM migrating some allocations to
different code paths. We can observe significant skid — for example, you may
find `Vec` allocations within RandomGenerator methods (which couldn't be there).
But you can still click around, look at the Reversed view, and see the overall
55% reduction of `Vec3` allocations which is equal to -42% of total allocations.
But the JVM could not or did not consider it necessary to remove all heap
allocations of it.

Perhaps, by only making `Vec3` a value object, we are not utilizing the Valhalla
potential to its fullest. Considering `Vec3`'s presence inside most other
classes, marking those as value classes may enhance data inlining and deliver
the performance boost we crave. Here are the modified lines (compared to the
initial version):

```
src/raytracer/HitRecord.java:3:   public value class HitRecord {
src/raytracer/Scatter.java:3:     public value class Scatter {
src/raytracer/Sphere.java:3:      public value class Sphere {
src/raytracer/Ray.java:3:         public value class Ray {
src/raytracer/Material.java:7:    public static value class Lambertian implements Material {
src/raytracer/Material.java:24:   public static value class Metal implements Material {
src/raytracer/Material.java:46:   public static value class Dielectric implements Material {
src/raytracer/Vec3.java:3:        public value class Vec3 {
```

By rerunning it, we get:

```sh
$ ./run.sh 500 20 out.png
render: imageWidth=500 samplesPerPixel=20 file=out.png
Elapsed: 15,086 ms
Allocated: 1,308 MB
```

Despite reducing the allocations further, the elapsed time is still higher than
in the POJO version. The allocation diffgraph between the initial version and
the all-value one is, as expected, predominantly blue. However, the CPU
flamegraph reveals something new.

<center>
<figure class="figure">
<div class="downscale-iframe-66" style="height:320px">
<iframe src="/img/posts/valhalla-m1-alloc-diff-pojo-allvalue.html?hide-sidebar=true" style="height:480px"></iframe>
</div>
<figcaption class="figure-caption text-center">
    Allocation diffgraph between POJO version (before) and all value object version (after).
    <a href="/img/posts/valhalla-m1-alloc-diff-pojo-allvalue.html?hide-sidebar=false"
target="_blank">Click</a> to open.
</figcaption>
</figure>
</center>

<center>
<figure class="figure">
<div class="downscale-iframe-66" style="height:300px">
<iframe src="/img/posts/valhalla-m1-cpu-allvalue.html?hide-sidebar=false" style="height:450px"></iframe>
</div>
<figcaption class="figure-caption text-center">
    CPU flamegraph, all value version. <a href="/img/posts/valhalla-m1-cpu-allvalue.html?hide-sidebar=false"
target="_blank">Click</a> to open.
</figcaption>
</figure>
</center>

A few methods began to call an internal JVM function
`BarrierSetNMethod::nmethod_stub_entry_barrier`. Judging from its name, I assume
it is a memory barrier generated by JVM in places where the program invokes
native methods. Since we don't call any native code ourselves, it must be that
the JVM started generating these barriers after we've replaced plain objects
with value objects. The total runtime contribution of barriers is 30% (you can
write `Barrier` into the highlight field and press Highlight), and it is unclear
whether this behavior is unique to ARM and/or MacOS. In our specific example, it
appears that these barriers negate the performance benefits gained from using
value classes.

### Primitive classes

Value objects are not the only new concept introduced by Project Valhalla. At
some point, Valhalla designers decided to split their initial idea into two
distinct incarnations — value objects and **primitive objects**. While they both
lack the property of identity, there are differences between them — primitive
objects cannot be `null` or contain non-primitive fields. If you can satisfy
both requirements, then making the class a primitive instead of value can
(theoretically) bring an even greater performance uplift.

Let's do it gradually again by starting with our most active class, `Vec3`. The
updated definition should say:

```java
public primitive class Vec3 {
```

```sh
$ ./run.sh 500 20 out.png
render: imageWidth=500 samplesPerPixel=20 file=out.png
Elapsed: 12,617 ms
Allocated: 224 MB
```

We're finally getting somewhere! It's encouraging to see that the runtime is at
least on par with, or slightly better than, the POJO variant, and the
allocations are nearly all gone. Let's keep going and transform everything into
primitives:

```
src/raytracer/HitRecord.java:3:   public primitive class HitRecord {
src/raytracer/Scatter.java:3:     public primitive class Scatter {
src/raytracer/Sphere.java:3:      public primitive class Sphere {
src/raytracer/Ray.java:3:         public primitive class Ray {
src/raytracer/Material.java:7:    public static primitive class Lambertian implements Material {
src/raytracer/Material.java:24:   public static primitive class Metal implements Material {
src/raytracer/Material.java:46:   public static primitive class Dielectric implements Material {
src/raytracer/Vec3.java:3:        public primitive class Vec3 {
```

```sh
$ ./run.sh 500 20 out.png
render: imageWidth=500 samplesPerPixel=20 file=out.png
Elapsed: 12,285 ms
Allocated: 275 MB
```

Confusingly enough, allocations have slightly increased, and the runtime got a
bit lower. But overall, there's little effect from transitioning the remaining
classes to primitives. Taking a CPU profile of the all-primitive yields a
picture similar to the all-value version — memory barriers are still present in
the profile, and they seem to cause the slowdown. Here's a CPU diffgraph between
all-value and all-primitive builds; see that the biggest difference is the
vanishing of `Vec3.<vnew>` (a new special method akin to `<init>` but for value
classes) from the `Sphere.hit` stacktrace. Making `Vec3` a primitive might have
enabled its scalarization instead of creating a full object; but I'm speculating
here.

<center>
<figure class="figure">
<div class="downscale-iframe-66" style="height:320px">
<iframe src="/img/posts/valhalla-m1-cpu-diff-allvalue-allprim.html?hide-sidebar=true" style="height:480px"></iframe>
</div>
<figcaption class="figure-caption text-center">
    CPU diffgraph between all value version (before) and all primitive version (after).
    <a href="/img/posts/valhalla-m1-cpu-diff-allvalue-allprim.html?hide-sidebar=false"
target="_blank">Click</a> to open.
</figcaption>
</figure>
</center>

### FAIL???

To be honest, I was a bit disappointed by these results. Not disappointed with
Valhalla, of course, but rather with my own high hopes of significant
improvement in this task. I know that Project Valhalla is still in flux,
implementation and optimizations will change many times until the release, and
plenty of confounding variables are at play, such as CPU architecture, platform,
and my poor understanding of the topic. And yet, my enthusiasm dwindled, the sky
turned grey, and I became sad. Was writing this article a pure waste of effort?

One thing that still bothered me was that I had no access to CPU performance
counters during this test. M1 provides some counters, and there is [a way to
track
them](https://lemire.me/blog/2021/03/24/counting-cycles-and-instructions-on-the-apple-m1-processor/),
but nothing is readily available for Java yet. And I don't have another physical
computer beyond my laptop, just unsuitable consumer devices — a home server
built on ARM SBC, an ARM phone, an ARM tablet, a Steam Deck...

A Steam Deck! A portable computer that's based on an [amd64
CPU](https://www.steamdeck.com/uk/tech) and runs a fully-featured Arch Linux
derivative. You can SSH onto it, install packages, and launch any software, just
like on a regular computer. Is there a reason _not_ to repeat the benchmarks on
it? I couldn't find any.

<center>
<figure class="figure">
<table class="table">
<thead><tr><th> </th><th>Plain objects</th><th>Value objects</th><th>Primitive objects</th></tr></thead>
<tbody>
<tr><td>Execution time</td><td>22.7 sec</td><td>48.4 sec</td><td>12.0 sec</td></tr>
<tr><td>Allocated heap</td><td>3.4 GB</td><td>1.7 GB</td><td>1.4 GB</td></tr>
</tbody></table>
<figcaption class="figure-caption text-center">
    Benchmarking results on Steam Deck.
</figcaption>
</figure>
</center>

The first thing to notice is that the value class version has a staggering 2x
runtime degradation compared to plain classes, despite the shrinking
allocations. This performance degradation also manifested itself on M1, albeit
to a lesser extent. Nonetheless, we have finally witnessed the long-awaited
improvement — the all-primitives version has beaten the POJO version in speed by
a remarkable 1.9x! What's even more fascinating is that it surpassed the best M1
result. Notably, it was achieved while still allocating 1.4 GB worth of objects,
compared to ~200 MB on M1. This underscores how differently Valhalla might
behave depending on the platform it runs on.

I tried profiling all three versions on the Steam Deck with
**clj-async-profiler**, but the results showed nothing of interest. This may be
attributed to the absence of debug symbols for this early access build of the
JDK. Instead, here is me running the POJO version through
[perf](https://perf.wiki.kernel.org/index.php/Main_Page):

```shell
$ perf stat -e cycles,instructions,L1-dcache-load-misses,L1-dcache-loads,L1-icache-load-misses\
,L1-icache-loads,branch-misses,branches,dTLB-load-misses,dTLB-loads,iTLB-load-misses,iTLB-loads\
,stalled-cycles-backend,stalled-cycles-frontend -- ./run.sh 500 20 out.png

render: imageWidth=500 samplesPerPixel=20 file=out.png
Elapsed: 22,992 ms
Allocated: 3,397 MB

 Performance counter stats for './run.sh 500 20 out.png':

    90,554,326,338   cycles                                                          (35.89%)
   281,979,878,536   instructions              #   3.11  insn per cycle
                                               #   0.18  stalled cycles per insn     (35.90%)
    10,244,854,583   L1-dcache-load-misses     #   7.06% of all L1-dcache accesses   (35.83%)
   145,092,667,792   L1-dcache-loads                                                 (35.73%)
        36,231,776   L1-icache-load-misses     #   1.75% of all L1-icache accesses   (35.91%)
     2,075,693,944   L1-icache-loads                                                 (35.91%)
       103,496,590   branch-misses             #   0.89% of all branches             (35.97%)
    11,573,112,791   branches                                                        (36.01%)
         6,632,725   dTLB-load-misses          #   9.70% of all dTLB cache accesses  (36.11%)
        68,374,293   dTLB-loads                                                      (36.08%)
         2,316,490   iTLB-load-misses          #  12.57% of all iTLB cache accesses  (36.06%)
        18,435,572   iTLB-loads                                                      (36.01%)
    51,576,704,766   stalled-cycles-backend    #  56.96% backend cycles idle         (36.01%)
     1,194,470,957   stalled-cycles-frontend   #   1.32% frontend cycles idle        (35.87%)
```

When specifying the performance counters of interest using the `-e` argument,
it's important to note that a CPU has limited hardware registers available for
tracking these counters. So, when requesting more PMCs than available registers,
perf employs a technique called multiplexing. It switches between tracking
different events and, in the end, interpolates the results. This is fine for us
because the workload of our benchmark is uniform, allowing us to gather the
overall picture from partial results. The percentages within parentheses in perf
output indicate how long the event has been actually tracked.

This is my naïve interpretation of the provided perf output:

- **3.11 IPC** (instructions per cycle) signifies outstanding efficiency. It
  means that the ILP (instruction-level parallelism) is doing great in this
  task, and the pipelines are filled tightly.
- **7.06% L1d miss ratio** is pretty good, meaning that 93% of memory accesses
  are fulfilled from the L1 cache. Therefore, the cache utilization is already
  high in the POJO version, even before the Valhalla improvements. The same goes
  for **1.75% L1i miss%**.
- **0.89% branch miss ratio** is OK.
- **9.70% dTLB misses** and **12.57% iTLB misses** look somewhat scary, but the
  absolute numbers must be low since they don't seem to affect IPC enough.
- **1.32% frontend idle** is low and reinforces my understanding that there
  ain't much to be improved on the frontend. **56.96% backend idle** tells us
  that the workload is primarily backend-bound, with the
  [FPU](https://en.wikipedia.org/wiki/Floating-point_unit) potentially being the
  bottleneck.

I then ran the remaining two versions with **perf**, and here's the comparison
table:

<center>
<figure class="figure">
<table class="table table-striped">
<thead><tr><th>PMC</th><th>POJO</th><th>Value</th><th>Primitive</th></tr></thead>
<tbody>
<tr><td>cycles</td><td>90,554M</td><td>179,880M</td><td><b>50,008M</b></td></tr>
<tr><td>instructions</td><td>281,980M</td><td>434,353M</td><td><b>162,894M</b></td></tr>
<tr><td>IPC</td><td>3.11</td><td>2.41</td><td><b>3.26</b></td></tr>
<tr><td>L1-dcache-load-misses</td><td>10,245M</td><td>9,774M</td><td><b>1,821M</b></td></tr>
<tr><td>L1-dcache-loads</td><td>145,093M</td><td>303,624M</td><td><b>30,032M</b></td></tr>
<tr><td>L1-dcache-miss%</td><td>7.06%</td><td><b>3.22%</b></td><td>6.06%</td></tr>
<tr><td>L1-icache-load-misses</td><td><b>36.2M</b></td><td>38.0M</td><td>36.4M</td></tr>
<tr><td>L1-icache-loads</td><td>2,076M</td><td>2,350M</td><td><b>1,922M</b></td></tr>
<tr><td>L1-icache-miss%</td><td>1.75%</td><td><b>1.62%</b></td><td>1.89%</td></tr>
<tr><td>branch-misses</td><td>103.5M</td><td>104.3M</td><td><b>103.1M</b></td></tr>
<tr><td>branches</td><td>11,573M</td><td>12,211M</td><td><b>11,298M</b></td></tr>
<tr><td>branch-miss%</td><td>0.89%</td><td><b>0.85%</b></td><td>0.91%</td></tr>
<tr><td>dTLB-load-misses</td><td>6.6M</td><td>5.8M</td><td><b>5.5M</b></td></tr>
<tr><td>dTLB-loads</td><td>68.4M</td><td>66.2M</td><td><b>63.6M</b></td></tr>
<tr><td>dTLB-miss%</td><td>9.70%</td><td>8.80%</td><td><b>8.59%</b></td></tr>
<tr><td>iTLB-load-misses</td><td>2.3M</td><td>2.1M</td><td><b>2.0M</b></td></tr>
<tr><td>iTLB-loads</td><td><b>18.4M</b></td><td>20.0M</td><td>20.3M</td></tr>
<tr><td>iTLB-miss%</td><td>12.57%</td><td>10.70%</td><td><b>9.86%</b></td></tr>
<tr><td>stalled-cycles-backend%</td><td>56.96%</td><td>69.91%</td><td><b>41.97%</b></td></tr>
<tr><td>stalled-cycles-frontend%</td><td>1.32%</td><td><b>0.69%</b></td><td>2.13%</td></tr>
</tbody></table>
<figcaption class="figure-caption text-center">
    Perf results for three ray tracer versions on Steam Deck. For "IPC", higher
    is better. For the rest rows, lower is better. M stands for millions.
</figcaption>
</figure>
</center>

Let's start with the numbers for the value class version. Somehow, it had to
retire 54% more instructions than the POJO version and with a worse IPC of 2.41.
It exhibited twice as many L1d reads (basically, memory reads) despite
maintaining the same number of misses. L1i access, TLB access, and branch
prediction stats don't differ a lot from POJO. But the higher stalled backend
cycles percentage (69.91%) makes me think it's not solely the shortage of FPUs
causing the bottleneck, but perhaps also
[AGUs](https://en.wikipedia.org/wiki/Address_generation_unit) (cue the increased
memory reads).

Now I'll look at the top dog — the primitives version. By some miracle, its
number of instructions is 45% lower than in POJO and with the same stellar IPC.
The amount of L1d accesses is a whopping **4.8x** lower, and the number of
misses is **5.6x** lower. This suggests that Valhalla managed to successfully
scalarize a whole lot of operations and keep the data in the registers without
roundtripping through memory. L1i access, TLB access, and branching remain the
same or marginally better than in POJO. Finally, stalled backend cycles are
significantly lower at 41.97%, reaffirming my latest hypothesis that it's the
memory address generation that's causing the stalls, not just the floating point
math.

### Conclusions

The benchmark results on M1 demonstrated that I unfairly expected more swing
from Valhalla than this task had space for. For instance, this ray tracer
implementation doesn't rely on many arrays of composite objects where structure
inlining would help the performance. Most of the data structures used are
ephemeral and processed at the very place they are created. Sure, you have to
allocate them on the heap, but heap allocations are actually incredibly cheap in
Java. And the impact of GC turned out to be minimal for this workload.

The Steam Deck results, on the other hand, proved that Valhalla has plenty of
potential. Making an already efficient program run twice faster by simply adding
a few keywords is a big deal. This, to me, is the strongest part of Project
Valhalla — it offers a powerful facility available on demand and a la carte. It
costs almost nothing to try out Valhalla for your program (once it hits the JDK
upstream), and if it does work, then — hey — why say no to free performance?

That being said, if Valhalla ships in its present state, with the current means
of introspection and awareness (which is to say — missing), it may become an
advanced tool for highly specialized cases[[3]](#fn3)<a name="bfn3"></a>. What
I've experienced so far is that the approach to applying Valhalla optimizations
is very stochastic; their effects are unpredictable and require empiric
evaluation. While it's feasible in a toy project like this, it could become
overwhelming in a large real-world application to go through the possible
combinations of value and primitive classes and guess how the JVM would behave.
I hope that over time, we'll get better instruments to illuminate what's going
on with the data objects under the covers of the JVM.

This concludes my exploration of Project Valhalla for now. I'm excited about the
direction Java is heading, grateful for the careful evolution approach taken by
Java's designers, and happy that I did this experiment and learned more about
these cool new developments. I hope you are, too. Give Valhalla a try, and thank
you for reading!

#### References

- [Project Valhalla home page](https://openjdk.org/projects/valhalla/)
- [State of Valhalla: The Language
  Model](https://openjdk.org/projects/valhalla/design-notes/state-of-valhalla/02-object-model)
- [Valhalla Update with Brian
  Goetz](https://www.youtube.com/watch?v=1H4vmT-Va4o) (some details have changed
  since then, but it's still a useful reference)
- [Does Java Need Inline Types?](https://www.youtube.com/watch?v=jGjWs2xpZrY)

#### Footnotes

1. <a name="fn1"></a><span> Java designers don't like the term "stack
   allocation" in the context of Java because it is, in fact, impossible. Due to
   various design constraints, a program wouldn't be able to refer to an object
   if it were on the stack. Instead, the preferred term is "scalarization",
   which means breaking down the object into primitives and putting those onto
   the stack or even straight in CPU registers. This way, allocation can be
   avoided altogether. One example of an optimization that may enable
   scalarization is [escape
   analysis](https://blogs.oracle.com/javamagazine/post/escape-analysis-in-the-hotspot-jit-compiler).</span>[↑](#bfn1)
2. <a name="fn2"></a><span> This is the cumulative allocated amount over the
   entire run, not to be confused with the size of the retained set that has to
   be present in memory at any given time.</span>[↑](#bfn2)
3. <a name="fn3"></a><span> This is a very narrow and one-dimensional view of
   mine. Besides value and primitive classes, Project Valhalla also brings the
   unification of generics, generalized containers for reference objects and
   primitives alike, consolidation of objects and primitives under a single type
   hierarchy, and more. These broader features will be useful to all Java
   programmers.</span>[↑](#bfn3)

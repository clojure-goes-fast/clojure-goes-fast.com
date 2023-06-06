{:title "clj-async-profiler"
 :page-index 0
 :layout :kb-page}

[clj-async-profiler](https://github.com/clojure-goes-fast/clj-async-profiler) is
an embedded high-precision performance profiler for Clojure. clj-async-profiler
presents the profiling results as an interactive
[flamegraph](http://www.brendangregg.com/flamegraphs.html). You can navigate the
flamegraph, query it, change parameters and adapt the results for easier
interpretation.

<center>
<figure class="figure">
<img class="img-responsive" src="/img/kb/flamegraph-screenshot.png">
<figcaption class="figure-caption text-center">
    Example flamegraph.
</figcaption>
</figure>
</center>

<center>
<figure class="figure">
<iframe width="560" height="315" src="https://www.youtube.com/embed/s3mjVAMNVrA" title="YouTube video player" frameborder="0" allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share" allowfullscreen></iframe>
<figcaption class="figure-caption text-center">Video from London Clojurians meetup includes clj-async-profiler demonstration.</figcaption>
</figure>
</center>

clj-async-profiler is based upon
[async-profiler](https://github.com/jvm-profiling-tools/async-profiler) which is
a low overhead sampling profiler for Java.

### Why use clj-async-profiler?

Compared to other profiling tools, clj-async-profiler has several pronounced
strong points to make it your primary go-to profiler.

1. **High precision**. Historically, many Java profilers suffered from an issue
   called [safepoint
   bias](http://psy-lob-saw.blogspot.com/2015/12/safepoints.html). It meant that
   they produced skewed and incorrect results when profiling certain code, and
   hence you would focus on optimizing already fast parts of your programs while
   the actual slow parts remained slow to the profiler. clj-async-profiler
   doesn't have this problem.
2. **Low overhead**. With its single-digit percentage overhead,
   clj-async-profiler is suitable to be used on production servers to obtain the
   performance profile in real production scenarios.
3. **Embeddable**. Adding the profiler is as easy as appending a single
   dependency. It is convenient both during development and when profiling in
   production.
4. **Programmatically controllable**. Again, in production scenarios, being able
   to drive the profiler from your application code enables multiple interesting
   usage patterns, like starting the profiler on schedule, or as a reaction to
   some event.
5. **Convenient presentation**. Flamegraphs as profile representation are very
   descriptive and demonstrative. The underlying raw data is in plain text which
   makes it malleable to extra processing and transformation.
6. **Not just CPU profiling**. clj-async-profiling also supports profiling of
    allocations (shows which parts of you code allocate the most
    objects/memory), locks, context switches, and other events supported by your
    operating system.

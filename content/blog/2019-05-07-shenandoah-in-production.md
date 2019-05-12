---
name: "Shenandoah GC in production: experience report"
author: Alexander Yakushev
date-published: 2019-05-07 13:00:00
reddit-link: https://www.reddit.com/r/Clojure/comments/blotpm/shenandoah_gc_with_clojure_in_production/
hn-link: https://news.ycombinator.com/item?id=19885863
---

_Update: I've made several edits to the post since Aleksey Shipilëv was kind
enough to suggest many corrections and improvements._

If you closely follow JVM development scene, you've probably noticed that the
last few years have been a renaissance of Java garbage collectors. From G1
finally becoming a default garbage collector in Java 9 and onward, to Oracle's
[ZGC](https://wiki.openjdk.java.net/display/zgc/Main) which takes inspiration
from Azul's
[C4](https://www.azul.com/resources/azul-technology/azul-c4-garbage-collector/)
pauseless garbage collector, to
[Shenandoah](https://wiki.openjdk.java.net/display/shenandoah/Main) being
developed by Red Hat, it is evident that:

1. Garbage collection is by no means a solved problem.
2. People care about garbage collectors becoming faster and handling larger
   heaps.

In this post, I'd like to describe my experience using Shenandoah GC on a real
project at [Grammarly](https://grammarly.com) that was moderately demanding from
a performance perspective. This won't be a mere tribute to this piece of
technology or a rose-tinted walk in the park. Rather, I want to give you enough
motivation to care which GC you are running in your project, explain in which
situations Shenandoah can be superior, and provide enough tips on how to operate
it (or any other GC) in a production environment.

If you are ready, hop aboard for a wild GC ride!

## What is Shenandoah GC?

[Shenandoah GC](https://wiki.openjdk.java.net/display/shenandoah/Main) is a
mostly concurrent garbage collector for the JVM platform. It is developed by a
team at Red Hat, with the most notable participants being [Roman
Kennke](https://twitter.com/rkennke), [Aleksey
Shipilëv](https://twitter.com/shipilev), and [Christine
Flood](https://twitter.com/chf_the_grouch). Being concurrent means that the GC
tries to perform most work in parallel with the rest of the application. This
achieves Shenandoah's goal of minimizing the pauses that the GC inflicts on the
user code. Another Shenandoah's design goal is to work efficiently with both
small and large heaps.

It frankly doesn't make much sense to repeat all of the rich information
available on Shenandoah GC. If you are new to this topic, be welcome to read
Shenandoah's home page and watch the following talks by Alexei:

- [Shenandoah GC: The Garbage Collector That Could, JavaZone
  2018](https://vimeo.com/289626122)
- Shenandoah GC in Detail, [part
  one](https://www.youtube.com/watch?v=JBaZ4lK6OBk) and
  [two](https://www.youtube.com/watch?v=HBWaffsl7fo). This talk is unfortunately
  only available in Russian, but the slides are in English.

Anyway, here's a short list of statements about Shenandoah and other concurrent
GCs if you don't feel like diving into more informative sources right now:

- Classic GCs (also called STW for Stop-The-World) work by stopping all
  application threads whenever there is no free memory left, removing all
  garbage and (optionally[<sup>1</sup>](#fn1)<a name="bfn1"></a>) compacting
  live objects, and then resuming the application. This stop-the-world pause can
  last for up to tens of seconds and increases linearly with the size of the
  heap.
- Many modern GCs (e.g., G1) are also _generational_ — they group objects into
  several generations based on how many GC cycles the object has survived, and
  then for each generation, they apply different garbage collection strategies
  that are expected to be more efficient for that particular generation.
- Shenandoah GC also produces STW pauses, but it keeps them very short because
  it performs the bulk of the GC work concurrently while the application is
  running. The length of those STW pauses doesn't increase much with the size of
  the heap.
- Shenandoah GC is also **not** generational. This means that is has to mark
  most live objects every GC cycle (something that generational GCs can avoid).
  In return, Shenandoah is not penalized in workloads that do not benefit from
  generational hypothesis.
- Shenandoah GC pays the price for being concurrent with reduced application
  throughput (the application as a whole, not only the GC phase, is slower with
  Shenandoah GC).

How do you get Shenandoah? This garbage collector has officially become part of
JDK only since version 12 and is available in [AdoptOpenJDK 12
builds](https://adoptopenjdk.net/releases.html?variant=openjdk12&jvmVariant=hotspot#x64_linux).
If you are not ready yet to move onto Java 12, Shenandoah is also backported to
8 and 11. Refer to [this
page](https://wiki.openjdk.java.net/display/shenandoah/Main#Main-BinaryDevelopmentBuilds)
for the list of available binary builds.

With that covered, we are ready to move on to the reasons you may need
Shenandoah in your project.

## Reasons for using Shenandoah GC

There is a common misconception in the developer community that GC pauses are
only important in latency-critical applications, e.g., in high-frequency
trading. Hence, if you are writing something mundane, like another REST API,
choosing a proper GC should be the least of your concerns.

Indeed, if you are writing a program that can accommodate arbitrary long pauses,
picking a throughput-focused stop-the-world GC like ParallelGC is a valid thing
to do. A good example of such workload is a batch processing task — you don't
care about hiccups along the way as long as the final result arrives on time.

However, if you are writing any kind of an interactive application (be it an API
or a website), GC pauses become much more impactful. A GC pause stalls your
program completely, so to the outer world, it appears frozen. The obvious effect
is that the requests caught in the pause will receive responses later. Depending
on the duration of the pause (which, remember, can be up to tens of seconds with
conventional GCs), the client might give up on the request and time out. Or it
may decide to retry, so you now have even more pending requests that will demand
attention once the pause is over. Circuit breakers might open both in the
service or on the callers' side, and it will take them some time to close back.
A sufficiently long pause may even cause your service to fail healthchecks
several times in a row which makes the supervisor restart it. While one node is
restarting, the others must sustain higher load which increases their own
chances for experiencing a longer pause (cascading failure), and so on.

Unpredictable GC pauses create instabilities in the system that ripple far
beyond the paused application itself. Clients are back-pressured, their queues
overflow, TimeoutExceptions fly through monitoring tools causing pagers to beep
woefully. Of course, you should make your system robust to these and other sorts
of failures. In reality, though, for a system to accommodate hiccups, it must
have a sufficient buffer in terms of CPU time, queue length, acceptable response
time, etc. And like the boy who cried wolf, those expected micro-outages make
you tolerant to alerts and complacent when the real trouble happens.

Which brings us to another surprising point. Even though Shenandoah takes a cut
of your application's throughput, it may be cheaper to run Shenandoah rather
than a conventional GC. Throughput reduction is predictable, and it's easy to
plan for that — if your program runs 10% slower, bring up ~10% more servers;
that's about it. But long GC pauses are rapid and volatile; you can't
"autoscale" out of them, so in order to not fall over, you must allot extra
resources to handle them. These resources will be idle most of the time and will
eat money. The longer are the potential pauses, the bigger leeway you need.
Either that or accept your system being occasionally erratic.

But enough of me rambling. Let's hear some of that advertised experience of
running Shenandoah in a real project.

## First encounter and initial impressions

Let me start with a few words about the application I tried Shenandoah on to
give broader context. It is nothing more than a simplistic reverse proxy with
one-to-many fan-out and some pre-/post-processing. The request goes in, gets
slightly modified, then is sent to multiple upstreams, and once all responses
are collected, the combined response goes back to the client. This seemingly
trivial project is complicated by the fact that the requests and responses carry
quite large JSON payloads, and we want to handle them at a rate of **~10k
req/s**, in/out network bandwidth reaching **350 MB/s**. The heap size is set to
**57 GB** to fill the available memory on AWS c5.9xlarge instance. The
application doesn't have much of its own to keep in memory — but it must have
enough of it to contain the incoming requests that stay in the heap until the
upstream responses come back (up to 5 seconds).

Starting with G1 as usual, the newly created service worked reasonably well
before reaching the peak load, at which point it was becoming very whimsical and
fragile. Frequent ~100-200ms pauses were interleaved with occasional
multi-second FullGC events. Can you imagine what happens when a 10k RPS service
operating at ~70% capacity decides to take a five-second break? It grows a huge
backlog, and for the next few seconds after the pause, it spins like crazy
trying to dig through the queues. Both the requests caught in the pause and the
ones after it get a degraded QoS. And with just a bit of bad luck, another
problem (e.g., sudden traffic spike) might coincide with the pause in a perfect
storm, knocking your service entirely off its feet.

Tuning G1 options seemed to help at first but ultimately made the setup even
more unstable. It might seem straightforward to tinker with things like
young/old ratios, but in practice, it can bring the app into novel failure
modes. I confess not being a GC expert, and my approach to tuning was perhaps
too naive; nevertheless, you probably wouldn't expect a deeper level of
sophistication from an average Java application writer. If an ordinary Joe like
me can't tune a GC properly, then it's probably not such a good idea to do it.

After a few unfruitful attempts to make the service stable at the peak load, we
switched to a Shenandoah-enabled OpenJDK 8 image
(`shipilev/openjdk-shenandoah:8`) and plugged `-XX:+UseShenandoahGC` into the
command-line arguments. Then this happened:

<center><blockquote class="twitter-tweet" data-lang="en"><p lang="en" dir="ltr">Quiz time: guess the moment when -XX:+UseG1GC was replaced by <a href="https://twitter.com/shipilev?ref_src=twsrc%5Etfw">@shipilev</a>&#39;s -XX:+UseShenandoahGC. <a href="https://t.co/FvcyqCg5do">pic.twitter.com/FvcyqCg5do</a></p>&mdash; Alex Yakushev (@unlog1c) <a href="https://twitter.com/unlog1c/status/1060161215102705664?ref_src=twsrc%5Etfw">November 7, 2018</a></blockquote>
<script async src="https://platform.twitter.com/widgets.js" charset="utf-8"></script></center>

<!-- https://twitter.com/unlog1c/status/1060161215102705664 -->

What you see on the chart is the maximum GC pause length over time. Shenandoah
has cut the biggest "normal" pauses from 50-150 ms to 10-20 ms. What the chart
doesn't show is the multi-second pauses that G1 caused from time to time.
Shenandoah didn't seem to have such a problem (but it has different failure
modes; I'll mention them later).

Suddenly, the service started to perform much more reliably. Several performance
bottlenecks have been lifted, allowing us to push for higher throughput per
machine. We could raise the heap size to 57g (it actually started with 20g)
without the latencies growing much. Bigger heap gave a larger buffer to handle
traffic spikes. The overall QoS has improved, significantly reducing latency
percentiles over a longer timespan.

## Living on with Shenandoah

Our honeymoon with the new garbage collector has lasted for a while. Just
swapping a GC has indeed improved enough aspects of the application runtime to
call it a victory. However, if you care about the stability and performance of
your service, at some point you have to invest a bit more effort than just
changing one parameter. In this section, I will tell you about the tools, tips,
and knowledge necessary to run Shenandoah efficiently.

#### jvm-hiccup-meter

[jvm-hiccup-meter](https://github.com/clojure-goes-fast/jvm-hiccup-meter) is a
miniature library that measures system-induced pauses and stalls. It's a
maximally trimmed down version of [jHiccup](https://www.azul.com/jhiccup/)
library written by Gil Tene. While jHiccup is designed to accumulate pauses over
the whole program run, jvm-hiccup-meter runs continuously and exposes the
observed stalls via a callback.

This library may seem redundant given that Shenandoah (or any other Java GC)
exposes the information about GC pauses via MBeans and GC logs. However, the
hiccup meter may sometimes spot pauses that the GC fails to report or other
pauses unrelated to GC (e.g., from triggering a heap dump).

This "library" is just a single Java class, so if you don't feel like adding an
extra dependency to your project, simply copying the class works as well.

#### jvm-alloc-rate-meter

Most modern GCs, Shenandoah included, are designed to handle huge amounts of
garbage without much trouble. However, a concurrent GC must collect the garbage
faster than the application produces it; thus, tracking the rate at which your
program allocates objects is still a good idea.

Surprisingly enough, JVM doesn't expose the allocation rate in a convenient way.
You can get such information from GC logs, but it's not practical for real-time
monitoring. Instead, you can use another micro-library,
[jvm-alloc-rate-meter](https://github.com/clojure-goes-fast/jvm-alloc-rate-meter),
to measure the allocation rate at any given point of time and forward the data
to a monitoring solution. Having an always-on view on this metric gives you
intuition as to whether your program allocates too much and helps you detect
allocation spikes that may cause longer-than-usual GC pauses.

Like with the previous library, this one fits into one class file and can be
trivially included into the project as-is.

#### Allocation profiler

Knowing the absolute value of the allocation rate is useful, but when the time
comes to reduce the amount of garbage the application generates, a memory
profiler proves more beneficial. It can tell you which exact parts of your
program produce the most garbage so that you can focus on optimizing the most
impactful things first.

There are many memory profilers for JVM; we have settled with
[async-profiler](https://github.com/jvm-profiling-tools/async-profiler) (which
we use via
[clj-async-profiler](https://github.com/clojure-goes-fast/clj-async-profiler)).
Async-profiler uses a very non-invasive method of allocation profiling which
might not be very precise, but instead bears negligible overhead and is suitable
for production use. Besides, async-profiler draws pretty flame graphs which are
easily navigable and interpretable.

#### Shenandoah failure modes

With all its power and innovative design, Shenandoah is not magic — it's a piece
of software that runs in a real unforgiving world. Naturally, under certain
conditions, it can't deliver its paper-thin pauses. Because a concurrent GC runs
simultaneously with the rest of the program, it means the program can continue
to allocate objects while the GC is running. But if it creates garbage quicker
than the GC can collect it, we are in trouble. Shenandoah developers are very
upfront about the failure modes that their GC has, and they describe them
thoroughly in the documentation.

The first thing Shenandoah tries to do when it can't keep up with allocations is
called _pacing_. Shenandoah will inject small pauses into the _mutator threads_
(threads that allocate objects) to reduce the rate of garbage creation. This is
similar to an STW pause but not quite — it only impacts individual threads, not
the entire application. Monitoring pacing is a bit tricky since it is not
reported as a GC pause (formally, it isn't one). You'll have to resort to
reading GC logs to find out that pacing happened.

If that still doesn't help, Shenandoah will enter _degenerated mode_ which is an
old-school STW GC pass with the difference being that the GC work that has
already been performed concurrently won't be redone. In other words, if
Shenandoah almost made it on time concurrently but had to fall into degenerated
mode at the end, the pause would be shorter than if it had to do all the work in
the STW phase. Unlike pacing, degenerated mode GC will be reported as a proper
pause and will be visible by most monitoring tools. If you start seeing
Shenandoah dropping into the degenerated mode, it is the first sign that you
allocate too much, and you should use the profiler to cut down the most
garbage-producing pieces of the code.

Finally, full stop-the-world GC might still occur if the degenerated GC couldn't
free enough memory. Shenandoah's FullGC is parallel, so at least it will be
faster than having a single-threaded STW GC, but the pause might still be long.
Fortunately, we haven't yet experienced FullGC in our workloads.

#### Shenandoah tuning

Shenandoah runs incredibly well with default options, so chances are you'll
never have to change them. The most significant parameter you need to touch is
`-Xmx` — just give Shenandoah enough heap, and it will work perfectly. But as
you get more understanding of how it works, a few tunables can help you fit the
GC to your specific workload.

Shenandoah's main tuning knob is the type of _heuristic_ it uses to decide when
to trigger the GC. The default value for it is **adaptive**, under which the GC
infers the thresholds from the allocation rates it sees in the first few minutes
of the program launch. You can also change it to **static** and manually set the
amount of free memory left at which point the GC should trigger. If you value
latency more than throughput, you can even set the heuristic to **compact** —
this will make the GC run almost back-to-back[<sup>2</sup>](#fn2)<a
name="bfn2"></a> so that there is almost no chance of pacing/degraded mode
happenning. We eventually settled with the compact heuristic for this project,
and the CPU usage hasn't increased that much.

Another nice feature is that unlike with generational GCs, it is much harder to
render Shenandoah unstable by changing the tunables. You might hamper the
throughput somewhat but won't accidentally jeopardize the latencies. The
tunables themselves are subjectively more intuitive, and their impact is better
understood.

#### Nitty details and surprises

There is a lot of helpful information available about Shenandoah GC, and that
encourages you to investigate and understand it better. The GC logs are also
very informative, and there is an extra killer feature — once the application
shuts down, Shenandoah prints into the GC log a very detailed table describing
how much time each phase of the GC cycle took. Armed with this data, and the
documentation of Shenandoah internals, you can optimize GC times even further.
Here are a few discoveries I've made:

- Heavy churn of weak references (soft, phantom, finalizers) can substantially
  increase Shenandoah pauses because they must be processed in stop-the-world
  phase[<sup>3</sup>](#fn3)<a name="bfn3"></a>. Even if you don't employ weak
  references directly, some of the libraries or frameworks used in your program
  can rely on them. In our case, it was Netty's leak detection mechanism which
  uses finalizers to spot unreleased ref-counted objects. Disabling the leak
  detection in production noticeably improved the pause times.
- Java garbage collectors in general don't like contended synchronized blocks
  because those inflate the monitors and increase the size of the rootset. We
  made this mistake trying to save up fifty bytes per object by replacing a
  ReentrantLock with synchronized class methods. Ultimately, we rolled back to
  ReentrantLock implementation as Shenandoah reported higher pauses after our
  "optimization". This is actually nice because with other GC it wouldn't be so
  clear where the pauses came from.
- This one was a real surprise to me. Shenandoah was for some reason slowly
  degrading its performance over time, with pause times increasing twice after 7
  days of uptime. The final GC distribution table blamed class and classloader
  scanning. After some time spent debugging, it turned out that we had a class
  loading leak caused by [reflection callsite
  inflation](http://anshuiitk.blogspot.com/2010/11/excessive-full-garbage-collection.html).
  Apparently, there is a mechanism in JVM that generates classes at runtime for
  reflective access, and those classes were not unloaded for some reason. We
  band-aided the problem for now by setting <span style="white-space:
  nowrap;">`-Dsun.reflect.inflationThreshold=2147483647`</span>.
- Make sure Shenandoah gets enough threads! This, of course, should occur
  automatically — Shenandoah decides how many threads to use based on the number
  of CPU cores on the machine. But if you happen to use [Amazon
  ECS](https://aws.amazon.com/ecs/), and you run JVM 9+, and you forget to set
  [CPU
  shares](https://docs.aws.amazon.com/AmazonECS/latest/developerguide/task_definition_parameters.html#container_definition_environment)
  for the container (which many people do, yours truly included), Java will end
  up seeing only one CPU core! Shenandoah will use only one concurrent thread to
  do the collections, and the entire application will likely crash and burn.

## Clojure specifics

You might notice that throughout the whole essay, I haven't mentioned that the
project we've tested Shenandoah on is written in Clojure. There is nothing in
the post that is Clojure-specific, and it just highlights how seamlessly Clojure
programmers can tap into the vast and powerful Java ecosystem. Everything
written here applies as much to any JVM language as it does to Java. However,
there are a few peculiarities related to the combination of Shenandoah and
Clojure that are worth mentioning:

- Idiomatic Clojure code heavily utilizes immutable objects and collections, so,
  on average, a Clojure program would produce more garbage. Fortunately, this
  might become an issue only as you are pushing the very limits of application
  performance. And even then, with the tools like
  [clj-async-profiler](https://github.com/clojure-goes-fast/clj-async-profiler),
  you can discover the offending parts and then rewrite them to mutable
  collections/pure Java which nobody takes away from you when you are using
  Clojure.
- Because of how the Clojure compiler is implemented, for every Clojure function
  a dedicated Java class is generated. This leads to Clojure programs having
  quite a lot of classes. Shenandoah scans class objects during the
  stop-the-world phase, so the more there are classes, the longer are pause
  times. This means that you probably would get somewhat longer pauses with
  Shenandoah in Clojure compared to Java. So, Clojure might still not be a good
  fit for high-frequency trading, but for everything else, it should be more
  than enough.

## References

- Already mentioned a few times, [Shenandoah GC home
  page](https://wiki.openjdk.java.net/display/shenandoah/Main).
- [Aleksey Shipilëv's website](https://shipilev.net/) contains links to all his
  Shenandoah talks (and many others).
- [Understanding Java Garbage
  Collection](https://www.youtube.com/watch?v=Uj1_4shgXpk), in which Gil Tene,
  co-author of [Azul Zing](https://www.azul.com/products/zing/) and Azul C4,
  explains how different GCs work.
- Not directly related to GCs, but [How NOT to Measure
  Latency](https://www.youtube.com/watch?v=lJ8ydIuPFeU) by the very same Gil
  Tene gives great insight into why latencies matter.

## Conclusion

This post has already grown too big, so I'll keep the summary short. Shenandoah
is such an impressively designed garbage collector that just switching to it can
immediately bring value for your application. Despite being fresh and somewhat
experimental, it is ready to be used in production, and its stellar
observability and documentation will smooth any transition pains should there be
any. After you reap the initial benefits, it will encourage you to explore and
learn this domain further, improving your programs even more.

I hope that what you've read here inspired you to check out Shenandoah and see
if it fits your problem space. And if it does, share your experiences and spread
the word, so that more people get to know about new garbage collectors and what
they can do. Until next time!

#### Footnotes

1. <a name="fn1"></a> Some GCs, like [Concurrent Mark
  Sweep](https://docs.oracle.com/javase/8/docs/technotes/guides/vm/gctuning/cms.html)
  collector, don't perform compaction and thus may have lower pauses for some
  period of time. Eventually the heap becomes too fragmented though, and they
  drop to FullGC.[↑](#bfn1)
2. <a name="fn2"></a> There is logic in Shenandoah's compact heuristic to
   prevent fruitless cycles by tracking the amount of memory allocated since the
   last GC cycle. When the application doesn't allocate much, compact mode won't
   run back-to-back.[↑](#bfn2)
3. <a name="fn3"></a> This only applies to references with short-lived
   referents. Weak references with live referents don't trouble the GC.[↑](#bfn3)

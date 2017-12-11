---
name: "Profiling tool: async-profiler"
author: Alexander Yakushev
date-published: 2017-12-11
---

_Clojure Goes Fast is now on [Twitter](https://twitter.com/ClojureGoesFast)!
Follow me to see the latest posts, news, and interesting links._

In
the
[previous installment](http://clojure-goes-fast.com/blog/profiling-tool-jvisualvm/),
we learned how to use VisualVM to profile your Clojure applications. VisualVM is
an excellent profiler: beginner-friendly, has enough features but not bloated,
all-in-all a good one to dip your toes into the waters of profiling. However,
VisualVM (and many other JVM profilers) suffer
from [safepoint bias](http://psy-lob-saw.blogspot.com/2015/12/safepoints.html).
Long story short, most sampling profilers can get a snapshot of the stack only
at the so-called **safepoints** — points of execution where the runtime can stop
and look around. The placement of those safepoints is quite arbitrary, and it
hampers the accuracy of profilers that have this problem.

### async-profiler

Fortunately, other profilers exist that don't rely on safepoints to do the
sampling. One such profiler is an
open-source
[async-profiler](https://github.com/jvm-profiling-tools/async-profiler). It is a
low overhead sampling profiler that operates by attaching a native Java agent to
a running JVM process and collecting the stack traces samples by using
HotSpot-specific APIs. async-profiler works only on GNU/Linux and MacOS, and on
the former, it additionally utilizes `perf_events` to dig into native code as
well (more on that in some future blog posts).

You can build async-profiler yourself by cloning the repository and following
the README instructions. It ships with a convenient binary that can attach to
any JVM process and start profiling.

Unlike VisualVM, async-profiler doesn't have a GUI. One way to see its results
is printing them to a console, which is while being simple is not particularly
informative. But the modus operandi of async-profiler is to collect **collapsed
stacktraces** that can be later transformed into flame graphs.

### Flame graphs

Flame graphs are a relatively recent fashion in the world of profiling. They are
a visualization of profiled software, allowing the most frequent code-paths to
be identified quickly and accurately.

The most popular way of creating flame graphs is Brendan
Gregg's [FlameGraph](https://github.com/brendangregg/FlameGraph) library. The
set of scripts can take the profiling data and generate an SVG image from it.
The resulting image shows which methods appear on stack statistically more often
than others. Compared to conventional ways of representing profiled data (hot
method lists, trees), flame graphs are:

- **Demonstrative.** All profiling data fits into a single image and can be
  navigated from there. Flame graphs dramatically reduce the time it takes to
  dig through the data. Often a few glances are enough to figure out what's
  going on.
- **Portable.** Any modern browser can render an SVG file. It means that you
  don't have to install proprietary tools to view some old profiling data or
  deal with version incompatibilities. A flame graph is entirely self-contained
  — no need to install any browser extensions to view it, and the ancillary
  JavaScript is bundled into the same SVG.
- **Shareable.** For the same reason as above, it is so much easier to share a
  flame graph with a broader audience, compared to any other profiler output.
  You can put a link to the SVG file or embed it directly into the webpage, and
  anyone with a browser can reliably open it.
- **Pretty.** For what it's worth.

A flame graph also ships some basic interactivity, such as zooming and
searching, which adds clarity and convenience to deciphering the profiling data.
You can learn more about flame graphs by following the links at the end of this
post.

### clj-async-profiler

The two tools described above are perfectly fine to be used together, and each
on its own. They don't strive to be a complete profiling solution but rather
building blocks for a flexible and customizable profiling workflow that you
might need. But it doesn't mean there shouldn't be an *easy* way to consume
these two projects. With that in mind, I
created
[clj-async-profiler](https://github.com/clojure-goes-fast/clj-async-profiler), a
tool for on-demand creation of flame graphs from the statistical profiling data
sampled in the current or external JVM process.

Let's learn it by example. First, add the following dependency to your
project.clj/build.boot/[deps.edn](https://clojure.org/guides/deps_and_cli):

```clojure
[com.clojure-goes-fast/clj-async-profiler "0.1.0"]
```

Now we'll conceive a toy profiling example.

```clojure
(defn test-sum []
  (reduce + (map inc (range 1000))))

(defn test-div []
  (reduce / (map inc (range 1000))))

(defn burn-cpu [secs]
  (let [start (System/nanoTime)]
    (while (< (/ (- (System/nanoTime) start) 1e9) secs)
      (test-sum)
      (test-div))))
```

Our preparatory steps are done, time to run the profiler!

```clojure-repl
user=> (require '[clj-async-profiler.core :as prof])
nil

user=> (prof/start {})
"Starting [cpu] profiling\n"

user=> (burn-cpu 10)
nil

user=> (prof/stop {})
#object[java.io.File 0x5c9cd2f7 "/tmp/clj-async-profiler/results/flamegraph-2017-12-11-01-37-05.svg"]
```

The result of the `stop` function points to a generated SVG flame graph. You can
open it directly in the browser, like
`file:///tmp/clj-async-profiler/results/flamegraph-....svg`.

<center>
<figure class="figure">
<object type="image/svg+xml" data="/img/posts/async-profiler-flamegraph.svg" width="100%"></object>
<figcaption class="figure-caption text-center">
  <a href="/img/posts/async-profiler-flamegraph.svg" target="_blank">Click here</a>
  to open in full.</figcaption>
</figure>
</center>

The above picture shows us that our function `burn-cpu` appeared in almost 100%
of samples (no surprise), and that `test-div` accounts for 98.8% of the
execution time. That's how much slower the ratio division is when compared to
addition (but again, this is a toy example, don't take these numbers as a hard
evidence).

The image of a flamegraph is interactive. Try playing with it! You can highlight
stack frames to get the profiling numbers, you can click on them to zoom in and
out. You can also click **Search** to highlight all of the frames that match the
provided regexp and get their total time percentage. For example, you can enter
`^java.*`, and it will tell you that Java-side code takes 95.8% of the total
time in our test.

#### Bonus features

The function `(profile-for <duration> <options>)` starts the profiling, waits
for the specified duration in seconds, and then generates a flamegraph. It
returns a future that will deliver the path to the generated flamegraph; thus it
doesn't block the execution thread.

Functions `start`, `stop`, and `profile-for` optionally accept a process ID as
their first argument. If PID is provided, clj-async-profiler will profile the
external JVM process specified by the PID. By default, the JVM where profiler is
started from is profiled.

clj-async-profiler includes a basic webserver that allows viewing the
flamegraphs remotely. `(serve-files <port>)` will start the webserver, similar
to Python's `SimpleHTTPServer` on the specified port.

All features and options of clj-async-profiler are listed in
the [README](https://github.com/clojure-goes-fast/clj-async-profiler).

### Conclusion: VisualVM or async-profiler?

First, let's outline the advantages of each. Async-profiler:

- All benefits of flame graphs apply. Flame graphs are really pleasant to work
  with.
- Because the profiler can be controlled from within the application, it is much
  easier to automate and orchestrate the profiling, and integrate it with
  business logic if necessary.
- Potentially more accurate because it doesn't suffer from safepoint bias.
- Doesn't require JMX to be enabled for remote profiling — you can profile from
  REPL if you have it set up, or expose auxiliary HTTP endpoints to control the
  profiler.

Now, VisualVM:

- A complete profiling+monitoring solution. You don't only get profiling; you
  can also watch heap metrics, monitor GC activity, view thread stacks, etc.
- Has much more sophisticated GUI for exploring the profiled data, with rich
  filters, different views, and on-demand aggregations.
- Extendable with plugins that can give it even more features.
- Unless you do hardcore number crunching in tight int-bound `for` loops, the
  skew from safepoint bias wouldn't probably be noticeable to you.
- VisualVM is fully cross-platform. Async-profiler currently works only on
  GNU/Linux and MacOS.

To summarize, VisualVM is a high-level holistic platform for monitoring
different performance-related aspects of your application. It has an actual user
interface, somewhat obscure, but still gives more clues to a beginner.

Async-profiler, on the other hand, is more of a low-level "hacker" tool. It
doesn't put the profiling data into a walled garden of incompatible binary
formats. It can be started rapidly from within the application. And once you
familiarize yourself with flame graphs, reading the profiles for you will be
much faster than VisualVM's UI.

For me, async-profiler has become a go-to ad hoc profiler. But I continue to use
VisualVM for reasons mentioned above, and as a second pair of "eyes" to look at
the performance picture. I hope this post has expanded your knowledge about
profiling and introduced you to another useful tool in your arsenal.

Let it burn!


<!-- <img src="/img/posts/async-profiler-flamegraph.svg"> -->
<!-- <img class="img-responsive" src="/img/posts/visualvm-sampler.png"> -->
### References

- http://psy-lob-saw.blogspot.com/2015/12/safepoints.html
- http://psy-lob-saw.blogspot.co.za/2016/06/the-pros-and-cons-of-agct.html
- http://psy-lob-saw.blogspot.com/2016/02/why-most-sampling-java-profilers-are.html
- http://www.brendangregg.com/flamegraphs.html
- http://psy-lob-saw.blogspot.com/2017/02/flamegraphs-intro-fire-for-everyone.html

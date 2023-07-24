{:title "Profiling tool: async-profiler"
 :date-published "2017-12-11"
 :date-updated "2023-06-09"
 :reddit-link "https://www.reddit.com/r/Clojure/comments/7j2hr6/cljasyncprofiler_clojure_profiler_with_flame/"}

_Updated on 2023-06-09: bumped version numbers and usage examples._  
_A more comprehensive clj-async-profiler documentation is available on [Knowledge
Base](http://clojure-goes-fast.com/kb/profiling/clj-async-profiler/)._  
_Runnable code for this post can be found
[here](https://github.com/clojure-goes-fast/clojure-goes-fast.com/tree/master/code/async-profiler)._

In the [previous
installment](http://clojure-goes-fast.com/blog/profiling-tool-jvisualvm/), we
learned how to use VisualVM to profile your Clojure applications. VisualVM is an
excellent profiler: beginner-friendly, has enough features but not bloated,
all-in-all a good one to dip your toes into the waters of profiling. However,
VisualVM (and many other JVM profilers) suffer from [safepoint
bias](http://psy-lob-saw.blogspot.com/2015/12/safepoints.html). Long story
short, most sampling profilers can get a snapshot of the stack only at the
so-called **safepoints** — points of execution where the runtime can stop and
look around. The placement of those safepoints is quite arbitrary, and it
hampers the accuracy of profilers that have this problem.

### async-profiler

Fortunately, other profilers exist that don't rely on safepoints to do the
sampling. One such profiler is an open-source
[async-profiler](https://github.com/async-profiler/async-profiler). It is a low
overhead sampling profiler that operates by attaching a native Java agent to a
running JVM process and collecting the stack traces samples by using
HotSpot-specific APIs. async-profiler works only on GNU/Linux and MacOS, and on
the former, it additionally utilizes `perf_events` to dig into native code as
well.

The output of async-profiler is commonly rendered with flame graphs. Flame
graphs are a relatively recent fashion in the world of profiling. They are a
visualization of profiled software, allowing the most frequent code-paths to be
identified quickly and accurately.

The most popular way of creating flame graphs on your own is Brendan
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

Clojure users have convenient access to async-profiler in the form of
[clj-async-profiler](https://github.com/clojure-goes-fast/clj-async-profiler).
Unlike VisualVM, clj-async-profiler is not a standalone application, but a
library that you include into your own application as a dependency.

Let's learn it by example. First, add the following dependency to your
`project.clj` or `deps.edn`:

```clojure
[com.clojure-goes-fast/clj-async-profiler "1.0.4"]
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

user=> (prof/profile (burn-cpu 10))
nil
```

After the execuion finishes, the flamegraph will be generated in
`/tmp/clj-async-profiler/results/` directory. You can open it directly in the
browser, like
`file:///tmp/clj-async-profiler/results/01-cpu-flamegraph-.....html`, or run
`(prof/serve-ui 8080)` to start a profiler UI local webserver.

<center>
<figure class="figure">
<div class="downscale-iframe-66" style="height:500px">
<iframe src="/img/posts/async-profiler-burn-cpu.html" style="height:750px"></iframe>
</div>
<figcaption class="figure-caption text-center">
    <a href="/img/posts/async-profiler-burn-cpu.html" target="_blank">Click here</a> to open in full.
</figcaption>
</figure>
</center>

The above picture shows us that our function `burn-cpu` appeared in 92% of
samples, and that `test-div` accounts for 91.32% of the execution time. That's
how much slower the ratio division is when compared to addition (but again, this
is a toy example, don't take these numbers as a hard evidence).

The image of a flamegraph is interactive. Try playing with it! You can highlight
stack frames to get the profiling numbers, you can click on them to zoom in and
out. You can also use **Highlight** section on the right to highlight all of the
frames that match the provided regexp and get their total time percentage. For
example, you can enter `/java.*/`, and it will tell you that Java-side code
takes 92.76% of the total time in our test.

#### Bonus features

The function `(profile-for <duration> <options>)` starts the profiling, waits
for the specified duration in seconds, and then generates a flamegraph. It
returns a future that will deliver the path to the generated flamegraph; thus it
doesn't block the execution thread.

Functions `start`, `stop`, `profile`, `profile-for` optionally accept a process
ID as `:pid` argument in the options map. If PID is provided, clj-async-profiler
will profile the external JVM process specified by the PID. By default, the JVM
where profiler is started from is profiled.

All features and options of clj-async-profiler are listed in the
[README](https://github.com/clojure-goes-fast/clj-async-profiler) and [Knowledge
Base](http://clojure-goes-fast.com/kb/profiling/clj-async-profiler/).

### Conclusion: VisualVM or clj-async-profiler?

First, let's outline the advantages of each. clj-async-profiler:

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
- It can still give you a pretty good coarse picture about the performance
  profile if you don't look too closely at the methods on the top of the stack.
- When non-CPU things become involved (like I/O), VisualVM can be more useful
  since it reports both CPU time and total time.
- VisualVM is fully cross-platform. Async-profiler currently works only on
  GNU/Linux and MacOS.

To summarize, VisualVM is a high-level holistic platform for monitoring
different performance-related aspects of your application. It has an actual user
interface, somewhat obscure, but still gives more clues to a beginner.

*UPDATE: Since the time of this post, [Java Mission
Control](http://www.oracle.com/technetwork/java/javaseproducts/mission-control/java-mission-control-1998576.html)
had become free. JMC has most of the benefits of VisualVM and also isn't
influenced by safepoints.*

clj-async-profiler, on the other hand, is more of a low-level "hacker" tool. It
doesn't put the profiling data into a walled garden of incompatible binary
formats. It can be started rapidly from within the application. And once you
familiarize yourself with flame graphs, reading the profiles for you will be
much faster than VisualVM's UI.

For me, clj-async-profiler has become a go-to ad hoc profiler. But I continue to
use VisualVM for reasons mentioned above, and as a second pair of "eyes" to look
at the performance picture. I hope this post has expanded your knowledge about
profiling and introduced you to another useful tool in your arsenal.

Let it burn!

### References

- [http://psy-lob-saw.blogspot.com/2015/12/safepoints.html](http://psy-lob-saw.blogspot.com/2015/12/safepoints.html)
- [http://psy-lob-saw.blogspot.co.za/2016/06/the-pros-and-cons-of-agct.html](http://psy-lob-saw.blogspot.co.za/2016/06/the-pros-and-cons-of-agct.html)
- [http://psy-lob-saw.blogspot.com/2016/02/why-most-sampling-java-profilers-are.html](http://psy-lob-saw.blogspot.com/2016/02/why-most-sampling-java-profilers-are.html)
- [http://www.brendangregg.com/flamegraphs.html](http://www.brendangregg.com/flamegraphs.html)
- [http://psy-lob-saw.blogspot.com/2017/02/flamegraphs-intro-fire-for-everyone.html](http://psy-lob-saw.blogspot.com/2017/02/flamegraphs-intro-fire-for-everyone.html)

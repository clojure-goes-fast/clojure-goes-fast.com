{:title "Clojure's slow start — what's inside?"
 :date-published "2018-01-02 13:30:00"
 :reddit-link "https://www.reddit.com/r/Clojure/comments/7nlvrf/clojures_slow_start_whats_inside/"
 :hn-link "https://news.ycombinator.com/item?id=16051257"}

It is not surprising that the question of Clojure's long startup time is raised
time and time again on forums and social media. This topic concerns users who
want to use Clojure as a scripting language, or who have a particular workflow
which requires restarting the Clojure program often. Compared to languages like
Python, Ruby, and JavaScript all of which have less than 100 millisecond startup
time, Clojure's seconds or even tenth of seconds seem incredibly slow and
wasteful.

The nature of Clojure loading is not exactly a secret. A few years ago Nicholas
Kariniemi profiled Clojure startup process and described his findings in the
post
["Why is Clojure bootstrapping so slow?"](http://blog.ndk.io/clojure-bootstrapping.html).
One of the points Nick brings forward is that JVM is not to blame for Clojure's
slowness — after all, a HelloWorld in Java executes in 50 ms. This is only
partly true, JVM's startup time is not a problem, but the JVM as a platform
makes Clojure starting time worse. JVM is optimized towards running long-lived
programs and invests a lot of time upfront when initializing the classes. That's
why Clojure on JVM boots much slower than ClojureScript on NodeJS.

The silver lining in this situation, and the main thing that keeps Clojure alive
even with its ludicrously long bootstrap, is the REPL, of course. So what that
you have to wait seconds until the project starts — you'll get your revenge
later, when the results in the REPL come back instantly while others are busy
recompiling and reloading. With some dexterity, you might not restart your REPL
for days. E.g., Leiningen and Boot both allow to dynamically inject new
dependencies into a running REPL (in Leiningen you'll
need
[clj-refactor](https://github.com/clojure-emacs/clj-refactor.el/wiki/cljr-add-project-dependency) or
[Alembic](https://github.com/pallet/alembic), Boot can do that by itself). If
you are worried about polluting your REPL state, you can adopt a workflow
like [Reloaded](https://github.com/stuartsierra/reloaded). Even when writing new
scripts, I prefer starting a REPL, connecting to it from Emacs, and then
interactively produce pieces of the script as I go.

So, there aren't many reasons to restart Clojure programs as you would do with
Python, for example. Yet the questions stand — what exactly is behind Clojure's
slow start and can we do anything about it? Today we'll try to answer the first
one in an exhaustive and reproducible way.


### Experiment description

We are going to profile Clojure's "time to REPL" for three common use cases:
bare Clojure
using
[Clojure CLI](https://clojure.org/guides/deps_and_cli),
[Leiningen](https://leiningen.org/), and [Boot](http://boot-clj.com/). In all
three cases, we'll be using the minimum possible number of dependencies (nothing
besides what comes with or is required by the build tool). Clojure version is
1.9.0 in all cases.

For profiling, we'll take
[async-profiler](https://github.com/jvm-profiling-tools/async-profiler) which
has the ability to begin profiling from the very start of JVM process. In the
[previous post](/blog/profiling-tool-async-profiler/), I announced a Clojure
wrapper around async-profiler — we won't need it here because it is not suited
for startup profiling. The default async-profiler is precisely what we need
here.

We will not add any "accelerating" JVM flags like `-XX:+TieredCompilation
-XX:TieredStopAtLevel=1 -Xverify:none`. While I use those day-to-day, the choice
to enable them is opinionated and has certain tradeoffs.

This post is about profiling, not benchmarking, so the parameters of my testing
rig are less critical. For what it's worth, I ran these experiments on MacBook
Pro 2015 with 3.1 GHz Core i7.

### Setup

To reproduce the experiments in this post do the following steps.

Get async-profiler. First, clone it
from [Github](https://github.com/jvm-profiling-tools/async-profiler). Then
you'll have to compile the native agent with `make`. Note that `JAVA_HOME`
environment variable must be set when running make. On MacOS you can do:

    JAVA_HOME=$(/usr/libexec/java_home) make

If all goes well, you'll find a directory `build`, with two files: `jattach` and
`libasyncProfiler.so`. We'll only be needing the last one. You may move this
file into a more convenient place or keep it here; just keep in mind that
whenever in the post we refer to `libasyncProfiler.so`, it means the valid path
to the file.

Clone [FlameGraph](https://github.com/brendangregg/FlameGraph) repository. We'll
need it for generating flamegraphs from the profiling data. We'll invoke
specifically `flamegraph.pl`, again, substitute it with a valid path when
running this command.

[This guide](https://clojure.org/guides/getting_started) describes how to
install Clojure CLI. [Boot](http://boot-clj.com/)
and [Leiningen](https://leiningen.org/) can be downloaded from their respective
websites.

### Clojure

Check if you have the correct Clojure version:

```console
$ clj                                                                                                                                                   ~
Clojure 1.9.0
user=>
```

Now let's measure the time from start to instant exit:

```console
$ time clj -e '(System/exit 0)'
clj -e '(System/exit 1)'  1.80s user 0.12s system 181% cpu 1.057 total
```

The time is around 1 second. It is much less than dozen-of-seconds load times
you might have experienced with real-world projects. So, Clojure is not the
primary time hog after all. But it is still too long for something so seemingly
simple. Let's find out what is happening exactly.

```console
$ cd /tmp
$ clj -J-agentpath:/path/to/agent/libasyncProfiler.so=start,event=cpu,file=raw-clojure.txt,collapsed -e '(System/exit 0)'
$ /path/to/FlameGraph/flamegraph.pl --colors=java --minwidth 2 raw-clojure.txt > raw-clojure.svg
```

Now, you can view the resulting flamegraph in the browser by
visiting [file:///tmp/raw-clojure.svg](file:///tmp/raw-clojure.svg). Or just see
my image below.

<center>
<figure class="figure">
<object type="image/svg+xml" data="/img/posts/slow-startup-profile-raw-clojure.svg" width="100%"></object>
<figcaption class="figure-caption text-center">
  Raw Clojure startup flamegraph
  (<a href="/img/posts/slow-startup-profile-raw-clojure.svg" target="_blank">View in full</a>)
</figcaption>
</figure>
</center>

_Note: I trimmed my flamegraphs a little so that they fit easier on the screen.
Frames like (require ...) are manually collapsed._

If you haven't learned yet how to read flamegraphs, now is the excellent
opportunity to do so. The vertical axis shows stacktraces going from bottom to
top. On the horizontal axis, the width corresponds to how often the frame was
seen on the stack during the execution (≈ time spent in the method). Relative
position of frames on X-axis is arbitrary and doesn't mean anything. You can
click the frames to zoom in/out on particular stacktraces.

You can explore the flamegraph on your own; meanwhile, I will list the main
points of interest, together with some explanations. If you can't find a
stackframe I'm referring to, use **Search** button in the top right corner and
enter the sought frame.

- The biggest part is `clojure/lang/RT.load` (73.03%) that comes into
  `clojure/core__init.<clinit>` (64.29%). That corresponds to the initialization
  of
  [clojure.core](https://github.com/clojure/clojure/blob/master/src/clj/clojure/core.clj) namespace.
  The time spent there is so long because the namespace is that large, it is
  already ~7700 LOC and counting.
    - Its largest constituent is `clojure/core__init.load` (59.36%). This is
      where Vars are assigned their values and metadata, and top-level
      non-defining forms are executed. We can still distinguish several big
      chunks.
        - `clojure/core$fn__8055.invoke` (7.14%) corresponds
          to
          [this form](https://github.com/clojure/clojure/blob/clojure-1.9.0/src/clj/clojure/core.clj#L6707) which
          loads
          [core_instant18](https://github.com/clojure/clojure/blob/clojure-1.9.0/src/clj/clojure/core_instant18.clj) namespace
          (actually, I couldn't tell it from the flamegraph, I had to go to
          decompile the generated class). I'm not sure why the namespace takes
          so long to load though — it seems tiny, with just a single
          `extend-protocol` call. Needs further investigation.
        - `(load ...)` (11.70%) and `(require ...)` (5.42%) loads and
          initializes many medium-to-big namespaces like `clojure.pprint`,
          `clojure.java.io`, `clojure.core.protocols`, etc. Those namespaces
          contain many definitions and plenty of top-level execution which adds
          to the loading time significantly.
        - `java/lang/ClassLoader.loadClass` (23.40%) is where the JVM loads the
          monster of a class that `clojure/core__init.class` is (by the way, its
          size is 316Kb).
- The tower on the right, `clojure/lang/Var.invoke` (18.23%), loads
  `clojure.core.server` namespace, which through `clojure.main` triggers loading
  of `clojure.spec.alpha`. [Spec](https://clojure.org/about/spec) is the latest
  addition to the language, and while it has many useful applications, it is a
  big piece of machinery that takes a while to load.

We see that the most significant contributor to Clojure load time is the size of
its base library. There are several potential ways to help that:

- Reduce the time needed to load each definition. This would be perfect, but
  there are no obvious ways to do so yet.
- Separate the namespaces more and reduce the number of definitions that need to
  be loaded immediately. Achievable, but does only so much — when more libraries
  and code are added to the project, the delayed definitions will probably get
  loaded, incurring the time costs anyway.

### Leiningen

We are using Leiningen 2.8.1.

```console
$ lein version                                                                                                                                              ~
Leiningen 2.8.1 on Java 1.8.0_102 Java HotSpot(TM) 64-Bit Server VM
```

To profile Leiningen startup, we need a more complicated setup. First, we'll
have to create a new Lein project; otherwise, there is no way to specify the
exact Clojure version for `lein repl`.

```console
$ lein new profile-lein
$ cd profile-lein
$ sed -i 's/1\.8\.0/1.9.0/g' project.clj
$ lein repl
...
Clojure 1.9.0
```

Now we can measure the startup time with Lein:

```console
$ echo '(System/exit 0)' | time lein repl
...
lein repl  6.88s user 0.61s system 124% cpu 6.003 total
```

The elapsed time of 6 seconds is much more than Clojure's original second.
Let's find out what exactly happens in there. There are some changes to be made
to `project.clj`, here's what it should look like:

```
(defproject profile-lein "0.1.0-SNAPSHOT"
  :jvm-opts ["-agentpath:/path/to/agent/libasyncProfiler.so=start,event=cpu,file=lein-child.txt,collapsed"]
  :dependencies [[org.clojure/clojure "1.9.0"]])
```

And then launch the REPL like this:

```console
$ echo '(System/exit 0)' | LEIN_JVM_OPTS='-agentpath:/path/to/agent/libasyncProfiler.so=start,event=cpu,file=lein-host.txt,collapsed' lein repl
```

You've probably noticed that we specified the async-profiler agent string twice.
It is not accidental. Leiningen launches two Clojure processes: one for itself,
and one for the actual project. We need to profile both of them, so we do, and
we save the output to different files, `lein-host.txt` and `lein-child.txt`.
Then, thanks to the beautiful textual nature of collapsed stacktraces, we can
straightforwardly merge them[[1]](#fn1)<a name="bfn1"></a>:

```console
$ cat lein-host.txt lein-child.txt > lein.txt
$ /path/to/FlameGraph/flamegraph.pl --colors=java --minwidth 2 lein.txt > lein.svg
```

<center>
<figure class="figure">
<object type="image/svg+xml" data="/img/posts/slow-startup-profile-lein.svg" width="100%"></object>
<figcaption class="figure-caption text-center">
  Leiningen startup flamegraph
  (<a href="/img/posts/slow-startup-profile-lein.svg" target="_blank">View in full</a>)
</figcaption>
</figure>
</center>

I have heavily redacted this flamegraph to make it easier to navigate. Your
flames would be higher.

- On the left, you'll see a familiar subgraph, `clojure/main.<clinit>` (26.40%),
  where the base Clojure is loaded. One thing is different: it contains twice
  the sample count compared to when we profile bare Clojure. This is because
  Leiningen loads Clojure twice.
- Green `java/lang/Thread.run` (10.61%) on the right contains initialization of
  Leiningen namespaces and tools.nrepl middleware. Significant part goes to
  loading clojure-complete (`clojure/lang/AFn.call`, 6.17%).
- Now to the elephant in the room, `clojure/main.main` (62.00%).
    - `clojure/lang/RestFn.applyTo` (18.41%) consists mostly of
      `tools.nrepl.sever` initialization.
    - `clojure/core$require.invokeStatic` (13.88%) in the middle eventually
      comes to loading [pomegranate](https://github.com/cemerick/pomegranate), a
      library for resolving dependencies through Maven.
    - Thin column `leiningen/core/project$read.invoke` (3.61%) goes through
      `leiningen/core/project$load_plugins.invoke` (3.32%) towards
      `cemerick/.../aether$resolve_dependencies` (3.18%).
      Looks like here Leiningen resolves plugins through Pomegranate.
    - `leiningen/core/main$task_args.invoke` (5.57%) also seems to be executing
      nREPL-related code.
    - What's left, `leiningen/core/main$apply_task.invoke` (20.51%) ultimately
      calls [Reply](https://github.com/trptcolin/reply), Clojure's alternative
      REPL client.

With this knowledge we can approximate that Leiningen's bootstrap process
consists of:

- Loading Clojure twice (26.40%, 1.58 s)
- Resolving dependencies (17.49%, 1.05 s)
- Initializing REPL server and client: (50.66%, 3.04 s)

I think there is room for improvement here, especially on the REPL side. More
thorough investigation of tools.nrepl and Reply might reveal potential
candidates for optimization.

#### Boot

Check Boot version. If Clojure is not 1.9.0, change it in `~/.boot/boot.properties`.

```console
$ boot -V
...
BOOT_CLOJURE_VERSION=1.9.0
BOOT_VERSION=2.7.2
```

Now measure the start time:

```console
$ time boot repl -s
boot repl -s  14.71s user 0.60s system 288% cpu 5.303 total
```

5.3 seconds, almost the same as Leiningen.

```console
$
BOOT_JVM_OPTIONS='-agentpath:/path/to/agent/libasyncProfiler.so=start,event=cpu,file=boot.txt,collapsed' boot repl -s
$ /path/to/FlameGraph/flamegraph.pl --colors=java --minwidth 2 boot.txt > boot.svg
```

<center>
<figure class="figure">
<object type="image/svg+xml" data="/img/posts/slow-startup-profile-boot.svg" width="100%"></object>
<figcaption class="figure-caption text-center">
  Boot startup flamegraph
  (<a href="/img/posts/slow-startup-profile-boot.svg" target="_blank">View in full</a>)
</figcaption>
</figure>
</center>

- Left part, `Boot.main` (19.35%) loads Boot's own namespaces. Boot has quite a
  lot of features, thus plenty of code to load.
- Our old friend, Clojure initialization graph, is in the middle as
  `clojure/lang/RT.<clinit>` (36.89%). Unlike Leiningen, Boot doesn't start two
  JVM processes, but the number of samples loading Clojure is on par with Lein.
  Why is it so? It is due to Boot's feature
  called [pods](https://github.com/boot-clj/boot/wiki/Pods). Boot runs in a
  single JVM process, but it still loads Clojure **twice**, one for its own
  purposes (build tooling), and one for the application context.
- Green `.../ClojureRuntimeShimImpl.require` (21.00%)
  seems to be initializing more Boot namespaces.
- `clojure/lang/AFn.call` (19.75%) on the right resolves and adds dependencies
  (4.46%), initializes tools.nrepl (11.31%), and prettifies REPL exceptions with
  [pretty](https://github.com/AvisoNovate/pretty) (2.75%).

To summarize, Boot spends those 5.3 seconds on:

- Loading Clojure twice (36.89%, 1.96 sec)
- Initializing its own namespaces (43.1%, 2.28 sec)
- Preparing the REPL (14.06%, 0.75 sec)
- Resolving dependencies (4.46%, 0.24 sec)

I think the easiest way to reduce Boot loading time would be to trim the number
of functions that need to be loaded from the start. From there, there are no
immediately apparent optimizations.

### Conclusions

As it turns out, Clojure start time is a complicated and multi-dimensional
topic. Clojure projects are slow to start not only because of JVM — JVM itself
starts in ~50 ms — but because of JVM specifics the classes are loaded slowly.
Clojure projects are slow to start not only because of Clojure — Clojure itself
starts in ~1 second — but because of Clojure specifics, the namespaces,
especially not AOT-compiled one, are loaded slowly. And so on.

In this post, we have observed three common ways to use Clojure and discovered
where the initial lag comes from. We haven't even started on long loading times
of big projects with huge dependency trees. But now you know how to profile the
startup of your application regardless of the build tool you are using. And
meanwhile your REPL is loading, go make yourself a cup of tea. You earned it.

### References

- [Improving Clojure Start Time](https://dev.clojure.org/display/design/Improving+Clojure+Start+Time)
- [Why is Clojure bootstrapping so slow?](http://blog.ndk.io/clojure-bootstrapping.html)

##### Footnotes

1. <a name="fn1"></a><span> Notice
   a
   [Useful use of cat(1)](https://www.in-ulm.de/~mascheck/various/uuoc/)!</span>[↑](#bfn1)

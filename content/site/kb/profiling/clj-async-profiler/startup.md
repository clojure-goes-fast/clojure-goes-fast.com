{:title "Startup profiling"
 :page-index 5
 :layout :kb-page
 :toc true}

clj-async-profiler can be conveniently started from the REPL of the process you
want to profile or from the browser UI within that same process. In either case,
the application has to be already running, and clj-async-profiler needs to be
loaded. There are times, however, when you want profile the application right
from the beginning (from the JVM launch); or it is not obvious where to put
clj-async-profiler initiliazation code (maybe, it's not a Clojure application at
all). Regardless, clj-async-profiler offers a way to collect such profiles while
retaining access to its flamegraph rendering features.

The underlying library that clj-async-profiler uses, **async-profiler**, has a
startup profiling mode that requires launching the Java process with its agent.
The parameters passed to this agent require knowing their custom syntax. You
also need the version of the agent (which is native library) that is correctly
compiled for you current CPU architecture. clj-async-profiler simplifes both of
those troubles for you.

First, you have to launch a REPL **in a different project**, not in the one you
want to profile the startup time. In fact, you can launch it outside of any
project, only specifying the clj-async-profiler dependency, like this:

```shell
$ clj -Sdeps "{:deps {com.clojure-goes-fast/clj-async-profiler {:mvn/version \"1.2.0\"}}}"
```

Then, type this into the REPL:

```clj
user=> (require '[clj-async-profiler.core :as prof])
user=> (prof/print-jvm-opt-for-startup-profiling {})
;; Text below is printed by the function.

Add this as a JVM option for the Java process you want to profile.
If you use Clojure CLI to launch, don't forget to add -J in front:

    -agentpath:/tmp/clj-async-profiler/libasyncProfiler-darwin-universal.so=start,event=cpu,file=/tmp/clj-async-profiler/results/01-startup-cpu-2023-10-11-19-50-07.txt,interval=1000000,collapsed

Once the process finishes running, go back to this REPL and run this to generate the flamegraph:

    (clj-async-profiler.core/generate-flamegraph "/tmp/clj-async-profiler/results/01-startup-cpu-2023-10-11-19-50-07.txt" {})
```

The function will produce a JVM option that specifies the path to the unpacked
agent library complete with the necessary parameters. The map of options you
pass to `print-jvm-opt-for-startup-profiling` is mostly the same as you would
pass to `start` (see [Functions and
options](/kb/profiling/clj-async-profiler/basic-usage#functions-and-options)).
The string that it gives you (the one that starts with `-agentpath`) is a JVM
option which you need to add to the launch of a JVM process you want to profile.
How to do that depends on the build tool/runner that you use to start your
Clojure or Java program. For example, if you use
[tools.deps](https://clojure.org/guides/deps_and_cli), you can either add this
string to `:jvm-opts` in your `deps.edn`, or add directly to the shell command
with `-J` prefix.

Let's try to profile Clojure's startup to see what it is mostly doing while
loading. We invoke the newly learned function first:

```clj
user=> (prof/print-jvm-opt-for-startup-profiling {:interval 10000, :threads true})
```

This gives us JVM option that we'll weave into the following shell command:

```shell
$ clojure -J-agentpath:/tmp/clj-async-profiler/libasyncProfiler-darwin-universal.so=start,event=cpu,file=/tmp/clj-async-profiler/results/02-startup-cpu-2023-10-11-20-04-33.txt,interval=10000,threads,collapsed -M -e '(System/exit 0)'
```

This command launches a Clojure process with the profiler agent attached from
the beginning, performs all the bootstrapping, and finally invokes `(System/exit
0)` as we instructed it to. The profile data will be saved into the file once
the process finishes. Once that happens, let's jump back to the REPL and run
this:

```clj
user=> (prof/generate-flamegraph "/tmp/clj-async-profiler/results/01-startup-cpu-2023-10-11-19-50-07.txt" {})
```

The filename is also given to us in the output of
`print-jvm-opt-for-startup-profiling` invocation, so we don't have to search for
it ourselves. Now, we can see the created flamegraph in the web UI or directly
in `/tmp/clj-async-profiler/results/`.

<center>
<figure class="figure">
<div class="downscale-iframe-66" style="height:500px">
<iframe src="/img/kb/cljap-startup.html" style="height:750px"></iframe>
</div>
<figcaption class="figure-caption text-center">
    Clojure startup flamegraph. <a href="/img/kb/cljap-startup.html"
target="_blank">Click</a> to open in a dedicated tab.
</figcaption>
</figure>
</center>

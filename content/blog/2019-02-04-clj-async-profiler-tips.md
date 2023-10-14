{:title "clj-async-profiler: tips, tricks, new features"
 :date-published "2019-02-04 12:00:00"
 :reddit-link "https://www.reddit.com/r/Clojure/comments/an08sm/cljasyncprofiler_030_tips_tricks_new_features/"}

Today I released [clj-async-profiler
0.3.0](https://github.com/clojure-goes-fast/clj-async-profiler/blob/master/CHANGELOG.md).
This version contains a few new features and possibilities that warrant a
dedicated blog post clarifying them. I will try to give you the taste of what
you are able to do with this profiler and what mistakes should be avoided.

If this is the first time you hear about clj-async-profiler check [this
introduction](/blog/profiling-tool-async-profiler/). It explains the motivation
behind this profiler and how to start using it.

### Stack demunging and unique color scheme

Version 0.3.0 boasts a new color scheme specifically tailored to Clojure
programs. It paints Java frames in green (like in the default color scheme) and
Clojure frames in blue.

Another visual improvement is that both Java and Clojure frames are now
demunged. What looked like `clojure/core$filter.invoke` now correctly displays
as `clojure.core/filter`. Furthermore, duplicate frames are removed (Clojure
function call usually involves two method invocations, `.invoke` and
`.invokeStatic`).

Here's a side-by-side comparison of the same program profiled with 0.2.0 and
0.3.0. The `burn-cpu` code is taken from the [introduction
post](/blog/profiling-tool-async-profiler/), profiled as `(prof/profile
(burn-cpu 10))`.

<center>
<figure class="figure">
<object type="image/svg+xml" data="/img/posts/cljap-020-burncpu.svg" width="49%"></object>
<object type="image/svg+xml" data="/img/posts/cljap-030-burncpu.svg" width="49%"></object>
<figcaption class="figure-caption text-center">On the left is
  <a href="/img/posts/cljap-020-burncpu.svg" target="_blank">0.2.0</a>, on the
  right is   <a href="/img/posts/cljap-030-burncpu.svg"
  target="_blank">0.3.0</a>. Click the links to open in full.
</figcaption>
</figure>
</center>

You'll see (probably not on this page but if you open the images in a separate
tab) that the new version displays fewer stackframes due to deduplication, has
nicer-looking method and function names, and more eye-friendly coloring.

### Allocation profiling

clj-async-profiler profiles CPU usage by default, but it can do other things.
You may call `list-event-types` function to know which events are supported on
your machine. On MacOS, you'll probably see something like this (Linux users
will get some extra native events):

```clj
user=> (prof/list-event-types)

Basic events:
  cpu
  alloc
  lock
  wall
  itimer
```

For example, to figure out which code does the most allocations in your program,
you can pass `:event :alloc` to any profiling function (`profile`,
`profile-for`, `start`). Let's see that in action. I downloaded this big
[twitter.json](https://github.com/miloyip/nativejson-benchmark/blob/master/data/twitter.json)
file and will try to parse it with Cheshire and understand where the allocations
happen.

```clj
user=> (require '[cheshire.core :as json])

user=> (prof/profile
        {:event :alloc}
        (dotimes [_ 500]
          (json/decode (slurp "twitter.json"))))
```

This is the profile I got:

<center>
<figure class="figure">
<object type="image/svg+xml" data="/img/posts/cljap-alloc-slurp.svg" width="100%"></object>
<figcaption class="figure-caption text-center">Raw Cheshire allocations profile.
<a href="/img/posts/cljap-alloc-slurp.svg" target="_blank">Click to open.</a>
</figcaption>
</figure>
</center>

This flame graph looks similar to a regular CPU graph, except the upper frame
is always the class of the objects that are allocated in that stack most often.
You will notice in the profile above that Cheshire is responsible only for 36%
of the allocation volume; meanwhile, `slurp` makes 63% of the allocations. Let's
modify the code so that it doesn't slurp in the loop and just profile the actual
parsing:

```clj
user=> (let [s (slurp "twitter.json")]
         (prof/profile
          {:event :alloc}
          (dotimes [_ 500]
            (json/decode s))))
```

<center>
<figure class="figure">
<object type="image/svg+xml" data="/img/posts/cljap-alloc-cached.svg" width="100%"></object>
<figcaption class="figure-caption text-center">Raw Cheshire allocations profile
without slurping.
<a href="/img/posts/cljap-alloc-cached.svg" target="_blank">Click to open.</a>
</figcaption>
</figure>
</center>

Now Cheshire is a sole allocator as expected, and its allocations consist of
creating internal [Jackson](https://github.com/FasterXML/jackson-core) objects
and modifying [transient maps](https://clojure.org/reference/transients).

You can learn how allocation profiling works in async-profiler
[here](https://github.com/jvm-profiling-tools/async-profiler#allocation-profiling).

### Stack transforming

A prominent new feature in 0.3.0 is the ability to transform and filter stacks
using regular Clojure code. This allows to post-process the profiling results
and make their interpretation easier for your exact usecase.

First, let's take a look at the stacks file. You can open any file
`profile-***.txt` in clj-async-profiler's `results/` directory and see that each
line has the following format:

    frame1;frame2;frame3;frame4 <number-of-samples>

Since 0.3.0, clj-async-profiler provides an API to transform this data before
the flamegraph is generated. You can pass `:transform <my-fn>` to `profile` or
`start`, where `<my-fn>` is a function which accepts a string that represents
the full stacktrace without the number of samples. Your function then can return
a modified string (to alter the stack on the flamegraph) or `nil` (to remove
this stack from the results).

There is also a separate function `generate-flamegraph` that accepts a
`profile-***.txt` file and an options map and produces a flamegraph. It is
convenient when you just want to experiment with post-processing and don't want
to re-run the profiling code each time.

Let's look back at our results for profiling allocations in Cheshire. Because
`cheshire.core/parse*` is recursive, it is hard to observe the actual
distribution of objects allocated by this function. See how there are multiple
`assoc!` calls in the flamegraph, all originating from `parse*` but separate
because they appeared on a different stack level. We can solve this by
collapsing consecutive `parse*` calls into just one. Another thing you might
want is to collapse all frames preceding `clojure.core/eval` since they are
non-informative.

```clj
(require '[clojure.string :as str])

(prof/generate-flamegraph
 "/tmp/clj-async-profiler/results/profile-2019-02-01-13-10-16.txt"
 {:transform (fn [s]
               (-> s
                   (str/replace #"(cheshire\.parse/parse\*;)+" "cheshire.core/parse*...;")
                   (str/replace #"^.+clojure\.core/eval;" "START;")))})
```

<center>
<figure class="figure">
<object type="image/svg+xml" data="/img/posts/cljap-alloc-transformed.svg" width="100%"></object>
<figcaption class="figure-caption text-center">Post-processed Cheshire allocations profile.
<a href="/img/posts/cljap-alloc-transformed.svg" target="_blank">Click to open.</a>
</figcaption>
</figure>
</center>

We used a little bit of regexp-fu to massage the stacks into what we wanted.
Now, it's much easier to understand that the transients-related code does 60% of
allocations, and Jackson objects make up the remaining 40%. The flamegraph
itself has become much lower because we collapsed uninteresting frames.

For another, more convoluted example, we'll launch an
[Aleph](https://github.com/ztellman/aleph) server:

```clj
(require '[aleph.http :as http]
         '[manifold.deferred :as md]
         '[clojure.string :as str]
         '[clj-async-profiler.core :as prof])

(defn handler [req]
  ;; Our handler will wait 10 milliseconds and return tangent of the URI.
  (let [num (Double/parseDouble (subs (:uri req) 1))]
    (-> (md/deferred)
        (md/timeout! 10 {:body (format "The tangent of %s is %s"
                                       num (Math/tan num))}))))

(def srv (http/start-server handler {:port 8080}))
```

A quick curl to sanity-check:

```shell
$ curl 'localhost:8080/12345'
The tangent of 12345.0 is -8.917885942518717
```

Then, we'll start [wrk](https://github.com/giltene/wrk2) to bombard our small
server at 10000 RPS:

```shell
$ wrk -c 500 -d 60 -R 10000 'http://localhost:8080/12345'
```

Finally, meanwhile wrk is working, we'll start the profiler in the same REPL
where out HTTP server is running:

```clj
user=> (prof/profile-for 10)
```

<center>
<figure class="figure">
<object type="image/svg+xml" data="/img/posts/cljap-aleph-full.svg" width="100%"></object>
<figcaption class="figure-caption text-center">Raw Aleph profile.
<a href="/img/posts/cljap-aleph-full.svg" target="_blank">Click to open.</a>
</figcaption>
</figure>
</center>

We got a flamegraph, but it's not very convenient to read it. Many samples that
were collected are spent in parking threads and waiting for I/O events. We can't
do much about them right now, but they make other stacks smaller. Another thing
that can be improved is that Netty has quite many frames on its stacks, and we
also don't care much about them. So, to address these points, we can profile
with the following transform function:

```clj
user=> (prof/profile-for
        10 {:transform (fn [s]
                         ;; This transform removes stacks that contain `Parker::` or
                         ;; `kevent`, and collapses consecutive Netty frames.
                         (when-not (re-find #"(Parker::|kevent)" s)
                           (str/replace s #"(io\.netty\..+?;)+" "io.netty...;")))})
```

<center>
<figure class="figure">
<object type="image/svg+xml" data="/img/posts/cljap-aleph-processed.svg" width="100%"></object>
<figcaption class="figure-caption text-center">Post-processed Aleph profile.
<a href="/img/posts/cljap-aleph-processed.svg" target="_blank">Click to open.</a>
</figcaption>
</figure>
</center>

Again, the flame graph is now much shorter and more balanced. It probably still
won't tell you much if you aren't familiar with Aleph implementation, but it's a
useful aid in learning Aleph internals.

### Per-thread profiling

By default, clj-async-profiler lumps stacks of all threads together into a
single flamegraph. In some cases, you might want to see a separate profile for
every thread. For that, add `:threads true` to the options map. If you want to
look at the profile of just one thread, use the following trick:

```clj
;; Suppose, something you want to profile is already running in one of the
;; background threads.
user=> (prof/profile-for
        10 {:threads true
            :transform #(when (> (.indexOf ^String % "my-thread-name") -1) %)})
```

If you accumulate a massive profile with per-thread profiling enabled, the
resulting SVG file might be too hard for your browser to render. If you notice
that the flame graph takes many seconds to render in the browser, try passing
`:min-width 1` (or a larger number) in the options. This will drop the stacks
that have too few samples to occupy even 1 pixel in the SVG.

### Make sure you have enough samples

The actual profiling library under the hood of clj-async-profiler,
[async-profiler](https://github.com/jvm-profiling-tools/async-profiler), is a
sampling profiler. It means that it is not 100% accurate; instead it derives the
profile picture statistically. Whenever it comes to statistics, you need enough
data samples to be confident about the results. So, make sure that when you
profile anything with clj-async-profiler, you get at least 1000 samples. The
empirically optimal number of samples to have is 5000-10000. This roughly
corresponds to 5-10 seconds of running time; so when you want to measure
something that finishes faster, then repeat it in a loop that runs for a few
seconds at least.

Note that these exact numbers are given only for CPU profiling. When profiling
other events (e.g., alloc, lock), you might not be able to get that many
samples.

You can learn how many samples the profiler collected by hovering over the
bottom stackframe in the flamegraph.

### Conclusions

Efficient and thoughtful profiling is not the most obvious and straightforward
process. And we didn't even mention the correct interpretation of the results!
Learning to do all of this properly takes some practice. But I hope that the
right tools can make it easier for you and, most importantly, fun. Please, try
out the new clj-async-profiler in your projects and let me know how it went at
[@ClojureGoesFast](https://twitter.com/ClojureGoesFast). Thanks for reading!

{:title "Allocation profiling"
 :page-index 4
 :layout :kb-page}

clj-async-profiler is capable of profiling not only time spent on the CPU but
other events as well. Here we'll learn how to use it for allocation profiling.
This means finding out which parts of your program allocate the most objects
(measured in total size).

The way allocation profiling is implemented in the underlying library —
[async-profiler](https://github.com/jvm-profiling-tools/async-profiler#allocation-profiling)
— is very efficient and non-intrusive. It might not be as accurate as
heavyweight memory profiling mechanisms available in other profilers, but in
practice, it is good enough at pointing at the biggest allocation hogs in your
code with almost zero costs for the runtime. Similar to regular CPU profiling,
allocation profiling with clj-async-profiler is safe even on highly loaded
production servers.

Let's see allocation profiling in action. First, download this file
[twitter.json](https://github.com/miloyip/nativejson-benchmark/blob/master/data/twitter.json),
and we'll try to parse it with Cheshire and understand where the allocations
happen.

```clojure-repl
user=> (require '[cheshire.core :as json]
                '[clj-async-profiler.core :as prof])

user=> (prof/profile
        {:event :alloc}
        (dotimes [_ 500]
          (json/decode (slurp "twitter.json"))))
```

<center>
<figure class="figure">
<div class="downscale-iframe-66" style="height:560px">
<iframe src="/img/kb/cljap-alloc1.html?hide-sidebar=true" style="height:840px"></iframe>
</div>
<figcaption class="figure-caption text-center">
    Initial allocation flamegraph. <a href="/img/kb/cljap-alloc1.html?hide-sidebar=true" target="_blank">Click to open.</a>
</figcaption>
</figure>
</center>

Visually, an allocation flamegraph is almost identical to a regular CPU
flamegraph. What is different is that at the top of every stack, there is a
special frame (or multiple frames) that denotes class of objects that were
allocated there[[1]](#fn1)<a name="bfn1"></a>. You can see in the flamegraph
above that Cheshire is responsible for 52% of the allocation volume; meanwhile,
`slurp` makes 48% of the allocations. Let's hoist `slurp` outside the loop and
profile just the parsing.

```clj
(let [s (slurp "twitter.json")]
  (prof/profile
   {:event :alloc}
   (dotimes [_ 1000]
     (json/decode s))))
```

You'll also notice that the function `parse*` is recursive, which makes the
result less clear. Fortunately, we've already learned how to simplify
flamegraphs that contain recursive functions with [live
transforms](/kb/profiling/clj-async-profiler/exploring-flamegraphs/#live-transforms).
Let's add one of those to this flamegraph:

<center>
<figure class="figure">
<div class="downscale-iframe-66" style="height:420px">
<iframe src="/img/kb/cljap-alloc2.html" style="height:630px"></iframe>
</div>
<figcaption class="figure-caption text-center">
    Cheshire allocations flamegraph. <a href="/img/kb/cljap-alloc2.html" target="_blank">Click to open.</a>
</figcaption>
</figure>
</center>

Now Cheshire is a sole allocator, as expected, and its allocations consist of
creating internal Jackson objects and modifying transient maps.

Note that just like with CPU profiling, allocation profiling cannot measure the
absolute numbers of your program's allocation pressure and whether it's too
much. It only tells you which parts of the code allocate more than the others in
comparison. To get the exact allocation rate, you should use separate tools,
e.g.,
[jvm-alloc-rate-meter](https://github.com/clojure-goes-fast/jvm-alloc-rate-meter).

#### Footnotes

1. <a name="fn1"></a><span> The suffix in brackets is an annotation that tells whether
  the allocation happened in Java code (`[i]` for inlined) or native code (`[k]`
  for kernel). This may not be important to you initially, so feel free to
  ignore it.</span>[↑](#bfn1)

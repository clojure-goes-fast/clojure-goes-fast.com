{:title "clj-async-profiler 1.0.0: fast HTML rendering and dynamic transforms"
 :date-published "2022-07-22 10:30:00"
 :og-image "/img/posts/cljap100-all-transforms.png"
 :reddit-link "https://www.reddit.com/r/Clojure/comments/w54fao/cljasyncprofiler_100_fast_html_rendering_and/?"}

The first stable release of
[clj-async-profiler](https://github.com/clojure-goes-fast/clj-async-profiler)
marks a principal milestone for this project. Almost five years have passed
since its creation, and during these years, I used clj-async-profiler almost
daily to make the programs I write faster and better. New challenges presented
new ideas for improving the profiler, which in turn materialized in such
features as [diffgraphs](blog/clj-async-profiler-040/), [stack
transforms](blog/clj-async-profiler-tips#stack-transforming), demunging, etc.
Today, I released clj-async-profiler 1.0.0, which comes with more goodies that
will hopefully improve your experience of using the profiler. In this post, I
want to highlight these changes and teach you how to make use of them in your
workflow.

Here's a brief list of changes before we dive deeper into each of them:

- **HTML flamegraphs** are now generated instead of SVG files. It is a huge
  change that creates the basis for other improvements.
- Flamegraph rendering got **significantly faster**. This includes both the
  initial rendering and the subsequent navigation, which is now much more
  responsive. On heavy flamegraphs, the difference is gargantuan — where the SVG
  version would take 20-30 seconds to fully present itself in Chrome, the HTML
  canvas-based implementation displays instantly.
- Flamegraphs have been greatly **decreased in size** too. In the example we'll
  study below, the SVG version weighs 10 megabytes, whereas the HTML one is just
  300 kilobytes — a 30x reduction. This is impactful if you profile over the
  network (e.g., on production instances) since you will download the file
  faster and see the rendered flamegraph sooner.
- Many flamegraph options from now on can be changed **dynamically in the
  browser**. Things like minimal frame width, normal/inverse graph, horizontal
  frame order, and, most crucially, **stack transforms** no longer require
  generating a new flamegraph from stacks. All of those can be changed on the
  fly. Also, flamegraphs are automatically stretched to fill the browser window,
  so it is no longer necessary to predefine the width and height of the graph.

### Example

Let's work through a non-trivial example to see the changes in action and learn
how to use the new features. I've generated a new HTML flamegraph in a sample
client-server application — <a href="/img/posts/cljap100-html-fg1.html"
target="_blank">click here</a> to open it. Here is what you will see:

<center>
<figure class="figure">
<img class="img-responsive" src="/img/posts/cljap100-fg1.png" style="max-height: 300px;">
<figcaption class="figure-caption text-center">
    Initial flamegraph. <a href="/img/posts/cljap100-html-fg1.html" target="_blank">Click to open.</a>
</figcaption>
</figure>
</center>

The first thing you'll notice is how fast it opens. That comes from the reduced
file size and much snappier rendering. Click around the graph, see how
responsive it has become (if you want to feel the improvement, check the <a
href="/img/posts/cljap100-old.svg" target="_blank">SVG version</a> of the same
flamegraph — and that one doesn't even contain all frames!).

The second big difference is the addition of that barebones panel on the right.
It is actually collapsible, so you don't have to stare at it all the time. This
panel (the looks of which might get better later) is the vessel for the new
exciting ways to interact with the flamegraph. First from the top is the
Highlight section which is where the existing Search functionality has been
moved. You can write the query as a string or a `/regex/`. Next goes the field
for setting the minimal frame width. It is used as an optimization for very
large flamegraphs since hairbreadth frames (meaning they have few samples) are
not very helpful, but skipping them can improve rendering speed even further.

Further down is the Reversed flag. Gone are the days when you had to regenerate the
whole flamegraph to see the so-called "icicles" graph! Right below is a toggle
between sorting the frames by their name or width. You can experiment with these
options by changing them and clicking Apply.

<center>
<figure class="figure">
<img class="img-responsive" src="/img/posts/cljap100-fg2.png" style="max-height: 300px;">
<figcaption class="figure-caption text-center">
    Reversed flamegraph, sorted by width and with "netty" frames highlighted.
</figcaption>
</figure>
</center>

The final section is the meat of the new flamegraph functionality — it gives the
ability to transform the stacks in the flamegraph. You can add any number of
transforms that could be one of the three possible types: Filter, Remove, or
Replace, with the latter being the most useful. To understand transforms, it is
important to remember how stacks in clj-async-profiler are represented:

    frame1;frame2;frame3;frame4

A regex-based Replace transform is allowed not just to rename individual frames
but also elide frames or even introduce new frames (if that's ever needed). But
regardless of what replacement you are doing, the total number of samples
remains the same. You don't lose any data; you just redistribute it to other
stacks. Let's start with an easier usecase. Say, in our flamegraph, we don't
really care about what Cheshire is doing under the hood since we won't be able
to optimize that anytime soon. So, we can add a Replace transform that replaces
regex `/cheshire.core.+/` (slashes signify that it is a regular expression and
not just a string) to `cheshire/...`. It means: in any stack where there is a
frame containing `cheshire.core` replace that and everything afterward with
`cheshire/...`.

<center>
<figure class="figure">
<img class="img-responsive" src="/img/posts/cljap100-replace-cheshire.png" style="max-height: 300px;">
<figcaption class="figure-caption text-center">
    Complex cheshire.core part turned into a single flat frame.
</figcaption>
</figure>
</center>

If you try doing this exercise on your own, you will notice that the total
number of frames for that Cheshire subtree has remained the same after the
transform.

Here is another usecase, a more complicated one this time. On the right side,
you should see a tall subgraph that contains invocations of
`example.client/rand-json-object` and `example.client/rand-json-array`. These
two functions are recursive, even mutually recursive, and this makes
interpreting the profiling output for them very cumbersome. With a transform, we
can collapse all recursive calls into just one frame so that the real work
performed within those functions sticks together and becomes more prominent. It
is achievable by the following Replace:

    /example.client/rand-json-\w+;.+;(example.client/rand-json-\w+;)/
    $1

In the regex to replace, we say that we are looking for a stackframe that looks
like `example.client/rand-json-` and then some word. Any number of other frames
can follow this frame, but at some point, there should appear another frame with
`example.client/rand-json-`. We capture that second frame into a match group
(with parentheses) and replace the whole substack that matched with just that
last frame. As a result, that complicated recursive tree flattens down and it is
now much easier to analyze what is the most expensive within those functions.

<center>
<figure class="figure">
<img class="img-responsive" src="/img/posts/cljap100-replace-recursion.png" style="max-height: 300px;">
<figcaption class="figure-caption text-center">
    The transform has removed nested recursive invocations of <code>rand-json-object</code> and <code>-array</code>.
</figcaption>
</figure>
</center>

The other two transforms, Filter and Remove, are mirror versions of each other.
Filter only retains those stacks that match the given substring of a regex, and
Remove does the opposite — removes the stacks that match. For example, you might
want to remove that leftmost part of the graph that starts with the frame
`[unknown_Java]`. They appear when the profiler cannot accurately map the perf
events to what's going on within the JVM. For that, you can add a Remove
transform with the string `unknown_Java`.

By adding some more transforms (collapsing of consecutive `io.netty` and
`manifold` frames), our flamegraph could be looking like this:

<center>
<figure class="figure">
<img class="img-responsive" src="/img/posts/cljap100-all-transforms.png" style="max-height: 300px;">
<figcaption class="figure-caption text-center">
    Original flamegraph after applying all transforms.
</figcaption>
</figure>
</center>

It might not be immediately obvious how these transforms make it easier to read
the profiler results. Writing such transforms is a learned skill, and it may
take a while to start appreciating what they bring you. Good command of regular
expressions also helps.

Diffgraphs are also rendered in HTML now, so everything above applies to
diffgraphs too. In diffgraphs, Filter and Remove transforms can be more
beneficial than in regular flamegraphs. You can also dynamically switch between
normalized and non-normalized diffing within the
flamegraph[<sup>1</sup>](#fn1)<a name="bfn1"></a>).

### Other changes

The main breaking change of the new release is that SVG generation is no longer
supported. I assume it won't matter much to most users unless you specifically
relied on the profiler's output being an SVG file[<sup>2</sup>](#fn2)<a
name="bfn2"></a>). In that case, you are stuck with the previous version of
clj-async-profiler; maybe, in the future, SVG generation will make it back as an
option if there is enough user interest.

Since transforms are now dynamic, the old `:transform` option that could be
passed to the profiler's façade functions is no longer essential. It is still
available and supported, though, since it accepts arbitrary code and can
potentially do much more than simple filter/remove/replace, and somebody might
still need that. Another option was added called `:predefined-transforms` which
accepts a sequence of maps that look like this:

```clojure
;; Notice that you should use Clojure's regular expression syntax here. It will
;; be converged into JavaScript's regex syntax automatically.

:predefined-transforms [{:type :replace
                         :what #"(cheshire|instaparse).core.+"
                         :replacement "$1/..."}
                        {:type :replace
                         :what #"example.client/rand-json-\w+;.+;(example.client/rand-json-\w+;)"
                         :replacement "$1"}
                        {:type :remove
                         :what #"unknown_Java|thread_start"}
                        ...]
```

This option allows to bake common transforms directly into the HTML. Once baked
in, you are still able to modify and disable those transforms. This is
convenient in applications that you profile often and repeatedly apply the same
transforms. Predefining such transforms can save you a bit of time. As an
example, <a href="/img/posts/cljap100-html-fg-with-transforms.html"
target="_blank">here is a flamegraph</a> with all the transforms mentioned above
already defined.

The HTTP server that powers `serve-files` UI has been rewritten. It is still an
embedded thing; clj-async-profiler continues to be a proud member of the Zero
Dependencies movement. The fresh rewrite brings some basic features like cache
headers for the served files. Otherwise, the UI has remained the same for now,
but I have some more ideas for it.

As always, you can check for the exhaustive list of changes in the
[CHANGELOG](https://github.com/clojure-goes-fast/clj-async-profiler/blob/master/CHANGELOG.md).

And that's pretty much it. Personally, I'm very excited about being able to
finally deliver this release to you. Hope you find it as useful as I do, and
feel free to leave any comments, requests, and bug reports on
[Github](https://github.com/clojure-goes-fast/clj-async-profiler/issues) or
here.

#### Footnotes

1. <a name="fn1"></a> A normalized diffgraph means that the samples are scaled
  in one of the profiles in such a way that the total number of samples is equal
  between both profiles.[↑](#bfn1)
2. <a name="fn2"></a> If you enjoy embedding interactive SVG flamegraphs into
  HTML pages, you can do just that with HTML graphs using the `<iframe>`
  tag.[↑](#bfn2)

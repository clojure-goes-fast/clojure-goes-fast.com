{:title "clj-async-profiler 1.1.0: right-click menu and startup profiling"
 :date-published "2023-10-17 10:30:00"
 :og-image "/img/posts/cljap110-menu.png"
 :reddit-link "https://reddit.com/r/Clojure/comments/179vco3/cljasyncprofiler_110_rightclick_menu_and_startup/"}

Last year, I made a major release of
[clj-async-profiler](https://github.com/clojure-goes-fast/clj-async-profiler),
introducing [dynamic
transforms](/kb/profiling/clj-async-profiler/exploring-flamegraphs/#live-transforms)
within the flamegraphs. This granted users the ability to arbitrarily modify the
profiling data on the fly without regenerating a flamegraph. Such an instrument
lets you get a better picture and uncover hidden performance gotchas. For
example, a solid usecase for this feature would be exploring the flamegraph of a
recursive function, which becomes much more palatable when folded. I personally
use dynamic transforms all the time, and they aid me tremendously in deciphering
the profiling results. However, I acknowledge that writing regular expressions
manually may be intimidating if you are not very familiar with them, and plain
tedious if you are.

The release **1.1.0** makes a step towards more user-friendly transforms by
adding a custom context menu to flamegraphs. You can now right-click any frame
in the flamegraph to open a dedicated menu. Here's what it looks like:

<center>
<figure class="figure">
<img class="img-responsive" src="/img/posts/cljap110-menu.png" style="max-height: 300px;">
<figcaption class="figure-caption text-center">
    New right-click menu.
</figcaption>
</figure>
</center>

The transforms accessible through the menu are nothing different from what you
can write manually. In fact, clicking any of these transforms simply adds a
prepared transform to the sidebar on the right. From there, you are free to edit
it like any regular transform: disable via a checkbox, modify the expressions,
reorder, etc. The main benefit is that the quick actions in the context menu
cover many of the cases where you want to alter a flamegraph and are much faster
to use than writing a transform by hand.

Let's go over each item in detail. I'll use a sample flamegraph below to
demonstrate the new features so you can follow along.

<center>
<figure class="figure">
<div class="downscale-iframe-66" style="height:400px">
<iframe src="/img/posts/cljap110-example.html" style="height:600px"></iframe>
</div>
<figcaption class="figure-caption text-center">
    Example 1.1.0 flamegraph. <a href="/img/posts/cljap110-example.html"
target="_blank">Click</a> to open.
</figcaption>
<</figure>
</center>

- **Highlight**. Try right-clicking any of the `bottom-up-tree` frames and
  choose "Highlight". This will highlight frames with this name everywhere on
  the flamegraph and show the total matched percentage in the lower right
  corner. This is the same as writing a Highlight query manually in the sidebar,
  but quicker. As always, you can click **Clear** button to clear the highlight.
- **Copy as text** and **Copy as regex** have migrated here from the sidebar.
  This will copy the name of the right-clicked frame to the clipboard, either as
  plaintext or as a regular expression. The primary usecase for these is to then
  use the copied text for constructing transforms.
- **Filter containing stacks** adds a simple Filter transform, which retains
  only those stacks that have the clicked frame anywhere in them. This can be
  helpful if the code you are interested in has multiple entrypoints, and you
  don't want to deal with anything besides it. Let's right-click any
  `bottom-up-tree` frame again and select this menu item. See that only the
  stacks containing this frame are now displayed on the flamegraph. You can then
  uncheck the checkbox next to the added transform and press "Apply"; the
  flamegraph will revert to the previous state.
- **Remove containing stacks** is the opposite of the previous transform. It
  allows you to hide whole stacks by clicking on a frame that is common to them.
  Let's right-click the red `thread_start` frame, which is the entrypoint to
  some GC work, and select this menu item. Observe how this part of the
  flamegraph disappeared.
- **Hide frames above** is convenient when you want to keep some part of the
  flamegraph "by weight" (so that the samples in it correctly offset the other
  parts) but aren't interested in the particular work that is done inside. Say,
  you can click `thread_start` frame and select "Hide frames above". All the
  "noise" above that frame will disappear.
- **Collapse recursive** is one of the most valuable transforms. Right now, you
  can see that `bottom-up-tree` consists of recursive calling of itself and some
  actual work, but it's hard to tell how parts of that work relate to each
  other. Let's click any of the `bottom-up-tree` frames and select "Collapse
  recursive". The whole recursive stack will be reduced to a single frame, and
  the other functions called by the recursive frames will be merged together to
  paint a much clearer picture.
- **Collapse recursive (with gaps)** can be used for those recursive functions
  that don't call themselves directly, but rather call another function(s) that
  finally invoke the initial function. Selecting this menu for such a function
  would still fold the recursive part into a single invocation chain.
- **Hide frames below**. Once you've collapsed the recursive `bottom-up-tree`,
  try highlighting it through the menu. You will see that it is still split into
  two parts on the graph. That's because the lazy sequence processing machinery
  calls this function through two disparate code paths. But we can bring those
  separated samples back together by right-clicking any of the two
  `bottom-up-tree` frames and selecting "Hide frames below". Observe how this
  frame "dropped" to the bottom of the flamegraph, and the two subgraphs merged
  into a single one. If you then "Filter" the `bottom-up-tree`, you will
  ultimately get a flamegraph dedicated to just that one function.

That's all the quick transforms available for now. Some more may be added in the
future, but these should cover a lot of usecases already. Quick transforms can
also be a stepping stone to more complex behavior Start by adding one from the
menu and then experiment with expression in the sidebar to reach the most
informative view.

This release also adds a function `print-jvm-opt-for-startup-profiling` that
simplifies enabling the profiler from the very launch of the JVM. I've described
this new functionality in the [KB
article](/kb/profiling/clj-async-profiler/startup/).

I hope this new version of clj-async-profiler sparks joy and helps you
understand your flamegraphs better. I'll be delighted to hear your suggestions
about which other quick transforms are worth adding. Now go ignite some flames!

{:title "clj-async-profiler 1.3.0: new sidebar and snappier rendering"
 :date-published "2024-09-10 14:00:00"
 :og-image "/img/posts/cljap130-screenshot.png"
 :reddit-link "https://old.reddit.com/r/Clojure/comments/1fdgnlo/cljasyncprofiler_130_redesigned_sidebar_and/?"}

I'm excited to announce a fresh release of
[clj-async-profiler](https://github.com/clojure-goes-fast/clj-async-profiler) —
**1.3.0**. This new version brings a lot of visual changes, so it deserves a
separate post describing all the improvements and design considerations. I'm a
visual person, and I firmly believe that even the smallest experiences (either
delightful or annoying) may greatly impact the overall productivity. That's why,
in order to make meaningful improvements to the current UX, I asked for the help
of a person with actual design skills, talent, and taste (and who is very dear
to me). Settling on the new design wasn't a clear-cut task and involved a lot of
back-and-forth, but I'm pleased with the result, and I hope you will be, too.

### Sidebar looks and user experience

Ever since the sidebar was introduced in version **1.0.0**, its visual
appearance has remained the same:

<center>
<figure class="figure">
<div class="downscale-iframe-66" style="height:400px;">
<iframe src="/img/posts/cljap130-old120.html" style="height:600px; width:150%;"></iframe>
</div>
<figcaption class="figure-caption text-center">
    Old flamegraph generated with 1.2.0. <a href="/img/posts/cljap130-old120.html" target="_blank">Click</a> to open.
</figcaption>
</figure>
</center>

The sidebar serves as a container for different configuration parameters and
actions that change the look of the flamegraph viewer, namely:

- **Highlight field** is used to search for frames in the flamegraph. It is
  accompanied by two buttons — Highlight and Clear.
- **Minimal frame size** is a hack to improve the rendering performance of big
  flamegraphs with many samples. I hate this field because it's never obvious
  what value this should be set to for optimal experience.
- **Reverse** — an option to build "inverse" flamegraphs.
- **Sort by name or width** toggle.
- The remaining space is dedicated to managing **dynamic transforms** —
  modifications on the flamegraph data model that help you discover the
  important parts of the data and clean up noise. The transform can be added by
  selecting the transform type from the dropdown and clicking Add. A transform
  block appears, containing input fields and extra buttons for managing the
  transform.

The sidebar serves its practical purpose but is otherwise ugly. The rendered
flamegraph is a self-contained HTML file, so it doesn't use any CSS frameworks;
and I spent almost no effort to make it look nice. It uses browser-native form
elements with poor alignment and padding. There is a narrow and hard-to-click
button that collapses the sidebar — something you are tempted to click instantly
to prevent your eyes from bleeding.

I tend to use the dynamic transforms a lot, so the sidebar stays open. However,
after version **1.1.0** shipped with the common transforms accessible by
right-clicking, the usefulness of the sidebar went down. It is still
occasionally convenient to see which transforms are currently applied and
disable or delete them, but most transforms can now be added with the sidebar
closed.

Bearing all this previous experience in mind, I wanted to make the following
changes to the design:

- Make the Highlight and Clear buttons a single toggle.
- Provide better UX for the minimal frame size input.
- Unify active elements' positioning and style.
- Clear up rarely used actions in dynamic transforms and make the blocks
  visually distinctive.
- Make it easier to expand/collapse the sidebar.
- And make the whole thing prettier, damn it.

Fast-forward, here is the final state of **1.3.0**:

<center>
<figure class="figure">
<div class="downscale-iframe-66" style="height:400px;">
<iframe src="/img/posts/cljap130-new130.html?sidebar=expanded" style="height:600px; width:150%;"></iframe>
</div>
<figcaption class="figure-caption text-center">
    New flamegraph generated with 1.3.0. <a href="/img/posts/cljap130-new130.html?sidebar=expanded" target="_blank">Click</a> to open.
</figcaption>
</figure>
</center>

The Highlight and Clear buttons are gone, replaced with a single magnifying
glass toggleable button. Reversed checkbox has also become a toggle. Min width
input is gone for the reason I'll explain in the next section. The sorting
widget looks cleaner. The dropdown and the Add button have been replaced with
three separate chips. Every second transform block is striped spreadsheet-style.

Finally, the uppermost button collapses the sidebar into a floating panel. The
collapsed sidebar is still functional. Clicking the search button either cancels
the current highlight or brings up a modal window to search for something else.
The search text is preserved between the collapsed and expanded sidebar. The
Reverse button is also available. And, of course, you can expand back to the
full sidebar.

Changes were made to the bottom panel that displays the currently hovered
stackframe info and the percentage of matched frames. This panel is now pinned
to the bottom of the screen, so it is still visible if you scroll up (or down,
in the Reversed view). I also added a little spinner to the left of it when the
renderer performs a slow action. For example, you can observe it by clicking the
Reverse button.

I've slightly revised the color palette. The Clojure Blue™ and Clojure Green™
still represent Clojure and Java frames, respectively. I've changed the
"unknown" color from red to orange so that it doesn't stand out as much and is
less confusable with the Highlighted Fuchsia in thin frames. The bottommost
"Total" is now grey.

Overall, I'm quite satisfied with the new looks. Nothing is over the top here,
just some clean and consistent design, moderate use of shadows and rounded
corners. All of it is achieved with a small amount of hand-crafted CSS and
inlined SVGs.

### Rendering performance

Before 1.0.0, clj-async-profiler rendered the flamegraphs as SVG files using
Brendan Gregg's
[flamegraph.pl](https://github.com/brendangregg/FlameGraph/blob/master/flamegraph.pl)
script. Unfortunately, its rendering performance with real-world flamegraphs was
lackluster. Switching to HTML canvas-based flamegraphs meant that you got much
smaller file sizes and more quicker UI. However, it is still possible to slow
down the browser with a flamegraph that contains 100k+ frames.

In this release, I made some further performance improvements to flamegraph
rendering:

- The binary search algorithm introduced in this release ensures that the frames
  invisible in the current drill-down don't slow down the render.
- I revised my approach to rendering very narrow frames. Now, they are sampled,
  which avoids rendering most of them but makes their presence still visible.
  More importantly, the omitted thin frames no longer change the calculated
  percentage of "matched" frames when using the Search feature. This made the
  Minimal Frame Width option obsolete.
- Expanding and collapsing the sidebar is much faster because it no longer
  triggers full data recomputation.

The improvements may not very notable, but the UI feels more responsive when
working with huge flamegraphs.

### Work in progress

While this is a big release, some changes will still ship in the following minor
versions. Stay tuned for updates, and please let me know about the inevitable
bugs and hiccups.

_I thank [Clojurists Together](https://www.clojuriststogether.org) for
sponsoring my open-source work in Q3 2024 and making this release possible._

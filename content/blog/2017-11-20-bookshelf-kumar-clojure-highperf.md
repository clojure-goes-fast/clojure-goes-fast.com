{:title "Bookshelf: \"Clojure High Performance Programming\""
 :date-published "2017-11-20"}

*Full name: Shantanu Kumar, "Clojure High Performance Programming", 2013.*

- [Goodreads](https://www.goodreads.com/book/show/18961260-clojure-high-performance-programming)
- [Amazon](https://www.amazon.com/gp/product/1782165606/)
- Personal rating: **3 of 5**

"Clojure High Performance Programming" claims to be a guide to everything
related to writing high-performance Clojure code. Published in 2013, it is
expected to be a little outdated, considering the fast pace at which Clojure
ecosystem evolves. However, there are not many books dedicated exclusively to
performance in Clojure, so it still makes sense to review this one.

## What is good

The book indeed covers plenty of topics. It starts with performance vocabulary,
general performance metrics and useful latencies — something that would be
useful for beginners in the field of performance optimizations. Then it moves
onto Clojure abstractions, data structures and their complexity guarantees. A
few obscure data structures are also mentioned (like `PersistentTreeMap`).

Next chapter is about Clojure-Java interop, and how it can be used to squeeze
additional performance from your program. It is followed by a section about host
performance (both bare metal and JVM), things like memory layout, CPU caches,
JIT. An entire chapter is dedicated to Clojure concurrency tools like refs and
agents, and also a few words are written about Java's own concurrency
primitives.

Chapter six focuses on ways of measuring and optimizing performance bottom-up,
by profiling various parts of the program. In contrast, the next chapter is
about top-down performance optimizations at the design level — it covers things
like pooling, batching, and caching.

## What is bad

The book's worst sin is that it has a very incoherent structure. In a pursuit of
writing a little bit about everything the author sacrificed the cohesion between
the chapters, so it doesn't feel like a continuous resource, rather a
compilation of topics.

Another big problem is that this book is not so much about Clojure. This makes
it an OK material for beginners, but less useful for those who seek knowledge on
how to optimize Clojure programs specifically. Clojure parts are hidden between
paragraphs of generic data, and those parts honestly feel forced. The connection
between the described generic problem and the specific Clojure solution isn't
often vivid.

Not author's fault, of course, but the book is already four years old. Such
important topics like **core.async** and **transducers** are missing.

## Verdict

Overall, the author managed to fit a lot of different information into a rather
thin book. It may be recommended to novices in the world of performance
optimizations for its breadth. For everyone else, it would be better to search
for a more systematic material on the Web.

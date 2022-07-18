{:title "Introspection tool: object memory meter"
 :date-published "2018-03-05 12:40:00"
 :reddit-link "https://www.reddit.com/r/Clojure/comments/8253ld/cljmemorymeter_measure_the_object_size_in_memory/"}

Long gone are the days when computer programmers counted every byte used by
their programs. Today, we enjoy multi-gigabyte or even terabyte RAM machines and
are much less frugal about the memory footprint of our data structures and
algorithms. The way we squander all this memory would probably give a poor chap
from the 1980s running the original 64K IBM PC a heart attack. But we are fine
now, the memory is cheap, and the added value we get is worth paying for.

However, sometimes we end up in a situation where each couple of bytes counts.
Perhaps, you are dealing with so many objects that the overhead of a single one
becomes relevant. E.g., this applies to NLP where you often deal with large
dictionaries with millions of records. Each extra byte in a record turns into an
additional megabyte of consumed heap. Another example is a web server which
keeps all user sessions in memory. The less space a session occupies, the more
of those sessions you can fit onto the server simultaneously.

Another consideration for smaller memory footprint is because it causes fewer
page faults and is more cache-friendly. You are unlikely to feel this effect
most of the time, but in a situation where you squeeze nanoseconds out of your
tight loop, this might become a bottleneck.

By default, JVM doesn't provide the ability to measure memory occupancy of
arbitrary objects. There is no out-of-the-box `sizeof` in Java. Luckily for us,
we can obtain this functionality with a third-party library/agent.

### clj-memory-meter

[clj-memory-meter](https://github.com/clojure-goes-fast/clj-memory-meter) is a
Clojure library for interactive exploration of object memory usage. It
wraps [JAMM](https://github.com/jbellis/jamm), on top of which it provides the
ability to load the JVM agent at runtime (so you don't need to start the program
with `-javaagent` parameter) and exposes a convenient API facade.
clj-memory-meter doesn't depend on the build tool (works with Lein, Boot, and
clj).

To get started, add `[com.clojure-goes-fast/clj-memory-meter "0.1.0"]` to your
list of dependencies. You can also do this dynamically at runtime if you
use [Boot](http://boot-clj.com/)
or
[clj-refactor](https://github.com/clojure-emacs/clj-refactor.el/blob/master/examples/add-project-dependency.gif).
Then, you can do this:

```clojure-repl
user=> (require '[clj-memory-meter.core :as mm])
nil

user=> (mm/measure "hello world")
"64 B"
```

`measure` is the only function you would use. It walks the object and its
components, calculates the total memory occupancy, and returns a human-readable
result. You can call it on any Java or Clojure object:

```clojure-repl
;; This is how much memory an empty vector occupies.
user=> (mm/measure [])
"240 B"

;; Measure the size of a vector of strings. Notice we use String constructor to
;; ensure objects are unique.
user=> (mm/measure (vec (repeatedly 100 #(String. "hello"))))
"3.1 KB"

;; Now, turn strings into keywords. Keywords are backed by interned strings, so
;; the size should go down - all those :hello's are the same object.
user=> (mm/measure (vec (repeatedly 100 #(keyword (String. "hello")))))
"872 B"

;; List vs vector vs array
user=> (mm/measure (list (doall (repeatedly 1000 (constantly "hello")))))
"62.6 KB"

user=> (mm/measure (vec (repeatedly 1000 (constantly "hello"))))
"5.4 KB"

user=> (mm/measure (object-array (repeatedly 1000 (constantly "hello"))))
"4.0 KB"
```

You can provide `:shallow true` as a parameter to do only a shallow analysis of
the object's memory usage. It counts the object header plus the space for object
fields, without following the references.

```clojure-repl
;; includes the underlying char[] array
user=> (mm/measure "Clojure goes fast!")
"80 B"

;; doesn't include the underlying char[] array
user=> (mm/measure "Clojure goes fast!" :shallow true)
"24 B"
```

You can pass `:debug true` to `measure` to print the object layout tree with
sizes for each part. Or you can pass `:debug <number>` to limit the nesting
level being printed:

```clojure-repl
user=> (mm/measure (vec (repeat 50 "hello")) :debug true)

root [clojure.lang.PersistentVector] 536 bytes (40 bytes)
root [clojure.lang.PersistentVector$Node] 352 bytes (24 bytes)
  |  |
  |  +--edit [java.util.concurrent.atomic.AtomicReference] 16 bytes (16 bytes)
  |  |
  |  +--array [java.lang.Object[]] 312 bytes (144 bytes)
  |    |
  |    +--0 [clojure.lang.PersistentVector$Node] 168 bytes (24 bytes)
  |      |
  |      +--array [java.lang.Object[]] 144 bytes (144 bytes)
  |
  +--tail [java.lang.Object[]] 144 bytes (88 bytes)
    |
    +--0 [java.lang.String] 56 bytes (24 bytes)
      |
      +--value [char[]] 32 bytes (32 bytes)

"536 B"
```

`:meter` parameter allows you to pass a `MemoryMeter` object configured to your
liking. Check
the
[source code](https://github.com/jbellis/jamm/blob/master/src/org/github/jamm/MemoryMeter.java) to
see the available options. Finally, you can provide `:bytes true` to return a
number in bytes instead of a formatted string.

### Conclusion

Once again, Clojure shows that it's an incredibly practical and empowering
language. Not only can you leverage the existing tools for the JVM platform, but
also do it more conveniently. In Java, you'd have to preload the agent at
startup and put measuring lines in your test file. In Clojure, you can load it
on-demand and execute at any time on the objects you already have in the REPL,
without the pains of setting up the evaluation environment. Feel free to add
this little library to your toolbox and let your Clojure programs go fast and
lean.

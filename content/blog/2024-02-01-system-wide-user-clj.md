{:title "System-wide user.clj with tools.deps"
 :date-published "2024-02-01 11:00:00"
  :reddit-link
 "https://reddit.com/r/Clojure/comments/1ag8bq0/systemwide_userclj_with_toolsdeps/"}

Ever since I converted from [Leiningen](https://leiningen.org/) and
[Boot](https://boot-clj.github.io/) to
[tools.deps](https://clojure.org/guides/deps_and_cli), I've been missing a place
to define devtime functions and helpers that would automatically be available in
any REPL I start locally. Boot allows to put any code into `profile.boot`,
Leiningen has a system-wide `profiles.clj` that is a bit more awkward for
defining functions but it still can be done. I finally decided to recreate the
same experience with tools.deps and got pretty close. The setup I came up with
took a bit of effort to figure out, so I want to document all the steps and
gotchas here and share this setup with you.

### Command line options, deps.edn, and aliases

Let's begin by creating a file `~/.clojure/user.clj`. For now, its content will
be the following:

```clj
(in-ns 'user)

(defn heap []
  (let [u (.getHeapMemoryUsage (java.lang.management.ManagementFactory/getMemoryMXBean))
        used (/ (.getUsed u) 1e6)
        total (/ (.getMax u) 1e6)]
    (format "Used: %.0f/%.0f MB (%.0f%%)" used total (/ used total 0.01))))

(println "Loaded system-wide user.clj!")
```

tools.deps does not have a notion of a special Clojure file that it will load
automatically. But we can instruct it to do so. There are two ways — two
command-line options — for this. `--init` (`-i` for short) will load the
provided file:

```shell
$ clj -i ~/.clojure/user.clj
Loaded system-wide user.clj!
```

Our `user.clj` got loaded, but notice how we didn't drop into the REPL — Clojure
CLI immediately quit after loading the file. We'll have to pass an explicit `-r`
flag to get the REPL together with the initializing file:

```shell
$ clj -i ~/.clojure/user.clj -r
Clojure 1.12.0-alpha5
Loaded system-wide user.clj!
user=> (heap)
"Used: 7/4295 MB (0%)"
```

Another option is to use the `--eval/-e` option and call `load-file` with it:

```shell
$ clj -e '(load-file (str (System/getProperty "user.home") "/.clojure/user.clj"))' -r
Clojure 1.12.0-alpha5
Loaded system-wide user.clj!
user=> (heap)
"Used: 7/4295 MB (0%)"
```

So, this is a way to explicitly load system-wide helpers into the REPL. We could
wrap it into shell aliases and call it a day. But there are other things that
you may want to set globally, such as extra dependencies and JVM options. To
satisfy all those requirements, we're going to put the initializing code into
`~/.clojure/deps.edn`.

There is a deps.edn parameter
[:main-opts](https://clojure.org/reference/deps_edn#aliases_mainopts) that
allows specifying default command-line parameters passed to Clojure CLI.
Unfortunately, top-level `:main-opts` is not supported; it has to be within an
alias. Let's make our global deps.edn look like this:

```clj
{...
 :aliases
 {:user
  {:main-opts ["-e" "(load-file (str (System/getProperty \"user.home\") \"/.clojure/user.clj\"))"]}}
  ...}
```

I prefer `-e` here instead of `-i` because neither `~` nor `$HOME` could be
resolved within `deps.edn`, and you would have to hardcode the full path to the
file, making the config less generic and cross-platform[[1]](#fn1)<a
name="bfn1"></a>.

Let's try out the new alias we've defined:

```shell
$ clj -M:user -r
Clojure 1.12.0-alpha5
Loaded system-wide user.clj!
user=> (heap)
"Used: 7/4295 MB (0%)"
```

### CIDER

In order for CIDER to automatically pick up the `:user` alias, you need to
execute `M-x customize-variable RET cider-clojure-cli-alises` and set it to
`:user`. Now, CIDER would append `:user` alias whenever you start a REPL with
it.

However, this is still not enough to load `user.clj`. CIDER provides its own
`:main-opts` when you invoke `cider-jack-in`, and since multiple `:main-opts`
from different aliases don't concatenate but override each other, the
`:main-opts` from `:user` alias is simply discarded. We have to change one extra
variable, `M-x customize-variable RET cider-repl-init-code`, and set its value
to:

```clj
'("(when-let [requires (resolve 'clojure.main/repl-requires)] (clojure.core/apply clojure.core/require @requires))"
  "(load-file (str (System/getProperty \"user.home\") \"/.clojure/user.clj\"))"
  "(in-ns 'user)")
```

Now, CIDER would load `user.clj` as instructed after the REPL starts. I don't
know how this is achieved in other Clojure IDEs, but I'm pretty sure they have a
similar option.

### Shell aliases

You can still add some shell aliases to simplify launching the REPL from the
terminal. They would look the same in `.bash_profile`, `.zshrc`, or
`fish.config`:

```shell
alias clojure="clojure -M:user"
alias clj="clj -M:user"
```

But since our `:user` alias hijacks command line options, you would still have
to launch the REPL as `clj -r`. I deal with this minor annoyance by having a
third alias, `cljr`, that also enables
[rebel-readline](https://github.com/bhauman/rebel-readline), which is much more
powerful than the standard readline. I have an extra alias for it in my
`deps.edn`:

```clj
{...
 :aliases
 {...
  :rebel {:extra-deps
          {com.bhauman/rebel-readline {:mvn/version "0.1.4"}}
          :main-opts
          ["-e" "(load-file (str (System/getProperty \"user.home\") \"/.clojure/user.clj\"))" "-m" "rebel-readline.main"]}}}
```

See I had to repeat `-e (load-file ...)` in `:rebel` alias. Again, this is
because `:main-opts` don't merge. Then, there is an extra line in my shell
config:

```shell
alias cljr="clojure -M:user:rebel"
```

Finally, just running `cljr` in the terminal would launch a REPL with
rebel-readline and my user.clj loaded.

### What to put into user.clj?

I keep many different helper functions in this global `user.clj`. For example,
functions that simplify reflection access to private fields and methods. `heap`,
which we've already seen.
[time+](https://clojure-goes-fast.com/kb/benchmarking/time-plus/). Because all
those functions are defined under `user` namespace, they become globally
accessible as `(user/heap)` and so on. Another helper I use all the time loads
performance tools into the current namespace. First, my full `:user` alias looks
like this:

```clj
{...
 :aliases
 {:user {:extra-deps
         {com.clojure-goes-fast/clj-async-profiler   {:mvn/version "1.1.1"}
          com.clojure-goes-fast/clj-java-decompiler  {:mvn/version "0.3.4"}
          com.clojure-goes-fast/clj-memory-meter     {:mvn/version "0.3.0"}
          criterium/criterium                        {:mvn/version "0.4.5"}}

         :jvm-opts ["-Djdk.attach.allowAttachSelf"
                    "-XX:+UseG1GC"
                    "-XX:-OmitStackTraceInFastThrow"
                    "-XX:+UnlockDiagnosticVMOptions" "-XX:+DebugNonSafepoints"]

         :main-opts ["-e" "(load-file (str (System/getProperty \"user.home\") \"/.clojure/user.clj\"))"]}}}
```

And inside `user.clj` I have this macro:

```clj
(defmacro perf-tools []
  '(do
     (require '[clj-async-profiler.core :as prof])
     (require '[clj-java-decompiler.core :refer [decompile]])
     (require '[clj-memory-meter.core :as mm])
     (require '[criterium.core :as crit])

     (.refer *ns* 'time+ #'user/time+)
     (.refer *ns* 'heap #'user/heap)))
```

Whenever I want to do some performance work, I execute `(user/perf-tools)`
within the current namespace. The library code only then gets loaded (so I don't
wait extra to load it when the REPL starts), and it becomes available in the
current namespace as `prof/...`, `mm/...`, and also `time+` and `heap` without
any extra qualifiers.

That is all for today. All of this is pretty basic, but I spent some time
reaching the setup I enjoy, so I hope this post can claim some of that time back
for you.

#### Footnotes

1. <a name="fn1"></a><span> There used to be a bug in Clojure CLI that would
split the string with spaces into multiple arguments and break everything.
That's why the string with `load-file` in my config previously contained commas
instead of spaces, since commas in Clojure are treated as whitespace. This
approach is colloquially known as the ["Corfield
comma."](https://soundcloud.com/defn-771544745/51-sean-corfield-aka-seancorfield)
The bug has been fixed; but the Corfield comma trick is still useful to be aware
of.</span>[↑](#bfn1)

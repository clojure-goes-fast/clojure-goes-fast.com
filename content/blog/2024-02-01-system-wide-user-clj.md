{:title "System-wide user.clj with tools.deps"
 :date-published "2024-02-01 11:00:00"
 :date-updated "2024-07-11"
 :reddit-link
 "https://reddit.com/r/Clojure/comments/1ag8bq0/systemwide_userclj_with_toolsdeps/"}

_Updated on 2024-07-11: changed the approach from passing eval-command with
 :main-opts to adding a :local/root directory to classpath. The old approach can
 be found at the end of the post._

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

### deps.edn and aliases

Clojure has this special feature where it automatically loads a file called
`user.clj`. It happens in the method
[doInit()](https://github.com/clojure/clojure/blob/clojure-1.11.3/src/jvm/clojure/lang/RT.java#L486)
of `clojure/lang/RT.java`. This behavior is commonly used on a project level to
define functions useful for REPL development, e.g., in the [reloaded
workflow](https://www.cognitect.com/blog/2013/06/04/clojure-workflow-reloaded).
But this can also be used as a place to put system-wide functions and tools.

Let's begin by creating a file `~/.clojure/src/user.clj`. For now, its content
will be the following:

```clj
(in-ns 'user)

(defn heap []
  (let [u (.getHeapMemoryUsage (java.lang.management.ManagementFactory/getMemoryMXBean))
        used (/ (.getUsed u) 1e6)
        total (/ (.getMax u) 1e6)]
    (format "Used: %.0f/%.0f MB (%.0f%%)" used total (/ used total 0.01))))

(println "Loaded system-wide user.clj!")
```

The `~/.clojure/src` directory we've just created will serve as a local project
that we can put onto the classpath. We can add it to a top-level `:deps` key,
but I prefer having a dedicated `:user` profile so that I can exclude it when
necessary. Open your `~/.clojure/deps.edn` and add this alias:

```clj
...
:aliases {:user {:extra-deps {global/user-clj {:local/root "/Users/myuser/.clojure/"}}}}
```

The name of the artificial project (`global/user-clj`) is completely arbitrary.
Note that you have to provide the full path to the `.clojure` dir, you can't use
`~` or `$HOME` here[[1]](#fn1)<a name="bfn1"></a>. Because it's a "project", you
might as well split `user.clj` into more files if it gets too big.

Let's try out the new alias we've defined:

```shell
$ clj -A:user
Loaded system-wide user.clj!
Clojure 1.12.0-beta1
user=> (heap)
"Used: 17/4295 MB (0%)"
```

### CIDER

In order for CIDER to automatically pick up the `:user` alias, you need to
execute `M-x customize-variable RET
cider-clojure-cli-global-aliases`[[2]](#fn2)<a name="bfn2"></a> and set it to
`:user`. Now, CIDER would append `:user` alias whenever you start a REPL with
it.

Try it out by jacking in into any project with `M-x cider-jack-in`. After you
see the REPL prompt, navigate to `*nrepl-server ...*` buffer. You should see
something like this:

```
Loaded system-wide user.clj!
nREPL server started on port 51068 on host localhost - nrepl://localhost:51068
```

The first line means that our global `user.clj` was  picked up succesfully.

### Shell aliases

You can still add some shell aliases to simplify launching the REPL from the
terminal. They would look the same in `.bash_profile`, `.zshrc`, or
`fish.config`:

```shell
alias clojure="clojure -A:user"
alias clj="clj -A:user"
```

### Projects with user.clj

I've mentioned before that some Clojure projects already have a `user.clj` file
in their `src` directory. But the logic in `clojure.lang.RT` only loads the
first `user.clj` it encounters on the classpath. Due to the nature of how
[tools.deps](https://clojure.org/guides/deps_and_cli) works, the first
`user.clj` will be the project-local one because the project `src` directory is
always first on the classpath. You can verify it for yourself by running
`clojure -Spath` in any project:

```shell
$ clojure -Spath | tr : \n
src
/Users/myuser/.clojure/src
/Users/myuser/.m2/repository/org/clojure/clojure/1.11.3/clojure-1.11.3.jar
/Users/myuser/.m2/repository/org/clojure/core.specs.alpha/0.2.62/core.specs.alpha-0.2.62.jar
/Users/myuser/.m2/repository/org/clojure/spec.alpha/0.3.218/spec.alpha-0.3.218.jar
```

So, Clojure will load the project-local `user.clj` and ignore our system-wide
one. I could only come up with a single solution to this — add a snippet to the
project-local `user.clj` which instructs it to go over **all** `user.clj` files
on the classpath and explicitly load them. The snippet would look like this:

```clj
;; This is the project-local user.clj.
(ns user)

;; This is a snippet that loads all user.clj's it can find.
;; If *file* is nil, it means we are called recursively, do nothing.
(when *file*
  (->> (.getResources (.getContextClassLoader (Thread/currentThread)) "user.clj")
       enumeration-seq
       ;; Assume the first userfile is the currently loaded one. Load others if
       ;; there are more to load.
       rest
       (run! #(clojure.lang.Compiler/load (clojure.java.io/reader %)))))

;; Below is the regular content of project-local user.clj.

(defn hello [] "hello")

(println "Loaded project-local user.clj!")
```

If we now run the REPL, we get this:

```shell
$ clj -A:user
Loaded system-wide user.clj!
Loaded project-local user.clj!
Clojure 1.11.3
user=> (heap)
"Used: 17/4295 MB (0%)"
user=> (hello)
"hello"
```

The snippet above can be copied and committed into any project that already has
a `user.clj`. The snippet has a property of actually loading all `user.clj`s on
the classpath, including those that come with JAR dependencies. Whether it's
desirable is up to you; if not, you can tune the snippet to skip the
JAR-residing files.

### What to put into user.clj?

I keep many different helper functions in this global `user.clj`. For example,
functions that simplify reflection access to private fields and methods. `heap`,
which we've already seen. [time+](/kb/benchmarking/time-plus/). Because all
those functions are defined under `user` namespace, they become globally
accessible as `(user/heap)` and so on. Another helper I use all the time loads
performance tools into the current namespace. First, my full `:user` alias looks
like this:

```clj
{...
 :aliases
 {:user {:extra-deps
         {global/user-clj                           {:local/root "/Users/myuser/.clojure/"}
          com.clojure-goes-fast/clj-async-profiler  {:mvn/version "1.2.0"}
          com.clojure-goes-fast/clj-java-decompiler {:mvn/version "0.3.4"}
          com.clojure-goes-fast/clj-memory-meter    {:mvn/version "0.3.0"}
          criterium/criterium                       {:mvn/version "0.4.5"}}

         :jvm-opts ["-Djdk.attach.allowAttachSelf"
                    "-XX:+UseG1GC"
                    "-XX:-OmitStackTraceInFastThrow"
                    "-XX:+UnlockDiagnosticVMOptions" "-XX:+DebugNonSafepoints"]}}}
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

### The old approach

Before, I've been using a different approach that involved supplying custom
`:main-opts`. It turned out to be too brittle, so I'm leaving it here for
posterity. I no longer recommend this solution.

The idea is still to make a file `~/.clojure/user.clj` and then add the
following into `:user` alias:

```clj
{...
 :aliases
 {:user
  {:main-opts ["-e" "(load-file (str (System/getProperty \"user.home\") \"/.clojure/user.clj\"))"]}}
  ...}
```

Then, you would make shell aliases as usual, but you would have to include `-r`
flag because us overriding `:main-opts` would prevent REPL from starting
automatically.

CIDER won't work with this automatically. CIDER provides its own `:main-opts`
when you invoke `cider-jack-in`, and since multiple `:main-opts` from different
aliases don't concatenate but override each other, the `:main-opts` from `:user`
alias is simply discarded. We have to change one extra variable, `M-x
customize-variable RET cider-repl-init-code`, and set its value to:

```clj
'("(when-let [requires (resolve 'clojure.main/repl-requires)] (clojure.core/apply clojure.core/require @requires))"
  "(load-file (str (System/getProperty \"user.home\") \"/.clojure/user.clj\"))"
  "(in-ns 'user)")
```

That is all for today. All of this is pretty basic, but I spent some time
reaching the setup I enjoy, so I hope this post can claim some of that time back
for you.

#### Footnotes

1. <a name="fn1"></a><span> I have found [this
library](https://github.com/gfredericks/user.clj) that allows not specifying
absolute file path. I however don't mind changing the absolute path once and
avoid an extra dependency.</span>[↑](#bfn1)
2. <a name="fn2"></a><span> Note that it better be
`cider-clojure-cli-global-aliases`, not `cider-clojure-cli-aliases`. The
latter would also work but it is intended for projects to override, while the
former is for system-wide aliases. See [Clojure CLI
options](https://docs.cider.mx/cider/basics/up_and_running.html#clojure-cli-options).</span>[↑](#bfn2)

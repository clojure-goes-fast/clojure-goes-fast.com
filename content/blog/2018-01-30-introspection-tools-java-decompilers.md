---
name: "Introspection tools: Java decompilers"
author: Alexander Yakushev
date-published: 2018-01-29 16:40:00
reddit-link: https://www.reddit.com/r/Clojure/comments/7tsqjr/introspection_tools_java_decompilers/
---

As you probably are already aware of, Clojure compiles down to JVM bytecode. The
fact that Clojure is not an interpreted language but a compiled one makes the
compilation output tangible. The resulting classes are something that we can
scrutinize and understand. In this post, we will take a look at tools that help
us decipher the arcane internals of JVM classfiles.

### Why you need this (and what it has to do with performance)

Frankly, unless you are hacking the Clojure compiler in your free time, the
information in this post is not your first priority to learn. But understanding
what the Clojure code ultimately turns into gives you an extra way to improve
and optimize your programs.

Decompiling the resulting code can significantly help in performance-sensitive
situations where you are writing interop-heavy tight loops. Seeing whether you
are really using primitive math and unboxed types is more reliable than compiler
warnings. Observing how exactly the type hints influence the bytecode allows you
to put only those that are necessary (and not just scatter them around until the
compiler is happy).

A decompiler also gives an ability to see what some obscure Clojure macros
expand into, e.g., `reify`, `proxy`, `gen-class`. It is difficult to see it from
their implementation as it's complicated and riddled with handwritten bytecode
assembly.

### Testbed

Let's create a simple project that we'll use to AOT-compile Clojure code. Both
Boot and Leiningen variants are provided side by side, however, there isn't any
substantial difference between them here.

```shell
$ boot -d seancorfield/boot-new new -n testbed   # Leiningen: lein new testbed
$ boot aot -n decomp.core target                 # Leiningen: lein update-in : assoc :aot :all -- compile
$ cd target/testbed                              # Leiningen: cd target/classes/testbed
$ ls -l
-rw-r-----  1325 core$fn__183.class
-rw-r-----   976 core$foo.class
-rw-r-----  1507 core$loading__6434__auto____181.class
-rw-r-----  2520 core__init.class
```

If you see the output like this, it means you have successfully compiled the
sample Clojure file `src/testbed/core.clj` into Java classes. Before we proceed,
let's understand how a single Clojure namespace produced four classfiles.

- Each Clojure function gets its own Java class. The function `testbed.core/foo`
  turned into `testbed/core$foo.class`.
- When a namespace is compiled, it produces several classes.
  `testbed/core__init.class` is a class where all the code defined in the
  namespace is executed. This includes loading and storing vars and running
  top-level code. `core$loading...` is a class where namespace's `import`s and
  `require`s are executed. Finally, the anonymous function `core$fn__183.class`
  when invoked just adds `testbed.core` to the list of loaded namespaces.

This is enough of prelude for now. In a little while, you'll be able to discover
all this by yourself.

### Bytecode disassembly

We can start with the simplest tool in our arsenal, `javap`. It is shipped with
every JDK distribution and allows to disassemble the compiled classes into their
bytecode representation.

Just a reminder, our sample function looks like this:

```clojure
(defn foo
  "I don't do a whole lot."
  [x]
  (println x "Hello, World!"))
```

```shell
$ javap -c core\$foo
```

It yields plenty of output, so we'll focus on the part where something actually
happens, the `invokeStatic` and static initializer methods.

```x86asm
  public static java.lang.Object invokeStatic(java.lang.Object);
    Code:
       0: getstatic     #15                 // Field const__0:Lclojure/lang/Var;
       3: invokevirtual #21                 // Method clojure/lang/Var.getRawRoot:()Ljava/lang/Object;
       6: checkcast     #23                 // class clojure/lang/IFn
       9: aload_0
      10: aconst_null
      11: astore_0
      12: ldc           #25                 // String Hello, World!
      14: invokeinterface #29,  3           // InterfaceMethod clojure/lang/IFn.invoke:(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;
      19: areturn

  public static {};
    Code:
       0: ldc           #36                 // String clojure.core
       2: ldc           #38                 // String println
       4: invokestatic  #44                 // Method clojure/lang/RT.var:(Ljava/lang/String;Ljava/lang/String;)Lclojure/lang/Var;
       7: checkcast     #17                 // class clojure/lang/Var
      10: putstatic     #15                 // Field const__0:Lclojure/lang/Var;
      13: return
```

In static initializer on lines 0 and 2, we load two string constants,
`"clojure.core"` and `"println"` onto the stack. Then, we call the static method
`clojure.lang.RT.var` on them which returns a Var back on the stack. Finally, we
store the resulting Var to a field `const__0`.

In the `invokeStatic` method, we begin by putting the Var from `const__0` onto
the stack and calling the method `getRawRoot` on it. This retains a function
object that corresponds to Clojure's `println` function. `aload_0` means putting
the first method argument on the stack (argument `x` in this case).

_Lines 10 and 11 might be confusing — they set the first method argument to
null. This is the feature of Clojure compiler called locals clearing — it is
designed to prevent retaining references to unused locals, something we might
explore in another blog post._

Line 12 puts the constant `"Hello, World"` onto the stack. The stack now looks
like this:

```
"Hello, World!", <x>, <println_fn>
```

Finally, line 14 calls the two-arg method `invoke(Object,Object)`. So, the first
two values on the stack are taken as arguments, and the third value is the
object on which the `invoke` method is called. All three values from the stack
are thus consumed, and the result of invoking the method is put back on the
stack. That value is then returned with `areturn`.

Whew, it takes a lot of work and additional knowledge to deconstruct such a
simple function! Reading JVM bytecode is similar to reading x86 assembly — you
gradually get better at it, but it's still harder and slower than reading
higher-level code.

Luckily, there exists an easier way to read bytecode. All we need is to
decompile it to Java. Although the bytecode that the Clojure compiler produces
doesn't precisely map to Java language, we can still gain a lot of insight with
much less effort. It is the difference between reading Assembly and slightly
hacky C code. I'd take the latter any day.

### Java decompilers

There are several Java decompilers, but unfortunately, neither of them is an
ultimate endgame solution, at least for Clojure. Each has shortcomings, so it
might be useful to have a few of them handy, for different usecases.

Most of the time I prefer the good
old [JAD](http://www.javadecompilers.com/jad). It's an abandoned non-open Java
decompiler written in C++, last updated in 2011. It doesn't support lambdas and
some other modern Java features (not like Clojure uses them anyway). On the
bright side, it is very fast, and it decompiles most Clojure bytecode quite well
(beyond some convoluted try/catch constructs, but that's forgivable).

One JAD deficiency turned into a strength is that it misunderstands Clojure's
above-mentioned _locals clearing_ trick. E.g. it will decompile the function
`(defn foo [x y] (+ x y))` into this:

```java
...
    public static Object invokeStatic(Object x, Object y) {
        return Numbers.add(x = null, y = null);
    }
...
```

Where other decompilers will produce this:

```java
...
    public static Object invokeStatic(Object x, Object y) {
        final Object o = x;
        x = null;
        final Object o2 = y;
        y = null;
        return Numbers.add(o, o2);
    }
...
```

Now, the JAD output is not really valid — Java would execute the null
assignments first, then calling `add` on two nulls. But after you start
recognizing all the `= null` as locals clearing, the JAD output becomes just
less noisy than the "correct" one.

Anyway, here are some other Java compilers that might or might not prove useful
to you:

- [Java Decompiler (JD Project)](http://jd.benow.ca/) is an open-source
  well-maintained project. It has a GUI interface and plugins for different
  IDEs. Sadly, it often fails to decompile Clojure-produced classes to Java and
  resorts to barfing out bytecode.
- [CFR](http://www.benf.org/other/cfr/) is an actively-developed decompiler
  written in Java. It deals fine with Clojure bytecode and has better support
  for new Java features too. CFR is free but not open-source.
- [Fernflower](https://github.com/fesh0r/fernflower)
  and [Procyon](https://bitbucket.org/mstrobel/procyon) seem to produce matching
  output, similar to CFR. They are both maintained and released as open-source.

_You don't have to install all of these to try them
out. [Java Decompilers Online](http://www.javadecompilers.com/) allows you to
upload your compiled Java classfile and decompile it with any of the listed
tools._

Let's decompile our already existing classfile with the decompiler of your
choice. I'll use JAD.

```shell
# JAD writes the output to a file named <class.jad>, we have to view it manually.
$ jad core\$foo.class && cat core\$foo.jad

package testbed;

import clojure.lang.*;

public final class core$foo extends AFunction
{

    public static Object invokeStatic(Object x)
    {
        return ((IFn)const__0.getRawRoot()).invoke(x = null, "Hello, World!");
    }

    public Object invoke(Object obj)
    {
        obj = null;
        return invokeStatic(obj);
    }

    public static final Var const__0 = (Var)RT.var("clojure.core", "println");
}
```

This is much better! It is evident now that `clojure.core/println` is stored in
`const__0` at initialization, and then it is called on `x` and `"Hello, World!"`
when the function is invoked. With the decompiler, we can tell what's going on
at a glance (given that the code is trivial) where we spent minutes to decipher
the bytecode.

Let's try another one:

```
$ jad core__init.class && cat core__init.jad

...
    public static void load() {
        ((IFn)const__0.getRawRoot()).invoke(const__1);
        ((IFn)new core.loading__6434__auto____181()).invoke();
        if(!((Symbol)const__1).equals(const__2)) goto _L2; else goto _L1
_L1:
        null;
          goto _L3
_L2:
        LockingTransaction.runInTransaction((Callable)new core.fn__183());
        null;
_L3:
        const__3.setMeta((IPersistentMap)const__12);
        const__3.bindRoot(new core.foo());
        const__3;
    }

    public static void __init0() {
        const__0 = (Var)RT.var("clojure.core", "in-ns");
        const__1 = (AFn)Symbol.intern(null, "testbed.core");
        const__2 = (AFn)Symbol.intern(null, "clojure.core");
        const__3 = (Var)RT.var("testbed.core", "foo");
        const__12 = (AFn)RT.map(new Object[] {
            RT.keyword(null, "arglists"), PersistentList.create(Arrays.asList(new Object[] {
                Tuple.create(Symbol.intern(null, "x"))
            })), RT.keyword(null, "doc"), "I don't do a whole lot.", RT.keyword(null, "line"), Integer.valueOf(3), RT.keyword(null, "column"), Integer.valueOf(1), RT.keyword(null, "file"), "testbed/core.clj"
        });
    }

    static {
        __init0();
        Compiler.pushNSandLoader(RT.classForName("testbed.core__init").getClassLoader());
        load();
        break MISSING_BLOCK_LABEL_17;
        Var.popThreadBindings();
        throw ;
        Var.popThreadBindings();
    }
```

What we see now is the class for the `testbed.core` namespace. When loaded,
static initializer is called which in turn calls `__init0()`. That's where all
constants are assigned. You can also see that's the `testbed.core/foo` var is
created there, together with its metadata.

Afterwards, `load()` is called which performs some namespace-related rituals and
then sets `const__3` (which is our `testbed.core/foo` var) the proper metadata
and value.

With other decompilers, you'll get a similar experience. You run the binary on
the classfile and get Java code back. All decompilers have plenty of tunables
that can slightly modify their behavior; just pass `--help` to the decompiler to
see them.

Now, you are able to decompile and study any AOT-compiled Clojure bytecode.
Clojure itself might be a fun place to start — you can compile it yourself or
just extract the classes from downloaded `clojure.jar`. Then, let the decompiler
loose on the classes and go wild!

### Live decompilation in the REPL

One drawback of the described workflow is that it is not very dynamic. You have
to save the code in a file first, then AOT-compile it with a build tool, then
run the decompiler to get the results. I wanted to make it more natural to
experiment and fool around, so I
created
[clj-java-decompiler](https://github.com/clojure-goes-fast/clj-java-decompiler).
It's a convenience wrapper
around [Procyon](https://bitbucket.org/mstrobel/procyon/overview) that allows
decompiling any Clojure form directly from the REPL. It dramatically cuts the
feedback loop and makes it much easier to explore Clojure compiler's
intricacies.

Add `[com.clojure-goes-fast/clj-java-decompiler "0.1.0"]` to your dependencies
(might as well load it dynamically) and start playing:

```
user> (require '[clj-java-decompiler.core :refer [decompile]])
nil
user> (decompile (fn [] (println "Hello, decompiler!")))

// Decompiling class: user$fn__13649
import clojure.lang.*;

public final class user$fn__13649 extends AFunction
{
    public static final Var const__0;

    public static Object invokeStatic() {
        return ((IFn)const__0.getRawRoot()).invoke((Object)"Hello, decompiler!");
    }

    public Object invoke() {
        return invokeStatic();
    }

    static {
        const__0 = RT.var("clojure.core", "println");
    }
}
```

The output is similar to what the standalone decompiler gave us, but now you can
instantly decompile anything you want!

#### Did you know... (A few examples to whet your appetite)

...that `case` compiles to Java's `switch`?

```
user> (decompile (case "foo"
                   "foo" 1
                   "bar" 2
                   3))

...
    public static Object invokeStatic() {
        final Object G__13653 = "foo";
        switch (Util.hash(G__13653)) {
            case 97299: {
                if (Util.equiv(G__13653, (Object)const__0)) {
                    return const__1;
                }
                break;
            }
            case 101574: {
                if (Util.equiv(G__13653, (Object)const__2)) {
                    return const__3;
                }
                break;
            }
        }
        return const__4;
    }
...
```

...that `loop` compiles into Java's `while` (and an efficient one)?

```
user> (decompile (loop [i 0, sum 0]
                   (if (> i 10)
                     sum
                     (recur (unchecked-inc i) (unchecked-add sum i)))))

...
    public static Object invokeStatic() {
        long i = 0L;
        long sum = 0L;
        while (i <= 10L) {
            final long n = i + 1L;
            sum += i;
            i = n;
        }
        return Numbers.num(sum);
    }
```

...what it means when the compiler warns
about
[reflection](http://clojure-goes-fast.com/blog/performance-nemesis-reflection/)?

```
user> (decompile (fn [] (.substring @(volatile! "foobar") 3)))

...
    public static Object invokeStatic() {
        return Reflector.invokeInstanceMethod(((IFn)const__0.getRawRoot()).invoke(((IFn)const__1.getRawRoot()).invoke((Object)"foobar")), "substring", new Object[] { const__2 });
    }
```

### Conclusions

With the expertise obtained today, you'll be able to better understand the
internals of Clojure compilation process and its runtime. A dynamic decompiler
is yet another instrument under your belt that gives you a unique perspective on
the code you are writing. I hope you will master this tool and use it to make your
Clojure programs go even faster.

### References

- [Java Bytecode Fundamentals](http://arhipov.blogspot.com/2011/01/java-bytecode-fundamentals.html)
- [Java Decompilers Online](http://www.javadecompilers.com/)

{:title "Profiling"
 :page-index 0
 :layout :kb-page}

If benchmarking is discovering how long a piece of code takes to run as a whole,
then profiling means figuring out how much time each function, method, or
instruction spends. The primary purpose of using a profiler is to optimize one's
code. You don't want to go tweaking and rewriting your code blindly, spending
time on optimizations that might not even matter. Profiling gives you precisely
that. It identifies who are the primary contributors to your program's execution
time. Once you know them, you can focus on revising them first, as it will have
the biggest effect.

### Tracing and sampling profilers

A **sampling profiler** (also known as a [statistical
profiler](https://en.wikipedia.org/wiki/Profiling_\(computer_programming\)#Statistical_profilers))
works by regularly taking a stack snapshot for each live thread and recording
which methods were on the stack at that moment. Then, it uses this information
to infer approximately how long each method has been running. The reasoning is
the following: if 9 of 10 stack samples contain method `foo()`, and only 1
sample contains `bar()`, then `foo` must run 9 times longer than `bar`.

Because it is based on statistics, a sampling profiler doesn't really know how
many invocations of each method happened or how much time each method spent
exactly. But, surprisingly, statistical profilers are quite accurate if you give
them a reasonable sampling rate (around 100-10000 Hz) and some time to run.

A **tracing profiler** is another name for an [instrumenting
profiler](https://en.wikipedia.org/wiki/Profiling_\(computer_programming\)#Instrumentation).
Instrumentation means that the profiler is going to inject its profiling code
into every method of the program. The profiling code will track every entrance
and exit in/out of the method and the time spent inside. This type of profiling
makes no guesses. It knows for sure what methods were the slowest and invoked
the most number of times.

In the case of Clojure, the instrumentation of every method poses a huge
problem. Because of the way the Clojure compiler is implemented, a bare Clojure
application contains thousands of classes, let alone methods. Setting an
instrumenting profiler on a Clojure program will often grind your program to a
halt. It will take 5-10 minutes for the profiler to finish instrumenting, and
good luck using your program after that. However, to be fair, some JVM profilers
have quite an efficient instrumentation mechanism.

But even if an instrumenting profiler doesn't render your program completely
unusable, there is another problem with these kinds of profilers. Because
instrumentation is, by definition, invasive (methods are recompiled to contain
code that you didn't write), the veracity of its results dramatically
diminishes. Furthermore, depending on the implementation, an instrumenting
profiler can disable JIT and inlining of your code for better introspection.
Suddenly, the instrumented code that you profile stops resembling the original
code entirely. In such a scenario, an instrumenting profiler becomes much less
precise than a sampling one.

Therefore, it is advised to use a sampling profiler to profile Clojure programs
or pick the sampling profiling option in profilers that support both mechanisms.

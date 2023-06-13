# Benchmarks for CLJ-2786

https://clojure.atlassian.net/browse/CLJ-2786

Running the benchmarks:

    clojure -T:build javac && clojure -m jmh bench Vec profiler gc

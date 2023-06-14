## CLJ-2691 benchmarks

See [CLJ-2691](https://clojure.atlassian.net/browse/CLJ-2691).

Clone this dir:

    git clone --depth 1 --filter=blob:none --no-checkout https://github.com/alexander-yakushev/playground; cd playground; git checkout master -- clj-2691-benchmark; cd clj-2691-benchmark

Launch with:

    clojure -X runner/jmh :bench IfNot

For the first time, you have to run this command twice due to some classpath
annoyances.

The `:bench` parameter is a regexp, you can change it to select specific
benchmarks to run, e.g.:

    clojure -X runner/jmh :bench IfNotLoop.testOpaque

The results are in [results.txt](./results.txt).

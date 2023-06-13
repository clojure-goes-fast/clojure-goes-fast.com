package bench;

import org.openjdk.jmh.annotations.*;
import java.util.*;
import java.util.concurrent.*;
import clojure.lang.*;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 5, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 5, timeUnit = TimeUnit.SECONDS)
@State(Scope.Benchmark)
@Fork(value = 1)
public class Vec {

    @Param({"0", "1", "2", "3", "4", "5", "6", "8", "16", "32"})
    public static int size;

    @Benchmark
    public Object conjPersistent() {
        PersistentVector vec = PersistentVector.EMPTY;
        for (int i = 0; i < size; i++) {
            vec = vec.cons("dummy");
        }
        return vec;
    }

    @Benchmark
    public Object conjTransient() {
        ITransientCollection vec = PersistentVector.EMPTY.asTransient();
        for (int i = 0; i < size; i++) {
            vec = vec.conj("dummy");
        }
        return vec.persistent();
    }
}

package bench;

import org.openjdk.jmh.annotations.*;
import java.util.*;
import java.util.concurrent.*;
import clojure.lang.*;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 1, time = 5, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 1, time = 5, timeUnit = TimeUnit.SECONDS)
@State(Scope.Benchmark)
@Fork(value = 1)
public class Bench {

    @Benchmark
    public int render() {
        raytracer.Render.render(400, 10, "out2.png");
        return 1;
    }
}

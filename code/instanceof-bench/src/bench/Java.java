package bench;

import org.openjdk.jmh.annotations.*;
import java.util.*;
import java.util.concurrent.*;
import org.openjdk.jmh.infra.Blackhole;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 3, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 3, timeUnit = TimeUnit.SECONDS)
@State(Scope.Benchmark)
@Fork(value = 1)
public class Java {

    Object a;

    @Setup(Level.Iteration)
    public void setup() {
        a = new A();
    }

    @Benchmark
    public boolean instanceof_A() {
        return a instanceof A;
    }

    @Benchmark
    public boolean instanceof_Iface1() {
        return a instanceof Iface1;
    }

    @Benchmark
    public boolean instanceof_Iface2() {
        return a instanceof Iface2;
    }

    @Benchmark
    public boolean isInstance_A() {
        return A.class.isInstance(a);
    }

    @Benchmark
    public boolean isInstance_Iface1() {
        return Iface1.class.isInstance(a);
    }

    @Benchmark
    public boolean isInstance_Iface2() {
        return Iface2.class.isInstance(a);
    }

    static interface Iface1 {
        public long value();
    }

    static class A implements Iface1 {
        public long value() { return 1; }
    }

    static interface Iface2 {
        public long value();
    }

    static class B implements Iface2 {
        public long value() { return 2; }
    }

    static class C implements Iface2 {
        public long value() { return 3; }
    }

    static class D implements Iface2 {
        public long value() { return 4; }
    }
}

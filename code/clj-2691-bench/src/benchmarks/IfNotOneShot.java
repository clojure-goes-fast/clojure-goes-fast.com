package benchmarks;

import org.openjdk.jmh.annotations.*;
import java.util.*;
import java.util.concurrent.*;
import clojure.lang.*;
import clojure.java.api.Clojure;
import java.util.function.*;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 3, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 3, timeUnit = TimeUnit.SECONDS)
@State(Scope.Benchmark)
@Fork(value = 1)
public class IfNotOneShot {

    static Object require(String nsName) {
        return RT.var("clojure.core", "require").invoke(Symbol.intern(nsName));
    }

    static IFn.LL resolveFn(String fnName) {
        return (IFn.LL)RT.var("clj-code", fnName).getRawRoot();
    }

    IFn.LL id;
    IFn.LL testIf;
    IFn.LL testIfNot;
    IFn.LL testOpaqueIf;
    IFn.LL testOpaqueIfNot;
    IFn.LL testDirectIf;
    IFn.LL testDirectIfNot;
    IFn.LL testDirectOpaqueIf;
    IFn.LL testDirectOpaqueIfNot;

    long val;

    @Setup(Level.Iteration)
    public void setup() {
        require("clj-code");
        id                    = resolveFn("id");
        testIf                = resolveFn("test-if");
        testIfNot             = resolveFn("test-if-not");
        testOpaqueIf          = resolveFn("test-opaque-if");
        testOpaqueIfNot       = resolveFn("test-opaque-if-not");
        testDirectIf          = resolveFn("test-direct-if");
        testDirectIfNot       = resolveFn("test-direct-if-not");
        testDirectOpaqueIf    = resolveFn("test-direct-opaque-if");
        testDirectOpaqueIfNot = resolveFn("test-direct-opaque-if-not");

        val = ThreadLocalRandom.current().nextInt(20);
    }

    @Benchmark
    public long baseline() {
        return id.invokePrim(val);
    }

    @Benchmark
    public long testIf() {
        return testIf.invokePrim(val);
    }

    @Benchmark
    public long testIfNot() {
        return testIfNot.invokePrim(val);
    }

    @Benchmark
    public long testOpaqueIf() {
        return testOpaqueIf.invokePrim(val);
    }

    @Benchmark
    public long testOpaqueIfNot() {
        return testOpaqueIfNot.invokePrim(val);
    }

    @Benchmark
    public long testDirectIf() {
        return testDirectIf.invokePrim(val);
    }

    @Benchmark
    public long testDirectIfNot() {
        return testDirectIfNot.invokePrim(val);
    }

    @Benchmark
    public long testDirectOpaqueIf() {
        return testDirectOpaqueIf.invokePrim(val);
    }

    @Benchmark
    public long testDirectOpaqueIfNot() {
        return testDirectOpaqueIfNot.invokePrim(val);
    }
}

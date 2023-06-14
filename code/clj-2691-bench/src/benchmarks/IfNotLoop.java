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
public class IfNotLoop {

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

    public static final int SIZE = 0x100000;

    long[] xs;

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

        xs = new long[SIZE];
        for (int i = 0; i < SIZE; i++) {
            xs[i] = ThreadLocalRandom.current().nextInt(20);
        }
    }

    @Benchmark
    @OperationsPerInvocation(SIZE)
    public long baseline() {
        long acc = 0;
        for (long l : xs) {
            acc += id.invokePrim(l);
        }
        return acc;
    }

    @Benchmark
    @OperationsPerInvocation(SIZE)
    public long testIf() {
        long acc = 0;
        for (long l : xs) {
            acc += testIf.invokePrim(l);
        }
        return acc;
    }

    @Benchmark
    @OperationsPerInvocation(SIZE)
    public long testIfNot() {
        long acc = 0;
        for (long l : xs) {
            acc += testIfNot.invokePrim(l);
        }
        return acc;
    }

    @Benchmark
    @OperationsPerInvocation(SIZE)
    public long testOpaqueIf() {
        long acc = 0;
        for (long l : xs) {
            acc += testOpaqueIf.invokePrim(l);
        }
        return acc;
    }

    @Benchmark
    @OperationsPerInvocation(SIZE)
    public long testOpaqueIfNot() {
        long acc = 0;
        for (long l : xs) {
            acc += testOpaqueIfNot.invokePrim(l);
        }
        return acc;
    }

    @Benchmark
    @OperationsPerInvocation(SIZE)
    public long testDirectIf() {
        long acc = 0;
        for (long l : xs) {
            acc += testDirectIf.invokePrim(l);
        }
        return acc;
    }

    @Benchmark
    @OperationsPerInvocation(SIZE)
    public long testDirectIfNot() {
        long acc = 0;
        for (long l : xs) {
            acc += testDirectIfNot.invokePrim(l);
        }
        return acc;
    }

    @Benchmark
    @OperationsPerInvocation(SIZE)
    public long testDirectOpaqueIf() {
        long acc = 0;
        for (long l : xs) {
            acc += testDirectOpaqueIf.invokePrim(l);
        }
        return acc;
    }

    @Benchmark
    @OperationsPerInvocation(SIZE)
    public long testDirectOpaqueIfNot() {
        long acc = 0;
        for (long l : xs) {
            acc += testDirectOpaqueIfNot.invokePrim(l);
        }
        return acc;
    }
}

package bench;

import org.openjdk.jmh.annotations.*;
import java.util.*;
import java.util.concurrent.*;
import org.openjdk.jmh.infra.Blackhole;
import clojure.lang.*;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 5, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 5, timeUnit = TimeUnit.SECONDS)
@State(Scope.Benchmark)
@Fork(value = 1)
public class Clj {

    Object a;

    IFn instancePred;
    IFn isaPred;
    IFn satisfiesPred;
    IFn extendsPred;
    IFn cljEqual;
    IFn cljClass;
    Object collReduce;

    @Setup(Level.Iteration)
    public void setup() {
        a = new A();

        instancePred  = (IFn)RT.var("clojure.core", "instance?");
        isaPred       = (IFn)RT.var("clojure.core", "isa?");
        satisfiesPred = (IFn)RT.var("clojure.core", "satisfies?");
        extendsPred   = (IFn)RT.var("clojure.core", "extends?");
        cljEqual      = (IFn)RT.var("clojure.core", "=");
        cljClass      = (IFn)RT.var("clojure.core", "class");
        collReduce    = RT.var("clojure.core.protocols", "CollReduce").deref();
    }

    @Benchmark
    public Object instancePred_A() {
        return instancePred.invoke(A.class, a);
    }

    @Benchmark
    public Object instancePred_B() {
        return instancePred.invoke(B.class, a);
    }

    @Benchmark
    public Object instancePred_Iface1() {
        return instancePred.invoke(Iface1.class, a);
    }

    @Benchmark
    public Object instancePred_Iface2() {
        return instancePred.invoke(Iface2.class, a);
    }

    @Benchmark
    public Object isaPred_A() {
        return isaPred.invoke(a.getClass(), A.class);
    }

    @Benchmark
    public Object isaPred_B() {
        return isaPred.invoke(a.getClass(), B.class);
    }

    @Benchmark
    public Object isaPred_Iface1() {
        return isaPred.invoke(a.getClass(), Iface1.class);
    }

    @Benchmark
    public Object isaPred_Iface2() {
        return isaPred.invoke(a.getClass(), Iface2.class);
    }

    @Benchmark
    public Object satisfiesPred_hit() {
        return satisfiesPred.invoke(collReduce, PersistentVector.EMPTY);
    }

    @Benchmark
    public Object satisfiesPred_miss() {
        return satisfiesPred.invoke(collReduce, a);
    }

    @Benchmark
    public Object extendsPred_hit() {
        return extendsPred.invoke(collReduce, PersistentVector.class);
    }

    @Benchmark
    public Object extendsPred_miss() {
        return extendsPred.invoke(collReduce, A.class);
    }

    @Benchmark
    public Object equalClass_A() {
        return cljEqual.invoke(cljClass.invoke(a), A.class);
    }

    @Benchmark
    public Object equalClass_B() {
        return cljEqual.invoke(cljClass.invoke(a), B.class);
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

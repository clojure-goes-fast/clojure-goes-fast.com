package bench;

import org.openjdk.jmh.annotations.*;
import java.util.*;
import java.util.concurrent.*;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 5, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 5, timeUnit = TimeUnit.SECONDS)
@State(Scope.Benchmark)
@Fork(value = 1)
public class BranchPrediction {

    @Param({"1000", "10000", "100000"})
    public static int size;

    @State(Scope.Thread)
    public static class BenchState {

        int[] unsorted, sorted;

        @Setup(Level.Iteration)
        public void prepare() {
            // Create an array and fill it with random numbers in range 0..size.
            unsorted = new int[size];
            for (int i = 0; i < size; i++)
                unsorted[i] = ThreadLocalRandom.current().nextInt(size);

            // Make a sorted array from the unsorted array.
            sorted = unsorted.clone();
            Arrays.sort(sorted);
        }
    }

    public long sumArray(int[] a) {
        long sum = 0;
        // Threshold is the median value in the array.
        int thresh = size / 2;
        for (int el : a)
            // Sum all array elements that are lower than the median.
            if (el < thresh)
                sum += el;
        return sum;
    }

    @Benchmark
    public long unsortedArray(BenchState state) {
        return sumArray(state.unsorted);
    }

    @Benchmark
    public long sortedArray(BenchState state) {
        return sumArray(state.sorted);
    }
}

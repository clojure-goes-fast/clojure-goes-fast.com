package raytracer;

import java.util.random.*;

public class Rng {

    public static RandomGenerator SEED;

    static { reset(); }

    static void reset() {
        SEED = RandomGeneratorFactory.of("Xoroshiro128PlusPlus").create(0xDEADBEEF);
    }

    static double rand() { return SEED.nextDouble(); }

    static double rand(double min, double max) {
        return min + ((max - min) * SEED.nextDouble());
    }
}

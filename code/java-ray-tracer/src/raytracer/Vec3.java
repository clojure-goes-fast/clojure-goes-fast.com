package raytracer;

public class Vec3 {

    static final Vec3 ZERO = new Vec3(0, 0, 0);
    static final Vec3 ONE = new Vec3(1, 1, 1);

    final double x;
    final double y;
    final double z;

    Vec3(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    Vec3 neg() {
        return new Vec3(-x, -y, -z);
    }

    Vec3 add(Vec3 o) {
        return new Vec3(x + o.x, y + o.y, z + o.z);
    }

    Vec3 sub(Vec3 o) {
        return new Vec3(x - o.x, y - o.y, z - o.z);
    }

    Vec3 mul(Vec3 o) {
        return new Vec3(x * o.x, y * o.y, z * o.z);
    }

    Vec3 scale(double t) {
        return new Vec3(x * t, y * t, z * t);
    }

    Vec3 div(double t) {
        return new Vec3(x / t, y / t, z / t);
    }

    double dot(Vec3 o) {
        return x * o.x + y * o.y + z * o.z;
    }

    Vec3 cross(Vec3 o) {
        return new Vec3(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x);
    }

    double lengthSquared() {
        return x*x + y*y + z*z;
    }

    double length() {
        return Math.sqrt(lengthSquared());
    }

    Vec3 normalize() {
        return div(length());
    }

    boolean isNearZero() {
        double e = 0.0000008;
        return Math.abs(x) < e && Math.abs(y) < e && Math.abs(z) < e;
    }

    static Vec3 random() {
        return new Vec3(Rng.rand(), Rng.rand(), Rng.rand());
    }

    static Vec3 random(double min, double max) {
        return new Vec3(Rng.rand(min, max),
                        Rng.rand(min, max),
                        Rng.rand(min, max));
    }

    static Vec3 randomInUnitSphere() {
        while (true) {
            Vec3 p = random(-1, 1);
            if (p.lengthSquared() < 1) return p;
        }
    }

    static Vec3 randomInUnitDisk() {
        while (true) {
            Vec3 p = new Vec3(Rng.rand(-1, 1), Rng.rand(-1, 1), 0);
            if (p.lengthSquared() < 1) return p;
        }
    }

    public String toString() { return String.format("[%.2f,%.2f,%.2f]", x, y, z); }
}

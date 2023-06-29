package raytracer;

public class Ray {

    final Vec3 origin;
    final Vec3 direction;

    Ray(Vec3 origin, Vec3 direction) {
        this.origin = origin;
        this.direction = direction;
    }

    Vec3 at(double t) {
        return origin.add(direction.scale(t));
    }
}

package raytracer;

public class Sphere {

    final Vec3 center;
    final double radius;
    final Material material;

    Sphere(Vec3 center, double radius, Material material) {
        this.center = center;
        this.radius = radius;
        this.material = material;
    }

    HitRecord hit(Ray ray, double t_max) {
        double t_min = 0.01;
        Vec3 oc = ray.origin.sub(center);
        double a = ray.direction.lengthSquared();
        double half_b = oc.dot(ray.direction);
        double c = oc.lengthSquared() - radius*radius;
        double discriminant = half_b*half_b - a*c;

        if (discriminant < 0) return HitRecord.MISS;

        double sqrtd = Math.sqrt(discriminant);
        // Find the nearest root that lies in the acceptable range.
        double root = (-half_b - sqrtd) / a;
        if (root < t_min || root > t_max) {
            root = (-half_b - sqrtd) / a;
            if (root < t_min || root > t_max) return HitRecord.MISS;
        }

        Vec3 intersection = ray.at(root);
        Vec3 outwardNormal = intersection.sub(center).div(radius);
        boolean frontFace = ray.direction.dot(outwardNormal) < 0;
        return new HitRecord(intersection,
                             frontFace ? outwardNormal : outwardNormal.neg(),
                             material, root, frontFace, false);
    }
}

package raytracer;

public interface Material {

    // public Scatter scatter(Ray in, HitRecord hit);

    public static class Lambertian implements Material {

        Vec3 albedo;

        Lambertian(Vec3 albedo) {
            this.albedo = albedo;
        }

        public Scatter scatter(Ray ray, HitRecord hit) {
            Vec3 scatterDirection = hit.normal.add(Vec3.randomInUnitSphere().normalize());
            if (scatterDirection.isNearZero())
                scatterDirection = hit.normal;
            Ray scattered = new Ray(hit.point, scatterDirection);
            return new Scatter(scattered, albedo);
        }
    }

    public static class Metal implements Material {

        Vec3 albedo;
        double fuzz;

        Metal(Vec3 albedo, double fuzz) {
            this.albedo = albedo;
            this.fuzz = fuzz;
        }

        static Vec3 reflect(Vec3 v, Vec3 normal) {
            return v.sub(normal.scale(2).scale(v.dot(normal)));
        }

        public Scatter scatter(Ray ray, HitRecord hit) {
            Vec3 reflected = reflect(ray.direction.normalize(), hit.normal);
            if (reflected.dot(hit.normal) < 0) return Scatter.ABSORBED;
            Ray scattered = new Ray(hit.point, reflected.add(Vec3.randomInUnitSphere().normalize().scale(fuzz)));
            return new Scatter(scattered, albedo);
        }
    }

    public static class Dielectric implements Material {

        double refractionIndex;

        Dielectric(double refractionIndex) {
            this.refractionIndex = refractionIndex;
        }

        static Vec3 refract(Vec3 uv, Vec3 normal, double refractionRatio, double cos_theta) {
            Vec3 r_out_perp = uv.add(normal.scale(cos_theta)).scale(refractionRatio);
            Vec3 r_out_parallel = normal.scale(-Math.sqrt(Math.abs(1 - r_out_perp.lengthSquared())));
            return r_out_perp.add(r_out_parallel);
        }

        static double reflectance(double cosine, double reflectanceIndex) {
            double r = (1-reflectanceIndex) / (1+reflectanceIndex);
            double r2 = r * r;
            return r2 + (1-r2) * Math.pow(1-cosine, 5);
        }

        public Scatter scatter(Ray ray, HitRecord hit) {
            double refractionRatio = hit.frontFace ? 1/refractionIndex : refractionIndex;
            Vec3 unitDir = ray.direction.normalize();
            Vec3 normal = hit.normal;

            double cos_theta = Math.min(unitDir.neg().dot(normal), 1.0);
            double sin_theta = Math.sqrt(1.0 - cos_theta * cos_theta);
            boolean cantRefract = refractionRatio * sin_theta > 1.0;
            Vec3 direction;
            if (cantRefract || reflectance(cos_theta, refractionRatio) > Rng.rand()) {
                direction = Metal.reflect(unitDir, normal);
            } else {
                direction = refract(unitDir, normal, refractionRatio, cos_theta);
            }
            return new Scatter(new Ray(hit.point, direction), Vec3.ONE);
        }
    }
}

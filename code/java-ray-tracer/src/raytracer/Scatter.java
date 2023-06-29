package raytracer;

public class Scatter {

    final Ray ray;
    final Vec3 attenuation;
    boolean absorbed;

    final static Scatter ABSORBED
        = new Scatter(new Ray(Vec3.ZERO, Vec3.ZERO), Vec3.ZERO, true);

    Scatter(Ray ray, Vec3 attenuation) {
        this(ray, attenuation, false);
    }

    Scatter(Ray ray, Vec3 attenuation, boolean absorbed) {
        this.ray = ray;
        this.attenuation = attenuation;
        this.absorbed = absorbed;
    }
}

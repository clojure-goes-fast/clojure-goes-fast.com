package raytracer;

public class HitRecord {

    final Vec3 point;
    final Vec3 normal;
    final Material material;
    final double t;
    final boolean frontFace;
    final boolean miss;

    final static HitRecord MISS = new HitRecord(Vec3.ZERO, Vec3.ZERO, null, Double.MAX_VALUE, false, true);

    HitRecord(Vec3 point, Vec3 normal, Material material, double t, boolean frontFace, boolean miss) {
        this.point = point;
        this.normal = normal;
        this.material = material;
        this.t = t;
        this.frontFace = frontFace;
        this.miss = miss;
    }
}

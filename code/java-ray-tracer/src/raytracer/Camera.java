package raytracer;

public class Camera {

    final Vec3 origin;
    final Vec3 horizontal;
    final Vec3 vertical;
    final Vec3 lowerLeftCorner;
    final Vec3 w, u, v;
    final double lensRadius;

    static double degToRad(double deg) {
        return Math.PI * deg / 180;
    }

    Camera(double aspectRatio, double vfov, Vec3 lookFrom, Vec3 lookAt, Vec3 vup, double aperture, double focusDistance) {
        double theta = degToRad(vfov);
        double h = Math.tan(theta/2);
        double viewportH = 2*h;
        double viewportW = aspectRatio * viewportH;

        this.w = lookFrom.sub(lookAt).normalize();
        this.u = vup.cross(w).normalize();
        this.v = w.cross(u);

        this.origin = lookFrom;
        this.horizontal = u.scale(viewportW * focusDistance);
        this.vertical = v.scale(viewportH * focusDistance);
        this.lowerLeftCorner = origin
            .sub(horizontal.div(2))
            .sub(vertical.div(2))
            .sub(w.scale(focusDistance));

        this.lensRadius = aperture / 2;
    }

    Ray getRay(double s, double t) {
        Vec3 rd = Vec3.randomInUnitDisk().scale(lensRadius);
        Vec3 offset = u.scale(rd.x).add(v.scale(rd.y));
        // Vec3 offset = Vec3.ZERO;
        Vec3 dir = lowerLeftCorner
            .add(horizontal.scale(s))
            .add(vertical.scale(t))
            .sub(origin)
            .sub(offset);
        return new Ray(origin.add(offset), dir);
    }
}

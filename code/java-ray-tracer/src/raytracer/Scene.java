package raytracer;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.Color;
import java.util.*;

public class Scene {

    private Vec3 white = Vec3.ONE;
    private Vec3 blue = new Vec3(.5, .7, 1.);

    Vec3 sky(Ray r) {
        Vec3 unitDirection = r.direction.normalize();
        double t = .5 * (unitDirection.y + 1.);
        return white.scale(1-t).add(blue.scale(t));
    }

    Vec3 rayColor(Ray r, Sphere[] world, int maxBounces) {
        Vec3 accAttenuation = Vec3.ONE;
        while (maxBounces >= 0) {
            HitRecord hit = hitAll(r, world);
            if (!hit.miss) {
                Material mat = hit.material;
                Scatter scatter;
                if (mat instanceof Material.Lambertian)
                    scatter = ((Material.Lambertian)mat).scatter(r, hit);
                else if (mat instanceof Material.Metal)
                    scatter = ((Material.Metal)mat).scatter(r, hit);
                else
                    scatter = ((Material.Dielectric)mat).scatter(r, hit);
                if (scatter.absorbed) { return Vec3.ZERO; }
                accAttenuation = accAttenuation.mul(scatter.attenuation);
                r = scatter.ray;
                maxBounces--;
            } else
                return sky(r).mul(accAttenuation);
        }
        return Vec3.ZERO;
    }

    HitRecord hitAll(Ray r, Sphere[] world) {
        HitRecord hit = HitRecord.MISS;
        for (Sphere sphere : world) {
            HitRecord h = sphere.hit(r, hit.t);
            if (!h.miss) {
                hit = h;
            }
        }
        return hit;
    }

    Sphere[] buildRandomWorld() {
        Sphere[] world = new Sphere[500];
        int n = 0;

        // Ground
        world[n++] = new Sphere(new Vec3(0,-1000,0), 1000, new Material.Lambertian(new Vec3(.5,.5,.5)));

        // Three big spheres
        world[n++] = new Sphere(new Vec3(0,1,0), 1, new Material.Dielectric(1.5));
        world[n++] = new Sphere(new Vec3(-4,1,0), 1, new Material.Lambertian(new Vec3(.8,0,.2)));
        world[n++] = new Sphere(new Vec3(4,1,0), 1, new Material.Metal(new Vec3(.7,.6,.5), 0));

        // Random
        for (int a = -11; a < 11; a++) {
            for (int b = -11; b < 11; b++) {
                Vec3 center = new Vec3(a + .9 * Rng.rand(), .2, b + .9 * Rng.rand());

                if (center.sub(new Vec3(4, .2, 0)).length() > 0.9) {
                    double choose_mat = Rng.rand();
                    Material mat;
                    if (choose_mat < 0.8) {
                        // Diffuse
                        Vec3 albedo = Vec3.random().mul(Vec3.random());
                        mat = new Material.Lambertian(albedo);
                    } else if (choose_mat < 0.95) {
                        // Metal
                        Vec3 albedo = Vec3.random(.5, 1);
                        double fuzz = Rng.rand(0, .5);
                        mat = new Material.Metal(albedo, fuzz);
                    } else {
                        // Glass
                        mat = new Material.Dielectric(1.5);
                    }
                    world[n++] = new Sphere(center, .2, mat);
                }
            }
        }

        Sphere[] res = new Sphere[n];
        System.arraycopy(world, 0, res, 0, n);
        return res;
    }

    public void paint(Render render, int samplesPerPixel) {
        // Image
        int imageW = render.img.getWidth();
        int imageH = render.img.getHeight();
        double aspectRatio = (double)imageW / imageH;
        int maxBounces = 10;

        Vec3 lookFrom = new Vec3(13,5,5);
        Vec3 lookAt = new Vec3(0,0,0);
        double aperture = 0.1;
        double focusDistance = 10;
        Vec3 cameraUp = new Vec3(0,1,0);
        double vfov = 21;
        Camera camera = new Camera(aspectRatio, vfov, lookFrom, lookAt,
                                   cameraUp, aperture, focusDistance);
        Sphere[] world = buildRandomWorld();

        // Paint
        for (int i = 0; i < imageW; i++) {
            for (int j = 0; j < imageH; j++) {
                Vec3 pixel = Vec3.ZERO;
                for (int s = 0; s < samplesPerPixel; s++) {
                    double u = ((double)i + Rng.rand()) / (imageW - 1);
                    double v = ((double)j + Rng.rand()) / (imageH - 1);
                    Ray r = camera.getRay(u, v);
                    pixel = pixel.add(rayColor(r, world, maxBounces));
                    render.putPixel(i, imageH-j-1, pixel, samplesPerPixel);
                }
            }
        }
    }
}

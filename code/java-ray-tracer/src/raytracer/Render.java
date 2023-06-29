package raytracer;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.Color;
import java.util.*;
import java.io.*;
import com.sun.management.ThreadMXBean;
import java.lang.management.ManagementFactory;

public class Render {

    BufferedImage img;

    Render(BufferedImage img) {
        this.img = img;
    }

    static double clamp(double x, double min, double max) {
        if (x < min) return min;
        if (x > max) return max;
        return x;
    }

    void putPixel(int x, int y, Vec3 pixel, int samples) {
        double scale = 1. / samples;
        double r = 255.999 * clamp(Math.sqrt(pixel.x * scale), 0, 1);
        double g = 255.999 * clamp(Math.sqrt(pixel.y * scale), 0, 1);
        double b = 255.999 * clamp(Math.sqrt(pixel.z * scale), 0, 1);
        int color = 0xFF000000 | ((int)r << 16) | ((int)g << 8) | (int)b;
        // Comment for cleaner benchmarks.
        img.setRGB(x, y, color);
    }

    static void measure(Runnable r) {
        ThreadMXBean bean = (ThreadMXBean)ManagementFactory.getThreadMXBean();
        long start = System.currentTimeMillis();
        long bytesBefore = bean.getCurrentThreadAllocatedBytes();
        r.run();
        System.out.println(String.format("Elapsed: %,d ms", System.currentTimeMillis() - start));
        System.out.println(String.format("Allocated: %,.0f MB",
                                         (bean.getCurrentThreadAllocatedBytes() - bytesBefore) / 1e6));
    }

    public static void render(int imageWidth, int samplesPerPixel, String outFilename) {
        System.out.println(String.format("render: imageWidth=%d samplesPerPixel=%d file=%s",
                                         imageWidth, samplesPerPixel, outFilename));
        double aspectRatio = 3./2.;
        var img = new BufferedImage(imageWidth, (int)(imageWidth / aspectRatio),
                                    BufferedImage.TYPE_INT_ARGB);
        Render render = new Render(img);
        measure(() -> {
                Rng.reset();
                new Scene().paint(new Render(img), samplesPerPixel);
            });
        // Comment for cleaner benchmarks.
        try {
            ImageIO.write(img, "png", new File(outFilename));
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    public static void main(String[] args) throws Exception {
        render(Integer.parseInt(args[0]), Integer.parseInt(args[1]), args[2]);
    }
}

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.LinearGradientPaint;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Path2D;
import java.awt.geom.Area;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import javax.imageio.ImageIO;

/**
 * Original launcher artwork: a mint-to-cyan battery with three heat waves on a soft white tile.
 * All geometry is vector-based and authored here; no OEM pixels or assets are copied.
 * PNG resources support Android 4.4, which cannot inflate platform VectorDrawables.
 * Run from the repository root with Java 11+: java tools/GenerateLauncherIcon.java
 */
public final class GenerateLauncherIcon {
    private static final int DESIGN = 512;
    private static final int SCALE = 3;
    private static final Color WHITE = new Color(255, 255, 255);

    public static void main(String[] args) throws Exception {
        Path root = args.length == 0 ? Paths.get(".") : Paths.get(args[0]);
        BufferedImage master = draw();
        String[] density = {"mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi"};
        int[] sizes = {64, 96, 128, 192, 256};
        for (int i = 0; i < density.length; i++) {
            Path file = root.resolve("app/src/main/res/drawable-" + density[i] + "/ic_launcher.png");
            write(master, sizes[i], file);
        }
        write(master, 512, root.resolve("artwork/launcher-icon-preview.png"));
        System.out.println("Generated five density resources and artwork/launcher-icon-preview.png");
    }

    private static BufferedImage draw() {
        BufferedImage image = new BufferedImage(DESIGN * SCALE, DESIGN * SCALE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = graphics(image);
        Shape tile = new RoundRectangle2D.Double(43, 39, 426, 426, 105, 105);
        // A diffuse lower/right shadow and upper/left white halo match light OEM home-screen tiles.
        shadow(g, tile, 4, 8, 7, new Color(67, 79, 84, 61));
        shadow(g, tile, -4, -4, 5, new Color(255, 255, 255, 228));
        g.setPaint(new GradientPaint(56, 42, new Color(250, 251, 252), 438, 462, new Color(232, 235, 238)));
        g.fill(tile);
        g.setPaint(new GradientPaint(50, 40, WHITE, 444, 455, new Color(223, 227, 230)));
        g.setStroke(new BasicStroke(3.2f)); g.draw(tile);
        Shape inset = new RoundRectangle2D.Double(49, 45, 414, 414, 95, 95);
        g.setPaint(new GradientPaint(68, 48, new Color(255, 255, 255, 220), 425, 445, new Color(255, 255, 255, 0)));
        g.setStroke(new BasicStroke(3)); g.draw(inset);

        Shape cap = new RoundRectangle2D.Double(222, 116, 68, 38, 10, 10);
        Shape battery = new RoundRectangle2D.Double(153, 145, 206, 244, 18, 18);
        Area symbol = new Area(battery); symbol.add(new Area(cap));
        shadow(g, symbol, 1, 4, 3, new Color(29, 108, 121, 42));
        g.setPaint(new LinearGradientPaint(170, 140, 355, 373,
            new float[]{0, 0.48f, 1}, new Color[]{new Color(30, 211, 166), new Color(34, 200, 198), new Color(0, 167, 222)}));
        g.fill(symbol);
        g.setPaint(new GradientPaint(157, 149, new Color(24, 163, 148), 357, 389, new Color(0, 137, 184)));
        g.setStroke(new BasicStroke(2.6f)); g.draw(symbol);
        g.setPaint(new GradientPaint(160, 150, new Color(172, 255, 235, 140), 346, 383, new Color(255, 255, 255, 0)));
        g.setStroke(new BasicStroke(1)); g.draw(symbol);

        // Three broad S-curves remain distinct at a 64dp launcher size.
        for (int x : new int[]{207, 256, 305}) {
            Path2D wave = new Path2D.Double();
            wave.moveTo(x, 334);
            wave.curveTo(x + 18, 312, x - 18, 293, x, 271);
            wave.curveTo(x + 18, 249, x - 18, 230, x, 208);
            g.translate(1.5, 2);
            g.setColor(new Color(0, 126, 159, 90));
            g.setStroke(new BasicStroke(13.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)); g.draw(wave);
            g.translate(-1.5, -2);
            g.setPaint(new GradientPaint(200, 207, WHITE, 308, 336, new Color(233, 255, 254)));
            g.setStroke(new BasicStroke(12.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)); g.draw(wave);
        }
        g.dispose();
        return image;
    }

    private static Graphics2D graphics(BufferedImage image) {
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.scale(SCALE, SCALE);
        return g;
    }

    private static void shadow(Graphics2D target, Shape shape, double dx, double dy, double sigma, Color color) {
        int side = DESIGN * SCALE;
        BufferedImage mask = new BufferedImage(side, side, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = graphics(mask); g.translate(dx, dy); g.setColor(color); g.fill(shape); g.dispose();
        double scaledSigma = sigma * SCALE;
        int radius = (int) Math.ceil(scaledSigma * 3);
        float[] weights = new float[radius * 2 + 1]; double sum = 0;
        for (int i = -radius; i <= radius; i++) {
            weights[i + radius] = (float) Math.exp(-i * i / (2 * scaledSigma * scaledSigma));
            sum += weights[i + radius];
        }
        for (int i = 0; i < weights.length; i++) weights[i] /= sum;
        ConvolveOp horizontal = new ConvolveOp(new Kernel(weights.length, 1, weights), ConvolveOp.EDGE_NO_OP, null);
        ConvolveOp vertical = new ConvolveOp(new Kernel(1, weights.length, weights), ConvolveOp.EDGE_NO_OP, null);
        BufferedImage blurred = vertical.filter(horizontal.filter(mask, null), null);
        target.drawImage(blurred, 0, 0, DESIGN, DESIGN, null);
    }

    private static void write(BufferedImage master, int size, Path path) throws Exception {
        BufferedImage output = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = output.createGraphics();
        g.setComposite(AlphaComposite.Src);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(master, 0, 0, size, size, null); g.dispose();
        Files.createDirectories(path.getParent());
        ImageIO.write(output, "png", path.toFile());
    }
}

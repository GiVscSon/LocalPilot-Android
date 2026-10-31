import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/** Optional local-only 2048 R2C4 JPEG probe. Never uploads screenshots. */
class LocalJpgVerify {
    private static final String NAME = "Screenshot_2026-10-30-14-32-00-613_ru.beru.android.jpg";
    private static final int LEFT = 54, TOP = 825, RIGHT = 638, BOTTOM = 1409;
    private static final int COL = 3, ROW = 1, SIZE = 4;

    private record Sample(int r, int g, int b, long count) {
        int packed() { return (r << 16) | (g << 8) | b; }
    }

    private static void require(boolean ok, String detail) {
        if (!ok) throw new IllegalArgumentException(detail);
    }

    private static Sample rgb(int p) { return new Sample((p >> 16) & 255, (p >> 8) & 255, p & 255, 1); }

    private static String format(Sample s) {
        float[] hsv = Color.RGBtoHSB(s.r(), s.g(), s.b(), null);
        return String.format(java.util.Locale.ROOT,
                "RGB(%d,%d,%d) #%02X%02X%02X HSV(%.1f,%.3f,%.3f)",
                s.r(), s.g(), s.b(), s.r(), s.g(), s.b(),
                hsv[0] * 360f, hsv[1], hsv[2]);
    }

    private static Sample ring(BufferedImage img, int cx, int cy) {
        long rs = 0, gs = 0, bs = 0, count = 0;
        for (int y = cy - 51; y <= cy + 51; y += 4) {
            for (int x = cx - 51; x <= cx + 51; x += 4) {
                int dx = x - cx, dy = y - cy, d2 = dx * dx + dy * dy;
                if (d2 < 22 * 22 || d2 > 48 * 48) continue;
                int p = img.getRGB(x, y);
                rs += (p >> 16) & 255;
                gs += (p >> 8) & 255;
                bs += p & 255;
                count++;
            }
        }
        require(count > 0, "No background annulus samples");
        return new Sample((int)(rs / count), (int)(gs / count), (int)(bs / count), count);
    }

    private static Sample probe(BufferedImage img) {
        require(img != null, "JPEG decoding returned no image");
        require(RIGHT - LEFT == 584 && BOTTOM - TOP == 584, "Invalid 4x4 ROI geometry");
        int step = (RIGHT - LEFT) / SIZE;
        int x0 = LEFT + COL * step, y0 = TOP + ROW * step;
        int cx = x0 + step / 2, cy = y0 + step / 2;
        require(step == 146 && x0 == 492 && y0 == 971 && cx == 565 && cy == 1044,
                "R2C4 mismatch: check screenshot coordinates");
        require(img.getWidth() > RIGHT && img.getHeight() > BOTTOM,
                "JPEG " + img.getWidth() + "x" + img.getHeight()
                    + " is too small for original screenshot coordinates; do not scale silently");
        Sample avg = ring(img, cx, cy);
        Sample center = rgb(img.getRGB(cx, cy));
        System.out.printf("DIAG_2048_CELL_R2C4 ring_avg=%s center=%s ring_points=%d image=%dx%d roi=[%d,%d][%d,%d]%n",
                format(avg), format(center), avg.count(), img.getWidth(), img.getHeight(), LEFT, TOP, RIGHT, BOTTOM);
        int[][] pts = {{cx,cy},{530,1009},{600,1009},{530,1079},{600,1079},{565,1000},{565,1088}};
        for (int i = 0; i < pts.length; i++)
            System.out.printf("pt[%d] (%d,%d) -> %s%n", i, pts[i][0], pts[i][1],
                    format(rgb(img.getRGB(pts[i][0],pts[i][1]))));
        System.out.println("NOTE: center is a pixel, not proof of a digit; annulus may include shadows or glyphs.");
        return avg;
    }

    private static void selfTest() {
        BufferedImage img = new BufferedImage(691, 1536, BufferedImage.TYPE_INT_RGB);
        int glass = (189 << 16) | (174 << 8) | 193;
        for (int y = 0; y < img.getHeight(); y++)
            for (int x = 0; x < img.getWidth(); x++) img.setRGB(x, y, glass);
        img.setRGB(565, 1044, 0xFFFFFF);
        Sample s = probe(img);
        require(s.r() == 189 && s.g() == 174 && s.b() == 193,
                "Annulus must exclude the center and preserve background RGB");
        boolean caught = false;
        try { probe(new BufferedImage(400, 400, BufferedImage.TYPE_INT_RGB)); }
        catch (IllegalArgumentException expected) { caught = true; }
        require(caught, "Out-of-bounds screenshot must fail closed");
        System.out.println("LOCAL_JPG_SELF_TEST PASS (synthetic pixels only; not the missing JPEG)");
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 1 && "--self-test".equals(args[0])) { selfTest(); return; }
        require(args.length == 0, "Use --self-test or supply -Dlocalpilot.jpgDir=/folder");
        String dir = System.getProperty("localpilot.jpgDir");
        if (dir == null || dir.isBlank()) dir = System.getenv("LOCALPILOT_JPG_DIR");
        require(dir != null && !dir.isBlank(), "Set -Dlocalpilot.jpgDir or LOCALPILOT_JPG_DIR");
        File f = Path.of(dir, NAME).toFile();
        require(f.isFile(), "JPEG not available: " + f.getAbsolutePath());
        probe(ImageIO.read(f));
    }
}

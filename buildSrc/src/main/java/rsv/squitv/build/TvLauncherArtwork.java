package rsv.squitv.build;

import java.awt.*;
import java.awt.font.GlyphVector;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;

/** TV artwork sharing the original mark; phone and Windows rendering is preserved. */
public final class TvLauncherArtwork {
    private static final Color BACKGROUND = new Color(5, 9, 20);
    private static final Color ORANGE = new Color(255, 154, 54);

    private TvLauncherArtwork() {}

    public static BufferedImage banner() {
        BufferedImage image = canvas(1280, 720);
        Graphics2D g = graphics(image);
        try {
            Font font = new Font(Font.SANS_SERIF, Font.BOLD, 122);
            GlyphVector name = font.createGlyphVector(g.getFontRenderContext(), "SQUI TV");
            Rectangle2D bounds = name.getVisualBounds();
            double markSize = 240;
            double gap = 42;
            // Keep the mark and wordmark inside the central 882 x 448 safe area.
            double scale = Math.min(1, 870 / (markSize + gap + bounds.getWidth()));
            double width = scale * (markSize + gap + bounds.getWidth());
            g.translate((1280 - width) / 2, 360);
            g.scale(scale, scale);

            BufferedImage original = BrandIcon.render(864, false);
            // Crop only the empty padding around the original vector-rendered mark.
            g.drawImage(original, 0, -120, 240, 120, 192, 192, 672, 672, null);
            g.translate(markSize + gap - bounds.getX(), -bounds.getCenterY());
            paintWordmark(g, name);
        } finally { g.dispose(); }
        return image;
    }

    private static void paintWordmark(Graphics2D g, GlyphVector name) {
        for (int i = 0; i < name.getNumGlyphs(); i++) {
            Shape glyph = name.getGlyphOutline(i);
            g.setColor(i >= 5 ? ORANGE : Color.WHITE);
            g.fill(glyph);
        }
    }

    private static BufferedImage canvas(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try { g.setColor(BACKGROUND); g.fillRect(0, 0, width, height); }
        finally { g.dispose(); }
        return image;
    }

    private static Graphics2D graphics(BufferedImage image) {
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        return g;
    }
}

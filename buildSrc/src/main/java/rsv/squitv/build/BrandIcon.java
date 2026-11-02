package rsv.squitv.build;

import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;

/** The existing Android launcher artwork, shared by the Android and Windows builds. */
public final class BrandIcon {
    private BrandIcon() {}
    public static BufferedImage render(int pixels, boolean round) {
        BufferedImage image = new BufferedImage(pixels, pixels, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.scale(pixels / 108.0, pixels / 108.0);
            g.setColor(new Color(5, 9, 20));
            if (round) g.fill(new Ellipse2D.Float(0, 0, 108, 108));
            else g.fill(new RoundRectangle2D.Float(0, 0, 108, 108, 20, 20));
            g.setColor(new Color(255, 154, 54));
            g.setStroke(new BasicStroke(4, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(new Line2D.Float(43, 38, 38, 30));
            g.draw(new Line2D.Float(65, 38, 70, 30));
            g.draw(new RoundRectangle2D.Float(29, 38, 50, 38, 20, 20));
            g.setColor(new Color(255, 107, 53));
            Path2D.Float play = new Path2D.Float();
            play.moveTo(49, 46); play.lineTo(66, 57); play.lineTo(49, 68); play.closePath();
            g.fill(play);
        } finally { g.dispose(); }
        return image;
    }
}

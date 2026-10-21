// Biểu tượng tự vẽ (không phụ thuộc font có ký tự đặc biệt hay không) dùng chung cho giao diện sinh viên và giám thị.
package exam.common;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import javax.swing.Icon;

public final class Icons {
    private Icons() {}

    /** Một biểu tượng vẽ trong ô vuông size x size bằng nét/đường do painter quy định. */
    private interface Painter { void paint(Graphics2D g, float s); }

    private record Glyph(int size, Painter painter) implements Icon {
        @Override public int getIconWidth() { return size; }
        @Override public int getIconHeight() { return size; }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g2.translate(x, y);
            painter.paint(g2, size);
            g2.dispose();
        }
    }

    private static BasicStroke stroke(float w) { return new BasicStroke(w, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND); }

    /** Tam giác cảnh báo cam có dấu chấm than. */
    public static Icon warning(int size) {
        return new Glyph(size, (g, s) -> {
            g.setColor(Palette.WARNING);
            Path2D tri = new Path2D.Float();
            tri.moveTo(s / 2, s * 0.06);
            tri.lineTo(s * 0.97, s * 0.92);
            tri.lineTo(s * 0.03, s * 0.92);
            tri.closePath();
            g.fill(tri);
            float w = Math.max(2f, s / 10);
            g.setColor(Color.WHITE);
            g.setStroke(stroke(w));
            g.drawLine((int) (s / 2), (int) (s * 0.35), (int) (s / 2), (int) (s * 0.62));
            g.fillOval((int) (s / 2 - w / 2), (int) (s * 0.72), (int) w, (int) w);
        });
    }

    /** Hình tròn xanh có dấu tích. */
    public static Icon check(int size) { return circle(size, Palette.SUCCESS, true); }

    /** Hình tròn đỏ có dấu nhân. */
    public static Icon cross(int size) { return circle(size, Palette.ERROR, false); }

    private static Icon circle(int size, Color color, boolean tick) {
        return new Glyph(size, (g, s) -> {
            g.setColor(color);
            g.fill(new Ellipse2D.Float(0, 0, s, s));
            g.setColor(Color.WHITE);
            g.setStroke(stroke(Math.max(2f, s / 10)));
            Path2D p = new Path2D.Float();
            if (tick) {
                p.moveTo(s * 0.27, s * 0.52);
                p.lineTo(s * 0.44, s * 0.68);
                p.lineTo(s * 0.74, s * 0.33);
            } else {
                p.moveTo(s * 0.32, s * 0.32);
                p.lineTo(s * 0.68, s * 0.68);
                p.moveTo(s * 0.68, s * 0.32);
                p.lineTo(s * 0.32, s * 0.68);
            }
            g.draw(p);
        });
    }

    /** Dấu tích lớn trên nền tròn xanh nhạt (màn kết quả). */
    public static Icon bigCheck(int size) {
        return new Glyph(size, (g, s) -> {
            g.setColor(Palette.SUCCESS_SOFT);
            g.fill(new Ellipse2D.Float(0, 0, s, s));
            g.setColor(Palette.SUCCESS_TEXT);
            g.setStroke(stroke(s / 12));
            Path2D p = new Path2D.Float();
            p.moveTo(s * 0.30, s * 0.52);
            p.lineTo(s * 0.44, s * 0.66);
            p.lineTo(s * 0.71, s * 0.36);
            g.draw(p);
        });
    }

    public static Icon clock(int size, Color color) {
        return new Glyph(size, (g, s) -> {
            g.setColor(color);
            g.setStroke(stroke(Math.max(1.4f, s / 12)));
            g.draw(new Ellipse2D.Float(s * 0.08f, s * 0.08f, s * 0.84f, s * 0.84f));
            Path2D hands = new Path2D.Float();
            hands.moveTo(s / 2, s * 0.28);
            hands.lineTo(s / 2, s / 2);
            hands.lineTo(s * 0.68, s * 0.6);
            g.draw(hands);
        });
    }

    public static Icon search(int size, Color color) {
        return new Glyph(size, (g, s) -> {
            g.setColor(color);
            g.setStroke(stroke(Math.max(1.4f, s / 11)));
            g.draw(new Ellipse2D.Float(s * 0.1f, s * 0.1f, s * 0.55f, s * 0.55f));
            g.drawLine((int) (s * 0.58), (int) (s * 0.58), (int) (s * 0.88), (int) (s * 0.88));
        });
    }

    public static Icon eye(int size, Color color) {
        return new Glyph(size, (g, s) -> {
            g.setColor(color);
            g.setStroke(stroke(Math.max(1.4f, s / 12)));
            Path2D lid = new Path2D.Float();
            lid.moveTo(s * 0.05, s / 2);
            lid.quadTo(s / 2, s * 0.05, s * 0.95, s / 2);
            lid.quadTo(s / 2, s * 0.95, s * 0.05, s / 2);
            g.draw(lid);
            g.fill(new Ellipse2D.Float(s * 0.38f, s * 0.38f, s * 0.24f, s * 0.24f));
        });
    }
}

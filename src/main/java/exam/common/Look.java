package exam.common;

import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatLightLaf;
import java.awt.GraphicsEnvironment;
import java.util.Arrays;
import java.util.Map;
import javax.swing.UIManager;

/** Cài giao diện FlatLaf chung cho client và giám thị: màu từ Palette, bo góc từ exam/theme/FlatLaf.properties. */
public final class Look {
    private Look() {}

    /** Gọi một lần ở đầu main, trước khi tạo cửa sổ. */
    public static void install(float baseSize) {
        FlatLaf.registerCustomDefaultsSource("exam.theme");
        FlatLaf.setGlobalExtraDefaults(Map.of("@accentColor", hex(Palette.PRIMARY), "@background", hex(Palette.BACKGROUND)));
        // Segoe UI trên Windows, Inter nếu máy Linux có sẵn, không thì để FlatLaf chọn
        boolean windows = System.getProperty("os.name", "").toLowerCase().contains("win");
        String family = windows ? "Segoe UI" : "Inter";
        if (Arrays.asList(GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()).contains(family)) {
            FlatLaf.setPreferredFontFamily(family);
        }
        FlatLightLaf.setup();
        UIManager.put("defaultFont", UIManager.getFont("defaultFont").deriveFont(baseSize));
    }

    private static String hex(java.awt.Color c) { return String.format("#%06X", c.getRGB() & 0xFFFFFF); }
}

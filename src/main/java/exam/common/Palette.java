// Bảng màu chung cho giao diện sinh viên và giám thị (theo ảnh tham khảo): chỉ chứa hằng số màu.
package exam.common;

import java.awt.Color;

public final class Palette {
    public static final Color PRIMARY = new Color(0x1F6FEB), PRIMARY_SOFT = new Color(0xE4EEFF), WARNING = new Color(0xE67700),
        WARNING_SOFT = new Color(0xFFF1DB), ERROR = new Color(0xD92D20), SUCCESS = new Color(0x1E9E50),
        SUCCESS_SOFT = new Color(0xE3F4EA), BACKGROUND = new Color(0xF4F6FA), CARD = Color.WHITE, TEXT = new Color(0x1F2937),
        MUTED = new Color(0x6B7280), BORDER = new Color(0xD3D9E3), SEPARATOR = new Color(0xE8ECF2), NEUTRAL_SOFT = new Color(0xEDEFF3);

    private Palette() {}
}

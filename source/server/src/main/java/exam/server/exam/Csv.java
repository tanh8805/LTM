// Owner: Nguoi2

package exam.server.exam;

import java.util.ArrayList;
import java.util.List;

/**
 * Đọc và ghi CSV đơn giản (một bản ghi một dòng).
 * Hỗ trợ trường đặt trong dấu nháy kép, dấu phẩy bên trong trường, và "" là một dấu nháy kép.
 */
public class Csv {

    private Csv() {
    }

    /** Tách một dòng CSV thành các trường. Ném IllegalArgumentException nếu có dấu nháy mở mà không đóng. */
    public static List<String> parseLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean insideQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (insideQuotes) {
                if (c == '"') {
                    boolean escapedQuote = i + 1 < line.length() && line.charAt(i + 1) == '"';
                    if (escapedQuote) {
                        current.append('"');
                        i++;
                    } else {
                        insideQuotes = false;
                    }
                } else {
                    current.append(c);
                }
            } else if (c == '"') {
                insideQuotes = true;
            } else if (c == ',') {
                fields.add(current.toString().trim());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        if (insideQuotes) {
            throw new IllegalArgumentException("thiếu dấu nháy đóng");
        }
        fields.add(current.toString().trim());
        return fields;
    }

    /** Thêm dấu nháy quanh trường nếu cần (chứa dấu phẩy, nháy kép hoặc xuống dòng). */
    public static String escape(String field) {
        if (field == null) {
            return "";
        }
        boolean needsQuotes = field.contains(",") || field.contains("\"") || field.contains("\n") || field.contains("\r");
        if (!needsQuotes) {
            return field;
        }
        return "\"" + field.replace("\"", "\"\"") + "\"";
    }
}

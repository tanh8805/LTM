// Owner: Nguoi4

package exam.tools;

import java.util.HashMap;
import java.util.Map;

/** Đọc tham số dòng lệnh dạng "--ten giaTri" (hoặc "--co" không có giá trị). */
public class Args {

    private final Map<String, String> values = new HashMap<>();

    public Args(String[] args) {
        for (int i = 0; i < args.length; i++) {
            if (!args[i].startsWith("--")) {
                throw new IllegalArgumentException("Tham số không hợp lệ: " + args[i] + " (phải bắt đầu bằng --)");
            }
            String key = args[i].substring(2);
            boolean hasValue = i + 1 < args.length && !args[i + 1].startsWith("--");
            values.put(key, hasValue ? args[++i] : "true");
        }
    }

    public String get(String key, String defaultValue) {
        return values.getOrDefault(key, defaultValue);
    }

    public String require(String key) {
        String value = values.get(key);
        if (value == null) {
            throw new IllegalArgumentException("Thiếu tham số bắt buộc --" + key);
        }
        return value;
    }

    public int getInt(String key, int defaultValue) {
        String value = values.get(key);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("--" + key + " phải là số nguyên, đang là: " + value, e);
        }
    }

    public long getLong(String key, long defaultValue) {
        String value = values.get(key);
        return value == null ? defaultValue : Long.parseLong(value);
    }

    public double getDouble(String key, double defaultValue) {
        String value = values.get(key);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("--" + key + " phải là số, đang là: " + value, e);
        }
    }

    public boolean has(String key) {
        return values.containsKey(key);
    }
}

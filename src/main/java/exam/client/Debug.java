// Log gỡ lỗi của client, chỉ in khi chạy với -Dexam.debug=true (Người 2).
package exam.client;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

final class Debug {
    static final boolean ON = Boolean.getBoolean("exam.debug");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    private Debug() {}

    static void log(String msg) {
        if (ON) { System.err.println(LocalTime.now().format(TIME) + " [debug] " + msg); }
    }

    /** Chạy một lần đo: in thời gian chạy của phép quét {@code what}. */
    static <T> T timed(String what, java.util.function.Supplier<T> task) {
        long t0 = System.nanoTime();
        T result = task.get();
        if (ON) { log(what + ": " + (System.nanoTime() - t0) / 1_000_000 + " ms"); }
        return result;
    }
}

// Owner: Nguoi1

package exam.client.testutil;

import java.util.function.BooleanSupplier;

/** Chờ một điều kiện trở thành đúng (dùng cho các việc chạy ở thread khác). */
public final class Wait {

    private Wait() {
    }

    /** Trả về true nếu điều kiện đúng trong thời gian cho phép. */
    public static boolean until(BooleanSupplier condition, long timeoutMillis) {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return true;
            }
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return condition.getAsBoolean();
    }
}

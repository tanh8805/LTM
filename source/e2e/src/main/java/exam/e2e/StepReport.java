// Owner: Nguoi1

package exam.e2e;

import java.util.ArrayList;
import java.util.List;

/** Ghi lại kết quả từng bước kiểm tra, in ra ngay khi có kết quả. */
public class StepReport {

    private final List<String> failures = new ArrayList<>();
    private int total = 0;

    public void check(String step, boolean ok, String detail) {
        total++;
        if (ok) {
            System.out.println("✅ " + step);
        } else {
            System.out.println("❌ " + step + (detail == null || detail.isEmpty() ? "" : " -- " + detail));
            failures.add(step + (detail == null || detail.isEmpty() ? "" : " -- " + detail));
        }
    }

    public void check(String step, boolean ok) {
        check(step, ok, "");
    }

    public void info(String text) {
        System.out.println("   · " + text);
    }

    public boolean allPassed() {
        return failures.isEmpty();
    }

    public int getTotal() {
        return total;
    }

    public List<String> getFailures() {
        return failures;
    }

    public String summary() {
        return (total - failures.size()) + "/" + total + " bước đạt" + (failures.isEmpty() ? "" : ", THẤT BẠI: " + failures);
    }
}

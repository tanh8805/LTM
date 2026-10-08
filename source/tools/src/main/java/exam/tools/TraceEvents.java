// Owner: Nguoi4

package exam.tools;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Ground truth của một trace, lưu ở file RIÊNG (<tên trace>.events.json) bên cạnh file CSV số liệu.
 *
 * Mỗi sự kiện là một khoảng thời gian [startTime, endTime) do script thu thập ghi lại từ lúc workload THẬT sự
 * bắt đầu/kết thúc (không phải kế hoạch). Nhờ tách riêng nên CSV số liệu không chứa nhãn.
 *
 * category:
 *   anomaly : kịch bản bất thường hệ thống được tạo có kiểm soát (CPU, network, memory, process...). Đây KHÔNG phải gian lận.
 *   benign  : hoạt động ứng dụng bình thường. Không phải bất thường; dùng để đo báo nhầm.
 */
public class TraceEvents {

    public static class Event {
        public String id;
        /** Loại: CPU_LOAD, NETWORK_LOAD, MEMORY_LOAD, PROCESS_CHURN, MIXED_LOAD, FOCUS_LOSS, APPLICATION_ACTIVITY. */
        public String type;
        public String category;
        public long startTime;
        public long endTime;
        public Map<String, Object> params = new LinkedHashMap<>();
        public String description;
    }

    public String sessionId;
    public List<Event> events = new ArrayList<>();

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    /** Đường dẫn file events của một file trace: data/traces/cpu_01.csv -> data/traces/cpu_01.events.json */
    public static Path pathFor(Path traceCsv) {
        return sibling(traceCsv, ".events.json");
    }

    public static Path metaPathFor(Path traceCsv) {
        return sibling(traceCsv, ".meta.json");
    }

    private static Path sibling(Path traceCsv, String suffix) {
        String name = traceCsv.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        return traceCsv.resolveSibling(base + suffix);
    }

    public static TraceEvents read(Path file) throws IOException {
        TraceEvents events = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), TraceEvents.class);
        if (events == null || events.events == null) {
            throw new IOException("File events rỗng hoặc sai định dạng: " + file);
        }
        return events;
    }

    public void write(Path file) throws IOException {
        Files.writeString(file, GSON.toJson(this), StandardCharsets.UTF_8);
    }

    /**
     * Sự kiện áp dụng cho một mẫu đo tại thời điểm sampleTime. Mẫu là số liệu trung bình của khoảng
     * (sampleTime - intervalMs, sampleTime], nên dùng điểm GIỮA khoảng đó để quyết định mẫu thuộc sự kiện nào.
     * Nếu nhiều sự kiện cùng phủ điểm đó thì sự kiện "anomaly" được ưu tiên. Trả về null nếu không có.
     */
    public Event eventAt(long sampleTime, long intervalMs) {
        long middle = sampleTime - intervalMs / 2;
        Event found = null;
        for (Event event : events) {
            if (middle >= event.startTime && middle < event.endTime) {
                if (found == null || TraceRow.CATEGORY_ANOMALY.equals(event.category)) {
                    found = event;
                }
            }
        }
        return found;
    }
}

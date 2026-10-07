// Owner: Nguoi3

package exam.server.db;

import exam.common.model.Metrics;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/** Truy vấn bảng metrics_log: nhật ký số liệu giám sát dùng cho thống kê, baseline và thực nghiệm ML. */
public class MetricsLogDao {

    private final Database database;

    public MetricsLogDao(Database database) {
        this.database = database;
    }

    /** kind = "SUMMARY" hoặc "DETAIL"; rateMode = "NORMAL", "HIGH" hoặc "BASELINE". */
    public void insert(String machineId, long time, String kind, String rateMode, Metrics metrics) throws SQLException {
        String sql = """
            INSERT INTO metrics_log
                (machine_id, time, kind, rate_mode, kb_sent, kb_received, connection_count,
                 distinct_destination_count, cpu_percent, ram_percent, process_count, focus_lost_count)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, machineId);
            statement.setLong(2, time);
            statement.setString(3, kind);
            statement.setString(4, rateMode);
            statement.setDouble(5, metrics.kbSent);
            statement.setDouble(6, metrics.kbReceived);
            statement.setInt(7, metrics.connectionCount);
            statement.setInt(8, metrics.distinctDestinationCount);
            statement.setDouble(9, metrics.cpuPercent);
            statement.setDouble(10, metrics.ramPercent);
            statement.setInt(11, metrics.processCount);
            statement.setInt(12, metrics.focusLostCount);
            statement.executeUpdate();
        }
    }

    public int count(String machineId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM metrics_log WHERE machine_id = ?";

        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, machineId);

            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1);
            }
        }
    }

    /**
     * Xuất nhật ký ra CSV (cột giống file trace trong data/traces/ để ExperimentRunner đọc được).
     * machineId = null hoặc rỗng thì xuất mọi máy. onlyRateMode = null thì xuất mọi chế độ.
     */
    public String exportCsv(String machineId, String onlyRateMode) throws SQLException {
        String sql = """
            SELECT machine_id, time, kind, rate_mode, kb_sent, kb_received, connection_count,
                   distinct_destination_count, cpu_percent, ram_percent, process_count, focus_lost_count
            FROM metrics_log
            WHERE (? IS NULL OR machine_id = ?) AND (? IS NULL OR rate_mode = ?)
            ORDER BY id
            """;
        String machineFilter = (machineId == null || machineId.isBlank()) ? null : machineId;

        StringBuilder csv = new StringBuilder();
        csv.append("machineId,time,kind,rateMode,kbSent,kbReceived,connectionCount,distinctDestinationCount,")
                .append("cpuPercent,ramPercent,processCount,focusLostCount\n");

        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, machineFilter);
            statement.setString(2, machineFilter);
            statement.setString(3, onlyRateMode);
            statement.setString(4, onlyRateMode);

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    csv.append(resultSet.getString(1)).append(',')
                            .append(resultSet.getLong(2)).append(',')
                            .append(resultSet.getString(3)).append(',')
                            .append(resultSet.getString(4)).append(',')
                            .append(resultSet.getDouble(5)).append(',')
                            .append(resultSet.getDouble(6)).append(',')
                            .append(resultSet.getInt(7)).append(',')
                            .append(resultSet.getInt(8)).append(',')
                            .append(resultSet.getDouble(9)).append(',')
                            .append(resultSet.getDouble(10)).append(',')
                            .append(resultSet.getInt(11)).append(',')
                            .append(resultSet.getInt(12)).append('\n');
                }
            }
        }
        return csv.toString();
    }
}

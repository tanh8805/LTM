// Owner: Nguoi3

package exam.server.monitor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import exam.common.model.AlertLevel;
import exam.server.ServerConfig;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.Test;

class RoomStatsTest {

    private final ServerConfig config = ServerConfig.fromProperties(new Properties()); // warning 3x, critical 6x

    // ----- median -----

    @Test
    void medianOfOddCountIsTheMiddleValue() {
        assertEquals(3.0, RoomStats.median(List.of(5.0, 1.0, 3.0)));
    }

    @Test
    void medianOfEvenCountIsAverageOfTwoMiddleValues() {
        assertEquals(2.5, RoomStats.median(List.of(4.0, 1.0, 3.0, 2.0)));
    }

    @Test
    void medianOfOneValueAndEmptyList() {
        assertEquals(7.0, RoomStats.median(List.of(7.0)));
        assertEquals(0.0, RoomStats.median(List.of()));
    }

    @Test
    void medianIgnoresOutliers() {
        assertEquals(10.0, RoomStats.median(List.of(10.0, 10.0, 11.0, 9.0, 100000.0)));
    }

    // ----- MAD -----

    @Test
    void madIsMedianOfAbsoluteDeviations() {
        // median = 3, độ lệch tuyệt đối = [2, 1, 0, 1, 97] -> sắp: [0, 1, 1, 2, 97] -> MAD = 1
        List<Double> values = List.of(1.0, 2.0, 3.0, 4.0, 100.0);
        double median = RoomStats.median(values);

        assertEquals(3.0, median);
        assertEquals(1.0, RoomStats.mad(values, median));
    }

    @Test
    void madIsZeroWhenAllValuesAreEqual() {
        List<Double> values = List.of(5.0, 5.0, 5.0, 5.0);

        assertEquals(0.0, RoomStats.mad(values, 5.0));
    }

    // ----- ngưỡng lệch -----

    @Test
    void valueBelowWarningMultiplierIsNotFlagged() {
        assertNull(RoomStats.check(25.0, 10.0, 0.0, config)); // 2.5 lần < 3 lần
    }

    @Test
    void valueAtWarningMultiplierIsYellow() {
        RoomStats.Deviation deviation = RoomStats.check(40.0, 10.0, 1.0, config); // 4 lần

        assertNotNull(deviation);
        assertEquals(AlertLevel.YELLOW, deviation.level);
        assertEquals(4.0, deviation.ratio, 0.0001);
    }

    @Test
    void valueAtCriticalMultiplierIsRed() {
        RoomStats.Deviation deviation = RoomStats.check(100.0, 10.0, 0.0, config); // 10 lần

        assertNotNull(deviation);
        assertEquals(AlertLevel.RED, deviation.level);
    }

    @Test
    void valueBelowMedianIsNeverFlagged() {
        assertNull(RoomStats.check(1.0, 10.0, 0.0, config));
    }

    @Test
    void largeRoomSpreadPreventsFalseAlarm() {
        // Cả phòng vốn dao động mạnh (MAD lớn): 40 chỉ cách trung vị 30 mà MAD chuẩn hóa là 29.6 -> chưa đáng ngờ
        assertNull(RoomStats.check(40.0, 10.0, 20.0, config));
    }

    @Test
    void zeroMedianUsesMinimumMedianToAvoidDivisionByZero() {
        // Cả phòng đang rảnh (trung vị 0): dùng room.min.median = 1 làm mẫu số
        RoomStats.Deviation deviation = RoomStats.check(5.0, 0.0, 0.0, config);

        assertNotNull(deviation);
        assertEquals(5.0, deviation.ratio, 0.0001);
        assertEquals(AlertLevel.YELLOW, deviation.level);
    }

    @Test
    void thresholdsComeFromConfig() {
        Properties properties = new Properties();
        properties.setProperty("room.warning.multiplier", "2.0");
        properties.setProperty("room.critical.multiplier", "2.5");
        ServerConfig strict = ServerConfig.fromProperties(properties);

        assertEquals(AlertLevel.YELLOW, RoomStats.check(21.0, 10.0, 0.0, strict).level);
        assertEquals(AlertLevel.RED, RoomStats.check(26.0, 10.0, 0.0, strict).level);
    }
}

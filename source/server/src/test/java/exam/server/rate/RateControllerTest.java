// Owner: Nguoi1

package exam.server.rate;

import static org.junit.jupiter.api.Assertions.assertEquals;

import exam.common.model.RateMode;
import exam.common.protocol.SetRateMessage;
import exam.server.ServerConfig;
import exam.server.testutil.FakeMessageSender;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RateControllerTest {

    private FakeMessageSender sender;
    private RateControllerImpl rateController;

    @BeforeEach
    void setUp() {
        Properties properties = new Properties();
        properties.setProperty("rate.high.quiet.seconds", "1"); // rút từ 60 giây xuống 1 giây cho test
        sender = new FakeMessageSender();
        rateController = new RateControllerImpl(ServerConfig.fromProperties(properties), sender);
    }

    private SetRateMessage lastRateMessage() {
        List<SetRateMessage> messages = sender.machineMessagesOfType(SetRateMessage.class);
        return messages.get(messages.size() - 1);
    }

    @Test
    void defaultModeIsNormalWithTenSecondInterval() {
        rateController.onMachineOnline("SV001");

        assertEquals(RateMode.NORMAL, rateController.getMode("SV001"));
        assertEquals(RateMode.NORMAL, lastRateMessage().mode);
        assertEquals(10_000, lastRateMessage().intervalMs);
    }

    @Test
    void raiseToHighSendsSetRateWithTwoSecondInterval() {
        rateController.raiseToHigh("SV001");

        assertEquals(RateMode.HIGH, rateController.getMode("SV001"));
        assertEquals(RateMode.HIGH, lastRateMessage().mode);
        assertEquals(2_000, lastRateMessage().intervalMs);
    }

    @Test
    void raisingTwiceSendsOnlyOneSetRate() {
        rateController.raiseToHigh("SV001");
        rateController.raiseToHigh("SV001");

        assertEquals(1, sender.machineMessagesOfType(SetRateMessage.class).size());
    }

    @Test
    void highReturnsToNormalAfterQuietPeriod() throws Exception {
        rateController.raiseToHigh("SV001");

        rateController.returnCalmMachinesToNormal();
        assertEquals(RateMode.HIGH, rateController.getMode("SV001"), "chưa đủ thời gian yên");

        Thread.sleep(1100);
        rateController.returnCalmMachinesToNormal();

        assertEquals(RateMode.NORMAL, rateController.getMode("SV001"));
        assertEquals(RateMode.NORMAL, lastRateMessage().mode);
    }

    @Test
    void newSuspicionRestartsTheQuietClock() throws Exception {
        rateController.raiseToHigh("SV001");
        Thread.sleep(700);
        rateController.raiseToHigh("SV001"); // bị nghi ngờ lại: đồng hồ yên bắt đầu lại
        Thread.sleep(700);

        rateController.returnCalmMachinesToNormal();

        assertEquals(RateMode.HIGH, rateController.getMode("SV001"));
    }

    @Test
    void baselineAppliesToAllMachinesWithOneSecondInterval() {
        rateController.onMachineOnline("SV001");
        rateController.onMachineOnline("SV002");

        rateController.startBaselineForAll();

        assertEquals(RateMode.BASELINE, rateController.getMode("SV001"));
        assertEquals(RateMode.BASELINE, rateController.getMode("SV002"));
        assertEquals(1_000, lastRateMessage().intervalMs);
    }

    @Test
    void baselineWinsOverHighAndNewMachinesJoinBaseline() {
        rateController.onMachineOnline("SV001");
        rateController.startBaselineForAll();

        rateController.raiseToHigh("SV001");
        rateController.onMachineOnline("SV009"); // máy vào sau khi BASELINE đã bật

        assertEquals(RateMode.BASELINE, rateController.getMode("SV001"));
        assertEquals(RateMode.BASELINE, rateController.getMode("SV009"));
    }

    @Test
    void stoppingBaselineReturnsMachinesToNormal() {
        rateController.onMachineOnline("SV001");
        rateController.startBaselineForAll();

        rateController.stopBaselineForAll();

        assertEquals(RateMode.NORMAL, rateController.getMode("SV001"));
        assertEquals(10_000, lastRateMessage().intervalMs);
    }

    @Test
    void intervalsComeFromConfig() {
        Properties properties = new Properties();
        properties.setProperty("rate.high.interval.ms", "500");
        FakeMessageSender customSender = new FakeMessageSender();
        RateControllerImpl custom = new RateControllerImpl(ServerConfig.fromProperties(properties), customSender);

        custom.raiseToHigh("SV001");

        assertEquals(500, customSender.machineMessagesOfType(SetRateMessage.class).get(0).intervalMs);
    }
}

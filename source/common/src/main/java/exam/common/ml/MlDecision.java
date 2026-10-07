// Owner: Nguoi4

package exam.common.ml;

import exam.common.model.MlMode;

/** Ghép kết quả Isolation Forest (client) và Chronos (server) theo ml.mode. */
public class MlDecision {

    private MlDecision() {
    }

    /**
     * NONE     : luôn false
     * IF       : chỉ xét Isolation Forest
     * CHRONOS  : chỉ xét Chronos
     * BOTH_OR  : Isolation Forest HOẶC Chronos
     * BOTH_AND : Isolation Forest VÀ Chronos
     */
    public static boolean isSuspicious(MlMode mode, boolean isolationForestSuspicious, boolean chronosSuspicious) {
        switch (mode) {
            case IF:
                return isolationForestSuspicious;
            case CHRONOS:
                return chronosSuspicious;
            case BOTH_OR:
                return isolationForestSuspicious || chronosSuspicious;
            case BOTH_AND:
                return isolationForestSuspicious && chronosSuspicious;
            case NONE:
            default:
                return false;
        }
    }

    public static boolean usesIsolationForest(MlMode mode) {
        return mode == MlMode.IF || mode == MlMode.BOTH_OR || mode == MlMode.BOTH_AND;
    }

    public static boolean usesChronos(MlMode mode) {
        return mode == MlMode.CHRONOS || mode == MlMode.BOTH_OR || mode == MlMode.BOTH_AND;
    }
}

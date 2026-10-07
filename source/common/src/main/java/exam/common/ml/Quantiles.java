// Owner: Nguoi4

package exam.common.ml;

/** Dự báo của Chronos cho một metric ở bước kế tiếp: các phân vị 0.1, 0.5, 0.9. */
public class Quantiles {
    public double q10;
    public double q50;
    public double q90;

    public Quantiles() {
    }

    public Quantiles(double q10, double q50, double q90) {
        this.q10 = q10;
        this.q50 = q50;
        this.q90 = q90;
    }
}

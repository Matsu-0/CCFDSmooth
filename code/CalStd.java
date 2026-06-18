import java.util.ArrayList;

public class CalStd {
    private double[] std;
    private final int columnCnt;
    private final ArrayList<ArrayList<Double>> origin_td;

    public CalStd(int columnCnt, ArrayList<ArrayList<Double>> origin_td) {
        this.columnCnt = columnCnt;
        this.std = new double[this.columnCnt];
        this.origin_td = origin_td;
        for (int i = 0; i < this.columnCnt; i++) {
            std[i] = Math.sqrt(this.varianceImperative(getColumn(i)));
        }
    }

    private double varianceImperative(double[] value) {
        double average = 0.0;
        int cnt = 0;
        for (double p : value) {
            if (!Double.isNaN(p)){
                cnt += 1;
                average += p;
            }
        }
        if (cnt == 0) {
            return 0d;
        }
        average /= cnt;

        double variance = 0.0;
        for (double p : value) {
            if (!Double.isNaN(p)) {
                variance += (p - average) * (p - average);
            }
        }
        return variance / cnt;
    }

    private double[] getColumn(int pos) {
        double[] column = new double[this.origin_td.size()];
        for (int i = 0; i < this.origin_td.size(); i++) {
            column[i] = this.origin_td.get(i).get(pos);
        }
        return column;
    }

    public double[] getStd() {
        return std;
    }
}

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;

public class Analysis {
    private int columnCnt;
    private ArrayList<ArrayList<Double>> td = new ArrayList<>();
    private ArrayList<ArrayList<Double>> constantPattern = new ArrayList<>();
    private ArrayList<ArrayList<Double>> td_cleaned = new ArrayList<>();
    private ArrayList<ArrayList<Double>> test_td = new ArrayList<>();
    private double MAE;
    private double RMSE;
    /** RMSE after min–max scaling each column to [0, 1] using clean td range. */
    private double RMSE01;
    private double[] tolerance;
    private double tolerance_rate;
    private double[] std;
    /** Per-column min/max from clean td (for [0,1] normalization). */
    private double[] colMin;
    private double[] colMax;
    /** Number of repairs: time points where std-normalized distance between noisy input and repaired output > 0 (n-cost; divided by 1e4 for output). */
    private int repairCount;
    /** Mean repair distance: sum(dist(test, cleaned)) / n (n-cost-d). */
    private double repairDistanceMean;
    /** Dirty time points in noisy input (same across methods; reference only). */
    private int dirtyInputCount;

    private static final double DIST_EPS = 1e-6;

    public Analysis(int columnCnt, ArrayList<ArrayList<Double>> td, ArrayList<ArrayList<Double>> constantPattern,
            ArrayList<ArrayList<Double>> test_td, ArrayList<ArrayList<Double>> td_cleaned,
            double tolerance_rate, double[] std) {
        this.columnCnt = columnCnt;
        this.td = td;
        this.test_td = test_td;
        this.constantPattern = constantPattern;
        this.td_cleaned = td_cleaned;
        this.tolerance = new double[columnCnt];
        this.tolerance_rate = tolerance_rate;
        this.std = std;
        this.MAE = 0d;
        this.RMSE = 0d;
        this.RMSE01 = 0d;
        this.calTolerance();
        this.computeColMinMax();
        this.countRepairMetrics();
        this.analysis();
    }

    public String getMAE() {
        return String.format("%.3f", MAE);
    }

    public String getRMSE() {
        return String.format("%.3f", RMSE);
    }

    public String getRMSE01() {
        return String.format("%.3f", RMSE01);
    }

    public double getRMSE01Value() {
        return RMSE01;
    }

    public int getRepairCount() {
        return repairCount;
    }

    /** Matches figure/*-n-cost.dat: repair count / 10^4. */
    public String getRepairCountTimes1e4() {
        return String.format("%.4f", repairCount / 10000.0);
    }

    public String getRepairDistanceMean() {
        return String.format("%.4f", repairDistanceMean);
    }

    public int getDirtyInputCount() {
        return dirtyInputCount;
    }

    /**
     * Same as CCFDRepair.get_tm_distance / KDTreeUtil: Euclidean distance after std normalization.
     */
    public double getTmDistance(ArrayList<Double> a, ArrayList<Double> b) {
        double sum = 0d;
        for (int j = 0; j < columnCnt; j++) {
            double x = a.get(j);
            double y = b.get(j);
            if (Double.isNaN(x) || Double.isNaN(y)) {
                continue;
            }
            double temp = (x - y) / std[j];
            sum += temp * temp;
        }
        return Math.sqrt(sum);
    }

    /**
     * n-cost: time points with a repair; n-cost-d: mean repair distance over the full series (includes unchanged points with distance 0).
     */
    private void countRepairMetrics() {
        int n = Math.min(td.size(), Math.min(test_td.size(), td_cleaned.size()));
        if (n == 0) {
            return;
        }
        double distanceSum = 0d;
        for (int i = 0; i < n; i++) {
            ArrayList<Double> test = test_td.get(i);
            ArrayList<Double> cleaned = td_cleaned.get(i);
            if (checkIfDirty(test)) {
                dirtyInputCount++;
            }
            double dist = getTmDistance(test, cleaned);
            distanceSum += dist;
            if (dist > DIST_EPS) {
                repairCount++;
            }
        }
        repairDistanceMean = distanceSum / n;
    }

    public void analysis() {
        ArrayList<ArrayList<Double>> arrayLists = this.td;
        int consistentCnt = 0;
        for (int i = 0; i < arrayLists.size(); i++) {
            ArrayList<Double> o_tuple = arrayLists.get(i);
            if (!checkIfDirty(o_tuple)) {
                consistentCnt++;
                ArrayList<Double> r_tuple = this.td_cleaned.get(i);
                for (int j = 0; j < columnCnt; j++) {
                    this.MAE += Math.abs(o_tuple.get(j) - r_tuple.get(j)) / this.std[j];
                    this.RMSE += Math.pow((o_tuple.get(j) - r_tuple.get(j)) / this.std[j], 2);
                    double no = normalize01(o_tuple.get(j), j);
                    double nr = normalize01(r_tuple.get(j), j);
                    this.RMSE01 += Math.pow(no - nr, 2);
                }
            }
        }
        int columnCnt = consistentCnt * this.columnCnt;
        this.MAE = this.MAE / columnCnt;
        this.RMSE = Math.sqrt(this.RMSE / columnCnt);
        this.RMSE01 = Math.sqrt(this.RMSE01 / columnCnt);
    }

    /** Min–max per column from clean td → [0, 1]. Constant columns map to 0. */
    private void computeColMinMax() {
        this.colMin = new double[columnCnt];
        this.colMax = new double[columnCnt];
        for (int j = 0; j < columnCnt; j++) {
            double min = Double.POSITIVE_INFINITY;
            double max = Double.NEGATIVE_INFINITY;
            for (ArrayList<Double> row : this.td) {
                double v = row.get(j);
                if (!Double.isNaN(v)) {
                    min = Math.min(min, v);
                    max = Math.max(max, v);
                }
            }
            if (min == Double.POSITIVE_INFINITY) {
                min = 0d;
                max = 1d;
            }
            this.colMin[j] = min;
            this.colMax[j] = max;
        }
    }

    private double normalize01(double v, int col) {
        if (Double.isNaN(v)) {
            return 0d;
        }
        double span = this.colMax[col] - this.colMin[col];
        if (span <= 1e-12) {
            return 0d;
        }
        return (v - this.colMin[col]) / span;
    }

    public void calTolerance() {
        this.tolerance = new double[columnCnt];
        for (int i = 0; i < this.columnCnt; i++) {
            this.tolerance[i] = this.std[i] * tolerance_rate;
        }
    }

    public boolean checkIfComplete(ArrayList<Double> t_tuple) {
        for (Double value : t_tuple) {
            if (Double.isNaN(value)) {
                return false;
            }
        }
        return true;
    }

    public boolean checkIfConsistent(ArrayList<Double> t_tuple) {
        for (ArrayList<Double> constantPatternTuple : this.constantPattern) {
            boolean isConsistent = true;
            for (int k = 0; k < t_tuple.size(); k++) {
                if (Math.abs(t_tuple.get(k) - constantPatternTuple.get(k)) > this.tolerance[k]) {
                    isConsistent = false;
                    break;
                }
            }
            if (isConsistent) {
                return true;
            }
        }
        return false;
    }

    public boolean checkIfDirty(ArrayList<Double> t_tuple) {
        return !checkIfComplete(t_tuple) || !checkIfConsistent(t_tuple);
    }

    private double varianceImperative(double[] value) {
        double average = 0.0;
        int cnt = 0;
        for (double p : value) {
            if (!Double.isNaN(p)) {
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
        double[] column = new double[this.td.size()];
        for (int i = 0; i < this.td.size(); i++) {
            column[i] = this.td.get(i).get(pos);
        }
        return column;
    }

    public void writeRepairResultToFile(String targetFileName) {
        File writeFile = new File(targetFileName);
        File parent = writeFile.getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }
        try {
            BufferedWriter writeText = new BufferedWriter(new FileWriter(writeFile));
            for (int j = 0; j < this.td_cleaned.size(); j++) {
                writeText.newLine();
                ArrayList<Double> tuple = this.td_cleaned.get(j);
                writeText.write(j + ",");
                for (int i = 0; i < columnCnt - 1; i++) {
                    if (!Double.isNaN(tuple.get(i))) {
                        writeText.write(String.valueOf(tuple.get(i)));
                    }
                    writeText.write(",");
                }
                if (!Double.isNaN(tuple.get(columnCnt - 1))) {
                    writeText.write(String.valueOf(tuple.get(columnCnt - 1)));
                }
            }
            writeText.flush();
            writeText.close();
        } catch (IOException e) {
            System.out.println("Error writing " + targetFileName + ": " + e.getMessage());
        }
    }
}

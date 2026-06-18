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
    private double[] tolerance;
    private double tolerance_rate;
    private double[] std;
    /** 修复次数：加噪输入与修复输出之间标准化距离 > 0 的时点数（对应 n-cost，再除以 1e4 输出） */
    private int repairCount;
    /** 平均修复距离：sum(dist(test, cleaned)) / n（对应 n-cost-d） */
    private double repairDistanceMean;
    /** 加噪输入中被判定为脏的时点数（各方法相同，仅作参考） */
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
        this.calTolerance();
        this.countRepairMetrics();
        this.analysis();
    }

    public String getMAE() {
        return String.format("%.3f", MAE);
    }

    public String getRMSE() {
        return String.format("%.3f", RMSE);
    }

    public int getRepairCount() {
        return repairCount;
    }

    /** 与 figure/*-n-cost.dat 一致：修复次数 / 10^4 */
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
     * 与 CCFDRepair.get_tm_distance / KDTreeUtil 相同：按 std 标准化后的欧氏距离。
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
     * n-cost：发生修复的时点数；n-cost-d：全序列平均修复距离（含未改动的 0 距离点）。
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
                }
            }
        }
        int columnCnt = consistentCnt * this.columnCnt;
        this.MAE = this.MAE / columnCnt;
        this.RMSE = Math.sqrt(this.RMSE / columnCnt);
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

package Algorithm;

import java.util.ArrayList;

public class EditingRuleRepair {
    private final int columnCnt;
    private final ArrayList<Long> td_time;
    private final ArrayList<ArrayList<Double>> td;
    private final ArrayList<ArrayList<Double>> constantPattern;
    private final ArrayList<ArrayList<Double>> td_cleaned = new ArrayList<>();
    private double[] tolerance;

    public EditingRuleRepair(ArrayList<Long> td_time, ArrayList<ArrayList<Double>> td, ArrayList<ArrayList<Double>> constantPattern, int columnCnt) {
        this.td_time = td_time;
        this.td = td;
        this.constantPattern = constantPattern;
        this.columnCnt = columnCnt;
        this.preprocess();
        long startTime = System.currentTimeMillis();
        this.repair();
        long endTime = System.currentTimeMillis();
        System.out.println("Time cost:" + (endTime - startTime) + "ms");
    }

    public void preprocess() {
        calTolerance();
    }

    public void calTolerance() {
        this.tolerance = new double[columnCnt];
        for (int i = 0; i < columnCnt; i++) {
            this.tolerance[i] = Math.abs(this.constantPattern.get(0).get(i) - this.constantPattern.get(1).get(i));
        }
    }

    public boolean checkIfComplete(ArrayList<Double> t_tuple) {
        for (Double value : t_tuple) {
            if (value == null) {
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

    public boolean editRule(
            ArrayList<Double> t_tuple, ArrayList<Double> constantPatternTuple, int diff) {
        int null_cnt = 0;
        for (Double v : t_tuple) {
            if (Double.isNaN(v)) {
                null_cnt++;
            }
        }

        int same_cnt = 0;
        for (int i = 0; i < columnCnt; i++) {
            if (!Double.isNaN(t_tuple.get(i)) && Math.abs(t_tuple.get(i) - constantPatternTuple.get(i)) < this.tolerance[i]) {
                same_cnt ++;
            }
        }
        return null_cnt + same_cnt >= columnCnt - diff;
    }

    public boolean repairWithEditRule(ArrayList<Double> t_tuple, int diff) {
        for (ArrayList<Double> constantPatternTuple : this.constantPattern) {
            if (this.editRule(t_tuple, constantPatternTuple, diff)) {
                this.td_cleaned.add(constantPatternTuple);
                return true;
            }
        }
        return false;
    }

    public ArrayList<Double> fillSingleTuple(ArrayList<Double> tuple) {
        ArrayList<Double> last_tuple = this.td_cleaned.get(this.td_cleaned.size() - 1);
        for (int i = 0; i < columnCnt; i++) {
            if (Double.isNaN(tuple.get(i))) {
                tuple.set(i, last_tuple.get(i));
            }
        }
        return tuple;
    }

    public void repair() {
        for (ArrayList<Double> t_tuple : this.td) {
            if (checkIfDirty(t_tuple)) {
                boolean repaired = false;
                for (int diff = 1; diff < columnCnt; diff++) {
                    repaired = repairWithEditRule(t_tuple, diff);
                    if (repaired) {
                        break;
                    }
                }
                if (!repaired) {
                    this.td_cleaned.add(fillSingleTuple(t_tuple));
                }
            } else {
                this.td_cleaned.add(t_tuple);
            }
        }
    }

    public ArrayList<ArrayList<Double>> getTd_cleaned() {
        return td_cleaned;
    }
}

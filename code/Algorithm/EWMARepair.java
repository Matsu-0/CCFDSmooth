package Algorithm;

import java.util.ArrayList;

public class EWMARepair {
    private final int columnCnt;
    private final ArrayList<Long> td_time;
    private final ArrayList<ArrayList<Double>> td;
    private final ArrayList<ArrayList<Double>> td_cleaned = new ArrayList<>();
    private double beta = 0.2;
    private double last_ewma = 0d;

    public EWMARepair(ArrayList<Long> td_time, ArrayList<ArrayList<Double>> td, int columnCnt) {
        this.td_time = td_time;
        this.td = td;
        this.columnCnt = columnCnt;
        this.fillNullValue();
        long startTime = System.currentTimeMillis();
        this.repair();
        long endTime = System.currentTimeMillis();
        System.out.println("Time cost:" + (endTime - startTime) + "ms");
    }

    public double[] getColumn(int pos) {
        double[] column = new double[this.td.size()];
        for (int i = 0; i < this.td.size(); i++) {
            column[i] = this.td.get(i).get(pos);
        }
        return column;
    }

    public void fillNullValue() {
        for (int i = 0; i < columnCnt; i++) {
            double temp = this.td.get(0).get(i);
            for (ArrayList<Double> arrayList : this.td) {
                if (Double.isNaN(arrayList.get(i))) {
                    arrayList.set(i, temp);
                } else {
                    temp = arrayList.get(i);
                }
            }
        }
    }

    public ArrayList<ArrayList<Double>> getTd_cleaned() {
        return td_cleaned;
    }

    public void repair() {
        double[][] temp_cleaned = new double[this.columnCnt][];
        for (int c = 0; c < columnCnt; c++) {
            temp_cleaned[c] = new double[this.td.size()];
            double[] column = getColumn(c);
            temp_cleaned[c][0] = column[0];
            last_ewma = column[0];
            for (int i = 1; i < column.length; i++) {
                double new_value = this.beta * last_ewma + (1 - beta) * column[i];
                temp_cleaned[c][i] = new_value;
                last_ewma = new_value;
            }
        }
        for (int i = 0; i < this.td.size(); i++) {
            ArrayList<Double> new_tuple = new ArrayList<>();
            for (int j = 0; j < this.columnCnt; j++) {
                new_tuple.add(temp_cleaned[j][i]);
            }
            this.td_cleaned.add(new_tuple);
        }
    }
}

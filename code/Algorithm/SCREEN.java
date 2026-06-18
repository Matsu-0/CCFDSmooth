package Algorithm;

import Algorithm.util.ScreenUtil;

import java.util.ArrayList;

public class SCREEN {
    private final int columnCnt;
    private final ArrayList<Long> td_time;
    private final ArrayList<ArrayList<Double>> td;
    private final ArrayList<ArrayList<Double>> td_cleaned = new ArrayList<>();
    private double[] minSpeeds;
    private double[] maxSpeeds;
    private ScreenUtil screenUtil;

    public SCREEN(ArrayList<Long> td_time, ArrayList<ArrayList<Double>> td, int columnCnt) throws Exception {
        this.td_time = td_time;
        this.td = td;
        this.columnCnt = columnCnt;
        this.fillNullValue();
        long startTime = System.currentTimeMillis();
        this.repair();
        long endTime = System.currentTimeMillis();
        System.out.println("Time cost:" + (endTime - startTime) + "ms");
    }

    public SCREEN(ArrayList<Long> td_time, ArrayList<ArrayList<Double>> td, int columnCnt, double[] minSpeed, double[] maxSpeed) {
        this.td_time = td_time;
        this.td = td;
        this.columnCnt = columnCnt;
        this.minSpeeds = minSpeed;
        this.maxSpeeds = maxSpeed;
    }

    public ArrayList<ArrayList<Double>> getTd_cleaned() {
        return td_cleaned;
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

    public void repair() throws Exception {
        long[] times = this.arrayListToListLong(this.td_time);
        double[][] temp_cleaned = new double[this.columnCnt][];
        for (int i = 0; i < columnCnt; i++) {
            double[] column = getColumn(i);
            screenUtil = new ScreenUtil(times, column);
            screenUtil.repair();
            temp_cleaned[i] = screenUtil.getRepaired();
        }
        for (int i = 0; i < this.td.size(); i++) {
            ArrayList<Double> new_tuple = new ArrayList<>();
            for (int j = 0; j < this.columnCnt; j++) {
                new_tuple.add(temp_cleaned[j][i]);
            }
            this.td_cleaned.add(new_tuple);
        }
    }

    public double[] arrayListToListDouble(ArrayList<Double> arrayList) {
        double[] doubles = new double[arrayList.size()];
        for (int i = 0; i < arrayList.size(); i++) {
            doubles[i] = arrayList.get(i);
        }
        return doubles;
    }

    public long[] arrayListToListLong(ArrayList<Long> arrayList) {
        long[] longs = new long[arrayList.size()];
        for (int i = 0; i < arrayList.size(); i++) {
            longs[i] = arrayList.get(i);
        }
        return longs;
    }

    public ArrayList<Double> listToArrayList(double[] list) {
        ArrayList<Double> arrayList = new ArrayList<>(list.length);
        for (int i = 0; i < list.length; i++) {
            arrayList.set(i, list[i]);
        }
        return arrayList;
    }
}

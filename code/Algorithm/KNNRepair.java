package Algorithm;

import Algorithm.util.KDTreeUtil;

import java.util.ArrayList;

public class KNNRepair {
    private final int columnCnt;
    private final ArrayList<Long> td_time;
    private final ArrayList<ArrayList<Double>> td;
    private final ArrayList<ArrayList<Double>> constantPattern;
    private final ArrayList<ArrayList<Double>> td_cleaned = new ArrayList<>();
    private final double[] std;
    private KDTreeUtil kdTreeUtil;

    public KNNRepair(ArrayList<Long> td_time, ArrayList<ArrayList<Double>> td, ArrayList<ArrayList<Double>> constantPattern, int columnCnt, double[] std) {
        this.td_time = td_time;
        this.td = td;
        this.constantPattern = constantPattern;
        this.columnCnt = columnCnt;
        this.std = std;
        this.preprocess();
        long startTime = System.currentTimeMillis();
        this.repair();
        long endTime = System.currentTimeMillis();
        System.out.println("Time cost:" + (endTime - startTime) + "ms");
    }

    public void preprocess() {
        this.fillNullValue();
        this.buildKDTree();
    }

    public ArrayList<Long> getTime() {
        return td_time;
    }

    public ArrayList<ArrayList<Double>> getTdCleaned() {
        return td_cleaned;
    }

    public ArrayList<ArrayList<Double>> getTd_cleaned() {
        return td_cleaned;
    }

    public void buildKDTree() {
        this.kdTreeUtil = KDTreeUtil.build(constantPattern, this.columnCnt);
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

    public void repair() {
        for (ArrayList<Double> tuple : this.td) {
            ArrayList<Double> rt = this.kdTreeUtil.query(tuple, std);
            for (int j = 0; j < rt.size(); j++) {
                rt.set(j, rt.get(j));
            }
            td_cleaned.add(rt);
        }
    }
}

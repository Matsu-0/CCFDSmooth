package Algorithm;

import Algorithm.util.KDTreeUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Objects;

public class CCFDRepair {
    private final ArrayList<ArrayList<Double>> td;
    private final ArrayList<ArrayList<Double>> td_cleaned = new ArrayList<>();
    private final ArrayList<ArrayList<Double>> constantPattern;
    private final ArrayList<Long> td_time;
    private final int columnCnt;
    private long omega;
    private Double eta;
    private Double tau;
    private int k;
    private final double[] std;
    private KDTreeUtil kdTreeUtil;

    private final double eta_rate = 0.6826; // 0.6826 0.9544 0.9974
    private final int omega_rate = 10; // 10
    private final boolean dynamic_eta = false;
    private final int eta_window_length = 100000;

    public CCFDRepair(ArrayList<Long> td_time, ArrayList<ArrayList<Double>> td, ArrayList<ArrayList<Double>> constantPattern,
            int columnCnt, long omega, double eta, int k, double[] std) throws Exception {
        this.td_time = td_time;
        this.td = td;
        this.constantPattern = constantPattern;
        this.columnCnt = columnCnt;
        this.std = std;
        this.preprocess();
        this.omega = omega;
        this.eta = eta;
        this.k = k;
        long startTime = System.currentTimeMillis();
        this.repair();
        long endTime = System.currentTimeMillis();
        System.out.println("Time cost:" + (endTime - startTime) + "ms");
    }

    public CCFDRepair(ArrayList<Long> td_time, ArrayList<ArrayList<Double>> td, ArrayList<ArrayList<Double>> constantPattern,
            int columnCnt, double[] std, double tau) throws Exception {
        this.td_time = td_time;
        this.td = td;
        this.constantPattern = constantPattern;
        this.columnCnt = columnCnt;
        this.std = std;
        this.preprocess();
        set_omega();
        set_eta();
        set_k();
        System.out.println(this.omega);
        System.out.println(this.eta);
        System.out.println(this.k);
        this.tau = tau;
        long startTime = System.currentTimeMillis();
        this.repair();
        long endTime = System.currentTimeMillis();
        System.out.println("Time cost:" + (endTime - startTime) + "ms");
    }

    void set_omega() {
        ArrayList<Long> intervals = getIntervals();
        Collections.sort(intervals);
        long interval = intervals.get(intervals.size() / 2);
        this.omega = interval * this.omega_rate;
    }

    void set_eta() {
        ArrayList<Double> distance_list = new ArrayList<>();
        for (int i = 1; i < this.td.size(); i++) {
            for (int l = i - 1; l >= 0; l--) {
                if (this.td_time.get(i) <= this.td_time.get(l) + omega) {
                    distance_list.add(get_tm_distance(this.td.get(i), this.td.get(l)));
                } else
                    break;
            }
        }
        Collections.sort(distance_list);
        eta = distance_list.get((int) (distance_list.size() * this.eta_rate));
    }

    void set_k() {
        this.k = -1;
        for (int temp_k = 2; temp_k <= 5; temp_k++) {
            ArrayList<Double> distance_list = new ArrayList<>();
            for (ArrayList<Double> tuple : this.td) {
                ArrayList<ArrayList<Double>> neighbors = this.kdTreeUtil.queryKNN(tuple, temp_k, std);
                for (ArrayList<Double> neighbor : neighbors) {
                    distance_list.add(get_tm_distance(tuple, neighbor));
                }
            }
            Collections.sort(distance_list);
            if (distance_list.get((int) (distance_list.size() * 0.9)) > eta) {
                k = temp_k;
                break;
            }
        }
        if (k == -1) {
            k = 5;
        }
    }

    public ArrayList<Long> getIntervals() {
        ArrayList<Long> intervals = new ArrayList<>();
        for (int i = 1; i < this.td_time.size(); i++) {
            intervals.add(this.td_time.get(i) - this.td_time.get(i - 1));
        }
        return intervals;
    }

    public void preprocess() {
        this.fillNullValue();
        this.buildKDTree();
    }

    public void buildKDTree() {
        this.kdTreeUtil = KDTreeUtil.build(constantPattern, this.columnCnt);
    }

    public double get_tm_distance(ArrayList<Double> t_tuple, ArrayList<Double> constantPatternTuple) {
        double distance = 0d;
        for (int pos = 0; pos < columnCnt; pos++) {
            double temp = t_tuple.get(pos) - constantPatternTuple.get(pos);
            temp = temp / std[pos];
            distance += temp * temp;
        }
        distance = Math.sqrt(distance);
        return distance;
    }

    public ArrayList<Integer> cal_W(ArrayList<Long> td_time, int i) {
        ArrayList<Integer> W_i = new ArrayList<>();
        for (int l = i - 1; l >= 0; l--) {
            if (td_time.get(i) <= td_time.get(l) + omega) {
                W_i.add(l);
            } else
                break;
        }
        return W_i;
    }

    public ArrayList<ArrayList<Double>> cal_C(ArrayList<Double> tuple, ArrayList<ArrayList<Double>> td_cleaned,
            ArrayList<Integer> W_i) {
        ArrayList<ArrayList<Double>> C_i = new ArrayList<>();
        ArrayList<Double> nearest = this.kdTreeUtil.query(tuple, std);
        if (W_i.size() == 0) {
            // C_i.add(this.kdTreeUtil.query(tuple, std));
            C_i.add(nearest);
        } else {
            C_i.addAll(this.kdTreeUtil.queryKNN(tuple, k, std));
            for (Integer integer : W_i) {
                if(integer < td_cleaned.size()){
                C_i.addAll(this.kdTreeUtil.queryKNN(td_cleaned.get(integer), k, std));}
            }
        }
        double dist = this.kdTreeUtil.get_tm_distance(tuple, nearest, std);
        if (dist <= this.tau && !C_i.contains(tuple)) {
            C_i.add(tuple);
        }

        return C_i;
    }

    public ArrayList<ArrayList<Double>> repairColdWindow(ArrayList<ArrayList<Double>> cold_window,
            ArrayList<Long> cold_window_time) {
        ArrayList<ArrayList<Double>> results = new ArrayList<>();
        int length = cold_window.size();
        double min_cost = Double.MAX_VALUE;
        for (int i = 0; i < length; i++) {
            ArrayList<Long> right_window_time = new ArrayList<>();
            ArrayList<ArrayList<Double>> right_window = new ArrayList<>();
            ArrayList<ArrayList<Double>> right_window_cleaned = new ArrayList<>();
            for (int j = i; j < length; j++) {
                right_window.add(cold_window.get(j));
                right_window_time.add(cold_window_time.get(j));
            }
            double right_cost = ccfd_repair(right_window_time, right_window, right_window_cleaned, "window");

            ArrayList<Long> left_window_time = new ArrayList<>();
            ArrayList<ArrayList<Double>> left_window = new ArrayList<>();
            ArrayList<ArrayList<Double>> left_window_cleaned = new ArrayList<>();
            for (int j = i; j >= 0; j--) {
                left_window.add(cold_window.get(j));
                left_window_time.add(cold_window_time.get(j) * (-1));
            }
            double left_cost = ccfd_repair(left_window_time, left_window, left_window_cleaned, "window");

            if (min_cost > right_cost + left_cost) {
                min_cost = right_cost + left_cost;
                results.clear();
                for (int j = left_window_cleaned.size() - 1; j >= 0; j--) {
                    results.add(left_window_cleaned.get(j));
                }
                for (int j = 1; j < right_window_cleaned.size(); j++) {
                    results.add(right_window_cleaned.get(j));
                }
            }
        }
        return results;
    }

    void update_eta(int start, int end) {
        ArrayList<Double> distance_list = new ArrayList<>();
        for (int i = start; i < end; i++) {
            for (int l = i - 1; l >= 0; l--) {
                if (this.td_time.get(i) <= this.td_time.get(l) + omega) {
                    distance_list.add(get_tm_distance(this.td.get(i), this.td.get(l)));
                } else
                    break;
            }
        }
        Collections.sort(distance_list);
        eta = distance_list.get((int) (distance_list.size() * this.eta_rate));
    }

    public double ccfd_repair(ArrayList<Long> td_time, ArrayList<ArrayList<Double>> td,
            ArrayList<ArrayList<Double>> td_cleaned, String type) {
        double min_cost = 0.0;
        ArrayList<ArrayList<Double>> cold_window = new ArrayList<>();
        ArrayList<Long> cold_window_time = new ArrayList<>();
        for (int i = 0; i < td.size(); i++) {
            if (dynamic_eta) {
                if (i > 0 && i % 10000 == 0) {
                    update_eta(Math.max(0, i - eta_window_length), i);
                }
            }
            ArrayList<Double> tuple = td.get(i);
            ArrayList<Integer> W_i = cal_W(td_time, i); // 获取一个窗口内的元素

            if (Objects.equals(type, "whole_series")) {
                if (W_i.size() == 0 || cal_W(td_time, W_i.get(0)).size() == 0) {
                    cold_window.add(td.get(i));
                    cold_window_time.add(td_time.get(i));
                    continue;
                } else {
                    if (cold_window.size() != 0) {
                        cold_window.add(td.get(i));
                        cold_window_time.add(td_time.get(i));
                        ArrayList<ArrayList<Double>> cold_window_results = repairColdWindow(cold_window,
                                cold_window_time);
                        td_cleaned.addAll(cold_window_results);
                        cold_window.clear();
                        cold_window_time.clear();
                        continue;
                    }
                }
            }

            ArrayList<ArrayList<Double>> C_i = this.cal_C(tuple, td_cleaned, W_i); // 候选集

            double min_dis = Double.MAX_VALUE;
            ArrayList<Double> repair_tuple = new ArrayList<>();
            for (ArrayList<Double> c_i : C_i) {
                boolean smooth = true;
                for (Integer w_i : W_i) {
                    if(w_i >= td_cleaned.size()){
                        continue;
                    }
                    ArrayList<Double> w_is = td_cleaned.get(w_i);
                    if (get_tm_distance(c_i, w_is) > eta) {
                        smooth = false;
                        break;
                    }
                }
                if (smooth) {
                    double dis = get_tm_distance(c_i, tuple);
                    if (dis < min_dis) {
                        min_dis = dis;
                        repair_tuple = c_i;
                    }
                }
            }

            // 确保修复值不为空
            if (repair_tuple.isEmpty()) {
                // 如果没有找到合适的修复值，使用当前元组作为修复值
                repair_tuple = tuple;
            }

            td_cleaned.add(repair_tuple);
            min_cost += min_dis;
        }

        return min_cost;
    }

    public void repair() throws Exception {
        ccfd_repair(this.td_time, this.td, this.td_cleaned, "whole_series");
    }

    public ArrayList<ArrayList<Double>> getTd_cleaned() {
        return td_cleaned;
    }

    public ArrayList<Long> getTime() {
        return td_time;
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
}
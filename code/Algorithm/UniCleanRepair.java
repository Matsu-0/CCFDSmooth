package Algorithm;

import Algorithm.util.KDTreeUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * UniClean-style repair (Fan et al., SIGMOD 2011 / JDIQ 2014), adapted to numeric
 * time-series tuples with constant pattern: cRepair → eRepair → hRepair.
 */
public class UniCleanRepair {
    public enum FixLevel {
        NONE, DETERMINISTIC, RELIABLE, POSSIBLE
    }

    private final int columnCnt;
    private final ArrayList<ArrayList<Double>> td;
    private final ArrayList<ArrayList<Double>> constantPattern;
    private final ArrayList<ArrayList<Double>> td_cleaned = new ArrayList<>();
    private final double[] std;
    private final double eta;
    private final int delta1;
    private final double delta2;

    private double[] tolerance;
    private KDTreeUtil kdTreeUtil;

    public UniCleanRepair(ArrayList<Long> td_time, ArrayList<ArrayList<Double>> td,
            ArrayList<ArrayList<Double>> constantPattern, int columnCnt, double[] std) {
        this(td_time, td, constantPattern, columnCnt, std, 0.8, 3, 0.85);
    }

    public UniCleanRepair(ArrayList<Long> td_time, ArrayList<ArrayList<Double>> td,
            ArrayList<ArrayList<Double>> constantPattern, int columnCnt, double[] std,
            double eta, int delta1, double delta2) {
        this.td = td;
        this.constantPattern = constantPattern;
        this.columnCnt = columnCnt;
        this.std = std;
        this.eta = eta;
        this.delta1 = delta1;
        this.delta2 = delta2;
        long start = System.currentTimeMillis();
        preprocess();
        repair();
        System.out.println("Time cost:" + (System.currentTimeMillis() - start) + "ms");
    }

    private static class TupleState {
        final ArrayList<Double> values;
        final double[] cf;
        final FixLevel[] level;
        final int[] changeCount;

        TupleState(ArrayList<Double> src, int n) {
            values = new ArrayList<>(src);
            cf = new double[n];
            level = new FixLevel[n];
            changeCount = new int[n];
            for (int i = 0; i < n; i++) {
                level[i] = FixLevel.NONE;
            }
        }
    }

    public void preprocess() {
        fillNullValue();
        calTolerance();
        kdTreeUtil = KDTreeUtil.build(constantPattern, columnCnt);
    }

    public void calTolerance() {
        tolerance = new double[columnCnt];
        for (int i = 0; i < columnCnt; i++) {
            tolerance[i] = Math.abs(constantPattern.get(0).get(i) - constantPattern.get(1).get(i));
            if (tolerance[i] == 0) {
                tolerance[i] = 1e-6;
            }
        }
    }

    public void fillNullValue() {
        for (int i = 0; i < columnCnt; i++) {
            double temp = td.get(0).get(i);
            for (ArrayList<Double> row : td) {
                if (Double.isNaN(row.get(i))) {
                    row.set(i, temp);
                } else {
                    temp = row.get(i);
                }
            }
        }
    }

    private boolean withinTol(double a, double b, int col) {
        return Math.abs(a - b) <= tolerance[col];
    }

    private int countMatches(ArrayList<Double> t, ArrayList<Double> m) {
        int c = 0;
        for (int i = 0; i < columnCnt; i++) {
            if (withinTol(t.get(i), m.get(i), i)) {
                c++;
            }
        }
        return c;
    }

    private ArrayList<Double> nearestConstantPattern(ArrayList<Double> t) {
        return kdTreeUtil.query(t, std);
    }

    private void initConfidence(ArrayList<TupleState> states) {
        for (TupleState ts : states) {
            ArrayList<Double> ref = nearestConstantPattern(ts.values);
            for (int i = 0; i < columnCnt; i++) {
                if (withinTol(ts.values.get(i), ref.get(i), i)) {
                    ts.cf[i] = 1.0;
                } else {
                    ts.cf[i] = 0.4;
                }
            }
        }
    }

    /** Phase 1: cRepair — deterministic fixes via constant-pattern-style rules. */
    private void cRepair(ArrayList<TupleState> states) {
        boolean changed = true;
        while (changed) {
            changed = false;
            for (TupleState ts : states) {
                for (int diff = 1; diff < columnCnt; diff++) {
                    int need = columnCnt - diff;
                    for (ArrayList<Double> cp : constantPattern) {
                        if (countMatches(ts.values, cp) < need) {
                            continue;
                        }
                        boolean premiseOk = true;
                        for (int i = 0; i < columnCnt; i++) {
                            if (withinTol(ts.values.get(i), cp.get(i), i) && ts.cf[i] < eta) {
                                premiseOk = false;
                                break;
                            }
                            if (!withinTol(ts.values.get(i), cp.get(i), i) && ts.cf[i] < eta) {
                                // non-matching premise attr still needs high cf in strict constant pattern;
                                // skip if this attr is part of premise but doesn't match
                            }
                        }
                        if (!premiseOk) {
                            continue;
                        }
                        double minPremiseCf = 1.0;
                        for (int i = 0; i < columnCnt; i++) {
                            if (withinTol(ts.values.get(i), cp.get(i), i)) {
                                minPremiseCf = Math.min(minPremiseCf, ts.cf[i]);
                            }
                        }
                        for (int j = 0; j < columnCnt; j++) {
                            if (ts.level[j] == FixLevel.DETERMINISTIC) {
                                continue;
                            }
                            if (!withinTol(ts.values.get(j), cp.get(j), j) && ts.cf[j] < eta) {
                                ts.values.set(j, cp.get(j));
                                ts.cf[j] = Math.max(eta, minPremiseCf);
                                ts.level[j] = FixLevel.DETERMINISTIC;
                                changed = true;
                            }
                        }
                        if (changed) {
                            break;
                        }
                    }
                }
            }
        }
        applyVariableCfdDeterministic(states);
    }

    /** Variable CFD: unique high-confidence RHS within a quantized LHS group. */
    private void applyVariableCfdDeterministic(ArrayList<TupleState> states) {
        if (columnCnt < 2) {
            return;
        }
        for (int rhs = 0; rhs < columnCnt; rhs++) {
            Map<String, ArrayList<TupleState>> groups = new HashMap<>();
            for (TupleState ts : states) {
                String key = lhsKey(ts.values, rhs);
                groups.computeIfAbsent(key, k -> new ArrayList<>()).add(ts);
            }
            for (ArrayList<TupleState> group : groups.values()) {
                if (group.size() < 2) {
                    continue;
                }
                Double candidate = null;
                int highCf = 0;
                for (TupleState ts : group) {
                    if (ts.cf[rhs] >= eta) {
                        highCf++;
                        double v = ts.values.get(rhs);
                        if (candidate == null) {
                            candidate = v;
                        } else if (Math.abs(candidate - v) > tolerance[rhs]) {
                            candidate = null;
                            break;
                        }
                    }
                }
                if (candidate == null || highCf != 1) {
                    continue;
                }
                for (TupleState ts : group) {
                    if (ts.level[rhs] == FixLevel.DETERMINISTIC) {
                        continue;
                    }
                    if (ts.cf[rhs] < eta && Math.abs(ts.values.get(rhs) - candidate) > tolerance[rhs]) {
                        ts.values.set(rhs, candidate);
                        ts.cf[rhs] = eta;
                        ts.level[rhs] = FixLevel.DETERMINISTIC;
                    }
                }
            }
        }
    }

    private String lhsKey(ArrayList<Double> t, int rhsCol) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < columnCnt; i++) {
            if (i == rhsCol) {
                continue;
            }
            double q = Math.round(t.get(i) / tolerance[i]);
            sb.append(i).append(':').append(q).append(';');
        }
        return sb.toString();
    }

    private double normalizedEntropy(ArrayList<TupleState> group, int rhs) {
        HashMap<Long, Integer> hist = new HashMap<>();
        for (TupleState ts : group) {
            long bin = Math.round(ts.values.get(rhs) / tolerance[rhs]);
            hist.merge(bin, 1, Integer::sum);
        }
        int k = hist.size();
        if (k <= 1) {
            return 0;
        }
        double n = group.size();
        double h = 0;
        for (int c : hist.values()) {
            double p = c / n;
            h -= p * (Math.log(p) / Math.log(2));
        }
        return h / (Math.log(k) / Math.log(2));
    }

    private double majorityValue(ArrayList<TupleState> group, int rhs) {
        HashMap<Long, Integer> hist = new HashMap<>();
        HashMap<Long, Double> rep = new HashMap<>();
        for (TupleState ts : group) {
            long bin = Math.round(ts.values.get(rhs) / tolerance[rhs]);
            hist.merge(bin, 1, Integer::sum);
            rep.putIfAbsent(bin, ts.values.get(rhs));
        }
        int best = -1;
        long bestBin = 0;
        for (Map.Entry<Long, Integer> e : hist.entrySet()) {
            if (e.getValue() > best) {
                best = e.getValue();
                bestBin = e.getKey();
            }
        }
        return rep.get(bestBin);
    }

    /** Phase 2: eRepair — reliable fixes via low entropy in CFD groups + constant pattern resolve. */
    private void eRepair(ArrayList<TupleState> states) {
        boolean changed = true;
        int rounds = 0;
        while (changed && rounds < 10) {
            changed = false;
            rounds++;
            if (columnCnt >= 2) {
                for (int rhs = 0; rhs < columnCnt; rhs++) {
                    Map<String, ArrayList<TupleState>> groups = new HashMap<>();
                    for (TupleState ts : states) {
                        groups.computeIfAbsent(lhsKey(ts.values, rhs), k -> new ArrayList<>()).add(ts);
                    }
                    for (ArrayList<TupleState> group : groups.values()) {
                        if (group.size() < 2) {
                            continue;
                        }
                        double h = normalizedEntropy(group, rhs);
                        if (h > delta2) {
                            continue;
                        }
                        double target = majorityValue(group, rhs);
                        for (TupleState ts : group) {
                            if (ts.level[rhs] == FixLevel.DETERMINISTIC) {
                                continue;
                            }
                            if (ts.changeCount[rhs] >= delta1) {
                                continue;
                            }
                            if (Math.abs(ts.values.get(rhs) - target) > tolerance[rhs]) {
                                ts.values.set(rhs, target);
                                ts.level[rhs] = FixLevel.RELIABLE;
                                ts.changeCount[rhs]++;
                                changed = true;
                            }
                        }
                    }
                }
            }
            for (TupleState ts : states) {
                ArrayList<Double> best = nearestConstantPattern(ts.values);
                if (countMatches(ts.values, best) < columnCnt - 1) {
                    continue;
                }
                for (int j = 0; j < columnCnt; j++) {
                    if (ts.level[j] == FixLevel.DETERMINISTIC || ts.changeCount[j] >= delta1) {
                        continue;
                    }
                    if (!withinTol(ts.values.get(j), best.get(j), j)) {
                        ts.values.set(j, best.get(j));
                        ts.level[j] = FixLevel.RELIABLE;
                        ts.changeCount[j]++;
                        changed = true;
                    }
                }
            }
        }
    }

    /** Phase 3: hRepair — heuristic constant-pattern/editing rules; preserve deterministic attrs. */
    private void hRepair(ArrayList<TupleState> states) {
        for (TupleState ts : states) {
            if (isConsistent(ts.values)) {
                continue;
            }
            boolean repaired = false;
            for (int diff = 1; diff < columnCnt && !repaired; diff++) {
                for (ArrayList<Double> cp : constantPattern) {
                    if (!editRule(ts.values, cp, diff)) {
                        continue;
                    }
                    for (int j = 0; j < columnCnt; j++) {
                        if (ts.level[j] == FixLevel.DETERMINISTIC) {
                            continue;
                        }
                        ts.values.set(j, cp.get(j));
                        ts.level[j] = FixLevel.POSSIBLE;
                    }
                    repaired = true;
                    break;
                }
            }
            if (!repaired) {
                ArrayList<Double> knn = nearestConstantPattern(ts.values);
                for (int j = 0; j < columnCnt; j++) {
                    if (ts.level[j] == FixLevel.DETERMINISTIC) {
                        continue;
                    }
                    if (Double.isNaN(ts.values.get(j)) || !isConsistent(ts.values)) {
                        ts.values.set(j, knn.get(j));
                        ts.level[j] = FixLevel.POSSIBLE;
                    }
                }
            }
        }
        // Forward-fill remaining NaNs on possible-fix attrs
        for (int i = 0; i < columnCnt; i++) {
            double last = states.get(0).values.get(i);
            for (TupleState ts : states) {
                if (Double.isNaN(ts.values.get(i))) {
                    ts.values.set(i, last);
                } else {
                    last = ts.values.get(i);
                }
            }
        }
    }

    private boolean editRule(ArrayList<Double> t, ArrayList<Double> m, int diff) {
        int nullCnt = 0;
        int sameCnt = 0;
        for (int i = 0; i < columnCnt; i++) {
            if (Double.isNaN(t.get(i))) {
                nullCnt++;
            } else if (withinTol(t.get(i), m.get(i), i)) {
                sameCnt++;
            }
        }
        return nullCnt + sameCnt >= columnCnt - diff;
    }

    private boolean isConsistent(ArrayList<Double> t) {
        for (ArrayList<Double> cp : constantPattern) {
            if (countMatches(t, cp) == columnCnt) {
                return true;
            }
        }
        return false;
    }

    public void repair() {
        ArrayList<TupleState> states = new ArrayList<>();
        for (ArrayList<Double> row : td) {
            states.add(new TupleState(row, columnCnt));
        }
        initConfidence(states);
        cRepair(states);
        eRepair(states);
        hRepair(states);
        for (TupleState ts : states) {
            td_cleaned.add(new ArrayList<>(ts.values));
        }
    }

    public ArrayList<ArrayList<Double>> getTd_cleaned() {
        return td_cleaned;
    }
}

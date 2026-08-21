import Algorithm.*;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Downstream-application repair export for Fig.17-style experiments.
 *
 * For each dataset under data_repair/, injects noise, runs Java baselines
 * (Dirty / CCFDSmooth / SCREEN / LsGreedy / UniClean), and writes repaired
 * CSVs under result/downstream/<dataset>/. MTSClean is run separately via
 * code/downstream/run_downstream.py (Python baseline).
 *
 * Usage (from repo root):
 *   javac -d code/out code/*.java code/Algorithm/*.java code/Algorithm/util/*.java
 *   java -cp code/out ExperimentDownstream                 # all datasets
 *   java -cp code/out ExperimentDownstream fuel weather    # subset
 *   java -cp code/out ExperimentDownstream --td-len 10000 fuel
 */
public class ExperimentDownstream {

    enum Dataset {
        FUEL("fuel", "engine",
                "data_repair/engine/time_series_data_1596148.csv",
                "data_repair/engine/constant_pattern_14756.csv",
                "data_repair/engine/engine_dirty.csv",
                3, 14756, 2000,
                new double[] { 0.0, 500.0, 0.0 },
                new double[] { 2000.0, 2500.0, 30.0 }),
        GPS("gps", "gps",
                "data_repair/gps/time_series_data_1166375.csv",
                "data_repair/gps/constant_pattern_92110.csv",
                "data_repair/gps/gps_dirty.csv",
                3, 92110, 4000,
                new double[] { 100.0, 30.0, 1100.0 },
                new double[] { 110.0, 40.0, 1500.0 }),
        WEATHER("weather", "weather",
                "data_repair/weather/time_series_data_390598.csv",
                "data_repair/weather/constant_pattern_524288.csv",
                "data_repair/weather/weather_dirty.csv",
                3, 524288, 4000,
                new double[] { -10.0, 0.0, 850.0 },
                new double[] { 30.0, 15.0, 950.0 }),
        ROAD("road", "road",
                "data_repair/road/time_series_data_1829660.csv",
                "data_repair/road/constant_pattern_66876.csv",
                "data_repair/road/road_dirty.csv",
                2, 66876, 4000,
                new double[] { 110.0, 38.0 },
                new double[] { 120.0, 50.0 });

        final String key;
        final String outDir;
        final String tdPath;
        final String constantPatternPath;
        final String dirtyPath;
        final int columnCnt;
        final int patternUniverse;
        final int defaultPatternLen;
        final double[] lower;
        final double[] upper;

        Dataset(String key, String outDir, String tdPath, String constantPatternPath, String dirtyPath,
                int columnCnt, int patternUniverse, int defaultPatternLen, double[] lower, double[] upper) {
            this.key = key;
            this.outDir = outDir;
            this.tdPath = tdPath;
            this.constantPatternPath = constantPatternPath;
            this.dirtyPath = dirtyPath;
            this.columnCnt = columnCnt;
            this.patternUniverse = patternUniverse;
            this.defaultPatternLen = defaultPatternLen;
            this.lower = lower;
            this.upper = upper;
        }

        static Dataset fromKey(String key) {
            for (Dataset d : values()) {
                if (d.key.equalsIgnoreCase(key) || d.outDir.equalsIgnoreCase(key)
                        || (key.equalsIgnoreCase("engine") && d == FUEL)) {
                    return d;
                }
            }
            throw new IllegalArgumentException("Unknown dataset: " + key
                    + " (expected fuel|gps|weather|road)");
        }
    }

    private static final String METHOD_DEFAULT = "pt";
    private static final int THR_DEFAULT = 3;            // 0-10; fraction corrupted ≈ thr/10 (keep ~30%)
    private static final double NOISE_RATE_DEFAULT = 5.0; // × column std — larger offset, same % corrupted
    private static final double TOLERANCE_RATE = 0.5;
    private static int tdLen = 30000;
    private static int patternLenOverride = -1;
    private static String noiseMethod = METHOD_DEFAULT;
    private static int thr = THR_DEFAULT;
    private static double noiseRate = NOISE_RATE_DEFAULT;

    public static void main(String[] args) throws Exception {
        Set<Dataset> selected = new HashSet<>();
        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            if (a.equals("--td-len") && i + 1 < args.length) {
                tdLen = Integer.parseInt(args[++i]);
            } else if (a.equals("--pattern-len") && i + 1 < args.length) {
                patternLenOverride = Integer.parseInt(args[++i]);
            } else if (a.equals("--thr") && i + 1 < args.length) {
                thr = Integer.parseInt(args[++i]);
            } else if (a.equals("--noise-rate") && i + 1 < args.length) {
                noiseRate = Double.parseDouble(args[++i]);
            } else if (a.equals("--noise-method") && i + 1 < args.length) {
                noiseMethod = args[++i];
            } else if (a.equals("--help") || a.equals("-h")) {
                System.out.println(
                        "Usage: ExperimentDownstream [--td-len N] [--pattern-len N] "
                                + "[--thr 0-10] [--noise-rate R] [--noise-method pt|seg] "
                                + "[fuel|gps|weather|road ...]");
                System.out.println("  --thr         fraction of points corrupted is thr/10 (default "
                        + THR_DEFAULT + ", keep low to fix % corrupted)");
                System.out.println("  --noise-rate  Gaussian offset scale = rate × column_std (default "
                        + NOISE_RATE_DEFAULT + ")");
                return;
            } else if (!a.startsWith("-")) {
                selected.add(Dataset.fromKey(a));
            }
        }
        if (selected.isEmpty()) {
            selected.addAll(Arrays.asList(Dataset.values()));
        }

        System.out.println("Noise config: method=" + noiseMethod + " thr=" + thr
                + " noise_rate=" + noiseRate);

        for (Dataset ds : Dataset.values()) {
            if (!selected.contains(ds)) {
                continue;
            }
            runOne(ds);
        }
    }

    private static void runOne(Dataset ds) throws Exception {
        int patternLen = patternLenOverride > 0 ? patternLenOverride
                : Math.min(ds.defaultPatternLen, ds.patternUniverse);
        System.out.println("\n========== Downstream repair: " + ds.key
                + " td_len=" + tdLen + " pattern_len=" + patternLen
                + " thr=" + thr + " noise_rate=" + noiseRate + " ==========");

        LoadData loadData = new LoadData(ds.columnCnt, ds.tdPath, ds.constantPatternPath,
                tdLen, patternLen, ds.patternUniverse);
        ArrayList<Long> td_time = loadData.getTd_time();
        ArrayList<ArrayList<Double>> td = loadData.getTd();
        ArrayList<ArrayList<Double>> constantPattern = loadData.getConstantPattern();
        System.out.println("td.size()=" + td.size() + " constantPattern.size()=" + constantPattern.size());

        CalStd calStd = new CalStd(ds.columnCnt, td);
        double[] std = calStd.getStd();

        AddNoise addNoise = new AddNoise(ds.columnCnt, ds.tdPath, ds.dirtyPath,
                ds.lower, ds.upper, noiseMethod, thr, tdLen, noiseRate);
        ArrayList<ArrayList<Double>> test_td = addNoise.getTest_td();

        String outRoot = "result/downstream/" + ds.outDir;
        writeSeries(outRoot + "/clean.csv", td, ds.columnCnt);
        writeSeries(outRoot + "/dirty.csv", test_td, ds.columnCnt);
        writeTimes(outRoot + "/times.csv", td_time);

        // Keep an immutable dirty snapshot; repair algorithms may mutate their input.
        ArrayList<ArrayList<Double>> dirtySnapshot = deepCopy(test_td);
        runAndWrite("dirty", dirtySnapshot, outRoot, ds, td, constantPattern, std, dirtySnapshot);

        System.out.println("\nCCFDSmooth");
        CCFDRepair ccfd = new CCFDRepair(td_time, deepCopy(dirtySnapshot), constantPattern, ds.columnCnt, std, 0.0);
        runAndWrite("ccfd", ccfd.getTd_cleaned(), outRoot, ds, td, constantPattern, std, dirtySnapshot);

        System.out.println("\nSCREEN");
        SCREEN screen = new SCREEN(td_time, deepCopy(dirtySnapshot), ds.columnCnt);
        runAndWrite("screen", screen.getTd_cleaned(), outRoot, ds, td, constantPattern, std, dirtySnapshot);

        System.out.println("\nLsGreedy");
        Lsgreedy lsgreedy = new Lsgreedy(td_time, deepCopy(dirtySnapshot), ds.columnCnt);
        runAndWrite("lsgreedy", lsgreedy.getTd_cleaned(), outRoot, ds, td, constantPattern, std, dirtySnapshot);

        System.out.println("\nUniClean");
        UniCleanRepair uni = new UniCleanRepair(td_time, deepCopy(dirtySnapshot), constantPattern, ds.columnCnt, std);
        runAndWrite("uniclean", uni.getTd_cleaned(), outRoot, ds, td, constantPattern, std, dirtySnapshot);

        System.out.println("Wrote Java repairs to " + outRoot
                + " (run Python downstream for MTSClean + LSTM/DTW metrics)");
    }

    private static void runAndWrite(String method, ArrayList<ArrayList<Double>> cleaned, String outRoot,
            Dataset ds, ArrayList<ArrayList<Double>> td, ArrayList<ArrayList<Double>> constantPattern,
            double[] std, ArrayList<ArrayList<Double>> test_td) throws Exception {
        writeSeries(outRoot + "/" + method + ".csv", cleaned, ds.columnCnt);
        Analysis analysis = new Analysis(ds.columnCnt, td, constantPattern, test_td, cleaned, TOLERANCE_RATE, std);
        System.out.println(method + " repair RMSE=" + analysis.getRMSE());
    }

    private static ArrayList<ArrayList<Double>> deepCopy(ArrayList<ArrayList<Double>> src) {
        ArrayList<ArrayList<Double>> out = new ArrayList<>(src.size());
        for (ArrayList<Double> row : src) {
            out.add(new ArrayList<>(row));
        }
        return out;
    }

    private static void writeTimes(String path, ArrayList<Long> times) throws Exception {
        File f = new File(path);
        if (f.getParentFile() != null) {
            f.getParentFile().mkdirs();
        }
        try (BufferedWriter w = new BufferedWriter(new FileWriter(f))) {
            w.write("idx,time_ms");
            for (int i = 0; i < times.size(); i++) {
                w.newLine();
                w.write(i + "," + times.get(i));
            }
        }
    }

    private static void writeSeries(String path, ArrayList<ArrayList<Double>> series, int columnCnt)
            throws Exception {
        File f = new File(path);
        if (f.getParentFile() != null) {
            f.getParentFile().mkdirs();
        }
        try (BufferedWriter w = new BufferedWriter(new FileWriter(f))) {
            StringBuilder header = new StringBuilder("idx");
            for (int c = 0; c < columnCnt; c++) {
                header.append(",col").append(c);
            }
            w.write(header.toString());
            for (int i = 0; i < series.size(); i++) {
                w.newLine();
                ArrayList<Double> row = series.get(i);
                StringBuilder line = new StringBuilder();
                line.append(i);
                for (int c = 0; c < columnCnt; c++) {
                    line.append(',');
                    Double v = c < row.size() ? row.get(c) : Double.NaN;
                    if (v != null && !Double.isNaN(v)) {
                        line.append(String.format(Locale.US, "%.6f", v));
                    }
                }
                w.write(line.toString());
            }
        }
    }
}

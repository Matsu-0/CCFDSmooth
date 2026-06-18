import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;

/**
 * constant pattern 噪声率扫描实验（对应 figure 中 Constant Pattern Noise Rate / varying-m）。
 * 固定 TD 加噪与 td_len、constantPatternLen，对每个 constantPatternNoiseRate 从干净 constant pattern 重新加噪后跑各修复方法。
 *
 * 运行：java -cp code/out ExperimentConstantPatternNoise
 * 可选参数：--rates 0,0.05,0.1,0.15,0.2,0.25
 */
public class ExperimentConstantPatternNoise {
    private static final String tdPath = "data_repair/weather/time_series_data_390598.csv";
    private static final String constantPatternPath = "data_repair/weather/constant_pattern_524288.csv";
    private static final String testTdPath = "data_repair/weather/weather_dirty.csv";
    private static final String resultDatPath = "figure/new_exp/constant-pattern-noise/weather-constant-pattern-rmse.dat";

    private static final int columnCnt = 3;
    private static final int td_len = 260000;
    private static final int constantPatternLen = 2000;
    private static final String method = "pt";
    private static final int thr = 3;
    private static final double noise_rate = 10.0;
    private static final double[] lower_bound = { -10.0, 0.0, 850.0 };
    private static final double[] upper_bound = { 30.0, 15.0, 950.0 };

    /** 测试 constant pattern 噪声率：0, 0.05, 0.1, 0.15, 0.2, 0.25 */
    private static final double[] CONSTANT_PATTERN_NOISE_RATES = { 0.0, 0.05, 0.1, 0.15, 0.2, 0.25 };
    /** gnuplot x 轴刻度：0.0→1, 0.05→3, …, 0.25→11 */
    private static final int[] PLOT_X_INDEX = { 1, 3, 5, 7, 9, 11 };

    private static double[] std;
    private static double tau = 0.0;

    private static class RowResult {
        final int plotX;
        final double constantPatternNoiseRate;
        final Analysis ccfd;
        final Analysis knn;
        final Analysis er;
        final Analysis uniClean;

        RowResult(int plotX, double constantPatternNoiseRate, Analysis ccfd, Analysis knn, Analysis er,
                Analysis uniClean) {
            this.plotX = plotX;
            this.constantPatternNoiseRate = constantPatternNoiseRate;
            this.ccfd = ccfd;
            this.knn = knn;
            this.er = er;
            this.uniClean = uniClean;
        }
    }

    public static void main(String[] args) throws Exception {
        double[] constantPatternNoiseRates = parseRates(args);

        System.out.println("=== constant pattern noise rate sweep ===");
        System.out.println("td_len=" + td_len + ", constantPatternLen=" + constantPatternLen);
        System.out.print("constantPatternNoiseRates:");
        for (double rate : constantPatternNoiseRates) {
            System.out.print(" " + rate);
        }
        System.out.println();

        LoadData loadData = new LoadData(columnCnt, tdPath, constantPatternPath, td_len, constantPatternLen);
        ArrayList<Long> td_time = loadData.getTd_time();
        ArrayList<ArrayList<Double>> td = loadData.getTd();
        ArrayList<ArrayList<Double>> cleanConstantPattern = AddConstantPatternNoise.copyConstantPattern(loadData.getConstantPattern());

        std = new CalStd(columnCnt, td).getStd();
        Experiment.std = std;
        Experiment.tau = tau;

        System.out.println("add TD noise (fixed for all constant pattern rates)...");
        AddNoise addNoise = new AddNoise(columnCnt, tdPath, testTdPath, lower_bound, upper_bound,
                method, thr, td_len, noise_rate);
        ArrayList<ArrayList<Double>> test_td = addNoise.getTest_td();

        ArrayList<RowResult> rows = new ArrayList<>();
        for (int r = 0; r < constantPatternNoiseRates.length; r++) {
            double rate = constantPatternNoiseRates[r];
            int plotX = r < PLOT_X_INDEX.length ? PLOT_X_INDEX[r] : r + 1;
            long seed = 1000L + r;

            System.out.println("\n--- constantPatternNoiseRate = " + rate + " ---");
            ArrayList<ArrayList<Double>> constantPattern = AddConstantPatternNoise.addNoise(
                    AddConstantPatternNoise.copyConstantPattern(cleanConstantPattern), rate, lower_bound, upper_bound, seed);

            rows.add(new RowResult(
                    plotX,
                    rate,
                    Experiment.ccfdRepair(td_time, td, test_td, constantPattern),
                    Experiment.knnRepair(td_time, td, test_td, constantPattern),
                    Experiment.erRepair(td_time, td, test_td, constantPattern),
                    Experiment.uniCleanRepair(td_time, td, test_td, constantPattern)));
        }

        printResults(rows);
        writeDatFile(rows, resultDatPath);
        System.out.println("\nWrote " + resultDatPath);
    }

    private static double[] parseRates(String[] args) {
        for (int i = 0; i < args.length - 1; i++) {
            if ("--rates".equals(args[i])) {
                String[] parts = args[i + 1].split(",");
                double[] rates = new double[parts.length];
                for (int j = 0; j < parts.length; j++) {
                    rates[j] = Double.parseDouble(parts[j].trim());
                }
                return rates;
            }
        }
        return CONSTANT_PATTERN_NOISE_RATES;
    }

    private static void printResults(ArrayList<RowResult> rows) {
        System.out.println("\nRMSE (constantPatternNoiseRate: ccfd / knn / er / uniclean):");
        for (RowResult row : rows) {
            System.out.printf("%5.2f: %s  %s  %s  %s%n",
                    row.constantPatternNoiseRate,
                    row.ccfd.getRMSE(), row.knn.getRMSE(), row.er.getRMSE(),
                    row.uniClean.getRMSE());
        }
    }

    private static void writeDatFile(ArrayList<RowResult> rows, String path) throws IOException {
        File file = new File(path);
        File parent = file.getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }
        try (BufferedWriter w = new BufferedWriter(new FileWriter(file))) {
            w.write("Models\tCCFD\tER\t1NN\tUniClean");
            w.newLine();
            for (RowResult row : rows) {
                w.write(String.format("%d\t%s\t%s\t%s\t%s",
                        row.plotX,
                        row.ccfd.getRMSE(),
                        row.er.getRMSE(),
                        row.knn.getRMSE(),
                        row.uniClean.getRMSE()));
                w.newLine();
            }
        }
    }
}

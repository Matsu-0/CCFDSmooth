import Algorithm.*;

import java.util.ArrayList;

public class Experiment {
    // private static final String tdPath = "data/road/TimeSeries/time_series_data_1829660.csv"; // complete time series
    // private static final String constantPatternPath = "data/road/ConstantPattern/constant_pattern_754850.csv";
    // private static final String tdPath = "data_repair/engine/time_series_data_1596148.csv"; // complete time series
    // private static final String constantPatternPath = "data_repair/engine/constant_pattern_14756.csv";
    private static final String tdPath = "data_repair/weather/time_series_data_390598.csv"; // complete time series
    private static final String constantPatternPath = "data_repair/weather/constant_pattern_524288.csv";
    // private static final String testTdPath = "data_repair/engine/engine_dirty.csv";
    private static final String testTdPath = "data_repair/weather/weather_dirty.csv";
    private static final int columnCnt = 3;
    private static final int td_len = 260000;
    private static final int constantPatternLen = 2000;
    private static final double toleranceRate = 0.5;
    private static final String method = "pt"; // pt/seg
    private static final int thr = 3; // 0-10
    private static final double noise_rate = 10.0;
    /** constant pattern 噪声率：0 表示不加噪；与 ExperimentConstantPatternNoise 扫描实验一致 */
    private static final double constantPatternNoiseRate = 0.0;
    static double[] std;
    static double tau = 0.0;
    private static final double[] lower_bound = { -10.0, 0.0, 850.0 };// Weather
    private static final double[] upper_bound = { 30.0, 15.0, 950.0 };
    // private static final double[] lower_bound = { 100.0, 30.0, 1100.0 };// GPS
    // private static final double[] upper_bound = { 110.0, 40.0, 1500.0 };
    // private static final double[] lower_bound = { 0.0, 500.0, 0.0 };// Engine
    // private static final double[] upper_bound = { 2000.0, 2500.0, 30.0 };
    // private static final double[] lower_bound = { 110.0, 38.0 };// Road
    // private static final double[] upper_bound = { 120.0, 50.0 };

    public static Analysis ccfdRepair(ArrayList<Long> td_time, ArrayList<ArrayList<Double>> td,
            ArrayList<ArrayList<Double>> test_td, ArrayList<ArrayList<Double>> constantPattern) throws Exception {
        System.out.println("\nGlobalCCFD");
        CCFDRepair ccfdRepair = new CCFDRepair(td_time, test_td, constantPattern, columnCnt, std, tau);
        ArrayList<ArrayList<Double>> td_cleaned = ccfdRepair.getTd_cleaned();
        return new Analysis(columnCnt, td, constantPattern, test_td, td_cleaned, toleranceRate, std);
    }

    public static Analysis knnRepair(ArrayList<Long> td_time, ArrayList<ArrayList<Double>> td,
            ArrayList<ArrayList<Double>> test_td, ArrayList<ArrayList<Double>> constantPattern) {
        System.out.println("\nKNN");
        KNNRepair knnRepair = new KNNRepair(td_time, test_td, constantPattern, columnCnt, std);
        ArrayList<ArrayList<Double>> td_cleaned = knnRepair.getTd_cleaned();
        return new Analysis(columnCnt, td, constantPattern, test_td, td_cleaned, toleranceRate, std);
    }

    public static Analysis erRepair(ArrayList<Long> td_time, ArrayList<ArrayList<Double>> td,
            ArrayList<ArrayList<Double>> test_td, ArrayList<ArrayList<Double>> constantPattern) {
        System.out.println("\nERRepair");
        EditingRuleRepair editingRuleRepair = new EditingRuleRepair(td_time, test_td, constantPattern, columnCnt);
        ArrayList<ArrayList<Double>> td_cleaned = editingRuleRepair.getTd_cleaned();
        return new Analysis(columnCnt, td, constantPattern, test_td, td_cleaned, toleranceRate, std);
    }

    public static Analysis screenRepair(ArrayList<Long> td_time, ArrayList<ArrayList<Double>> td,
            ArrayList<ArrayList<Double>> test_td, ArrayList<ArrayList<Double>> constantPattern) throws Exception {
        System.out.println("\nSCREEN");
        SCREEN screen = new SCREEN(td_time, test_td, columnCnt);
        ArrayList<ArrayList<Double>> td_cleaned = screen.getTd_cleaned();
        return new Analysis(columnCnt, td, constantPattern, test_td, td_cleaned, toleranceRate, std);
    }

    public static Analysis lsgreedyRepair(ArrayList<Long> td_time, ArrayList<ArrayList<Double>> td,
            ArrayList<ArrayList<Double>> test_td, ArrayList<ArrayList<Double>> constantPattern) throws Exception {
        System.out.println("\nLsGreedy");
        Lsgreedy lsgreedy = new Lsgreedy(td_time, test_td, columnCnt);
        ArrayList<ArrayList<Double>> td_cleaned = lsgreedy.getTd_cleaned();
        return new Analysis(columnCnt, td, constantPattern, test_td, td_cleaned, toleranceRate, std);
    }

    public static Analysis ewmaRepair(ArrayList<Long> td_time, ArrayList<ArrayList<Double>> td,
            ArrayList<ArrayList<Double>> test_td, ArrayList<ArrayList<Double>> constantPattern) {
        System.out.println("\nEWMA");
        EWMARepair ewmaRepair = new EWMARepair(td_time, test_td, columnCnt);
        ArrayList<ArrayList<Double>> td_cleaned = ewmaRepair.getTd_cleaned();
        return new Analysis(columnCnt, td, constantPattern, test_td, td_cleaned, toleranceRate, std);
    }

    public static Analysis uniCleanRepair(ArrayList<Long> td_time, ArrayList<ArrayList<Double>> td,
            ArrayList<ArrayList<Double>> test_td, ArrayList<ArrayList<Double>> constantPattern) {
        System.out.println("\nUniClean");
        UniCleanRepair uniCleanRepair = new UniCleanRepair(td_time, test_td, constantPattern, columnCnt, std);
        ArrayList<ArrayList<Double>> td_cleaned = uniCleanRepair.getTd_cleaned();
        return new Analysis(columnCnt, td, constantPattern, test_td, td_cleaned, toleranceRate, std);
    }

    public static void main(String[] args) throws Exception {
        System.out.println("start load data");
        LoadData loadData = new LoadData(columnCnt, tdPath, constantPatternPath, td_len, constantPatternLen);
        ArrayList<Long> td_time = loadData.getTd_time();
        ArrayList<ArrayList<Double>> td = loadData.getTd();
        ArrayList<ArrayList<Double>> constantPattern = loadData.getConstantPattern();
        System.out.println("td.size() = " + td.size());
        System.out.println("constantPattern.size() = " + constantPattern.size());
        System.out.println("finish load data");

        CalStd calStd = new CalStd(columnCnt, td);
        std = calStd.getStd();

        if (constantPatternNoiseRate > 0) {
            System.out.println("add constant pattern noise, rate = " + constantPatternNoiseRate);
            constantPattern = AddConstantPatternNoise.addNoise(constantPattern, constantPatternNoiseRate, lower_bound, upper_bound, 42L);
        }

        System.out.println("start add noise");
        AddNoise addNoise = new AddNoise(columnCnt, tdPath, testTdPath, lower_bound, upper_bound, method, thr, td_len, noise_rate);
        ArrayList<ArrayList<Double>> test_td = addNoise.getTest_td();
        System.out.println("end add noise");
        System.out.println("total points: " + test_td.size());

        Analysis ccfd = ccfdRepair(td_time, td, test_td, constantPattern);
        Analysis knn = knnRepair(td_time, td, test_td, constantPattern);
        Analysis er = erRepair(td_time, td, test_td, constantPattern);
        Analysis screen = screenRepair(td_time, td, test_td, constantPattern);
        Analysis lsgreedy = lsgreedyRepair(td_time, td, test_td, constantPattern);
        Analysis ewmaRepair = ewmaRepair(td_time, td, test_td, constantPattern);
        Analysis uniClean = uniCleanRepair(td_time, td, test_td, constantPattern);

        ccfd.writeRepairResultToFile("result/engine/fuel_" + td_len + "_ccfd.csv");
        knn.writeRepairResultToFile("result/engine/fuel_" + td_len + "_knn.csv");
        er.writeRepairResultToFile("result/engine/fuel_" + td_len + "_er.csv");
        screen.writeRepairResultToFile("result/engine/fuel_" + td_len + "_screen.csv");
        lsgreedy.writeRepairResultToFile("result/engine/fuel_" + td_len + "_lsgreedy.csv");
        ewmaRepair.writeRepairResultToFile("result/engine/fuel_" + td_len + "_ewma.csv");
        uniClean.writeRepairResultToFile("result/engine/fuel_" + td_len + "_uniclean.csv");

        System.out.println("dirty input points (vs constant pattern, all methods): " + ccfd.getDirtyInputCount());

        System.out.println("\nRMSE:");
        System.out.println("ccfd: " + ccfd.getRMSE());
        System.out.println("knn: " + knn.getRMSE());
        System.out.println("er: " + er.getRMSE());
        System.out.println("screen: " + screen.getRMSE());
        System.out.println("lsgreedy: " + lsgreedy.getRMSE());
        System.out.println("ewma: " + ewmaRepair.getRMSE());
        System.out.println("uniclean: " + uniClean.getRMSE());

        System.out.println("\nRepair count (×10^4, dist(test, repaired) > 0):");
        System.out.println("ccfd: " + ccfd.getRepairCountTimes1e4());
        System.out.println("knn: " + knn.getRepairCountTimes1e4());
        System.out.println("er: " + er.getRepairCountTimes1e4());
        System.out.println("screen: " + screen.getRepairCountTimes1e4());
        System.out.println("lsgreedy: " + lsgreedy.getRepairCountTimes1e4());
        System.out.println("ewma: " + ewmaRepair.getRepairCountTimes1e4());
        System.out.println("uniclean: " + uniClean.getRepairCountTimes1e4());

        System.out.println("\nRepair distance (mean std-dist, test -> repaired):");
        System.out.println("ccfd: " + ccfd.getRepairDistanceMean());
        System.out.println("knn: " + knn.getRepairDistanceMean());
        System.out.println("er: " + er.getRepairDistanceMean());
        System.out.println("screen: " + screen.getRepairDistanceMean());
        System.out.println("lsgreedy: " + lsgreedy.getRepairDistanceMean());
        System.out.println("ewma: " + ewmaRepair.getRepairDistanceMean());
        System.out.println("uniclean: " + uniClean.getRepairDistanceMean());
    }
}

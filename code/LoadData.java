import java.io.File;
import java.io.FileNotFoundException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

public class LoadData {
    private final int columnCnt;
    private final int td_len;
    private final int constantPatternLen;
    private final ArrayList<Long> td_time = new ArrayList<>();
    private final ArrayList<ArrayList<Double>> td = new ArrayList<>();
    private final ArrayList<ArrayList<Double>> constantPattern = new ArrayList<>();
    private final ArrayList<ArrayList<Double>> td_cleaned = new ArrayList<>();
    private final ArrayList<ArrayList<Double>> test_td = new ArrayList<>();

    public LoadData(int columnCnt, String tdPath, String constantPatternPath, int td_len, int constantPatternLen) throws FileNotFoundException, ParseException {
        this.columnCnt = columnCnt;
        this.td_len = td_len;
        this.constantPatternLen = constantPatternLen;
        this.loadTimeSeriesData(tdPath);
        this.loadConstantPatternRandom((constantPatternPath));
    }

    public void loadTimeSeriesData(String filename) throws FileNotFoundException, ParseException {
        Scanner sc = new Scanner(new File(filename));
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        sc.useDelimiter("\\s*(,|\\r|\\n)\\s*"); // set separator
        sc.nextLine();
        for (int k = td_len; k > 0 && sc.hasNextLine(); --k) {
            String[] line_str = sc.nextLine().split(",");
            long t = format.parse(line_str[0]).getTime();
            this.td_time.add(t);
            ArrayList<Double> values = new ArrayList<>();
            for (int i = 1; i < line_str.length; i++) {
                String temp = line_str[i];
                if (!temp.equals("")) {
                    double v = Double.parseDouble(temp);
                    values.add(v);
                } else {
                    values.add(Double.NaN);
                }
            }
            for (int i = line_str.length; i <= columnCnt; i++) {
                values.add(Double.NaN);
            }
            this.td.add(values);
        }
    }

    public void loadConstantPattern(String filename) throws FileNotFoundException {
        Scanner sc = new Scanner(new File(filename));
        sc.useDelimiter("\\s*(,|\\r|\\n)\\s*"); // set separator
        sc.nextLine();

        int step;
        if (constantPatternLen > 14756) {
            System.out.println("Warning: constantPatternLen exceed the upper bound");
            step = 1;
        } else step = 14756 / constantPatternLen;

        for (int k = constantPatternLen; k > 0 && k % step == 0 && sc.hasNextLine(); --k) {
            String[] line_str = sc.nextLine().split(",");
            ArrayList<Double> values = new ArrayList<>();
            for (int i = 1; i < line_str.length; i++) {
                String temp = line_str[i];
                if (!temp.equals("")) {
                    double v = Double.parseDouble(temp);
                    values.add(v);
                } else {
                    values.add(Double.NaN);
                }
            }
            for (int i = line_str.length; i <= columnCnt; i++) {
                values.add(Double.NaN);
            }
            this.constantPattern.add(values);
        }
    }

    public void loadConstantPatternStratified(String filename) throws FileNotFoundException {
        Scanner sc = new Scanner(new File(filename));
        sc.useDelimiter("\\s*(,|\\r|\\n)\\s*"); // set separator
        sc.nextLine();

        Random r = new Random();
        boolean rev = false;
        int sam_1 = constantPatternLen % 124, sam_n = constantPatternLen / 124;
        if (constantPatternLen > 14756) sam_1 = sam_n = 119;
        if (constantPatternLen > 14756 / 2) rev = true;

        for (int t = 1; t <= 124 && sc.hasNextLine(); ++t) {
            int sam = t <= sam_1 ? sam_n + 1 : sam_n;
            if (rev) sam = 119 - sam;

            Map<Integer, String> map = new HashMap<>();
            while (map.size() < sam) {
                int rand = r.nextInt(119);
                if (!map.containsKey(rand)) map.put(rand, "");
            }

            for (int k = 0; k < 119 && sc.hasNextLine(); ++k) {
                boolean check = !map.containsKey(k);
                check = rev == check;
                if (check) {
                    String[] line_str = sc.nextLine().split(",");
                    ArrayList<Double> values = new ArrayList<>();
                    for (int i = 1; i < line_str.length; i++) {
                        String temp = line_str[i];
                        if (!temp.equals("")) {
                            double v = Double.parseDouble(temp);
                            values.add(v);
                        } else {
                            values.add(Double.NaN);
                        }
                    }
                    for (int i = line_str.length; i <= columnCnt; i++) {
                        values.add(Double.NaN);
                    }
                    this.constantPattern.add(values);
                }
            }
        }
    }

    public void loadConstantPatternRandom(String filename) throws FileNotFoundException {
        Scanner sc = new Scanner(new File(filename));
        sc.useDelimiter("\\s*(,|\\r|\\n)\\s*"); // set separator
        sc.nextLine();

        Random r = new Random();
        boolean rev = false;
        int sam = Math.min(constantPatternLen, 14756);
        if (sam > 14756 / 2) {
            rev = true;
            sam = 14756 - sam;
        }

        Map<Integer, String> map = new HashMap<>();
        while (map.size() < sam) {
            int rand = r.nextInt(14756);
            if (!map.containsKey(rand)) map.put(rand, "");
        }

        for (int k = 0; sc.hasNextLine(); k++) {
            String[] line_str = sc.nextLine().split(",");

            if (rev == map.containsKey(k))
                continue;

            ArrayList<Double> values = new ArrayList<>();
            for (int i = 1; i < line_str.length; i++) {
                String temp = line_str[i];
                if (!temp.equals("")) {
                    double v = Double.parseDouble(temp);
                    values.add(v);
                } else {
                    values.add(Double.NaN);
                }
            }
            for (int i = line_str.length; i <= columnCnt; i++) {
                values.add(Double.NaN);
            }
            this.constantPattern.add(values);
        }
    }

    public void loadTestTimeSeriesData(String filename) throws FileNotFoundException, ParseException {
        Scanner sc = new Scanner(new File(filename));
        sc.useDelimiter("\\s*(,|\\r|\\n)\\s*"); // set separator
        sc.nextLine();
        while (sc.hasNextLine()) {
            String[] line_str = sc.nextLine().split(",");
            ArrayList<Double> values = new ArrayList<>();
            for (int i = 1; i < line_str.length; i++) {
                String temp = line_str[i];
                if (!temp.equals("")) {
                    double v = Double.parseDouble(temp);
                    values.add(v);
                } else {
                    values.add(Double.NaN);
                }
            }
            for (int i = line_str.length; i <= columnCnt; i++) {
                values.add(Double.NaN);
            }
            this.test_td.add(values);
        }
    }

    public double get_tm_distance(ArrayList<Double> t_tuple, ArrayList<Double> constantPatternTuple, double[] std) {
        double distance = 0d;
        for (int pos = 0; pos < columnCnt; pos++) {
            if (Double.isNaN(t_tuple.get(pos)) || Double.isNaN(constantPatternTuple.get(pos))) {
                continue;
            }
            double temp = t_tuple.get(pos) - constantPatternTuple.get(pos);
            temp = temp / std[pos];
            distance += temp * temp;
        }
        distance = Math.sqrt(distance);
        return distance;
    }

    public ArrayList<Double> getDistance(Long omega, double[] std) {
        ArrayList<Double> distance = new ArrayList<>();
        for (int i = 1; i < this.td.size(); i++) {
            for (int l = i - 1; l >= 0; l--) {
                if (this.td_time.get(i) <= this.td_time.get(l) + omega) {
                    double d = get_tm_distance(this.td.get(i), this.td.get(i - 1), std);
                    distance.add(d);
                } else {
                    break;
                }
            }
        }
        return distance;
    }


    public ArrayList<ArrayList<Double>> getTest_td() {
        return test_td;
    }

    public ArrayList<ArrayList<Double>> getConstantPattern() {
        return constantPattern;
    }

    public ArrayList<ArrayList<Double>> getTd() {
        return td;
    }

    public ArrayList<Long> getTd_time() {
        return td_time;
    }

    public ArrayList<ArrayList<Double>> getTd_cleaned() {
        return td_cleaned;
    }
}

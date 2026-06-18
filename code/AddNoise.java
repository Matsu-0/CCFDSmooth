import java.io.*;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Random;
import java.util.Scanner;
import java.util.Iterator;

public class AddNoise {
    private final ArrayList<ArrayList<Double>> origin_td;
    private final ArrayList<String> origin_time;
    private final ArrayList<ArrayList<Double>> test_td;
    private final int columnCnt;
    private String meta;
    private final int thr;
    private final int td_len;
    private final double noise_rate;
    private final double[] std;
    private final double[] lower_bound;
    private final double[] upper_bound;

    public AddNoise(int columnCnt, String originFileName, String targetFileName, double[] lower_bound,
            double[] upper_bound, String method, int thr, int td_len, double noise_rate) throws FileNotFoundException {
        this.columnCnt = columnCnt;
        this.origin_td = new ArrayList<>();
        this.origin_time = new ArrayList<>();
        this.test_td = new ArrayList<>();
        this.std = new double[this.columnCnt];
        this.lower_bound = lower_bound;
        this.upper_bound = upper_bound;
        this.thr = thr;
        this.td_len = td_len;
        this.noise_rate = noise_rate;
        this.loadOriginData(originFileName);

        if (method.equals("pt"))
            this.addNoise();
        else if (method.equals("seg"))
            this.addNoiseSeg();

        this.fillNullValue();
        this.writeToTargetFile(targetFileName);

        this.saveOriginalData();
        this.saveDirtyData();
    }

    private void loadOriginData(String originFileName) throws FileNotFoundException {
        Scanner sc = new Scanner(new File(originFileName));
        sc.useDelimiter("\\s*(,|\\r|\\n)\\s*");
        this.meta = sc.nextLine();
        for (int k = td_len; k > 0 && sc.hasNextLine(); --k) {
            String[] line_str = sc.nextLine().split(",");
            origin_time.add(line_str[0]);
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
            origin_td.add(values);
        }
    }

    public double[] getStd() {
        return this.std;
    }

    private double varianceImperative(double[] value) {
        double average = 0.0;
        int cnt = 0;
        for (double p : value) {
            if (!Double.isNaN(p)) {
                cnt += 1;
                average += p;
            }
        }
        if (cnt == 0) {
            return 0d;
        }
        average /= cnt;

        double variance = 0.0;
        for (double p : value) {
            if (!Double.isNaN(p)) {
                variance += (p - average) * (p - average);
            }
        }
        return variance / cnt;
    }

    private double[] getColumn(int pos) {
        double[] column = new double[this.origin_td.size()];
        for (int i = 0; i < this.origin_td.size(); i++) {
            column[i] = this.origin_td.get(i).get(pos);
        }
        return column;
    }

    private void addNoise() {
        Random random = new Random();
        for (int i = 0; i < this.columnCnt; i++) {
            std[i] = Math.sqrt(this.varianceImperative(getColumn(i)));
        }
        // ArrayList<Double> noise_std = new ArrayList<>(Arrays.asList(21.0 / 6, 11.0 /
        // 6));
        ArrayList<Double> noise_std = new ArrayList<>(Arrays.asList(21.0 / 6, 11.0 /
                6, std[2]));
        for (ArrayList<Double> o_tuple : this.origin_td) {
            Random r = new Random();
            int i1 = r.nextInt(10);
            if (i1 < this.thr) {
                ArrayList<Double> new_tuple = new ArrayList<>();

                for (int i = 0; i < this.columnCnt; i++) {
                    if (!Double.isNaN(o_tuple.get(i))) {
                        double new_value = o_tuple.get(i) + noise_std.get(i) * random.nextGaussian() * this.noise_rate;
                        if (new_value < this.lower_bound[i]) {
                            new_value = this.lower_bound[i];
                        }
                        if (new_value > this.upper_bound[i]) {
                            new_value = this.upper_bound[i];
                        }
                        BigDecimal b = new BigDecimal(new_value);
                        new_tuple.add(b.setScale(2, BigDecimal.ROUND_HALF_UP).doubleValue());
                    } else {
                        new_tuple.add(Double.NaN);
                    }
                }

                this.test_td.add(new_tuple);
            } else {
                this.test_td.add(o_tuple);
            }
        }
    }

    private void addNoiseSeg() {
        Random random = new Random();
        for (int i = 0; i < this.columnCnt; i++) {
            std[i] = Math.sqrt(this.varianceImperative(getColumn(i)));
        }
        ArrayList<Double> noise_std = new ArrayList<>(Arrays.asList(21.0 / 6, 11.0 / 6, std[2]));

        Iterator<ArrayList<Double>> itr = this.origin_td.iterator();
        while (itr.hasNext()) {
            Random r = new Random();
            int i1 = r.nextInt(10);
            if (i1 < this.thr) {
                ArrayList<Double> noise = new ArrayList<>();
                for (int i = 0; i < this.columnCnt; i++) {
                    noise.add(noise_std.get(i) * random.nextGaussian() * this.noise_rate);
                }
                ArrayList<Double> noise_seg_std = new ArrayList<>(
                        Arrays.asList(noise.get(0) / 3, noise.get(1) / 3, noise.get(2) / 3));

                int seg_len = r.nextInt(10) + 5;
                while (seg_len-- > 0 && itr.hasNext()) {
                    ArrayList<Double> new_tuple = new ArrayList<>();
                    ArrayList<Double> o_tuple = itr.next();

                    for (int i = 0; i < this.columnCnt; i++) {
                        if (!Double.isNaN(o_tuple.get(i))) {
                            double new_value = o_tuple.get(i) + noise.get(i)
                                    + noise_seg_std.get(i) * random.nextGaussian();
                            if (new_value < this.lower_bound[i]) {
                                new_value = this.lower_bound[i];
                            }
                            if (new_value > this.upper_bound[i]) {
                                new_value = this.upper_bound[i];
                            }
                            BigDecimal b = new BigDecimal(new_value);
                            new_tuple.add(b.setScale(2, BigDecimal.ROUND_HALF_UP).doubleValue());
                        } else {
                            new_tuple.add(Double.NaN);
                        }
                    }

                    this.test_td.add(new_tuple);
                }
            } else {
                ArrayList<Double> o_tuple = itr.next();
                this.test_td.add(o_tuple);
            }
        }
    }

    public void fillNullValue() {
        for (int i = 0; i < columnCnt; i++) {
            double temp = this.test_td.get(0).get(i);
            for (ArrayList<Double> arrayList : this.test_td) {
                if (Double.isNaN(arrayList.get(i))) {
                    arrayList.set(i, temp);
                } else {
                    temp = arrayList.get(i);
                }
            }
        }
    }

    private void writeToTargetFile(String targetFileName) {
        File writeFile = new File(targetFileName);

        try {
            BufferedWriter writeText = new BufferedWriter(new FileWriter(writeFile));
            writeText.write(this.meta);
            for (int j = 0; j < test_td.size(); j++) {
                ArrayList<Double> t_tuple = test_td.get(j);
                writeText.newLine();
                writeText.write(origin_time.get(j) + ",");
                for (int i = 0; i < columnCnt - 1; i++) {
                    if (!Double.isNaN(t_tuple.get(i))) {
                        writeText.write(String.valueOf(t_tuple.get(i)));
                    }
                    writeText.write(",");
                }
                if (!Double.isNaN(t_tuple.get(columnCnt - 1))) {
                    writeText.write(String.valueOf(t_tuple.get(columnCnt - 1)));
                }
            }
            writeText.flush();
            writeText.close();
        } catch (IOException e) {
            System.out.println("Error");
        }
    }

    private void saveDirtyData() {
        String targetFileName = "data/fuel/repair_results/fuel_" + td_len + "_dirty.csv";
        File writeFile = new File(targetFileName);

        try {
            BufferedWriter writeText = new BufferedWriter(new FileWriter(writeFile));

            for (int j = 0; j < test_td.size(); j++) {
                ArrayList<Double> t_tuple = test_td.get(j);
                writeText.newLine();
                writeText.write(j + ",");
                for (int i = 0; i < columnCnt - 1; i++) {
                    if (!Double.isNaN(t_tuple.get(i))) {
                        writeText.write(String.valueOf(t_tuple.get(i)));
                    }
                    writeText.write(",");
                }
                if (!Double.isNaN(t_tuple.get(columnCnt - 1))) {
                    writeText.write(String.valueOf(t_tuple.get(columnCnt - 1)));
                }
            }
            writeText.flush();
            writeText.close();
        } catch (IOException e) {
            System.out.println("Error");
        }
    }

    private void saveOriginalData() {
        String targetFileName = "data/fuel/repair_results/fuel_" + td_len + "_original.csv";
        File writeFile = new File(targetFileName);

        try {
            BufferedWriter writeText = new BufferedWriter(new FileWriter(writeFile));

            for (int j = 0; j < origin_td.size(); j++) {
                ArrayList<Double> t_tuple = origin_td.get(j);
                writeText.newLine();
                writeText.write(j + ",");
                for (int i = 0; i < columnCnt - 1; i++) {
                    if (!Double.isNaN(t_tuple.get(i))) {
                        writeText.write(String.valueOf(t_tuple.get(i)));
                    }
                    writeText.write(",");
                }
                if (!Double.isNaN(t_tuple.get(columnCnt - 1))) {
                    writeText.write(String.valueOf(t_tuple.get(columnCnt - 1)));
                }
            }
            writeText.flush();
            writeText.close();
        } catch (IOException e) {
            System.out.println("Error");
        }
    }

    public ArrayList<ArrayList<Double>> getTest_td() {
        return test_td;
    }
}

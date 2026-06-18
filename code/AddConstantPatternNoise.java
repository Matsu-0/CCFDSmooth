import java.util.ArrayList;
import java.util.Random;

public class AddConstantPatternNoise {
    public static ArrayList<ArrayList<Double>> copyConstantPattern(ArrayList<ArrayList<Double>> constantPattern) {
        ArrayList<ArrayList<Double>> copy = new ArrayList<>();
        for (ArrayList<Double> tuple : constantPattern) {
            copy.add(new ArrayList<>(tuple));
        }
        return copy;
    }

    public static ArrayList<ArrayList<Double>> addNoise(
            ArrayList<ArrayList<Double>> constantPattern,
            double noiseRate,
            double[] lowerBound,
            double[] upperBound) {
        return addNoise(constantPattern, noiseRate, lowerBound, upperBound, -1L);
    }

    /**
     * @param seed &gt;= 0 时使用固定种子；&lt; 0 则随机
     */
    public static ArrayList<ArrayList<Double>> addNoise(
            ArrayList<ArrayList<Double>> constantPattern,
            double noiseRate,
            double[] lowerBound,
            double[] upperBound,
            long seed) {
        Random rand = seed >= 0 ? new Random(seed) : new Random();
        ArrayList<ArrayList<Double>> noisyConstantPattern = new ArrayList<>();
        int dims = lowerBound.length;

        for (ArrayList<Double> tuple : constantPattern) {
            ArrayList<Double> newTuple = new ArrayList<>();
            for (int d = 0; d < dims; d++) {
                double val = d < tuple.size() ? tuple.get(d) : Double.NaN;

                if (!Double.isNaN(val) && rand.nextDouble() < noiseRate) {
                    double range = upperBound[d] - lowerBound[d];
                    double noise = (rand.nextDouble() - 0.5) * 0.5 * range;
                    val = Math.min(upperBound[d], Math.max(lowerBound[d], val + noise));
                }

                newTuple.add(val);
            }
            noisyConstantPattern.add(newTuple);
        }
        return noisyConstantPattern;
    }
}

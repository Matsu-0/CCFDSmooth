# Downstream application evaluation (Appendix Fig.17)

Reproduce **Fuel / Weather / Road** prediction (LSTM → RMSE) and **GPS** classification
(DTW / 1NN → Accuracy), comparing:

`Dirty · CCFDSmooth · SCREEN · LsGreedy · UniClean · MTSClean`

## Pipeline

1. **Java** (`ExperimentDownstream`) — inject noise + export Dirty / CCFD / SCREEN / LsGreedy / UniClean CSVs under `result/downstream/<dataset>/`
2. **Python MTSClean** — repair the same dirty CSV (`mtsclean_repair.py`)
3. **Python apps** — LSTM forecast or 1NN classify; write standalone `.dat` under `result/downstream/`

## How to run

From the **repository root**:

```bash
# 1) Java repairs (default thr=3 ≈30% points; noise_rate=5×std offset)
mkdir -p code/out
javac -d code/out code/*.java code/Algorithm/*.java code/Algorithm/util/*.java
java -cp code/out ExperimentDownstream --td-len 20000

# Larger offset only (same corruption %):
# java -cp code/out ExperimentDownstream --td-len 20000 --thr 3 --noise-rate 8

# Optional: one dataset only (fuel | gps | weather | road)
java -cp code/out ExperimentDownstream --td-len 20000 fuel

# 2) MTSClean + downstream metrics → result/downstream/*.dat
python3 code/downstream/run_downstream.py --datasets fuel,gps,weather,road

# Skip MTSClean if only Java methods are needed
python3 code/downstream/run_downstream.py --skip-mtsclean --datasets fuel
```

Outputs under **`result/downstream/`** (same root as Java repair CSVs; does **not** overwrite `figure/`):

| File | Content |
|------|---------|
| `engine\|gps\|weather\|road/*.csv` | Clean / dirty / repaired series |
| `fuel_prediction.dat` | Fuel LSTM RMSE |
| `weather_prediction.dat` | Weather LSTM RMSE |
| `road_prediction.dat` | Road LSTM RMSE |
| `gps_classification.dat` | GPS accuracy |
| `applications.dat` | All datasets in one table |
| `summary.json` | Same metrics as JSON |

## Protocol (aligned with appendix)

- **Prediction (Fuel / Weather / Road):** Train one LSTM on clean data; test with each method's series as input; z-scored RMSE vs clean targets. Dirty inputs raise RMSE.
- **Classification (GPS):** segment trajectories; pseudo-labels = KMeans (4 modes) on **clean** segments; 1NN on method segments (Euclidean flattened windows by default; `--exact-dtw` for classic DTW on a small train set).

Prefer `--td-len 30000+` for trends closer to Fig.17. Absolute values depend on `td_len`, seeds, and GPS pseudo-labels.

## Dependencies

```bash
pip install numpy pandas scikit-learn torch tqdm scipy
# plus MTSClean extras if running mtsclean (see baseline/DataQualityGroup-MTSClean/experiments/requirements.txt)
```

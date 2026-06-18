# CCFDSmooth

Time series data cleaning and smoothing using **constant CFD (CCFD)** with **constant patterns** as reference tuples. This repository contains the Java implementation of the proposed repair method and baselines used in the paper experiments.

For theoretical proofs, full experimental results, and application evaluation, see `appendix.pdf`.

---

## Repository layout

| Path | Description |
|------|-------------|
| `code/` | Java source: data loading, noise injection, repair algorithms, evaluation, and experiment entry points |
| `data/` | Application datasets (e.g. road network time series and constant patterns) |
| `baseline/` | External baselines (UniClean, MTSClean, etc.) |
| `figure/` | Gnuplot scripts and result data for paper figures |
| `appendix.pdf` | Supplementary material |

---

## Code overview

All experiment code lives under `code/`. There is no external build tool; compile with `javac` and run from the repository root.

### Directory structure

```
code/
├── Experiment.java                    # Main multi-method comparison experiment
├── ExperimentConstantPatternNoise.java  # Constant-pattern noise rate sweep
├── LoadData.java                      # Load time series and constant patterns
├── AddNoise.java                      # Inject synthetic noise into time series
├── AddConstantPatternNoise.java       # Inject noise into constant patterns
├── CalStd.java                        # Per-column standard deviation from clean TD
├── Analysis.java                      # RMSE / MAE / repair-cost metrics
└── Algorithm/
    ├── CCFDRepair.java                # Proposed global CCFD repair (this work)
    ├── KNNRepair.java                 # 1-NN over constant patterns
    ├── EditingRuleRepair.java         # Editing-rule repair
    ├── UniCleanRepair.java            # UniClean-style three-phase repair
    ├── SCREEN.java                    # SCREEN smoothness repair
    ├── Lsgreedy.java                  # Local greedy repair
    ├── EWMARepair.java                # Exponential weighted moving average
    └── util/
        ├── KDTreeUtil.java            # KD-tree over constant patterns
        ├── ScreenUtil.java
        ├── LsgreedyUtil.java
        └── Pair.java
```

### Core modules

| Module | Role |
|--------|------|
| `LoadData` | Reads clean time series (`td`) with timestamps and samples constant-pattern tuples. Supports sequential, stratified, and random sampling (`loadConstantPatternRandom` is used by default). |
| `AddNoise` | Builds dirty input `test_td` from clean series or a pre-generated dirty CSV (`pt` point noise or `seg` segment noise). |
| `AddConstantPatternNoise` | Optionally corrupts constant patterns at a given rate (for robustness experiments). |
| `CalStd` | Column-wise standard deviation from clean `td`, used to normalize distances and errors. |
| `Analysis` | Evaluates repair quality and cost. Constant patterns are used for **evaluation filtering** (and by CCFD / KNN / ER / UniClean for repair), not by SCREEN / LsGreedy / EWMA. |

### Repair methods

| Class | Short name | Uses constant pattern for repair? |
|-------|------------|-----------------------------------|
| `CCFDRepair` | **ccfd** | Yes (KD-tree candidates + smoothness) |
| `KNNRepair` | knn | Yes (nearest neighbor) |
| `EditingRuleRepair` | er | Yes (editing rules) |
| `UniCleanRepair` | uniclean | Yes (cRepair / eRepair / hRepair) |
| `SCREEN` | screen | No |
| `Lsgreedy` | lsgreedy | No |
| `EWMARepair` | ewma | No |

`CCFDRepair` is the main contribution: it builds a KD-tree over constant patterns, selects candidate repairs per timestamp using a temporal window and smoothness threshold η, and resolves cold-start segments via window decomposition.

Baseline details for UniClean and MTSClean are documented under `baseline/UniClean/README.md` and `baseline/DataQualityGroup-MTSClean/`.

---

## Experiments

Edit static fields at the top of each experiment class (`tdPath`, `constantPatternPath`, `td_len`, etc.), compile, and run from the repo root:

```bash
mkdir -p code/out
javac -d code/out code/Experiment.java
java -cp code/out Experiment
```

### Multi-method comparison (`Experiment.java`)

**Purpose.** Compare all repair methods on one dataset under the same noisy input and report accuracy / repair cost.

**How it runs.**

1. **Load data** — `LoadData` reads `td_len` rows of clean time series (`td`, with timestamps) and randomly samples `constantPatternLen` tuples from the constant-pattern file.
2. **Normalize** — `CalStd` computes per-column `std` from clean `td`.
3. **Corrupt patterns (optional)** — If `constantPatternNoiseRate > 0`, `AddConstantPatternNoise` perturbs constant patterns before repair.
4. **Build dirty input** — `AddNoise` produces `test_td` (reads `testTdPath` if present, otherwise synthesizes point/segment noise with `method`, `thr`, `noise_rate`).
5. **Repair** — Call each `*Repair` hook in turn on the same `test_td`; algorithms that use constant patterns receive the (possibly noisy) pattern set.
6. **Evaluate** — `Analysis` compares each `td_cleaned` against clean `td` (RMSE, MAE) and against `test_td` (repair count, repair distance). Only rows consistent with some constant-pattern tuple are included in accuracy metrics.
7. **Output** — Metrics printed to stdout; repaired series written to `result/engine/fuel_<td_len>_<method>.csv`.

**Methods invoked:** ccfd, knn, er, screen, lsgreedy, ewma, uniclean (via `ccfdRepair`, `knnRepair`, …, `uniCleanRepair`).

### Constant-pattern noise sweep (`ExperimentConstantPatternNoise.java`)

**Purpose.** Measure how repair quality changes when constant patterns are increasingly noisy, while time-series noise stays fixed (paper figure: varying pattern noise).

**How it runs.**

1. **Load data** — Same as above: clean `td`, timestamps, and a copy of clean constant patterns (`cleanConstantPattern`).
2. **Fix TD noise once** — `AddNoise` builds a single `test_td` shared by all sweep points.
3. **Sweep pattern noise** — For each rate in `CONSTANT_PATTERN_NOISE_RATES` (default `0, 0.05, …, 0.25`, overridable with `--rates`):
   - Re-noise a fresh copy of `cleanConstantPattern` at that rate (seed `1000 + r`).
   - Run **ccfd**, **knn**, **er**, **uniclean** via `Experiment.*Repair`.
4. **Output** — RMSE table to stdout; gnuplot `.dat` file to `resultDatPath`.

```bash
javac -d code/out code/ExperimentConstantPatternNoise.java
java -cp code/out ExperimentConstantPatternNoise
java -cp code/out ExperimentConstantPatternNoise --rates 0,0.05,0.1,0.15,0.2,0.25
```

### Evaluation (`Analysis.java`)

Used by both experiments. **RMSE / MAE** use clean `td` as ground truth on rows that are complete and within tolerance of at least one constant-pattern tuple. **Repair count / distance** measure change from `test_td` to `td_cleaned`. Constant patterns filter evaluation rows; SCREEN / LsGreedy / EWMA do not use them during repair.

---

## Quick start

```bash
mkdir -p code/out
javac -d code/out code/Experiment.java
java -cp code/out Experiment
```

Reduce `td_len` in `Experiment.java` for a quicker test run.

---

## Related work in this repo

- **UniClean baseline**: `baseline/UniClean/README.md` — maps Fan et al. (SIGMOD 2011) to `UniCleanRepair.java`.
- **MTSClean baseline**: `baseline/DataQualityGroup-MTSClean/` — Python constraint-based cleaning; see `experiments/README_JAVA_ALIGNED.md` for Java-aligned RMSE.

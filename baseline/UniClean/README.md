# UniClean Baseline (Fan et al., SIGMOD 2011 / JDIQ 2014)

This directory documents how the **UniClean** three-stage cleaning pipeline from *Interaction between record matching and data repairing* is implemented and used in this repository.

Implementation: `code/Algorithm/UniCleanRepair.java` (same package as other baselines in `Experiment.java` for easy compilation and execution).

## Paper method (adaptation notes)

The original work targets relational data with **CFD + constant pattern + constant patterns**, in three stages:

| Stage | Paper algorithm | This implementation |
|-------|-----------------|---------------------|
| 1 | **cRepair**: deterministic fix based on attribute confidence η | Initialize confidence from distance to constant patterns; constant-pattern-style rules (repair remaining attributes when some attributes match) |
| 2 | **eRepair**: reliable fix based on entropy threshold δ₂ | Bucket tuples by LHS, compute RHS entropy per group, repair with majority value when entropy is low |
| 3 | **hRepair**: heuristic repair extending Cong et al., VLDB'07 | Preserve deterministic attributes; for the rest, use editing-rule-style constant pattern matching + KNN fallback |

Continuous numeric time series have no discrete CFD patterns, so:

- **Constant pattern**: Similar to `EditingRuleRepair`, allow `columnCnt - diff` attributes to match a constant pattern within tolerance, then repair the other columns.
- **Variable CFD**: Treat remaining columns as RHS; quantize LHS columns by `tolerance` into buckets and apply within-group entropy repair.

## Default parameters

| Parameter | Default | Meaning |
|-----------|---------|---------|
| `eta` | 0.8 | Confidence threshold η |
| `delta1` | 3 | Max modifications per attribute (eRepair) |
| `delta2` | 0.85 | Normalized entropy upper bound (reliable fix when below) |

Adjust these in the `UniCleanRepair` constructor.

## Running

```bash
cd /path/to/CCFDSmooth
javac code/Experiment.java code/Algorithm/*.java code/Algorithm/util/*.java
java -cp code Experiment
```

`Experiment` prints the UniClean RMSE section and writes results to `result/engine/fuel_<td_len>_uniclean.csv` (same convention as other methods).

## Reference

Wenfei Fan, Jianzhong Li, Shuai Ma, Nan Tang, Wenyuan Yu. *Interaction between record matching and data repairing.* SIGMOD 2011.  
Extended version: ACM JDIQ, 2014.

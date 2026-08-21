# TimescaleDB Same-Database Baseline

Reproduces the Figure 13 logic (**Candidate Search** / **Smoothing Query**) with **TimescaleDB as a same-database deployment**: time series and constant patterns live in **one** Timescale/PostgreSQL instance (hypertable + pgvector).

This is the setup promised to reviewers (TimescaleDB supports hypertables and vector indexes together). It is **not** the old cross-database baseline `PostgreSQL(Vector)+IoTDB`.

## Layout

```
baseline/TimescaleDB/
├── README.md
├── requirements.txt
├── config.py                 # DSN + per-dataset paths / ω, η, k
├── db.py
├── load_data.py              # load CSV → hypertable + pattern vectors
├── neighbor.py               # κ-NN via pgvector
├── smooth.py                 # CCFDSmooth-style repair (mirrors code/Algorithm/CCFDRepair)
├── bench_candidate_search.py # metric (a)
├── bench_smoothing_query.py  # metric (b)
├── run_all.py                # run benches + write results/*.dat
├── sql/01_extensions.sql
└── results/                  # database-knn-time.dat / database-repair-time.dat
```

## Prerequisites

1. PostgreSQL with **TimescaleDB** and **pgvector**
2. Python 3.10+
3. Datasets under `data_repair/{weather,engine,gps,road}/`

```bash
cd baseline/TimescaleDB
pip install -r requirements.txt
```

If you see `Connection refused` on port 5432, PostgreSQL is not running yet.

### Option A — Native install (no Docker)

On macOS with [Homebrew](https://brew.sh):

```bash
# 1) PostgreSQL 16
brew install postgresql@16
brew services start postgresql@16
echo 'export PATH="/opt/homebrew/opt/postgresql@16/bin:$PATH"' >> ~/.zshrc   # Apple Silicon
# Intel: /usr/local/opt/postgresql@16/bin

# 2) TimescaleDB (must match the same PostgreSQL major version)
brew tap timescale/tap
brew install timescaledb
timescaledb-tune --quiet --yes
brew services restart postgresql@16

# 3) pgvector — build against the same PG (brew pgvector may target PG 17+)
git clone --branch v0.8.1 https://github.com/pgvector/pgvector.git /tmp/pgvector
cd /tmp/pgvector
export PG_CONFIG="$(brew --prefix postgresql@16)/bin/pg_config"
make && make install
cd -

# 4) Create DB + extensions
chmod +x setup_db_local.sh
./setup_db_local.sh
```

Homebrew PostgreSQL usually uses your **macOS username** with **no password** on localhost. `setup_db_local.sh` prints the correct `TIMESCALE_DSN`, e.g.:

```bash
export TIMESCALE_DSN="host=127.0.0.1 port=5432 dbname=ccfd_timescale user=matsu"
```

Or create a dedicated role (optional):

```bash
psql -d postgres -c "CREATE USER postgres WITH SUPERUSER PASSWORD 'postgres';"
export TIMESCALE_DSN="host=127.0.0.1 port=5432 dbname=ccfd_timescale user=postgres password=postgres"
```

**Linux:** install `postgresql-16`, TimescaleDB, and pgvector from your distro or [Timescale docs](https://docs.timescale.com/self-hosted/latest/install/), then run `./setup_db_local.sh`.

**Remote server:** any PostgreSQL 16+ with both extensions enabled; only set `TIMESCALE_DSN`.

### Option B — Docker

```bash
chmod +x setup_db.sh
./setup_db.sh
export TIMESCALE_DSN="host=127.0.0.1 port=5432 dbname=ccfd_timescale user=postgres password=postgres"
```

## How the experiment works

### Same-database storage

| Object | Table | Mechanism |
|--------|-------|-----------|
| Time series | `ccfd_<dataset>_ts` | Timescale **hypertable** on `ts` |
| Constant patterns | `ccfd_<dataset>_cp` | `VECTOR(d)` + **ivfflat** L2 index |
| Meta (std, ω, η, k) | `ccfd_dataset_meta` | used at query time |

Patterns are stored **normalized by column `std`** (same δ as `CCFDRepair` / `KDTreeUtil`).

### (a) Candidate Search

For every time-series point, run:

```sql
SELECT emb FROM ccfd_<dataset>_cp
ORDER BY emb <-> $query::vector
LIMIT k;
```

Report total wall-clock seconds (after a short warmup).

### (b) Smoothing Query

End-to-end path matching `CCFDRepair`:

1. Stream points from the hypertable (`ORDER BY ts`)
2. Build candidate set `C_i` via repeated κ-NN (current point + window neighbors)
3. Keep candidates that satisfy smoothness threshold η; pick the closest to the dirty point

Report total wall-clock seconds for the full pipeline.

## Run

Smoke test (Weather, first 5k points):

```bash
python load_data.py --dataset weather --td-limit 5000 --pattern-limit 2000
python bench_candidate_search.py --dataset weather --td-limit 5000
python bench_smoothing_query.py --dataset weather --td-limit 5000
```

Full Weather (paper-scale; slow):

```bash
python load_data.py --dataset weather
python run_all.py --datasets weather
```

All four datasets:

```bash
for d in weather engine gps road; do python load_data.py --dataset $d; done
python run_all.py --datasets weather,engine,gps,road
```

Outputs:

- `results/database-knn-time.dat` — candidate search (adds `TimescaleDB(Ours)` column beside existing Figure 13 numbers)
- `results/database-repair-time.dat` — smoothing query

## Notes

- Default ω / η / k follow the paper’s per-dataset settings in `config.py`.
- Road CSV timestamps may be malformed; the loader falls back to synthetic epochs so ordering is still well-defined.
- For fair comparison with Figure 13, use **full** series/patterns (`--td-limit` / `--pattern-limit` unset).
- Index type defaults to **ivfflat**; switch to HNSW in `load_data.py` if your pgvector build supports it and you need higher recall.

# Test datasets

Raw observations and solutions for the validation experiments reported in the
accompanying paper. Recorded on **2026-09-23**.

## Equipment and settings

| Item | Value |
|---|---|
| Rover | Google Pixel 7 (Android smartphone) |
| Base | CHCNAV i87 |
| Baseline | ≈ 130 m (short baseline) |
| Sample rate | 1 Hz |
| Correction source | Local base station (no CORS/NTRIP) |
| Ephemeris | Broadcast (`BRDM`) |
| Processing | RTKLIB EX 2.5.0 |
| Elevation mask | 15° |
| Ambiguity validation | ratio threshold 3.0, BDS AR on, GLO AR off |
| Ionosphere / troposphere | Broadcast model / Saastamoinen |

> No CORS or NTRIP service was used in these experiments, so the reported fix
> rates reflect a local-base-station configuration rather than network RTK.

## Directory layout

```
results/
├── common/
│   ├── base_chc-i87_20260923.26O.gz   CHCNAV i87 base observations (1 Hz)
│   └── brdc_2026266.rnx.gz            broadcast ephemeris, DOY 266 / 2026
├── static/
│   ├── rover_pixel7_20260923.26o.gz   Pixel 7 static observation
│   └── solution/
│       ├── rtk_fwd.pos                RTK forward (used for the static results)
│       ├── rtk_static.pos             RTK (alternative run)
│       ├── spp_static.pos             SPP
│       └── rtk_fwd_events.pos         RTK event log
└── kinematic/
    ├── rover_pixel7_20260923.26o.gz   Pixel 7 kinematic observation
    ├── rover_pnav_export.26o.gz       same session, exported by P-Nav itself
    ├── truth_ie_ppk.csv               reference trajectory (Inertial Explorer, PPK)
    └── solution/
        ├── rtk_final.pos              RTK combined (used for the kinematic results)
        └── spp_l1.pos                 SPP
```

`rover_pnav_export.26o.gz` is the RINEX 3.04 file written by the P-Nav app
itself, provided so that the app's export can be compared against the
GnssLogger-derived file for the same session.

## Sessions

| Session | Epochs | Duration | Reference |
|---|---|---|---|
| Static | 3077 | ≈ 51.3 min (11:43:07 – 12:34:23) | IE static solution |
| Kinematic | 562 | ≈ 9.4 min (12:51:49 – 13:01:10) | IE PPK |

The single base-station file in `common/` spans 11:38:23 – 13:11:06 and covers
both sessions.

Static reference position (from the IE solution, used as the local origin):

```
lat = 28.722085553°   lon = 115.815642469°   h = 72.773 m
```

## Using these files

The `.pos` files are RTKLIB's text solution format. Column 6 is the solution
quality flag:

| Q | Meaning |
|---|---|
| 1 | fixed (integer ambiguities resolved) |
| 2 | float |
| 4 | DGPS |
| 5 | single point |

Horizontal error is obtained by transforming the solution and the reference to
a local ENU frame, then evaluating the per-epoch differences. All accuracy
figures in the paper use only E and N; the vertical component is not assessed.

The observations can be re-processed directly with RTKLIB:

```bash
# RTK, kinematic session
rnx2rtkp -x 3 -p 0 \
    -o rtk.pos \
    results/kinematic/rover_pixel7_20260923.26o.gz \
    results/common/base_chc-i87_20260923.26O.gz \
    results/common/brdc_2026266.rnx.gz
```

A local `ENU` conversion using the static reference position above reproduces
the error series and statistics reported in the paper.

## Notes on the RINEX observation codes

The observation files in this directory were written before the BDS B2a
tracking-code fix described in the paper, and therefore declare `C5Q` for BDS
B2a. `5Q` is valid for GPS L5 and Galileo E5a but **not** for BDS B2a, whose
permitted codes are `5D`, `5P` and `5X`. Current P-Nav builds write `5P`.

The effect on the results reported here is limited: RTKLIB resolves an
observation code by its band digit only (`code2freq_BDS` switches on `obs[0]`),
so `C5Q` is still mapped to the B2a frequency and no observations were dropped
in the RTKLIB processing used for the paper. Stricter RINEX readers may reject
the code, which is why it was corrected in the writer.

## License

The observation data are released under the same BSD 2-Clause license as the
rest of this repository.

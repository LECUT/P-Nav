#include "gnss_quality.h"
#include <stdlib.h>
#include <string.h>
#include <math.h>
#include <stdio.h>
#include <pthread.h>
#include <android/log.h>

#define LOG_TAG "MPN"
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)

/* ═══════════════════════════════════════════════════════════════════════
 * Internal types
 * ═══════════════════════════════════════════════════════════════════════ */

#define SAT_HASH_SIZE   256
#define CMC_BUF_SIZE    120
#define MP_BUF_SIZE     20
#define GF_POLY_WIN     10
#define POLY_DEGREE     4     /* CMC polynomial detrend degree */
#define SLIP_THRESH_M   0.30  /* GF slip — L1/L2 */
#define GF_WARMUP       5     /* 新L2跟踪后跳过前N个历元的GF检测 */
#define MW_THRESHOLD    4.0   /* MW recursive sigma multiplier */

/* ── Per-satellite state ────────────────────────────────────────────── */

typedef struct {
    int    key;            /* constellation * 1000 + svid */
    int    active;         /* 1 if slot is occupied */
    int    sys_idx;        /* pre-mapped system index 0..6 */

    /* SNR accumulation */
    double snr_sum;
    int    snr_count;

    /* CMC arc (L1 pseudorange - L1 phase) */
    int    cmc_head;
    int    cmc_len;
    double cmc_vals[CMC_BUF_SIZE];   /* CMC values */
    int    cmc_epochs[CMC_BUF_SIZE]; /* epoch index for x-axis */

    /* GF previous (L1 - L2) */
    double prev_gf;
    int    has_prev_gf;
    int    gf_warmup;               /* epochs since L2 started; skip GF check until GF_WARMUP */

    /* MW recursive state */
    int    mw_arc_idx;
    double mw_mean;
    double mw_var;

    /* GF ring buffer for polyfit slip detection */
    int    gf_head;
    int    gf_len;
    double gf_vals[GF_POLY_WIN];

    /* Per-satellite CMC residual tracking (FDE) */
    double cmc_resid_sumsq;
    int    cmc_resid_count;

    /* MP ring buffers (N_sw = 20) */
    int    mp_head;
    int    mp_len;
    double mp1_vals[MP_BUF_SIZE];
    double mp2_vals[MP_BUF_SIZE];
    /* 时间差分法：用相邻历元差避免相对 TOW 参考系跳变
       mp_raw = Σ dMP, 从弧首 0 开始累积，弧段均值相减后常数偏差自动消除 */
    double mp_prev_p1;   /* 上一历元 L1 PR (m) */
    double mp_prev_l1;   /* 上一历元 L1 ADR (m) */
    double mp_prev_l2;   /* 上一历元 L2/L5 ADR (m) */
    int    mp_has_prev;  /* 上一历元数据是否有效 */
    double mp_raw1;      /* 累积 MP1 原始值 */
    double mp_raw2;      /* 累积 MP2 原始值 */

    /* Ion rate: previous (GF, time_nanos) */
    double  prev_gf_ion;
    int64_t prev_time_ion;
    int     has_prev_ion;

    /* TD: previous-epoch P1(m) and L1(cycles) for triple-difference */
    double  prev_p1_td;        /* P1 pseudorange in metres */
    double  prev_l1_cycles;    /* L1 carrier phase in cycles */
    float   prev_elev;         /* elevation for ref-sat selection */
    int     has_prev_td_pr;    /* 1 = valid previous-epoch P1 for TD-PR */
    int     has_prev_td_cp;    /* 1 = valid previous-epoch L1 for TD-CP */
} sat_state_t;

/* ── Global quality state ───────────────────────────────────────────── */

/* Per-epoch snapshot for time-series charting */
typedef struct {
    int   epoch;
    float snr_mean[7];
    float cmc_rms[7];
    float mp1_sw_rms[7];
    float ion_rate[7];
    float ion_rate_k2[7];
    int   slip_combined[7];
    int   sat_count[7];
    float completeness[7];      /* per-epoch data completeness ratio */
    int   elev_bins[7][ELEV_BIN_COUNT];
    float td_pr_noise_rms[7];
    float td_cp_noise_rms[7];
    float mp2_sw_rms[7];
    float mp5_sw_rms[7];
    float gf_rms[7];
    float elev_mean[7];         /* per-system mean elevation (deg) */
} epoch_snapshot_t;

#define TSS_RING_SIZE TSS_BUF_SIZE

static struct {
    int initialized;
    pthread_mutex_t mutex;

    /* Per-system SNR bins [sys_idx][bin] */
    int snr_bins[7][MAX_SNR_BINS];

    /* Per-system CMC residuals (squared sum for RMS) */
    double cmc_sumsq[7];
    int    cmc_count[7];

    /* Per-system slip counters */
    int gf_slips[7];
    int mw_slips[7];
    int gf_poly_slips[7];

    /* Per-system MP residuals */
    double mp1_sumsq[7];
    int    mp1_count[7];
    double mp2_sumsq[7];
    int    mp2_count[7];
    double mp5_sumsq[7];
    int    mp5_count[7];
    double mp1_sw_sum[7];
    int    mp1_sw_count[7];
    double mp2_sw_sum[7];
    int    mp2_sw_count[7];
    double mp5_sw_sum[7];
    int    mp5_sw_count[7];

    /* Per-system TD noise */
    double td_pr_sumsq[7];
    int    td_pr_count[7];
    double td_cp_sumsq[7];
    int    td_cp_count[7];

    /* Per-system GF epoch-diff (for timeseries) */
    double gf_diff_sumsq[7];
    int    gf_diff_count[7];

    /* Per-system ion rate */
    double ion_rate_sum[7];
    int    ion_rate_count[7];
    double ion_rate_k2_sum[7];
    int    ion_rate_k2_count[7];

    /* ADR availability */
    int has_adr;

    /* Per-system elevation bins */
    int    elev_bins[7][ELEV_BIN_COUNT];

    /* Per-system completeness / fullness */
    int    total_obs_sys[7];
    int    dual_freq_obs_sys[7];
    int    sat_epoch_sum[7];    /* Σ(sats_in_epoch) over all epochs */
    int    epoch_count_sys[7];  /* epochs for this system */
    int    epoch_obs[7];        /* per-epoch observation count */
    int    epoch_sat_count[7];  /* per-epoch satellite count (for completeness denominator) */
    int    epoch_dual[7];       /* per-epoch dual-freq satellite count */

    /* Satellite hash table */
    sat_state_t *sat_table;
    int          sat_capacity;

    /* Global epoch counter */
    int epoch_counter;
    int total_obs_all;

    /* Time-series ring buffer for chart display */
    epoch_snapshot_t tss_ring[TSS_RING_SIZE];
    int              tss_head;   /* next write position */
    int              tss_count;  /* total snapshots written (may exceed RING_SIZE) */

} g_state;

/* ═══════════════════════════════════════════════════════════════════════
 * Frequency band helpers
 * ═══════════════════════════════════════════════════════════════════════ */

static int freq_is_l1_gps(double f)  { return fabs(f - 1575.42e6) < 10.0e6; }
static int freq_is_l1_bds(double f)  { return fabs(f - 1561.098e6) < 10.0e6 || fabs(f - 1575.42e6) < 10.0e6; } /* B1I + B1C */
static int freq_is_l1_gal(double f)  { return fabs(f - 1575.42e6) < 10.0e6; }
static int freq_is_g1_glo(double f)  { return f > 1598.0e6 && f < 1606.0e6; }
static int freq_is_l2_gps(double f)  { return fabs(f - 1227.60e6) < 10.0e6; }
static int freq_is_l2_bds(double f)  { return fabs(f - 1207.14e6) < 10.0e6; } /* B2b */
static int freq_is_l2_glo(double f)  { return fabs(f - 1246.00e6) < 10.0e6; }
static int freq_is_l5(double f)      { return fabs(f - 1176.45e6) < 10.0e6; }

/* Return 1 if freq is a primary L1/G1 band for this constellation */
static int is_l1_band(int cons, double f_hz) {
    switch (cons) {
        case 1: return freq_is_l1_gps(f_hz);       /* GPS L1 */
        case 2: return 0;                            /* SBS — no L1 defined */
        case 3: return freq_is_g1_glo(f_hz);         /* GLO G1 */
        case 4: return 0;                            /* QZS — skip for now */
        case 5: return freq_is_l1_bds(f_hz);         /* BDS B1I */
        case 6: return freq_is_l1_gal(f_hz);         /* GAL E1 */
        default: return 0;
    }
}

/* Return 1 if freq is a secondary L2/L5 band for this constellation */
static int is_l2_band(int cons, double f_hz) {
    switch (cons) {
        case 1: return freq_is_l2_gps(f_hz) || freq_is_l5(f_hz);
        case 3: return freq_is_l2_glo(f_hz);
        case 5: return freq_is_l2_bds(f_hz) || freq_is_l5(f_hz);
        case 6: return freq_is_l5(f_hz);
        default: return 0;
    }
}

/* Return MHz value for computing wavelength, MP combinations, etc. */
static double get_f1_mhz(int cons) {
    switch (cons) { case 1: return GPS_L1_MHZ; case 5: return BDS_B1I_MHZ;
        case 6: return GAL_E1_MHZ; case 3: return GLO_G1_MHZ; default: return 1575.42; }
}

static double get_f2_mhz(int cons, double f2_hz) {
    if (freq_is_l2_gps(f2_hz) || freq_is_l2_glo(f2_hz) || freq_is_l2_bds(f2_hz))
        return f2_hz / 1.0e6;
    if (freq_is_l5(f2_hz)) return 1176.45;
    /* fallback: L2 GPS */
    return 1227.60;
}

/* Match constellation constant (1..6) to internal system index 0..6 */
static int cons_to_sys(int cons) {
    return (cons >= 0 && cons <= 6) ? cons : 0;
}

/* ═══════════════════════════════════════════════════════════════════════
 * Median + MAD helpers (for outlier rejection)
 * ═══════════════════════════════════════════════════════════════════════ */

static int _cmp_double(const void *a, const void *b) {
    double d = *(const double *)a - *(const double *)b;
    return d < 0 ? -1 : (d > 0 ? 1 : 0);
}

static double median_of(double *vals, int n) {
    if (n <= 0) return NAN;
    qsort(vals, n, sizeof(double), _cmp_double);
    if (n % 2 == 1) return vals[n / 2];
    return 0.5 * (vals[n / 2 - 1] + vals[n / 2]);
}

/* Returns MAD threshold = max(fallback, multiplier * MAD).
   On return, sorted[0..sorted_n-1] holds the sorted copy. */
static double mad_threshold(double *sorted, int n, double multiplier, double fallback) {
    if (n < 3) return fallback;
    double med = median_of(sorted, n);
    /* Compute absolute deviations, reuse second half of array */
    for (int i = 0; i < n; i++)
        sorted[i] = fabs(sorted[i] - med);
    double mad = median_of(sorted, n);
    if (mad <= 0.0) return fallback;
    double thr = multiplier * mad;
    return thr > fallback ? thr : fallback;
}

/* ═══════════════════════════════════════════════════════════════════════
 * Satellite hash table
 * ═══════════════════════════════════════════════════════════════════════ */

static int sat_hash(int key) { return (unsigned int)(key * 2654435761u) % SAT_HASH_SIZE; }

static sat_state_t *sat_get(int cons, int svid) {
    int key = cons * 1000 + svid;
    int idx = sat_hash(key);
    for (int i = 0; i < SAT_HASH_SIZE; i++) {
        int probe = (idx + i) % SAT_HASH_SIZE;
        sat_state_t *s = &g_state.sat_table[probe];
        if (!s->active) return s;        /* empty slot */
        if (s->key == key) return s;     /* found */
    }
    return NULL; /* table full — should never happen */
}

static sat_state_t *sat_get_or_create(int cons, int svid) {
    int key = cons * 1000 + svid;
    sat_state_t *s = sat_get(cons, svid);
    if (s == NULL) return NULL;
    if (!s->active) {
        memset(s, 0, sizeof(*s));
        s->active = 1;
        s->key = key;
        s->sys_idx = cons_to_sys(cons);
    }
    return s;
}

/* ═══════════════════════════════════════════════════════════════════════
 * Polynomial fitting (least-squares, normal equations + Gaussian elim)
 * ═══════════════════════════════════════════════════════════════════════ */

#define POLY_MAX_DEG 5

static int polyfit(const double *x, const double *y, int n, int deg, double *coeff) {
    /* Build normal equations: X^T X * coeff = X^T y */
    double A[POLY_MAX_DEG + 1][POLY_MAX_DEG + 2]; /* augmented matrix */
    int m = deg + 1;
    memset(A, 0, sizeof(A));
    for (int i = 0; i < n; i++) {
        double xi = x[i];
        double pow_xi[POLY_MAX_DEG + 1];
        pow_xi[0] = 1.0;
        for (int d = 1; d <= 2 * deg; d++)
            pow_xi[d] = pow_xi[d - 1] * xi;
        for (int r = 0; r < m; r++)
            for (int c = 0; c < m; c++)
                A[r][c] += pow_xi[r + c];
        for (int r = 0; r < m; r++)
            A[r][m] += pow_xi[r] * y[i];
    }
    /* Gaussian elimination with partial pivoting */
    for (int col = 0; col < m; col++) {
        int max_row = col;
        for (int row = col + 1; row < m; row++)
            if (fabs(A[row][col]) > fabs(A[max_row][col]))
                max_row = row;
        if (fabs(A[max_row][col]) < 1e-20) return -1; /* singular */
        if (max_row != col) {
            for (int c = col; c <= m; c++) {
                double tmp = A[col][c]; A[col][c] = A[max_row][c]; A[max_row][c] = tmp;
            }
        }
        double pivot = A[col][col];
        for (int c = col; c <= m; c++) A[col][c] /= pivot;
        for (int row = 0; row < m; row++) {
            if (row == col) continue;
            double factor = A[row][col];
            for (int c = col; c <= m; c++) A[row][c] -= factor * A[col][c];
        }
    }
    for (int i = 0; i < m; i++) coeff[i] = A[i][m];
    return 0;
}

static double polyval(const double *coeff, int deg, double x) {
    double result = 0.0;
    for (int i = deg; i >= 0; i--)
        result = result * x + coeff[i];
    return result;
}

/* ═══════════════════════════════════════════════════════════════════════
 * MW combination
 * ═══════════════════════════════════════════════════════════════════════ */

static double compute_mw(double l1_m, double l2_m, double p1_m, double p2_m,
                         double f1_mhz, double f2_mhz) {
    double f1 = f1_mhz * 1e6, f2 = f2_mhz * 1e6;
    double wl1 = CLIGHT / f1, wl2 = CLIGHT / f2;
    double wl_wl = CLIGHT / (f1 - f2);
    double phi_wl = l1_m / wl1 - l2_m / wl2;
    double p_nl = (f1 * p1_m + f2 * p2_m) / (f1 + f2);
    return phi_wl - p_nl / wl_wl;
}

/* ═══════════════════════════════════════════════════════════════════════
/* ═══════════════════════════════════════════════════════════════════════
 * Public API
 * ═══════════════════════════════════════════════════════════════════════ */

void quality_init(void) {
    quality_free();
    pthread_mutex_init(&g_state.mutex, NULL);
    pthread_mutex_lock(&g_state.mutex);
    g_state.sat_capacity = SAT_HASH_SIZE;
    g_state.sat_table = (sat_state_t *)calloc(SAT_HASH_SIZE, sizeof(sat_state_t));
    if (!g_state.sat_table) {
        g_state.sat_capacity = 0;
        pthread_mutex_unlock(&g_state.mutex);
        return;
    }
    g_state.initialized = 1;
    pthread_mutex_unlock(&g_state.mutex);
}

void quality_free(void) {
    if (!g_state.initialized && !g_state.sat_table) {
        memset(&g_state, 0, sizeof(g_state));
        return;
    }
    pthread_mutex_lock(&g_state.mutex);
    free(g_state.sat_table);
    g_state.sat_table = NULL;
    g_state.sat_capacity = 0;
    g_state.initialized = 0;
    pthread_mutex_unlock(&g_state.mutex);
    pthread_mutex_destroy(&g_state.mutex);
    memset(&g_state, 0, sizeof(g_state));
}

void quality_get_report(quality_report_t *r) {
    memset(r, 0, sizeof(*r));
    pthread_mutex_lock(&g_state.mutex);

    /* ── SNR bins + means ── */
    for (int sys = 0; sys < 7; sys++) {
        for (int b = 0; b < MAX_SNR_BINS; b++) {
            r->snr_bins[sys][b] = g_state.snr_bins[sys][b];
        }
        int total_bin = 0;
        for (int b = 0; b < MAX_SNR_BINS; b++) total_bin += g_state.snr_bins[sys][b];
        if (total_bin > 0) {
            for (int b = 0; b < MAX_SNR_BINS; b++)
                r->snr_bin_pct[sys][b] = 100.0 * g_state.snr_bins[sys][b] / total_bin;
        }
        /* Per-satellite mean SNR: average across tracked satellites */
        double sat_mean_sum = 0.0;
        int sat_mean_count = 0;
        for (int i = 0; i < g_state.sat_capacity; i++) {
            sat_state_t *s = &g_state.sat_table[i];
            if (!s->active || s->sys_idx != sys || s->snr_count == 0) continue;
            sat_mean_sum += s->snr_sum / s->snr_count;
            sat_mean_count++;
        }
        r->snr_mean[sys] = sat_mean_count > 0 ? sat_mean_sum / sat_mean_count : NAN;
        r->snr_sample_count[sys] = total_bin;
    }

    /* ── CMC RMS ── */
    for (int sys = 0; sys < 7; sys++) {
        if (g_state.cmc_count[sys] > 0)
            r->cmc_rms[sys] = sqrt(g_state.cmc_sumsq[sys] / g_state.cmc_count[sys]);
        else
            r->cmc_rms[sys] = NAN;
        r->cmc_sample_count[sys] = g_state.cmc_count[sys];
    }

    /* ── Cycle slips ── */
    for (int sys = 0; sys < 7; sys++) {
        r->gf_slip_count[sys]      = g_state.gf_slips[sys];
        r->mw_slip_count[sys]      = g_state.mw_slips[sys];
        r->gf_poly_slip_count[sys] = g_state.gf_poly_slips[sys];
        r->slip_combined[sys]      = g_state.mw_slips[sys] + g_state.gf_poly_slips[sys];
    }

    /* ── MP multipath ── */
    for (int sys = 0; sys < 7; sys++) {
        if (g_state.mp1_count[sys] > 0)
            r->mp1_rms[sys] = sqrt(g_state.mp1_sumsq[sys] / g_state.mp1_count[sys]);
        else r->mp1_rms[sys] = NAN;
        if (g_state.mp2_count[sys] > 0)
            r->mp2_rms[sys] = sqrt(g_state.mp2_sumsq[sys] / g_state.mp2_count[sys]);
        else r->mp2_rms[sys] = NAN;
        if (g_state.mp5_count[sys] > 0)
            r->mp5_rms[sys] = sqrt(g_state.mp5_sumsq[sys] / g_state.mp5_count[sys]);
        else r->mp5_rms[sys] = NAN;
        if (g_state.mp1_sw_count[sys] > 0)
            r->mp1_sw_rms[sys] = g_state.mp1_sw_sum[sys] / g_state.mp1_sw_count[sys];
        else r->mp1_sw_rms[sys] = NAN;
        if (g_state.mp2_sw_count[sys] > 0)
            r->mp2_sw_rms[sys] = g_state.mp2_sw_sum[sys] / g_state.mp2_sw_count[sys];
        else r->mp2_sw_rms[sys] = NAN;
        if (g_state.mp5_sw_count[sys] > 0)
            r->mp5_sw_rms[sys] = g_state.mp5_sw_sum[sys] / g_state.mp5_sw_count[sys];
        else r->mp5_sw_rms[sys] = NAN;
    }

    /* ── TD noise (BD 420022-2019 §6.5/6.6: σ = √(ΣTD² / (8·N))) ── */
    for (int sys = 0; sys < 7; sys++) {
        if (g_state.td_pr_count[sys] > 0)
            r->td_pr_noise_rms[sys] = sqrt(g_state.td_pr_sumsq[sys] / (8.0 * g_state.td_pr_count[sys]));
        else r->td_pr_noise_rms[sys] = NAN;
        if (g_state.td_cp_count[sys] > 0)
            r->td_cp_noise_rms[sys] = sqrt(g_state.td_cp_sumsq[sys] / (8.0 * g_state.td_cp_count[sys]));
        else r->td_cp_noise_rms[sys] = NAN;
    }

    /* ── Ion rate ── */
    for (int sys = 0; sys < 7; sys++) {
        if (g_state.ion_rate_count[sys] > 0)
            r->ion_rate_mean[sys] = g_state.ion_rate_sum[sys] / g_state.ion_rate_count[sys];
        else r->ion_rate_mean[sys] = NAN;
        r->ion_rate_count[sys] = g_state.ion_rate_count[sys];
        if (g_state.ion_rate_k2_count[sys] > 0)
            r->ion_rate_k2_mean[sys] = g_state.ion_rate_k2_sum[sys] / g_state.ion_rate_k2_count[sys];
        else r->ion_rate_k2_mean[sys] = NAN;
        r->ion_rate_k2_count[sys] = g_state.ion_rate_k2_count[sys];

        /* Elevation bins */
        for (int eb = 0; eb < ELEV_BIN_COUNT; eb++)
            r->elev_bins[sys][eb] = g_state.elev_bins[sys][eb];
    }

    /* ── Completeness ── */
    for (int sys = 0; sys < 7; sys++) {
        int n_epochs = g_state.epoch_count_sys[sys];
        /* Count unique satellites for this system */
        int unique_sats = 0;
        for (int i = 0; i < g_state.sat_capacity; i++) {
            sat_state_t *s = &g_state.sat_table[i];
            if (!s->active || s->sys_idx != sys) continue;
            /* A satellite is "present" if it had at least one valid dual-freq obs */
            unique_sats++;
        }
        int sum_b = n_epochs * unique_sats;
        int sum_a = g_state.dual_freq_obs_sys[sys]; /* epochs where we had L1+L2 for that sat */
        if (sum_b > 0)
            r->completeness[sys] = (double)sum_a / sum_b;
        else
            r->completeness[sys] = 0.0;

        if (n_epochs > 0)
            r->avg_sats_per_epoch[sys] = (double)g_state.sat_epoch_sum[sys] / n_epochs;
        else
            r->avg_sats_per_epoch[sys] = 0.0;

        if (g_state.total_obs_sys[sys] > 0)
            r->dual_freq_ratio[sys] = (double)g_state.dual_freq_obs_sys[sys] / g_state.total_obs_sys[sys];
        else
            r->dual_freq_ratio[sys] = 0.0;

        /* CSR = observations per slip (o/s). Larger = better. */
        int total_slips = g_state.mw_slips[sys] + g_state.gf_poly_slips[sys];
        if (total_slips > 0 && n_epochs > 0)
            r->csr[sys] = (double)n_epochs / total_slips;
        else if (n_epochs > 0)
            r->csr[sys] = (double)n_epochs; /* no slips */
        else
            r->csr[sys] = 0.0;
    }

    r->epoch_count = g_state.epoch_counter;
    r->total_obs   = g_state.total_obs_all;

    /* ── Per-system satellite counts ── */
    {
        int sat_count[7] = {0};
        for (int i = 0; i < g_state.sat_capacity; i++) {
            if (g_state.sat_table[i].active)
                sat_count[g_state.sat_table[i].sys_idx]++;
        }
        r->sat_total = 0;
        for (int s = 0; s < 7; s++) {
            r->sat_count[s] = sat_count[s];
            r->sat_total += sat_count[s];
        }
    }

    /* Check if we've seen any dual-freq data */
    r->has_dual_freq = 0;
    for (int sys = 0; sys < 7; sys++)
        if (g_state.dual_freq_obs_sys[sys] > 0) { r->has_dual_freq = 1; break; }

    r->has_adr = g_state.has_adr;

    /* ── Per-satellite info (FDE) ── */
    r->sat_info_count = 0;
    for (int i = 0; i < g_state.sat_capacity && r->sat_info_count < MAX_SAT_INFO; i++) {
        sat_state_t *sat = &g_state.sat_table[i];
        if (!sat->active || sat->cmc_resid_count < 10) continue;
        sat_info_t *si = &r->sat_info[r->sat_info_count++];
        si->cons = sat->sys_idx;
        si->svid = sat->key % 1000;
        si->cmc_rms = sqrt(sat->cmc_resid_sumsq / sat->cmc_resid_count);
        si->snr_mean = sat->snr_count > 0 ? (sat->snr_sum / sat->snr_count) : 0.0;
        si->sample_count = sat->cmc_resid_count;
    }

    pthread_mutex_unlock(&g_state.mutex);
}

/* ═══════════════════════════════════════════════════════════════════════
 * quality_get_timeseries_json — serialise ring buffer for charts
 * ═══════════════════════════════════════════════════════════════════════ */

/* Helper: write a float array to buf. Returns bytes written (excl null). */
static int json_float_array(char *buf, int bufsize, const float *vals, int n, int skip_nan) {
    int pos = 0;
    int safe = (int)bufsize - 2; /* leave room for ']' + null */
    if (bufsize < 4) return 0;

    if (pos < safe) { pos += snprintf(buf + pos, bufsize - pos, "["); }
    int first = 1;
    for (int i = 0; i < n && pos < safe; i++) {
        float v = vals[i];
        if (skip_nan && !isfinite(v)) continue;
        if (!first && pos < safe) pos += snprintf(buf + pos, bufsize - pos, ",");
        if (pos < safe) pos += snprintf(buf + pos, bufsize - pos, "%.3f", (double)v);
        first = 0;
    }
    if (pos < safe) pos += snprintf(buf + pos, bufsize - pos, "]");
    return pos;
}

/* Helper: write an int array */
static int json_int_array(char *buf, int bufsize, const int *vals, int n) {
    int pos = 0;
    int safe = (int)bufsize - 2;
    if (bufsize < 4) return 0;
    if (pos < safe) { pos += snprintf(buf + pos, bufsize - pos, "["); }
    for (int i = 0; i < n && pos < safe; i++) {
        if (i > 0 && pos < safe) pos += snprintf(buf + pos, bufsize - pos, ",");
        if (pos < safe) pos += snprintf(buf + pos, bufsize - pos, "%d", vals[i]);
    }
    if (pos < safe) pos += snprintf(buf + pos, bufsize - pos, "]");
    return pos;
}

int quality_get_timeseries_json(char *buf, int bufsize) {
    if (!g_state.initialized) return -1;
    if (bufsize < 98304) return -1;

    pthread_mutex_lock(&g_state.mutex);

    /* Determine how many snapshots to output (min of ring capacity and count) */
    int n = g_state.tss_count;
    if (n > TSS_RING_SIZE) n = TSS_RING_SIZE;
    if (n == 0) {
        int r = snprintf(buf, bufsize, "{\"epochs\":[],"
            "\"snr_gps\":[],\"snr_glo\":[],\"snr_bds\":[],\"snr_gal\":[],"
            "\"cmc_gps\":[],\"cmc_glo\":[],\"cmc_bds\":[],\"cmc_gal\":[],"
            "\"ion_gps\":[],\"ion_glo\":[],\"ion_bds\":[],\"ion_gal\":[],"
            "\"ion_k2_gps\":[],\"ion_k2_glo\":[],\"ion_k2_bds\":[],\"ion_k2_gal\":[],"
            "\"mp1_gps\":[],\"mp1_glo\":[],\"mp1_bds\":[],\"mp1_gal\":[],"
            "\"slip_gps\":[],\"slip_glo\":[],\"slip_bds\":[],\"slip_gal\":[],"
            "\"sat_gps\":[],\"sat_glo\":[],\"sat_bds\":[],\"sat_gal\":[],"
            "\"comp_gps\":[],\"comp_glo\":[],\"comp_bds\":[],\"comp_gal\":[],"
            "\"td_pr_gps\":[],\"td_pr_glo\":[],\"td_pr_bds\":[],\"td_pr_gal\":[],"
            "\"td_cp_gps\":[],\"td_cp_glo\":[],\"td_cp_bds\":[],\"td_cp_gal\":[],"
            "\"mp2_gps\":[],\"mp2_glo\":[],\"mp2_bds\":[],\"mp2_gal\":[],"
            "\"mp5_gps\":[],\"mp5_glo\":[],\"mp5_bds\":[],\"mp5_gal\":[],"
            "\"gf_gps\":[],\"gf_glo\":[],\"gf_bds\":[],\"gf_gal\":[],"
            "\"elev_gps\":[],\"elev_glo\":[],\"elev_bds\":[],\"elev_gal\":[]}");
        pthread_mutex_unlock(&g_state.mutex);
        return r;
    }

    /* Read from ring buffer in chronological order */
    int start = g_state.tss_count > TSS_RING_SIZE
        ? g_state.tss_head
        : 0;
    if (g_state.tss_count <= TSS_RING_SIZE) start = 0;

    /* Build temporary flat arrays in chronological order — 4 systems */
    /* k=1 GPS, k=3 GLO, k=5 BDS, k=6 GAL */
    float snr_1[TSS_RING_SIZE], snr_3[TSS_RING_SIZE], snr_5[TSS_RING_SIZE], snr_6[TSS_RING_SIZE];
    float cmc_1[TSS_RING_SIZE], cmc_3[TSS_RING_SIZE], cmc_5[TSS_RING_SIZE], cmc_6[TSS_RING_SIZE];
    float ion_1[TSS_RING_SIZE], ion_3[TSS_RING_SIZE], ion_5[TSS_RING_SIZE], ion_6[TSS_RING_SIZE];
    float ionk2_1[TSS_RING_SIZE], ionk2_3[TSS_RING_SIZE], ionk2_5[TSS_RING_SIZE], ionk2_6[TSS_RING_SIZE];
    float mp1_1[TSS_RING_SIZE], mp1_3[TSS_RING_SIZE], mp1_5[TSS_RING_SIZE], mp1_6[TSS_RING_SIZE];
    int   slip_1[TSS_RING_SIZE], slip_3[TSS_RING_SIZE], slip_5[TSS_RING_SIZE], slip_6[TSS_RING_SIZE];
    int   sat_1[TSS_RING_SIZE], sat_3[TSS_RING_SIZE], sat_5[TSS_RING_SIZE], sat_6[TSS_RING_SIZE];
    int   epochs[TSS_RING_SIZE];
    float tdpr_1[TSS_RING_SIZE], tdpr_3[TSS_RING_SIZE], tdpr_5[TSS_RING_SIZE], tdpr_6[TSS_RING_SIZE];
    float comp_1[TSS_RING_SIZE], comp_3[TSS_RING_SIZE], comp_5[TSS_RING_SIZE], comp_6[TSS_RING_SIZE];
    float tdcp_1[TSS_RING_SIZE], tdcp_3[TSS_RING_SIZE], tdcp_5[TSS_RING_SIZE], tdcp_6[TSS_RING_SIZE];
    float mp2_1[TSS_RING_SIZE], mp2_3[TSS_RING_SIZE], mp2_5[TSS_RING_SIZE], mp2_6[TSS_RING_SIZE];
    float mp5_1[TSS_RING_SIZE], mp5_3[TSS_RING_SIZE], mp5_5[TSS_RING_SIZE], mp5_6[TSS_RING_SIZE];
    float gf_1[TSS_RING_SIZE], gf_3[TSS_RING_SIZE], gf_5[TSS_RING_SIZE], gf_6[TSS_RING_SIZE];
    float elev_1[TSS_RING_SIZE], elev_3[TSS_RING_SIZE], elev_5[TSS_RING_SIZE], elev_6[TSS_RING_SIZE];

    for (int i = 0; i < n; i++) {
        int idx = start + i;
        if (g_state.tss_count > TSS_RING_SIZE) idx = idx % TSS_RING_SIZE;
        epoch_snapshot_t *s = &g_state.tss_ring[idx];

        epochs[i]     = s->epoch;
        /* GPS */
        snr_1[i]    = s->snr_mean[1]; cmc_1[i] = s->cmc_rms[1];
        ion_1[i]    = s->ion_rate[1]; ionk2_1[i] = s->ion_rate_k2[1];
        mp1_1[i]    = s->mp1_sw_rms[1];
        slip_1[i]   = s->slip_combined[1]; sat_1[i] = s->sat_count[1];
        /* GLO */
        snr_3[i]    = s->snr_mean[3]; cmc_3[i] = s->cmc_rms[3];
        ion_3[i]    = s->ion_rate[3]; ionk2_3[i] = s->ion_rate_k2[3];
        mp1_3[i]    = s->mp1_sw_rms[3];
        slip_3[i]   = s->slip_combined[3]; sat_3[i] = s->sat_count[3];
        /* BDS */
        snr_5[i]    = s->snr_mean[5]; cmc_5[i] = s->cmc_rms[5];
        ion_5[i]    = s->ion_rate[5]; ionk2_5[i] = s->ion_rate_k2[5];
        mp1_5[i]    = s->mp1_sw_rms[5];
        slip_5[i]   = s->slip_combined[5]; sat_5[i] = s->sat_count[5];
        /* GAL */
        snr_6[i]    = s->snr_mean[6]; cmc_6[i] = s->cmc_rms[6];
        ion_6[i]    = s->ion_rate[6]; ionk2_6[i] = s->ion_rate_k2[6];
        mp1_6[i]    = s->mp1_sw_rms[6];
        slip_6[i]   = s->slip_combined[6]; sat_6[i] = s->sat_count[6];

        comp_1[i]  = s->completeness[1] * 100.0f;
        comp_3[i]  = s->completeness[3] * 100.0f;
        comp_5[i]  = s->completeness[5] * 100.0f;
        comp_6[i]  = s->completeness[6] * 100.0f;
        tdpr_1[i]     = s->td_pr_noise_rms[1]; tdpr_3[i] = s->td_pr_noise_rms[3];
        tdpr_5[i]     = s->td_pr_noise_rms[5]; tdpr_6[i] = s->td_pr_noise_rms[6];
        tdcp_1[i]     = s->td_cp_noise_rms[1]; tdcp_3[i] = s->td_cp_noise_rms[3];
        tdcp_5[i]     = s->td_cp_noise_rms[5]; tdcp_6[i] = s->td_cp_noise_rms[6];
        mp2_1[i]      = s->mp2_sw_rms[1]; mp2_3[i] = s->mp2_sw_rms[3];
        mp2_5[i]      = s->mp2_sw_rms[5]; mp2_6[i] = s->mp2_sw_rms[6];
        mp5_1[i]      = s->mp5_sw_rms[1]; mp5_3[i] = s->mp5_sw_rms[3];
        mp5_5[i]      = s->mp5_sw_rms[5]; mp5_6[i] = s->mp5_sw_rms[6];
        gf_1[i]       = s->gf_rms[1]; gf_3[i] = s->gf_rms[3];
        gf_5[i]       = s->gf_rms[5]; gf_6[i] = s->gf_rms[6];

        elev_1[i]     = s->elev_mean[1]; elev_3[i] = s->elev_mean[3];
        elev_5[i]     = s->elev_mean[5]; elev_6[i] = s->elev_mean[6];
    }

    int pos = 0;
    pos += snprintf(buf + pos, bufsize - pos, "{\"epochs\":");
    pos += json_int_array(buf + pos, bufsize - pos, epochs, n);

    pos += snprintf(buf + pos, bufsize - pos, ",\"snr_gps\":");
    pos += json_float_array(buf + pos, bufsize - pos, snr_1, n, 1);
    pos += snprintf(buf + pos, bufsize - pos, ",\"snr_glo\":");
    pos += json_float_array(buf + pos, bufsize - pos, snr_3, n, 1);
    pos += snprintf(buf + pos, bufsize - pos, ",\"snr_bds\":");
    pos += json_float_array(buf + pos, bufsize - pos, snr_5, n, 1);
    pos += snprintf(buf + pos, bufsize - pos, ",\"snr_gal\":");
    pos += json_float_array(buf + pos, bufsize - pos, snr_6, n, 1);

    pos += snprintf(buf + pos, bufsize - pos, ",\"cmc_gps\":");
    pos += json_float_array(buf + pos, bufsize - pos, cmc_1, n, 1);
    pos += snprintf(buf + pos, bufsize - pos, ",\"cmc_glo\":");
    pos += json_float_array(buf + pos, bufsize - pos, cmc_3, n, 1);
    pos += snprintf(buf + pos, bufsize - pos, ",\"cmc_bds\":");
    pos += json_float_array(buf + pos, bufsize - pos, cmc_5, n, 1);
    pos += snprintf(buf + pos, bufsize - pos, ",\"cmc_gal\":");
    pos += json_float_array(buf + pos, bufsize - pos, cmc_6, n, 1);

    pos += snprintf(buf + pos, bufsize - pos, ",\"ion_gps\":");
    pos += json_float_array(buf + pos, bufsize - pos, ion_1, n, 1);
    pos += snprintf(buf + pos, bufsize - pos, ",\"ion_glo\":");
    pos += json_float_array(buf + pos, bufsize - pos, ion_3, n, 1);
    pos += snprintf(buf + pos, bufsize - pos, ",\"ion_bds\":");
    pos += json_float_array(buf + pos, bufsize - pos, ion_5, n, 1);
    pos += snprintf(buf + pos, bufsize - pos, ",\"ion_gal\":");
    pos += json_float_array(buf + pos, bufsize - pos, ion_6, n, 1);

    pos += snprintf(buf + pos, bufsize - pos, ",\"ion_k2_gps\":");
    pos += json_float_array(buf + pos, bufsize - pos, ionk2_1, n, 1);
    pos += snprintf(buf + pos, bufsize - pos, ",\"ion_k2_glo\":");
    pos += json_float_array(buf + pos, bufsize - pos, ionk2_3, n, 1);
    pos += snprintf(buf + pos, bufsize - pos, ",\"ion_k2_bds\":");
    pos += json_float_array(buf + pos, bufsize - pos, ionk2_5, n, 1);
    pos += snprintf(buf + pos, bufsize - pos, ",\"ion_k2_gal\":");
    pos += json_float_array(buf + pos, bufsize - pos, ionk2_6, n, 1);

    pos += snprintf(buf + pos, bufsize - pos, ",\"mp1_gps\":");
    pos += json_float_array(buf + pos, bufsize - pos, mp1_1, n, 1);
    pos += snprintf(buf + pos, bufsize - pos, ",\"mp1_glo\":");
    pos += json_float_array(buf + pos, bufsize - pos, mp1_3, n, 1);
    pos += snprintf(buf + pos, bufsize - pos, ",\"mp1_bds\":");
    pos += json_float_array(buf + pos, bufsize - pos, mp1_5, n, 1);
    pos += snprintf(buf + pos, bufsize - pos, ",\"mp1_gal\":");
    pos += json_float_array(buf + pos, bufsize - pos, mp1_6, n, 1);

    pos += snprintf(buf + pos, bufsize - pos, ",\"slip_gps\":");
    pos += json_int_array(buf + pos, bufsize - pos, slip_1, n);
    pos += snprintf(buf + pos, bufsize - pos, ",\"slip_glo\":");
    pos += json_int_array(buf + pos, bufsize - pos, slip_3, n);
    pos += snprintf(buf + pos, bufsize - pos, ",\"slip_bds\":");
    pos += json_int_array(buf + pos, bufsize - pos, slip_5, n);
    pos += snprintf(buf + pos, bufsize - pos, ",\"slip_gal\":");
    pos += json_int_array(buf + pos, bufsize - pos, slip_6, n);

    pos += snprintf(buf + pos, bufsize - pos, ",\"sat_gps\":");
    pos += json_int_array(buf + pos, bufsize - pos, sat_1, n);
    pos += snprintf(buf + pos, bufsize - pos, ",\"sat_glo\":");
    pos += json_int_array(buf + pos, bufsize - pos, sat_3, n);
    pos += snprintf(buf + pos, bufsize - pos, ",\"sat_bds\":");
    pos += json_int_array(buf + pos, bufsize - pos, sat_5, n);
    pos += snprintf(buf + pos, bufsize - pos, ",\"sat_gal\":");
    pos += json_int_array(buf + pos, bufsize - pos, sat_6, n);

    pos += snprintf(buf + pos, bufsize - pos, ",\"comp_gps\":");
    pos += json_float_array(buf + pos, bufsize - pos, comp_1, n, 1);
    pos += snprintf(buf + pos, bufsize - pos, ",\"comp_glo\":");
    pos += json_float_array(buf + pos, bufsize - pos, comp_3, n, 1);
    pos += snprintf(buf + pos, bufsize - pos, ",\"comp_bds\":");
    pos += json_float_array(buf + pos, bufsize - pos, comp_5, n, 1);
    pos += snprintf(buf + pos, bufsize - pos, ",\"comp_gal\":");
    pos += json_float_array(buf + pos, bufsize - pos, comp_6, n, 1);
    pos += snprintf(buf + pos, bufsize - pos, ",\"td_pr_gps\":");
    pos += json_float_array(buf + pos, bufsize - pos, tdpr_1, n, 1);
    pos += snprintf(buf + pos, bufsize - pos, ",\"td_pr_glo\":");
    pos += json_float_array(buf + pos, bufsize - pos, tdpr_3, n, 1);
    pos += snprintf(buf + pos, bufsize - pos, ",\"td_pr_bds\":");
    pos += json_float_array(buf + pos, bufsize - pos, tdpr_5, n, 1);
    pos += snprintf(buf + pos, bufsize - pos, ",\"td_pr_gal\":");
    pos += json_float_array(buf + pos, bufsize - pos, tdpr_6, n, 1);
    pos += snprintf(buf + pos, bufsize - pos, ",\"td_cp_gps\":");
    pos += json_float_array(buf + pos, bufsize - pos, tdcp_1, n, 1);
    pos += snprintf(buf + pos, bufsize - pos, ",\"td_cp_glo\":");
    pos += json_float_array(buf + pos, bufsize - pos, tdcp_3, n, 1);
    pos += snprintf(buf + pos, bufsize - pos, ",\"td_cp_bds\":");
    pos += json_float_array(buf + pos, bufsize - pos, tdcp_5, n, 1);
    pos += snprintf(buf + pos, bufsize - pos, ",\"td_cp_gal\":");
    pos += json_float_array(buf + pos, bufsize - pos, tdcp_6, n, 1);
    pos += snprintf(buf + pos, bufsize - pos, ",\"mp2_gps\":");
    pos += json_float_array(buf + pos, bufsize - pos, mp2_1, n, 1);
    pos += snprintf(buf + pos, bufsize - pos, ",\"mp2_glo\":");
    pos += json_float_array(buf + pos, bufsize - pos, mp2_3, n, 1);
    pos += snprintf(buf + pos, bufsize - pos, ",\"mp2_bds\":");
    pos += json_float_array(buf + pos, bufsize - pos, mp2_5, n, 1);
    pos += snprintf(buf + pos, bufsize - pos, ",\"mp2_gal\":");
    pos += json_float_array(buf + pos, bufsize - pos, mp2_6, n, 1);
    pos += snprintf(buf + pos, bufsize - pos, ",\"mp5_gps\":");
    pos += json_float_array(buf + pos, bufsize - pos, mp5_1, n, 1);
    pos += snprintf(buf + pos, bufsize - pos, ",\"mp5_glo\":");
    pos += json_float_array(buf + pos, bufsize - pos, mp5_3, n, 1);
    pos += snprintf(buf + pos, bufsize - pos, ",\"mp5_bds\":");
    pos += json_float_array(buf + pos, bufsize - pos, mp5_5, n, 1);
    pos += snprintf(buf + pos, bufsize - pos, ",\"mp5_gal\":");
    pos += json_float_array(buf + pos, bufsize - pos, mp5_6, n, 1);
    pos += snprintf(buf + pos, bufsize - pos, ",\"gf_gps\":");
    pos += json_float_array(buf + pos, bufsize - pos, gf_1, n, 1);
    pos += snprintf(buf + pos, bufsize - pos, ",\"gf_glo\":");
    pos += json_float_array(buf + pos, bufsize - pos, gf_3, n, 1);
    pos += snprintf(buf + pos, bufsize - pos, ",\"gf_bds\":");
    pos += json_float_array(buf + pos, bufsize - pos, gf_5, n, 1);
    pos += snprintf(buf + pos, bufsize - pos, ",\"gf_gal\":");
    pos += json_float_array(buf + pos, bufsize - pos, gf_6, n, 1);

    pos += snprintf(buf + pos, bufsize - pos, ",\"elev_gps\":");
    pos += json_float_array(buf + pos, bufsize - pos, elev_1, n, 1);
    pos += snprintf(buf + pos, bufsize - pos, ",\"elev_glo\":");
    pos += json_float_array(buf + pos, bufsize - pos, elev_3, n, 1);
    pos += snprintf(buf + pos, bufsize - pos, ",\"elev_bds\":");
    pos += json_float_array(buf + pos, bufsize - pos, elev_5, n, 1);
    pos += snprintf(buf + pos, bufsize - pos, ",\"elev_gal\":");
    pos += json_float_array(buf + pos, bufsize - pos, elev_6, n, 1);

    pos += snprintf(buf + pos, bufsize - pos, "}");

    pthread_mutex_unlock(&g_state.mutex);
    return pos;
}

/* ═══════════════════════════════════════════════════════════════════════
 * process_epoch — main entry point
 * ═══════════════════════════════════════════════════════════════════════ */

void quality_process_epoch(const raw_meas_t *meas, int n) {
    if (!g_state.initialized) return;
    if (!g_state.sat_table) return;
    if (n <= 0) return;

    pthread_mutex_lock(&g_state.mutex);

    g_state.epoch_counter++;
    /* Reset per-epoch counters */
    for (int s = 0; s < 7; s++) { g_state.epoch_obs[s] = 0; g_state.epoch_dual[s] = 0; g_state.epoch_sat_count[s] = 0; }
    int ei = g_state.epoch_counter; /* current epoch index for polynomial x-axis */
    int64_t epoch_time = meas[0].recv_time_nanos; /* receiver time for this epoch */

    /* ── First pass: per-system counts + SNR bins + group by sat ── */

    struct { int cons; int svid; double l1_p; double l1_l; double l2_p; double l2_l;
             double l1_snr; double l2_snr; double l2_freq;
             int l1_adr_state; int l2_adr_state; int has_l1; int has_l2;
             float elev; } grouped[128];
    int n_grouped = 0;
    int grouped_keys[128] = {0};

    for (int i = 0; i < n; i++) {
        const raw_meas_t *m = &meas[i];
        int cons = m->constellation;
        if (cons < 0 || cons > 6) continue;
        int sys = cons_to_sys(cons);

        g_state.total_obs_all++;
        g_state.total_obs_sys[sys]++;
        g_state.epoch_obs[sys]++;

        /* Track ADR availability */
        if (!g_state.has_adr && isfinite(m->adr_m)) g_state.has_adr = 1;

        /* SNR bin */
        if (isfinite(m->snr_db) && m->snr_db > 0) {
            int b = -1;
            if (m->snr_db <= 20) b = SNR_BIN_0_20;
            else if (m->snr_db <= 30) b = SNR_BIN_20_30;
            else if (m->snr_db <= 40) b = SNR_BIN_30_40;
            else if (m->snr_db <= 60) b = SNR_BIN_40_60;
            if (b >= 0) g_state.snr_bins[sys][b]++;
        }

        /* Elevation bin */
        if (isfinite((double)m->elevation_deg) && m->elevation_deg >= 0 && m->elevation_deg <= 90) {
            int eb = -1;
            if (m->elevation_deg <= 15) eb = ELEV_BIN_0_15;
            else if (m->elevation_deg <= 30) eb = ELEV_BIN_15_30;
            else if (m->elevation_deg <= 60) eb = ELEV_BIN_30_60;
            else eb = ELEV_BIN_60_90;
            if (eb >= 0) g_state.elev_bins[sys][eb]++;
        }

        /* Frequency band classification */
        double f = m->carrier_freq_hz;
        int is_l1 = isfinite(f) && is_l1_band(cons, f);
        int is_l2 = isfinite(f) && is_l2_band(cons, f);

        /* Dual-freq tracking — per-satellite (see grouped loop below) */
        /* (is_l1 && is_l2) is always false for single-freq measurements */

        /* Group by (cons, svid) for paired processing */
        int key = cons * 1000 + m->svid;
        int found = -1;
        for (int j = 0; j < n_grouped; j++) {
            if (grouped_keys[j] == key) { found = j; break; }
        }
        if (found < 0) {
            if (n_grouped >= 128) continue; /* safety */
            found = n_grouped++;
            grouped_keys[found] = key;
            memset(&grouped[found], 0, sizeof(grouped[0]));
            grouped[found].cons = cons;
            grouped[found].svid = m->svid;
            grouped[found].elev = m->elevation_deg;  /* last L1 elevation wins */
            grouped[found].l1_snr = NAN;
            grouped[found].l2_snr = NAN;
            grouped[found].l1_p = NAN; grouped[found].l1_l = NAN;
            grouped[found].l2_p = NAN; grouped[found].l2_l = NAN;
        }

        if (is_l1) {
            grouped[found].has_l1 = 1;
            if (isfinite(m->snr_db)) grouped[found].l1_snr = m->snr_db;
            if (isfinite(m->pr_m))   grouped[found].l1_p = m->pr_m;
            if (isfinite(m->adr_m))  { grouped[found].l1_l = m->adr_m; grouped[found].l1_adr_state = m->adr_state; }
            /* CMC for L1: accumulate per-satellite CMC buffer */
            if (isfinite(m->pr_m) && isfinite(m->adr_m)) {
                sat_state_t *sat = sat_get_or_create(cons, m->svid);
                if (sat) {
                    double cmc = m->pr_m - m->adr_m;
                    if (sat->cmc_len < CMC_BUF_SIZE) {
                        int pos = (sat->cmc_head + sat->cmc_len) % CMC_BUF_SIZE;
                        sat->cmc_vals[pos] = cmc;
                        sat->cmc_epochs[pos] = ei;
                        sat->cmc_len++;
                    } else {
                        sat->cmc_head = (sat->cmc_head + 1) % CMC_BUF_SIZE;
                        int pos = (sat->cmc_head + sat->cmc_len - 1) % CMC_BUF_SIZE;
                        sat->cmc_vals[pos] = cmc;
                        sat->cmc_epochs[pos] = ei;
                    }

                    /* CMC polynomial detrend: when we have >= degree+3 points, fit and
                       compute residual at latest point */
                    if (sat->cmc_len >= POLY_DEGREE + 3) {
                        double x[CMC_BUF_SIZE], y[CMC_BUF_SIZE];
                        int start = sat->cmc_head;
                        for (int k = 0; k < sat->cmc_len; k++) {
                            int idx = (start + k) % CMC_BUF_SIZE;
                            x[k] = (double)sat->cmc_epochs[idx];
                            y[k] = sat->cmc_vals[idx];
                        }
                        double coeff[POLY_DEGREE + 1];
                        if (polyfit(x, y, sat->cmc_len, POLY_DEGREE, coeff) == 0) {
                            double trend = polyval(coeff, POLY_DEGREE, (double)ei);
                            double resid = cmc - trend;
                            g_state.cmc_sumsq[sys] += resid * resid;
                            g_state.cmc_count[sys]++;
                            sat->cmc_resid_sumsq += resid * resid;
                            sat->cmc_resid_count++;
                        }
                    }
                }
            }
        }
        if (is_l2) {
            grouped[found].has_l2 = 1;
            grouped[found].l2_freq = f;
            if (isfinite(m->snr_db)) grouped[found].l2_snr = m->snr_db;
            if (isfinite(m->pr_m))   grouped[found].l2_p = m->pr_m;
            if (isfinite(m->adr_m))  { grouped[found].l2_l = m->adr_m; grouped[found].l2_adr_state = m->adr_state; }
        }
    }

    /* ── Per-system epoch sat count + mean elevation ── */
    {
        int sys_sat_count[7] = {0};
        double sys_elev_sum[7] = {0};
        for (int j = 0; j < n_grouped; j++) {
            int sys = cons_to_sys(grouped[j].cons);
            sys_sat_count[sys]++;
            if (isfinite(grouped[j].elev)) sys_elev_sum[sys] += grouped[j].elev;
        }
        for (int s = 0; s < 7; s++) {
            if (sys_sat_count[s] > 0) {
                g_state.sat_epoch_sum[s] += sys_sat_count[s];
                g_state.epoch_count_sys[s]++;
            }
        }
    }

    /* ── Per-satellite: SNR, GF, MW, MP, PR noise, CP noise, Ion rate ── */

    for (int j = 0; j < n_grouped; j++) {
        int cons = grouped[j].cons;
        int svid = grouped[j].svid;
        int sys  = cons_to_sys(cons);
        int has_l1 = grouped[j].has_l1;
        int has_l2 = grouped[j].has_l2;

        /* 双频统计：卫星同时有 L1 和 L2 观测 */
        g_state.epoch_sat_count[sys]++;
        if (has_l1 && has_l2) {
            g_state.dual_freq_obs_sys[sys]++;
            g_state.epoch_dual[sys]++;
        }

        double p1 = grouped[j].l1_p, l1 = grouped[j].l1_l;
        double p2 = grouped[j].l2_p, l2 = grouped[j].l2_l;
        double snr_l1 = grouped[j].l1_snr;

        sat_state_t *sat = sat_get_or_create(cons, svid);
        if (!sat) continue;

        if (isfinite(snr_l1) && snr_l1 > 0) {
            sat->snr_sum += snr_l1;
            sat->snr_count++;
        }

        /* GF cycle slip detection */
        if (has_l1 && has_l2 && isfinite(l1) && isfinite(l2)) {
            double gf = l1 - l2;
            sat->gf_warmup++;
            if (sat->has_prev_gf && sat->gf_warmup > GF_WARMUP) {
                double dgf = gf - sat->prev_gf;
                g_state.gf_diff_sumsq[sys] += dgf * dgf;
                g_state.gf_diff_count[sys]++;
                /* L1/L2 GF 周跳检测：阈值 0.30m
                   L1/L5 不适用 GF 检测 — 手机通道间 IFB 漂移使 GF 一阶差分远大于任何合理阈值，
                   对 L5 只依赖 ADR_STATE (RESET|CYCLE_SLIP) 位掩码。 */
                if (!freq_is_l5(grouped[j].l2_freq)) {
                    if (fabs(dgf) > SLIP_THRESH_M) {
                        g_state.gf_slips[sys]++;
                        LOGW("GF slip C%d-%d dgf=%.3f l1=%.3f l2=%.3f gf=%.3f prev=%.3f mp_len=%d",
                             cons, svid, dgf, l1, l2, gf, sat->prev_gf, sat->mp_len);
                        sat->cmc_len = 0; sat->cmc_head = 0;
                        sat->cmc_resid_sumsq = 0; sat->cmc_resid_count = 0;
                        sat->mp_len = 0;  sat->mp_head = 0;
                        sat->has_prev_td_pr = 0;
                        sat->has_prev_td_cp = 0;
                        sat->has_prev_ion = 0;
                    }
                }
            }
            sat->prev_gf = gf;
            sat->has_prev_gf = 1;

            /* GF polyfit supplementary slip detection */
            if (sat->gf_len < GF_POLY_WIN) {
                int pos = (sat->gf_head + sat->gf_len) % GF_POLY_WIN;
                sat->gf_vals[pos] = gf;
                sat->gf_len++;
            } else {
                sat->gf_head = (sat->gf_head + 1) % GF_POLY_WIN;
                int pos = (sat->gf_head + sat->gf_len - 1) % GF_POLY_WIN;
                sat->gf_vals[pos] = gf;
                /* Fit 2nd-degree poly to window, flag if last residual > 0.05 m */
                if (sat->gf_len >= GF_POLY_WIN) {
                    double x[GF_POLY_WIN], y[GF_POLY_WIN];
                    for (int k = 0; k < GF_POLY_WIN; k++) {
                        int idx = (sat->gf_head + k) % GF_POLY_WIN;
                        x[k] = (double)k;
                        y[k] = sat->gf_vals[idx];
                    }
                    double coeff[3];
                    if (polyfit(x, y, GF_POLY_WIN, 2, coeff) == 0) {
                        double fitted = polyval(coeff, 2, (double)(GF_POLY_WIN - 1));
                        if (fabs(y[GF_POLY_WIN - 1] - fitted) > 0.05) {
                            g_state.gf_poly_slips[sys]++;
                            sat->cmc_len = 0; sat->cmc_head = 0;
                            /* MP arc NOT reset — phone L5 phase noise causes false polyfit triggers */
                            sat->has_prev_td_pr = 0;
                            sat->has_prev_td_cp = 0;
                            sat->has_prev_ion = 0;
                        }
                    }
                }
            }
        } else {
            sat->gf_warmup = 0;  /* L2 掉线时重置预热计数器 */
        }

        /* MW recursive cycle slip detection */
        if (has_l1 && has_l2 && isfinite(p1) && isfinite(l1) && isfinite(p2) && isfinite(l2)) {
            double f1m = get_f1_mhz(cons);
            double f2m = get_f2_mhz(cons, grouped[j].l2_freq);
            double mw = compute_mw(l1, l2, p1, p2, f1m, f2m);
            if (sat->mw_arc_idx == 0) {
                sat->mw_mean = mw;
                sat->mw_var = 0.0;
                sat->mw_arc_idx = 1;
            } else {
                double sigma = sqrt(fmax(0.0, sat->mw_var));
                if (sigma > 0 && fabs(mw - sat->mw_mean) > MW_THRESHOLD * sigma) {
                    g_state.mw_slips[sys]++;
                    sat->mw_mean = mw;
                    sat->mw_var = 0.0;
                    sat->mw_arc_idx = 1;
                    sat->cmc_len = 0; sat->cmc_head = 0; /* reset CMC arc */
                    /* MP arc NOT reset — arc-mean subtraction absorbs WL ambiguity jumps */
                    /* Reset TD/ION noise epoch-differencing to reject slip epoch */
                    sat->has_prev_td_pr = 0;
                    sat->has_prev_td_cp = 0;
                    sat->has_prev_ion = 0;
                } else {
                    sat->mw_arc_idx++;
                    double diff = mw - sat->mw_mean;
                    sat->mw_mean += diff / sat->mw_arc_idx;
                    sat->mw_var  += (diff * (mw - sat->mw_mean) - sat->mw_var) / sat->mw_arc_idx;
                    if (sat->mw_var < 0) sat->mw_var = 0.0;
                }
            }
        }

        /* MP multipath — 时间差分累积法。
           只计算相邻历元间的 MP 变化量，避免相对 TOW 参考系跨历元跳变
           (同历元内所有卫星共享同一 maxTow，相邻历元 maxTow 通常不变，
           即使偶有跳变也只影响单个历元，被滑动窗口均值相减吸收)。

           dMP1 = (P1-P1_prev) - K1*(L1-L1_prev) + K2*(L2-L2_prev)
           mp_raw 从弧首 0 开始累积，弧段均值相减消除常数偏差。

           跳过 ADR 有 RESET/CYCLE_SLIP 标记的历元，避免污染缓冲区 */
        if (has_l1 && has_l2
            && isfinite(p1) && p1 > 1e6 && p1 < 1e10
            && isfinite(l1) && isfinite(p2) && p2 > 1e6 && p2 < 1e10
            && isfinite(l2)
            && !(grouped[j].l1_adr_state & 6)   /* 无 RESET | CYCLE_SLIP */
            && !(grouped[j].l2_adr_state & 6)) {

            double f1m = get_f1_mhz(cons);
            double f2m = get_f2_mhz(cons, grouped[j].l2_freq);
            double alpha = (f1m / f2m) * (f1m / f2m);
            double inv_a1 = 1.0 / (alpha - 1.0);
            double a1 = 1.0 + 2.0 * inv_a1;      /* (α+1)/(α-1) */
            double a2 = 2.0 * inv_a1;             /* 2/(α-1) */
            double c1 = 2.0 * alpha * inv_a1;     /* 2α/(α-1) */

            double mp1, mp2;
            double dp1 = 0, dl1 = 0, dl2 = 0, dmp1 = 0, dmp2 = 0;

            /* 同一卫星的 L1/L5 伪距必须相同（几何距离一致）。
               Java 端已有 L1→L5 借用逻辑，但部分 L5 可能有自己的 ReceivedSvTimeNanos
               导致 getPseudorange() 返回不同的 PR 值。这里兜底：强制 p2 = p1。 */
            if (isfinite(p1)) p2 = p1;

            if (sat->mp_len == 0) {
                /* 弧段首历元：初始化累积值为 0，记录参考值供下一历元差分 */
                sat->mp_raw1 = 0.0;
                sat->mp_raw2 = 0.0;
                sat->mp_prev_p1 = p1;
                sat->mp_prev_l1 = l1;
                sat->mp_prev_l2 = l2;
                sat->mp_has_prev = 1;
                mp1 = 0.0;
                mp2 = 0.0;
            } else if (sat->mp_has_prev) {
                /* 时间差分：dMP = ΔP - K1*ΔADR + K2*ΔADR
                   Android ADR 正 = 距离增加（卫星远离），ADR = range(t) - range(t_lock)。
                   因此 ΔADR = Δrange = Δ(载波相位)，MP 公式中 ΔL = ΔADR。 */
                dp1 = p1 - sat->mp_prev_p1;
                dl1 = l1 - sat->mp_prev_l1;
                dl2 = l2 - sat->mp_prev_l2;
                dmp1 = dp1 - a1 * dl1 + a2 * dl2;
                dmp2 = dp1 - c1 * dl1 + a1 * dl2;  /* dp2 = dp1, 因 p2 = p1 */

                sat->mp_raw1 += dmp1;
                sat->mp_raw2 += dmp2;
                mp1 = sat->mp_raw1;
                mp2 = sat->mp_raw2;

                /* 更新上一历元参考值 */
                sat->mp_prev_p1 = p1;
                sat->mp_prev_l1 = l1;
                sat->mp_prev_l2 = l2;
            } else {
                /* prev 数据无效（不应到达这里），重新初始化 */
                sat->mp_prev_p1 = p1;
                sat->mp_prev_l1 = l1;
                sat->mp_prev_l2 = l2;
                sat->mp_has_prev = 1;
                mp1 = 0.0;
                mp2 = 0.0;
            }

            /* Ring buffer update */
            if (sat->mp_len < MP_BUF_SIZE) {
                int pos = (sat->mp_head + sat->mp_len) % MP_BUF_SIZE;
                sat->mp1_vals[pos] = mp1; sat->mp2_vals[pos] = mp2;
                sat->mp_len++;
                if (sat->mp_len == 5 || sat->mp_len == MP_BUF_SIZE)
                    LOGW("C%d-%d mp_len=%d mp1=%.2f mp2=%.2f",
                         cons, svid, sat->mp_len, mp1, mp2);
            } else {
                sat->mp_head = (sat->mp_head + 1) % MP_BUF_SIZE;
                int pos = (sat->mp_head + sat->mp_len - 1) % MP_BUF_SIZE;
                sat->mp1_vals[pos] = mp1; sat->mp2_vals[pos] = mp2;
            }

            /* Segment mean subtraction: use running mean of current arc */
            /* Simple approach: subtract cumulative mean */
            if (sat->mp_len >= 5) {
                double sum1 = 0, sum2 = 0;
                for (int k = 0; k < sat->mp_len; k++) {
                    int idx = (sat->mp_head + k) % MP_BUF_SIZE;
                    sum1 += sat->mp1_vals[idx];
                    sum2 += sat->mp2_vals[idx];
                }
                double mean1 = sum1 / sat->mp_len;
                double mean2 = sum2 / sat->mp_len;
                double res1 = mp1 - mean1;
                double res2 = mp2 - mean2;
                g_state.mp1_sumsq[sys] += res1 * res1;
                g_state.mp1_count[sys]++;
                if (freq_is_l5(grouped[j].l2_freq)) {
                    g_state.mp5_sumsq[sys] += res2 * res2;
                    g_state.mp5_count[sys]++;
                } else {
                    g_state.mp2_sumsq[sys] += res2 * res2;
                    g_state.mp2_count[sys]++;
                }

                /* Sliding window RMS (N_sw = 50) */
                if (sat->mp_len >= MP_BUF_SIZE) {
                    double sw_sum_sq1 = 0, sw_sum_sq2 = 0;
                    for (int k = 0; k < MP_BUF_SIZE; k++) {
                        int idx = (sat->mp_head + k) % MP_BUF_SIZE;
                        double r1 = sat->mp1_vals[idx] - mean1;
                        double r2 = sat->mp2_vals[idx] - mean2;
                        sw_sum_sq1 += r1 * r1;
                        sw_sum_sq2 += r2 * r2;
                    }
                    g_state.mp1_sw_sum[sys] += sqrt(sw_sum_sq1 / MP_BUF_SIZE);
                    g_state.mp1_sw_count[sys]++;
                    if (sys == 1 && g_state.mp1_sw_count[1] <= 3)
                        LOGW("mp1_sw[GPS] cnt=%d sw_rms=%.3f", g_state.mp1_sw_count[1], sqrt(sw_sum_sq1 / MP_BUF_SIZE));
                    if (freq_is_l5(grouped[j].l2_freq)) {
                        g_state.mp5_sw_sum[sys] += sqrt(sw_sum_sq2 / MP_BUF_SIZE);
                        g_state.mp5_sw_count[sys]++;
                    } else {
                        g_state.mp2_sw_sum[sys] += sqrt(sw_sum_sq2 / MP_BUF_SIZE);
                        g_state.mp2_sw_count[sys]++;
                    }
                }
            }
        }

        /* PR noise: epoch-to-epoch differencing (dP1 - dL1).
           Skip on ADR cycle-slip/reset to avoid inflated RMS. */
        if (has_l1 && isfinite(p1) && isfinite(l1)) {
            int adr_bad = grouped[j].l1_adr_state & 6; /* ADR_STATE_RESET=2 | CYCLE_SLIP=4 */
            if (adr_bad) {
                sat->has_prev_td_pr = 0;
                sat->has_prev_td_cp = 0;
            }
        }

        /* Ionospheric delay rate: dGF/dt */
        if (has_l1 && has_l2 && isfinite(l1) && isfinite(l2)) {
            double gf = l1 - l2;
            /* Skip on ADR cycle-slip/reset to avoid inflated IOD */
            int adr1_bad = grouped[j].l1_adr_state & 6;
            int adr2_bad = grouped[j].l2_adr_state & 6;
            if (adr1_bad || adr2_bad) {
                sat->has_prev_ion = 0;
            }
            if (sat->has_prev_ion && epoch_time > sat->prev_time_ion) {
                double dt = (epoch_time - sat->prev_time_ion) * 1e-9;
                if (dt > 0 && dt < 3600) {
                    double dgf = gf - sat->prev_gf_ion;
                    /* IOD_k1 = dGF * f2²/(f1²-f2²) */
                    double f1m = get_f1_mhz(cons);
                    double f2m = get_f2_mhz(cons, grouped[j].l2_freq);
                    double f1 = f1m * 1e6, f2 = f2m * 1e6;
                    double denom = f1*f1 - f2*f2;
                    if (denom > 0) {
                        double k1 = f2*f2 / denom;
                        double k2 = f1*f1 / denom;
                        double ion_rate = dgf * k1 / dt;
                        double ion_rate_k2 = dgf * k2 / dt;
                        g_state.ion_rate_sum[sys] += fabs(ion_rate);
                        g_state.ion_rate_count[sys]++;
                        g_state.ion_rate_k2_sum[sys] += fabs(ion_rate_k2);
                        g_state.ion_rate_k2_count[sys]++;
                    }
                }
            }
            sat->prev_gf_ion = gf;
            sat->prev_time_ion = epoch_time;
            sat->has_prev_ion = 1;
        }
    }

    /* ── TD noise: single-receiver triple-difference ── */
    /*   SD_ij = inter-satellite single-difference (sat_i - sat_ref)
         TD = SD(t) - SD(t-1)  (inter-epoch double-difference)
         Reference satellite: highest elevation among common sats.
         PR: pseudorange only — works without carrier phase.
         CP: L1 metres → cycles, filter |TD| > 0.5 cycles (requires ADR). */
    {
        for (int sys = 1; sys <= 6; sys++) {
            if (sys != 1 && sys != 3 && sys != 5 && sys != 6) continue;

            /* ── TD-PR: pseudorange only (no carrier phase needed) ── */
            {
                int td_sats[64], td_n = 0;
                double td_p1[64];
                float  td_elev[64];

                for (int j = 0; j < n_grouped; j++) {
                    if (cons_to_sys(grouped[j].cons) != sys) continue;
                    if (!grouped[j].has_l1) continue;
                    double p1 = grouped[j].l1_p;
                    if (!isfinite(p1)) continue;

                    sat_state_t *sat = sat_get(grouped[j].cons, grouped[j].svid);
                    if (!sat || !sat->has_prev_td_pr) continue;

                    if (td_n >= 64) break;
                    td_sats[td_n] = j;
                    td_p1[td_n] = p1;
                    td_elev[td_n] = isfinite(grouped[j].elev) ? grouped[j].elev : 0.0f;
                    td_n++;
                }

                if (td_n >= 2) {
                    int ref_j = 0;
                    float ref_elev = td_elev[0];
                    for (int k = 1; k < td_n; k++) {
                        if (td_elev[k] > ref_elev) { ref_elev = td_elev[k]; ref_j = k; }
                    }

                    sat_state_t *ref_sat = sat_get(grouped[td_sats[ref_j]].cons,
                                                   grouped[td_sats[ref_j]].svid);
                    if (ref_sat) {
                        double ref_p1_prev = ref_sat->prev_p1_td;
                        double ref_p1_curr = td_p1[ref_j];
                        double pr_sumsq = 0;
                        int    pr_n = 0;

                        for (int k = 0; k < td_n; k++) {
                            if (k == ref_j) continue;
                            sat_state_t *sat = sat_get(grouped[td_sats[k]].cons,
                                                       grouped[td_sats[k]].svid);
                            if (!sat) continue;
                            double sd_curr = td_p1[k] - ref_p1_curr;
                            double sd_prev = sat->prev_p1_td - ref_p1_prev;
                            double td_pr = sd_curr - sd_prev;
                            pr_sumsq += td_pr * td_pr;
                            pr_n++;
                        }

                        if (pr_n > 0) {
                            g_state.td_pr_sumsq[sys] += pr_sumsq;
                            g_state.td_pr_count[sys] += pr_n;
                        }
                    }
                }
            }

            /* ── TD-CP: carrier phase (requires ADR) ── */
            {
                int td_sats[64], td_n = 0;
                double td_l1_cycles[64];
                float  td_elev[64];

                for (int j = 0; j < n_grouped; j++) {
                    if (cons_to_sys(grouped[j].cons) != sys) continue;
                    if (!grouped[j].has_l1) continue;
                    double l1 = grouped[j].l1_l;
                    if (!isfinite(l1)) continue;

                    sat_state_t *sat = sat_get(grouped[j].cons, grouped[j].svid);
                    if (!sat || !sat->has_prev_td_cp) continue;

                    if (td_n >= 64) break;
                    td_sats[td_n] = j;
                    double f1m = get_f1_mhz(grouped[j].cons);
                    double wl = CLIGHT / (f1m * 1e6);
                    td_l1_cycles[td_n] = l1 / wl;
                    td_elev[td_n] = isfinite(grouped[j].elev) ? grouped[j].elev : 0.0f;
                    td_n++;
                }

                if (td_n >= 2) {
                    int ref_j = 0;
                    float ref_elev = td_elev[0];
                    for (int k = 1; k < td_n; k++) {
                        if (td_elev[k] > ref_elev) { ref_elev = td_elev[k]; ref_j = k; }
                    }

                    sat_state_t *ref_sat = sat_get(grouped[td_sats[ref_j]].cons,
                                                   grouped[td_sats[ref_j]].svid);
                    if (ref_sat) {
                        double ref_l1c_prev = ref_sat->prev_l1_cycles;
                        double ref_l1c_curr = td_l1_cycles[ref_j];
                        double cp_sumsq = 0;
                        int    cp_n = 0;

                        for (int k = 0; k < td_n; k++) {
                            if (k == ref_j) continue;
                            sat_state_t *sat = sat_get(grouped[td_sats[k]].cons,
                                                       grouped[td_sats[k]].svid);
                            if (!sat) continue;
                            double sd_curr_c = td_l1_cycles[k] - ref_l1c_curr;
                            double sd_prev_c = sat->prev_l1_cycles - ref_l1c_prev;
                            double td_cp = sd_curr_c - sd_prev_c;
                            if (fabs(td_cp) <= 0.5) {
                                cp_sumsq += td_cp * td_cp;
                                cp_n++;
                            }
                        }

                        if (cp_n > 0) {
                            g_state.td_cp_sumsq[sys] += cp_sumsq;
                            g_state.td_cp_count[sys] += cp_n;
                        }
                    }
                }
            }
        }

        /* Store current-epoch values as "previous" for next epoch */
        for (int j = 0; j < n_grouped; j++) {
            int cons = grouped[j].cons;
            int svid = grouped[j].svid;
            if (!grouped[j].has_l1) continue;

            sat_state_t *sat = sat_get_or_create(cons, svid);
            if (!sat) continue;

            double p1 = grouped[j].l1_p;
            if (isfinite(p1)) {
                sat->prev_p1_td = p1;
                sat->has_prev_td_pr = 1;
            }

            double l1 = grouped[j].l1_l;
            if (isfinite(l1)) {
                double f1m = get_f1_mhz(cons);
                double wl = CLIGHT / (f1m * 1e6);
                sat->prev_l1_cycles = l1 / wl;
                sat->has_prev_td_cp = 1;
            }

            sat->prev_elev = isfinite(grouped[j].elev) ? grouped[j].elev : 0.0f;
        }
    }

    /* ── Record time-series snapshot ── */
    {
        int pos = g_state.tss_head;
        epoch_snapshot_t *snap = &g_state.tss_ring[pos];

        snap->epoch = g_state.epoch_counter;

        /* Per-system mean elevation (from grouped satellites) */
        double elev_sum_sys[7] = {0};
        int    elev_cnt_sys[7] = {0};
        for (int j = 0; j < n_grouped; j++) {
            int sys = cons_to_sys(grouped[j].cons);
            if (isfinite((double)grouped[j].elev)) {
                elev_sum_sys[sys] += grouped[j].elev;
                elev_cnt_sys[sys]++;
            }
        }

        for (int s = 0; s < 7; s++) {
            /* SNR mean */
            double snr_sum = 0; int snr_n = 0;
            for (int i = 0; i < g_state.sat_capacity; i++) {
                sat_state_t *sat = &g_state.sat_table[i];
                if (sat->active && sat->sys_idx == s && sat->snr_count > 0) {
                    snr_sum += sat->snr_sum / sat->snr_count;
                    snr_n++;
                }
            }
            snap->snr_mean[s] = snr_n > 0 ? (float)(snr_sum / snr_n) : NAN;

            /* CMC RMS */
            if (g_state.cmc_count[s] > 0)
                snap->cmc_rms[s] = (float)sqrt(g_state.cmc_sumsq[s] / g_state.cmc_count[s]);
            else snap->cmc_rms[s] = NAN;

            /* MP1 SW RMS */
            if (g_state.mp1_sw_count[s] > 0)
                snap->mp1_sw_rms[s] = (float)(g_state.mp1_sw_sum[s] / g_state.mp1_sw_count[s]);
            else snap->mp1_sw_rms[s] = NAN;

            /* Ion rate k1 */
            if (g_state.ion_rate_count[s] > 0)
                snap->ion_rate[s] = (float)(g_state.ion_rate_sum[s] / g_state.ion_rate_count[s]);
            else snap->ion_rate[s] = NAN;

            /* Ion rate k2 */
            if (g_state.ion_rate_k2_count[s] > 0)
                snap->ion_rate_k2[s] = (float)(g_state.ion_rate_k2_sum[s] / g_state.ion_rate_k2_count[s]);
            else snap->ion_rate_k2[s] = NAN;

            /* Combined slips (cumulative) */
            snap->slip_combined[s] = g_state.mw_slips[s] + g_state.gf_poly_slips[s];

            /* Satellite count per system for this epoch */
            {
                int cnt = 0;
                for (int i = 0; i < g_state.sat_capacity; i++) {
                    if (g_state.sat_table[i].active && g_state.sat_table[i].sys_idx == s)
                        cnt++;
                }
                snap->sat_count[s] = cnt;
            }

            /* Elevation bins */
            for (int eb = 0; eb < ELEV_BIN_COUNT; eb++)
                snap->elev_bins[s][eb] = g_state.elev_bins[s][eb];

            /* Data completeness (per-epoch dual-freq卫星占比) */
            if (g_state.epoch_sat_count[s] > 0)
                snap->completeness[s] = (float)((double)g_state.epoch_dual[s] / g_state.epoch_sat_count[s]);
            else snap->completeness[s] = 0.0f;

            /* TD noise: per-epoch RMS (BD 420022-2019 §6.5/6.6) */
            if (g_state.td_pr_count[s] > 0)
                snap->td_pr_noise_rms[s] = (float)sqrt(g_state.td_pr_sumsq[s] / (8.0 * g_state.td_pr_count[s]));
            else snap->td_pr_noise_rms[s] = NAN;
            if (g_state.td_cp_count[s] > 0)
                snap->td_cp_noise_rms[s] = (float)sqrt(g_state.td_cp_sumsq[s] / (8.0 * g_state.td_cp_count[s]));
            else snap->td_cp_noise_rms[s] = NAN;

            /* MP2 SW RMS: cumulative */
            if (g_state.mp2_sw_count[s] > 0)
                snap->mp2_sw_rms[s] = (float)(g_state.mp2_sw_sum[s] / g_state.mp2_sw_count[s]);
            else snap->mp2_sw_rms[s] = NAN;

            /* MP5 SW RMS: cumulative */
            if (g_state.mp5_sw_count[s] > 0)
                snap->mp5_sw_rms[s] = (float)(g_state.mp5_sw_sum[s] / g_state.mp5_sw_count[s]);
            else snap->mp5_sw_rms[s] = NAN;

            /* GF epoch-diff RMS: cumulative */
            if (g_state.gf_diff_count[s] > 0)
                snap->gf_rms[s] = (float)sqrt(g_state.gf_diff_sumsq[s] / g_state.gf_diff_count[s]);
            else snap->gf_rms[s] = NAN;

            /* Per-system mean elevation */
            snap->elev_mean[s] = elev_cnt_sys[s] > 0
                ? (float)(elev_sum_sys[s] / elev_cnt_sys[s]) : NAN;
        }

        /* ── 快照收尾 ── */
        g_state.tss_head = (pos + 1) % TSS_RING_SIZE;
        g_state.tss_count++;

        pthread_mutex_unlock(&g_state.mutex);
    }
}

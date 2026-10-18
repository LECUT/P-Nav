#ifndef GNSS_QUALITY_H
#define GNSS_QUALITY_H

#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

/* ── Constants ─────────────────────────────────────────────────────── */

#define CLIGHT      299792458.0         /* speed of light (m/s) */
#define GPS_L1_MHZ  1575.42
#define BDS_B1I_MHZ 1561.098
#define GAL_E1_MHZ  1575.42
#define GLO_G1_MHZ  1602.0             /* nominal; real GLO freq varies by slot */

#define MAX_SNR_BINS    4
#define MAX_CONS         5              /* 0=GLO,1=GPS,2=GAL,3=BDS,4=QZS */
#define FREQ_TOLERANCE  10.0           /* MHz tolerance for freq band matching */

/* ── Per-measurement input structure ───────────────────────────────── */

typedef struct {
    int    constellation;   /* GnssStatus.CONSTELLATION_*: 0=UNKNOWN,1=GPS,2=SBS,3=GLO,4=QZS,5=BDS,6=GAL */
    int    svid;
    double carrier_freq_hz; /* Hz, or NAN if unknown */
    double snr_db;           /* dB-Hz, or NAN if not available */
    double pr_m;             /* pseudorange in metres, or NAN */
    double adr_m;            /* accumulated delta range (carrier phase) in metres, or NAN */
    int    adr_state;        /* ADR state flags (GnssMeasurement.ADR_STATE_*) */
    int64_t recv_time_nanos; /* receiver clock time for epoch grouping */
    float  elevation_deg;    /* satellite elevation angle (degrees), or NAN */
} raw_meas_t;

/* ── SNR bin constants ─────────────────────────────────────────────── */

#define SNR_BIN_0_20    0
#define SNR_BIN_20_30   1
#define SNR_BIN_30_40   2
#define SNR_BIN_40_60   3

/* ── Elevation bin constants ────────────────────────────────────────── */

#define ELEV_BIN_COUNT  4
#define ELEV_BIN_0_15   0
#define ELEV_BIN_15_30  1
#define ELEV_BIN_30_60  2
#define ELEV_BIN_60_90  3

/* ── Per-satellite info for FDE ── */
#define MAX_SAT_INFO 64
typedef struct {
    int    cons;           /* constellation: 1=GPS, 3=GLO, 5=BDS, 6=GAL */
    int    svid;
    double cmc_rms;        /* detrended CMC residual RMS (metres) */
    double snr_mean;       /* mean SNR (dB-Hz) */
    int    sample_count;   /* number of CMC residual samples */
} sat_info_t;

/* ── Quality report structure ──────────────────────────────────────── */

typedef struct {
    /* ── SNR (per system) ── */
    double snr_mean[7];           /* mean SNR in dB-Hz (per-satellite averaging) */
    int    snr_bins[7][MAX_SNR_BINS]; /* bin counts: [sys][bin] */
    double snr_bin_pct[7][MAX_SNR_BINS]; /* bin percentages */
    int    snr_sample_count[7];   /* total SNR samples per system */

    /* ── Satellite counts ── */
    int sat_total;                /* total measurements in this epoch */
    int sat_valid;                /* measurements with valid SNR */
    int sat_count[7];             /* per-system measurement count */

    /* ── CMC multipath RMS (metres) ── */
    double cmc_rms[7];            /* RMS of detrended CMC residuals */
    int    cmc_sample_count[7];

    /* ── Cycle slip counts ── */
    int gf_slip_count[7];         /* GF epoch-difference slips */
    int mw_slip_count[7];         /* MW recursive mean/RMS slips */
    int gf_poly_slip_count[7];    /* GF polynomial-fit supplementary slips */
    int slip_combined[7];         /* union of MW + GF-poly */

    /* ── MP multipath RMS (metres) ── */
    double mp1_rms[7];            /* MP1 (L1 multipath) RMS */
    double mp2_rms[7];            /* MP2 (L2 multipath) RMS — GPS/GLO */
    double mp5_rms[7];            /* MP5 (L5/E5a multipath) RMS — BDS/GAL */
    double mp1_sw_rms[7];         /* MP1 sliding-window mean RMS (N_sw=50) */
    double mp2_sw_rms[7];         /* MP2 sliding-window mean RMS */
    double mp5_sw_rms[7];         /* MP5 sliding-window mean RMS */

    /* ── Triple-difference noise (single-receiver) ── */
    double td_pr_noise_rms[7];    /* TD pseudorange noise RMS (metres) */
    double td_cp_noise_rms[7];    /* TD carrier-phase noise RMS (cycles) */

    /* ── Ionospheric delay rate (m/s) ── */
    double ion_rate_mean[7];      /* mean |IOD_k1| */
    int    ion_rate_count[7];
    double ion_rate_k2_mean[7];   /* mean |IOD_k2| on L2 frequency point */
    int    ion_rate_k2_count[7];

    /* ── Elevation distribution ── */
    int    elev_bins[7][ELEV_BIN_COUNT];  /* counts per elevation bin per system */

    /* ── Data completeness & fullness ── */
    double completeness[7];       /* DI_f = sum(A^j)/sum(B^j) */
    double avg_sats_per_epoch[7]; /* average satellite count per epoch */
    double dual_freq_ratio[7];    /* fraction of dual-frequency observations */
    double csr[7];                /* observations-per-slip ratio (CSR) */

    /* ── Overall epoch counters ── */
    int epoch_count;              /* total epochs processed */
    int total_obs;                /* total observation count */

    /* ── Status ── */
    int has_dual_freq;            /* 1 if any dual-freq data seen */
    int has_adr;                  /* 1 if any valid carrier-phase (ADR) seen */

    /* ── Per-satellite info for FDE ── */
    int sat_info_count;
    sat_info_t sat_info[MAX_SAT_INFO];

} quality_report_t;

/* ── API ───────────────────────────────────────────────────────────── */

/* Allocate and initialise internal state. Call once before processing. */
void quality_init(void);

/* Process one epoch of raw measurements. Arrays must all be of length n.
   Values that are not available should be set to NAN (for doubles) or 0 (for ints). */
void quality_process_epoch(const raw_meas_t *meas, int n);

/* Fill the report structure with current accumulated metrics. */
void quality_get_report(quality_report_t *report);

/* Free internal state. Safe to call quality_init() again afterwards. */
void quality_free(void);

/* ── Time-series API ─────────────────────────────────────────────────── */

#define TSS_BUF_SIZE    120    /* max epochs stored for chart display (~2 min at 1 Hz) */

/* Write a JSON string with per-epoch arrays suitable for MPAndroidChart.
   The caller provides a buffer (at least 16384 bytes recommended).
   Returns the number of characters written (excluding null terminator),
   or -1 if the buffer is too small. */
int quality_get_timeseries_json(char *buf, int bufsize);

#ifdef __cplusplus
}
#endif

#endif /* GNSS_QUALITY_H */

package com.ecut.pnav.ui.data;

/**
 * Live satellite measurement for the real-time measurement panel.
 */
public class SatMeasurement {
    public String prn;           // "G05", "C12", "R03" ...
    public int constellation;    // android.location.GnssStatus.CONSTELLATION_*
    public float cn0DbHz;        // C/N0 in dB-Hz
    public float elevationDeg;   // elevation angle in degrees, -1 if unknown
    public boolean hasCarrier;   // ADR_STATE_VALID
    public double carrierPhase;  // accumulated delta range in meters

    public SatMeasurement(String prn, int constellation, float cn0DbHz,
                          float elevationDeg, boolean hasCarrier, double carrierPhase) {
        this.prn = prn;
        this.constellation = constellation;
        this.cn0DbHz = cn0DbHz;
        this.elevationDeg = elevationDeg;
        this.hasCarrier = hasCarrier;
        this.carrierPhase = carrierPhase;
    }
}

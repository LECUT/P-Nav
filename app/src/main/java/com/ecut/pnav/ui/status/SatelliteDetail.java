package com.ecut.pnav.ui.status;

public class SatelliteDetail {
    public final int svid;
    public final int constellationType;
    public final float elevation;
    public final float azimuth;
    public final float cn0;
    public final boolean usedInFix;
    public final boolean hasEphemeris;
    public final double carrierFrequencyHz;

    public SatelliteDetail(int svid, int constellationType, float elevation, float azimuth,
                           float cn0, boolean usedInFix, boolean hasEphemeris,
                           double carrierFrequencyHz) {
        this.svid = svid;
        this.constellationType = constellationType;
        this.elevation = elevation;
        this.azimuth = azimuth;
        this.cn0 = cn0;
        this.usedInFix = usedInFix;
        this.hasEphemeris = hasEphemeris;
        this.carrierFrequencyHz = carrierFrequencyHz;
    }
}

package com.ecut.pnav.ui.status;

public class SatelliteInfo {
    public float elevation;
    public float azimuth;
    public int prn;
    public int constellationType;
    public float cn0;
    public boolean usedInFix;

    public SatelliteInfo(float elevation, float azimuth, int prn, int constellationType, float cn0, boolean usedInFix) {
        this.elevation = elevation;
        this.azimuth = azimuth;
        this.prn = prn;
        this.constellationType = constellationType;
        this.cn0 = cn0;
        this.usedInFix = usedInFix;
    }
}

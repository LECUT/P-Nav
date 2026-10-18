package com.ecut.pnav.ui.status;

public class Cn0Info {
    public final int constellationType;
    public final int svid;
    public final float cn0;
    public final double carrierFrequencyHz;

    public Cn0Info(int constellationType, int svid, float cn0, double carrierFrequencyHz) {
        this.constellationType = constellationType;
        this.svid = svid;
        this.cn0 = cn0;
        this.carrierFrequencyHz = carrierFrequencyHz;
    }

    /** 返回频率短标签，如 "B1I"、"L5"、"B2a" 等 */
    public String getFreqLabel() {
        if (carrierFrequencyHz <= 0) return "";
        double f = carrierFrequencyHz;
        switch (constellationType) {
            case android.location.GnssStatus.CONSTELLATION_GPS:
                if (Math.abs(f - 1.57542e9) < 1e6) return "L1";
                if (Math.abs(f - 1.22760e9) < 1e6) return "L2";
                if (Math.abs(f - 1.17645e9) < 1e6) return "L5";
                break;
            case android.location.GnssStatus.CONSTELLATION_BEIDOU:
                if (Math.abs(f - 1.561098e9) < 1e6) return "B1I";
                if (Math.abs(f - 1.57542e9) < 1e6)  return "B1C";
                if (Math.abs(f - 1.17645e9) < 1e6)  return "B2a";
                if (Math.abs(f - 1.20714e9) < 1e6)  return "B2b";
                break;
            case android.location.GnssStatus.CONSTELLATION_GALILEO:
                if (Math.abs(f - 1.57542e9) < 1e6) return "E1";
                if (Math.abs(f - 1.20714e9) < 1e6) return "E5b";
                if (Math.abs(f - 1.17645e9) < 1e6) return "E5a";
                break;
            case android.location.GnssStatus.CONSTELLATION_GLONASS:
                if (Math.abs(f - 1.602e9) < 8e6)   return "G1";
                if (Math.abs(f - 1.246e9) < 8e6)   return "G2";
                break;
        }
        return "";
    }
}

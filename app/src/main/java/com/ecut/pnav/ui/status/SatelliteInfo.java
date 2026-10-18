/*
 * Derived from the open-source myGNSS application.
 *
 *     Copyright (c) 2024, z1Yang-1, All rights reserved.
 *     Licensed under the BSD 2-Clause License.
 *     https://github.com/z1Yang-1/myGNSS
 *
 * Substantially modified and extended for P-Nav.
 * See THIRD-PARTY-NOTICES.md for details.
 */
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

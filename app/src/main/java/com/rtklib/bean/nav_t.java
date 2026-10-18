/*
 * 移植自 RTKLIB 2.4.3 b34（T.TAKASU 原作，C 语言实现）。
 * 原始 Java 源码经反编译工具转写，类名与结构有所调整。
 *
 *     Copyright (C) 2007-2020 by T.TAKASU, All rights reserved.
 *
 * 依 BSD 2-Clause 许可使用。许可全文见工程根目录 LICENSE-RTKLIB。
 */
package com.rtklib.bean;

import com.rtklib.bean.dgps_t;
import com.rtklib.bean.eph_t;
import com.rtklib.bean.erp_t;
import com.rtklib.bean.geph_t;
import com.rtklib.bean.pclk_t;
import com.rtklib.bean.pcv_t;
import com.rtklib.bean.peph_t;
import com.rtklib.bean.sbsion_t;
import com.rtklib.bean.sbssat_t;
import com.rtklib.bean.seph_t;
import com.rtklib.bean.ssr_t;
import com.rtklib.bean.tec_t;

public class nav_t {
    public static int NSYS = 7;
    public static final int MAXRCV = 64;
    public static final int MINPRNGPS = 1;
    public static final int MAXPRNGPS = 32;
    public static final int MINPRNSBS = 120;
    public static final int MAXPRNSBS = 158;
    public static final int MINPRNQZS = 193;
    public static final int MAXPRNQZS = 202;
    public static final int MAXPRNGLO = 27;
    public static final int MINPRNGLO = 1;
    public static final int MINPRNGAL = 1;
    public static final int MAXPRNGAL = 36;
    public static final int MINPRNCMP = 1;
    public static final int MAXPRNCMP = 63;
    public static final int MINPRNIRN = 1;
    public static final int MAXPRNIRN = 14;
    public static final int MINPRNLEO = 1;
    public static final int MAXPRNLEO = 10;
    public static final int NSATGPS = 32;
    public static final int NSATGLO = 27;
    public static final int NSATGAL = 36;
    public static final int NSATQZS = 10;
    public static final int NSATIRN = 14;
    public static final int NSATCMP = 63;
    public static final int NSATLEO = 10;
    public static final int NSATSBS = 39;
    public static final int MAXBAND = 10;
    static final int NSYSGPS = 1;
    public int n;
    public int nmax;
    public int ng;
    public int ngmax;
    public int ns;
    public int nsmax;
    private int ne;
    private int nemax;
    public int nc;
    private int ncmax;
    private int na;
    private int namax;
    private int nt;
    private int ntmax;
    public eph_t[] eph;
    public geph_t[] geph;
    public seph_t[] seph;
    public peph_t[] peph;
    private pclk_t[] pclk;
    private tec_t[] tec;
    public erp_t erp;
    public double[] utc_gps;
    public double[] utc_glo;
    public double[] utc_gal;
    public double[] utc_qzs;
    public double[] utc_cmp;
    public double[] utc_irn;
    public double[] utc_sbs;
    public double[] ion_gps;
    public double[] ion_gal;
    public double[] ion_qzs;
    public double[] ion_cmp;
    public double[] ion_irn;
    public int[] glo_fcn;
    public double[][] cbias;
    double[][][] rbias;
    public pcv_t[] pcvs;
    public sbssat_t sbssat;
    sbsion_t[] sbsion;
    dgps_t[] dgps;
    ssr_t[] ssr;

    public nav_t() {
        this.eph = new eph_t[this.n];
        this.geph = new geph_t[this.n];
        this.seph = new seph_t[this.n];
        this.peph = new peph_t[this.n];
        this.pclk = new pclk_t[this.n];
        this.utc_gps = new double[8];
        this.utc_glo = new double[8];
        this.utc_gal = new double[8];
        this.utc_qzs = new double[8];
        this.utc_cmp = new double[8];
        this.utc_irn = new double[9];
        this.utc_sbs = new double[4];
        this.ion_gps = new double[8];
        this.ion_gal = new double[4];
        this.ion_qzs = new double[8];
        this.ion_cmp = new double[8];
        this.ion_irn = new double[8];
        this.glo_fcn = new int[32];
        this.cbias = new double[nav_t.getMaxSat()][3];
        this.rbias = new double[64][2][3];
        this.pcvs = new pcv_t[nav_t.getMaxSat()];
        this.sbsion = new sbsion_t[11];
        this.dgps = new dgps_t[nav_t.getMaxSat()];
        this.ssr = new ssr_t[nav_t.getMaxSat()];
    }

    public pclk_t[] getPclk() {
        return this.pclk;
    }

    public void setPclk(pclk_t[] pclk) {
        this.pclk = pclk;
    }

    public tec_t[] getTec() {
        return this.tec;
    }

    public void setTec(tec_t[] tec) {
        this.tec = tec;
    }

    public static int getNSYS() {
        return NSYS;
    }

    public static void setNSYS(int NSYS) {
        nav_t.NSYS = NSYS;
    }

    public int getN() {
        return this.n;
    }

    public void setN(int n) {
        this.n = n;
    }

    public int getNmax() {
        return this.nmax;
    }

    public void setNmax(int nmax) {
        this.nmax = nmax;
    }

    public int getNg() {
        return this.ng;
    }

    public void setNg(int ng) {
        this.ng = ng;
    }

    public int getNgmax() {
        return this.ngmax;
    }

    public void setNgmax(int ngmax) {
        this.ngmax = ngmax;
    }

    public int getNs() {
        return this.ns;
    }

    public void setNs(int ns) {
        this.ns = ns;
    }

    public int getNsmax() {
        return this.nsmax;
    }

    public void setNsmax(int nsmax) {
        this.nsmax = nsmax;
    }

    public int getNe() {
        return this.ne;
    }

    public int setNe(int ne) {
        this.ne = ne;
        return ne;
    }

    public int getNemax() {
        return this.nemax;
    }

    public int setNemax(int nemax) {
        this.nemax = nemax;
        return nemax;
    }

    public int getNc() {
        return this.nc;
    }

    public void setNc(int nc) {
        this.nc = nc;
    }

    public int getNcmax() {
        return this.ncmax;
    }

    public void setNcmax(int ncmax) {
        this.ncmax = ncmax;
    }

    public int getNa() {
        return this.na;
    }

    public void setNa(int na) {
        this.na = na;
    }

    public int getNamax() {
        return this.namax;
    }

    public void setNamax(int namax) {
        this.namax = namax;
    }

    public int getNt() {
        return this.nt;
    }

    public void setNt(int nt) {
        this.nt = nt;
    }

    public int getNtmax() {
        return this.ntmax;
    }

    public void setNtmax(int ntmax) {
        this.ntmax = ntmax;
    }

    public eph_t[] getEph() {
        return this.eph;
    }

    public void setEph(eph_t[] eph) {
        this.eph = eph;
    }

    public geph_t[] getGeph() {
        return this.geph;
    }

    public void setGeph(geph_t[] geph) {
        this.geph = geph;
    }

    public seph_t[] getSeph() {
        return this.seph;
    }

    public void setSeph(seph_t[] seph) {
        this.seph = seph;
    }

    public peph_t[] getPeph() {
        return this.peph;
    }

    public void setPeph(peph_t[] peph) {
        this.peph = peph;
    }

    public erp_t getErp() {
        return this.erp;
    }

    public void setErp(erp_t erp) {
        this.erp = erp;
    }

    public double[] getUtc_gps() {
        return this.utc_gps;
    }

    public void setUtc_gps(double[] utc_gps) {
        this.utc_gps = utc_gps;
    }

    public double[] getUtc_glo() {
        return this.utc_glo;
    }

    public void setUtc_glo(double[] utc_glo) {
        this.utc_glo = utc_glo;
    }

    public double[] getUtc_gal() {
        return this.utc_gal;
    }

    public void setUtc_gal(double[] utc_gal) {
        this.utc_gal = utc_gal;
    }

    public double[] getUtc_qzs() {
        return this.utc_qzs;
    }

    public void setUtc_qzs(double[] utc_qzs) {
        this.utc_qzs = utc_qzs;
    }

    public double[] getUtc_cmp() {
        return this.utc_cmp;
    }

    public void setUtc_cmp(double[] utc_cmp) {
        this.utc_cmp = utc_cmp;
    }

    public double[] getUtc_irn() {
        return this.utc_irn;
    }

    public void setUtc_irn(double[] utc_irn) {
        this.utc_irn = utc_irn;
    }

    public double[] getUtc_sbs() {
        return this.utc_sbs;
    }

    public void setUtc_sbs(double[] utc_sbs) {
        this.utc_sbs = utc_sbs;
    }

    public double[] getIon_gps() {
        return this.ion_gps;
    }

    public void setIon_gps(double[] ion_gps) {
        this.ion_gps = ion_gps;
    }

    public double[] getIon_gal() {
        return this.ion_gal;
    }

    public void setIon_gal(double[] ion_gal) {
        this.ion_gal = ion_gal;
    }

    public double[] getIon_qzs() {
        return this.ion_qzs;
    }

    public void setIon_qzs(double[] ion_qzs) {
        this.ion_qzs = ion_qzs;
    }

    public double[] getIon_cmp() {
        return this.ion_cmp;
    }

    public void setIon_cmp(double[] ion_cmp) {
        this.ion_cmp = ion_cmp;
    }

    public double[] getIon_irn() {
        return this.ion_irn;
    }

    public void setIon_irn(double[] ion_irn) {
        this.ion_irn = ion_irn;
    }

    public int[] getGlo_fcn() {
        return this.glo_fcn;
    }

    public void setGlo_fcn(int[] glo_fcn) {
        this.glo_fcn = glo_fcn;
    }

    public double[][] getCbias() {
        return this.cbias;
    }

    public void setCbias(double[][] cbias) {
        this.cbias = cbias;
    }

    public double[][][] getRbias() {
        return this.rbias;
    }

    public void setRbias(double[][][] rbias) {
        this.rbias = rbias;
    }

    public pcv_t[] getPcvs() {
        return this.pcvs;
    }

    public void setPcvs(pcv_t[] pcvs) {
        this.pcvs = pcvs;
    }

    public sbssat_t getSbssat() {
        return this.sbssat;
    }

    public void setSbssat(sbssat_t sbssat) {
        this.sbssat = sbssat;
    }

    public sbsion_t[] getSbsion() {
        return this.sbsion;
    }

    public void setSbsion(sbsion_t[] sbsion) {
        this.sbsion = sbsion;
    }

    public dgps_t[] getDgps() {
        return this.dgps;
    }

    public void setDgps(dgps_t[] dgps) {
        this.dgps = dgps;
    }

    public ssr_t[] getSsr() {
        return this.ssr;
    }

    public void setSsr(ssr_t[] ssr) {
        this.ssr = ssr;
    }

    public static int getMaxSat() {
        return 231;
    }
}


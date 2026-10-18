/*
 * 移植自 RTKLIB 2.4.3 b34（T.TAKASU 原作，C 语言实现）。
 * 原始 Java 源码经反编译工具转写，类名与结构有所调整。
 *
 *     Copyright (C) 2007-2020 by T.TAKASU, All rights reserved.
 *
 * 依 BSD 2-Clause 许可使用。许可全文见工程根目录 LICENSE-RTKLIB。
 */
package com.rtklib.bean;

import com.rtklib.bean.nav_t;
import com.rtklib.bean.pcv_t;
import com.rtklib.bean.snrmask_t;

/**
 * 处理选项类型 — EX 2.5.0 版本
 * err[5]→[8], eratio[NFREQ]→[MAXFREQ=6], maxinno→[2](phase/code)
 * 新增: thresdop/varholdamb/gainholdamb/gpsmodear/arfilter/minfixsats/minholdsats/mindropsats
 */
public class prcopt_t {
    public static final int MAXANT = 64;
    public int mode;
    int soltype;
    int nf;
    public int navsys;
    double elmin;
    snrmask_t snrmask;
    int sateph;
    int modear;
    public int glomodear;
    int gpsmodear;                             /* GPS AR mode (0:off,1:on) */
    int bdsmodear;
    int arfilter;                              /* AR filtering to reject bad sats (0:off,1:on) */
    int maxout;
    int minlock;
    int minfixsats;                            /* min sats to fix integer ambiguities */
    int minholdsats;                           /* min sats to hold integer ambiguities */
    int mindropsats;                           /* min sats to drop sats in AR */
    int minfix;
    int armaxiter;
    public int ionoopt;
    public int tropopt;
    public int varmodel;                         /* stochastic model: 0=height-angle, 1=SNR */
    int dynamics;
    int tidecorr;
    int niter;
    int codesmooth;
    int intpref;
    int sbascorr;
    int sbassatsel;
    int rovpos;
    public int refpos;
    double[] eratio = new double[6];           /* MAXFREQ=6 */
    double[] err = new double[8];              /* [reserved,constant,elevation,baseline,doppler,snr-max,snr,rcv_std] */
    double[] std = new double[3];
    double[] prn = new double[6];
    double sclkstab;
    double[] thresar = new double[8];
    double elmaskar;
    double elmaskhold;
    double thresslip;
    double thresdop;                           /* slip threshold of doppler (m) */
    double maxtdiff;
    double[] maxinno = new double[2];          /* reject threshold [0]=phase [1]=code (m) */
    double maxgdop;
    double varholdamb;                         /* variance for fix-and-hold (cycle^2) */
    double gainholdamb;                        /* gain for GLO/SBAS ambiguity */
    double[] baseline = new double[2];
    double[] ru = new double[3];
    double[] rb = new double[3];
    char[][] anttype = new char[2][64];
    double[][] antdel = new double[2][3];
    public pcv_t[] pcvr = new pcv_t[2];
    public byte[] exsats = new byte[nav_t.getMaxSat()];
    int maxaveep;
    int initrst;
    int outsingle;
    int syncsol;
    char[][] rnxopt = new char[2][256];
    int[] posopt = new int[6];
    double[][] odisp = new double[2][66];
    int freqopt;
    char[] pppopt = new char[256];

    public void setMode(int mode) { this.mode = mode; }
    public void setSoltype(int soltype) { this.soltype = soltype; }
    public void setNf(int nf) { this.nf = nf; }
    public void setNavsys(int navsys) { this.navsys = navsys; }
    public void setElmin(double elmin) { this.elmin = elmin; }
    public void setSnrmask(snrmask_t snrmask) { this.snrmask = snrmask; }
    public void setModear(int modear) { this.modear = modear; }
    public void setGlomodear(int glomodear) { this.glomodear = glomodear; }
    public void setGpsmodear(int gpsmodear) { this.gpsmodear = gpsmodear; }
    public void setBdsmodear(int bdsmodear) { this.bdsmodear = bdsmodear; }
    public void setArfilter(int arfilter) { this.arfilter = arfilter; }
    public void setMaxout(int maxout) { this.maxout = maxout; }
    public void setMinlock(int minlock) { this.minlock = minlock; }
    public void setMinfixsats(int minfixsats) { this.minfixsats = minfixsats; }
    public void setMinholdsats(int minholdsats) { this.minholdsats = minholdsats; }
    public void setMindropsats(int mindropsats) { this.mindropsats = mindropsats; }
    public void setMinfix(int minfix) { this.minfix = minfix; }
    public void setArmaxiter(int armaxiter) { this.armaxiter = armaxiter; }
    public void setIonoopt(int ionoopt) { this.ionoopt = ionoopt; }
    public void setTropopt(int tropopt) { this.tropopt = tropopt; }
    public void setVarmodel(int varmodel) { this.varmodel = varmodel; }
    public void setDynamics(int dynamics) { this.dynamics = dynamics; }
    public void setTidecorr(int tidecorr) { this.tidecorr = tidecorr; }
    public void setNiter(int niter) { this.niter = niter; }
    public void setCodesmooth(int codesmooth) { this.codesmooth = codesmooth; }
    public void setIntpref(int intpref) { this.intpref = intpref; }
    public void setSbascorr(int sbascorr) { this.sbascorr = sbascorr; }
    public void setSbassatsel(int sbassatsel) { this.sbassatsel = sbassatsel; }
    public void setRovpos(int rovpos) { this.rovpos = rovpos; }
    public void setRefpos(int refpos) { this.refpos = refpos; }
    public void setEratio(double[] eratio) { this.eratio = eratio; }
    public void setErr(double[] err) { this.err = err; }
    public void setStd(double[] std) { this.std = std; }
    public void setPrn(double[] prn) { this.prn = prn; }
    public void setSclkstab(double sclkstab) { this.sclkstab = sclkstab; }
    public void setThresar(double[] thresar) { this.thresar = thresar; }
    public void setElmaskar(double elmaskar) { this.elmaskar = elmaskar; }
    public void setElmaskhold(double elmaskhold) { this.elmaskhold = elmaskhold; }
    public void setThresslip(double thresslip) { this.thresslip = thresslip; }
    public void setThresdop(double thresdop) { this.thresdop = thresdop; }
    public void setMaxtdiff(double maxtdiff) { this.maxtdiff = maxtdiff; }
    public void setMaxinno(double[] maxinno) { this.maxinno = maxinno; }
    public void setMaxinnoPhase(double v) { this.maxinno[0] = v; }
    public void setMaxinnoCode(double v) { this.maxinno[1] = v; }
    public void setMaxgdop(double maxgdop) { this.maxgdop = maxgdop; }
    public void setVarholdamb(double varholdamb) { this.varholdamb = varholdamb; }
    public void setGainholdamb(double gainholdamb) { this.gainholdamb = gainholdamb; }
    public void setBaseline(double[] baseline) { this.baseline = baseline; }
    public void setRu(double[] ru) { this.ru = ru; }
    public void setRb(double[] rb) { this.rb = rb; }
    public void setAnttype(char[][] anttype) { this.anttype = anttype; }
    public void setAntdel(double[][] antdel) { this.antdel = antdel; }
    public void setPcvr(pcv_t[] pcvr) { this.pcvr = pcvr; }
    public void setExsats(byte[] exsats) { this.exsats = exsats; }
    public void setMaxaveep(int maxaveep) { this.maxaveep = maxaveep; }
    public void setInitrst(int initrst) { this.initrst = initrst; }
    public void setOutsingle(int outsingle) { this.outsingle = outsingle; }
    public void setSyncsol(int syncsol) { this.syncsol = syncsol; }
    public void setRnxopt(char[][] rnxopt) { this.rnxopt = rnxopt; }
    public void setPosopt(int[] posopt) { this.posopt = posopt; }
    public void setOdisp(double[][] odisp) { this.odisp = odisp; }
    public void setFreqopt(int freqopt) { this.freqopt = freqopt; }
    public void setPppopt(char[] pppopt) { this.pppopt = pppopt; }
    public void setSateph(int sateph) { this.sateph = sateph; }

    public int getMode() { return this.mode; }
    public int getSoltype() { return this.soltype; }
    public int getNf() { return this.nf; }
    public int getNavsys() { return this.navsys; }
    public double getElmin() { return this.elmin; }
    public snrmask_t getSnrmask() { return this.snrmask; }
    public int getSateph() { return this.sateph; }
    public int getModear() { return this.modear; }
    public int getGlomodear() { return this.glomodear; }
    public int getGpsmodear() { return this.gpsmodear; }
    public int getBdsmodear() { return this.bdsmodear; }
    public int getArfilter() { return this.arfilter; }
    public int getMaxout() { return this.maxout; }
    public int getMinlock() { return this.minlock; }
    public int getMinfixsats() { return this.minfixsats; }
    public int getMinholdsats() { return this.minholdsats; }
    public int getMindropsats() { return this.mindropsats; }
    public int getMinfix() { return this.minfix; }
    public int getArmaxiter() { return this.armaxiter; }
    public int getIonoopt() { return this.ionoopt; }
    public int getTropopt() { return this.tropopt; }
    public int getVarmodel() { return this.varmodel; }
    public int getDynamics() { return this.dynamics; }
    public int getTidecorr() { return this.tidecorr; }
    public int getNiter() { return this.niter; }
    public int getCodesmooth() { return this.codesmooth; }
    public int getIntpref() { return this.intpref; }
    public int getSbascorr() { return this.sbascorr; }
    public int getSbassatsel() { return this.sbassatsel; }
    public int getRovpos() { return this.rovpos; }
    public int getRefpos() { return this.refpos; }
    public double[] getEratio() { return this.eratio; }
    public double[] getErr() { return this.err; }
    public double[] getStd() { return this.std; }
    public double[] getPrn() { return this.prn; }
    public double getSclkstab() { return this.sclkstab; }
    public double[] getThresar() { return this.thresar; }
    public double getElmaskar() { return this.elmaskar; }
    public double getElmaskhold() { return this.elmaskhold; }
    public double getThresslip() { return this.thresslip; }
    public double getThresdop() { return this.thresdop; }
    public double getMaxtdiff() { return this.maxtdiff; }
    public double[] getMaxinno() { return this.maxinno; }
    public double getMaxinnoPhase() { return this.maxinno[0]; }
    public double getMaxinnoCode() { return this.maxinno[1]; }
    public double getMaxgdop() { return this.maxgdop; }
    public double getVarholdamb() { return this.varholdamb; }
    public double getGainholdamb() { return this.gainholdamb; }
    public double[] getBaseline() { return this.baseline; }
    public double[] getRu() { return this.ru; }
    public double[] getRb() { return this.rb; }
    public char[][] getAnttype() { return this.anttype; }
    public double[][] getAntdel() { return this.antdel; }
    public pcv_t[] getPcvr() { return this.pcvr; }
    public byte[] getExsats() { return this.exsats; }
    public int getMaxaveep() { return this.maxaveep; }
    public int getInitrst() { return this.initrst; }
    public int getOutsingle() { return this.outsingle; }
    public int getSyncsol() { return this.syncsol; }
    public char[][] getRnxopt() { return this.rnxopt; }
    public int[] getPosopt() { return this.posopt; }
    public double[][] getOdisp() { return this.odisp; }
    public int getFreqopt() { return this.freqopt; }
    public char[] getPppopt() { return this.pppopt; }

    public prcopt_t() {
    }

    /* copy constructor */
    public prcopt_t(prcopt_t opt) {
        this.mode = opt.mode;
        this.soltype = opt.soltype;
        this.nf = opt.nf;
        this.navsys = opt.navsys;
        this.elmin = opt.elmin;
        this.snrmask = opt.snrmask;
        this.sateph = opt.sateph;
        this.modear = opt.modear;
        this.glomodear = opt.glomodear;
        this.gpsmodear = opt.gpsmodear;
        this.bdsmodear = opt.bdsmodear;
        this.arfilter = opt.arfilter;
        this.maxout = opt.maxout;
        this.minlock = opt.minlock;
        this.minfixsats = opt.minfixsats;
        this.minholdsats = opt.minholdsats;
        this.mindropsats = opt.mindropsats;
        this.minfix = opt.minfix;
        this.armaxiter = opt.armaxiter;
        this.ionoopt = opt.ionoopt;
        this.tropopt = opt.tropopt;
        this.varmodel = opt.varmodel;
        this.dynamics = opt.dynamics;
        this.tidecorr = opt.tidecorr;
        this.niter = opt.niter;
        this.codesmooth = opt.codesmooth;
        this.intpref = opt.intpref;
        this.sbascorr = opt.sbascorr;
        this.sbassatsel = opt.sbassatsel;
        this.rovpos = opt.rovpos;
        this.refpos = opt.refpos;
        this.eratio = opt.eratio;
        this.err = opt.err;
        this.std = opt.std;
        this.prn = opt.prn;
        this.sclkstab = opt.sclkstab;
        this.thresar = opt.thresar;
        this.elmaskar = opt.elmaskar;
        this.elmaskhold = opt.elmaskhold;
        this.thresslip = opt.thresslip;
        this.thresdop = opt.thresdop;
        this.maxtdiff = opt.maxtdiff;
        this.maxinno = opt.maxinno;
        this.maxgdop = opt.maxgdop;
        this.varholdamb = opt.varholdamb;
        this.gainholdamb = opt.gainholdamb;
        this.baseline = opt.baseline;
        this.ru = opt.ru;
        this.rb = opt.rb;
        this.anttype = opt.anttype;
        this.antdel = opt.antdel;
        this.pcvr = opt.pcvr;
        this.exsats = opt.exsats;
        this.maxaveep = opt.maxaveep;
        this.initrst = opt.initrst;
        this.outsingle = opt.outsingle;
        this.syncsol = opt.syncsol;
        this.rnxopt = opt.rnxopt;
        this.posopt = opt.posopt;
        this.odisp = opt.odisp;
        this.freqopt = opt.freqopt;
        this.pppopt = opt.pppopt;
    }
}

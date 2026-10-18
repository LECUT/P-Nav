/*
 * Decompiled with CFR 0.152.
 */
import com.rtklib.bean.filopt_t;
import com.rtklib.bean.gtime_t;
import com.rtklib.bean.nav_t;
import com.rtklib.bean.pcv_t;
import com.rtklib.bean.prcopt_t;
import com.rtklib.bean.snrmask_t;
import com.rtklib.bean.solopt_t;
import com.rtklib.postpos.postpos;
import java.io.IOException;

public class Main {
    public static final int PMODE_SINGLE = 0;
    public static final int SOLF_LLH = 0;
    public static final int TIMES_GPST = 0;

    public static void main(String[] args) throws IOException, CloneNotSupportedException {
        double D2R = Math.PI / 180;
        double tint = 0.0;
        prcopt_t prcopt_default = new prcopt_t();
        solopt_t solopt_default = new solopt_t(0, 0, 0, 3, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, new double[]{0.0, 0.0}, " ".toCharArray(), "".toCharArray());
        prcopt_default.mode = 0;
        prcopt_default.refpos = 1;
        prcopt_default.glomodear = 1;
        prcopt_default.setSateph(0);
        gtime_t ts = new gtime_t(0.0, 0.0);
        gtime_t te = new gtime_t(0.0, 0.0);
        String[] infile = new String[3];
        prcopt_t prcopt = prcopt_default;
        solopt_t solopt = solopt_default;
        filopt_t filopt = new filopt_t();
        prcopt.navsys = 1;
        prcopt.setModear(3);
        solopt.degf = 1;
        solopt.posf = 1;
        prcopt.ionoopt = 0;
        prcopt.tropopt = 0;
        prcopt.setTidecorr(0);
        prcopt.setElmin(0.17453292519943295);
        prcopt.setDynamics(0);
        prcopt.setNf(1);
        String outfile = "C:\\Users\\25838\\Desktop\\Mi8_PNav_DGPS_instant.pos";
        infile[0] = "C:\\Users\\25838\\Desktop\\240919-JSNU-2Phones\\xiaomi_8\\GEOP263F-G.24o";
        infile[2] = "C:\\Users\\25838\\Desktop\\240919-JSNU-2Phones\\Huace\\base\\raw\\1027307263F.24o";
        infile[1] = "C:\\Users\\25838\\Desktop\\test231\\hour3120.24n";
        int n = 0;
        for (String s : infile) {
            if (s == null) continue;
            ++n;
        }
        postpos.post_pos(ts, te, tint, 0.0, prcopt, solopt, filopt, infile, n, outfile, "", ' ');
    }
}


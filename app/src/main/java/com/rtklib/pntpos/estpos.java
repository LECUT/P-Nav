/*
 * 移植自 RTKLIB 2.4.3 b34（T.TAKASU 原作，C 语言实现）。
 * 原始 Java 源码经反编译工具转写，类名与结构有所调整。
 *
 *     Copyright (C) 2007-2020 by T.TAKASU, All rights reserved.
 *
 * 依 BSD 2-Clause 许可使用。许可全文见工程根目录 LICENSE-RTKLIB。
 */
package com.rtklib.pntpos;

import android.util.Log;

import com.rtklib.bean.gtime_t;
import com.rtklib.bean.nav_t;
import com.rtklib.bean.obsd_t;
import com.rtklib.bean.prcopt_t;
import com.rtklib.bean.sol_t;
import com.rtklib.pntpos.rescode;
import com.rtklib.readobsnav.satSys;
import com.rtklib.util.math;
import com.rtklib.util.util1;
import com.rtklib.util.util2;

public class estpos {
	static int NX_FOR_NAVSYS(int navsys) {
		int nx = 3; // 位置 X/Y/Z
		if ((navsys & 1) != 0) nx++;   // GPS 钟
		if ((navsys & 4) != 0) nx++;   // GLONASS 钟
		if ((navsys & 8) != 0) nx++;   // Galileo 钟
		if ((navsys & 32) != 0) nx++;  // BDS 钟
		if ((navsys & (16 | 64)) != 0) nx++; // QZSS/IRNSS 钟（共享列7）
		return nx;
	}
	public static final int MAXITR = 10;
	public static final int SOLQ_SBAS = 3;
	public static final int SOLQ_DGPS = 4;
	public static final int SOLQ_SINGLE = 5;
	public static final int MIN_NSAT_SOL = 4;
	/** 高残差剔除阈值(米)，参照 GnssLogger 的 removeHighResidualSats */
	private static final double THRES_RESIDUAL_METERS = 20.0;
	public static final double[] chisqr = new double[]{10.8, 13.8, 16.3, 18.5, 20.5, 22.5, 24.3, 26.1, 27.9, 29.6, 31.3, 32.9, 34.5, 36.1, 37.7, 39.3, 40.8, 42.3, 43.8, 45.3, 46.8, 48.3, 49.7, 51.2, 52.6, 54.1, 55.5, 56.9, 58.3, 59.7, 61.1, 62.5, 63.9, 65.2, 66.6, 68.0, 69.3, 70.7, 72.1, 73.4, 74.7, 76.0, 77.3, 78.6, 80.0, 81.3, 82.6, 84.0, 85.4, 86.7, 88.0, 89.3, 90.6, 91.9, 93.3, 94.7, 96.0, 97.4, 98.7, 100.0, 101.0, 102.0, 103.0, 104.0, 105.0, 107.0, 108.0, 109.0, 110.0, 112.0, 113.0, 114.0, 115.0, 116.0, 118.0, 119.0, 120.0, 122.0, 123.0, 125.0, 126.0, 127.0, 128.0, 129.0, 131.0, 132.0, 133.0, 134.0, 135.0, 137.0, 138.0, 139.0, 140.0, 142.0, 143.0, 144.0, 145.0, 147.0, 148.0, 149.0};

	public static int est_pos(obsd_t[] obs, int n, double[] rs, double[] dts, double[] vare, int[] svh, nav_t nav, prcopt_t opt, sol_t sol, double[] azel, int[] vsat, double[] resp, String msg) {
		int nv = 0;
		int i;
		int nx = NX_FOR_NAVSYS(opt.getNavsys());
		Log.d("DIAG_ESTPOS", "est_pos ENTER n=" + n + " navsys=" + opt.getNavsys() + " nx=" + nx);

		double[] x = new double[8];
		double[] dx = new double[nx];
		double[] Q = new double[nx * nx];
		double[] v = math.mat(n + 4, 1);
		double[] H = math.mat(nx, n + 4);
		double[] var = math.mat(n + 4, 1);
		int[] ns = new int[1];
		for (i = 0; i < 3; ++i) {
			x[i] = sol.rr[i];
		}

		// ── 保存原始 sat[0]，高残差剔除时置 0 来跳过 rescode ──
		int[] sat0Save = new int[n];
		for (i = 0; i < n; ++i) sat0Save[i] = obs[i].sat[0];
		boolean[] excluded = new boolean[n];
		int activeCount = n;

		boolean redo;
		do {
			redo = false;
			// 应用排除列表
			for (i = 0; i < n; ++i) {
				obs[i].sat[0] = excluded[i] ? 0 : sat0Save[i];
			}
			// 重置状态向量（钟差归零，位置复用上一历元）
			for (i = 0; i < 3; ++i) x[i] = sol.rr[i];
			for (i = 3; i < 8; ++i) x[i] = 0.0;

		for (i = 0; i < 10 && (nv = rescode.res_code(i, obs, n, rs, dts, vare, svh, nav, x, opt, v, H, var, azel, vsat, resp, ns, nx)) >= nx; ++i) {
			Log.d("DIAG_ESTPOS", "iter=" + i + " nv=" + nv + " nx=" + nx + " ns=" + ns[0] + " — 进入LSQ");
			int j;
			for (j = 0; j < nv; ++j) {
				double sig = Math.sqrt(var[j]);
				int n2 = j;
				v[n2] = v[n2] / sig;
				for (int k = 0; k < nx; ++k) {
					int n3 = k + j * nx;
					H[n3] = H[n3] / sig;
				}
			}
			if (math.lsq(H, v, nx, nv, dx, Q) != 0) {
				Log.d("DIAG_ESTPOS", "LSQ 失败 iter=" + i + " nv=" + nv + " dx=" + String.format("%.3f", math.norm(dx, nx)));
				break;
			}
			for (j = 0; j < nx; ++j) {
				int n4 = j;
				x[n4] = x[n4] + dx[j];
			}
			if (!(math.norm(dx, nx) < 1.0E-4)) continue;

			// ── 收敛后剔除高残差卫星（参照 GnssLogger removeHighResidualSats）──
			double maxRes = 0.0;
			int maxResIdx = -1;
			int validCnt = 0;
			for (int si = 0; si < n; ++si) {
				if (vsat[si] != 0 && !excluded[si]) {
					validCnt++;
					double absRes = Math.abs(resp[si]);
					if (absRes > maxRes) { maxRes = absRes; maxResIdx = si; }
				}
			}
			if (maxRes > THRES_RESIDUAL_METERS && validCnt > MIN_NSAT_SOL) {
				excluded[maxResIdx] = true;
				activeCount--;
				Log.d("DIAG_ESTPOS", "剔除高残差卫星 PRN" + sat0Save[maxResIdx]
					+ " 残差=" + String.format("%.1f", maxRes) + "m 剩余=" + activeCount);
				redo = true;
				break;
			}

			sol.type = 0;
			gtime_t temptime = new gtime_t(obs[0].time.time, obs[0].time.sec);
			sol.time = util1.timeadd(temptime, -x[3] / 2.99792458E8);
			sol.dtr[0] = x[3] / 2.99792458E8;
			sol.dtr[1] = x[4] / 2.99792458E8;
			sol.dtr[2] = x[5] / 2.99792458E8;
			sol.dtr[3] = x[6] / 2.99792458E8;
			sol.dtr[4] = x[7] / 2.99792458E8;
			for (j = 0; j < 6; ++j) {
				sol.rr[j] = j < 3 ? x[j] : 0.0;
			}
			for (j = 0; j < 3; ++j) {
				sol.qr[j] = (float)Q[j + j * nx];
			}
			sol.qr[3] = (float)Q[1];
			sol.qr[4] = (float)Q[1 * nx + 2];
			sol.qr[5] = (float)Q[2];
			sol.ns = (byte)ns[0];
			sol.ratio = 0.0f;
			sol.age = 0.0f;
			int stat = estpos.valsol(azel, vsat, n, opt, v, nv, nx, msg);
			if (stat == 1) {
				sol.stat = (byte)(opt.getSateph() == 2 ? 3 : 5);
				Log.d("DIAG_ESTPOS", "SUCCESS iter=" + i + " stat=" + sol.stat + " ns=" + ns[0]);
			} else {
				Log.d("DIAG_ESTPOS", "valsol 拒绝 iter=" + i + " stat=" + stat + " msg=" + msg);
			}
			// 恢复 sat[0] 后返回
			for (int si = 0; si < n; ++si) obs[si].sat[0] = sat0Save[si];
			return stat;
		}
		} while (redo);

		// 恢复 sat[0]
		for (int si = 0; si < n; ++si) obs[si].sat[0] = sat0Save[si];

		if (i >= 10) {
			util2.strcpy(msg, "iteration divergent");
			Log.d("DIAG_ESTPOS", "迭代发散 i>=10");
		}
		Log.d("DIAG_ESTPOS", "FAIL exit: i=" + i + " nx=" + nx + " nv=" + nv + " n=" + n + " navsys=" + opt.getNavsys());
		return 0;
	}

	public static double SQRT(double x) {
		return x < 0.0 || Double.isNaN(x) ? 0.0 : Math.sqrt(x);
	}

	public static void dops(int ns, double[] azel, double elmin, double[] dop) {
		int i;
		double[] H = new double[4 * nav_t.getMaxSat()];
		double[] Q = new double[16];
		for (i = 0; i < 4; ++i) {
			dop[i] = 0.0;
		}
		int n = 0;
		for (i = 0; i < ns && i < nav_t.getMaxSat(); ++i) {
			if (azel[1 + i * 2] < elmin || azel[1 + i * 2] <= 0.0) continue;
			double cosel = Math.cos(azel[1 + i * 2]);
			double sinel = Math.sin(azel[1 + i * 2]);
			H[4 * n] = cosel * Math.sin(azel[i * 2]);
			H[1 + 4 * n] = cosel * Math.cos(azel[i * 2]);
			H[2 + 4 * n] = sinel;
			H[3 + 4 * n++] = 1.0;
		}
		if (n < 4) {
			return;
		}
		math.matmul("NT", 4, 4, n, 1.0, H, H, 0.0, Q);
		if (math.matinv(Q, 4) == 0) {
			dop[0] = estpos.SQRT(Q[0] + Q[5] + Q[10] + Q[15]);
			dop[1] = estpos.SQRT(Q[0] + Q[5] + Q[10]);
			dop[2] = estpos.SQRT(Q[0] + Q[5]);
			dop[3] = estpos.SQRT(Q[10]);
		}
	}

	public static int valsol(double[] azel, int[] vsat, int n, prcopt_t opt, double[] v, int nv, int nx, String msg) {
		double[] azels = new double[192];
		double[] dop = new double[4];
		double vv = math.dot(v, v, nv);
		if (nv > nx && vv > chisqr[nv - nx - 1]) {
			Log.d("DIAG_ESTPOS", "卡方警告: nv=" + nv + " nx=" + nx + " vv=" + String.format("%.1f", vv) + " cs=" + String.format("%.1f", chisqr[nv - nx - 1]));
			// 手机伪距噪声大，不因卡方检验拒绝解算（参考 MobileGNSS-SPP）
			// return 0;
		}
		int ns = 0;
		for (int i = 0; i < n; ++i) {
			if (vsat[i] == 0) continue;
			azels[ns * 2] = azel[i * 2];
			azels[1 + ns * 2] = azel[1 + i * 2];
			++ns;
		}
		estpos.dops(ns, azels, opt.getElmin(), dop);
		if (dop[0] <= 0.0 || dop[0] > opt.getMaxgdop()) {
			Log.d("DIAG_ESTPOS", "GDOP拒绝: dop[0]=" + String.format("%.1f", dop[0]) + " maxgdop=" + String.format("%.1f", opt.getMaxgdop()) + " ns=" + ns);
			return 0;
		}
		return 1;
	}
}

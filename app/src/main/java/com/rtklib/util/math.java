/*
 * 移植自 RTKLIB 2.4.3 b34（T.TAKASU 原作，C 语言实现）。
 * 原始 Java 源码经反编译工具转写，类名与结构有所调整。
 *
 *     Copyright (C) 2007-2020 by T.TAKASU, All rights reserved.
 *
 * 依 BSD 2-Clause 许可使用。许可全文见工程根目录 LICENSE-RTKLIB。
 */
package com.rtklib.util;

import java.util.Arrays;

public class math {
    public static int lsq(double[] A, double[] y, int n, int m, double[] x, double[] Q) {
        double[] Ay = new double[n];
        if (m < n) {
            return -1;
        }
        math.matmul("NN", n, 1, m, 1.0, A, y, 0.0, Ay);
        math.matmul("NT", n, n, m, 1.0, A, A, 0.0, Q);
        int info = math.matinv(Q, n);
        if (info == 0) {
            math.matmul("NN", n, 1, n, 1.0, Q, Ay, 0.0, x);
        }
        return info;
    }

    public static void matcpy(double[] A, double[] B, int n, int m) {
        for (int i = 0; i < n; ++i) {
            if (m < 0) continue;
            System.arraycopy(B, i * m, A, i * m, m);
        }
    }

    public static void matmul(String tr, int n, int k, int m, double alpha, double[] A, double[] B, double beta, double[] C) {
        int f = tr.toCharArray()[0] == 'N' ? (tr.toCharArray()[1] == 'N' ? 1 : 2) : (tr.toCharArray()[1] == 'N' ? 3 : 4);
        for (int i = 0; i < n; ++i) {
            for (int j = 0; j < k; ++j) {
                double d = 0.0;
                switch (f) {
                    case 1: {
                        int x;
                        for (x = 0; x < m; ++x) {
                            d += A[i + x * n] * B[x + j * m];
                        }
                        break;
                    }
                    case 2: {
                        int x;
                        for (x = 0; x < m; ++x) {
                            d += A[i + x * n] * B[j + x * k];
                        }
                        break;
                    }
                    case 3: {
                        int x;
                        for (x = 0; x < m; ++x) {
                            d += A[x + i * m] * B[x + j * m];
                        }
                        break;
                    }
                    case 4: {
                        int x;
                        for (x = 0; x < m; ++x) {
                            d += A[x + i * m] * B[j + x * k];
                        }
                        break;
                    }
                }
                C[i + j * n] = beta == 0.0 ? alpha * d : alpha * d + beta * C[i + j * n];
            }
        }
    }

    public static int ludcmp(double[] A, int n, int[] indx, double[] d) {
        double tmp;
        int j;
        double big;
        int i;
        int imax = 0;
        double[] vv = math.mat(n, 1);
        d[0] = 1.0;
        for (i = 0; i < n; ++i) {
            big = 0.0;
            for (j = 0; j < n; ++j) {
                double d2;
                tmp = Math.abs(A[i + j * n]);
                if (!(tmp > big)) continue;
                big = tmp;
            }
            if (!(big > 0.0)) {
                return -1;
            }
            vv[i] = 1.0 / big;
        }
        for (j = 0; j < n; ++j) {
            int k;
            double s;
            for (i = 0; i < j; ++i) {
                s = A[i + j * n];
                for (k = 0; k < i; ++k) {
                    s -= A[i + k * n] * A[k + j * n];
                }
                A[i + j * n] = s;
            }
            big = 0.0;
            for (i = j; i < n; ++i) {
                double d3;
                s = A[i + j * n];
                for (k = 0; k < j; ++k) {
                    s -= A[i + k * n] * A[k + j * n];
                }
                A[i + j * n] = s;
                tmp = vv[i] * Math.abs(s);
                if (!(tmp >= big)) continue;
                big = tmp;
                imax = i;
            }
            if (j != imax) {
                for (k = 0; k < n; ++k) {
                    tmp = A[imax + k * n];
                    A[imax + k * n] = A[j + k * n];
                    A[j + k * n] = tmp;
                }
                d[0] = -d[0];
                vv[imax] = vv[j];
            }
            indx[j] = imax;
            if (A[j + j * n] == 0.0) {
                return -1;
            }
            if (j == n - 1) continue;
            tmp = 1.0 / A[j + j * n];
            for (i = j + 1; i < n; ++i) {
                int n2 = i + j * n;
                A[n2] = A[n2] * tmp;
            }
        }
        return 0;
    }

    public static void lubksb(double[] A, int n, int[] indx, double[] b) {
        int j;
        double s;
        int i;
        int ii = -1;
        for (i = 0; i < n; ++i) {
            int ip = indx[i];
            s = b[ip];
            b[ip] = b[i];
            if (ii >= 0) {
                for (j = ii; j < i; ++j) {
                    s -= A[i + j * n] * b[j];
                }
            } else if (s != 0.0) {
                ii = i;
            }
            b[i] = s;
        }
        for (i = n - 1; i >= 0; --i) {
            s = b[i];
            for (j = i + 1; j < n; ++j) {
                s -= A[i + j * n] * b[j];
            }
            b[i] = s / A[i + i * n];
        }
    }

    public static int matinv(double[] A, int n) {
        double[] d = new double[1];
        int[] indx = math.imat(n, 1);
        double[] B = math.mat(n, n);
        assert (B != null);
        System.arraycopy(A, 0, B, 0, n * n);
        if (math.ludcmp(B, n, indx, d) != 0) {
            return -1;
        }
        for (int j = 0; j < n; ++j) {
            for (int i = 0; i < n; ++i) {
                A[i + j * n] = 0.0;
            }
            A[j + j * n] = 1.0;
            double[] tempA = Arrays.copyOfRange(A, j * n, j * n + n + 1);
            math.lubksb(B, n, indx, tempA);
            for (int k = 0; k < n; ++k) {
                A[j * n + k] = tempA[k];
            }
        }
        return 0;
    }

    public static double dot(double[] a, double[] b, int n) {
        double c = 0.0;
        while (--n >= 0) {
            c += a[n] * b[n];
        }
        return c;
    }

    public static double norm(double[] a, int n) {
        return Math.sqrt(math.dot(a, a, n));
    }

    public static double[] eye(int n) {
        if (n <= 0) {
            return null;
        }
        double[] p = math.zeros(n, n);
        if (p != null) {
            for (int i = 0; i < n; ++i) {
                p[i + i * n] = 1.0;
            }
        }
        return p;
    }

    public static double[] mat(int n, int m) {
        if (n <= 0 || m <= 0) {
            throw new IllegalArgumentException("Invalid matrix dimensions: n=" + n + ", m=" + m);
        }
        double[] p = new double[n * m];
        return p;
    }

    public static double[] zeros(int n, int m) {
        double[] p = math.mat(n, m);
        return p;
    }

    public static int[] imat(int n, int m) {
        if (n <= 0 || m <= 0) {
            return null;
        }
        int[] p = new int[n * m];
        return p;
    }

    public static void Rx(double t, double[] X) {
        X[0] = 1.0;
        X[6] = 0.0;
        X[3] = 0.0;
        X[2] = 0.0;
        X[1] = 0.0;
        X[4] = X[8] = Math.cos(t);
        X[7] = Math.sin(t);
        X[5] = -X[7];
    }

    public static void Ry(double t, double[] X) {
        X[4] = 1.0;
        X[7] = 0.0;
        X[5] = 0.0;
        X[3] = 0.0;
        X[1] = 0.0;
        X[0] = X[8] = Math.cos(t);
        X[2] = Math.sin(t);
        X[6] = -X[2];
    }

    public static void Rz(double t, double[] X) {
        X[8] = 1.0;
        X[7] = 0.0;
        X[6] = 0.0;
        X[5] = 0.0;
        X[2] = 0.0;
        X[0] = X[4] = Math.cos(t);
        X[3] = Math.sin(t);
        X[1] = -X[3];
    }
}


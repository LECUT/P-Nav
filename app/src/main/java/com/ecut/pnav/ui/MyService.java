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
package com.ecut.pnav.ui;

import static com.ecut.pnav.ui.Ntrip.NtripFragment.initrtcm;
import static com.ecut.pnav.ui.Ntrip.NtripFragment.initrtcm2;
import static com.ecut.pnav.ui.Ntrip.NtripFragment.inputrtcm2;
import static com.ecut.pnav.ui.Ntrip.NtripFragment.inputrtcm3;
import static com.ecut.pnav.ui.Ntrip.NtripViewModel.gettime;
import static com.rtklib.bean.nav_t.getMaxSat;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.ecut.pnav.R;
import com.ecut.pnav.ui.Ntrip.NtripClient;
import com.ecut.pnav.ui.Ntrip.OnRtcmDataReceivedListener;
import com.ecut.pnav.ui.Ntrip.rtcm_t;
import com.ecut.pnav.util.SessionLogger;
import com.rtklib.bean.gtime_t;
import com.rtklib.bean.obs_t;
import com.rtklib.bean.obsd_t;
import com.rtklib.bean.sta_t;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Serializable;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Base64;
import java.util.List;

public class MyService extends Service implements OnRtcmDataReceivedListener {

    private static final String CHANNEL_ID = "my_channel_id";
    private static final int NOTIFICATION_ID = 1;
    /** 静态标记，供其他组件查询 CORS 服务是否在运行，替代弃用的 getRunningServices() */
    public static volatile boolean isServiceRunning = false;
    private Thread getrtcmThread;
    private boolean isRunning = true; // 控制线程运行
    private List<NtripClient> mData; // 存储来自 ViewModel 的数据
    private double[] gga_message;
    public static rtcm_t rtcm = new rtcm_t();
    public static rtcm_t rtcm2 = new rtcm_t();  // RTCM2 解码器实例
    static gtime_t[] obstime = new gtime_t[getMaxSat()];
    public static obsd_t[] obssd = new obsd_t[getMaxSat()];
    public static double[] stapos = new double[3];
    static int no = 0;
    static OnRtcmDataReceivedListener dataReceivedListener;
    public static StringBuilder rtcmDiag = new StringBuilder();
    // RTCM 诊断计数器，供 UI 组件读取
    public static long rtcmTotalBytes = 0;
    public static int rtcmCountObs = 0;
    public static int rtcmCountSta = 0;
    public static int rtcmCountOther = 0;
    public static int rtcmLastSatCount = 0;
    /** 重连计数器，UI 可观察 */
    public static int rtcmReconnectCount = 0;
    /** 最后一次成功接收数据的时间戳 */
    public static long rtcmLastDataTime = 0;
    private Handler handler;
    private Runnable runnable;
    @Override
    public void onCreate() {
        super.onCreate();
        isServiceRunning = true;
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "My Channel",
                NotificationManager.IMPORTANCE_DEFAULT
        );
        channel.setDescription("This is my notification channel");

        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        notificationManager.createNotificationChannel(channel);

        // 前台服务必须在 5 秒内调用 startForeground，否则系统崩溃
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("P-NAV NTRIP")
                .setContentText("正在接收CORS差分数据…")
                .setSmallIcon(android.R.drawable.ic_menu_compass)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
        startForeground(NOTIFICATION_ID, notification);
        SessionLogger.cors("Service onCreate — startForeground OK");
        handler = new Handler(Looper.getMainLooper());

        runnable = new Runnable() {
            @Override
            public void run() {
                // 仅在 mData 有效时启动 RTCM 线程
                if (mData != null && !mData.isEmpty()) {
                    startRtcmThread();
                }
                handler.postDelayed(this, 2 * 60 * 1000); // 每两分钟兜底检查
            }
        };
        handler.post(runnable); // 启动第一次执行
    }
    public MyService() {
        initrtcm();
        initrtcm2();
        rtcm.setSta(new sta_t());
        rtcm2.setSta(new sta_t());
        for(int i=0;i<getMaxSat();i++){
            obssd[i] = new obsd_t();
            obstime[i] = new gtime_t(0,0);
        }
    }
    @SuppressLint("ForegroundServiceType")
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // START_STICKY 时系统重启服务 intent 可能为 null
        if (intent == null) return START_STICKY;
        // 从 Intent 中获取传递过来的数据
        mData = (List<NtripClient>) intent.getSerializableExtra("NtripDataList");
        gga_message = intent.getDoubleArrayExtra("GgaMessage");
        // 确保 mData 不为空
        if (mData != null && !mData.isEmpty()) {
            SessionLogger.cors("onStartCommand: 收到 " + mData.size() + " 个挂载点，启动RTCM线程");
            startRtcmThread(); // 启动线程
        } else {
            stopSelf(); // 数据为空，停止服务
        }
        return START_STICKY;
    }
    private void startRtcmThread() {
        // 避免重复启动线程
        if (getrtcmThread != null && getrtcmThread.isAlive()) {
            Log.d("RTCM_SVC", "RTCM 线程已在运行，跳过重复启动");
            return;
        }
        isRunning = true;
        getrtcmThread = new Thread(() -> {
            int reconnectDelay = 1;
            final int MAX_RECONNECT_DELAY = 30;
            boolean authFailed = false;

            while (isRunning && !authFailed) {
                Socket socket = null;
                BufferedInputStream bis = null;
                OutputStream os = null;
                try {
                    socket = new Socket(mData.get(0).getHost(), mData.get(0).getPort());
                    bis = new BufferedInputStream(socket.getInputStream());
                    os = socket.getOutputStream();

                    String auth = mData.get(0).getNtrip_User() + ":" + mData.get(0).getNtrip_password();
                    String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes());
                    String request = "GET /" + mData.get(0).getMountPoint() + " HTTP/1.0\r\n";
                    request += "User-Agent: NTRIP GNSSInternetRadio/1.4.10\r\n";
                    request += "Authorization: Basic " + encodedAuth + "\r\n";
                    request += "Accept: */*\r\n";
                    request += "Connection: close\r\n";
                    request += "\r\n";
                    os.write(request.getBytes());
                    os.flush();

                    // 统一读至 \r\n\r\n 结束响应头
                    socket.setSoTimeout(3000);
                    StringBuilder headerBuf = new StringBuilder();
                    int prev3 = 0;
                    boolean isIcy = false;
                    int b;
                    try {
                        while ((b = bis.read()) != -1 && isRunning) {
                            headerBuf.append((char) b);
                            prev3 = ((prev3 << 8) | b) & 0xFFFFFFFF;
                            String buf = headerBuf.toString();
                            if (!isIcy && buf.startsWith("ICY")) {
                                isIcy = true;
                                Log.d("RTCM_SVC", "检测到 ICY 协议响应");
                            }
                            if (prev3 == 0x0D0A0D0A) break;
                        }
                    } catch (java.net.SocketTimeoutException e) {
                        Log.d("RTCM_SVC", "响应头读取超时 (纯ICY服务器, 正常) headerLen=" + headerBuf.length());
                    }

                    String headers = headerBuf.toString();
                    Log.d("RTCM_SVC", "HTTP headers received: " + headers.length() + " bytes");
                    rtcmDiag.setLength(0);
                    rtcmDiag.append("[RTCM] HTTP 响应 ").append(headers.length()).append(" bytes");

                    if (headers.contains("200 OK")) {
                        // 连接成功，重置退避延迟
                        reconnectDelay = 1;
                        SessionLogger.cors("200 OK — 连接成功，挂载点=" + (mData != null && !mData.isEmpty() ? mData.get(0).getMountPoint() : "?") + " 坐标=" + String.format("%.4f,%.4f", (gga_message != null && gga_message.length > 0 ? gga_message[0] : 0), (gga_message != null && gga_message.length > 1 ? gga_message[1] : 0)));
                        System.out.println("Connected to mount point and ready to receive RTCM data.");
                        Log.d("RTCM_SVC", "CORS 200 OK, starting to receive RTCM stream...");
                        rtcmDiag.append("\n[RTCM] CORS 200 OK, 开始接收数据流...");
                        sendRtcmDiag(rtcmDiag.toString());
                        broadcastConnStatus("connected");
                        Intent intent = new Intent("com.ecut.pnav.SHOW_TOAST");
                        intent.putExtra("toastMessage", "Successfully connected to the CORS");
                        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
                        // 构造 GGA 上行
                        double decLat = (gga_message != null && gga_message.length > 0) ? gga_message[0] : 0;
                        double decLon = (gga_message != null && gga_message.length > 1) ? gga_message[1] : 0;
                        if (decLat == 0 && decLon == 0) {
                            decLat = com.ecut.pnav.ui.status.HomeViewModel.latestGnssLatLon[0];
                            decLon = com.ecut.pnav.ui.status.HomeViewModel.latestGnssLatLon[1];
                            Log.d("RTCM_SVC", "GGA坐标未设置，回退到手机定位: " + decLat + "," + decLon);
                        }
                        String latNmea = decimalToNmeaPos(decLat, true);
                        String lonNmea = decimalToNmeaPos(decLon, false);
                        String ns = decLat >= 0 ? "N" : "S";
                        String ew = decLon >= 0 ? "E" : "W";
                        String ggaBody = "GPGGA," + gettime() + "," + latNmea + "," + ns + "," + lonNmea
                                + "," + ew + ",2,10,5.00,10.0000,M,-2.860,M,08,0000";
                        String ggaData = "$" + ggaBody + "*" + nmeaChecksum(ggaBody) + "\r\n";
                        Log.d("RTCM_SVC", "发送GGA: " + ggaData.trim());
                        os.write(ggaData.getBytes());
                        os.flush();
                        Log.d("RTCM_SVC", "GGA已发送，设置5秒读超时，等待RTCM数据...");
                        socket.setSoTimeout(5000);
                        long lastGgaTime = System.currentTimeMillis();

                        byte[] buffer = new byte[4096];
                        int bytesRead;
                        long totalBytes = 0;
                        int countResult1 = 0, countResult5 = 0, countResultOther = 0;
                        int lastObsSatCount = 0;
                        int emptyDecodeCount = 0;
                        int readCount = 0;
                        while (isRunning) {
                            try {
                                bytesRead = bis.read(buffer);
                                if (bytesRead == -1) break;
                            } catch (java.net.SocketTimeoutException e) {
                                long now = System.currentTimeMillis();
                                if (now - lastGgaTime > 8000) {
                                    decLat = (gga_message != null && gga_message.length > 0) ? gga_message[0] : 0;
                                    decLon = (gga_message != null && gga_message.length > 1) ? gga_message[1] : 0;
                                    latNmea = decimalToNmeaPos(decLat, true);
                                    lonNmea = decimalToNmeaPos(decLon, false);
                                    ns = decLat >= 0 ? "N" : "S";
                                    ew = decLon >= 0 ? "E" : "W";
                                    ggaBody = "GPGGA," + gettime() + "," + latNmea + "," + ns + "," + lonNmea
                                            + "," + ew + ",2,10,5.00,10.0000,M,-2.860,M,08,0000";
                                    ggaData = "$" + ggaBody + "*" + nmeaChecksum(ggaBody) + "\r\n";
                                    try { os.write(ggaData.getBytes()); os.flush(); } catch (IOException ignored) {}
                                    Log.d("RTCM_SVC", "重发GGA(超时): " + ggaData.trim() + " totalBytes=" + totalBytes);
                                    rtcmDiag.append("\n[RTCM] 重发GGA totalBytes=").append(totalBytes);
                                    sendRtcmDiag(rtcmDiag.toString());
                                    lastGgaTime = now;
                                }
                                continue;
                            }
                            readCount++;
                            totalBytes += bytesRead;
                            rtcmTotalBytes = totalBytes;
                            rtcmLastDataTime = System.currentTimeMillis();
                            if (readCount <= 3) {
                                StringBuilder hex = new StringBuilder();
                                for (int j = 0; j < Math.min(bytesRead, 30); j++) {
                                    hex.append(String.format("%02X ", buffer[j] & 0xFF));
                                }
                                Log.d("RTCM_SVC", "第" + readCount + "次读取: " + bytesRead + "字节 前30:" + hex.toString());
                            }
                            int decodedThisRound = 0;
                            for (int i = 0; i < bytesRead; i++) {
                                int result = inputrtcm3(rtcm, buffer[i], obssd, obstime, stapos);
                                if (result == 0) {
                                    result = inputrtcm2(rtcm2, buffer[i], obssd, obstime, stapos);
                                }
                                if (result > 0) {
                                    decodedThisRound++;
                                    emptyDecodeCount = 0;
                                    // 每次成功解码重置退避（连接健康）
                                    reconnectDelay = 1;
                                    if (countResult1 == 0 && countResult5 == 0 && countResultOther == 0) {
                                        SessionLogger.rtcm("首次解码: type=" + result + " totalBytes=" + totalBytes);
                                        Log.d("RTCM_SVC", "First RTCM decoded: type=" + result + " totalBytes=" + totalBytes);
                                        rtcmDiag.append("\n[RTCM] 首次解码: type=").append(result)
                                                .append(" (1=obs,5=sta,其他=MSM/eph) totalBytes=").append(totalBytes);
                                        sendRtcmDiag(rtcmDiag.toString());
                                    }
                                    if (result == 1) countResult1++;
                                    else if (result == 5) countResult5++;
                                    else countResultOther++;
                                    rtcmCountObs = countResult1;
                                    rtcmCountSta = countResult5;
                                    rtcmCountOther = countResultOther;
                                    if (stapos[0] != 0) {
                                        rtcm.getSta().setPos(stapos);
                                    }
                                    if (result == 1) {
                                        no = 0;
                                        for (int k = 0; k < obstime.length; k++) {
                                            if (obssd[k].sat[0] != 0) no++;
                                        }
                                        lastObsSatCount = no;
                                        rtcmLastSatCount = no;
                                        for (int k = 0; k < obstime.length; k++) {
                                            if (obstime[k].time != 0) {
                                                obssd[k].time.setTime(obstime[k].time);
                                                obssd[k].time.setSec(obstime[k].sec);
                                            }
                                        }
                                        obs_t tempobs = new obs_t();
                                        tempobs.setN(no);
                                        tempobs.data = obssd.clone();
                                        rtcm.setObs(tempobs);
                                        if (countResult1 % 10 == 1) {
                                            SessionLogger.rtcm("obs#" + countResult1 + ": sats=" + no + " stapos=[" + String.format("%.0f", stapos[0]) + "," + String.format("%.0f", stapos[1]) + "," + String.format("%.0f", stapos[2]) + "] bytes=" + totalBytes);
                                            Log.d("RTCM_SVC", "RTCM obs #" + countResult1 + ": " + no + " sats, stapos=[" + stapos[0] + "," + stapos[1] + "," + stapos[2] + "], totalBytes=" + totalBytes + ", type5=" + countResult5 + ", other=" + countResultOther);
                                            rtcmDiag.append("\n[RTCM] obs#").append(countResult1)
                                                    .append(": 卫星数=").append(no)
                                                    .append(" 基准站坐标=[").append(String.format("%.0f", stapos[0]))
                                                    .append(",").append(String.format("%.0f", stapos[1]))
                                                    .append(",").append(String.format("%.0f", stapos[2])).append("]")
                                                    .append(" 累计: type1=").append(countResult1)
                                                    .append(" type5=").append(countResult5)
                                                    .append(" other=").append(countResultOther);
                                            sendRtcmDiag(rtcmDiag.toString());
                                        }
                                        Intent broadcastIntent = new Intent("com.ecut.pnav.RTCM_DATA_RECEIVED");
                                        broadcastIntent.putExtra("rtcmData", rtcm);
                                        LocalBroadcastManager.getInstance(this).sendBroadcast(broadcastIntent);
                                    }
                                }
                            }
                            if (decodedThisRound == 0 && totalBytes > 100) {
                                emptyDecodeCount++;
                                if (emptyDecodeCount == 1 || emptyDecodeCount % 100 == 0) {
                                    Log.d("RTCM_SVC", "已读 " + totalBytes + " B, 连续" + emptyDecodeCount + "次未解码");
                                }
                            }
                        }
                        // 读取循环退出（bytesRead==-1 或 !isRunning）→ 连接断开
                        SessionLogger.cors("连接断开 统计: obs=" + countResult1 + " sta=" + countResult5 + " other=" + countResultOther + " bytes=" + totalBytes);
                        rtcmDiag.append("\n[RTCM] 连接断开. 最终统计: type1=").append(countResult1)
                                .append(" type5=").append(countResult5)
                                .append(" other=").append(countResultOther)
                                .append(" totalBytes=").append(totalBytes);
                        sendRtcmDiag(rtcmDiag.toString());
                        broadcastConnStatus("disconnected");

                    } else if (headers.contains("ERROR") || headers.contains("401") || headers.contains("403")) {
                        // 认证失败 → 不重连
                        authFailed = true;
                        SessionLogger.err("CORS", "连接失败: " + headers.replace("\r\n", " | "));
                        System.err.println("Error connecting to mount point: " + headers.replace("\r\n", " | "));
                        isRunning = false;
                        rtcmDiag.append("\n[RTCM] 认证失败: ").append(headers.replace("\r\n", " | "));
                        sendRtcmDiag(rtcmDiag.toString());
                        broadcastConnStatus("auth_failed");
                        Intent intent = new Intent("com.ecut.pnav.SHOW_TOAST");
                        intent.putExtra("toastMessage", "Incorrect password, failed to connect to the CORS");
                        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
                        Intent broadcastIntent = new Intent("com.ecut.pnav.SWITCH_STATE");
                        broadcastIntent.putExtra("isswitchsate", false);
                        LocalBroadcastManager.getInstance(this).sendBroadcast(broadcastIntent);
                    } else {
                        // 非预期响应 → 重试
                        SessionLogger.err("CORS", "异常响应: " + headers.replace("\r\n", " | "));
                        Log.w("RTCM_SVC", "Unexpected CORS response: " + headers.replace("\r\n", " | "));
                        rtcmDiag.append("\n[RTCM] 异常响应: ").append(headers.replace("\r\n", " | "));
                        sendRtcmDiag(rtcmDiag.toString());
                        broadcastConnStatus("unexpected");
                    }
                } catch (Exception e) {
                    SessionLogger.err("RTCM_SVC", "线程异常: " + e.getMessage());
                    Log.e("RTCM_SVC", "线程异常: " + e.getMessage(), e);
                    rtcmDiag.append("\n[RTCM] 线程异常: ").append(e.getMessage());
                    sendRtcmDiag(rtcmDiag.toString());
                    broadcastConnStatus("error");
                } finally {
                    try { if (bis != null) bis.close(); } catch (IOException ignored) {}
                    try { if (os != null) os.close(); } catch (IOException ignored) {}
                    try { if (socket != null) socket.close(); } catch (IOException ignored) {}
                }

                // 认证失败不重连
                if (authFailed || !isRunning) break;

                // 指数退避重连
                if (isRunning) {
                    rtcmReconnectCount++;
                    Log.d("RTCM_SVC", "等待 " + reconnectDelay + "s 后重连... (第" + rtcmReconnectCount + "次重连)");
                    rtcmDiag.append("\n[RTCM] 等待 ").append(reconnectDelay).append("s 后重连... (第").append(rtcmReconnectCount).append("次)");
                    sendRtcmDiag(rtcmDiag.toString());
                    broadcastConnStatus("reconnecting");
                    try { Thread.sleep(reconnectDelay * 1000L); } catch (InterruptedException ignored) { break; }
                    reconnectDelay = Math.min(reconnectDelay * 2, MAX_RECONNECT_DELAY);
                }
            }
            getrtcmThread = null; // 允许 Handler 兜底重启
        });
        getrtcmThread.start();
    }

    /** 广播连接状态变更，供 UI 层监听 */
    private void broadcastConnStatus(String status) {
        Intent intent = new Intent("com.ecut.pnav.RTCM_CONN_STATUS");
        intent.putExtra("status", status);
        intent.putExtra("reconnectCount", rtcmReconnectCount);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }
    @Override
    public void onDestroy() {
        super.onDestroy();
        SessionLogger.cors("Service onDestroy — 线程终止, 最终 RTCM 统计: obs=" + rtcmCountObs + " sta=" + rtcmCountSta + " other=" + rtcmCountOther + " bytes=" + rtcmTotalBytes);
        isServiceRunning = false;
        isRunning = false;
        rtcmReconnectCount = 0;
        rtcmLastDataTime = 0;
        if (handler != null) {
            handler.removeCallbacks(runnable);
        }
        if (getrtcmThread != null && getrtcmThread.isAlive()) {
            getrtcmThread.interrupt();
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        // 此服务通过 startService() 启动，不绑定
        return null;
    }

    @Override
    public void onRtcmDataReceived(rtcm_t rtcmData) {
        if (dataReceivedListener != null) {
            dataReceivedListener.onRtcmDataReceived(rtcmData);
        }
    }
    public void setOnRtcmDataReceivedListener(OnRtcmDataReceivedListener listener) {
        dataReceivedListener = listener;
    }
    /** 计算 NMEA 校验和（$ 和 * 之间字节的 XOR 值，大写十六进制） */
    private String nmeaChecksum(String sentence) {
        int checksum = 0;
        for (int i = 0; i < sentence.length(); i++) {
            checksum ^= sentence.charAt(i);
        }
        return String.format("%02X", checksum & 0xFF);
    }
    /** 十进制度 → NMEA DDMM.MMMM 格式（参考 Lefebure GenerateGGAFromLatLon） */
    private static String decimalToNmeaPos(double deg, boolean isLat) {
        double abs = Math.abs(deg);
        int d = (int) abs;
        double m = (abs - d) * 60.0;
        if (isLat) {
            return String.format(java.util.Locale.US, "%02d%07.4f", d, m);
        } else {
            return String.format(java.util.Locale.US, "%03d%07.4f", d, m);
        }
    }
    private void sendRtcmDiag(String msg) {
        Intent diagIntent = new Intent("com.ecut.pnav.RTCM_DIAG");
        diagIntent.putExtra("diagMessage", msg);
        LocalBroadcastManager.getInstance(this).sendBroadcast(diagIntent);
    }
}
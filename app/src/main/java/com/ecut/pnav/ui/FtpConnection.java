package com.ecut.pnav.ui;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class FtpConnection {

    // FTP server list: {host, port, dataBasePath, productsBasePath}
    private static final String[][] FTP_SERVERS = {
        {"igs.gnsswhu.cn", "21", "/pub/gps", "/pub/gnss"},
        {"igs.ensg.ign.fr", "21", "/pub/igs", "/pub/igs"},
        {"isdcftp.gfz-potsdam.de", "21", "/gnss", "/pub/GNSS"},
    };

    // Primary server info (same as current, but now as backup too)
    // Actually the first one is primary
    // Disable for now
    // {"cddis.gsfc.nasa.gov", "21", "/pub/gps", "/pub/gps"},  // FTP dead since 2020

    private static int connectedServerIndex = -1;
    private static final int port = 21;
    private static final String TAG = "FtpHelper";
    private static final int TIMEOUT = 10000;
    private static final String ENCODING = "UTF-8";

    // ★ 下载进度回调接口（文件名 + 大小提示）
    public interface DownloadProgressListener {
        void onProgress(String fileName, long sizeBytes);
    }
    private static DownloadProgressListener progressListener;
    public static void setProgressListener(DownloadProgressListener l) { progressListener = l; }
    private static void notifyProgress(String name, long size) {
        if (progressListener != null) progressListener.onProgress(name, size);
    }

    private static int getgpstweek(){
        Calendar gpsStart = Calendar.getInstance();
        gpsStart.set(1980, Calendar.JANUARY, 6);
        Calendar now = Calendar.getInstance(TimeZone.getDefault());
        long daysBetween = now.getTimeInMillis() - gpsStart.getTimeInMillis();
        return (int) (daysBetween / (7 * 24 * 60 * 60 * 1000L));
    }

    public static String getSelectedDataBasePath() {
        if (connectedServerIndex < 0) return "/pub/gps";
        return FTP_SERVERS[connectedServerIndex][2];
    }

    public static String getSelectedProductsBasePath() {
        if (connectedServerIndex < 0) return "/pub/gnss";
        return FTP_SERVERS[connectedServerIndex][3];
    }

    public static String getSelectedServerName() {
        if (connectedServerIndex < 0) return FTP_SERVERS[0][0];
        return FTP_SERVERS[connectedServerIndex][0];
    }

    public static FTPClient getFTPClient() throws IOException {
        IOException lastException = null;
        for (int i = 0; i < FTP_SERVERS.length; i++) {
            FTPClient client = new FTPClient();
            client.setConnectTimeout(TIMEOUT);
            client.setDataTimeout(TIMEOUT);
            client.setDefaultTimeout(TIMEOUT);
            client.setControlEncoding(ENCODING);
            try {
                Log.d(TAG, "Trying FTP server: " + FTP_SERVERS[i][0]);
                client.connect(FTP_SERVERS[i][0], port);
                Log.d(TAG, "Connected to " + FTP_SERVERS[i][0] + " on " + port);

                if (!client.login("anonymous", "")) {
                    Log.d(TAG, "Could not login to " + FTP_SERVERS[i][0]);
                    client.disconnect();
                    continue;
                }
                Log.d(TAG, "Logged in to " + FTP_SERVERS[i][0]);

                client.enterLocalPassiveMode();
                client.setFileType(FTP.BINARY_FILE_TYPE);
                connectedServerIndex = i;
                return client;
            } catch (IOException e) {
                Log.w(TAG, "Failed to connect to " + FTP_SERVERS[i][0] + ": " + e.getMessage());
                lastException = e;
                try { client.disconnect(); } catch (Exception ignored) {}
            }
        }
        throw new IOException("All " + FTP_SERVERS.length + " FTP servers failed. Last error: " +
                (lastException != null ? lastException.getMessage() : "unknown"));
    }

    // 在指定BRDC目录中搜索星历文件，填充到candidates列表
    private static final String[] NAV_EXTS = {"rnx.gz", "n.gz", "g.gz", "l.gz", "c.gz"};

    private static boolean isNavFile(String name, int doy) {
        // RINEX 3 多系统文件: BRDM00DLR_S_YYYYDDDHHHH_01D_MN.rnx.gz
        if (name.contains("BRDM") && name.endsWith("_MN.rnx.gz")) return true;
        // RINEX 2 单系统文件: hour{DOY}*.{ext}.gz
        if (!name.contains("hour" + doy)) return false;
        for (String ext : NAV_EXTS) {
            if (name.contains(ext)) return true;
        }
        return false;
    }

    private static void searchBrdcInDir(FTPClient ftpClient, String dirPath, int doy, int startHour,
                                         List<FTPFile> outFiles, List<String> outDirs) throws IOException {
        FTPFile[] dayEntries = ftpClient.listFiles(dirPath);

        // Pass 1: 从startHour往回搜6小时，收集所有候选（不提前break，最新小时可能只有GPS）
        for (int h = startHour; h >= Math.max(0, startHour - 6); h--) {
            String hourDir = dirPath + "/" + String.format("%02d", h);
            try {
                FTPFile[] hourFiles = ftpClient.listFiles(hourDir);
                for (FTPFile sf : hourFiles) {
                    if (sf.isFile() && isNavFile(sf.getName(), doy)) {
                        outFiles.add(sf);
                        outDirs.add(hourDir);
                    }
                }
            } catch (IOException ignored) {}
        }

        // Pass 2: 小时目录无结果，在日目录直接匹配
        if (outFiles.isEmpty()) {
            for (FTPFile entry : dayEntries) {
                if (entry.isFile() && (entry.getName().endsWith("rnx.gz") || entry.getName().endsWith("n.gz"))
                        && (entry.getName().contains("hour" + doy) || entry.getName().contains("BRDM"))) {
                    outFiles.add(entry);
                    outDirs.add(dirPath);
                }
            }
        }

        // Pass 3: 遍历所有子目录作为回退
        if (outFiles.isEmpty()) {
            for (FTPFile entry : dayEntries) {
                if (entry.isDirectory()) {
                    String subDir = dirPath + "/" + entry.getName();
                    try {
                        FTPFile[] subFiles = ftpClient.listFiles(subDir);
                        for (FTPFile sf : subFiles) {
                            if (sf.isFile() && (sf.getName().endsWith("rnx.gz") || sf.getName().endsWith("n.gz"))) {
                                outFiles.add(sf);
                                outDirs.add(subDir);
                            }
                        }
                    } catch (IOException e) {
                        Log.w(TAG, "searchBrdcInDir子目录失败: " + subDir + " err=" + e.getMessage());
                    }
                }
            }
        }
    }

    // 下载FTP服务器上的最新的.gz文件到本地目录
    //option == 1:下载广播星历
    //option == 2:下载精密星历
    //option == other:两者都下载
    public static void downloadFile(Context context, FTPClient ftpClient, String[] remoteDirPath,
                                    String[] infile, int option) throws IOException {
        // 每次调用动态计算UTC时间，避免静态字段在类加载时固化导致星历过期
        ZonedDateTime nowUtc = ZonedDateTime.now(ZoneOffset.UTC);
        int currentYear = nowUtc.getYear();
        int dayOfYear = nowUtc.getDayOfYear();
        int dayOfYear_brdc = nowUtc.getDayOfYear();
        int year2d = currentYear % 100;
        // SP3/CLK产品每6小时发布(00/06/12/18 UTC)，匹配最近两个可用块
        int blockHour = (nowUtc.getHour() / 6) * 6;
        String strCurrentUtcHour = String.format("%02d", blockHour);
        String strCurrentUtcHour2 = String.format("%02d", (blockHour + 18) % 24);

        FTPFile brdclatestFile = null;
        FTPFile preclatestFile = null;
        FTPFile clklatestFile = null;
        File externalStorageDir = context.getExternalFilesDir(null);
        File myDir = new File(externalStorageDir, "myFolder");

        if (!myDir.exists()) {
            myDir.mkdir(); // 创建文件夹
        }
        if(option == 1)
        {
            //只获取广播星历文件
            // ★ 策略：优先RINEX3多系统文件(.rnx.gz)，回退RINEX2(.n.gz)
            String foundDir = null;
            int yesterdayDoy = nowUtc.minusDays(1).getDayOfYear();
            int yesterdayYear = nowUtc.minusDays(1).getYear();
            String doyPad = String.format("%03d", dayOfYear_brdc);

            // ★ 优先搜索 RINEX 3 多系统文件 (BRDM00DLR_S_YYYYDDD0000_01D_MN.rnx.gz)
            String rnx3Path = remoteDirPath.length > 4 ? remoteDirPath[4] : null;
            if (rnx3Path != null) {
                try {
                    FTPFile[] rnx3Files = ftpClient.listFiles(rnx3Path);
                    String rnx3Pattern = "BRDM00DLR_S_" + currentYear + doyPad + "0000_01D_MN.rnx.gz";
                    Log.d(TAG, "RINEX3搜索: " + rnx3Path + " pattern=" + rnx3Pattern);
                    for (FTPFile df : rnx3Files) {
                        if (df.isFile() && df.getName().equals(rnx3Pattern)) {
                            notifyProgress(df.getName(), df.getSize());
                            String localPath = myDir.getPath() + "/" + df.getName();
                            try (OutputStream os = Files.newOutputStream(Paths.get(localPath))) {
                                if (ftpClient.retrieveFile(rnx3Path + "/" + df.getName(), os)) {
                                    infile[0] = localPath;
                                    foundDir = myDir.getPath();
                                    Log.d(TAG, "RINEX3下载成功: " + df.getName());
                                }
                            }
                            break;
                        }
                    }
                } catch (IOException e) {
                    Log.w(TAG, "RINEX3搜索失败: " + rnx3Path + " err=" + e.getMessage());
                }
            }

            // ★ 回退 RINEX 2 日目录搜索
            if (foundDir == null) {
            String[][] dailyDirs = {
                {remoteDirPath[2], doyPad, String.valueOf(currentYear)}
            };

            for (String[] dd : dailyDirs) {
                try {
                    // 搜索该目录下所有RINEX2系统文件
                    List<FTPFile> sysFiles = new ArrayList<>();
                    FTPFile[] dFiles = ftpClient.listFiles(dd[0]);
                    String prefix = "brdc" + dd[1] + "0." + (Integer.parseInt(dd[2]) % 100);
                    Log.d(TAG, "日目录搜索: " + dd[0] + " prefix=" + prefix + " files=" + (dFiles != null ? dFiles.length : 0));
                    for (FTPFile df : dFiles) {
                        if (df.isFile() && df.getName().startsWith(prefix)
                                && df.getName().endsWith(".gz")) {
                            sysFiles.add(df);
                        }
                    }
                    if (!sysFiles.isEmpty()) {
                        // 下载GPS星历文件
                        String targetDir = dd[0];
                        if (downloadNavFiles(ftpClient, sysFiles, targetDir,
                                myDir, infile)) {
                            foundDir = myDir.getPath();
                            Log.d(TAG, "日下载GPS星历: " + sysFiles.size() + "个文件");
                            break;
                        }
                    }
                } catch (IOException e) {
                    Log.w(TAG, "日目录搜索失败: " + dd[0] + " err=" + e.getMessage());
                }
                if (foundDir != null) break;
            }
            }
            // ★ 日合并没拿到 → 小时目录搜索(遍历各小时子目录，收集所有系统文件并合并)
            if (foundDir == null) {
                String yestHourlyPath = getSelectedDataBasePath() + "/data/hourly/"
                        + yesterdayYear + "/" + yesterdayDoy;

                // [目录路径, DOY, 起始小时]
                String[][] hourlySearches = {
                    {remoteDirPath[3], String.valueOf(dayOfYear_brdc), String.valueOf(nowUtc.getHour())},
                    {yestHourlyPath, String.valueOf(yesterdayDoy), "23"}
                };

                outer:
                for (String[] hs : hourlySearches) {
                    int doy = Integer.parseInt(hs[1]);
                    int startH = Integer.parseInt(hs[2]);

                    // ★ 优先搜小时根目录(WHU文件直接在这层)
                    try {
                        List<FTPFile> companions = collectCompanionsFromDir(ftpClient, hs[0], doy);
                        if (!companions.isEmpty()) {
                            if (downloadNavFiles(ftpClient, companions, hs[0], myDir, infile)) {
                                foundDir = myDir.getPath();
                                Log.d(TAG, "小时根目录下载GPS: DOY=" + doy);
                                break outer;
                            }
                        }
                    } catch (IOException e) {
                        Log.w(TAG, "小时根目录搜索失败: " + hs[0] + " err=" + e.getMessage());
                    }

                    // 回退: 搜索子目录(当前小时→前2小时→标准IGS时刻)
                    java.util.LinkedHashSet<Integer> hours = new java.util.LinkedHashSet<>();
                    for (int h = startH; h >= Math.max(0, startH - 2); h--) hours.add(h);
                    for (int h : new int[]{0, 6, 12, 18, 23}) if (h <= startH) hours.add(h);
                    for (int h : hours) {
                        String hourDir = hs[0] + "/" + String.format("%02d", h);
                        try {
                            List<FTPFile> companions = collectCompanionsFromDir(ftpClient, hourDir, doy);
                            if (!companions.isEmpty()) {
                                if (downloadNavFiles(ftpClient, companions, hourDir, myDir, infile)) {
                                    foundDir = myDir.getPath();
                                    Log.d(TAG, "小时下载GPS: DOY=" + doy + " H=" + h);
                                    break outer;
                                }
                            }
                        } catch (IOException e) {
                        Log.w(TAG, "小时根目录搜索失败: " + hs[0] + " err=" + e.getMessage());
                    }
                    }
                }
            }

            // 下载(GPS文件已由downloadNavFiles处理)
            if (foundDir != null) {
                Handler handler = new Handler(Looper.getMainLooper());
                String localName;
                if (foundDir.equals(myDir.getPath())) {
                    // 已合并到本地，infile[0]已设置
                    localName = new File(infile[0]).getName();
                } else {
                    // 传统路径：FTP下载
                    String remoteFilePath = foundDir + "/" + brdclatestFile.getName();
                    infile[0] = myDir.getPath()+"/"+brdclatestFile.getName();
                    localName = brdclatestFile.getName();
                    try (OutputStream outputStream = Files.newOutputStream(Paths.get(infile[0]))) {
                        ftpClient.retrieveFile(remoteFilePath, outputStream);
                    }
                }
                String doneName = localName;
                handler.post(() ->
                    Toast.makeText(context, context.getString(com.ecut.pnav.R.string.dl_brdc_ok, doneName), Toast.LENGTH_SHORT).show());
                Log.d(TAG, "星历准备就绪: " + localName);
            } else {
                new Handler(Looper.getMainLooper()).post(() -> Toast.makeText(context, com.ecut.pnav.R.string.dl_brdc_not_found, Toast.LENGTH_SHORT).show());
                Log.d(TAG, "No files found in the directory.");
            }
        }else if(option == 2)
        {
            //只获取精密星历文件
            //获取精密星历文件列表
            FTPFile[] files2 = ftpClient.listFiles(remoteDirPath[1]+"/"+getgpstweek());
            // 寻找最新的精密星历的.gz文件
            // 判断文件夹是否为空
            if (files2.length == 0) {
                System.out.println("Directory is empty.");

            }else{
                for (FTPFile file : files2) {
                    if (file.isFile() && file.getName().contains(".SP3") && file.getName()
                            .contains("_"+currentYear+dayOfYear+strCurrentUtcHour)) {
                        if (preclatestFile == null || isNewer(file, preclatestFile)) {
                            preclatestFile = file;
                        }
                    }
                }
            }
            // 如果找到最新的精密星历的.gz文件，则下载
            if (preclatestFile != null) {
                String remoteFilePath2 = remoteDirPath[1] + "/"+getgpstweek()+"/" + preclatestFile.getName();
                infile[1] = myDir.getPath()+"/"+preclatestFile.getName();
                Handler handler = new Handler(Looper.getMainLooper()); // 在这里声明handler变量
                notifyProgress(preclatestFile.getName(), preclatestFile.getSize());
                try (OutputStream outputStream = Files.newOutputStream(Paths.get(myDir.getPath() + "/" + preclatestFile.getName()))) {
                    boolean success = ftpClient.retrieveFile(remoteFilePath2, outputStream);
                    if (success) {
                        handler.post(() -> Toast.makeText(context, com.ecut.pnav.R.string.dl_prec_ok, Toast.LENGTH_SHORT).show());
                        Log.d(TAG, preclatestFile.getName() + " has been downloaded successfully.");
                    }else {
                        handler.post(() ->Toast.makeText(context, com.ecut.pnav.R.string.dl_fail, Toast.LENGTH_SHORT).show());
                        Log.d(TAG, preclatestFile.getName() + " has been downloaded Failed.");
                    }
                }
            } else {
                for (FTPFile file : files2) {
                    if (file.isFile() && file.getName().contains(".SP3") && file.getName()
                            .contains("_"+currentYear+dayOfYear+strCurrentUtcHour2)) {
                        if (preclatestFile == null || isNewer(file, preclatestFile)) {
                            preclatestFile = file;
                        }
                    }
                }
                if (preclatestFile != null) {
                    String remoteFilePath2 = remoteDirPath[1] + "/"+getgpstweek()+"/" + preclatestFile.getName();
                    infile[1] = myDir.getPath()+"/"+preclatestFile.getName();
                    Handler handler = new Handler(Looper.getMainLooper()); // 在这里声明handler变量
                    notifyProgress(preclatestFile.getName(), preclatestFile.getSize());
                try (OutputStream outputStream = Files.newOutputStream(Paths.get(myDir.getPath() + "/" + preclatestFile.getName()))) {
                        boolean success = ftpClient.retrieveFile(remoteFilePath2, outputStream);
                        if (success) {
                            handler.post(() -> Toast.makeText(context, com.ecut.pnav.R.string.dl_prec_ok, Toast.LENGTH_SHORT).show());
                            Log.d(TAG, preclatestFile.getName() + " has been downloaded successfully.");
                        }else {
                            handler.post(() ->Toast.makeText(context, com.ecut.pnav.R.string.dl_fail, Toast.LENGTH_SHORT).show());
                            Log.d(TAG, preclatestFile.getName() + " has been downloaded Failed.");
                        }
                    }
                }else{
                    new Handler(Looper.getMainLooper()).post(() -> Toast.makeText(context, com.ecut.pnav.R.string.dl_prec_not_found, Toast.LENGTH_SHORT).show());
                    Log.d(TAG, "No files found in the directory.");
                }
            }
        } else if(option == 3)
        {
            //只获取CLK文件
            //获取精密星历文件列表
            FTPFile[] files3 = ftpClient.listFiles(remoteDirPath[1]+"/"+getgpstweek());
            // 寻找最新的精密星历的.gz文件
            for (FTPFile file : files3) {
                if (file.isFile() && file.getName().contains(".CLK") && file.getName().contains("_"+currentYear+dayOfYear+strCurrentUtcHour)) {
                    if (clklatestFile == null || isNewer(file, clklatestFile)) {
                        clklatestFile = file;
                    }
                }
            }
            // 如果找到最新的精密星历的.gz文件，则下载
            if (clklatestFile != null) {
                String remoteFilePath2 = remoteDirPath[1] + "/"+getgpstweek()+"/" + clklatestFile.getName();
                infile[2] = myDir.getPath()+"/"+clklatestFile.getName();
                Handler handler = new Handler(Looper.getMainLooper()); // 在这里声明handler变量
                notifyProgress(clklatestFile.getName(), clklatestFile.getSize());
                try (OutputStream outputStream = Files.newOutputStream(Paths.get(myDir.getPath() + "/" + clklatestFile.getName()))) {
                    boolean success = ftpClient.retrieveFile(remoteFilePath2, outputStream);
                    if (success) {
                        handler.post(() -> Toast.makeText(context, com.ecut.pnav.R.string.dl_clk_ok, Toast.LENGTH_SHORT).show());
                        Log.d(TAG, clklatestFile.getName() + " has been downloaded successfully.");
                    }else {
                        handler.post(() ->Toast.makeText(context, com.ecut.pnav.R.string.dl_fail, Toast.LENGTH_SHORT).show());
                        Log.d(TAG, clklatestFile.getName() + " has been downloaded Failed.");
                    }
                }
            } else {
                for (FTPFile file : files3) {
                    if (file.isFile() && file.getName().contains(".CLK") && file.getName().contains("_"+currentYear+dayOfYear+strCurrentUtcHour2)) {
                        if (clklatestFile == null || isNewer(file, clklatestFile)) {
                            clklatestFile = file;
                        }
                    }
                }
                // 如果找到最新的精密星历的.gz文件，则下载
                if (clklatestFile != null) {
                    String remoteFilePath2 = remoteDirPath[1] + "/"+getgpstweek()+"/" + clklatestFile.getName();
                    infile[2] = myDir.getPath()+"/"+clklatestFile.getName();
                    Handler handler = new Handler(Looper.getMainLooper()); // 在这里声明handler变量
                    notifyProgress(clklatestFile.getName(), clklatestFile.getSize());
                try (OutputStream outputStream = Files.newOutputStream(Paths.get(myDir.getPath() + "/" + clklatestFile.getName()))) {
                        boolean success = ftpClient.retrieveFile(remoteFilePath2, outputStream);
                        if (success) {
                            handler.post(() -> Toast.makeText(context, com.ecut.pnav.R.string.dl_clk_ok, Toast.LENGTH_SHORT).show());
                            Log.d(TAG, clklatestFile.getName() + " has been downloaded successfully.");
                        }else {
                            handler.post(() ->Toast.makeText(context, com.ecut.pnav.R.string.dl_fail, Toast.LENGTH_SHORT).show());
                            Log.d(TAG, clklatestFile.getName() + " has been downloaded Failed.");
                        }
                    }
                }else{
                    new Handler(Looper.getMainLooper()).post(() -> Toast.makeText(context, com.ecut.pnav.R.string.dl_prec_not_found, Toast.LENGTH_SHORT).show());
                    Log.d(TAG, "No files found in the directory.");
                }
            }
        } else if(option == 4)
        {
            //三种星历文件都获取
            //获取广播星历文件列表
            FTPFile[] files = ftpClient.listFiles(remoteDirPath[0]);
            //获取精密星历文件列表
            FTPFile[] files2 = ftpClient.listFiles(remoteDirPath[1]+"/"+getgpstweek());
            //获取精密星历文件列表
            FTPFile[] files3 = ftpClient.listFiles(remoteDirPath[1]+"/"+getgpstweek());
            // 寻找最新的广播星历的.gz文件
            for (FTPFile file : files) {
                if (file.isFile() && file.getName().contains("MN.rnx.gz")&&file.getName().contains("ABPO")) {
                    if (brdclatestFile == null || isNewer(file, brdclatestFile)) {
                        brdclatestFile = file;
                    }
                }
            }
            // 寻找最新的精密星历的.gz文件
            for (FTPFile file : files2) {
                if (file.isFile() && file.getName().contains(".SP3") && file.getName().contains("_"+currentYear+dayOfYear+strCurrentUtcHour)) {
                    if (preclatestFile == null || isNewer(file, preclatestFile)) {
                        preclatestFile = file;
                    }
                }
            }
            // 寻找最新的精密星历的.gz文件
            for (FTPFile file : files3) {
                if (file.isFile() && file.getName().contains(".CLK") && file.getName().contains("_"+currentYear+dayOfYear+strCurrentUtcHour)) {
                    if (clklatestFile == null || isNewer(file, clklatestFile)) {
                        clklatestFile = file;
                    }
                }
            }
            // 如果找到最新的广播\精密星历的.gz文件，则下载
            if (brdclatestFile != null) {
                String remoteFilePath = remoteDirPath[0] + "/" + brdclatestFile.getName();
                infile[0] = myDir.getPath()+"/"+brdclatestFile.getName();
                Handler handler = new Handler(Looper.getMainLooper()); // 在这里声明handler变量
                notifyProgress(brdclatestFile.getName(), brdclatestFile.getSize());
                try (OutputStream outputStream = Files.newOutputStream(Paths.get(myDir.getPath() + "/" + brdclatestFile.getName()))) {
                    boolean success = ftpClient.retrieveFile(remoteFilePath, outputStream);
                    if (success) {
                        FTPFile finalBrdclatestFile = brdclatestFile;
                        handler.post(() -> Toast.makeText(context, context.getString(com.ecut.pnav.R.string.dl_brdc_ok, finalBrdclatestFile.getName()), Toast.LENGTH_SHORT).show());
                        Log.d(TAG, brdclatestFile.getName() + " has been downloaded successfully.");
                    }else {
                        handler.post(() ->Toast.makeText(context, com.ecut.pnav.R.string.dl_fail, Toast.LENGTH_SHORT).show());
                        Log.d(TAG, brdclatestFile.getName() + " has been downloaded Failed.");
                    }
                }
            } else {
                // 寻找最新的广播星历的.gz文件 (RINEX3 + RINEX2)
                for (FTPFile file : files) {
                    if (file.isFile() && (file.getName().contains("BRDM00DLR_S_") && file.getName().endsWith("_MN.rnx.gz")
                            || file.getName().contains(dayOfYear+"0."+year2d+"n.gz"))) {
                        if (brdclatestFile == null || isNewer(file, brdclatestFile)) {
                            brdclatestFile = file;
                        }
                    }
                }
                // 如果找到最新的广播\精密星历的.gz文件，则下载
                if (brdclatestFile != null) {
                    String remoteFilePath = remoteDirPath[0] + "/" + brdclatestFile.getName();
                    infile[0] = myDir.getPath()+"/"+brdclatestFile.getName();
                    Handler handler = new Handler(Looper.getMainLooper()); // 在这里声明handler变量
                    notifyProgress(brdclatestFile.getName(), brdclatestFile.getSize());
                try (OutputStream outputStream = Files.newOutputStream(Paths.get(myDir.getPath() + "/" + brdclatestFile.getName()))) {
                        boolean success = ftpClient.retrieveFile(remoteFilePath, outputStream);
                        if (success) {
                            FTPFile ephDone = brdclatestFile;
                            handler.post(() -> Toast.makeText(context, context.getString(com.ecut.pnav.R.string.dl_brdc_ok, ephDone.getName()), Toast.LENGTH_SHORT).show());
                            Log.d(TAG, brdclatestFile.getName() + " has been downloaded successfully.");
                        }else {
                            handler.post(() ->Toast.makeText(context, com.ecut.pnav.R.string.dl_fail, Toast.LENGTH_SHORT).show());
                            Log.d(TAG, brdclatestFile.getName() + " has been downloaded Failed.");
                        }
                    }
                }else{
                    new Handler(Looper.getMainLooper()).post(() -> Toast.makeText(context, com.ecut.pnav.R.string.dl_brdc_not_found, Toast.LENGTH_SHORT).show());
                    Log.d(TAG, "No files found in the directory.");
                }
            }
            // 如果找到最新的精密星历的.gz文件，则下载
            if (preclatestFile != null) {
                String remoteFilePath2 = remoteDirPath[1] + "/"+getgpstweek()+"/" + preclatestFile.getName();
                infile[1] = myDir.getPath()+"/"+preclatestFile.getName();
                Handler handler = new Handler(Looper.getMainLooper()); // 在这里声明handler变量
                notifyProgress(preclatestFile.getName(), preclatestFile.getSize());
                try (OutputStream outputStream = Files.newOutputStream(Paths.get(myDir.getPath() + "/" + preclatestFile.getName()))) {
                    boolean success = ftpClient.retrieveFile(remoteFilePath2, outputStream);
                    if (success) {
                        handler.post(() -> Toast.makeText(context, com.ecut.pnav.R.string.dl_prec_ok, Toast.LENGTH_SHORT).show());
                        Log.d(TAG, preclatestFile.getName() + " has been downloaded successfully.");
                    }else {
                        handler.post(() ->Toast.makeText(context, com.ecut.pnav.R.string.dl_fail, Toast.LENGTH_SHORT).show());
                        Log.d(TAG, preclatestFile.getName() + " has been downloaded Failed.");
                    }
                }
            } else {
                new Handler(Looper.getMainLooper()).post(() -> Toast.makeText(context, com.ecut.pnav.R.string.dl_prec_not_found, Toast.LENGTH_SHORT).show());
                Log.d(TAG, "No files found in the directory.");
            }
            // 如果找到最新的精密星历的.gz文件，则下载
            if (clklatestFile != null) {
                String remoteFilePath2 = remoteDirPath[1] + "/"+getgpstweek()+"/" + clklatestFile.getName();
                infile[2] = myDir.getPath()+"/"+clklatestFile.getName();
                Handler handler = new Handler(Looper.getMainLooper()); // 在这里声明handler变量
                notifyProgress(clklatestFile.getName(), clklatestFile.getSize());
                try (OutputStream outputStream = Files.newOutputStream(Paths.get(myDir.getPath() + "/" + clklatestFile.getName()))) {
                    boolean success = ftpClient.retrieveFile(remoteFilePath2, outputStream);
                    if (success) {
                        handler.post(() -> Toast.makeText(context, com.ecut.pnav.R.string.dl_clk_ok, Toast.LENGTH_SHORT).show());
                        Log.d(TAG, clklatestFile.getName() + " has been downloaded successfully.");
                    }else {
                        handler.post(() ->Toast.makeText(context, com.ecut.pnav.R.string.dl_fail, Toast.LENGTH_SHORT).show());
                        Log.d(TAG, clklatestFile.getName() + " has been downloaded Failed.");
                    }
                }
            } else {
                new Handler(Looper.getMainLooper()).post(() -> Toast.makeText(context, com.ecut.pnav.R.string.dl_clk_not_found, Toast.LENGTH_SHORT).show());
                Log.d(TAG, "No files found in the directory.");
            }
        }else if(option == 5)
        {
            //获取混合星历文件
            //获取广播星历文件列表
            FTPFile[] files = ftpClient.listFiles(remoteDirPath[0]);
            // 寻找最新的广播星历的.gz文件
            for (FTPFile file : files) {
                if (file.isFile() && file.getName().contains("MN.rnx.gz")&&file.getName().contains("BRMG")) {
                    if (brdclatestFile == null || isNewer(file, brdclatestFile)) {
                        brdclatestFile = file;
                    }
                }
            }
            // 如果找到最新的广播\精密星历的.gz文件，则下载
            if (brdclatestFile != null) {
                String remoteFilePath = remoteDirPath[0] + "/" + brdclatestFile.getName();
                infile[0] = myDir.getPath()+"/"+brdclatestFile.getName();
                Handler handler = new Handler(Looper.getMainLooper()); // 在这里声明handler变量
                notifyProgress(brdclatestFile.getName(), brdclatestFile.getSize());
                try (OutputStream outputStream = Files.newOutputStream(Paths.get(myDir.getPath() + "/" + brdclatestFile.getName()))) {
                    boolean success = ftpClient.retrieveFile(remoteFilePath, outputStream);
                    if (success) {
                        FTPFile finalBrdclatestFile2 = brdclatestFile;
                        handler.post(() ->
                                Toast.makeText(context, "Mixed ephemeris download successful"+ finalBrdclatestFile2.getName(), Toast.LENGTH_SHORT)
                                        .show());
                        Log.d(TAG, brdclatestFile.getName()
                                + " has been downloaded successfully.");
                    }else {
                        handler.post(() ->
                                Toast.makeText(context, "Download failed", Toast.LENGTH_SHORT).show());
                        Log.d(TAG, brdclatestFile.getName() + " has been downloaded Failed.");
                    }
                }
            } else {
                new Handler(Looper.getMainLooper()).post(() -> Toast.makeText(context, "No files found in the directory", Toast.LENGTH_SHORT).show());
                Log.d(TAG, "No files found in the directory.");
            }
        }else if(option == 6)
        {
            // WHU MGEX NRT超快速精密星历 (SP3)，作为广播星历不可用时的回退
            // NRT文件位于 products/mgex/{GPS周}/ 目录，每小时更新
            // 命名: WUM0MGXNRT_{YYYYDOY}{hh}00_02D_05M_ORB.SP3.gz
            // SP3自带钟差(第4列)，SPP不需要额外CLK文件
            int[] gpsWeeks = {getgpstweek(), getgpstweek() - 1};
            Log.d(TAG, "NRT搜索: 尝试GPS周 " + gpsWeeks[0] + ", " + gpsWeeks[1]);
            String matchedWeekPath = null;

            outerWeek:
            for (int gpsWeek : gpsWeeks) {
                String nrtProductsPath = remoteDirPath[1] + "/" + gpsWeek;
                Log.d(TAG, "NRT搜索: path=" + nrtProductsPath + " week=" + gpsWeek);

                try {
                    FTPFile[] nrtFiles = ftpClient.listFiles(nrtProductsPath);
                    Log.d(TAG, "NRT listFiles: count=" + (nrtFiles != null ? nrtFiles.length : -1));
                    if (nrtFiles == null || nrtFiles.length == 0) {
                        Log.d(TAG, "NRT目录为空或不存在, gpsWeek=" + gpsWeek);
                        continue;
                    }
                    for (int di = 0; di < Math.min(10, nrtFiles.length); di++) {
                        Log.d(TAG, "NRT file[" + di + "]: " + nrtFiles[di].getName());
                    }
                    String[] doyCandidates = {
                        String.format("%04d", currentYear) + String.format("%03d", dayOfYear),
                        String.format("%04d", currentYear) + String.format("%03d",
                            nowUtc.minusDays(1).getDayOfYear()),
                        String.format("%04d", currentYear) + String.format("%03d",
                            nowUtc.minusDays(2).getDayOfYear())
                    };
                    Log.d(TAG, "NRT DOY候选: " + doyCandidates[0] + ", " + doyCandidates[1] + ", " + doyCandidates[2]);

                    outer:
                    for (String doyPrefix : doyCandidates) {
                        for (int h = 23; h >= 0; h--) {
                            String hourTag = doyPrefix + String.format("%02d", h) + "00";
                            FTPFile sp3Match = null;

                            for (FTPFile file : nrtFiles) {
                                String name = file.getName();
                                if (!file.isFile() || !name.contains(hourTag)) continue;

                                if ((name.contains("SP3") || name.contains("sp3"))
                                        && name.contains("05M") && name.contains("ORB")) {
                                    if (sp3Match == null || isNewer(file, sp3Match)) {
                                        sp3Match = file;
                                    }
                                }
                            }

                            if (sp3Match != null) {
                                preclatestFile = sp3Match;
                                matchedWeekPath = nrtProductsPath;
                                Log.d(TAG, "NRT匹配: week=" + gpsWeek + " DOY=" + doyPrefix + " H=" + h
                                    + " SP3=" + sp3Match.getName());
                                break outerWeek;
                            }
                        }
                    }
                } catch (IOException e) {
                    Log.w(TAG, "NRT目录列表失败: " + nrtProductsPath + " err=" + e.getMessage());
                }
            }  // end outerWeek

            // 下载NRT SP3
            if (preclatestFile != null) {
                FTPFile finalSp3 = preclatestFile;
                String remotePath = matchedWeekPath + "/" + finalSp3.getName();
                infile[1] = myDir.getPath() + "/" + finalSp3.getName();
                Handler handler = new Handler(Looper.getMainLooper());
                notifyProgress(finalSp3.getName(), finalSp3.getSize());
                try (OutputStream os = Files.newOutputStream(Paths.get(infile[1]))) {
                    if (ftpClient.retrieveFile(remotePath, os)) {
                        handler.post(() -> Toast.makeText(context, "NRT SP3: " + finalSp3.getName(), Toast.LENGTH_SHORT).show());
                        Log.d(TAG, "NRT SP3下载成功: " + finalSp3.getName());
                    } else {
                        handler.post(() -> Toast.makeText(context, com.ecut.pnav.R.string.dl_nrt_fail, Toast.LENGTH_SHORT).show());
                        infile[1] = null;
                    }
                } catch (IOException e) {
                    Log.e(TAG, "NRT SP3下载异常: " + e.getMessage());
                    infile[1] = null;
                }
            } else {
                Log.w(TAG, "未找到NRT SP3文件");
                new Handler(Looper.getMainLooper()).post(() ->
                    Toast.makeText(context, com.ecut.pnav.R.string.dl_nrt_unavailable, Toast.LENGTH_SHORT).show());
            }
        }
    }

    // ★ 收集同目录中匹配DOY的所有系统星历文件
    private static List<FTPFile> collectCompanionsFromDir(FTPClient ftpClient, String dirPath, int doy) throws IOException {
        List<FTPFile> result = new ArrayList<>();
        FTPFile[] files = ftpClient.listFiles(dirPath);
        for (FTPFile f : files) {
            if (f.isFile() && isNavFile(f.getName(), doy)) {
                result.add(f);
            }
        }
        return result;
    }

    // ★ 下载所有RINEX2系统导航文件(.n.gz/.g.gz/.c.gz/.l.gz)，返回第一个成功的文件路径
    private static boolean downloadNavFiles(FTPClient ftpClient, List<FTPFile> sysFiles,
            String remoteDir, File myDir, String[] infile) {
        if (sysFiles == null || sysFiles.isEmpty()) return false;

        boolean anyOk = false;
        for (FTPFile f : sysFiles) {
            notifyProgress(f.getName(), f.getSize());
            String localPath = myDir.getPath() + "/" + f.getName();
            try (OutputStream os = Files.newOutputStream(Paths.get(localPath))) {
                if (ftpClient.retrieveFile(remoteDir + "/" + f.getName(), os)) {
                    Log.d(TAG, "下载: " + f.getName());
                    if (!anyOk) {
                        infile[0] = localPath;
                        anyOk = true;
                    }
                }
            } catch (IOException e) {
                Log.w(TAG, "下载失败: " + f.getName());
            }
        }
        return anyOk;
    }

    public static void decompressGzipFile(String[] infile, String[] outputFilePath)
            throws IOException {
        Log.d(TAG, "decompressGzipFile CALLED infile0=" + (infile != null && infile[0] != null ? infile[0] : "null")
            + " exists=" + (infile != null && infile[0] != null && new File(infile[0]).exists()));
        // 辅助方法：解压或复制文件
        java.util.function.BiConsumer<String, String> decompressOrCopy = (src, dst) -> {
            try {
                if (src.toLowerCase().endsWith(".gz")) {
                    try (GZIPInputStream gis = new GZIPInputStream(Files.newInputStream(Paths.get(src)));
                         FileOutputStream fos = new FileOutputStream(dst)) {
                        byte[] buffer = new byte[1024];
                        int len;
                        while ((len = gis.read(buffer)) > 0) {
                            fos.write(buffer, 0, len);
                        }
                    }
                    // 解压成功后删除源 .gz，避免 myFolder 里压缩包与解压文件混存
                    new File(src).delete();
                } else if (src.toLowerCase().endsWith(".z")) {
                    // Unix compress格式(.Z)，Java原生不支持，尝试直接复制
                    Log.w(TAG, "Z格式文件需commons-compress解压，尝试直接复制: " + src);
                    Files.copy(Paths.get(src), Paths.get(dst), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                } else {
                    // 无压缩文件直接复制
                    Files.copy(Paths.get(src), Paths.get(dst), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    Log.d(TAG, "直接复制(无压缩): " + src + " -> " + dst);
                }
            } catch (IOException e) {
                Log.e(TAG, "解压/复制失败: " + src, e);
            }
        };

        // ★ RINEX2 广播星历(.n/.g/.c/.l.gz)由 decompressNavFiles 扫描解压成正确系统命名，
        //    不在此处解压到 outputFilePath[0](brdm 错位名)，否则 GLO/BDS/GAL 命名错乱且源 .gz 被删
        if(infile[0]!=null && new File(infile[0]).exists() && outputFilePath != null && outputFilePath.length > 0 && outputFilePath[0] != null){
            String f0 = infile[0].toLowerCase();
            // RINEX2 广播星历: brdc{DOY}0.{n,g,c,l}.gz — 用不带点的 n.gz/g.gz/c.gz/l.gz 匹配，
            // 避免 brdc2310.26n.gz 的 .endsWith(".n.gz") 因末尾字符为数字而误判 false
            boolean isRnx2Nav = f0.endsWith("n.gz") || f0.endsWith("g.gz")
                    || f0.endsWith("c.gz") || f0.endsWith("l.gz");
            // RINEX3 多系统文件(.rnx.gz)或精密/其他格式 → 正常解压到 outputFilePath[0]
            if (!isRnx2Nav) {
                decompressOrCopy.accept(infile[0], outputFilePath[0]);
            }
        }
        if(infile[1]!=null && new File(infile[1]).exists() && outputFilePath != null && outputFilePath.length > 1 && outputFilePath[1] != null) {
            decompressOrCopy.accept(infile[1], outputFilePath[1]);
        }
        if(infile[2]!=null && new File(infile[2]).exists() && outputFilePath != null && outputFilePath.length > 2 && outputFilePath[2] != null){
            decompressOrCopy.accept(infile[2], outputFilePath[2]);
        }
        // ★ 自动解压多系统导航文件(GLO/BDS/GAL)
        if(infile[0] != null && new File(infile[0]).exists()) {
            String myDir = new File(infile[0]).getParent();
            Log.d(TAG, "decompressGzipFile: infile[0]=" + infile[0] + " myDir=" + myDir);
            if (myDir != null) {
                decompressNavFiles(myDir);
            }
        }
    }

    // ★ 解压所有下载的导航文件(.n.gz/.g.gz/.c.gz/.l.gz)
    // 不依赖 MainActivity.navFilePaths（后台下载线程可能尚未初始化它），直接扫描目录
    public static void decompressNavFiles(String myDirPath) {
        Log.d(TAG, "decompressNavFiles CALLED myDir=" + myDirPath);
        if (myDirPath == null) return;
        File dir = new File(myDirPath);
        File[] files = dir.listFiles();
        Log.d(TAG, "decompressNavFiles files=" + (files == null ? "null" : files.length));
        if (files == null) return;
        for (File gz : files) {
            String name = gz.getName();
            // 用不带点的 n.gz/g.gz/c.gz/l.gz 匹配 RINEX2 广播星历（brdc2310.26n.gz 等）
            boolean isNav = name.endsWith("n.gz") || name.endsWith("g.gz")
                    || name.endsWith("c.gz") || name.endsWith("l.gz");
            if (!isNav) continue;
            String outPath = myDirPath + "/" + name.substring(0, name.length() - 3); // 去掉 .gz
            try (GZIPInputStream gis = new GZIPInputStream(Files.newInputStream(gz.toPath()));
                 FileOutputStream fos = new FileOutputStream(outPath)) {
                byte[] buffer = new byte[1024];
                int len;
                while ((len = gis.read(buffer)) > 0) {
                    fos.write(buffer, 0, len);
                }
                Log.d(TAG, "解压导航文件: " + name);
                // 解压成功后删除源 .gz
                gz.delete();
            } catch (IOException e) {
                Log.w(TAG, "解压失败: " + name);
            }
        }
    }

    // ★ 清理过期的星历文件，保留今天 + 昨天（昨天星历可作缓存回退）
    public static void cleanupExpiredEphemeris(File myDir) {
        if (myDir == null || !myDir.isDirectory()) return;
        File[] files = myDir.listFiles();
        if (files == null) return;

        ZonedDateTime nowUtc = ZonedDateTime.now(ZoneOffset.UTC);
        String todayDoy = String.format("%03d", nowUtc.getDayOfYear());
        String yestDoy = String.format("%03d", nowUtc.minusDays(1).getDayOfYear());

        int deleted = 0;
        for (File f : files) {
            if (!f.isFile()) continue;
            String doy = extractDoy(f.getName());
            if (doy == null) continue;                                  // 非星历文件，跳过
            if (doy.equals(todayDoy) || doy.equals(yestDoy)) continue;  // 今天/昨天保留
            if (f.delete()) {
                deleted++;
                Log.d(TAG, "清理过期星历: " + f.getName());
            }
        }
        if (deleted > 0) Log.d(TAG, "共清理 " + deleted + " 个过期星历文件");
    }

    // ★ 计算导航文件数组的总大小（仅统计存在的文件）
    private static long totalNavSize(String[] navPaths) {
        long total = 0;
        if (navPaths != null) {
            for (String fp : navPaths) {
                if (fp != null) {
                    File f = new File(fp);
                    if (f.exists()) total += f.length();
                }
            }
        }
        return total;
    }

    // ★ 广播星历缓存是否新鲜（age<1h && 当天 && >150KB）
    public static boolean isBroadcastFresh(String[] navPaths) {
        if (navPaths == null || navPaths.length == 0) return false;
        File firstNav = new File(navPaths[0]);
        if (!firstNav.exists()) return false;
        long ageMs = System.currentTimeMillis() - firstNav.lastModified();
        String todayDoy = String.format("%03d", ZonedDateTime.now(ZoneOffset.UTC).getDayOfYear());
        boolean isToday = firstNav.getName().contains(todayDoy);
        return ageMs < 3600_000 && isToday && totalNavSize(navPaths) > 150000;
    }

    // ★ 广播星历缓存是否可用（昨天回退：age<2h && 非当天 && >=150KB）
    public static boolean isBroadcastUsable(String[] navPaths) {
        if (navPaths == null || navPaths.length == 0) return false;
        File firstNav = new File(navPaths[0]);
        if (!firstNav.exists()) return false;
        long ageMs = System.currentTimeMillis() - firstNav.lastModified();
        String todayDoy = String.format("%03d", ZonedDateTime.now(ZoneOffset.UTC).getDayOfYear());
        boolean isToday = firstNav.getName().contains(todayDoy);
        return ageMs < 7200_000 && !isToday && totalNavSize(navPaths) >= 150000;
    }

    // ★ NRT 精密星历缓存是否新鲜（age<1h）
    public static boolean isNrtFresh(String sp3Path) {
        if (sp3Path == null) return false;
        File sp3 = new File(sp3Path);
        if (!sp3.exists()) return false;
        long ageMs = System.currentTimeMillis() - sp3.lastModified();
        return ageMs < 3600_000;
    }

    // ★ 从文件名提取 DOY（3位），非星历文件返回 null
    private static String extractDoy(String name) {
        if (name == null) return null;
        // brdc{DOY}0. / brdm{DOY}0. → DOY 在第 4~7 位
        if (name.startsWith("brdc") || name.startsWith("brdm")) {
            if (name.length() >= 7) return name.substring(4, 7);
            return null;
        }
        // WUM..._{YYYY}{DOY}... → DOY 在年份后 3 位
        if (name.contains("WUM")) {
            int idx = name.indexOf('_');
            if (idx >= 0 && idx + 8 <= name.length()) {
                String yyyy = name.substring(idx + 1, idx + 5);
                String doy = name.substring(idx + 5, idx + 8);
                if (yyyy.matches("\\d{4}") && doy.matches("\\d{3}")) return doy;
            }
            return null;
        }
        return null;
    }

    // ★ 安全比较两个 FTPFile 的修改时间（部分 FTP 服务器 getTimestamp() 返回 null）
    private static boolean isNewer(FTPFile a, FTPFile b) {
        if (a == null || b == null) return false;
        java.util.Calendar ta = a.getTimestamp();
        java.util.Calendar tb = b.getTimestamp();
        if (ta == null || tb == null) return false;
        return ta.after(tb);
    }
}

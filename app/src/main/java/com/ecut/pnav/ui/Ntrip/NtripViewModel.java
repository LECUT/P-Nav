package com.ecut.pnav.ui.Ntrip;
import static android.content.Context.MODE_PRIVATE;

import static com.ecut.pnav.ui.Ntrip.NtripFragment.initrtcm;
import static com.ecut.pnav.ui.Ntrip.NtripFragment.inputrtcm3;
import static com.rtklib.bean.nav_t.getMaxSat;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.ecut.pnav.ui.Ntrip.Adapter.ShowSubitemAdapter;
import com.rtklib.bean.gtime_t;
import com.rtklib.bean.obs_t;
import com.rtklib.bean.obsd_t;
import com.rtklib.bean.sta_t;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.TimeZone;

public class NtripViewModel extends ViewModel implements OnRtcmDataReceivedListener{
    // 连接状态 Toast token —— Fragment 观察端映射为本地化文案
    public static final String NT_FETCHING = "ntrip_fetching";
    public static final String NT_BAD_PWD = "ntrip_bad_pwd";
    // MutableLiveData用于存储和观察数据
    static Thread getrtcmthread;
    private final MutableLiveData<String> rtcmdata = new MutableLiveData<>();
    final String[] data = {
            "Launch", "Data source settings","GGA settings"
    };
    static boolean firstreceive = true;
    ShowSubitemAdapter adapter;
    public static List<NtripClient> mData;
    ArrayList<String> Mountpoint= new ArrayList<>() ;
    // 使用一个变量来记录用户的选择
    SharedPreferences sharedPreferences;
    static volatile boolean isRunning = true; // 控制线程运行的标志

    String User;
    String Password;
    static double[] gga_message = {0,0};
    static Socket socket;
    static rtcm_t rtcm = new rtcm_t();
    static gtime_t[] obstime = new gtime_t[getMaxSat()];
    static obsd_t[] obssd = new obsd_t[getMaxSat()];
    static double[] stapos = new double[3];
    static int no = 0;
    static OnRtcmDataReceivedListener dataReceivedListener;
    private static MutableLiveData<Boolean> switchState = new MutableLiveData<>();


    static {
        System.loadLibrary("pnav"); // 替换为你的库名
    }
//    public static native int inputrtcm3(rtcm_t rtcm, byte data, obsd_t[] obss, gtime_t[] time, double[] stapos);
//    public native void initrtcm();
    public NtripViewModel(Context context) {
        sharedPreferences = context.getSharedPreferences(
                context.getPackageName() + "_preferences", MODE_PRIVATE);
        mData = new LinkedList<>();
        String savedIp = sharedPreferences.getString("User_ip", "120.253.226.97");
        int savedPort = Integer.parseInt(sharedPreferences.getString("User_port", "8001"));
        String savedMount = sharedPreferences.getString("Mount_point", "RTCM33_GRCE");
        String savedUser = sharedPreferences.getString("User_text", "user");
        String savedPassword = sharedPreferences.getString("Password_text", "password");
        mData.add(new NtripClient(savedIp, savedPort, savedMount, savedUser, savedPassword));
        // 恢复已保存的 GGA 坐标
        gga_message[0] = Double.longBitsToDouble(sharedPreferences.getLong("gga_lat", Double.doubleToLongBits(0)));
        gga_message[1] = Double.longBitsToDouble(sharedPreferences.getLong("gga_lon", Double.doubleToLongBits(0)));
        initrtcm();
        rtcm.setSta(new sta_t());
        for(int i=0;i<getMaxSat();i++){
            obssd[i] = new obsd_t();
            obstime[i] = new gtime_t(0,0);
        }
    }
    public void updateRtcmData(rtcm_t rtcmData) {
        dataReceivedListener.onRtcmDataReceived(rtcmData);
    }
    // 获取LiveData
    public LiveData<Boolean> getSwitchState() {
        return switchState;
    }
    public void setSwitchState(boolean state) {
        switchState.postValue(state);
    }
    public LiveData<String> getData() {
        return rtcmdata;
    }
    private static MutableLiveData<String> toastMessage = new MutableLiveData<>();
    public LiveData<String> getToastMessage() {
        return toastMessage;
    }
    public void getSourceTable(String server, int port, ArrayList<String> Mountpoint) {
        try (Socket socket = new Socket(server, port);
             BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             OutputStream os = socket.getOutputStream()) {
            // Send request for source table
            String request = "GET / HTTP/1.1\r\n";
            request += "Ntrip-Version: Ntrip/2.0\r\n";
            request += "User-Agent: NTRIP GNSSInternetRadio/1.4.10\r\n";
            request += "Accept: */*\r\n";
            request += "Connection: close\r\n";
            request += "\r\n";
            os.write(request.getBytes());
            os.flush();
            // Read source table
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
                if(!Objects.equals(extractBetweenSemicolons(line), ""))
                    Mountpoint.add(extractBetweenSemicolons(line));
            }

        } catch (Exception e) {
            Log.e("NtripViewModel", "Error parsing mountpoints", e);
        }
    }
    public static String extractBetweenSemicolons(String input) {
        String[] parts = input.split(";");
        if (parts.length > 2) {
            return parts[1]; // 返回第一个分号和第二个分号之间的字符串
        }
        return ""; // 如果没有足够的分号，返回空字符串
    }
    public void setDataReceivedListener(OnRtcmDataReceivedListener listener) {
        this.dataReceivedListener = listener;
    }
    /**
     * @deprecated 使用 {@link com.ecut.pnav.ui.MyService} 替代。
     * 此方法存在 BufferedReader 截流 RTCM 二进制数据的 bug，且与 MyService 会产生双连接冲突。
     */
    @Deprecated
    public static void receivertcmdata(){
        isRunning = true;
        getrtcmthread = new Thread(() -> {
            try {
                socket = new Socket(mData.get(0).getHost(), mData.get(0).getPort());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            try (
                BufferedInputStream bis = new BufferedInputStream(socket.getInputStream());
                BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                OutputStream os = socket.getOutputStream()) {
                // Encode username and password in Base64
                String auth = mData.get(0).getNtrip_User() + ":" + mData.get(0).getNtrip_password();
                String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes());
                // Send request to connect to the mountpoint
                String request = "GET /" + mData.get(0).getMountPoint() + " HTTP/1.0\r\n";
                request += "User-Agent: NTRIP GNSSInternetRadio/1.4.10\r\n";
                request += "Authorization: Basic " + encodedAuth + "\r\n";
                request += "Accept: */*\r\n";
                request += "Connection: close\r\n";
                request += "\r\n";
                os.write(request.getBytes());
                os.flush();
                // Read data from the mountpoint
                // 读取服务器响应并检查是否成功连接到挂载点
                String line;
                while ((line = reader.readLine()) != null&&isRunning) {
                    if (line.contains("200 OK")) {
                        System.out.println("Connected to mount point and ready to " +
                                "receive RTCM data.");
                        String ggaData = "$GPGGA," + gettime() + "," + gga_message[0] + ",N," + gga_message[1] +
                                ",E,2,10,5.00,10.0000,M,-2.860,M,08,0000*7A\r\n";
                        os.write(ggaData.getBytes());
                        os.flush();
                        // 开始接收RTCM数据流
                        byte[] buffer = new byte[4096];
                        int bytesRead;
                        while (((bytesRead = bis.read(buffer)) != -1)&&isRunning) {
                            if(firstreceive){
                                toastMessage.postValue(NT_FETCHING);
//                                menuItem.setEnabled(true);
                                firstreceive = false;
                            }
                            for (int i = 0; i < bytesRead; i++) {
                                int result = inputrtcm3(rtcm, buffer[i], obssd, obstime, stapos);
                                if (result > 0) {
                                    Log.d("成功解码消息，类型: ", String.valueOf(result));
                                    if(stapos[0]!=0){
                                        rtcm.getSta().setPos(stapos);
                                    }
                                    if (result == 1) {
                                        for (int k = 0; k < obstime.length; k++) {
                                            if (obssd[k].sat[0] != 0) no++;
                                        }
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
                                        //传输数据
                                        dataReceivedListener.onRtcmDataReceived(rtcm);
                                    }
                                }
                            }
                        }
                    } else if (line.startsWith("ERROR")) {
                        System.err.println("Error connecting to mount point: " + line);
                        isRunning = false;
                        toastMessage.postValue(NT_BAD_PWD);
                        switchState.postValue(false);
//                        menuItem.setEnabled(false);
                        return;
                    }
                }
            } catch (Exception e) {
                Log.e("NtripViewModel", "Error in mountpoint processing", e);
            }
        });
        getrtcmthread.start();
    }
    public static void stoprtcmReceiver() {
        isRunning = false; // 设置标志位为 false，停止线程
        if (getrtcmthread != null && getrtcmthread.isAlive()) {
            getrtcmthread.interrupt(); // 请求线程停止
        }
        if (socket != null && !socket.isClosed()) {
            try {
                socket.close(); // 关闭 socket 连接
            } catch (IOException e) {
                Log.e("NtripViewModel", "Failed to close socket", e);
            }
        }
    }
    private static String getCurrentUtcTimeFormatted() {
        @SuppressLint("SimpleDateFormat") SimpleDateFormat formatter = new SimpleDateFormat("HHmmss.SSS");
        formatter.setTimeZone(TimeZone.getTimeZone("UTC"));
        Date currentUtcTime = new Date();
        return formatter.format(currentUtcTime);
    }
    public static String gettime(){
        // 创建一个Calendar实例，并设置为UTC时间zone
        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));

        // 设置时间格式，例如hhmmss.sss
        @SuppressLint("SimpleDateFormat") SimpleDateFormat dateFormat =
                new SimpleDateFormat("HHmmss.SSS");
        // 获取当前UTC时间并格式化
        return dateFormat.format(calendar.getTime());
    }

    public double[] getGgaMessage() {
        return gga_message;
    }
    /** 保存 GGA 坐标到 SharedPreferences，确保应用重启后不丢失 */
    public void saveGgaMessage() {
        sharedPreferences.edit()
                .putLong("gga_lat", Double.doubleToRawLongBits(gga_message[0]))
                .putLong("gga_lon", Double.doubleToRawLongBits(gga_message[1]))
                .apply();
    }
    @Override
    public void onRtcmDataReceived(rtcm_t rtcmData) {
        this.dataReceivedListener = (OnRtcmDataReceivedListener) rtcmData;
    }
}

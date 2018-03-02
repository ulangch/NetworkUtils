package com.mi.networkutils.connectivity;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkInfo;
import android.net.RouteInfo;

import com.mi.networkutils.utils.Command;
import com.mi.networkutils.utils.Log;

public class WlanLatencyAnalysis {
    private static final String TAG = "WlanLatencyAnalysis";

    private final Context mContext;
    private final String mLogFileName;
    private final String mBadLatencyFileName;
    private final double mBadLatencyMs;
    private Command mPingCommand;
    private BufferedOutputStream mLogWriter;
    private BufferedOutputStream mBadLatencyWriter;
    private long mBadLatencyMsCountScan;
    private long mBadLatencyMsCountNotScan;
    private InetAddress mGateWay;
    private String mInterface;
    
    public WlanLatencyAnalysis(Context context, String filename, double badLatencyMs, String badLatencyFileName) {
        mLogFileName = filename;
        mContext = context;
        mBadLatencyMs = badLatencyMs;
        mBadLatencyFileName = badLatencyFileName;
    }
    
    public void start() {
        
        final ConnectivityManager cm = (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        final Network[] networks = cm.getAllNetworks();
        
        for (Network network : networks) {
            NetworkInfo ni = cm.getNetworkInfo(network);
            if (ni.getType() == ConnectivityManager.TYPE_WIFI) {
                
                LinkProperties lp = cm.getLinkProperties(network);
                mInterface = lp.getInterfaceName();
                for (RouteInfo route : lp.getRoutes()) {
                    final InetAddress gateway = route.getGateway();
                    if (!gateway.isAnyLocalAddress()) {
                        mGateWay = gateway;
                    }
                }
                break;
            }
        }
        Log.i(TAG, "interface = " + mInterface + " gateway = " + mGateWay.getHostAddress());
        
        if (mLogFileName != null) {
            try {
                final OutputStream fis = new FileOutputStream(new File(mLogFileName));
                mLogWriter = new BufferedOutputStream(fis);
            } catch (FileNotFoundException e) {
                Log.e(TAG, e.toString());
            }
        }
        
        if (mBadLatencyFileName != null) {
            try {
                final OutputStream fis = new FileOutputStream(new File(mBadLatencyFileName));
                mBadLatencyWriter = new BufferedOutputStream(fis);
            } catch (FileNotFoundException e) {
                Log.e(TAG, e.toString());
            }
        }
        
        Command.runRootCommand("setenforce 0");
        runPingCommand();
    }

    public void stop() {
        // TODO: stop ping
        stopPingCommand();
        if (mLogWriter != null) {
            try {
                mLogWriter.flush();
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            mLogWriter = null;
        }
        if (mBadLatencyWriter != null) {
            try {
                String out = "the count of latency > " + mBadLatencyMs + " in the scan state is " + mBadLatencyMsCountScan + "\n";
                out += "the count of latency > " + mBadLatencyMs + " not int the scan state is " + mBadLatencyMsCountNotScan + "\n";
                mBadLatencyWriter.write(out.getBytes());
                mBadLatencyWriter.flush();
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            mBadLatencyWriter = null;
        }
        Command.runRootCommand("setenforce 1");
    }

    public String getBadLatencyInfo() {
        return null;
    }
    
    private int getMatchedIntValue(String str, String pattenStart, String pattenEnd) {
        int value = -1;
        try {
            int timeIndexStart = str.indexOf(pattenStart);
            if (timeIndexStart < 0) {
                return value;
            }
            timeIndexStart += pattenStart.length();
            final int timeIndexEnd = str.indexOf(pattenEnd, timeIndexStart);
            value = Integer.parseInt(str.substring(timeIndexStart, timeIndexEnd));
        } catch (NumberFormatException e) {
            Log.e(TAG, str + " " + e);
        } catch (IndexOutOfBoundsException e) {
            Log.e(TAG, str + " " + e);
        }
        
        return value;
    }
    
    private double getMatchedDoubleValue(String str, String pattenStart, String pattenEnd) {
        double value = -1.00;
        try {
            int timeIndexStart = str.indexOf(pattenStart);
            if (timeIndexStart < 0) {
                return value;
            }
            timeIndexStart += pattenStart.length();
            final int timeIndexEnd = str.indexOf(pattenEnd, timeIndexStart);
            value = Double.parseDouble(str.substring(timeIndexStart, timeIndexEnd));
        } catch (NumberFormatException e) {
            Log.e(TAG, str + " " + e);
        } catch (IndexOutOfBoundsException e) {
            Log.e(TAG, str + " " + e);
        }
        
        return value;
    }
    
    private boolean parsePingMessage(String line) throws IOException {
        if (mLogWriter != null) {
            mLogWriter.write(line.getBytes());
            mLogWriter.write("\n".getBytes());
        }

        /*
         * [1465348941.169466] probe_scan=0 reply_scan=0 [1465348941.176433] 64 bytes from 192.168.31.1: icmp_seq=1662 ttl=64 time=6.02 ms
         * [1465349114.772856] probe_scan=0 [1465349114.772934] no answer yet for icmp_seq=2
         * */
        double timeMs = mBadLatencyMs + 100.00;
        if (line.contains("bytes from") && line.contains("time=")) {
            // replay ok.
            timeMs = getMatchedDoubleValue(line, "time=", " ms");
        }
        
        if (timeMs < 0.00 || timeMs > mBadLatencyMs) {
            Log.e(TAG, line);
            int scan_sta = getMatchedIntValue(line, "probe_scan=", " ");
            if (scan_sta >= 0) {
                if (scan_sta == 1) {
                    mBadLatencyMsCountScan++;
                } else {
                    mBadLatencyMsCountNotScan++;
                }
            }
            if (mBadLatencyWriter != null) {
                mBadLatencyWriter.write(line.getBytes());
                mBadLatencyWriter.write("\n".getBytes());
            }
            Log.e(TAG, "mBadLatencyMsCountScan = " + mBadLatencyMsCountScan + " mBadLatencyMsCountNotScan = " + mBadLatencyMsCountNotScan);
        }

        return true;
    }
    
    private void runPingCommand() {
        String command = Command.PING_COMMAND + (mInterface != null ? (" -I " + mInterface) : "") + " -D -O -E wifi_scan " + mGateWay.getHostAddress();
        mPingCommand = new Command(command, Command.PING_PROCESS, null, true);
        mPingCommand.start();
        new Thread(new Runnable() {
            @Override
            public void run() {
                final InputStream stdout = mPingCommand.getInputStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(stdout));
                String line = null;
                try {
                    while ((line = reader.readLine()) != null) {
                        parsePingMessage(line);
                    }
                    Log.i(TAG, "exit read.");
                } catch (IOException e) {
                    Log.i(TAG, "io error = " + e);
                }
            }
        }).start();
    }

    private int stopPingCommand() {
        mPingCommand.destroy();
        try {
            if (mPingCommand.isAlive()) {
                mPingCommand.join();
            }
        } catch (InterruptedException e) {
        }
        
        int ret = mPingCommand.getExitCode();
        
        mPingCommand = null;
        return ret;
    }
}

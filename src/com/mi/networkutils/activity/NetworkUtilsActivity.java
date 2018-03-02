package com.mi.networkutils.activity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

import com.mi.networkutils.R;
import com.mi.networkutils.capture.Capture;
import com.mi.networkutils.connectivity.WlanLatencyAnalysis;
import com.mi.networkutils.utils.Command;
import com.mi.networkutils.utils.Log;
import com.mi.networkutils.utils.Utils;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.AssetManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;

public class NetworkUtilsActivity extends PreferenceActivity implements OnSharedPreferenceChangeListener {
    private final static String TAG = "NetworkUtilsActivity";
    private static final String KEY_CAPTURE_ISCAPTURING = "isCapturing";
    private static final String KEY_CAPTURE_INTERFACE = "interface";
    private static final String KEY_CAPTURE_WRITE_FILE = "write_file";
    private static final String KEY_WL_ISANYALYSING = "isAnalysising";
    private static final String KEY_WL_LATENCY = "wl_latency_timeMs";
    private static final String KEY_WL_LOGFILE = "wl_latency_logfile";
    private static final String KEY_WL_LATENCY_LOGFILE = "wl_latency_badlatency_logfile";
    private static final int MSG_NO_ROOT = 0;

    private Preference mIsCapturingCheck;
    private Preference mInterfaceList;
    private EditTextPreference mWriteFileEdit;
    
    private EditTextPreference mWLLatencyTimeEdit;
    private EditTextPreference mWLLogFileEdit;
    private EditTextPreference mWLLatencyLogFileEdit;

    private Capture mCapture;
    private WlanLatencyAnalysis mWlanLatencyAnalysis;
    
    final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
            case MSG_NO_ROOT:
                showAToast(getString(R.string.require_root_alert));
                break;
            }
            super.handleMessage(msg);
        }
    };

    @SuppressWarnings("deprecation")
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.networkutils_preference);
        /* init Runtime Environment */
        new Thread() {
            @Override
            public void run() {
                if (!Utils.isRoot()) {
                    mHandler.sendEmptyMessage(MSG_NO_ROOT);
                }
                initRuntimeEnvironment();
            }
        }.start();

        /* Capture */
        mIsCapturingCheck = (Preference) findPreference(KEY_CAPTURE_ISCAPTURING);
        mInterfaceList = (Preference) findPreference(KEY_CAPTURE_INTERFACE);
        mWriteFileEdit = (EditTextPreference) findPreference(KEY_CAPTURE_WRITE_FILE);
        
        /*ping*/
        mWLLatencyTimeEdit = (EditTextPreference) findPreference(KEY_WL_LATENCY);
        mWLLogFileEdit = (EditTextPreference) findPreference(KEY_WL_LOGFILE);
        mWLLatencyLogFileEdit = (EditTextPreference) findPreference(KEY_WL_LATENCY_LOGFILE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
        if (!settings.getString(KEY_CAPTURE_INTERFACE, "").equals("")) {
            mInterfaceList.setSummary(settings.getString(KEY_CAPTURE_INTERFACE, "").toUpperCase());
        }

        mWriteFileEdit.setText(Utils.generateFileName("networkutils_capture_", "pcap"));
        mWLLogFileEdit.setText(Utils.generateFileName("networkutils_wl_logfile_", "txt"));
        mWLLatencyLogFileEdit.setText(Utils.generateFileName("networkutils_latency_logfile_", "txt"));
        
        getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences settings, String key) {
        Log.i(TAG, " onSharedPreferenceChanged " + key);
        if (key.equals(KEY_CAPTURE_ISCAPTURING)) {
            if (settings.getBoolean(KEY_CAPTURE_ISCAPTURING, false)) {
                // Start Capture
                startCapture();
                mInterfaceList.setEnabled(false);
                mWriteFileEdit.setEnabled(false);
                String filename = settings.getString(KEY_CAPTURE_WRITE_FILE, "");
                if (filename.equals("")) {
                    filename = null;
                } else {
                    filename = Utils.getDataPath() + "/" + filename;
                    mWriteFileEdit.setSummary(filename);
                }
            } else {
                //Stop Capture
                stopCapture();
                mInterfaceList.setEnabled(true);
                mWriteFileEdit.setEnabled(true);
            }
        } else if (key.equals(KEY_WL_ISANYALYSING)) {
            if (settings.getBoolean(KEY_WL_ISANYALYSING, false)) {
                mWLLatencyTimeEdit.setEnabled(false);
                mWLLogFileEdit.setEnabled(false);
                mWLLatencyLogFileEdit.setEnabled(false);
                final int latency = Integer.parseInt(settings.getString(KEY_WL_LATENCY, "50"));
                final String logFileName = settings.getString(KEY_WL_LOGFILE, "");
                final String latencyLogFileName = settings.getString(KEY_WL_LATENCY_LOGFILE, "");
                mWlanLatencyAnalysis = new WlanLatencyAnalysis(this,
                        Utils.getDataPath() + "/"+ logFileName, latency, Utils.getDataPath() + "/" + latencyLogFileName);
                mWlanLatencyAnalysis.start();
            } else {
                if (mWlanLatencyAnalysis != null) {
                    mWlanLatencyAnalysis.stop();
                }
                mWlanLatencyAnalysis = null;
                mWLLatencyTimeEdit.setEnabled(true);
                mWLLogFileEdit.setEnabled(true);
                mWLLatencyLogFileEdit.setEnabled(true);
            }
        }
    }

    private void showAToast(String msg) {
        if (!isFinishing()) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setMessage(msg)
                    .setCancelable(false)
                    .setNegativeButton(getString(R.string.ok_iknow),
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog,
                                        int id) {
                                    dialog.cancel();
                                }
                            });
            AlertDialog alert = builder.create();
            alert.show();
        }
    }

    private void initRuntimeEnvironment() {
        final SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
        try {
            String versionName = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            if (!settings.getBoolean(versionName, false)) {
                copyAssets();
                Editor edit = settings.edit();
                edit.putBoolean(versionName, true);
                edit.commit();
            }
        } catch (NameNotFoundException e) {
            Log.e(TAG, "name not found " + e);
        }
    }

    private void copyAssets() {
        AssetManager assetManager = getAssets();
        String[] files = null;
        try {
            files = assetManager.list("");
        } catch (Exception e) {
            Log.e(TAG, "copyAssets list " + e);
        }
        if (files != null) {
            for (String file : files) {
                Log.e(TAG, file);
                try {
                    InputStream in = assetManager.open(file);
                    File outFile = new File(Command.COMMAND_PATH + file);
                    OutputStream out = new FileOutputStream(outFile);
                    Utils.copyFile(in, out);
                    outFile.setExecutable(true, false);
                    outFile.setReadable(true, false);//设置可读权限
                    in.close();
                    out.close();
                } catch (Exception e) {
                    Log.e(TAG, "copyAssets file: " + file + " " + e);
                }
            }
        }
    }
    
    private void startCapture() {
        final SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
        String interf = settings.getString(KEY_CAPTURE_INTERFACE, "");
        if (interf.equals("") || interf.equalsIgnoreCase("current")) {
            interf = null;
        }
        String filename = settings.getString(KEY_CAPTURE_WRITE_FILE, "");
        if (filename.equals("")) {
            filename = null;
        } else {
            filename = Utils.getDataPath() + "/" + filename;
        }
        mCapture = new Capture(interf, filename);
        mCapture.start();
    }
    
    private void stopCapture() {
        if (mCapture != null) {
            mCapture.stop();
        }
        mCapture = null;
    }
}

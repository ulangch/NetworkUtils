package com.mi.networkutils.capture;

import com.mi.networkutils.utils.Command;
import com.mi.networkutils.utils.Log;

public class Capture {
    private final static String TAG = "Capture";
    
    private String mNetInterface;
    private String mWriteFileName;
    private boolean mIsStarted;
    private Command mCommand;
    
    public Capture() {
        this(null, null);
    }
    
    public Capture(String netInterface, String writeFile){
        mNetInterface = netInterface;
        mWriteFileName = writeFile;
    }
    
    private void runCaptureCommand() {
        String command = Command.CAPTURE_CMD;
        if (mNetInterface != null) {
            command = command + " -i " + mNetInterface;
        }
        if (mWriteFileName != null) {
            command = command +  " -w " + mWriteFileName;
        }
        Log.i(TAG, "command " + command);
        mCommand = new Command(command, Command.CAPTURE_PROCESS, null, true);
        mCommand.start();
    }

    private int stopCaptureCommand() {
        mCommand.destroy();
        try {
            if (mCommand.isAlive()) {
                mCommand.join();
            }
        } catch (InterruptedException e) {
            Log.e(TAG, "jion error " + e);
        }
        int ret = mCommand.getExitCode();
        mCommand = null;
        return ret;
    }

    public boolean start() {
        if (mIsStarted) {
            return true;
        }
        runCaptureCommand(); 
        mIsStarted = true;
        return true;
    }

    public void stop() {
        if (!mIsStarted) {
            return;
        }
        stopCaptureCommand();
        mIsStarted = false;
    }
}

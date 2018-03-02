package com.mi.networkutils.utils;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;

public final class Command extends Thread {
    private static final String TAG = "COMMAND";
    
    public static final String COMMAND_PATH = "/data/data/com.mi.networkutils/";
    public static final String DEFAULT_SHELL = "/system/bin/sh";
    public static final String DEFAULT_ROOT = "/system/bin/su";
    public static final String ALTERNATIVE_ROOT = "/system/xbin/su";
    public static final String CAPTURE_PROCESS = "networkutils_capture";
    public static final String CAPTURE_CMD = COMMAND_PATH + CAPTURE_PROCESS;
    public static final String WPA_CLI_PROCESS = "networkutils_wpa_cli";
    public static final String WPA_CLI_COMMAND = COMMAND_PATH + WPA_CLI_PROCESS;
    public static final String PING_PROCESS = "networkutils_ping";
    public static final String PING_COMMAND = COMMAND_PATH + PING_PROCESS;

    public static final int TIME_OUT = -99;
    private static String mRootShell;
    private static String mShell;

    private final String mCommand;
    private final String mProcessName;
    private final StringBuilder mResult;
    private final Object mLock = new Object();
    private final boolean mAsRoot;
    private int mExitCode;
    private int[] pid = new int[1];
    private FileDescriptor mPipe;

    
    public Command(String command, String processName, StringBuilder result, boolean asroot) {
        this.mCommand = command + "\n" + "exit\n";
        this.mProcessName = processName;
        this.mResult = result;
        this.mAsRoot = asroot;
    }

    public static int runRootCommand(String command) {
        return runRootCommand(command, null);
    }
    
    public static int runRootCommand(String command, String processName) {
        return runCommand(command, processName, null, TIME_OUT, true);
    }
    
    public static int runCommand(String command) {
        return runCommand(command, null, null, TIME_OUT, true);
    }
    
    public static int runCommand(String command,
            final StringBuilder res, final long timeout, final boolean asroot) {
        return runCommand(command, null, res, timeout, asroot);
    }
    
    public synchronized static int runCommand(final String command, final String processName,
            final StringBuilder res, final long timeout, final boolean asroot) {
        final Command cmd = new Command(command, processName, res, asroot);
        cmd.start();
        try {
            if (timeout > 0) {
                cmd.join(timeout);
            } else {
                cmd.join();
            }
            if (cmd.isAlive()) {
                cmd.destroy();
                cmd.join(1000);
                return TIME_OUT;
            }
        } catch (InterruptedException e) {
            return TIME_OUT;
        }
        return cmd.mExitCode;
    }
    
    public InputStream getInputStream() {
        synchronized(mLock) {
            if (mPipe != null) {
                return new FileInputStream(mPipe);
            }
        }
        return null;
    }
    
    @Override
    public void destroy() {
        
        if (mAsRoot && mProcessName != null) {
            Command.runRootCommand("busybox killall -HUP " + mProcessName, null);
        }
        
        if (pid[0] != -1) {
            Os.hangupProcessGroup(pid[0]);
            pid[0] = -1;
        }
        if (mPipe != null) {
            Os.close(mPipe);
            mPipe = null;
        }
    }

    public int getExitCode() {
        return mExitCode;
    }
    
    private FileDescriptor createSubprocess(int[] processId, String cmd) {
        ArrayList<String> argList = parse(cmd);
        String arg0 = argList.get(0);
        String[] args = argList.toArray(new String[1]);
    
        return Os.createSubprocess(1, arg0, args, null,
            mCommand + "\nexit\n", processId);
    }
    
    private ArrayList<String> parse(String cmd) {
        final int PLAIN = 0;
        final int WHITESPACE = 1;
        final int INQUOTE = 2;
        int state = WHITESPACE;
        ArrayList<String> result = new ArrayList<String>();
        int cmdLen = cmd.length();
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < cmdLen; i++) {
            char c = cmd.charAt(i);
            if (state == PLAIN) {
                if (Character.isWhitespace(c)) {
                    result.add(builder.toString());
                    builder.delete(0, builder.length());
                    state = WHITESPACE;
                } else if (c == '"') {
                    state = INQUOTE;
                } else {
                    builder.append(c);
                }
            } else if (state == WHITESPACE) {
                if (Character.isWhitespace(c)) {
                    // do nothing
                } else if (c == '"') {
                    state = INQUOTE;
                } else {
                    state = PLAIN;
                    builder.append(c);
                }
            } else if (state == INQUOTE) {
                if (c == '\\') {
                    if (i + 1 < cmdLen) {
                        i += 1;
                        builder.append(cmd.charAt(i));
                    }
                } else if (c == '"') {
                    state = PLAIN;
                } else {
                    builder.append(c);
                }
            }
        }
        if (builder.length() > 0) {
            result.add(builder.toString());
        }
        return result;
    }
    
    private static String getShell() {
        if (mShell == null) {
            mShell = DEFAULT_SHELL;
            if (!new File(mShell).exists())
                mShell = "sh";
        }
        return mShell;
    }

    @Override
    public void run() {
        try {
            synchronized (mLock) {
                pid[0] = -1;
                if (this.mAsRoot) {
                    if (mRootShell == null) {
                        // switch between binaries
                        if (new File(Command.DEFAULT_ROOT).exists()) {
                            mRootShell = Command.DEFAULT_ROOT;
                        } else if (new File(Command.ALTERNATIVE_ROOT).exists()) {
                            mRootShell = Command.ALTERNATIVE_ROOT;
                        } else {
                            mRootShell = "su";
                        }
                    }
                    mPipe = createSubprocess(pid, mRootShell);
                } else {
                    mPipe = createSubprocess(pid, getShell());
                }
            }
            if (pid[0] != -1) {
                mExitCode = Os.waitFor(pid[0]);
            }
            if (mResult == null || mPipe == null) {
                return;
            }
            final InputStream stdout = new FileInputStream(mPipe);
            while (stdout.available() > 0) {
                final byte buf[] = new byte[8192];
                int read = stdout.read(buf);
                mResult.append(new String(buf, 0, read));
            }
        } catch (Exception ex) {
            Log.e(TAG, "Cannot execute command" + ex);
            if (mResult != null)
                mResult.append("\n").append(ex);
        } finally {
            if (mPipe != null) {
                Os.close(mPipe);
            }
            if (pid[0] != -1) {
                Os.hangupProcessGroup(pid[0]);
            }
        }
    }
}

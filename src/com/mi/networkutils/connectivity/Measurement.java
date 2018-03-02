package com.mi.networkutils.connectivity;

import java.util.concurrent.CountDownLatch;

import com.mi.networkutils.utils.Utils;


public class Measurement {
    private static final String SUCCEEDED = "SUCCEEDED";
    private static final String FAILED = "FAILED";

    // TODO: Refactor to make these private for better encapsulation.
    public String description = "";
    public long startTime;
    public long finishTime;
    public String result = "";
    public Thread thread;
    public CountDownLatch mCountDownLatch;
    
    public void recordSuccess(String msg) {
        maybeFixupTimes();
        result = SUCCEEDED + ": " + msg;
        if (mCountDownLatch != null) {
            mCountDownLatch.countDown();
        }
    }

    public void recordFailure(String msg) {
        maybeFixupTimes();
        result = FAILED + ": " + msg;
        if (mCountDownLatch != null) {
            mCountDownLatch.countDown();
        }
    }

    private void maybeFixupTimes() {
        // Allows the caller to just set success/failure and not worry
        // about also setting the correct finishing time.
        if (finishTime == 0) {
            finishTime = Utils.now();
        }

        // In cases where, for example, a failure has occurred before the
        // measurement even began, fixup the start time to reflect as much.
        if (startTime == 0) {
            startTime = finishTime;
        }
    }

    @Override
    public String toString() {
        return description + ": " + result + " (" + (finishTime - startTime)
                + "ms)";
    }
}

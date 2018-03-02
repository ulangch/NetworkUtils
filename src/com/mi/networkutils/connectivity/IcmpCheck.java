package com.mi.networkutils.connectivity;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.concurrent.CountDownLatch;

import com.mi.networkutils.utils.Utils;

import android.net.Network;
import android.system.ErrnoException;
import static android.system.OsConstants.AF_INET6;
import static android.system.OsConstants.IPPROTO_ICMPV6;
import static android.system.OsConstants.IPPROTO_ICMP;
import static android.system.OsConstants.SOCK_DGRAM;

public class IcmpCheck extends SimpleSocketCheck implements Runnable {
    private static final int TIMEOUT_SEND = 100;
    private static final int TIMEOUT_RECV = 300;
    private static final int ICMPV4_ECHO_REQUEST = 8;
    private static final int ICMPV6_ECHO_REQUEST = 128;
    private static final int PACKET_BUFSIZE = 512;
    private final int mProtocol;
    private final int mIcmpType;
    private final CountDownLatch mCountDownLatch;
    private final long mTimeoutMs;
    private final long mStartTime;
    private final long mDeadlineTime;
    
    /*
     *  ping -D -O target
     * [1465024345.830092] no answer yet for icmp_seq=1
     * [1465024346.838127] no answer yet for icmp_seq=2
     * */

    public IcmpCheck(InetAddress target, Measurement measurement, Network network, long timeoutMs) {
        super(target, measurement, network, null);

        if (mAddressFamily == AF_INET6) {
            mProtocol = IPPROTO_ICMPV6;
            mIcmpType = ICMPV6_ECHO_REQUEST;
            mMeasurement.description = "ICMPv6";
        } else {
            mProtocol = IPPROTO_ICMP;
            mIcmpType = ICMPV4_ECHO_REQUEST;
            mMeasurement.description = "ICMPv4";
        }

        mMeasurement.description += " dst{" + mTarget.getHostAddress() + "}";
        mCountDownLatch = new CountDownLatch(1);
        mMeasurement.mCountDownLatch = mCountDownLatch;
        mStartTime = Utils.now();
        mTimeoutMs = timeoutMs;
        mDeadlineTime = mStartTime + mTimeoutMs;
    }

    @Override
    public void run() {
        // Check if this measurement has already failed during setup.
        if (mMeasurement.finishTime > 0) {
            // If the measurement failed during construction it didn't
            // decrement the countdown latch; do so here.
            mCountDownLatch.countDown();
            return;
        }

        try {
            setupSocket(SOCK_DGRAM, mProtocol, TIMEOUT_SEND, TIMEOUT_RECV, 0);
        } catch (ErrnoException | IOException e) {
            mMeasurement.recordFailure(e.toString());
            return;
        }
        mMeasurement.description += " src{" + getSocketAddressString() + "}";

        // Build a trivial ICMP packet.
        final byte[] icmpPacket = {
                (byte) mIcmpType, 0, 0, 0, 0, 0, 0, 0  // ICMP header
        };

        int count = 0;
        mMeasurement.startTime = Utils.now();
        while (Utils.now() < mDeadlineTime - (TIMEOUT_SEND + TIMEOUT_RECV)) {
            count++;
            icmpPacket[icmpPacket.length - 1] = (byte) count;
            try {
                android.system.Os.write(mFileDescriptor, icmpPacket, 0, icmpPacket.length);
            } catch (ErrnoException | InterruptedIOException e) {
                mMeasurement.recordFailure(e.toString());
                break;
            }

            try {
                ByteBuffer reply = ByteBuffer.allocate(PACKET_BUFSIZE);
                android.system.Os.read(mFileDescriptor, reply);
                // TODO: send a few pings back to back to guesstimate packet loss.
                mMeasurement.recordSuccess("1/" + count);
                break;
            } catch (ErrnoException | InterruptedIOException e) {
                continue;
            }
        }
        if (mMeasurement.finishTime == 0) {
            mMeasurement.recordFailure("0/" + count);
        }
        close();
    }
}

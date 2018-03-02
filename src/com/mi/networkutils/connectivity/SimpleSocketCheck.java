package com.mi.networkutils.connectivity;

import static android.system.OsConstants.AF_INET;
import static android.system.OsConstants.AF_INET6;
import static android.system.OsConstants.SO_SNDTIMEO;
import static android.system.OsConstants.SO_RCVTIMEO;
import static android.system.OsConstants.SOL_SOCKET;

import java.io.Closeable;
import java.io.FileDescriptor;
import java.io.IOException;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.UnknownHostException;

import com.mi.networkutils.utils.Log;
import com.mi.networkutils.utils.Os;
import com.mi.networkutils.utils.StructTimeval;

import android.annotation.TargetApi;
import android.net.Network;
import android.system.ErrnoException;

public class SimpleSocketCheck implements Closeable {
    
    private final static String TAG = "SimpleSocketCheck";

    protected final InetAddress mTarget;
    protected final Network mNetwork;
    protected final int mAddressFamily;
    protected final Measurement mMeasurement;
    protected FileDescriptor mFileDescriptor;
    protected SocketAddress mSocketAddress;
    private final Integer mInterfaceIndex;

    protected SimpleSocketCheck(InetAddress target, Measurement measurement, Network network, Integer interfaceIndex) {
        mMeasurement = measurement;
        mNetwork = network;
        mInterfaceIndex = interfaceIndex;
        if (target instanceof Inet6Address) {
            Inet6Address targetWithScopeId = null;
            if (target.isLinkLocalAddress() && mInterfaceIndex != null) {
                try {
                    targetWithScopeId = Inet6Address.getByAddress(
                            null, target.getAddress(), mInterfaceIndex);
                } catch (UnknownHostException e) {
                    mMeasurement.recordFailure(e.toString());
                }
            }
            mTarget = (targetWithScopeId != null) ? targetWithScopeId : target;
            mAddressFamily = AF_INET6;
        } else {
            mTarget = target;
            mAddressFamily = AF_INET;
        }
    }

    @TargetApi(23)
    protected void setupSocket(
            int sockType, int protocol, long writeTimeout, long readTimeout, int dstPort)
            throws ErrnoException, IOException {
        mFileDescriptor = android.system.Os.socket(mAddressFamily, sockType, protocol);
        // Setting SNDTIMEO is purely for defensive purposes.
        
        Os.setsockoptTimeval(mFileDescriptor,
                SOL_SOCKET, SO_SNDTIMEO, StructTimeval.fromMillis(writeTimeout));
        Os.setsockoptTimeval(mFileDescriptor,
                SOL_SOCKET, SO_RCVTIMEO, StructTimeval.fromMillis(readTimeout));
        // TODO: Use IP_RECVERR/IPV6_RECVERR, pending OsContants availability.
        if (mNetwork != null ) {
            mNetwork.bindSocket(mFileDescriptor);
        }
        android.system.Os.connect(mFileDescriptor, mTarget, dstPort);
        mSocketAddress = android.system.Os.getsockname(mFileDescriptor);
    }

    protected String getSocketAddressString() {
        // The default toString() implementation is not the prettiest.
        InetSocketAddress inetSockAddr = (InetSocketAddress) mSocketAddress;
        InetAddress localAddr = inetSockAddr.getAddress();
        return String.format(
                (localAddr instanceof Inet6Address ? "[%s]:%d" : "%s:%d"),
                localAddr.getHostAddress(), inetSockAddr.getPort());
    }

    @Override
    public void close() {
        try {
            android.system.Os.close(mFileDescriptor);
        } catch (ErrnoException e) {
            // TODO Auto-generated catch block
            Log.e(TAG, "Close error " + e);
        }
    }

}

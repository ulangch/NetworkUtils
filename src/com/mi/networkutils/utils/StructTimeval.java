package com.mi.networkutils.utils;

import java.util.Objects;

public final class StructTimeval {

    /** Seconds. */
    public final long tv_sec;

    /** Microseconds. */
    public final long tv_usec;

    private StructTimeval(long tv_sec, long tv_usec) {
      this.tv_sec = tv_sec;
      this.tv_usec = tv_usec;
    }

    public static StructTimeval fromMillis(long millis) {
      long tv_sec = millis / 1000;
      long tv_usec = (millis - (tv_sec * 1000)) * 1000;
      return new StructTimeval(tv_sec, tv_usec);
    }

    public long toMillis() {
      return (tv_sec * 1000) + (tv_usec / 1000);
    }

    @Override public String toString() {
      return Objects.toString(this);
    }
}

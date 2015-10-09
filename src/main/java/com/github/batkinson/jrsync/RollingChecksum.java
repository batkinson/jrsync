package com.github.batkinson.jrsync;

import java.util.zip.Checksum;

/**
 * Implementation of a rolling checksum as described in https://rsync.samba.org/tech_report/node3.html.
 */
public class RollingChecksum implements Checksum {

    private static int POW2 = 16;
    private static int MOD_MASK = (1 << POW2) - 1;

    private byte[] buffer;
    private int a;
    private int b;
    private int i;
    private int size;

    public RollingChecksum(int window) {
        this.buffer = new byte[window];
        reset();
    }

    @Override
    public void update(int value) {
        value = value & 0xFF;
        if (size < buffer.length) {
            a = (a + value) & MOD_MASK;
            b = (b + ((buffer.length - (i + 1) + 1) * value)) & MOD_MASK;
            size++;
        } else {
            a = ((a - buffer[i]) + value) & MOD_MASK;
            b = (b - (buffer.length * buffer[i]) + a) & MOD_MASK;
        }
        buffer[i] = (byte) value;
        i = (i + 1) % buffer.length;
    }

    @Override
    public void update(byte[] b, int off, int len) {
        for (int i = off; i < off + len; i++) {
            update(b[i]);
        }
    }

    public void update(byte[] b) {
        update(b, 0, b.length);
    }

    public long getValue() {
        return a + (b << POW2);
    }

    public void reset() {
        a = 0;
        b = 0;
        i = 0;
        size = 0;
    }
}

package com.github.batkinson.sync;

/**
 * Implementation of a rolling checksum as described in https://rsync.samba.org/tech_report/node3.html.
 */
public class RollingChecksum {

    private static int POW2 = 16;
    private static int MOD_MASK = (1 << POW2) - 1;

    private int window;
    private int a;
    private int b;
    private int i;

    public RollingChecksum(int window) {
        this.window = window;
        reset();
    }

    public long update(byte prevVal, byte byteVal) {
        if (i <= window) {
            add(byteVal);
        } else {
            roll(prevVal, byteVal);
        }
        return getValue();
    }

    public void add(byte value) {

        if (i > window) {
            throw new IllegalStateException("checksum requires prev start item to roll forward");
        }

        a = (a + value) & MOD_MASK;
        b = (b + ((window - i + 1) * value)) & MOD_MASK;
        i++;
    }

    private void roll(byte prevStart, byte nextEnd) {
        a = ((a - prevStart) + nextEnd) & MOD_MASK;
        b = (b - (window * prevStart) + a) & MOD_MASK;
    }

    public long start(byte[] content) {
        return start(content, 0);
    }

    public long start(byte[] content, int offset) {

        if (i != 1) {
            throw new IllegalStateException("sum already started");
        }

        int stopOffset = offset + Math.min(window, content.length - offset);
        for (int j = offset; j < stopOffset; j++) {
            add(content[j]);
        }

        return getValue();
    }

    public long getValue() {
        return a + (b << POW2);
    }

    public void reset() {
        a = 0;
        b = 0;
        i = 1;
    }
}

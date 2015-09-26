package com.github.batkinson;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class RingBufferTest {

    byte[] items = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10};

    @Test
    public void wrapsProperly() {

        int capSize = 3;
        RingBuffer buf = new RingBuffer(capSize);

        for (int i = 0; i < capSize; i++) {
            buf.add(items[i]);
            assertEquals(i + 1, buf.length());
        }

        for (int i = capSize; i < items.length; i++) {
            buf.add(items[i]);
            assertEquals(items[i - capSize + 1], buf.get(0));
            assertEquals(buf.capacity(), buf.length());
        }
    }
}

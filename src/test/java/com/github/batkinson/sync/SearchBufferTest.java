package com.github.batkinson.sync;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class SearchBufferTest {

    byte[] items = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10};

    @Test
    public void wraps() {
        for (int blockSize = 1; blockSize < items.length; blockSize++) {
            SearchBuffer buf = new SearchBuffer(blockSize);
            // Verify length is what we would expect before wrapping
            for (int i = 0; i < blockSize; i++) {
                buf.add(items[i]);
                assertEquals(i + 1, buf.length());
            }
            // Add bytes that should cause wrapping, verify truncation
            for (int i = blockSize; i < items.length; i++) {
                buf.add(items[i]);
                assertEquals(items[i - blockSize], buf.get(0));
                assertEquals(buf.capacity(), buf.length());
            }
        }
    }

    @Test
    public void position() {
        int blockSize = 3;
        SearchBuffer buf = new SearchBuffer(blockSize);
        buf.add(Arrays.copyOfRange(items, 0, blockSize));
        assertEquals(0, buf.position());
        buf.add(items[blockSize]);
        assertEquals(1, buf.position());
        buf.add(items[blockSize + 1]);
        assertEquals(2, buf.position());
        buf.add(Arrays.copyOfRange(items, blockSize + 2, 2 * blockSize + 2));
        assertEquals(5, buf.position());
    }

    @Test
    public void checksum() {
        int blockSize = 3;
        SearchBuffer buf = new SearchBuffer(blockSize);
        byte[] block = Arrays.copyOfRange(items, 0, blockSize);
        buf.add(block);
        assertEquals(new RollingChecksum(blockSize).start(block), buf.checksum());
        for (int i = blockSize; i < items.length; i++) {
            buf.add(items[i]);
            assertEquals(new RollingChecksum(blockSize).start(Arrays.copyOfRange(items, i+1-blockSize,i+1)), buf.checksum());
        }
    }

    @Test
    public void getBlock() {
        int blockSize = 2;
        SearchBuffer buf = new SearchBuffer(blockSize);
        buf.add(items[0]);
        assertArrayEquals(Arrays.copyOfRange(items, 0, 1), buf.getBlock(new byte[1]));
        buf.add(items[1]);
        assertArrayEquals(Arrays.copyOfRange(items, 0, 2), buf.getBlock(new byte[2]));
        buf.add(items[2]);
        assertArrayEquals(Arrays.copyOfRange(items, 1, 3), buf.getBlock(new byte[2]));
        buf.add(items[3]);
        assertArrayEquals(Arrays.copyOfRange(items, 2, 4), buf.getBlock(new byte[2]));
        buf.add(items[4]);
        assertArrayEquals(Arrays.copyOfRange(items, 3, 5), buf.getBlock(new byte[2]));
    }
}

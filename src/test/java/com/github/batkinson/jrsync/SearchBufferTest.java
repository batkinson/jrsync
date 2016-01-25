package com.github.batkinson.jrsync;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static com.github.batkinson.jrsync.TestUtils.blockStr;
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
                assertEquals(items[i - blockSize + 1], buf.get(0));
                assertEquals(blockSize, buf.length());
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
        RollingChecksum checksum = new RollingChecksum(blockSize);
        checksum.update(block);
        assertEquals(checksum.getValue(), buf.checksum());
        for (int i = blockSize; i < items.length; i++) {
            checksum.reset();
            buf.add(items[i]);
            checksum.update(Arrays.copyOfRange(items, i + 1 - blockSize, i + 1));
            assertEquals(checksum.getValue(), buf.checksum());
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

    @Test
    public void blocksMatchWithInsert() {

        int blockSize = 2;
        SearchBuffer b1 = new SearchBuffer(blockSize), b2 = new SearchBuffer(blockSize);

        byte[][] target = {{1, 2}, {3, 4}, {5, 6}, {7, 8}, {9, 0}};
        byte[][] source = {{1, 2}, {3, 0}, {4}, {5, 6}, {7, 8}, {9, 0}};

        byte[] tmp = {0, 0};

        // compute expected target
        Set<String> expected = new HashSet<>();
        for (int i = 0; i < target.length; i++) {
            b1.add(target[i]);
            if (i != 1) {
                expected.add(blockStr(b1.checksum(), b1.getBlock(tmp)));
            }
        }

        // compute matches
        Set<String> matched = new HashSet<>();
        for (int i = 0; i < source.length; i++) {
            b2.add(source[i]);
            String bd = blockStr(b2.checksum(), b2.getBlock(tmp));
            if (expected.contains(bd)) {
                matched.add(bd);
            }
        }

        assertEquals(expected, matched);
    }
}

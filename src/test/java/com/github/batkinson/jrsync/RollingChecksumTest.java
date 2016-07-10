package com.github.batkinson.jrsync;

import org.junit.Test;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.URISyntaxException;

import static com.github.batkinson.jrsync.TestUtils.randomAccess;
import static com.github.batkinson.jrsync.TestUtils.testFile;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class RollingChecksumTest {

    byte[] content = "Roll out. Keep rolling, rolling, rolling. Rolling in the deep.".getBytes();

    @Test
    public void checksumOfOneByte() {
        RollingChecksum small = new RollingChecksum(1);
        small.update(content[0]);
        assertEquals(5374034, small.getValue());
    }

    @Test
    public void checksumOfTwoByes() {
        RollingChecksum small = new RollingChecksum(2);
        small.update(content, 0, 2);
        assertEquals(18022593, small.getValue());
    }

    @Test
    public void checksumRollsOnce() {
        RollingChecksum direct = new RollingChecksum(1);
        RollingChecksum rolled = new RollingChecksum(1);
        rolled.update(content[0]);
        direct.update(content, 0, 2);
        assertEquals(5374034, rolled.getValue());
        assertEquals(7274607, direct.getValue());
        rolled.update(content[1]);
        assertEquals(7274607, rolled.getValue());
    }

    @Test
    public void checksumRollsMultiple() {
        RollingChecksum rolled = new RollingChecksum(1);
        for (int i = 0; i < content.length; i++) {
            RollingChecksum direct = new RollingChecksum(1);
            direct.update(content, 0, i + 1);
            rolled.update(content[i]);
            assertEquals(direct.getValue(), rolled.getValue());
        }
    }

    @Test
    public void checksumRollsMultipleWithReset() {
        RollingChecksum rolled = new RollingChecksum(1);
        RollingChecksum direct = new RollingChecksum(1);
        for (int i = 0; i < content.length; i++) {
            direct.reset();
            direct.update(content, 0, i + 1);
            rolled.update(content[i]);
            assertEquals(direct.getValue(), rolled.getValue());
        }
    }

    @Test
    public void testRollsWithLargerWindows() {
        for (int window = 2; window < content.length; window++) {
            RollingChecksum rolled = new RollingChecksum(window);
            for (int i = 0; i < content.length - window; i++) {
                RollingChecksum direct = new RollingChecksum(window);
                direct.update(content, i, window);
                if (i == 0) {
                    rolled.update(content, 0, window);
                } else {
                    rolled.update(content[i + window - 1]);
                }
                assertEquals(direct.getValue(), rolled.getValue());
            }
        }
    }

    @Test
    public void recurrenceHolds() throws URISyntaxException, IOException {

        int blockSize = 1;
        RandomAccessFile testFile = randomAccess(testFile("guitar.jpg"));
        RollingChecksum rollSum = new RollingChecksum(blockSize), blockSum = new RollingChecksum(blockSize);

        // Handle the first block directly
        byte[] b = new byte[blockSize];
        testFile.readFully(b);
        rollSum.update(b);
        blockSum.update(b);
        assertEquals("first block checksum should match", blockSum.getValue(), rollSum.getValue());

        // Roll through the file, confirm entire block sum matches the rolling sum for each offset
        for (long bs = 1; testFile.getFilePointer() < testFile.length(); bs++) {

            byte nextByte = testFile.readByte();
            long newPos = testFile.getFilePointer();
            testFile.seek(bs);
            testFile.readFully(b);
            assertEquals("last byte of block read should match next sequential byte", nextByte, b[b.length - 1]);

            rollSum.update(nextByte);
            blockSum.reset();
            blockSum.update(b);
            assertEquals("recurrence relationship should hold", blockSum.getValue(), rollSum.getValue());
            testFile.seek(newPos);
        }
    }

}

package com.github.batkinson.jrsync;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class RollingChecksumTest {

    byte[] content = "Roll out. Keep rolling, rolling, rolling. Rolling in the deep.".getBytes();

    @Test
    public void checksumOfOneByte() {
        RollingChecksum small = new RollingChecksum(1);
        assertEquals(5374034, small.start(content));
    }

    @Test
    public void checksumOfTwoByes() {
        RollingChecksum small = new RollingChecksum(2);
        assertEquals(18022593, small.start(content));
    }

    @Test
    public void checksumRollsOnce() {
        RollingChecksum direct = new RollingChecksum(1);
        RollingChecksum rolled = new RollingChecksum(1);
        assertEquals(5374034, rolled.start(content));
        assertEquals(7274607, direct.start(content, 1));
        assertEquals(7274607, rolled.update(content[0], content[1]));
    }

    @Test
    public void checksumRollsMultiple() {
        RollingChecksum rolled = new RollingChecksum(1);
        for (int i = 0; i < content.length; i++) {
            RollingChecksum direct = new RollingChecksum(1);
            if (i == 0) {
                assertEquals(direct.start(content), rolled.start(content));
            } else {
                assertEquals(direct.start(content, i), rolled.update(content[i - 1], content[i]));
            }
        }
    }

    @Test
    public void checksumRollsMultipleWithReset() {
        RollingChecksum rolled = new RollingChecksum(1);
        RollingChecksum direct = new RollingChecksum(1);
        for (int i = 0; i < content.length; i++) {
            direct.reset();
            if (i == 0) {
                assertEquals(direct.start(content), rolled.start(content));
            } else {
                assertEquals(direct.start(content, i), rolled.update(content[i - 1], content[i]));
            }
        }
    }

    @Test
    public void testRollsWithLargerWindows() {
        for (int w = 2; w < content.length; w++) {
            RollingChecksum rolled = new RollingChecksum(w);
            for (int i = 0; i < (content.length - w); i++) {
                RollingChecksum direct = new RollingChecksum(w);
                if (i == 0) {
                    assertEquals(direct.start(content), rolled.start(content));
                } else {
                    assertEquals(direct.start(content, i), rolled.update(content[i - 1], content[i + w - 1]));
                }
            }
        }
    }
}

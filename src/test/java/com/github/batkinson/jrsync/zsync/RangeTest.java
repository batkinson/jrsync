package com.github.batkinson.jrsync.zsync;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static com.github.batkinson.jrsync.zsync.Range.base10Digits;
import static com.github.batkinson.jrsync.zsync.Range.estimateStringLength;
import static com.github.batkinson.jrsync.zsync.Range.toRangeString;
import static org.junit.Assert.assertEquals;

public class RangeTest {

    @Test
    public void testBase10Digits() {
        assertEquals(1, base10Digits(0));
        for (long i = 1; i >= 1 && i <= Long.MAX_VALUE; i *= 10) {
            assertEquals(String.valueOf(i).length(), base10Digits(i));
        }
    }

    @Test
    public void estimatedRangeStringLength() {
        int ranges = (int)((Math.random() * 100) + 1);
        List<Range> rangeList = new ArrayList<>();
        for (int i=0; i<ranges; i++) {
            rangeList.add(new Range((long)(Math.random() * Long.MAX_VALUE), (long)(Math.random() * Long.MAX_VALUE)));
            assertEquals(toRangeString(rangeList).length(), estimateStringLength(rangeList));
        }
    }
}

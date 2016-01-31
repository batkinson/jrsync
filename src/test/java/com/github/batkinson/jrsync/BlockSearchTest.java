package com.github.batkinson.jrsync;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.URISyntaxException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

import static com.github.batkinson.jrsync.TestUtils.computeBlocks;
import static com.github.batkinson.jrsync.TestUtils.computeHash;
import static com.github.batkinson.jrsync.TestUtils.randomAccess;
import static com.github.batkinson.jrsync.TestUtils.testFile;
import static com.github.batkinson.jrsync.zsync.IOUtil.close;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class BlockSearchTest {

    private static final String MD5 = "MD5";

    File outputDir;

    RandomAccessFile file1;
    RandomAccessFile file2;
    RandomAccessFile violin;
    RandomAccessFile guitar;
    RandomAccessFile file6;

    @Before
    public void setup() throws URISyntaxException, FileNotFoundException {

        outputDir = new File(System.getProperty("outputDir"), "patched-files");
        outputDir.mkdirs();

        file1 = randomAccess(testFile("file1.txt"));
        file2 = randomAccess(testFile("file2.txt"));
        violin = randomAccess(testFile("violin.jpg"));
        guitar = randomAccess(testFile("guitar.jpg"));
        file6 = randomAccess(testFile("file6.txt"));
    }

    @After
    public void teardown() {
        close(file1, file2, violin, guitar, file6);
    }

    @Test
    public void rsyncDifferentBlockSizes() throws IOException, NoSuchAlgorithmException {
        for (int blockSize : Arrays.asList(1, 13, (int) file1.length(), (int) file2.length(), 1100)) {
            assertSearch(blockSize, "rdbs", file1, file2, false);
        }
    }

    @Test
    public void rsyncBinaryBigToSmall() throws IOException, NoSuchAlgorithmException {
        assertSearch(191, "rbbts", violin, guitar, false);
    }

    @Test
    public void rsyncBinarySmallToBig() throws IOException, NoSuchAlgorithmException {
        assertSearch(191, "rbstb", guitar, violin, false);
    }

    @Test
    public void rsyncBinaryIdentical() throws IOException, NoSuchAlgorithmException {
        assertSearch(191, "rbi", guitar, guitar, false);
    }

    @Test
    public void zsyncDifferentBlockSizes() throws IOException, NoSuchAlgorithmException {
        for (int blockSize : Arrays.asList(1, 13, (int) file1.length(), (int) file2.length(), 1100)) {
            assertSearch(blockSize, "zdbs", file1, file2, true);
        }
    }

    @Test
    public void zsyncBinaryBigToSmall() throws IOException, NoSuchAlgorithmException {
        assertSearch(191, "zbbts", violin, guitar, true);
    }

    @Test
    public void zsyncBinarySmallToBig() throws IOException, NoSuchAlgorithmException {
        assertSearch(191, "zbstb", guitar, violin, true);
    }

    @Test
    public void zsyncBinaryIdentical() throws IOException, NoSuchAlgorithmException {
        assertSearch(191, "zbi", guitar, guitar, true);
    }

    @Test
    public void zsyncPoisonByte() throws IOException, NoSuchAlgorithmException {
        assertSearch(10, "zpb", file6, file1, true);
    }


    private void assertSearch(int blockSize, String name, RandomAccessFile basis, RandomAccessFile target, boolean reverse) throws IOException, NoSuchAlgorithmException {
        final BlockSearch search = new BlockSearch(computeBlocks(reverse ? target : basis, blockSize, MD5), blockSize);
        File tempFile = File.createTempFile(name + "-" + blockSize + "-", "", outputDir);
        FilePatcher patcher = new FilePatcher(blockSize, basis, target, tempFile, reverse);
        if (reverse) {
            search.zsyncSearch(basis, target.length(), MD5, patcher);
        } else {
            search.rsyncSearch(target, MD5, patcher);
        }
        assertArrayEquals(computeHash(target), computeHash(patcher.getDest()));
        assertEquals(target.length(), patcher.getBytesMatched() + patcher.getBytesNeeded());
    }
}

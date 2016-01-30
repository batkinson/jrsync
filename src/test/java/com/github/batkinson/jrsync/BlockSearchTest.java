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
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class BlockSearchTest {

    private static final String MD5 = "MD5";

    File outputDir;

    RandomAccessFile file1;
    RandomAccessFile file2;
    RandomAccessFile violin;
    RandomAccessFile guitar;

    @Before
    public void setup() throws URISyntaxException, FileNotFoundException {

        outputDir = new File(System.getProperty("outputDir"), "patched-files");
        outputDir.mkdirs();

        file1 = randomAccess(testFile("file1.txt"));
        file2 = randomAccess(testFile("file2.txt"));
        violin = randomAccess(testFile("violin.jpg"));
        guitar = randomAccess(testFile("guitar.jpg"));
    }

    @After
    public void teardown() {
        for (RandomAccessFile f : Arrays.asList(file1, file2, violin, guitar)) {
            try {
                f.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Test
    public void rsyncDifferentBlockSizes() throws IOException, NoSuchAlgorithmException {
        for (int blockSize : Arrays.asList(1, 13, (int) file1.length(), (int) file2.length(), 1100)) {
            assertRsync(blockSize, "rdbs", file1, file2);
        }
    }

    @Test
    public void rsyncBinaryBigToSmall() throws IOException, NoSuchAlgorithmException {
        assertRsync(191, "rbbts", violin, guitar);
    }

    @Test
    public void rsyncBinarySmallToBig() throws IOException, NoSuchAlgorithmException {
        assertRsync(191, "rbstb", guitar, violin);
    }

    @Test
    public void rsyncBinaryIdentical() throws IOException, NoSuchAlgorithmException {
        assertRsync(191, "rbi", guitar, guitar);
    }

    private void assertRsync(int blockSize, String name, RandomAccessFile basis, RandomAccessFile target)
            throws IOException, NoSuchAlgorithmException {
        final BlockSearch search = new BlockSearch(computeBlocks(basis, blockSize, MD5), blockSize);
        File tempFile = File.createTempFile(name + "-" + blockSize + "-", "", outputDir);
        FilePatcher patcher = new FilePatcher(blockSize, basis, target, tempFile);
        search.rsyncSearch(target, MD5, patcher);
        assertArrayEquals(computeHash(target), computeHash(patcher.getDest()));
        assertEquals(target.length(), patcher.getBytesMatched() + patcher.getBytesNeeded());
    }

    @Test
    public void zsyncDifferentBlockSizes() throws IOException, NoSuchAlgorithmException {
        for (int blockSize : Arrays.asList(1, 13, (int) file1.length(), (int) file2.length(), 1100)) {
            assertRsync(blockSize, "zdbs", file1, file2);
        }
    }

    @Test
    public void zsyncBinaryBigToSmall() throws IOException, NoSuchAlgorithmException {
        assertZsync(191, "zbbts", violin, guitar);
    }

    @Test
    public void zsyncBinarySmallToBig() throws IOException, NoSuchAlgorithmException {
        assertZsync(191, "zbstb", guitar, violin);
    }

    @Test
    public void zsyncBinaryIdentical() throws IOException, NoSuchAlgorithmException {
        assertZsync(191, "zbi", guitar, guitar);
    }

    private void assertZsync(int blockSize, String name, RandomAccessFile basis, RandomAccessFile target)
            throws IOException, NoSuchAlgorithmException {
        final BlockSearch search = new BlockSearch(computeBlocks(target, blockSize, MD5), blockSize);
        File tempFile = File.createTempFile(name + "-" + blockSize + "-", "", outputDir);
        FilePatcher patcher = new FilePatcher(blockSize, basis, target, tempFile);
        search.zsyncSearch(basis, target.length(), MD5, patcher);
        assertArrayEquals(computeHash(target), computeHash(patcher.getDest()));
        assertEquals(target.length(), patcher.getBytesMatched() + patcher.getBytesNeeded());
    }
}

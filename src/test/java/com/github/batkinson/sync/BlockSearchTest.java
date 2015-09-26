package com.github.batkinson.sync;

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

import static com.github.batkinson.sync.BlockDesc.describe;
import static com.github.batkinson.sync.TestUtils.computeHash;
import static com.github.batkinson.sync.TestUtils.fileContent;
import static com.github.batkinson.sync.TestUtils.openFile;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class BlockSearchTest {

    private static final String MD5 = "MD5";

    File outputDir;

    RandomAccessFile basis;
    RandomAccessFile target;
    RandomAccessFile violin;
    RandomAccessFile guitar;

    @Before
    public void setup() throws URISyntaxException, FileNotFoundException {

        outputDir = new File(System.getProperty("outputDir"), "patched-files");
        outputDir.mkdirs();

        basis = openFile("file1.txt");
        target = openFile("file2.txt");
        violin = openFile("violin.jpg");
        guitar = openFile("guitar.jpg");
    }

    @After
    public void teardown() {
        for (RandomAccessFile f : Arrays.asList(basis, target)) {
            try {
                f.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Test
    public void execute() throws IOException, NoSuchAlgorithmException {
        for (int blockSize = 1; blockSize <= target.length(); blockSize++) {
            final BlockSearch search = new BlockSearch(describe(basis, blockSize, MD5), blockSize);
            FilePatcher patcher = new FilePatcher("bstfile", blockSize, basis, target, outputDir);
            search.execute(target, MD5, patcher);
            assertArrayEquals(computeHash(target), computeHash(patcher.getDest()));
            assertEquals(target.length(), patcher.getBytesMatched() + patcher.getBytesNeeded());
        }
    }

    @Test
    public void makeViolinIntoGuitar() throws IOException, NoSuchAlgorithmException {
        for (int blockSize : Arrays.asList(128)) {
            final BlockSearch search = new BlockSearch(describe(violin, blockSize, MD5), blockSize);
            FilePatcher patcher = new FilePatcher("bstvig", blockSize, violin, guitar, outputDir);
            search.execute(guitar, MD5, patcher);
            assertArrayEquals(computeHash(guitar), computeHash(patcher.getDest()));
            assertEquals(guitar.length(), patcher.getBytesMatched() + patcher.getBytesNeeded());
        }
    }

    @Test
    public void makeGuitarIntoViolin() throws IOException, NoSuchAlgorithmException {
        for (int blockSize : Arrays.asList(128)) {
            final BlockSearch search = new BlockSearch(describe(guitar, blockSize, MD5), blockSize);
            FilePatcher patcher = new FilePatcher("bstgiv", blockSize, guitar, violin, outputDir);
            search.execute(violin, MD5, patcher);
            assertArrayEquals(computeHash(violin), computeHash(patcher.getDest()));
            assertEquals(violin.length(), patcher.getBytesMatched() + patcher.getBytesNeeded());
        }
    }

    @Test
    public void makeGuitarIntoGuitar() throws IOException, NoSuchAlgorithmException {
        for (int blockSize : Arrays.asList(128)) {
            final BlockSearch search = new BlockSearch(describe(guitar, blockSize, MD5), blockSize);
            FilePatcher patcher = new FilePatcher("bstgig", blockSize, guitar, guitar, outputDir);
            search.execute(guitar, MD5, patcher);
            assertArrayEquals(computeHash(guitar), computeHash(patcher.getDest()));
            assertEquals(guitar.length(), patcher.getBytesMatched() + patcher.getBytesNeeded());
        }
    }
}

/**
 * Patches a file together based on block search results. Precursor to actual patch code, but
 * doesn't handle large content sections.
 */
class FilePatcher implements SearchHandler {

    private int blockSize;
    private RandomAccessFile dest;
    private RandomAccessFile basis;
    private RandomAccessFile target;
    private long bytesMatched = 0;
    private long bytesNeeded = 0;

    FilePatcher(String name, int blockSize, RandomAccessFile basis, RandomAccessFile target, File dest) throws IOException {
        this.blockSize = blockSize;
        this.dest = new RandomAccessFile(File.createTempFile(name, "", dest), "rw");
        this.basis = basis;
        this.target = target;
    }

    public RandomAccessFile getDest() {
        return dest;
    }

    @Override
    public void blockMatch(long offset, long blockIndex) throws IOException {
        long start = blockIndex * blockSize, end = start + blockSize, size = end - start;
        bytesMatched += size;
        dest.seek(offset);
        dest.write(fileContent(basis, start, end));
    }

    @Override
    public void needsContent(long startOffset, long endOffset) throws IOException {
        bytesNeeded += endOffset - startOffset;
        dest.seek(startOffset);
        dest.write(fileContent(target, startOffset, endOffset));
    }

    public long getBytesMatched() {
        return bytesMatched;
    }

    public long getBytesNeeded() {
        return bytesNeeded;
    }
}

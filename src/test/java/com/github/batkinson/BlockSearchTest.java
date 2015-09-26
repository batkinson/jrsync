package com.github.batkinson;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.URISyntaxException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

import static com.github.batkinson.BlockDesc.describe;
import static com.github.batkinson.TestUtils.openFile;
import static org.junit.Assert.assertArrayEquals;

public class BlockSearchTest {

    private static final String MD5 = "MD5";

    RandomAccessFile basis;
    RandomAccessFile target;
    RandomAccessFile zeros;

    @Before
    public void setup() throws URISyntaxException, FileNotFoundException {
        basis = openFile("file1.txt");
        target = openFile("file2.txt");
        zeros = openFile("zeros.txt");
    }

    @After
    public void teardown() {
        for (RandomAccessFile f : Arrays.asList(basis, target, zeros)) {
            try {
                f.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private byte[] fileContent(RandomAccessFile f, long start, long end) throws IOException {
        long origPos = f.getFilePointer();
        try {
            f.seek(start);
            byte[] bytes = new byte[(int) (end - start)];
            f.readFully(bytes);
            return bytes;
        } finally {
            f.seek(origPos);
        }
    }

    /**
     * Patches a file together based on block search results. Precursor to actual patch code, but
     * doesn't handle large content sections.
     */
    class FilePatcher implements SearchHandler {

        private int blockSize;
        private RandomAccessFile dest;

        FilePatcher(String name, int blockSize) throws IOException {
            this.blockSize = blockSize;
            this.dest = new RandomAccessFile(File.createTempFile(name, "", null), "rw");
        }

        public RandomAccessFile getDest() {
            return dest;
        }

        @Override
        public void blockMatch(long offset, long blockIndex) throws IOException {
            dest.seek(offset);
            dest.write(fileContent(basis, blockIndex * blockSize, (blockIndex * blockSize) + blockSize));
        }

        @Override
        public void needsContent(long startOffset, long endOffset) throws IOException {
            dest.seek(startOffset);
            dest.write(fileContent(target, startOffset, endOffset));
        }
    }

    /**
     * Not to be used with large files, allocates the storage in memory.
     */
    private byte[] computeHash(RandomAccessFile f) throws IOException, NoSuchAlgorithmException {
        f.seek(0);
        byte[] content = new byte[(int)f.length()];
        f.readFully(content);
        return MessageDigest.getInstance("SHA1").digest(content);
    }

    @Test
    public void execute() throws IOException, NoSuchAlgorithmException {
        for (int blockSize = 1; blockSize <= target.length(); blockSize++) {
            final BlockSearch search = new BlockSearch(describe(basis, blockSize, MD5), blockSize);
            FilePatcher patcher = new FilePatcher("executetest", blockSize);
            search.execute(target, MD5, patcher);
            assertArrayEquals(computeHash(target), computeHash(patcher.getDest()));
        }
    }
}

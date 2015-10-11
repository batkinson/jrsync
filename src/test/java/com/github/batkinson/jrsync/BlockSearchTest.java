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

import static com.github.batkinson.jrsync.BlockDesc.describe;
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
    public void execute() throws IOException, NoSuchAlgorithmException {
        for (int blockSize : Arrays.asList(1, 13, (int) file1.length(), (int) file2.length(), 1100)) {
            assertPatch(blockSize, "bstfile", file1, file2);
        }
    }

    @Test
    public void makeViolinIntoGuitar() throws IOException, NoSuchAlgorithmException {
        assertPatch(191, "bstvig", violin, guitar);
    }

    @Test
    public void makeGuitarIntoViolin() throws IOException, NoSuchAlgorithmException {
        assertPatch(191, "bstgiv", guitar, violin);
    }

    @Test
    public void makeGuitarIntoGuitar() throws IOException, NoSuchAlgorithmException {
        assertPatch(191, "bstgig", guitar, guitar);
    }

    private void assertPatch(int blockSize, String name, RandomAccessFile basis, RandomAccessFile target)
            throws IOException, NoSuchAlgorithmException {
        final BlockSearch search = new BlockSearch(describe(basis, blockSize, MD5), blockSize);
        FilePatcher patcher = new FilePatcher(name + "-" + blockSize + "-", blockSize, basis, target, outputDir);
        search.execute(target, MD5, patcher);
        assertArrayEquals(computeHash(target), computeHash(patcher.getDest()));
        assertEquals(target.length(), patcher.getBytesMatched() + patcher.getBytesNeeded());
    }
}


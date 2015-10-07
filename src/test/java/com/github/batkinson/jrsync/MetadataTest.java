package com.github.batkinson.jrsync;

import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.URISyntaxException;
import java.security.NoSuchAlgorithmException;

import static com.github.batkinson.jrsync.TestUtils.computeHash;
import static com.github.batkinson.jrsync.TestUtils.testFile;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class MetadataTest {

    File outputDir;

    @Before
    public void setUp() {
        outputDir = new File(System.getProperty("outputDir"), "metadata-scratch");
        outputDir.mkdirs();
    }

    @Test
    public void simpleWrite() throws IOException, URISyntaxException, NoSuchAlgorithmException {
        String sourceName = "";
        RandomAccessFile output = new RandomAccessFile(
                File.createTempFile("simple", "", outputDir), "rw");
        RandomAccessFile in = testFile("file1.txt");
        Metadata.write(sourceName, (int) in.length(), "MD5", "MD5", in, output);
        RandomAccessFile ref = testFile("file1.jrsmd");
        assertEquals(ref.length(), output.length());
        assertArrayEquals(computeHash(ref), computeHash(output));
    }
}

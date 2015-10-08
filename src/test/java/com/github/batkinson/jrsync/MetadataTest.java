package com.github.batkinson.jrsync;

import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.URISyntaxException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import static com.github.batkinson.jrsync.TestUtils.computeChecksum;
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

    @Test
    public void multiBlockWriteWithSource() throws IOException, URISyntaxException, NoSuchAlgorithmException {
        String sourceName = "nowhere";
        RandomAccessFile output = new RandomAccessFile(
                File.createTempFile("multiBlock", "", outputDir), "rw");
        RandomAccessFile in = testFile("file2.txt");
        Metadata.write(sourceName, 100, "SHA1", "MD5", in, output);
        RandomAccessFile ref = testFile("file2.jrsmd");
        assertEquals(ref.length(), output.length());
        assertArrayEquals(computeHash(ref), computeHash(output));
    }

    @Test
    public void readSimpleMetadata() throws IOException, URISyntaxException, NoSuchAlgorithmException {
        RandomAccessFile file1 = testFile("file1.txt");
        RandomAccessFile file1meta = testFile("file1.jrsmd");

        Metadata metadata = Metadata.read(file1meta);

        assertEquals(file1.length(), metadata.getFileSize());
        assertEquals(MessageDigest.getInstance(metadata.getFileHashAlg()).getDigestLength(), metadata.getFileHash().length);
        assertArrayEquals(computeHash(file1, metadata.getFileHashAlg()), metadata.getFileHash());
        assertEquals("", metadata.getContentSource());
        assertEquals("MD5", metadata.getBlockHashAlg());
        assertEquals(file1.length(), metadata.getBlockSize());
        assertEquals(1, metadata.getBlockDescs().size());

        BlockDesc bd = metadata.getBlockDescs().get(0);
        assertEquals(0, bd.blockIndex);
        assertEquals(computeChecksum(file1, metadata.getBlockSize()), bd.weakChecksum);
        assertArrayEquals(computeHash(file1, metadata.getBlockHashAlg()), bd.cryptoHash);
    }

    @Test
    public void readMultiBlockMetadata() throws IOException, URISyntaxException, NoSuchAlgorithmException {
        RandomAccessFile file2 = testFile("file2.txt");
        RandomAccessFile file2meta = testFile("file2.jrsmd");

        Metadata metadata = Metadata.read(file2meta);

        assertEquals(file2.length(), metadata.getFileSize());
        assertEquals(MessageDigest.getInstance(metadata.getFileHashAlg()).getDigestLength(), metadata.getFileHash().length);
        assertArrayEquals(computeHash(file2, metadata.getFileHashAlg()), metadata.getFileHash());
        assertEquals("nowhere", metadata.getContentSource());
        assertEquals("MD5", metadata.getBlockHashAlg());
        assertEquals(100, metadata.getBlockSize());
        int expectedBlocks = (int)file2.length() / metadata.getBlockSize();
        assertEquals(expectedBlocks, metadata.getBlockDescs().size());

        for (int i=0; i<metadata.getBlockDescs().size(); i++) {
            BlockDesc bd = metadata.getBlockDescs().get(i);
            assertEquals(i, bd.blockIndex);
            assertEquals(computeChecksum(file2, metadata.getBlockSize(), i * metadata.getBlockSize()), bd.weakChecksum);
            assertArrayEquals(computeHash(file2, metadata.getBlockHashAlg(), metadata.getBlockSize(), i * metadata.getBlockSize()), bd.cryptoHash);
        }
    }
}

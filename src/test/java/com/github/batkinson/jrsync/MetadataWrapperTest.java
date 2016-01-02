package com.github.batkinson.jrsync;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.security.NoSuchAlgorithmException;

import static com.github.batkinson.jrsync.TestUtils.computeHash;
import static com.github.batkinson.jrsync.TestUtils.inputStream;
import static com.github.batkinson.jrsync.TestUtils.randomAccess;
import static com.github.batkinson.jrsync.TestUtils.testFile;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class MetadataWrapperTest {

    private static final int BLOCK_SIZE = 100;
    private static final String SOURCE = "nowhere";
    private static final String FILE_ALG = "SHA1";
    private static final String BLOCK_ALG = "MD5";

    private static File outputDir;

    private static File metadataFile;
    private static byte[] metadataHash;

    private static File contentFile;
    private static byte[] contentHash;

    File contentOutput;

    @BeforeClass
    public static void setupOnce() throws URISyntaxException, IOException, NoSuchAlgorithmException {
        contentFile = testFile("file2.txt");
        contentHash = computeHash(randomAccess(contentFile));
        metadataFile = testFile("file2.jrsmd");
        metadataHash = computeHash(randomAccess(metadataFile));
        outputDir = new File(System.getProperty("outputDir"), "metadata-wrapper-scratch");
        outputDir.mkdirs();
    }

    @Before
    public void setUp() throws IOException {
        contentOutput = File.createTempFile("mwt", "txt", outputDir);
    }

    @Test
    public void testInputWrapperDefaultDir() throws IOException, NoSuchAlgorithmException, URISyntaxException {
        MetadataInputWrapper in = new MetadataInputWrapper(new BufferedInputStream(inputStream(contentFile)), SOURCE, BLOCK_SIZE, FILE_ALG, BLOCK_ALG);
        OutputStream out = new BufferedOutputStream(new FileOutputStream(contentOutput));
        copy(in, out);
        assertResult(in.getMetadataFile(), contentOutput);
    }

    @Test
    public void testInputWrapperSpecifiedDir() throws IOException, NoSuchAlgorithmException, URISyntaxException {
        MetadataInputWrapper in = new MetadataInputWrapper(new BufferedInputStream(inputStream(contentFile)), SOURCE, BLOCK_SIZE, FILE_ALG, BLOCK_ALG, outputDir);
        OutputStream out = new BufferedOutputStream(new FileOutputStream(contentOutput));
        copy(in, out);
        File metadataFile = in.getMetadataFile();
        assertResult(metadataFile, contentOutput);
        assertEquals(outputDir, metadataFile.getParentFile());
    }

    @Test
    public void testOutputWrapperDefaultDir() throws IOException, NoSuchAlgorithmException, URISyntaxException {
        MetadataOutputWrapper out = new MetadataOutputWrapper(new BufferedOutputStream(new FileOutputStream(contentOutput)), SOURCE, BLOCK_SIZE, FILE_ALG, BLOCK_ALG);
        InputStream in = new BufferedInputStream(inputStream(contentFile));
        copy(in, out);
        assertResult(out.getMetadataFile(), contentOutput);
    }

    @Test
    public void testOutputWrapperSpecifiedDir() throws IOException, NoSuchAlgorithmException, URISyntaxException {
        MetadataOutputWrapper out = new MetadataOutputWrapper(new BufferedOutputStream(new FileOutputStream(contentOutput)), SOURCE, BLOCK_SIZE, FILE_ALG, BLOCK_ALG, outputDir);
        InputStream in = new BufferedInputStream(inputStream(contentFile));
        copy(in, out);
        File metadataFile = out.getMetadataFile();
        assertResult(metadataFile, contentOutput);
        assertEquals(outputDir, metadataFile.getParentFile());
    }

    private void copy(InputStream in, OutputStream out) throws IOException {
        int read;
        while ((read = in.read()) >= 0) {
            out.write(read);
        }
        in.close();
        out.close();
    }

    private void assertResult(File metadata, File contents) throws NoSuchAlgorithmException, IOException, URISyntaxException {
        assertContents(metadataHash, metadata);
        assertContents(contentHash, contents);
    }

    private void assertContents(byte[] hash, File file) throws IOException, URISyntaxException, NoSuchAlgorithmException {
        assertNotNull(file);
        assertTrue(file.exists());
        assertArrayEquals(hash, computeHash(randomAccess(file)));
    }
}

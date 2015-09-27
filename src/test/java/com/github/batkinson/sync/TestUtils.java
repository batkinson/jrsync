package com.github.batkinson.sync;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class TestUtils {

    public static final int BLOCK_SIZE = 4096;

    /**
     * Opens the named file from the first match in same package as this class. Used for test files.
     */
    public static RandomAccessFile openFile(String path)
            throws URISyntaxException, FileNotFoundException {
        URL url = BlockDescTest.class.getResource(path);
        File f = new File(url.toURI());
        return new RandomAccessFile(f, "r");
    }

    /**
     * Gets the specified section of the file as a byte array, then returns the file to its previous
     * position.
     */
    public static void writeContent(RandomAccessFile source, long start, long end, RandomAccessFile dest)
            throws IOException {
        long origPos = source.getFilePointer();
        try {
            source.seek(start);
            int read, scratchSize = BLOCK_SIZE, remaining = (int) (end - start);
            byte[] scratch = new byte[scratchSize];
            while (remaining > 0 && (read = source.read(scratch, 0, Math.min(scratchSize, remaining))) >= 0) {
                dest.write(scratch, 0, read);
                remaining -= read;
            }
        } finally {
            source.seek(origPos);
        }
    }

    /**
     * Used to verify integrity of copied files, leaving position where it was when it was first
     * invoked. Handles large files efficiently.
     */
    public static byte[] computeHash(RandomAccessFile f)
            throws IOException, NoSuchAlgorithmException {
        long origPos = f.getFilePointer();
        try {
            f.seek(0);
            int read;
            MessageDigest digest = MessageDigest.getInstance("SHA1");
            byte[] scratch = new byte[BLOCK_SIZE];
            while ((read = f.read(scratch)) >= 0) {
                digest.update(scratch, 0, read);
            }
            return digest.digest();
        } finally {
            f.seek(origPos);
        }
    }
}

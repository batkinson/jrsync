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

    /**
     * Opens the named file from the first match in same package as this class. Used for test files.
     */
    public static RandomAccessFile openFile(String path) throws URISyntaxException, FileNotFoundException {
        URL url = BlockDescTest.class.getResource(path);
        File f = new File(url.toURI());
        return new RandomAccessFile(f, "r");
    }

    /**
     * Gets the specified section of the file as a byte array, then returns the file to its previous
     * position.
     */
    public static byte[] fileContent(RandomAccessFile f, long start, long end) throws IOException {
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
     * Not to be used with large files, allocates the storage in memory.
     */
    public static byte[] computeHash(RandomAccessFile f) throws IOException, NoSuchAlgorithmException {
        f.seek(0);
        byte[] content = new byte[(int)f.length()];
        f.readFully(content);
        return MessageDigest.getInstance("SHA1").digest(content);
    }


}

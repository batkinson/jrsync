package com.github.batkinson.jrsync;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

public class TestUtils {

    public static final int BLOCK_SIZE = 4096;

    /**
     * Given a package relative resource path, returns a file object.
     */
    public static File testFile(String path) throws URISyntaxException {
        URL url = BlockDescTest.class.getResource(path);
        return new File(url.toURI());
    }

    /**
     * Given a package relative resource path, returns it as an {@link InputStream}.
     */
    public static InputStream inputStream(File f) throws FileNotFoundException {
        return new FileInputStream(f);
    }

    /**
     * Opens given file from the first match in same package as this class. Used for test files.
     */
    public static RandomAccessFile randomAccess(File f)
            throws URISyntaxException, FileNotFoundException {
        return new RandomAccessFile(f, "r");
    }

    /**
     * Gets the specified section of the file as a byte array, then returns the file to its previous
     * position.
     */
    public static void copyRange(RandomAccessFile source, long start, long end, RandomAccessFile dest)
            throws IOException {
        long origPos = source.getFilePointer();
        try {
            source.seek(start);
            int read, bufSize = BLOCK_SIZE, remaining = (int) (end - start);
            byte[] buf = new byte[bufSize];
            while (remaining > 0 && (read = source.read(buf, 0, Math.min(bufSize, remaining))) >= 0) {
                dest.write(buf, 0, read);
                remaining -= read;
            }
        } finally {
            source.seek(origPos);
        }
    }

    public static long computeChecksum(RandomAccessFile f, int blockSize) throws IOException {
        return computeChecksum(f, blockSize, 0);
    }

    public static long computeChecksum(RandomAccessFile f, int blockSize, long offset) throws IOException {
        long origPosition = f.getFilePointer();
        try {
            f.seek(offset);
            RollingChecksum checksum = new RollingChecksum(blockSize);
            byte[] block = new byte[blockSize];
            f.readFully(block);
            checksum.update(block);
            return checksum.getValue();
        } finally {
            f.seek(origPosition);
        }
    }

    public static byte[] computeHash(RandomAccessFile f)
            throws IOException, NoSuchAlgorithmException {
        return computeHash(f, "SHA1");
    }

    /**
     * Used to verify integrity of copied files, leaving position where it was when it was first
     * invoked. Handles large files efficiently.
     */
    public static byte[] computeHash(RandomAccessFile f, String alg)
            throws IOException, NoSuchAlgorithmException {
        long origPos = f.getFilePointer();
        try {
            f.seek(0);
            int read;
            MessageDigest digest = MessageDigest.getInstance(alg);
            byte[] scratch = new byte[BLOCK_SIZE];
            while ((read = f.read(scratch)) >= 0) {
                digest.update(scratch, 0, read);
            }
            return digest.digest();
        } finally {
            f.seek(origPos);
        }
    }

    /**
     * Complutes a hash of only a block of a file.
     */
    public static byte[] computeHash(RandomAccessFile f, String alg, int blockSize, long offset)
            throws IOException, NoSuchAlgorithmException {
        long origPos = f.getFilePointer();
        try {
            f.seek(offset);
            MessageDigest digest = MessageDigest.getInstance(alg);
            byte[] block = new byte[blockSize];
            f.readFully(block);
            return digest.digest(block);
        } finally {
            f.seek(origPos);
        }
    }

    /**
     * Used to print contents of a byte array in hexadecimal.
     */
    public static String toHex(byte[] bytes) {
        String[] hexDigit = {
                "0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "A", "B", "C", "D", "E", "F"
        };
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < bytes.length; i++) {
            byte b = bytes[i];
            sb.append(hexDigit[(b & 0xF0) >>> 4]);
            sb.append(hexDigit[b & 0x0F]);
        }
        return sb.toString();
    }

    /**
     * Converts an int value into a byte array.
     */
    public static byte[] toBytes(int v) {
        byte[] result = new byte[4];
        for (int i = 0; i < result.length; i++) {
            int byteShift = result.length - 1 - i, bitShiftAmt = byteShift << 3;
            int mask = 0xFF << bitShiftAmt;
            result[i] = (byte) ((v & mask) >>> bitShiftAmt);
        }
        return result;
    }

    public static List<BlockDesc> computeBlocks(RandomAccessFile file, int blockSize, String digestAlgorithm) throws IOException, NoSuchAlgorithmException {
        List<BlockDesc> blockDescs = new ArrayList<>();
        long length = file.length();
        byte[] block = new byte[blockSize];
        MessageDigest digest = MessageDigest.getInstance(digestAlgorithm);
        RollingChecksum checksum = new RollingChecksum(blockSize);
        for (int i = 0, blockStart = 0; blockStart + blockSize <= length; i++, blockStart += blockSize) {
            file.seek(blockStart);
            file.readFully(block);
            checksum.update(block);
            blockDescs.add(new BlockDesc(i, checksum.getValue(), digest.digest(block)));
            checksum.reset();
        }
        return blockDescs;
    }

    /**
     * Prints a description of the file, useful for constructing metadata.
     */
    public static void describeFile(RandomAccessFile file, int blockSize, String fileAlg, String blockAlg) throws IOException, URISyntaxException, NoSuchAlgorithmException {
        byte[] content = new byte[blockSize];
        RollingChecksum sum = new RollingChecksum(blockSize);
        MessageDigest fileDigest = MessageDigest.getInstance(fileAlg);
        long blocks = file.length() / blockSize,
                remainder = file.length() % blockSize;
        for (int i = 0; i < blocks; i++) {
            MessageDigest blockDigest = MessageDigest.getInstance(blockAlg);
            file.readFully(content);
            sum.update(content);
            fileDigest.update(content);
            System.out.printf("block %d: sum: %s, digest: %s%n",
                    i,
                    toHex(toBytes((int) sum.getValue())),
                    toHex(blockDigest.digest(content)));
        }
        if (remainder > 0) {
            file.readFully(content, 0, (int) remainder);
            fileDigest.update(content, 0, (int) remainder);
            System.out.printf("block ~: %d addt'l bytes%n", remainder);
        }
        System.out.printf("file: %d bytes, digest: %s%n", file.length(), toHex(fileDigest.digest()));
    }

    public static String blockStr(long checksum, byte[] content) {
        return String.format("checksum: '%d', content: '%s'", checksum, TestUtils.toHex(content));
    }
}

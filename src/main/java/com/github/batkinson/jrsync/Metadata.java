package com.github.batkinson.jrsync;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Writes a metadata description of a file to a stream.
 */
public class Metadata {

    public static void write(String contentSource, int blockSize, String fileHashAlg, String blockHash, RandomAccessFile source, RandomAccessFile metadata) throws IOException, NoSuchAlgorithmException {

        MessageDigest fileDigest = MessageDigest.getInstance(fileHashAlg);
        RollingChecksum checksum = new RollingChecksum(blockSize);
        MessageDigest blockDigest = MessageDigest.getInstance(blockHash);

        /* The Header */

        // File hash: we know it only after, write filler
        metadata.writeUTF(fileHashAlg);
        metadata.writeByte(fileDigest.getDigestLength());
        long fileHashPos = metadata.getFilePointer();
        metadata.write(fileDigest.digest());

        // File size
        metadata.writeLong(source.length());

        // File source
        metadata.writeUTF(contentSource);

        // Block hash
        metadata.writeUTF(blockHash);
        metadata.write(blockDigest.getDigestLength());

        // Block size
        metadata.writeInt(blockSize);

        /* The Sums */
        source.seek(0);
        byte[] block = new byte[blockSize];
        int wholeBlocks = (int)(source.length() / blockSize);
        int remainder = (int)(source.length() % blockSize);
        for (int i = 0; i<wholeBlocks; i++) {
            source.readFully(block);
            checksum.update(block);
            metadata.writeLong(checksum.getValue());
            metadata.write(blockDigest.digest(block));
            fileDigest.update(block);
        }

        // Add bytes that didn't fit into a whole block
        if (remainder > 0) {
            source.readFully(block, 0, remainder);
            fileDigest.update(block, 0, remainder);
        }

        // Update the file hash once we have processed the entire contents
        metadata.seek(fileHashPos);
        metadata.write(fileDigest.digest());
    }
}

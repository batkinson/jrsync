package com.github.batkinson.jrsync;

import java.io.DataInput;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

/**
 * Writes a metadata description of a file to a stream.
 */
public class Metadata {

    private String contentSource = "";
    private String fileHashAlg = "SHA1";
    private String blockHashAlg = "MD5";
    private int blockSize;
    private long fileSize;
    private byte[] fileHash;
    private final List<BlockDesc> blockDescs = new ArrayList<>();

    private Metadata() {
    }

    public String getContentSource() {
        return contentSource;
    }

    public String getFileHashAlg() {
        return fileHashAlg;
    }

    public String getBlockHashAlg() {
        return blockHashAlg;
    }

    public int getBlockSize() {
        return blockSize;
    }

    public long getFileSize() {
        return fileSize;
    }

    public byte[] getFileHash() {
        return fileHash;
    }

    public List<BlockDesc> getBlockDescs() {
        return blockDescs;
    }

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

        long sourceLength = source.length();

        // File size
        metadata.writeLong(sourceLength);

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
        int wholeBlocks = (int) (sourceLength / blockSize);
        int remainder = (int) (sourceLength % blockSize);
        for (int i = 0; i < wholeBlocks; i++) {
            source.readFully(block);
            checksum.update(block);
            metadata.writeInt((int) checksum.getValue());
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

    public static Metadata read(DataInput in) throws IOException, NoSuchAlgorithmException {

        Metadata result = new Metadata();

        result.fileHashAlg = in.readUTF();
        result.fileHash = new byte[in.readByte()];
        in.readFully(result.fileHash);
        result.fileSize = in.readLong();
        result.contentSource = in.readUTF();
        result.blockHashAlg = in.readUTF();
        int blockHashSize = in.readByte();
        result.blockSize = in.readInt();

        int completeBlocks = (int) (result.fileSize / result.blockSize);
        for (int i = 0; i < completeBlocks; i++) {
            long checksum = in.readInt();
            byte[] hash = new byte[blockHashSize];
            in.readFully(hash);
            result.blockDescs.add(new BlockDesc(i, checksum, hash));
        }

        return result;
    }
}

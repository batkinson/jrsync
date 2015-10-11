package com.github.batkinson.jrsync;

import java.io.BufferedInputStream;
import java.io.DataInput;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

/**
 * Writes a metadata description of a file to a stream.
 */
public class Metadata {

    public static final String FILE_EXT = "jrsmd";
    public static final String MIME_TYPE = "application/vnd.jrsync+" + FILE_EXT;

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

    /**
     * Utility for generating a metadata file for an input stream.
     */
    public static void generate(String contentSource, int blockSize, String fileHashAlg, String blockHashAlg, InputStream source, File metadata) throws IOException, NoSuchAlgorithmException {
        MetadataInputWrapper out = new MetadataInputWrapper(new BufferedInputStream(source), contentSource, blockSize, fileHashAlg, blockHashAlg);
        try {
            while (out.read() >= 0);
        } finally {
            out.close();
            out.getMetadataFile().renameTo(metadata);
        }
    }

    /**
     * Utility for loading metadata from a file.
     */
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

package com.github.batkinson.jrsync;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

/**
 * Describes blocks from the byte stream we wish to use as a copy basis.
 */
public class BlockDesc {

    long blockIndex;
    long weakChecksum;
    byte[] cryptoHash;

    public BlockDesc(long blockIndex, long weakChecksum, byte[] cryptoHash) {
        this.blockIndex = blockIndex;
        this.weakChecksum = weakChecksum;
        this.cryptoHash = cryptoHash;
    }

    public static List<BlockDesc> describe(RandomAccessFile file, int blockSize, String digestAlgorithm) throws IOException, NoSuchAlgorithmException {
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
}

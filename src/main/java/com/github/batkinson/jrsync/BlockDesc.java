package com.github.batkinson.jrsync;

/**
 * Describes blocks from the byte stream we wish to use as a copy basis.
 */
public class BlockDesc {

    final long blockIndex;
    final long weakChecksum;
    final byte[] cryptoHash;

    public BlockDesc(long blockIndex, long weakChecksum, byte[] cryptoHash) {
        this.blockIndex = blockIndex;
        this.weakChecksum = weakChecksum;
        this.cryptoHash = cryptoHash;
    }

    public long getBlockIndex() {
        return blockIndex;
    }

    public long getWeakChecksum() {
        return weakChecksum;
    }

    public byte[] getCryptoHash() {
        return cryptoHash;
    }
}

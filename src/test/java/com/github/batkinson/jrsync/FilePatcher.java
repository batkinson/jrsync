package com.github.batkinson.jrsync;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

import static com.github.batkinson.jrsync.TestUtils.writeContent;

/**
 * Patches a file together based on block search results. Precursor to actual patch code.
 */
class FilePatcher implements SearchHandler {

    private int blockSize;
    private RandomAccessFile dest;
    private RandomAccessFile basis;
    private RandomAccessFile target;
    private long bytesMatched = 0;
    private long bytesNeeded = 0;

    FilePatcher(String name, int blockSize, RandomAccessFile basis, RandomAccessFile target, File dest) throws IOException {
        this.blockSize = blockSize;
        this.dest = new RandomAccessFile(File.createTempFile(name, "", dest), "rw");
        this.basis = basis;
        this.target = target;
    }

    public RandomAccessFile getDest() {
        return dest;
    }

    @Override
    public void matched(long offset, BlockDesc match) throws IOException {
        long start = match.blockIndex * blockSize, end = start + blockSize, size = end - start;
        bytesMatched += size;
        dest.seek(offset);
        writeContent(basis, start, end, dest);
    }

    @Override
    public void unmatched(long startOffset, long endOffset) throws IOException {
        bytesNeeded += endOffset - startOffset;
        dest.seek(startOffset);
        writeContent(target, startOffset, endOffset, dest);
    }

    public long getBytesMatched() {
        return bytesMatched;
    }

    public long getBytesNeeded() {
        return bytesNeeded;
    }
}

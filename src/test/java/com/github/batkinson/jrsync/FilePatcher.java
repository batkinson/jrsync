package com.github.batkinson.jrsync;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

import static com.github.batkinson.jrsync.TestUtils.copyRange;

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
    private boolean reverse;

    FilePatcher(int blockSize, RandomAccessFile basis, RandomAccessFile target, File dest, boolean reverse) throws IOException {
        this.blockSize = blockSize;
        this.dest = new RandomAccessFile(dest, "rw");
        this.basis = basis;
        this.target = target;
        this.reverse = reverse;
    }

    public RandomAccessFile getDest() {
        return dest;
    }

    @Override
    public void searched(int percent) throws IOException {
    }

    @Override
    public void matched(long offset, BlockDesc match) throws IOException {
        long start = reverse ? offset : match.blockIndex * blockSize;
        long end = start + blockSize, size = end - start;
        bytesMatched += size;
        dest.seek(reverse ? match.blockIndex * blockSize : offset);
        copyRange(basis, start, end, dest);
    }

    @Override
    public void unmatched(long start, long end) throws IOException {
        bytesNeeded += end - start;
        dest.seek(start);
        copyRange(target, start, end, dest);
    }

    public long getBytesMatched() {
        return bytesMatched;
    }

    public long getBytesNeeded() {
        return bytesNeeded;
    }
}

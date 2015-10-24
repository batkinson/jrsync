package com.github.batkinson.jrsync;

import java.io.EOFException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Searches a file for blocks that match blocks described by the specified {@link BlockDesc}s for
 * the specified block size.
 */
public class BlockSearch {

    private int blockSize;
    private Map<Long, List<BlockDesc>> blockDescs = Collections.emptyMap();

    public BlockSearch(List<BlockDesc> basisDesc, int blockSize) {
        this.blockSize = blockSize;
        this.blockDescs = Collections.unmodifiableMap(buildBlockTable(basisDesc));
    }

    private Map<Long, List<BlockDesc>> buildBlockTable(List<BlockDesc> blocks) {
        Map<Long, List<BlockDesc>> result = new HashMap<>();
        if (blocks != null) {
            for (BlockDesc desc : blocks) {
                List<BlockDesc> checksumHits = result.get(desc.weakChecksum);
                if (checksumHits == null) {
                    checksumHits = new ArrayList<>();
                    result.put(desc.weakChecksum, checksumHits);
                }
                checksumHits.add(desc);
            }
        }
        return result;
    }

    private List<BlockDesc> matchingBlocks(long checksum) {
        return blockDescs.containsKey(checksum) ? blockDescs.get(checksum) : Collections.EMPTY_LIST;
    }

    public void execute(RandomAccessFile file, String digestAlgorithm, SearchHandler handler) throws IOException, NoSuchAlgorithmException {

        long interimStart = 0;
        SearchBuffer sb = new SearchBuffer(blockSize);
        MessageDigest digest = MessageDigest.getInstance(digestAlgorithm);

        file.seek(interimStart);

        // Load a block from file
        byte[] blockBuf = new byte[blockSize];
        try {
            file.readFully(blockBuf);
            sb.add(blockBuf);
        } catch (EOFException eof) {
            unmatched(handler, interimStart, file.length());
            return;
        }

        while (true) {

            BlockDesc match = null;
            for (BlockDesc candidate : matchingBlocks(sb.checksum())) {
                if (Arrays.equals(digest.digest(sb.getBlock(blockBuf)), candidate.cryptoHash)) {
                    match = candidate;
                    break;
                }
            }

            try {
                if (match != null) {
                    long nextStart = sb.position() + blockSize;
                    if (interimStart != sb.position()) {
                        unmatched(handler, interimStart, sb.position());
                    }
                    // matching block in dest file, communicate match
                    handler.matched(sb.position(), match);
                    interimStart = nextStart;
                    // advance buffer to be at block's end
                    if (nextStart <= file.length()) {
                        file.readFully(blockBuf);
                        sb.add(blockBuf);
                    }
                } else {
                    // advance buffer one byte
                    byte next = file.readByte();
                    sb.add(next);
                }
            } catch (EOFException eof) {
                unmatched(handler, interimStart, file.length());
                return;
            }
        }
    }

    private void unmatched(SearchHandler handler, long start, long end) throws IOException {
        if (start < end) {
            handler.unmatched(start, end);
        }
    }
}


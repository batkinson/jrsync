package com.github.batkinson.jrsync;

import java.io.EOFException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.util.Collections.unmodifiableMap;
import static java.util.Collections.EMPTY_LIST;

/**
 * Searches a file for blocks that match blocks described by the specified {@link BlockDesc}s for
 * the specified block size.
 */
public class BlockSearch {

    private int blockSize;
    private List<BlockDesc> blockSummary;

    public BlockSearch(List<BlockDesc> basisDesc, int blockSize) {
        this.blockSize = blockSize;
        this.blockSummary = basisDesc;
    }

    private Map<Long, List<BlockDesc>> buildMatchTable(List<BlockDesc> blocks) {
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

    private List<BlockDesc> checksumMatches(Map<Long, List<BlockDesc>> blockDescs, long checksum) {
        return blockDescs.containsKey(checksum) ? blockDescs.get(checksum) : EMPTY_LIST;
    }

    public void execute(RandomAccessFile file, String digestAlgorithm, SearchHandler handler) throws IOException, NoSuchAlgorithmException {

        Map<Long, List<BlockDesc>> blockTable = unmodifiableMap(buildMatchTable(blockSummary));
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
            List<BlockDesc> candidates = checksumMatches(blockTable, sb.checksum());
            if (!candidates.isEmpty()) {
                byte[] contentHash = digest.digest(sb.getBlock(blockBuf));
                for (BlockDesc candidate : candidates) {
                    if (Arrays.equals(contentHash, candidate.cryptoHash)) {
                        match = candidate;
                        break;
                    }
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


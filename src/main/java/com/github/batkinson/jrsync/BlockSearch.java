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
        buildBlockTable(basisDesc);
    }

    private void buildBlockTable(List<BlockDesc> blocks) {
        if (blocks != null) {
            blockDescs = new HashMap<>();
            for (BlockDesc desc : blocks) {
                List<BlockDesc> checksumHits = blockDescs.get(desc.weakChecksum);
                if (checksumHits == null) {
                    checksumHits = new ArrayList<>();
                    blockDescs.put(desc.weakChecksum, checksumHits);
                }
                checksumHits.add(desc);
            }
        }
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
            needsContent(handler, interimStart, file.length());
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

            if (match != null) {
                long nextStart = sb.position() + blockSize;
                if (interimStart != sb.position()) {
                    needsContent(handler, interimStart, sb.position());
                }
                // matching block in dest file, communicate match
                handler.blockMatch(sb.position(), match.blockIndex);
                interimStart = nextStart;
                // advance buffer to be at block's end
                if (nextStart <= file.length()) {
                    try {
                        file.readFully(blockBuf);
                        sb.add(blockBuf);
                    } catch (EOFException eof) {
                        needsContent(handler, interimStart, file.length());
                        return;
                    }
                }
            } else {
                //   advance buffer one byte
                try {
                    byte next = file.readByte();
                    sb.add(next);
                } catch (EOFException eof) {
                    needsContent(handler, interimStart, file.length());
                    return;
                }
            }
        }
    }

    private void needsContent(SearchHandler handler, long start, long end) throws IOException {
        if (start < end) {
            handler.needsContent(start, end);
        }
    }
}


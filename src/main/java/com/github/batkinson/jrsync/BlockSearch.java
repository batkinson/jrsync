package com.github.batkinson.jrsync;

import java.io.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

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

    /**
     * A flyweight used to avoid memory allocations due to auto-boxing. The match table {@link Map} requires boxed
     * {@link Long} keys for finding matches and though {@link Long} does provide a cache it is only used for small
     * values. Since the worst case can require a lookup for every byte in the file if no matching blocks are found,
     * at least one new object would be allocated for every byte in the basis file. For large files, this can make a
     * huge difference in search performance.
     *
     * <strong>
     *     Never share or modify a flyweight instance while it is used as a key in the match table, since this violates
     *     the contract for the collections and will result in undefined semantics.
     * </strong>
     */
    private static final class FlyweightLong {

        long value;

        FlyweightLong() { }

        FlyweightLong(long v) {
            value = v;
        }

        @Override
        public boolean equals(Object o) {
            return o != null && o instanceof FlyweightLong && value == ((FlyweightLong) o).value;
        }

        @Override
        public int hashCode() {
            return (int) (value ^ (value >>> 32));
        }
    }

    private Map<FlyweightLong, Collection<BlockDesc>> buildMatchTable(List<BlockDesc> blocks) {
        Map<FlyweightLong, Collection<BlockDesc>> result = new HashMap<>();
        if (blocks != null) {
            for (BlockDesc desc : blocks) {
                FlyweightLong blockSum = new FlyweightLong(desc.weakChecksum);
                Collection<BlockDesc> checksumHits = result.get(blockSum);
                if (checksumHits == null) {
                    checksumHits = new LinkedHashSet<>();
                    result.put(blockSum, checksumHits);
                }
                checksumHits.add(desc);
            }
        }
        return result;
    }

    private Collection<BlockDesc> checksumMatches(Map<FlyweightLong, Collection<BlockDesc>> blockDescs, FlyweightLong checksum) {
        return blockDescs.containsKey(checksum) ? blockDescs.get(checksum) : EMPTY_LIST;
    }

    private void unmatched(SearchHandler handler, long start, long end) throws IOException {
        if (start < end) {
            handler.unmatched(start, end);
        }
    }

    /**
     * Performs an rsync block search on the target file, attempting to match
     * blocks in block summary specified when the search was created. Handler
     * methods are guaranteed to be called in the byte order of the target file
     * so it is possible to handle the file in a single, serial pass.
     *
     * @param target          the file to generate using bytes from basis summary
     * @param targetLength    the length of the file to generate in bytes
     * @param digestAlgorithm hash algorithm to use for block equality
     * @param handler         the object that handles search output
     * @throws IOException
     * @throws NoSuchAlgorithmException
     * @throws InterruptedException
     */
    public void rsyncSearch(DataInput target, long targetLength, String digestAlgorithm, SearchHandler handler)
            throws IOException, NoSuchAlgorithmException, InterruptedException {

        Map<FlyweightLong, Collection<BlockDesc>> blockTable = buildMatchTable(blockSummary);
        FlyweightLong checksum = new FlyweightLong();
        long interimStart = 0;
        SearchBuffer sb = new SearchBuffer(blockSize);
        MessageDigest digest = MessageDigest.getInstance(digestAlgorithm);

        // Load a block from file
        byte[] blockBuf = new byte[blockSize];
        try {
            target.readFully(blockBuf);
            sb.add(blockBuf);
        } catch (EOFException eof) {
            unmatched(handler, interimStart, targetLength);
            return;
        }

        while (true) {

            if (Thread.interrupted()) {
                throw new InterruptedException();
            }

            BlockDesc match = null;
            checksum.value = sb.checksum();
            Collection<BlockDesc> candidates = checksumMatches(blockTable, checksum);
            if (!candidates.isEmpty()) {
                byte[] contentHash = digest.digest(sb.getBlock(blockBuf));
                for (BlockDesc candidate : candidates) {
                    if (Arrays.equals(contentHash, candidate.cryptoHash)) {
                        match = candidate;
                        break;
                    }
                }
            }

            searched(handler, sb.position() + sb.length(), targetLength);

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
                    if (nextStart <= targetLength) {
                        target.readFully(blockBuf);
                        sb.add(blockBuf);
                    }
                } else {
                    // advance buffer one byte
                    byte next = target.readByte();
                    sb.add(next);
                }
            } catch (EOFException eof) {
                searched(handler, targetLength, targetLength);
                unmatched(handler, interimStart, targetLength);
                return;
            }
        }
    }

    private void searched(SearchHandler handler, long filePos, long fileLength) throws IOException {
        handler.searched((int) ((double) filePos / (fileLength == 0 ? 1 : fileLength) * 100));
    }

    /**
     * Performs an zsync block search on a basis file, attempting to match
     * blocks in the block summary of a target file, specified when the search
     * was created. Handler methods are *not* guaranteed to be called in target
     * byte order. Matches are handled in order, then unmatched content in order.
     *
     * @param basis           the local file used to build remote target
     * @param basisLength     size of the basis file in bytes
     * @param targetLength    size of the file to construct in bytes, used to handle trailing content
     * @param digestAlgorithm hash algorithm to use for block equality
     * @param handler         the object that handles search output
     * @throws IOException
     * @throws NoSuchAlgorithmException
     * @throws InterruptedException
     */
    public void zsyncSearch(DataInput basis, long basisLength, long targetLength, String digestAlgorithm, SearchHandler handler) throws IOException, NoSuchAlgorithmException, InterruptedException {

        // Modifiable so we can eliminate matched blocks as we go
        Map<FlyweightLong, Collection<BlockDesc>> blockTable = buildMatchTable(blockSummary);
        FlyweightLong checksum = new FlyweightLong();

        SearchBuffer sb = new SearchBuffer(blockSize);
        MessageDigest digest = MessageDigest.getInstance(digestAlgorithm);

        long matchedBlocks = 0;

        try {
            // Load first block
            byte[] blockBuf = new byte[blockSize];
            basis.readFully(blockBuf);
            sb.add(blockBuf);

            // Test all block offsets, starting with first block
            while (matchedBlocks < blockSummary.size()) {

                if (Thread.interrupted()) {
                    throw new InterruptedException();
                }

                boolean blockMatched = false;
                checksum.value = sb.checksum();
                Collection<BlockDesc> candidates = checksumMatches(blockTable, checksum);
                if (!candidates.isEmpty()) {
                    byte[] contentHash = digest.digest(sb.getBlock(blockBuf));
                    Iterator<BlockDesc> candidatesIter = candidates.iterator();
                    while (candidatesIter.hasNext()) {
                        BlockDesc candidate = candidatesIter.next();
                        if (Arrays.equals(contentHash, candidate.cryptoHash)) {
                            handler.matched(sb.position(), candidate);
                            candidatesIter.remove(); // Match once and only once
                            matchedBlocks++; // So we can halt early, if possible
                            blockMatched = true;
                        }
                    }
                }

                searched(handler, sb.position() + sb.length(), basisLength);

                if (blockMatched) {
                    // Advance through next block, throws at end-of-file
                    basis.readFully(blockBuf);
                    sb.add(blockBuf);
                } else {
                    // Advance to next offset, throws at end-of-file
                    sb.add(basis.readByte());
                }
            }

        } catch (EOFException eof) {
            searched(handler, basisLength, basisLength);
        }

        // Scan block summary of target and notify handler of unmatched
        // Avoids O(n) when every block has same content by using sets
        for (BlockDesc d : blockSummary) {
            checksum.value = d.weakChecksum;
            if (checksumMatches(blockTable, checksum).contains(d)) {
                long blockOffset = d.blockIndex * blockSize;
                unmatched(handler, blockOffset, blockOffset + blockSize);
            }
        }

        // Notify handler it needs target's trailing bytes, if any
        unmatched(handler, blockSummary.size() * blockSize, targetLength);
    }
}


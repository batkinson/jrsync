package com.github.batkinson.jrsync;

import java.io.IOException;

/**
 * The contract for handling block search results. The methods should always be called in the byte
 * order of the target file (the one being searched), which can be important for stream-based ops
 * such as compression, etc.
 */
public interface SearchHandler {
    void matched(long startOffset, BlockDesc match) throws IOException;
    void unmatched(long startOffset, long endOffset) throws IOException;
}

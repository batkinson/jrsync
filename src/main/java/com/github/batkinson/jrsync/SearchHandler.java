package com.github.batkinson.jrsync;

import java.io.IOException;

/**
 * The contract for handling block search results. Methods may be called out of
 * byte order depending on the search performed. See {@link BlockSearch} for
 * more details.
 */
public interface SearchHandler {
    void searched(int percent) throws IOException;
    void matched(long startOffset, BlockDesc match) throws IOException;
    void unmatched(long startOffset, long endOffset) throws IOException;
}

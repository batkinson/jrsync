package com.github.batkinson.sync;

import java.io.IOException;

interface SearchHandler {
    void blockMatch(long startOffset, long blockIndex) throws IOException;
    void needsContent(long startOffset, long endOffset) throws IOException;
}

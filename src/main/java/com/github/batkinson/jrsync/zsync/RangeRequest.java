package com.github.batkinson.jrsync.zsync;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;

/**
 * Abstraction for an http request capable of satisfying a range request. This
 * allows for completely decoupling http dependencies from the zsync algorithm.
 */
public interface RangeRequest extends Closeable {

    int getResposeCode() throws IOException;

    String getContentType();

    String getHeader(String name);

    void setHeader(String name, String value);

    InputStream getInputStream() throws IOException;
}
package com.github.batkinson.jrsync.zsync;

import java.io.IOException;

/**
 * Abstraction for http range request creation. This allows the zsync code to
 * delegate request creation to client code and avoid coupling to additional
 * http-related dependencies.
 */
public interface RangeRequestFactory {
    RangeRequest create() throws IOException;
}
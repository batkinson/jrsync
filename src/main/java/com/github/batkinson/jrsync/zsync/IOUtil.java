package com.github.batkinson.jrsync.zsync;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Convenient I/O methods, mostly to simplify zsync.
 */
public class IOUtil {

    public static final int BUFFER_SIZE = 8192;

    /**
     * Streams the contents of the supplied {@link BlockReadable} object to the
     * supplied {@link OutputStream}, stopping once the specified number of
     * bytes have been copied.
     *
     * @param in    readable to read from
     * @param out   stream to write to
     * @param count number of bytes to copy
     * @throws IOException
     */
    static void copy(BlockReadable in, OutputStream out, int count) throws IOException {
        byte[] buf = new byte[BUFFER_SIZE];
        int remaining = count;
        while (remaining > 0) {
            int read = in.read(buf, 0, Math.min(remaining, buf.length));
            if (read < 0)
                throw new IOException("failed to read content, end of stream");
            out.write(buf, 0, read);
            remaining -= read;
        }
    }

    /**
     * Wraps the specified {@link InputStream} with a {@link BufferedInputStream}.
     *
     * @param in stream to wrap
     * @return wrapped stream
     */
    public static InputStream buffer(InputStream in) {
        return new BufferedInputStream(in, BUFFER_SIZE);
    }

    /**
     * Wraps the specified {@link OutputStream} with a {@link BufferedOutputStream}.
     *
     * @param out stream to wrap
     * @return wrapped stream
     */
    public static OutputStream buffer(OutputStream out) {
        return new BufferedOutputStream(out, BUFFER_SIZE);
    }

    /**
     * Silently closes the specified list of {@link Closeable} objects in order.
     *
     * @param closeables objects to close, can be null or contain nulls
     */
    public static void close(Closeable... closeables) {
        if (closeables != null) {
            for (Closeable c : closeables)
                try {
                    if (c != null)
                        c.close();
                } catch (IOException e) {
                    // Ignore
                }
        }
    }
}

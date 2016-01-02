package com.github.batkinson.jrsync;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.security.NoSuchAlgorithmException;

/**
 * A utility class that generates a jrsync metadata as content is read from the
 * stream on-the-fly. The metadata file is complete and can be safely relocated
 * when the stream is closed.
 */
public class MetadataInputWrapper extends InputStream {

    private final InputStream wrapped;
    private final MetadataGenerator generator;
    private File metadataFile;
    private RandomAccessFile metadata;

    public MetadataInputWrapper(InputStream wrapped, String source, int blockSize, String fileHashAlg, String blockHashAlg)
            throws NoSuchAlgorithmException, IOException {
        this(wrapped, source, blockSize, fileHashAlg, blockHashAlg, null);
    }

    public MetadataInputWrapper(InputStream wrapped, String source, int blockSize, String fileHashAlg, String blockHashAlg,
                                File metadataDir) throws NoSuchAlgorithmException, IOException {
        this.wrapped = wrapped;
        generator = new MetadataGenerator(source, blockSize, fileHashAlg, blockHashAlg);
        metadataFile = File.createTempFile("miw", Metadata.FILE_EXT, metadataDir);
        metadata = new RandomAccessFile(metadataFile, "rw");
        generator.setHandler(new MetadataWriter(metadata));
    }

    public File getMetadataFile() {
        return metadataFile;
    }

    @Override
    public int read() throws IOException {
        int b = wrapped.read();
        if (b >= 0) {
            generator.add((byte) b);
        }
        return b;
    }

    public void close() throws IOException {
        try {
            super.close();
        } finally {
            generator.finish();
        }
    }
}

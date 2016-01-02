package com.github.batkinson.jrsync;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.security.NoSuchAlgorithmException;

/**
 * A utility class that generates a jrsync metadata as content is written to the
 * stream on-the-fly. The metadata file is complete and can be safely relocated
 * when the stream is closed.
 */
public class MetadataOutputWrapper extends OutputStream {

    private final OutputStream wrapped;
    private final MetadataGenerator generator;
    private File metadataFile;
    private RandomAccessFile metadata;

    public MetadataOutputWrapper(OutputStream wrapped, String source, int blockSize, String fileHashAlg, String blockHashAlg)
            throws NoSuchAlgorithmException, IOException {
        this(wrapped, source, blockSize, fileHashAlg, blockHashAlg, null);
    }

    public MetadataOutputWrapper(OutputStream wrapped, String source, int blockSize, String fileHashAlg, String blockHashAlg,
                                 File metadataDir) throws NoSuchAlgorithmException, IOException {
        this.wrapped = wrapped;
        generator = new MetadataGenerator(source, blockSize, fileHashAlg, blockHashAlg);
        metadataFile = File.createTempFile("mow", Metadata.FILE_EXT, metadataDir);
        metadata = new RandomAccessFile(metadataFile, "rw");
        generator.setHandler(new MetadataWriter(metadata));
    }

    public File getMetadataFile() {
        return metadataFile;
    }

    @Override
    public void write(int b) throws IOException {
        wrapped.write(b);
        generator.add((byte) b);
    }

    public void close() throws IOException {
        try {
            wrapped.close();
        } finally {
            generator.finish();
        }
    }
}

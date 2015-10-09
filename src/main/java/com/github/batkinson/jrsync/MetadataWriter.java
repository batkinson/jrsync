package com.github.batkinson.jrsync;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;

/**
 * Writes metadata directly to a file.
 */
public class MetadataWriter implements MetadataGenerator.Handler {

    private final RandomAccessFile metadata;
    private long fileHashPos;

    public MetadataWriter(RandomAccessFile metadataFile) throws FileNotFoundException {
        metadata = metadataFile;
    }

    @Override
    public void header(String fileHashAlg, int fileHashLength, String source, String blockHashAlg, int blockHashLength, int blockSize) throws IOException {
        metadata.writeUTF(fileHashAlg);
        metadata.writeByte(fileHashLength);

        // Skip file details, but keep position so we can update when complete
        fileHashPos = metadata.getFilePointer();
        int fileSizeLength = 8;
        metadata.seek(fileHashPos + fileHashLength + fileSizeLength);

        metadata.writeUTF(source);
        metadata.writeUTF(blockHashAlg);
        metadata.writeByte(blockHashLength);
        metadata.writeInt(blockSize);
    }

    @Override
    public void block(long checksum, byte[] digest) throws IOException {
        metadata.writeInt((int) checksum);
        metadata.write(digest);
    }

    @Override
    public void complete(long fileSize, byte[] digest) throws IOException {
        // Update file details now that we're finished
        metadata.seek(fileHashPos);
        metadata.write(digest);
        metadata.writeLong(fileSize);
    }
}

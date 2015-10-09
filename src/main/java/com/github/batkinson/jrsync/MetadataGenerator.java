package com.github.batkinson.jrsync;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class MetadataGenerator {

    private long written;
    private final String contentSource;
    private final int blockSize;
    private final String fileHashAlg;
    private final RollingChecksum checksum;
    private final MessageDigest fileDigest;
    private final MessageDigest blockDigest;
    private final String blockHashAlg;

    private Handler handler;

    public interface Handler {
        void header(String fileHashAlg, int fileHashLength, String source, String blockHashAlg, int blockHashLength, int blockSize) throws IOException;
        void block(long checksum, byte[] digest) throws IOException;
        void complete(long fileSize, byte[] digest) throws IOException;
    }

    private static final class NoOpHandler implements Handler {
        @Override
        public void header(String fileHashAlg, int fileHashLength, String source, String blockHashAlg, int blockHashLength, int blockSize) {
        }

        @Override
        public void block(long checksum, byte[] digest) {
        }

        @Override
        public void complete(long fileSize, byte[] digest) {
        }
    }

    public MetadataGenerator(String source, int blockSize, String fileHashAlg, String blockHashAlg) throws NoSuchAlgorithmException {
        this.contentSource = source;
        this.blockSize = blockSize;
        this.fileHashAlg = fileHashAlg;
        this.blockHashAlg = blockHashAlg;
        this.checksum = new RollingChecksum(blockSize);
        this.fileDigest = MessageDigest.getInstance(fileHashAlg);
        this.blockDigest = MessageDigest.getInstance(blockHashAlg);
        this.handler = new NoOpHandler();
        reset();
    }

    public void setHandler(Handler handler) {
        this.handler = handler;
    }

    public void add(byte b) throws IOException {
        if (written == 0) {
            handler.header(fileHashAlg, fileDigest.getDigestLength(), contentSource, blockHashAlg, blockDigest.getDigestLength(), blockSize);
        }
        checksum.update(b);
        fileDigest.update(b);
        blockDigest.update(b);
        written++;
        if (written % blockSize == 0) {
            handler.block(checksum.getValue(), blockDigest.digest());
        }
    }

    public void finish() throws IOException {
        handler.complete(written, fileDigest.digest());
        reset();
    }

    public void reset() {
        written = 0;
        checksum.reset();
        fileDigest.reset();
        blockDigest.reset();
    }
}

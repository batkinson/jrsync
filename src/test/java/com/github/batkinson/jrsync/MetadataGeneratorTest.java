package com.github.batkinson.jrsync;
import org.junit.Test;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;

import static org.mockito.Mockito.*;


public class MetadataGeneratorTest {

    @Test
    public void defaultHandlerNoException() throws IOException, NoSuchAlgorithmException {
        MetadataGenerator generator = new MetadataGenerator("", 1, "SHA1", "MD5");
        generator.add((byte)0);
        generator.finish();
        generator.reset();
    }

    @Test
    public void testHandlerCalled() throws NoSuchAlgorithmException, IOException {

        String source = "", fileAlg = "SHA1", blockAlg = "MD5";
        int blockSize = 1;
        MetadataGenerator generator = new MetadataGenerator(source, blockSize, fileAlg, blockAlg);

        MetadataGenerator.Handler mock = mock(MetadataGenerator.Handler.class);
        generator.setHandler(mock);

        generator.add((byte) 0);
        generator.finish();
        generator.reset();

        verify(mock).header(fileAlg, 20, source, blockAlg, 16, blockSize);
        verify(mock).block(anyLong(), any(byte[].class));
        verify(mock).complete(eq(1L), any(byte[].class));
    }
}

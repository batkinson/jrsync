package com.github.batkinson.jrsync.zsync;

import com.github.batkinson.jrsync.Metadata;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.URISyntaxException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.github.batkinson.jrsync.TestUtils.randomAccess;
import static com.github.batkinson.jrsync.TestUtils.testFile;
import static com.github.batkinson.jrsync.zsync.IOUtil.close;
import static com.github.batkinson.jrsync.zsync.ZSync.SC_PARTIAL_CONTENT;
import static com.github.batkinson.jrsync.zsync.ZSync.sync;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ZSyncTest {

    private TestRangeRequestFactory factory;

    File outputDir;
    File file1, file6;
    Metadata file1Single, file1Multiple, file1Uneven,
            file3Leading, file4Internal, file5Multiple;

    @Before
    public void setup() throws URISyntaxException, IOException, NoSuchAlgorithmException {

        outputDir = new File(System.getProperty("outputDir"), "zsync-files");
        outputDir.mkdirs();

        file1 = testFile("file1.txt");
        file6 = testFile("file6.txt");

        file1Single = Metadata.read(randomAccess(testFile("file1.jrsmd")));
        file1Multiple = Metadata.read(randomAccess(testFile("file1-bs10.jrsmd")));
        file1Uneven = Metadata.read(randomAccess(testFile("file1-bs12.jrsmd")));
        file3Leading = Metadata.read(randomAccess(testFile("file3.jrsmd")));
        file4Internal = Metadata.read(randomAccess(testFile("file4.jrsmd")));
        file5Multiple = Metadata.read(randomAccess(testFile("file5.jrsmd")));

        factory = new TestRangeRequestFactory(null);
    }

    @Test
    public void trailingRange() throws IOException, NoSuchAlgorithmException {
        setupResponse(SC_PARTIAL_CONTENT, "789\n", "Content-Range: bytes 996-999/1000");
        sync(file1Uneven, file1, tempFile("trail-rng"), factory);
    }

    @Test
    public void leadingRange() throws IOException, NoSuchAlgorithmException, URISyntaxException {
        setupResponse(SC_PARTIAL_CONTENT, "456123789\n", "Content-Range: bytes 0-9/1000");
        sync(file3Leading, file1, tempFile("lead-rng"), factory);
    }

    @Test
    public void internalRange() throws IOException, NoSuchAlgorithmException, URISyntaxException {
        setupResponse(SC_PARTIAL_CONTENT, "12345FUN SOCIETY789\n", "Content-Range: bytes 180-199/1000");
        sync(file4Internal, file1, tempFile("internal-rng"), factory);
    }

    @Test
    public void multipleRanges() throws IOException, NoSuchAlgorithmException, URISyntaxException {
        String multipartContent =
                "\r\n--EOR\r\nContent-Type: text/plain\r\nContent-Range: bytes 0-9/1000\r\n\r\n*23456789\n" +
                        "\n--EOR\nContent-Type: text/plain\nContent-Range: bytes 480-489/1000\n\n123**6789\n" +
                        "\n--EOR\nContent-Type: text/plain\nContent-Range: bytes 990-999/1000\n\n12345678*\n" +
                        "\n--EOR--";
        setupResponse(SC_PARTIAL_CONTENT, multipartContent, "Content-Type: multipart/byteranges; boundary=EOR");
        sync(file5Multiple, file1, tempFile("mult-rng"), factory);
    }

    @Test
    public void exactSingleBlock() throws IOException, NoSuchAlgorithmException {
        sync(file1Single, file1, tempFile("exact-sb"), factory);
    }

    class TestTracker implements ProgressTracker {
        Map<Stage, List<Integer>> calls = new HashMap<>();

        @Override
        public void onProgress(Stage stage, int percentComplete) {
            if (!calls.containsKey(stage))
                calls.put(stage, new ArrayList<Integer>());
            calls.get(stage).add(percentComplete);
        }

        void assertCorrect() {
            for (ProgressTracker.Stage stage : ProgressTracker.Stage.values()) {
                List<Integer> calls = this.calls.get(stage);
                assertTrue("expected multiple progress calls", calls.size() > 1);
                boolean valuesSame = true;
                for (int i = 1; i < calls.size(); i++) {
                    Integer v1 = calls.get(i - 1).intValue(), v2 = calls.get(i).intValue();
                    assertTrue("expected progress never decreases", v2 >= v1);
                    if (v1 != v2)
                        valuesSame = false;
                }
                assertFalse("expected progress values aren't all the same", valuesSame);
            }
        }
    }

    @Test
    public void exactMultipleBlocks() throws IOException, NoSuchAlgorithmException {
        sync(file1Multiple, file1, tempFile("exact-mb"), factory);
    }

    @Test
    public void progressTracker() throws IOException, NoSuchAlgorithmException {
        TestTracker tracker = new TestTracker();
        sync(file1Multiple, file1, tempFile("exact-mb"), factory, tracker);
        tracker.assertCorrect();
    }

    @Test
    public void poisonByte() throws IOException, NoSuchAlgorithmException {
        setupResponse(SC_PARTIAL_CONTENT, "987654321\n", "Content-Range: bytes 40-49/1000");
        sync(file1Multiple, file6, tempFile("poison-byte"), factory);
    }

    private File tempFile(String test) throws IOException {
        return File.createTempFile(test, "", outputDir);
    }

    private void setupResponse(final int status, final String content, final String... headers) {
        factory.handler = new RequestHandler() {
            @Override
            public Response service(Map<String, String> dontCare) {
                HashMap<String, String> responseHeaders = new HashMap<>();
                if (headers != null)
                    for (String header : headers) {
                        String[] headerFields = header.split("[:]\\s+", 2);
                        responseHeaders.put(headerFields[0], headerFields[1]);
                    }
                return new Response(status, responseHeaders, new ByteArrayInputStream(content.getBytes()));
            }
        };
    }
}

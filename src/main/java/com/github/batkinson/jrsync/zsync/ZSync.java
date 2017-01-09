package com.github.batkinson.jrsync.zsync;

import com.github.batkinson.jrsync.BlockDesc;
import com.github.batkinson.jrsync.BlockSearch;
import com.github.batkinson.jrsync.Metadata;
import com.github.batkinson.jrsync.SearchHandler;

import java.io.*;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.github.batkinson.jrsync.zsync.IOUtil.buffer;
import static com.github.batkinson.jrsync.zsync.IOUtil.close;
import static com.github.batkinson.jrsync.zsync.IOUtil.copy;
import static com.github.batkinson.jrsync.zsync.Range.appendRange;
import static com.github.batkinson.jrsync.zsync.Range.estimateStringLength;
import static com.github.batkinson.jrsync.zsync.Range.toRangeString;
import static com.github.batkinson.jrsync.zsync.ZSync.parseContentRange;
import static java.util.Arrays.asList;
import static java.util.Collections.EMPTY_MAP;

/**
 * Contains methods useful for implementing the zsync algorithm. By relying only
 * on abstract HTTP interfaces, the code avoids dependencies on HTTP-related
 * code. To use the implementation, you will likely need to implement your own
 * {@link RangeRequest} and {@link RangeRequestFactory}, which will comprise
 * the necessary, but missing HTTP handling code.
 */
public class ZSync {

    public static final int SC_PARTIAL_CONTENT = 206;
    public static final String RANGE_HEADER = "Range";
    public static final String CONTENT_RANGE_HEADER = "Content-Range";
    public static final Pattern CONTENT_RANGE_PATTERN = Pattern.compile("bytes (\\d+)-(\\d+)/(\\d+)");
    public static final String MULTIPART_BYTERANGES_MIME_TYPE = "multipart/byteranges";

    /**
     * Parses the specified {@link String} as an http content range header value.
     *
     * @param contentRange value of an http content range header
     * @return an array of long containing start, finish indices and total size
     * of the range in bytes.
     */
    static long[] parseContentRange(String contentRange) {
        Matcher m = CONTENT_RANGE_PATTERN.matcher(contentRange);
        if (m.find()) {
            int groups = m.groupCount();
            long[] result = new long[groups];
            for (int i = 1; i <= groups; i++)
                result[i - 1] = Long.parseLong(m.group(i));
            return result;
        }
        throw new RuntimeException("invalid content range, expected start-end/total size");
    }

    /**
     * Performs a remote file synchronization using remote metadata and an http
     * range request. Note client code is responsible for closing the basis
     * file this method *does not* close it.
     *
     * @param metadata       describes remote file
     * @param basis          local file to search for matching content
     * @param target         file that will be written as a result
     * @param requestFactory factory to create http range request
     * @throws NoSuchAlgorithmException
     * @throws IOException
     */
    public static void sync(Metadata metadata, File basis, File target, RangeRequestFactory requestFactory)
            throws IOException, NoSuchAlgorithmException, InterruptedException {
        sync(metadata, basis, target, requestFactory, null);
    }

    /**
     * Performs a remote file synchronization using remote metadata and an http
     * range request. Note client code is responsible for closing the basis
     * file this method *does not* close it.
     *
     * @param metadata       describes remote file
     * @param basis          local file to search for matching content
     * @param target         file that will be written as a result
     * @param requestFactory factory to create http range request
     * @param tracker        event handler for progress updates, null for no tracking
     * @throws NoSuchAlgorithmException
     * @throws IOException
     * @throws InterruptedException
     */
    public static void sync(Metadata metadata, File basis, File target, RangeRequestFactory requestFactory,
                            ProgressTracker tracker) throws NoSuchAlgorithmException, IOException, InterruptedException {

        MessageDigest digest = MessageDigest.getInstance(metadata.getFileHashAlg());
        DigestOutputStream digestOut = new DigestOutputStream(buffer(new FileOutputStream(target)), digest);

        // Perform block search for remote content in local file
        BlockSearch search = new BlockSearch(metadata.getBlockDescs(), metadata.getBlockSize());
        Analyzer analyzer = new Analyzer(metadata);
        if (tracker != null)
            analyzer.setTracker(tracker);
        DataInputStream searchInput = new DataInputStream(buffer(new FileInputStream(basis)));
        try {
            search.zsyncSearch(searchInput, basis.length(), metadata.getFileSize(), metadata.getBlockHashAlg(), analyzer);
        } finally {
            searchInput.close();
        }

        RangeRequest req = null;
        RangeStream input = null;
        try {
            if (analyzer.remoteBytes() > 0) {
                req = requestFactory.create();
                req.setHeader(RANGE_HEADER, "bytes=" + toRangeString(analyzer.getRemoteRanges()));

                int status = req.getResponseCode();
                String contentType = req.getContentType(), contentRange = req.getHeader(CONTENT_RANGE_HEADER);
                InputStream bodyIn = req.getInputStream();

                if (status != SC_PARTIAL_CONTENT)
                    throw new RuntimeException(
                            "expected " + SC_PARTIAL_CONTENT + ", was: " + status);

                if (contentRange != null) {
                    input = new ContentRangeStream(bodyIn, contentRange);
                } else if (contentType != null && contentType.contains(MULTIPART_BYTERANGES_MIME_TYPE)) {
                    input = new MultipartByteRangeInputStream(bodyIn, contentType);
                } else
                    throw new RuntimeException("expected http range content for single or multiple ranges");
            }

            RandomAccessFile randomAccessBasis = new RandomAccessFile(basis, "r");
            try {
                buildFile(metadata, randomAccessBasis, analyzer.getMatches(), input, digestOut, tracker);
            } finally {
                randomAccessBasis.close();
            }

        } finally {
            close(input, req, digestOut);
        }

        if (!Arrays.equals(metadata.getFileHash(), digest.digest())) {
            throw new RuntimeException("constructed file doesn't match metadata");
        }
    }

    /**
     * Implements a {@link CopyTracker} that automatically reports sync progress using a {@link ProgressTracker}.
     */
    static class CopyTracker implements IOUtil.CopyListener {

        ProgressTracker tracker;
        long fileSize, copied;
        int fileProgress;

        CopyTracker(ProgressTracker tracker, long fileSize) {
            this.tracker = tracker;
            this.fileSize = fileSize;
        }

        @Override
        public void copied(int bytes) {
            copied += bytes;
            int nextValue = fileSize <= 0 ? 100 : (int) ((((double) copied) / fileSize) * 100);
            if (tracker != null && nextValue != fileProgress) {
                tracker.onProgress(ProgressTracker.Stage.BUILD, fileProgress);
            }
            fileProgress = nextValue;
        }
    }

    /**
     * Constructs a file from matching local content and multiple ranges of
     * remote content. It assumes the server will return ranges in order
     * requested. It also does *not* close the basis file.
     */
    static void buildFile(Metadata metadata, RandomAccessFile basis, Map<Long, Long> matches, RangeStream remoteInput,
                          OutputStream output, ProgressTracker tracker) throws IOException, InterruptedException {
        if (remoteInput == null)
            remoteInput = new EmptyRangeStream();
        BlockReadable localInput = new RandomAccessBlockReadable(basis);
        int blockSize = metadata.getBlockSize();
        long offset = 0, targetSize = metadata.getFileSize();
        CopyTracker copyTracker = new CopyTracker(tracker, targetSize);
        Range nextRange;
        while (offset < targetSize) {

            if (Thread.interrupted()) {
                throw new InterruptedException();
            }

            if (matches.containsKey(offset)) {
                basis.seek(matches.get(offset));
                copy(localInput, output, blockSize, copyTracker);
                offset += blockSize;
            } else if ((nextRange = remoteInput.next()) != null && offset == nextRange.first) {
                int rangeLength = (int) (nextRange.last - nextRange.first) + 1;
                copy(remoteInput, output, rangeLength, copyTracker);
                offset += rangeLength;
            } else
                throw new RuntimeException("no content for offset: " + offset);
        }
    }
}


/**
 * Represents a byte range from start to finish. Uses a zero-based byte
 * index and range is inclusive (last byte is in range, not past it).
 */
class Range {

    final long first, last;

    Range(long first, long last) {
        this.first = first;
        this.last = last;
    }

    public boolean directlyFollows(Range other) {
        return other != null && first == other.last + 1;
    }

    public boolean canMerge(Range other) {
        return other != null
                && (directlyFollows(other) || other.directlyFollows(this));
    }

    public Range merge(Range other) {
        if (other != null) {
            if (directlyFollows(other))
                return new Range(other.first, last);
            if (other.directlyFollows(this))
                return new Range(first, other.last);
        }
        throw new RuntimeException("attempted to merge non-contiguous ranges");
    }

    /**
     * Adds the specified range to list, compacting into contiguous ranges.
     */
    public static void appendRange(List<Range> ranges, long start, long finish) {
        Range next = new Range(start, finish);
        if (!ranges.isEmpty()) {
            int prevIndex = ranges.size() - 1;
            Range prev = ranges.get(prevIndex);
            if (prev.canMerge(next)) {
                ranges.set(prevIndex, prev.merge(next));
                return;
            }
        }
        ranges.add(next);
    }

    private static final long[] DIGITS_LOOKUP = {
            10L, 100L, 1000L, 10000L, 100000L, 1000000L, 10000000L, 100000000L, 1000000000L, 10000000000L,
            100000000000L, 1000000000000L, 10000000000000L, 100000000000000L, 1000000000000000L, 10000000000000000L,
            100000000000000000L, 1000000000000000000L
    };

    /**
     * Returns the number of digits required to encode the value in a base 10 string.
     *
     * @param value non-negative integer
     * @return number of digits to encode as base 10 string
     */
    public static long base10Digits(long value) {
        for (int i = 0; i < DIGITS_LOOKUP.length; i++) {
            if (value < DIGITS_LOOKUP[i])
                return i + 1;
        }
        return DIGITS_LOOKUP.length + 1;
    }

    /**
     * Returns an estimated length for the list of ranges encoded as a range string.
     *
     * @param ranges
     * @return
     */
    public static long estimateStringLength(List<Range> ranges) {
        long length = 0;
        for (int i = 0; i < ranges.size(); i++) {
            Range r = ranges.get(i);
            length += base10Digits(r.first) + base10Digits(r.last) + (i == 0 ? 1 : 2);
        }
        return length;
    }

    /**
     * Converts a list of ranges into a range string suitable for using with
     * http range request headers.
     */
    public static String toRangeString(List<Range> ranges) {
        StringBuilder buf = new StringBuilder();
        for (Range r : ranges) {
            if (buf.length() > 0)
                buf.append(',');
            buf.append(r);
        }
        return buf.toString();
    }

    @Override
    public String toString() {
        return String.format("%s-%s", first, last);
    }
}


/**
 * A search handler that computes everything required to perform the sync
 * process based on local and remote content.
 */
class Analyzer implements SearchHandler {

    private static final int RANGE_STRING_MAX_LENGTH = 3700;

    // Maps remote block index to offset of matching content in local file
    private final Map<Long, Long> matches = new HashMap<>();
    private final Metadata metadata;
    private final List<Range> required = new ArrayList<>();
    private long rangeStringLength = -1;
    private ProgressTracker tracker;

    Analyzer(Metadata metadata) {
        this.metadata = metadata;
    }

    public void setTracker(ProgressTracker tracker) {
        this.tracker = tracker;
    }

    @Override
    public void searched(int percent) throws IOException {
        if (tracker != null)
            tracker.onProgress(ProgressTracker.Stage.SEARCH, percent);
    }

    @Override
    public void matched(long start, BlockDesc desc) throws IOException {
        matches.put(desc.getBlockIndex() * metadata.getBlockSize(), start);
    }

    @Override
    public void unmatched(long start, long end) throws IOException {
        appendRange(required, start, end - 1);
    }

    private boolean isRangeStringTooLong() {
        if (rangeStringLength < 0) {
            rangeStringLength = estimateStringLength(required);
        }
        return rangeStringLength > RANGE_STRING_MAX_LENGTH;
    }

    public Map<Long, Long> getMatches() {
        return isRangeStringTooLong() ? EMPTY_MAP : matches;
    }

    /**
     * The amount of remote content we can source locally.
     */
    public long localBytes() {
        return isRangeStringTooLong() ? 0 : metadata.getBlockSize() * matches.size();
    }

    /**
     * The amount of remote content we have to fetch.
     */
    public long remoteBytes() {
        return metadata.getFileSize() - localBytes();
    }

    /**
     * The minimal list of remote byte ranges required in ascending byte order.
     */
    public List<Range> getRemoteRanges() {
        return isRangeStringTooLong() ? asList(new Range(0, metadata.getFileSize() - 1)) : required;
    }
}

/**
 * Abstraction allowing for unified copy implementation using a buffer despite
 * differing byte source implementations not sharing a common interface, like
 * {@link RandomAccessFile}.
 */
interface BlockReadable extends Closeable {
    int read(byte[] buf, int offset, int length) throws IOException;
}


/**
 * A wrapper for {@link RandomAccessFile} objects so they can be used with our
 * buffered copy implementation.
 */
class RandomAccessBlockReadable implements BlockReadable {

    private RandomAccessFile file;

    public RandomAccessBlockReadable(RandomAccessFile file) {
        this.file = file;
    }

    @Override
    public int read(byte[] buf, int offset, int length) throws IOException {
        return file.read(buf, offset, length);
    }

    @Override
    public void close() {
        IOUtil.close(file);
    }
}


/**
 * An abstraction for the various sources for byte ranges and their data. This
 * allows for a single implementation of the sync process.
 */
interface RangeStream extends BlockReadable {
    Range next() throws IOException;
}


/**
 * A {@link RangeStream} implementation useful when there is no remote content
 * to fetch.
 */
class EmptyRangeStream implements RangeStream {

    public EmptyRangeStream() {
    }

    @Override
    public Range next() {
        return null;
    }

    @Override
    public int read(byte[] buf, int offset, int length) {
        return -1;
    }

    @Override
    public void close() {
    }
}

/**
 * A {@link RangeStream} implementation that can read the body of an http
 * response containing a single range. Use this when the response contains
 * the Content-Range header, per the HTTP specification.
 */
class ContentRangeStream implements RangeStream {

    private InputStream input;
    private Range range;

    public ContentRangeStream(InputStream input, String contentTypeHeader) {
        this.input = input;
        long[] rangeInfo = parseContentRange(contentTypeHeader);
        range = new Range(rangeInfo[0], rangeInfo[1]);
    }

    @Override
    public Range next() throws IOException {
        Range result = range;
        if (range != null) {
            range = null;  // Only return a range once
        }
        return result;
    }

    @Override
    public int read(byte[] buf, int offset, int length) throws IOException {
        return input.read(buf, offset, length);
    }

    @Override
    public void close() {
        IOUtil.close(input);
    }
}

/**
 * A {@link RangeStream} implementation that can read the body of a multipart
 * byte range http response. It allows simple traversal of the ranges without
 * having to know the format. Use this when the Content-Type of a response to a
 * range request is 'multipart/byteranges', per the HTTP specification.
 */
class MultipartByteRangeInputStream extends InputStream implements RangeStream {

    private static final Pattern BOUNDARY_PATTERN = Pattern.compile("boundary=(\\S+)");
    private static final String CRLF = "\r\n", LF = "\n";

    private InputStream input;
    private Map<String, String> headers = Collections.emptyMap();
    private String boundary, finalBoundary;

    public MultipartByteRangeInputStream(InputStream input, String contentTypeHeader) {
        this.input = buffer(input);
        Matcher m = BOUNDARY_PATTERN.matcher(contentTypeHeader);
        if (m.find()) {
            boundary = "--" + m.group(1);
            finalBoundary = boundary + "--";
        } else
            throw new RuntimeException("expected boundary in Content-Type");
    }

    /**
     * Reads content until the next carriage-return/line-feed combination in the
     * input stream and returns it. This is meant to read header lines preceding
     * the byte ranges in the multi-part format.
     */
    private String readLine() throws IOException {

        StringBuilder buf = new StringBuilder();
        int crlfIndex = -1, lfIndex = -1;

        // Scan until we hit a carriage return/line feed combo
        do {
            byte b = (byte) input.read();
            if (b < 0) {
                if (buf.length() == 0)
                    throw new EOFException();
                else
                    break;
            }
            buf.append((char) b);  // Cast ok because HTTP headers are US-ASCII
        }
        while (!(buf.length() >= CRLF.length() && (crlfIndex = buf.indexOf(CRLF, buf.length() - CRLF.length())) >= 0) &&
                !(buf.length() >= LF.length() && (lfIndex = buf.indexOf(LF, buf.length() - LF.length())) >= 0));

        // Chop off the endline
        if (crlfIndex >= 0) {
            buf.delete(crlfIndex, buf.length());
        } else if (lfIndex >= 0) {
            buf.delete(lfIndex, buf.length());
        }

        return buf.toString();
    }

    /**
     * Advances the stream to the beginning of the next byte range part. It
     * scans past and consumes the part headers in the process. It returns the
     * start byte from the part's content-range as specified in its
     * Content-Range header or null if there are no more parts.
     */
    public Range next() throws IOException {

        String headerLine;
        Range nextRange = null;
        headers = new HashMap<>();

        // Scan past start boundary (or final boundary)
        while (!boundary.equals(headerLine = readLine()) &&
                !finalBoundary.equals(headerLine)) ;

        // Parse headers until empty line
        if (boundary.equals(headerLine)) {
            while (!"".equals(headerLine = readLine())) {
                String[] header = headerLine.split("[:]\\s+", 2);
                String headerName = header[0], headerValue = header[1];
                headers.put(headerName, headerValue);
                if ("Content-Range".equals(headerName)) {
                    long[] rangeVals = parseContentRange(headerValue);
                    nextRange = new Range(rangeVals[0], rangeVals[1]);
                }
            }
        }

        return nextRange;
    }

    @Override
    public int read() throws IOException {
        return input.read();
    }

    @Override
    public void close() {
        IOUtil.close(input);
    }
}



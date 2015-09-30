package com.github.batkinson.jrsync;

/**
 * A uni-directional circular byte buffer that maintains a block and it's checksum.
 */
class SearchBuffer {

    private long position;
    private byte[] buffer;
    private int size;
    private int offset;
    private RollingChecksum checksum;

    public SearchBuffer(int blockSize) {
        buffer = new byte[blockSize];
        position = 0;
        size = 0;
        offset = 0;
        checksum = new RollingChecksum(blockSize);
    }

    public long position() {
        return position;
    }

    public int length() {
        return size;
    }

    private int offset(int index) {
        return (offset + index) % buffer.length;
    }

    public void add(byte item) {

        if (size >= buffer.length) {
            position++; // position in stream of bytes of block start
            offset++;   // position in internal buffer to write next byte
        } else {
            size++;     // just write directly until we reach capacity
        }

        buffer[offset(size - 1)] = item; // Add the item to the end of the window
        checksum.update(item);
    }

    public void add(byte[] items) {
        for (int i = 0; i < items.length; i++) {
            add(items[i]);
        }
    }

    byte get(int index) {
        if (index >= size)
            throw new IndexOutOfBoundsException("index " + index + " greater than buffer size " + size);
        return buffer[offset(index)];
    }

    public byte[] getBlock(byte[] toFill) {
        for (int i = 0; i < Math.min(size, toFill.length); i++)
            toFill[i] = get(i);
        return toFill;
    }

    public long checksum() {
        return checksum.getValue();
    }
}

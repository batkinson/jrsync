package com.github.batkinson.sync;

/**
 * A uni-directional circular byte buffer that maintains a block and it's checksum.
 */
class SearchBuffer {

    private long position;
    private int blockSize;
    private int capacity;
    private byte[] buffer;
    private int size;
    private int offset;
    private RollingChecksum checksum;

    public SearchBuffer(int blockSize) {
        capacity = blockSize + 1;  // We need the byte previous to block to compute rolling sum
        this.blockSize = blockSize;
        buffer = new byte[capacity];
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

    public int capacity() {
        return capacity;
    }

    public int blockSize() {
        return blockSize;
    }

    private int offset(int index) {
        return (offset + index) % capacity;
    }

    public void add(byte item) {

        // Append item to buffer maintaining...
        if (size >= blockSize) {
            position++; // position in stream of bytes of block start
        }

        if (size < capacity) {
            size++;  // length of content in our internal buffer
        } else {
            offset++;  // starting offset of the beginning of content (byte prior to block start)
        }

        buffer[offset(size - 1)] = item; // Add the item to the end of the window

        if (size <= blockSize) {
            checksum.add(item);
        } else {
            checksum.update(get(0), item);
        }
    }

    public void add(byte[] items) {
        if (items.length > capacity)
            throw new IllegalArgumentException("items exceed capacity");
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
        int shim = size < capacity? 0 : 1;
        for (int i = 0; i < Math.min(size, toFill.length); i++)
            toFill[i] = get(i + shim);
        return toFill;
    }

    public long checksum() {
        return checksum.getValue();
    }
}

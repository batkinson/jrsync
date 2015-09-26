package com.github.batkinson;

/**
 * A uni-directional circular byte buffer, useful for keeping track of the byte prior to the current
 * block when searching.
 */
class RingBuffer {

    int capacity;
    byte[] buffer;
    int size;
    int offset;

    public RingBuffer(int capacity) {
        this.capacity = capacity;
        this.buffer = new byte[capacity];
        this.size = 0;
        this.offset = 0;
    }

    public int length() {
        return size;
    }

    public int capacity() {
        return capacity;
    }

    private int offset(int index) {
        return (offset + index) % capacity;
    }

    public void add(byte item) {
        if (size < capacity) {
            size++;
        } else {
            offset++;
        }
        buffer[offset(size - 1)] = item;
    }

    public void add(byte[] items) {
        if (items.length > capacity)
            throw new IllegalArgumentException("items exceed capacity");
        for (int i = 0; i < items.length; i++) {
            add(items[i]);
        }
    }

    public byte get(int index) {
        if (index >= size)
            throw new IndexOutOfBoundsException("index " + index + " greater than buffer size " + size);
        return buffer[offset(index)];
    }

    public byte[] bytes(byte[] toFill, int offset) {
        for (int i = offset; i < Math.min(size, toFill.length) - offset; i++)
            toFill[i] = get(i);
        return toFill;
    }
}

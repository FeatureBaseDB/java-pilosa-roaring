package com.pilosa.roaring;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.NoSuchElementException;

class Container {
    Container() {
        this.bitmaps = new long[BITMAP_N];
    }

    void add(long bit) {
        if ((this.bitmaps[(int)(bit >>> 6)] & (1L << (bit % 64))) == 1) {
            return;
        }
        this.bit_count += 1;
        this.bitmaps[(int)(bit >>> 6)] |= (1L << (bit % 64));
    }

    BitIterator iterator() {
        return new ContainerBitIterator(this);
    }

    ContainerMeta serializedProperties() {
        int index = 0;
        long sizes[] = new long[] {2 * this.bit_count, 8 * BITMAP_N, 0};
        short types[] = new short[] {TYPE_ARRAY, TYPE_BITMAP, TYPE_RLE};
        long runCount = countRuns();
        if (runCount > MAX_RUNS) {
            // encoding in RLE would produce invalid data; don't bother.
            if (sizes[1] < sizes[0]) index = 1;
        }
        else {
            sizes[2] = 2 + 4 * runCount; // rle
            if (sizes[1] < sizes[index]) index = 1;
            if (sizes[2] < sizes[index]) index = 2;
        }
        return new ContainerMeta(sizes[index], types[index], this.bit_count);
    }

    void serializeAsArrayInto(ByteBuffer buf) {
        BitIterator it = iterator();
        while (it.hasNext()) {
            long bit = it.next();
            buf.putShort((short)bit);
        }
    }

    void serializeAsBitmapInto(ByteBuffer buf) {
        for (long bitmap : this.bitmaps) {
            buf.putLong(bitmap);
        }
    }

    void serializeAsRLE(ByteBuffer buf) {
        int offset = buf.position();
        // temporarily put the rle length
        short runCount = 0;
        buf.putShort(runCount);
        BitIterator it = iterator();
        if (!it.hasNext()) {
            return;
        }
        long start = it.next();
        long last = start;
        while (it.hasNext()) {
            long bit = it.next();
            if (bit == last + 1) {
                last = bit;
                continue;
            }
            // run ended, put it
            buf.putShort((short)start);
            buf.putShort((short)last);
            start = last = bit;
            runCount += 1;
        }
        buf.putShort((short)start);
        buf.putShort((short)last);
        runCount += 1;
        // go back and write the correct runCount
        buf.putShort(offset, runCount);
    }

    private long countRuns() {
        long runCount = 0;
        Long start = null;
        long last = 0;

        for (int bx = 0; bx < BITMAP_N; bx++) {
            long bitmap = this.bitmaps[bx];
            if (bitmap == 0) {
                continue;
            }
            for (int p = 0; p < 64; p++) {
                long v = 1L << p;
                if ((bitmap & v) == v) {
                    Long bit = bx *64L + p;
                    if (start == null) {
                        start = last = bit;
                        continue;
                    }
                    if (bit == last + 1) {
                        last = bit;
                        continue;
                    }
                    runCount += 1;
                    start = last = bit;
                }
            }
        }
        runCount += 1;
        return runCount;
    }

    static final int BITMAP_N = 1024;
    static final int MAX_RUNS = 2048;
    static final int TYPE_ARRAY = 1;
    static final int TYPE_BITMAP = 2;
    static final int TYPE_RLE = 3;

    long[] bitmaps;
    private  short bit_count = 0;
}

class ContainerBitIterator implements BitIterator {

    ContainerBitIterator(Container container) {
        this.container = container;
    }

    public boolean hasNext() {
        if (this.stopped) {
            return false;
        }
        if (this.nextBit != null) {
            return true;
        }
        long[] bitmaps = this.container.bitmaps;
        while (this.nextBitmapIndex < Container.BITMAP_N) {
            short nextBitmapIndex = this.nextBitmapIndex;
            short nextBitIndex = this.nextBitIndex;
            this.nextBitIndex += 1;
            if (this.nextBitIndex == 64) {
                this.nextBitIndex = 0;
                this.nextBitmapIndex += 1;
            }
            long bitmap = bitmaps[nextBitmapIndex];
            if (bitmap == 0) {
                continue;
            }
            long v = 1L << nextBitIndex;
            if ((bitmap & v) == v) {
                this.nextBit = nextBitmapIndex * 64  + nextBitIndex;
                return true;
            }
        }
        this.stopped = true;
        return false;
    }

    public Long next() {
        if (this.stopped) {
            throw new NoSuchElementException();
        }
        long value = this.nextBit;
        this.nextBit = null;
        return value;
    }

    public void remove() {
        // Java 7 compatibility
    }

    private Container container;
    private short nextBitmapIndex = 0;
    private short nextBitIndex = 0;
    private Integer nextBit;
    private boolean stopped = false;
}

class ContainerMeta {
    public ContainerMeta(long size, short type, short bit_count) {
        this.size = size;
        this.type = type;
        this.bit_count = bit_count;
    }

    public byte[] serialize() {
        return ByteBuffer.allocate(12)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putLong(this.key)
                .putShort(this.type)
                .putShort((short)(this.bit_count - 1))
                .array();
    }

    long size;
    short type;
    short bit_count;
    long key;
}
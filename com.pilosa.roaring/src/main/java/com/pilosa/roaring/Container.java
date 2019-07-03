package com.pilosa.roaring;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;

class Container {
    Container() {
        this.bits = new TreeSet<>();
    }

    void add(long bit) {
        this.bits.add((int)bit);
    }

    Iterator<Integer> iterator() {
        return this.bits.iterator();
    }

    ContainerMeta serializedProperties() {
        int index = 0;
        int bit_count = this.bits.size();
        long[] sizes = new long[]{2 * bit_count, 8 * BITMAP_N, 0};
        short[] types = new short[]{TYPE_ARRAY, TYPE_BITMAP, TYPE_RLE};
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
        return new ContainerMeta(sizes[index], types[index], bit_count);
    }

    void serializeAsArrayInto(ByteBuffer buf) {
        for (long bit : this.bits) {
            buf.putShort((short)bit);
        }
    }

    void serializeAsBitmapInto(ByteBuffer buf) {
        long bitmap = 0;
        int bitmapIndex = 0;
        ByteBuffer bmpBuf = ByteBuffer.allocate(8 * BITMAP_N).order(ByteOrder.LITTLE_ENDIAN);
        for (long bit : this.bits) {
            int index = (int)(bit >>> 6);
            if (index != bitmapIndex) {
                // put the current bitmap
                bmpBuf.putLong(8 * bitmapIndex, bitmap);
                bitmapIndex = index;
                bitmap = 0L;
            }
            bitmap |= (1L << (bit % 64));
        }
        // put the current bitmap
        bmpBuf.putLong(8 * bitmapIndex, bitmap);
        buf.put(bmpBuf);
    }

    void serializeAsRLE(ByteBuffer buf) {
        int offset = buf.position();
        // temporarily put the rle length
        short runCount = 0;
        buf.putShort(runCount);
        Iterator<Integer> it = iterator();
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
        Iterator<Integer> it = iterator();
        long last = it.next();
        while (it.hasNext()) {
            long bit = it.next();
            if (bit == last + 1) {
                last = bit;
                continue;
            }
            // run ended
            runCount += 1;
        }
        runCount += 1;
        return runCount;
    }

    static final int BITMAP_N = 1024;
    static final int MAX_RUNS = 2048;
    static final int TYPE_ARRAY = 1;
    static final int TYPE_BITMAP = 2;
    static final int TYPE_RLE = 3;

    Set<Integer> bits;
}

class ContainerMeta {
    ContainerMeta(long size, short type, int bit_count) {
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
    int bit_count;
    long key;
}

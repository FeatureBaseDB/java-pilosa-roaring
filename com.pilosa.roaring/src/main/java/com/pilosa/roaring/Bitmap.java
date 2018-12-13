package com.pilosa.roaring;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;

public class Bitmap {
    public Bitmap() {
        this.containers = new HashMap<>();
    }

    public void add(long bit) {
        long key = bit >>> 16L;
        Container container = this.containers.get(key);
        if (container == null) {
            container = new Container();
            this.containers.put(key, container);
        }
        container.add((int)(bit & 0xFFFF));
    }

    public BitIterator iterator() {
        return new BitmapBitIterator(this);
    }

    public ByteBuffer serialize() {
        int bufSize = 4 + 4;
        List<ContainerMeta> containerMetaList = new ArrayList<>(this.containers.size());
        List<Long> keys = new ArrayList<>(this.containers.keySet());
        Collections.sort(keys);
        for (long key : keys) {
            ContainerMeta props = this.containers.get(key).serializedProperties();
            props.key = key;
            bufSize += 12 + 4 + props.size;
            containerMetaList.add(props);
        }

        ByteBuffer buf = ByteBuffer.allocate(bufSize).order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt(COOKIE);
        buf.putInt(this.containers.size());

        // write meta
        for (ContainerMeta meta : containerMetaList) {
            buf.put(meta.serialize());
        }

        // write offsets
        int dataOffset = buf.position() + this.containers.size() * 4;
        for (ContainerMeta meta : containerMetaList) {
            buf.putInt(dataOffset);
            dataOffset += meta.size;
        }
        // sort containers by their key

        for (int i = 0; i < containerMetaList.size(); i++) {
            Container container = this.containers.get(keys.get(i));
            switch (containerMetaList.get(i).type) {
                case Container.TYPE_ARRAY:
                    container.serializeAsArrayInto(buf);
                    break;
                case Container.TYPE_BITMAP:
                    container.serializeAsBitmapInto(buf);
                    break;
                case Container.TYPE_RLE:
                    container.serializeAsRLE(buf);
                    break;
                default:
                    throw new RuntimeException(String.format("Invalid container type: %d", containerMetaList.get(i).type));
            }
        }

        return buf;
    }

    private static final short MAGIC_NUMBER = 12348;
    private static final short STORAGE_VERSION = 0;
    private static final short COOKIE = (MAGIC_NUMBER + (STORAGE_VERSION << 16));

    Map<Long, Container> containers;
}

class BitmapBitIterator implements BitIterator {
    BitmapBitIterator(Bitmap bitmap) {
        this.entryIterator = bitmap.containers.entrySet().iterator();
        if (entryIterator.hasNext()) {
            this.iterateEntry();
        }
    }

    @Override
    public boolean hasNext() {
        if (this.stopped) {
            return false;
        }
        if (this.bitIterator.hasNext()) {
            return true;
        }
        if (this.entryIterator.hasNext()) {
            this.iterateEntry();
            if (this.bitIterator.hasNext()) {
                return true;
            }
        }
        this.stopped = true;
        return false;
    }

    private void iterateEntry() {
        Map.Entry<Long, Container> entry = this.entryIterator.next();
        this.key = entry.getKey();
        this.bitIterator = entry.getValue().iterator();
    }

    @Override
    public Long next() {
        if (this.stopped) {
            throw new NoSuchElementException();
        }
        long next = this.bitIterator.next();
        return (this.key << 16) + next;
    }

    @Override
    public void remove() {
        // Java 7 compatibility
    }

    private Iterator<Map.Entry<Long, Container>> entryIterator;
    private long key;
    private Iterator<Integer> bitIterator = null;
    private boolean stopped = false;

}
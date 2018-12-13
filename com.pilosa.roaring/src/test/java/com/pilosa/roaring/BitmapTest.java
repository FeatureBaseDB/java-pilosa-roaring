package com.pilosa.roaring;

import org.junit.Test;

import java.io.*;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class BitmapTest {
    @Test
    public void addTest() {
        Bitmap bmp = getSampleBitmap();
        List<Long> target = getTargetList();
        BitIterator iter = bmp.iterator();
        List<Long> bits = new ArrayList<>(target.size());
        while (iter.hasNext()) {
            bits.add(iter.next());
        }
        assertEquals(target, bits);
    }

    @Test
    public void serializeTest() throws IOException {
        Bitmap bmp = getSampleBitmap();
        ByteBuffer buf = bmp.serialize();
        byte[] target = getSerializedBitmap();
        byte[] arr = buf.array();
        assertEquals(target.length, arr.length);
        assertArrayEquals(target, arr);
    }

    private Bitmap getSampleBitmap() {
        List<Long> arr = getTargetList();
        Bitmap bmp = new Bitmap();
        for (long bit : arr) {
            bmp.add(bit);
        }
        return bmp;
    }

    private byte[] getSerializedBitmap() throws IOException {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        URL uri = loader.getResource("serialized.bitmap");
        if (uri == null) {
            fail("serialized.bitmap not found");
        }

        InputStream inStream = new FileInputStream(uri.getPath());
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        byte []buf = new byte[1024];
        int read = 0;
        while ((read = inStream.read(buf)) > 0) {
            outputStream.write(buf, 0, read);
        }
        return outputStream.toByteArray();
    }

    private List<Long> getTargetList() {
        ArrayList<Long> arr = new ArrayList<>();
        for (long i = 0; i < 4096; i++) {
            arr.add(i);
        }
        for (long i = 1L << 32; i < (1L << 32) + 8193L; i += 2) {
            arr.add(i);
        }
        arr.add(-1L);
        return arr;
    }
}

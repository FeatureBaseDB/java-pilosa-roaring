package com.pilosa.roaring;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static com.pilosa.roaring.Container.TYPE_BITMAP;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class ContainerTest {
    @Test
    public void addTest() {
        Container con = new Container();
        con.add(42);
        con.add(65534);
        con.add(65535);
        Iterator<Integer> iter = con.iterator();
        List<Long> bits = new ArrayList<>(3);
        while (iter.hasNext()) {
            bits.add((long)iter.next());
        }
        Long[] target = new Long[]{42L, 65534L, 65535L};
        assertArrayEquals(target, bits.toArray());
    }

    @Test
    public void addTestIssue5() {
        // Tests https://github.com/pilosa/java-pilosa-roaring/issues/5
        Container con = new Container();
        for (long i = 0; i < 65535; i++) {
            if (i % 5 != 0) {
                con.add(i);
            }
        }
        Iterator<Integer> iter = con.iterator();
        List<Long> bits = new ArrayList<>();
        while (iter.hasNext()) {
            bits.add((long)iter.next());
        }
        List<Long> target = new ArrayList<>();
        for (long i = 0; i < 65535; i++) {
            if (i % 5 != 0) {
                target.add(i);
            }
        }

        assertArrayEquals(target.toArray(), bits.toArray());
        ContainerMeta p = con.serializedProperties();
        assertEquals(p.type, TYPE_BITMAP);
        assertEquals(target.size(), p.bit_count);
        assertEquals(8192, p.size);
    }
}

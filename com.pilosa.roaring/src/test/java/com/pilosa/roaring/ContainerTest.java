package com.pilosa.roaring;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;

public class ContainerTest {
    @Test
    public void addTest() {
        Container con = new Container();
        con.add((short)42);
        con.add((short)15534);
        con.add((short)15535);
        Iterator<Integer> iter = con.iterator();
        List<Long> bits = new ArrayList<>(3);
        while (iter.hasNext()) {
            bits.add((long)iter.next());
        }
        Long target[] = new Long[] {42L, 15534L, 15535L};
        assertArrayEquals(target, bits.toArray());
    }
}

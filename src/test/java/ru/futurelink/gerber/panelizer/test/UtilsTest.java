package ru.futurelink.gerber.panelizer.test;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import ru.futurelink.gerber.panelizer.gui.Utils;

public class UtilsTest {

    @Test
    void testQRange() {
        var r1 = new Utils.QRange(-5, 5);
        var r2 = new Utils.QRange(-3, 3);

        assertTrue(r1.contains(0));
        assertTrue(r1.contains(-5));
        assertTrue(r1.contains(5));
        assertTrue(r1.contains(-3));
        assertTrue(r1.contains(3));
        assertFalse(r1.contains(-5.001));
        assertFalse(r1.contains(5.001));

        var i = r1.intersection(r2);
        assertNotNull(i);
        assertEquals(i.x1(), -3);
        assertEquals(i.x2(), 3);

        i = r2.intersection(r1);
        assertNotNull(i);
        assertEquals(i.x1(), -3);
        assertEquals(i.x2(), 3);

        r2 = new Utils.QRange(-3, 8);
        i = r2.intersection(r1);
        assertNotNull(i);
        assertEquals(i.x1(), -3);
        assertEquals(i.x2(), 5);

        r2 = new Utils.QRange(-8, 3);
        i = r2.intersection(r1);
        assertNotNull(i);
        assertEquals(i.x1(), -5);
        assertEquals(i.x2(), 3);
    }
}

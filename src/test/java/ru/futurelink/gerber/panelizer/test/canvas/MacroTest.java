package ru.futurelink.gerber.panelizer.test.canvas;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import ru.futurelink.gerber.panelizer.canvas.Macro;

public class MacroTest {
        @Test
        void operationFromStringTest() {
            var testSet = new Double[] { 1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0, 10.0 };

            var t = Macro.Operation.fromString("4,1,4,$2,$3,$4,$5,$6,$7,$8+$7,$9,$2,$3,0");
            assertNotNull(t);

            var r = t.eval(testSet);
            assertTrue(r.getExposure());
            assertEquals(Macro.Operation.Type.Outline, r.getType());
            assertEquals(4, r.getValue(0));
            assertEquals(2.0, r.getValue(1));
            assertEquals(6.0, r.getValue(5));
            assertEquals(15.0, r.getValue(7));
            assertEquals(0, r.getValue(11));

            t = Macro.Operation.fromString("1 , 0 ,  $2 , $7, $8 x $7,0");
            assertNotNull(t);

            r = t.eval(testSet);
            assertFalse(r.getExposure());
            assertEquals(Macro.Operation.Type.Circle, r.getType());
            assertEquals(2.0, r.getValue(0));
            assertEquals(7.0, r.getValue(1));
            assertEquals(56.0, r.getValue(2));
        }
}

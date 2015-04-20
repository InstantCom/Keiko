import junit.framework.TestCase;

import net.instantcom.util.BitField;

public class TestBitField extends TestCase {

    public void test() {
        BitField bf = new BitField(11);
        assertEquals(11, bf.getSize());
        assertEquals(2, bf.getData().length);
        assertTrue(bf.allBitsCleared());
        assertFalse(bf.allBitsSet());
        assertEquals(11, bf.getNumZeroes());
        assertEquals(0, bf.getNumOnes());
        for (int i = 0; i < 11; i++) {
            assertFalse(bf.get(i));
        }
        for (int i = 0; i < 11; i += 2) {
            bf.set(i);
            assertTrue(bf.get(i));
        }
        assertFalse(bf.allBitsCleared());
        assertFalse(bf.allBitsSet());
        assertEquals(5, bf.getNumZeroes());
        assertEquals(6, bf.getNumOnes());
        bf = bf.and(bf);
        assertFalse(bf.allBitsCleared());
        assertFalse(bf.allBitsSet());
        assertEquals(5, bf.getNumZeroes());
        assertEquals(6, bf.getNumOnes());
        bf = bf.not();
        assertFalse(bf.allBitsCleared());
        assertFalse(bf.allBitsSet());
        assertEquals(6, bf.getNumZeroes());
        assertEquals(5, bf.getNumOnes());
        bf = bf.not();
        bf = bf.and(bf);
        assertFalse(bf.allBitsCleared());
        assertFalse(bf.allBitsSet());
        assertEquals(5, bf.getNumZeroes());
        assertEquals(6, bf.getNumOnes());
        for (int i = 0; i < 11; i += 2) {
            bf.clear(i);
            assertFalse(bf.get(i));
        }
        assertTrue(bf.allBitsCleared());
        assertFalse(bf.allBitsSet());
        assertEquals(11, bf.getNumZeroes());
        assertEquals(0, bf.getNumOnes());
        for (int i = 0; i < 11; i++) {
            bf.set(i);
            assertTrue(bf.get(i));
        }
        assertFalse(bf.allBitsCleared());
        assertTrue(bf.allBitsSet());
        assertEquals(0, bf.getNumZeroes());
        assertEquals(11, bf.getNumOnes());
    }

}

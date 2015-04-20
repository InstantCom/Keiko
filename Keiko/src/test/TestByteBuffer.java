import java.nio.ByteBuffer;

import junit.framework.TestCase;

public class TestByteBuffer extends TestCase {

    public void test() {
        ByteBuffer buf = ByteBuffer.allocate(10);
        assertFalse(buf.isDirect());
        assertEquals(0, buf.position());
        assertEquals(10, buf.limit());
        assertEquals(10, buf.remaining());
        buf.putInt(0xcafebabe);
        assertEquals(4, buf.position());
        buf.putShort((short) 0xbaba);
        assertEquals(6, buf.position());
        buf.put((byte) 0x33);
        assertEquals(7, buf.position());
        assertEquals(10, buf.limit());
        assertEquals(3, buf.remaining());
        buf.flip();
        assertEquals(0, buf.position());
        assertEquals(7, buf.limit());
        assertEquals(7, buf.remaining());
        int i = buf.getInt();
        assertEquals(0xcafebabe, i);
        assertEquals(4, buf.position());
        short j = buf.getShort();
        assertEquals((short) 0xbaba, j);
        assertEquals(6, buf.position());
        byte b = buf.get();
        assertEquals(0x33, b);
        assertEquals(7, buf.position());
        assertEquals(0, buf.remaining());
        buf.clear();
        assertEquals(0, buf.position());
        assertEquals(10, buf.limit());
        assertEquals(10, buf.remaining());
    }

}

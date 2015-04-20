import junit.framework.TestCase;

import net.instantcom.keiko.ipfilter.IP;
import net.instantcom.keiko.ipfilter.IPFilter;
import net.instantcom.keiko.ipfilter.IPRange;

public class TestIPFilter extends TestCase {

    public void test() {
        IPFilter filter = IPFilter.getInstance();
        assertEquals(0, filter.size()); // make sure Keiko is not started
        assertFalse(filter.isBlocking(new IP("192.168.0.1")));
        filter
            .add(new IPRange(new IP("192.168.0.10"), new IP("192.168.10.200")));
        filter.add(new IP("123.123.123.123"));
        assertFalse(filter.isBlocking(new IP("192.168.0.1")));
        assertFalse(filter.isBlocking(new IP("192.168.0.9")));
        assertTrue(filter.isBlocking(new IP("192.168.0.10")));
        assertTrue(filter.isBlocking(new IP("192.168.0.100")));
        assertTrue(filter.isBlocking(new IP("192.168.0.200")));
        assertTrue(filter.isBlocking(new IP("192.168.0.255")));
        assertTrue(filter.isBlocking(new IP("192.168.5.0")));
        assertTrue(filter.isBlocking(new IP("192.168.10.200")));
        assertFalse(filter.isBlocking(new IP("192.168.10.201")));
        assertFalse(filter.isBlocking(new IP("192.168.10.255")));
        assertFalse(filter.isBlocking(new IP("111.111.111.111")));
        assertFalse(filter.isBlocking(new IP("222.222.222.222")));
        assertTrue(filter.isBlocking(new IP("123.123.123.123")));
    }

}

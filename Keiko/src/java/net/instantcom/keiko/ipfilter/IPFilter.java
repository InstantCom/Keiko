package net.instantcom.keiko.ipfilter;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public final class IPFilter {

    private static final Log log = LogFactory.getLog(IPFilter.class);
    private static final IPFilter instance = new IPFilter();

    private IPFilter() {
    }

    /**
     * Gets instance of IP filter.
     * 
     * @return instance
     */
    public static IPFilter getInstance() {
        return instance;
    }

    /**
     * Clears all entries in the blocking list.
     */
    public void clear() {
        ranges.clear();
    }

    /**
     * Returns size of list.
     * 
     * @return size
     */
    public int size() {
        return ranges.size();
    }

    /**
     * Adds IP ranges specified as comma separated values.
     * 
     * @param list
     *            comma separated ip ranges
     */
    public void add(String list) {
        if (null == list) {
            return;
        }
        for (String s : list.split(",")) {
            try {
                int dash = s.indexOf('-');
                if (dash >= 0) {
                    // ip range
                    IP start = new IP(s.substring(0, dash).trim());
                    IP end = new IP(s.substring(1 + dash).trim());
                    add(new IPRange(start, end));
                } else {
                    // single ip
                    add(new IP(s.trim()));
                }
            } catch (Exception e) {
                log.warn("'" + s + "' is not valid IP nor IP range");
            }
        }
    }

    /**
     * Adds new IP range to the blocking list.
     * 
     * @param range
     *            IP range to add
     */
    public void add(IPRange range) {
        ranges.add(range);
    }

    /**
     * Adds new IP to the blocking list.
     * 
     * @param ip
     *            IP to add
     */
    public void add(IP ip) {
        ranges.add(new IPRange(ip, ip));
    }

    /**
     * Checks if filter is blocking specified IP.
     * 
     * @param ip
     *            IP to check
     * @return true if filter is blocking specified IP
     */
    public boolean isBlocking(IP ip) {
        for (IPRange range : ranges) {
            if (range.isBlocking(ip)) {
                return true;
            }
        }
        return false;
    }

    private final List<IPRange> ranges = new ArrayList<IPRange>();

}

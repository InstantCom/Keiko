package net.instantcom.keiko.ipfilter;

public class IPRange {

    /**
     * Creates new IP range.
     * 
     * @param start
     *            first IP in range
     * @param end
     *            last IP in range
     */
    public IPRange(IP start, IP end) {
        this.start = start;
        this.end = end;
        if (start.compareTo(end) > 0) {
            throw new IllegalArgumentException("start > end : " + start + "-"
                + start);
        }
    }

    /**
     * Gets first IP in range.
     * 
     * @return start IP
     */
    public IP getStart() {
        return start;
    }

    /**
     * Gets last IP in range.
     * 
     * @return end IP
     */
    public IP getEnd() {
        return end;
    }

    /**
     * Checks if this IP range is blocking specified IP.
     * 
     * @param ip
     *            IP to check
     * @return true if blocking
     */
    public boolean isBlocking(IP ip) {
        return start.compareTo(ip) <= 0 && end.compareTo(ip) >= 0;
    }

    private IP start;
    private IP end;

}

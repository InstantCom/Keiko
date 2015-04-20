package net.instantcom.keiko.ipfilter;

public class IP implements Comparable<IP> {

    /**
     * Creates new IP from specified String.
     * 
     * @param ip
     *            IP
     * @throws IllegalArgumentException
     *             if specified IP is not a valid IP
     */
    public IP(String ip) throws IllegalArgumentException {
        // convert to 000.000.000.000 format
        StringBuffer sb = new StringBuffer();
        int count = 0;
        for (String s : ip.split("\\.")) {
            if (3 != s.length()) {
                s = "000" + s;
                s = s.substring(s.length() - 3);
            }
            sb.append(s);
            if (count < 3) {
                sb.append(".");
            }
            ++count;
        }
        if (4 != count) {
            throw new IllegalArgumentException(ip + " is not valid IP");
        }
        this.ip = sb.toString();
    }

    /**
     * Gets IP as String in 000.000.000.000 format.
     * 
     * @return ip in 000.000.000.000 format
     */
    public String getIP() {
        return ip;
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Comparable#compareTo(java.lang.Object)
     */
    @Override
    public int compareTo(IP other) {
        return ip.compareTo(other.getIP());
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return ip;
    }

    private String ip;

}

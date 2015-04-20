package net.instantcom.keikosniffer.analyzer;

public class Host {

    public Host(String host) {
        if (null == host) {
            throw new IllegalArgumentException("host == null");
        }
        this.host = host;
    }

    public String getHost() {
        return host;
    }

    public int getReferenceCount() {
        return referenceCount;
    }

    public void incReferenceCount() {
        ++referenceCount;
    }

    @Override
    public boolean equals(Object obj) {
        if (null == obj) {
            return false;
        }
        if (!(obj instanceof Host)) {
            return false;
        }
        Host other = (Host) obj;
        return host.equals(other.getHost());
    }

    private String host;
    private int referenceCount;

}

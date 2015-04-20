package net.instantcom.keiko.peer;

public final class Peer {

    public static final int MAX_CONNECT_ERRORS = 3;

    public Peer(String host, int port) {
        this.host = host;
        this.port = port;
        supportsCrypto = true;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public boolean isSupportingCrypto() {
        return supportsCrypto;
    }

    public void setSupportsCrypto(boolean supportsCrypto) {
        this.supportsCrypto = supportsCrypto;
    }

    public int getConnectErrors() {
        return connectErrors;
    }

    public void incConnectErrors() {
        ++connectErrors;
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Object#equals(java.lang.Object)
     */
    @Override
    public boolean equals(Object obj) {
        if (null == obj) {
            return false;
        }
        Peer other = (Peer) obj;
        return host.equals(other.getHost());
    }

    private String host;
    private int port;
    private boolean supportsCrypto;
    private int connectErrors;

}

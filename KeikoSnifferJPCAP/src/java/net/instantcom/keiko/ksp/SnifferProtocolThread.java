package net.instantcom.keiko.ksp;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

public class SnifferProtocolThread extends ProtocolThread {

    private static final int INPUT_BUFFER_SIZE = 1024; // not receiving anything
    private static final int OUTPUT_BUFFER_SIZE = 4 * 1024 * 1024; // 4 MB should be enough

    protected static final long MIN_CONNECT_INTERVAL = 30000L; // 30 sec

    public SnifferProtocolThread(String host, int port) throws IOException {
        super("SnifferProtocolThread-" + nextThreadNum());
        this.host = host;
        this.port = port;
    }

    public ConnectionState getThreadSpecificConnectionState() {
        ConnectionState state;
        synchronized (connectionStates) {
            state = connectionStates.get(snifferSocket);
        }
        return state;
    }

    public void disconnect() {
        ConnectionState state = getThreadSpecificConnectionState();
        if (null != state) {
            state.setConnected(false);
        }
        if (null != snifferSocket) {
            close(snifferSocket);
            snifferSocket = null;
        }
    }

    @Override
    protected void reconnect() throws IOException {
        if (null == getThreadSpecificConnectionState() && null != host && 0 != port
            && System.currentTimeMillis() > nextAllowedConnectTime) {
            if (null != snifferSocket) {
                try {
                    snifferSocket.close();
                } catch (IOException ignored) {
                }
                snifferSocket = null;
            }
            SocketChannel socket = SocketChannel.open();
            socket.socket().setKeepAlive(false);
            socket.socket().setTcpNoDelay(true);
            socket.configureBlocking(false);
            socket.register(selector, SelectionKey.OP_CONNECT);
            socket.connect(new InetSocketAddress(host, port));
            snifferSocket = socket;
        }
    }

    @Override
    protected int getConnectableOps() {
        return SelectionKey.OP_READ | SelectionKey.OP_WRITE;
    }

    @Override
    protected void doHandshake(ConnectionState state) throws Exception {
        SocketChannel socket = state.getSocketChannel();
        ByteBuffer inputBuffer = state.getInputBuffer();
        ByteBuffer outputBuffer = state.getOutputBuffer();
        if (!state.isOwnHandshakeSent()) {
            synchronized (outputBuffer) {
                outputBuffer.putInt(HANDSHAKE_BASE | HANDSHAKE_VERSION);
            }
            write(state);
            state.setOwnHandshakeSent(true);
            socket.register(selector, SelectionKey.OP_READ);
        } else {
            read(state);
            inputBuffer.flip();
            if (inputBuffer.remaining() >= 4) {
                int handshake = inputBuffer.getInt();
                if (HANDSHAKE_BASE != (handshake & 0xffffff00)) {
                    throw new Exception("handshake error: unrecognized handshake");
                }
                int version = handshake & 0xff;
                if (version <= 0) {
                    throw new Exception("handshake error: incorrect version: " + version);
                }
                state.setAgreedVersion(Math.min(version, HANDSHAKE_VERSION));
                state.setRemoteHandshakeReceived(true);
            }
            inputBuffer.compact();
        }
        if (state.isOwnHandshakeSent() && state.isRemoteHandshakeReceived()) {
            state.setHandshakeCompleted(true);
            socket.register(selector, SelectionKey.OP_READ);
            if (log.isDebugEnabled()) {
                log.debug(getName() + " handshake completed");
            }
        }
    }

    @Override
    protected void close(SocketChannel socket) {
        super.close(socket);
        // don't try to reconnect too soon
        nextAllowedConnectTime = System.currentTimeMillis() + MIN_CONNECT_INTERVAL;
    }

    @Override
    protected ConnectionState createConnectionState() {
        return new ConnectionState(INPUT_BUFFER_SIZE, OUTPUT_BUFFER_SIZE);
    }

    @Override
    public void sendMessage(Message msg) throws Exception {
        msg.setSocketChannel(snifferSocket);
        super.sendMessage(msg);
    }

    private static synchronized int nextThreadNum() {
        return threadInitNumber++;
    }

    private String host;
    private int port;
    private long nextAllowedConnectTime;
    private SocketChannel snifferSocket;

    private static int threadInitNumber;

}

package net.instantcom.keiko.ksp;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

public class KeikoProtocolThread extends ProtocolThread {

    private static final int INPUT_BUFFER_SIZE = 4 * 1024 * 1024; // 4 MB should be enough
    private static final int OUTPUT_BUFFER_SIZE = 1024; // not sending anything

    public KeikoProtocolThread() throws IOException {
        super("KeikoProtocolThread-" + nextThreadNum());
        ServerSocketChannel serverSocket = ServerSocketChannel.open();
        serverSocket.socket().bind(new InetSocketAddress(SERVER_PORT));
        serverSocket.configureBlocking(false);
        serverSocket.register(selector, SelectionKey.OP_ACCEPT);
    }

    @Override
    protected void reconnect() throws IOException {
        // keiko never (re)connects
    }

    @Override
    protected int getConnectableOps() {
        return SelectionKey.OP_READ;
    }

    @Override
    protected void doHandshake(ConnectionState state) throws Exception {
        SocketChannel socket = state.getSocketChannel();
        ByteBuffer inputBuffer = state.getInputBuffer();
        ByteBuffer outputBuffer = state.getOutputBuffer();
        read(state);
        inputBuffer.flip();
        if (inputBuffer.remaining() >= 4) {
            int handshake = inputBuffer.getInt();
            if (HANDSHAKE_BASE != (handshake & 0xffffff00)) {
                throw new Exception("handshake error: unrecognized handshake: "
                    + Integer.toHexString(handshake));
            }
            int version = handshake & 0xff;
            if (version <= 0) {
                throw new Exception("handshake error: incorrect version: " + version);
            }
            state.setAgreedVersion(Math.min(version, HANDSHAKE_VERSION));
            state.setRemoteHandshakeReceived(true);
            synchronized (outputBuffer) {
                outputBuffer.putInt(HANDSHAKE_BASE | state.getAgreedVersion());
            }
            write(state);
            state.setOwnHandshakeSent(true);
            socket.register(selector, SelectionKey.OP_READ);
        }
        inputBuffer.compact();
        if (state.isOwnHandshakeSent() && state.isRemoteHandshakeReceived()) {
            state.setHandshakeCompleted(true);
            socket.register(selector, SelectionKey.OP_READ);
            if (log.isDebugEnabled()) {
                log.debug(getName() + " handshake completed");
            }
        }
    }

    @Override
    protected ConnectionState createConnectionState() {
        return new ConnectionState(INPUT_BUFFER_SIZE, OUTPUT_BUFFER_SIZE);
    }

    private static synchronized int nextThreadNum() {
        return threadInitNumber++;
    }

    private static int threadInitNumber;

}

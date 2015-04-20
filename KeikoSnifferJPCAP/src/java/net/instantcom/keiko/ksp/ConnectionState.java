package net.instantcom.keiko.ksp;

import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

public class ConnectionState {

    public ConnectionState(int inputBufferSize, int outputBufferSize) {
        this.inputBufferSize = inputBufferSize;
        this.outputBufferSize = outputBufferSize;
        inputBuffer = ByteBuffer.allocate(inputBufferSize);
        outputBuffer = ByteBuffer.allocate(outputBufferSize);
    }

    public int getInputBufferSize() {
        return inputBufferSize;
    }

    public int getOutputBufferSize() {
        return outputBufferSize;
    }

    public boolean isConnected() {
        return connected;
    }

    public void setConnected(boolean connected) {
        this.connected = connected;
        if (connected) {
            connectionEstablishedTime = System.currentTimeMillis();
        }
    }

    public SocketChannel getSocketChannel() {
        return socketChannel;
    }

    public void setSocketChannel(SocketChannel socketChannel) {
        this.socketChannel = socketChannel;
    }

    public long getConnectionEstablishedTime() {
        return connectionEstablishedTime;
    }

    public int getAgreedVersion() {
        return agreedVersion;
    }

    public void setAgreedVersion(int agreedVersion) {
        this.agreedVersion = agreedVersion;
    }

    public boolean isOwnHandshakeSent() {
        return ownHandshakeSent;
    }

    public void setOwnHandshakeSent(boolean ownHandshakeSent) {
        this.ownHandshakeSent = ownHandshakeSent;
    }

    public boolean isRemoteHandshakeReceived() {
        return remoteHandshakeReceived;
    }

    public void setRemoteHandshakeReceived(boolean remoteHandshakeReceived) {
        this.remoteHandshakeReceived = remoteHandshakeReceived;
    }

    public boolean isHandshakeCompleted() {
        return handshakeCompleted;
    }

    public void setHandshakeCompleted(boolean handshakeCompleted) {
        this.handshakeCompleted = handshakeCompleted;
    }

    protected ByteBuffer getInputBuffer() {
        return inputBuffer;
    }

    protected ByteBuffer getOutputBuffer() {
        return outputBuffer;
    }

    private boolean connected;
    private SocketChannel socketChannel;
    private long connectionEstablishedTime;
    private int agreedVersion;
    private boolean ownHandshakeSent;
    private boolean remoteHandshakeReceived;
    private boolean handshakeCompleted;

    private int inputBufferSize;
    private int outputBufferSize;
    private ByteBuffer inputBuffer;
    private ByteBuffer outputBuffer;

}

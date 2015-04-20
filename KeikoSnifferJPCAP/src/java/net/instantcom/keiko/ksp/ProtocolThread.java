package net.instantcom.keiko.ksp;

import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;

import net.instantcom.util.LRUCache;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public abstract class ProtocolThread extends Thread {

    protected static final Log log = LogFactory.getLog(ProtocolThread.class);

    private static final int MAX_CONNECTIONS = 8;

    protected static final int HANDSHAKE_BASE = 0x4b535000; // KSP
    protected static final int HANDSHAKE_VERSION = 0x01; // v0.1

    public static final int SERVER_PORT = 7777;

    private static final long HANDSHAKE_TIMEOUT = 5000L; // 5 sec

    public ProtocolThread(String name) throws IOException {
        super(name);
        selector = Selector.open();
    }

    public void setListener(ProtocolListener listener) {
        this.listener = listener;
    }

    private ConnectionState getFirstValidConnectionState() {
        ConnectionState state = null;
        synchronized (connectionStates) {
            // find first valid state
            for (ConnectionState cs : connectionStates.values()) {
                if (null != cs && cs.isConnected() && cs.isHandshakeCompleted()) {
                    state = cs;
                    break;
                }
            }
        }
        return state;
    }

    public void sendMessage(Message msg) throws Exception {
        ConnectionState state = null;
        synchronized (connectionStates) {
            state = connectionStates.get(msg.getSocketChannel());
        }
        if (null == state) {
            throw new Exception("error sending message: no connection found");
        }
        if (!state.isConnected() || null == state.getSocketChannel()) {
            throw new Exception("not connected");
        }
        ByteBuffer outputBuffer = state.getOutputBuffer();
        synchronized (outputBuffer) {
            try {
                outputBuffer.mark();
                int len = msg.getData().length;
                if (0 == len) {
                    throw new Exception("nothing to send: message has no data");
                }
                outputBuffer.putInt(13 + len);
                outputBuffer.put((byte) (msg.getType() & 0xff));
                outputBuffer.putInt(msg.getSourceIp());
                outputBuffer.putShort((short) (msg.getSourcePort() & 0xffff));
                outputBuffer.putInt(msg.getDestinationIp());
                outputBuffer.putShort((short) (msg.getDestinationPort() & 0xffff));
                outputBuffer.put(msg.getData());
                if (log.isDebugEnabled()) {
                    log.debug(getName() + " sent message: len=" + len + "  type=" + msg.getType());
                }
                state.getSocketChannel().register(selector,
                    SelectionKey.OP_READ | SelectionKey.OP_WRITE);
            } catch (Exception e) {
                if (log.isDebugEnabled()) {
                    log.debug("sendMessage", e);
                }
                outputBuffer.reset();
                throw e;
            }
        }
    }

    @Override
    public void interrupt() {
        running = false;
        super.interrupt();
    }

    public void shutdown() {
        running = false;
    }

    protected abstract void reconnect() throws IOException;

    protected abstract int getConnectableOps();

    protected abstract void doHandshake(ConnectionState state) throws Exception;

    protected abstract ConnectionState createConnectionState();

    @Override
    public void run() {
        running = true;
        log.info(getName() + " started");

        while (running) {
            SocketChannel socket = null;
            SelectionKey key = null;
            try {
                if (0 == selector.select(100L)) {
                    if (!running) {
                        break;
                    }
                    reconnect();
                    // cleanup disconnected and expired handshakes
                    synchronized (connectionStates) {
                        for (ConnectionState state : connectionStates.values()) {
                            if (null != state) {
                                if (!state.isConnected() && state.isHandshakeCompleted()) {
                                    throw new Exception("cleaning up after disconnect");
                                } else if (state.isConnected()
                                    && !state.isHandshakeCompleted()
                                    && (System.currentTimeMillis() - state
                                        .getConnectionEstablishedTime()) > HANDSHAKE_TIMEOUT) {
                                    socket = state.getSocketChannel();
                                    throw new Exception("handshake timeout");
                                }
                            }
                        }
                    }
                    continue;
                }

                Iterator<SelectionKey> it = selector.selectedKeys().iterator();
                while (it.hasNext()) {
                    key = it.next();
                    it.remove();

                    if (!key.isValid()) {
                        continue;
                    }

                    // acceptable
                    if (key.isAcceptable()) {
                        ServerSocketChannel server = (ServerSocketChannel) key.channel();
                        socket = server.accept();
                        if (null != socket) {
                            log.info(getName() + ": incoming connection from "
                                + socket.socket().getInetAddress().getHostAddress() + ":"
                                + socket.socket().getPort());
                            // if (connected) {
                            // throw new Exception("connection refused: already connected");
                            // }
                            socket.socket().setKeepAlive(false);
                            socket.socket().setTcpNoDelay(true);
                            socket.configureBlocking(false);
                            socket.register(selector, SelectionKey.OP_READ);
                            ConnectionState state = createConnectionState();
                            state.setConnected(true);
                            state.setSocketChannel(socket);
                            synchronized (connectionStates) {
                                connectionStates.put(socket, state);
                            }
                        }
                    }

                    // connectable
                    if (key.isConnectable()) {
                        socket = (SocketChannel) key.channel();
                        if (socket.finishConnect()) {
                            socket.register(selector, getConnectableOps());
                            log.info(getName() + ": connected to "
                                + socket.socket().getInetAddress().getHostAddress() + ":"
                                + socket.socket().getPort());
                            ConnectionState state = createConnectionState();
                            state.setConnected(true);
                            state.setSocketChannel(socket);
                            synchronized (connectionStates) {
                                connectionStates.put(socket, state);
                            }
                        } else {
                            throw new IOException("can't finish connecting on outbound: "
                                + socket.socket().getInetAddress().getHostAddress() + ":"
                                + socket.socket().getPort());
                        }
                    }

                    // readable or writable
                    if (key.isReadable() || key.isWritable()) {
                        socket = (SocketChannel) key.channel();
                        ConnectionState state;
                        synchronized (connectionStates) {
                            state = connectionStates.get(socket);
                        }
                        if (null == state) {
                            // sorry mate
                            throw new Exception("connection state not found");
                        }
                        ByteBuffer inputBuffer = state.getInputBuffer();
                        if (!state.isHandshakeCompleted()) {
                            doHandshake(state);
                        } else {
                            if (write(state)) {
                                socket.register(selector, SelectionKey.OP_READ);
                            }
                            int bytesRead = read(state);
                            inputBuffer.flip();
                            while (inputBuffer.remaining() > 4) {
                                inputBuffer.mark();
                                try {
                                    Message msg = new Message(inputBuffer);
                                    msg.setProtocolThread(this);
                                    msg.setSocketChannel(state.getSocketChannel());
                                    listener.onMessage(msg);
                                } catch (BufferUnderflowException e) {
                                    inputBuffer.reset();
                                    break;
                                }
                            }
                            if (log.isDebugEnabled()) {
                                log.debug("read " + bytesRead + " bytes  remaining="
                                    + inputBuffer.remaining());
                            }
                            inputBuffer.compact();
                        }
                    }
                }
            } catch (Exception e) {
                if (null != e.getMessage() && e.getMessage().indexOf("Connection refused") >= 0) {
                    log.warn(getName() + ": can't connect to keiko, will retry in "
                        + (SnifferProtocolThread.MIN_CONNECT_INTERVAL / 1000) + " seconds");
                } else {
                    log.error(getName(), e);
                }
                // if (null != key) {
                // key.cancel();
                // }
                if (null != socket) {
                    close(socket);
                }
            }
        }

        // close selector
        try {
            selector.close();
        } catch (IOException ignored) {
        }
        log.info(getName() + " stopped");
    }

    protected void close(SocketChannel socket) {
        log.info(getName() + " closing socket");
        // // cancel all registrations
        // try {
        // socket.register(selector, 0);
        // } catch (Exception ignored) {
        // }
        // close socket
        try {
            socket.close();
        } catch (IOException ignored) {
        }
        // remove state
        synchronized (connectionStates) {
            connectionStates.remove(socket);
        }
    }

    protected int read(ConnectionState conn) throws Exception {
        ByteBuffer inputBuffer = conn.getInputBuffer();
        int bytesRead = conn.getSocketChannel().read(inputBuffer);
        if (-1 == bytesRead) {
            throw new Exception("connection closed by remote host");
        }
        return bytesRead;
    }

    protected boolean write(ConnectionState conn) throws Exception {
        boolean writeCompleted;
        ByteBuffer outputBuffer = conn.getOutputBuffer();
        synchronized (outputBuffer) {
            outputBuffer.flip();
            conn.getSocketChannel().write(outputBuffer);
            writeCompleted = 0 == outputBuffer.remaining();
            if (log.isDebugEnabled()) {
                log.debug("writeCompleted=" + writeCompleted + "  remaining="
                    + outputBuffer.remaining());
            }
            outputBuffer.compact();
        }
        return writeCompleted;
    }

    public boolean isHandshakeCompleted() {
        ConnectionState state = getFirstValidConnectionState();
        return null != state && state.isHandshakeCompleted();
    }

    private ProtocolListener listener;
    protected Selector selector;
    private volatile boolean running;
    protected final LRUCache<SocketChannel, ConnectionState> connectionStates =
        new LRUCache<SocketChannel, ConnectionState>(MAX_CONNECTIONS);

}

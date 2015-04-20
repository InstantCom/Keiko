package net.instantcom.keiko.bittorrent.protocol;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;
import java.util.Arrays;
import java.util.HashMap;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import net.instantcom.keiko.Server;
import net.instantcom.keiko.bittorrent.MetaInfo;
import net.instantcom.keiko.bittorrent.extensions.Extensions;
import net.instantcom.keiko.ipfilter.IP;
import net.instantcom.keiko.ipfilter.IPFilter;
import net.instantcom.util.BitField;
import net.instantcom.util.SHA1Util;

public class PeerConnection {

    private static final Log log = LogFactory.getLog(PeerConnection.class);

    // socket buffer size makes difference between 1 MB/s and 11 MB/s
    public static final int SOCKET_MIN_BUFFER_SIZE = 64512; // 16400;

    /**
     * Creates new peer connection for specified socket, using socket's streams.
     * 
     * @param socket
     *            socket
     * @param incoming
     *            true if connection is incoming
     */
    public PeerConnection(Socket socket, boolean incoming) {
        this.socket = socket;
        this.incoming = incoming;
        try {
            dis = new DataInputStream(socket.getInputStream());
            dos = new DataOutputStream(socket.getOutputStream());
        } catch (IOException e) {
            close();
        }
        fixSocketBuffers();
    }

    /**
     * Creates new peer connection for specified socket, using existing
     * (possibly encrypted) streams.
     * 
     * @param socket
     *            socket
     * @param incoming
     *            true if connection is incoming
     * @param dis
     *            data input stream
     * @param dos
     *            data output stream
     */
    public PeerConnection(Socket socket, boolean incoming, DataInputStream dis,
        DataOutputStream dos) {
        this.socket = socket;
        this.incoming = incoming;
        this.dis = dis;
        this.dos = dos;
        fixSocketBuffers();
    }

    private void fixSocketBuffers() {
        try {
            if (socket.getReceiveBufferSize() < SOCKET_MIN_BUFFER_SIZE) {
                socket.setReceiveBufferSize(SOCKET_MIN_BUFFER_SIZE);
            }
            if (socket.getSendBufferSize() < SOCKET_MIN_BUFFER_SIZE) {
                socket.setSendBufferSize(SOCKET_MIN_BUFFER_SIZE);
            }
        } catch (SocketException e) {
            if (log.isDebugEnabled()) {
                log.debug("can't fix socket buffers");
            }
        }
    }

    protected boolean isIncoming() {
        return incoming;
    }

    public boolean isEncrypted() {
        return encrypted;
    }

    public void setEncrypted(boolean encrypted) {
        this.encrypted = encrypted;
    }

    public boolean isFullyEncrypted() {
        return fullyEncrypted;
    }

    public void setFullyEncrypted(boolean fullyEncrypted) {
        this.fullyEncrypted = fullyEncrypted;
    }

    /**
     * Does BitTorrent handshake.
     * 
     * @return arranged orrent
     * @throws HandshakeException
     *             if handshake failed
     */
    public Torrent doHandshake() throws HandshakeException {
        try {
            int[] order = new int[2];
            if (incoming) {
                order[0] = 0; // remote
                order[1] = 1; // self
            } else {
                order[0] = 1; // self
                order[1] = 0; // remote
            }
            for (int element : order) {
                if (0 == element) { // remote
                    // first byte must be 19
                    int b = dis.readUnsignedByte();
                    if (19 != b) {
                        throw new HandshakeException("first byte mismatch");
                    }
                    // "BitTorrent protocol" follows
                    byte[] tmp = new byte[19];
                    dis.readFully(tmp);
                    if (!Arrays.equals(tmp, "BitTorrent protocol".getBytes())) {
                        throw new HandshakeException("wrong bt string");
                    }
                    // 8 bytes of supported extensions
                    dis.readFully(remoteExtensions.getData());
                    // 20 bytes of infoHash
                    dis.readFully(infoHash);
                    if (incoming) {
                        // check if there is an active torrent with such
                        // infoHash
                        Torrent torrent =
                            Server.getTorrents().get(
                                SHA1Util.convertToString(infoHash));
                        if (null == torrent) {
                            // no such torrent
                            throw new HandshakeException("no such torrent");
                        } else {
                            setTorrent(torrent);
                        }
                    } else if (!Arrays.equals(torrent.getMetaInfo()
                        .getInfoHash(), infoHash)) {
                        // wrong torrent
                        throw new HandshakeException(
                            "remote answered with wrong info hash");
                    }
                    // trackers don't send peerId while testing NAT
                    if (dis.available() >= 20) {
                        dis.readFully(remotePeerId);
                        if (log.isDebugEnabled()) {
                            log.debug("got peer id: "
                                + Arrays.toString(remotePeerId));
                        }
                        // trackers send your ip too so be careful not to talk
                        // to yourself ;)
                        if (Arrays.equals(remotePeerId, myPeerId)) {
                            // ban yourself
                            IPFilter.getInstance()
                                .add(
                                    new IP(socket.getInetAddress()
                                        .getHostAddress()));
                            throw new HandshakeException("same id");
                        }
                    } else {
                        if (log.isDebugEnabled()) {
                            log
                                .debug("not reading peer_id because there are only "
                                    + dis.available()
                                    + " available bytes but 20 are needed, it's"
                                    + " probably tracker check");
                        }
                    }
                } else { // self
                    // first byte must be 19
                    dos.writeByte(19);
                    // "BitTorrent protocol" follows
                    dos.write("BitTorrent protocol".getBytes());
                    // 8 bytes of supported extensions
                    dos.write(Extensions.getSupportedExtensions().getData());
                    // 20 bytes of infoHash
                    dos.write(infoHash);
                    // 20 bytes of peerId
                    dos.write(myPeerId);
                    dos.flush();
                }
            }
            // set arranged extensions
            commonExtensions =
                Extensions.getSupportedExtensions().and(remoteExtensions);
            // set remote have bitfield (it is assumed remote doesn't have any)
            remoteHavePieces =
                new BitField(torrent.getMetaInfo().getNumPieces());
            updateMyTrafficTime();
            updateRemoteTrafficTime();
        } catch (IOException e) {
            throw new HandshakeException(e.toString());
        }
        return torrent;
    }

    public void close() {
        try {
            socket.close();
        } catch (IOException ignored) {
        }
        if (null != torrent) {
            torrent.getPiecePicker().cancelReservations(this);
        }
    }

    public void setTorrent(Torrent torrent) throws IOException {
        this.torrent = torrent;
        this.infoHash =
            null != torrent ? torrent.getMetaInfo().getInfoHash() : null;
    }

    public Socket getSocket() {
        return socket;
    }

    public boolean amChoking() {
        return amChoking;
    }

    public boolean amInterested() {
        return amInterested;
    }

    public boolean isChoking() {
        return isChoking;
    }

    public boolean isInterested() {
        return isInterested;
    }

    private void updateSpeedMetrics() {
        // don't update while not active
        if (null == connectionThread) {
            return;
        }
        long now = System.currentTimeMillis();
        // don't update more than once per second
        if (now - lastSpeedMetricsUpdateTime < 1000L) {
            return;
        }
        long diff = now - lastSpeedMetricsUpdateTime;
        uploadRate = (1000L * speedMetricsUploaded) / diff;
        downloadRate = (1000L * speedMetricsDownloaded) / diff;
        lastSpeedMetricsUpdateTime = now;
        speedMetricsUploaded = 0;
        speedMetricsDownloaded = 0;
    }

    private void resetSpeedMetrics() {
        lastSpeedMetricsUpdateTime = System.currentTimeMillis();
        speedMetricsUploaded = speedMetricsDownloaded = 0;
        uploadRate = downloadRate = 0;
    }

    public long getUploadRate() {
        updateSpeedMetrics();
        return uploadRate;
    }

    public long getDownloadRate() {
        updateSpeedMetrics();
        return downloadRate;
    }

    private void updateMyTrafficTime() {
        myLastTrafficTime = System.currentTimeMillis();
    }

    protected long getMyLastTrafficTime() {
        return myLastTrafficTime;
    }

    private void updateRemoteTrafficTime() {
        remoteLastTrafficTime = System.currentTimeMillis();
    }

    protected long getRemoteLastTrafficTime() {
        return remoteLastTrafficTime;
    }

    /**
     * Checks if connection is idle. Connection is assumed idle if there was no
     * traffic at any end for at least 5 minutes.
     * 
     * @return
     */
    public boolean isIdle() {
        final long timeout = 300000L; // 5 minutes
        long now = System.currentTimeMillis();
        long myDiff = now - myLastTrafficTime;
        long remoteDiff = now - remoteLastTrafficTime;
        return myDiff > timeout || remoteDiff > timeout;
    }

    public void keepAlive() throws IOException {
        outputStreamFlushed = false;
        dos.writeInt(0);
        flushOutputStream();
        if (log.isDebugEnabled()) {
            debug("sent: keep-alive");
        }
    }

    public void choke() throws IOException {
        outputStreamFlushed = false;
        checkFibrillation();
        lastChokeChangeTime = System.currentTimeMillis();
        amChoking = true;
        dos.writeInt(1);
        dos.writeByte(0);
        flushOutputStream();
        if (log.isDebugEnabled()) {
            debug("sent: choke");
        }
    }

    public void unchoke() throws IOException {
        outputStreamFlushed = false;
        checkFibrillation();
        lastChokeChangeTime = System.currentTimeMillis();
        amChoking = false;
        dos.writeInt(1);
        dos.writeByte(1);
        flushOutputStream();
        if (log.isDebugEnabled()) {
            debug("sent: unchoke");
        }
    }

    public void interested() throws IOException {
        outputStreamFlushed = false;
        amInterested = true;
        dos.writeInt(1);
        dos.writeByte(2);
        flushOutputStream();
        if (log.isDebugEnabled()) {
            debug("sent: interested");
        }
    }

    public void notInterested() throws IOException {
        outputStreamFlushed = false;
        amInterested = false;
        dos.writeInt(1);
        dos.writeByte(3);
        flushOutputStream();
        if (log.isDebugEnabled()) {
            debug("sent: not interested");
        }
    }

    private void have(int pieceIndex) throws IOException {
        // Because peers are extremely unlikely to download pieces that they
        // already have, a peer may choose not to advertise having a piece to a
        // peer that already has that piece. At a minimum "HAVE supression" will
        // result in a 50% reduction in the number of HAVE messages, this
        // translates to around a 25-35% reduction in protocol overhead.
        // At the same time, it may be worthwhile to send a HAVE message to a
        // peer that has that piece already since it will be useful in
        // determining which piece is rare.
        // from what i've seen, most clients always send have
        // if (!remoteHavePieces.get(pieceIndex)) {
        outputStreamFlushed = false;
        dos.writeInt(5);
        dos.writeByte(4);
        dos.writeInt(pieceIndex);
        // flushOutputStream(); // don't flush (not a priority msg), let
        // them go as bunch
        if (log.isDebugEnabled()) {
            debug("sent: have " + pieceIndex);
        }
        // }
    }

    public void bitfield() throws IOException {
        outputStreamFlushed = false;
        BitField have = torrent.getHavePieces();
        // make a copy
        advertisedPieces = have.and(have);
        byte[] data = have.getData();
        dos.writeInt(1 + data.length);
        dos.writeByte(5);
        dos.write(data);
        flushOutputStream();
        if (log.isDebugEnabled()) {
            debug("sent: bitfield");
        }
    }

    public void request(int pieceIndex, int offset, int size)
        throws IOException {
        outputStreamFlushed = false;
        dos.writeInt(13);
        dos.writeByte(6);
        dos.writeInt(pieceIndex);
        dos.writeInt(offset);
        dos.writeInt(size);
        // flushOutputStream(); // don't flush, let them go as bunch
        // debug("sent: request(" + pieceIndex + ", " + offset + ", " + size +
        // ")");
    }

    public void piece(Piece piece, int offset, int size) throws IOException {
        outputStreamFlushed = false;
        dos.writeInt(9 + size);
        dos.writeByte(7);
        dos.writeInt(piece.getIndex());
        dos.writeInt(offset);
        dos.write(piece.getData(), offset, size);
        flushOutputStream();
        speedMetricsUploaded += size;
        torrent.updateUploadStats(size);
        Server.bandwidthStats.update(0, size);
        // debug("sent: piece(" + piece.getIndex() + ", " + offset + ", " + size
        // + ")");
    }

    public void cancel(int pieceIndex, int offset, int size) throws IOException {
        outputStreamFlushed = false;
        dos.writeInt(13);
        dos.writeByte(8);
        dos.writeInt(pieceIndex);
        dos.writeInt(offset);
        dos.writeInt(size);
        // flushOutputStream(); // don't flush, let them go as bunch
        if (log.isDebugEnabled()) {
            debug("sent: cancel(" + pieceIndex + ", " + offset + ", " + size
                + ")");
        }
    }

    public void port(int port) throws IOException {
        outputStreamFlushed = false;
        dos.writeInt(3);
        dos.writeByte(9);
        dos.writeShort(port);
        flushOutputStream();
        if (log.isDebugEnabled()) {
            debug("sent: port " + port);
        }
    }

    public void flushOutputStream() throws IOException {
        if (!outputStreamFlushed) {
            dos.flush();
            outputStreamFlushed = true;
            updateMyTrafficTime();
        }
    }

    public boolean hasIncomingTraffic() throws IOException {
        // 4 bytes (keep-alive) is shortest message
        return dis.available() >= 4;
    }

    public long getLastAdvertiseTime() {
        return lastAdvertiseTime;
    }

    public void advertisePieces() throws IOException {
        BitField have = torrent.getHavePieces();
        int size = have.getSize();
        for (int i = 0; i < size; i++) {
            if (!advertisedPieces.get(i) && have.get(i)
            /* && !remoteHavePieces.get(i) */) {
                have(i);
                advertisedPieces.set(i);
            }
        }
        flushOutputStream();
        lastAdvertiseTime = System.currentTimeMillis();
    }

    // TODO reads from DataInputStream may (and will) block and malicious
    // clients could use that to block threads. maybe it would be wise to use
    // some kind of extended datainputstream which would allow peek() methods or
    // rewrite this method's behavior so it doesn't block
    public void processIncomingTraffic() throws IOException {
        int len = dis.readInt();
        if (0 == len) {
            if (log.isDebugEnabled()) {
                debug("got: keep-alive");
            }
            // keep-alive
            updateRemoteTrafficTime();
            // updateInterestedStatus();
        } else if (1 == len) {
            int id = dis.readUnsignedByte();
            if (0 == id) {
                if (log.isDebugEnabled()) {
                    debug("got: choke");
                }
                // choke
                isChoking = true;
                updateRemoteTrafficTime();
            } else if (1 == id) {
                if (log.isDebugEnabled()) {
                    debug("got: unchoke");
                }
                // unchoke
                isChoking = false;
                updateRemoteTrafficTime();
            } else if (2 == id) {
                if (log.isDebugEnabled()) {
                    debug("got: interested");
                }
                // interested
                isInterested = true;
                updateRemoteTrafficTime();
            } else if (3 == id) {
                if (log.isDebugEnabled()) {
                    debug("got: not interested");
                }
                // not interested
                isInterested = false;
                updateRemoteTrafficTime();
            } else {
                if (log.isDebugEnabled()) {
                    debug("error: unknown message: 0x"
                        + Integer.toHexString(id));
                    byte[] tmp = new byte[dis.available()];
                    dis.readFully(tmp);
                    debug("next bytes: " + SHA1Util.convertToString(tmp));
                }
                // unknown message
                close();
            }
        } else {
            int id = dis.readUnsignedByte();
            if (5 == len && 4 == id) {
                // have
                updateRemoteTrafficTime();
                int pieceIndex = dis.readInt();
                if (log.isDebugEnabled()) {
                    debug("got: have " + pieceIndex);
                }
                remoteHavePieces.set(pieceIndex);
                torrent.getPiecePicker().update(pieceIndex);
                // updateInterestedStatus();
            } else if (5 == id) {
                if (log.isDebugEnabled()) {
                    debug("got: bitfield");
                }
                // bitfield
                updateRemoteTrafficTime();
                if (remoteHavePieces.getData().length != len - 1) {
                    // wrong size
                    if (log.isDebugEnabled()) {
                        debug("error: wrong size");
                    }
                    close();
                } else {
                    dis.readFully(remoteHavePieces.getData());
                    torrent.getPiecePicker().update(remoteHavePieces);
                    // updateInterestedStatus();
                }
            } else if (13 == len && 6 == id) {
                // request
                updateRemoteTrafficTime();
                int pieceIndex = dis.readInt();
                int offset = dis.readInt();
                int size = dis.readInt();
                // debug("got: request(" + pieceIndex + ", " + offset + ", "
                // + size + ")");
                if (!amChoking) {
                    int expectedBlockSize = Piece.BLOCK_SIZE;
                    if (pieceIndex == (torrent.getMetaInfo().getNumPieces() - 1)
                        && (offset + Piece.BLOCK_SIZE > torrent.getMetaInfo()
                            .getLastPieceLength())) {
                        expectedBlockSize =
                            torrent.getMetaInfo().getLastPieceLength()
                                % Piece.BLOCK_SIZE;
                    }
                    if (pieceIndex < 0
                        || pieceIndex >= torrent.getMetaInfo().getNumPieces()) {
                        // wrong pieceIndex
                        if (log.isDebugEnabled()) {
                            debug("error: wrong piece index");
                        }
                        close();
                    } else if (0 != (offset % Piece.BLOCK_SIZE)) {
                        // wrong offset
                        if (log.isDebugEnabled()) {
                            debug("error: wrong offset");
                        }
                        close();
                    } else if (expectedBlockSize != size) {
                        // wrong size
                        if (log.isDebugEnabled()) {
                            debug("error: wrong size, got " + size
                                + ", expected " + expectedBlockSize);
                        }
                        close();
                    } else if (!torrent.getHavePieces().get(pieceIndex)) {
                        // don't have that piece
                        if (log.isDebugEnabled()) {
                            debug("error: don't have requested piece");
                        }
                        close();
                    } else if (remoteQueue.size() < 2) { // 2 pieces max
                        BlockRequests reqs = remoteQueue.get(pieceIndex);
                        if (null == reqs) {
                            MetaInfo metaInfo = torrent.getMetaInfo();
                            boolean lastPiece =
                                pieceIndex == (metaInfo.getNumPieces() - 1);
                            int pieceLength =
                                lastPiece ? metaInfo.getLastPieceLength()
                                    : metaInfo.getPieceLength();
                            int numBlocks = pieceLength / Piece.BLOCK_SIZE;
                            if (lastPiece
                                && 0 != (pieceLength % Piece.BLOCK_SIZE)) {
                                ++numBlocks;
                            }
                            reqs = new BlockRequests(pieceIndex, numBlocks);
                            remoteQueue.put(pieceIndex, reqs);
                        }
                        reqs.requestBlock(offset / Piece.BLOCK_SIZE);
                    } else if (log.isDebugEnabled()) {
                        debug("ignoring request because remote already sent"
                            + " too many");
                    }
                }
            } else if (7 == id) {
                // piece
                updateRemoteTrafficTime();
                int pieceIndex = dis.readInt();
                int offset = dis.readInt();
                int size = len - 9;
                // debug("got: piece(" + pieceIndex + ", " + offset + ", " +
                // size + ")");
                int expectedBlockSize = Piece.BLOCK_SIZE;
                if (pieceIndex == (torrent.getMetaInfo().getNumPieces() - 1)
                    && (offset + Piece.BLOCK_SIZE > torrent.getMetaInfo()
                        .getLastPieceLength())) {
                    expectedBlockSize =
                        torrent.getMetaInfo().getLastPieceLength()
                            % Piece.BLOCK_SIZE;
                }
                if (pieceIndex < 0
                    || pieceIndex >= torrent.getMetaInfo().getNumPieces()) {
                    // wrong pieceIndex
                    if (log.isDebugEnabled()) {
                        debug("error: wrong piece index");
                    }
                    close();
                } else if (0 != (offset % Piece.BLOCK_SIZE)) {
                    // wrong offset
                    if (log.isDebugEnabled()) {
                        debug("error: wrong offset");
                    }
                    close();
                } else if (expectedBlockSize != size) {
                    // wrong size
                    if (log.isDebugEnabled()) {
                        debug("error: wrong size, got " + size + ", expected "
                            + expectedBlockSize);
                    }
                    close();
                } else {
                    byte[] array = new byte[size];
                    dis.readFully(array);
                    speedMetricsDownloaded += size;
                    // sometimes block will arrive just after connection thread
                    // demoted connection to choked, ignore block in that case
                    if (null != connectionThread) {
                        // pass block data to connection thread
                        connectionThread.onBlockArrived(pieceIndex, offset,
                            size, array);
                    }
                }
            } else if (13 == len && 8 == id) {
                // cancel
                updateRemoteTrafficTime();
                int pieceIndex = dis.readInt();
                int offset = dis.readInt();
                int size = dis.readInt();
                if (log.isDebugEnabled()) {
                    debug("got: cancel(" + pieceIndex + ", " + offset + ", "
                        + size + ")");
                }
                BlockRequests reqs = remoteQueue.get(pieceIndex);
                if (null != reqs) {
                    reqs.unrequestBlock(offset / Piece.BLOCK_SIZE);
                }
            } else if (3 == len && 9 == id) {
                // port
                updateRemoteTrafficTime();
                int port = dis.readUnsignedShort();
                if (log.isDebugEnabled()) {
                    debug("got: port " + port);
                }
                // TODO when/if DHT is supported
            } else {
                if (log.isDebugEnabled()) {
                    debug("error: unknown message: 0x"
                        + Integer.toHexString(id));
                    byte[] tmp = new byte[dis.available()];
                    dis.readFully(tmp);
                    debug("next bytes: " + SHA1Util.convertToString(tmp));
                }
                // unknown message
                close();
            }
        }
    }

    protected HashMap<Integer, BlockRequests> getRemoteQueue() {
        return remoteQueue;
    }

    /**
     * @return the connectionThread
     */
    public ConnectionThread getConnectionThread() {
        return connectionThread;
    }

    /**
     * @param connectionThread
     *            the connectionThread to set
     */
    public void setConnectionThread(ConnectionThread connectionThread) {
        this.connectionThread = connectionThread;
        if (null != connectionThread) {
            // reset speed
            resetSpeedMetrics();
        }
    }

    private void checkFibrillation() {
        // who cares, others do it too
        // long diff = System.currentTimeMillis() - lastChokeChangeTime;
        // if (diff < 10000L) {
        // debug("warning: i'm fibrillating!");
        // }
    }

    public void updateInterestedStatus() throws IOException {
        BitField needPieces =
            torrent.getHavePieces().not().and(remoteHavePieces);
        boolean interested = needPieces.getNumOnes() > 0;
        if (interested && !amInterested) {
            interested();
        } else if (!interested && amInterested) {
            notInterested();
        }
        // debug("needPieces=" + needPieces.getNumOnes() + " interested="
        // + interested + " amInterested=" + amInterested + " isInterested="
        // + isInterested + " amChoking=" + amChoking + " isChoking="
        // + isChoking);
    }

    public BitField getRemoteHavePieces() {
        return remoteHavePieces;
    }

    protected Torrent getTorrent() {
        return torrent;
    }

    protected boolean isSafeToChangeChokeState() {
        // choke state shouldn't change more than once per 10 seconds
        return System.currentTimeMillis() - lastChokeChangeTime >= 10000L;
    }

    public boolean isSeeder() {
        if (null == remoteHavePieces) {
            return false;
        }
        return remoteHavePieces.allBitsSet();
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
        PeerConnection other = (PeerConnection) obj;
        return socket.getInetAddress().getHostAddress().equals(
            other.getSocket().getInetAddress().getHostAddress());
    }

    protected String getPrefix() {
        return "[" + socket.getInetAddress().getHostAddress() + ":"
            + socket.getPort() + "] ";
    }

    private void debug(String s) {
        log.debug(getPrefix() + s);
    }

    // private void debug(String s, Exception e) {
    // log.debug(getRemoteIPPrefix() + s, e);
    // }

    private Socket socket;
    private boolean incoming;
    private DataInputStream dis;
    private DataOutputStream dos;
    private Torrent torrent;
    private byte[] infoHash = new byte[20];
    private byte[] remotePeerId = new byte[20];
    // uTorrent recognizes fake ids and marks clients as FAKE
    // this one is from real uTorrent/1760
    public final static byte[] myPeerId =
        {
            45, 85, 84, 49, 55, 54, 48, 45, -77, -98, 93, 65, -66, 2, 72, 9,
            -62, 40, 118, -35
        };
    private BitField remoteExtensions = new BitField(64);
    private BitField commonExtensions; // common for local and remote
    private BitField remoteHavePieces;
    private BitField advertisedPieces;
    private HashMap<Integer, BlockRequests> remoteQueue =
        new HashMap<Integer, BlockRequests>();
    private ConnectionThread connectionThread;

    private boolean amChoking = true;
    private boolean amInterested = false;
    private boolean isChoking = true;
    private boolean isInterested = false;

    private long lastSpeedMetricsUpdateTime;
    private long speedMetricsUploaded;
    private long speedMetricsDownloaded;
    private long uploadRate;
    private long downloadRate;
    private long myLastTrafficTime;
    private long remoteLastTrafficTime;
    private long lastChokeChangeTime;
    private long lastAdvertiseTime;

    private boolean outputStreamFlushed;

    private boolean encrypted;
    private boolean fullyEncrypted;

}

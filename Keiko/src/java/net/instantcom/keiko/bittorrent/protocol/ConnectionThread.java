package net.instantcom.keiko.bittorrent.protocol;

import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;
import java.util.HashMap;
import java.util.NoSuchElementException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import net.instantcom.keiko.Server;
import net.instantcom.keiko.bittorrent.cache.PieceCache;
import net.instantcom.util.BitField;

/**
 * ConnectionThread is the core of BitTorrent protocol and it works with
 * PeerConections. While there can be thousands of PeerConnections, number of
 * ConnectionThreads is usually limited to small number such as 4 per torrent.
 * Actual number depends on number of unchoked connections.
 */
public class ConnectionThread implements Runnable {

    private static final Log log = LogFactory.getLog(ConnectionThread.class);

    private static final int MIN_REQUESTS = 4;
    private static final int MAX_REQUESTS = 256;

    protected ConnectionThread(PeerConnection connection, Torrent torrent) {
        this(connection, torrent, false);
    }

    protected ConnectionThread(PeerConnection connection, Torrent torrent,
        boolean optimisticallyUnchoked) {
        this.connection = connection;
        this.torrent = torrent;
        this.optimisticallyUnchoked = optimisticallyUnchoked;
        currentPiece = null;
        blocksRequested = null;
    }

    protected void start() {
        if (!running) {
            running = true;
            Thread t = new Thread(this);
            t.setDaemon(true);
            t.start();
        }
    }

    protected void stop() {
        running = false;
    }

    protected boolean isRunning() {
        return running;
    }

    protected boolean isOptimisticallyUnchoked() {
        return optimisticallyUnchoked;
    }

    protected void setOptimisticallyUnchoked(boolean optimisticallyUnchoked) {
        this.optimisticallyUnchoked = optimisticallyUnchoked;
    }

    protected boolean isSnubbed() {
        return snubbed;
    }

    protected void setSnubbed(boolean snubbed) {
        this.snubbed = snubbed;
    }

    public void run() {
        if (log.isDebugEnabled()) {
            debug("ConnectionThread started");
        }
        boolean socketError = false;
        connection.setConnectionThread(this);
        try {
            // update interested status
            connection.updateInterestedStatus();

            // unchoke if remote is interested or he's optimistically unchoked
            if (connection.amChoking()
                && (connection.isInterested() || optimisticallyUnchoked)) {
                connection.unchoke();
            }

            while (running && isSocketConnected(connection.getSocket())) {
                boolean idle = true;
                // process a message from incoming traffic
                if (connection.hasIncomingTraffic()) {
                    idle = false;
                    boolean remoteWasChoking = connection.isChoking();
                    connection.processIncomingTraffic();
                    if (connection.isChoking() && !remoteWasChoking) {
                        // remote just choked us
                        if (!connection.isInterested()) {
                            if (log.isDebugEnabled()) {
                                debug("exiting loop because remote just choked me"
                                    + " and he's not interested");
                            }
                            break;
                        }
                    }
                }

                // get remote requests
                if (null == remoteRequests) {
                    HashMap<Integer, BlockRequests> remoteQueue =
                        connection.getRemoteQueue();
                    if (!remoteQueue.isEmpty()) {
                        try {
                            remoteRequests =
                                remoteQueue.values().iterator().next();
                        } catch (NoSuchElementException ignored) {
                        }
                    }
                }
                // send a remotely queued block
                if (null != remoteRequests) {
                    idle = false;
                    // get piece from cache and send requested block
                    int pieceIndex = remoteRequests.getPieceIndex();
                    int blockIndex = remoteRequests.getRequestedBlockIndex();
                    if (blockIndex < 0) {
                        // no more requests
                        remoteRequests = null;
                        // remove from map
                        connection.getRemoteQueue().remove(pieceIndex);
                    } else {
                        // snubbed clients don't get any upload from us
                        // unless they're optimistically unchoked
                        if (!snubbed || optimisticallyUnchoked) {
                            // don't send blocks to seeders
                            if (!connection.isSeeder()) {
                                Piece piece =
                                    PieceCache.getInstance().get(torrent,
                                        pieceIndex);
                                if (null != piece) {
                                    connection.piece(piece, blockIndex
                                        * Piece.BLOCK_SIZE, getBlockSize(piece,
                                        blockIndex));
                                }
                            }
                            remoteRequests.unrequestBlock(blockIndex);
                        }
                    }
                }

                if (!connection.isChoking()) {
                    // pick a piece to download
                    if (null == currentPiece && !torrent.isCompleted()) {
                        int pieceIndex =
                            torrent.getPiecePicker().pickPiece(connection);
                        if (pieceIndex >= 0) {
                            currentPiece =
                                PieceCache.getInstance().get(torrent,
                                    pieceIndex);
                            // sometimes pieces are downloaded after state
                            // was saved (abnormal termination) so after state
                            // is resumed torrent will be missing some 1 in
                            // "have". cache will fix this while fetching piece
                            // from disk but that will happen after piecepicker
                            // already chose a piece. that's why we need to
                            // check it again here
                            if (null != currentPiece
                                && currentPiece.isComplete()) {
                                currentPiece = null;
                            } else {
                                if (null != currentPiece) {
                                    blocksRequested =
                                        new BitField(currentPiece
                                            .getBlockAvailability().getSize());
                                } else {
                                    // nothing to download from remote, send not
                                    // interested
                                    connection.notInterested();
                                }
                            }
                        }
                    }

                    if (null != currentPiece) {
                        // check if other connection thread already
                        // downloaded it (end game)
                        if (currentPiece.isComplete()) {
                            cancelAllRequestedBlocks();
                            currentPiece = null;
                            blocksRequested = null;
                        } else {
                            // limit block requests based on download speed
                            // (one block per each 4kB/s)
                            int requestLimit =
                                (int) (connection.getDownloadRate() / 4096L);
                            if (requestLimit < MIN_REQUESTS) {
                                requestLimit = MIN_REQUESTS;
                            } else if (requestLimit > MAX_REQUESTS) {
                                requestLimit = MAX_REQUESTS;
                            }
                            // request blocks as needed
                            int totalBlocks = blocksRequested.getSize();
                            int i = 0;
                            int oldNumBlocksRequested =
                                blocksRequested.getNumOnes();
                            int newBlocksRequested = 0;
                            BitField blockAvailability =
                                currentPiece.getBlockAvailability();
                            while (oldNumBlocksRequested + newBlocksRequested < requestLimit
                                && i < totalBlocks) {
                                for (; i < totalBlocks; i++) {
                                    if (!blockAvailability.get(i)
                                        && !blocksRequested.get(i)) {
                                        connection.request(currentPiece
                                            .getIndex(), i * Piece.BLOCK_SIZE,
                                            getBlockSize(currentPiece, i));
                                        blocksRequested.set(i);
                                        newBlocksRequested++;
                                        break;
                                    }
                                }
                            }
                            // flush queued (not flushed) requests
                            if (newBlocksRequested > 0) {
                                idle = false;
                                connection.flushOutputStream();
                            }
                        }
                    }
                }

                // if i'm choking
                if (connection.amChoking()) {
                    // unchoke if remote is interested
                    if (connection.isInterested()) {
                        if (log.isDebugEnabled()) {
                            debug("unchoking because remote is interested");
                        }
                        connection.unchoke();
                    }
                } else {
                    // i'm not choking
                    // choke if remote is not interested
                    if (!connection.isInterested()) {
                        if (log.isDebugEnabled()) {
                            debug("choking because remote is not interested");
                        }
                        connection.choke();
                    }
                }

                // exit loop if both are choking
                if (connection.amChoking() && connection.isChoking()) {
                    if (log.isDebugEnabled()) {
                        debug("exiting loop because both are choking");
                    }
                    break;
                }

                // exit loop if both are uninterested (if not optimistically
                // unchoked)
                if (!optimisticallyUnchoked && !connection.amInterested()
                    && !connection.isInterested()) {
                    if (log.isDebugEnabled()) {
                        debug("exiting loop because no one is interested");
                    }
                    break;
                }

                // advertise pieces and update interested status every 10
                // seconds
                long now = System.currentTimeMillis();
                if (now - connection.getLastAdvertiseTime() >= 10000L) {
                    connection.advertisePieces();
                    connection.updateInterestedStatus();
                }

                // send keep-alive as needed at least every 2 minutes
                now = System.currentTimeMillis();
                if (now - connection.getMyLastTrafficTime() >= 120000L) {
                    connection.keepAlive();
                    // active connection should never be idle so long
                    // there is high chance of packet loss so both sides are
                    // waiting for each other
                    // demote back to choked
                    if (log.isDebugEnabled()) {
                        debug("exiting loop because i sent keep-alive");
                    }
                    running = false;
                    break;
                }

                if (idle) {
                    // take a short nap, not much going on around here
                    // don't sleep too long for bandwidth's sake
                    long napTime = 500L;
                    if (null != blocksRequested
                        && blocksRequested.getNumOnes() > 0) {
                        napTime = 20L;
                    } else if (null != remoteRequests
                        && remoteRequests.getNumBlocksRequested() > 0) {
                        napTime = 20L;
                    }
                    try {
                        Thread.sleep(napTime);
                    } catch (InterruptedException ignored) {
                    }
                }
            }
        } catch (SocketException e) {
            socketError = true;
            if (log.isDebugEnabled()) {
                debug("connection lost");
            }
        } catch (IOException e) {
            socketError = true;
            if (log.isDebugEnabled()) {
                debug("connection lost");
            }
        } catch (Exception e) {
            log.error("error", e);
        } finally {
            if (log.isDebugEnabled()) {
                debug("entered finally");
            }
            // cancel all outbound requests, choke and send not interested
            if (!socketError && isSocketConnected(connection.getSocket())) {
                try {
                    cancelAllRequestedBlocks();
                    if (!connection.amChoking()) {
                        connection.choke();
                    }
                    if (connection.amInterested()) {
                        connection.notInterested();
                    }
                } catch (SocketException e) {
                    socketError = true;
                } catch (IOException e) {
                    socketError = true;
                }
            }
            // clear all incoming requests
            connection.getRemoteQueue().clear();
            remoteRequests = null;
            // cancel all piece reservations
            torrent.getPiecePicker().cancelReservations(connection);
            // remove self from activePeers
            torrent.removeActivePeer(this);
            // close socket if error
            if (socketError) {
                connection.close();
            } else if (isSocketConnected(connection.getSocket())) {
                // add connection to choked peer list
                try {
                    torrent.addPeer(connection, false);
                } catch (SocketException ignored) {
                } catch (IOException ignored) {
                }
            }
        }
        connection.setConnectionThread(null);
        if (log.isDebugEnabled()) {
            debug("ConnectionThread stopped");
        }
    }

    protected void onBlockArrived(int pieceIndex, int offset, int size,
        byte[] data) throws IOException {
        if (null == currentPiece) {
            if (log.isDebugEnabled()) {
                debug("got block unrequested, ignoring");
            }
            return;
        }
        synchronized (currentPiece) {
            if (currentPiece.getIndex() != pieceIndex) {
                // damn riaa/mpaa freak, wasting bandwidth with random data
                connection.close();
                throw new IllegalArgumentException("received block for piece "
                    + pieceIndex + " but block for piece "
                    + currentPiece.getIndex() + " was expected");
            }
            // check if other connection thread already downloaded
            // it (end game)
            if (currentPiece.isComplete()) {
                cancelAllRequestedBlocks();
                currentPiece = null;
                blocksRequested = null;
                return;
            }
            blocksRequested.clear(offset / Piece.BLOCK_SIZE);
            currentPiece.writeBlock(offset, data);
            // update session stats
            torrent.updateDownloadStats(size);
            Server.bandwidthStats.update(size, 0);
            // check if piece is completed
            if (currentPiece.isComplete()) {
                // check hash
                if (currentPiece.setData(currentPiece.getData())) {
                    // piece done
                    // save to disk
                    currentPiece.save();

                    // mark bit in HAVE
                    torrent.getHavePieces().set(currentPiece.getIndex());

                    // cancel reservation
                    torrent.getPiecePicker().cancelReservations(connection);

                    // prepare for next piece
                    currentPiece = null;
                    blocksRequested = null;
                } else {
                    // failed hash check, clear data and download from other
                    // peer (or the same if there is only one)
                    currentPiece.clearData();
                    currentPiece = null;
                    blocksRequested = null;
                    torrent.getPiecePicker().cancelReservations(connection);
                }
            }
        }
    }

    private boolean isSocketConnected(Socket socket) {
        return !(socket.isInputShutdown() || socket.isOutputShutdown());
    }

    private void cancelAllRequestedBlocks() throws IOException {
        if (null != currentPiece && null != blocksRequested
            && blocksRequested.getNumOnes() > 0) {
            if (log.isDebugEnabled()) {
                log.debug("cancelling all requests");
            }
            for (int i = 0; i < blocksRequested.getSize(); i++) {
                if (blocksRequested.get(i)) {
                    blocksRequested.clear(i);
                    connection.cancel(currentPiece.getIndex(), i
                        * Piece.BLOCK_SIZE, getBlockSize(currentPiece, i));
                }
            }
            connection.flushOutputStream();
        }
    }

    private int getBlockSize(Piece piece, int blockIndex) {
        int blockSize = Piece.BLOCK_SIZE;
        if (piece.getIndex() == (torrent.getMetaInfo().getNumPieces() - 1)
            && ((blockIndex * Piece.BLOCK_SIZE) + Piece.BLOCK_SIZE > torrent
                .getMetaInfo().getLastPieceLength())) {
            blockSize =
                torrent.getMetaInfo().getLastPieceLength() % Piece.BLOCK_SIZE;
        }
        return blockSize;
    }

    protected PeerConnection getConnection() {
        return connection;
    }

    private String getRemoteIPPrefix() {
        if (null != connection) {
            return connection.getPrefix();
        }
        return "";
    }

    private void debug(String s) {
        log.debug(getRemoteIPPrefix() + s);
    }

    // private void debug(String s, Exception e) {
    // log.debug(getRemoteIPPrefix() + s, e);
    // }

    private boolean running;
    private PeerConnection connection;
    private Torrent torrent;
    private boolean optimisticallyUnchoked;
    private boolean snubbed;
    private Piece currentPiece;
    private BitField blocksRequested;
    private BlockRequests remoteRequests;

}

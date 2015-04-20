package net.instantcom.keiko.bittorrent.protocol;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.Socket;
import java.net.SocketException;
import java.util.List;
import java.util.Queue;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import net.instantcom.keiko.Server;
import net.instantcom.keiko.bittorrent.MetaInfo;
import net.instantcom.keiko.bittorrent.protocol.encryption.EncryptedHandshake;
import net.instantcom.keiko.bittorrent.tracker.TrackerUpdater;
import net.instantcom.keiko.config.Configuration;
import net.instantcom.keiko.ipfilter.IP;
import net.instantcom.keiko.ipfilter.IPFilter;
import net.instantcom.keiko.peer.Peer;
import net.instantcom.util.BitField;

// FIXME writing to sockets can last up to several minutes! you should not use write to socket directly from this class, use queueing and let connectionthread/? do it
public class Torrent implements Runnable, Serializable {

    private static final long serialVersionUID = 20080121L;
    private static final Log log = LogFactory.getLog(Torrent.class);
    private static final int MAX_TRACKER_SUPPLIED_PEERS =
        Configuration.getInstance().getInt("torrent.max.tracker.peers", 100);
    private static final Object debugLock = new Object();

    private class OutboundConnection extends Thread {

        private final Log log = LogFactory.getLog(OutboundConnection.class);

        private OutboundConnection(Peer peer) {
            this.peer = peer;
        }

        @Override
        public void run() {
            String outboundPEPolicy =
                Configuration.getInstance().getString("torrent.pe.outbound",
                    "enabled");
            boolean success = false;
            Socket socket = null;
            EncryptedHandshake encrypted = null;
            PeerConnection pc = null;
            try {
                if (peer.isSupportingCrypto()
                    && !"disabled".equalsIgnoreCase(outboundPEPolicy)) {
                    // try encrypted first
                    try {
                        if (log.isDebugEnabled()) {
                            debug("connecting (encrypted)");
                        }
                        socket = new Socket(peer.getHost(), peer.getPort());
                        encrypted =
                            new EncryptedHandshake(socket.getInputStream(),
                                socket.getOutputStream());
                        encrypted.doHandshake(Torrent.this.getMetaInfo()
                            .getInfoHash());
                        pc =
                            new PeerConnection(socket, false, encrypted
                                .getDataInputStream(), encrypted
                                .getDataOutputStream());
                        pc.setTorrent(Torrent.this);
                        pc.setEncrypted(true);
                        pc.setFullyEncrypted(encrypted.isFullyEncrypted());
                        if (Torrent.this.equals(pc.doHandshake())) {
                            addPeer(pc, true);
                            success = true;
                            if (log.isDebugEnabled()) {
                                debug("connected (encrypted)");
                            }
                        }
                    } catch (Exception e) {
                        if (log.isDebugEnabled()) {
                            debug("failed (encrypted): " + e.toString());
                        }
                        safeSocketClose(socket);
                        socket = null;
                    }
                }
                if (!success) {
                    if (!"forced".equalsIgnoreCase(outboundPEPolicy)) {
                        try {
                            // try unencrypted
                            if (log.isDebugEnabled()) {
                                debug("connecting (plaintext)");
                            }
                            socket = new Socket(peer.getHost(), peer.getPort());
                            pc = new PeerConnection(socket, false);
                            pc.setTorrent(Torrent.this);
                            pc.setEncrypted(false);
                            pc.setFullyEncrypted(false);
                            if (Torrent.this.equals(pc.doHandshake())) {
                                addPeer(pc, true);
                                success = true;
                                if (log.isDebugEnabled()) {
                                    debug("connected (plaintext)");
                                }
                            }
                        } catch (Exception e) {
                            if (log.isDebugEnabled()) {
                                debug("failed (plaintext): " + e.toString());
                            }
                            safeSocketClose(socket);
                        }
                    }
                }
            } finally {
                if (!success) {
                    if (null != pc) {
                        pc.close();
                    } else if (null != socket) {
                        safeSocketClose(socket);
                    }
                    peer.incConnectErrors();
                    if (peer.getConnectErrors() < Peer.MAX_CONNECT_ERRORS) {
                        trackerSuppliedPeers.offer(peer);
                    }
                }
            }
        }

        private void safeSocketClose(Socket socket) {
            if (null != socket) {
                try {
                    socket.close();
                } catch (Exception ignored) {
                }
            }
        }

        private String getPrefix() {
            return Torrent.this.getPrefix().trim() + "[" + peer.getHost() + ":"
                + peer.getPort() + "] ";
        }

        private void debug(String s) {
            log.debug(getPrefix() + s);
        }

        // private void debug(String s, Exception e) {
        // log.debug(getPrefix() + s, e);
        // }

        private Peer peer;

    }

    public Torrent(MetaInfo metaInfo) {
        this.metaInfo = metaInfo;
        piecePicker = new PiecePicker(this);
        havePieces = new BitField(metaInfo.getNumPieces());
        if (Configuration.getInstance().getBoolean("torrent.use.trackers",
            false)) {
            trackerUpdater = new TrackerUpdater(this);
        }
    }

    public void start() {
        if (!running) {
            running = true;
            new Thread(this).start();
        }
    }

    public void stop() {
        running = false;
    }

    @Override
    public void run() {
        // allow server to start listening before torrent contacts trackers
        while (!Server.listening) {
            try {
                Thread.sleep(1000L);
            } catch (InterruptedException ignored) {
            }
        }
        if (null != trackerUpdater) {
            // notify trackers
            trackerUpdater.started();
        }

        info("torrent '" + metaInfo.getName() + "' started");

        boolean wasSeed = havePieces.allBitsSet();

        // randomization on outbound connections otherwise all torrents would be
        // doing it in the same time
        lastTSPtoCPTime =
            System.currentTimeMillis() + new Random().nextInt(10000);

        while (running) {
            try {
                boolean idle = true;

                if (log.isDebugEnabled()) {
                    long now = System.currentTimeMillis();
                    if (now - lastActiveDumpTime > 15000L) {
                        lastActiveDumpTime = now;
                        dumpPeers();
                    }
                }

                // check if am seeder
                long now = System.currentTimeMillis();
                if (!amSeed && (now - lastSeedCheckTime >= 5000L)) {
                    lastSeedCheckTime = now;
                    // check if all pieces are completed
                    if (havePieces.allBitsSet()) {
                        // yay, we're seeder :)
                        amSeed = true;
                        info("finished downloading, seeding...");
                        // save state
                        Server.saveState();
                        if (!wasSeed && null != trackerUpdater) {
                            // notify trackers
                            trackerUpdater.completed();
                        }
                        potentialSeeder = null;
                    }
                }

                if (!chokedPeers.isEmpty()) {
                    for (PeerConnection pc : chokedPeers) {
                        // close connection if remote is seeder and we're
                        // seeding (seeder <-> seeder is pointless)
                        if (amSeed && pc.isSeeder()) {
                            if (log.isDebugEnabled()) {
                                debug("closing connection for "
                                    + pc.getSocket().getInetAddress()
                                        .getHostAddress() + ":"
                                    + pc.getSocket().getPort()
                                    + " as we're both seeding");
                            }
                            pc.close();
                            chokedPeers.remove(pc);
                            continue;
                        }

                        // remove closed connections from list
                        if (!isSocketConnected(pc.getSocket())) {
                            pc.close();
                            chokedPeers.remove(pc);
                            continue;
                        }

                        try {
                            // process data in InputStreams (if any) on choked
                            // connections
                            // int msgCount = 0;
                            if (pc.hasIncomingTraffic() /* && msgCount < 10 */) {
                                idle = false;
                                pc.processIncomingTraffic();
                                // ++msgCount;
                            }

                            // advertise pieces and update interested status
                            // every 10 seconds
                            now = System.currentTimeMillis();
                            if (now - pc.getLastAdvertiseTime() >= 10000L) {
                                pc.advertisePieces();
                                // pc.updateInterestedStatus();
                            }

                            // send keep-alive as needed at least every 2
                            // minutes
                            now = System.currentTimeMillis();
                            if (now - pc.getMyLastTrafficTime() >= 120000L) {
                                pc.keepAlive();
                            }
                        } catch (SocketException e) {
                            if (log.isDebugEnabled()) {
                                debug("connection lost");
                            }
                            pc.close();
                            chokedPeers.remove(pc);
                            continue;
                        } catch (IOException e) {
                            if (log.isDebugEnabled()) {
                                debug("connection lost");
                            }
                            pc.close();
                            chokedPeers.remove(pc);
                            continue;
                        }
                    }
                }

                // addition to bt algorithm
                if (!amSeed) {
                    // promote potential seeder if he unchoked us
                    if (null != potentialSeeder && !potentialSeeder.isChoking()) {
                        if (log.isDebugEnabled()) {
                            debug("potential seeder unchoked us, promoting");
                        }
                        // promote potential
                        chokedPeers.remove(potentialSeeder);
                        ConnectionThread ct =
                            new ConnectionThread(potentialSeeder, this);
                        activePeers.offer(ct);
                        ct.start();
                        potentialSeeder = null;
                        if (log.isDebugEnabled()) {
                            debug("choked=" + chokedPeers.size() + " active="
                                + activePeers.size() + " queued="
                                + trackerSuppliedPeers.size());
                        }
                    }
                    // check for new potential seeder every 10 seconds
                    now = System.currentTimeMillis();
                    if (now - lastPotentialSeederCheckTime >= 10000L) {
                        lastPotentialSeederCheckTime = now;
                        try {
                            if (null != potentialSeeder) {
                                if (log.isDebugEnabled()) {
                                    log.debug("sending not-interested to"
                                        + " potential seeder as he"
                                        + " didn't unchoke us");
                                }
                                potentialSeeder.notInterested();
                            }
                            if (getNumActiveSeeders() < Configuration
                                .getInstance().getInt(
                                    "torrent.max.active.peers", 4)) {
                                potentialSeeder =
                                    findPotentialSeeder(potentialSeeder);
                                if (null != potentialSeeder) {
                                    if (log.isDebugEnabled()) {
                                        debug("got new potential seeder: "
                                            + potentialSeeder.getPrefix());
                                    }
                                    // put him at the end of queue
                                    chokedPeers.remove(potentialSeeder);
                                    chokedPeers.offer(potentialSeeder);
                                    potentialSeeder.interested();
                                }
                            } else {
                                potentialSeeder = null;
                            }
                        } catch (SocketException e) {
                            if (log.isDebugEnabled()) {
                                debug("connection lost");
                            }
                            potentialSeeder.close();
                            chokedPeers.remove(potentialSeeder);
                            potentialSeeder = null;
                        } catch (IOException e) {
                            if (log.isDebugEnabled()) {
                                debug("connection lost");
                            }
                            potentialSeeder.close();
                            chokedPeers.remove(potentialSeeder);
                            potentialSeeder = null;
                        }
                    }
                }

                // classic bt algorithm
                // promote potential downloader if it becomes interested
                if (null != potentialDownloader
                    && potentialDownloader.isInterested()) {
                    if (log.isDebugEnabled()) {
                        debug("potential downloader is interested, promoting");
                    }
                    if (getNumActiveDownloaders() > Configuration.getInstance()
                        .getInt("torrent.max.active.peers", 4)) {
                        // demote active with worst rate
                        ConnectionThread worstActive =
                            findActiveWithWorstRate();
                        if (null != worstActive) {
                            if (log.isDebugEnabled()) {
                                debug("stopping worst downloader");
                            }
                            // just stop it, it will demote itself
                            worstActive.stop();
                        }
                    }
                    // promote potential
                    chokedPeers.remove(potentialDownloader);
                    ConnectionThread ct =
                        new ConnectionThread(potentialDownloader, this);
                    activePeers.offer(ct);
                    ct.start();
                    potentialDownloader = null;
                    if (log.isDebugEnabled()) {
                        debug("choked=" + chokedPeers.size() + " active="
                            + activePeers.size() + " queued="
                            + trackerSuppliedPeers.size());
                    }
                }
                // check for new potential downloader every 10 seconds
                now = System.currentTimeMillis();
                if (now - lastPotentialDownloaderCheckTime >= 10000L) {
                    lastPotentialDownloaderCheckTime = now;
                    try {
                        // choke last potential downloader which didn't become
                        // interested
                        if (null != potentialDownloader) {
                            if (log.isDebugEnabled()) {
                                debug("choking potential downloader because he's not interested");
                            }
                            potentialDownloader.choke();
                        }
                        // find and unchoke new potential downloader (choked and
                        // not interested with better rate than best active
                        // downloader)
                        potentialDownloader =
                            findChokedAndNotInterestedWithBetterRateThan(getBestActiveDownloadRate());
                        if (null != potentialDownloader) {
                            if (null != optimisticallyUnchoked
                                && potentialDownloader
                                    .equals(optimisticallyUnchoked
                                        .getConnection())) {
                                potentialDownloader = null;
                            } else {
                                if (log.isDebugEnabled()) {
                                    debug("got new potential downloader, unchoking");
                                }
                                potentialDownloader.unchoke();
                            }
                        }
                    } catch (SocketException e) {
                        if (log.isDebugEnabled()) {
                            debug("connection lost");
                        }
                        potentialDownloader.close();
                        chokedPeers.remove(potentialDownloader);
                        potentialDownloader = null;
                    } catch (IOException e) {
                        if (log.isDebugEnabled()) {
                            debug("connection lost");
                        }
                        potentialDownloader.close();
                        chokedPeers.remove(potentialDownloader);
                        potentialDownloader = null;
                    }
                }

                // rotate optimistically unchoked every 30 seconds
                now = System.currentTimeMillis();
                if (now - lastOptimisticTime >= 30000L) {
                    lastOptimisticTime = now;

                    if (null != optimisticallyUnchoked) {
                        if (optimisticallyUnchoked.getConnection()
                            .isInterested()) {
                            // not optimistically unchoked anymore
                            optimisticallyUnchoked
                                .setOptimisticallyUnchoked(false);
                            // remove slowest active as needed
                            if (getNumActiveDownloaders() > Configuration
                                .getInstance().getInt(
                                    "torrent.max.active.peers", 4)) {
                                // demote active with worst rate
                                ConnectionThread worstActive =
                                    findActiveWithWorstRate();
                                if (null != worstActive) {
                                    if (log.isDebugEnabled()) {
                                        debug("stopping worst downloader");
                                    }
                                    // just stop it, it will demote itself
                                    worstActive.stop();
                                }
                            }
                        } else {
                            // stop it
                            optimisticallyUnchoked.stop();
                            activePeers.remove(optimisticallyUnchoked);
                        }
                    }

                    PeerConnection lastOne = null;
                    if (null != optimisticallyUnchoked) {
                        lastOne = optimisticallyUnchoked.getConnection();
                    }
                    optimisticallyUnchoked = null;
                    PeerConnection pc = null;
                    int size = chokedPeers.size();
                    for (int i = 0; i < size; i++) {
                        pc = chokedPeers.poll();
                        if (null == pc) {
                            // no more choked peers
                            pc = null;
                            break;
                        }
                        if (pc.isSeeder() || pc.equals(potentialDownloader)
                            || pc.equals(lastOne)) {
                            // put it back
                            chokedPeers.offer(pc);
                            // don't optimistically unchoke seeds and potential
                            // downloader
                            pc = null;
                        } else {
                            // found it
                            break;
                        }
                    }
                    if (null != pc) {
                        if (log.isDebugEnabled()) {
                            debug("unchoking new optimistically unchoked");
                            debug("choked=" + chokedPeers.size() + " active="
                                + activePeers.size() + " queued="
                                + trackerSuppliedPeers.size());
                        }
                        optimisticallyUnchoked =
                            new ConnectionThread(pc, this, true);
                        activePeers.offer(optimisticallyUnchoked);
                        optimisticallyUnchoked.start();
                    } else {
                        // next check in 5 seconds
                        lastOptimisticTime =
                            System.currentTimeMillis() - 25000L;
                    }
                }

                // TODO half-open limit
                // try to promote one tracker supplied peer to new peer each 10
                // seconds
                now = System.currentTimeMillis();
                if (now - lastTSPtoCPTime > 10000L) {
                    lastTSPtoCPTime = now;
                    // don't connect to peers if seeding, let them connect to
                    // you
                    if (amSeed) {
                        // clear peer list
                        trackerSuppliedPeers.clear();
                    } else {
                        final Peer peer = trackerSuppliedPeers.poll();
                        if (null != peer) {
                            // check if it's duplicate
                            // TODO unfortunately, can't use
                            // chokedPeers.contains()
                            // as there is no way to create a socket with
                            // inetaddress without connecting
                            boolean unique = true;
                            // check choked peers
                            if (unique && !chokedPeers.isEmpty()) {
                                for (PeerConnection pc : chokedPeers) {
                                    if (peer.getHost().equals(
                                        pc.getSocket().getInetAddress()
                                            .getHostAddress())) {
                                        unique = false;
                                        break;
                                    }
                                }
                            }
                            // check active peers
                            if (unique && !activePeers.isEmpty()) {
                                for (ConnectionThread ct : activePeers) {
                                    if (peer.getHost().equals(
                                        ct.getConnection().getSocket()
                                            .getInetAddress().getHostAddress())) {
                                        unique = false;
                                        break;
                                    }
                                }
                            }
                            if (unique) {
                                // check if IP filter blocks it
                                try {
                                    if (IPFilter.getInstance().isBlocking(
                                        new IP(peer.getHost()))) {
                                        if (log.isDebugEnabled()) {
                                            debug("ipfilter blocked "
                                                + peer.getHost() + ":"
                                                + peer.getPort());
                                        }
                                    } else {
                                        OutboundConnection outgoing =
                                            new OutboundConnection(peer);
                                        outgoing.setDaemon(true);
                                        outgoing.start();
                                    }
                                } catch (IllegalArgumentException e) {
                                    if (log.isDebugEnabled()) {
                                        debug(e.toString());
                                    }
                                }
                            } else if (log.isDebugEnabled()) {
                                debug("tracker supplied peer "
                                    + peer.getHost()
                                    + ":"
                                    + peer.getPort()
                                    + " already exists on choked list, ignoring");
                            }
                        }
                    }
                }

                if (idle) {
                    // take a nap, not much going on around here
                    try {
                        Thread.sleep(1000L);
                    } catch (InterruptedException ignored) {
                    }
                }
            } catch (Exception e) {
                log.error("error", e);
            }
        }
        info("torrent '" + metaInfo.getName() + "' stopping");
        if (null != optimisticallyUnchoked) {
            optimisticallyUnchoked.stop();
        }
        // stop all active peers
        // due to nature of ConcurrentLinkedQueue we need to do it in a loop
        while (!activePeers.isEmpty()) {
            for (ConnectionThread ct : activePeers) {
                ct.stop();
            }
            // wait until they're stopped
            try {
                Thread.sleep(1000L);
            } catch (InterruptedException ignored) {
            }
        }
        // clear both lists (activePeers is already empty)
        chokedPeers.clear();
        if (null != trackerUpdater) {
            // notify trackers
            trackerUpdater.stopped();
        }
        info("torrent '" + metaInfo.getName() + "' stopped");
    }

    public MetaInfo getMetaInfo() {
        return metaInfo;
    }

    public BitField getHavePieces() {
        return havePieces;
    }

    public PiecePicker getPiecePicker() {
        return piecePicker;
    }

    public void addPeer(PeerConnection pc, boolean newClient)
        throws IOException {
        if (null == pc.getTorrent()) {
            pc.setTorrent(this);
        } else if (!this.equals(pc.getTorrent())) {
            throw new IllegalArgumentException(
                "trying to add peer which belongs to other torrent");
        }
        // TODO don't use size, takes too long - use additional counter
        // but counter requires synchronization and we're back on square
        // one - approximation will hurt in the long run - use counter
        // without synchronization and update it with size() each 10
        // seconds or so - couple of peers +/- doesn't matter
        boolean success = false;
        if (chokedPeers.size() < Configuration.getInstance().getInt(
            "torrent.max.inactive.peers", 100)) {
            if (newClient) {
                pc.bitfield();
                success = chokedPeers.offer(pc);
            } else if (chokedPeers.offer(pc)) {
                success = true;
            }
        }
        if (!success) {
            pc.close();
        }
        // don't call size() if not needed
        if (log.isDebugEnabled()) {
            debug("choked=" + chokedPeers.size() + " active="
                + activePeers.size() + " queued=" + trackerSuppliedPeers.size());
        }
    }

    protected void removeActivePeer(ConnectionThread ct) {
        activePeers.remove(ct);
        // don't call size() if not needed
        if (log.isDebugEnabled()) {
            debug("choked=" + chokedPeers.size() + " active="
                + activePeers.size() + " queued=" + trackerSuppliedPeers.size());
        }
    }

    private int getNumActiveDownloaders() {
        int count = 0;
        for (ConnectionThread ct : activePeers) {
            if (!ct.isRunning()) {
                continue;
            }
            if (ct.getConnection().isInterested()) {
                ++count;
            }
        }
        return count;
    }

    private int getNumActiveSeeders() {
        int count = 0;
        for (ConnectionThread ct : activePeers) {
            if (!ct.isRunning()) {
                continue;
            }
            if (ct.getConnection().isSeeder()) {
                ++count;
            }
        }
        return count;
    }

    // actually, without read/write it's impossible to detect if remote peer
    // disconnected
    private boolean isSocketConnected(Socket socket) {
        return !(socket.isInputShutdown() || socket.isOutputShutdown());
    }

    protected synchronized void updateDownloadStats(int bytes) {
        bytesDownloadedThisSession += bytes;
    }

    protected synchronized void updateUploadStats(int bytes) {
        bytesUploadedThisSession += bytes;
    }

    public long getBytesDownloadedThisSession() {
        return bytesDownloadedThisSession;
    }

    public long getBytesUploadedThisSession() {
        return bytesUploadedThisSession;
    }

    public void addTrackerSuppliedPeers(List<Peer> peers, byte[] cryptoFlags) {
        // don't connect to peers if seeding, let them connect to you
        if (amSeed) {
            return;
        }
        int size = trackerSuppliedPeers.size();
        int index = 0;
        for (Peer peer : peers) {
            peer.setSupportsCrypto(0 != cryptoFlags[index]);
            ++index;
            if (!trackerSuppliedPeers.contains(peer)) {
                if (trackerSuppliedPeers.offer(peer)) {
                    ++size;
                }
            }
            if (size >= MAX_TRACKER_SUPPLIED_PEERS) {
                break;
            }
        }
        if (log.isDebugEnabled()) {
            debug("tracker supplied list of peers has " + size + " peers");
        }
        // debug("tracker supplied list of crypto_flags has " +
        // cryptoFlags.length + " flags");
    }

    public boolean isCompleted() {
        return amSeed;
    }

    private PeerConnection findPotentialSeeder(PeerConnection last) {
        if (chokedPeers.isEmpty()) {
            return null;
        }
        for (PeerConnection pc : chokedPeers) {
            if (pc.equals(last) || pc.equals(potentialDownloader)) {
                continue;
            }
            if (pc.isSeeder()) {
                return pc;
            }
        }
        return null;
    }

    private ConnectionThread findActiveWithWorstRate() {
        if (activePeers.isEmpty()) {
            return null;
        }
        ConnectionThread worst = null;
        long worstValue = Long.MAX_VALUE;
        for (ConnectionThread ct : activePeers) {
            if (!ct.isRunning()) {
                continue;
            }
            PeerConnection pc = ct.getConnection();
            if (!pc.isInterested()) {
                continue;
            }
            long rate = amSeed ? pc.getUploadRate() : pc.getDownloadRate();
            if (rate < worstValue) {
                worstValue = rate;
                worst = ct;
            }
        }
        if (null != worst && log.isDebugEnabled()) {
            debug("found worst active: " + worst.getConnection().getPrefix()
                + " down: " + worst.getConnection().getDownloadRate() + " up: "
                + worst.getConnection().getUploadRate() + " optimistic="
                + worst.isOptimisticallyUnchoked() + " snubbed="
                + worst.isSnubbed());
        }
        return worst;
    }

    private long getBestActiveDownloadRate() {
        if (activePeers.isEmpty()) {
            return 0;
        }
        long bestValue = Long.MIN_VALUE;
        for (ConnectionThread ct : activePeers) {
            if (ct.isRunning()) {
                bestValue =
                    Math.max(bestValue, ct.getConnection().getDownloadRate());
            }
        }
        return bestValue;
    }

    private PeerConnection findChokedAndNotInterestedWithBetterRateThan(
        long rate) {
        if (chokedPeers.isEmpty()) {
            return null;
        }
        PeerConnection best = null;
        for (PeerConnection pc : chokedPeers) {
            if (pc.amChoking() && !pc.isInterested() && !pc.isSeeder()
                && (amSeed ? pc.getUploadRate() : pc.getDownloadRate()) >= rate) {
                best = pc;
                break;
            }
        }
        return best;
    }

    // private boolean amInterestedButRemoteDoesntKnowIt(PeerConnection pc) {
    // return havePieces.not().and(pc.getRemoteHavePieces()).getNumOnes() > 0;
    // }

    // flags displays various letters, each carrying a special meaning about the
    // state of the connection:
    // ?: your client unchoked the peer but the peer is not interested
    // D: currently downloading from the peer (interested and not choked)
    // d: your client wants to download, but peer doesn't want to send
    // (interested and choked)
    // E: peer is using Protocol Encryption (all traffic)
    // e: peer is using Protocol Encryption (handshake)
    // H: peer was obtained through DHT.
    // I: peer established an incoming connection
    // K: peer unchoked your client, but your client is not interested
    // L: peer has been or discovered via Local Peer Discovery
    // O: optimistic unchoke
    // S: peer is snubbed
    // U: currently uploading to the peer (interested and not choked)
    // u: the peer wants your client to upload, but your client doesn't want to
    // (interested and choked)
    // X: peer was included in peer lists obtained through Peer Exchange (PEX)
    private String getFlags(ConnectionThread ct) {
        String s = getFlags(ct.getConnection());
        if (ct.isSnubbed()) {
            s = "S" + s;
        }
        if (ct.equals(optimisticallyUnchoked)) {
            s = "O" + s;
        }
        return s;
    }

    private String getFlags(PeerConnection pc) {
        StringBuffer sb = new StringBuffer();
        boolean amInterested = pc.amInterested();
        boolean isInterested = pc.isInterested();
        boolean amChoking = pc.amChoking();
        boolean isChoking = pc.isChoking();
        if (isInterested) {
            sb.append(amChoking ? 'u' : 'U');
        } else if (!amChoking) {
            if (null == optimisticallyUnchoked
                || !pc.equals(optimisticallyUnchoked.getConnection())) {
                sb.append('?');
            }
        }
        if (amInterested) {
            sb.append(isChoking ? 'd' : 'D');
        } else if (!isChoking) {
            sb.append('K');
        }
        if (pc.isIncoming()) {
            sb.append('I');
        }
        if (pc.isEncrypted()) {
            sb.append(pc.isFullyEncrypted() ? 'E' : 'e');
        }
        return sb.toString();
    }

    public void dumpPeers() {
        synchronized (debugLock) {
            boolean activePeersEmpty = activePeers.isEmpty();
            boolean chokedPeersEmpty = chokedPeers.isEmpty();
            if (activePeersEmpty && chokedPeersEmpty) {
                return;
            }
            debug("**********************************************");
            if (!activePeersEmpty) {
                debug("--------------active-peers----------------");
                for (ConnectionThread ct : activePeers) {
                    debug(String
                        .format(
                            "%24s  %7s  complete: %3d  down: %6d  up: %6d  running: %5b",
                            ct.getConnection().getPrefix(), getFlags(ct),
                            (100 * ct.getConnection().getRemoteHavePieces()
                                .getNumOnes())
                                / metaInfo.getNumPieces(), ct.getConnection()
                                .getDownloadRate(), ct.getConnection()
                                .getUploadRate(), ct.isRunning()));
                }
            }
            if (!chokedPeersEmpty) {
                debug("-------------inactive-peers---------------");
                for (PeerConnection pc : chokedPeers) {
                    debug(String.format(
                        "%24s  %7s  complete: %3d  down: %6d  up: %6d", pc
                            .getPrefix(), getFlags(pc), (100 * pc
                            .getRemoteHavePieces().getNumOnes())
                            / metaInfo.getNumPieces(), pc.getDownloadRate(), pc
                            .getUploadRate()));
                }
            }
            debug("**********************************************");
        }
    }

    public String getPrefix() {
        return "["
            + ((100 * havePieces.getNumOnes()) / metaInfo.getNumPieces())
            + "% - " + metaInfo.getName() + "] ";
    }

    // private void debug(String s, Exception e) {
    // debug(s + " " + e.toString());
    // }

    private void debug(String s) {
        log.debug(getPrefix() + s);
    }

    private void info(String s) {
        log.info(getPrefix() + s);
    }

    private void writeObject(ObjectOutputStream out) throws IOException {
        out.writeObject(metaInfo);
        out.writeObject(havePieces);
        out.writeBoolean(amSeed);
    }

    private void readObject(ObjectInputStream in) throws IOException,
        ClassNotFoundException {
        metaInfo = (MetaInfo) in.readObject();
        havePieces = (BitField) in.readObject();
        amSeed = in.readBoolean();
        running = false;
        chokedPeers = new ConcurrentLinkedQueue<PeerConnection>();
        activePeers = new ConcurrentLinkedQueue<ConnectionThread>();
        piecePicker = new PiecePicker(this);
        if (Configuration.getInstance().getBoolean("torrent.use.trackers",
            false)) {
            trackerUpdater = new TrackerUpdater(this);
        }
        bytesDownloadedThisSession = bytesUploadedThisSession = 0;
        trackerSuppliedPeers = new ConcurrentLinkedQueue<Peer>();
        lastTSPtoCPTime = 0;
        lastSeedCheckTime = 0;
        potentialDownloader = potentialSeeder = null;
        optimisticallyUnchoked = null;
        lastPotentialDownloaderCheckTime = lastPotentialSeederCheckTime = 0;
        lastOptimisticTime = 0;
        lastActiveDumpTime = 0;
    }

    // serialized objects
    private MetaInfo metaInfo;
    private BitField havePieces;
    private boolean amSeed = false;

    // not serialized objects
    private boolean running;
    private Queue<PeerConnection> chokedPeers =
        new ConcurrentLinkedQueue<PeerConnection>();
    private Queue<ConnectionThread> activePeers =
        new ConcurrentLinkedQueue<ConnectionThread>();
    private PiecePicker piecePicker;
    private TrackerUpdater trackerUpdater;
    private long bytesDownloadedThisSession;
    private long bytesUploadedThisSession;
    private Queue<Peer> trackerSuppliedPeers =
        new ConcurrentLinkedQueue<Peer>();
    private long lastTSPtoCPTime; // tracker supplied peer -> choked peer
    private long lastSeedCheckTime;
    private PeerConnection potentialDownloader;
    private PeerConnection potentialSeeder;
    private ConnectionThread optimisticallyUnchoked;
    private long lastPotentialDownloaderCheckTime;
    private long lastPotentialSeederCheckTime;
    private long lastOptimisticTime;
    private long lastActiveDumpTime; // debug

}

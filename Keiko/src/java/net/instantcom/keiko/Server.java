package net.instantcom.keiko;

import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.log4j.PropertyConfigurator;

import net.instantcom.keiko.bandwidth.BandwidthStats;
import net.instantcom.keiko.bittorrent.cache.PieceCache;
import net.instantcom.keiko.bittorrent.protocol.HandshakeException;
import net.instantcom.keiko.bittorrent.protocol.PeerConnection;
import net.instantcom.keiko.bittorrent.protocol.Torrent;
import net.instantcom.keiko.bittorrent.protocol.encryption.EncryptedHandshake;
import net.instantcom.keiko.config.Configuration;
import net.instantcom.keiko.deploy.DeployDirectory;
import net.instantcom.keiko.deploy.DeployDirectoryListener;
import net.instantcom.keiko.diskmanager.DiskManager;
import net.instantcom.keiko.filefilter.TorrentFileFilter;
import net.instantcom.keiko.ipfilter.IP;
import net.instantcom.keiko.ipfilter.IPFilter;
import net.instantcom.util.BDecoder;
import net.instantcom.util.HumanReadable;

public class Server implements DeployDirectoryListener {

    private static final Log log = LogFactory.getLog(Server.class);

    private class InboundConnection extends Thread {

        private final Log log = LogFactory.getLog(InboundConnection.class);

        private InboundConnection(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            if (log.isDebugEnabled()) {
                log.debug("incoming connection from "
                    + socket.getInetAddress().getHostAddress());
            }
            EncryptedHandshake encrypted = null;
            PeerConnection pc = null;
            boolean success = false;
            try {
                // try encrypted handshake
                if (log.isDebugEnabled()) {
                    log.debug("trying encrypted handshake");
                }
                encrypted =
                    new EncryptedHandshake(socket.getInputStream(), socket
                        .getOutputStream());
                // if connection is unencrypted the following call will return
                // false
                boolean encryptedSuccess = encrypted.doHandshake(null);
                if (encryptedSuccess
                    || Configuration.getInstance().getBoolean(
                        "torrent.pe.allow.inbound.legacy", true)) {
                    if (!encryptedSuccess && log.isDebugEnabled()) {
                        log.debug("remote sends plaintext");
                    }
                    // try plaintext (also needed for encrypted)
                    pc =
                        new PeerConnection(socket, true, encrypted
                            .getDataInputStream(), encrypted
                            .getDataOutputStream());
                    // normal bt handshake
                    try {
                        Torrent torrent = pc.doHandshake();
                        if (null == torrent) {
                            // error
                            if (log.isDebugEnabled()) {
                                log.debug("error: handshake failed");
                            }
                            pc.close();
                        } else {
                            // success
                            if (log.isDebugEnabled()) {
                                log.debug("handshake completed");
                            }
                            pc.setEncrypted(encryptedSuccess);
                            pc.setFullyEncrypted(encryptedSuccess ? encrypted
                                .isFullyEncrypted() : false);
                            torrent.addPeer(pc, true);
                            success = true;
                        }
                    } catch (HandshakeException e) {
                        if (log.isDebugEnabled()) {
                            log.debug("handshake failed: " + e.toString());
                        }
                    }
                }
            } catch (HandshakeException e) {
                if (log.isDebugEnabled()) {
                    log.debug("handshake error: " + e.toString());
                }
            } catch (IOException e) {
                if (log.isDebugEnabled()) {
                    log.debug("error: " + e.toString());
                }
            } catch (Exception e) {
                log.error("error", e);
            } finally {
                if (!success) {
                    if (null != pc) {
                        pc.close();
                    } else {
                        try {
                            socket.close();
                        } catch (Exception ignored) {
                        }
                    }
                }
            }
        }

        private Socket socket;

    }

    public Server(int port, int backLog) throws Exception {
        serverSocket = new ServerSocket(port, backLog);
        // serverSocket.setPerformancePreferences(0, 1, 2);
    }

    public static void addTorrent(File torrentFile) throws Exception {
        addTorrent(new Torrent(BDecoder.loadMetaInfo(torrentFile)));
    }

    public static void addTorrent(Torrent torrent) throws Exception {
        String key = torrent.getMetaInfo().getInfoHashAsString();
        if (!torrents.containsKey(key)) {
            synchronized (torrents) {
                torrents.put(key, torrent);
            }
            log.info("added torrent '" + torrent.getMetaInfo().getName() + "'");
            torrent.start();
        } else {
            log.info("torrent '" + torrent.getMetaInfo().getName()
                + "' already exists, ignoring");
        }
    }

    public static void startTorrents() {
        synchronized (torrents) {
            for (Torrent torrent : torrents.values()) {
                torrent.start();
            }
        }
    }

    public static void stopTorrents() {
        synchronized (torrents) {
            for (Torrent torrent : torrents.values()) {
                torrent.stop();
            }
        }
    }

    public void start() {
        if (running || listening) {
            log.error("another instance is already running");
            return;
        }

        running = true;
        log.info(About.PRODUCT_VERSION + " started");
        log.info(About.COPYRIGHT);
        log.info(About.URL);

        Configuration config = Configuration.getInstance();

        // configure deploy directory
        String deployDir = config.getString("deploy.directory", null);
        if (null != deployDir) {
            File file = new File(deployDir);
            file.mkdirs();
            torrentDeployDirectory =
                new DeployDirectory(file, new TorrentFileFilter(), this);
        }

        // configure ip filter
        IPFilter ipfilter = IPFilter.getInstance();
        ipfilter.add(config.getString("ipfilter", null));
        if (log.isDebugEnabled()) {
            log.debug("ipfilter has " + ipfilter.size() + " entries");
        }

        // load state
        loadState();

        // save state every now and then
        scheduler.schedule(new TimerTask() {

            @Override
            public void run() {
                saveState();
            }

        }, 600000L, 600000L); // 10 minutes

        // log current bandwidth
        scheduler.schedule(new TimerTask() {

            @Override
            public void run() {
                long currentDownload = bandwidthStats.getAverageDownload();
                long currentUpload = bandwidthStats.getAverageUpload();
                maxDownload = Math.max(currentDownload, maxDownload);
                maxUpload = Math.max(currentUpload, maxUpload);
                if (log.isDebugEnabled()) {
                    // log only once every minute
                    long now = System.currentTimeMillis();
                    if (now - lastReportTime >= 60000L) {
                        lastReportTime = now;
                        log.debug("==========================================");
                        log.debug("current bandwidth: "
                            + HumanReadable.humanReadableBytes(currentDownload)
                            + "/s down, "
                            + HumanReadable.humanReadableBytes(currentUpload)
                            + "/s up");
                        log.debug("max bandwidth: "
                            + HumanReadable.humanReadableBytes(maxDownload)
                            + "/s down, "
                            + HumanReadable.humanReadableBytes(maxUpload)
                            + "/s up");
                        log.debug("total bandwidth: "
                            + HumanReadable.humanReadableBytes(bandwidthStats
                                .getTotalDownload())
                            + " down, "
                            + HumanReadable.humanReadableBytes(bandwidthStats
                                .getTotalUpload())
                            + " up, ratio "
                            + HumanReadable.humanReadableRatio(bandwidthStats
                                .getRatio()));
                        log.debug("==========================================");
                    }
                }
            }

            private long maxDownload;
            private long maxUpload;
            private long lastReportTime;

        }, 5000L, 5000L); // calculate each 5 seconds

        // check deploy directory
        if (null != torrentDeployDirectory) {
            scheduler.schedule(new TimerTask() {

                @Override
                public void run() {
                    torrentDeployDirectory.check();
                }

            }, 8000, 60000L); // 1 minute
        }

        // start torrents
        startTorrents();

        log.info("listening on port " + serverSocket.getLocalPort());
        while (running) {
            try {
                listening = true;
                Socket socket = serverSocket.accept();
                if (IPFilter.getInstance().isBlocking(
                    new IP(socket.getInetAddress().getHostAddress()))) {
                    if (log.isDebugEnabled()) {
                        log.debug("ipfilter blocked "
                            + socket.getInetAddress().getHostAddress() + ":"
                            + socket.getPort());
                    }
                    try {
                        socket.close();
                    } catch (IOException ignored) {
                    }
                } else {
                    new InboundConnection(socket).start();
                }
            } catch (Exception e) {
                log.error("error " + e.toString());
            }
        }
        log.info("stopped listening on port" + serverSocket.getLocalPort());
        scheduler.cancel();
        stopTorrents();
        try {
            serverSocket.close();
        } catch (Exception ignored) {
        }
        listening = false;
    }

    public void stop() {
        running = false;
    }

    /*
     * (non-Javadoc)
     * 
     * @see net.instantcom.keiko.deploy.DeployDirectoryListener#onFileFound(java.io.File)
     */
    @Override
    public boolean onFileFound(File file) {
        try {
            addTorrent(file);
        } catch (Exception e) {
            if (log.isDebugEnabled()) {
                log.debug("can't process torrent '" + file.getName() + "': "
                    + e.toString());
            }
        }
        return false;
    }

    public static HashMap<String, Torrent> getTorrents() {
        return torrents;
    }

    public static void main(String[] args) {
        // Configure Log4J
        PropertyConfigurator.configure("src/conf/keiko.conf");

        if (running || listening) {
            log.error("another instance is already running");
            return;
        }

        Configuration config = Configuration.getInstance();
        Server server = null;
        try {
            server =
                new Server(config.getInt("torrent.port",
                    Configuration.DEFAULT_PORT), config.getInt(
                    "socket.backlog", 10));
            server.start();
        } catch (Exception e) {
            log.error("error", e);
        } finally {
            if (null != server) {
                server.stop();
            }
            saveState();
            log.info(About.PRODUCT_VERSION + " stopped");
        }
    }

    private static synchronized void loadState() {
        if (log.isDebugEnabled()) {
            log.debug("loading saved state");
        }
        try {
            HashMap<String, Torrent> torrents =
                DiskManager.getInstance().loadTorrents();
            if (null != torrents) {
                Server.torrents = torrents;
            }
        } catch (Exception e) {
            log.error("error loading torrents", e);
        }
        try {
            BandwidthStats bandwidthStats =
                DiskManager.getInstance().loadBandwidthStats();
            if (null != bandwidthStats) {
                Server.bandwidthStats = bandwidthStats;
            }
        } catch (Exception e) {
            log.error("error loading bandwidth stats", e);
        }
    }

    public static synchronized void saveState() {
        if (log.isDebugEnabled()) {
            log.debug("saving state");
        }
        DiskManager diskManager = DiskManager.getInstance();
        try {
            diskManager.saveTorrents(torrents);
        } catch (Exception e) {
            log.error("error saving torrents");
        }
        try {
            diskManager.saveBandwidthStats(bandwidthStats);
        } catch (Exception e) {
            log.error("error saving bandwidth stats");
        }

        PieceCache cache = PieceCache.getInstance();
        if (log.isDebugEnabled()) {
            log.debug("cache usage: "
                + HumanReadable.humanReadableBytes(cache.getCurrentByteSize())
                + "/"
                + HumanReadable.humanReadableBytes(cache.getMaxByteSize())
                + " (" + cache.size() + " pieces)");
        }
    }

    private static boolean running;
    public static boolean listening;
    private ServerSocket serverSocket;
    private static HashMap<String, Torrent> torrents =
        new HashMap<String, Torrent>();
    public static BandwidthStats bandwidthStats = new BandwidthStats();
    private static DeployDirectory torrentDeployDirectory;
    public static final Timer scheduler = new Timer(true);

}

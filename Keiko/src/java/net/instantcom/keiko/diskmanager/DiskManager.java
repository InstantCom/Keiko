package net.instantcom.keiko.diskmanager;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.HashMap;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import net.instantcom.keiko.bandwidth.BandwidthStats;
import net.instantcom.keiko.bittorrent.protocol.Piece;
import net.instantcom.keiko.bittorrent.protocol.Torrent;
import net.instantcom.keiko.config.Configuration;

public final class DiskManager {

    private static final Log log = LogFactory.getLog(DiskManager.class);

    private static final DiskManager instance = new DiskManager();

    private DiskManager() {
        Configuration config = Configuration.getInstance();
        cacheRoot =
            new File(config.getString("cache.disk.path", "keiko-cache"));
        if (!cacheRoot.exists()) {
            cacheRoot.mkdirs();
            log.info("created " + cacheRoot.getAbsolutePath());
        }

        // TODO calculate how much disk space we're using at the moment and use
        // it for restrictions later

        storageBytesLimit =
            1024L * 1024L * 1024L * config.getLong("cache.disk.max.size", 0);
        long usableSpace = cacheRoot.getUsableSpace();
        if (0 == storageBytesLimit || storageBytesLimit > usableSpace) {
            storageBytesLimit = cacheRoot.getUsableSpace() / 2L;
        }
        log.info("disk cache limit: "
            + (storageBytesLimit / (1024L * 1024L * 1024L)) + " GB");
    }

    public static DiskManager getInstance() {
        return instance;
    }

    /**
     * Loads Piece from disk.
     * 
     * @param torrent
     *            Torrent whom Piece belongs to
     * @param pieceIndex
     *            index of Piece
     * @return Piece or null if piece does not exist on disk
     */
    public Piece load(Torrent torrent, int pieceIndex) {
        // load piece
        Piece piece = null;
        try {
            File file =
                new File(cacheRoot.toURI().resolve(
                    "torrent/" + torrent.getMetaInfo().getInfoHashAsString()
                        + "/" + pieceIndex + ".piece"));
            if (file.exists()) {
                ObjectInputStream ois =
                    new ObjectInputStream(new BufferedInputStream(
                        new FileInputStream(file)));
                piece = (Piece) ois.readObject();
                ois.close();
                piece.setTorrent(torrent);
            }
        } catch (Exception e) {
            log.error("error", e);
        }
        return piece;
    }

    /**
     * Saves Piece to disk.
     * 
     * @param piece
     *            Piece to save
     */
    public void save(Piece piece) {
        // save piece
        try {
            File file =
                new File(cacheRoot.toURI().resolve(
                    "torrent/"
                        + piece.getTorrent().getMetaInfo()
                            .getInfoHashAsString() + "/" + piece.getIndex()
                        + ".piece"));
            if (!file.exists()) {
                file.mkdirs();
            }
            if (file.exists()) {
                if (!file.delete()) {
                    throw new Exception("unable to delete file "
                        + file.getAbsolutePath());
                }
            } else {
                if (!file.createNewFile()) {
                    throw new Exception("unable to create file "
                        + file.getAbsolutePath());
                }
            }
            ObjectOutputStream oos =
                new ObjectOutputStream(new BufferedOutputStream(
                    new FileOutputStream(file)));
            oos.writeObject(piece);
            oos.flush();
            oos.close();
        } catch (Exception e) {
            log.error("error", e);
        }
    }

    /**
     * Loads torrent list from disk.
     * 
     * @return loaded torrent list or null if it can't be loaded
     */
    @SuppressWarnings("unchecked")
    public HashMap<String, Torrent> loadTorrents() throws Exception {
        HashMap<String, Torrent> torrents = null;
        File file =
            new File(cacheRoot.toURI().resolve("torrent/torrentlist.dat"));
        if (file.exists()) {
            if (log.isDebugEnabled()) {
                log.debug("loading torrents");
            }
            ObjectInputStream ois =
                new ObjectInputStream(new BufferedInputStream(
                    new FileInputStream(file)));
            torrents = (HashMap<String, Torrent>) ois.readObject();
            ois.close();
        }
        return torrents;
    }

    /**
     * Saves torrent list to disk.
     * 
     * @param torrents
     *            torrent list
     */
    public void saveTorrents(HashMap<String, Torrent> torrents)
        throws Exception {
        if (log.isDebugEnabled()) {
            log.debug("saving torrents");
        }
        File file =
            new File(cacheRoot.toURI().resolve("torrent/torrentlist.dat"));
        if (!file.exists()) {
            file.mkdirs();
        }
        if (file.exists()) {
            file.delete();
        } else {
            file.createNewFile();
        }
        ObjectOutputStream oos =
            new ObjectOutputStream(new BufferedOutputStream(
                new FileOutputStream(file)));
        oos.writeObject(torrents);
        oos.flush();
        oos.close();
    }

    public BandwidthStats loadBandwidthStats() throws Exception {
        BandwidthStats bandwidthStats = null;
        File file = new File(cacheRoot.toURI().resolve("bandwidth.dat"));
        if (file.exists()) {
            if (log.isDebugEnabled()) {
                log.debug("loading bandwidth stats");
            }
            ObjectInputStream ois =
                new ObjectInputStream(new BufferedInputStream(
                    new FileInputStream(file)));
            bandwidthStats = (BandwidthStats) ois.readObject();
            ois.close();
        }
        return bandwidthStats;
    }

    public void saveBandwidthStats(BandwidthStats bandwidthStats)
        throws Exception {
        if (log.isDebugEnabled()) {
            log.debug("saving bandwidth stats");
        }
        File file = new File(cacheRoot.toURI().resolve("bandwidth.dat"));
        if (!file.exists()) {
            file.mkdirs();
        }
        if (file.exists()) {
            file.delete();
        } else {
            file.createNewFile();
        }
        ObjectOutputStream oos =
            new ObjectOutputStream(new BufferedOutputStream(
                new FileOutputStream(file)));
        oos.writeObject(bandwidthStats);
        oos.flush();
        oos.close();
    }

    private File cacheRoot;
    private long storageBytesLimit;

}

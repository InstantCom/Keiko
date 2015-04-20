package net.instantcom.keiko.bittorrent.cache;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Queue;

import net.instantcom.keiko.bittorrent.protocol.Piece;
import net.instantcom.keiko.bittorrent.protocol.Torrent;
import net.instantcom.keiko.config.Configuration;
import net.instantcom.keiko.diskmanager.DiskManager;

public final class PieceCache {

    // private static final Log log = LogFactory.getLog(PieceCache.class);

    private static final PieceCache instance = new PieceCache();
    private static final int MAX_SIZE =
        1024 * 1024 * Configuration.getInstance().getInt("cache.heap.max.size",
            64);

    private PieceCache() {
    }

    public static PieceCache getInstance() {
        return instance;
    }

    public int size() {
        return storage.size();
    }

    public int getCurrentByteSize() {
        return byteSize;
    }

    public int getMaxByteSize() {
        return MAX_SIZE;
    }

    private boolean removeEldestEntry() {
        Piece piece = order.poll();
        if (null != piece) {
            String key =
                piece.getTorrent().getMetaInfo().getInfoHashAsString()
                    + piece.getIndex();
            storage.remove(key);
            byteSize -= piece.getApproximateSizeOf();
            return true;
        }
        return false;
    }

    // public synchronized void put(Torrent torrent, Piece piece) {
    // String key = piece.getKey();
    // if (null == key || storage.containsKey(key)) {
    // return;
    // }
    // storage.put(key, piece);
    // order.offer(piece);
    // byteSize += piece.getApproximateSizeOf();
    // }

    public synchronized Piece get(Torrent torrent, int pieceIndex) {
        String key = torrent.getMetaInfo().getInfoHashAsString() + pieceIndex;
        Piece piece = storage.get(key);
        if (null == piece) {
            // not in cache, try disk
            piece = DiskManager.getInstance().load(torrent, pieceIndex);
            if (null == piece) {
                // not on disk either
                // create empty piece
                piece = new Piece(torrent, pieceIndex, true);
                byte[] sha1 = new byte[20];
                System.arraycopy(torrent.getMetaInfo().getPiecesSHA1(),
                    20 * pieceIndex, sha1, 0, 20);
                piece.setSHA1(sha1);
            } else {
                // got piece from disk

                // couple of things can happen here:
                // a) piece is complete and bit is set in "have" - piece was
                // downloaded before the state was saved - good
                // b) piece is complete but bit is not set in "have" - piece was
                // downloaded after state was saved - bad

                // as we're not saving unfinished pieces to disk we're solving
                // b) by setting all blocks as available and update appropriate
                // bit in "have"
                piece.getBlockAvailability().setAll();
                torrent.getHavePieces().set(piece.getIndex());
            }
            storage.put(piece.getKey(), piece);
            order.offer(piece);
            byteSize += piece.getApproximateSizeOf();
        } else {
            if (order.remove(piece)) {
                order.offer(piece);
            }
        }
        // restrict byte size of storage
        while (byteSize > MAX_SIZE) {
            if (!removeEldestEntry()) {
                break;
            }
        }
        return piece;
    }

    private int byteSize = 0;
    private final HashMap<String, Piece> storage = new HashMap<String, Piece>();
    private final Queue<Piece> order = new ArrayDeque<Piece>();

}

package net.instantcom.keiko.bittorrent.protocol;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import net.instantcom.keiko.bittorrent.MetaInfo;
import net.instantcom.keiko.diskmanager.DiskManager;
import net.instantcom.util.BitField;
import net.instantcom.util.SHA1Util;

public class Piece implements Serializable {

    private static final long serialVersionUID = 20080119L;
    private static final Log log = LogFactory.getLog(Piece.class);

    // in all current bittorrent implemenations block is exactly 16k except on
    // last piece
    public static final int BLOCK_SIZE = 16384;

    public Piece(Torrent torrent, int index, boolean allocateData) {
        this.torrent = torrent;
        this.index = index;
        MetaInfo metaInfo = torrent.getMetaInfo();
        size =
            (index < metaInfo.getNumPieces() - 1) ? metaInfo.getPieceLength()
                : metaInfo.getLastPieceLength();
        if (allocateData) {
            data = new byte[size];
            int numBlocks = size / BLOCK_SIZE;
            if (0 != (size % BLOCK_SIZE)) {
                ++numBlocks;
            }
            blockAvailability = new BitField(numBlocks);
        }
        key = torrent.getMetaInfo().getInfoHashAsString() + index;
        saved = false;
    }

    /**
     * Gets index of Piece.
     * 
     * @return Piece index
     */
    public int getIndex() {
        return index;
    }

    /**
     * Gets size of Piece.
     * 
     * @return the size of Piece
     */
    public int getSize() {
        return size;
    }

    public byte[] getSHA1() {
        return sha1;
    }

    public void setSHA1(byte[] sha1) {
        this.sha1 = sha1;
    }

    /**
     * Checks if specified block is available.
     * 
     * @param index
     *            index of Block
     * @return true if Block is available
     */
    public boolean isBlockAvailable(int index) {
        return blockAvailability.get(index);
    }

    public byte[] getData() {
        return data;
    }

    /**
     * Checks if data is valid and attempts to set it for this Piece.
     * 
     * @param data
     *            data to set
     * @return true if data was successfully set, false if data failed hash
     *         check
     */
    public boolean setData(byte[] data) {
        if (data.length != size) {
            throw new IllegalArgumentException("size mismatch");
        }
        boolean result = true;
        try {
            if (Arrays.equals(sha1, SHA1Util.getSHA1(data))) {
                this.data = data;
                if (log.isDebugEnabled()) {
                    log.debug("piece " + index + " successfully downloaded");
                }
            } else {
                if (log.isDebugEnabled()) {
                    log.debug("piece " + index + " failed hash check");
                }
                result = false;
            }
        } catch (NoSuchAlgorithmException e) {
            // assume data is valid as we don't have any means to check it
            this.data = data;
        }
        saved = false;
        return result;
    }

    public void clearData() {
        blockAvailability.clearAll();
        for (int i = 0; i < data.length; i++) {
            data[i] = 0;
        }
        saved = false;
    }

    public void readBlock(int offset, byte[] block) {
        // if (0 != (offset % BLOCK_SIZE)) {
        // throw new IllegalArgumentException(
        // "block offset not aligned to 16k");
        // }
        // log.debug("readBlock: " + offset + ", " + block.length);
        System.arraycopy(data, offset, block, 0, block.length);
    }

    public void writeBlock(int offset, byte[] block) {
        // if (0 != (offset % BLOCK_SIZE)) {
        // throw new IllegalArgumentException(
        // "block offset not aligned to 16k");
        // }
        // log.debug("writeBlock: " + offset + ", " + block.length);
        System.arraycopy(block, 0, data, offset, block.length);
        blockAvailability.set(offset / BLOCK_SIZE);
        saved = false;
    }

    public boolean isComplete() {
        return blockAvailability.allBitsSet();
    }

    public BitField getBlockAvailability() {
        return blockAvailability;
    }

    public boolean isSaved() {
        return saved;
    }

    public void save() {
        if (!saved && isComplete()) {
            DiskManager.getInstance().save(this);
            saved = true;
        }
    }

    public String getKey() {
        return key;
    }

    public Torrent getTorrent() {
        return torrent;
    }

    public void setTorrent(Torrent torrent) {
        this.torrent = torrent;
    }

    private void writeObject(ObjectOutputStream out) throws IOException {
        out.writeInt(index);
        out.writeInt(size);
        out.writeObject(sha1);
        out.writeObject(data);
        out.writeObject(blockAvailability);
        out.writeUTF(key);
        saved = true;
    }

    private void readObject(ObjectInputStream in) throws IOException,
        ClassNotFoundException {
        index = in.readInt();
        size = in.readInt();
        sha1 = (byte[]) in.readObject();
        data = (byte[]) in.readObject();
        blockAvailability = (BitField) in.readObject();
        key = in.readUTF();
        saved = true;
    }

    public int getApproximateSizeOf() {
        // data + sha1 + key + blockavailability.data + 21
        // (21 = torrent + saved + index + size + reference +
        // blockavailability.size)
        // return size + sha1.length + 2 * key.length()
        // + blockAvailability.getData().length + 21;

        // actually, overhead is so small it's not worth mentioning
        // it's better to use raw piece size to allow cache to populate
        // efficiently
        return size;
    }

    private Torrent torrent; // not serialized
    private boolean saved; // not serialized

    private int index;
    private int size;
    private byte[] sha1;
    private byte[] data;
    private BitField blockAvailability;
    private String key;

}

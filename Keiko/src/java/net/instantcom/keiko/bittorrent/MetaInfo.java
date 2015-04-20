package net.instantcom.keiko.bittorrent;

import java.io.Serializable;
import java.util.List;

import net.instantcom.util.SHA1Util;

public class MetaInfo implements Serializable {

    private static final long serialVersionUID = 20080119L;

    public MetaInfo() {
    }

    /**
     * @return the announce
     */
    public String getAnnounce() {
        return announce;
    }

    /**
     * @param announce
     *            the announce to set
     */
    public void setAnnounce(String announce) {
        this.announce = announce;
    }

    /**
     * @return the announceList
     */
    public List<String> getAnnounceList() {
        return announceList;
    }

    /**
     * @param announceList
     *            the announceList to set
     */
    public void setAnnounceList(List<String> announceList) {
        this.announceList = announceList;
    }

    /**
     * @return the creationDate
     */
    public long getCreationDate() {
        return creationDate;
    }

    /**
     * @param creationDate
     *            the creationDate to set
     */
    public void setCreationDate(long creationDate) {
        this.creationDate = creationDate;
    }

    /**
     * @return the comment
     */
    public String getComment() {
        return comment;
    }

    /**
     * @param comment
     *            the comment to set
     */
    public void setComment(String comment) {
        this.comment = comment;
    }

    /**
     * @return the createdBy
     */
    public String getCreatedBy() {
        return createdBy;
    }

    /**
     * @param createdBy
     *            the createdBy to set
     */
    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    /**
     * @return the encoding
     */
    public String getEncoding() {
        return encoding;
    }

    /**
     * @param encoding
     *            the encoding to set
     */
    public void setEncoding(String encoding) {
        this.encoding = encoding;
    }

    /**
     * @return the infoHash
     */
    public byte[] getInfoHash() {
        return infoHash;
    }

    /**
     * @param infoHash
     *            the infoHash to set
     */
    public void setInfoHash(byte[] infoHash) {
        this.infoHash = infoHash;
        infoHashString = SHA1Util.convertToString(infoHash);
    }

    /**
     * Gets info hash as string.
     * 
     * @return info hash
     */
    public String getInfoHashAsString() {
        return infoHashString;
    }

    /**
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * @param name
     *            the name to set
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * @return the files
     */
    @SuppressWarnings("unchecked")
    public List getFiles() {
        return files;
    }

    /**
     * @param files
     *            the files to set
     */
    @SuppressWarnings("unchecked")
    public void setFiles(List files) {
        this.files = files;
    }

    /**
     * @return the length
     */
    public long getLength() {
        return length;
    }

    /**
     * @param length
     *            the length to set
     */
    public void setLength(long length) {
        this.length = length;
    }

    /**
     * @return the pieceLength
     */
    public int getPieceLength() {
        return pieceLength;
    }

    /**
     * @param pieceLength
     *            the pieceLength to set
     */
    public void setPieceLength(int pieceLength) {
        this.pieceLength = pieceLength;
    }

    /**
     * @return the lastPieceLength
     */
    public int getLastPieceLength() {
        return lastPieceLength;
    }

    /**
     * @param lastPieceLength
     *            the lastPieceLength to set
     */
    public void setLastPieceLength(int lastPieceLength) {
        this.lastPieceLength = lastPieceLength;
    }

    /**
     * @return the piecesSHA1
     */
    public byte[] getPiecesSHA1() {
        return piecesSHA1;
    }

    /**
     * @param piecesSHA1
     *            the piecesSHA1 to set
     */
    public void setPiecesSHA1(byte[] piecesSHA1) {
        this.piecesSHA1 = piecesSHA1;
    }

    /**
     * Gets number of pieces.
     * 
     * @return number of pieces
     */
    public int getNumPieces() {
        return piecesSHA1.length / 20;
    }

    /**
     * @return the isPrivate
     */
    public boolean isPrivate() {
        return isPrivate;
    }

    /**
     * @param isPrivate
     *            the isPrivate to set
     */
    public void setPrivate(boolean isPrivate) {
        this.isPrivate = isPrivate;
    }

    @Override
    public String toString() {
        return "MetaInfo(announce=" + announce + ", announceList="
            + announceList + ", creationDate=" + creationDate + ", comment="
            + comment + ", createdBy=" + createdBy + ", encoding=" + encoding
            + ", infoHash=" + infoHashString + ", name=" + name + ", files="
            + files + ", length=" + length + ", numPieces=" + getNumPieces()
            + ", pieceLength=" + pieceLength + ", piecesSHA1=" + piecesSHA1
            + ", private=" + isPrivate + ")";
    }

    // tracker url
    private String announce;
    // backup trackers urls (optional)
    private List<String> announceList;
    // unix timestamp (optional)
    private long creationDate;
    // comment (optional)
    private String comment;
    // program used to create the torrent (optional)
    private String createdBy;
    // encoding (usually UTF-8)
    private String encoding;
    // info hash
    private byte[] infoHash;
    // info hash (string)
    private String infoHashString;

    // info dictionary
    // name (filename for single-file torrents, null for multi-file)
    private String name;
    // files (null for single-file torrents)
    @SuppressWarnings("unchecked")
    private List files;
    // length (torrent size)
    private long length;
    // length of each piece (bytes)
    private int pieceLength;
    // length of last piece (bytes)
    private int lastPieceLength;
    // pieces (concatenated 20-byte SHA1 hash values, one per piece)
    private byte[] piecesSHA1;
    // private (optional, 0 or 1)
    private boolean isPrivate;

    // rest of info dictionary is ignored as we're working only with pieces,
    // not files

}

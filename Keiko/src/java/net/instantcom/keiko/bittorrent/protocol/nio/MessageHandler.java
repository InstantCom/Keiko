package net.instantcom.keiko.bittorrent.protocol.nio;

import java.nio.ByteBuffer;

/**
 * Message handler for BitTorrent protocol messages.
 */
public interface MessageHandler {

    /**
     * Called when unknown message arrives or message is known but not entirely
     * valid. No further messages will be processed and socket should be closed.
     * Any further calls to <code>MessageProcessor.processNext()</code> will
     * result in calling this method again. Also,
     * <code>MessageProcessor.hasNext()</code> will return false.
     */
    public void onError();

    /**
     * Called when <i>keep-alive<i> message arrives.
     */
    public void onKeepAlive();

    /**
     * Called when <i>choke</i> message arrives.
     */
    public void onChoke();

    /**
     * Called when <i>unchoke</i> message arrives.
     */
    public void onUnchoke();

    /**
     * Called when <i>interested</i> message arrives.
     */
    public void onInterested();

    /**
     * Called when <i>not-interested</i> message arrives.
     */
    public void onNotInterested();

    /**
     * Called when <i>bitfield</i> message arrives.
     * 
     * @param bitfield
     *            bitfield
     */
    public void onBitfield(ByteBuffer bitfield);

    /**
     * Called when <i>have</i> message arrives.
     * 
     * @param pieceIndex
     *            piece index
     */
    public void onHave(int pieceIndex);

    /**
     * Called when <i>request</i> message arrives.
     * 
     * @param pieceIndex
     *            piece index
     * @param blockOffset
     *            block offset
     * @param blockLength
     *            block length
     */
    public void onRequest(int pieceIndex, int blockOffset, int blockLength);

    /**
     * Called when <i>piece</i> message arrives.
     * 
     * @param pieceIndex
     *            piece index
     * @param blockOffset
     *            block offset
     * @param blockData
     *            block length
     */
    public void onPiece(int pieceIndex, int blockOffset, ByteBuffer blockData);

    /**
     * Called when <i>cancel</i> message arrives.
     * 
     * @param pieceIndex
     *            piece index
     * @param blockOffset
     *            block offset
     * @param blockLength
     *            block length
     */
    public void onCancel(int pieceIndex, int blockOffset, int blockLength);

    /**
     * Called when <i>port</i> message arrives.
     * 
     * @param port
     *            port
     */
    public void onPort(short port);

}

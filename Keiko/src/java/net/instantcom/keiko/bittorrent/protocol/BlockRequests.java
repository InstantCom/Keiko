package net.instantcom.keiko.bittorrent.protocol;

import net.instantcom.util.BitField;

/**
 * Block requests for a single piece.
 * <p>
 * Since requests are packed in bitfield it saves on number of objects in heap
 * as there is no need to create new object for each block requested.
 */
public class BlockRequests {

    /**
     * Creates new block requests for specified piece.
     * 
     * @param pieceIndex
     *            index of piece
     * @param numBlocks
     *            number of blocks in piece
     */
    public BlockRequests(int pieceIndex, int numBlocks) {
        this.pieceIndex = pieceIndex;
        requestedBlocks = new BitField(numBlocks);
        currentBlockIndex = 0;
    }

    /**
     * Gets index of one of requested blocks.
     * 
     * @return block index or -1 if there was no blocks queued
     */
    public int getRequestedBlockIndex() {
        if (0 == getNumBlocksRequested()) {
            return -1;
        }
        while (!requestedBlocks.get(currentBlockIndex)) {
            ++currentBlockIndex;
            currentBlockIndex %= requestedBlocks.getSize();
        }
        return currentBlockIndex;
    }

    /**
     * Returns number of blocks requested.
     * 
     * @return number of blocks requested
     */
    public int getNumBlocksRequested() {
        return requestedBlocks.getNumOnes();
    }

    /**
     * Requests specified block.
     * 
     * @param index
     *            block index
     */
    public void requestBlock(int index) {
        requestedBlocks.set(index);
    }

    /**
     * Unrequests (clears request for) specified block.
     * 
     * @param index
     *            block index
     */
    public void unrequestBlock(int index) {
        requestedBlocks.clear(index);
    }

    /**
     * Gets piece index.
     * 
     * @return piece index
     */
    public int getPieceIndex() {
        return pieceIndex;
    }

    private int pieceIndex;
    private BitField requestedBlocks;
    private int currentBlockIndex;

}

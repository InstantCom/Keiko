package net.instantcom.keiko.bittorrent.protocol;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import net.instantcom.util.BitField;

public class PiecePicker {

    private static final Log log = LogFactory.getLog(PiecePicker.class);

    public PiecePicker(Torrent torrent) {
        this.torrent = torrent;
        numPieces = torrent.getMetaInfo().getNumPieces();
        availability = new int[numPieces];
        reservations = new PeerConnection[numPieces];
    }

    /**
     * Updates stats based on contents of specified bitfield. This method should
     * be called only once for each remote peer. Any later updates for these
     * peers should be done via <code>update(int)</code> after receiving
     * "HAVE" messages.
     * 
     * @param bitfield
     *            remote peer's bitfield
     */
    public synchronized void update(BitField bitfield) {
        for (int i = 0; i < bitfield.getSize(); i++) {
            if (bitfield.get(i)) {
                availability[i]++;
            }
        }
    }

    /**
     * Updates stats for specified piece. This method is called either after
     * receiving "HAVE" message from remote peer or after completing the
     * download of a piece.
     * 
     * @param pieceIndex
     *            piece index
     */
    public synchronized void update(int pieceIndex) {
        availability[pieceIndex]++;
    }

    /**
     * Suggests a piece for download alternating between "rarest first" and
     * "random" piece downloading strategies. Suggested piece is reserved by
     * specified PeerConnection and won't be suggested again until reservation
     * is cancelled.
     * <p>
     * <i>Clients may choose to download pieces in random order. A better
     * strategy is to download pieces in rarest first order. The client can
     * determine this by keeping the initial bitfield from each peer, and
     * updating it with every 'have message. Then, the client can download the
     * pieces that appear least frequently in these peer bitfields. Note that
     * any Rarest First strategy should include randomization among at least
     * several of the least common pieces, as having many clients all attempting
     * to jump on the same "least common" piece would be counter productive.
     * </i>
     * 
     * @param pc
     *            PeerConnection used for piece reservation
     * @return piece index or -1 if there was no suitable piece
     */
    public synchronized int pickPiece(PeerConnection pc) {
        int pieceIndex;
        ++counter;
        counter &= 7;
        if (counter < 3) {
            pieceIndex = getRarestPiece(pc);
        } else {
            pieceIndex = getRandomPiece(pc);
        }
        if (!endGame) {
            endGame =
                torrent.getHavePieces().getNumOnes() == torrent.getMetaInfo()
                    .getNumPieces() - 1;
            if (endGame) {
                if (log.isDebugEnabled()) {
                    debug(" entered end game");
                }
            }
        }
        // don't reserve pieces in end game, all connection threads should
        // download it and they'll cancel requests when piece completes
        if (pieceIndex >= 0 && !endGame) {
            reservations[pieceIndex] = pc;
            if (log.isDebugEnabled()) {
                debug(pc.getPrefix() + "reserved piece " + pieceIndex);
            }
        }
        return pieceIndex;
    }

    /**
     * Cancels all piece reservations for specified PeerConnection. This method
     * should be called when PeerConnection is moved from active to choked list
     * or when connection is closed. Failing to cancel reservations results in
     * reserved pieces not being able to download.
     * 
     * @param pc
     *            PeerConnection whose reservations needs to be cancelled
     */
    public synchronized void cancelReservations(PeerConnection pc) {
        for (int i = 0; i < numPieces; i++) {
            if (pc == reservations[i]) {
                reservations[i] = null;
                if (log.isDebugEnabled()) {
                    debug(pc.getPrefix() + "cancelled reservation of piece "
                        + i);
                }
            }
        }
    }

    // returns index of one of the rarest pieces in swarm which haven't been
    // downloaded yet and have no reservation
    private int getRarestPiece(PeerConnection pc) {
        final int tolerance = Math.abs((int) System.currentTimeMillis()) % 5;
        BitField have = torrent.getHavePieces();
        BitField remoteHas = pc.getRemoteHavePieces();
        int bestValue = Integer.MAX_VALUE;
        int bestIndex = -1;
        for (int i = 0; i < numPieces; i++) {
            if (!have.get(i) && remoteHas.get(i) && null == reservations[i]
                && availability[i] < bestValue) {
                bestValue = availability[i] + tolerance;
                bestIndex = i;
            }
        }
        if (bestIndex >= 0 && log.isDebugEnabled()) {
            debug(pc.getPrefix() + "picked rarest piece: " + bestIndex);
        }
        return bestIndex;
    }

    // returns index of random piece which haven't been downloaded yet and has
    // no reservation
    private int getRandomPiece(PeerConnection pc) {
        BitField have = torrent.getHavePieces();
        BitField remoteHas = pc.getRemoteHavePieces();
        int bestIndex = -1;
        int index = Math.abs((int) System.currentTimeMillis()) % numPieces;
        for (int i = 0; i < numPieces; i++) {
            if (!have.get(index) && remoteHas.get(index)
                && null == reservations[index]) {
                bestIndex = index;
                break;
            }
            ++index;
            index %= numPieces;
        }
        if (bestIndex >= 0 && log.isDebugEnabled()) {
            debug(pc.getPrefix() + "picked random piece: " + bestIndex);
        }
        return bestIndex;
    }

    private void debug(String s) {
        log.debug(torrent.getPrefix().trim() + s);
    }

    private Torrent torrent;
    private int numPieces;
    private int[] availability;
    private PeerConnection[] reservations;
    private int counter = 0;
    private boolean endGame;

}

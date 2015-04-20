package net.instantcom.keiko.bittorrent.protocol.nio;

import java.nio.ByteBuffer;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import net.instantcom.keiko.bittorrent.protocol.Torrent;

/**
 * Non-blocking message processor.
 */
public class MessageProcessor extends Message {

    private static final Log log = LogFactory.getLog(MessageProcessor.class);

    // states
    private static final int STATE_ERROR = -1;
    private static final int STATE_EMPTY = 0;
    private static final int STATE_LEN_FETCHED = 1;
    private static final int STATE_ID_FETCHED = 2;
    private static final int STATE_COMPLETE = 3;

    /**
     * Creates new message processor.
     * 
     * @param buffer
     *            input buffer
     * @param handler
     *            message handler
     * @param torrent
     *            torrent
     */
    public MessageProcessor(ByteBuffer buffer, MessageHandler handler,
        Torrent torrent) {
        if (null == buffer) {
            throw new NullPointerException("buffer == null");
        }
        if (null == handler) {
            throw new NullPointerException("handler == null");
        }
        // torrent can be null during incoming handshake
        // if (null == torrent) {
        // throw new NullPointerException("torrent == null");
        // }
        if (buffer.capacity() < 16400) {
            if (buffer.capacity() < 16384) {
                String s =
                    "buffer too small: at least 16384 bytes are"
                        + " required, 16400 or more preferred";
                log.error(s);
                throw new IllegalArgumentException(s);
            } else {
                log.warn("minimum recommended buffer size is 16400,"
                    + " current size is " + buffer.capacity());
            }
        }
        this.buffer = buffer;
        this.handler = handler;
        this.torrent = torrent;
    }

    /**
     * Sets torrent.
     * 
     * @param torrent
     *            torrent
     */
    public void setTorrent(Torrent torrent) {
        this.torrent = torrent;
    }

    /**
     * Checks if there is next message available.
     * 
     * @return true if a message is available
     */
    public boolean hasNext() {
        // return false after error
        if (STATE_ERROR == state) {
            return false;
        }
        fetch();
        return STATE_COMPLETE == state;
    }

    /**
     * Processes next message calling appropriate handler method.
     * 
     * @see MessageHandler
     */
    public void processNext() {
        // don't process after error
        if (STATE_ERROR == state) {
            handler.onError();
            return;
        }
        if (STATE_COMPLETE != state) {
            // fetch in case caller is not using hasNext()
            fetch();
            return;
        }
        // check if keep-alive
        if (0 == len) {
            // keep-alive
            handler.onKeepAlive();
        } else {
            switch (id) {
                case ID_CHOKE:
                    handler.onChoke();
                    break;

                case ID_UNCHOKE:
                    handler.onUnchoke();
                    break;

                case ID_INTERESTED:
                    handler.onInterested();
                    break;

                case ID_NOTINTERESTED:
                    handler.onNotInterested();
                    break;

                case ID_HAVE:
                    handler.onHave(pieceIndex);
                    break;

                case ID_BITFIELD:
                    handler.onBitfield(bitfield);
                    break;

                case ID_REQUEST:
                    handler.onRequest(pieceIndex, blockOffset, blockLength);
                    break;

                case ID_PIECE:
                    handler.onPiece(pieceIndex, blockOffset, block);
                    break;

                case ID_CANCEL:
                    handler.onCancel(pieceIndex, blockOffset, blockLength);
                    break;

                case ID_PORT:
                    handler.onPort(port);
                    break;

                default:
                    log.error("default reached in processNext()");
                    break;
            }
        }
        // prepare for next message
        state = STATE_EMPTY;
        len = 0;
        id = 0;
        pieceIndex = 0;
        blockOffset = 0;
        blockLength = 0;
        port = 0;
        bitfield = null;
        block = null;
    }

    private void fetch() {
        // don't fetch if already completely fetched (not yet processed)
        if (STATE_COMPLETE == state) {
            return;
        }
        try {
            switch (state) {
                case STATE_EMPTY:
                    // enough for len?
                    if (buffer.remaining() < 4) {
                        // nope
                        break;
                    }
                    // get len
                    len = buffer.getInt();
                    state = STATE_LEN_FETCHED;
                    // check if keep-alive
                    if (0 == len) {
                        // got all
                        state = STATE_COMPLETE;
                        break;
                    }
                    // continues

                case STATE_LEN_FETCHED:
                    // enough for id?
                    if (buffer.remaining() < 1) {
                        // nope
                        break;
                    }
                    // get id
                    id = buffer.get();
                    state = STATE_ID_FETCHED;
                    // continues

                case STATE_ID_FETCHED:
                    switch (id) {
                        case ID_CHOKE:
                        case ID_UNCHOKE:
                        case ID_INTERESTED:
                        case ID_NOTINTERESTED:
                            assertEquals(1, len);
                            // got all
                            state = STATE_COMPLETE;
                            break;

                        case ID_HAVE:
                            assertEquals(5, len);
                            // enough for piece index?
                            if (buffer.remaining() < 4) {
                                // nope
                                break;
                            }
                            // get piece index
                            pieceIndex = buffer.getInt();
                            // got all
                            state = STATE_COMPLETE;
                            break;

                        case ID_BITFIELD:
                            assertEquals(1 + torrent.getMetaInfo()
                                .getNumPieces(), len);
                            int bitfieldSize = len - 1;
                            // enough for bitfield?
                            if (buffer.remaining() < bitfieldSize) {
                                // nope
                                break;
                            }
                            // get bitfield
                            bitfield = buffer.slice();
                            bitfield.limit(bitfieldSize);
                            buffer.position(buffer.position() + bitfieldSize);
                            // got all
                            state = STATE_COMPLETE;
                            break;

                        case ID_REQUEST:
                            assertEquals(13, len);
                            // enough for pieceIndex, offset and length?
                            if (buffer.remaining() < 12) {
                                // nope
                                break;
                            }
                            // get pieceIndex, offset and length
                            pieceIndex = buffer.getInt();
                            blockOffset = buffer.getInt();
                            blockLength = buffer.getInt();
                            // got all
                            state = STATE_COMPLETE;
                            break;

                        case ID_PIECE:
                            assertTrue(len < 9 + 16384);
                            // enough for index, offset and block?
                            int blockSize = len - 9;
                            if (buffer.remaining() < blockSize) {
                                // nope
                                break;
                            }
                            // get index, offset and block
                            pieceIndex = buffer.getInt();
                            blockOffset = buffer.getInt();
                            block = buffer.slice();
                            block.limit(blockSize);
                            buffer.position(buffer.position() + blockSize);
                            // got all
                            state = STATE_COMPLETE;
                            break;

                        case ID_CANCEL:
                            assertEquals(13, len);
                            // enough for index, offset and length?
                            if (buffer.remaining() < 12) {
                                // nope
                                break;
                            }
                            // get index, offset and length
                            pieceIndex = buffer.getInt();
                            blockOffset = buffer.getInt();
                            blockLength = buffer.getInt();
                            // got all
                            state = STATE_COMPLETE;
                            break;

                        case ID_PORT:
                            assertEquals(3, len);
                            // enough for port?
                            if (buffer.remaining() < 2) {
                                // nope
                                break;
                            }
                            // get port
                            port = buffer.getShort();
                            // got all
                            state = STATE_COMPLETE;
                            break;

                        default:
                            // unknown message
                            throw new UnsupportedOperationException();
                    }
                    break;

                default:
                    // unknown state
                    throw new UnsupportedOperationException();
            }
        } catch (UnsupportedOperationException e) {
            state = STATE_ERROR;
        }
        // compact buffer
        buffer.compact();
    }

    private void assertEquals(int expected, int actual)
        throws UnsupportedOperationException {
        if (expected != actual) {
            throw new UnsupportedOperationException();
        }
    }

    private void assertTrue(boolean actual)
        throws UnsupportedOperationException {
        if (!actual) {
            throw new UnsupportedOperationException();
        }
    }

    private ByteBuffer buffer;
    private MessageHandler handler;
    private Torrent torrent;
    private int state = STATE_EMPTY;
    private int len;
    private byte id;
    private int pieceIndex;
    private int blockOffset;
    private int blockLength;
    private short port;
    private ByteBuffer bitfield;
    private ByteBuffer block;

}

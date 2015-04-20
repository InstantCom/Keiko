package net.instantcom.keikosniffer;

import java.io.IOException;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Map;
import java.util.Queue;

import jpcap.packet.TCPPacket;
import net.instantcom.keiko.ksp.ConnectionState;
import net.instantcom.keiko.ksp.InvalidMessageException;
import net.instantcom.keiko.ksp.Message;
import net.instantcom.keiko.ksp.ProtocolListener;
import net.instantcom.keiko.ksp.SnifferProtocolThread;
import net.instantcom.keikosniffer.config.Configuration;
import net.instantcom.keikosniffer.http.AnalyzedResult;
import net.instantcom.keikosniffer.http.HttpUtil;
import net.instantcom.keikosniffer.stream.OrderedPacketStream;
import net.instantcom.keikosniffer.stream.StreamTooLargeException;
import net.instantcom.util.BinaryMatcher;
import net.instantcom.util.LRUCache;
import net.instantcom.util.bcoder.BStreamDecoder;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.HttpEntity;
import org.apache.http.HttpException;
import org.apache.http.HttpMessage;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.MalformedChunkCodingException;

public class PacketProcessor implements Runnable, ProtocolListener {

    private static final Log log = LogFactory.getLog(PacketProcessor.class);

    private static final int MAX_PACKETS_IN_QUEUE = 1000;
    private static final long QUEUE_CHECK_INTERVAL = 10000L; // 10 seconds

    private static final String BENC_PIECES_STRING = "6:pieces";
    private static final String BENC_PEERS_STRING = "5:peers";

    private static final BinaryMatcher requestMatcher = new BinaryMatcher(new String[] {
        "GET", "/announce", "info_hash"
    });
    private static final BinaryMatcher responseMatcher = new BinaryMatcher(new String[] {
        "5:peers"
    });
    private static final BinaryMatcher responseFailureMatcher = new BinaryMatcher(new String[] {
        "14:failure reason"
    });
    private static final BinaryMatcher torrentMatcher = new BinaryMatcher(new String[] {
        "8:announce"
    });

    // private static final byte[] EMPTY_ARRAY = new byte[0];

    public PacketProcessor(int threadNum, Queue<TCPPacket> queue, SnifferProtocolThread kspThread)
        throws IOException {
        this.threadNum = threadNum;
        this.queue = queue;
        this.kspThread = kspThread;
    }

    @Override
    public void run() {
        info("started");

        kspThread.setListener(this);

        while (KeikoSniffer.running) {
            TCPPacket packet = queue.poll();
            if (null == packet) {
                try {
                    Thread.sleep(100L);
                } catch (InterruptedException ignored) {
                }
                continue;
            }

            long now = System.currentTimeMillis();
            if (now >= nextCheckTime) {
                nextCheckTime = now + QUEUE_CHECK_INTERVAL;
                if (queue.size() >= MAX_PACKETS_IN_QUEUE) {
                    queue.clear();
                    cache.clear();
                    continue;
                }
            }

            String key = getKey(packet);
            try {
                // if (null == packet.data) {
                // packet.data = EMPTY_ARRAY;
                // }
                byte[] data = packet.data;

                // get stream from cache
                OrderedPacketStream stream = cache.get(key);
                if (null == stream) {
                    // check if it looks like something we should track (request or response)
                    int type = -1;
                    if (looksLikeRequest(data)) {
                        type = OrderedPacketStream.TYPE_REQUEST;
                    } else if (looksLikeHttpResponse(data) || looksLikeHeaderlessResponse(data)) {
                        type = OrderedPacketStream.TYPE_RESPONSE;
                        ++numResponseStreamsCreated;
                    }
                    if (type < 0) {
                        continue;
                    }
                    // create stream
                    stream = new OrderedPacketStream(key, type);
                    // cache stream
                    cache.put(key, stream);
                    if (log.isDebugEnabled()) {
                        debug("created stream (" + type + "): " + key);
                    }
                }

                // append to stream
                stream.append(packet);
                if (log.isDebugEnabled()) {
                    debug("appended packet to stream (" + stream.getType() + "): " + key);
                }

                // identify and process if ready for parsing
                if (stream.isReadyForParsing()) {
                    int type = stream.getType();
                    try {
                        HttpMessage msg = stream.parse();
                        switch (type) {
                            case OrderedPacketStream.TYPE_REQUEST:
                                data = stream.getDataAsBuffer().toByteArray();
                                if (looksLikeAnnounceRequest(data)) {
                                    stats.incRequestsFound();
                                    HttpRequest request = (HttpRequest) msg;
                                    String uri = request.getRequestLine().getUri();
                                    if (log.isDebugEnabled()) {
                                        debug("got REQUEST: " + uri);
                                    }
                                    send(Message.MSGTYPE_ANNOUNCE_REQUEST, uri.getBytes(), packet);
                                }
                                cache.remove(key);
                                if (log.isDebugEnabled()) {
                                    debug("removed stream (request MAYBE sent): " + key);
                                }
                                break;

                            case OrderedPacketStream.TYPE_RESPONSE:
                                HttpResponse response = (HttpResponse) msg;
                                HttpEntity body = response.getEntity();
                                if (null != body) {
                                    // Header contentType = body.getContentType();
                                    data = HttpUtil.getBody(response);
                                    if (isDataCompleted(data)) {
                                        if (log.isDebugEnabled()) {
                                            debug("body IS completed");
                                        }
                                        body.consumeContent();
                                        if (looksLikeHeaderlessResponse(data)) {
                                            AnalyzedResult result = analyze(data);
                                            if (result.isPotentialTrackerFailure()) {
                                                cache.remove(key);
                                                if (log.isDebugEnabled()) {
                                                    debug("removed stream (response failure): "
                                                        + key);
                                                }
                                                stats.incResponseFailuresFound();
                                                if (log.isDebugEnabled()) {
                                                    debug("got FAILURE: " + new String(data));
                                                }
                                            } else if (result.isPotentialTrackerResponse()) {
                                                cache.remove(key);
                                                if (log.isDebugEnabled()) {
                                                    debug("removed stream (tracker response): "
                                                        + key);
                                                }
                                                stats.incResponsesFound();
                                                if (log.isDebugEnabled()) {
                                                    // debug("got RESPONSE: " + new String(data));
                                                    debug("got RESPONSE: " + Arrays.toString(data));
                                                }
                                                send(Message.MSGTYPE_ANNOUNCE_RESPONSE, data,
                                                    packet);
                                            } else if (result.isPotentialTorrent()) {
                                                cache.remove(key);
                                                if (log.isDebugEnabled()) {
                                                    debug("removed stream (torrent): " + key);
                                                }
                                                stats.incTorrentsFound();
                                                if (log.isDebugEnabled()) {
                                                    // debug("got TORRENT: " + new String(data));
                                                    debug("got TORRENT: " + Arrays.toString(data));
                                                }
                                                send(Message.MSGTYPE_TORRENT, data, packet);
                                            } else {
                                                if (log.isDebugEnabled()) {
                                                    debug("body DOESN'T look like anything i need");
                                                }
                                            }
                                        } else {
                                            if (log.isDebugEnabled()) {
                                                debug("body NOT looking like headerless response");
                                            }
                                        }
                                    } else {
                                        if (log.isDebugEnabled()) {
                                            debug("body NOT completed");
                                        }
                                    }
                                } else {
                                    if (log.isDebugEnabled()) {
                                        debug("body == null");
                                    }
                                }
                                break;

                            default:
                                throw new IllegalArgumentException("unknown type: " + type);
                        }
                    } catch (HttpException e) {
                        // this happens when httpcore can't parse response because there is no http
                        // status line nor http headers. it's common for trackers to send raw
                        // response
                        data = stream.getDataAsBuffer().toByteArray();
                        if (OrderedPacketStream.TYPE_RESPONSE == type
                            && looksLikeHeaderlessResponse(data) && isDataCompleted(data)) {
                            if (log.isDebugEnabled()) {
                                debug("processing headerless response:\n" + new String(data));
                            }
                            AnalyzedResult result = analyze(data);
                            if (result.isPotentialTrackerFailure()) {
                                cache.remove(key);
                                if (log.isDebugEnabled()) {
                                    debug("removed stream (headerless tracker failure): " + key);
                                }
                                stats.incResponseFailuresFound();
                                if (log.isDebugEnabled()) {
                                    debug("got FAILURE: " + new String(data));
                                }
                            } else if (result.isPotentialTrackerResponse()) {
                                cache.remove(key);
                                if (log.isDebugEnabled()) {
                                    debug("removed stream (headerless response): " + key);
                                }
                                stats.incResponsesFound();
                                if (log.isDebugEnabled()) {
                                    // debug("got RESPONSE: " + new String(data));
                                    debug("got RESPONSE: " + Arrays.toString(data));
                                }
                                send(Message.MSGTYPE_ANNOUNCE_RESPONSE, data, packet);
                            } else if (result.isPotentialTorrent()) {
                                cache.remove(key);
                                if (log.isDebugEnabled()) {
                                    debug("removed stream (headerless torrent): " + key);
                                }
                                stats.incTorrentsFound();
                                if (log.isDebugEnabled()) {
                                    // debug("got TORRENT: " + new String(data));
                                    debug("got TORRENT: " + Arrays.toString(data));
                                }
                                send(Message.MSGTYPE_TORRENT, data, packet);
                            } else {
                                if (log.isDebugEnabled()) {
                                    debug("don't know what to do with UNRECOGNIZED headerless stream: "
                                        + key);
                                }
                            }
                        }
                    }
                }
            } catch (BufferOverflowException e) {
                cache.clear();
                warn("Buffer overflow detected, disconnecting to let her breathe a bit...");
                kspThread.disconnect();
            } catch (MalformedChunkCodingException e) {
                cache.remove(key);
                if (log.isDebugEnabled()) {
                    debug("removed stream (malformed chunk coding): " + key);
                    debug("run", e);
                }
            } catch (StreamTooLargeException e) {
                cache.remove(key);
                if (log.isDebugEnabled()) {
                    debug("removed stream (stream too large): " + key);
                    debug("run", e);
                }
            } catch (Exception e) {
                // this shouldn't happen
                error("run", e);
                error("was processing packet:\n" + Arrays.toString(packet.data));
                // stop caching
                cache.remove(key);
                if (log.isDebugEnabled()) {
                    debug("removed stream (generic exception): " + key);
                }
            }
        }
        kspThread.shutdown();

        info("stopped");

        if (log.isDebugEnabled()) {
            debug("created " + numResponseStreamsCreated + " response streams");
            if (cache.size() > 0) {
                debug("the following keys are still in cache:");
                for (OrderedPacketStream stream : cache.values()) {
                    debug("  " + stream.getKey());
                }
            }
        }
    }

    private boolean isDataCompleted(byte[] data) {
        if (null == data) {
            if (log.isDebugEnabled()) {
                debug("body NOT completed because data == null");
            }
            return false;
        }
        // XXX this makes lots of false positives. every time packet
        // ends with 'e' we get false positive and assume stream is
        // completed even when it's not!
        // return data.length > 0 && 'e' == data[data.length - 1];

        // XXX since there is no way to find out if stream is completed or not, we try to bdecode
        // response and see if it passes or not. this costs a lot and we're doing it after every
        // packet which has 'e' at the end! however, keiko will thank us for this check and won't
        // disconnect us like it did before when we were sending garbage.
        if (data.length < 8) {
            if (log.isDebugEnabled()) {
                debug("body NOT completed because data.length < 8");
            }
            return false;
        }
        // each bencoded response starts with 'd' (dictionary)
        if ('d' != data[0]) {
            if (log.isDebugEnabled()) {
                debug("body NOT completed because data doesn't start with 'd'");
            }
            return false;
        }
        // each bencoded response ends with 'e' (end of dictionary)
        if ('e' != data[data.length - 1]) {
            if (log.isDebugEnabled()) {
                debug("body NOT completed because data doesn't end with 'e', data: "
                    + Arrays.toString(data));
            }
            return false;
        }
        boolean result = false;
        try {
            Map<String, Object> map = bsd.decode(ByteBuffer.wrap(data));
            if (null != map && !map.isEmpty()) {
                // everything looks ok
                result = true;
            }
        } catch (Exception e) {
            if (log.isDebugEnabled()) {
                debug("isDataCompleted", e);
            }
        }
        return result;
    }

    @Override
    public void onMessage(Message msg) throws InvalidMessageException {
        // nothing to do, we're not receiving any messages
    }

    public void send(Message msg) throws Exception {
        ConnectionState cs = kspThread.getThreadSpecificConnectionState();
        if (null != cs && cs.isConnected()) {
            if (log.isDebugEnabled()) {
                String s = new String(msg.getData(), "US-ASCII");
                int pos = s.indexOf(BENC_PEERS_STRING);
                if (pos > 0) {
                    s = s.substring(0, pos + BENC_PEERS_STRING.length()) + "...";
                } else {
                    pos = s.indexOf(BENC_PIECES_STRING);
                    if (pos > 0) {
                        s = s.substring(0, pos + BENC_PIECES_STRING.length()) + "...";
                    }
                }
                debug("sending message: " + msg + ",data=" + s);
            }
            kspThread.sendMessage(msg);
            // count only successfully sent messages
            if (!KeikoSniffer.USE_DUMP_CAP_FILE) {
                if (Message.MSGTYPE_TORRENT == msg.getType()) {
                    stats.incTorrentsSent();
                } else if (Message.MSGTYPE_ANNOUNCE_REQUEST == msg.getType()) {
                    stats.incRequestsSent();
                } else if (Message.MSGTYPE_ANNOUNCE_RESPONSE == msg.getType()) {
                    stats.incResponsesSent();
                }
            }
        }
        // fake successfully sent messages (not connected to keiko)
        if (KeikoSniffer.USE_DUMP_CAP_FILE) {
            if (Message.MSGTYPE_TORRENT == msg.getType()) {
                stats.incTorrentsSent();
            } else if (Message.MSGTYPE_ANNOUNCE_REQUEST == msg.getType()) {
                stats.incRequestsSent();
            } else if (Message.MSGTYPE_ANNOUNCE_RESPONSE == msg.getType()) {
                stats.incResponsesSent();
            }
        }
    }

    private void send(int type, byte[] data, TCPPacket packet) throws Exception {
        if (type < 0) {
            throw new Exception("trying to send unknown stream");
        }
        if (log.isDebugEnabled()) {
            debug("sending data of size " + data.length);
        }
        Message msg = createMessageFromPacket(type, packet);
        msg.setData(data);
        send(msg);
    }

    private Message createMessageFromPacket(int type, TCPPacket packet) {
        Message msg = new Message();
        msg.setType(type);
        byte[] ip = packet.src_ip.getAddress();
        msg.setSourceIp(((ip[0] & 0xff) << 24) | ((ip[1] & 0xff) << 16) | ((ip[2] & 0xff) << 8)
            | ((ip[3] & 0xff)));
        msg.setSourcePort(packet.src_port);
        ip = packet.dst_ip.getAddress();
        msg.setDestinationIp(((ip[0] & 0xff) << 24) | ((ip[1] & 0xff) << 16)
            | ((ip[2] & 0xff) << 8) | ((ip[3] & 0xff)));
        msg.setDestinationPort(packet.dst_port);
        return msg;
    }

    private boolean looksLikeHeaderlessResponse(byte[] data) {
        if (null == data) {
            return false;
        }
        if (data.length < 8) {
            return false;
        }
        if ((byte) 'd' != data[0]) {
            return false;
        }
        return true;
    }

    private AnalyzedResult analyze(byte[] data) {
        AnalyzedResult result = new AnalyzedResult();
        result.setPotentialTorrent(torrentMatcher.matches(data, data.length));
        result.setPotentialTrackerFailure(responseFailureMatcher.matches(data, data.length));
        result.setPotentialTrackerResponse(responseMatcher.matches(data, data.length));
        return result;
    }

    private boolean looksLikeHttpResponse(byte[] data) {
        if (null == data) {
            return false;
        }
        if (data.length < 8) {
            return false;
        }
        if ('H' == data[0] && 'T' == data[1] && 'T' == data[2] && 'P' == data[3]) {
            return true;
        }
        return false;
    }

    private boolean looksLikeRequest(byte[] data) {
        if (null == data) {
            return false;
        }
        if (data.length < 8) {
            return false;
        }
        if ('G' == data[0] && 'E' == data[1] && 'T' == data[2]) {
            return true;
        }
        return false;
    }

    private boolean looksLikeAnnounceRequest(byte[] data) {
        return requestMatcher.matches(data, data.length);
    }

    public Stats getStats() {
        return stats;
    }

    public static String getKey(TCPPacket packet) {
        StringBuffer sb = new StringBuffer();
        sb.append(packet.src_ip.getHostAddress());
        sb.append(':');
        sb.append(packet.src_port);
        sb.append('-');
        sb.append(packet.dst_ip.getHostAddress());
        sb.append(':');
        sb.append(packet.dst_port);
        return sb.toString();
    }

    public String getName() {
        return "PacketProcessor" + "-" + threadNum;
    }

    private void debug(String s) {
        log.debug(getName() + " " + s);
    }

    private void debug(String s, Throwable t) {
        log.debug(getName() + " " + s, t);
    }

    private void info(String s) {
        log.info(getName() + " " + s);
    }

    private void warn(String s) {
        log.warn(getName() + " " + s);
    }

    // private void warn(String s, Throwable t) {
    // log.warn(getName() + " " + s, t);
    // }

    private void error(String s) {
        log.error(getName() + " " + s);
    }

    private void error(String s, Throwable t) {
        log.error(getName() + " " + s, t);
    }

    private int threadNum;
    private Queue<TCPPacket> queue;
    private SnifferProtocolThread kspThread;
    private final LRUCache<String, OrderedPacketStream> cache =
        new LRUCache<String, OrderedPacketStream>(Configuration.getInstance().getInt(
            "tcp.cache.size", 1000));
    private final Stats stats = new Stats();
    private final BStreamDecoder bsd = new BStreamDecoder();
    private int numResponseStreamsCreated;
    private long nextCheckTime;

}

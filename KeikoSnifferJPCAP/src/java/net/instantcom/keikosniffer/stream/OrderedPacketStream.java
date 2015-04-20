package net.instantcom.keikosniffer.stream;

import java.io.IOException;
import java.util.Map;
import java.util.TreeMap;

import jpcap.packet.TCPPacket;
import net.instantcom.keikosniffer.http.SessionInputBufferMockup;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.HttpException;
import org.apache.http.HttpMessage;
import org.apache.http.HttpRequestFactory;
import org.apache.http.HttpResponse;
import org.apache.http.HttpResponseFactory;
import org.apache.http.impl.DefaultHttpRequestFactory;
import org.apache.http.impl.DefaultHttpResponseFactory;
import org.apache.http.impl.entity.EntityDeserializer;
import org.apache.http.impl.entity.StrictContentLengthStrategy;
import org.apache.http.impl.io.HttpRequestParser;
import org.apache.http.impl.io.HttpResponseParser;
import org.apache.http.io.SessionInputBuffer;
import org.apache.http.message.BasicLineParser;
import org.apache.http.message.LineParser;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.util.ByteArrayBuffer;

public class OrderedPacketStream {

    private static final Log log = LogFactory.getLog(OrderedPacketStream.class);

    // mega packs up to 100-200 gigs are popular these days. these torrent files take up to 2 megs.
    private static final int MAX_SIZE = 4 * 1024 * 1024; // 4 MB

    public static final int TYPE_REQUEST = 0;
    public static final int TYPE_RESPONSE = 1;

    private static final HttpRequestFactory requestFactory = new DefaultHttpRequestFactory();
    private static final HttpResponseFactory responseFactory = new DefaultHttpResponseFactory();
    private static final LineParser lineParser = new BasicLineParser();
    private static final EntityDeserializer entityDeserializer =
        new EntityDeserializer(new StrictContentLengthStrategy());

    public OrderedPacketStream(String key, int type) {
        if (null == key) {
            throw new IllegalArgumentException("key == null");
        }
        if (TYPE_REQUEST != type && TYPE_RESPONSE != type) {
            throw new IllegalArgumentException("unsupported type: " + type);
        }
        this.key = key;
        this.type = type;
    }

    public ByteArrayBuffer getDataAsBuffer() {
        // if (!isReadyForParsing()) {
        // throw new IllegalArgumentException(
        // "not ready for parsing, some packets are out-of-order");
        // }
        // int size = 0;
        ByteArrayBuffer buffer = new ByteArrayBuffer(0);
        for (TCPPacket packet : orderedMap.values()) {
            byte[] data = packet.data;
            buffer.append(data, 0, data.length);
            // size += data.length;
        }
        // buffer.setLength(size);
        return buffer;
    }

    public boolean isReadyForParsing() {
        boolean result = hasAllPacketsInOrder();
        if (log.isDebugEnabled()) {
            log.debug("stream " + (result ? "" : "NOT ") + "ready for parsing: " + key);
        }
        return result;
    }

    public HttpMessage parse() throws HttpException, IOException {
        // if (!isReadyForParsing()) {
        // throw new IllegalArgumentException(
        // "not ready for parsing, some packets are out-of-order");
        // }
        if (log.isDebugEnabled()) {
            log.debug("parsing stream: " + key);
        }
        // byte[] data = getDataAsBuffer().buffer();
        byte[] data = getDataAsBuffer().toByteArray();
        SessionInputBuffer input = new SessionInputBufferMockup(data);
        HttpMessage msg = null;
        switch (type) {
            case TYPE_REQUEST:
                HttpRequestParser requestParser =
                    new HttpRequestParser(input, lineParser, requestFactory, new BasicHttpParams());
                msg = requestParser.parse();
                break;

            case TYPE_RESPONSE:
                HttpResponseParser responseParser =
                    new HttpResponseParser(input, lineParser, responseFactory,
                        new BasicHttpParams());
                msg = responseParser.parse();
                ((HttpResponse) msg).setEntity(entityDeserializer.deserialize(input, msg));
                break;
        }
        return msg;
    }

    public void append(TCPPacket packet) throws StreamTooLargeException {
        orderedMap.put(packet.sequence, packet);
        size += packet.data.length;
        if (size > MAX_SIZE) {
            throw new StreamTooLargeException();
        }
    }

    public boolean hasAllPacketsInOrder() {
        if (orderedMap.isEmpty()) {
            return false;
        }
        boolean result = true;
        long nextSequence = -1;
        for (Long key : orderedMap.keySet()) {
            TCPPacket packet = orderedMap.get(key);
            if (nextSequence < 0) {
                nextSequence = packet.sequence + packet.data.length;
            } else {
                if (nextSequence != packet.sequence) {
                    result = false;
                    break;
                }
                nextSequence += packet.data.length;
            }
        }
        return result;
    }

    public String getKey() {
        return key;
    }

    public int getType() {
        return type;
    }

    public int getSize() {
        return size;
    }

    private String key;
    private int type;
    private Map<Long, TCPPacket> orderedMap = new TreeMap<Long, TCPPacket>();
    private int size;

}

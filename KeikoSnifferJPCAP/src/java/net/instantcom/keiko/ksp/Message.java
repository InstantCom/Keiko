package net.instantcom.keiko.ksp;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

public class Message implements Serializable {

    private static final long serialVersionUID = 20080904L;

    public static final int MSGTYPE_TORRENT = 0x00;
    public static final int MSGTYPE_ANNOUNCE_REQUEST = 0x01;
    public static final int MSGTYPE_ANNOUNCE_RESPONSE = 0x02;

    public Message() {
    }

    public Message(ByteBuffer buf) throws MessageTooLargeException, BufferUnderflowException {
        int len = buf.getInt();
        if (buf.capacity() < len + 4) {
            throw new MessageTooLargeException("message too large for buffer: msgLen=" + len
                + ": bufLen=" + buf.capacity());
        }
        if (buf.remaining() < len) {
            throw new BufferUnderflowException();
        }
        type = buf.get();
        if (type < 0) {
            type += 0x100;
        }
        sourceIp = buf.getInt();
        sourcePort = buf.getShort();
        if (sourcePort < 0) {
            sourcePort += 0x10000;
        }
        destinationIp = buf.getInt();
        destinationPort = buf.getShort();
        if (destinationPort < 0) {
            destinationPort += 0x10000;
        }
        data = new byte[len - 13];
        buf.get(data);
    }

    public String getKey() {
        StringBuffer sb = new StringBuffer();
        String key = null;
        if (MSGTYPE_ANNOUNCE_REQUEST == type) {
            sb.append(sourceIp).append(sourcePort).append(destinationIp).append(destinationPort);
            key = sb.toString();
        } else if (MSGTYPE_ANNOUNCE_RESPONSE == type) {
            sb.append(destinationIp).append(destinationPort).append(sourceIp).append(sourcePort);
            key = sb.toString();
        }
        return key;
    }

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }

    public int getSourceIp() {
        return sourceIp;
    }

    public void setSourceIp(int sourceIp) {
        this.sourceIp = sourceIp;
    }

    public int getSourcePort() {
        return sourcePort;
    }

    public void setSourcePort(int sourcePort) {
        this.sourcePort = sourcePort;
    }

    public int getDestinationIp() {
        return destinationIp;
    }

    public void setDestinationIp(int destinationIp) {
        this.destinationIp = destinationIp;
    }

    public int getDestinationPort() {
        return destinationPort;
    }

    public void setDestinationPort(int destinationPort) {
        this.destinationPort = destinationPort;
    }

    public byte[] getData() {
        return data;
    }

    public void setData(byte[] data) {
        this.data = data;
    }

    protected SocketChannel getSocketChannel() {
        return socketChannel;
    }

    public void setSocketChannel(SocketChannel socketChannel) {
        this.socketChannel = socketChannel;
    }

    protected void setProtocolThread(ProtocolThread protocolThread) {
        this.protocolThread = protocolThread;
    }

    public void sendReply(Message msg) throws Exception {
        msg.setSocketChannel(socketChannel);
        protocolThread.sendMessage(msg);
    }

    private void writeObject(ObjectOutputStream out) throws IOException {
        out.writeInt(type);
        out.writeInt(sourceIp);
        out.writeInt(sourcePort);
        out.writeInt(destinationIp);
        out.writeInt(destinationPort);
        if (null == data) {
            out.writeInt(0);
        } else {
            out.writeInt(data.length);
            if (data.length > 0) {
                out.write(data);
            }
        }
    }

    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        type = in.readInt();
        sourceIp = in.readInt();
        sourcePort = in.readInt();
        destinationIp = in.readInt();
        destinationPort = in.readInt();
        int dataSize = in.readInt();
        if (0 != dataSize) {
            data = new byte[dataSize];
            in.readFully(data);
        }
    }

    @Override
    public String toString() {
        StringBuffer sb = new StringBuffer();
        sb.append("type=");
        switch (type) {
            case MSGTYPE_TORRENT:
                sb.append("TORRENT");
                break;

            case MSGTYPE_ANNOUNCE_REQUEST:
                sb.append("REQUEST");
                break;

            case MSGTYPE_ANNOUNCE_RESPONSE:
                sb.append("RESPONSE");
                break;

            default:
                sb.append("UNKNOWN");
                break;
        }
        sb.append(", sourceIp=");
        sb.append(ipToString(sourceIp));
        sb.append(", sourcePort=");
        sb.append(sourcePort);
        sb.append(", destinationIp=");
        sb.append(ipToString(destinationIp));
        sb.append(", destinationPort=");
        sb.append(destinationPort);
        // try {
        // String s = new String(data, "UTF-8");
        // sb.append(", body=");
        // sb.append(s);
        // } catch (Exception e) {
        // e.printStackTrace();
        // }
        return sb.toString();
    }

    private String ipToString(int ip) {
        StringBuffer sb = new StringBuffer();
        int i = (ip >> 24) & 0xff;
        if (i < 0) {
            i += 0x0100;
        }
        sb.append(i);
        sb.append('.');
        i = (ip >> 16) & 0xff;
        if (i < 0) {
            i += 0x0100;
        }
        sb.append(i);
        sb.append('.');
        i = (ip >> 8) & 0xff;
        if (i < 0) {
            i += 0x0100;
        }
        sb.append(i);
        sb.append('.');
        i = ip & 0xff;
        if (i < 0) {
            i += 0x0100;
        }
        sb.append(i);
        return sb.toString();
    }

    private int type;
    private int sourceIp;
    private int sourcePort;
    private int destinationIp;
    private int destinationPort;
    private byte[] data;

    private ProtocolThread protocolThread;
    private SocketChannel socketChannel;

}

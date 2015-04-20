package net.instantcom.keiko.bittorrent.protocol.nio;

public abstract class Message {

    public static final int ID_CHOKE = 0;
    public static final int ID_UNCHOKE = 1;
    public static final int ID_INTERESTED = 2;
    public static final int ID_NOTINTERESTED = 3;
    public static final int ID_HAVE = 4;
    public static final int ID_BITFIELD = 5;
    public static final int ID_REQUEST = 6;
    public static final int ID_PIECE = 7;
    public static final int ID_CANCEL = 8;
    public static final int ID_PORT = 9;

}

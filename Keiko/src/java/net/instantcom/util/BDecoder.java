package net.instantcom.util;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Stack;

import net.instantcom.keiko.bittorrent.MetaInfo;
import net.instantcom.keiko.peer.Peer;

public abstract class BDecoder {

    // private static final Log log = LogFactory.getLog(BDecoder.class);

    /**
     * Loads MetaInfo from .torrent file.
     * 
     * @param file
     *            .torrent file
     * @return MetaInfo filled with values from .torrent file
     * @throws Exception
     *             in case of error
     */
    @SuppressWarnings("unchecked")
    public static MetaInfo loadMetaInfo(File file) throws Exception {
        MetaInfo meta = new MetaInfo();

        HashMap map =
            bdecode(new BufferedInputStream(new FileInputStream(file)));
        meta.setAnnounce((String) map.get("announce"));
        meta.setAnnounceList((List) map.get("announce-list"));
        meta.setComment((String) map.get("comment"));
        meta.setCreatedBy((String) map.get("created by"));
        meta.setCreationDate(Long.parseLong((String) map.get("creation date")));
        meta.setEncoding((String) map.get("encoding"));
        meta.setInfoHash((byte[]) map.get("info hash"));

        HashMap info = (HashMap) map.get("info");
        meta.setName((String) info.get("name"));
        meta.setFiles((List) info.get("files"));
        meta
            .setPieceLength(Integer.parseInt((String) info.get("piece length")));
        meta.setLength(Long.parseLong((String) info.get("length")));
        meta.setLastPieceLength((int) meta.getLength() % meta.getPieceLength());
        meta.setPiecesSHA1((byte[]) info.get("pieces"));

        return meta;
    }

    /**
     * BDecodes InputStream and saves results in HashMap.
     * 
     * @param is
     *            bencoded InputStream
     * @return HashMap with values
     * @throws Exception
     *             in case of error
     */
    @SuppressWarnings("unchecked")
    public static HashMap bdecode(InputStream is) throws Exception {
        final int STATE_UNKNOWN = 0;
        final int STATE_INTEGER = 1;
        int state = STATE_UNKNOWN;
        long length = 0;
        HashMap root = new HashMap();
        Object current = root;
        Stack<Object> stack = new Stack<Object>();
        String name = "root";
        String value = "";
        String keyword = "";
        boolean recording = false;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        while (true) {
            int b = is.read();
            if (-1 == b) {
                break;
            }
            if (recording) {
                baos.write(b);
            }
            final char c = (char) b;
            if (':' == c) {
                // string
                int size = Integer.parseInt(keyword);
                if ("pieces".equals(name)) {
                    byte[] piecesSHA1 = new byte[size];
                    for (int i = 0; i < size; i++) {
                        b = is.read();
                        piecesSHA1[i] = (byte) b;
                        if (recording) {
                            baos.write(b);
                        }
                    }
                    ((HashMap) current).put(name, piecesSHA1);
                    name = value = null;
                } else if ("peers".equals(name)) {
                    List<Peer> peerList = new ArrayList<Peer>();
                    int peerListSize = size / 6;
                    StringBuffer sb = new StringBuffer();
                    while (peerListSize > 0) {
                        peerListSize--;
                        b = is.read();
                        if (-1 == b) {
                            break;
                        }
                        sb.append(b);
                        sb.append('.');
                        b = is.read();
                        if (-1 == b) {
                            break;
                        }
                        sb.append(b);
                        sb.append('.');
                        b = is.read();
                        if (-1 == b) {
                            break;
                        }
                        sb.append(b);
                        sb.append('.');
                        b = is.read();
                        if (-1 == b) {
                            break;
                        }
                        sb.append(b);
                        b = is.read();
                        if (-1 == b) {
                            break;
                        }
                        int port = b * 256;
                        b = is.read();
                        if (-1 == b) {
                            break;
                        }
                        port |= b;
                        peerList.add(new Peer(sb.toString(), port));
                        sb.setLength(0);
                    }
                    if (current instanceof HashMap) {
                        ((HashMap) current).put(name, peerList);
                    } else {
                        ((List) current).add(peerList);
                    }
                    name = value = null;
                } else if ("crypto_flags".equals(name)) {
                    byte[] cryptoFlags = new byte[size];
                    for (int i = 0; i < size; i++) {
                        b = is.read();
                        if (-1 == b) {
                            break;
                        }
                        cryptoFlags[i] = (byte) b;
                    }
                    ((HashMap) current).put(name, cryptoFlags);
                    name = value = null;
                } else {
                    keyword = "";
                    for (int i = 0; i < size; i++) {
                        b = is.read();
                        keyword += (char) b;
                        if (recording) {
                            baos.write(b);
                        }
                    }
                    if (current instanceof HashMap) {
                        if (null == name) {
                            name = keyword;
                        } else {
                            value = keyword;
                            ((HashMap) current).put(name, value);
                            name = value = null;
                        }
                    } else {
                        value = keyword;
                        ((List) current).add(value);
                        name = value = null;
                    }
                }
                keyword = "";
            } else if ('d' == c) {
                // start of dictionary
                stack.push(current);
                HashMap map = new HashMap();
                if (current instanceof HashMap) {
                    ((HashMap) current).put(null != name ? name : ""
                        .equals(keyword) ? "root" : keyword, map);
                } else {
                    ((List) current).add(map);
                }
                current = map;
                keyword = "";
                // check if it's info dictionary
                if ("info".equals(name)) {
                    recording = true;
                    baos.write('d');
                }
                name = value = null;
            } else if ('l' == c) {
                // start of list
                stack.push(current);
                List list = new ArrayList();
                if (current instanceof HashMap) {
                    ((HashMap) current).put(null != name ? name : ""
                        .equals(keyword) ? "list" : keyword, list);
                } else {
                    ((List) current).add(list);
                }
                current = list;
                keyword = "";
                name = value = null;
            } else if ('i' == c) {
                // start of integer
                state = STATE_INTEGER;
            } else if ('e' == c) {
                // end of ... ?
                if (STATE_INTEGER == state) {
                    // end of integer
                    if (current instanceof HashMap) {
                        if (null == name) {
                            name = keyword;
                        } else {
                            value = keyword;
                            ((HashMap) current).put(name, value);
                        }
                    } else {
                        value = keyword;
                        ((List) current).add(value);
                    }
                    if ("length".equals(name)) {
                        try {
                            length += Long.parseLong(value);
                        } catch (NumberFormatException ignored) {
                        }
                    }
                    keyword = "";
                    name = value = null;
                    state = STATE_UNKNOWN;
                } else {
                    // check if info
                    if (current
                        .equals(((HashMap) root.get("root")).get("info"))) {
                        recording = false;
                    }
                    current = stack.pop();
                }
            } else {
                keyword += c;
            }
        }
        // get root map
        HashMap map = (HashMap) root.get("root");
        // get torrent info map
        HashMap info = (HashMap) map.get("info");
        if (null != info) {
            // create length entry if it doesn't exist
            if (null == info.get("length")) {
                info.put("length", String.valueOf(length));
            }
            // create torrent info hash
            map.put("info hash", SHA1Util.getSHA1(baos.toByteArray()));
        } else {
            // check if peers exist (tracker response)
            List<Peer> peerList = (List<Peer>) map.get("peers");
            // check if crypto_flags exists
            byte[] cryptoFlags = (byte[]) map.get("crypto_flags");
            // create crypto_flags if it doesn't exist or it does but size does
            // not match peers size
            if (null != peerList
                && (null == cryptoFlags || cryptoFlags.length != peerList
                    .size())) {
                cryptoFlags = new byte[peerList.size()];
                // assume all peers support crypto
                for (int i = 0; i < cryptoFlags.length; i++) {
                    cryptoFlags[i] = 1;
                }
                map.put("crypto_flags", cryptoFlags);
            }
        }
        is.close();
        return (HashMap) root.get("root");
    }

}

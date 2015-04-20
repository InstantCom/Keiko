package net.instantcom.util.bcoder;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.EmptyStackException;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.util.TreeMap;

/**
 * Mock b-decoder based on Keiko's real decoder. Some stuff had to be discarded as they would
 * require bunch of Keiko's packages included but we don't really need them, we just want to see if
 * stream can be decoded or not.
 */
public class BStreamDecoder {

    public BStreamDecoder() {
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> decode(ByteBuffer buf) throws BDecodingException {
        final int STATE_UNKNOWN = 0;
        final int STATE_INTEGER = 1;
        int state = STATE_UNKNOWN;
        long length = 0;
        Map<String, Object> root = new TreeMap<String, Object>();
        Object current = root;
        Stack<Object> stack = new Stack<Object>();
        String name = "root";
        String value = "";
        String keyword = "";
        boolean recording = false;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            while (buf.hasRemaining()) {
                int b = buf.get();
                if (b < 0) {
                    b += 0x100;
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
                            b = buf.get();
                            piecesSHA1[i] = (byte) b;
                            if (recording) {
                                baos.write(b);
                            }
                        }
                        ((Map<String, Object>) current).put(name, piecesSHA1);
                        name = value = null;
                    } else if ("peers".equals(name) || "added".equals(name)
                        || "dropped".equals(name)) {
                        byte[] array = new byte[size];
                        buf.get(array);
                        if (current instanceof Map) {
                            ((Map<String, Object>) current).put(name, array);
                        } else {
                            ((List<Object>) current).add(array);
                        }
                        name = value = null;
                    } else if ("crypto_flags".equals(name) || "added.f".equals(name)) {
                        byte[] flags = new byte[size];
                        buf.get(flags);
                        ((Map<String, Object>) current).put(name, flags);
                        name = value = null;
                    } else {
                        byte[] tmp = new byte[size];
                        buf.get(tmp);
                        keyword = new String(tmp, "UTF-8");
                        if (recording) {
                            baos.write(tmp);
                        }
                        if (current instanceof Map) {
                            if (null == name) {
                                name = keyword;
                            } else {
                                value = keyword;
                                ((Map<String, Object>) current).put(name, value);
                                name = value = null;
                            }
                        } else {
                            value = keyword;
                            ((List<Object>) current).add(value);
                            name = value = null;
                        }
                    }
                    keyword = "";
                } else if ('d' == c) {
                    // start of dictionary
                    stack.push(current);
                    Map<String, Object> map = new TreeMap<String, Object>();
                    if (current instanceof Map) {
                        ((Map<String, Object>) current).put(null != name ? name : ""
                            .equals(keyword) ? "root" : keyword, map);
                    } else {
                        ((List<Object>) current).add(map);
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
                    List<Object> list = new ArrayList<Object>();
                    if (current instanceof Map) {
                        ((Map<String, Object>) current).put(null != name ? name : ""
                            .equals(keyword) ? "list" : keyword, list);
                    } else {
                        ((List<Object>) current).add(list);
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
                        if (current instanceof Map) {
                            if (null == name) {
                                name = keyword;
                            } else {
                                value = keyword;
                                ((Map<String, Object>) current).put(name, value);
                            }
                        } else {
                            value = keyword;
                            ((List<Object>) current).add(value);
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
                        if (current.equals(((Map<String, Object>) root.get("root")).get("info"))) {
                            recording = false;
                        }
                        try {
                            current = stack.pop();
                        } catch (EmptyStackException e) {
                            // some clients incorrectly encode empty peers as d...5:peers0:ee
                            // first 'e' closes dictionary, second 'e' is illegal
                            // we stop parsing as stack is empty
                            break;
                        }
                    }
                } else {
                    keyword += c;
                }
            }
            if (!stack.isEmpty()) {
                throw new BDecodingException("stack not empty");
            }
        } catch (Exception e) {
            throw new BDecodingException(e);
        }
        return (Map<String, Object>) root.get("root");
    }

}

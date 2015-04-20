package net.instantcom.util;

import java.io.UnsupportedEncodingException;

/**
 * Simple matcher for byte arrays. Several times faster than regular expressions.
 * 
 * @author disco
 */
public class BinaryMatcher {

    /**
     * Creates new BinaryMatcher.
     * 
     * @param keywords
     *            list of keywords
     */
    public BinaryMatcher(String[] keywords) {
        this.keywords = keywords;
    }

    /**
     * Checks if specified array matches this matcher's keywords.
     * 
     * @param haystack
     *            array to check
     * @param numChars
     *            search depth limit
     * @return true if match was found, false otherwise
     */
    public boolean matches(byte[] haystack, int numChars) {
        numChars = Math.min(numChars, haystack.length);
        int wordIndex = 0;
        int letterIndex = 0;
        int pos = -1;
        while (numChars > 0) {
            ++pos;
            --numChars;
            String word = keywords[wordIndex];
            if (haystack[pos] == word.charAt(letterIndex)) {
                ++letterIndex;
                if (letterIndex >= word.length()) {
                    letterIndex = 0;
                    ++wordIndex;
                    if (wordIndex >= keywords.length) {
                        return true;
                    }
                }
            } else {
                letterIndex = 0;
            }
        }
        return false;
    }

    /**
     * Performs a search for needle in haystack.
     * <p>
     * afterIndexOf methods return index of first char after the needle, not the index of first char
     * of needle! For instance, this is the usual usage of indexOf method:
     * 
     * <pre>
     * int pos = indexOf(url, &quot;info_hash=&quot;.toCharArray());
     * if (pos &gt;= 0) {
     *     pos += &quot;info_hash=&quot;.length();
     *     // do something with pos
     * }
     * </pre>
     * 
     * afterIndexOf methods make this easier:
     * 
     * <pre>
     * int pos = afterIndexOf(haystack, &quot;info_hash=&quot;.toCharArray());
     * if (pos &gt;= 0) {
     *     // pos is already incremented by &quot;info_hash=&quot;.length() and points to first char after it
     * }
     * </pre>
     * 
     * @param haystack
     *            haystack
     * @param needle
     *            needle
     * @param numChars
     *            search depth limit
     * @return index of first char after the needle or -1 if needle was not found
     */
    public static int afterIndexOf(byte[] haystack, char[] needle, int numChars) {
        return afterIndexOf(haystack, needle, 0, numChars);
    }

    /**
     * Performs a search for needle in haystack starting from specified position/index.
     * <p>
     * afterIndexOf methods return index of first char after the needle, not the index of first char
     * of needle! For instance, this is the usual usage of indexOf method:
     * 
     * <pre>
     * int pos = indexOf(url, &quot;info_hash=&quot;.toCharArray());
     * if (pos &gt;= 0) {
     *     pos += &quot;info_hash=&quot;.length();
     *     // do something with pos
     * }
     * </pre>
     * 
     * afterIndexOf methods make this easier:
     * 
     * <pre>
     * int pos = afterIndexOf(haystack, &quot;info_hash=&quot;.toCharArray());
     * if (pos &gt;= 0) {
     *     // pos is already incremented by &quot;info_hash=&quot;.length() and points to first char after it
     * }
     * </pre>
     * 
     * @param haystack
     *            haystack
     * @param needle
     *            needle
     * @param startPos
     *            starting search position/index
     * @param numChars
     *            search depth limit
     * @return index of first char after the needle or -1 if needle was not found
     */
    public static int afterIndexOf(byte[] haystack, char[] needle, int startPos, int numChars) {
        numChars = Math.min(numChars, haystack.length - startPos);
        int letterIndex = 0;
        int pos = startPos - 1;
        while (numChars > 0) {
            ++pos;
            --numChars;
            if (haystack[pos] == needle[letterIndex]) {
                ++letterIndex;
                if (letterIndex >= needle.length) {
                    letterIndex = 0;
                    return 1 + pos;
                }
            } else {
                letterIndex = 0;
            }
        }
        return -1;
    }

    /**
     * Performs a search for single byte inside the byte array.
     * 
     * @param array
     *            byte array
     * @param offset
     *            starting position/index
     * @param limit
     *            search depth limit
     * @param key
     *            byte to search for
     * @return index of specified byte or -1 if specified byte was not found
     */
    public static int indexOf(byte[] array, int offset, int limit, byte key) {
        int result = -1;
        int realLimit = Math.min(array.length, offset + limit);
        for (int i = offset; i < realLimit; i++) {
            if (key == array[i]) {
                result = i;
                break;
            }
        }
        return result;
    }

    /**
     * Performs a search for US-ASCII string inside the byte array.
     * 
     * @param haystack
     *            byte array
     * @param s
     *            string to search for
     * @return index of first char of the specified string inside the array or -1 if string was not
     *         found
     * @throws UnsupportedEncodingException
     *             if system doesn't support US-ASCII encoding
     */
    public static int indexOf(byte[] haystack, String s) throws UnsupportedEncodingException {
        byte[] needle = s.getBytes("US-ASCII");
        for (int i = 0; i < haystack.length - needle.length; i++) {
            int j;
            for (j = 0; j < needle.length && haystack[i + j] == needle[j]; j++) {
            }
            if (j >= needle.length) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Performs a reverse (back to front) search for US-ASCII string inside the byte array.
     * 
     * @param haystack
     *            byte array
     * @param s
     *            string to search for
     * @return index of first char of the specified string inside the array or -1 if string was not
     *         found
     * @throws UnsupportedEncodingException
     *             if system doesn't support US-ASCII encoding
     */
    public static int lastIndexOf(byte[] haystack, String s) throws UnsupportedEncodingException {
        if (s.length() < 1) {
            return -1;
        }
        byte[] needle = s.getBytes("US-ASCII");
        for (int i = haystack.length - needle.length; i >= 0; i--) {
            int j;
            for (j = 0; j < needle.length && haystack[i + j] == needle[j]; j++) {
            }
            if (j >= needle.length) {
                return i;
            }
        }
        return -1;
    }

    private String[] keywords;

}

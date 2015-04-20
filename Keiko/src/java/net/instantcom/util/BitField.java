package net.instantcom.util;

import java.io.Serializable;

public class BitField implements Serializable {

    private static final long serialVersionUID = 20080119L;
    private static final int[] BITS = {
        0x80, 0x40, 0x20, 0x10, 0x08, 0x04, 0x02, 0x01
    };
    private static final int[] LAST_BYTE_FIX = {
        0xff, 0x80, 0xc0, 0xe0, 0xf0, 0xf8, 0xfc, 0xfe
    };

    public BitField(int size) {
        this.size = size;
        int arraySize = size / 8;
        if (0 != (size & 7)) {
            ++arraySize;
        }
        data = new byte[arraySize];
    }

    // make sure all unused bits in last byte are cleared
    protected void fixLastByte() {
        data[data.length - 1] &= LAST_BYTE_FIX[size & 7];
    }

    public synchronized void clearAll() {
        for (int i = 0; i < data.length; i++) {
            data[i] = 0x00;
        }
    }

    public synchronized void setAll() {
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) 0xff;
        }
        fixLastByte();
    }

    public boolean get(int index) {
        return 0 != (data[index / 8] & BITS[index & 7]);
    }

    public synchronized void clear(int index) {
        data[index / 8] &= ~BITS[index & 7];
    }

    public synchronized void set(int index) {
        data[index / 8] |= BITS[index & 7];
    }

    public int getSize() {
        return size;
    }

    public byte[] getData() {
        return data;
    }

    public void setData(byte[] data) {
        this.data = data;
        fixLastByte();
    }

    /**
     * Checks if all bits are set.
     * 
     * @return true if all bits are set
     */
    public boolean allBitsSet() {
        return size == getNumOnes();
    }

    /**
     * Checks if all bits are cleared.
     * 
     * @return true if all bits are cleared
     */
    public boolean allBitsCleared() {
        return size == getNumZeroes();
    }

    /**
     * Returns new BitField which is bitwise AND of specified BitField and this
     * one. Both BitFields must be of the same size.
     * 
     * @param other
     *            other BitField
     * @return bitwise ANDed result
     */
    public BitField and(BitField other) {
        if (size != other.getSize()) {
            throw new IllegalArgumentException("sizes do not match");
        }
        BitField result = new BitField(size);
        byte[] resultData = result.getData();
        byte[] otherData = other.getData();
        for (int i = 0; i < data.length; i++) {
            resultData[i] = (byte) (data[i] & otherData[i]);
        }
        result.fixLastByte();
        return result;
    }

    /**
     * Returns new BitField which is bitwise NOT of this one.
     * 
     * @return bitwise NOTed result
     */
    public BitField not() {
        BitField result = new BitField(size);
        byte[] resultData = result.getData();
        for (int i = 0; i < data.length; i++) {
            resultData[i] = (byte) ~data[i];
        }
        result.fixLastByte();
        return result;
    }

    /**
     * Gets number of set bits (1).
     * 
     * @return number of set bits
     */
    public int getNumOnes() {
        int count = 0;
        for (int element : data) {
            if (element < 0) {
                element += 0x100;
            }
            count += Integer.bitCount(element);
        }
        return count;
    }

    /**
     * Gets number of cleared bits (0).
     * 
     * @return number of cleared bits
     */
    public int getNumZeroes() {
        return size - getNumOnes();
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        StringBuffer sb = new StringBuffer();
        for (byte element : data) {
            String s = "0000000" + Integer.toBinaryString(element);
            sb.append(s.substring(s.length() - 8));
        }
        return sb.substring(0, size);
    }

    private int size;
    private byte[] data;

}

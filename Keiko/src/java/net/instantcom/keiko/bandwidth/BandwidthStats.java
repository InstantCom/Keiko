package net.instantcom.keiko.bandwidth;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

public class BandwidthStats implements Serializable {

    private static final long serialVersionUID = 20080128L;

    // private static final Log log = LogFactory.getLog(BandwidthStats.class);

    public BandwidthStats() {
        lastCheckTime = System.currentTimeMillis();
    }

    public synchronized void update(long download, long upload) {
        totalDownload += download;
        totalUpload += upload;
    }

    /**
     * @return the totalDownload
     */
    public long getTotalDownload() {
        return totalDownload;
    }

    /**
     * @return the totalUpload
     */
    public long getTotalUpload() {
        return totalUpload;
    }

    /**
     * Gets ratio.
     * 
     * @return ratio
     */
    public double getRatio() {
        if (0 == totalDownload) {
            return 1.0;
        }
        return ((double) totalUpload) / ((double) totalDownload);
    }

    private void averageCheck() {
        long now = System.currentTimeMillis();
        long diff = now - lastCheckTime;
        if (diff >= 1000L) {
            lastCheckTime = now;
            avgDown = (1000L * (totalDownload - lastDown)) / diff;
            lastDown = totalDownload;
            avgUp = (1000L * (totalUpload - lastUp)) / diff;
            lastUp = totalUpload;
        }
    }

    /**
     * Gets average download rate since last check.
     * 
     * @return average download
     */
    public long getAverageDownload() {
        averageCheck();
        return avgDown;
    }

    /**
     * Gets average upload rate since last check.
     * 
     * @return average upload
     */
    public long getAverageUpload() {
        averageCheck();
        return avgUp;
    }

    private void writeObject(ObjectOutputStream out) throws IOException {
        out.writeLong(totalDownload);
        out.writeLong(totalUpload);
    }

    private void readObject(ObjectInputStream in) throws IOException,
        ClassNotFoundException {
        totalDownload = in.readLong();
        totalUpload = in.readLong();
        lastCheckTime = System.currentTimeMillis();
        lastDown = totalDownload;
        lastUp = totalUpload;
        avgDown = avgUp = 0;
    }

    private long totalDownload;
    private long totalUpload;

    // non-serialized objects
    private long lastCheckTime;
    private long lastDown;
    private long lastUp;
    private long avgDown;
    private long avgUp;

}

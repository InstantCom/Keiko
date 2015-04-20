package net.instantcom.keiko.bittorrent.tracker;

import java.net.URL;
import java.util.TimerTask;

public class Tracker {

    public Tracker(URL url) {
        this.url = url;
    }

    protected URL getURL() {
        return url;
    }

    protected int getInterval() {
        return interval;
    }

    protected void setInterval(int interval) {
        if (interval < 60) {
            interval = 60;
        }
        this.interval = interval;
    }

    protected String getTrackerId() {
        return trackerId;
    }

    protected void setTrackerId(String trackerId) {
        this.trackerId = trackerId;
    }

    protected void cancelTimerTask() {
        timerTask.cancel();
    }

    protected void setTimerTask(TimerTask timerTask) {
        this.timerTask = timerTask;
    }

    private URL url;
    private int interval = 3600000; // 1 hour
    private String trackerId;
    private TimerTask timerTask;

}

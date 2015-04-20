package net.instantcom.keiko.bittorrent.tracker;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.TimerTask;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import net.instantcom.keiko.Server;
import net.instantcom.keiko.bittorrent.MetaInfo;
import net.instantcom.keiko.bittorrent.protocol.PeerConnection;
import net.instantcom.keiko.bittorrent.protocol.Torrent;
import net.instantcom.keiko.config.Configuration;
import net.instantcom.keiko.peer.Peer;
import net.instantcom.util.BDecoder;
import net.instantcom.util.BitField;

public class TrackerUpdater {

    private static final Log log = LogFactory.getLog(TrackerUpdater.class);

    @SuppressWarnings("unchecked")
    public TrackerUpdater(Torrent torrent) {
        this.torrent = torrent;
        key = String.valueOf(System.currentTimeMillis());
        MetaInfo metaInfo = torrent.getMetaInfo();
        String announce = metaInfo.getAnnounce();
        if (null != announce) {
            try {
                trackers.add(new Tracker(new URL(announce)));
            } catch (MalformedURLException e) {
                log.warn("torrent '" + metaInfo.getName()
                    + "' has invalid tracker URL: " + announce);
            }
        }
        List<String> list = metaInfo.getAnnounceList();
        if (null != list && !list.isEmpty()) {
            // some torrents have strings in list, some have list in list, some
            // have list in list for each string
            for (Object obj : list) {
                if (obj instanceof String) {
                    try {
                        String s = (String) obj;
                        if (!s.equals(announce)) {
                            trackers.add(new Tracker(new URL(s)));
                        }
                    } catch (MalformedURLException e) {
                        log.warn("torrent '" + metaInfo.getName()
                            + "' has invalid tracker URL: " + (String) obj);
                    }
                } else if (obj instanceof List) {
                    List<String> innerList = (List<String>) obj;
                    for (String s : innerList) {
                        try {
                            if (!s.equals(announce)) {
                                trackers.add(new Tracker(new URL(s)));
                            }
                        } catch (MalformedURLException e) {
                            log.warn("torrent '" + metaInfo.getName()
                                + "' has invalid tracker URL: " + s);
                        }
                    }
                }
            }
        }
    }

    public void started() {
        for (final Tracker tracker : trackers) {
            update(tracker, "started");
            TimerTask task = new TimerTask() {

                @Override
                public void run() {
                    update(tracker, null);
                }

            };
            tracker.setTimerTask(task);
            long time = 1000L * tracker.getInterval();
            Server.scheduler.schedule(task, time, time);
        }
    }

    public void completed() {
        for (Tracker tracker : trackers) {
            update(tracker, "completed");
        }
    }

    public void stopped() {
        // cancel timers fast
        for (Tracker tracker : trackers) {
            tracker.cancelTimerTask();
        }
        // no rush on http
        for (Tracker tracker : trackers) {
            update(tracker, "stopped");
        }
    }

    @SuppressWarnings("unchecked")
    private void update(Tracker tracker, String event) {
        String trackerPrefix = "[" + tracker.getURL() + "] ";
        if (log.isDebugEnabled()) {
            log
                .debug(trackerPrefix
                    + "updating tracker"
                    + (null == event ? "" : (" with event " + event
                        .toUpperCase())));
        }
        MetaInfo info = torrent.getMetaInfo();
        StringBuffer sb = new StringBuffer();
        String s = tracker.getURL().toString();
        sb.append(s);

        // info hash
        if (s.indexOf('?') >= 0) {
            sb.append('&');
        } else {
            sb.append('?');
        }
        sb.append("info_hash=");
        sb.append(escape(info.getInfoHash()));

        // peer_id
        sb.append("&peer_id=");
        sb.append(escape(PeerConnection.myPeerId));

        // port
        sb.append("&port=");
        sb.append(Configuration.getInstance().getInt("torrent.port",
            Configuration.DEFAULT_PORT));

        // uploaded
        sb.append("&uploaded=");
        sb.append(torrent.getBytesUploadedThisSession());

        // downloaded
        sb.append("&downloaded=");
        sb.append(torrent.getBytesDownloadedThisSession());

        // left
        sb.append("&left=");
        BitField have = torrent.getHavePieces();
        if (have.allBitsSet()) {
            sb.append("0");
        } else {
            long need = have.getNumZeroes();
            boolean needLastPiece = have.get(have.getSize() - 1);
            if (needLastPiece) {
                need--;
                if (need < 0) {
                    need = 0;
                }
            }
            need *= info.getPieceLength();
            if (needLastPiece) {
                need += info.getLastPieceLength();
            }
            if (need < 0) {
                need = 0;
            }
            sb.append(need);
        }

        // compact
        sb.append("&compact=1");

        // no_peer_id
        sb.append("&no_peer_id=1");

        // event
        if (null != event) {
            sb.append("&event=");
            sb.append(event);
        }

        // key
        sb.append("&key=");
        sb.append(key);

        // tracker id
        if (null != tracker.getTrackerId()) {
            sb.append("&tracker_id=");
            sb.append(tracker.getTrackerId());
        }

        // numwant
        if (torrent.getHavePieces().allBitsSet()) {
            // don't need any peers when seeding, they can connect to us
            sb.append("&numwant=0");
        }

        // support crypto
        // always enabled as we always accept encrypted inbound connections
        sb.append("&supportcrypto=1");

        // require crypto
        boolean requireCrypto =
            "forced".equalsIgnoreCase(Configuration.getInstance().getString(
                "torrent.pe.outbound", "enabled"))
                && !Configuration.getInstance().getBoolean(
                    "torrent.pe.allow.inbound.legacy", true);
        sb.append("&requirecrypto=");
        sb.append(requireCrypto ? '1' : '0');

        try {
            URL trackerUrl = new URL(sb.toString());
            if (log.isDebugEnabled()) {
                log.debug("updating tracker: " + trackerUrl.toString());
            }
            HashMap map = BDecoder.bdecode(trackerUrl.openStream());

            // failure reason
            String failureReason = (String) map.get("failure reason");
            if (null != failureReason) {
                log.warn(trackerPrefix + failureReason);
            } else {
                // warning message
                String warningMessage = (String) map.get("warning message");
                if (null != warningMessage) {
                    log.warn("tracker warning: " + warningMessage);
                }
                // interval
                String interval = (String) map.get("interval");
                tracker.setInterval(null != interval ? Integer
                    .parseInt(interval) : 60);
                // tracker id
                String trackerId = (String) map.get("tracker id");
                if (null != trackerId) {
                    tracker.setTrackerId(trackerId);
                }
                // peers
                List<Peer> peers = (List<Peer>) map.get("peers");
                if (null != peers) {
                    torrent.addTrackerSuppliedPeers(peers, (byte[]) map
                        .get("crypto_flags"));
                }
            }
        } catch (Exception e) {
            if (log.isDebugEnabled()) {
                log.debug(trackerPrefix + "tracker update error "
                    + e.toString());
            }
        }
    }

    // escapes any byte not in the set 0-9, a-z, A-Z, '.', '-', '_' and '~'
    private String escape(byte[] array) {
        StringBuffer sb = new StringBuffer();
        for (int b : array) {
            if (b < 0) {
                b += 0x100;
            }
            char c = (char) b;
            // '.', '-', '_' and '~',
            if ((c >= '0' && c <= '9') || (c >= 'A' && c <= 'Z')
                || (c >= 'a' && c <= 'z') || c == '.' || c == '-' || c == '_'
                || c == '~') {
                sb.append(c);
            } else {
                sb.append('%');
                if (b < 0x10) {
                    sb.append('0');
                }
                sb.append(Integer.toHexString(b));
            }
        }
        return sb.toString();
    }

    private Torrent torrent;
    private List<Tracker> trackers = new ArrayList<Tracker>();
    // An additional identification that is not shared with any users. It is
    // intended to allow a client to prove their identity should their IP
    // address change
    private String key;

}

package net.instantcom.keikosniffer.task;

import java.util.List;

import jpcap.JpcapCaptor;
import net.instantcom.keikosniffer.KeikoSniffer;
import net.instantcom.keikosniffer.Stats;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class ScheduledStatsLoggerTask implements Runnable {

    private static final Log log = LogFactory.getLog(ScheduledStatsLoggerTask.class);

    public ScheduledStatsLoggerTask(JpcapCaptor captor, List<Stats> stats) {
        this.captor = captor;
        this.stats = stats;
    }

    @Override
    public void run() {
        report();
    }

    public void report() {
        log.info("=========================================================");
        Stats st = new Stats();
        for (Stats s : stats) {
            st.merge(s);
        }
        if (!KeikoSniffer.USE_DUMP_CAP_FILE) {
            captor.updateStat();
            long received = captor.received_packets;
            if (firstReceivedCount < 0) {
                firstReceivedCount = received;
            }
            long dropped = captor.dropped_packets;
            if (firstDroppedCount < 0) {
                firstDroppedCount = dropped;
            }
            log.info("jpcap packet stats: received: " + (received - firstReceivedCount)
                + "  dropped: " + (dropped - firstDroppedCount));
        }
        log.info("requests:");
        log.info("  found:  " + st.getRequestsFound());
        log.info("  sent:   " + st.getRequestsSent());
        log.info("  missed: " + st.getRequestsMissed());
        log.info("responses:");
        log.info("  found:  " + st.getResponsesFound());
        log.info("  failed: " + st.getResponseFailuresFound());
        log.info("  sent:   " + st.getResponsesSent());
        log.info("  missed: " + st.getResponsesMissed());
        log.info("torrents:");
        log.info("  found:    " + st.getTorrentsFound());
        log.info("  sent:     " + st.getTorrentsSent());
        // log.info(" not sent: " + st.getTorrentsNotSent());
        log.info("  missed:   " + st.getTorrentsMissed());
        // log.info("all streams:");
        // log.info(" created: " + st.getStreamsCreated());
        // log.info(" completed: " + st.getStreamsCompleted());
        // log.info("normal streams:");
        // log.info(" created: " + st.getNormalStreamsCreated());
        // log.info(" completed: " + st.getNormalStreamsCompleted());
        // log.info("chunked streams:");
        // log.info(" created: " + st.getChunkedStreamsCreated());
        // log.info(" completed: " + st.getChunkedStreamsCompleted());
        // log.info("gzipped streams:");
        // log.info(" created: " + st.getGzippedStreamsCreated());
        // log.info(" completed: " + st.getGzippedStreamsCompleted());
        log.info("=========================================================");
    }

    // captor counts packets before our startup which takes some time so it's normal to have
    // some packets by the time we're up and running. that's why we sync counters on first stats
    // update
    private long firstDroppedCount = -1;
    private long firstReceivedCount = -1;
    private JpcapCaptor captor;
    private List<Stats> stats;

}

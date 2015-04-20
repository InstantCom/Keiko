package net.instantcom.keikosniffer.task;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

import jpcap.JpcapCaptor;
import net.instantcom.keikosniffer.config.Configuration;
import net.instantcom.keikosniffer.scraper.TorrentKingScraper;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class ScheduledFilterReloaderTask implements Runnable {

    private static final Log log = LogFactory.getLog(ScheduledFilterReloaderTask.class);

    public ScheduledFilterReloaderTask(JpcapCaptor captor) {
        this.captor = captor;
    }

    @Override
    public void run() {
        log.info("started");
        log.info("reloading configuration");
        Configuration.getInstance().reload();
        try {
            prepareAndApplyFilter(captor);
        } catch (IOException e) {
            log.error("filter compilation failed", e);
        }
        log.info("finished");
    }

    // private static String listToCommaSeparated(List<String> list) {
    // StringBuffer sb = new StringBuffer();
    // int count = 0;
    // for (String s : list) {
    // if (count > 0) {
    // sb.append(", ");
    // }
    // sb.append(s);
    // ++count;
    // }
    // return sb.toString();
    // }

    private static void addToList(List<String> list, String commaSeparatedList) {
        if (null == commaSeparatedList) {
            return;
        }
        for (String s : commaSeparatedList.split(",")) {
            String trimmed = s.trim().toLowerCase();
            if (trimmed.length() > 0 && !list.contains(trimmed)) {
                list.add(trimmed);
            }
        }
    }

    public static void prepareAndApplyFilter(JpcapCaptor captor) throws IOException {
        log.info("preparing filter");
        Configuration config = Configuration.getInstance();
        String defaultFilter = config.getString("filter", "tcp").trim();
        List<String> trackers = new ArrayList<String>();
        addToList(trackers, config.getString("trackers", "").trim());
        if (config.getBoolean("scrape", false)) {
            TorrentKingScraper scraper = new TorrentKingScraper();
            List<String> list = scraper.scrape();
            if (null == list || list.isEmpty()) {
                log.warn("no trackers scraped");
            } else {
                trackers.addAll(list);
            }
        }
        if (0 == trackers.size()) {
            log.warn("no trackers defined, using default filter: " + defaultFilter);
            log.info("compiling filter: " + defaultFilter);
            captor.setFilter(defaultFilter, true);
            log.info("filter successfully compiled and applied");
        } else {
            log.info("resolving URLs, this may take a while...");
            List<String> resolvedHosts = new ArrayList<String>();
            int current = 0;
            int total = trackers.size();
            int lastPercentage = 0;
            for (String tracker : trackers) {
                ++current;
                int percentage = (100 * current) / total;
                if (percentage != lastPercentage) {
                    log.info("resolving progress: " + percentage + "%");
                }
                lastPercentage = percentage;
                String host = tracker.trim();
                try {
                    InetAddress[] addresses = InetAddress.getAllByName(host);
                    for (InetAddress addr : addresses) {
                        String ip = addr.getHostAddress();
                        if (!resolvedHosts.contains(ip)) {
                            resolvedHosts.add(ip);
                        }
                    }
                } catch (UnknownHostException e) {
                    log.warn("can't resolve host: " + host);
                }
            }
            log.info("done resolving");
            StringBuffer sb = new StringBuffer();
            if (resolvedHosts.isEmpty()) {
                sb.append(defaultFilter);
            } else {
                if (defaultFilter.contains(" and ") || defaultFilter.contains(" or ")) {
                    sb.append('(');
                    sb.append(defaultFilter);
                    sb.append(") and (");
                } else {
                    sb.append(defaultFilter);
                    sb.append(" and (");
                }
                int count = 0;
                for (String host : resolvedHosts) {
                    if (0 == count) {
                        sb.append("host ");
                    } else {
                        sb.append(" or host ");
                    }
                    sb.append(host);
                    ++count;
                }
                sb.append(")");
            }
            String filter = sb.toString();
            log.info("compiling filter: " + filter);
            captor.setFilter(filter, true);
            log.info("filter successfully compiled and applied");
        }
    }

    private JpcapCaptor captor;

}

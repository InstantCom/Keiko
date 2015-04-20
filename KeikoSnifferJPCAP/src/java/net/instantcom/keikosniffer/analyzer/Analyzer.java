package net.instantcom.keikosniffer.analyzer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jpcap.JpcapCaptor;
import jpcap.PacketReceiver;
import jpcap.packet.Packet;
import jpcap.packet.TCPPacket;
import net.instantcom.util.BinaryMatcher;

public class Analyzer implements PacketReceiver {

    private static final int NUM_TRACKERS = 500; // dropped packets observed with 600

    // private static final String HOST = "Host:";

    private static final BinaryMatcher requestMatcher = new BinaryMatcher(new String[] {
        "GET", "/announce", "info_hash"
    });

    private static final BinaryMatcher torrentMatcher = new BinaryMatcher(new String[] {
        "d8:announce"
    });
    private static final BinaryMatcher torrentMatcherContentType = new BinaryMatcher(new String[] {
        "application/x-bittorrent"
    });

    private static final Comparator<Host> COMPARE_BY_REF_COUNT = new Comparator<Host>() {

        @Override
        public int compare(Host o1, Host o2) {
            return Integer.valueOf(o2.getReferenceCount()).compareTo(
                Integer.valueOf(o1.getReferenceCount()));
        }

    };

    public Analyzer() {
    }

    public void analyze(String fileName) throws IOException {
        // final String FILENAME = "dump.cap";
        // final String FILENAME = "announces.cap";
        // final String FILENAME = "announces-and-torrents.cap";
        JpcapCaptor captor = JpcapCaptor.openFile(fileName);
        captor.loopPacket(-1, this);
    }

    @Override
    public void receivePacket(Packet packet) {
        if (packet instanceof TCPPacket) {
            TCPPacket p = (TCPPacket) packet;
            try {
                byte[] data = p.data;
                if (null != data && data.length > 32) {
                    if ('G' == data[0] && 'E' == data[1] && 'T' == data[2]
                        && requestMatcher.matches(data, data.length)) { // announce request
                        // String host = getHost(data);
                        String host = getDestHost(p);
                        if (null != host) {
                            // log.info("found announce to host: " + host);
                            Host tracker = trackerSites.get(host);
                            if (null == tracker) {
                                tracker = new Host(host);
                                trackerSites.put(host, tracker);
                            }
                            tracker.incReferenceCount();
                        }
                    } else if (torrentMatcher.matches(data, data.length)
                        || torrentMatcherContentType.matches(data, data.length)) { // torrent
                        // String host = getHost(data);
                        // if (null == host) {
                        // // no host, use ip
                        // host = p.src_ip.getHostAddress();
                        // }
                        String host = getSrcHost(p);
                        // log.info("found torrent from host: " + host);
                        Host tracker = torrentSites.get(host);
                        if (null == tracker) {
                            tracker = new Host(host);
                            torrentSites.put(host, tracker);
                        }
                        tracker.incReferenceCount();
                    }
                }
            } catch (Exception e) {
                // log.error("receivePacket", e);
                e.printStackTrace();
            }
        }
    }

    // private String getHost(byte[] data) {
    // String result = null;
    // BufferedReader br =
    // new BufferedReader(new InputStreamReader(new ByteArrayInputStream(data)));
    // String line = null;
    // try {
    // while (null != (line = br.readLine())) {
    // if (0 == line.length()) {
    // break;
    // }
    // if (line.startsWith(HOST)) {
    // result = line.substring(HOST.length()).trim();
    // int pos = result.indexOf(':');
    // if (pos > 0) {
    // result = result.substring(0, pos);
    // }
    // break;
    // }
    // }
    // } catch (Exception e) {
    // log.error("getHost: line=" + line, e);
    // } finally {
    // try {
    // br.close();
    // } catch (IOException e) {
    // log.error("getHost.finally", e);
    // }
    // }
    // return result;
    // }

    private String getSrcHost(TCPPacket packet) {
        return packet.src_ip.getHostAddress();
    }

    private String getDestHost(TCPPacket packet) {
        return packet.dst_ip.getHostAddress();
    }

    // private static int countDots(String s) {
    // int count = 0;
    // while (true) {
    // int pos = s.indexOf('.');
    // if (pos < 0) {
    // break;
    // }
    // ++count;
    // s = s.substring(1 + pos);
    // }
    // return count;
    // }

    private static String createBestConfigLine(List<Host> torrentHosts, List<Host> trackerHosts) {
        List<Host> hosts = new ArrayList<Host>();
        if (torrentHosts.size() <= NUM_TRACKERS / 2) {
            hosts.addAll(torrentHosts);
        } else {
            hosts.addAll(torrentHosts.subList(0, NUM_TRACKERS / 2));
        }
        int limit = NUM_TRACKERS - hosts.size();
        if (trackerHosts.size() <= limit) {
            hosts.addAll(trackerHosts);
        } else {
            hosts.addAll(trackerHosts.subList(0, Math.min(limit, trackerHosts.size())));
        }

        StringBuffer sb = new StringBuffer();
        sb.append("trackers=");
        if (null != hosts) {
            List<String> list = new ArrayList<String>();
            int count = 0;
            for (Host tracker : hosts) {
                ++count;
                if (count > NUM_TRACKERS) {
                    break;
                }
                String host = tracker.getHost();
                if (!list.contains(host)) {
                    list.add(host);
                }
                // if (!host.startsWith("www.")) {
                // while (countDots(host) > 1) {
                // host = host.substring(1 + host.indexOf('.'));
                // if (!list.contains(host)) {
                // list.add(host);
                // }
                // }
                // }
            }
            count = 0;
            for (String host : list) {
                ++count;
                if (count > 1) {
                    sb.append(", ");
                }
                sb.append(host);
            }
        }
        return sb.toString();
    }

    public static void main(String[] args) {
        if (args.length != 1) {
            System.out.println("usage: java ... net.instantcom.keikosniffer.Analyzer <filename>");
            return;
        }
        String fileName = args[0];

        System.out.println("analyzing: " + fileName);

        Analyzer analyzer = new Analyzer();
        try {
            analyzer.analyze(fileName);

            List<Host> torrentHosts = new ArrayList<Host>();
            torrentHosts.addAll(torrentSites.values());
            Collections.sort(torrentHosts, COMPARE_BY_REF_COUNT);
            System.out.println("TORRENT sites:");
            System.out.println("#  refCnt host");
            int count = 0;
            for (Host tracker : torrentHosts) {
                ++count;
                System.out.println(count + ". " + tracker.getReferenceCount() + "  "
                    + tracker.getHost());
            }

            List<Host> trackers = new ArrayList<Host>();
            trackers.addAll(trackerSites.values());
            Collections.sort(trackers, COMPARE_BY_REF_COUNT);
            System.out.println("TRACKER sites:");
            System.out.println("#  refCnt host");
            count = 0;
            for (Host tracker : trackers) {
                ++count;
                System.out.println(count + ". " + tracker.getReferenceCount() + "  "
                    + tracker.getHost());
            }

            System.out.println("proposing the following config line for best effect...");
            System.out.println(createBestConfigLine(torrentHosts, trackers));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // private static final Map<String, Tracker> hosts = new HashMap<String, Tracker>();
    private static final Map<String, Host> trackerSites = new HashMap<String, Host>();
    private static final Map<String, Host> torrentSites = new HashMap<String, Host>();

}

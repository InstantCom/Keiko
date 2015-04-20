package net.instantcom.keikosniffer;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.management.InstanceAlreadyExistsException;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
import javax.management.MBeanServerFactory;
import javax.management.MalformedObjectNameException;
import javax.management.NotCompliantMBeanException;
import javax.management.ObjectName;
import javax.management.remote.JMXConnectorServer;
import javax.management.remote.JMXConnectorServerFactory;
import javax.management.remote.JMXServiceURL;

import jpcap.JpcapCaptor;
import jpcap.NetworkInterface;
import jpcap.PacketReceiver;
import jpcap.packet.Packet;
import jpcap.packet.TCPPacket;
import net.instantcom.keiko.ksp.SnifferProtocolThread;
import net.instantcom.keikosniffer.config.Configuration;
import net.instantcom.keikosniffer.task.ScheduledFilterReloaderTask;
import net.instantcom.keikosniffer.task.ScheduledStatsLoggerTask;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.log4j.PropertyConfigurator;
import org.tanukisoftware.wrapper.WrapperListener;
import org.tanukisoftware.wrapper.WrapperManager;

public class KeikoSniffer implements Runnable, WrapperListener, PacketReceiver {

    public static final boolean USE_DUMP_CAP_FILE = (new File("src/java")).isDirectory();
    private static final String DUMP_FILENAME = "dump.cap";
    // private static final String DUMP_FILENAME = "torrents.cap";

    private static final Log log = LogFactory.getLog(KeikoSniffer.class);
    private static final int NUM_PROCESSING_THREADS =
        1 + Runtime.getRuntime().availableProcessors();
    private static final int NUM_KSP_THREADS = 1;

    public KeikoSniffer() {
        for (int i = 0; i < NUM_PROCESSING_THREADS; i++) {
            queues.add(new ConcurrentLinkedQueue<TCPPacket>());
        }
    }

    @Override
    public void receivePacket(Packet packet) {
        // if some error occurred or EOF has been reached, break the loop
        if (null == packet || Packet.EOF == packet) {
            return;
        }
        // caplen must match len, data must be present
        if (packet.caplen != packet.len || null == packet.data || 0 == packet.data.length) {
            return;
        }
        // we're interested only in tcp packets
        if (!(packet instanceof TCPPacket)) {
            return;
        }
        TCPPacket tcpPacket = (TCPPacket) packet;
        // get queue index based on destination port
        int index =
            80 == tcpPacket.src_port ? tcpPacket.dst_port % NUM_PROCESSING_THREADS
                : tcpPacket.src_port % NUM_PROCESSING_THREADS;
        // queue packet
        queues.get(index).offer(tcpPacket);
    }

    public void run() {
        running = true;

        if (!USE_DUMP_CAP_FILE) {
            captor.loopPacket(-1, this);
        } else {
            long startTime = System.currentTimeMillis();
            try {
                while (running) {
                    JpcapCaptor captor = this.captor;
                    if (null == captor) {
                        break;
                    }
                    // read a packet from the opened file
                    Packet packet = captor.getPacket();
                    // if some error occurred or EOF has been reached, break the loop
                    if (null == packet || Packet.EOF == packet) {
                        break;
                    }
                    // caplen must match len, data must be present
                    if (packet.caplen != packet.len || null == packet.data
                        || 0 == packet.data.length) {
                        continue;
                    }
                    // we're interested only in tcp packets
                    if (!(packet instanceof TCPPacket)) {
                        log.warn("got non-tcp packet, check your filter");
                        continue;
                    }
                    TCPPacket tcpPacket = (TCPPacket) packet;
                    // log.info("got packet: " + packet);
                    // get queue index based on destination port
                    int index =
                        80 == tcpPacket.src_port ? tcpPacket.dst_port % NUM_PROCESSING_THREADS
                            : tcpPacket.src_port % NUM_PROCESSING_THREADS;
                    // queue packet
                    queues.get(index).offer(tcpPacket);
                }
            } catch (Exception e) {
                log.error("run", e);
            } finally {
                if (null != captor) {
                    captor.close();
                }
            }

            // wait until all packets in queues have been processed
            log.info("finished, waiting for queues to become empty");
            while (!areQueuesEmpty()) {
                try {
                    Thread.sleep(100L);
                } catch (InterruptedException ignored) {
                }
            }

            log.info("time: " + (System.currentTimeMillis() - startTime) + " ms");
            stop();
            System.exit(0);
        }

        running = false;
    }

    private void start() throws Exception {
        // fire it up in a second, thread needs to be responsive otherwise wrapper will terminate it
        scheduledThreadPool.schedule(new Runnable() {

            public void run() {
                try {
                    log.info(About.PRODUCT_VERSION + " starting");
                    log.info(About.COPYRIGHT);
                    log.info(About.URI);
                    log.info("available processors: " + Runtime.getRuntime().availableProcessors());

                    if (USE_DUMP_CAP_FILE) {
                        // open a file to read saved packets
                        captor = JpcapCaptor.openFile(DUMP_FILENAME);
                    } else {
                        NetworkInterface[] devices = JpcapCaptor.getDeviceList();
                        if (null == devices || 0 == devices.length) {
                            log.error("no network interface seems to be available, make sure "
                                + About.PRODUCT_NAME + " is running as root");
                            System.exit(1);
                        } else {
                            log.info("the following network interfaces are available:");
                            for (NetworkInterface ni : devices) {
                                log.info("name=" + ni.name + "  datalink_name=" + ni.datalink_name);
                            }
                            String ifName =
                                Configuration.getInstance().getString("interface", "eth0");
                            NetworkInterface iface = null;
                            for (NetworkInterface device : devices) {
                                if (device.name.equals(ifName)) {
                                    iface = device;
                                    break;
                                }
                            }
                            if (null == iface) {
                                log.error("can't find specified interface");
                                System.exit(2);
                            }
                            log.info("using interface: " + iface.name);
                            captor = JpcapCaptor.openDevice(iface, 65535, true, 1000);
                            captor.setPacketReadTimeout(1000);
                        }
                    }

                    // compile filter
                    ScheduledFilterReloaderTask.prepareAndApplyFilter(captor);

                    threadPool.execute(new Thread(KeikoSniffer.this));
                    SnifferProtocolThread kspThread =
                        new SnifferProtocolThread(Configuration.getInstance().getString(
                            "keiko.host", "localhost"), Configuration.getInstance().getInt(
                            "keiko.port", SnifferProtocolThread.SERVER_PORT));
                    kspThread.setDaemon(true);
                    threadPool.execute(kspThread);
                    List<Stats> stats = new ArrayList<Stats>();
                    log.info("creating " + NUM_PROCESSING_THREADS + " processing threads");
                    for (int i = 0; i < NUM_PROCESSING_THREADS; i++) {
                        PacketProcessor pp = new PacketProcessor(i, queues.get(i), kspThread);
                        stats.add(pp.getStats());
                        threadPool.execute(pp);
                    }
                    loggerTask = new ScheduledStatsLoggerTask(captor, stats);
                    scheduledThreadPool.scheduleWithFixedDelay(loggerTask, 1, 1, TimeUnit.MINUTES);

                    // don't change filter in runtime, there seem to be issues with it and jvm
                    // just dies without any error or crash log
                    // scheduledThreadPool.scheduleWithFixedDelay(new ScheduledFilterReloaderTask(
                    // captor), 0, 24, TimeUnit.HOURS);

                    log.info(About.PRODUCT_VERSION + " started");
                } catch (Exception e) {
                    log.error("start", e);
                    System.exit(3);
                }
            }

        }, 1, TimeUnit.SECONDS);
    }

    private void stop() {
        running = false;
        if (null != captor && !USE_DUMP_CAP_FILE) {
            captor.breakLoop();
            captor = null;
        }
        if (null != scheduledThreadPool) {
            try {
                scheduledThreadPool.shutdown();
                // threadPool.awaitTermination(3, TimeUnit.SECONDS);
            } catch (Exception ignored) {
            } finally {
                try {
                    scheduledThreadPool.shutdownNow();
                } catch (Exception ignored) {
                }
            }
            scheduledThreadPool = null;
        }
        if (null != threadPool) {
            try {
                threadPool.shutdown();
                // threadPool.awaitTermination(3, TimeUnit.SECONDS);
            } catch (Exception ignored) {
            } finally {
                try {
                    threadPool.shutdownNow();
                } catch (Exception ignored) {
                }
            }
            threadPool = null;
        }
        if (null != queues) {
            queues.clear();
            queues = null;
        }
        if (null != loggerTask) {
            log.info("waiting for threads to stop");
            try {
                Thread.sleep(1000L);
            } catch (InterruptedException ignored) {
            }
            loggerTask.report();
            loggerTask = null;
        }
        log.info(About.PRODUCT_VERSION + " stopped\n\r\n\r\n\r");
    }

    private boolean areQueuesEmpty() {
        for (ConcurrentLinkedQueue<TCPPacket> queue : queues) {
            if (!queue.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.tanukisoftware.wrapper.WrapperListener#controlEvent(int)
     */
    @Override
    public void controlEvent(int event) {
        if ((WrapperManager.WRAPPER_CTRL_LOGOFF_EVENT == event)
            && WrapperManager.isLaunchedAsService()) {
            // Ignore
        } else {
            WrapperManager.stop(0);
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.tanukisoftware.wrapper.WrapperListener#start(java.lang.String[])
     */
    @Override
    public Integer start(String[] arg0) {
        Integer result;
        try {
            start();
            result = null;
        } catch (Exception e) { // never thrown
            log.error("Startup failed", e);
            result = new Integer(1);
        }

        return result;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.tanukisoftware.wrapper.WrapperListener#stop(int)
     */
    @Override
    public int stop(int exitCode) {
        if (running) {
            stop();
        }
        LogFactory.releaseAll();
        return 0;
    }

    private static void createJSR160Server() throws RemoteException, MalformedURLException,
        IOException {
        // Create RMI registry needed for JSR-160 connectors
        LocateRegistry.createRegistry(8998);

        // Create MBeanServer
        final MBeanServer server = MBeanServerFactory.createMBeanServer();

        // Create the JMXConnectorServer
        final JMXServiceURL address =
            new JMXServiceURL("service:jmx:rmi://localhost/jndi/rmi://localhost:8998/jmxconnector");
        // The environment map
        // Map environment = new HashMap();
        // environment.put(JMXConnectorServer.AUTHENTICATOR, new
        // FooAuthenticator());

        final JMXConnectorServer connectorServer =
            JMXConnectorServerFactory.newJMXConnectorServer(address, null, server);

        // Start the JMXConnectorServer
        connectorServer.start();
    }

    private static void registerMBean(Object mbean, String name)
        throws InstanceAlreadyExistsException, MBeanRegistrationException,
        NotCompliantMBeanException, MalformedObjectNameException {
        final List<MBeanServer> mbeanServers = MBeanServerFactory.findMBeanServer(null);
        if (!mbeanServers.isEmpty()) {
            // Register Wrapper with a first MBean server available
            (mbeanServers.get(0)).registerMBean(mbean, new ObjectName(name));
        } else {
            log.warn("JMX MBeanServer ain't available");
        }
    }

    /**
     * @param args
     */
    public static void main(String[] args) throws Exception {
        // Configure Log4J
        PropertyConfigurator.configure(Configuration.getInstance().getConfigurationFile()
            .getAbsolutePath());

        // Create JSR-160 enabled MBean Server
        createJSR160Server();

        // Register Wrapper MBean
        registerMBean(new org.tanukisoftware.wrapper.jmx.WrapperManager(),
            "KeikoSniffer:type=server");

        // Start server
        WrapperManager.start(new KeikoSniffer(), args);
    }

    private List<ConcurrentLinkedQueue<TCPPacket>> queues =
        new ArrayList<ConcurrentLinkedQueue<TCPPacket>>();
    private JpcapCaptor captor;

    public static boolean running;
    private static ExecutorService threadPool =
        Executors.newFixedThreadPool(1 + NUM_PROCESSING_THREADS + NUM_KSP_THREADS);
    private static ScheduledExecutorService scheduledThreadPool =
        Executors.newScheduledThreadPool(2);
    private ScheduledStatsLoggerTask loggerTask;

}

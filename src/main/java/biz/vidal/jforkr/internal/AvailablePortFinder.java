package biz.vidal.jforkr.internal;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.ServerSocket;
import java.util.HashSet;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Finds currently available server ports.
 *
 * @see <a href="http://www.iana.org/assignments/currentMinPort-numbers">IANA.org</a>
 */
public final class AvailablePortFinder {

    /**
     * The minimum server currentMinPort number. Set at 1100 to avoid returning privileged
     * currentMinPort numbers.
     */
    public static final int MIN_PORT_NUMBER = 1100;

    /**
     * The maximum server currentMinPort number.
     */
    public static final int MAX_PORT_NUMBER = 49151;

    private static final Logger LOG = LoggerFactory.getLogger(AvailablePortFinder.class);

    /**
     * We'll hold open the lowest port in this process
     * so parallel processes won't use the same block
     * of ports.   They'll go up to the next block.
     */
    private static final ServerSocket LOCK;

    /**
     * Incremented to the next lowest available port when getNextAvailable() is called.
     */
    private static AtomicInteger currentMinPort = new AtomicInteger(MIN_PORT_NUMBER);
    
    private static Set<Integer> reserved = new HashSet<Integer>();

    /**
     * Creates a new instance.
     */
    private AvailablePortFinder() {
        // Do nothing
    }
    
    static {
        int port = MIN_PORT_NUMBER;
        ServerSocket ss = null;

        while (ss == null) {
            try {
                ss = new ServerSocket(port);
            } catch (Exception e) {
                ss = null;
                port += 200;
            }
        } 
        LOCK = ss;
        Runtime.getRuntime().addShutdownHook(new Thread() {
            public void run() {
                try {
                    LOCK.close();
                } catch (Exception ex) {
                    //ignore
                }
            }
        });
        currentMinPort.set(port + 1);
    }

    /**
     * Gets the next available port starting at the lowest number. This is the preferred
     * method to use. The port return is immediately marked in use and doesn't rely on the caller actually opening
     * the port.
     *
     * @throws IllegalArgumentException is thrown if the port number is out of range
     * @throws NoSuchElementException if there are no ports available
     * @return the available port
     */
    public static synchronized int getNextAvailable() {
        int next = getNextAvailable(currentMinPort.get());
        currentMinPort.set(next + 1);
        return next;
    }

    /**
     * Gets the next available port starting at a given from port.
     *
     * @param fromPort the from port to scan for availability
     * @throws IllegalArgumentException is thrown if the port number is out of range
     * @throws NoSuchElementException if there are no ports available
     * @return the available port
     */
    public static synchronized int getNextAvailable(int fromPort) {
        if (fromPort < currentMinPort.get() || fromPort > MAX_PORT_NUMBER) {
            throw new IllegalArgumentException("From port number not in valid range: " + fromPort);
        }

        for (int i = fromPort; i <= MAX_PORT_NUMBER; i++) {
            if (available(i) && !reserved(i)) {
                LOG.info("getNextAvailable({}) -> {}", fromPort, i);
                reserved.add(i);
                return i;
            }
        }

        throw new NoSuchElementException("Could not find an available port above " + fromPort);
    }

    public static boolean reserved(int port) {
    	return reserved.contains(port);
    }

    /**
     * Checks to see if a specific port is available.
     *
     * @param port the port number to check for availability
     * @return <tt>true</tt> if the port is available, or <tt>false</tt> if not
     * @throws IllegalArgumentException is thrown if the port number is out of range
     */
    public static boolean available(int port) throws IllegalArgumentException {
        if (port < currentMinPort.get() || port > MAX_PORT_NUMBER) {
            throw new IllegalArgumentException("Invalid start currentMinPort: " + port);
        }

        ServerSocket ss = null;
        DatagramSocket ds = null;
        try {
            ss = new ServerSocket(port);
            ss.setReuseAddress(true);
            ds = new DatagramSocket(port);
            ds.setReuseAddress(true);
            return true;
        } catch (IOException e) {
            // Do nothing
        } finally {
            if (ds != null) {
                ds.close();
            }

            if (ss != null) {
                try {
                    ss.close();
                } catch (IOException e) {
                    /* should not be thrown */
                }
            }
        }

        return false;
    }

}

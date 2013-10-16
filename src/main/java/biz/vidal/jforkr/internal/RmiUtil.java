package biz.vidal.jforkr.internal;

import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.remoting.rmi.RmiProxyFactoryBean;

/**
 * @author <a href="http://vidal.biz">Cedric Vidal</a>
 *
 */
public class RmiUtil {

    private static Logger log = LoggerFactory.getLogger(RmiUtil.class);

    public static <T> T importService(Class<T> serviceInterface, int port, String serviceName, long timeout, TimeUnit timeunit) {
        long timeoutMs = timeunit.toMillis(timeout);

        String url = "rmi://localhost:" + port + "/" + serviceName;
        log.info("Creating RMI proxy for " + serviceInterface + " on " + url);
        RmiProxyFactoryBean rmiFactory = new RmiProxyFactoryBean();
        rmiFactory.setServiceInterface(serviceInterface);
        rmiFactory.setServiceUrl(url);
        rmiFactory.setRefreshStubOnConnectFailure(true);
        rmiFactory.setLookupStubOnStartup(true);

        boolean connected = false;
        RuntimeException thrown = null;
        long start = System.currentTimeMillis();
        while (!connected && System.currentTimeMillis() - start < timeoutMs) {
            try {
                rmiFactory.afterPropertiesSet();
                connected = true;
            } catch (RuntimeException e) {
                thrown = e;
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e1) {
                    throw new RuntimeException("Interrupted while waiting for service to be available", e1);
                }
            }
        }
        if (!connected) {
            throw thrown;
        }

        T serviceProxy = (T) rmiFactory.getObject();
        return serviceProxy;
    }

}

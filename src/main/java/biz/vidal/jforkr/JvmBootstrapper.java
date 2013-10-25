package biz.vidal.jforkr;

import static biz.vidal.jforkr.internal.RmiUtil.importService;
import static java.util.concurrent.TimeUnit.SECONDS;

import java.rmi.RemoteException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.remoting.RemoteLookupFailureException;
import org.springframework.remoting.rmi.RmiServiceExporter;

import biz.vidal.jforkr.JvmManager.Ping;
import biz.vidal.jforkr.internal.JvmControllerServerImpl;

/**
 * @author <a href="http://vidal.biz">Cedric Vidal</a>
 *
 * @param <T>
 */
public class JvmBootstrapper<T> {
    private static Logger log = LoggerFactory.getLogger(JvmBootstrapper.class);

    private int rmiRegistryPort;
    private String serviceClassName;
    private String serviceInterfaceName;

    private JvmControllerServerImpl controller;

    private AtomicBoolean stop = new AtomicBoolean(false);
    private CountDownLatch latch = new CountDownLatch(1);

    private String processName;

    public static void main(String[] args) throws Exception {

        // redirectSysIO();

        int port = Integer.parseInt(args[0]);
        final String serviceInterfaceName = args[1];
        String serviceClassName = args[2];
        String processName = args[3];
        String uuid = args[4];

        log.info("Booting {} in process {} on JVM {}", new String[] { serviceInterfaceName, processName, uuid });

        JvmBootstrapper<Object> bootstrapper = new JvmBootstrapper<Object>();
        bootstrapper.setRmiRegistryPort(port);
        bootstrapper.setServiceClassName(serviceClassName);
        bootstrapper.setServiceInterfaceName(serviceInterfaceName);
        bootstrapper.setProcessName(processName);
        bootstrapper.setUuid(uuid);
        bootstrapper.run();

    }

    private void setProcessName(String processName) {
        this.processName = processName;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void run() throws Exception {

        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                log.info("Terminated " + serviceInterfaceName);
                log.debug("Keepalive thread still running " + keepaliveRunning);
            }
        });

        Class<?> serviceClass = Class.forName(serviceClassName);
        Class<?> serviceInterface = Class.forName(serviceInterfaceName);

        controller = new JvmControllerServerImpl();
        controller.setBootstrapper(this);

        String serviceName = serviceName(serviceInterface, uuid);
        String controllerName = controllerName(uuid);

        ping = importService(Ping.class, rmiRegistryPort, Ping.class.getSimpleName(), 10, SECONDS);
        exportService(serviceClass, serviceInterface, serviceName);
        exportService(controller, JvmController.class, controllerName);

        startKeepalive();

        latch.await();

    }

    private AtomicBoolean keepaliveRunning = new AtomicBoolean(false);

    private Ping ping;

    private String uuid;

    private void startKeepalive() {
        new Thread("keepalive") {

            @Override
            public void run() {
                log.debug("Starting keepalive thread");
                keepaliveRunning.set(true);
                try {
                    while (!stop.get()) {

                        // Ping will fail if owner process is not reachable
                        log.debug("Trying to ping owner process");
                        ping.ping(uuid);

                        Thread.sleep(2000);
                    }
                } catch (InterruptedException e) {
                    log.warn("Interrupted");
                } catch (RemoteLookupFailureException e) {
                    log.error("Owner process is not reachable");
                } catch (Throwable e) {
                    log.error("An unexpected exception occured", e);
                } finally {
                    keepaliveRunning.set(false);
                }
                if (!stop.get()) {
                    log.debug("Exit requested");
                }
                exit();
            };
        }.start();
    }

    protected void exportService(Object service, Class<?> serviceInterface, String serviceName) throws InstantiationException, IllegalAccessException, RemoteException {
        log.debug("Exporting " + serviceInterface.getName() + " as " + serviceName);
        RmiServiceExporter exporter = new RmiServiceExporter();
        exporter.setService(service);
        exporter.setServiceInterface(serviceInterface);
        exporter.setRegistryPort(rmiRegistryPort);
        exporter.setRegistryHost("localhost");
        exporter.setServiceName(serviceName);
        try {
            exporter.afterPropertiesSet();
        } catch (RuntimeException e) {
            log.error("Could not export {} as {}", serviceInterface, serviceName);
            throw e;
        }
    }

    protected void exportService(Class<?> serviceClass, Class<?> serviceInterface, String serviceName) throws InstantiationException, IllegalAccessException, RemoteException {
        exportService(serviceClass.newInstance(), serviceInterface, serviceName);
    }

    public int getRmiRegistryPort() {
        return rmiRegistryPort;
    }

    public void setRmiRegistryPort(int port) {
        this.rmiRegistryPort = port;
    }

    public String getServiceClassName() {
        return serviceClassName;
    }

    public void setServiceClassName(String serviceClassName) {
        this.serviceClassName = serviceClassName;
    }

    public String getServiceInterfaceName() {
        return serviceInterfaceName;
    }

    public void setServiceInterfaceName(String serviceInterfaceName) {
        this.serviceInterfaceName = serviceInterfaceName;
    }

    public void exit() {
        log.info("Exiting " + processName);
        this.stop.set(true);
        this.latch.countDown();
        System.exit(0);
    }

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public static <T> String serviceName(Class<T> serviceInterface, String uuid) {
        String serviceName = uuid + "/" + serviceInterface.getSimpleName();
        return serviceName;
    }

    public static <T> String controllerName(String uuid) {
        String serviceName = uuid + "/Controller";
        return serviceName;
    }

}

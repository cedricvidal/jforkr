package biz.vidal.jforkr;

import static biz.vidal.jforkr.JvmBootstrapper.controllerName;
import static biz.vidal.jforkr.JvmBootstrapper.serviceName;
import static biz.vidal.jforkr.internal.RmiUtil.importService;
import static com.google.common.base.Predicates.notNull;
import static com.google.common.collect.Collections2.filter;
import static com.google.common.collect.Iterables.addAll;
import static com.google.common.collect.Iterables.transform;
import static com.google.common.collect.Lists.newArrayList;
import static java.lang.System.currentTimeMillis;
import static java.util.Collections.addAll;
import static java.util.Collections.max;
import static java.util.Collections.unmodifiableMap;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.jboss.shrinkwrap.resolver.api.DependencyResolvers.use;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.rmi.NoSuchObjectException;
import java.rmi.RemoteException;
import java.rmi.registry.Registry;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.codehaus.classworlds.Launcher;
import org.jboss.shrinkwrap.resolver.api.maven.MavenDependencyResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.remoting.rmi.RmiRegistryFactoryBean;
import org.springframework.remoting.rmi.RmiServiceExporter;

import biz.vidal.jforkr.internal.AvailablePortFinder;
import biz.vidal.jforkr.internal.JvmControllerClientImpl;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Predicate;

/**
 * @author <a href="http://vidal.biz">Cedric Vidal</a>
 *
 */
public class JvmManager {

    private static Logger log = LoggerFactory.getLogger(JvmManager.class);
    private File classworldsJar;
    private Iterable<File> systemClasspath;

    private int rmiRegistryPort;
    private Registry rmiRegistry;
    private Set<Integer> usedPorts = new HashSet<Integer>();
    private Object ping;
    private DisposableBean pingDisposer;
    private DisposableBean rmiRegistryDisposer;
	private List<JvmController<?>> controllers = new ArrayList<JvmController<?>>();

    public void init() throws Exception {

        if(rmiRegistry == null) {
            classworldsJar = findClassworldsJar();
            systemClasspath = getSystemClasspath();
            log.debug("Classpath is \n" + Joiner.on("\n").join(systemClasspath));

            rmiRegistryPort = AvailablePortFinder.getNextAvailable();
            RmiRegistryFactoryBean rmiRegistryFactory = createRmiRegistry(rmiRegistryPort);
            rmiRegistryDisposer = rmiRegistryFactory;
            rmiRegistry = rmiRegistryFactory.getObject();

            ping = createPing();
            pingDisposer = export(ping, Ping.class, Ping.class.getSimpleName(), rmiRegistry);

            lbPort = AvailablePortFinder.getNextAvailable(usedPorts.isEmpty() ? 8080 : max(usedPorts) + 1);

            Runtime.getRuntime().addShutdownHook(new Thread("forked-process-terminator") {
                @Override
                public void run() {
                    shutdown();
                }
            });
        } else {
            pruneDeadProcesses();
        }

    }

    public void pruneDeadProcesses() {
        for (JvmController<?> ctrl : newArrayList(controllers)) {
            if(!ctrl.isAlive()) {
                controllers.remove(ctrl);
            }
        }
    }

    protected File findClassworldsJar() {
        File jar = null;
        String classPath = "/" + Launcher.class.getName().replace(".", "/") + ".class";
        URL classUrl = Launcher.class.getResource(classPath);
        String url = classUrl.toExternalForm();
        if(url.startsWith("jar:")) {
            url = url.substring(4);
            int sep = url.indexOf("!");
            if(sep > 0) {
                url = url.substring(0, sep);
                try {
                    jar = toFile(new URL(url));
                } catch (MalformedURLException e) {
                    ; // ignore
                }
            }
        }
        if(jar == null) {
            jar = use(MavenDependencyResolver.class).artifact("classworlds:classworlds:1.1").resolveAsFiles()[0];
        }
        return jar;
    }

    public static File toFile(URL url) {
        File f;
        try {
          f = new File(url.toURI());
        } catch(URISyntaxException e) {
          f = new File(url.getPath());
        }
        return f;
    }

    protected SimplePing createPing() {
        return new SimplePing();
    }

    protected DisposableBean export(Object service, Class<?> serviceInterface, String serviceName, Registry registry) throws InstantiationException, IllegalAccessException, RemoteException {
        log.info("Exporting " + serviceInterface.getName());
        RmiServiceExporter exporter = new RmiServiceExporter();
        exporter.setService(service);
        exporter.setServiceInterface(serviceInterface);
        exporter.setRegistry(registry);
        exporter.setServiceName(serviceName);
        exporter.afterPropertiesSet();
        return exporter;
    }

    public interface Ping {
        public void ping(String uuid);
    }

    public static class SimplePing implements Ping {
        private static Logger log = LoggerFactory.getLogger(SimplePing.class);
        private ConcurrentHashMap<String, Long> lastPings = new ConcurrentHashMap<String, Long>();
        @Override
        public void ping(String uuid) {
            log.debug("Pinged by {}", uuid);
            lastPings.put(uuid, currentTimeMillis());
        }
        public Map<String, Long> lastPings() {
            return unmodifiableMap(lastPings);
        }
    }

    protected RmiRegistryFactoryBean createRmiRegistry(int port) throws Exception {
        RmiRegistryFactoryBean rmiRegistryFactory = new RmiRegistryFactoryBean();
        rmiRegistryFactory.setPort(port);
        rmiRegistryFactory.setAlwaysCreate(true);
        rmiRegistryFactory.afterPropertiesSet();
        return rmiRegistryFactory;
    }

    @SuppressWarnings("unchecked")
    public <T> JvmController<T> fork(String processName, Class<T> serviceInterface, Class<?> serviceClass) throws IOException, FileNotFoundException {

        List<File> libs = new ArrayList<File>();
        addAll(libs, systemClasspath);

        File classworldsConf = File.createTempFile("classworlds", "conf");
        writeClassworldsConfiguration(new FileOutputStream(classworldsConf), JvmBootstrapper.class.getName(), libs);

        String javaHome = System.getProperty("java.home");
        File javaHomeFile = new File(javaHome);

        String javaName = "java";
        if(System.getProperty("os.name").toLowerCase().contains("windows")) {
            javaName = "java.exe";
        }

        String javaExe = new File(javaHomeFile, "/bin/" + javaName).toString();

        List<String> args = new ArrayList<String>();
        addAll(args, javaExe);

        String classpath = classworldsJar.getAbsolutePath();
        addAll(args, "-cp", classpath);

//        addSystemProperties(args);

        JvmControllerClientImpl<T> controller = new JvmControllerClientImpl<T>();

        Set<String> vmArgs = new HashSet<String>();
        RuntimeMXBean RuntimemxBean = ManagementFactory.getRuntimeMXBean();
        vmArgs.addAll(filter(RuntimemxBean.getInputArguments(), isVmArgumentP()));
        log.info("VM Args : " + Joiner.on(" ").join(vmArgs));
        for (Iterator i = vmArgs.iterator(); i.hasNext() && controller.getDebugPort() == null;) {
            String vmArg = (String) i.next();
            if (vmArg.startsWith("-Xrunjdwp:transport=")) {
                int debugPort = AvailablePortFinder.getNextAvailable(8000);
                controller.setDebugPort(debugPort);
                i.remove();
            }
            if(vmArg.startsWith("-agentlib:jdwp=")) {
                int debugPort = AvailablePortFinder.getNextAvailable(8000);
                controller.setDebugPort(debugPort);
                i.remove();
            }
        }
        if (controller.getDebugPort() != null) {
            addAll(vmArgs, "-Xdebug", "-Xnoagent", "-Djava.compiler=NONE", "-Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=" + controller.getDebugPort());
        }

        addAll(args, vmArgs);

        addAll(args, "-Dclassworlds.conf=" + classworldsConf.getAbsolutePath());
        addAll(args, Launcher.class.getName());

        String uuid = UUID.randomUUID().toString();
        addAll(args, "" + rmiRegistryPort, serviceInterface.getName(), serviceClass.getName(), processName, uuid);

        String[] targetArgs = args.toArray(new String[] {});

        log.info("Forking process {} into JVM {}", processName, uuid);
        Process process = new ProcessBuilder(targetArgs).redirectErrorStream(true).start();

        print(process, processName);

        try {
            int exitValue = process.exitValue();
            log.debug("Process exited with code " + exitValue);
        } catch (IllegalThreadStateException e) {
            log.debug("Process still running");
        }

        controller.setProcess(process);

        String serviceName = serviceName(serviceInterface, uuid);
        T serviceProxy = importService(serviceInterface, rmiRegistryPort, serviceName, 10, SECONDS);

        String controllerName = controllerName(uuid);
        JvmController<T> controllerProxy = importService(JvmController.class, rmiRegistryPort, controllerName, 10, TimeUnit.SECONDS);

        controller.setService(serviceProxy);
        controller.setDelegate(controllerProxy);
        controller.setProcessName(processName);
        controller.setUUID(uuid);

        controllers.add(controller);
        return controller;
    }

    public static Predicate<String> isVmArgumentP() {
        return new Predicate<String>() {
            @Override
            public boolean apply(String input) {
                return input.startsWith("-");
            }
        };
    }

    private void addSystemProperties(List<String> args) {
        // Forward the system properties we were called with
        for (Entry<Object, Object> entry : System.getProperties().entrySet()) {
            String key = (String) entry.getKey();
            String value = (String) entry.getValue();
            args.add("-D" + key + "=" + value);
        }
    }

    protected void print(Process process, String processName) {
        log.info("Input stream");
        print(process.getInputStream(), processName, "IN");
        log.info("Error stream");
        print(process.getErrorStream(), processName, "ER");
    }

    private void print(final InputStream is, final String processName, String type) {
        final String threadName = processName + "-" + type;
        Thread thread = new Thread(threadName) {
            @Override
            public void run() {
                log.info("Starting input reader thread " + threadName);
                InputStreamReader isr = new InputStreamReader(is);
                BufferedReader br = new BufferedReader(isr);

                String line;
                try {
                    while ((line = br.readLine()) != null) {
                        System.out.println("[" + threadName + "] " + line);
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                log.info("No more input for " + threadName);
            };
        };
        thread.setDaemon(true);
        thread.start();
    }

    protected Iterable<File> getSystemClasspath() {
        String pathSeparator = System.getProperty("path.separator", ":");
        Iterable<File> classpathList = transform(newArrayList(System.getProperty("java.class.path", "").split(pathSeparator)), toFileF());
        return classpathList;
    }

    public static Function<String, File> toFileF() {
        return new Function<String, File>() {
            @Override
            public File apply(String path) {
                return new File(path);
            }
        };
    }

    protected void writeClassworldsConfiguration(OutputStream outputStream, String name, Iterable<File> coreLibs) throws IOException {
        PrintStream print = new PrintStream(outputStream);
        print.println("main is " + name + " from app");
        print.println("[app]");
        for (File file : coreLibs) {
            String path = file.toURI().toURL().toExternalForm();
            print.println("\tload " + path);
        }
    }

    boolean shutdown = false;
    private int lbPort;
    public synchronized void shutdown() {
        if(shutdown) {
            return;
        }
        for (JvmController<?> ctrl : filter(controllers, notNull())) {
            try {
                ctrl.exit();
            } catch (Exception e) {
                log.info("Failed to shutdown " + ctrl, e);
            }
        }
        destroy(pingDisposer, "ping");
        destroy(rmiRegistryDisposer, "RMI Registry");
        shutdown = true;
    }

    protected void destroy(DisposableBean disposableBean, String name) {
        if (disposableBean != null) {
            try {
                disposableBean.destroy();
            } catch(NoSuchObjectException e) {
                ; // ignore
            } catch (Exception e) {
                log.warn("Failed to dispose {}", name, e);
            }
        }
    }

}

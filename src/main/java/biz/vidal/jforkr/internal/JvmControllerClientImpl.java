package biz.vidal.jforkr.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.remoting.RemoteConnectFailureException;

import biz.vidal.jforkr.JvmController;

/**
 * @author <a href="http://vidal.biz">Cedric Vidal</a>
 *
 * @param <T>
 */
public class JvmControllerClientImpl<T> implements JvmController<T> {
    private Process process;
    private T service;
    private JvmController<T> delegate;
    private Integer debugPort;
    private String processName;
    private String uuid;

    private static Logger log = LoggerFactory.getLogger(JvmController.class.getName());

    @Override
    public T getService() {
        return this.service;
    }

    @Override
    public void exit() {
        if (delegate != null) {
            synchronized (delegate) {
                log.info("Shutting down " + service.getClass().getName());
                try {
                    delegate.exit();
                } catch (RemoteConnectFailureException e) {
                    ; // expected just after the process has been terminated
                } catch (Exception e) {
                    log.info("Failed to shutdown " + service.getClass().getName() + " so killing it", e);
                    kill();
                } finally {
                    delegate = null;
                }
            }
        } else {
            log.info("Not running");
        }
    }

    @Override
    public void kill() {
        process.destroy();
    }

    public void setProcess(Process process) {
        this.process = process;
    }

    public void setService(T service) {
        this.service = service;
    }

    public JvmController<T> getDelegate() {
        return delegate;
    }

    public void setDelegate(JvmController<T> delegate) {
        this.delegate = delegate;
    }

    @Override
    public String toString() {
        return "JvmController<" + processName + "@" + uuid + ">";
    }

    public void setDebugPort(Integer debugPort) {
        this.debugPort = debugPort;
    }

    @Override
    public Integer getDebugPort() {
        return debugPort;
    }

    @Override
    public boolean isAlive() {
        boolean alive = false;
        try {
            process.exitValue();
        } catch (IllegalThreadStateException e) {
            alive = true;
        }
        return alive;
    }

    public void setProcessName(String processName) {
        this.processName = processName;
    }

    @Override
    public String getProcessName() {
        return processName;
    }

    public void setUUID(String uuid) {
        this.uuid = uuid;
    }

    public String getUUID() {
        return uuid;
    }

}

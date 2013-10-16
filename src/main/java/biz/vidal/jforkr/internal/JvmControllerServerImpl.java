package biz.vidal.jforkr.internal;

import biz.vidal.jforkr.JvmBootstrapper;
import biz.vidal.jforkr.JvmController;


/**
 * @author <a href="http://vidal.biz">Cedric Vidal</a>
 *
 * @param <T>
 */
public class JvmControllerServerImpl<T> implements JvmController<T> {

    private JvmBootstrapper<T> bootstrapper;

    @Override
    public T getService() {
        throw new UnsupportedOperationException("Not supported server side");
    }

    @Override
    public void exit() {
        bootstrapper.exit();
    }

    public JvmBootstrapper<T> getBootstrapper() {
        return bootstrapper;
    }

    public void setBootstrapper(JvmBootstrapper<T> bootstrapper) {
        this.bootstrapper = bootstrapper;
    }

    @Override
    public Integer getDebugPort() {
        throw new UnsupportedOperationException("Not supported server side");
    }

    @Override
    public void kill() {
    }

    @Override
    public boolean isAlive() {
        return true;
    }

    public String getProcessName() {
        return null;
    }

}

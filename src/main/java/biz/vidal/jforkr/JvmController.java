package biz.vidal.jforkr;

/**
 * @author <a href="http://vidal.biz">Cedric Vidal</a>
 *
 * @param <T>
 */
public interface JvmController<T> {
    public T getService();
    public void exit();
    public abstract Integer getDebugPort();
    public abstract void kill();
    public boolean isAlive();
    public abstract String getProcessName();
}


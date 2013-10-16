package biz.vidal.jforkr;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author <a href="http://vidal.biz">Cedric Vidal</a>
 *
 */
public class JvmManagerTest {

	private static Logger log = LoggerFactory.getLogger(JvmManagerTest.class);

	@Test
	public void test() throws Exception {
		JvmManager jvmManager = new JvmManager();
		jvmManager.init();

		try {
			JvmController<Echo> loadBalancerController = jvmManager.fork("Echo", Echo.class, EchoImpl.class);
			Echo echo = loadBalancerController.getService();

			if (loadBalancerController.getDebugPort() != null) {
				log.info("Echo debug port is {}", loadBalancerController.getDebugPort());
			}

			assertEquals("Hello", echo.echo("Hello"));
		} finally {
			if (jvmManager != null) {
				jvmManager.shutdown();
				jvmManager = null;
			}
		}

	}

}

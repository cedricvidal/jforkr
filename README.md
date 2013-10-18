jforkr
======

JVM Forker and controller. Allows to fork a service in a separate JVM and remotely control it.

Sample usage scenarios
----------------------

Simple

- Run multi JVM unit tests
- Create mocks that need to run in a separate JVM
- Run integration tests where you care about testing networking and protocols instead of mocking at the code level.

Advanced

- Test how your application behaves behind a load balancer: mock a load balancer and check what happens on fail over

How does it work ?
------------------

Forks the currently running JVM by detecting the current java executable and launching it using the current classpath.

Exports your service using RMI in the forked JVM so that you can access it from the current JVM.
You can control the forked JVM using a JvmController.
A heartbeat makes sure your process is still alive so you know if it dies.
The forked JVM commits suicide if it looses contact with the current JVM so that no dangling JVM are left over.

Works around -cp length OS limitations by using classworlds.

If the current JVM has been started with a debugger (tested with the Eclipse debugger), it is detected and the forked JVM is started with remote debugging active so that one can easily remote debug the forked JVM.

You can simply run a sample application or a unit test from your favorite IDE and run a multi JVM scenario. At the end of the run, all forked JVM are killed.

Sample usage
------------

	JvmManager jvmManager = new JvmManager();
	jvmManager.init();

	try {
	
		// Fork Echo service in a separate JVM and return a handle to its controller
		JvmController<Echo> loadBalancerController = jvmManager.fork("Echo", Echo.class, EchoImpl.class);
		
		// Gets the remote Echo service (exported using RMI)
		Echo echo = loadBalancerController.getService();

		// If the current JVM has been started with debugging then the forked JVM can also be remotely debugged. Display port so that one can connect a remote debugger to the port.
		if (loadBalancerController.getDebugPort() != null) {
			log.info("Echo debug port is {}", loadBalancerController.getDebugPort());
		}

		// Check that the remote service works, all parameters must be Serializable
		assertEquals("Hello", echo.echo("Hello"));

	} finally {
		if (jvmManager != null) {
			jvmManager.shutdown();
			jvmManager = null;
		}
	}

Current dependencies
--------------------

Currently depends on Spring Core and Spring AOP for transparent RMI exporting but at some point it would be interesting to remove that dependency to lower the footprint.

Also depends on [Shrinkwrap](http://www.jboss.org/shrinkwrap) resolver to resolve classwords jar if not present in the running JVM. Here, also planning on finding a lower footprint solution.

Future improvements
-------------------

**Forked JVM recycling**

Running multi JVM integration tests can be very slow if you stop and start all your forked JVM after each test. It would be interesting to allow recycling a forked JVM when its state has been dirtied. Note that one can already implement this in the current state of this library as you can just add a recycle method to your exported service but it currently requires some work. Not sure yet if it should be part of Jforkr though.

Contributing
------------

Just fork the repo and make a pull request. I can't promise I'll get back to you in the hour but I promise to consider the request.

[Cédric Vidal](http://vidal.biz)

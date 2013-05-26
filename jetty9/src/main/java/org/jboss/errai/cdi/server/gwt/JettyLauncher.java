/*
 * License: APL2
 * Source: https://code.google.com/p/gwt-jetty/ * 
 */
package org.jboss.errai.cdi.server.gwt;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.List;

import javax.naming.Binding;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;

import org.eclipse.jetty.http.HttpField;
import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.RequestLog;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.RequestLogHandler;
import org.eclipse.jetty.util.component.AbstractLifeCycle;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.eclipse.jetty.webapp.WebAppClassLoader;
import org.eclipse.jetty.webapp.WebAppContext;
import org.slf4j.LoggerFactory;

import com.google.gwt.core.ext.ServletContainer;
import com.google.gwt.core.ext.ServletContainerLauncher;
import com.google.gwt.core.ext.TreeLogger;
import com.google.gwt.core.ext.UnableToCompleteException;
import com.google.gwt.dev.shell.jetty.JettyNullLogger;
import com.google.gwt.dev.util.InstalledHelpInfo;
//import org.eclipse.jetty.http.HttpFields.Field;
//import org.eclipse.jetty.server.nio.SelectChannelConnector;

public class JettyLauncher extends ServletContainerLauncher {

	static {
		System.setProperty("org.eclipse.jetty.jndi.InitialContextFactory", "org.eclipse.jetty.jndi.InitialContextFactory");
		System.setProperty("java.naming.factory.url.pkgs", "org.eclipse.jetty.jndi");
		System.setProperty("java.naming.factory.initial", "org.eclipse.jetty.jndi.InitialContextFactory");
	}
	
    public static String[] __dftConfigurationClasses =
    {
        "org.eclipse.jetty.webapp.WebInfConfiguration",
        "org.eclipse.jetty.webapp.WebXmlConfiguration",
        "org.eclipse.jetty.webapp.MetaInfConfiguration",
        "org.eclipse.jetty.webapp.FragmentConfiguration",
        "org.eclipse.jetty.plus.webapp.EnvConfiguration",
        "org.eclipse.jetty.plus.webapp.PlusConfiguration",
        "org.eclipse.jetty.webapp.JettyWebXmlConfiguration"
        //"org.eclipse.jetty.webapp.TagLibConfiguration"
    } ;
    
	public static class JettyRequestLogger extends AbstractLifeCycle implements
			RequestLog {

		private final TreeLogger logger;
		private final TreeLogger.Type normalLogLevel;

		public JettyRequestLogger(TreeLogger logger,
				TreeLogger.Type normalLogLevel) {
			this.logger = logger;
			assert (normalLogLevel != null);
			this.normalLogLevel = normalLogLevel;
		}

		/**
		 * Log an HTTP request/response to TreeLogger.
		 */
		public void log(Request request, Response response) {
			int status = response.getStatus();
			if (status < 0) {
				// Copied from NCSARequestLog
				status = 404;
			}
			TreeLogger.Type logStatus, logHeaders;
			if (status >= 500) {
				logStatus = TreeLogger.ERROR;
				logHeaders = TreeLogger.INFO;
			} else if (status == 404) {
				if ("/favicon.ico".equals(request.getRequestURI())
						&& request.getQueryString() == null) {
					/*
					 * We do not want to call the developer's attention to a 404
					 * when requesting favicon.ico. This is a very common 404.
					 */
					logStatus = TreeLogger.TRACE;
					logHeaders = TreeLogger.DEBUG;
				} else {
					logStatus = TreeLogger.WARN;
					logHeaders = TreeLogger.INFO;
				}
			} else if (status >= 400) {
				logStatus = TreeLogger.WARN;
				logHeaders = TreeLogger.INFO;
			} else {
				logStatus = normalLogLevel;
				logHeaders = TreeLogger.DEBUG;
			}

			String userString = request.getRemoteUser();
			if (userString == null) {
				userString = "";
			} else {
				userString += "@";
			}
			String bytesString = "";
			if (response.getContentCount() > 0) {
				bytesString = " " + response.getContentCount() + " bytes";
			}
			if (logger.isLoggable(logStatus)) {
				TreeLogger branch = logger.branch(logStatus, String
						.valueOf(status)
						+ " - "
						+ request.getMethod()
						+ ' '
						+ request.getUri()
						+ " ("
						+ userString
						+ request.getRemoteHost()
						+ ')'
						+ bytesString);
				if (branch.isLoggable(logHeaders)) {
					// Request headers
					TreeLogger headers = branch.branch(logHeaders,
							"Request headers");
//					Iterator<Field> headerFields = request.getConnection()
//							.getRequestFields().get
//					while (headerFields.hasNext()) {
//						Field headerField = headerFields.next();
//						headers.log(logHeaders, headerField.getName() + ": "
//								+ headerField.getValue());
//					}
					//HttpFields httpFields = request.getConnection().getRequestFields();
					HttpFields httpFields = request.getHttpFields();
					for (int i = 0; i < httpFields.size(); i++) {
						HttpField headerField = httpFields.getField(i);
						if (headerField != null)
							headers.log(logHeaders, headerField.getName() + ": " + headerField.getValue());
					}
					// Response headers
					headers = branch.branch(logHeaders, "Response headers");
					httpFields = response.getHttpFields();
					for (int i = 0; i < httpFields.size(); i++) {
						HttpField headerField = httpFields.getField(i);
						if (headerField != null)
							headers.log(logHeaders, headerField.getName() + ": " + headerField.getValue());
					}
				}
			}
		}
	}

	/**
	 * An adapter for the Jetty logging system to GWT's TreeLogger. This
	 * implementation class is only public to allow {@link Log} to instantiate
	 * it.
	 * 
	 * The weird static data / default construction setup is a game we play with
	 * {@link Log}'s static initializer to prevent the initial log message from
	 * going to stderr.
	 */
	public static class JettyTreeLogger implements Logger {

		private final TreeLogger logger;
		
		public JettyTreeLogger(TreeLogger logger) {
			if (logger == null) {
				throw new NullPointerException();
			}
			
			this.logger = logger;
		}

		@Override
		public void debug(Throwable thrown) {
			logger.log(TreeLogger.SPAM, format(thrown));
		}

		@Override
		public void debug(String msg, Object... args) {
			logger.log(TreeLogger.SPAM, format(msg, args));
		}

		@Override
		public void debug(String msg, Throwable thrown) {
			logger.log(TreeLogger.SPAM, msg, thrown);
		}

		@Override
		public Logger getLogger(String name) {
			return this;
		}

		@Override
		public String getName() {
			return JettyTreeLogger.class.getSimpleName();
		}

		@Override
		public void info(Throwable thrown) {
			logger.log(TreeLogger.TRACE, format(thrown));
		}

		@Override
		public void info(String msg, Object... args) {
			logger.log(TreeLogger.TRACE, format(msg, args));
		}

		@Override
		public void info(String msg, Throwable thrown) {
			logger.log(TreeLogger.TRACE, msg, thrown);
		}

		@Override
		public boolean isDebugEnabled() {
			return logger.isLoggable(TreeLogger.SPAM);
		}

		@Override
		public void setDebugEnabled(boolean enabled) {}

		@Override
		public void warn(Throwable thrown) {
			logger.log(TreeLogger.WARN, format(thrown));
		}

		@Override
		public void warn(String msg, Object... args) {
			logger.log(TreeLogger.WARN, format(msg, args));
		}

		@Override
		public void warn(String msg, Throwable thrown) {
			logger.log(TreeLogger.WARN, msg, thrown);
		}

		/**
		 * @param msg
		 * @param args
		 */
		private String format(String msg, Object... args) {
			StringBuilder buffer = new StringBuilder();
			format(buffer, msg, args);
			
			return buffer.toString();
		}
		
		/**
		 * @param thrown
		 */
		private String format(Throwable thrown) {
			StringBuilder buffer = new StringBuilder();
			format(buffer, thrown);
			
			return buffer.toString();
		}
		
		/**
	     * Copied from org.eclipse.jetty.util.log.StdErrLog.
	     */
		private void format(StringBuilder builder, String msg, Object... args)
	    {
	        msg = String.valueOf(msg); // Avoids NPE
	        String braces = "{}";
	        int start = 0;
	        for (Object arg : args)
	        {
	            int bracesIndex = msg.indexOf(braces, start);
	            if (bracesIndex < 0)
	            {
	                escape(builder, msg.substring(start));
	                builder.append(" ");
	                builder.append(arg);
	                start = msg.length();
	            }
	            else
	            {
	                escape(builder, msg.substring(start, bracesIndex));
	                builder.append(String.valueOf(arg));
	                start = bracesIndex + braces.length();
	            }
	        }
	        escape(builder, msg.substring(start));
	    }

		/**
	     * Copied from org.eclipse.jetty.util.log.StdErrLog.
	     */
	    private void escape(StringBuilder builder, String string)
	    {
	        for (int i = 0; i < string.length(); ++i)
	        {
	            char c = string.charAt(i);
	            if (Character.isISOControl(c))
	            {
	                if (c == '\n')
	                    builder.append('|');
	                else if (c == '\r')
	                    builder.append('<');
	                else
	                    builder.append('?');
	            }
	            else
	                builder.append(c);
	        }
	    }

	    /**
	     * Copied from org.eclipse.jetty.util.log.StdErrLog.
	     */
	    private void format(StringBuilder buffer, Throwable thrown)
	    {
	        if (thrown == null)
	        {
	            buffer.append("null");
	        }
	        else
	        {
	            buffer.append('\n');
	            format(buffer, thrown.toString());
	            StackTraceElement[] elements = thrown.getStackTrace();
	            for (int i = 0; elements != null && i < elements.length; i++)
	            {
	                buffer.append("\n\tat ");
	                format(buffer, elements[i].toString());
	            }
	            
	            Throwable cause = thrown.getCause();
	            if (cause!=null && cause!=thrown)
	            {
	                buffer.append("\nCaused by: ");
	                format(buffer,cause);
	            }
	        }
	    }

		@Override
		public void ignore(Throwable arg0) {
			logger.log(TreeLogger.WARN, format(arg0));
		}
		
	}

	/**
	 * The resulting {@link ServletContainer} this is launched.
	 */
	public static class JettyServletContainer extends ServletContainer {
		private final int actualPort;
		private final File appRootDir;
		private final TreeLogger logger;
		private final Server server;
		private final WebAppContext wac;

		public JettyServletContainer(TreeLogger logger, Server server,
				WebAppContext wac, int actualPort, File appRootDir) {
			this.logger = logger;
			this.server = server;
			this.wac = wac;
			this.actualPort = actualPort;
			this.appRootDir = appRootDir;
		}

		@Override
		public int getPort() {
			return actualPort;
		}

		@Override
		public void refresh() throws UnableToCompleteException {
			String msg = "Reloading web app to reflect changes in "
					+ appRootDir.getAbsolutePath();
			TreeLogger branch = logger.branch(TreeLogger.INFO, msg);
			// Temporarily log Jetty on the branch.
			Log.setLog(new JettyTreeLogger(branch));
			try {
				wac.stop();
				wac.start();
				branch.log(TreeLogger.INFO, "Reload completed successfully");
			} catch (Exception e) {
				branch.log(TreeLogger.ERROR,
						"Unable to restart embedded Jetty server", e);
				throw new UnableToCompleteException();
			} finally {
				// Reset the top-level logger.
				Log.setLog(new JettyTreeLogger(logger));
			}
		}

		@Override
		public void stop() throws UnableToCompleteException {
			TreeLogger branch = logger.branch(TreeLogger.INFO,
					"Stopping Jetty server");
			// Temporarily log Jetty on the branch.
			Log.setLog(new JettyTreeLogger(branch));
			try {
				server.stop();
				server.setStopAtShutdown(false);
				branch.log(TreeLogger.TRACE, "Stopped successfully");
			} catch (Exception e) {
				branch.log(TreeLogger.ERROR,
						"Unable to stop embedded Jetty server", e);
				throw new UnableToCompleteException();
			} finally {
				// Reset the top-level logger.
				Log.setLog(new JettyTreeLogger(logger));
			}
		}
	}

	/**
	 * A {@link WebAppContext} tailored to GWT hosted mode. Features hot-reload
	 * with a new {@link WebAppClassLoader} to pick up disk changes. The default
	 * Jetty {@code WebAppContext} will create new instances of servlets, but it
	 * will not create a brand new {@link ClassLoader}. By creating a new
	 * {@code ClassLoader} each time, we re-read updated classes from disk.
	 * 
	 * Also provides special class filtering to isolate the web app from the GWT
	 * hosting environment.
	 */
	protected static final class WebAppContextWithReload extends WebAppContext {
		
		private static final org.slf4j.Logger consoleLog = LoggerFactory.getLogger(WebAppContext.class);

		/**
		 * Specialized {@link WebAppClassLoader} that allows outside resources
		 * to be brought in dynamically from the system path. A warning is
		 * issued when this occurs.
		 */
		private class WebAppClassLoaderExtension extends WebAppClassLoader {

			private static final String META_INF_SERVICES = "META-INF/services/";

			public WebAppClassLoaderExtension() throws IOException {
				super(bootStrapOnlyClassLoader, WebAppContextWithReload.this);
			}

			@Override
			public URL findResource(String name) {
				// Specifically for
				// META-INF/services/javax.xml.parsers.SAXParserFactory
				String checkName = name;
				if (checkName.startsWith(META_INF_SERVICES)) {
					checkName = checkName.substring(META_INF_SERVICES.length());
				}

				// For a system path, load from the outside world.
				URL found;
				if (isSystemPath(checkName)) {
					found = systemClassLoader.getResource(name);
					if (found != null) {
						return found;
					}
				}

				// Always check this ClassLoader first.
				found = super.findResource(name);
				if (found != null) {
					return found;
				}

				// See if the outside world has it.
				found = systemClassLoader.getResource(name);
				if (found == null) {
					return null;
				}

				// Warn, add containing URL to our own ClassLoader, and retry
				// the call.
				String warnMessage = "Server resource '"
						+ name
						+ "' could not be found in the web app, but was found on the system classpath";
				if (!addContainingClassPathEntry(warnMessage, found, name)) {
					return null;
				}
				return super.findResource(name);
			}

			/**
			 * Override to additionally consider the most commonly available JSP
			 * and XML implementation as system resources. (In fact, Jasper is
			 * in gwt-dev via embedded Tomcat, so we always hit this case.)
			 */
			public boolean isSystemPath(String name) {
				name = name.replace('/', '.');
				return !WebAppContextWithReload.this.isSystemClass(name)
						|| name.startsWith("org.eclipse.jetty.servlet.")
						//|| name.startsWith("javax.servlet.")
						|| name.startsWith("org.eclipse.jetty.server.")
						//|| name.startsWith("com.google.gwt.")
//						|| name.startsWith("javax.servlet.")
						|| name.startsWith("org.eclipse.jetty.plus.")
//						|| name.startsWith("org.eclipse.jetty.util.")
						|| name.startsWith("org.apache.jasper.")
						|| name.startsWith("org.apache.xerces.");
			}

			@Override
			protected Class<?> findClass(String name)
					throws ClassNotFoundException {
				// For system path, always prefer the outside world.
				if (isSystemPath(name)) {
					try {
						return systemClassLoader.loadClass(name);
					} catch (ClassNotFoundException e) {
					}
				}

				try {
					return super.findClass(name);
				} catch (ClassNotFoundException e) {
					// Don't allow server classes to be loaded from the outside.
					if (WebAppContextWithReload.this.isServerClass(name)) {
						throw e;
					}
				}

				// See if the outside world has a URL for it.
				String resourceName = name.replace('.', '/') + ".class";
				URL found = systemClassLoader.getResource(resourceName);
				if (found == null) {
					return null;
				}

				// Warn, add containing URL to our own ClassLoader, and retry
				// the call.
				String warnMessage = "Server class '"
						+ name
						+ "' could not be found in the web app, but was found on the system classpath";
				if (!addContainingClassPathEntry(warnMessage, found,
						resourceName)) {
					throw new ClassNotFoundException(name);
				}
				return super.findClass(name);
			}

			private boolean addContainingClassPathEntry(String warnMessage,
					URL resource, String resourceName) {
				TreeLogger.Type logLevel = (System
						.getProperty(PROPERTY_NOWARN_WEBAPP_CLASSPATH) == null) ? TreeLogger.WARN
						: TreeLogger.DEBUG;
				TreeLogger branch = logger.branch(logLevel, warnMessage);
				String classPathURL;
				String foundStr = resource.toExternalForm();
				if (resource.getProtocol().equals("file")) {
					assert foundStr.endsWith(resourceName);
					classPathURL = foundStr.substring(0, foundStr.length()
							- resourceName.length());
				} else if (resource.getProtocol().equals("jar")) {
					assert foundStr.startsWith("jar:");
					assert foundStr.endsWith("!/" + resourceName);
					classPathURL = foundStr.substring(4, foundStr.length()
							- (2 + resourceName.length()));
				} else {
					branch.log(TreeLogger.ERROR,
							"Found resouce but unrecognized URL format: '"
									+ foundStr + '\'');
					return false;
				}
				branch = branch.branch(logLevel, "Adding classpath entry '"
						+ classPathURL
						+ "' to the web app classpath for this session", null,
						new InstalledHelpInfo("webAppClassPath.html"));
				try {
					addClassPath(classPathURL);
					return true;
				} catch (IOException e) {
					branch.log(TreeLogger.ERROR, "Failed add container URL: '"
							+ classPathURL + '\'', e);
					return false;
				}
			}
		}

		/**
		 * Parent ClassLoader for the Jetty web app, which can only load JVM
		 * classes. We would just use <code>null</code> for the parent
		 * ClassLoader except this makes Jetty unhappy.
		 */
		private final ClassLoader bootStrapOnlyClassLoader = new ClassLoader(
				null) {
		};

		private final TreeLogger logger;

		/**
		 * In the usual case of launching {@link com.google.gwt.dev.DevMode},
		 * this will always by the system app ClassLoader.
		 */
		private final ClassLoader systemClassLoader = Thread.currentThread()
				.getContextClassLoader();

		private WebAppContextWithReload(TreeLogger logger, String webApp,
				String contextPath) {
			super(webApp, contextPath);
			consoleLog.info(String.format("logger=%s, webApp=%s, contextPath=%s", logger, webApp, contextPath));
			this.logger = logger;

			// Prevent file locking on Windows; pick up file changes.
			getInitParams().put(
					"org.eclipse.jetty.servlet.Default.useFileMappedBuffer",
					"false");

			// Since the parent class loader is bootstrap-only, prefer it first.
			setParentLoaderPriority(true);
		}

		@Override
		protected void doStart() throws Exception {
			setClassLoader(new WebAppClassLoaderExtension());
			super.doStart();
			consoleLog.info("Started Jetty version: {}", Server.getVersion());			
		}

		@Override
		protected void doStop() throws Exception {
			super.doStop();
			setClassLoader(null);
			consoleLog.info("Stopped Jetty instance!");
		}
	}

	@SuppressWarnings("rawtypes")
	private static final void listContext(javax.naming.Context ctx, String indent) {
	    try {
	        NamingEnumeration list = ctx.listBindings("");
	        while (list.hasMore()) {
	            Binding item = (Binding) list.next();
	            String className = item.getClassName();
	            String name = item.getName();
	            System.out.println(indent + className + " " + name);
	            Object o = item.getObject();
	            if (o instanceof javax.naming.Context) {
	            	listContext((javax.naming.Context) o, indent + " ");
	            }
	        }
	    } catch (NamingException ex) {
	        System.out.println(ex);
	    }
	}
	
	
	/**
	 * System property to suppress warnings about loading web app classes from
	 * the system classpath.
	 */
	private static final String PROPERTY_NOWARN_WEBAPP_CLASSPATH = "gwt.nowarn.webapp.classpath";

	static {
		// Suppress spammy Jetty log initialization.
		System.setProperty("org.mortbay.log.class", JettyNullLogger.class
				.getName());
		Log.getRootLogger();

		/*
		 * Make JDT the default Ant compiler so that JSP compilation just works
		 * out-of-the-box. If we don't set this, it's very, very difficult to
		 * make JSP compilation work.
		 */
		String antJavaC = System.getProperty("build.compiler",
				"org.eclipse.jdt.core.JDTCompilerAdapter");
		System.setProperty("build.compiler", antJavaC);
	}

	private final Object privateInstanceLock = new Object();

	// default value used if setBaseLogLevel isn't called
	private TreeLogger.Type baseLogLevel = TreeLogger.INFO;

	private String bindAddress = null;

	@Override
	public String getName() {
		return "Jetty";
	}

	/*
	 * TODO: This is a hack to pass the base log level to the SCL. We'll have to
	 * figure out a better way to do this for SCLs in general. Please do not
	 * depend on this method, as it is subject to change.
	 */
	public void setBaseRequestLogLevel(TreeLogger.Type baseLogLevel) {
		synchronized (privateInstanceLock) {
			this.baseLogLevel = baseLogLevel;
		}
	}

	@Override
	public void setBindAddress(String bindAddress) {
		this.bindAddress = bindAddress;
	}

	@Override
	public ServletContainer start(TreeLogger logger, int port, File appRootDir)
			throws Exception {
		TreeLogger branch = logger.branch(TreeLogger.TRACE,
				"Starting Jetty on port " + port, null);

		checkStartParams(branch, port, appRootDir);

		// Setup our branch logger during startup.
		Log.setLog(new JettyTreeLogger(branch));

		// Turn off XML validation.
		System.setProperty("org.mortbay.xml.XmlParser.Validating", "false");

		Server server = new Server();
		
		ServerConnector connector = getConnector(server);
		if (bindAddress != null) {
			connector.setHost(bindAddress.toString());
		}
		connector.setPort(port);

		// Don't share ports with an existing process.
		connector.setReuseAddress(false);

		// Linux keeps the port blocked after shutdown if we don't disable this.
		connector.setSoLingerTime(0);

		server.addConnector(connector);
		
		
		

		// Create a new web app in the war directory.
		WebAppContext wac = new WebAppContextWithReload(logger, appRootDir.getAbsolutePath(), "/");
		//wac.setDescriptor(appRootDir.getAbsolutePath() + "/WEB-INF/web.xml");
		wac.setConfigurationDiscovered(true);
		
		wac.setConfigurationClasses(__dftConfigurationClasses);
		
//		javax.naming.Context t = (javax.naming.Context)new javax.naming.InitialContext().lookup("java:comp");
//		listContext(t, "");

		RequestLogHandler logHandler = new RequestLogHandler();
		logHandler.setRequestLog(new JettyRequestLogger(logger,
				getBaseLogLevel()));
		logHandler.setHandler(wac);
		server.setHandler(logHandler);
		server.start();
		server.setStopAtShutdown(true);

		// Now that we're started, log to the top level logger.
		Log.setLog(new JettyTreeLogger(logger));

		return new JettyServletContainer(logger, server, wac, connector
				.getLocalPort(), appRootDir);
	}

	protected ServerConnector getConnector(Server server) {		
		return new ServerConnector(server);
	}

	private void checkStartParams(TreeLogger logger, int port, File appRootDir) {
		if (logger == null) {
			throw new NullPointerException("logger cannot be null");
		}

		if (port < 0 || port > 65535) {
			throw new IllegalArgumentException(
					"port must be either 0 (for auto) or less than 65536");
		}

		if (appRootDir == null) {
			throw new NullPointerException("app root direcotry cannot be null");
		}
	}

	/*
	 * TODO: This is a hack to pass the base log level to the SCL. We'll have to
	 * figure out a better way to do this for SCLs in general.
	 */
	private TreeLogger.Type getBaseLogLevel() {
		synchronized (privateInstanceLock) {
			return this.baseLogLevel;
		}
	}

	protected JettyServletContainer createServletContainer(TreeLogger logger,
			File appRootDir, Server server, WebAppContext wac, int localPort) {
		return new JettyServletContainer(logger, server, wac, localPort, appRootDir);
	}

}
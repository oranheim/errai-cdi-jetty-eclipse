/*
 * Copyright 2008 Google Inc.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.google.gwt.junit;

import com.google.gwt.core.ext.Linker;
import com.google.gwt.core.ext.TreeLogger;
import com.google.gwt.core.ext.TreeLogger.Type;
import com.google.gwt.core.ext.UnableToCompleteException;
import com.google.gwt.core.ext.linker.impl.StandardLinkerContext;
import com.google.gwt.core.ext.typeinfo.JClassType;
import com.google.gwt.core.ext.typeinfo.TypeOracle;
import com.google.gwt.dev.ArgProcessorBase;
import com.google.gwt.dev.Compiler;
import com.google.gwt.dev.DevMode;
import com.google.gwt.dev.cfg.BindingProperty;
import com.google.gwt.dev.cfg.ModuleDef;
import com.google.gwt.dev.cfg.Properties;
import com.google.gwt.dev.cfg.Property;
import com.google.gwt.dev.javac.CompilationState;
import com.google.gwt.dev.javac.CompilationUnit;
import com.google.gwt.dev.javac.CompilationProblemReporter;
import com.google.gwt.dev.shell.CheckForUpdates;
//import com.google.gwt.dev.shell.jetty.JettyLauncher;
import com.google.gwt.dev.util.arg.ArgHandlerDeployDir;
import com.google.gwt.dev.util.arg.ArgHandlerDisableAggressiveOptimization;
import com.google.gwt.dev.util.arg.ArgHandlerDisableCastChecking;
import com.google.gwt.dev.util.arg.ArgHandlerDisableClassMetadata;
import com.google.gwt.dev.util.arg.ArgHandlerDisableRunAsync;
import com.google.gwt.dev.util.arg.ArgHandlerDisableUpdateCheck;
import com.google.gwt.dev.util.arg.ArgHandlerDraftCompile;
import com.google.gwt.dev.util.arg.ArgHandlerEnableAssertions;
import com.google.gwt.dev.util.arg.ArgHandlerExtraDir;
import com.google.gwt.dev.util.arg.ArgHandlerGenDir;
import com.google.gwt.dev.util.arg.ArgHandlerLocalWorkers;
import com.google.gwt.dev.util.arg.ArgHandlerLogLevel;
import com.google.gwt.dev.util.arg.ArgHandlerMaxPermsPerPrecompile;
import com.google.gwt.dev.util.arg.ArgHandlerScriptStyle;
import com.google.gwt.dev.util.arg.ArgHandlerWarDir;
import com.google.gwt.dev.util.arg.ArgHandlerWorkDirOptional;
import com.google.gwt.junit.JUnitMessageQueue.ClientStatus;
import com.google.gwt.junit.client.GWTTestCase;
import com.google.gwt.junit.client.TimeoutException;
import com.google.gwt.junit.client.impl.JUnitHost.TestInfo;
import com.google.gwt.junit.client.impl.JUnitResult;
import com.google.gwt.util.tools.ArgHandlerFlag;
import com.google.gwt.util.tools.ArgHandlerInt;
import com.google.gwt.util.tools.ArgHandlerString;

import junit.framework.AssertionFailedError;
import junit.framework.TestCase;
import junit.framework.TestResult;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.webapp.WebAppContext;
import org.jboss.errai.cdi.server.gwt.JettyLauncher;
import org.jboss.errai.cdi.server.gwt.JettyLauncher.JettyServletContainer;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * This class is responsible for hosting JUnit test case execution. There are
 * three main pieces to the JUnit system.
 * 
 * <ul>
 * <li>Test environment</li>
 * <li>Client classes</li>
 * <li>Server classes</li>
 * </ul>
 * 
 * <p>
 * The test environment consists of this class and the non-translatable version
 * of {@link com.google.gwt.junit.client.GWTTestCase}. These two classes
 * integrate directly into the real JUnit test process.
 * </p>
 * 
 * <p>
 * The client classes consist of the translatable version of
 * {@link com.google.gwt.junit.client.GWTTestCase}, translatable JUnit classes,
 * and the user's own {@link com.google.gwt.junit.client.GWTTestCase}-derived
 * class. The client communicates to the server via RPC.
 * </p>
 * 
 * <p>
 * The server consists of {@link com.google.gwt.junit.server.JUnitHostImpl}, an
 * RPC servlet which communicates back to the test environment through a
 * {@link JUnitMessageQueue}, thus closing the loop.
 * </p>
 */
public class JUnitJettyEclipseShell extends DevMode {
	
	final private JUnitShell shell;
	
	WebAppContext wac;

	public boolean equals(Object arg0) {
		return shell.equals(arg0);
	}

	public Type getBaseLogLevelForUI() {
		return shell.getBaseLogLevelForUI();
	}

	public TreeLogger getTopLogger() {
		return shell.getTopLogger();
	}

	public String getModuleUrl(String moduleName) {
		return shell.getModuleUrl(moduleName);
	}

	public int hashCode() {
		return shell.hashCode();
	}

	public void onRestartServer(TreeLogger logger) {
		shell.onRestartServer(logger);
	}

	public void onDone() {
		shell.onDone();
	}

	public String toString() {
		return shell.toString();
	}

	private JUnitJettyEclipseShell() {
		shell = JUnitShell.getUnitTestShell();
	}

	
	private final class MyJettyLauncher extends JettyLauncher {

		/**
		 * Adds in special JUnit stuff.
		 */
		@Override
		protected JettyServletContainer createServletContainer(
				TreeLogger logger, File appRootDir, Server server,
				WebAppContext wac, int localPort) {
			// Don't bother shutting down cleanly.
			server.setStopAtShutdown(false);
			// Save off the Context so we can add our own servlets later.
			JUnitJettyEclipseShell.this.wac = wac;
			return super.createServletContainer(logger, appRootDir, server,
					wac, localPort);
		}

		/**
		 * Ignore DevMode's normal WEB-INF classloader rules and just allow the
		 * system classloader to dominate. This makes JUnitHostImpl live in the
		 * right classloader (mine).
		 */
		@SuppressWarnings("unchecked")
		// @Override
		protected WebAppContext createWebAppContext(TreeLogger logger,
				File appRootDir) {
			return new WebAppContext(appRootDir.getAbsolutePath(), "/") {
				{
					// Prevent file locking on Windows; pick up file changes.
					getInitParams()
							.put("org.mortbay.jetty.servlet.Default.useFileMappedBuffer",
									"false");

					// Prefer the parent class loader so that JUnit works.
					setParentLoaderPriority(true);
				}
			};
		}
	}	
	
}

/*******************************************************************************
 * Copyright (c) Contributors to the Eclipse Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0 
 *******************************************************************************/

package org.osgi.test.cases.configurator.junit;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.concurrent.TimeUnit;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.launch.Framework;
import org.osgi.framework.launch.FrameworkFactory;
import org.osgi.test.support.OSGiTestCase;
public class ConfiguratorInitTestCase extends OSGiTestCase {

	private static final String	STORAGEROOT			= "org.osgi.test.cases.configurator.storageroot";
	private static final String	FRAMEWORK_FACTORY	= "/META-INF/services/org.osgi.framework.launch.FrameworkFactory";

	// in these tests we launch a new Framework each time and check whether the
	// correct initial configurations are set

	public void testInitialConfigFile() throws Exception {
		String pid = "org.osgi.test.init.pid.file";

		// write init_config.json to file
		URL url = getContext().getBundle().getResource("init_config.json");
		InputStream is = url.openConnection().getInputStream();
		File f = getContext().getDataFile("init_config.json");
		try (FileOutputStream out = new FileOutputStream(f)) {
			byte[] buffer = new byte[1024];
			int bytesRead;
			while ((bytesRead = is.read(buffer)) != -1) {
				out.write(buffer, 0, bytesRead);
			}
		}

		// provide file uri as configurator.initial
		Map<String,String> launchConfig = getConfiguration(getName());
		String config = f.toURI().toString();
		launchConfig.put("configurator.initial", config);

		Framework framework = startFramework(launchConfig);

		// check configuration
		assertTrue("There should be a configuration with pid " + pid,
				hasConfig(framework, pid));

		framework.stop();
		framework.waitForStop(10000);
	}

	public void testInitialConfig() throws Exception {
		String pid = "org.osgi.test.init.pid1";
		Map<String,String> launchConfig = getConfiguration(getName());
		String config = "{\":configurator:resource-version\": 1,"
				+ "\":configurator:symbolic-name\": \"org.osgi.test.config.init-name_1\","
				+ "\":configurator:version\": \"1.2.3.qualifier-1\"," + "\"" + pid
				+ "\":{\"foo\": \"bar\"}}";
		launchConfig.put("configurator.initial", config);

		Framework framework = startFramework(launchConfig);

		// check configuration
		assertTrue("There should be a configuration with pid " + pid,
				hasConfig(framework, pid));

		framework.stop();
		framework.waitForStop(10000);
	}

	public void testInitialConfigRequiresVersion() throws Exception {
		String pid = "org.osgi.test.init.pid2";
		Map<String,String> launchConfig = getConfiguration(getName());
		String config = "{\":configurator:resource-version\": 1,"
				+ "\":configurator:symbolic-name\": \"org.osgi.test.config.init\","
				+ "\"" + pid + "\":{\"foo\": \"bar\"}}";
		launchConfig.put("configurator.initial", config);

		Framework framework = startFramework(launchConfig);

		// check configuration
		assertFalse("This init config has a missing version",
				hasConfig(framework, pid));

		framework.stop();
		framework.waitForStop(10000);
	}

	public void testInitialConfigRequiresSymbolicname() throws Exception {
		String pid = "org.osgi.test.init.pid3";
		Map<String,String> launchConfig = getConfiguration(getName());
		String config = "{\":configurator:resource-version\": 1,"
				+ "\":configurator:version\": \"1.0.0\","
				+ "\"" + pid + "\":{\"foo\": \"bar\"}}";
		launchConfig.put("configurator.initial", config);

		Framework framework = startFramework(launchConfig);

		// check configuration
		assertFalse("This init config has a missing symblicname",
				hasConfig(framework, pid));

		framework.stop();
		framework.waitForStop(10000);
	}

	public void testInitialConfigInvalidResourceVersionType() throws Exception {
		assertInitialConfigIgnored(":configurator:resource-version", "\"1\"");
	}

	public void testInitialConfigFractionalResourceVersion() throws Exception {
		assertInitialConfigIgnored(":configurator:resource-version", "1.5");
	}

	public void testInitialConfigInvalidSymbolicNameType() throws Exception {
		assertInitialConfigIgnored(":configurator:symbolic-name", "1");
	}

	public void testInitialConfigInvalidSymbolicNameSyntax() throws Exception {
		for (String symbolicName : new String[] {
				"", ".com.example", "com..example", "com.example.",
				"com/example", "com:example", "com example", "com.ex\u00e4mple"
		}) {
			assertInitialConfigIgnored(":configurator:symbolic-name",
					"\"" + symbolicName + "\"");
		}
	}

	public void testInitialConfigInvalidVersionType() throws Exception {
		assertInitialConfigIgnored(":configurator:version", "1");
	}

	public void testInitialConfigInvalidVersionSyntax() throws Exception {
		for (String version : new String[] {
				"", "+1", "1..2", "1.2.x", "1.2.3.", "1.2.3.bad qualifier",
				"1.2.3.4.5", "-1.2.3", " 1.2.3"
		}) {
			assertInitialConfigIgnored(":configurator:version",
					"\"" + version + "\"");
		}
	}

	private void assertInitialConfigIgnored(String key, String value)
			throws Exception {
		String pid = "org.osgi.test.init.invalid";
		String controlPid = "org.osgi.test.init.control";
		String metadata = "\":configurator:resource-version\": 1,"
				+ "\":configurator:symbolic-name\": \"org.osgi.test.config.init\","
				+ "\":configurator:version\": \"1.0.0\",";
		String config = "{" + metadata.replaceFirst(
				"\"" + key + "\": [^,]+,", "\"" + key + "\": " + value + ",")
				+ "\"" + pid + "\": {\"foo\": \"bar\"}}";
		String control = "{" + metadata + "\"" + controlPid
				+ "\": {\"foo\": \"bar\"}}";
		File invalidFile = getContext().getDataFile("initial-invalid.json");
		File controlFile = getContext().getDataFile("initial-valid.json");
		Files.write(invalidFile.toPath(), config.getBytes(StandardCharsets.UTF_8));
		Files.write(controlFile.toPath(), control.getBytes(StandardCharsets.UTF_8));
		Map<String,String> launchConfig = getConfiguration(getName());
		launchConfig.put("configurator.initial", invalidFile.toURI() + ","
				+ controlFile.toURI());

		Framework framework = startFramework(launchConfig);
		try {
			// A valid resource must be processed too: absence alone could mean
			// the Configurator has not started processing initial resources yet.
			assertTrue("The valid initial resource must be processed",
					hasConfig(framework, controlPid, 5000));
			assertFalse("The resource with " + key + " = " + value
					+ " must be ignored", hasConfig(framework, pid, 1000));
		} finally {
			framework.stop();
			framework.waitForStop(10000);
		}
	}

	private boolean hasConfig(Framework framework, String pid, long timeout)
			throws Exception {
		long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeout);
		do {
			if (hasConfig(framework, pid))
				return true;
			Thread.sleep(20);
		} while (System.nanoTime() < deadline);
		return false;
	}

	private boolean hasConfig(Framework framework, String pid)
			throws Exception {
		ServiceReference< ? >[] refs = framework.getBundleContext()
				.getAllServiceReferences(null,
						"(objectClass=org.osgi.service.cm.ConfigurationAdmin)");
		if (refs == null)
			return false;

		Object configAdmin = framework.getBundleContext().getService(refs[0]);
		try {
			Method listConfigs = configAdmin.getClass()
					.getMethod("listConfigurations", String.class);
			Object configs = listConfigs.invoke(configAdmin,
					"(" + Constants.SERVICE_PID + "=" + pid + ")");
			return configs != null;
		} finally {
			framework.getBundleContext().ungetService(refs[0]);
		}
	}

	private Framework startFramework(Map<String,String> configuration)
			throws Exception {
		// get factory
		String frameworkFactoryClassName = getFrameworkFactoryClassName();
		Class< ? > clazz = loadFrameworkClass(frameworkFactoryClassName);
		FrameworkFactory frameworkFactory = (FrameworkFactory) clazz
				.getConstructor()
				.newInstance();

		// create and start framework
		Framework framework = frameworkFactory.newFramework(configuration);
		try {
			framework.init();
			framework.start();

			// install bundles in framework
			installFramework(framework);
			return framework;
		} catch (Exception | Error e) {
			framework.stop();
			framework.waitForStop(10000);
			throw e;
		}
	}

	private String getFrameworkFactoryClassName() throws IOException {
		BundleContext context = getBundleContextWithoutFail();
		URL factoryService = context == null
				? this.getClass().getResource(FRAMEWORK_FACTORY)
				: context.getBundle(0).getEntry(FRAMEWORK_FACTORY);
		return getClassName(factoryService);

	}

	private Class< ? > loadFrameworkClass(String className)
			throws ClassNotFoundException {
		BundleContext context = getBundleContextWithoutFail();
		return context == null ? Class.forName(className)
				: getContext().getBundle(0).loadClass(className);
	}

	private String getClassName(URL factoryService) throws IOException {
		InputStream in = factoryService.openStream();
		BufferedReader br = null;
		try {
			br = new BufferedReader(new InputStreamReader(in));
			for (String line = br.readLine(); line != null; line = br
					.readLine()) {
				int pound = line.indexOf('#');
				if (pound >= 0)
					line = line.substring(0, pound);
				line.trim();
				if (!"".equals(line))
					return line;
			}
		} finally {
			try {
				if (br != null)
					br.close();
			} catch (IOException e) {
				// did our best; just ignore
			}
		}
		return null;
	}

	private Map<String,String> getConfiguration(String testName) {
		return getConfiguration(testName, true);
	}

	private Map<String,String> getConfiguration(String testName,
			boolean delete) {
		Map<String,String> configuration = new HashMap<>();
		if (testName != null)
			configuration.put(Constants.FRAMEWORK_STORAGE,
					getStorageArea(testName, delete).getAbsolutePath());
		return configuration;
	}

	private String getStorageAreaRoot() {
		String storageroot = getProperty(STORAGEROOT);
		assertNotNull("Must set property: " + STORAGEROOT, storageroot);
		return storageroot;
	}

	protected File getStorageArea(String testName, boolean delete) {
		File storageArea = new File(getStorageAreaRoot(), testName);
		if (delete) {
			assertTrue(
					"Could not clean up storage area: " + storageArea.getPath(),
					delete(storageArea));
			assertTrue("Could not create storage area directory: "
					+ storageArea.getPath(), storageArea.mkdirs());
		}
		return storageArea;
	}

	private boolean delete(File file) {
		if (file.exists()) {
			if (file.isDirectory()) {
				String list[] = file.list();
				if (list != null) {
					int len = list.length;
					for (int i = 0; i < len; i++)
						if (!delete(new File(file, list[i])))
							return false;
				}
			}

			return file.delete();
		}
		return (true);
	}

	private void installFramework(Framework f) throws Exception {
		List<Bundle> bundles = new LinkedList<>();
		StringTokenizer st = new StringTokenizer(getProperty(
				"org.osgi.test.cases.configurator.bundles", ""), ",");
		while (st.hasMoreTokens()) {
			String bundle = st.nextToken();
			Bundle b = f.getBundleContext().installBundle("file:" + bundle);
			bundles.add(b);
		}

		for (Iterator<Bundle> it = bundles.iterator(); it.hasNext();) {
			Bundle b = it.next();
			if (b.getHeaders().get(Constants.FRAGMENT_HOST) == null) {
				b.start();
			}
		}
	}

	private BundleContext getBundleContextWithoutFail() {
		try {
			if ("true".equals(getProperty("noframework")))
				return null;
			return getContext();
		} catch (Throwable t) {
			return null; // don't fail
		}
	}
}



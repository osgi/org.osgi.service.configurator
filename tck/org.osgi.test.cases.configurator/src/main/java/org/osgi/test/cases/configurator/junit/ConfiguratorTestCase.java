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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.assertj.core.api.Assertions.within;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.Iterator;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.cm.ConfigurationEvent;
import org.osgi.service.cm.ConfigurationListener;
import org.osgi.test.common.annotation.InjectBundleContext;
import org.osgi.test.common.annotation.InjectBundleInstaller;
import org.osgi.test.common.install.BundleInstaller;
import org.osgi.util.promise.Deferred;
import org.osgi.util.promise.Promise;
import org.osgi.util.tracker.ServiceTracker;
public class ConfiguratorTestCase {

	@InjectBundleContext
	BundleContext		context;

	@InjectBundleInstaller
	BundleInstaller		installer;
	private ConfigurationAdmin configAdmin;
	private ServiceTracker<ConfigurationAdmin,ConfigurationAdmin> configAdminTracker;

	@BeforeEach
	public void setUp() throws Exception {
		configAdminTracker = new ServiceTracker<>(
				context, ConfigurationAdmin.class, null);
		configAdminTracker.open();
		configAdmin = configAdminTracker.waitForService(5000);
	}

	@AfterEach
	public void tearDown() {
		configAdminTracker.close();
	}

	@Test
	public void testIgnoreComments() throws Exception {
		String pid = "org.osgi.test.pid1";
		Deferred<Configuration> deleted = new Deferred<>();
		Deferred<Configuration> updated = new Deferred<>();

		ServiceRegistration<ConfigurationListener> reg = registerConfigListener(
				pid, updated, deleted);
		try {
			assertThat(readConfig(pid)).as("Precondition, should not yet have the test config").isNull();

			Bundle tb1 = installer.installBundle("tb1.jar", false);
			assertThat(getTimeoutPromise(updated).isDone()).isFalse();
			tb1.start();

			Configuration cfg = getTimeoutPromise(updated).getValue();
			Dictionary<String,Object> props = cfg.getProperties();
			assertThat(props.get("foo")).isEqualTo("bar");
			assertThat(props.get("comment1")).isNull();
			assertThat(props.get("comment2")).isNull();
			assertThat(props.get("foo2")).isEqualTo("bar");

			assertThat(getTimeoutPromise(deleted).isDone()).isFalse();
			tb1.uninstall();
			assertThat(getTimeoutPromise(deleted).getValue().getPid()).isEqualTo(pid);
		} finally {
			reg.unregister();
		}
	}

	@Test
	public void testBasicConfigurationLifecycle() throws Exception {
		String pid = "org.osgi.test.pid1";
		Deferred<Configuration> deleted = new Deferred<>();
		Deferred<Configuration> updated = new Deferred<>();

		ServiceRegistration<ConfigurationListener> reg = registerConfigListener(
				pid, updated, deleted);
		try {
			assertThat(readConfig(pid)).as("Precondition, should not yet have the test config").isNull();

			Bundle tb1 = installer.installBundle("tb1.jar", false);
			assertThat(getTimeoutPromise(updated).isDone()).isFalse();
			tb1.start();

			Configuration cfg = getTimeoutPromise(updated).getValue();
			Dictionary<String,Object> props = cfg.getProperties();
			assertThat(props.get("foo")).isEqualTo("bar");

			assertThat(getTimeoutPromise(deleted).isDone()).isFalse();
			tb1.uninstall();
			assertThat(getTimeoutPromise(deleted).getValue().getPid()).isEqualTo(pid);
		} finally {
			reg.unregister();
		}
	}
	
	@Test
	public void testUpdateConfigBundle() throws Exception {
		String pid = "org.osgi.test.pid11";
		Deferred<Configuration> updated = new Deferred<>();
		Deferred<Configuration> updated2 = new Deferred<>();

		ServiceRegistration<ConfigurationListener> reg = registerConfigListener(
				pid, updated, null);
		ServiceRegistration<ConfigurationListener> reg2 = null;
		try {
			assertThat(readConfig(pid)).as("Precondition, should not yet have the test config").isNull();

			Bundle tb11 = installer.installBundle("tb11a.jar", false);
			assertThat(getTimeoutPromise(updated).isDone()).isFalse();
			tb11.start();

			Configuration cfg = getTimeoutPromise(updated).getValue();
			Dictionary<String,Object> props = cfg.getProperties();
			assertThat(props.get("taa")).isEqualTo("daa");
			assertThat(props.get("too")).isEqualTo("doo");

			reg2 = registerConfigListener(pid, updated2, null);
			tb11.update(context.getBundle().getEntry("tb11b.jar").openStream());
			Configuration cfg2 = getTimeoutPromise(updated2).getValue();
			Dictionary<String,Object> props2 = cfg2.getProperties();
			assertThat(props2.get("taa")).isEqualTo("daadaa");
			assertThat(props2.get("too")).isEqualTo("doo");

			tb11.uninstall();
		} finally {
			reg.unregister();
			if (reg2 != null)
				reg2.unregister();
		}
	}

	@Test
	public void testIgnoreOverwrite() throws Exception {
		String pid = "org.osgi.test.pid1";
		Deferred<Configuration> deleted = new Deferred<>();
		Deferred<Configuration> updated = new Deferred<>();

		Configuration cfg = configAdmin.getConfiguration(pid, "?");
		Dictionary<String,Object> props = new Hashtable<>();
		props.put("foo", "baz");
		cfg.update(props);

		ServiceRegistration<ConfigurationListener> reg = registerConfigListener(
				pid, updated, deleted);
		try {
			Bundle tb1 = installer.installBundle("tb1.jar", false);
			assertThat(getTimeoutPromise(updated).isDone()).isFalse();
			tb1.start();

			// should not be updated
			assertThat(getTimeoutPromise(updated).isDone()).isFalse();

			cfg = configAdmin.getConfiguration(pid, "?");
			props = cfg.getProperties();
			assertThat(props.get("foo")).isEqualTo("baz");

			// nothing should happen on uninstall either
			assertThat(getTimeoutPromise(deleted).isDone()).isFalse();
			tb1.uninstall();
			assertThat(getTimeoutPromise(deleted).isDone()).isFalse();
		} finally {
			reg.unregister();
			cfg.delete();
		}
	}

	@Test
	public void testForceOverwrite() throws Exception {
		String pid = "org.osgi.test.pid10";
		Deferred<Configuration> updated = new Deferred<>();

		ServiceRegistration<ConfigurationListener> reg1 = registerConfigListener(
				pid, updated, null);

		Configuration cfg = configAdmin.getConfiguration(pid, "?");
		Dictionary<String,Object> props = new Hashtable<>();
		props.put("foo", "baz");
		cfg.update(props);

		Configuration cfg2 = null;
		ServiceRegistration<ConfigurationListener> reg2 = null, reg3 = null;
		try {
			assertThat(getTimeoutPromise(updated).getValue().getProperties().get(
							"foo")).isEqualTo("baz");

			Deferred<Configuration> updated2 = new Deferred<>();
			reg2 = registerConfigListener(pid, updated2, null);

			Bundle tb10 = installer.installBundle("tb10.jar", false);
			tb10.start();
			cfg2 = getTimeoutPromise(updated2).getValue();

			props = cfg2.getProperties();
			assertThat(props.get("foo")).isEqualTo("yes!");

			tb10.uninstall();
		} finally {
			unregister(reg1);
			unregister(reg2);
			unregister(reg3);
		}
	}

	@Test
	public void testForceOverwrite2() throws Exception {
		String pid = "org.osgi.test.pid8";
		Deferred<Configuration> updated = new Deferred<>();

		ServiceRegistration<ConfigurationListener> reg = registerConfigListener(
				pid, updated, null);
		ServiceRegistration<ConfigurationListener> reg1 = null, reg2 = null,
				reg3 = null;
		try {
			Bundle tb7 = installer.installBundle("tb7.jar", false);
			tb7.start();

			Configuration cfg = getTimeoutPromise(updated).getValue();
			assertThat(cfg.getProperties().get("foo")).isEqualTo("test!");

			Deferred<Configuration> updated1 = new Deferred<>();
			reg1 = registerConfigListener(pid, updated1, null);

			// Make a manual update
			Dictionary<String,Object> props = cfg.getProperties();
			props.put("foo", "ooof");
			cfg.update(props);
			Configuration cfg1 = getTimeoutPromise(updated1).getValue();
			assertThat(cfg1.getProperties().get("foo")).isEqualTo("ooof");

			Deferred<Configuration> updated2 = new Deferred<>();
			reg2 = registerConfigListener(pid, updated2, null);

			Bundle tb8 = installer.installBundle("tb8.jar", false);
			tb8.start();
			Configuration cfg2 = getTimeoutPromise(updated2).getValue();
			assertThat(cfg2.getProperties().get("foo")).isEqualTo("tadaa!");

			Deferred<Configuration> updated3 = new Deferred<>();
			reg3 = registerConfigListener(pid, updated3, null);

			tb8.uninstall();

			Configuration cfg3 = getTimeoutPromise(updated3).getValue();
			assertThat(cfg3.getProperties().get("foo")).isEqualTo("test!");
			tb7.uninstall();
		} finally {
			unregister(reg);
			unregister(reg1);
			unregister(reg2);
			unregister(reg3);
		}
	}

	@Test
	public void testConfigNestingForce() throws Exception {
		String pid = "org.osgi.test.pid8";
		Deferred<Configuration> updated = new Deferred<>();

		ServiceRegistration<ConfigurationListener> reg = null;
		ServiceRegistration<ConfigurationListener> reg2 = null;
		ServiceRegistration<ConfigurationListener> reg3 = null;
		try {
			reg = registerConfigListener(pid, updated, null);
			Bundle tb7 = installer.installBundle("tb7.jar", false);
			tb7.start();

			Configuration cfg = getTimeoutPromise(updated).getValue();
			assertThat(cfg.getProperties().get("foo")).isEqualTo("test!");

			Deferred<Configuration> updated2 = new Deferred<>();
			reg2 = registerConfigListener(pid, updated2, null);
			Bundle tb8 = installer.installBundle("tb8.jar", false);
			tb8.start();
			Configuration cfg2 = getTimeoutPromise(updated2).getValue();
			assertThat(cfg2.getProperties().get("foo")).isEqualTo("tadaa!");
			tb8.uninstall();

			Deferred<Configuration> updated3 = new Deferred<>();
			reg3 = registerConfigListener(pid, updated3, null);
			Configuration cfg3 = getTimeoutPromise(updated3).getValue();
			assertThat(cfg3.getProperties().get("foo")).isEqualTo("test!");

			tb7.uninstall();
		} finally {
			unregister(reg);
			unregister(reg2);
			unregister(reg3);
		}
	}

	@Test
	public void testConfigNestingNoForce() throws Exception {
		String pid = "org.osgi.test.pid8";
		Deferred<Configuration> updated = new Deferred<>();

		ServiceRegistration<ConfigurationListener> reg = null;
		ServiceRegistration<ConfigurationListener> reg2 = null;
		ServiceRegistration<ConfigurationListener> reg3 = null;
		try {
			reg = registerConfigListener(pid, updated, null);
			Bundle tb7 = installer.installBundle("tb7.jar", false);
			tb7.start();

			Configuration cfg = getTimeoutPromise(updated).getValue();
			assertThat(cfg.getProperties().get("foo")).isEqualTo("test!");

			Deferred<Configuration> updated2 = new Deferred<>();
			reg2 = registerConfigListener(pid, updated2, null);
			Bundle tb9 = installer.installBundle("tb9.jar", false);
			tb9.start();
			Configuration cfg2 = getTimeoutPromise(updated2).getValue();
			assertThat(cfg2.getProperties().get("foo")).isEqualTo("dingdong");
			tb9.uninstall();

			Deferred<Configuration> updated3 = new Deferred<>();
			reg3 = registerConfigListener(pid, updated3, null);
			Configuration cfg3 = getTimeoutPromise(updated3).getValue();
			assertThat(cfg3.getProperties().get("foo")).isEqualTo("test!");

			tb7.uninstall();
		} finally {
			unregister(reg);
			unregister(reg2);
			unregister(reg3);
		}
	}

	@Test
	public void testIgnoreRemove() throws Exception {
		String pid = "org.osgi.test.pid1";
		Deferred<Configuration> deleted = new Deferred<>();
		Deferred<Configuration> updated = new Deferred<>();

		ServiceRegistration<ConfigurationListener> reg = registerConfigListener(
				pid, updated, deleted);
		try {
			assertThat(readConfig(pid)).as("Precondition, should not yet have the test config").isNull();

			Bundle tb1 = installer.installBundle("tb1.jar", false);
			assertThat(getTimeoutPromise(updated).isDone()).isFalse();
			tb1.start();

			Configuration cfg = getTimeoutPromise(updated).getValue();
			Dictionary<String,Object> props = cfg.getProperties();
			assertThat(props.get("foo")).isEqualTo("bar");

			// update configuration
			props.put("foo", "baz");
			cfg.update(props);

			// the configuration should not be deleted
			assertThat(getTimeoutPromise(deleted).isDone()).isFalse();
			tb1.uninstall();
			assertThat(getTimeoutPromise(deleted).isDone()).isFalse();

			cfg = readConfig(pid);
			props = cfg.getProperties();
			assertThat(props.get("foo")).isEqualTo("baz");

			// cleanup ourselves
			cfg.delete();
		} finally {
			reg.unregister();
		}
	}

	@Test
	public void testOverwrite() throws Exception {
		String pid = "org.osgi.test.pid1";
		Deferred<Configuration> deleted = new Deferred<>();
		Deferred<Configuration> updated = new Deferred<>();

		Configuration cfg = configAdmin.getConfiguration(pid, "?");
		Dictionary<String,Object> props = new Hashtable<>();
		props.put("foo", "baz");
		cfg.update(props);

		ServiceRegistration<ConfigurationListener> reg = registerConfigListener(
				pid, updated, deleted);
		try {
			Bundle tb6 = installer.installBundle("tb6.jar", false);
			assertThat(getTimeoutPromise(updated).isDone()).isFalse();
			tb6.start();

			cfg = getTimeoutPromise(updated).getValue();
			props = cfg.getProperties();
			assertThat(props.get("foo")).isEqualTo("bar");

			assertThat(getTimeoutPromise(deleted).isDone()).isFalse();
			tb6.uninstall();
			assertThat(getTimeoutPromise(deleted).getValue().getPid()).isEqualTo(pid);
		} finally {
			reg.unregister();
		}
	}

	@Test
	public void testOverwriteRemove() throws Exception {
		String pid = "org.osgi.test.pid1";
		Deferred<Configuration> deleted = new Deferred<>();
		Deferred<Configuration> updated = new Deferred<>();

		ServiceRegistration<ConfigurationListener> reg = registerConfigListener(
				pid, updated, deleted);
		try {
			assertThat(readConfig(pid)).as("Precondition, should not yet have the test config").isNull();

			Bundle tb6 = installer.installBundle("tb6.jar", false);
			assertThat(getTimeoutPromise(updated).isDone()).isFalse();
			tb6.start();

			Configuration cfg = getTimeoutPromise(updated).getValue();
			Dictionary<String,Object> props = cfg.getProperties();
			assertThat(props.get("foo")).isEqualTo("bar");

			// update configuration
			props.put("foo", "baz");
			cfg.update(props);

			// the configuration should still be deleted
			assertThat(getTimeoutPromise(deleted).isDone()).isFalse();
			tb6.uninstall();
			assertThat(getTimeoutPromise(deleted).getValue().getPid()).isEqualTo(pid);
		} finally {
			reg.unregister();
		}
	}

	@Test
	public void testNoRequirement() throws Exception {
		String pid = "org.osgi.test.pid1";

		try {
			assertThat(readConfig(pid)).as("Precondition, should not yet have the test config").isNull();

			Bundle tb5 = installer.installBundle("tb5.jar", false);
			tb5.start();

			assertThat(readConfig(pid)).as("This test bundle has no requirement (osgi.extender=osgi.configurator),"
							+ " hence no configuration should be done").isNull();

			tb5.uninstall();
		} finally {}
	}

	@Test
	public void testImplicitDatatypes() throws Exception {
		Deferred<Configuration> updated = new Deferred<>();

		ServiceRegistration<ConfigurationListener> reg = registerConfigListener(
				"org.osgi.test.pid2", updated, null);
		try {
			Bundle tb2 = installer.installBundle("tb2.jar", false);
			tb2.start();

			Configuration cfg = getTimeoutPromise(updated).getValue();
			Dictionary<String,Object> props = cfg.getProperties();
			assertThat(props.size()).isEqualTo(6);
			assertThat(props.get("bval")).isEqualTo(true);
			assertThat(props.get("ival")).isEqualTo(1234L);
			assertThat(props.get("dval")).isEqualTo(-2.718);
			assertThat(props.get("sval")).isEqualTo("bar");
			assertThat(((String) props.get("oval")).replaceAll("\\s", "")).isEqualTo("{\"a\":1,\"b\":\"2\",\"c\":{\"d\":true,\"e\":[999,1000]}}");

			assertThat(props.get(Constants.SERVICE_PID)).isEqualTo("org.osgi.test.pid2");

			tb2.uninstall();
		} finally {
			reg.unregister();
		}
	}

	@Test
	public void testExplicitDatatypes() throws Exception {
		Deferred<Configuration> updated = new Deferred<>();

		ServiceRegistration<ConfigurationListener> reg = registerConfigListener(
				"org.osgi.test.pid3a", updated, null);
		try {
			Bundle tb2 = installer.installBundle("tb2.jar", false);
			tb2.start();

			Configuration cfg = getTimeoutPromise(updated).getValue();
			Dictionary<String,Object> props = cfg.getProperties();
			assertThat(props.get("Ival")).isEqualTo(Integer.valueOf(1234));
			assertThat(props.get("Bval")).isEqualTo(Boolean.TRUE);
			assertThat(props.get("Cval")).isEqualTo('q');
			assertThat(props.get("Lval")).isEqualTo(Long.MAX_VALUE);
			assertThat(props.get("Sval")).isEqualTo("false");
			assertThat(props.get("Fval")).isEqualTo(-12.34f);
			assertThat(props.get("Dval")).isEqualTo(3.141592653589793);
			assertThat(props.get("ByteVal")).isEqualTo(Byte.valueOf("-128"));
			assertThat(props.get("ShortVal")).isEqualTo(Short.valueOf("16384"));

			tb2.uninstall();
		} finally {
			reg.unregister();
		}
	}

	@Test
	public void testArraysBasic() throws Exception {
		Deferred<Configuration> updated = new Deferred<>();

		ServiceRegistration<ConfigurationListener> reg = registerConfigListener(
				"org.osgi.test.pid4a", updated, null);
		try {
			Bundle tb2 = installer.installBundle("tb2.jar", false);
			tb2.start();

			Configuration cfg = getTimeoutPromise(updated).getValue();
			Dictionary<String,Object> props = cfg.getProperties();
			assertThat((Object[]) props.get("ba")).isEqualTo(new Boolean[] {
					true, true, false, true
			});
			assertThat((Object[]) props.get("la")).isEqualTo(new Long[] {
					Long.MAX_VALUE, Long.MIN_VALUE
			});
			assertThat((Object[]) props.get("da")).isEqualTo(new Double[] {
					-999.999
			});
			assertThat((Object[]) props.get("sa")).isEqualTo(new String[] {
					"one", "two", "three"
			});
			assertThat((Object[]) props.get("oa")).isEqualTo(new String[] {
					"{\"foo\":{\"yo\":\"ya\"}}", "{\"bar\":{\"to\":9182}}"
			});
			assertThat((Object[]) props.get("xa")).isEqualTo(new String[] {});

			tb2.uninstall();
		} finally {
			reg.unregister();
		}
	}

	@Test
	public void testArraysMixed() throws Exception {
		Deferred<Configuration> updated = new Deferred<>();
		Deferred<Configuration> deleted = new Deferred<>();
		ServiceRegistration<ConfigurationListener> reg = registerConfigListener(
				"org.osgi.test.pid4f", updated, deleted);
		Bundle tb2 = null;
		try {
			tb2 = installer.installBundle("tb2.jar", false);
			tb2.start();

			Dictionary<String,Object> props = getTimeoutPromise(updated)
					.getValue().getProperties();
			assertThat(props.get("mixed"))
					.as("Mixed JSON arrays must become String arrays")
					.isInstanceOf(String[].class);
			assertThat((String[]) props.get("mixed")).containsExactly("hello", "3");
			tb2.uninstall();
			getTimeoutPromise(deleted).getValue();
		} finally {
			try {
				if (tb2 != null && tb2.getState() != Bundle.UNINSTALLED)
					tb2.uninstall();
			} finally {
				reg.unregister();
			}
		}
	}

	@Test
	public void testArraysSpecificBoxed() throws Exception {
		Deferred<Configuration> updated = new Deferred<>();

		ServiceRegistration<ConfigurationListener> reg = registerConfigListener(
				"org.osgi.test.pid4b", updated, null);
		try {
			Bundle tb2 = installer.installBundle("tb2.jar", false);
			tb2.start();

			Configuration cfg = getTimeoutPromise(updated).getValue();
			Dictionary<String,Object> props = cfg.getProperties();
			assertThat((Object[]) props.get("ba")).isEqualTo(new Boolean[] {
					true, true, false, true
			});
			assertThat((Object[]) props.get("ca")).isEqualTo(new Character[] {
					'h', 'e', 'l', 'l', 'o'
			});
			assertThat((Object[]) props.get("da")).isEqualTo(new Double[] {
					-999.999
			});
			assertThat((Object[]) props.get("fa")).isEqualTo(new Float[] {
					-0.1f, 0f, 0.1f, 0f, -0.1f
			});
			assertThat((Object[]) props.get("ia")).isEqualTo(new Integer[] {
					-1, -2, -3
			});
			assertThat((Object[]) props.get("la")).isEqualTo(new Long[] {
					Long.MAX_VALUE, Long.MIN_VALUE
			});
			assertThat((Object[]) props.get("sa")).isEqualTo(new String[] {
					"one", "two", "three"
			});
			assertThat((Object[]) props.get("com.acme.ByteVal")).isEqualTo(new Byte[] {
					99
			});
			assertThat((Object[]) props.get("com.acme.ShortVal")).isEqualTo(new Short[] {
					32767, 32767
			});
			assertThat((Object[]) props.get("xa")).isEqualTo(new Integer[] {});

			tb2.uninstall();
		} finally {
			reg.unregister();
		}
	}

	@Test
	public void testCollectionsImplicit() throws Exception {
		Deferred<Configuration> updated = new Deferred<>();

		ServiceRegistration<ConfigurationListener> reg = registerConfigListener(
				"org.osgi.test.pid4d", updated, null);
		try {
			Bundle tb2 = installer.installBundle("tb2.jar", false);
			tb2.start();

			Configuration cfg = getTimeoutPromise(updated).getValue();
			Dictionary<String,Object> props = cfg.getProperties();
			assertCollectionEquals(Arrays.asList(true, true, false, true),
					props.get("bcg"));
			assertCollectionEquals(Arrays.asList(-0.1, 0.0, 0.1, 0.0, -0.1),
					props.get("dcg"));
			assertThat(((Collection<?>) props.get("ecg")).size()).isZero();
			assertCollectionEquals(
					Arrays.asList(Long.MAX_VALUE, Long.MIN_VALUE),
					props.get("lcg"));
			assertCollectionEquals(Arrays.asList("one", "two", "three"),
					props.get("scg"));

			tb2.uninstall();
		} finally {
			reg.unregister();
		}
	}

	@Test
	public void testCollectionsSpecific() throws Exception {
		Deferred<Configuration> updated = new Deferred<>();

		ServiceRegistration<ConfigurationListener> reg = registerConfigListener(
				"org.osgi.test.pid4e", updated, null);
		try {
			Bundle tb2 = installer.installBundle("tb2.jar", false);
			tb2.start();

			Configuration cfg = getTimeoutPromise(updated).getValue();
			Dictionary<String,Object> props = cfg.getProperties();
			assertCollectionEquals(Arrays.asList(true, true, false, true),
					props.get("bc"));
			assertCollectionEquals(Arrays.asList('h', 'e', 'l', 'l', 'o'),
					props.get("cc"));
			assertCollectionEquals(Arrays.asList(-999.999), props.get("dc"));
			assertCollectionEquals(Arrays.asList(-0.1f, 0f, 0.1f, 0f, -0.1f),
					props.get("fc"));
			assertCollectionEquals(Arrays.asList(-1, -2, -3), props.get("ic"));
			assertCollectionEquals(
					Arrays.asList(Long.MAX_VALUE, Long.MIN_VALUE),
					props.get("lc"));
			assertCollectionEquals(Arrays.asList("one", "two", "three"),
					props.get("sc"));
			assertCollectionEquals(Arrays.asList((byte) 99),
					props.get("com.acme.ByteVal"));
			assertCollectionEquals(Arrays.asList((short) 32766, (short) 32766),
					props.get("com.acme.ShortVal"));
			assertCollectionEquals(Collections.emptyList(), props.get("ec"));

			tb2.uninstall();
		} finally {
			reg.unregister();
		}
	}

	@Test
	public void testArraysSpecificPrimitive() throws Exception {
		Deferred<Configuration> updated = new Deferred<>();

		ServiceRegistration<ConfigurationListener> reg = registerConfigListener(
				"org.osgi.test.pid4c", updated, null);
		try {
			Bundle tb2 = installer.installBundle("tb2.jar", false);
			tb2.start();

			Configuration cfg = getTimeoutPromise(updated).getValue();
			Dictionary<String,Object> props = cfg.getProperties();
			assertArrayEquals(new boolean[] {
					true, true, false, true
			}, props.get("ba"));
			assertThat((char[]) props.get("ca")).isEqualTo(new char[] {
					'h', 'e', 'l', 'l', 'o'
			});
			assertThat((double[]) props.get("da")).containsExactly(new double[] {
					-999.999
			}, within(0.0000001));
			assertThat((float[]) props.get("fa")).containsExactly(new float[] {
					-0.1f, 0f, 0.1f, 0f, -0.1f
			}, within(0.0001f));
			assertThat((int[]) props.get("ia")).isEqualTo(new int[] {
					-1, -2, -3
			});
			assertThat((long[]) props.get("la")).isEqualTo(new long[] {
					Long.MAX_VALUE, Long.MIN_VALUE
			});
			assertThat((byte[]) props.get("com.acme.ByteVal")).isEqualTo(new byte[] {
					99
			});
			assertThat((short[]) props.get("com.acme.ShortVal")).isEqualTo(new short[] {
					32767, 32767
			});
			assertArrayEquals(new boolean[] {}, props.get("xa"));

			tb2.uninstall();
		} finally {
			reg.unregister();
		}
	}

	@Test
	public void testBinaries() throws Exception {
		Deferred<Configuration> updated = new Deferred<>();

		ServiceRegistration<ConfigurationListener> reg = registerConfigListener(
				"binarytest", updated, null);
		try {
			Bundle tb2 = installer.installBundle("tb2.jar", false);
			tb2.start();

			Configuration cfg = getTimeoutPromise(updated).getValue();
			Dictionary<String,Object> props = cfg.getProperties();
			Path path = Paths.get(props.get("binaryval").toString());
			assertThat(path.toFile()).isFile();
			assertThat(new String(Files.readAllBytes(path))).isEqualTo("Binary file1");

			String[] paths = (String[]) props.get("binaryarr");
			assertThat(paths).hasSize(2);
			Path path2 = Paths.get(paths[0]);
			assertThat(new String(Files.readAllBytes(path2))).isEqualTo("Binary file2");

			Path path3 = Paths.get(paths[1]);
			assertThat(new String(Files.readAllBytes(path3))).isEqualTo("Binary file3");

			tb2.uninstall();
		} finally {
			reg.unregister();
		}
	}

	@Test
	public void testFactoryConfigurations() throws Exception {
		Deferred<Configuration> updated1 = new Deferred<>();
		Deferred<Configuration> updated2 = new Deferred<>();

		ServiceRegistration<ConfigurationListener> reg1 = registerConfigListener(
				"org.acme.factory~instance1", updated1, null);

		ServiceRegistration<ConfigurationListener> reg2 = registerConfigListener(
				"org.acme.factory~instance2", updated2, null);
		try {
			Bundle tb3 = installer.installBundle("tb3.jar", false);
			tb3.start();

			Configuration cfg1 = getTimeoutPromise(updated1).getValue();
			Dictionary<String,Object> props1 = cfg1.getProperties();
			assertThat(props1.get(Constants.SERVICE_PID)).isEqualTo("org.acme.factory~instance1");
			assertThat(props1.get(ConfigurationAdmin.SERVICE_FACTORYPID)).isEqualTo("org.acme.factory");
			assertThat(props1.get("somekey")).isEqualTo("someval");

			Configuration cfg2 = getTimeoutPromise(updated2).getValue();
			Dictionary<String,Object> props2 = cfg2.getProperties();
			assertThat(props2.get(Constants.SERVICE_PID)).isEqualTo("org.acme.factory~instance2");
			assertThat(props2.get(ConfigurationAdmin.SERVICE_FACTORYPID)).isEqualTo("org.acme.factory");
			assertThat(props2.get("somekey")).isEqualTo("someval2");

			tb3.uninstall();
		} finally {
			reg1.unregister();
			reg2.unregister();
		}
	}

	@Test
	public void testRanking() throws Exception {
		Deferred<Configuration> updated1 = new Deferred<>();
		Deferred<Configuration> updated2 = new Deferred<>();

		ServiceRegistration<ConfigurationListener> reg1 = registerConfigListener(
				"pid1", updated1, null);

		ServiceRegistration<ConfigurationListener> reg2 = registerConfigListener(
				"pid2", updated2, null);
		try {
			Bundle tb4 = installer.installBundle("tb4.jar", false);
			tb4.start();

			Configuration cfg1 = getTimeoutPromise(updated1).getValue();
			Dictionary<String,Object> props1 = cfg1.getProperties();
			assertThat(props1.get("akey")).isEqualTo("winning");

			Configuration cfg2 = getTimeoutPromise(updated2).getValue();
			Dictionary<String,Object> props2 = cfg2.getProperties();
			assertThat(props2.get("akey")).isEqualTo("winning");

			tb4.uninstall();
		} finally {
			reg1.unregister();
			reg2.unregister();
		}
	}

	@Test
	public void testInvalidBundleResourceSymbolicName() throws Exception {
		assertBundleConfigIgnored("org.osgi.test.invalid.symbolicname");
	}

	@Test
	public void testInvalidBundleResourceVersion() throws Exception {
		assertBundleConfigIgnored("org.osgi.test.invalid.version");
	}

	private void assertBundleConfigIgnored(String pid) throws Exception {
		String controlPid = "org.osgi.test.metadata.control";
		Deferred<Configuration> updated = new Deferred<>();
		Deferred<Configuration> deleted = new Deferred<>();
		Deferred<Configuration> ignored = new Deferred<>();
		ServiceRegistration<ConfigurationListener> reg = registerConfigListener(
				controlPid, updated, deleted);
		ServiceRegistration<ConfigurationListener> ignoredReg = registerConfigListener(
				pid, ignored, null);
		Bundle tb12 = null;
		try {
			assertThat(readConfig(pid))
					.as("Precondition, should not yet have the test config").isNull();
			tb12 = installer.installBundle("tb12.jar", false);
			tb12.start();

			// Wait for a valid resource in the same bundle before checking
			// that the invalid resource was ignored.
			assertThat(getTimeoutPromise(updated).getValue()
					.getProperties().get("foo")).isEqualTo("bar");
			assertThat(readConfig(pid)).as("The invalid resource must be ignored")
					.isNull();
			tb12.uninstall();
			getTimeoutPromise(deleted).getValue();
			assertThat(ignored.getPromise().isDone())
					.as("The invalid resource must never create a configuration")
					.isFalse();
		} finally {
			try {
				if (tb12 != null && tb12.getState() != Bundle.UNINSTALLED)
					tb12.uninstall();
			} finally {
				reg.unregister();
				ignoredReg.unregister();
			}
		}
	}

	@Test
	public void testConfiguratorPolicyKeyWithTypeSuffix() throws Exception {
		String pid = "org.osgi.test.reserved.policy";
		Deferred<Configuration> updated = new Deferred<>();
		Deferred<Configuration> controlUpdated = new Deferred<>();
		Deferred<Configuration> controlDeleted = new Deferred<>();
		ServiceRegistration<ConfigurationListener> reg = registerConfigListener(
				pid, updated, null);
		ServiceRegistration<ConfigurationListener> controlReg = registerConfigListener(
				"org.osgi.test.reserved.control", controlUpdated, controlDeleted);
		Configuration cfg = configAdmin.getConfiguration(pid, "?");
		Bundle tb13 = null;
		try {
			Dictionary<String,Object> props = new Hashtable<>();
			props.put("foo", "manual");
			cfg.update(props);
			getTimeoutPromise(updated).getValue();
			long changeCount = cfg.getChangeCount();

			tb13 = installer.installBundle("tb13.jar", false);
			tb13.start();
			getTimeoutPromise(controlUpdated).getValue();

			// :configurator:policy:String is a distinct reserved key, not
			// a typed spelling of :configurator:policy. The default applies.
			Configuration current = readConfig(pid);
			assertThat(current).as("The manual configuration must be retained")
					.isNotNull();
			assertThat(current.getProperties().get("foo"))
					.as("A suffixed policy key must not force an overwrite")
					.isEqualTo("manual");
			assertThat(cfg.getChangeCount()).isEqualTo(changeCount);
			tb13.uninstall();
			getTimeoutPromise(controlDeleted).getValue();
			assertThat(readConfig(pid))
					.as("The manual configuration must be retained").isNotNull();
			assertThat(readConfig(pid).getProperties().get("foo"))
					.isEqualTo("manual");
		} finally {
			try {
				if (tb13 != null && tb13.getState() != Bundle.UNINSTALLED)
					tb13.uninstall();
			} finally {
				reg.unregister();
				controlReg.unregister();
				Configuration remaining = readConfig(pid);
				if (remaining != null)
					remaining.delete();
			}
		}
	}

	@Test
	public void testConfiguratorRankingKeyWithTypeSuffix() throws Exception {
		Deferred<Configuration> updated = new Deferred<>();
		Deferred<Configuration> deleted = new Deferred<>();
		ServiceRegistration<ConfigurationListener> reg = registerConfigListener(
				"org.osgi.test.reserved.ranking", updated, deleted);
		Bundle tb13 = null;
		try {
			tb13 = installer.installBundle("tb13.jar", false);
			tb13.start();
			Dictionary<String,Object> props = getTimeoutPromise(updated)
					.getValue().getProperties();

			// Only the exact :configurator:ranking key sets the ranking:
			// 15 wins over 10, regardless of the suffixed values 5 and 20.
			assertThat(props.get("foo")).isEqualTo("winning");
			assertThat(props.size())
					.as("Reserved properties must not reach Configuration Admin")
					.isEqualTo(2);
			assertThat(props.get(":configurator:ranking")).isNull();
			assertThat(props.get(":configurator:ranking:Integer")).isNull();
			tb13.uninstall();
			getTimeoutPromise(deleted).getValue();
		} finally {
			try {
				if (tb13 != null && tb13.getState() != Bundle.UNINSTALLED)
					tb13.uninstall();
			} finally {
				reg.unregister();
			}
		}
	}

	// For some reason Assert doesn't provide this one...
	private void assertArrayEquals(boolean[] expected, Object actual) {
		assertThat(actual instanceof boolean[]).isTrue();
		boolean[] a = (boolean[]) actual;
		assertThat(a.length).isEqualTo(expected.length);
		for (int i = 0; i < expected.length; i++) {
			assertThat(a[i]).isEqualTo(expected[i]);
		}
	}

	private void assertCollectionEquals(Collection< ? > expected,
			Object actual) {
		assertThat(actual).isInstanceOf(Collection.class);
		Collection< ? > ac = (Collection< ? >) actual;
		assertThat(ac.size()).isEqualTo(expected.size());

		for (Iterator< ? > ei = expected.iterator(), ea = ac.iterator(); ei
				.hasNext();) {
			assertThat(ea.next()).isEqualTo(ei.next());
		}
	}

	private ServiceRegistration<ConfigurationListener> registerConfigListener(
			final String pid,
			Deferred<Configuration> updated, Deferred<Configuration> deleted) {
		if (updated == null)
			updated = new Deferred<>();
		if (deleted == null)
			deleted = new Deferred<>();

		final Deferred<Configuration> fupdated = updated;
		final Deferred<Configuration> fdeleted = deleted;

		ConfigurationListener cl = new ConfigurationListener() {
			@Override
			public void configurationEvent(ConfigurationEvent event) {
				if (!pid.equals(event.getPid()))
					return;

				try {
					ConfigurationAdmin cm = context
							.getService(event.getReference());
					switch (event.getType()) {
						case ConfigurationEvent.CM_UPDATED :
							fupdated.resolve(
									cm.getConfiguration(event.getPid()));
							return;
						case ConfigurationEvent.CM_DELETED :
							fdeleted.resolve(
									cm.getConfiguration(event.getPid()));
							return;
					}
				} catch (IOException e) {
					fail("Unexpected test result", e);
				}
			}
		};
		return context.registerService(ConfigurationListener.class, cl,
				null);
	}

	private Configuration readConfig(String pid)
			throws IOException, InvalidSyntaxException {
		Configuration[] configs = configAdmin.listConfigurations(
						"(" + Constants.SERVICE_PID + "=" + pid + ")");
		if (configs == null)
			return null;
		return configs[0];
	}

	private <T> Promise<T> getTimeoutPromise(Deferred<T> def) {
		return def.getPromise().timeout(30000);
	}

	private void unregister(ServiceRegistration< ? > reg) {
		if (reg != null)
			reg.unregister();
	}
}



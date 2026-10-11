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

package org.osgi.test.cases.configurator.secure.junit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.Dictionary;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.cm.ConfigurationEvent;
import org.osgi.service.cm.ConfigurationListener;
import org.osgi.test.common.annotation.InjectBundleContext;
import org.osgi.test.common.annotation.InjectBundleInstaller;
import org.osgi.test.common.install.BundleInstaller;
import org.osgi.util.promise.Deferred;
import org.osgi.util.tracker.ServiceTracker;
public class ConfiguratorSecurityTestCase {

	@InjectBundleContext
	BundleContext		context;

	@InjectBundleInstaller
	BundleInstaller		installer;

	private ServiceTracker<ConfigurationAdmin,ConfigurationAdmin> configAdminTracker;

	@BeforeEach
	public void setUp() throws Exception {
		configAdminTracker = new ServiceTracker<>(
				context, ConfigurationAdmin.class, null);
		configAdminTracker.open();
	}

	@AfterEach
	public void tearDown() {
		configAdminTracker.close();
	}

	@Test
	public void testPermissionOk() throws Exception {
		checkAppliedFoo("tb1.jar", true);
	}

	@Test
	public void testPermissionDenied() throws Exception {
		checkAppliedFoo("tb2.jar", false);
	}

	private void checkAppliedFoo(String bundle, boolean permissionsOk)
			throws Exception {
		Deferred<Configuration> updated = new Deferred<>();

		ServiceRegistration<ConfigurationListener> reg = registerConfigListener(
				"org.osgi.test.pid", updated, null);
		try {
			Bundle tb = installer.installBundle(bundle, false);
			tb.start();

			Configuration cfg = waitForConfiguration(updated);
			if (permissionsOk) {
				assertThat(cfg).isNotNull();
				Dictionary<String,Object> props = cfg.getProperties();
				assertThat(props.get("foo")).isEqualTo("bar");
			} else {
				assertThat(cfg).isNull();
			}

			tb.uninstall();
		} finally {
			reg.unregister();
		}
	}

	private ServiceRegistration<ConfigurationListener> registerConfigListener(
			final String pid, Deferred<Configuration> updated,
			Deferred<Configuration> deleted) {
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

	private Configuration waitForConfiguration(Deferred<Configuration> def) {
		try {
			return def.getPromise().timeout(2000).getValue();
		} catch (InvocationTargetException | InterruptedException e) {
			return null;
		}
	}
}



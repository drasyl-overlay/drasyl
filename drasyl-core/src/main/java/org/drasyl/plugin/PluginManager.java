/*
 * Copyright (c) 2020.
 *
 * This file is part of drasyl.
 *
 *  drasyl is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Lesser General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  drasyl is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with drasyl.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.drasyl.plugin;

import org.drasyl.DrasylConfig;
import org.drasyl.identity.Identity;
import org.drasyl.pipeline.Pipeline;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

/**
 * The {@code PluginManager} notifies all enabled plugins about specific node events (like startup
 * or shutdown).
 */
public class PluginManager {
    private static final Logger LOG = LoggerFactory.getLogger(PluginManager.class);
    private final DrasylConfig config;
    private final Identity identity;
    private final Pipeline pipeline;

    public PluginManager(final DrasylConfig config,
                         final Identity identity,
                         final Pipeline pipeline) {
        this.config = config;
        this.identity = identity;
        this.pipeline = pipeline;
    }

    /**
     * This method is called first when the {@link org.drasyl.DrasylNode} is started.
     */
    public void beforeStart() {
        if (!config.getPlugins().isEmpty()) {
            LOG.debug("Execute onBeforeStart listeners for all plugins...");
            final PluginEnvironment environment = new PluginEnvironment(config, identity, pipeline);
            config.getPlugins().forEach(plugin -> plugin.onBeforeStart(environment));
            LOG.debug("All onBeforeStart listeners executed");
        }
    }

    /**
     * This method is called last when the {@link org.drasyl.DrasylNode} is started.
     */
    public void afterStart() {
        if (!config.getPlugins().isEmpty()) {
            LOG.debug("Execute onAfterStart listeners for all plugins...");
            final PluginEnvironment environment = new PluginEnvironment(config, identity, pipeline);
            config.getPlugins().forEach(plugin -> plugin.onAfterStart(environment));
            LOG.debug("All onAfterStart listeners executed");
        }
    }

    /**
     * This method get called first when the {@link org.drasyl.DrasylNode} is shut down.
     */
    public void beforeShutdown() {
        if (!config.getPlugins().isEmpty()) {
            LOG.debug("Execute onBeforeShutdown listeners for all plugins...");
            final PluginEnvironment environment = new PluginEnvironment(config, identity, pipeline);
            config.getPlugins().forEach(plugin -> plugin.onBeforeShutdown(environment));
            LOG.debug("All onBeforeShutdown listeners executed");
        }
    }

    /**
     * This method get called last when the {@link org.drasyl.DrasylNode} is shut down.
     */
    public void afterShutdown() {
        if (!config.getPlugins().isEmpty()) {
            LOG.debug("Execute onAfterShutdown listeners for all plugins...");
            final PluginEnvironment environment = new PluginEnvironment(config, identity, pipeline);
            config.getPlugins().forEach(plugin -> plugin.onAfterShutdown(environment));
            LOG.debug("All onAfterShutdown listeners executed");
        }
    }
}
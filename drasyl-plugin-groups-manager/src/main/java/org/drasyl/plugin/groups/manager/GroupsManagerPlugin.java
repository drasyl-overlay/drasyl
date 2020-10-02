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
 *  along with drasyl.  If not, see <http://www.gnu.org/licenses />.
 */
package org.drasyl.plugin.groups.manager;

import com.typesafe.config.Config;
import org.drasyl.plugin.groups.manager.data.Group;
import org.drasyl.plugin.groups.manager.database.DatabaseAdapter;
import org.drasyl.plugin.groups.manager.database.DatabaseAdapterManager;
import org.drasyl.plugin.groups.manager.database.DatabaseException;
import org.drasyl.plugins.DrasylPlugin;
import org.drasyl.plugins.PluginEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

import static java.util.Objects.requireNonNull;

/**
 * Starting point for the groups master plugin.
 */
public class GroupsManagerPlugin implements DrasylPlugin {
    public static final String GROUPS_MANAGER_HANDLER = "GROUPS_MANAGER_HANDLER";
    private static final Logger LOG = LoggerFactory.getLogger(GroupsManagerPlugin.class);
    private final GroupsManagerConfig config;
    private DatabaseAdapter database;

    GroupsManagerPlugin(final GroupsManagerConfig config,
                        final DatabaseAdapter database) {
        this.config = requireNonNull(config);
        this.database = database;
    }

    public GroupsManagerPlugin(final GroupsManagerConfig config) {
        this(config, null);
    }

    public GroupsManagerPlugin(final Config config) {
        this(new GroupsManagerConfig(config));
    }

    @Override
    public void onBeforeStart(final PluginEnvironment env) {
        try {
            // init database
            if (database == null) {
                database = DatabaseAdapterManager.initAdapter(config.getDatabaseUri());
            }

            for (final Map.Entry<String, Group> entry : config.getGroups().entrySet()) {
                final String name = entry.getKey();
                final Group group = entry.getValue();
                if (!database.addGroup(group)) {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Group '{}' already exists.", name);
                    }
                }
                else {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Group '{}' was added.", name);
                    }
                }
            }
        }
        catch (final DatabaseException e) {
            LOG.error("Database Error: ", e);
        }
    }

    @Override
    public void onAfterStart(final PluginEnvironment env) {
        env.getPipeline().addLast(GROUPS_MANAGER_HANDLER, new GroupsManagerHandler(database));

        LOG.debug("Groups Manager Plugin was started with options: {}", config);
    }

    @Override
    public void onBeforeShutdown(final PluginEnvironment env) {
        env.getPipeline().remove(GROUPS_MANAGER_HANDLER);
        try {
            database.close();
            database = null;
        }
        catch (final DatabaseException e) {
            if (LOG.isWarnEnabled()) {
                LOG.warn("Error occurred during closing the groups database: ", e);
            }
        }

        if (LOG.isDebugEnabled()) {
            LOG.debug("Groups Manager Plugin was stopped.");
        }
    }
}
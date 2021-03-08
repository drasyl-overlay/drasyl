/*
 * Copyright (c) 2020-2021.
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
package org.drasyl.plugin.groups.client;

import com.typesafe.config.Config;
import org.drasyl.plugin.DrasylPlugin;
import org.drasyl.plugin.PluginEnvironment;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

/**
 * The Groups Membership Client Plugin allows drasyl nodes to connect to membership managers at
 * startup to join groups. The manager can then inform the node about existing memberships as well
 * as leaving and joining nodes.
 */
public class GroupsClientPlugin implements DrasylPlugin {
    public static final String GROUPS_CLIENT_HANDLER = "GROUPS_CLIENT_HANDLER";
    private static final Logger LOG = LoggerFactory.getLogger(GroupsClientPlugin.class);
    private final GroupsClientConfig config;

    /**
     * This constructor is used by {@link org.drasyl.DrasylConfig} to initialize this plugin.
     *
     * @param config the plugin's portion of the configuration
     */
    public GroupsClientPlugin(final Config config) {
        this(new GroupsClientConfig(config));
    }

    public GroupsClientPlugin(final GroupsClientConfig config) {
        this.config = config;
    }

    @Override
    public void onBeforeStart(final PluginEnvironment environment) {
        LOG.debug("Start Groups Client Plugin with options: {}", config);

        environment.getPipeline().addLast(GROUPS_CLIENT_HANDLER, new GroupsClientHandler(config.getGroups()));
    }

    @Override
    public void onBeforeShutdown(final PluginEnvironment environment) {
        LOG.debug("Stop Groups Client Plugin.");

        environment.getPipeline().remove(GROUPS_CLIENT_HANDLER);
    }
}

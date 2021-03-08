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
package org.drasyl.plugin.groups.manager;

import com.typesafe.config.ConfigFactory;
import org.drasyl.pipeline.Pipeline;
import org.drasyl.plugin.PluginEnvironment;
import org.drasyl.plugin.groups.manager.data.Group;
import org.drasyl.plugin.groups.manager.database.DatabaseAdapter;
import org.drasyl.plugin.groups.manager.database.DatabaseException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.Map;

import static org.drasyl.plugin.groups.manager.GroupsManagerConfig.API_BIND_HOST;
import static org.drasyl.plugin.groups.manager.GroupsManagerConfig.API_BIND_PORT;
import static org.drasyl.plugin.groups.manager.GroupsManagerConfig.API_ENABLED;
import static org.drasyl.plugin.groups.manager.GroupsManagerConfig.DATABASE_URI;
import static org.drasyl.plugin.groups.manager.GroupsManagerConfig.GROUPS;
import static org.drasyl.plugin.groups.manager.GroupsManagerPlugin.GROUPS_MANAGER_HANDLER;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GroupsManagerPluginTest {
    @Mock
    private GroupsManagerConfig groupsManagerConfig;
    @Mock
    private Pipeline pipeline;
    @Mock
    private PluginEnvironment env;
    @Mock
    private DatabaseAdapter databaseAdapter;

    @Nested
    class OnBeforeStart {
        @Test
        void shouldInitDB() throws DatabaseException {
            final GroupsManagerPlugin plugin = new GroupsManagerPlugin(groupsManagerConfig, databaseAdapter);
            final Group group = Group.of("group", "secret", (byte) 0, Duration.ofSeconds(60));
            when(groupsManagerConfig.getGroups()).thenReturn(Map.of(group.getName(), group));
            when(env.getPipeline()).thenReturn(pipeline);

            plugin.onBeforeStart(env);

            verify(databaseAdapter).addGroup(group);
        }

        @Test
        void shouldAddHandlerToPipeline() {
            final GroupsManagerPlugin plugin = new GroupsManagerPlugin(new GroupsManagerConfig(ConfigFactory.parseMap(Map.of(
                    DATABASE_URI, "",
                    GROUPS, Map.of(),
                    API_ENABLED, false,
                    API_BIND_HOST, "0.0.0.0",
                    API_BIND_PORT, 8080))), databaseAdapter);
            when(env.getPipeline()).thenReturn(pipeline);

            plugin.onBeforeStart(env);

            verify(pipeline).addLast(eq(GROUPS_MANAGER_HANDLER), isA(GroupsManagerHandler.class));
        }
    }

    @Nested
    class OnBeforeShutdown {
        @Test
        void shouldRemoveHandlerFromPipeline() throws DatabaseException {
            final GroupsManagerPlugin plugin = new GroupsManagerPlugin(groupsManagerConfig, databaseAdapter);
            when(env.getPipeline()).thenReturn(pipeline);

            plugin.onBeforeShutdown(env);

            verify(pipeline).remove(GROUPS_MANAGER_HANDLER);
            verify(databaseAdapter).close();
        }
    }
}

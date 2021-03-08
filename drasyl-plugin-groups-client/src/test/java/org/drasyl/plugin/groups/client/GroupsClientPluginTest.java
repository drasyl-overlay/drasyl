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

import org.drasyl.DrasylConfig;
import org.drasyl.pipeline.Pipeline;
import org.drasyl.plugin.PluginEnvironment;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.drasyl.plugin.groups.client.GroupsClientPlugin.GROUPS_CLIENT_HANDLER;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GroupsClientPluginTest {
    @Mock
    private GroupsClientConfig groupsClientConfig;
    @Mock
    private Pipeline pipeline;
    @Mock
    private DrasylConfig config;
    @Mock
    private PluginEnvironment env;

    @Test
    void shouldAddHandlerToPipeline() {
        final GroupsClientPlugin plugin = new GroupsClientPlugin(groupsClientConfig);
        when(env.getPipeline()).thenReturn(pipeline);

        plugin.onBeforeStart(env);

        verify(pipeline).addLast(eq(GROUPS_CLIENT_HANDLER), isA(GroupsClientHandler.class));
    }

    @Test
    void shouldRemoveHandlerFromPipeline() {
        final GroupsClientPlugin plugin = new GroupsClientPlugin(groupsClientConfig);
        when(env.getPipeline()).thenReturn(pipeline);

        plugin.onBeforeShutdown(env);

        verify(pipeline).remove(GROUPS_CLIENT_HANDLER);
    }

    @Test
    void shouldDoNothingOnNotUsedEvents() {
        final GroupsClientPlugin plugin = new GroupsClientPlugin(groupsClientConfig);

        plugin.onAfterShutdown(env);

        verifyNoInteractions(pipeline);
        verifyNoInteractions(config);
        verifyNoInteractions(env);
        verifyNoInteractions(groupsClientConfig);
    }
}

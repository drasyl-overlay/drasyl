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
package org.drasyl.plugin.groups.manager;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.drasyl.plugin.groups.manager.data.Group;
import org.drasyl.plugin.groups.manager.database.DatabaseAdapter;
import org.drasyl.plugin.groups.manager.database.DatabaseException;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;
import spark.Request;
import spark.Response;
import spark.ResponseTransformer;
import spark.Service;

import java.io.IOException;
import java.net.InetAddress;
import java.time.Duration;
import java.util.Map;
import java.util.Set;

import static java.time.Duration.ofSeconds;
import static org.drasyl.util.JSONUtil.JACKSON_READER;
import static org.drasyl.util.JSONUtil.JACKSON_WRITER;
import static org.eclipse.jetty.http.HttpStatus.INTERNAL_SERVER_ERROR_500;
import static org.eclipse.jetty.http.HttpStatus.NOT_FOUND_404;
import static org.eclipse.jetty.http.HttpStatus.NO_CONTENT_204;
import static org.eclipse.jetty.http.HttpStatus.SEE_OTHER_303;
import static org.eclipse.jetty.http.HttpStatus.UNPROCESSABLE_ENTITY_422;

/**
 * This class starts a HTTP server with a REST API to manage groups and memberships.
 */
@SuppressWarnings({ "java:S1192" })
public class GroupsManagerApi {
    private static final Logger LOG = LoggerFactory.getLogger(GroupsManagerApi.class);
    private final InetAddress bindHost;
    private final int bindPort;
    private final DatabaseAdapter database;
    private final Service server;

    GroupsManagerApi(final InetAddress bindHost,
                     final int bindPort,
                     final DatabaseAdapter database,
                     final Service server) {
        this.bindHost = bindHost;
        this.bindPort = bindPort;
        this.database = database;
        this.server = server;
    }

    public GroupsManagerApi(final GroupsManagerConfig config, final DatabaseAdapter database) {
        this(
                config.getApiBindHost(),
                config.getApiBindPort(),
                database,
                Service.ignite().ipAddress(config.getApiBindHost().getHostAddress()).port(config.getApiBindPort())
        );
    }

    void start() {
        LOG.debug("Start Groups Manager API listening on http://{}:{}...", bindHost, bindPort);

        server.defaultResponseTransformer(new JsonTransformer());

        // serve swagger-ui and swagger.json as static files
        server.staticFiles.location("/public");

        // always set Content-Type to json
        server.before((request, response) -> response.type("application/json"));

        // add routes
        server.get("/groups", this::groupsIndex);
        server.post("/groups", this::groupsCreate);
        server.get("/groups/:name", this::groupsShow);
        server.put("/groups/:name", this::groupsUpdate);
        server.delete("/groups/:name", this::groupsDelete);
        server.get("/groups/:name/memberships", this::groupsMembershipsIndex);

        // 404 page
        server.notFound("Not Found");

        // exception handling
        server.exception(Exception.class, (e, request, response) -> {
            response.status(INTERNAL_SERVER_ERROR_500);
            try {
                response.body(JACKSON_WRITER.writeValueAsString(e.getClass().getSimpleName() + ": " + e.getMessage()));
            }
            catch (final JsonProcessingException e1) {
                LOG.error("Unable to return exception to client: ", e1);
            }
        });

        // start server
        server.init();
        server.awaitInitialization();

        LOG.debug("Groups Manager API started.");
    }

    void shutdown() {
        LOG.debug("Stop Groups Manager API listening on http://{}:{}...", bindHost, bindPort);
        server.stop();
        server.awaitStop();
        LOG.debug("Groups Manager API stopped.");
    }

    Set<Group> groupsIndex(final Request request,
                           final Response response) throws DatabaseException {
        return database.getGroups();
    }

    Object groupsCreate(final Request request,
                        final Response response) throws DatabaseException {
        try {
            final Group group = JACKSON_READER.readValue(request.body(), Group.class);

            final boolean created = database.addGroup(group);
            if (created) {
                response.redirect("/groups/" + group.getName(), SEE_OTHER_303);
                return null;
            }
            else {
                response.status(UNPROCESSABLE_ENTITY_422);
                return "Name already taken";
            }
        }
        catch (final IOException e) {
            response.status(UNPROCESSABLE_ENTITY_422);
            return "Unprocessable Entity: " + e.getMessage();
        }
    }

    Object groupsShow(final Request request,
                      final Response response) throws DatabaseException {
        final String name = request.params(":name");

        final Group group = database.getGroup(name);
        if (group == null) {
            response.status(NOT_FOUND_404);
            return "Not Found";
        }
        return group;
    }

    String groupsUpdate(final Request request,
                        final Response response) throws DatabaseException {
        final String name = request.params(":name");

        final Group group = database.getGroup(name);
        if (group == null) {
            response.status(NOT_FOUND_404);
            return "Not Found";
        }
        else {
            try {
                @SuppressWarnings("unchecked") final Map<String, Object> updateParams = JACKSON_READER.readValue(request.body(), Map.class);
                final String credentials = updateParams.getOrDefault("credentials", group.getCredentials()).toString();
                final byte minDifficulty = Byte.parseByte(updateParams.getOrDefault("minDifficulty", group.getMinDifficulty()).toString());
                final Duration timeout = ofSeconds(Long.parseLong(updateParams.getOrDefault("timeout", group.getTimeoutSeconds()).toString()));

                final Group newGroup = Group.of(name, credentials, minDifficulty, timeout);
                database.updateGroup(newGroup);

                response.redirect("/groups/" + group.getName(), SEE_OTHER_303);
                return null;
            }
            catch (final IOException | IllegalArgumentException e) {
                response.status(UNPROCESSABLE_ENTITY_422);
                return "Unprocessable Entity: " + e.getMessage();
            }
        }
    }

    String groupsDelete(final Request request,
                        final Response response) throws DatabaseException {
        final String name = request.params(":name");

        if (database.deleteGroup(name)) {
            response.status(NO_CONTENT_204);
            return "No Content";
        }
        else {
            response.status(NOT_FOUND_404);
            return "Not Found";
        }
    }

    Object groupsMembershipsIndex(final Request request,
                                  final Response response) throws DatabaseException {
        final String name = request.params(":name");

        final Group group = database.getGroup(name);
        if (group != null) {
            return database.getGroupMembers(name);
        }
        else {
            response.status(NOT_FOUND_404);
            return "Not Found";
        }
    }

    static class JsonTransformer implements ResponseTransformer {
        @Override
        public String render(final Object model) throws Exception {
            if (model != null) {
                return JACKSON_WRITER.writeValueAsString(model);
            }
            else {
                return "";
            }
        }
    }
}
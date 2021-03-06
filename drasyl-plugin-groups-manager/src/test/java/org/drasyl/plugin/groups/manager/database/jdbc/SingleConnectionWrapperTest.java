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
package org.drasyl.plugin.groups.manager.database.jdbc;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Savepoint;
import java.sql.ShardingKey;
import java.sql.Statement;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * This test should only raise the coverage of the corresponding class. It actually test nothing.
 */
@ExtendWith(MockitoExtension.class)
class SingleConnectionWrapperTest {
    @Mock
    Connection con;

    @SuppressWarnings("ThrowableNotThrown")
    @Test
    void coverage(@Mock final Savepoint savepoint,
                  @Mock final Properties properties,
                  @Mock final Executor executor,
                  @Mock final ShardingKey shardingKey) {
        final SingleConnectionWrapper underTest = new SingleConnectionWrapper(con);

        assertDoesNotThrow(() -> {
            underTest.createStatement();
            underTest.prepareStatement("");
            underTest.prepareCall("");
            underTest.nativeSQL("");
            underTest.unwrap(Class.class);
            underTest.isWrapperFor(Class.class);
            underTest.terminate();
            underTest.setAutoCommit(false);
            underTest.getAutoCommit();
            underTest.commit();
            underTest.rollback();
            underTest.close();
            underTest.isClosed();
            underTest.getMetaData();
            underTest.setReadOnly(true);
            underTest.isReadOnly();
            underTest.setCatalog("");
            underTest.getCatalog();
            underTest.setTransactionIsolation(Connection.TRANSACTION_NONE);
            underTest.getTransactionIsolation();
            underTest.getWarnings();
            underTest.clearWarnings();
            underTest.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
            underTest.prepareStatement("", ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
            underTest.prepareCall("", ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
            underTest.getTypeMap();
            underTest.setTypeMap(Map.of());
            underTest.setHoldability(0);
            underTest.getHoldability();
            underTest.setSavepoint();
            underTest.setSavepoint("");
            underTest.rollback(savepoint);
            underTest.releaseSavepoint(savepoint);
            underTest.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY, ResultSet.HOLD_CURSORS_OVER_COMMIT);
            underTest.prepareStatement("", ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY, ResultSet.HOLD_CURSORS_OVER_COMMIT);
            underTest.prepareCall("", ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY, ResultSet.HOLD_CURSORS_OVER_COMMIT);
            underTest.prepareStatement("", Statement.RETURN_GENERATED_KEYS);
            underTest.prepareStatement("", new int[]{});
            underTest.prepareStatement("", new String[]{});
            underTest.createClob();
            underTest.createBlob();
            underTest.createNClob();
            underTest.createSQLXML();
            underTest.isValid(0);
            underTest.setClientInfo("", "");
            underTest.setClientInfo(properties);
            underTest.getClientInfo();
            underTest.getClientInfo("");
            underTest.createArrayOf("", new Object[]{});
            underTest.createStruct("", new Object[]{});
            underTest.setSchema("");
            underTest.getSchema();
            underTest.abort(executor);
            underTest.setNetworkTimeout(executor, 0);
            underTest.getNetworkTimeout();
            underTest.beginRequest();
            underTest.endRequest();
            underTest.setShardingKeyIfValid(shardingKey, shardingKey, 0);
            underTest.setShardingKeyIfValid(shardingKey, 0);
            underTest.setShardingKey(shardingKey, shardingKey);
            underTest.setShardingKey(shardingKey);
        });
    }
}

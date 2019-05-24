/*
 * Copyright 2015 Kakao Corporation
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
 */

package com.kakao.hbase.snapshot;

import com.kakao.hbase.SnapshotArgs;
import com.kakao.hbase.TestBase;
import com.kakao.hbase.common.Args;
import com.kakao.hbase.common.util.AlertSender;
import com.kakao.hbase.common.util.AlertSenderTest;
import org.apache.hadoop.hbase.client.HBaseAdmin;
import org.apache.hadoop.hbase.protobuf.generated.HBaseProtos;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.Assert.assertEquals;

public class SnapshotTest extends TestBase {
    public SnapshotTest() {
        super(SnapshotTest.class);
    }

    @Test
    public void testAllTables() throws Exception {
        List<HBaseProtos.SnapshotDescription> snapshotDescriptions;

        // create tables
        createAdditionalTable(tableName + "2");
        createAdditionalTable(tableName + "3");

        // all tables, keep unlimited
        String[] argsParam = {"localhost", ".*", "--test"};
        SnapshotArgs args = new SnapshotArgs(argsParam);
        Snapshot app = new Snapshot(admin, args);

        // create snapshot 1
        app.run();
        snapshotDescriptions = listSnapshots(tableName + ".*");
        assertEquals(3, snapshotDescriptions.size());

        // create snapshot 2
        Thread.sleep(1000);
        app.run();
        snapshotDescriptions = listSnapshots(tableName + ".*");
        assertEquals(6, snapshotDescriptions.size());

        // create snapshot 3
        Thread.sleep(1000);
        app.run();
        snapshotDescriptions = listSnapshots(tableName + ".*");
        assertEquals(9, snapshotDescriptions.size());

        // create snapshot 3
        Thread.sleep(1000);
        app.run();
        snapshotDescriptions = listSnapshots(tableName + ".*");
        assertEquals(12, snapshotDescriptions.size());
    }

    @Test
    public void testDuplicatedName() throws Exception {
        List<HBaseProtos.SnapshotDescription> snapshotDescriptions;
        String[] argsParam;
        SnapshotArgs args;
        Snapshot app;

        argsParam = new String[]{"localhost", ".*", "--test"};
        args = new SnapshotArgs(argsParam);
        app = new Snapshot(admin, args);

        String snapshotName = app.getPrefix(tableName) + "test";
        // create snapshot first
        app.snapshot(null, tableName, snapshotName);
        snapshotDescriptions = listSnapshots(tableName + ".*");
        assertEquals(1, snapshotDescriptions.size());

        // create snapshot again
        app.snapshot(null, tableName, snapshotName);
        snapshotDescriptions = listSnapshots(tableName + ".*");
        assertEquals(1, snapshotDescriptions.size());
    }

    @Test
    public void testNotExistingTable() throws Exception {
        class SnapshotArgsTest extends SnapshotArgs {
            public SnapshotArgsTest(String[] args) throws IOException {
                super(args);
            }

            @Override
            public Set<String> tableSet(HBaseAdmin admin) throws IOException {
                Set<String> set = new TreeSet<>();
                set.add("INVALID_TABLE");
                return set;
            }
        }

        String[] argsParam;
        SnapshotArgs args;
        Snapshot app;

        argsParam = new String[]{"localhost", ".*",
                "--" + Args.OPTION_AFTER_FAILURE + "=" + AlertSenderTest.ALERT_SCRIPT};
        args = new SnapshotArgsTest(argsParam);
        app = new Snapshot(admin, args);
        app.run();
    }

    @Test
    public void testNamespace() throws Exception {
        List<HBaseProtos.SnapshotDescription> snapshotDescriptions;
        String[] argsParam;
        SnapshotArgs args;
        Snapshot app;
        String fullTableName = null;

        try {
            // create table with namespace
            fullTableName = TEST_NAMESPACE + ":" + TestBase.tableName;
            createTable(fullTableName);

            // table with namespace
            argsParam = new String[]{"localhost", fullTableName};
            args = new SnapshotArgs(argsParam);
            app = new Snapshot(admin, args);

            // create snapshot
            app.run();
            snapshotDescriptions = admin.listSnapshots(app.getPrefix(fullTableName) + ".*");
            assertEquals(1, snapshotDescriptions.size());
        } finally {
            dropTable(fullTableName);
        }
    }
}
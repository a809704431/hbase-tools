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

package com.kakao.hbase.manager.command;

import com.kakao.hbase.ManagerArgs;
import com.kakao.hbase.TestBase;
import com.kakao.hbase.common.Args;
import com.kakao.hbase.common.util.Util;
import org.apache.hadoop.hbase.HRegionInfo;
import org.apache.hadoop.hbase.ServerName;
import org.apache.hadoop.hbase.client.HTable;
import org.apache.hadoop.hbase.master.RegionPlan;
import org.junit.Assert;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.assertTrue;

public class BalanceTest extends TestBase {
    public BalanceTest() {
        super(BalanceTest.class);
    }


    @Test
    public void testBalanceDefault() throws Exception {
        splitTable("a".getBytes());

        NavigableMap<HRegionInfo, ServerName> regionLocations;
        List<Map.Entry<HRegionInfo, ServerName>> hRegionInfoList;

        try (HTable table = getTable(tableName)) {
            regionLocations = table.getRegionLocations();
            hRegionInfoList = new ArrayList<>(regionLocations.entrySet());
            Assert.assertEquals(2, regionLocations.size());
            Assert.assertEquals(hRegionInfoList.get(0).getValue(), hRegionInfoList.get(1).getValue());

            String[] argsParam = {"zookeeper", tableName, "default", "--force-proceed"};
            Args args = new ManagerArgs(argsParam);
            Assert.assertEquals("zookeeper", args.getZookeeperQuorum());
            Balance command = new Balance(admin, args);

            command.run();

            regionLocations = table.getRegionLocations();
            hRegionInfoList = new ArrayList<>(regionLocations.entrySet());
            Assert.assertEquals(2, hRegionInfoList.size());
        }
    }

    @Test
    public void testParseTableSet() throws Exception {
        String[] argsParam;
        Args args;
        Set<String> tableSet;

        createAdditionalTable(TestBase.tableName + "2");
        createAdditionalTable(TestBase.tableName + "22");
        createAdditionalTable(TestBase.tableName + "3");

        argsParam = new String[]{"zookeeper", ".*", "st", "--force-proceed", "--test"};
        args = new ManagerArgs(argsParam);
        tableSet = Util.parseTableSet(admin, args);
        for (String tableNameArg : tableSet) {
            assertTrue(tableNameArg.startsWith(tableName));
        }
        Assert.assertEquals(4, tableSet.size());

        argsParam = new String[]{"zookeeper", tableName, "st", "--force-proceed"};
        args = new ManagerArgs(argsParam);
        tableSet = Util.parseTableSet(admin, args);
        Assert.assertEquals(1, tableSet.size());

        argsParam = new String[]{"zookeeper", tableName + ".*", "st", "--force-proceed"};
        args = new ManagerArgs(argsParam);
        tableSet = Util.parseTableSet(admin, args);
        Assert.assertEquals(4, tableSet.size());

        argsParam = new String[]{"zookeeper", tableName + "2.*", "st", "--force-proceed"};
        args = new ManagerArgs(argsParam);
        tableSet = Util.parseTableSet(admin, args);
        Assert.assertEquals(2, tableSet.size());

        argsParam = new String[]{"zookeeper", tableName + "2.*," + tableName + "3.*", "st", "--force-proceed"};
        args = new ManagerArgs(argsParam);
        tableSet = Util.parseTableSet(admin, args);
        Assert.assertEquals(3, tableSet.size());
    }

    @Test
    public void testBalanceAllTables() throws Exception {
        List<ServerName> serverNameList;
        List<HRegionInfo> regionInfoList;

        // create tables
        String tableName2 = createAdditionalTable(TestBase.tableName + "2");
        String tableName3 = createAdditionalTable(TestBase.tableName + "3");

        // move all regions to rs1
        serverNameList = getServerNameList();
        ServerName rs1 = serverNameList.get(0);
        regionInfoList = getRegionInfoList(tableName);
        regionInfoList.addAll(getRegionInfoList(tableName2));
        regionInfoList.addAll(getRegionInfoList(tableName3));
        for (HRegionInfo hRegionInfo : regionInfoList) {
            move(hRegionInfo, rs1);
        }
        Assert.assertEquals(3, getRegionInfoList(rs1, tableName).size() + getRegionInfoList(rs1, tableName2).size() + getRegionInfoList(rs1, tableName3).size());

        String[] argsParam = {"zookeeper", ".*", "st", "--force-proceed", "--test"};
        Args args = new ManagerArgs(argsParam);
        Assert.assertEquals("zookeeper", args.getZookeeperQuorum());
        Balance command = new Balance(admin, args);

        command.run();

        // check regions balanced
        Assert.assertNotEquals(3, getRegionInfoList(rs1, tableName).size() + getRegionInfoList(rs1, tableName2).size() + getRegionInfoList(rs1, tableName3).size());
    }

    @Test
    public void testBalanceByFactor() throws Exception {
        splitTable("a".getBytes());
        splitTable("b".getBytes());
        splitTable("c".getBytes());

        NavigableMap<HRegionInfo, ServerName> regionLocations;
        List<Map.Entry<HRegionInfo, ServerName>> hRegionInfoList;

        try (HTable table = getTable(tableName)) {
            // set regions unbalanced
            regionLocations = table.getRegionLocations();
            hRegionInfoList = new ArrayList<>(regionLocations.entrySet());
            Assert.assertEquals(4, regionLocations.size());
            Assert.assertEquals(hRegionInfoList.get(0).getValue(), hRegionInfoList.get(1).getValue());
            Assert.assertEquals(hRegionInfoList.get(0).getValue(), hRegionInfoList.get(2).getValue());
            Assert.assertEquals(hRegionInfoList.get(0).getValue(), hRegionInfoList.get(3).getValue());

            String[] argsParam = {"zookeeper", tableName, "St", "--force-proceed", "--factor=ss"};
            Args args = new ManagerArgs(argsParam);
            Assert.assertEquals("zookeeper", args.getZookeeperQuorum());
            Balance command = new Balance(admin, args);

            // balance
            command.run();

            // assert
            regionLocations = table.getRegionLocations();
            hRegionInfoList = new ArrayList<>(regionLocations.entrySet());
            Map<ServerName, Integer> serverCountMap = new HashMap<>();
            for (Map.Entry<HRegionInfo, ServerName> entry : hRegionInfoList) {
                if (serverCountMap.get(entry.getValue()) == null) {
                    serverCountMap.put(entry.getValue(), 1);
                } else {
                    serverCountMap.put(entry.getValue(), serverCountMap.get(entry.getValue()) + 1);
                }
            }
            int regionCount = 0;
            for (ServerName serverName : getServerNameList()) {
                List<HRegionInfo> regionInfoList = getRegionInfoList(serverName, tableName);
                Assert.assertNotEquals(4, regionInfoList);
                regionCount += regionInfoList.size();
            }
            Assert.assertEquals(4, regionCount);
        }
    }

    @Test
    public void testBalanceAsync() throws Exception {
        splitTable("a".getBytes());
        splitTable("b".getBytes());
        splitTable("c".getBytes());

        NavigableMap<HRegionInfo, ServerName> regionLocations;
        List<Map.Entry<HRegionInfo, ServerName>> hRegionInfoList;

        try (HTable table = getTable(tableName)) {
            regionLocations = table.getRegionLocations();
            hRegionInfoList = new ArrayList<>(regionLocations.entrySet());
            Assert.assertEquals(4, regionLocations.size());
            Assert.assertEquals(hRegionInfoList.get(0).getValue(), hRegionInfoList.get(1).getValue());
            Assert.assertEquals(hRegionInfoList.get(0).getValue(), hRegionInfoList.get(2).getValue());
            Assert.assertEquals(hRegionInfoList.get(0).getValue(), hRegionInfoList.get(3).getValue());

            String[] argsParam = {"zookeeper", tableName, "rr", "--force-proceed", "--move-async"};
            Args args = new ManagerArgs(argsParam);
            Assert.assertEquals("zookeeper", args.getZookeeperQuorum());
            Balance command = new Balance(admin, args);

            command.run();

            regionLocations = table.getRegionLocations();
            hRegionInfoList = new ArrayList<>(regionLocations.entrySet());
            Assert.assertNotEquals(hRegionInfoList.get(0).getValue(), hRegionInfoList.get(1).getValue());
            Assert.assertNotEquals(hRegionInfoList.get(2).getValue(), hRegionInfoList.get(3).getValue());
        }
    }
}
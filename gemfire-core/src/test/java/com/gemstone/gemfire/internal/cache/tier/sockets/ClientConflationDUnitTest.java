/*=========================================================================
 * Copyright (c) 2010-2014 Pivotal Software, Inc. All Rights Reserved.
 * This product is protected by U.S. and international copyright
 * and intellectual property laws. Pivotal products are covered by
 * one or more patents listed at http://www.pivotal.io/patents.
 *=========================================================================
 */
package com.gemstone.gemfire.internal.cache.tier.sockets;

import java.util.Iterator;
import java.util.Properties;

import com.gemstone.gemfire.cache.AttributesFactory;
import com.gemstone.gemfire.cache.Cache;
import com.gemstone.gemfire.cache.CacheFactory;
import com.gemstone.gemfire.cache.CacheWriterException;
import com.gemstone.gemfire.cache.EntryEvent;
import com.gemstone.gemfire.cache.Region;
import com.gemstone.gemfire.cache.RegionAttributes;
import com.gemstone.gemfire.cache.Scope;
import com.gemstone.gemfire.cache.client.Pool;
import com.gemstone.gemfire.cache.client.PoolFactory;
import com.gemstone.gemfire.cache.client.PoolManager;
import com.gemstone.gemfire.cache.client.internal.PoolImpl;
import com.gemstone.gemfire.cache.util.BridgeServer;
import com.gemstone.gemfire.cache.util.CacheListenerAdapter;
import com.gemstone.gemfire.distributed.DistributedSystem;
import com.gemstone.gemfire.distributed.internal.DistributionConfig;
import com.gemstone.gemfire.distributed.internal.InternalDistributedSystem;
import com.gemstone.gemfire.internal.AvailablePort;
import com.gemstone.gemfire.internal.cache.BridgeObserverAdapter;
import com.gemstone.gemfire.internal.cache.BridgeObserverHolder;
import com.gemstone.gemfire.internal.cache.BridgeServerImpl;

import dunit.DistributedTestCase;
import dunit.Host;
import dunit.VM;

/**
 * This test verifies the per-client queue conflation override functionality
 * Taken from the existing ConflationDUnitTest.java and modified.
 *
 * @author Vishal Rao
 * @since 5.7
 */
public class ClientConflationDUnitTest extends DistributedTestCase
{
  VM vm0 = null; // server
  VM vm1 = null; // client
  private static Cache cacheClient = null;
  private static Cache cacheFeeder = null;
  private static Cache cacheServer = null;
  private static int PORT ;
  private static int poolNameCounter = 0;
  private static final String REGION_NAME1 = "ClientConflationDUnitTest_region1" ;
  private static final String REGION_NAME2 = "ClientConflationDUnitTest_region2" ;

  /** constructor */
  public ClientConflationDUnitTest(String name) {
    super(name);
  }

  public void setUp() throws Exception {
    super.setUp();
    disconnectAllFromDS();

    final Host host = Host.getHost(0);
    vm0 = host.getVM(0);
    vm1 = host.getVM(1);
    setIsSlowStart();
    vm0.invoke(ClientConflationDUnitTest.class, "setIsSlowStart");
    PORT =  ((Integer)vm0.invoke(ClientConflationDUnitTest.class, "createServerCache" )).intValue();
  }

  private Cache createCache(Properties props) throws Exception
  {
    DistributedSystem ds = getSystem(props);
    Cache cache = CacheFactory.create(ds);
    if (cache == null) {
      throw new Exception("CacheFactory.create() returned null ");
    }
    return cache;
  }

  /**
   * set the boolean for starting the dispatcher thread a bit later.
   *
   */
  public static void setIsSlowStart()
  {
    CacheClientProxy.isSlowStartForTesting = true;
    System.setProperty("slowStartTimeForTesting","15000");
  }

  public void testConflationDefault() {
    try {
      performSteps(DistributionConfig.CLIENT_CONFLATION_PROP_VALUE_DEFAULT);
    }
    catch( Exception e ) {
      fail("testConflationDefault failed due to exception", e);
    }
  }
  
  public void testConflationOn() {
    try {
      performSteps(DistributionConfig.CLIENT_CONFLATION_PROP_VALUE_ON);
    }
    catch( Exception e ) {
      fail("testConflationOn failed due to exception", e);
    }
  }
  
  public void testConflationOff() {
    try {
      performSteps(DistributionConfig.CLIENT_CONFLATION_PROP_VALUE_OFF);
    }
    catch( Exception e ) {
      fail("testConflationOff failed due to exception", e);
    }
  }
  
  private void performSteps(String conflation) throws Exception {
    createClientCacheFeeder(getServerHostName(Host.getHost(0)), new Integer(PORT));
    vm1.invoke(ClientConflationDUnitTest.class, "createClientCache", new Object[] { getServerHostName(vm1.getHost()), new Integer(PORT),
      conflation});
    vm1.invoke(ClientConflationDUnitTest.class, "setBridgeObserverForBeforeInterestRecovery");
    vm1.invoke(ClientConflationDUnitTest.class, "setAllCountersZero");
    vm1.invoke(ClientConflationDUnitTest.class, "assertAllCountersZero");
    vm1.invoke(ClientConflationDUnitTest.class, "registerInterest");
    putEntries();
    vm0.invoke(ConflationDUnitTest.class, "unsetIsSlowStart");
    Thread.sleep(20000);
    vm0.invoke(ClientConflationDUnitTest.class, "assertAllQueuesEmpty");
    
    vm1.invoke(ClientConflationDUnitTest.class, "assertCounterSizes", new Object[] {conflation});
    vm1.invoke(ClientConflationDUnitTest.class, "assertValue");
  }
  
  /**
   * create properties for a loner VM
   */
  private static Properties createProperties1(String conflation){
    Properties props = new Properties();
    props.setProperty(DistributionConfig.DELTA_PROPAGATION_PROP_NAME, "false");
    props.setProperty(DistributionConfig.MCAST_PORT_NAME, "0");
    props.setProperty(DistributionConfig.LOCATORS_NAME, "");
    props.setProperty(DistributionConfig.CLIENT_CONFLATION_PROP_NAME, conflation);
    return props;
  }

  
  private static void createPool2(String host, AttributesFactory factory,Integer port) {
    PoolFactory pf = PoolManager.createFactory();
    pf.addServer(host,port.intValue())
    .setSubscriptionEnabled(true)
    .setThreadLocalConnections(true)
    .setReadTimeout(10000)
    .setSocketBufferSize(32768)
    .setPingInterval(1000)
    .setMinConnections(3)
    .setSubscriptionRedundancy(-1)
    ;
    Pool pool = pf.create("superpoolish" + (poolNameCounter++));
    factory.setPoolName(pool.getName());
  }

  /**
   * create client 2 with 2 regions each with a unique writer
   * and unique listeners
   * @throws Exception
   */
  public static void createClientCache(String host, Integer port, String conflation) throws Exception
  {
    ClientConflationDUnitTest test = new ClientConflationDUnitTest("temp");
    cacheClient = test.createCache(createProperties1(conflation));
    AttributesFactory factory = new AttributesFactory();
    factory.setScope(Scope.LOCAL);
    createPool2(host, factory, port);
    factory.setCacheListener(new CacheListenerAdapter() {
      public void afterCreate(EntryEvent event)
      {
        synchronized (ClientConflationDUnitTest.class) {
          counterCreate1++;
        }
      }

      public void afterUpdate(EntryEvent event)
      {
//        getLogWriter().info("afterUpdate event = " + event, new Exception());
        synchronized (this) {
          counterUpdate1++;
        }
      }
    });
    RegionAttributes attrs = factory.create();
    cacheClient.createRegion(REGION_NAME1, attrs);
    createPool2(host, factory, port);
    factory.setCacheListener(new CacheListenerAdapter() {
      public void afterCreate(EntryEvent event)
      {
        synchronized (ClientConflationDUnitTest.class) {
          counterCreate2++;
        }
      }

      public void afterUpdate(EntryEvent event)
      {
        synchronized (this) {
          counterUpdate2++;
        }
      }
    });    
    attrs = factory.create();
    cacheClient.createRegion(REGION_NAME2, attrs);
  }

  public static void createClientCacheFeeder(String host, Integer port) throws Exception
  {
    ClientConflationDUnitTest test = new ClientConflationDUnitTest("temp");
    cacheFeeder = test.createCache(createProperties1(DistributionConfig.CLIENT_CONFLATION_PROP_VALUE_DEFAULT));
    AttributesFactory factory = new AttributesFactory();
    factory.setScope(Scope.LOCAL);
    createPool2(host, factory, port);
    RegionAttributes attrs = factory.create();
    cacheFeeder.createRegion(REGION_NAME1, attrs);
    attrs = factory.create();
    cacheFeeder.createRegion(REGION_NAME2, attrs);
  }
  
  /**
   * variables to count operations (messages received on client from server)
   */
  
  // For first region with server conflation setting on
  volatile  static int counterCreate1 = 0;
  volatile static int counterUpdate1 = 0;

  //For first region with server conflation setting off
  volatile  static int counterCreate2 = 0;
  volatile static int counterUpdate2 = 0;
  
  /**
   * assert all the counters are zero
   *
   */
  public static void assertAllCountersZero()
  {
    assertEquals(counterCreate1, 0);
    assertEquals(counterUpdate1, 0);
    assertEquals(counterCreate2, 0);
    assertEquals(counterUpdate2, 0);
  }

  /**
   * set all the counters to zero
   *
   */
  public static void setAllCountersZero()
  {
    counterCreate1 = 0;
    counterUpdate1 = 0;
    counterCreate2 = 0;
    counterUpdate2 = 0;
  }

  /**
   * reset all counters to zero before interest recovery
   *
   */
  public static void setBridgeObserverForBeforeInterestRecovery()
  {
    PoolImpl.BEFORE_RECOVER_INTEREST_CALLBACK_FLAG = true;
    BridgeObserverHolder.setInstance(new BridgeObserverAdapter() {
      public void beforeInterestRecovery()
      {
        setAllCountersZero();
      }
    });
  }

  /**
   * Assert all queues are empty to aid later assertion for listener event counts.
   */
  public static void assertAllQueuesEmpty() {
    Iterator servers = cacheServer.getBridgeServers().iterator();
    while (servers.hasNext()) {
      Iterator proxies = ((BridgeServerImpl)servers.next()).getAcceptor().
        getCacheClientNotifier().getClientProxies().iterator();
      while (proxies.hasNext()) {
        int qsize = ((CacheClientProxy)proxies.next()).getQueueSize();
        assertTrue("Queue size expected to be zero but is " + qsize, qsize == 0);
      }
    }
  }
  
  /**
   * assert the listener counters size are as expected
   *
   */
  public static void assertCounterSizes(String conflation)
  {
    // we do 5 puts on each key, so:
    
    // for writer 1 default conflation is on 
    final int create1 = 1;
    int update1 = 1;
    // for writer 2 default conflation is off
    final int create2 = 1;
    int update2 = 4;
    
    if (conflation.equals(DistributionConfig.CLIENT_CONFLATION_PROP_VALUE_ON)) {
      // override
      update2 = 1;
    }
    else if (conflation.equals(DistributionConfig.CLIENT_CONFLATION_PROP_VALUE_OFF)) {
      // override
      update1 = 4;
    }

    WaitCriterion ev = new WaitCriterion() {
      public boolean done() {
        Thread.yield(); // TODO is this necessary?
        return counterCreate1 == create1;
      }
      public String description() {
        return null;
      }
    };
    DistributedTestCase.waitForCriterion(ev, 60 * 1000, 200, true);
    
    final int u1 = update1;
    ev = new WaitCriterion() {
      public boolean done() {
        Thread.yield(); // TODO is this necessary?
        return counterUpdate1 == u1;
      }
      public String description() {
        return null;
      }
    };
    DistributedTestCase.waitForCriterion(ev, 60 * 1000, 200, true);
    
    ev = new WaitCriterion() {
      public boolean done() {
        Thread.yield(); // TODO is this necessary?
        return counterCreate2 == create2;
      }
      public String description() {
        return null;
      }
    };
    DistributedTestCase.waitForCriterion(ev, 60 * 1000, 200, true);
    
    final int u2 = update2;
    ev = new WaitCriterion() {
      public boolean done() {
        Thread.yield(); // TODO is this necessary?
        return counterUpdate2 == u2;
      }
      public String description() {
        return null;
      }
    };
    DistributedTestCase.waitForCriterion(ev, 60 * 1000, 200, true);
  }

  /**
   * assert that the final value is 55
   *
   */
  public static void assertValue()
  {
    try {
      Region r1 = cacheClient.getRegion(Region.SEPARATOR + REGION_NAME1);
      Region r2 = cacheClient.getRegion(Region.SEPARATOR + REGION_NAME2);
      assertTrue( r1.containsKey("key-1"));
      assertTrue( r1.get("key-1").equals("55"));
      assertTrue( r2.containsKey("key-1"));
      assertTrue( r2.get("key-1").equals("55"));
    }
    catch (Exception e) {
      fail("Exception in trying to get due to " + e);
    }
  }

  /**
   * create a server cache and start the server
   *
   * @throws Exception
   */
  public static Integer createServerCache() throws Exception
  {
    Properties props = new Properties();
    props.setProperty(DistributionConfig.DELTA_PROPAGATION_PROP_NAME, "false");
    ClientConflationDUnitTest test = new ClientConflationDUnitTest("temp");
    cacheServer = test.createCache(props);
    AttributesFactory factory = new AttributesFactory();
    factory.setScope(Scope.DISTRIBUTED_ACK);
    factory.setEnableConflation(true);
    RegionAttributes attrs1 = factory.create();
    factory.setEnableConflation(false);
    RegionAttributes attrs2 = factory.create();
    cacheServer.createRegion(REGION_NAME1, attrs1);
    cacheServer.createRegion(REGION_NAME2, attrs2);
    BridgeServer server = cacheServer.addBridgeServer();
    int port = AvailablePort.getRandomAvailablePort(AvailablePort.SOCKET) ;
    server.setPort(port);
    server.setNotifyBySubscription(true);
    server.setSocketBufferSize(32768);
    server.start();
    return new Integer(server.getPort());
  }

  /**
   * close the client cache
   *
   */
  public static void closeCacheClient()
  {
    if (cacheClient != null && !cacheClient.isClosed()) {
      cacheClient.close();
      cacheClient.getDistributedSystem().disconnect();
    }
  }

  /**
   * close the feeder cache
   *
   */
  public static void closeCacheFeeder()
  {
    if (cacheFeeder != null && !cacheFeeder.isClosed()) {
      cacheFeeder.close();
      cacheFeeder.getDistributedSystem().disconnect();
    }
  }
  
  /**
   * close the server cache
   *
   */
  public static void closeCacheServer()
  {
    if (cacheServer != null && !cacheServer.isClosed()) {
      cacheServer.close();
      cacheServer.getDistributedSystem().disconnect();
    }
  }

  /**
   * register interest with the server on ALL_KEYS
   *
   */
  public static void registerInterest()
  {
    try {
      Region region1 = cacheClient.getRegion(Region.SEPARATOR +REGION_NAME1);
      Region region2 = cacheClient.getRegion(Region.SEPARATOR +REGION_NAME2);
      assertTrue(region1 != null);
      assertTrue(region2 != null);
      region1.registerInterest("ALL_KEYS");
      region2.registerInterest("ALL_KEYS");
    }
    catch (CacheWriterException e) {
      fail("test failed due to " + e);
    }
  }


  /**
   * register interest with the server on ALL_KEYS
   *
   */

  public static void unregisterInterest()
  {
    try {
      Region region1 = cacheClient.getRegion(Region.SEPARATOR +REGION_NAME1);
      Region region2 = cacheClient.getRegion(Region.SEPARATOR +REGION_NAME2);
      region1.unregisterInterest("ALL_KEYS");
      region2.unregisterInterest("ALL_KEYS");
    }
    catch (CacheWriterException e) {
      fail("test failed due to " + e);
    }
  }

  /**
   * do 5 puts on key-1
   *
   */
  public static void putEntries()
  {
    try {
      getLogWriter().info("Putting entries...");
      Region r1 = cacheFeeder.getRegion(Region.SEPARATOR +REGION_NAME1);
      Region r2 = cacheFeeder.getRegion(Region.SEPARATOR +REGION_NAME2);
      r1.put("key-1", "11");
      r2.put("key-1", "11");
      r1.put("key-1", "22");
      r2.put("key-1", "22");
      r1.put("key-1", "33");
      r2.put("key-1", "33");
      r1.put("key-1", "44");
      r2.put("key-1", "44");
      r1.put("key-1", "55");      
      r2.put("key-1", "55");
    }
    catch (Exception ex) {
      ex.printStackTrace();
      fail("failed while region.put()", ex);
    }
  }

  /**
   * close the cache in tearDown
   */
  public void tearDown2() throws Exception
  {
    // close client
    closeCacheFeeder();
    vm1.invoke(ClientConflationDUnitTest.class, "closeCacheClient");
    // close server
    vm0.invoke(ClientConflationDUnitTest.class, "closeCacheServer");
  }
}


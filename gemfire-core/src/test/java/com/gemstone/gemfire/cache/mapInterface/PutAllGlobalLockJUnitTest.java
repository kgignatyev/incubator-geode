/*=========================================================================
 * Copyright (c) 2010-2014 Pivotal Software, Inc. All Rights Reserved.
 * This product is protected by U.S. and international copyright
 * and intellectual property laws. Pivotal products are covered by
 * one or more patents listed at http://www.pivotal.io/patents.
 *=========================================================================
 */
package com.gemstone.gemfire.cache.mapInterface;

import static org.junit.Assert.*;

import java.util.Properties;
import java.util.TreeMap;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import junit.framework.TestCase;

import com.gemstone.gemfire.cache.AttributesFactory;
import com.gemstone.gemfire.cache.Cache;
import com.gemstone.gemfire.cache.CacheFactory;
import com.gemstone.gemfire.cache.EntryEvent;
import com.gemstone.gemfire.cache.Region;
import com.gemstone.gemfire.cache.RegionAttributes;
import com.gemstone.gemfire.cache.Scope;
import com.gemstone.gemfire.cache.util.CacheListenerAdapter;
import com.gemstone.gemfire.distributed.DistributedSystem;
import com.gemstone.gemfire.test.junit.categories.IntegrationTest;

import dunit.DistributedTestCase;

@Category(IntegrationTest.class)
public class PutAllGlobalLockJUnitTest {
    
    Region testRegion = null;
    volatile boolean done = false;
    boolean testOK = false;
    Thread thread;
    
    public PutAllGlobalLockJUnitTest() {
    }
    
    @Before
    public void setUp() throws Exception {
        try {
            Properties properties = new Properties();
            properties.setProperty("mcast-port", "0");
            properties.setProperty("locators", "");
            DistributedSystem distributedSystem = DistributedSystem
                    .connect(properties);
            Cache cache = CacheFactory.create(distributedSystem);
            AttributesFactory factory = new AttributesFactory();
            factory.setScope(Scope.GLOBAL);
            factory.setCacheListener(new Listener());
            RegionAttributes regionAttributes = factory.create();
            testRegion = cache.createRegion("TestRegion", regionAttributes);
        } catch (Exception e) {
            e.printStackTrace();
            fail("test failed to create a distributed system/cache");
        }
    }
    

    @Test
    public void testPutAllGlobalLock() {
        TreeMap trialMap = new TreeMap();
        for (long i = 0; i < 1000; i++) {
            trialMap.put(new Long(i), new Long(i));
        }
        try {
            testRegion.putAll(trialMap);
            DistributedTestCase.join(this.thread, 30 * 1000, null);
            assertTrue(this.testOK);
        } catch (Exception e) {
            fail("Test has failed due to "+e);
        }      
    }
       
    protected  class Listener extends CacheListenerAdapter {
        
        public void afterCreate(EntryEvent event) {
            if (event.getKey().equals(new Long(1))) {
                PutAllGlobalLockJUnitTest.this.thread = new Thread(new Runner());
                thread.start();
            }else if (event.getKey().equals(new Long(999))) {
                PutAllGlobalLockJUnitTest.this.done = true;
                
            }
        }
    }
    
    protected  class Runner implements Runnable {
        
        public void run() {
            testRegion.put(new Long(1000), new Long(1000));
            PutAllGlobalLockJUnitTest.this.testOK = PutAllGlobalLockJUnitTest.this.done;
        }
    }
}

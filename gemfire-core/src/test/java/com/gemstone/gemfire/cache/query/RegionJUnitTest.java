/*=========================================================================
 * Copyright (c) 2010-2014 Pivotal Software, Inc. All Rights Reserved.
 * This product is protected by U.S. and international copyright
 * and intellectual property laws. Pivotal products are covered by
 * one or more patents listed at http://www.pivotal.io/patents.
 *=========================================================================
 */
package com.gemstone.gemfire.cache.query;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import java.util.Iterator;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import com.gemstone.gemfire.cache.AttributesFactory;
import com.gemstone.gemfire.cache.Cache;
import com.gemstone.gemfire.cache.Region;
import com.gemstone.gemfire.cache.RegionAttributes;
import com.gemstone.gemfire.cache.query.data.Portfolio;
import com.gemstone.gemfire.internal.cache.EntrySnapshot;
import com.gemstone.gemfire.internal.cache.LocalRegion;
// for internal access test
import com.gemstone.gemfire.internal.cache.RegionEntry;
import com.gemstone.gemfire.test.junit.categories.IntegrationTest;

/**
 * RegionJUnitTest.java
 *
 * Created on January 31, 2005, 3:54 PM
 * 
 * @author vaibhav
 */
@Category(IntegrationTest.class)
public class RegionJUnitTest{
  
  static String queries[] = {
    "status = 'active'",
            "status <> 'active'",
            "ID > 2",
            "ID < 1",
            "ID >= 2",
            "ID <= 1",
            "status = 'active' AND ID = 0",
            "status = 'active' AND ID = 1",
            "status = 'active' OR ID = 1",
            "isActive",
            "isActive()",
            "testMethod(true)",
            "NOT isActive",
            "P1.secId = 'SUN'",
            "status = 'active' AND ( ID = 1 OR P1.secId = 'SUN')",
  };
  
  Region region;
  QueryService qs;
  Cache cache;
  
  
  @Test
  public void testShortcutMethods() throws Exception {
    for(int i=0;i<queries.length;i++){
      CacheUtils.log("Query = "+queries[i]);
      Object r = region.query(queries[i]);
      CacheUtils.getLogger().fine(Utils.printResult(r));
    }
  }
  
  @Test
  public void testQueryServiceInterface() throws Exception {
    for(int i=0;i<queries.length;i++){
      CacheUtils.log("Query = select distinct * from /pos where "+queries[i]);
      Query q = qs.newQuery("select distinct * from /pos where "+queries[i]);
      Object r = q.execute();
      CacheUtils.getLogger().fine(Utils.printResult(r));
    }
  }
  
  
  @Test
  public void testParameterBinding() throws Exception {
    Query q = qs.newQuery("select distinct * from /pos where ID = $1");
    Object [] params = new Object[]{new Integer(0)};//{"active"};
    Object r = q.execute(params);
    CacheUtils.getLogger().fine(Utils.printResult(r));
    
    q = qs.newQuery("select distinct * from $1 where status = $2 and ID = $3");
    params = new Object[]{this.region, "active", new Integer(0)};
    r = q.execute(params);
    CacheUtils.getLogger().fine(Utils.printResult(r));
  }
  
  
  
  @Test
  public void testQRegionInterface() throws Exception {
    String queries[] = {
      "select distinct * from /pos.keys where toString = '1'",
              "select distinct * from /pos.values where status = 'active'",
              "select distinct * from /pos.entries where key = '1'",
              "select distinct * from /pos.entries where value.status = 'active'"
    };
    
    for(int i=0;i<queries.length;i++){
      CacheUtils.log("Query = "+queries[i]);
      Query q = qs.newQuery(queries[i]);
      Object r = q.execute();
      CacheUtils.getLogger().fine(Utils.printResult(r));
    }
  }
  
  
  @Test
  public void testInvalidEntries() throws Exception {
    region.invalidate("1");
    region.invalidate("3");   
    Query q = qs.newQuery("select distinct * from /pos");
    SelectResults results = (SelectResults)q.execute();
    assertEquals(2, results.size()); // should not include NULLs
  }
  
  @Test
  public void testRegionEntryAccess() throws Exception {
    Iterator entriesIter = region.entries(false).iterator();
    while(entriesIter.hasNext()){
      Region.Entry entry = (Region.Entry)entriesIter.next();
      RegionEntry regionEntry = null;
      if (entry instanceof LocalRegion.NonTXEntry) {
        regionEntry = ((LocalRegion.NonTXEntry)entry).getRegionEntry();
      }
      else {
        regionEntry = ((EntrySnapshot)entry).getRegionEntry();
      }
      assertNotNull(regionEntry);
    }
    
    
    LocalRegion lRegion = (LocalRegion)region;
    Iterator keysIterator = lRegion.keys().iterator();
    while(keysIterator.hasNext()){
      Object key = keysIterator.next();
      Region.Entry rEntry = lRegion.getEntry(key);
      RegionEntry regionEntry = null;
      if (rEntry instanceof LocalRegion.NonTXEntry) {
        regionEntry = ((LocalRegion.NonTXEntry)rEntry).getRegionEntry();
      }
      else {
        regionEntry = ((EntrySnapshot)rEntry).getRegionEntry();
      }
      assertNotNull(regionEntry);
    }
  }
  
  @Test
  public void testRegionNames() {
    
    String queryStrs[] = new String[] {
        "SELECT * FROM /pos",
        "SELECT * FROM /pos where status='active'",
        "SELECT * FROM /p:os", // Using ':' in region name.
        "SELECT * FROM /p@os", // Using '@' in region name.
        "SELECT * FROM /p-os", // Using '-' in region name.
        "SELECT * FROM /p#os", // Using '#' in region name.
        "SELECT * FROM /p+os", // Using '+' in region name.
    };
    
    CacheUtils.startCache();
    cache = CacheUtils.getCache();
    AttributesFactory attributesFactory = new AttributesFactory();
    attributesFactory.setValueConstraint(Portfolio.class);
    RegionAttributes regionAttributes = attributesFactory.create();
    
    cache.createRegion("p:os",regionAttributes);
    cache.createRegion("p@os",regionAttributes);
    cache.createRegion("p-os",regionAttributes);
    cache.createRegion("p#os",regionAttributes);
    cache.createRegion("p+os",regionAttributes);

    for(int i=0; i<queryStrs.length;++i) {
       Query q = CacheUtils.getQueryService().newQuery(queryStrs[i]);
       try {
         q.execute();
       } catch (Exception ex) {
         // Failed - Any other exception.
         fail("Failed to execute the query. '" +  queryStrs[i] + "' Error: " + ex.getMessage());
       }
    }
  }
 
  @Before
  public void setUp() throws Exception {
    CacheUtils.startCache();
    cache = CacheUtils.getCache();
    AttributesFactory attributesFactory = new AttributesFactory();
    attributesFactory.setValueConstraint(Portfolio.class);
    RegionAttributes regionAttributes = attributesFactory.create();
    
    region = cache.createRegion("pos",regionAttributes);
    region.put("0",new Portfolio(0));
    region.put("1",new Portfolio(1));
    region.put("2",new Portfolio(2));
    region.put("3",new Portfolio(3));
    
    qs = cache.getQueryService();
  }
  
  @After
  public void tearDown() throws Exception {
    CacheUtils.closeCache();
  }
  
}

package com.gemstone.gemfire.internal.redis.executor.list;

import java.util.List;

import com.gemstone.gemfire.cache.Region;
import com.gemstone.gemfire.cache.query.QueryService;
import com.gemstone.gemfire.internal.cache.GemFireCacheImpl;
import com.gemstone.gemfire.internal.redis.ByteArrayWrapper;
import com.gemstone.gemfire.internal.redis.ExecutionHandlerContext;
import com.gemstone.gemfire.internal.redis.RedisDataType;
import com.gemstone.gemfire.internal.redis.executor.AbstractExecutor;


public abstract class ListExecutor extends AbstractExecutor {

  protected static enum ListDirection {LEFT, RIGHT};

  protected final static QueryService getQueryService() {
    return GemFireCacheImpl.getInstance().getQueryService();
  }

  @SuppressWarnings("unchecked")
  @Override
  protected Region<Integer, ByteArrayWrapper> getOrCreateRegion(ExecutionHandlerContext context, ByteArrayWrapper key, RedisDataType type) {
    return (Region<Integer, ByteArrayWrapper>) context.getRegionCache().getOrCreateRegion(key, type, context);
  }
  
  @SuppressWarnings("unchecked")
  protected Region<Integer, ByteArrayWrapper> getRegion(ExecutionHandlerContext context, ByteArrayWrapper key) {
    return (Region<Integer, ByteArrayWrapper>) context.getRegionCache().getRegion(key);
  }

  /**
   * Helper method to be used by the push commands to push elements onto a list.
   * Because our current setup requires non trivial code to push elements in
   * to a Region, I wanted all the push code to reside in one place. 
   * 
   * @param key Name of the list
   * @param commandElems Pieces of the command, this is where the elements that need to be
   * pushed live
   * @param startIndex The index to start with in the commandElems list, inclusive
   * @param endIndex The index to end with in the commandElems list, exclusive
   * @param keyRegion Region of list
   * @param pushType ListDirection.LEFT || ListDirection.RIGHT
   * @param context Context of this push
   */
  protected void pushElements(ByteArrayWrapper key, List<byte[]> commandElems, int startIndex, int endIndex,
      Region<Integer, ByteArrayWrapper> keyRegion, ListDirection pushType, ExecutionHandlerContext context) {
    Region<String, Integer> meta = context.getRegionCache().getListsMetaRegion();

    String indexKey = pushType == ListDirection.LEFT ? key + "head" : key + "tail";
    String oppositeKey = pushType == ListDirection.RIGHT ? key + "head" : key + "tail";
    Integer index = meta.get(indexKey);
    Integer opp = meta.get(oppositeKey);

    if (index != opp)
      index += pushType == ListDirection.LEFT ? -1 : 1; // Subtract index if left push, add if right push

    /**
     * Multi push command
     * 
     * For every element that needs to be added
     */

    for (int i = startIndex; i < endIndex; i++) {
      byte[] value = commandElems.get(i);
      ByteArrayWrapper wrapper = new ByteArrayWrapper(value);

      /**
       * 
       * First, use the start index to attempt to insert the
       * value into the Region
       * 
       */

      Object oldValue;
      do {
        oldValue = keyRegion.putIfAbsent(index, wrapper);
        if (oldValue != null)
          index += pushType == ListDirection.LEFT ? -1 : 1; // Subtract index if left push, add if right push
      } while (oldValue != null);

      /**
       * 
       * Next, update the index in the meta data region. Keep trying
       * to replace the existing index unless the index is further out
       * than previously inserted, that's ok. Example below:
       * 
       * ********************** LPUSH/LPUSH ***************************
       *   Push occurring at the same time, further index update first
       *   |    This push
       *   |      |
       *   |      |
       *   V      V
       * [-4]   [-3]    [-2]    [-1]    [0]     [1]     [2]
       * 
       * In this case, -4 would already exist in the meta data region, therefore
       * we do not try to put -3 in the meta data region because a further
       * index is already there.
       * ***************************************************************
       * 
       * Another example
       * 
       * ********************** LPUSH/LPOP *****************************
       *   This push
       *   |    Simultaneous LPOP, meta data head index already updated to -2
       *   |     |
       *   |     |
       *   V     V
       * [-4]   [X]    [-2]    [-1]    [0]     [1]     [2]
       * 
       * In this case, -2 would already exist in the meta data region, but
       * we need to make sure the element at -4 is visible to all other threads
       * so we will attempt to change the index to -4 as long as it is greater
       * than -4
       * ***************************************************************
       * 
       */

      boolean indexSet = false;
      do {
        Integer existingIndex = meta.get(indexKey);
        if ((pushType == ListDirection.RIGHT && existingIndex < index) || (pushType == ListDirection.LEFT && existingIndex > index))
          indexSet = meta.replace(indexKey, existingIndex, index);
        else
          break;
      } while (!indexSet);

    }
  }

}

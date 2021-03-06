/*=========================================================================
 * Copyright (c) 2005-2014 Pivotal Software, Inc. All Rights Reserved.
 * This product is protected by U.S. and international copyright
 * and intellectual property laws. Pivotal products are covered by
 * more patents listed at http://www.pivotal.io/patents.
 *========================================================================
 */

package com.gemstone.gemfire.cache.query.internal;

import java.util.*;

import com.gemstone.gemfire.GemFireCacheException;
import com.gemstone.gemfire.GemFireException;
import com.gemstone.gemfire.cache.CacheException;
import com.gemstone.gemfire.cache.query.AmbiguousNameException;
import com.gemstone.gemfire.cache.query.FunctionDomainException;
import com.gemstone.gemfire.cache.query.IndexInvalidException;
import com.gemstone.gemfire.cache.query.NameResolutionException;
import com.gemstone.gemfire.cache.query.QueryException;
import com.gemstone.gemfire.cache.query.QueryInvalidException;
import com.gemstone.gemfire.cache.query.QueryInvocationTargetException;
import com.gemstone.gemfire.cache.query.TypeMismatchException;
import com.gemstone.gemfire.cache.query.internal.parse.OQLLexerTokenTypes;
import com.gemstone.gemfire.cache.query.internal.types.TypeUtils;
import com.gemstone.gemfire.internal.i18n.LocalizedStrings;

/**
 * This class represents a compiled form of sort criterian present in order by
 * clause
 * 
 * @author Yogesh Mahajan
 * @author Asif
 */
public class CompiledSortCriterion extends AbstractCompiledValue {
  // Asif: criterion true indicates descending order
  private boolean criterion = false;
  private CompiledValue expr = null;
  int columnIndex = -1;
  // private String correctedCanonicalizedExpression = null;
  private CompiledValue originalCorrectedExpression = null;

  @Override
  public List getChildren() {
    return Collections.singletonList(this.originalCorrectedExpression);
  }

  /**
   * @return int
   */
  public int getType() {
    return SORT_CRITERION;
  }

  /**
   * evaluates sort criteria in order by clause
   * 
   * @param context
   * @return Object
   * @throws FunctionDomainException
   * @throws TypeMismatchException
   * @throws NameResolutionException
   * @throws QueryInvocationTargetException
   *
   *           public Object evaluate(ExecutionContext context) throws
   *           FunctionDomainException, TypeMismatchException,
   *           NameResolutionException, QueryInvocationTargetException {
   *           if(this.columnIndex >= 0) { return
   *           context.getFieldFromProjectedRow(this.columnIndex); }else {
   *           return this.expr.evaluate(context); } }
   */

  public Object evaluate(Object data, ExecutionContext context) {
    Object value = null;
    if (this.columnIndex > 0) {
      value = ((Object[]) data)[this.columnIndex];
    } else if (this.columnIndex == 0) {
      if (data instanceof Object[]) {
        value = ((Object[]) data)[this.columnIndex];
      } else {
        value = data;
      }
    } else {
      throw new IllegalStateException(" Order By Column attribute unmapped");
    }
    context.setCurrentProjectionField(value);
    try {
      return this.expr.evaluate(context);
    } catch (Exception e) {
      throw new CacheException(e) {
      };
    }

  }

  /**
   * concstructor
   * 
   * @param criterion
   * @param cv
   */
  CompiledSortCriterion(boolean criterion, CompiledValue cv) {
    this.expr = cv;
    this.criterion = criterion;
    this.originalCorrectedExpression = this.expr;
  }

  public boolean getCriterion() {
    return criterion;
  }

  public CompiledValue getExpr() {
    return this.originalCorrectedExpression;
  }

  public int getColumnIndex() {
    return this.columnIndex;
  }

  /*
   * public String getCorrectedCanonicalizedExpression() { return
   * this.correctedCanonicalizedExpression; }
   */

  @Override
  public Object evaluate(ExecutionContext context)
      throws FunctionDomainException, TypeMismatchException,
      NameResolutionException, QueryInvocationTargetException {
    
    return this.expr.evaluate(context);
  }

  private void substituteExpression(CompiledValue newExpression, int columnIndex) {
    this.expr = newExpression;
    this.columnIndex = columnIndex;
  }

  private void substituteExpressionWithProjectionField(int columnIndex) {
    this.expr = ProjectionField.getProjectionField();
    this.columnIndex = columnIndex;
  }

  private CompiledValue getReconstructedExpression(String projAttribStr,
      ExecutionContext context) throws AmbiguousNameException,
      TypeMismatchException, NameResolutionException {
    List<CompiledValue> expressions = PathUtils.collectCompiledValuesInThePath(
        expr, context);
    StringBuffer tempBuff = new StringBuffer();
    ListIterator<CompiledValue> listIter = expressions.listIterator(expressions
        .size());
    while (listIter.hasPrevious()) {
      listIter.previous().generateCanonicalizedExpression(tempBuff, context);
      if (tempBuff.toString().equals(projAttribStr)) {
        // we have found our match from where we have to replace the expression
        break;
      } else {
        tempBuff.delete(0, tempBuff.length());
      }
    }

    // Now we need to create a new CompiledValue which terminates with
    // ProjectionField
    CompiledValue cvToRetainTill = listIter.previous();

    CompiledValue prevCV = null;
    List<Object> reconstruct = new ArrayList<Object>();
    CompiledValue cv = expressions.get(0);
    int index = 0;
    do {
      prevCV = cv;
      
      switch( cv.getType()) {     
      case CompiledOperation.METHOD_INV:
        reconstruct.add(0, ((CompiledOperation) cv).getArguments());
        reconstruct.add(0, ((CompiledOperation) cv).getMethodName());
        break;       
      case CompiledPath.PATH:
        reconstruct.add(0, ((CompiledPath) cv).getTailID());
        break;      
      case CompiledIndexOperation.TOK_LBRACK:
        reconstruct.add(0, ((CompiledIndexOperation) cv).getExpression());
        break;     
       default:
         throw new IllegalStateException(
             "Unexpected CompiledValue in order by clause");
      }
      reconstruct.add(0, Integer.valueOf(prevCV.getType()));
      cv = expressions.get(++index);
    } while (prevCV != cvToRetainTill);

    // Now reconstruct back
    Iterator<Object> iter = reconstruct.iterator();
    CompiledValue currentValue = ProjectionField.getProjectionField();
    while (iter.hasNext()) {
      int type = ((Integer) iter.next()).intValue();
      switch (type) {
      case CompiledValue.PATH:
        currentValue = new CompiledPath(currentValue, (String) iter.next());
        break;
      case OQLLexerTokenTypes.METHOD_INV:
        currentValue = new CompiledOperation(currentValue,
            (String) iter.next(), (List) iter.next());
        break;
      case OQLLexerTokenTypes.TOK_LBRACK:
        currentValue = new CompiledIndexOperation(currentValue,
            (CompiledValue) iter.next());
        break;
      }

    }

    return currentValue;
  }

  boolean mapExpressionToProjectionField(List projAttrs,
      ExecutionContext context) throws AmbiguousNameException,
      TypeMismatchException, NameResolutionException {
    boolean mappedColumn = false;
    this.originalCorrectedExpression = expr;
    if (projAttrs != null) {
      // if expr is CompiledID , check for alias
      if (expr.getType() == OQLLexerTokenTypes.Identifier) {

        for (int i = 0; i < projAttrs.size() && !mappedColumn; ++i) {
          Object[] prj = (Object[]) TypeUtils.checkCast(projAttrs.get(i),
              Object[].class);
          if (prj[0] != null && prj[0].equals(((CompiledID) expr).getId())) {
            // set the field index
            this.substituteExpressionWithProjectionField(i);
            this.originalCorrectedExpression = (CompiledValue) prj[1];
            mappedColumn = true;

          }
        }

      }
      if (!mappedColumn) {
        // the order by expr is not an alias check for path
        StringBuffer orderByExprBuffer = new StringBuffer(), projAttribBuffer = new StringBuffer();
        expr.generateCanonicalizedExpression(orderByExprBuffer, context);
        final String orderByExprStr = orderByExprBuffer.toString();
        for (int i = 0; i < projAttrs.size(); ++i) {
          Object[] prj = (Object[]) TypeUtils.checkCast(projAttrs.get(i),
              Object[].class);
          CompiledValue cvProj = (CompiledValue) TypeUtils.checkCast(prj[1],
              CompiledValue.class);
          cvProj.generateCanonicalizedExpression(projAttribBuffer, context);
          final String projAttribStr = projAttribBuffer.toString();
          if (projAttribStr.equals(orderByExprStr)) {
            // set the field index
            this.substituteExpressionWithProjectionField(i);
            mappedColumn = true;
            break;
          } else if (orderByExprStr.startsWith(projAttribStr)) {
            CompiledValue newExpr = getReconstructedExpression(projAttribStr,
                context);
            this.substituteExpression(newExpr, i);
            mappedColumn = true;
            break;
          }
          projAttribBuffer.delete(0, projAttribBuffer.length());
        }
      }
    } else {
      RuntimeIterator rIter = context.findRuntimeIterator(expr);
      List currentIters = context.getCurrentIterators();
      for (int i = 0; i < currentIters.size(); ++i) {
        RuntimeIterator runtimeIter = (RuntimeIterator) currentIters.get(i);
        if (runtimeIter == rIter) {
          /* this.substituteExpressionWithProjectionField( i); */
          StringBuffer temp = new StringBuffer();
          rIter.generateCanonicalizedExpression(temp, context);
          // this.correctedCanonicalizedExpression = temp.toString();
          /* mappedColumn = true; */
          String projAttribStr = temp.toString();
          temp = new StringBuffer();
          expr.generateCanonicalizedExpression(temp, context);
          String orderbyStr = temp.toString();
          if (projAttribStr.equals(orderbyStr)) {
            this.substituteExpressionWithProjectionField(i);
            mappedColumn = true;
            break;
          } else {
            CompiledValue newExpr = getReconstructedExpression(projAttribStr,
                context);
            this.substituteExpression(newExpr, i);
            mappedColumn = true;
            break;
          }
        }
      }
    }
    return mappedColumn;

  }

  static class ProjectionField extends AbstractCompiledValue {

    private static ProjectionField singleton = new ProjectionField();

    private ProjectionField() {
    }

    public Object evaluate(ExecutionContext context)
        throws FunctionDomainException, TypeMismatchException,
        NameResolutionException, QueryInvocationTargetException {
      return context.getCurrentProjectionField();

    }

    @Override
    public int getType() {
      return FIELD;
    }

    public static ProjectionField getProjectionField() {
      return singleton;
    }

  }

}

/*
 * Copyright 2017, Yahoo! Inc.
 * Licensed under the terms of the Apache License 2.0. See LICENSE file at the project root for terms.
 */

package com.yahoo.sketches.hive.hll;

import java.util.Arrays;
import java.util.List;

import org.apache.hadoop.hive.ql.exec.UDFArgumentException;
import org.apache.hadoop.hive.ql.exec.UDFArgumentTypeException;
import org.apache.hadoop.hive.ql.parse.SemanticException;
import org.apache.hadoop.hive.ql.udf.generic.GenericUDAFEvaluator;
import org.apache.hadoop.hive.ql.udf.generic.GenericUDAFEvaluator.Mode;
import org.apache.hadoop.hive.ql.udf.generic.GenericUDAFParameterInfo;
import org.apache.hadoop.hive.ql.udf.generic.SimpleGenericUDAFParameterInfo;
import org.apache.hadoop.hive.serde2.objectinspector.ObjectInspector;
import org.apache.hadoop.hive.serde2.objectinspector.ObjectInspectorFactory;
import org.apache.hadoop.hive.serde2.objectinspector.PrimitiveObjectInspector.PrimitiveCategory;
import org.apache.hadoop.hive.serde2.objectinspector.primitive.PrimitiveObjectInspectorFactory;
import org.apache.hadoop.hive.serde2.typeinfo.TypeInfoFactory;
import org.apache.hadoop.io.BytesWritable;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.Text;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.yahoo.memory.Memory;
import com.yahoo.sketches.hll.HllSketch;
import com.yahoo.sketches.hll.TgtHllType;

public class UnionSketchUDAFTest {

  private static final ObjectInspector intInspector =
      PrimitiveObjectInspectorFactory.getPrimitiveWritableObjectInspector(PrimitiveCategory.INT);

  private static final ObjectInspector stringInspector =
      PrimitiveObjectInspectorFactory.getPrimitiveWritableObjectInspector(PrimitiveCategory.STRING);

  private static final ObjectInspector binaryInspector =
      PrimitiveObjectInspectorFactory.getPrimitiveWritableObjectInspector(PrimitiveCategory.BINARY);

  private static final ObjectInspector structInspector = ObjectInspectorFactory.getStandardStructObjectInspector(
      Arrays.asList("lgK", "type", "sketch"),
      Arrays.asList(intInspector, stringInspector, binaryInspector)
    );

  static final ObjectInspector intConstantInspector =
      PrimitiveObjectInspectorFactory.getPrimitiveWritableConstantObjectInspector(TypeInfoFactory.intTypeInfo, null);

  static final ObjectInspector stringConstantInspector =
      PrimitiveObjectInspectorFactory.getPrimitiveWritableConstantObjectInspector(TypeInfoFactory.stringTypeInfo, null);

  @Test(expectedExceptions = { UDFArgumentException.class })
  public void tooFewArguments() throws SemanticException {
    ObjectInspector[] inspectors = new ObjectInspector[] { };
    GenericUDAFParameterInfo params = new SimpleGenericUDAFParameterInfo(inspectors, false, false);
    new UnionSketchUDAF().getEvaluator(params);
  }

  @Test(expectedExceptions = { UDFArgumentException.class })
  public void tooManyArguments() throws SemanticException {
    ObjectInspector[] inspectors = new ObjectInspector[] { binaryInspector, intConstantInspector, stringConstantInspector, stringConstantInspector };
    GenericUDAFParameterInfo params = new SimpleGenericUDAFParameterInfo(inspectors, false, false);
    new UnionSketchUDAF().getEvaluator(params);
  }

  @Test(expectedExceptions = { UDFArgumentTypeException.class })
  public void invalidCategoryArg1() throws SemanticException {
    ObjectInspector[] inspectors = new ObjectInspector[] { structInspector };
    GenericUDAFParameterInfo params = new SimpleGenericUDAFParameterInfo(inspectors, false, false);
    new UnionSketchUDAF().getEvaluator(params);
  }

  @Test(expectedExceptions = { UDFArgumentTypeException.class })
  public void invalidTypeArg1() throws SemanticException {
    ObjectInspector[] inspectors = new ObjectInspector[] { intInspector };
    GenericUDAFParameterInfo params = new SimpleGenericUDAFParameterInfo(inspectors, false, false);
    new UnionSketchUDAF().getEvaluator(params);
  }

  @Test(expectedExceptions = { UDFArgumentTypeException.class })
  public void invalidCategoryArg2() throws SemanticException {
    ObjectInspector[] inspectors = new ObjectInspector[] { binaryInspector, structInspector };
    GenericUDAFParameterInfo params = new SimpleGenericUDAFParameterInfo(inspectors, false, false);
    new UnionSketchUDAF().getEvaluator(params);
  }

  @Test(expectedExceptions = { UDFArgumentTypeException.class })
  public void invalidTypeArg2() throws SemanticException {
    ObjectInspector[] inspectors = new ObjectInspector[] { binaryInspector, stringInspector };
    GenericUDAFParameterInfo params = new SimpleGenericUDAFParameterInfo(inspectors, false, false);
    new UnionSketchUDAF().getEvaluator(params);
  }

  @Test(expectedExceptions = { UDFArgumentTypeException.class })
  public void arg2NotConstant() throws SemanticException {
    ObjectInspector[] inspectors = new ObjectInspector[] { binaryInspector, intInspector };
    GenericUDAFParameterInfo params = new SimpleGenericUDAFParameterInfo(inspectors, false, false);
    new UnionSketchUDAF().getEvaluator(params);
  }

  @Test(expectedExceptions = { UDFArgumentTypeException.class })
  public void invalidCategoryArg3() throws SemanticException {
    ObjectInspector[] inspectors = new ObjectInspector[] { binaryInspector, intConstantInspector, structInspector };
    GenericUDAFParameterInfo params = new SimpleGenericUDAFParameterInfo(inspectors, false, false);
    new UnionSketchUDAF().getEvaluator(params);
  }

  @Test(expectedExceptions = { UDFArgumentTypeException.class })
  public void invalidTypeArg3() throws SemanticException {
    ObjectInspector[] inspectors = new ObjectInspector[] { binaryInspector, intConstantInspector, intInspector };
    GenericUDAFParameterInfo params = new SimpleGenericUDAFParameterInfo(inspectors, false, false);
    new UnionSketchUDAF().getEvaluator(params);
  }

  @Test(expectedExceptions = { UDFArgumentTypeException.class })
  public void arg3NotConstant() throws SemanticException {
    ObjectInspector[] inspectors = new ObjectInspector[] { binaryInspector, intConstantInspector, stringInspector };
    GenericUDAFParameterInfo params = new SimpleGenericUDAFParameterInfo(inspectors, false, false);
    new UnionSketchUDAF().getEvaluator(params);
  }

  // PARTIAL1 mode (Map phase in Map-Reduce): iterate + terminatePartial
  @Test
  public void partial1ModeDefaultParams() throws Exception {
    ObjectInspector[] inspectors = new ObjectInspector[] { binaryInspector };
    GenericUDAFParameterInfo info = new SimpleGenericUDAFParameterInfo(inspectors, false, false);
    GenericUDAFEvaluator eval = new UnionSketchUDAF().getEvaluator(info);
    ObjectInspector resultInspector = eval.init(Mode.PARTIAL1, inspectors);
    DataToSketchUDAFTest.checkIntermediateResultInspector(resultInspector);

    State state = (State) eval.getNewAggregationBuffer();

    HllSketch sketch1 = new HllSketch(SketchEvaluator.DEFAULT_LG_K);
    sketch1.update(1);
    eval.iterate(state, new Object[] {new BytesWritable(sketch1.toCompactByteArray())});

    HllSketch sketch2 = new HllSketch(SketchEvaluator.DEFAULT_LG_K);
    sketch2.update(2);
    eval.iterate(state, new Object[] {new BytesWritable(sketch2.toCompactByteArray())});

    Object result = eval.terminatePartial(state);
    Assert.assertNotNull(result);
    Assert.assertTrue(result instanceof List);
    List<?> r = (List<?>) result;
    Assert.assertEquals(r.size(), 3);
    Assert.assertEquals(((IntWritable) (r.get(0))).get(), SketchEvaluator.DEFAULT_LG_K);
    Assert.assertEquals(((Text) (r.get(1))).toString(), SketchEvaluator.DEFAULT_HLL_TYPE.toString());
    HllSketch resultSketch = HllSketch.heapify(Memory.wrap(((BytesWritable) (r.get(2))).getBytes()));
    Assert.assertEquals(resultSketch.getLgConfigK(), SketchEvaluator.DEFAULT_LG_K);
    Assert.assertEquals(resultSketch.getTgtHllType(), SketchEvaluator.DEFAULT_HLL_TYPE);
    Assert.assertEquals(resultSketch.getEstimate(), 2.0, 0.01);

    eval.close();
  }

  @Test
  public void partial1ModeExplicitParams() throws Exception {
    ObjectInspector[] inspectors = new ObjectInspector[] { binaryInspector, intConstantInspector, stringConstantInspector };
    GenericUDAFParameterInfo info = new SimpleGenericUDAFParameterInfo(inspectors, false, false);
    GenericUDAFEvaluator eval = new UnionSketchUDAF().getEvaluator(info);
    ObjectInspector resultInspector = eval.init(Mode.PARTIAL1, inspectors);
    DataToSketchUDAFTest.checkIntermediateResultInspector(resultInspector);

    final int lgK = 10;
    final TgtHllType hllType = TgtHllType.HLL_6;
    State state = (State) eval.getNewAggregationBuffer();

    HllSketch sketch1 = new HllSketch(lgK, hllType);
    sketch1.update(1);
    eval.iterate(state, new Object[] {new BytesWritable(sketch1.toCompactByteArray()),
        new IntWritable(lgK), new Text(hllType.toString())});

    HllSketch sketch2 = new HllSketch(lgK, hllType);
    sketch2.update(2);
    eval.iterate(state, new Object[] {new BytesWritable(sketch2.toCompactByteArray()),
        new IntWritable(lgK), new Text(hllType.toString())});

    Object result = eval.terminatePartial(state);
    Assert.assertNotNull(result);
    Assert.assertTrue(result instanceof List);
    List<?> r = (List<?>) result;
    Assert.assertEquals(r.size(), 3);
    Assert.assertEquals(((IntWritable) (r.get(0))).get(), lgK);
    Assert.assertEquals(((Text) (r.get(1))).toString(), hllType.toString());
    HllSketch resultSketch = HllSketch.heapify(Memory.wrap(((BytesWritable) (r.get(2))).getBytes()));
    Assert.assertEquals(resultSketch.getLgConfigK(), lgK);
    Assert.assertEquals(resultSketch.getTgtHllType(), hllType);
    Assert.assertEquals(resultSketch.getEstimate(), 2.0, 0.01);

    eval.close();
  }

  // PARTIAL2 mode (Combine phase in Map-Reduce): merge + terminatePartial
  @Test
  public void partial2Mode() throws Exception {
    ObjectInspector[] inspectors = new ObjectInspector[] { binaryInspector };
    GenericUDAFParameterInfo info = new SimpleGenericUDAFParameterInfo(inspectors, false, false);
    GenericUDAFEvaluator eval = new UnionSketchUDAF().getEvaluator(info);
    ObjectInspector resultInspector = eval.init(Mode.PARTIAL2, new ObjectInspector[] {structInspector});
    DataToSketchUDAFTest.checkIntermediateResultInspector(resultInspector);

    State state = (State) eval.getNewAggregationBuffer();

    HllSketch sketch1 = new HllSketch(SketchEvaluator.DEFAULT_LG_K);
    sketch1.update(1);
    eval.merge(state, Arrays.asList(
      new IntWritable(SketchEvaluator.DEFAULT_LG_K),
      new Text(SketchEvaluator.DEFAULT_HLL_TYPE.toString()),
      new BytesWritable(sketch1.toCompactByteArray()))
    );

    HllSketch sketch2 = new HllSketch(SketchEvaluator.DEFAULT_LG_K);
    sketch2.update(2);
    eval.merge(state, Arrays.asList(
      new IntWritable(SketchEvaluator.DEFAULT_LG_K),
      new Text(SketchEvaluator.DEFAULT_HLL_TYPE.toString()),
      new BytesWritable(sketch2.toCompactByteArray()))
    );

    Object result = eval.terminatePartial(state);
    Assert.assertNotNull(result);
    Assert.assertTrue(result instanceof List);
    List<?> r = (List<?>) result;
    Assert.assertEquals(r.size(), 3);
    Assert.assertEquals(((IntWritable) (r.get(0))).get(), SketchEvaluator.DEFAULT_LG_K);
    Assert.assertEquals(((Text) (r.get(1))).toString(), SketchEvaluator.DEFAULT_HLL_TYPE.toString());
    HllSketch resultSketch = HllSketch.heapify(Memory.wrap(((BytesWritable) (r.get(2))).getBytes()));
    Assert.assertEquals(resultSketch.getLgConfigK(), SketchEvaluator.DEFAULT_LG_K);
    Assert.assertEquals(resultSketch.getTgtHllType(), SketchEvaluator.DEFAULT_HLL_TYPE);
    Assert.assertEquals(resultSketch.getEstimate(), 2.0, 0.01);

    eval.reset(state);
    result = eval.terminate(state);
    Assert.assertNull(result);

    eval.close();
  }

  // FINAL mode (Reduce phase in Map-Reduce): merge + terminate
  @Test
  public void finalMode() throws Exception {
    ObjectInspector[] inspectors = new ObjectInspector[] { binaryInspector };
    GenericUDAFParameterInfo info = new SimpleGenericUDAFParameterInfo(inspectors, false, false);
    GenericUDAFEvaluator eval = new UnionSketchUDAF().getEvaluator(info);
    ObjectInspector resultInspector = eval.init(Mode.FINAL, new ObjectInspector[] {structInspector});
    DataToSketchUDAFTest.checkFinalResultInspector(resultInspector);

    State state = (State) eval.getNewAggregationBuffer();

    HllSketch sketch1 = new HllSketch(SketchEvaluator.DEFAULT_LG_K);
    sketch1.update(1);
    eval.merge(state, Arrays.asList(
      new IntWritable(SketchEvaluator.DEFAULT_LG_K),
      new Text(SketchEvaluator.DEFAULT_HLL_TYPE.toString()),
      new BytesWritable(sketch1.toCompactByteArray()))
    );

    HllSketch sketch2 = new HllSketch(SketchEvaluator.DEFAULT_LG_K);
    sketch2.update(2);
    eval.merge(state, Arrays.asList(
      new IntWritable(SketchEvaluator.DEFAULT_LG_K),
      new Text(SketchEvaluator.DEFAULT_HLL_TYPE.toString()),
      new BytesWritable(sketch2.toCompactByteArray()))
    );

    Object result = eval.terminate(state);
    Assert.assertNotNull(result);
    Assert.assertTrue(result instanceof BytesWritable);
    HllSketch resultSketch = HllSketch.heapify(Memory.wrap(((BytesWritable) result).getBytes()));
    Assert.assertEquals(resultSketch.getEstimate(), 2.0, 0.01);

    eval.close();
  }

  // COMPLETE mode (single mode, alternative to MapReduce): iterate + terminate
  @Test
  public void completeModeDefaultParams() throws Exception {
    ObjectInspector[] inspectors = new ObjectInspector[] { binaryInspector };
    GenericUDAFParameterInfo info = new SimpleGenericUDAFParameterInfo(inspectors, false, false);
    GenericUDAFEvaluator eval = new UnionSketchUDAF().getEvaluator(info);
    ObjectInspector resultInspector = eval.init(Mode.COMPLETE, inspectors);
    DataToSketchUDAFTest.checkFinalResultInspector(resultInspector);

    State state = (State) eval.getNewAggregationBuffer();

    HllSketch sketch1 = new HllSketch(SketchEvaluator.DEFAULT_LG_K);
    sketch1.update(1);
    eval.iterate(state, new Object[] {new BytesWritable(sketch1.toCompactByteArray())});

    HllSketch sketch2 = new HllSketch(SketchEvaluator.DEFAULT_LG_K);
    sketch2.update(2);
    eval.iterate(state, new Object[] {new BytesWritable(sketch2.toCompactByteArray())});

    Object result = eval.terminate(state);
    Assert.assertNotNull(result);
    Assert.assertTrue(result instanceof BytesWritable);
    HllSketch resultSketch = HllSketch.heapify(Memory.wrap(((BytesWritable) result).getBytes()));
    Assert.assertEquals(resultSketch.getEstimate(), 2.0, 0.01);

    eval.reset(state);
    result = eval.terminate(state);
    Assert.assertNull(result);

    eval.close();
  }

  @Test
  public void completeModeExplicitParams() throws Exception {
    ObjectInspector[] inspectors = new ObjectInspector[] { binaryInspector, intConstantInspector, stringConstantInspector };
    GenericUDAFParameterInfo info = new SimpleGenericUDAFParameterInfo(inspectors, false, false);
    GenericUDAFEvaluator eval = new UnionSketchUDAF().getEvaluator(info);
    ObjectInspector resultInspector = eval.init(Mode.COMPLETE, inspectors);
    DataToSketchUDAFTest.checkFinalResultInspector(resultInspector);

    final int lgK = 4;
    final TgtHllType hllType = TgtHllType.HLL_6;
    State state = (State) eval.getNewAggregationBuffer();

    HllSketch sketch1 = new HllSketch(lgK, hllType);
    sketch1.update(1);
    eval.iterate(state, new Object[] {new BytesWritable(sketch1.toCompactByteArray()),
        new IntWritable(lgK), new Text(hllType.toString())});

    HllSketch sketch2 = new HllSketch(lgK, hllType);
    sketch2.update(2);
    eval.iterate(state, new Object[] {new BytesWritable(sketch2.toCompactByteArray()),
        new IntWritable(lgK), new Text(hllType.toString())});

    Object result = eval.terminate(state);
    Assert.assertNotNull(result);
    Assert.assertTrue(result instanceof BytesWritable);
    HllSketch resultSketch = HllSketch.heapify(Memory.wrap(((BytesWritable) result).getBytes()));
    Assert.assertEquals(resultSketch.getLgConfigK(), lgK);
    Assert.assertEquals(resultSketch.getTgtHllType(), hllType);
    Assert.assertEquals(resultSketch.getEstimate(), 2.0, 0.01);

    eval.reset(state);
    result = eval.terminate(state);
    Assert.assertNull(result);

    eval.close();
  }

}

/*
 * Copyright 2016, Yahoo! Inc.
 * Licensed under the terms of the Apache License 2.0. See LICENSE file at the project root for terms.
 */

package com.yahoo.sketches.hive.tuple;

import static com.yahoo.sketches.Util.DEFAULT_NOMINAL_ENTRIES;

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
import org.apache.hadoop.hive.serde2.objectinspector.PrimitiveObjectInspector;
import org.apache.hadoop.hive.serde2.objectinspector.PrimitiveObjectInspector.PrimitiveCategory;
import org.apache.hadoop.hive.serde2.objectinspector.StructField;
import org.apache.hadoop.hive.serde2.objectinspector.StructObjectInspector;
import org.apache.hadoop.hive.serde2.objectinspector.primitive.PrimitiveObjectInspectorFactory;
import org.apache.hadoop.io.BytesWritable;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.Text;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.yahoo.memory.Memory;
import com.yahoo.sketches.tuple.DoubleSummary;
import com.yahoo.sketches.tuple.DoubleSummaryDeserializer;
import com.yahoo.sketches.tuple.DoubleSummaryFactory;
import com.yahoo.sketches.tuple.Sketch;
import com.yahoo.sketches.tuple.SketchIterator;
import com.yahoo.sketches.tuple.Sketches;
import com.yahoo.sketches.tuple.UpdatableSketch;
import com.yahoo.sketches.tuple.UpdatableSketchBuilder;

public class UnionDoubleSummaryWithModeSketchUDAFTest {

  private static final ObjectInspector intInspector =
      PrimitiveObjectInspectorFactory.getPrimitiveWritableObjectInspector(PrimitiveCategory.INT);

  private static final ObjectInspector floatInspector =
      PrimitiveObjectInspectorFactory.getPrimitiveWritableObjectInspector(PrimitiveCategory.FLOAT);

  private static final ObjectInspector stringInspector =
      PrimitiveObjectInspectorFactory.getPrimitiveWritableObjectInspector(PrimitiveCategory.STRING);

  private static final ObjectInspector binaryInspector =
      PrimitiveObjectInspectorFactory.getPrimitiveWritableObjectInspector(PrimitiveCategory.BINARY);

  private static final ObjectInspector structInspector = ObjectInspectorFactory.getStandardStructObjectInspector(
      Arrays.asList("nominalEntries", "summaryMode", "sketch"),
      Arrays.asList(intInspector, stringInspector, binaryInspector)
    );

  @Test(expectedExceptions = { UDFArgumentException.class })
  public void tooFewArguments() throws SemanticException {
    ObjectInspector[] inspectors = new ObjectInspector[] { };
    GenericUDAFParameterInfo params = new SimpleGenericUDAFParameterInfo(inspectors, false, false);
    new UnionDoubleSummaryWithModeSketchUDAF().getEvaluator(params);
  }

  @Test(expectedExceptions = { UDFArgumentException.class })
  public void tooManyArguments() throws SemanticException {
    ObjectInspector[] inspectors = new ObjectInspector[] { binaryInspector, intInspector, stringInspector, intInspector };
    GenericUDAFParameterInfo params = new SimpleGenericUDAFParameterInfo(inspectors, false, false);
    new UnionDoubleSummaryWithModeSketchUDAF().getEvaluator(params);
  }

  @Test(expectedExceptions = { UDFArgumentTypeException.class })
  public void invalidCategoryArg1() throws SemanticException {
    ObjectInspector[] inspectors = new ObjectInspector[] { structInspector };
    GenericUDAFParameterInfo params = new SimpleGenericUDAFParameterInfo(inspectors, false, false);
    new UnionDoubleSummaryWithModeSketchUDAF().getEvaluator(params);
  }

  @Test(expectedExceptions = { UDFArgumentTypeException.class })
  public void invalidTypeArg1() throws SemanticException {
    ObjectInspector[] inspectors = new ObjectInspector[] { intInspector };
    GenericUDAFParameterInfo params = new SimpleGenericUDAFParameterInfo(inspectors, false, false);
    new UnionDoubleSummaryWithModeSketchUDAF().getEvaluator(params);
  }

  @Test(expectedExceptions = { UDFArgumentTypeException.class })
  public void invalidCategoryArg2() throws SemanticException {
    ObjectInspector[] inspectors = new ObjectInspector[] { binaryInspector, structInspector };
    GenericUDAFParameterInfo params = new SimpleGenericUDAFParameterInfo(inspectors, false, false);
    new UnionDoubleSummaryWithModeSketchUDAF().getEvaluator(params);
  }

  @Test(expectedExceptions = { UDFArgumentTypeException.class })
  public void invalidTypeArg2() throws SemanticException {
    ObjectInspector[] inspectors = new ObjectInspector[] { binaryInspector, floatInspector };
    GenericUDAFParameterInfo params = new SimpleGenericUDAFParameterInfo(inspectors, false, false);
    new UnionDoubleSummaryWithModeSketchUDAF().getEvaluator(params);
  }

  @Test(expectedExceptions = { UDFArgumentTypeException.class })
  public void invalidCategorysArg3() throws SemanticException {
    ObjectInspector[] inspectors = new ObjectInspector[] { binaryInspector, intInspector,  structInspector };
    GenericUDAFParameterInfo params = new SimpleGenericUDAFParameterInfo(inspectors, false, false);
    new UnionDoubleSummaryWithModeSketchUDAF().getEvaluator(params);
  }

  @Test(expectedExceptions = { UDFArgumentTypeException.class })
  public void invalidTypeArg3() throws SemanticException {
    ObjectInspector[] inspectors = new ObjectInspector[] { binaryInspector, intInspector,  floatInspector };
    GenericUDAFParameterInfo params = new SimpleGenericUDAFParameterInfo(inspectors, false, false);
    new UnionDoubleSummaryWithModeSketchUDAF().getEvaluator(params);
  }

  // PARTIAL1 mode (Map phase in Map-Reduce): iterate + terminatePartial
  @Test
  public void partial1ModeDefaultParams() throws Exception {
    ObjectInspector[] inspectors = new ObjectInspector[] { binaryInspector };
    GenericUDAFParameterInfo info = new SimpleGenericUDAFParameterInfo(inspectors, false, false);
    try (GenericUDAFEvaluator eval = new UnionDoubleSummaryWithModeSketchUDAF().getEvaluator(info)) {
      ObjectInspector resultInspector = eval.init(Mode.PARTIAL1, inspectors);
      checkIntermediateResultInspector(resultInspector);
  
      @SuppressWarnings("unchecked")
      State<DoubleSummary> state = (State<DoubleSummary>) eval.getNewAggregationBuffer();
  
      UpdatableSketch<Double, DoubleSummary> sketch1 = new UpdatableSketchBuilder<Double, DoubleSummary>(new DoubleSummaryFactory()).build();
      sketch1.update(1, 1.0);
      sketch1.update(2, 2.0);
      eval.iterate(state, new Object[] {new BytesWritable(sketch1.compact().toByteArray())});
  
      UpdatableSketch<Double, DoubleSummary> sketch2 = new UpdatableSketchBuilder<Double, DoubleSummary>(new DoubleSummaryFactory()).build();
      sketch2.update(1, 2.0);
      sketch2.update(2, 1.0);
      eval.iterate(state, new Object[] {new BytesWritable(sketch2.compact().toByteArray())});
  
      Object result = eval.terminatePartial(state);
      Assert.assertNotNull(result);
      Assert.assertTrue(result instanceof List);
      List<?> r = (List<?>) result;
      Assert.assertEquals(r.size(), 3);
      Assert.assertEquals(((IntWritable) r.get(0)).get(), DEFAULT_NOMINAL_ENTRIES);
      Assert.assertEquals(((Text) r.get(1)).toString(), DoubleSummary.Mode.Sum.toString());
      Sketch<DoubleSummary> resultSketch = Sketches.heapifySketch(
          Memory.wrap(((BytesWritable) r.get(2)).getBytes()), new DoubleSummaryDeserializer());
      Assert.assertEquals(resultSketch.getEstimate(), 2.0);
      SketchIterator<DoubleSummary> it = resultSketch.iterator();
      while (it.next()) {
        Assert.assertEquals(it.getSummary().getValue(), 3.0);
      }
    }
  }

  @Test
  public void partial1ModeExplicitParams() throws Exception {
    ObjectInspector[] inspectors = new ObjectInspector[] { binaryInspector, intInspector, stringInspector };
    GenericUDAFParameterInfo info = new SimpleGenericUDAFParameterInfo(inspectors, false, false);
    try (GenericUDAFEvaluator eval = new UnionDoubleSummaryWithModeSketchUDAF().getEvaluator(info)) {
      ObjectInspector resultInspector = eval.init(Mode.PARTIAL1, inspectors);
      checkIntermediateResultInspector(resultInspector);
  
      final int nomNumEntries = 8;
      @SuppressWarnings("unchecked")
      State<DoubleSummary> state = (State<DoubleSummary>) eval.getNewAggregationBuffer();
  
      UpdatableSketch<Double, DoubleSummary> sketch1 =
          new UpdatableSketchBuilder<Double, DoubleSummary>(new DoubleSummaryFactory()).setNominalEntries(nomNumEntries).build();
      sketch1.update(1, 1.0);
      sketch1.update(2, 2.0);
      eval.iterate(state, new Object[] {new BytesWritable(sketch1.compact().toByteArray()),
          new IntWritable(nomNumEntries), new Text("Max")});
  
      UpdatableSketch<Double, DoubleSummary> sketch2 =
          new UpdatableSketchBuilder<Double, DoubleSummary>(new DoubleSummaryFactory()).setNominalEntries(nomNumEntries).build();
      sketch2.update(1, 2.0);
      sketch2.update(2, 1.0);
      eval.iterate(state, new Object[] {new BytesWritable(sketch2.compact().toByteArray()),
          new IntWritable(nomNumEntries), new Text("Max")});
  
      Object result = eval.terminatePartial(state);
      Assert.assertNotNull(result);
      Assert.assertTrue(result instanceof List);
      List<?> r = (List<?>) result;
      Assert.assertEquals(r.size(), 3);
      Assert.assertEquals(((IntWritable) r.get(0)).get(), nomNumEntries);
      Assert.assertEquals(((Text) r.get(1)).toString(), DoubleSummary.Mode.Max.toString());
      Sketch<DoubleSummary> resultSketch = Sketches.heapifySketch(
          Memory.wrap(((BytesWritable) r.get(2)).getBytes()), new DoubleSummaryDeserializer());
      Assert.assertEquals(resultSketch.getEstimate(), 2.0);
      SketchIterator<DoubleSummary> it = resultSketch.iterator();
      while (it.next()) {
        Assert.assertEquals(it.getSummary().getValue(), 2.0);
      }
    }
  }

  // PARTIAL2 mode (Combine phase in Map-Reduce): merge + terminatePartial
  @Test
  public void partial2Mode() throws Exception {
    ObjectInspector[] inspectors = new ObjectInspector[] { binaryInspector };
    GenericUDAFParameterInfo info = new SimpleGenericUDAFParameterInfo(inspectors, false, false);
    try (GenericUDAFEvaluator eval = new UnionDoubleSummaryWithModeSketchUDAF().getEvaluator(info)) {
      ObjectInspector resultInspector = eval.init(Mode.PARTIAL2, new ObjectInspector[] {structInspector});
      checkIntermediateResultInspector(resultInspector);
  
      @SuppressWarnings("unchecked")
      State<DoubleSummary> state = (State<DoubleSummary>) eval.getNewAggregationBuffer();
  
      UpdatableSketch<Double, DoubleSummary> sketch1 = new UpdatableSketchBuilder<Double, DoubleSummary>(new DoubleSummaryFactory()).build();
      sketch1.update(1, 1.0);
      sketch1.update(2, 2.0);
      eval.merge(state, Arrays.asList(
        new IntWritable(DEFAULT_NOMINAL_ENTRIES),
        new Text("Sum"),
        new BytesWritable(sketch1.compact().toByteArray()))
      );
  
      UpdatableSketch<Double, DoubleSummary> sketch2 = new UpdatableSketchBuilder<Double, DoubleSummary>(new DoubleSummaryFactory()).build();
      sketch2.update(1, 2.0);
      sketch2.update(2, 1.0);
      eval.merge(state, Arrays.asList(
        new IntWritable(DEFAULT_NOMINAL_ENTRIES),
        new Text("Sum"),
        new BytesWritable(sketch2.compact().toByteArray()))
      );
  
      Object result = eval.terminatePartial(state);
      Assert.assertNotNull(result);
      Assert.assertTrue(result instanceof List);
      List<?> r = (List<?>) result;
      Assert.assertEquals(r.size(), 3);
      Assert.assertEquals(((IntWritable) r.get(0)).get(), DEFAULT_NOMINAL_ENTRIES);
      Assert.assertEquals(((Text) r.get(1)).toString(), DoubleSummary.Mode.Sum.toString());
      Sketch<DoubleSummary> resultSketch = Sketches.heapifySketch(
          Memory.wrap(((BytesWritable) r.get(2)).getBytes()), new DoubleSummaryDeserializer());
      Assert.assertEquals(resultSketch.getEstimate(), 2.0);
      SketchIterator<DoubleSummary> it = resultSketch.iterator();
      while (it.next()) {
        Assert.assertEquals(it.getSummary().getValue(), 3.0);
      }
  
      eval.reset(state);
      result = eval.terminate(state);
      Assert.assertNull(result);
    }
  }

  // FINAL mode (Reduce phase in Map-Reduce): merge + terminate
  @Test
  public void finalMode() throws Exception {
    ObjectInspector[] inspectors = new ObjectInspector[] { binaryInspector };
    GenericUDAFParameterInfo info = new SimpleGenericUDAFParameterInfo(inspectors, false, false);
    try (GenericUDAFEvaluator eval = new UnionDoubleSummaryWithModeSketchUDAF().getEvaluator(info)) {
      ObjectInspector resultInspector = eval.init(Mode.FINAL, new ObjectInspector[] {structInspector});
      DataToDoubleSummarySketchUDAFTest.checkFinalResultInspector(resultInspector);
  
      @SuppressWarnings("unchecked")
      State<DoubleSummary> state = (State<DoubleSummary>) eval.getNewAggregationBuffer();
  
      UpdatableSketch<Double, DoubleSummary> sketch1 = new UpdatableSketchBuilder<Double, DoubleSummary>(new DoubleSummaryFactory()).build();
      sketch1.update(1, 1.0);
      sketch1.update(2, 2.0);
      eval.merge(state, Arrays.asList(
        new IntWritable(DEFAULT_NOMINAL_ENTRIES),
        new Text("Min"),
        new BytesWritable(sketch1.compact().toByteArray()))
      );
  
      UpdatableSketch<Double, DoubleSummary> sketch2 = new UpdatableSketchBuilder<Double, DoubleSummary>(new DoubleSummaryFactory()).build();
      sketch2.update(1, 2.0);
      sketch2.update(2, 1.0);
      eval.merge(state, Arrays.asList(
        new IntWritable(DEFAULT_NOMINAL_ENTRIES),
        new Text("Min"),
        new BytesWritable(sketch2.compact().toByteArray()))
      );
  
      Object result = eval.terminate(state);
      Assert.assertNotNull(result);
      Assert.assertTrue(result instanceof BytesWritable);
      Sketch<DoubleSummary> resultSketch = Sketches.heapifySketch(
          Memory.wrap(((BytesWritable) result).getBytes()), new DoubleSummaryDeserializer());
      Assert.assertEquals(resultSketch.getEstimate(), 2.0);
      SketchIterator<DoubleSummary> it = resultSketch.iterator();
      while (it.next()) {
        Assert.assertEquals(it.getSummary().getValue(), 1.0);
      }
    }
  }

  // COMPLETE mode (single mode, alternative to MapReduce): iterate + terminate
  @Test
  public void completeModeDefaultParams() throws Exception {
    ObjectInspector[] inspectors = new ObjectInspector[] { binaryInspector };
    GenericUDAFParameterInfo info = new SimpleGenericUDAFParameterInfo(inspectors, false, false);
    try (GenericUDAFEvaluator eval = new UnionDoubleSummaryWithModeSketchUDAF().getEvaluator(info)) {
      ObjectInspector resultInspector = eval.init(Mode.COMPLETE, inspectors);
      DataToDoubleSummarySketchUDAFTest.checkFinalResultInspector(resultInspector);
  
      @SuppressWarnings("unchecked")
      State<DoubleSummary> state = (State<DoubleSummary>) eval.getNewAggregationBuffer();
  
      UpdatableSketch<Double, DoubleSummary> sketch1 = new UpdatableSketchBuilder<Double, DoubleSummary>(new DoubleSummaryFactory()).build();
      sketch1.update(1, 1.0);
      sketch1.update(2, 2.0);
      eval.iterate(state, new Object[] {new BytesWritable(sketch1.compact().toByteArray())});
  
      UpdatableSketch<Double, DoubleSummary> sketch2 = new UpdatableSketchBuilder<Double, DoubleSummary>(new DoubleSummaryFactory()).build();
      sketch2.update(1, 2.0);
      sketch2.update(2, 1.0);
      eval.iterate(state, new Object[] {new BytesWritable(sketch2.compact().toByteArray())});
  
      Object result = eval.terminate(state);
      Assert.assertNotNull(result);
      Assert.assertTrue(result instanceof BytesWritable);
      Sketch<DoubleSummary> resultSketch = Sketches.heapifySketch(
          Memory.wrap(((BytesWritable) result).getBytes()), new DoubleSummaryDeserializer());
      Assert.assertEquals(resultSketch.getEstimate(), 2.0);
      SketchIterator<DoubleSummary> it = resultSketch.iterator();
      while (it.next()) {
        Assert.assertEquals(it.getSummary().getValue(), 3.0);
      }
  
      eval.reset(state);
      result = eval.terminate(state);
      Assert.assertNull(result);
    }
  }

  @Test
  public void completeModeExplicitNominalNumEntries() throws Exception {
    ObjectInspector[] inspectors = new ObjectInspector[] { binaryInspector, intInspector };
    GenericUDAFParameterInfo info = new SimpleGenericUDAFParameterInfo(inspectors, false, false);
    try (GenericUDAFEvaluator eval = new UnionDoubleSummaryWithModeSketchUDAF().getEvaluator(info)) {
      ObjectInspector resultInspector = eval.init(Mode.COMPLETE, inspectors);
      DataToDoubleSummarySketchUDAFTest.checkFinalResultInspector(resultInspector);
  
      final int nomNumEntries = 8;
      @SuppressWarnings("unchecked")
      State<DoubleSummary> state = (State<DoubleSummary>) eval.getNewAggregationBuffer();
  
      UpdatableSketch<Double, DoubleSummary> sketch1 =
          new UpdatableSketchBuilder<Double, DoubleSummary>(new DoubleSummaryFactory()).setNominalEntries(nomNumEntries).build();
      sketch1.update(1, 1.0);
      sketch1.update(2, 2.0);
      eval.iterate(state, new Object[] {new BytesWritable(sketch1.compact().toByteArray()),
          new IntWritable(nomNumEntries)});
  
      UpdatableSketch<Double, DoubleSummary> sketch2 =
          new UpdatableSketchBuilder<Double, DoubleSummary>(new DoubleSummaryFactory()).setNominalEntries(nomNumEntries).build();
      sketch2.update(1, 2.0);
      sketch2.update(2, 1.0);
      eval.iterate(state, new Object[] {new BytesWritable(sketch2.compact().toByteArray()),
          new IntWritable(nomNumEntries)});
  
      Object result = eval.terminate(state);
      Assert.assertNotNull(result);
      Assert.assertTrue(result instanceof BytesWritable);
      Sketch<DoubleSummary> resultSketch = Sketches.heapifySketch(
          Memory.wrap(((BytesWritable) result).getBytes()), new DoubleSummaryDeserializer());
      Assert.assertEquals(resultSketch.getEstimate(), 2.0);
      SketchIterator<DoubleSummary> it = resultSketch.iterator();
      while (it.next()) {
        Assert.assertEquals(it.getSummary().getValue(), 3.0);
      }
  
      eval.reset(state);
      result = eval.terminate(state);
      Assert.assertNull(result);
    }
  }

  @Test
  public void completeModeExplicitNominalNumEntriesAndMode() throws Exception {
    ObjectInspector[] inspectors = new ObjectInspector[] { binaryInspector, intInspector, stringInspector };
    GenericUDAFParameterInfo info = new SimpleGenericUDAFParameterInfo(inspectors, false, false);
    try (GenericUDAFEvaluator eval = new UnionDoubleSummaryWithModeSketchUDAF().getEvaluator(info)) {
      ObjectInspector resultInspector = eval.init(Mode.COMPLETE, inspectors);
      DataToDoubleSummarySketchUDAFTest.checkFinalResultInspector(resultInspector);
  
      final int nomNumEntries = 8;
      @SuppressWarnings("unchecked")
      State<DoubleSummary> state = (State<DoubleSummary>) eval.getNewAggregationBuffer();
  
      UpdatableSketch<Double, DoubleSummary> sketch1 =
          new UpdatableSketchBuilder<Double, DoubleSummary>(new DoubleSummaryFactory()).setNominalEntries(nomNumEntries).build();
      sketch1.update(1, 1.0);
      sketch1.update(2, 2.0);
      eval.iterate(state, new Object[] {new BytesWritable(sketch1.compact().toByteArray()),
          new IntWritable(nomNumEntries), new Text("Max")});
  
      UpdatableSketch<Double, DoubleSummary> sketch2 =
          new UpdatableSketchBuilder<Double, DoubleSummary>(new DoubleSummaryFactory()).setNominalEntries(nomNumEntries).build();
      sketch2.update(1, 2.0);
      sketch2.update(2, 1.0);
      eval.iterate(state, new Object[] {new BytesWritable(sketch2.compact().toByteArray()),
          new IntWritable(nomNumEntries), new Text("Max")});
  
      Object result = eval.terminate(state);
      Assert.assertNotNull(result);
      Assert.assertTrue(result instanceof BytesWritable);
      Sketch<DoubleSummary> resultSketch = Sketches.heapifySketch(
          Memory.wrap(((BytesWritable) result).getBytes()), new DoubleSummaryDeserializer());
      Assert.assertEquals(resultSketch.getEstimate(), 2.0);
      SketchIterator<DoubleSummary> it = resultSketch.iterator();
      while (it.next()) {
        Assert.assertEquals(it.getSummary().getValue(), 2.0);
      }
  
      eval.reset(state);
      result = eval.terminate(state);
      Assert.assertNull(result);
    }
  }

  static void checkIntermediateResultInspector(ObjectInspector resultInspector) {
    Assert.assertNotNull(resultInspector);
    Assert.assertEquals(resultInspector.getCategory(), ObjectInspector.Category.STRUCT);
    StructObjectInspector structResultInspector = (StructObjectInspector) resultInspector;
    List<?> fields = structResultInspector.getAllStructFieldRefs();
    Assert.assertEquals(fields.size(), 3);

    ObjectInspector inspector1 = ((StructField) fields.get(0)).getFieldObjectInspector();
    Assert.assertEquals(inspector1.getCategory(), ObjectInspector.Category.PRIMITIVE);
    PrimitiveObjectInspector primitiveInspector1 = (PrimitiveObjectInspector) inspector1;
    Assert.assertEquals(primitiveInspector1.getPrimitiveCategory(), PrimitiveCategory.INT);

    ObjectInspector inspector2 = ((StructField) fields.get(1)).getFieldObjectInspector();
    Assert.assertEquals(inspector2.getCategory(), ObjectInspector.Category.PRIMITIVE);
    PrimitiveObjectInspector primitiveInspector2 = (PrimitiveObjectInspector) inspector2;
    Assert.assertEquals(primitiveInspector2.getPrimitiveCategory(), PrimitiveCategory.STRING);

    ObjectInspector inspector3 = ((StructField) fields.get(2)).getFieldObjectInspector();
    Assert.assertEquals(inspector3.getCategory(), ObjectInspector.Category.PRIMITIVE);
    PrimitiveObjectInspector primitiveInspector3 = (PrimitiveObjectInspector) inspector3;
    Assert.assertEquals(primitiveInspector3.getPrimitiveCategory(), PrimitiveCategory.BINARY);
  }

}

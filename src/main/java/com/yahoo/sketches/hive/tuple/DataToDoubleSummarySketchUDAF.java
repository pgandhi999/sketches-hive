/*
 * Copyright 2016, Yahoo! Inc.
 * Licensed under the terms of the Apache License 2.0. See LICENSE file at the project root for terms.
 */

package com.yahoo.sketches.hive.tuple;

import org.apache.hadoop.hive.ql.exec.Description;
import org.apache.hadoop.hive.ql.parse.SemanticException;
import org.apache.hadoop.hive.ql.udf.generic.GenericUDAFEvaluator;
import org.apache.hadoop.hive.ql.udf.generic.GenericUDAFParameterInfo;
import org.apache.hadoop.hive.serde2.objectinspector.ObjectInspector;
import org.apache.hadoop.hive.serde2.objectinspector.PrimitiveObjectInspector.PrimitiveCategory;

import com.yahoo.sketches.tuple.DoubleSummary;
import com.yahoo.sketches.tuple.DoubleSummaryDeserializer;
import com.yahoo.sketches.tuple.DoubleSummaryFactory;
import com.yahoo.sketches.tuple.DoubleSummarySetOperations;
import com.yahoo.sketches.tuple.SummaryDeserializer;
import com.yahoo.sketches.tuple.SummaryFactory;
import com.yahoo.sketches.tuple.SummarySetOperations;

/**
 * This simple implementation is to give an example of a concrete UDAF based on the abstract
 * DataToSketchUDAF if no extra arguments are needed. The same functionality is included into
 * DataToDoubleSummaryWithModeSketchUDAF with the default summary mode of Sum, but the
 * implementation is more complex because of the extra argument.
 */

@Description(
  name = "DataToDoubleSummarySketch",
  value = "_FUNC_(key, double value, nominal number of entries, sampling probability)",
  extended = "Returns a Sketch<DoubleSummary> as a binary blob that can be operated on by other"
    + " tuple sketch related functions. The nominal number of entries is optional, must be a power"
    + " of 2 and controls the relative error expected from the sketch."
    + " A number of 16384 can be expected to yield errors of roughly +-1.5% in the estimation of"
    + " uniques. The default number is defined in the sketches-core library, and at the time of this"
    + " writing was 4096 (about 3% error)."
    + " The sampling probability is optional and must be from 0 to 1. The default is 1 (no sampling)")
public class DataToDoubleSummarySketchUDAF extends DataToSketchUDAF {

  @Override
  public GenericUDAFEvaluator getEvaluator(final GenericUDAFParameterInfo info) throws SemanticException {
    super.getEvaluator(info);
    final ObjectInspector[] inspectors = info.getParameterObjectInspectors();
    ObjectInspectorValidator.validateGivenPrimitiveCategory(inspectors[1], 1, PrimitiveCategory.DOUBLE);
    return createEvaluator();
  }

  @Override
  public GenericUDAFEvaluator createEvaluator() {
    return new DataToDoubleSummarySketchEvaluator();
  }

  static class DataToDoubleSummarySketchEvaluator extends DataToSketchEvaluator<Double, DoubleSummary> {

    private static final SummaryDeserializer<DoubleSummary> SUMMARY_DESERIALIZER = new DoubleSummaryDeserializer();
    private static final SummaryFactory<DoubleSummary> SUMMARY_FACTORY = new DoubleSummaryFactory();
    private static final SummarySetOperations<DoubleSummary> SUMMARY_SET_OPS = new DoubleSummarySetOperations();

    @Override
    protected SummaryDeserializer<DoubleSummary> getSummaryDeserializer() {
      return SUMMARY_DESERIALIZER;
    }

    @Override
    protected SummaryFactory<DoubleSummary> getSummaryFactory(final Object[] data) {
      return SUMMARY_FACTORY;
    }

    @Override
    protected SummarySetOperations<DoubleSummary> getSummarySetOperationsForIterate(final Object[] data) {
      return null; // not needed for building sketches
    }

    @Override
    protected SummarySetOperations<DoubleSummary> getSummarySetOperationsForMerge(final Object data) {
      return SUMMARY_SET_OPS;
    }

  }

}

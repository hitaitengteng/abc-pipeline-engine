/**
 * This file is part of the RapidMiner Belt project.
 * Copyright (C) 2017-2019 RapidMiner GmbH
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General
 * Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any
 * later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License for more
 * details.
 *
 * You should have received a copy of the GNU Affero General Public License along with this program. If not, see 
 * https://www.gnu.org/licenses/.
 */

package base.operators.belt.transform;

import base.operators.belt.buffer.*;
import base.operators.belt.column.Column;
import base.operators.belt.execution.Context;
import base.operators.belt.execution.Workload;
import base.operators.belt.reader.CategoricalRow;
import base.operators.belt.reader.MixedRow;
import base.operators.belt.reader.NumericRow;
import base.operators.belt.reader.ObjectRow;
import base.operators.belt.util.IntegerFormats;

import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.Objects;
import java.util.function.*;


/**
 * Supplies transformation methods (different kinds of apply and reduce methods) starting from the given {@link
 * #transformationColumns}. Depending on how the transformation columns should be read (numeric, categorical or
 * objects) the right method (e.g. applyNumericTo or reduceCategorical) must be selected.
 *
 * @author Gisa Meier
 */
public final class RowTransformer {

	/**
	 * Message for {@code null} contexts
	 */
	private static final String MESSAGE_CONTEXT_NULL = "Context must not be null";

	/**
	 * Message for {@code null} reducers
	 */
	private static final String MESSAGE_REDUCER_NULL = "Reducer function must not be null";

	/**
	 * Message for {@code null} operators
	 */
	private static final String MESSAGE_OPERATOR_NULL = "Mapping function must not be null";

	/**
	 * Message for {@code null} operators
	 */
	private static final String MESSAGE_SUPPLIER_NULL = "Supplier function must not be null";

	/**
	 * Message for {@code null} operators
	 */
	private static final String MESSAGE_COMBINER_NULL = "Combiner function must not be null";

	/**
	 * Default progress callback which does nothing at all.
	 */
	private static final DoubleConsumer NOOP_CALLBACK = i -> {};

	private final List<Column> transformationColumns;

	private Workload workload = Workload.DEFAULT;

	private DoubleConsumer callback = NOOP_CALLBACK;

	/**
	 * Creates a new starting point for a transformation based on the given columns
	 *
	 * @param transformationColumns
	 * 		the columns to start the transformation from, must be all of the same length
	 * @param validate
	 * 		whether the input columns require validation
	 * @throws NullPointerException
	 * 		if the list is or contains {@code null}
	 * @throws IllegalArgumentException
	 * 		if the list is empty
	 */
	public RowTransformer(List<Column> transformationColumns, boolean validate) {
		if (validate) {
			checkColumns(transformationColumns);
		}
		this.transformationColumns = transformationColumns;
	}

	/**
	 * Creates a new starting point for a transformation based on the given columns
	 *
	 * @param transformationColumns
	 * 		the columns to start the transformation from, must be all of the same length
	 * @throws NullPointerException
	 * 		if the list is or contains {@code null}
	 * @throws IllegalArgumentException
	 * 		if the list is empty
	 */
	public RowTransformer(List<Column> transformationColumns) {
		// Public method, always validate the input columns!
		this(transformationColumns, true);
	}

	/**
	 * Ensures that the columns list is not {@code null} or empty and contains no {@code null} entries.
	 */
	private void checkColumns(List<Column> columns) {
		Objects.requireNonNull(columns, "Transformation columns list must not be null.");
		if (columns.isEmpty()) {
			throw new IllegalArgumentException("Transformation columns list must not be empty.");
		}
		for (Column column : columns) {
			Objects.requireNonNull(column, "Transformation columns list must not contain null.");
		}
	}

	/**
	 * Specifies the expected {@link Workload} per data point.
	 *
	 * @param workload
	 * 		the workload
	 * @return this transform
	 */
	public RowTransformer workload(Workload workload) {
		this.workload = Objects.requireNonNull(workload, "Workload must not be null");
		return this;
	}

	/**
	 * Specifies a progress callback function. The progress is reported as single {@code double} value where zero and
	 * one indicate 0% and 100% progress respectively. The value {@link Double#NaN} is used to indicate indeterminate
	 * states.
	 *
	 * @param callback
	 * 		the progress callback
	 * @return this transform
	 */
	public RowTransformer callback(DoubleConsumer callback) {
		this.callback = Objects.requireNonNull(callback, "Callback must not be null");
		return this;
	}

	/**
	 * Applies the given operator of arbitrary arity to the numeric readable transformation columns returning the
	 * result in a new real {@link NumericBuffer}. Depending on the input size and the specified workload per data-point,
	 * the computation might be performed in parallel.
	 *
	 * @param operator
	 * 		the operator to apply to each tuple (see {@link NumericRow})
	 * @param context
	 * 		the execution context to use
	 * @return a buffer containing the result of the mapping
	 * @throws NullPointerException
	 * 		if any of the arguments is {@code null}
	 * @throws UnsupportedOperationException
	 * 		if any of the transformation columns is not {@link Column.Capability#NUMERIC_READABLE}
	 */
	public NumericBuffer applyNumericToReal(ToDoubleFunction<NumericRow> operator, Context context) {
		Objects.requireNonNull(context, MESSAGE_CONTEXT_NULL);
		Objects.requireNonNull(operator, MESSAGE_OPERATOR_NULL);
		return new ParallelExecutor<>(new ApplierNNumericToNumeric(transformationColumns, operator, false),
						workload, callback).execute(context);
	}

	/**
	 * Applies the given operator of arbitrary arity to the numeric-readable transformation columns returning the
	 * result in a new integer {@link NumericBuffer}. Depending on the input size and the specified workload per
	 * data-point, the computation might be performed in parallel.
	 *
	 * @param operator
	 * 		the operator to apply to each tuple (see {@link NumericRow})
	 * @param context
	 * 		the execution context to use
	 * @return a buffer containing the result of the mapping
	 * @throws NullPointerException
	 * 		if any of the arguments is {@code null}
	 * @throws UnsupportedOperationException
	 * 		if any of the transformation columns is not {@link Column.Capability#NUMERIC_READABLE}
	 */
	public NumericBuffer applyNumericToInteger(ToDoubleFunction<NumericRow> operator, Context context) {
		Objects.requireNonNull(context, MESSAGE_CONTEXT_NULL);
		Objects.requireNonNull(operator, MESSAGE_OPERATOR_NULL);
		return new ParallelExecutor<>(new ApplierNNumericToNumeric(transformationColumns, operator, true),
						workload, callback).execute(context);
	}

	/**
	 * Applies the given operator of arbitrary arity to the categorical transformation columns returning the result
	 * in a new real {@link NumericBuffer}. Depending on the input size and the specified workload per data-point, the
	 * computation might be performed in parallel.
	 *
	 * @param operator
	 * 		the operator to apply to each tuple (see {@link CategoricalRow})
	 * @param context
	 * 		the execution context to use
	 * @return a buffer containing the result of the mapping
	 * @throws NullPointerException
	 * 		if any of the arguments is {@code null}
	 * @throws UnsupportedOperationException
	 * 		if any of the transformation columns is not {@link Column.Category#CATEGORICAL}
	 */
	public NumericBuffer applyCategoricalToReal(ToDoubleFunction<CategoricalRow> operator, Context context) {
		Objects.requireNonNull(context, MESSAGE_CONTEXT_NULL);
		Objects.requireNonNull(operator, MESSAGE_OPERATOR_NULL);
		return new ParallelExecutor<>(new ApplierNCategoricalToNumeric(transformationColumns, operator,
						false),
						workload, callback).execute(context);
	}

	/**
	 * Applies the given operator of arbitrary arity to the categorical transformation columns returning the result
	 * in a new integer {@link NumericBuffer}. Depending on the input size and the specified workload per data-point,
	 * the computation might be performed in parallel.
	 *
	 * @param operator
	 * 		the operator to apply to each tuple (see {@link CategoricalRow})
	 * @param context
	 * 		the execution context to use
	 * @return a buffer containing the result of the mapping
	 * @throws NullPointerException
	 * 		if any of the arguments is {@code null}
	 * @throws UnsupportedOperationException
	 * 		if any of the transformation columns is not {@link Column.Category#CATEGORICAL}
	 */
	public NumericBuffer applyCategoricalToInteger(ToDoubleFunction<CategoricalRow> operator, Context context) {
		Objects.requireNonNull(context, MESSAGE_CONTEXT_NULL);
		Objects.requireNonNull(operator, MESSAGE_OPERATOR_NULL);
		return new ParallelExecutor<>(new ApplierNCategoricalToNumeric(transformationColumns, operator, true),
						workload, callback).execute(context);
	}

	/**
	 * Applies the given operator of arbitrary arity to the object-readable transformation columns returning the result
	 * in a new real {@link NumericBuffer}. Depending on the input size and the specified workload per data-point, the
	 * computation might be performed in parallel.
	 *
	 * @param operator
	 * 		the operator to apply to each tuple (see {@link ObjectRow})
	 * @param type
	 * 		the type of the objects in the columns
	 * @param context
	 * 		the execution context to use
	 * @param <T>
	 * 		type of objects in the columns
	 * @return a buffer containing the result of the mapping
	 * @throws NullPointerException
	 * 		if any of the arguments is {@code null}
	 * @throws UnsupportedOperationException
	 * 		if any of the transformation columns is not {@link Column.Capability#OBJECT_READABLE}
	 * @throws IllegalArgumentException
	 * 		if the objects in the transformation columns cannot be read as the given type
	 */
	public <T> NumericBuffer applyObjectToReal(Class<T> type, ToDoubleFunction<ObjectRow<T>> operator, Context context) {
		Objects.requireNonNull(context, MESSAGE_CONTEXT_NULL);
		Objects.requireNonNull(operator, MESSAGE_OPERATOR_NULL);
		return new ParallelExecutor<>(new ApplierNObjectToNumeric<>(transformationColumns, type, operator, false),
						workload, callback).execute(context);
	}

	/**
	 * Applies the given operator of arbitrary arity to the object-readable transformation columns returning the result
	 * in a new integer {@link NumericBuffer}. Depending on the input size and the specified workload per data-point,
	 * the computation might be performed in parallel.
	 *
	 * @param operator
	 * 		the operator to apply to each tuple (see {@link ObjectRow})
	 * @param type
	 * 		the type of the objects in the columns
	 * @param context
	 * 		the execution context to use
	 * @param <T>
	 * 		type of objects in the columns
	 * @return a buffer containing the result of the mapping
	 * @throws NullPointerException
	 * 		if any of the arguments is {@code null}
	 * @throws UnsupportedOperationException
	 * 		if any of the transformation columns is not {@link Column.Capability#OBJECT_READABLE}
	 * @throws IllegalArgumentException
	 * 		if the objects in the transformation columns cannot be read as the given type
	 */
	public <T> NumericBuffer applyObjectToInteger(Class<T> type, ToDoubleFunction<ObjectRow<T>> operator,
												  Context context) {
		Objects.requireNonNull(context, MESSAGE_CONTEXT_NULL);
		Objects.requireNonNull(operator, MESSAGE_OPERATOR_NULL);
		return new ParallelExecutor<>(new ApplierNObjectToNumeric<>(transformationColumns, type, operator, true),
						workload, callback).execute(context);
	}

	/**
	 * Applies the given operator of arbitrary arity to the transformation columns returning the result in a new real
	 * {@link NumericBuffer}. Depending on the input size and the specified workload per data-point, the computation
	 * might be performed in parallel.
	 *
	 * <p>This method uses an operator starting from a {@link MixedRow} which allows to read the row in different
	 * ways. This is not as performant as reading the row only in one specific way. So whenever possible it is
	 * recommended to use the methods {@link #applyNumericToReal}, {@link #applyCategoricalToReal} or {@link
	 * #applyObjectToReal} instead.
	 *
	 * @param operator
	 * 		the operator to apply to each tuple (see {@link MixedRow})
	 * @param context
	 * 		the execution context to use
	 * @return a buffer containing the result of the mapping
	 * @throws NullPointerException
	 * 		if any of the arguments is {@code null}
	 */
	public NumericBuffer applyMixedToReal(ToDoubleFunction<MixedRow> operator, Context context) {
		Objects.requireNonNull(context, MESSAGE_CONTEXT_NULL);
		Objects.requireNonNull(operator, MESSAGE_OPERATOR_NULL);
		return new ParallelExecutor<>(new ApplierMixedToNumeric(transformationColumns, operator, false),
						workload, callback).execute(context);
	}

	/**
	 * Applies the given operator of arbitrary arity to the transformation columns returning the result in a new
	 * integer {@link NumericBuffer}. Depending on the input size and the specified workload per data-point, the
	 * computation might be performed in parallel.
	 *
	 * <p>This method uses an operator starting from a {@link MixedRow} which allows to read the row in different
	 * ways. This is not as performant as reading the row only in one specific way. So whenever possible it is
	 * recommended to use the methods {@link #applyNumericToInteger}, {@link #applyCategoricalToInteger} or {@link
	 * #applyObjectToInteger} instead.
	 *
	 * @param operator
	 * 		the operator to apply to each tuple (see {@link MixedRow})
	 * @param context
	 * 		the execution context to use
	 * @return a buffer containing the result of the mapping
	 * @throws NullPointerException
	 * 		if any of the arguments is {@code null}
	 */
	public NumericBuffer applyMixedToInteger(ToDoubleFunction<MixedRow> operator, Context context) {
		Objects.requireNonNull(context, MESSAGE_CONTEXT_NULL);
		Objects.requireNonNull(operator, MESSAGE_OPERATOR_NULL);
		return new ParallelExecutor<>(new ApplierMixedToNumeric(transformationColumns, operator, true),
						workload, callback).execute(context);
	}

	/**
	 * Applies the given operator of arbitrary arity to the object-readable transformation columns returning the result
	 * in a new {@link Int32CategoricalBuffer}. Depending on the input size and the specified workload per data-point,
	 * the computation might be performed in parallel.
	 *
	 * @param operator
	 * 		the operator to apply to each tuple (see {@link ObjectRow})
	 * @param type
	 * 		the type of the objects in the columns
	 * @param context
	 * 		the execution context to use
	 * @param <T>
	 * 		type of the result
	 * @param <R>
	 * 		type of objects in the columns
	 * @return a buffer containing the result of the mapping
	 * @throws NullPointerException
	 * 		if any of the arguments is {@code null}
	 * @throws UnsupportedOperationException
	 * 		if any of the transformation columns is not {@link Column.Capability#OBJECT_READABLE}
	 * @throws IllegalArgumentException
	 * 		if the objects in the transformation columns cannot be read as the given type
	 */
	public <R, T> Int32CategoricalBuffer<T> applyObjectToCategorical(Class<R> type, Function<ObjectRow<R>, T> operator,
																	 Context context) {
		Objects.requireNonNull(context, MESSAGE_CONTEXT_NULL);
		Objects.requireNonNull(operator, MESSAGE_OPERATOR_NULL);
		return (Int32CategoricalBuffer<T>) new ParallelExecutor<>(
				new ApplierNObjectToCategorical<>(transformationColumns, type, operator,
						IntegerFormats.Format.SIGNED_INT32), workload, callback).execute(context);
	}

	/**
	 * Applies the given operator of arbitrary arity to the object-readable transformation columns returning the result
	 * in a new {@link CategoricalBuffer}. The format of the target buffer is derived from given
	 * maxNumberOfValues parameter. Depending on the input size and the specified workload per data-point, the
	 * computation might be performed in parallel.
	 *
	 * @param type
	 * 		the type of the objects in the columns
	 * @param operator
	 * 		the operator to apply to each tuple (see {@link ObjectRow})
	 * @param maxNumberOfValues
	 * 		the maximal number of different values generated by the operator. Decides the format of the buffer.
	 * @param context
	 * 		the execution context to use
	 * @param <T>
	 * 		type of the result
	 * @param <R>
	 * 		type of objects in the columns
	 * @return a buffer containing the result of the mapping
	 * @throws NullPointerException
	 * 		if any of the arguments is {@code null}
	 * @throws UnsupportedOperationException
	 * 		if any of the transformation columns is not {@link Column.Capability#OBJECT_READABLE}
	 * @throws IllegalArgumentException
	 * 		if the objects in the transformation columns cannot be read as the given type or if more different values
	 * 		are	supplied than supported by the buffer format calculated from the maxNumberOfValues
	 */
	public <R, T> CategoricalBuffer<T> applyObjectToCategorical(Class<R> type,
																Function<ObjectRow<R>, T> operator,
																int maxNumberOfValues,
																Context context) {
		Objects.requireNonNull(context, MESSAGE_CONTEXT_NULL);
		Objects.requireNonNull(operator, MESSAGE_OPERATOR_NULL);
		IntegerFormats.Format format = IntegerFormats.Format.findMinimal(maxNumberOfValues);
		return new ParallelExecutor<>(
				new ApplierNObjectToCategorical<>(transformationColumns, type, operator, format),
				workload, callback).execute(context);
	}

	/**
	 * Applies the given operator of arbitrary arity to the categorical transformation columns returning the result
	 * in a new {@link Int32CategoricalBuffer}. Depending on the input size and the specified workload per data-point,
	 * the computation might be performed in parallel.
	 *
	 * @param operator
	 * 		the operator to apply to each tuple (see {@link CategoricalRow})
	 * @param context
	 * 		the execution context to use
	 * @param <T>
	 * 		type of the result
	 * @return a buffer containing the result of the mapping
	 * @throws UnsupportedOperationException
	 * 		if any of the transformation columns is not {@link Column.Category#CATEGORICAL}
	 * @throws IllegalArgumentException
	 * 		if the objects in the transformation columns cannot be read as the given type
	 */
	public <T> Int32CategoricalBuffer<T> applyCategoricalToCategorical(Function<CategoricalRow, T> operator,
																	   Context context) {
		Objects.requireNonNull(context, MESSAGE_CONTEXT_NULL);
		Objects.requireNonNull(operator, MESSAGE_OPERATOR_NULL);
		return (Int32CategoricalBuffer<T>) new ParallelExecutor<>(
				new ApplierNCategoricalToCategorical<>(transformationColumns, operator,
						IntegerFormats.Format.SIGNED_INT32), workload, callback).execute(context);
	}

	/**
	 * Applies the given operator of arbitrary arity to the categorical transformation columns returning the result
	 * in a new {@link CategoricalBuffer}. The format of the target buffer is derived from given maxNumberOfValues
	 * parameter. Depending on the input size and the specified workload per data-point, the computation might be
	 * performed in parallel.
	 *
	 * @param operator
	 * 		the operator to apply to each tuple (see {@link CategoricalRow})
	 * @param maxNumberOfValues
	 * 		the maximal number of different values generated by the operator. Decides the format of the buffer.
	 * @param context
	 * 		the execution context to use
	 * @param <T>
	 * 		type of the result
	 * @return a buffer containing the result of the mapping
	 * @throws NullPointerException
	 * 		if any of the arguments is {@code null}
	 * @throws UnsupportedOperationException
	 * 		if any of the transformation columns is not {@link Column.Category#CATEGORICAL}
	 * @throws IllegalArgumentException
	 * 		if more different values are supplied than supported by the buffer format calculated from the
	 * 		maxNumberOfValues
	 */
	public <T> CategoricalBuffer<T> applyCategoricalToCategorical(Function<CategoricalRow, T> operator,
																  int maxNumberOfValues,
																  Context context) {
		Objects.requireNonNull(context, MESSAGE_CONTEXT_NULL);
		Objects.requireNonNull(operator, MESSAGE_OPERATOR_NULL);
		IntegerFormats.Format format = IntegerFormats.Format.findMinimal(maxNumberOfValues);
		return new ParallelExecutor<>(
				new ApplierNCategoricalToCategorical<>(transformationColumns, operator, format),
				workload, callback).execute(context);
	}


	/**
	 * Applies the given operator of arbitrary arity to the numeric-readable transformation columns returning the
	 * result in a new {@link Int32CategoricalBuffer}. Depending on the input size and the specified workload per
	 * data-point, the computation might be performed in parallel.
	 *
	 * @param operator
	 * 		the operator to apply to each tuple (see {@link NumericRow})
	 * @param context
	 * 		the execution context to use
	 * @param <T>
	 * 		type of the result
	 * @return a buffer containing the result of the mapping
	 * @throws NullPointerException
	 * 		if any of the arguments is {@code null}
	 * @throws UnsupportedOperationException
	 * 		if any of the transformation columns is not {@link Column.Capability#NUMERIC_READABLE}
	 */
	public <T> Int32CategoricalBuffer<T> applyNumericToCategorical(Function<NumericRow, T> operator,
																   Context context) {
		Objects.requireNonNull(context, MESSAGE_CONTEXT_NULL);
		Objects.requireNonNull(operator, MESSAGE_OPERATOR_NULL);
		return (Int32CategoricalBuffer<T>) new ParallelExecutor<>(
				new ApplierNNumericToCategorical<>(transformationColumns, operator,
						IntegerFormats.Format.SIGNED_INT32), workload, callback).execute(context);
	}

	/**
	 * Applies the given operator of arbitrary arity to the numeric-readable transformation columns returning the
	 * result in a new {@link CategoricalBuffer}. The format of the target buffer is derived from given
	 * maxNumberOfValues parameter. Depending on the input size and the specified workload per data-point, the
	 * computation might be performed in parallel.
	 *
	 * @param operator
	 * 		the operator to apply to each tuple (see {@link NumericRow})
	 * @param maxNumberOfValues
	 * 		the maximal number of different values generated by the operator. Decides the format of the buffer.
	 * @param context
	 * 		the execution context to use
	 * @param <T>
	 * 		type of the result
	 * @return a buffer containing the result of the mapping
	 * @throws NullPointerException
	 * 		if any of the arguments is {@code null}
	 * @throws UnsupportedOperationException
	 * 		if any of the transformation columns is not {@link Column.Capability#NUMERIC_READABLE}
	 * @throws IllegalArgumentException
	 * 		if more different values are supplied than supported by the buffer format calculated from the
	 * 		maxNumberOfValues
	 */
	public <T> CategoricalBuffer<T> applyNumericToCategorical(Function<NumericRow, T> operator,
															  int maxNumberOfValues,
															  Context context) {
		Objects.requireNonNull(context, MESSAGE_CONTEXT_NULL);
		Objects.requireNonNull(operator, MESSAGE_OPERATOR_NULL);
		IntegerFormats.Format format = IntegerFormats.Format.findMinimal(maxNumberOfValues);
		return new ParallelExecutor<>(
				new ApplierNNumericToCategorical<>(transformationColumns, operator, format),
				workload, callback).execute(context);
	}


	/**
	 * Applies the given operator of arbitrary arity to the transformation columns returning the result in a new {@link
	 * Int32CategoricalBuffer}. Depending on the input size and the specified workload per data-point, the computation
	 * might be performed in parallel.
	 *
	 * <p>This method uses an operator starting from a {@link MixedRow} which allows to read the row in different
	 * ways. This is not as performant as reading the row only in one specific way. So whenever possible it is
	 * recommended to use the methods {@link #applyNumericToCategorical}, {@link #applyCategoricalToCategorical} or
	 * {@link #applyObjectToCategorical} instead.
	 *
	 * @param operator
	 * 		the operator to apply to each tuple (see {@link MixedRow})
	 * @param context
	 * 		the execution context to use
	 * @param <T>
	 * 		type of the result
	 * @return a buffer containing the result of the mapping
	 * @throws NullPointerException
	 * 		if any of the arguments is {@code null}
	 */
	public <T> Int32CategoricalBuffer<T> applyMixedToCategorical(Function<MixedRow, T> operator,
																 Context context) {
		Objects.requireNonNull(context, MESSAGE_CONTEXT_NULL);
		Objects.requireNonNull(operator, MESSAGE_OPERATOR_NULL);
		return (Int32CategoricalBuffer<T>) new ParallelExecutor<>(
				new ApplierMixedToCategorical<>(transformationColumns, operator,
						IntegerFormats.Format.SIGNED_INT32), workload, callback).execute(context);
	}

	/**
	 * Applies the given operator of arbitrary arity to the transformation columns returning the result in a new {@link
	 * CategoricalBuffer}. The format of the target buffer is derived from given maxNumberOfValues parameter.
	 * Depending on the input size and the specified workload per data-point, the computation might be performed in
	 * parallel.
	 *
	 * <p>This method uses an operator starting from a {@link MixedRow} which allows to read the row in different
	 * ways. This is not as performant as reading the row only in one specific way. So whenever possible it is
	 * recommended to use the methods {@link #applyNumericToCategorical}, {@link #applyCategoricalToCategorical} or
	 * {@link #applyObjectToCategorical} instead.
	 *
	 * @param operator
	 * 		the operator to apply to each tuple (see {@link MixedRow})
	 * @param maxNumberOfValues
	 * 		the maximal number of different values generated by the operator. Decides the format of the buffer.
	 * @param context
	 * 		the execution context to use
	 * @param <T>
	 * 		type of the result
	 * @return a buffer containing the result of the mapping
	 * @throws NullPointerException
	 * 		if any of the arguments is {@code null}
	 * @throws IllegalArgumentException
	 * 		if more different values are supplied than supported by the buffer format calculated from the
	 * 		maxNumberOfValues
	 */
	public <T> CategoricalBuffer<T> applyMixedToCategorical(Function<MixedRow, T> operator,
															int maxNumberOfValues,
															Context context) {
		Objects.requireNonNull(context, MESSAGE_CONTEXT_NULL);
		Objects.requireNonNull(operator, MESSAGE_OPERATOR_NULL);
		IntegerFormats.Format format = IntegerFormats.Format.findMinimal(maxNumberOfValues);
		return new ParallelExecutor<>(
				new ApplierMixedToCategorical<>(transformationColumns, operator, format),
				workload, callback).execute(context);
	}

	/**
	 * Applies the given operator of arbitrary arity to the object-readable transformation columns returning the result
	 * in a new {@link ObjectBuffer}. Depending on the input size and the specified workload per data-point, the
	 * computation might be performed in parallel.
	 *
	 * @param operator
	 * 		the operator to apply to each tuple (see {@link ObjectRow})
	 * @param type
	 * 		the type of the objects in the columns
	 * @param context
	 * 		the execution context to use
	 * @param <T>
	 * 		type of the result
	 * @param <R>
	 * 		type of objects in the columns
	 * @return a buffer containing the result of the mapping
	 * @throws NullPointerException
	 * 		if any of the arguments is {@code null}
	 * @throws UnsupportedOperationException
	 * 		if any of the transformation columns is not {@link Column.Capability#OBJECT_READABLE}
	 * @throws IllegalArgumentException
	 * 		if the objects in the transformation columns cannot be read as the given type
	 */
	public <R, T> ObjectBuffer<T> applyObjectToObject(Class<R> type, Function<ObjectRow<R>, T> operator,
													  Context context) {
		Objects.requireNonNull(context, MESSAGE_CONTEXT_NULL);
		Objects.requireNonNull(operator, MESSAGE_OPERATOR_NULL);
		return new ParallelExecutor<>(
				new ApplierNObjectToObject<>(transformationColumns, type, operator), workload, callback).execute(context);
	}

	/**
	 * Applies the given operator of arbitrary arity to the object-readable transformation columns returning the result
	 * in a new {@link TimeBuffer}. Depending on the input size and the specified workload per data-point, the
	 * computation might be performed in parallel.
	 *
	 * @param operator
	 * 		the operator to apply to each tuple (see {@link ObjectRow})
	 * @param type
	 * 		the type of the objects in the columns
	 * @param context
	 * 		the execution context to use
	 * @param <R>
	 * 		type of objects in the columns
	 * @return a buffer containing the result of the mapping
	 * @throws NullPointerException
	 * 		if any of the arguments is {@code null}
	 * @throws UnsupportedOperationException
	 * 		if any of the transformation columns is not {@link Column.Capability#OBJECT_READABLE}
	 * @throws IllegalArgumentException
	 * 		if the objects in the transformation columns cannot be read as the given type
	 */
	public <R> TimeBuffer applyObjectToTime(Class<R> type, Function<ObjectRow<R>, LocalTime> operator,
											Context context) {
		Objects.requireNonNull(context, MESSAGE_CONTEXT_NULL);
		Objects.requireNonNull(operator, MESSAGE_OPERATOR_NULL);
		return new ParallelExecutor<>(
				new ApplierNObjectToTime<>(transformationColumns, type, operator), workload, callback).execute(context);
	}

	/**
	 * Applies the given operator of arbitrary arity to the object-readable transformation columns returning the result
	 * in a new {@link DateTimeBuffer}. Depending on the input size and the specified workload per
	 * data-point, the computation might be performed in parallel.
	 *
	 * @param operator
	 * 		the operator to apply to each tuple (see {@link ObjectRow})
	 * @param type
	 * 		the type of the objects in the columns
	 * @param context
	 * 		the execution context to use
	 * @param <R>
	 * 		type of objects in the columns
	 * @return a buffer containing the result of the mapping
	 * @throws NullPointerException
	 * 		if any of the arguments is {@code null}
	 * @throws UnsupportedOperationException
	 * 		if any of the transformation columns is not {@link Column.Capability#OBJECT_READABLE}
	 * @throws IllegalArgumentException
	 * 		if the objects in the transformation columns cannot be read as the given type
	 */
	public <R> DateTimeBuffer applyObjectToDateTime(Class<R> type,
													Function<ObjectRow<R>, Instant> operator,
													Context context) {
		Objects.requireNonNull(context, MESSAGE_CONTEXT_NULL);
		Objects.requireNonNull(operator, MESSAGE_OPERATOR_NULL);
		return new ParallelExecutor<>(
				new ApplierNObjectToDateTime<>(transformationColumns, type, operator), workload, callback).execute(context);
	}


	/**
	 * Applies the given operator of arbitrary arity to the categorical transformation columns returning the result
	 * in a new {@link ObjectBuffer}. Depending on the input size and the specified workload per data-point, the
	 * computation might be performed in parallel.
	 *
	 * @param operator
	 * 		the operator to apply to each tuple (see {@link CategoricalRow})
	 * @param context
	 * 		the execution context to use
	 * @param <T>
	 * 		type of the result
	 * @return a buffer containing the result of the mapping
	 * @throws NullPointerException
	 * 		if any of the arguments is {@code null}
	 * @throws UnsupportedOperationException
	 * 		if any of the transformation columns is not {@link Column.Category#CATEGORICAL}
	 */
	public <T> ObjectBuffer<T> applyCategoricalToObject(Function<CategoricalRow, T> operator,
														Context context) {
		Objects.requireNonNull(context, MESSAGE_CONTEXT_NULL);
		Objects.requireNonNull(operator, MESSAGE_OPERATOR_NULL);
		return new ParallelExecutor<>(
				new ApplierNCategoricalToObject<>(transformationColumns, operator), workload, callback).execute(context);
	}

	/**
	 * Applies the given operator of arbitrary arity to the categorical transformation columns returning the result
	 * in a
	 * new {@link TimeBuffer}. Depending on the input size and the specified workload per data-point, the
	 * computation might be performed in parallel.
	 *
	 * @param operator
	 * 		the operator to apply to each tuple (see {@link CategoricalRow})
	 * @param context
	 * 		the execution context to use
	 * @return a buffer containing the result of the mapping
	 * @throws NullPointerException
	 * 		if any of the arguments is {@code null}
	 * @throws UnsupportedOperationException
	 * 		if any of the transformation columns is not {@link Column.Category#CATEGORICAL}
	 */
	public TimeBuffer applyCategoricalToTime(Function<CategoricalRow, LocalTime> operator,
											 Context context) {
		Objects.requireNonNull(context, MESSAGE_CONTEXT_NULL);
		Objects.requireNonNull(operator, MESSAGE_OPERATOR_NULL);
		return new ParallelExecutor<>(
				new ApplierNCategoricalToTime(transformationColumns, operator), workload, callback).execute(context);
	}

	/**
	 * Applies the given operator of arbitrary arity to the categorical transformation columns returning the result
	 * in a new {@link DateTimeBuffer}. Depending on the input size and the specified workload per data-point,
	 * the computation might be performed in parallel.
	 *
	 * @param operator
	 * 		the operator to apply to each tuple (see {@link CategoricalRow})
	 * @param context
	 * 		the execution context to use
	 * @return a buffer containing the result of the mapping
	 * @throws NullPointerException
	 * 		if any of the arguments is {@code null}
	 * @throws UnsupportedOperationException
	 * 		if any of the transformation columns is not {@link Column.Category#CATEGORICAL}
	 */
	public DateTimeBuffer applyCategoricalToDateTime(Function<CategoricalRow, Instant> operator,
															   Context context) {
		Objects.requireNonNull(context, MESSAGE_CONTEXT_NULL);
		Objects.requireNonNull(operator, MESSAGE_OPERATOR_NULL);
		return new ParallelExecutor<>(
				new ApplierNCategoricalToDateTime(transformationColumns, operator), workload, callback).execute(context);

	}

	/**
	 * Applies the given operator of arbitrary arity to the numeric-readable transformation columns returning the
	 * result in a new {@link ObjectBuffer}. Depending on the input size and the specified workload per data-point,
	 * the computation might be performed in parallel.
	 *
	 * @param operator
	 * 		the operator to apply to each tuple (see {@link NumericRow})
	 * @param context
	 * 		the execution context to use
	 * @param <T>
	 * 		type of the result
	 * @return a buffer containing the result of the mapping
	 * @throws NullPointerException
	 * 		if any of the arguments is {@code null}
	 * @throws UnsupportedOperationException
	 * 		if any of the transformation columns is not {@link Column.Capability#NUMERIC_READABLE}
	 */
	public <T> ObjectBuffer<T> applyNumericToObject(Function<NumericRow, T> operator, Context context) {
		Objects.requireNonNull(context, MESSAGE_CONTEXT_NULL);
		Objects.requireNonNull(operator, MESSAGE_OPERATOR_NULL);
		return new ParallelExecutor<>(
				new ApplierNNumericToObject<>(transformationColumns, operator), workload, callback).execute(context);
	}

	/**
	 * Applies the given operator of arbitrary arity to the numeric-readable transformation columns returning the
	 * result
	 * in a new {@link TimeBuffer}. Depending on the input size and the specified workload per data-point, the
	 * computation might be performed in parallel.
	 *
	 * @param operator
	 * 		the operator to apply to each tuple (see {@link NumericRow})
	 * @param context
	 * 		the execution context to use
	 * @return a buffer containing the result of the mapping
	 * @throws NullPointerException
	 * 		if any of the arguments is {@code null}
	 * @throws UnsupportedOperationException
	 * 		if any of the transformation columns is not {@link Column.Capability#NUMERIC_READABLE}
	 */
	public TimeBuffer applyNumericToTime(Function<NumericRow, LocalTime> operator, Context context) {
		Objects.requireNonNull(context, MESSAGE_CONTEXT_NULL);
		Objects.requireNonNull(operator, MESSAGE_OPERATOR_NULL);
		return new ParallelExecutor<>(
				new ApplierNNumericToTime(transformationColumns, operator), workload, callback).execute(context);
	}

	/**
	 * Applies the given operator of arbitrary arity to the numeric-readable transformation columns returning the
	 * result in a new {@link DateTimeBuffer}. Depending on the input size and the specified workload per
	 * data-point, the computation might be performed in parallel.
	 *
	 * @param operator
	 * 		the operator to apply to each tuple (see {@link NumericRow})
	 * @param context
	 * 		the execution context to use
	 * @return a buffer containing the result of the mapping
	 * @throws NullPointerException
	 * 		if any of the arguments is {@code null}
	 * @throws UnsupportedOperationException
	 * 		if any of the transformation columns is not {@link Column.Capability#NUMERIC_READABLE}
	 */
	public DateTimeBuffer applyNumericToDateTime(Function<NumericRow, Instant> operator, Context context) {
		Objects.requireNonNull(context, MESSAGE_CONTEXT_NULL);
		Objects.requireNonNull(operator, MESSAGE_OPERATOR_NULL);
		return new ParallelExecutor<>(
				new ApplierNNumericToDateTime(transformationColumns, operator), workload, callback).execute(context);
	}

	/**
	 * Applies the given operator of arbitrary arity to the transformation columns returning the result in a new {@link
	 * ObjectBuffer}. Depending on the input size and the specified workload per data-point, the computation might
	 * be performed in parallel.
	 *
	 * <p>This method uses an operator starting from a {@link MixedRow} which allows to read the row in different
	 * ways. This is not as performant as reading the row only in one specific way. So whenever possible it is
	 * recommended to use the methods {@link #applyNumericToObject}, {@link #applyCategoricalToObject} or {@link
	 * #applyObjectToObject} instead.
	 *
	 * @param operator
	 * 		the operator to apply to each tuple (see {@link MixedRow})
	 * @param context
	 * 		the execution context to use
	 * @param <T>
	 * 		type of the result
	 * @return a buffer containing the result of the mapping
	 * @throws NullPointerException
	 * 		if any of the arguments is {@code null}
	 */
	public <T> ObjectBuffer<T> applyMixedToObject(Function<MixedRow, T> operator, Context context) {
		Objects.requireNonNull(context, MESSAGE_CONTEXT_NULL);
		Objects.requireNonNull(operator, MESSAGE_OPERATOR_NULL);
		return new ParallelExecutor<>(
				new ApplierMixedToObject<>(transformationColumns, operator), workload, callback).execute(context);
	}

	/**
	 * Applies the given operator of arbitrary arity to the transformation columns returning the result in a new {@link
	 * TimeBuffer}. Depending on the input size and the specified workload per data-point, the computation might
	 * be performed in parallel.
	 *
	 * <p>This method uses an operator starting from a {@link MixedRow} which allows to read the row in different
	 * ways. This is not as performant as reading the row only in one specific way. So whenever possible it is
	 * recommended to use the methods {@link #applyNumericToTime}, {@link #applyCategoricalToTime} or {@link
	 * #applyObjectToTime} instead.
	 *
	 * @param operator
	 * 		the operator to apply to each tuple (see {@link MixedRow})
	 * @param context
	 * 		the execution context to use
	 * @return a buffer containing the result of the mapping
	 * @throws NullPointerException
	 * 		if any of the arguments is {@code null}
	 */
	public TimeBuffer applyMixedToTime(Function<MixedRow, LocalTime> operator, Context context) {
		Objects.requireNonNull(context, MESSAGE_CONTEXT_NULL);
		Objects.requireNonNull(operator, MESSAGE_OPERATOR_NULL);
		return new ParallelExecutor<>(
				new ApplierMixedToTime(transformationColumns, operator), workload, callback).execute(context);
	}

	/**
	 * Applies the given operator of arbitrary arity to the transformation columns returning the result in a new {@link
	 * DateTimeBuffer}. Depending on the input size and the specified workload per data-point, the
	 * computation might be performed in parallel.
	 *
	 * <p>This method uses an operator starting from a {@link MixedRow} which allows to read the row in different
	 * ways. This is not as performant as reading the row only in one specific way. So whenever possible it is
	 * recommended to use the methods {@link #applyNumericToDateTime}, {@link #applyCategoricalToDateTime} or {@link
	 * #applyObjectToDateTime} instead.
	 *
	 * @param operator
	 * 		the operator to apply to each tuple (see {@link MixedRow})
	 * @param context
	 * 		the execution context to use
	 * @return a buffer containing the result of the mapping
	 * @throws NullPointerException
	 * 		if any of the arguments is {@code null}
	 */
	public DateTimeBuffer applyMixedToDateTime(Function<MixedRow, Instant> operator, Context context) {
		Objects.requireNonNull(context, MESSAGE_CONTEXT_NULL);
		Objects.requireNonNull(operator, MESSAGE_OPERATOR_NULL);
		return new ParallelExecutor<>(
				new ApplierMixedToDateTime(transformationColumns, operator), workload, callback).execute(context);
	}

	/**
	 * Applies the given binary operator to the numeric-readable transformation column pair returning the result in a
	 * new {@link NumericBuffer}. Depending on the input size and the specified workload per data-point, the computation
	 * might be performed in parallel.
	 *
	 * @param operator
	 * 		the operator to apply to each pair
	 * @param context
	 * 		the context to use
	 * @return a buffer containing the result of the operation
	 * @throws NullPointerException
	 * 		if any of the arguments is {@code null}
	 * @throws UnsupportedOperationException
	 * 		if any of the transformation columns is not {@link Column.Capability#NUMERIC_READABLE}
	 * @throws IllegalArgumentException
	 * 		if there are not exactly two transformation columns
	 */
	public NumericBuffer applyNumericToReal(DoubleBinaryOperator operator, Context context) {
		Objects.requireNonNull(context, MESSAGE_CONTEXT_NULL);
		Objects.requireNonNull(operator, MESSAGE_OPERATOR_NULL);
		if (transformationColumns.size() != 2) {
			throw new IllegalArgumentException("There must be exactly two transformation columns.");
		}
		return applyNumericToReal(row -> operator.applyAsDouble(row.get(0), row.get(1)), context);
	}


	/**
	 * Performs a mutable reduction operation on the rows of the numeric-readable transformation columns. A mutable
	 * reduction is one in which the reduced value is a mutable result container, such as an {@code ArrayList}, and
	 * elements are incorporated by updating the state of the result rather than by replacing the result. This produces
	 * a result equivalent to:
	 *
	 * <pre>{@code
	 *     T result = supplier.get();
	 *     NumericRowReader reader = new NumericRowReader(transformationColumns);
	 *     while (reader.hasRemaining){
	 *     	   reader.move();
	 *         reducer.accept(result, reader);
	 *     }
	 *     return result;
	 * }</pre>
	 *
	 * but is not constrained to execute sequentially.
	 *
	 * <p>The reduction is executed via the given context and automatically parallelized depending on the input size
	 * and the specified workload per data-point.
	 *
	 * @param supplier
	 * 		a function that creates a new result container. In case of a parallel execution, this function may be
	 * 		called multiple times and must return a new instance each time.
	 * @param reducer
	 * 		a stateless function for incorporating an additional element into a result
	 * @param combiner
	 * 		an associative, stateless function for combining two values, which must be compatible with the	accumulator
	 * 		function
	 * @param context
	 * 		the execution context
	 * @param <T>
	 * 		type of the result
	 * @return the result of the reduction
	 * @throws NullPointerException
	 * 		if any of the arguments is or the supplier returns {@code null}
	 * @throws UnsupportedOperationException
	 * 		if any of the transformation columns is not {@link Column.Capability#NUMERIC_READABLE}
	 * @see java.util.stream.Stream#collect(Supplier, BiConsumer, BiConsumer)
	 */
	public <T> T reduceNumeric(Supplier<T> supplier, BiConsumer<T, NumericRow> reducer, BiConsumer<T, T> combiner,
                               Context context) {
		Objects.requireNonNull(supplier, MESSAGE_SUPPLIER_NULL);
		Objects.requireNonNull(reducer, MESSAGE_REDUCER_NULL);
		Objects.requireNonNull(combiner, MESSAGE_COMBINER_NULL);
		Objects.requireNonNull(context, MESSAGE_CONTEXT_NULL);
		return new ParallelExecutor<>(new NumericColumnsReducer<>(transformationColumns, supplier, reducer, combiner),
				workload, callback).execute(context);
	}

	/**
	 * Performs a mutable reduction operation on the rows of the {@link Column.Category#CATEGORICAL} transformation
	 * columns. A mutable reduction is one in which the reduced value is a mutable result container, such as an {@code
	 * ArrayList}, and elements are incorporated by updating the state of the result rather than by replacing the
	 * result. This produces a result equivalent to:
	 *
	 * <pre>{@code
	 *     T result = supplier.get();
	 *     CategoricalRowReader reader = new CategoricalRowReader(transformationColumns);
	 *     while (reader.hasRemaining){
	 *     	   reader.move();
	 *         reducer.accept(result, reader);
	 *     }
	 *     return result;
	 * }</pre>
	 *
	 * but is not constrained to execute sequentially.
	 *
	 * <p>The reduction is executed via the given context and automatically parallelized depending on the input size
	 * and the specified workload per data-point.
	 *
	 * @param supplier
	 * 		a function that creates a new result container. In case of a parallel execution, this function may be
	 * 		called multiple times and must return a new instance each time.
	 * @param reducer
	 * 		a stateless function for incorporating an additional element into a result
	 * @param combiner
	 * 		an associative, stateless function for combining two values, which must be compatible with the	accumulator
	 * 		function
	 * @param context
	 * 		the execution context
	 * @param <T>
	 * 		type of the result
	 * @return the result of the reduction
	 * @throws NullPointerException
	 * 		if any of the arguments is or the supplier returns {@code null}
	 * @throws UnsupportedOperationException
	 * 		if any of the transformation columns is not {@link Column.Category#CATEGORICAL}
	 * @see java.util.stream.Stream#collect(Supplier, BiConsumer, BiConsumer)
	 */
	public <T> T reduceCategorical(Supplier<T> supplier, BiConsumer<T, CategoricalRow> reducer,
								   BiConsumer<T, T> combiner, Context context) {
		Objects.requireNonNull(supplier, MESSAGE_SUPPLIER_NULL);
		Objects.requireNonNull(reducer, MESSAGE_REDUCER_NULL);
		Objects.requireNonNull(combiner, MESSAGE_COMBINER_NULL);
		Objects.requireNonNull(context, MESSAGE_CONTEXT_NULL);
		return new ParallelExecutor<>(new CategoricalColumnsReducer<>(transformationColumns, supplier, reducer,
				combiner), workload, callback).execute(context);
	}

	/**
	 * Performs a mutable reduction operation on the rows of the object-readable transformation columns. A mutable
	 * reduction is one in which the reduced value is a mutable result container, such as an {@code ArrayList}, and
	 * elements are incorporated by updating the state of the result rather than by replacing the result. This produces
	 * a result equivalent to:
	 *
	 * <pre>{@code
	 *     T result = supplier.get();
	 *     ObjectRowReader<R> reader = new ObjectRowReader<>(transformationColumns, type);
	 *     while (reader.hasRemaining){
	 *     	   reader.move();
	 *         reducer.accept(result, reader);
	 *     }
	 *     return result;
	 * }</pre>
	 *
	 * but is not constrained to execute sequentially.
	 *
	 * <p>The reduction is executed via the given context and automatically parallelized depending on the input size
	 * and the specified workload per data-point.
	 *
	 * @param type
	 * 		the type of the objects in the columns
	 * @param supplier
	 * 		a function that creates a new result container. In case of a parallel execution, this function may be
	 * 		called multiple times and must return a new instance each time.
	 * @param reducer
	 * 		a stateless function for incorporating an additional element into a result
	 * @param combiner
	 * 		an associative, stateless function for combining two values, which must be compatible with the	accumulator
	 * 		function
	 * @param context
	 * 		the execution context
	 * @param <T>
	 * 		type of the result
	 * @param <R>
	 * 		type of objects in the columns
	 * @return the result of the reduction
	 * @throws NullPointerException
	 * 		if any of the arguments is or the supplier returns {@code null}
	 * @throws IllegalArgumentException
	 * 		if type is not a super type of the element type of all of the columns
	 * @throws UnsupportedOperationException
	 * 		if the one of the columns is not {@link Column.Capability#OBJECT_READABLE}
	 * @see java.util.stream.Stream#collect(Supplier, BiConsumer, BiConsumer)
	 */
	public <T, R> T reduceObjects(Class<R> type, Supplier<T> supplier, BiConsumer<T, ObjectRow<R>> reducer,
								  BiConsumer<T, T> combiner, Context context) {
		Objects.requireNonNull(type, "Type must not be null");
		Objects.requireNonNull(supplier, MESSAGE_SUPPLIER_NULL);
		Objects.requireNonNull(reducer, MESSAGE_REDUCER_NULL);
		Objects.requireNonNull(combiner, MESSAGE_COMBINER_NULL);
		Objects.requireNonNull(context, MESSAGE_CONTEXT_NULL);
		return new ParallelExecutor<>(
				new ObjectColumnsReducer<>(transformationColumns, type, supplier, reducer, combiner), workload,
				callback).execute(context);
	}

	/**
	 * Performs a mutable reduction operation on the rows of the transformation columns. A mutable reduction is one in
	 * which the reduced value is a mutable result container, such as an {@code ArrayList}, and elements are
	 * incorporated by updating the state of the result rather than by replacing the result. This produces a result
	 * equivalent to:
	 *
	 * <pre>{@code
	 *     T result = supplier.get();
	 *     MixedRowReader reader = new MixedRowReader(transformationColumns);
	 *     while (reader.hasRemaining){
	 *     	   reader.move();
	 *         reducer.accept(result, reader);
	 *     }
	 *     return result;
	 * }</pre>
	 *
	 * but is not constrained to execute sequentially.
	 *
	 * <p>The reduction is executed via the given context and automatically parallelized depending on the input size
	 * and the specified workload per data-point.
	 *
	 * <p>This method uses a reducer working with a {@link MixedRow} which allows to read the row in different
	 * ways. This is not as performant as reading the row only in one specific way. So whenever possible it is
	 * recommended to use the methods {@link #reduceCategorical}, {@link #reduceNumeric} or {@link #reduceObjects}
	 * instead.
	 *
	 * @param supplier
	 * 		a function that creates a new result container. In case of a parallel execution, this function may be
	 * 		called multiple times and must return a new instance each time.
	 * @param reducer
	 * 		a stateless function for incorporating an additional element into a result
	 * @param combiner
	 * 		an associative, stateless function for combining two values, which must be compatible with the	accumulator
	 * 		function
	 * @param context
	 * 		the execution context
	 * @param <T>
	 * 		type of the result
	 * @return the result of the reduction
	 * @throws NullPointerException
	 * 		if any of the arguments is or the supplier returns {@code null}
	 * @see java.util.stream.Stream#collect(Supplier, BiConsumer, BiConsumer)
	 */
	public <T> T reduceMixed(Supplier<T> supplier, BiConsumer<T, MixedRow> reducer,
							 BiConsumer<T, T> combiner, Context context) {
		Objects.requireNonNull(supplier, MESSAGE_SUPPLIER_NULL);
		Objects.requireNonNull(reducer, MESSAGE_REDUCER_NULL);
		Objects.requireNonNull(combiner, MESSAGE_COMBINER_NULL);
		Objects.requireNonNull(context, MESSAGE_CONTEXT_NULL);
		return new ParallelExecutor<>(new MixedColumnsReducer<>(transformationColumns, supplier, reducer,
				combiner), workload, callback).execute(context);
	}

}
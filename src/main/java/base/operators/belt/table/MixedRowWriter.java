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

package base.operators.belt.table;

import base.operators.belt.buffer.Buffers;
import base.operators.belt.column.Column;
import base.operators.belt.column.Column.TypeId;
import base.operators.belt.column.ColumnType;
import base.operators.belt.column.ColumnTypes;
import base.operators.belt.reader.NumericReader;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;


/**
 * Writer to create a {@link Table} from row-wise data.
 *
 * @author Gisa Meier
 * @see Buffers
 */
public final class MixedRowWriter {

	/**
	 * Placeholder buffer used before initialization.
	 */
	private static final double[] PLACEHOLDER_BUFFER = new double[0];

	/**
	 * The height of the internal buffer.
	 */
	private static final int BUFFER_HEIGHT = NumericReader.SMALL_BUFFER_SIZE;

	private final NumericColumnWriter[] columns;
	private final ComplexWriter[] objectColumns;
	private final Class<?>[] classes;
	private final String[] columnLabels;
	private final int bufferWidth;
	private final boolean initialize;

	private double[] buffer = PLACEHOLDER_BUFFER;
	private Object[] objectBuffer = new Object[0];
	private int bufferOffset = -BUFFER_HEIGHT;
	private int bufferRowIndex = 0;
	private int rowIndex = -1;


	/**
	 * Creates a row writer to create a {@link Table} with the given column labels and starting row size 0.
	 *
	 * @param columnLabels
	 * 		the names for the columns to construct
	 * @param types
	 * 		the types of the columns to construct
	 * @param initialize
	 * 		if this is {@code true} every value that is not explicitly set is missing, if this is {@link false} values
	 * 		for	indices that are not explicitly set are undetermined
	 */
	MixedRowWriter(List<String> columnLabels, List<ColumnType<?>> types, boolean initialize) {
		int numberOfColumns = columnLabels.size();
		columns = new NumericColumnWriter[numberOfColumns];
		objectColumns = new ComplexWriter[numberOfColumns];
		classes = new Class<?>[numberOfColumns];
		this.columnLabels = columnLabels.toArray(new String[0]);
		for (int i = 0; i < numberOfColumns; i++) {
			ColumnType<?> type = Objects.requireNonNull(types.get(i), "column type must not be null");
			columns[i] = getBufferForType(type);
			objectColumns[i] = getObjectBufferForType(type);
			classes[i] = type.elementType();
		}
		bufferWidth = numberOfColumns;
		this.initialize = initialize;
	}

	/**
	 * Creates a row writer to create a {@link Table} with the given column labels and starting row size expectedRows.
	 *
	 * @param columnLabels
	 * 		the names for the columns to construct
	 * @param types
	 * 		the types of the columns to construct
	 * @param expectedRows
	 * 		the expected number of rows
	 * @param initialize
	 * 		if this is {@code true} every value that is not explicitly set is missing, if this is {@link false} values
	 * 		for	indices that are not explicitly set are undetermined
	 */
	MixedRowWriter(List<String> columnLabels, List<ColumnType<?>> types, int expectedRows, boolean initialize) {
		int numberOfColumns = columnLabels.size();
		columns = new NumericColumnWriter[numberOfColumns];
		objectColumns = new ComplexWriter[numberOfColumns];
		classes = new Class<?>[numberOfColumns];
		this.columnLabels = columnLabels.toArray(new String[0]);
		for (int i = 0; i < numberOfColumns; i++) {
			ColumnType<?> type = Objects.requireNonNull(types.get(i), "column type must not be null");
			columns[i] = getBufferForType(type, expectedRows);
			objectColumns[i] = getObjectBufferForType(type, expectedRows);
			classes[i] = type.elementType();
		}
		bufferWidth = numberOfColumns;
		this.initialize = initialize;
	}

	private ComplexWriter getObjectBufferForType(ColumnType<?> columnType) {
		if (ColumnTypes.DATETIME.equals(columnType)) {
			return new NanosecondsDateTimeWriter();
		} else if (ColumnTypes.TIME.equals(columnType)) {
			return new TimeColumnWriter();
		} else if (columnType.category() == Column.Category.CATEGORICAL) {
			return new Int32CategoricalWriter<>(columnType);
		} else if (columnType.category() == Column.Category.OBJECT) {
			return new ObjectWriter<>(columnType);
		} else {
			return null;
		}
	}


	/**
	 * Returns an integer or real growing column buffer without set length.
	 */
	private NumericColumnWriter getBufferForType(ColumnType<?> type) {
		TypeId typeId = type.id();
		if (typeId == TypeId.INTEGER) {
			return new IntegerColumnWriter();
		} else if (typeId == TypeId.REAL) {
			return new RealColumnWriter();
		} else {
			return null;
		}
	}


	private ComplexWriter getObjectBufferForType(ColumnType<?> columnType, int expectedRows) {
		if (ColumnTypes.DATETIME.equals(columnType)) {
			return new NanosecondsDateTimeWriter(expectedRows);
		} else if (ColumnTypes.TIME.equals(columnType)) {
			return new TimeColumnWriter(expectedRows);
		} else if (columnType.category() == Column.Category.CATEGORICAL) {
			return new Int32CategoricalWriter<>(columnType, expectedRows);
		} else if (columnType.category() == Column.Category.OBJECT) {
			return new ObjectWriter<>(columnType, expectedRows);
		} else {
			return null;
		}
	}


	/**
	 * Returns an integer or real growing column buffer with the given length.
	 */
	private NumericColumnWriter getBufferForType(ColumnType<?> type, int expectedRows) {
		TypeId typeId = type.id();
		if (typeId == TypeId.INTEGER) {
			return new IntegerColumnWriter(expectedRows);
		} else if (typeId == TypeId.REAL) {
			return new RealColumnWriter(expectedRows);
		} else {
			return null;
		}
	}


	/**
	 * Moves the reader to the next row.
	 */
	public void move() {
		if (bufferRowIndex >= buffer.length - bufferWidth) {
			if (buffer == PLACEHOLDER_BUFFER) {
				buffer = new double[bufferWidth * BUFFER_HEIGHT];
				objectBuffer = new Object[bufferWidth * BUFFER_HEIGHT];
			} else {
				writeBuffer();
			}
			if (initialize) {
				Arrays.fill(buffer, Double.NaN);
				Arrays.fill(objectBuffer, null);
			}
			bufferOffset += BUFFER_HEIGHT;
			bufferRowIndex = 0;
		} else {
			bufferRowIndex += bufferWidth;
		}
		rowIndex++;
	}


	/**
	 * Sets the value at the given column index for a numeric column. This method is well-defined for indices zero
	 * (including) to {@link #width()} (excluding). For non-numeric columns use {@link #set(int, Object)} instead.
	 *
	 * <p>This method does not perform any range checks. Nor does it ever advance the current row. Before invoking this
	 * method, you will have to call {@link #move()} at least once.
	 *
	 * @param index
	 * 		the column index of a numeric column
	 * @param value
	 * 		the value to set
	 */
	public void set(int index, double value) {
		buffer[bufferRowIndex + index] = value;
	}

	/**
	 * Sets the value at the given column index for a non-numeric column. This method is well-defined for indices zero
	 * (including) to {@link #width()} (excluding). For numeric columns use {@link #set(int, double)} instead.
	 *
	 * <p>This method does not perform any range checks. Nor does it ever advance the current row. Before invoking this
	 * method, you will have to call {@link #move()} at least once.
	 *
	 * @param index
	 * 		the column index of a non-numeric column
	 * @param value
	 * 		the value to set
	 */
	public void set(int index, Object value) {
		objectBuffer[bufferRowIndex + index] = classes[index].cast(value);
	}

	/**
	 * Creates a {@link Table} from the data inside the row writer. The row writer cannot be changed afterwards.
	 *
	 * @return a new table
	 * @throws IllegalArgumentException
	 * 		if the stored column labels are not valid to create a table
	 */
	public Table create() {
		writeBuffer();
		Column[] finalColumns = new Column[columns.length];
		for (int i = 0; i < columns.length; i++) {
			NumericColumnWriter column = columns[i];
			if (column != null) {
				finalColumns[i] = column.toColumn();
			} else {
				ComplexWriter objectColumn = objectColumns[i];
				if (objectColumn != null) {
					finalColumns[i] = objectColumn.toColumn();
				}
			}
		}
		return new Table(finalColumns, columnLabels);
	}

	/**
	 * Writes the writer buffer to the column buffers.
	 */
	private void writeBuffer() {
		for (int i = 0; i < columns.length; i++) {
			NumericColumnWriter column = columns[i];
			if (column != null) {
				column.fill(buffer, bufferOffset, i, bufferWidth, rowIndex + 1);
			}
			ComplexWriter objectColumn = objectColumns[i];
			if (objectColumn != null) {
				objectColumn.fill(objectBuffer, bufferOffset, i, bufferWidth, rowIndex + 1);
			}
		}
	}

	/**
	 * @return the number of values per row
	 */
	public int width() {
		return columns.length;
	}


	@Override
	public String toString() {
		return "General row writer (" + (rowIndex + 1) + "x" + bufferWidth + ")";
	}
}

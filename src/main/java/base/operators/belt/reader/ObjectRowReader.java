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

package base.operators.belt.reader;

import base.operators.belt.column.Column;

import java.util.List;


/**
 * Buffered row-oriented reader for multiple {@link Column}s with capability {@link Column.Capability#OBJECT_READABLE}.
 *
 * <p>In its initial state the reader does not point at a valid row. Thus, one must invoke {@link #move()} at least once
 * before reading values using {@link #get(int)}. Example:
 *
 * <pre>{@code
 * ObjectRowReader<String> reader = ...;
 * while(reader.hasRemaining()) {
 *     reader.move();
 *     for (int i = 0; i < reader.width(); i++) {
 *         ... = reader.get(i);
 *     }
 * }
 * }</pre>
 *
 * <p>Given the column-oriented design of Belt, {@link ObjectRowReader}s do not perform as well as {@link
 * ObjectReader}s. Thus, whenever possible the use of {@link ObjectReader}s is preferred.
 *
 * @author Michael Knopf, Gisa Meier
 */
public final class ObjectRowReader<T> implements ObjectRow<T> {

	/**
	 * The desired elements in a buffer so that the size is not more than 256 KB
	 */
	private static final int BUFFER_ELEMENTS = 256 * 1024 / 8;

	/**
	 * Maximal number of rows in a buffer
	 */
	private static final int MAX_BUFFER_ROWS = NumericReader.SMALL_BUFFER_SIZE;

	/**
	 * Minimal number of rows in a buffer
	 */
	private static final int MIN_BUFFER_ROWS = NumericReader.MIN_BUFFER_SIZE;

	/**
	 * Placeholder buffer used before initialization.
	 */
	private static final Object[] PLACEHOLDER_BUFFER = new Object[0];

	private final List<Column> columns;
	private final int tableHeight;
	private final int bufferWidth;
	private final int bufferHeight;

	private Object[] buffer;
	private int bufferOffset;
	private int bufferRowIndex;
	private int rowIndex;

	/**
	 * Creates a new reader for row-wise reads of the given columns. The columns must be of column category {@link
	 * Column.Category#CATEGORICAL}.
	 *
	 * <p>Given the column-oriented design of Belt, {@link ObjectRowReader}s do not perform as well as {@link
	 * CategoricalReader}s. Thus, whenever possible the use of {@link CategoricalReader}s is preferred.
	 *
	 * @param src
	 * 		the columns to read
	 * @param type
	 * 		the common superclass of all the element types of the columns
	 * @throws NullPointerException
	 * 		if the source columns array is {@code null}
	 */
	ObjectRowReader(List<Column> src, Class<T> type) {
		this(src, type, BUFFER_ELEMENTS);
	}

	/**
	 * Creates a new reader for row-wise reads.
	 *
	 * @param src
	 * 		the columns to read
	 * @param type
	 * 		the common superclass of all the element types of the columns
	 * @param bufferElementsHint
	 * 		a suggested buffer height
	 * @throws NullPointerException
	 * 		if source columns array is {@code null}
	 */
	ObjectRowReader(List<Column> src, Class<T> type, int bufferElementsHint) {
		columns = src;
		for (Column column : columns) {
			if (!type.isAssignableFrom(column.type().elementType())) {
				throw new IllegalArgumentException("Element type is not super type of " + column.type().elementType());
			}
		}
		bufferWidth = columns.size();
		tableHeight = bufferWidth > 0 ? columns.get(0).size() : 0;
		if (bufferWidth > 0) {
			// at least min buffer rows, at most max buffer rows or table height, otherwise rows to fit buffer elements
			bufferHeight = Math.min(Math.max(MIN_BUFFER_ROWS, bufferElementsHint / bufferWidth),
					Math.min(tableHeight, MAX_BUFFER_ROWS));
		} else {
			bufferHeight = 0;
		}
		buffer = PLACEHOLDER_BUFFER;
		rowIndex = -1;
		bufferRowIndex = 0;
		bufferOffset = -bufferHeight;
	}

	/**
	 * Moves the reader to the next row.
	 */
	public void move() {
		if (bufferRowIndex >= buffer.length - bufferWidth) {
			if (buffer == PLACEHOLDER_BUFFER) {
				buffer = new Object[bufferWidth * bufferHeight];
			}
			bufferOffset += bufferHeight;
			int i = 0;
			for (Column column: columns) {
				column.fill(buffer, bufferOffset, i, bufferWidth);
				i++;
			}
			bufferRowIndex = 0;
		} else {
			bufferRowIndex += bufferWidth;
		}
		rowIndex++;
	}

	/**
	 * Returns the value of the current row at the given index. This method is well-defined for indices zero (including)
	 * to {@link #width()} (excluding).
	 *
	 * <p>This method does not perform any range checks. Nor does it ever advance the current row. Before invoking this
	 * method, you will have to call {@link #move()} at least once.
	 *
	 * @param index
	 * 		the index
	 * @return the value of the current row at the given index
	 * @see #move()
	 */
	@Override
	public T get(int index) {
		// the cast is safe because of the check in the constructor
		@SuppressWarnings("unchecked")
		T result = (T) buffer[bufferRowIndex + index];
		return result;
	}

	/**
	 * @return the number of remaining rows
	 */
	public int remaining() {
		return tableHeight - rowIndex - 1;
	}

	/**
	 * @return {@code true} iff further rows can be read
	 */
	public boolean hasRemaining() {
		return rowIndex < tableHeight - 1;
	}

	/**
	 * @return the number of values per row
	 */
	@Override
	public int width() {
		return bufferWidth;
	}

	/**
	 * Returns the position of the row in the table (0-based). Returns {@link Readers#BEFORE_FIRST_ROW} if the reader
	 * is before the first position, i.e., {@link #move()} has not been called.
	 *
	 * @return the row position
	 */
	@Override
	public int position() {
		return rowIndex;
	}

	/**
	 * Sets the reader position to the given row but without loading the data. The next {@link #move()} loads the data
	 * and goes to row {@code position+1}. Note that this can invalidate the buffer so that a new buffer is filled for
	 * the next move.
	 *
	 * @param position
	 * 		the position where to move
	 * @throws IndexOutOfBoundsException
	 * 		if the given position smaller than -1
	 */
	public void setPosition(int position) {
		if (position + 1 < 0) {
			throw new IndexOutOfBoundsException("Position must not be smaller than -1");
		}
		if (position + 1 >= bufferOffset && position + 1 < bufferOffset + bufferHeight) {
			bufferRowIndex = (position + 1 - bufferOffset) * bufferWidth - bufferWidth;
		} else {
			bufferOffset = position + 1 - bufferHeight;
			bufferRowIndex = buffer.length;
		}
		rowIndex = position;
	}

	@Override
	public String toString() {
		return "Object row reader (" + tableHeight + "x" + bufferWidth + ")" + "\n" + "Row position: " + position();
	}

}
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

package base.operators.belt.buffer;

import base.operators.belt.column.Column;
import base.operators.belt.column.Column.TypeId;
import base.operators.belt.column.ColumnType;

import java.util.Arrays;


/**
 * A {@link NumericBuffer} with length fixed from the start storing rounded double values.
 *
 * @author Gisa Meier
 */
final class IntegerBuffer extends NumericBuffer {

	private final double[] data;
	private boolean frozen = false;

	/**
	 * Creates a buffer of the given length to create a {@link Column} of type id {@link Column.TypeId#INTEGER}.
	 *
	 * @param length
	 * 		the length of the buffer
	 * @param initialize
	 * 		if {@code true} all values are set to {@link Double#NaN}
	 */
	IntegerBuffer(int length, boolean initialize) {
		data = new double[length];
		if (initialize) {
			Arrays.fill(data, Double.NaN);
		}
	}

	/**
	 * Creates a buffer by copying the data from the given column.  Throws a {@link UnsupportedOperationException} if the
	 * category has not the capability {@link Column.Capability#NUMERIC_READABLE}.
	 *
	 * @param column
	 * 		the Column to copy into the buffer
	 */
	IntegerBuffer(Column column) {
		data = new double[column.size()];
		column.fill(data, 0);
		ColumnType<?> type = column.type();
		if (type.id() != TypeId.INTEGER && type.id() != TypeId.TIME && type
				.category() != Column.Category.CATEGORICAL) {
			// must round if underlying data was not rounded already
			for (int i = 0; i < data.length; i++) {
				double value = data[i];
				if (!Double.isNaN(value)) {
					data[i] = Math.round(value);
				}
			}
		}
	}

	@Override
	public double get(int index) {
		return data[index];
	}


	/**
	 * {@inheritDoc} Finite values are rounded.
	 */
	@Override
	public void set(int index, double value) {
		if (frozen) {
			throw new IllegalStateException(BUFFER_FROZEN_MESSAGE);
		}
		double integerValue = value;
		if (Double.isFinite(integerValue)) {
			//round values that are not NaN, +- infinity
			integerValue = Math.round(integerValue);
		}
		data[index] = integerValue;
	}


	@Override
	public int size() {
		return data.length;
	}

	@Override
	public TypeId type() {
		return TypeId.INTEGER;
	}

	@Override
	protected double[] getData() {
		return data;
	}


	@Override
	protected void freeze() {
		frozen = true;
	}

	@Override
	public String toString() {
		return BufferPrinter.print(this);
	}

}

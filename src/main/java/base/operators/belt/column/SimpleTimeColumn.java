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

package base.operators.belt.column;

import base.operators.belt.util.Mapping;
import base.operators.belt.util.Order;
import base.operators.belt.util.Sorting;

import java.time.LocalTime;
import java.util.Comparator;


/**
 * Simple implementation of a {@link TimeColumn}.
 *
 * @author Gisa Meier
 */
class SimpleTimeColumn extends TimeColumn {

	static final SimpleTimeColumn EMPTY_TIME_COLUMN = new SimpleTimeColumn(new long[0]);

	private final long[] nanoOfDay;

	/**
	 * Creates a new time column interpreting the given {@code long} values as nanoseconds of the day (see {@link
	 * LocalTime#toNanoOfDay()}).
	 *
	 * @param nanoOfDay
	 * 		the nanoseconds of the day
	 */
	SimpleTimeColumn(long[] nanoOfDay) {
		super(nanoOfDay.length);
		this.nanoOfDay = nanoOfDay;
	}

	@Override
	public void fill(Object[] array, int rowIndex) {
		int start = Math.min(size(), rowIndex);
		int end = Math.min(start + array.length, size());
		int i = 0;
		for (int offset = start; offset < end; offset++) {
			array[i++] = lookupLocalTime(offset);
		}

	}

	@Override
	public void fill(double[] array, int rowIndex) {
		int start = Math.min(size(), rowIndex);
		int end = Math.min(start + array.length, size());
		int i = 0;
		for (int offset = start; offset < end; offset++) {
			array[i++] = lookupDouble(offset);
		}
	}

	@Override
	public void fill(Object[] array, int startIndex, int arrayOffset, int arrayStepSize) {
		if (arrayStepSize < 1) {
			throw new IllegalArgumentException("step size must not be smaller than 1");
		}
		int max = Math.min(startIndex + (array.length - arrayOffset - 1) / arrayStepSize + 1, size());
		int rowIndex = startIndex;
		int arrayIndex = arrayOffset;
		while (rowIndex < max) {
			array[arrayIndex] = lookupLocalTime(rowIndex);
			arrayIndex += arrayStepSize;
			rowIndex++;
		}

	}

	@Override
	public void fill(double[] array, int startIndex, int arrayOffset, int arrayStepSize) {
		if (arrayStepSize < 1) {
			throw new IllegalArgumentException("step size must not be smaller than 1");
		}
		int max = Math.min(startIndex + (array.length - arrayOffset - 1) / arrayStepSize + 1, size());
		int rowIndex = startIndex;
		int arrayIndex = arrayOffset;
		while (rowIndex < max) {
			array[arrayIndex] = lookupDouble(rowIndex);
			arrayIndex += arrayStepSize;
			rowIndex++;
		}

	}

	@Override
	public void fillNanosIntoArray(long[] array, int arrayStartIndex) {
		int length = Math.min(nanoOfDay.length, array.length - arrayStartIndex);
		System.arraycopy(nanoOfDay, 0, array, arrayStartIndex, length);
	}

	@Override
	Column map(int[] mapping, boolean preferView) {
		if (preferView || mapping.length > size() * MAPPING_THRESHOLD) {
			return new MappedTimeColumn(nanoOfDay, mapping);
		} else {
			return new SimpleTimeColumn(Mapping.apply(nanoOfDay, mapping, MISSING_VALUE));
		}
	}

	@Override
	public int[] sort(Order order) {
		return Sorting.sort(size(), Comparator.comparingLong(a -> nanoOfDay[a]), order);
	}

	private LocalTime lookupLocalTime(int i) {
		long s = nanoOfDay[i];
		if (s == TimeColumn.MISSING_VALUE) {
			return null;
		} else {
			return LocalTime.ofNanoOfDay(s);
		}
	}

	private double lookupDouble(int i) {
		long s = nanoOfDay[i];
		if (s == TimeColumn.MISSING_VALUE) {
			return Double.NaN;
		} else {
			return s;
		}
	}

}

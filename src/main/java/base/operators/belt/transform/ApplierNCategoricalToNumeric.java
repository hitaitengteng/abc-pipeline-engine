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


import base.operators.belt.buffer.Buffers;
import base.operators.belt.buffer.NumericBuffer;
import base.operators.belt.column.Column;
import base.operators.belt.reader.CategoricalRow;
import base.operators.belt.reader.CategoricalRowReader;
import base.operators.belt.reader.Readers;

import java.util.List;
import java.util.function.ToDoubleFunction;


/**
 * Maps {@link Column.Category#CATEGORICAL} {@link Column}s to a {@link NumericBuffer} using a given mapping operator.
 *
 * @author Gisa Meier
 */
final class ApplierNCategoricalToNumeric implements Calculator<NumericBuffer> {


	private NumericBuffer target;
	private final List<Column> sources;
	private final ToDoubleFunction<CategoricalRow> operator;
	private final boolean round;

	ApplierNCategoricalToNumeric(List<Column> sources, ToDoubleFunction<CategoricalRow> operator, boolean round) {
		this.sources = sources;
		this.operator = operator;
		this.round = round;
	}


	@Override
	public void init(int numberOfBatches) {
		target = round ? Buffers.integerBuffer(sources.get(0).size(), false) :
				Buffers.realBuffer(sources.get(0).size(), false);
	}

	@Override
	public int getNumberOfOperations() {
		return sources.get(0).size();
	}

	@Override
	public void doPart(int from, int to, int batchIndex) {
		mapPart(sources, operator, target, from, to);
	}

	@Override
	public NumericBuffer getResult() {
		return target;
	}

	/**
	 * Maps every index between from (inclusive) and to (exclusive) of the sources columns using the operator and stores
	 * the result in target.
	 */
	private static void mapPart(final List<Column> sources, final ToDoubleFunction<CategoricalRow> operator, final NumericBuffer target,
                                int from, int to) {
		final CategoricalRowReader reader = Readers.categoricalRowReader(sources);
		reader.setPosition(from - 1);
		for (int i = from; i < to; i++) {
			reader.move();
			double value = operator.applyAsDouble(reader);
			target.set(i, value);
		}
	}


}
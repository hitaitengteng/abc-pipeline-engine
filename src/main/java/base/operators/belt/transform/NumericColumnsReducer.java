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


import base.operators.belt.column.Column;
import base.operators.belt.reader.NumericRow;
import base.operators.belt.reader.NumericRowReader;
import base.operators.belt.reader.Readers;

import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Supplier;


/**
 * Reduces several numeric-readable {@link Column}s using a given reduction information.
 *
 * @author Gisa Meier
 */
final class NumericColumnsReducer<T> implements Calculator<T> {


	private final List<Column> sources;
	private final Supplier<T> supplier;
	private final BiConsumer<T, NumericRow> reducer;
	private final BiConsumer<T, T> combiner;
	private NumericColumnReducer.CombineTree<T> combineTree;

	NumericColumnsReducer(List<Column> sources, Supplier<T> supplier, BiConsumer<T, NumericRow> reducer, BiConsumer<T, T> combiner) {
		this.sources = sources;
		this.supplier = supplier;
		this.reducer = reducer;
		this.combiner = combiner;
	}


	@Override
	public void init(int numberOfBatches) {
		combineTree = new NumericColumnReducer.CombineTree<>(numberOfBatches);
	}

	@Override
	public int getNumberOfOperations() {
		return sources.get(0).size();
	}

	@Override
	public void doPart(int from, int to, int batchIndex) {
		T supplied = Objects.requireNonNull(supplier.get(), "Supplier must not return null");
		reducePart(sources, supplied, reducer, from, to);
		combineTree.combine(supplied, batchIndex, combiner);
	}

	@Override
	public T getResult() {
		return combineTree.getRoot();
	}

	/**
	 * Calls the reducer for every row between from (inclusive) and to (exclusive).
	 */
	private static <T> void reducePart(List<Column> sources, T container, BiConsumer<T, NumericRow> reducer, int from, int to) {
		final NumericRowReader reader = Readers.numericRowReader(sources);
		reader.setPosition(from - 1);
		for (int i = from; i < to; i++) {
			reader.move();
			reducer.accept(container, reader);
		}
	}


}
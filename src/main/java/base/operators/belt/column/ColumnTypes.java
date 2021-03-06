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

import base.operators.belt.column.Column.Category;
import base.operators.belt.column.Column.TypeId;

import java.time.Instant;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.Objects;


/**
 * Utility class to create {@link ColumnType}s. Columns types of the categories {@link Category#CATEGORICAL} and
 * {@link Category#OBJECT} can be created using the {@link #categoricalType(String, Class, Comparator)} and
 * {@link #objectType(String, Class, Comparator)} methods respectively. Column types for builtin formats are predefined,
 * e.g., {@link #NOMINAL}.
 *
 * @author Michael Knopgf
 */
public final class ColumnTypes {

	/** Complex id for categorical columns with {@link String} values. */
	public static final ColumnType<String> NOMINAL = new ColumnType<>(TypeId.NOMINAL, null, Category.CATEGORICAL,
			String.class, String::compareTo);

	/** Complex id for integer columns without complex values. */
	public static final ColumnType<Void> INTEGER = new ColumnType<>(TypeId.INTEGER, null, Category.NUMERIC,
			Void.class, null, Column.Capability.SORTABLE);

	/** Complex id for real columns without complex values. */
	public static final ColumnType<Void> REAL = new ColumnType<>(TypeId.REAL, null, Category.NUMERIC,
			Void.class, null, Column.Capability.SORTABLE);

	/** Complex id for time columns. */
	public static final ColumnType<LocalTime> TIME = new ColumnType<>(TypeId.TIME, null, Category.OBJECT,
			LocalTime.class, LocalTime::compareTo, Column.Capability.NUMERIC_READABLE);

	/** Complex id for date-time columns. */
	public static final ColumnType<Instant> DATETIME = new ColumnType<>(TypeId.DATE_TIME, null, Category.OBJECT,
			Instant.class, Instant::compareTo);

	/**
	 * Returns a new categorical type with the given id, element type and comparator (optional). The id must be unique
	 * and should follow Java's naming convention for packages. Belt assumes that the given element type is immutable,
	 * i.e., that is is safe to share instances between tables.
	 *
	 * @param customTypeId
	 * 		the id of the custom type
	 * @param elementType
	 * 		the immutable element type
	 * @param comparator
	 * 		the comparator (optional)
	 * @param <T>
	 * 		the element type
	 * @return the complex custom types
	 * @see Category#CATEGORICAL
	 */
	public static <T> ColumnType<T> categoricalType(String customTypeId, Class<T> elementType, Comparator<T> comparator) {
		Objects.requireNonNull(customTypeId, "Custom type id must not be null");
		Objects.requireNonNull(elementType, "Element type must not be null");
		return new ColumnType<>(TypeId.CUSTOM, customTypeId, Category.CATEGORICAL, elementType, comparator);
	}

	/**
	 * Returns a new object type with the given id, element type and comparator (optional). The id must be unique and
	 * should follow Java's naming convention for packages. Belt assumes that the given element type is immutable, i.e.,
	 * that is is safe to share instances between tables.
	 *
	 * @param customTypeId
	 * 		the id of the custom type
	 * @param elementType
	 * 		the immutable element type
	 * @param comparator
	 * 		the comparator (optional)
	 * @param <T>
	 * 		the element type
	 * @return the complex custom type
	 * @see Category#OBJECT
	 */
	public static <T> ColumnType<T> objectType(String customTypeId, Class<T> elementType, Comparator<T> comparator) {
		Objects.requireNonNull(customTypeId, "Custom type id must not be null");
		Objects.requireNonNull(elementType, "Element type must not be null");
		return new ColumnType<>(TypeId.CUSTOM, customTypeId, Category.OBJECT, elementType, comparator);
	}

	private ColumnTypes() {
		// Suppress default constructor to prevent instantiation
		throw new AssertionError();
	}

}

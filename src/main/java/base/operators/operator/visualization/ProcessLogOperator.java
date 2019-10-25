/**
 * Copyright (C) 2001-2019 by RapidMiner and the contributors
 * 
 * Complete list of developers available at our web site:
 * 
 * http://rapidminer.com
 * 
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU Affero General Public License as published by the Free Software Foundation, either version 3
 * of the License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Affero General Public License for more details.
 * 
 * You should have received a copy of the GNU Affero General Public License along with this program.
 * If not, see http://www.gnu.org/licenses/.
*/
package base.operators.operator.visualization;

import base.operators.datatable.DataTable;
import base.operators.datatable.DataTableRow;
import base.operators.datatable.SimpleDataTable;
import base.operators.datatable.SimpleDataTableRow;
import base.operators.operator.*;
import base.operators.operator.performance.PerformanceEvaluator;
import base.operators.operator.performance.PerformanceVector;
import base.operators.operator.ports.DummyPortPairExtender;
import base.operators.operator.ports.PortPairExtender;
import base.operators.operator.ports.metadata.MDTransformationRule;
import base.operators.operator.ports.quickfix.ParameterSettingQuickFix;
import base.operators.parameter.*;
import base.operators.parameter.ParameterTypeValue.OperatorValueSelection;
import base.operators.parameter.conditions.EqualTypeCondition;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;


/**
 * This operator records almost arbitrary data. It can be written to a file which can then be read,
 * e.g., by gnuplot. Alternatively, the collected data can be plotted by the GUI. This is even
 * possible during process runtime (i.e. online plotting).<br/>
 *
 * Parameters in the list <code>log</code> are interpreted as follows: The <var>key</var> gives the
 * name for the column name (e.g. for use in the plotter). The <var>value</var> specifies where to
 * retrieve the value from. This is best explained by an example:
 * <ul>
 * <li>If the value is <code>operator.Evaluator.value.absolute</code>, the ProcessLogOperator looks
 * up the operator with the name <code>Evaluator</code>. If this operator is a
 * {@link PerformanceEvaluator}, it has a value named
 * <var>absolute</var> which gives the absolute error of the last evaluation. This value is queried
 * by the ProcessLogOperator</li>
 * <li>If the value is <code>operator.SVMLearner.parameter.C</code>, the ProcessLogOperator looks up
 * the parameter <var>C</var> of the operator named <code>SVMLearner</code>.</li>
 * </ul>
 * Each time the ProcessLogOperator is applied, all the values and parameters specified by the list
 * <var>log</var> are collected and stored in a data row. When the process finishes, the operator
 * writes the collected data rows to a file (if specified). In GUI mode, 2D or 3D plots are
 * automatically generated and displayed in the result viewer. <br/>
 * Please refer to section {@rapidminer.ref sec:parameter_optimization|Advanced Processes/Parameter
 * and performance analysis} for an example application.
 *
 * @rapidminer.todo Use IOObjects for logging as well (e.g.
 *                  {@link PerformanceVector})
 * @author Simon Fischer, Ingo Mierswa
 */
public class ProcessLogOperator extends Operator {

	public static final String PARAMETER_COLUMN_NAME = "column_name";

	/** The parameter name for &quot;operator.OPERATORNAME.[value|parameter].VALUE_NAME&quot; */
	public static final String PARAMETER_COLUMN_VALUE = "value";

	public static final String PARAMETER_FILENAME = "filename";

	public static final String PARAMETER_LOG = "log";

	public static final String PARAMETER_PERSISTENT = "persistent";

	public static final String PARAMETER_SORTING_TYPE = "sorting_type";

	public static final String PARAMETER_SORTING_DIMENSION = "sorting_dimension";

	public static final String PARAMETER_SORTING_K = "sorting_k";

	public static final String[] SORTING_TYPES = { "none", "top-k", "bottom-k" };

	public static final int SORTING_TYPE_NONE = 0;
	public static final int SORTING_TYPE_TOP_K = 1;
	public static final int SORTING_TYPE_BOTTOM_K = 2;

	private PortPairExtender dummyPorts = new DummyPortPairExtender("through", getInputPorts(), getOutputPorts());

	public ProcessLogOperator(OperatorDescription description) {
		super(description);

		dummyPorts.start();

		getTransformer().addRule(dummyPorts.makePassThroughRule());
		// check if the user entered duplicate column names
		getTransformer().addRule(new MDTransformationRule() {

			@Override
			public void transformMD() {
				try {
					getColumnNames();
				} catch (UserError e) {
					addError(new SimpleProcessSetupError(ProcessSetupError.Severity.INFORMATION, ProcessLogOperator.this.getPortOwner(),
							Collections.singletonList(new ParameterSettingQuickFix(ProcessLogOperator.this, PARAMETER_LOG)),
							"duplicate_log_column"));
				}
			}
		});
	}

	private double fetchValue(OperatorValueSelection selection, int column) throws UndefinedParameterError {
		Operator operator = lookupOperator(selection.getOperator());
		if (operator != null) {
			if (selection.isValue()) {
				Value value = operator.getValue(selection.getValueName());
				if (value == null) {
					getLogger().warning("No such value in '" + selection + "'");
					return Double.NaN;
				}
				if (value.isNominal()) {
					Object actualValue = value.getValue();
					if (actualValue != null) {
						String valueString = value.getValue().toString();
						SimpleDataTable table = (SimpleDataTable) getProcess().getDataTable(getName());
						return table.mapString(column, valueString);
					} else {
						return Double.NaN;
					}
				} else {
					return ((Double) value.getValue()).doubleValue();
				}

			} else {
				ParameterType parameterType = operator.getParameterType(selection.getParameterName());
				if (parameterType == null) {
					logWarning("No such parameter in '" + selection + "'");
					return Double.NaN;
				} else {
					if (parameterType.isNumerical()) { // numerical
						try {
							return Double.parseDouble(operator.getParameter(selection.getParameterName()).toString());
						} catch (NumberFormatException e) {
							logWarning("Cannot parse parameter value of '" + selection + "'");
						}
					} else { // nominal
						String value = parameterType.toString(operator.getParameter(selection.getParameterName()));
						SimpleDataTable table = (SimpleDataTable) getProcess().getDataTable(getName());
						return table.mapString(column, value);
					}
				}
			}
		} else {
			logWarning("Unknown operator '" + selection.getOperator() + "' in '" + selection + "'");
		}
		return Double.NaN;
	}

	private Collection<OperatorValueSelection> getValueDescriptions() throws UndefinedParameterError {
		List<String[]> parameters = getParameterList(PARAMETER_LOG);
		List<OperatorValueSelection> valueSelections = new LinkedList<>();
		for (String[] pair : parameters) {
			valueSelections.add(ParameterTypeValue.transformString2OperatorValueSelection(pair[1]));
		}
		return valueSelections;
	}

	public void createDataTable() throws OperatorException {
		getProcess().addDataTable(new SimpleDataTable(getName(), getColumnNames()));
	}

	@Override
	public void doWork() throws OperatorException {
		SimpleDataTable dataTable = (SimpleDataTable) getProcess().getDataTable(getName());
		if (dataTable == null) {
			createDataTable();
		}

		DataTableRow row = fetchAllValues();
		if (getParameterAsInt(PARAMETER_SORTING_TYPE) == SORTING_TYPE_NONE && getParameterAsBoolean(PARAMETER_PERSISTENT)) {
			writeOnline(row);
		}

		dummyPorts.passDataThrough();
	}

	private void writeOnline(DataTableRow row) throws UserError {
		DataTable table = getProcess().getDataTable(getName());
		File outputFile = getParameterAsFile(PARAMETER_FILENAME, true);
		try {
			// writing header if file does not exist or applyCount is 1 and file exists and has to
			// be overwritten
			if (!outputFile.exists() || getApplyCount() == 1) {
				try (FileWriter fw = new FileWriter(outputFile); PrintWriter out = new PrintWriter(fw)) {
					out.println("# Generated by " + getName() + "[" + getClass().getName() + "]");
					for (int j = 0; j < table.getNumberOfColumns(); j++) {
						out.print((j != 0 ? "\t" : "# ") + table.getColumnName(j));
					}
					out.println();
				}
			}
			// writing actual data
			try (FileWriter fw = new FileWriter(outputFile, true); PrintWriter out = new PrintWriter(fw)) {
				for (int j = 0; j < row.getNumberOfValues(); j++) {
					out.print((j != 0 ? "\t" : "") + table.getValueAsString(row, j));
				}
				out.println();
			}
		} catch (IOException e) {
			throw new UserError(this, 303, outputFile, e.getMessage());
		}
	}

	private DataTableRow fetchAllValues() throws UndefinedParameterError {
		Collection<OperatorValueSelection> valueDescriptions = getValueDescriptions();
		double[] row = new double[valueDescriptions.size()];
		int i = 0;
		for (OperatorValueSelection selection : valueDescriptions) {
			row[i] = fetchValue(selection, i);
			i++;
		}
		DataTableRow dataRow = new SimpleDataTableRow(row, null);
		SimpleDataTable dataTable = (SimpleDataTable) getProcess().getDataTable(getName());

		int sortingType = getParameterAsInt(PARAMETER_SORTING_TYPE);
		if (sortingType == SORTING_TYPE_NONE || dataTable.getNumberOfRows() < getParameterAsInt(PARAMETER_SORTING_K)) {
			dataTable.add(dataRow);
		} else {
			// sorting
			String sortingDimension = getParameterAsString(PARAMETER_SORTING_DIMENSION);
			int sortingDimensionIndex = dataTable.getColumnIndex(sortingDimension);

			if (dataTable.isNominal(sortingDimensionIndex)) {
				String currentWorst = null;
				int currentWorstIndex = -1;
				for (int r = 0; r < dataTable.getNumberOfRows(); r++) {
					double currentValue = dataTable.getRow(r).getValue(sortingDimensionIndex);
					String currentNominalValue = dataTable.mapIndex(sortingDimensionIndex, (int) currentValue);
					if (currentWorst == null || sortingType == SORTING_TYPE_TOP_K
							&& currentNominalValue.compareTo(currentWorst) < 0 || sortingType == SORTING_TYPE_BOTTOM_K
							&& currentNominalValue.compareTo(currentWorst) > 0) {
						currentWorst = currentNominalValue;
						currentWorstIndex = r;
					}
				}

				double candidateValue = dataRow.getValue(sortingDimensionIndex);
				String candidateNominalValue = dataTable.mapIndex(sortingDimensionIndex, (int) candidateValue);
				if (currentWorstIndex >= 0 && sortingType == SORTING_TYPE_TOP_K
						&& candidateNominalValue.compareTo(currentWorst) > 0 || sortingType == SORTING_TYPE_BOTTOM_K
						&& candidateNominalValue.compareTo(currentWorst) < 0) {
					dataTable.remove(dataTable.getRow(currentWorstIndex));
					dataTable.add(dataRow);
					dataTable.cleanMappingTables();
				}
			} else {
				double currentWorst = Double.NaN;
				int currentWorstIndex = -1;
				for (int r = 0; r < dataTable.getNumberOfRows(); r++) {
					double currentValue = dataTable.getRow(r).getValue(sortingDimensionIndex);
					if (Double.isNaN(currentWorst) || sortingType == SORTING_TYPE_TOP_K && currentValue < currentWorst
							|| sortingType == SORTING_TYPE_BOTTOM_K && currentValue > currentWorst) {
						currentWorst = currentValue;
						currentWorstIndex = r;
					}
				}

				double candidateValue = dataRow.getValue(sortingDimensionIndex);
				if (currentWorstIndex >= 0 && sortingType == SORTING_TYPE_TOP_K && candidateValue > currentWorst
						|| sortingType == SORTING_TYPE_BOTTOM_K && candidateValue < currentWorst) {
					dataTable.remove(dataTable.getRow(currentWorstIndex));
					dataTable.add(dataRow);
					dataTable.cleanMappingTables();
				}
			}
		}
		return dataRow;
	}

	@Override
	public void processFinished() throws OperatorException {
		super.processFinished();

		if (!getParameterAsBoolean(PARAMETER_PERSISTENT)) {
			DataTable table = getProcess().getDataTable(getName());
			if (table != null) {
				File file = null;
				try {
					file = getParameterAsFile(PARAMETER_FILENAME, true);
				} catch (UndefinedParameterError e) {
					// tries to determine a file for output writing
					// if no file was specified --> do not write results in file
				}
				if (file != null) {
					log("Writing data to '" + file.getName() + "'");
					try (FileWriter fw = new FileWriter(file); PrintWriter out = new PrintWriter(fw)) {
						table.write(out);
					} catch (IOException e) {
						throw new UserError(this, 303, file.getName(), e.getMessage());
					}
				}
			}
		}
	}

	@Override
	public List<ParameterType> getParameterTypes() {
		List<ParameterType> types = super.getParameterTypes();

		ParameterType type = new ParameterTypeFile(PARAMETER_FILENAME, "File to save the data to.", "log", true);
		type.setExpert(false);
		types.add(type);

		type = new ParameterTypeList(
				PARAMETER_LOG,
				"List of key value pairs where the key is the column name and the value specifies the process value to log.",
				new ParameterTypeString(PARAMETER_COLUMN_NAME, "The name of the column in the process log."),
				new ParameterTypeValue(PARAMETER_COLUMN_VALUE, "operator.OPERATORNAME.[value|parameter].VALUE_NAME"));
		type.setExpert(false);
		type.setPrimary(true);
		types.add(type);

		types.add(new ParameterTypeCategory(PARAMETER_SORTING_TYPE,
				"Indicates if the logged values should be sorted according to the specified dimension.", SORTING_TYPES,
				SORTING_TYPE_NONE));

		type = new ParameterTypeString(PARAMETER_SORTING_DIMENSION,
				"If the sorting type is set to top-k or bottom-k, this dimension is used for sorting.", true);
		type.registerDependencyCondition(new EqualTypeCondition(this, PARAMETER_SORTING_TYPE, SORTING_TYPES, true,
				SORTING_TYPE_TOP_K, SORTING_TYPE_BOTTOM_K));
		types.add(type);

		type = new ParameterTypeInt(PARAMETER_SORTING_K,
				"If the sorting type is set to top-k or bottom-k, this number of results will be kept.", 1,
				Integer.MAX_VALUE, 100);
		type.registerDependencyCondition(new EqualTypeCondition(this, PARAMETER_SORTING_TYPE, SORTING_TYPES, false,
				SORTING_TYPE_TOP_K, SORTING_TYPE_BOTTOM_K));
		types.add(type);

		type = new ParameterTypeBoolean(PARAMETER_PERSISTENT, "Indicates if results should be written to file immediately",
				false);
		type.registerDependencyCondition(new EqualTypeCondition(this, PARAMETER_SORTING_TYPE, SORTING_TYPES, false,
				SORTING_TYPE_NONE));
		types.add(type);

		return types;
	}

	/**
	 * Returns the array of log column names the user entered.
	 *
	 * @return the array, never {@code null}
	 * @throws UserError
	 *             if the user entered duplicate column names
	 */
	private String[] getColumnNames() throws UserError {
		List<String[]> parameters = getParameterList(PARAMETER_LOG);
		String columnNames[] = new String[parameters.size()];
		Iterator<String[]> i = parameters.iterator();
		int counter = 0;
		while (i.hasNext()) {
			String[] parameter = i.next();
			columnNames[counter++] = parameter[0];
		}

		// check for duplicates
		// performance is no real concern because the user has to manually enter these so we can
		// expect this to not be unreasonably large
		for (int j = 0; j < columnNames.length; j++) {
			for (int k = j + 1; k < columnNames.length; k++) {
				if (k != j && columnNames[j].equals(columnNames[k])) {
					throw new UserError(this, "log.duplicate_column", columnNames[k]);
				}
			}
		}

		return columnNames;
	}
}
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
package base.operators.operator.preprocessing.filter;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import base.operators.example.Attribute;
import base.operators.example.ExampleSet;
import base.operators.operator.annotation.ResourceConsumptionEstimator;
import base.operators.operator.preprocessing.AbstractDataProcessing;
import base.operators.tools.OperatorResourceConsumptionHandler;
import base.operators.operator.OperatorDescription;
import base.operators.operator.OperatorException;
import base.operators.operator.ProcessSetupError.Severity;
import base.operators.operator.SimpleProcessSetupError;
import base.operators.operator.UserError;
import base.operators.operator.ports.metadata.AttributeMetaData;
import base.operators.operator.ports.metadata.ExampleSetMetaData;
import base.operators.operator.ports.metadata.MetaData;
import base.operators.operator.tools.AttributeSubsetSelector;
import base.operators.parameter.ParameterType;
import base.operators.parameter.ParameterTypeRegexp;
import base.operators.parameter.ParameterTypeString;
import base.operators.parameter.UndefinedParameterError;


/**
 * <p>
 * This operator replaces parts of the attribute names (like whitespaces, parentheses, or other
 * unwanted characters) by a specified replacement. The replace_what parameter can be defined as a
 * regular expression (please refer to the annex of the RapidMiner tutorial for a description). The
 * replace_by parameter can be defined as an arbitrary string. Empty strings are also allowed.
 * Capturing groups of the defined regular expression can be accessed with $1, $2, $3...
 * </p>
 * 
 * @author Ingo Mierswa, Tobias Malbrecht
 */
public class ChangeAttributeNamesReplace extends AbstractDataProcessing {

	public static final String PARAMETER_REPLACE_WHAT = "replace_what";

	public static final String PARAMETER_REPLACE_BY = "replace_by";

	private final AttributeSubsetSelector attributeSelector = new AttributeSubsetSelector(this, getExampleSetInputPort());

	public ChangeAttributeNamesReplace(OperatorDescription description) {
		super(description);
	}

	@Override
	protected MetaData modifyMetaData(ExampleSetMetaData exampleSetMetaData) {
		String replaceWhat = "";
		try {
			ExampleSetMetaData subsetMetaData = attributeSelector.getMetaDataSubset(exampleSetMetaData, false);
			replaceWhat = getParameterAsString(PARAMETER_REPLACE_WHAT);
			Pattern replaceWhatPattern = Pattern.compile(replaceWhat);
			String replaceByString = isParameterSet(PARAMETER_REPLACE_BY) ? getParameterAsString(PARAMETER_REPLACE_BY) : "";

			for (AttributeMetaData attributeMetaData : subsetMetaData.getAllAttributes()) {
				String name = attributeMetaData.getName();

				exampleSetMetaData.getAttributeByName(name).setName(
						replaceWhatPattern.matcher(name).replaceAll(replaceByString));
			}
		} catch (UndefinedParameterError e) {
		} catch (IndexOutOfBoundsException e) {
			addError(new SimpleProcessSetupError(Severity.ERROR, getPortOwner(), "capturing_group_undefined",
					PARAMETER_REPLACE_BY, PARAMETER_REPLACE_WHAT));
		} catch (PatternSyntaxException e) {
			addError(new SimpleProcessSetupError(Severity.ERROR, getPortOwner(), "invalid_regex", replaceWhat));
		}

		return exampleSetMetaData;
	}

	@Override
	public ExampleSet apply(ExampleSet exampleSet) throws OperatorException {
		Set<Attribute> attributeSubset = attributeSelector.getAttributeSubset(exampleSet, false);
		Pattern replaceWhatPattern = Pattern.compile(getParameterAsString(PARAMETER_REPLACE_WHAT));
		String replaceByString = isParameterSet(PARAMETER_REPLACE_BY) ? getParameterAsString(PARAMETER_REPLACE_BY) : "";

		try {
			for (Attribute attribute : attributeSubset) {
				attribute.setName(replaceWhatPattern.matcher(attribute.getName()).replaceAll(replaceByString));
			}
		} catch (IndexOutOfBoundsException e) {
			throw new UserError(this, 215, replaceByString, PARAMETER_REPLACE_WHAT);
		}

		return exampleSet;
	}

	@Override
	public List<ParameterType> getParameterTypes() {
		List<ParameterType> types = super.getParameterTypes();
		types.addAll(attributeSelector.getParameterTypes());

		ParameterType type = new ParameterTypeRegexp(PARAMETER_REPLACE_WHAT,
				"A regular expression defining what should be replaced in the attribute names.", "\\W");
		type.setShowRange(false);
		type.setExpert(false);
		type.setPrimary(true);
		types.add(type);

		types.add(new ParameterTypeString(PARAMETER_REPLACE_BY,
				"This string is used as replacement for all parts of the matching attributes where the parameter '"
						+ PARAMETER_REPLACE_WHAT + "' matches.", true, false));
		return types;
	}

	@Override
	public boolean writesIntoExistingData() {
		return false;
	}

	@Override
	public ResourceConsumptionEstimator getResourceConsumptionEstimator() {
		return OperatorResourceConsumptionHandler.getResourceConsumptionEstimator(getInputPort(),
				ChangeAttributeNamesReplace.class, attributeSelector);
	}
}

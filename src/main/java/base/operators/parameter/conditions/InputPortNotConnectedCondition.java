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
package base.operators.parameter.conditions;

import base.operators.operator.Operator;
import base.operators.parameter.ParameterHandler;
import base.operators.parameter.PortProvider;
import base.operators.operator.ports.InputPort;


/**
 * This condition checks whether a {@link InputPort} of a {@link Operator} is connected or not. The
 * condition is fulfilled if the {@link InputPort} is NOT connected.
 * 
 * @deprecated use {@link PortConnectedCondition} instead
 * 
 * @author Nils Woehler
 * 
 */
@Deprecated
public class InputPortNotConnectedCondition extends PortConnectedCondition {

	public InputPortNotConnectedCondition(ParameterHandler handler, PortProvider portProvider, boolean becomeMandatory) {
		super(handler, portProvider, becomeMandatory, false);
	}

}

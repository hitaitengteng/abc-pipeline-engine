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
package base.operators.tools.config;

import base.operators.parameter.ParameterHandler;
import base.operators.parameter.ParameterType;
import base.operators.parameter.SimpleListBasedParameterHandler;


/**
 * The default {@link ParameterHandler} implementation for {@link Configurator}s.
 *
 * The method {@link #getParameterTypes()} has to be implemented to handle {@link ParameterType}
 * dependencies of {@link Configurable}s.
 *
 * @since 6.1.1 Extracted to public class
 *
 * @author Simon Fischer, Dominik Halfkann, Marco Boeck, Adrian Wilke
 * @deprecated since 9.1; use {@link SimpleListBasedParameterHandler} instead
 */
@Deprecated
public abstract class ConfiguratorParameterHandler extends SimpleListBasedParameterHandler {}

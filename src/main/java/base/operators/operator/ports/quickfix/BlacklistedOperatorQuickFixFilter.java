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
package base.operators.operator.ports.quickfix;

import java.util.function.Predicate;

import base.operators.operator.ports.metadata.MetaDataErrorQuickFixFilter;
import base.operators.tools.OperatorService;


/**
 * Filters out {@link OperatorInsertionQuickFix quick fixes} that try to create blacklisted operators
 *
 * @author Jonas Wilms-Pfau
 * @see MetaDataErrorQuickFixFilter MetaDataErrorQuickFixFilter
 * @since 9.0
 */
public class BlacklistedOperatorQuickFixFilter implements Predicate<QuickFix> {

	@Override
	public boolean test(QuickFix quickFix) {
		try {
			return !OperatorService.hasBlacklistedOperators() || !((quickFix instanceof OperatorInsertionQuickFix)
					&& OperatorService.isOperatorBlacklisted(((OperatorInsertionQuickFix) quickFix).createOperator().getOperatorDescription().getKey()));
		} catch (Exception ex) {
			return true;
		}
	}
}

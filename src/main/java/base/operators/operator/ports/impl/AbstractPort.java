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
package base.operators.operator.ports.impl;

import base.operators.adaption.belt.AtPortConverter;
import base.operators.operator.IOObject;
import base.operators.operator.PortUserError;
import base.operators.operator.UserError;
import base.operators.tools.AbstractObservable;
import base.operators.tools.ReferenceCache;
import base.operators.operator.ports.IncompatibleMDClassException;
import base.operators.operator.ports.Port;
import base.operators.operator.ports.Ports;
import base.operators.operator.ports.metadata.MetaData;
import base.operators.operator.ports.metadata.MetaDataError;
import base.operators.operator.ports.metadata.MetaDataErrorQuickFixFilter;
import base.operators.operator.ports.quickfix.BlacklistedOperatorQuickFixFilter;
import base.operators.operator.ports.quickfix.QuickFix;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Predicate;


/**
 * Implemented by keeping a weak reference to the data that can be cleared at any time by the
 * garbage collector.
 *
 * In addition to the week reference, this class also keeps a hard reference to the data, freeing it
 * when calling {@link #freeMemory()}.
 *
 * @author Simon Fischer
 *
 */
public abstract class AbstractPort extends AbstractObservable<Port> implements Port {

	private static final ReferenceCache<IOObject> IOO_REFERENCE_CACHE = new ReferenceCache<>(20);

	/** Filter used to sort out blacklisted operator insertion quick fixes */
	private static final Predicate<? super QuickFix> BLACKLISTED_OPERATOR_FILTER = new BlacklistedOperatorQuickFixFilter();

	private final List<MetaDataError> errorList = new LinkedList<>();
	private final Ports<? extends Port> ports;

	private String name;

	private ReferenceCache<IOObject>.Reference weakDataReference;

	private IOObject hardDataReference;

	private final boolean simulatesStack;
	private boolean locked = false;

	protected AbstractPort(Ports<? extends Port> owner, String name, boolean simulatesStack) {
		this.name = name;
		this.ports = owner;
		this.simulatesStack = simulatesStack;
	}

	protected final void setData(IOObject object) {
		// if there is a (G)UI and it is not in background => cache
//		if (!RapidMiner.getExecutionMode().isHeadless() && ports.getOwner() != null && ports.getOwner().getOperator() != null
//				&& ports.getOwner().getOperator().getProcess() != null && ports.getOwner().getOperator().getProcess()
//				.getRootOperator().getUserData(RapidMinerGUI.IS_GUI_PROCESS) != null) {
//			this.weakDataReference = IOO_REFERENCE_CACHE.newReference(object);
//		}
		this.hardDataReference = object;
	}

	@Deprecated
	@Override
	public <T extends IOObject> T getData() throws UserError {
		T data = this.<T> getDataOrNull();
		if (data == null) {
			throw new PortUserError(this, 149, getSpec() + (isConnected() ? " (connected)" : " (disconnected)"));
		} else {
			return data;
		}
	}

	@Override
	public IOObject getAnyDataOrNull() {
		if (hardDataReference != null) {
			return hardDataReference;
		} else {
			// This method is invoked from many places that should not keep the cache entry warm
			// (e.g., visualizations). Thus, perform only a weak get.
			return this.weakDataReference != null ? this.weakDataReference.weakGet() : null;
		}
	}

	@Override
	public <T extends IOObject> T getData(Class<T> desiredClass) throws UserError {
		IOObject data = getAnyDataOrNull();
		if (data == null) {
			throw new PortUserError(this, 149, getSpec() + (isConnected() ? " (connected)" : " (disconnected)"));
		} else if (desiredClass.isAssignableFrom(data.getClass())) {
			return desiredClass.cast(data);
		} else if (AtPortConverter.isConvertible(data.getClass(), desiredClass)) {
			return desiredClass.cast(AtPortConverter.convert(data, this));
		} else {
//			PortUserError error = new PortUserError(this, 156, RendererService.getName(data.getClass()), this.getName(),
//					RendererService.getName(desiredClass));
//			error.setExpectedType(desiredClass);
//			error.setActualType(data.getClass());
//			throw error;
		}
		return null;
	}

	@Override
	public <T extends IOObject> T getDataOrNull(Class<T> desiredClass) throws UserError {
		IOObject data = getAnyDataOrNull();
		if (data == null) {
			return null;
		} else if (desiredClass.isAssignableFrom(data.getClass())) {
			return desiredClass.cast(data);
		} else if (AtPortConverter.isConvertible(data.getClass(), desiredClass)) {
			return desiredClass.cast(AtPortConverter.convert(data, this));
//		} else {
//			PortUserError error = new PortUserError(this, 156, RendererService.getName(data.getClass()), this.getName(),
//					RendererService.getName(desiredClass));
//			error.setExpectedType(desiredClass);
//			error.setActualType(data.getClass());
//			throw error;
		}
		return null;
	}



	@SuppressWarnings("unchecked")
	@Deprecated
	@Override
	public <T extends IOObject> T getDataOrNull() throws UserError {
		IOObject data = getAnyDataOrNull();
		return (T) data;
	}

	@Override
	public final String getName() {
		return name;
	}

	@Override
	public String toString() {
		return getSpec();
	}

	@Override
	public Ports<? extends Port> getPorts() {
		return ports;
	}

	@Override
	public String getShortName() {
		if (name.length() > 3) {
			return name.substring(0, 3);
		} else {
			return name;
		}
	}

	/** Don't use this method. Use {@link Ports#renamePort(Port,String)}. */
	protected void setName(String newName) {
		this.name = newName;
	}

	@Override
	public void addError(MetaDataError metaDataError) {
		errorList.add(new MetaDataErrorQuickFixFilter(metaDataError, BLACKLISTED_OPERATOR_FILTER));
	}

	@Override
	public Collection<MetaDataError> getErrors() {
		return Collections.unmodifiableCollection(errorList);
	}

	@Override
	public void clear(int clearFlags) {
		if ((clearFlags & CLEAR_META_DATA_ERRORS) > 0) {
			this.errorList.clear();
		}
		if ((clearFlags & CLEAR_DATA) > 0) {
			this.weakDataReference = null;
			this.hardDataReference = null;
		}
	}

	/**
	 * Checks whether the desired class is assignable from provided meta data object class.
	 */
	protected void checkDesiredClass(MetaData obj, Class<? extends MetaData> desiredClass)
			throws IncompatibleMDClassException {
		if (!desiredClass.isAssignableFrom(obj.getClass())) {
			throw new IncompatibleMDClassException(getPorts().getOwner(), this);
		}
	}

	@Override
	public List<QuickFix> collectQuickFixes() {
		List<QuickFix> fixes = new LinkedList<>();
		for (MetaDataError error : getErrors()) {
			fixes.addAll(error.getQuickFixes());
		}
		Collections.sort(fixes);
		return fixes;
	}

	@Override
	public String getSpec() {
		if (getPorts() != null) {
			return getPorts().getOwner().getOperator().getName() + "." + getName();
		} else {
			return "DUMMY." + getName();
		}
	}

	@Override
	public boolean simulatesStack() {
		return simulatesStack;
	}

	@Override
	public boolean isLocked() {
		return locked;
	}

	@Override
	public void unlock() {
		this.locked = false;
	}

	@Override
	public void lock() {
		this.locked = true;
	}

	/** Releases of the hard reference. */
	@Override
	public void freeMemory() {
		this.hardDataReference = null;
	}
}
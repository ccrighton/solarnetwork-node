/* ==================================================================
 * ModbusController.java - Jul 10, 2013 7:14:40 AM
 * 
 * Copyright 2007-2013 SolarNetwork.net Dev Team
 * 
 * This program is free software; you can redistribute it and/or 
 * modify it under the terms of the GNU General Public License as 
 * published by the Free Software Foundation; either version 2 of 
 * the License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful, 
 * but WITHOUT ANY WARRANTY; without even the implied warranty of 
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU 
 * General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License 
 * along with this program; if not, write to the Free Software 
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 
 * 02111-1307 USA
 * ==================================================================
 */

package net.solarnetwork.node.control.sma.pcm;

import java.io.IOException;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import net.solarnetwork.domain.NodeControlInfo;
import net.solarnetwork.domain.NodeControlPropertyType;
import net.solarnetwork.node.NodeControlProvider;
import net.solarnetwork.node.io.modbus.ModbusConnectionCallback;
import net.solarnetwork.node.io.modbus.ModbusHelper;
import net.solarnetwork.node.io.modbus.ModbusSerialConnectionFactory;
import net.solarnetwork.node.reactor.Instruction;
import net.solarnetwork.node.reactor.InstructionHandler;
import net.solarnetwork.node.reactor.InstructionStatus.InstructionState;
import net.solarnetwork.node.settings.SettingSpecifier;
import net.solarnetwork.node.settings.SettingSpecifierProvider;
import net.solarnetwork.node.settings.support.BasicTextFieldSettingSpecifier;
import net.solarnetwork.node.settings.support.BasicTitleSettingSpecifier;
import net.solarnetwork.node.support.NodeControlInfoDatum;
import net.solarnetwork.util.DynamicServiceTracker;
import net.wimpi.modbus.net.SerialConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.context.support.ResourceBundleMessageSource;

/**
 * Toggle four Modbus "coil" type addresses to control the SMA Power Control
 * Module.
 * 
 * <p>
 * The configurable properties of this class are:
 * </p>
 * 
 * <dl class="class-properties">
 * <dt>d1Address</dt>
 * <dd>The Modbus address for the PCM D1 input.</dd>
 * <dt>d2Address</dt>
 * <dd>The Modbus address for the PCM D2 input.</dd>
 * <dt>d3Address</dt>
 * <dd>The Modbus address for the PCM D3 input.</dd>
 * <dt>d4Address</dt>
 * <dd>The Modbus address for the PCM D4 input.</dd>
 * </dl>
 * 
 * @author matt
 * @version 1.0
 */
public class ModbusController implements SettingSpecifierProvider, NodeControlProvider,
		InstructionHandler {

	private static MessageSource MESSAGE_SOURCE;

	private Integer d1Address = 0x4000;
	private Integer d2Address = 0x4002;
	private Integer d3Address = 0x4004;
	private Integer d4Address = 0x4006;

	private Integer unitId = 1;
	private String controlId = "/power/pcm/1";

	private DynamicServiceTracker<ModbusSerialConnectionFactory> connectionFactory;

	private final Logger log = LoggerFactory.getLogger(getClass());

	/**
	 * Get the values of the D1 - D4 discreet values, as a BitSet.
	 * 
	 * @return BitSet, with index 0 representing D1 and index 1 representing D2,
	 *         etc.
	 */
	private synchronized BitSet currentDiscreetValue() {
		BitSet result = ModbusHelper.readDiscreetValues(connectionFactory, new Integer[] { d1Address,
				d2Address, d3Address, d4Address }, 1, this.unitId);
		if ( log.isInfoEnabled() ) {
			log.info("Read discreet PCM values: {}", result);
		}
		return result;
	}

	/**
	 * Get the status value of the PCM, as an Integer.
	 * 
	 * <p>
	 * This returns the overall vale of the PCM, as an integer between 0 and 15.
	 * A value of 0 represent a 0% output setting, while 15 represents 100%.
	 * </p>
	 * 
	 * @return an integer between 0 and 15
	 */
	private Integer integerValueForBitSet(BitSet bits) {
		return ((bits.get(0) ? 1 : 0) | ((bits.get(1) ? 1 : 0) << 1) | ((bits.get(2) ? 1 : 0) << 2) | ((bits
				.get(3) ? 1 : 0) << 3));
	}

	/**
	 * Get the approximate power output setting, from 0 to 100.
	 * 
	 * <p>
	 * These values are described in the SMA documentation, it is not a direct
	 * percentage value derived from the value itself.
	 * </p>
	 */
	private Integer percentValueForIntegerValue(Integer val) {
		switch (val) {
			case 1:
				return 5;
			case 2:
				return 10;
			case 3:
				return 16;
			case 4:
				return 23;
			case 5:
				return 30;
			case 6:
				return 36;
			case 7:
				return 42;
			case 8:
				return 50;
			case 9:
				return 57;
			case 10:
				return 65;
			case 11:
				return 72;
			case 12:
				return 80;
			case 13:
				return 86;
			case 14:
				return 93;
			default:
				return (val < 1 ? 0 : 100);
		}
	}

	private synchronized boolean setPCMStatus(Integer desiredValue) {
		final BitSet bits = new BitSet(4);
		final int v = desiredValue;
		for ( int i = 0; i < 4; i++ ) {
			bits.set(i, ((v >> i) & 1) == 1);
		}
		log.info("Setting PCM status to {} ({}%)", desiredValue,
				percentValueForIntegerValue(desiredValue));
		final Integer[] addresses = new Integer[] { d1Address, d2Address, d3Address, d4Address };
		return ModbusHelper.execute(connectionFactory, new ModbusConnectionCallback<Boolean>() {

			@Override
			public Boolean doInConnection(SerialConnection conn) throws IOException {
				return ModbusHelper.writeDiscreetValues(conn, addresses, bits, unitId);
			}

		});
	}

	// NodeControlProvider

	@Override
	public List<String> getAvailableControlIds() {
		return Collections.singletonList(controlId);
	}

	@Override
	public NodeControlInfo getCurrentControlInfo(String controlId) {
		// read the control's current status
		log.debug("Reading PCM {} status", controlId);
		NodeControlInfoDatum result = null;
		try {
			Integer value = integerValueForBitSet(currentDiscreetValue());
			result = newNodeControlInfoDatum(controlId, value);
		} catch ( RuntimeException e ) {
			log.error("Error reading PCM {} status: {}", controlId, e.getMessage());
		}
		return result;
	}

	private NodeControlInfoDatum newNodeControlInfoDatum(String controlId, Integer status) {
		NodeControlInfoDatum info = new NodeControlInfoDatum();
		info.setCreated(new Date());
		info.setSourceId(controlId);
		info.setType(NodeControlPropertyType.Integer);
		info.setReadonly(false);
		info.setValue(status.toString());
		return info;
	}

	// InstructionHandler

	@Override
	public boolean handlesTopic(String topic) {
		return InstructionHandler.TOPIC_SET_CONTROL_PARAMETER.equals(topic);
	}

	@Override
	public InstructionState processInstruction(Instruction instruction) {
		// look for a parameter name that matches a control ID
		InstructionState result = null;
		log.debug("Inspecting instruction {} against control {}", instruction.getId(), controlId);
		for ( String paramName : instruction.getParameterNames() ) {
			log.trace("Got instruction parameter {}", paramName);
			if ( controlId.equals(paramName) ) {
				// treat parameter value as a decimal integer, value between 0-15
				String str = instruction.getParameterValue(controlId);
				Integer desiredValue = Integer.parseInt(str);
				if ( setPCMStatus(desiredValue) ) {
					result = InstructionState.Completed;
				} else {
					result = InstructionState.Declined;
				}
			}
		}
		return result;
	}

	// SettingSpecifierProvider

	@Override
	public String getSettingUID() {
		return "net.solarnetwork.node.control.sma.pcm";
	}

	@Override
	public String getDisplayName() {
		return "SMA Power Control Module";
	}

	@Override
	public List<SettingSpecifier> getSettingSpecifiers() {
		ModbusController defaults = new ModbusController();
		List<SettingSpecifier> results = new ArrayList<SettingSpecifier>(20);

		// get current value
		BasicTitleSettingSpecifier status = new BasicTitleSettingSpecifier("status", "N/A", true);
		try {
			BitSet bits = currentDiscreetValue();
			Integer val = integerValueForBitSet(bits);
			String binValue = Integer.toBinaryString(val);
			String padding = "";
			if ( binValue.length() < 4 ) {
				padding = String.format("%0" + (4 - binValue.length()) + "d", 0);
			}
			status.setDefaultValue(String.format("%s%s - %d%%", padding, binValue,
					percentValueForIntegerValue(val)));
		} catch ( RuntimeException e ) {
			log.debug("Error reading PCM status: {}", e.getMessage());
		}
		results.add(status);

		results.add(new BasicTextFieldSettingSpecifier("connectionFactory.propertyFilters['UID']",
				"/dev/ttyUSB0"));
		results.add(new BasicTextFieldSettingSpecifier("unitId", defaults.unitId.toString()));
		results.add(new BasicTextFieldSettingSpecifier("controlId", defaults.controlId.toString()));
		results.add(new BasicTextFieldSettingSpecifier("d1Address", defaults.d1Address.toString()));
		results.add(new BasicTextFieldSettingSpecifier("d2Address", defaults.d2Address.toString()));
		results.add(new BasicTextFieldSettingSpecifier("d3Address", defaults.d3Address.toString()));
		results.add(new BasicTextFieldSettingSpecifier("d4Address", defaults.d4Address.toString()));

		return results;
	}

	@Override
	public MessageSource getMessageSource() {
		if ( MESSAGE_SOURCE == null ) {
			ResourceBundleMessageSource source = new ResourceBundleMessageSource();
			source.setBundleClassLoader(getClass().getClassLoader());
			source.setBasename(getClass().getName());
			MESSAGE_SOURCE = source;
		}
		return MESSAGE_SOURCE;
	}

	public Integer getD1Address() {
		return d1Address;
	}

	public void setD1Address(Integer d1Address) {
		this.d1Address = d1Address;
	}

	public Integer getD2Address() {
		return d2Address;
	}

	public void setD2Address(Integer d2Address) {
		this.d2Address = d2Address;
	}

	public Integer getD3Address() {
		return d3Address;
	}

	public void setD3Address(Integer d3Address) {
		this.d3Address = d3Address;
	}

	public Integer getD4Address() {
		return d4Address;
	}

	public void setD4Address(Integer d4Address) {
		this.d4Address = d4Address;
	}

	public DynamicServiceTracker<ModbusSerialConnectionFactory> getConnectionFactory() {
		return connectionFactory;
	}

	public void setConnectionFactory(
			DynamicServiceTracker<ModbusSerialConnectionFactory> connectionFactory) {
		this.connectionFactory = connectionFactory;
	}

	public Integer getUnitId() {
		return unitId;
	}

	public void setUnitId(Integer unitId) {
		this.unitId = unitId;
	}

	public String getControlId() {
		return controlId;
	}

	public void setControlId(String controlId) {
		this.controlId = controlId;
	}

}

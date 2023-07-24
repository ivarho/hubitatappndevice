/**
 * Copyright 2023 Ivar Holand
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 *
 * Change log:
 * - New driver
 *
 */

metadata {
	definition (name: "VSR300 Systemair", namespace: "iholand", author: "Ivar Holand") {
		capability "Sensor"
		capability "FilterStatus"
		capability "FanControl"
		capability "Thermostat"
		capability "ThermostatSetpoint"
		capability "ThermostatMode"
		capability "ThermostatOperatingState"
		capability "Polling"

		attribute "registerValue", "number"
		attribute "lastWriteActionStatus", "string"
		attribute "readRegister", "number"
		attribute "supplyAir", "number"
		attribute "exhaustAir", "number"
		attribute "extractAir", "number"
		attribute "outdoorAir", "number"
		attribute "frostProtSensor", "number"

		command "sendTestData"
		command "closeConnection"
		//command "readRegisterValue"
		command "readRegisterValue", [[name: "register", type: "NUMBER"]]
		command "writeRegisterValue", [[name: "register*", type: "NUMBER"], [name: "value*", type: "NUMBER"]]
		command "readRelevantTemperatures"
		command "readSetPoint"
		command "readRotorState"
	}

	preferences {
		input name: "controller_ip", type: "text", title: "Controller IP", description: "Input the MODBUS controller IP", required: true, displayDuringSetup: true
		input name: "controller_port", type: "number", title: "Controller PORT", description: "Input the MODBUS controller PORT", required: true, displayDuringSetup: true

		input name: "client_address", type: "number", title: "Client Address", description: "Input the MODBUS client address (1-247)", required: true, displayDuringSetup: true
		input name: "client_register", type: "number", title: "Client Register to Read", description: "Input the MODBUS client register address", required: true, displayDuringSetup: true

		input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true
	}
}

def debug (message) {
	if (logEnable) log.debug message
}
def installed() {
	updated()
}

def initialize()
{
	updated()
}

def updated () {
	state.transactionIdentifier = 0

	supportedFanSpeeds = new groovy.json.JsonBuilder(["low","medium","high"])

	//speed - ENUM ["low","medium-low","medium","medium-high","high","on","off","auto"]
	sendEvent(name: "speed", value: "medium")

	groovy.json.JsonBuilder fanSpeeds = new groovy.json.JsonBuilder(["low","medium","high"])
	sendEvent([name: "supportedFanSpeeds", value: fanSpeeds])

	sendEvent(name: "filterStatus", value: "normal") // filterStatus - ENUM ["normal", "replace"]

	unschedule()

	schedule('0 */1 * ? * *', poll)
}

def setSpeed(fanspeed) {
	debug "Setting fan speed $fanspeed"

	if (device.currentValue("supportedFanSpeeds").contains(fanspeed)) {
		fanspeedTranslated = 2

		switch(fanspeed) {
			case "low":
			fanspeedTranslated = 1
			break
			case "medium":
			fanspeedTranslated = 2
			break
			case "high":
			fanspeedTranslated = 3
			break
			default:
			fanspeedTranslated = 2
		}

		writeRegisterValue(101, fanspeedTranslated)
	}

	readSpeed()
}

def poll() {
	readRelevantTemperatures()
	runInMillis(3000, readSetPoint)
	runInMillis(6000, readRotorState)
	runInMillis(9000, readSpeed)
}

def readSpeed()
{
	readRegisterValue(101)
}

def readRelevantTemperatures()
{
	readRegisterValue(214, 5)
}

def readSetPoint()
{
	readRegisterValue(222)
}

def readRotorState()
{
	readRegisterValue(352)
}

def setHeatingSetpoint(temperature) {
	if (temperatuer < 16) {
		temperature = 16
	} else if (temperature > 19) {
		temperature = 19
	}

	unschedule(auto)

	setSetpoint(temperature)
}

def setCoolingSetpoint(temperature) {
	sendEvent(name: "coolingSetpoint", value: temperature)
}

def setSetpoint(temperature) {
	temperature = temperature * 10 // Scale for VSR

	writeRegisterValue(222, temperature)

	readSetPoint()

}

def setThermostatMode(mode) {
}

def setThermostatFanMode(mode) {
}

def auto(){
	setSetpoint(16)
}

def off() {
}

def heat() {
}

def emergencyHeat() {
}

def cool() {
	setSetpoint(0)

	runIn(30*60, auto)
}


def cycleSpeed() {
	debug "Cycling speed"
}

def closeConnection() {
	interfaces.rawSocket.close()
}

def readRegisterValue(register=settings.client_register, numRegs=1) {

	byte[] modbusFrame = createModbusFrame(register, 3, null, numRegs)
	byte[] tcpModbusFrame = createTcpModbusFrame(modbusFrame)

	sendEvent(name: "readRegister", value: register)

	String msg = hubitat.helper.HexUtils.byteArrayToHexString(tcpModbusFrame)

	interfaces.rawSocket.connect(settings.controller_ip, settings.controller_port.toInteger(), byteInterface: true, readDelay: 500)
	interfaces.rawSocket.sendMessage(msg)
}

def writeRegisterValue(register, value) {
	byte[] modbusFrame = createModbusFrame(register as int, 0x06, value as int)
	byte[] tcpModbusFrame = createTcpModbusFrame(modbusFrame)

	state.register = register
	state.value = value

	String msg = hubitat.helper.HexUtils.byteArrayToHexString(tcpModbusFrame)

	interfaces.rawSocket.connect(settings.controller_ip, settings.controller_port.toInteger(), byteInterface: true, readDelay: 500)
	interfaces.rawSocket.sendMessage(msg)
}

def createModbusFrame(register, int function=3, value=null, numRegs=1) {
	ByteArrayOutputStream modbusStreamBuffer = new ByteArrayOutputStream()

	debug "Number of regs $numRegs"

	int mobus_reg_address = register - 1

	modbusStreamBuffer.write(settings.client_address as byte)
	modbusStreamBuffer.write(function as byte) // function code
	modbusStreamBuffer.write(((mobus_reg_address >> 8) & 0xff) as byte)
	modbusStreamBuffer.write(((mobus_reg_address) & 0xff) as byte)

	if (function == 0x03) { // Read holding register
		modbusStreamBuffer.write(((numRegs >> 8) & 0xff) as byte) // number of registers to read HIGH
		modbusStreamBuffer.write(((numRegs) & 0xff) as byte) // number of registers to read LOW
	} else if (function == 0x06 && value != null) {// Write single register
		modbusStreamBuffer.write(((value >> 8) & 0xff) as byte)
		modbusStreamBuffer.write(((value) & 0xff) as byte)
	}

	byte[] modbusFrame = modbusStreamBuffer.toByteArray()

	debug hubitat.helper.HexUtils.byteArrayToHexString(modbusFrame)

	return modbusFrame
}

def createTcpModbusFrame(byte[] modbusFrame) {
	ByteArrayOutputStream modbusTcpStreamBuffer = new ByteArrayOutputStream()

	transactionIdentifier = state.transactionIdentifier
	state.transactionIdentifier = transactionIdentifier + 1

	length = modbusFrame.size()

	modbusTcpStreamBuffer.write(((transactionIdentifier >> 8) & 0xff) as byte)
	modbusTcpStreamBuffer.write(((transactionIdentifier) & 0xff) as byte)
	modbusTcpStreamBuffer.write(0)
	modbusTcpStreamBuffer.write(0)
	modbusTcpStreamBuffer.write(((length >> 8) & 0xff) as byte)
	modbusTcpStreamBuffer.write(((length) & 0xff) as byte)

	for (i = 0; i < length; i++) {
		modbusTcpStreamBuffer.write(modbusFrame[i])
	}

	byte[] modbusTcpFrame = modbusTcpStreamBuffer.toByteArray()

	debug hubitat.helper.HexUtils.byteArrayToHexString(modbusTcpFrame)

	return modbusTcpFrame
}

def parse(incoming_data) {
	debug "*************** START ********************"
	if(logEnable) log.debug "parse $incoming_data"

	// Typical reply: 0001 0000 0009 00 03 06 0906 0000 0000

	byte[] msg_byte = hubitat.helper.HexUtils.hexStringToByteArray(incoming_data)

	transaction_identifier = new BigInteger([msg_byte[0], msg_byte[1]] as byte[]).intValue()
	protocol_identifier = new BigInteger([msg_byte[2], msg_byte[3]] as byte[]).intValue()
	incoming_length = new BigInteger([msg_byte[4], msg_byte[5]] as byte[]).intValue()

	if (protocol_identifier != 0) { //MODBUS protocol
		debug "Not identified as a MODBUS protocol, i.e. bytes 2-3 is not '0' value is $procol_identifier"
		interfaces.rawSocket.close()
		return
	}

	debug "Incoming data length: $incoming_length"

	ByteArrayOutputStream mobus_frame_tmp = new ByteArrayOutputStream()
	for (i = 0; i < incoming_length; i++) {
		mobus_frame_tmp.write(msg_byte[6 + i])
	}

	byte[] modbus_frame = mobus_frame_tmp.toByteArray()

	parse_MODBUS(modbus_frame, incoming_length, transaction_identifier)


	interfaces.rawSocket.close()
}

def parse_MODBUS(byte[] frame, lenght, transaction_identifier=0) {
	debug "Parsing MODBUS"

	// Typlical MODBUS frame: 00 03 02 0906
	unit = new BigInteger([frame[0]] as byte[])
	function_code = new BigInteger([frame[1]] as byte[])

	if (function_code < 0) {
		// Error

		debug "Error from slave: $function_code"

		error_code = new BigInteger([frame[2]] as byte[])

		switch (error_code) {
			case 0x01:
			debug "Illegal Function"
			break
			case 0x02:
			debug "Illegal Data Address"
			break
			case 0x03:
			debug "Illegal Data Value"
			break
			default:
			debug "Unknown error: $error_code"
			break
		}

		return
	}

	if (function_code == 0x03) { // Result of read holding register

		data_length = new BigInteger([frame[2]] as byte[])

		debug data_length

		if (data_length > 0) {

			BigInteger[] values = new BigInteger[data_length/2]

			for (i = 0; i < data_length/2; i++) {
				values[i] = new BigInteger([frame[3+(2*i)], frame[4+(2*i)]] as byte[])
			}

			debug values

			sendEvent(name: "registerValue", value: values[0])

			if (device.currentValue("readRegister") == 101) { // Fan speed
				switch (values[0]) {
					case 1:
					fanspeed = "low"
					break
					case 2:
					fanspeed = "medium"
					break
					case 3:
					fanspeed = "high"
					break
					case 0:
					fanspeed = "off"
					break
					default:
					fanspeed = "medium"
				}

				sendEvent(name: "speed", value: fanspeed)
				sendEvent(name: "thermostatFanMode", value: "on")
			}

			if (device.currentValue("readRegister") == 214) { // Temperatures
				sendEvent(name: "supplyAir", value: "Supply air: ${values[0]/10}&deg;C")
				sendEvent(name: "temperature", value: values[0]/10)
				sendEvent(name: "extractAir", value: "Extract air: ${values[1]/10}&deg;C")
				sendEvent(name: "exhaustAir", value: values[2]/10)
				sendEvent(name: "frostProtSensor", value: "Frost protection: ${values[3]/10}&deg;C")
				sendEvent(name: "outdoorAir", value: "Outdoor air: ${values[4]/10}&deg;C")
			}

			if (device.currentValue("readRegister") == 222) { // Set point
				sendEvent(name: "thermostatSetpoint", value: values[0]/10)
				sendEvent(name: "heatingSetpoint", value: values[0]/10)
				//sendEvent(name: "coolingSetpoint", value: values[0]/10)

			}

			if (device.currentValue("readRegister") == 352) { // Rotor state
				if (values[0] == 1) {
					sendEvent(name: "thermostatMode", value: "auto")
					sendEvent(name: "thermostatOperatingState", value: "heating")
				} else if (values[0] == 0) {
					sendEvent(name: "thermostatMode", value: "auto")
					sendEvent(name: "thermostatOperatingState", value: "fan only")
				}
			}
		}
	} else if (function_code == 0x06) {
		int writtenRegister = new BigInteger([frame[2], frame[3]] as byte[]).intValue()
		int writtenValue = new BigInteger([frame[4], frame[5]] as byte[]).intValue()

		writtenRegister = writtenRegister + 1

		if (writtenRegister == state.register && writtenValue == state.value) {
			debug "Value ($writtenValue) successfully written to register: $writtenRegister"
			sendEvent(name: "lastWriteActionStatus", value: "OK, transaction: $transaction_identifier")
		} else {
			sendEvent(name: "lastWriteActionStatus", value: "Fail, transaction: $transaction_identifier")

		}
	}

}

def sendTestData () {

	ByteArrayOutputStream data = new ByteArrayOutputStream()

	data_hex = "000100000006010300640001"

	data.write(hubitat.helper.HexUtils.hexStringToByteArray(data_hex))

	byte[] message = data.toByteArray()

	String msg = hubitat.helper.HexUtils.byteArrayToHexString(message)

	interfaces.rawSocket.connect(settings.controller_ip, settings.controller_port.toInteger(), byteInterface: true, readDelay: 500)
	interfaces.rawSocket.sendMessage(msg)
}

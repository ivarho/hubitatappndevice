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
        capability "RelativeHumidityMeasurement"

		attribute "registerValue", "number"
		attribute "lastWriteActionStatus", "string"
		attribute "readRegister", "number"
		attribute "registerValue", "number"
		attribute "supplyAir", "number"
		attribute "exhaustAir", "number"
		attribute "extractAir", "number"
		attribute "outdoorAir", "number"
		attribute "frostProtSensor", "number"
		attribute "targetSetpoint", "number"
		attribute "restoreSetpoint", "number"
        attribute "filterPeriodDays", "number"
        attribute "filterUsed", "string"

		command "sendTestData"
		command "closeConnection"
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
	debug "Updated"

	state.transactionIdentifier = 0

	state.commands = []

	supportedFanSpeeds = new groovy.json.JsonBuilder(["low","medium","high"])

	//speed - ENUM ["low","medium-low","medium","medium-high","high","on","off","auto"]
	sendEvent(name: "speed", value: "medium")

	//groovy.json.JsonBuilder fanSpeeds = new groovy.json.JsonBuilder(["low","medium","high"])
	sendEvent(name: "supportedFanSpeeds", value: new groovy.json.JsonBuilder(["low", "medium", "high"]))

	sendEvent(name: "supportedThermostatModes", value: new groovy.json.JsonBuilder(["auto", "cool", "boost"]))
	sendEvent(name: "supportedThermostatFanModes", value: new groovy.json.JsonBuilder(["on", "auto"]))

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
	readSetPoint()
	readRotorState()
	readSpeed()
    readRH()
    readFilterDays()
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

def readRH()
{
    readRegisterValue(381)
}

def readFilterDays()
{
    readRegisterValue(601) // Filter period in months
    readRegisterValue(602) // Filter used number of days
}

def setHeatingSetpoint(temperature) {
	if (temperature < 16) {
		temperature = 16
	} else if (temperature > 19) {
		temperature = 19
	}

	unschedule(auto)

	sendEvent(name: "targetSetpoint", value: temperature)

	runIn(3, updateSetPoint)
}

def setCoolingSetpoint(temperature) {
	sendEvent(name: "coolingSetpoint", value: temperature)
}

def updateSetPoint() {
	setSetpoint(device.currentValue("targetSetpoint"))
}

def setSetpoint(temperature) {
	temperature = temperature * 10 // Scale for VSR

	writeRegisterValue(222, temperature)

	readSetPoint()
}

def setThermostatMode(mode) {
	debug "Setting thermostat mode $mode"

	if (mode == "cool") {
		cool()
	} else if (mode == "auto") {
		unschedule(auto)
		auto()
    } else if (mode == "boost") {
        boost()
    }

	poll()
}

def setThermostatFanMode(mode) {
	debug "Setting fan mode $mode"
}

def auto(){
	debug "Auto mode"
	setSetpoint(device.currentValue("restoreSetpoint"))
	setSpeed("medium")
}

def off() {
}

def heat() {
}

def emergencyHeat() {
}

def cool() {
	debug "Setting Cool mode"

	sendEvent(name: "restoreSetpoint", value: device.currentValue("thermostatSetpoint"))

	setSetpoint(0)
	setSpeed("high")

	runIn(30*60, auto)
}

def boost() {
    debug "Setting boost mode"

    sendEvent(name: "restoreSetpoint", value: device.currentValue("thermostatSetpoint"))

    setSpeed("high")
    runIn(30*60, auto)
}

def cycleSpeed() {
	debug "Cycling speed"
}

def closeConnection() {
	interfaces.rawSocket.close()
}

def addCommandToBuffer(byte[] cmd, register, value=null) {
	bundle = []

	bundle.add(cmd)
	bundle.add(register)
	bundle.add(value)

	state.commands.add(0, bundle)
}

def sendFromBuffer() {
	try {
		bundle = state.commands.pop()
	} catch(java.util.NoSuchElementException e1) {
		debug "No more messages to send"
		return
	}

	if (bundle != null) {
		sendEvent(name: "readRegister", value: bundle[1])
		if (bundle[2] != null) {
			sendEvent(name: "registerValue", value: bundle[2])
		}

		String msg = hubitat.helper.HexUtils.byteArrayToHexString(bundle[0] as byte[])

		interfaces.rawSocket.connect(settings.controller_ip, settings.controller_port.toInteger(), byteInterface: true, readDelay: 500)
		interfaces.rawSocket.sendMessage(msg)

		runIn(5, sendTimeout)
	}
}

def sendTimeout() {
	log.error "Timeout on sending, no response from VSR"
	runInMillis(1000, sendFromBuffer) // Retry
}

def readRegisterValue(register=settings.client_register, numRegs=1) {

	byte[] modbusFrame = createModbusFrame(register, 3, null, numRegs)
	byte[] tcpModbusFrame = createTcpModbusFrame(modbusFrame)

	addCommandToBuffer(tcpModbusFrame, register)

	runInMillis(1, sendFromBuffer)
}

def writeRegisterValue(register, value) {
	byte[] modbusFrame = createModbusFrame(register as int, 0x06, value as int)
	byte[] tcpModbusFrame = createTcpModbusFrame(modbusFrame)

	addCommandToBuffer(tcpModbusFrame, register, value)

	runInMillis(1, sendFromBuffer)
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

	if (transactionIdentifier > (65536)) {
		state.transactionIdentifier = 0
		transactionIdentifier = 0
	}

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
	unschedule(sendTimeout)
	runInMillis(100, sendFromBuffer)

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

            if (device.currentValue("readRegister") == 381) { // Relative humidity, RH
                sendEvent(name: "humidity", value: values[0])
            }

            if (device.currentValue("readRegister") == 601) { // Filter period in months
                sendEvent(name: "filterPeriodDays", value: values[0] * (365.25/12))
            }

            if (device.currentValue("readRegister") == 602) { // Filter used for days
                filterStatus = (values[0] / device.currentValue("filterPeriodDays")) * 100

                filterStatus = (filterStatus as float).round(0)

                if (filterStatus > 100) {
                    sendEvent(name: "filterStatus", value: "replace")
                } else {
                    sendEvent(name: "filterStatus", value: "normal")
                }

                debug "FilterStatus: $filterStatus"

                sendEvent(name: "filterUsed", value: "Filter used: $filterStatus %")
            }
		}
	} else if (function_code == 0x06) {
		int writtenRegister = new BigInteger([frame[2], frame[3]] as byte[]).intValue()
		int writtenValue = new BigInteger([frame[4], frame[5]] as byte[]).intValue()

		writtenRegister = writtenRegister + 1

		if (writtenRegister == device.currentValue("readRegister") && writtenValue == device.currentValue("registerValue")) {
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

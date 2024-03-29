/**
 * Copyright 2020 Ivar Holand
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
 */

metadata {
	definition (name: "Multisensor", namespace: "iholand", author: "Ivar Holand") {
		capability "Temperature Measurement"
		capability "Health Check"
		capability "Battery"
		capability "Relative Humidity Measurement"
		capability "Switch"
		capability "PresenceSensor"

		attribute "lastCheckin", "String"
		attribute "batteryVoltage", "number"
		attribute "maxTemp", "number"
		attribute "minTemp", "number"
		attribute "s_address", "String"
		attribute "raw_humid", "number"
		attribute "debugMsg", "String"
		attribute "rssi", "number"
		attribute "debugTemperature", "number"

		command "setTemperature"
		command "setBatteryVoltage"
		command "setHumidity"
		command "resetMaxMin"
		command "checked_in"
		command "setStatus"
		command "setDebugMessage"
		command "setRSSI"
		command "debugMode"
		command "normalMode"
	}

	preferences {
		input "timeoutOffline", "number", title: "Offline timeout", description: "Set how many minutes before reporting offline"
		input "useRawHumid", "bool", title: "Use raw humid:"
		input "humidAtMax", "number", title: "Raw at max humid:"
		input "humidAtMin", "number", title: "Raw at min himid:"
		input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true
		input name: "forceStateChangeOnTempChange", type: "bool", title: "Always treat temperature update as state change", defaultValue: false
		input name: "forceStateChangeOnCheckinChange", type: "bool", title: "Always treat checkin as state change", defaultValue: false
	}
}

def logsOff() {
	log.warn "debug logging disabled..."
	device.updateSetting("logEnable", [value: "false", type: "bool"])
}

def updated(settings) {
	log.debug "Updated with settings: $settings"
	log.debug "$timeoutOffline"

	if (timeoutOffline == null) {
		log.debug "Timeout is null"
	}

	if (logEnable) runIn(1800, logsOff)

	state.mode = "normal"

	//setTemperature(20.92)
	//setBatteryVoltage(2000)
}

// parse events into attributes
def parse(String description) {
	if (logEnable) log.debug "Parsing '${description}'"
	// TODO: handle 'temperature' attribute

}

def ping() {
	return 0
}

def on() {
	sendEvent(name: "switch", value: "on")
}

def off() {
	sendEvent(name: "switch", value: "off")
}

def normalMode()
{
	state.mode = "normal"
	sendEvent(name: "mode", value: state.mode)
}

def debugMode()
{
	state.mode = "debug"
	sendEvent(name: "mode", value: state.mode)
}

def resetMaxMin() {
	def temp = device.currentValue("temperature")

	if (logEnable) log.debug "Resetting Max Min to: ${temp}"

	state.maxTemp = temp
	state.minTemp = temp

	updateMaxMin()
}

def updateMaxMin() {
	sendEvent(name: "maxTemp", value: state.maxTemp)
	sendEvent(name: "minTemp", value: state.minTemp)
}


def checked_in() {
	def devID = device.deviceNetworkId;

	if (logEnable) log.debug "Device id: $devID"

	sendEvent(name: "s_address", value: devID)

	def timeString = new Date().format("yyyy-MM-dd HH:mm:ss", location.timeZone)
	if (logEnable) log.debug("$timeString")
	if(forceStateChangeOnCheckinChange) sendEvent(name: "lastCheckin", value: timeString)

	state.lastCheckin = timeString

	sendEvent(name: "presence", value: "present")

	def timeout = 0

	if (timeoutOffline == null) {
		timeout = 30
	} else {
		timeout = timeoutOffline
	}

	runIn(60*timeout, timeoutHandler)
}

// handle commands
def setTemperature(temperature) {
	if (logEnable) log.debug "Executing 'setTemperature'";
	// TODO: handle 'setTemperature' command

	if (state.mode == null) {
		state.mode = "normal"
	}

	if (temperature > -60 && temperature < 300 && state.mode == "normal") {
		sendEvent(name: "temperature", value: temperature.round(1), unit: "°C", isStateChange: forceStateChangeOnTempChange)
	} else {
		sendEvent(name: "debugTemperature", value: temperature, unit: "°C")
	}


	if (temperature > state.maxTemp) {
		state.maxTemp = temperature
	}

	if (temperature < state.minTemp) {
		state.minTemp = temperature
	}

	updateMaxMin()

	checked_in()

}

def setBatteryVoltage(voltage) {
	if (logEnable) log.debug "Executing 'setBatteryVoltage'";

	bat_percentage = (voltage - 2700)/(3255-2700)*100.0
	bat_percentage = bat_percentage.toFloat()

	sendEvent(name: "batteryVoltage", value: voltage)
	sendEvent(name: "battery", value: bat_percentage.round(0), unit: "%")

	checked_in()
}

def map(x, input_start, input_end, output_start, output_end)
{
	return ((x - input_start)/(input_end - input_start)*(output_end - output_start) + output_start).toFloat()
}

def setHumidity(humid, raw = 9999) {
	if (logEnable) log.debug "Executing 'setHumidity(${humid})'";

	if (useRawHumid) {
		humid = map(raw.toInteger(), humidAtMin.toInteger(), humidAtMax.toInteger(), 0, 100);
	}

	sendEvent(name: "humidity", value: humid.round(1), unit: "%")

	sendEvent(name: "raw_humid", value: raw)

	checked_in()
}

def setStatus(status)
{
	if (logEnable) log.debug("Executing setStatus ${status}")

	if (status == "0") {
		off()
	} else if (status == "1") {
		on()
	} else if (status == "2") {
		on()
	} else if (status == "3") {
		off()
	}

	checked_in()
}

def setDebugMessage(message)
{
	if (logEnable) log.debug("Debug message: ${message}")

	sendEvent(name: "debugMsg", value: message)

	checked_in()
}

def setRSSI(rssi)
{
	if (logEnable) log.debug("Rssi: ${rssi}")

	sendEvent(name: "rssi", value: rssi, unit: "dBm")
}

def timeoutHandler()
{
	def displayName = device.displayName

	log.warn "Lost connection to sensor: $displayName"

	sendEvent(name: "healthStatus", value: "offline")
	sendEvent(name: "presence", value: "not present")
}

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

definition (
	name: "MQTT-SN App",
	namespace: "iholand",
	author: "Ivar Holand",
	description: "MQTT-SN Receiver",
	category: "Convenience",
	iconUrl: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png",
	iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png",
	iconX3Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png")


preferences {
	section("Debugging") {
		input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true
		input name: "acceptNewDevices", type: "bool", title: "Accept new devices for 2 minutes", defaultValue: true
	}
}

mappings {
	path("/temperature") {
		action: [
		GET: "listThermostats"
		]
	}
	path("/temperature/:id/:temperature") {
		action: [
		PUT: "updateTemperature"
		]
	}
	path("/temperature/:id/:temperature/:rssi") {
		action: [
		PUT: "updateTemperature"
		]
	}
	path("/battery/:id/:voltage") {
		action: [
		PUT:"updateBattery",
		POST:"updateBattery"
		]
	}
	path("/battery/:id/:voltage/:rssi") {
		action: [
		PUT:"updateBattery",
		POST:"updateBattery"
		]
	}
	path("/status/:id/:on_off") {
		action: [
		PUT: "updateStatus"
		]
	}
	path("/status/:id/:on_off/:rssi") {
		action: [
		PUT: "updateStatus"
		]
	}
	path("/gstatus/:id/:sub_id") {
		action: [
		GET: "getStatus"
		]
	}
	path("/gstatus/:id/:sub_id/:rssi") {
		action: [
		GET: "getStatus"
		]
	}
	path("/humidity/:id/:humid/:raw") {
		action: [
		PUT: "updateHumidity"
		]
	}
	path("/humidity/:id/:humid/:raw/:rssi") {
		action: [
		PUT: "updateHumidity"
		]
	}
	path("/humidity/:id/:humid") {
		action: [
		PUT: "updateHumidity"
		]
	}
	path("/humidity/:id/:humid/:rssi") {
		action: [
		PUT: "updateHumidity"
		]
	}
	path("/power/:id/:power") {
		action: [
		PUT: "updatePower"
		]
	}
	path("/energy/:id/:energy") {
		action: [
		PUT: "updateEnergy"
		]
	}
	path("/smokeD/:address/:value") {
		action: [
		PUT: "smokeDetector"
		]
	}
	path("/smokeD/:address/:value/:rssi") {
		action: [
		PUT: "smokeDetector"
		]
	}
	path("/switch/:address/:group/:on_off/:channel") {
		action: [
		PUT: "Mswitch"
		]
	}
	path("/switch/:address/:group/:on_off/:channel/:rssi") {
		action: [
		PUT: "Mswitch"
		]
	}

	path("/debug/:id/:message/:parameters/:parameters2") {
		action: [
			PUT: "debugMessage"
		]
	}
	path("/debug/:id/:message/:parameters/:parameters2/:rssi") {
		action: [
			PUT: "debugMessage"
		]
	}
	path("/debug/:id/:message/:parameters") {
		action: [
			PUT: "debugMessage"
		]
	}
	path("/debug/:id/:message/:parameters/:rssi") {
		action: [
			PUT: "debugMessage"
		]
	}
	path("/debug/:id/:message") {
		action: [
			PUT: "debugMessage"
		]
	}
	path("/debug/:id/:message/:rssi") {
		action: [
			PUT: "debugMessage"
		]
	}
	path("/:other/:id/:data") {
		action: [
		PUT: "other"
		]
	}
	path("/:other/:id/:data/:rssi") {
		action: [
		PUT: "other"
		]
	}
}

def installed() {
	log.debug "installed"
	createAccessToken()
	initialize()
}

def logsOff() {
	log.warn "debug logging disabled..."
	app.updateSetting("logEnable", [value: "false", type: "bool"])
}

def newDevicesOff() {
	log.warn "Does not accept new devices any more..."
	app.updateSetting("acceptNewDevices", [value: "false", type: "bool"])
}

def updated() {
	initialize()

	log.debug "updated"
	log.debug "Access URL:" + getFullLocalApiServerUrl()
	log.debug "Access token:" + state.accessToken

	if (logEnable) runIn(1800, logsOff)
	if (acceptNewDevices) runIn(2*60, newDevicesOff)
}

def initialize() {
	log.debug "initialize"
	log.debug "ventDevices: " + contactSensors
	log.debug "numberOption: " + devName
	unschedule()
	//runEvery5Minutes(checkDevices)
}
def uninstalled() {
	log.debug "uninstalled"
}

def checkDevices() {
	log.debug "checkDevices"
}

void updateTemperature() {
	// use the built-in request object to get the command parameter
	def id = params.id
	def temperature = Float.parseFloat(params.temperature)

	def tempSensor = getChildDevices().find{it.deviceNetworkId == id}

	if (params.rssi != null) {
		rssi = (-100.0 + (params.rssi.toInteger() * 1.03))
	} else {
		rssi = 0
	}

	if (tempSensor != null) {
		tempSensor.setTemperature(temperature)
		tempSensor.setRSSI(rssi)
		//tempSensor.sendEvent(name: "temperature", value: temperature)
		if (logEnable) log.debug("${tempSensor.displayName} (${tempSensor.name}) = $temperature *C, RSSI = $rssi")
	} else if (acceptNewDevices) {
		def newTempSensor = addChildDevice("iholand", "Multisensor",  id, null, [name: id, componentName: id, componentLabel: id])
		newTempSensor.setTemperature(temperature)
		newTempSensor.setRSSI(rssi)
		//newTempSensor.sendEvent(name: "temperature", value: temperature)
		if (logEnable) log.debug("${newTempSensor.name}) = $temperature *C, RSSI = $rssi")
	} else {
		log.warn "New temperature device attempting to connect: $id, $temperature"
	}
}

def updateBattery() {
	def id = params.id
	def voltage = Float.parseFloat(params.voltage)

	def tempSensor = getChildDevices().find{it.deviceNetworkId == id}

	if (params.rssi != null) {
		rssi = (-100.0 + (params.rssi.toInteger() * 1.03))
	} else {
		rssi = 0
	}

	if (tempSensor != null) {
		tempSensor.setBatteryVoltage(voltage)
		tempSensor.setRSSI(rssi)
		if (logEnable) log.debug("${tempSensor.displayName} (${tempSensor.name}) = $voltage mV, RSSI = $rssi")
	} else if (acceptNewDevices) {
		def newTempSensor = addChildDevice("iholand", "Multisensor",  id, null, [name: id, componentName: id, componentLabel: id])
		newTempSensor.setBatteryVoltage(voltage)
		newTempSensor.setRSSI(rssi)
		if (logEnable) log.debug("${newTempSensor.name} = $voltage mV")
	} else {
		log.warn "New battery device attempting to connect: $id, $voltage"
	}
}

void updateHumidity() {
	def id = params.id
	def humid = Float.parseFloat(params.humid)
	def raw = params.raw

	if (raw == null) {
		if (logEnable) log.debug("Raw NULL")
		raw = 9999;
	}

	if (params.rssi != null) {
		rssi = (-100.0 + (params.rssi.toInteger() * 1.03))
	} else {
		rssi = 0
	}

	def Sensor = getChildDevices().find{it.deviceNetworkId == id}

	if (Sensor != null) {
		Sensor.setHumidity(humid, raw)
		Sensor.setRSSI(rssi)
		 if (logEnable) log.debug("${Sensor.displayName} (${Sensor.name}) = $humid %, $raw, RSSI = $rssi")

	} else if (acceptNewDevices) {
		def newSensor = addChildDevice("iholand", "Multisensor",  id, null, [name: id, componentName: id, componentLabel: id])
		newSensor.setHumidity(humid, raw)
		newSensor.setRSSI(rssi)
		if (logEnable) log.debug("${newSensor.name} = $humid %")
	} else {
		log.warn "New humid device attempting to connect: $id, $humid"
	}
}

void updateStatus() {
	def id = params.id
	def on_off = params.on_off

	def status = getChildDevices().find{it.deviceNetworkId == id}

	if (status != null) {
		status.setStatus(on_off)
		if (logEnable) log.debug("${status.displayName} (${status.name}) = ${on_off==1?"on":"off"}")
	} else if (acceptNewDevices) {
		def newStatus = addChildDevice("iholand", "General Switch",  id, null, [name: id, componentName: id, componentLabel: id])
		newStatus.setStatus(on_off)
	} else {
		log.warn "New status device attempting to connect: $id, $on_off"
	}
}

def updatePower() {
	def id = params.id + "-pm"
	def power = Float.parseFloat(params.power)

	def powerMeter = getChildDevices().find{it.deviceNetworkId == id}

	if (powerMeter != null) {

		powerMeter.setPower(power)

		if (powerMeter.displayName.contains("Flow")) {
			if (logEnable) log.debug("${powerMeter.displayName} (${powerMeter.name}) = $power l/h - ${(power/60).round(2)} l/m")
		} else {
			if (logEnable) log.debug("${powerMeter.displayName} (${powerMeter.name}) = $power W")
		}

	} else if (acceptNewDevices) {
		def newPowerMeter = addChildDevice("iholand", "Power Meter",  id, null, [name: id, componentName: id, componentLabel: id])

		newPowerMeter.setPower(power)
		if (logEnable) log.debug("${newPowerMeter.name} = $power W")

	} else {
		log.warn "New power device attempting to connect: $id, $power"
	}
}

def updateEnergy() {
	def id = params.id + "-pm"
	def power = Float.parseFloat(params.energy)

	if (power == null) {
		power = 0.0
	}

	if (logEnable) log.debug("Energy from sensor ${id}: ${power} kWh")

	def powerMeter = getChildDevices().find{it.deviceNetworkId == id}

	if (powerMeter != null) {

		powerMeter.setEnergy(power)

		if (logEnable) log.debug("${powerMeter.displayName} (${powerMeter.name}) = $power kWh")

	} else if (acceptNewDevices) {
		def newPowerMeter = addChildDevice("iholand", "Power Meter",  id, null, [name: id, componentName: id, componentLabel: id])

		newPowerMeter.setEnergy(power)
		if (logEnable) log.debug("${newPowerMeter.name} = $power kWh")

	} else {
		log.warn "New energy device attempting to connect: $id, $power"
	}
}

def smokeDetector() {
	def address = params.address
	def value = params.value

	def id = address

	if (logEnable) log.debug("Smoke detected from sensor ${id}: ${address}")

	def smokeDetectorDevice = getChildDevices().find{it.deviceNetworkId == id}

	if (smokeDetectorDevice != null) {

		smokeDetectorDevice.setDetectionLevel(address, value)

		if (logEnable) log.debug("${smokeDetectorDevice.displayName} (${smokeDetectorDevice.name}) = $address, $value")

	} else {
		def newsmokeDetectorDevice = addChildDevice("iholand", "Smoke Detector",  id, null, [name: id, componentName: id, componentLabel: id])

		newsmokeDetectorDevice.setDetectionLevel(address, value)
		if (logEnable) log.debug("${newsmokeDetectorDevice.name} = $address, $value")

		log.warn "New smoke detector device discovered and reporting detected: $id, $address, $value"
	}
}

def Mswitch() {
	def address = params.address
	def group = params.group
	def on_off = params.on_off
	def channel = params.channel
	def id = "$address$channel"

	if (params.on_off == "1") {
		on_off = "0"
	} else if (params.on_off == "0") {
		on_off = "1"
	}

	if (logEnable) log.debug("Switch event from ${address}: ${on_off} on channel: $channel, with group: $group")

	def SwitchDevice = getChildDevices().find{it.deviceNetworkId == id}

	if (SwitchDevice != null) {

		SwitchDevice.setStatus(on_off)

		if (logEnable) log.debug("${SwitchDevice.displayName} (${SwitchDevice.name}) = $address, $on_off, $group, $channel")

	} else if (acceptNewDevices) {
		def newSwitchDevice = addChildDevice("iholand", "Multisensor",  id, null, [name: id, componentName: id, componentLabel: id])

		newSwitchDevice.setStatus(on_off)
		if (logEnable) log.debug("${newSwitchDevice.name} = $address, $on_off, $group, $channel")

	} else {
		log.warn "New switch device attempting to connect: $address, $on_off, $group, $channel"
	}
}

void other() {
	def protocol = params.other
	def id = params.id
	def data = params.data

	log.warn("Unhandled device type: ${protocol}")
	log.warn("ID: ${id}")
	log.warn("DATA: ${data}")
}

def getStatus() {
	def id = params.id
	def sub_id = params.sub_id

	if (params.rssi != null) {
		rssi = (-100.0 + (params.rssi.toInteger() * 1.03))
	} else {
		rssi = 0
	}

	def device = getChildDevices().find{it.deviceNetworkId == id}

	def state = device.currentValue("switch")

	def pb0 = 0

	if (state == "on") {
		pb0 = 1
	}

	if (logEnable) log.debug("Device: ${device.displayName} (${device.name}) requesting status on channel $sub_id, will return: $id/$sub_id/$pb0, RSSI = $rssi")

	device.checked_in()

	render contentType: "text/html", data: "$id/$sub_id/$pb0", status: 200
}

def debugMessage() {
	def id = params.id
	def message = params.message
	def addparam = params.parameters
	def addparam2 = params.parameters2

	if (params.rssi != null) {
		rssi = (-100.0 + (params.rssi.toInteger() * 1.03))
	} else {
		rssi = 0
	}

	def DebugDevice = getChildDevices().find{it.deviceNetworkId == id}

	if (DebugDevice != null) {

		DebugDevice.setDebugMessage("${message}, ${addparam}, ${addparam2}")
		DebugDevice.setRSSI(rssi)

		if (logEnable) log.debug("${DebugDevice.displayName} (${DebugDevice.name}) = $id, $message, $addparam, $addparam2, RSSI = $rssi")

	} else if (acceptNewDevices) {
		def newDebugDevice = addChildDevice("iholand", "Multisensor",  id, null, [name: id, componentName: id, componentLabel: id])

		newDebugDevice.setStatus(on_off)
		if (logEnable) log.debug("${newDebugDevice.name} = $id, $message, $addparam, $addparam2")

	} else {
		log.warn("New device debug message from: $id, $message, $addparam, $addparam2")
	}
}

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
	path("/battery/:id/:voltage") {
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
	path("/gstatus/:id/:sub_id") {
		action: [
		GET: "getStatus"
		]
	}
	path("/humidity/:id/:humid/:raw") {
		action: [
		PUT: "updateHumidity"
		]
	}
	path("/humidity/:id/:humid") {
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
	path("/:other/:id/:data") {
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
	device.updateSetting("logEnable", [value: "false", type: "bool"])
}

def updated() {
	log.debug "updated"
	log.debug "Access URL:" + getFullLocalApiServerUrl()
	log.debug "Access token:" + state.accessToken

	if (logEnable) runIn(1800, logsOff)

	initialize()
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

	if (tempSensor != null) {
		tempSensor.setTemperature(temperature)
		//tempSensor.sendEvent(name: "temperature", value: temperature)
		if (logEnable) log.debug("${tempSensor.displayName} (${tempSensor.name}) = $temperature *C")
	} else {
		def newTempSensor = addChildDevice("iholand", "Multisensor",  id, null, [name: id, componentName: id, componentLabel: id])
		newTempSensor.setTemperature(temperature)
		//newTempSensor.sendEvent(name: "temperature", value: temperature)
		if (logEnable) log.debug("${newTempSensor.name}) = $temperature *C")
	}
}

def updateBattery() {
	def id = params.id
	def voltage = Float.parseFloat(params.voltage)

	def tempSensor = getChildDevices().find{it.deviceNetworkId == id}

	if (tempSensor != null) {
		tempSensor.setBatteryVoltage(voltage)
		if (logEnable) log.debug("${tempSensor.displayName} (${tempSensor.name}) = $voltage mV")
	} else {
		def newTempSensor = addChildDevice("iholand", "Multisensor",  id, null, [name: id, componentName: id, componentLabel: id])
		newTempSensor.setBatteryVoltage(voltage)
		if (logEnable) log.debug("${newTempSensor.name} = $voltage mV")
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

	def Sensor = getChildDevices().find{it.deviceNetworkId == id}

	if (Sensor != null) {
		Sensor.setHumidity(humid, raw)
		 if (logEnable) log.debug("${Sensor.displayName} (${Sensor.name}) = $humid %, $raw")

	} else {
		def newSensor = addChildDevice("iholand", "Multisensor",  id, null, [name: id, componentName: id, componentLabel: id])
		newSensor.setHumidity(humid, raw)
		if (logEnable) log.debug("${newSensor.name} = $humid %")
	}
}

void updateStatus() {
	def id = params.id
	def on_off = params.on_off

	def status = getChildDevices().find{it.deviceNetworkId == id}

	if (status != null) {
		status.setStatus(on_off)
		if (logEnable) log.debug("${status.displayName} (${status.name}) = ${on_off?"on":"off"}")
	} else {
		def newStatus = addChildDevice("iholand", "General Switch",  id, null, [name: id, componentName: id, componentLabel: id])
		newStatus.setStatus(on_off)
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

	} else {
		def newPowerMeter = addChildDevice("iholand", "Power Meter",  id, null, [name: id, componentName: id, componentLabel: id])

		newPowerMeter.setPower(power)
		if (logEnable) log.debug("${newPowerMeter.name} = $power W")

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

	} else {
		def newPowerMeter = addChildDevice("iholand", "Power Meter",  id, null, [name: id, componentName: id, componentLabel: id])

		newPowerMeter.setEnergy(power)
		if (logEnable) log.debug("${newPowerMeter.name} = $power kWh")

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

	log.debug("Device requesting status: $id, $sub_id")

	def device = getChildDevices().find{it.deviceNetworkId == id}

	def state = device.currentValue("switch")

	def pb0 = 0

	if (state == "on") {
		pb0 = 1
	}

	device.checked_in()

	render contentType: "text/html", data: "$id/$sub_id/$pb0", status: 200
}


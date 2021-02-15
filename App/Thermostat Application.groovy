/**
 *  Thermostat Application
 *
 *  Copyright 2020 Ivar Holand
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 */
definition(
	name: "Termostat Application",
	namespace: "iholand",
	author: "Ivar Holand",
	description: "Termostat Application",
	category: "Convenience",
	iconUrl: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png",
	iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png",
	iconX3Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png")


preferences {
	section("Device Selection") {
		input "temperatureSensor", "capability.temperatureMeasurement", title: "Temperature sensor:", required: true
		input "thermostat", "capability.thermostat", title: "Thermostat:", required: true
		input "cooler", "capability.switch", title: "Cooling element:"
		input "heater", "capability.switch", title: "Heating element:"
		input "invertedHeater", "bool", title: "Invert heater output?", default: false
		input "debugEnabled", "bool", title: "Enable debugging"
	}
}

def installed() {
	log.debug "Installed with settings: ${settings}"

	initialize()
}

def uninstalled() {
	log.debug "Uninstalled"
	unsubscribe()
}

def updated() {
	log.debug "Updated with settings: ${settings}"

	thermostat.setTemperature(20.0)

	unsubscribe()
	initialize()
}

def initialize() {
	subscribe(temperatureSensor, "temperature", updateTemperature)
	subscribe(thermostat, "thermostatOperatingState", modeHandler)
}

def updateTemperature(evt) {
	if(debugEnabled) log.debug "Input temperature: ${evt.value}"

	thermostat.setTemperature(evt.value)
}

def modeHandler(evt) {
	if(debugEnabled) log.debug "Thermostat mode: ${evt.value}"

	def mode = evt.value

	if (mode == "cooling") {
		// Turn off heater
		heating(false)
		// Turn on cooling
		cooling(true)
	} else if (mode == "heating") {
		// Turn on heater
		heating(true)
		// Turn off cooling
		cooling(false)
	} else {
		// All off
		heating(false)
		cooling(false)
	}
}

def heating(boolean heat_on) {
	if (heater != null) {
		if (heat_on) {
			if (invertedHeater) {
				heater.off()
			} else {
				heater.on()
			}
		} else {
			if (invertedHeater) {
				heater.on()
			} else {
				heater.off()
			}
		}
	}
}

def cooling(boolean cool_on) {
	if (cooler != null) {
		if (cool_on) {
			cooler.on()
		} else {
			cooler.off()
		}
	}
}

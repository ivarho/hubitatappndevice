/**
 *  Power Meter
 *
 *  Copyright 2019 Ivar Holand
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
metadata {
	definition (name: "Power Meter", namespace: "iholand", author: "Ivar Holand") {
		capability "Power Meter"
		capability "Energy Meter"
		capability "Sensor"

		attribute "energyTodayLBL", "string"
		attribute "energyTillTodayLBL", "string"
		attribute "powerLBL", "string"

		// Used as flow meter as well
		attribute "flowL_hour_LBL", "string"
		attribute "flowL_minute_LBL", "string"

		command "setPower"
		command "setEnergy"
	}
}

preferences {
	input "Energy to today", "decimal"
	input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true
}

def logsOff() {
	log.warn "debug logging disabled..."
	device.updateSetting("logEnable", [value: "false", type: "bool"])
}


def updated(settings) {
	log.debug "Updated with settings: $settings"

	state.energy_today = 0

	if (logEnable) runIn(1800, logsOff)

	startNewDay()

	schedule("0 0 0 * * ?", startNewDay)

	//state.energy_to_today = 0

	setPower(9999)
	setEnergy(0)
}

// parse events into attributes
def parse(String description) {
	log.debug "Parsing '${description}'"
	// TODO: handle 'temperature' attribute

}

// handle commands
def setPower(power) {
	if (logEnable) log.debug "Executing 'setPower'"

	sendEvent(name: "powerLBL", value: "${power} W", isStateChange: true)
	sendEvent(name: "power", value: power, unit: "W", isStateChange: true)

	def liters_per_minute = power.toFloat().round(1)

	// Same device used as flow meter
	sendEvent(name: "flowL_hour_LBL", value: "${power} L/h", isStateChange: true)
	sendEvent(name: "flowL_minute_LBL", value: "${liters_per_minute} L/m", isStateChange: true)

}

def setEnergy(energy) {
	if (logEnable) log.debug "Executing 'setEnergy'";
	// TODO: handle 'setTemperature' command

	if (logEnable) log.debug(energy)

	def energy_to_today = state.energy_to_today

	if (energy_to_today == null) {
		energy_to_today = 0.0
	}

	if (logEnable) log.debug("Energy to today: '${state.energy_to_today}'")

	state.energy_total = energy.toFloat().round(1)

	def energy_today = (energy - energy_to_today).toFloat().round(1)

	if (energy_today < 0) {
		energy_today = (energy - 0).toFloat().round(1)
	}

	state.energy_today = energy_today

	if (logEnable) log.debug("Energy today: $state.energy_today")

	sendEvent(name: "energyTodayLBL", value: "${energy_today.round(1)} kWh", isStateChange: true)
	sendEvent(name: "energyTillTodayLBL", value: "${energy} kWh", isStateChange: true)

	sendEvent(name: "energy", value: energy_today.round(1), unit: "kWh", isStateChange: true)
	sendEvent(name: "energy_total", value: energy, unit: "kWh", isStateChange: true)
}

def startNewDay() {
	if (logEnable) log.debug "Calculating new day"

	def energy_today = state.energy_today.toFloat().round(1)

	sendEvent(name: "energy_yesterday", value: energy_today, unit: "kWh", isStateChange: true)

	state.energy_to_today = state.energy_total

}

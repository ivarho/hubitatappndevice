/**
 * Copyright 2021 Ivar Holand
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

definition(
	name: "CoolingEffectCalculator",
	namespace: "iholand",
	author: "Ivar Holand",
	description: "Cooling Effect Calculator",
	category: "",
	iconUrl: "",
	iconX2Url: "",
	iconX3Url: "")


preferences {
	section("Settings") {
		input "temperatureIN", "capability.temperatureMeasurement", title: "Air temperature in", multiple: false
		input "temperatureOUT", "capability.temperatureMeasurement", title: "Air temperature out", multiple: false

		input "outputLO", "capability.powerMeter", title: "Display power LOW device", multiple: false
		input "outputHI", "capability.powerMeter", title: "Display power HIGH device", multiple: false


		//input "someMessage", "text", title: "Some message to input"
		input "fanSpeedLO", "number", title: "Fan speed lo, m3/h"
		input "fanSpeedHI", "number", title: "Fan speed hi, m3/h"

		// Notifications
		//input "audioNotification", "capability.speechSynthesis", title: "Audio Notifier:"
		//input "textNotification", "capability.notification", title: "Text Notifier:"

		// Debug Control
		input "enableDebug", "bool", title: "Enable debug"
		input "enableTrace", "bool", title: "Enable trace"
		input "enableInfo", "bool", title: "Enable info"
	}
}

// Housekeeping
def logsOff() {
	log.warn "debug logging disabled..."
	device.updateSetting("enableDebug", [value: "false", type: "bool"])
}

def logDebug(msg) {
	if (enableDebug) log.debug msg
}

def logTrace(msg) {
	if (enableTrace) log.trace msg
}

def logInfo(msg) {
	if (enableInfo) log.info msg
}

// Initialize functions

def installed() {
	log.debug "Installed with settings: ${settings}"

	initialize()
}

def uninstalled() {
	log.debug "App uninstalled"
}

def updated() {
	log.debug "Updated with settings: ${settings}"

	// unschedule(...)
	initialize()
}

def initialize() {
	//schedule("0 */10 * ? * *", ...)
	subscribe(temperatureIN, "temperature", CalculateCoolingEffect)
	subscribe(temperatureOUT, "temperature", CalculateCoolingEffect)

	// Turn off logs after 30 min
	if (enableDebug) runIn(30*60, logsOff)

	CalculateCoolingEffect()
}

// App code here

def CalculateCoolingEffect(evt=null)
{
	tempIN = temperatureIN.currentValue("temperature")
	tempOUT = temperatureOUT.currentValue("temperature")

	cooling_effetct = (tempIN-tempOUT) * 0.33

	cooling_effect_lo = fanSpeedLO * cooling_effetct
	cooling_effect_hi = fanSpeedHI * cooling_effetct

	if (enableDebug) log.debug("Cooling effect on low fan speed: ${cooling_effect_lo}")
	if (enableDebug) log.debug("Cooling effect on high fan speed: ${cooling_effect_hi}")

	if (outputLO != null) {
		outputLO.sendEvent(name: "power", value: Math.round(cooling_effect_lo), unit: "W")
	}

	if (outputHI != null) {
		outputHI.sendEvent(name: "power", value: Math.round(cooling_effect_hi), unit: "W")
	}
}

/**
 *  Device Health Monitor
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
	name: "Device Health Monitor",
	namespace: "iholand",
	author: "Ivar Holand",
	description: "Device Health Monitor",
	category: "",
	iconUrl: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png",
	iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png",
	iconX3Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png")


preferences {
	section("Monitor these devices") {
		input "presenceSensors", "capability.presenceSensor", title: "Monitor:", multiple: true
		input "warningMessage", "text", title: "Warning message:"
		input "audioNotification", "capability.speechSynthesis", title: "Audio Notifier:"
		input "textNotification", "capability.notification", title: "Text Notifier:"
		input "infoPresenceSensor", "capability.presenceSensor", title: "Select precense sensor for status:"

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

def installed() {
	log.debug "Installed with settings: ${settings}"

	initialize()
}

def uninstalled() {
	log.debug "App uninstalled"
}

def updated() {
	log.debug "Updated with settings: ${settings}"

	unschedule(checkAllPrecenseSensorsPresent)
	initialize()
}

def initialize() {
	schedule("0 */10 * ? * *", checkAllPrecenseSensorsPresent)
	state.online = 1
	state.devicesOffline = []

	if (enableDebug) runIn(30*60, logsOff)
}

def presenceChangedHandler(evt) {
	logTrace "presenceChangedHandler(evt)"

	def presence = evt.getDevice().currentValue("presence")
	def deviceName = evt.getDevice().displayName

	logDebug "Presence updated to: $presence for $deviceName"

	if (infoPresenceSensor != null) {

		def anyDevicesNotPresent = false;

		presenceSensors.each {presenceSensor ->
			logDebug presenceSensor.label + ": " + presenceSensor.currentValue("presence", true)
			if (presenceSensor.currentValue("presence") == "not present") {
				anyDevicesNotPresent = true
			}
		}

		if (anyDevicesNotPresent) {
			infoPresenceSensor.departed()
		} else {
			infoPresenceSensor.arrived()
		}
	}

	if (presence == "not present" && state.online == 1) {
		log.warn "$warningMessage med $deviceName"
		state.online = 0

		if (infoPresenceSensor != null) {
			infoPresenceSensor.departed()
		}

		textNotification.deviceNotification("$deviceName: $warningMessage")
		audioNotification.speak("Dette er en informasjonsmelding: mistet kontakten med $deviceName")

	} else if (presence == "present" && state.online == 0) {
		logDebug "$onlineMessage"
		state.online = 1

		textNotification.deviceNotification("$deviceName: $onlineMessage")
	}
}

def checkAllPrecenseSensorsPresent() {
	logTrace "checkAllPrecenseSensorsPresent()"

	if (infoPresenceSensor != null) {
		logTrace "infoPresenceSensor found"

		def devicesPresent = true;
		def shouldWarn = false;

		presenceSensors.each {presenceSensor ->
			logDebug presenceSensor.label + ": " + presenceSensor.currentValue("presence", true)
			if (presenceSensor.currentValue("presence") == "not present") {
				devicesPresent = false

				if (presenceSensor.displayName in state.devicesOffline) {
					// Already warned about this device
				} else {
					state.devicesOffline.add(presenceSensor.displayName)
					shouldWarn = true
				}
			}
		}

		if (devicesPresent == false) {
			infoPresenceSensor.departed()
		} else {
			infoPresenceSensor.arrived()
			state.devicesOffline = []
		}

		if (shouldWarn == true) {
			//log.warn "$warningMessage med $deviceName"
			state.online = 0

			def deviceName = ""

			state.devicesOffline.each { devName ->
				deviceName += devName + " "
			}

			if (infoPresenceSensor != null) {
				infoPresenceSensor.departed()
			}

			textNotification?.deviceNotification("$deviceName: $warningMessage")
			audioNotification?.speak("Dette er en informasjonsmelding: mistet kontakten med $deviceName")

		}

		return devicesPresent
	}
}

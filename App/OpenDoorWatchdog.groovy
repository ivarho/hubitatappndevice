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

definition(
	name: "Open Door watchdog",
	namespace: "iholand",
	author: "Ivar Holand",
	description: "This can be used as a starting point for new Hubitat App development",
	category: "",
	iconUrl: "",
	iconX2Url: "",
	iconX3Url: "")


preferences {
	section("Settings") {
		input "contactSensor", "capability.contactSensor", title: "Select contract sensor to monitor", multiple: false
		input "colorBulbsToControl", "capability.colorControl", title: "Select color bulbs to control", multiple: true

		input "openDoorMessage", "text", title: "Message to speak after 3 min"
		input "firstWarning", "number", title: "Seconds to first warning", default: 40
		input "secondWarning", "number", title: "Seconds to second warning", default: 3*60

		// Notifications
		input "audioNotification", "capability.speechSynthesis", title: "Audio Notifier:"
		input "textNotification", "capability.notification", title: "Text Notifier:"

		// Debug Control
		input "enableDebug", "bool", title: "Enable debug"
		input "enableTrace", "bool", title: "Enable trace"
		input "enableInfo", "bool", title: "Enable trace"
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
	unsubscribe()
	subscribe(contactSensor, "contact.open", FridgeDoorOpen)
	subscribe(contactSensor, "contact.closed", FridgeDoorClosed)


	// Turn off logs after 30 min
	if (enableDebug) runIn(30*60, logsOff)

	// Define Globals
	state.openTimeout = false
	state.bulbSettings = [:]
}

// App code here
def FridgeDoorOpen(evt) {
	logTrace "Fridge door open"
	runIn(firstWarning, FridgeDoorOpenTimeout)
	runIn(secondWarning, NotifyDoorOpen)
}

def FridgeDoorOpenTimeout() {
	logTrace "FridgeDoorOpenTimeout"

	state.openTimeout = true

	colorBulbsToControl.each { bulb ->
		String bulbName = bulb.getLabel()

		logDebug bulbName

		// Store current bulb settings
		state.bulbSettings[bulbName] = ['switch': bulb.currentValue("switch"), 'color': ['hue': bulb.currentValue("hue"), 'saturation': bulb.currentValue("saturation"), 'level': bulb.currentValue("level")]]

		// Set color blue
		bulb.setColor([hue: 66, saturation: 100, level: 50])
	}
}

def FridgeDoorClosed(evt) {
	logTrace "Fridge door closed"

	unschedule(FridgeDoorOpenTimeout)
	unschedule(NotifyDoorOpen)

	if (state.openTimeout == true) {
		colorBulbsToControl.each { bulb ->
			String bulbName = bulb.getLabel()

			// Restore bulb settings
			if (state.bulbSettings[bulbName] != null) {
				bulb.setColor(state.bulbSettings[bulbName].color)

				if (state.bulbSettings[bulbName].switch == "off") {
					runIn(3, switchBulbOff, [device: bulb])
				}
			}
		}
	}
}

def NotifyDoorOpen() {
	audioNotification?.speak(openDoorMessage)
	textNotification?.deviceNotification(openDoorMessage)
}

def switchBulbOff(device) {
	device.device.off()
}

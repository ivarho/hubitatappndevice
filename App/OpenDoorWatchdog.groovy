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
	description: "This app can be used to monitor open doors/windows/fridges etc. first warning is light changing color, and next warning can be via speaker and/or push to phone",
	category: "",
	iconUrl: "",
	iconX2Url: "",
	iconX3Url: "")


preferences {
	section("Settings") {
		input "contactSensor", "capability.contactSensor", title: "Select contract sensor to monitor", multiple: false
		input "colorBulbsToControl", "capability.colorControl", title: "Select color bulbs to control", multiple: true

		input "openDoorMessage", "text", title: "Message to speak after second warning"
		input "firstWarning", "number", title: "Seconds to first warning", default: 40
		input "secondWarning", "number", title: "Seconds to second warning", default: 3*60

		// Notifications
		input "audioNotification", "capability.speechSynthesis", title: "Audio Notifier:", multiple: true
		input "textNotification", "capability.notification", title: "Text Notifier:", multiple: true

		// Select color for notification:
		input "hueSetting", "number", title: "Hue (0..100)", default: 66
		input "saturationSetting", "number", title: "Saturation (0..100)", default: 100
		input "levelSetting", "number", title: "Level (0..100)", default: 50

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
	unsubscribe()
	subscribe(contactSensor, "contact.open", DoorOpen)
	subscribe(contactSensor, "contact.closed", DoorClosed)


	// Turn off logs after 30 min
	if (enableDebug) runIn(30*60, logsOff)

	// Define Globals
	state.openTimeout = false
	state.bulbSettings = [:]
}

// App code here
def DoorOpen(evt) {
	logTrace "Door open"
	runIn(firstWarning, DoorOpenTimeout)
	runIn(secondWarning, NotifyDoorOpen)
}

def DoorOpenTimeout() {
	logTrace "DoorOpenTimeout"

	state.openTimeout = true

	colorBulbsToControl.each { bulb ->
		String bulbName = bulb.getLabel()

		logDebug bulbName

		// Store current bulb settings
		state.bulbSettings[bulbName] = ['switch': bulb.currentValue("switch"), 'color': ['hue': bulb.currentValue("hue"), 'saturation': bulb.currentValue("saturation"), 'level': bulb.currentValue("level")]]

		// Set color blue
		bulb.setColor([hue: hueSetting, saturation: saturationSetting, level: levelSetting])
	}
}

def DoorClosed(evt) {
	logTrace "Door closed"

	unschedule(DoorOpenTimeout)
	unschedule(NotifyDoorOpen)

	if (state.openTimeout == true) {
		state.openTimeout = false

		colorBulbsToControl.each { bulb ->
			String bulbName = bulb.getLabel()


			// Restore bulb settings
			if (state.bulbSettings[bulbName] != null) {
				bulb.setColor(state.bulbSettings[bulbName].color)

				// If bulb was off in the first place, or mode is Away turn it off
				if (state.bulbSettings[bulbName].switch == "off" || location.getMode() == "Away") {
					// This cannot run too early as the hub is busy sending previous Zigbee messages
					runIn(10, switchBulbOff, [data: bulbName])
				}
			}
		}
	}

	// Reset state machine, just in case
	state.openTimeout = false
}

def NotifyDoorOpen() {
	logTrace "Running second warning"

	audioNotification?.each { it ->
		it.speak(openDoorMessage)
	}

	textNotification?.each { it ->
		it.deviceNotification(openDoorMessage)
	}
}

def switchBulbOff(device) {
	logTrace "switchBulbOff"
	logDebug device

	colorBulbsToControl.each { bulb ->
		String bulbName = bulb.getLabel()

		if (bulbName == device) {
			logTrace "Switching $bulbName off"
			bulb.off()
		}
	}
}

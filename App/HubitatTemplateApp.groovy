/**
 * Copyright [yyyy] [name of copyright owner]
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
	name: "Template Application",
	namespace: "iholand",
	author: "Ivar Holand",
	description: "This can be used as a starting point for new Hubitat App development",
	category: "",
	iconUrl: "",
	iconX2Url: "",
	iconX3Url: "")


preferences {
	section("Settings") {
		input "presenceSensors", "capability.presenceSensor", title: "Select device(s)", multiple: true
		input "someMessage", "text", title: "Some message to input"
		input "someNumber", "number", title: "Some number to input"

		// Notifications
		input "audioNotification", "capability.speechSynthesis", title: "Audio Notifier:"
		input "textNotification", "capability.notification", title: "Text Notifier:"

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
	//subscribe(..., ...)

	// Turn off logs after 30 min
	if (enableDebug) runIn(30*60, logsOff)

	// Define Globals
	state.global1 = 1
	state.global2 = []
}

// App code here

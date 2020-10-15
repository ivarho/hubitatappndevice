/**
 *  Multi open door
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
definition(
	name: "Multi open door",
	namespace: "iholand",
	author: "Ivar Holand",
	description: "Multi open door",
	category: "Convenience",
	iconUrl: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png",
	iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png",
	iconX3Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png")


preferences {
	section("Multisensor monitor") {
		input "contactSensors", "capability.contactSensor", title: "Contact Sensors:", multiple: true
		input name: "devName", type: "text", title: "Name master contact:", required: true
	}
}

def installed() {
	log.debug "Installed with settings: ${settings}"

	initialize()
}

def uninstalled() {
	log.debug "Uninstalled"
	unsubscribe()

	deleteChildDevice(getChildDevices()[0].deviceNetworkId)
}

def updated() {
	log.debug "Updated with settings: ${settings}"

	schedule("0 */10 * ? * *", contactHandlerClosed)

	unsubscribe()
	initialize()
}

def initialize() {
	subscribe(contactSensors, "contact.open", contactHandlerOpen)
	subscribe(contactSensors, "contact.closed", contactHandlerClosed)

	def chdevice

	try {
		getChildDevices()[0].deviceNetworkId
		chdevice = getChildDevices()[0]
	} catch (e) {
		def ranNum = new Random().nextInt(65000) + 1
		chdevice = addChildDevice("hubitat", "Generic Component Contact/Switch",  String.format("$devName%d", ranNum), location.hubs[0].id, [name: devName, componentName: devName, componentLabel: devName])
		log.debug "Child device not found, creating a new child"
	}

	chdevice.sendEvent(name: "contact", value: "closed")
}

def contactHandlerOpen(evt) {
	log.debug "ContactSensor state changed"
	turnOnChildDevice()
}

def contactHandlerClosed(evt) {
	log.debug "ContactSensor state changed"

	if (anyOpen(contactSensors)) {
		turnOnChildDevice()
	} else {
		turnOffChildDevice()
	}
}

def anyOpen(doors) {
	def anyOpenDoors = false

	doors.each {door ->
		log.debug door.name + ": " + door.currentValue("contact")
		if (door.currentValue("contact") == "open") {
			anyOpenDoors = true
		}
	}

	return anyOpenDoors
}

def turnOnChildDevice()
{
	def childContact = getChildDevices()[0]
	childContact.sendEvent(name: "contact", value: "open")
}

def turnOffChildDevice()
{
	def childContact = getChildDevices()[0]
	childContact.sendEvent(name: "contact", value: "closed")
}

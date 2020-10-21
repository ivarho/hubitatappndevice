/**
 *  Contact Delayed Response
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
	name: "Dealyed Contact App",
	namespace: "iholand",
	author: "Ivar Holand",
	description: "Contact Delayed Response",
	category: "Safety & Security",
	iconUrl: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png",
	iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png",
	iconX3Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png")


preferences {
	section("Title") {
		input "c_sensor", "capability.contactSensor", title: "Contact Sensor:"
		input name: "devName", type: "text", title: "Name delayed contact:", required: true
		input name: "delaySeconds", type: "number", title: "Number of seconds to delay:", required: true

	}
}

def installed() {
	log.debug "Installed with settings: ${settings}"

	def ranNum = new Random().nextInt(65000) + 1
	def chdevice = addChildDevice("hubitat", "Generic Component Contact/Switch",  String.format("$devName%d", ranNum), location.hubs[0].id, [name: devName, componentName: devName, componentLabel: devName])

	chdevice.sendEvent(name: "contact", value: "closed")

	initialize()
}

def updated() {
	log.debug "Updated with settings: ${settings}"

	unsubscribe()
	initialize()
}

def initialize() {
	subscribe(c_sensor, "contact", contactHandler)
	subscribe(location, "hsmStatus", alarmHandler)
}

def alarmHandler(evt) {
	log.debug "Alarm Handler value: ${evt.value}"
	log.debug "alarm state: ${location.hsmStatus?.value}"

	if (evt.value == "disarmed") {
		unschedule(turnOnChildDevice)
		turnOffChildDevice()
	}

}

def contactHandler(evt) {
	log.debug("Contact triggered:" + evt.value)

	if (evt.value == "open" && location.hsmStatus?.value != "disarmed") {
		runIn(delaySeconds, turnOnChildDevice)
	}
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

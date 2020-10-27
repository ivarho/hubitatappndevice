/**
 *  TRV reminder app
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
	name: "TRV reminder app",
	namespace: "iholand",
	author: "Ivar Holand",
	description: "TRV reminder app",
	category: "Safety & Security",
	iconUrl: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png",
	iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png",
	iconX3Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png")


preferences {
	section("Title") {
		input "trvReader", "capability.sensor", title: "TRV leser:", required: true
		input "speechDev", "capability.speechSynthesis", title: "Speech Device: "
		input "notificationDev", "capability.notification", title: "Notification deivce(s): ", multiple: true
	}
}

def installed() {
	log.debug "Installed with settings: ${settings}"

	initialize()
}

def updated() {
	log.debug "Updated with settings: ${settings}"

	unsubscribe()
	initialize()
}

def initialize() {
	log.debug trvReader.currentValue("wastetype")

	schedule("0 0 21 ? * WED", reminderHandler)
}

def reminderHandler() {
	def wastetype = trvReader.currentValue("wastetype")

	def message = "Informasjonsmelding: husk søppelbøtte, ${wastetype} denne uken."

	speechDev?.speak(message)

	notificationDev?.each {notDev ->
		notDev.deviceNotification(message)
	}

}

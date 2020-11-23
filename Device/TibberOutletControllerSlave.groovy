/**
 *  Tibber Outlet Controller Slave
 *
 *  Copyright 2019-2020 Ivar Holand
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
	definition (name: "Tibber Outlet Controller Slave", namespace: "iholand", author: "Ivar Holand") {
		capability "Switch"
		capability "Energy Meter"

		attribute "nextChargeStartsAt", "number"

		command "setChargerStart"

		command "postpone12h"
		command "postpone24h"
		command "postpone48h"
	}

	preferences {
		input "msgBeforeTime", "text", title: "Message before time: ", defaultValue : "Charger starts at"
		input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true
	}

}

def updated() {
	off()

	if (logEnable) {
		runIn(30*60, logsOff)
	}
}

// parse events into attributes
def parse(String description) {
	if (logEnable) log.debug "Parsing '${description}'"
	// TODO: handle 'switch' attribute

}

def logsOff() {
	log.warn "debug logging disabled..."
	device.updateSetting("logEnable", [value: "false", type: "bool"])
}

// handle commands
def on() {
	if (logEnable) log.debug "Executing 'on'"

	def result = createEvent(name: "switch", value: "on")
	sendEvent(result)

	unschedule(off)
}

def off() {
	if (logEnable) log.debug "Executing 'off'"
	def result = createEvent(name: "switch", value: "off")
	sendEvent(result)

	state.postPosponeCharge = ""
	setChargerStart(state.startTime)

	unschedule(off)
}

def postpone12h() {
	on()
	if (logEnable) log.debug("Postponing 12h")

	state.postPosponeCharge = "+12h"
	setChargerStart(state.startTime)

	runIn(12*60*60, off)
}

def postpone24h() {
	on()
	if (logEnable) log.debug("Postponing 24h")

	state.postPosponeCharge = "+24h"
	setChargerStart(state.startTime)

	runIn(24*60*60, off)
}

def postpone48h() {
	on()
	if (logEnable) log.debug("Postponing 48h")

	state.postPosponeCharge = "+48h"
	setChargerStart(state.startTime)

	runIn(48*60*60, off)
}

def setChargerStart(startTime)
{
	if (logEnable) log.debug(startTime)

	state.startTime = startTime
	state.nextChargeStartsAt = msgBeforeTime + " " + startTime + ":00" + state.postPosponeCharge

	sendEvent(name: "nextChargeStartsAt", value: state.nextChargeStartsAt)
	sendEvent(name: "energy", value: startTime*100)
}

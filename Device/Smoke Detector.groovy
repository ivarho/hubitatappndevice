/**
 *  Smoke Detector
 *
 *  Copyright 2021 Ivar Holand
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
	definition (name: "Smoke Detector", namespace: "iholand", author: "Ivar Holand") {
		capability "SmokeDetector"
		capability "Sensor"

		attribute "address", "number"

		command "setDetectionLevel"
	}
}

preferences {
	input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true
}

def logsOff() {
	log.warn "debug logging disabled..."
	device.updateSetting("logEnable", [value: "false", type: "bool"])
}


def updated(settings) {
	log.debug "Updated with settings: $settings"

	if (logEnable) runIn(1800, logsOff)
}

// parse events into attributes
def parse(String description) {
	log.debug "Parsing '${description}'"
	// TODO: handle 'temperature' attribute

}

// handle commands
def setDetectionLevel(address, smoke="detected") {
	if (logEnable) log.debug "Executing 'setDetectionLevel'"

	sendEvent(name: "smoke", value: "${smoke}")
    sendEvent(name: "address", value: address)
}

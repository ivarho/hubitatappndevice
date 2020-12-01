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

metadata {
	definition(name: "Nexa 433 plug", namespace: "iholand", author: "iholand") {
		capability "Actuator"
		capability "Switch"
		capability "Sensor"

		command "group_on"
		command "group_off"
	}
}

preferences {
	section("URIs") {
		input "address", "number", title: "Address", required: false
		input "channel", "number", title: "Channel", required: false
		input "groupControl", "bool", title: "Main group should control group?", required: false
		input "NhubIP", "text", title: "Nexa Hub IP", required: false
		input "NhubPort", "number", title: "Nexa Hub PORT", required: false
		input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true
	}
}

def logsOff() {
	log.warn "debug logging disabled..."
	device.updateSetting("logEnable", [value: "false", type: "bool"])
}

def updated() {
	log.info "updated..."
	log.warn "debug logging is: ${logEnable == true}"
	if (logEnable) runIn(1800, logsOff)
}

def parse(String description) {
	if (logEnable) log.debug(description)
}

def handler(response, data) {
	if (logEnable) log.debug "Handler executed with response: ${response.getStatus()}"

	if (response.getStatus() == 200) {
		unschedule(timeOut)

		def json_data = response.getJson()

		if (json_data['status'] == 1 || json_data['status'] == 3) {
			sendEvent(name: "switch", value: "on", isStateChange: true)
		} else {
			sendEvent(name: "switch", value: "off", isStateChange: true)
		}
	}
}

def timeOut(data) {

	if (data[1] > 0) {
		log.warn "Failed to get response from device: ${data}"
		send_HTTP_get_status(data[0], data[1] - 1)
	} else {
		log.error "Failed to get response from device: ${data}"
	}
}

def send_HTTP_get_status(Integer status, Integer retry = 5)
{
	def NhubIP = "192.168.1.186"
	def NhubPORT = "80"

	def URI = ""

	if (settings.NhubIP != NULL) {
		NhubIP = settings.NhubIP
	}

	if (settings.NbubPORT != NULL) {
		NhubPORT = settings.NhubPORT
	}

	if (logEnable) log.debug "Sending on GET request to NEXA hub at: [${NhubIP}:${NhubPORT}]"


	URI = "http://$NhubIP:$NhubPORT/nexa.html?addr=$settings.address&ch=$settings.channel&status=$status"

	def params = [
            uri        : URI
    ]

    asynchttpGet(handler, params)

	Random random = new Random()

	runInMillis(random.nextInt(2000), timeOut, ["data": [status, retry]])

	if (logEnable) log.debug("Sending URI: $URI")
}

def on() {
	if (!groupControl) {
		send_HTTP_get_status(1)
	} else {
		group_on()
	}
}

def off() {
	if (!groupControl) {
		send_HTTP_get_status(0)
	} else {
		group_off()
	}
}

def group_on() {
	if (logEnable) log.debug "Running group_on"
	send_HTTP_get_status(3)
}

def group_off() {
	if (logEnable) log.debug("Running group_off")
	send_HTTP_get_status(2)
}

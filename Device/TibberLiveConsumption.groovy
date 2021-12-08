/**
 * Copyright 2021 Ivar Holand
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

import groovy.json.JsonSlurper

metadata {
	definition (name: "Tibber Live Consumption", namespace: "iholand", author: "Ivar Holand") {
		capability "Sensor"
		capability "EnergyMeter"
		capability "PowerMeter"
		capability "Switch"

		attribute "available_power", "number"
		attribute "available_power_w_unit", "String"

		command "connectSocket"
		command "closeSocket"
	}

	preferences {
		input name: "tibber_apikey", type: "password", title: "API Key", description: "Enter the Tibber API key.<p><i>This can be found on https://developer.tibber.com/explorer. Sign in and click Load Personal Token.</i></p>", required: true, displayDuringSetup: true
		input name: "tibber_homeId", type: "text", title: "homeId", description: "Enter the Tibber homeId: <p><i>This can be found on https://developer.tibber.com/explorer. Open the Real time subscription example, homeId should be on the left.</i></p>", required: true, displayDuringSetup: true
		input name: "threshold", type: "number", title: "Limit hour average to:", default: 5000

		input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true
	}
}

def parse(description) {
	if(logEnable) log.debug "parse $description"

	def jsonSlurper = new JsonSlurper()
	def object = jsonSlurper.parseText(description)

	if (object.type == "data") {

		def power = object.payload.data.liveMeasurement.power
		def accumulatedConsumptionLastHour = object.payload.data.liveMeasurement.accumulatedConsumptionLastHour
		def timestamp_str = object.payload.data.liveMeasurement.timestamp

		def minutes_into_hour = timestamp_str.split("T")[1].split(":")[1] as int
		def estimated_rem_consumption = (power/60 * (60-minutes_into_hour))/1000

		def hour_estimated_consumption = (estimated_rem_consumption + accumulatedConsumptionLastHour).toFloat()

        if (threshold == null) {
			threshold = 5000
		}

		def available_power_to_limit = (threshold-hour_estimated_consumption*1000)/((60-minutes_into_hour)/60)

		sendEvent(name:"available_power", value:available_power_to_limit.round())
		sendEvent(name:"available_power_w_unit", value:"Available:<br>${available_power_to_limit.round()} W")
		sendEvent(name:"energy", value:hour_estimated_consumption.round(3), unit:"kWh")
		sendEvent(name:"power", value:power, unit:"W")
	}
}

def webSocketStatus(String message) {
	log.debug "webSocketStatus: $message"

	if (message == "status: open") {
		sendEvent(name:"switch", value:"on")
	}

	if (message == "status: closing" || message == "failure: null") {
		sendEvent(name:"switch", value:"off")
	}
}

def connectSocket() {
	auth = '{"type":"connection_init","payload":"token='+ tibber_apikey +'"}'
	sub = '{"type": "start", "id": "1", "payload": {"query": "subscription{liveMeasurement(homeId:\\"'+ tibber_homeId +'\\"){timestamp\\r\\npower\\r\\naccumulatedConsumptionLastHour}}"}}'

	interfaces.webSocket.connect("wss://api.tibber.com/v1-beta/gql/subscriptions", pingInterval:-1, headers: ["Authorization":"{$tibber_apikey}", "Sec-WebSocket-Protocol": "graphql-ws"])
	interfaces.webSocket.sendMessage(auth)
	interfaces.webSocket.sendMessage(sub)
}

def updated(settings) {
	log.debug "updated"
	closeSocket()

	connectSocket()
}

def closeSocket() {
	log.debug "closeSocket"
	interfaces.webSocket.close()
}

def on() {
	closeSocket()
	connectSocket()
}

def off() {
	closeSocket()
}

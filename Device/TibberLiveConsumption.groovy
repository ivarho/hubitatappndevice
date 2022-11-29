/**
 * Copyright 2021-2022 Ivar Holand
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
 *
 *
 * Change log:
 * - Added hub firmware version to the user agent information, and use device id as client id
 * - Added support for the new webSocket API graphql-transport-ws
 * - Added a motion detector output as indication of over consumption
 * - Added setting for controlling run time, i.e. continuous, every 5 min etc.
 *    Auto reconnect added.
 *
 * User guide in Norwegian:
 * https://github.com/ivarho/hubitatappndevice/raw/master/Device/TibberLiveConsumption/Hvordan%20f%C3%A5%20live%20str%C3%B8mforbruk%20fra%20Tibber%20Pulse%20i%20Hubitat.pdf
 */

import groovy.json.JsonSlurper

metadata {
	definition (name: "Tibber Live Consumption", namespace: "iholand", author: "Ivar Holand") {
		capability "Sensor"
		capability "EnergyMeter"
		capability "PowerMeter"
		capability "Switch"
		// Use this to indicate over consumption = active, ok = inactive
		capability "MotionSensor"

		attribute "available_power", "number"
		attribute "available_power_w_unit", "String"

		command "connectSocket"
		command "closeSocket"
		command "querySubscriptionURL"
	}

	preferences {
		input name: "tibber_apikey", type: "password", title: "API Key", description: "Enter the Tibber API key.<p><i>This can be found on https://developer.tibber.com/explorer. Sign in and click Load Personal Token.</i></p>", required: true, displayDuringSetup: true
		input name: "tibber_homeId", type: "text", title: "homeId", description: "Enter the Tibber homeId: <p><i>This can be found on https://developer.tibber.com/explorer. Open the Real time subscription example, homeId should be on the left.</i></p>", required: true, displayDuringSetup: true
		input name: "threshold", type: "number", title: "Limit hour average to:", default: 5000
		input name: "run_config", type: "enum", title: "Configure run schedule:", options: [1:"Continuous without auto reconnect ~7000 evt/hour", 2:"Continuous with auto reconnect ~7000 evt/hour", 3: "Every 3 min ~80 evt/hour", 5: "Every 5 min  ~48 evt/hour", 10: "Every 10 min ~24 evt/hour", 15: "Every 15 min ~16 evt/hour", 20: "Every 20 min ~12 evt/hour", 30: "Every 30 min ~8 evt/hour"]

		input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true
	}
}

def parse(description) {
	if(logEnable) log.debug "parse $description"

	def jsonSlurper = new JsonSlurper()
	def object = jsonSlurper.parseText(description)

	if (object.type == "connection_ack") {
		if(logEnable) log.debug "Connection accepted"

		state.error_count = 0;

		sub = '{"type": "subscribe", "id": "'+ device.getDeviceNetworkId() +'", "payload": {"query": "subscription{liveMeasurement(homeId:\\"'+ tibber_homeId +'\\"){timestamp\\r\\npower\\r\\naccumulatedConsumptionLastHour}}"}}'

		interfaces.webSocket.sendMessage(sub)
	}

	if (object.type == "error") {
		log.error "Connection error with Tibber"

		querySubscriptionURL()

		if (state.error_count == null) {
			state.error_count = 1;
		} else {
			state.error_count = state.error_count + 1
		}

		// Safest thing to do is to unschedule
		if (state.error_count > 10) {
			unschedule()
		}
	}

	if (object.type == "next") {

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

		if (available_power_to_limit < 0) {
			sendEvent(name:"motion", value:"active")
		} else {
			sendEvent(name:"motion", value:"inactive")
		}

		sendEvent(name:"available_power", value:available_power_to_limit.round())
		sendEvent(name:"available_power_w_unit", value:"Available:<br>${available_power_to_limit.round()} W")
		sendEvent(name:"energy", value:hour_estimated_consumption.round(3), unit:"kWh")
		sendEvent(name:"power", value:power, unit:"W")

		if (run_config.toInteger() > 2) {
			closeSocket()
		}
	}
}

def webSocketStatus(String message) {
	if(logEnable) log.debug "webSocketStatus: $message"

	if (message == "status: open") {
		sendEvent(name:"switch", value:"on")
	}

	if (message == "status: closing") {
		sendEvent(name:"switch", value:"off")
	}

	if (message == "failure: null")  {
		sendEvent(name:"switch", value:"off")

		if (run_config == 2) {
			runIn(30, connectSocket)
		}
	}
}

def graphQLApiQuery(){
	return '{"query":"{ viewer { websocketSubscriptionUrl } }"}'
}

def querySubscriptionURL() {
	def params = [
		uri: "https://api.tibber.com/v1-beta/gql",
		headers: ["Content-Type": "application/json" , "Authorization": "Bearer $tibber_apikey"],
		body: graphQLApiQuery()
		]

	try {
		httpPostJson(params) { resp ->
			if (resp.status == 200) {
				if(logEnable) log.debug resp.data
				state.subscriptionURL = resp.data.data.viewer.websocketSubscriptionUrl
			}
		}
	} catch (e) {
		log.error "something went wrong: $e"
	}
}

def connectSocket() {
	auth = '{"type":"connection_init","payload":{"token":"'+ tibber_apikey +'"}}'

	if (state.subscriptionURL == null) {
		querySubscriptionURL()
	}

	interfaces.webSocket.connect(state.subscriptionURL, pingInterval:20, headers: ["Sec-WebSocket-Protocol": "graphql-transport-ws", "User-Agent":"Hubitat/${location.hub.firmwareVersionString} iholand-TibberLiveConsumption/2.0"])
	interfaces.webSocket.sendMessage(auth)

}

def endConnection() {
	completeMessage = '{"type":"complete", "id":"'+ device.getDeviceNetworkId() +'"}'

	if(logEnable) log.debug completeMessage

	interfaces.webSocket.sendMessage(completeMessage)

}

def updated(settings) {
	log.debug "updated with settings: ${settings}"

	closeSocket()

	unschedule()

	querySubscriptionURL()

	if (run_config.toInteger() > 2) {
		//Schedule run

		schedule("0 */${run_config} * ? * *", connectSocket)
		connectSocket()

	} else {
		connectSocket()
	}
}

def closeSocket() {
	if(logEnable) log.debug "closeSocket"
	endConnection()

	interfaces.webSocket.close()
}

def on() {
	closeSocket()
	connectSocket()
}

def off() {
	closeSocket()
}

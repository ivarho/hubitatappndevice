/*
 * NEXA 433MHz Plugs
 *
 * Controlled via a simple ATmega4809 web server
 *
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

def send_HTTP_get_status(Integer status)
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

	try {
		httpGet(URI) { resp ->
			if (resp.success) {
				if (status == 1) {
					sendEvent(name: "switch", value: "on", isStateChange: true)
				} else if (status == 0) {
					sendEvent(name: "switch", value: "off", isStateChange: true)
				} else if (status == 2) {
					sendEvent(name: "switch", value: "off", isStateChange: true)
					// Plus child devices
				} else if (status == 3) {
					sendEvent(name: "switch", value: "on", isStateChange: true)
					// Plus child devices
				}

			}
			if (logEnable)
				if (resp.data) log.debug "${resp.data}"
		}
	} catch (Exception e) {
		log.warn "Call to on failed: ${e.message}"
	}

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

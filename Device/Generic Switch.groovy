metadata {
	definition (name: "General Switch", namespace: "iholand", author: "Ivar Holand") {
		capability "Actuator"
		capability "Switch"
		capability "Sensor"

		command "setStatus"
	}
}

def parse(String description) {
}

def on() {
	sendEvent(name: "switch", value: "on")
}

def off() {
	sendEvent(name: "switch", value: "off")
}

def setStatus(status)
{
	if (status == "0") {
		off()
	} else if (status == "1") {
		on()
	} else if (status == "2") {
		on()
	} else if (status == "3") {
		off()
	}
}

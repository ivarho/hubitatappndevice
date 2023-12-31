/**
 * IMPORT URL: https://raw.githubusercontent.com/ivarho/hubitatappndevice/master/Device/tuyaDevices/tuyaGenericBulbRGBW.groovy
 *
 * Copyright 2023 Ivar Holand
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
	definition(name: "tuya Generic RGBW Bulb",
			namespace: "iholand",
			author: "iholand",
			importUrl: "https://raw.githubusercontent.com/ivarho/hubitatappndevice/master/Device/tuyaDevices/tuyaGenericBulbRGBW.groovy") {
		capability "Actuator"
		capability "Bulb"
		capability "ColorTemperature"
		capability "ColorControl"
		capability "ColorMode"
		capability "Refresh"
		capability "LevelPreset"
		capability "SwitchLevel"
		capability "Switch"
		capability "LightEffects"

		command "status"
		command "SendCustomDataToDevice", [[name:"endpoint*", type:"NUMBER", description:"To which endpint(dps) do you want the data to be sent"], [name:"data*", type:"STRING", description:"the data to be sent, treated as string, but true and false is converted"]]
		command "DriverSelfTest"

		attribute "rawMessage", "String"
	}
}

preferences {
	section("URIs") {
		input "ipaddress", "text", title: "Device IP:", required: false
		input "devId", "text", title: "Device ID:", required: false
		input "localKey", "text", title: "Device local key:", required: false
		input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true
		input "tuyaProtVersion", "enum", title: "Select tuya protocol version: ", required: true, options: [31: "3.1", 33 : "3.3", 34: "3.4 (experimental)"]
		input name: "poll_interval", type: "enum", title: "Configure poll interval:", options: [0: "No polling", 5: "Every 5 second", 10: "Every 10 second", 15: "Every 15 second", 20: "Every 20 second", 30: "Every 30 second", 60: "Every 1 min", 120: "Every 2 min", 180: "Every 3 min"]
		input name: "color_mode", type: "enum", title: "Configure bulb color mode:", options: ["hsv": "HSV (native Hubitat)", "hsl": "HSL"]
	}
}

def logsOff() {
	log.warn "debug logging disabled..."
	device.updateSetting("logEnable", [value: "false", type: "bool"])
}

def installed() {
	updated()
}

def updated() {
	log.info "updated..."
	log.warn "debug logging is: ${logEnable == true}"
	state.clear()
	if (logEnable) runIn(1800, logsOff)

	state.payload = [:]
	state.haveSession = false
	state.session_step = "step1"
	state.realLocalKey = localKey.replaceAll('&lt;', '<').getBytes("UTF-8")
	state.msgseq = 1

	// Configure pull interval, only the parent pull for status
	if (poll_interval.toInteger() != null) {
		//Schedule run

		if (poll_interval.toInteger() == 0) {
			unschedule(status)
		} else if (poll_interval.toInteger() < 60) {
			schedule("*/${poll_interval} * * ? * *", status)
		} else if (poll_interval.toInteger() < 60*60) {
			minutes = poll_interval.toInteger()/60
			if(logEnable) log.debug "Setting schedule to pull every ${minutes} minutes"
			schedule("0 */${minutes} * ? * *", status)
		}

		status()

	} else {
		status()
	}

	sendEvent(name: "switch", value: "off")
}


/*colortemperature required (NUMBER) - Color temperature in degrees Kelvin
level optional (NUMBER) - level to set
transitionTime optional (NUMBER) - transition time to use in seconds*/
def setColorTemperature(colortemperature, level=null, transitionTime=null) {

	def setMap = [:]

	// 0 - 1000 | 2700 - 6500
	// Ax + B = bulb_st_setting
	// A = 2700 | Ax + B = 0
	// A = 6500 | Ax + B = 1000

	setMap[21] = "white"

	Integer bulb_ct_setting = (colortemperature/3.8) - (2700/3.8)

	if (bulb_ct_setting < 0) bulb_ct_setting = 0
	if (bulb_ct_setting > 1000) bulb_ct_setting = 1000

	setMap[23] = bulb_ct_setting

	if (level != null) {
		if (level > 100) level = 100
		if (level < 0) level = 0

		setMap[22] = level*10
	}

	/* Not implemented, bulb does not support this
	if (transitionTime != null) {
		setMap[26] = transitionTime
	}*/

	//send(generate_payload("set", setMap))

	state.payload += setMap

	runInMillis(250, 'sendSetMessage')
}

//colormap required (COLOR_MAP) - Color map settings [hue*:(0 to 100), saturation*:(0 to 100), level:(0 to 100)]
def setColor(colormap) {
	def setMap = [:]

	setMap[21] = "colour"

	if (logEnable) log.debug(colormap)

	// Bug in Hubitat: documentation claims to give you a HSL color value,
	// however, the value corresponds to a HSV color value

	// Next bug, tuya documentation claims that the bulb wants a HSV color value
	// https://developer.tuya.com/en/docs/iot/generic-light-bulb-template?id=Kag3g03a9vy81
	// however, correct color is only achived by using HSL color value. This could also
	// be a Ledvance issue. So other bulbs, might or might not need conversion to HSV
	if (color_mode == "hsl") {
		colormap = hsvToHsl(colormap.hue, colormap.saturation, colormap.level)
	} else if (color_mode == "hsv") {
		colormap = colormap
	}

	Integer bHue = colormap.hue * 3.6
	Integer bSat = colormap.saturation*10
	Integer bValue = colormap.level*10


	def setting = sprintf("%04x%04x%04x", bHue, bSat, bValue)

	setMap[24] = setting

	//send(generate_payload("set", setMap))

	state.payload += setMap
	runInMillis(250, 'sendSetMessage')

}

//hue required (NUMBER) - Color Hue (0 to 100)
def setHue(hue) {
	// Not implemented
}

//saturation required (NUMBER) - Color Saturation (0 to 100)
def setSaturation(saturation) {
	// Not implemented
}

def presetLevel(level) {
	def setMap = [:]

	if (level != null) {
		if (level > 100) level = 100
		if (level <= 0) level = 1

		setMap[22] = level*10

		//send(generate_payload("set", setMap))
		state.payload += setMap
		runInMillis(250, 'sendSetMessage')
	} else {
		off()
	}
}

def setLevel(level, duration=null) {
	presetLevel(level)
}

def setEffect(effectnumber) {
	state.effectnumber = effectnumber.intValue()

	// Thanks to neerav.modi on the Hubitat forum for suggesting this feature and the scene information
	lightEffects = [
	0 : "000e0d0000000000000000c803e8", // Good night
	1 : "010e0d0000000000000003e803e8", // Reading
	2 : "020e0d0000000000000003e803e8", // Working
	3 : "030e0d0000000000000001f403e8", // Leisure
	4 : "04464602007803e803e800000000464602007803e8000a00000000", // Grassland
	5 : "06464601000003e803e800000000464601007803e803e80000000046460100f003e803e800000000", // Dazzling (flash between red, green, blue)
	6 : "c9646401000000000000022503e8646401016003e803e800000000"] // Flashing between some shade of white and red

	def setMap = [:]

	setMap[21] = "scene"

	setMap[25] = lightEffects[effectnumber.intValue()]

	state.payload += setMap
	runInMillis(250, 'sendSetMessage')
}

def setNextEffect() {
	def temp = state.effectnumber

	if (temp == null) {
		temp = 0
	}

	temp = temp + 1

	if (temp > 6) {
		temp = 0
	}

	setEffect(temp)
}

def setPreviousEffect() {
	def temp = state.effectnumber

	if (temp == null) {
		temp = 0
	}

	temp = temp - 1

	if (temp < 0) {
		temp = 6
	}

	setEffect(temp)
}

def refresh() {
	status()
}

def on() {
	//send(generate_payload("set", [20:true]))

	state.payload[20] = true
	runInMillis(250, 'sendSetMessage')
}

def off() {
	//send(generate_payload("set", [20:false]))
	state.payload[20] = false
	runInMillis(250, 'sendSetMessage')
}

def SendCustomDataToDevice(endpoint, data) {

	// A fix for a common use-case where true and false is sent
	// these values must be converted to boolean values to work
	if (data == "true") {
		data = true
	} else if (data == "false") {
		data = false
	}

	send("set", ["${endpoint}":data])
}

def sendSetMessage() {

	send("set", state.payload)
	state.payload = [:]
}

def status() {
	send("status", [:])
}

def device_specific_parser(Object status_object) {

	// Switch status (on / off)
	if (status_object.dps.containsKey("20")) {
		if (status_object.dps["20"] == true) {
			sendEvent(name: "switch", value : "on")
		} else {
			sendEvent(name: "switch", value : "off")
		}
	}

	// Bulb Mode
	if (status_object.dps.containsKey("21")) {
		if (status_object.dps["21"] == "white") {
			sendEvent(name: "colorMode", value : "CT")
		} else if (status_object.dps["21"] == "colour") {
			sendEvent(name: "colorMode", value : "RGB")
		} else {
			sendEvent(name: "colorMode", value : "EFFECTS")
		}
	}

	// Brightness
	if (status_object.dps.containsKey("22")) {
		sendEvent(name: "presetLevel", value : status_object.dps["22"]/10)
		sendEvent(name: "level", value : status_object.dps["22"]/10)
	}

	// Color temperature
	if (status_object.dps.containsKey("23")) {

		Integer colortemperature = (status_object.dps["23"] + (2700/3.8))*3.8

		sendEvent(name: "colorTemperature", value : colortemperature)
	}

	// Color information
	if (status_object.dps.containsKey("24")) {
		// Hue
		def hueStr = status_object.dps["24"].substring(0,4)
		Float hue_fl = Integer.parseInt(hueStr, 16)/3.6
		Integer hue = hue_fl.round(0)

		// Saturation
		def satStr = status_object.dps["24"].substring(5,8)
		def sat = Integer.parseInt(satStr, 16)/10

		// Level
		def levelStr = status_object.dps["24"].substring(9,12)
		def level = Integer.parseInt(levelStr, 16)/10

		// Bug in Hubitat: Hubitat stores colors as HSV, however documents claim HSL. The tuya
		// Ledvance bulb I have store color information in HSL, hence need to convert.
		def colormap = hslToHsv(hue, sat, level)

		sendEvent(name: "hue", value : colormap.hue)
		sendEvent(name: "saturation", value : colormap.saturation)
		sendEvent(name: "level", value : colormap.value)
	}
}

def hslToHsv(hue, saturation, level)
{
	if (logEnable) log.debug ("HSL to HSV")
	if (logEnable) log.debug ("${hue}, ${saturation}, ${level}")

	// hue = hue
	level = (level/100) * 2

	saturation = (saturation/100) * ((level <= 1) ? level : 2 - level)

	//ss *= (ll <= 100) ? ll : 2 - ll;

	def value = (level + saturation) / 2

	//*v = (ll + ss) / 2;

	def sat = (2 * saturation) / (level + saturation)
	//*s = (2 * ss) / (ll + ss);

	def retMap = ["hue": hue, "saturation": (sat*100).intValue(), "value": (value*100).intValue()]
	if (logEnable) log.debug retMap

	return retMap
}

def hsvToHsl(hue, saturation, value)
{
	if (logEnable) log.debug ("HSV to HSL")
	if (logEnable) log.debug ("${hue}, ${saturation}, ${value}")
	//*hh = h;

	def level = (2 - (saturation/100)) * (value/100)
	//*ll = (2 - s) * v;

	def sat = (saturation/100) * (value/100)
	//*ss = s * v;

	if (level != 0) {
		sat = sat / ((level <= 1) ? level : 2 - level)
		//*ss /= (*ll < = 1) ? (*ll) : 2 - (*ll);
	}

	level = level / 2
	//*ll /= 2;

	def retMap = ["hue": hue, "saturation": (sat*100).intValue(), "level": (level*100).intValue()]
	if (logEnable) log.debug retMap

	return retMap
}

// ************************************ TUYA PROTOCOL FUNCTIONS *************************************

import hubitat.device.HubAction
import hubitat.device.Protocol

def socketStatus(String socketMessage) {
	log.warn "Socket status message received: " + socketMessage

	if (socketMessage == "send error: Broken pipe (Write failed)") {
		socket_close()
	}

	if (socketMessage.contains('disconnect')) {
		socket_close()
	}
}

def socket_connect() {

	if (logEnable) log.debug "Socket connect: $settings.ipaddress at port: 6668"

	try {
		//port 6668
		interfaces.rawSocket.connect(settings.ipaddress, 6668, byteInterface: true, readDelay: 150)
	} catch (e) {
		log.error "Error $e"
	}
}

def socket_write(byte[] message) {
	String msg = hubitat.helper.HexUtils.byteArrayToHexString(message)

	if (logEnable) log.debug "Socket: write - " + settings.ipaddress + ":" + 6668 + " msg: " + msg

	try {
		interfaces.rawSocket.sendMessage(msg)
	} catch (e) {
		log.error "Error sending data to device: $e"
	}
}

def socket_close() {
	if(logEnable) log.debug "Socket: close"

	state.session_step = "step1"
	state.haveSession = false
	state.sessionKey = null

	try {
		interfaces.rawSocket.close()
	} catch (e) {
		log.error "Could not close socket: $e"
	}
}

def send(String command, Map message=null) {

	boolean sessionState = state.haveSession

	if (sessionState == false) {
		if(logEnable) log.debug "No session, creating new session"
		sessionState = get_session(settings.tuyaProtVersion)
	}

	if (sessionState) {
		socket_write(generate_payload(command, message))
	} else {
		state.command = command
		state.message = message
	}

	state.haveSession = sessionState

	runIn(30, socketStatus, [data: "disconnect: pipe closed"])
}

def sendAll() {
	send(state.command, state.message)
}

def DriverSelfTestReport(testName, generated, expected) {
	boolean retValue = false

	log.debug "Generated " + hubitat.helper.HexUtils.byteArrayToHexString(generatedTestVector)
	log.debug "Expected " + expected

	if (hubitat.helper.HexUtils.byteArrayToHexString(generatedTestVector) == expected) {
		log.info "$testName: Test passed"
		sendEvent(name: "DriverSelfTest_$testName", value: "OK")
		retValue = true
	} else {
		log.error "$testName: Test failed! The generated message does not match the expected output"
		sendEvent(name: "DriverSelfTest_$testName", value: "FAIL")
	}

	return retValue
}

def DriverSelfTest() {
	log.info "********** Starting driver self test *******************"

	state.clear()
	// Need to make sure to have this variable
	state.payload = [:]

	// Testing 3.1 set message
	expected = "000055AA0000000000000007000000B3332E313365666533353337353164353333323070306A6A4A75744C704839416F324B566F76424E55492B4A78527649334E5833305039794D594A6E33703842704B456A737767354C332B7849343638314B5277434F484C366B374B3543375A362F58766D6A7665714446736F714E31792B31584A53707542766D5A4337567371644944336A386A393354387944526154664A45486150516E784C394844625948754A63634A636E33773D3D1A3578640000AA55"
	generatedTestVector = generate_payload("set", ["20": true], "1702671803", localKey="7ae83ffe1980sa3c".getBytes("UTF-8"), devid="bfd733c97d1bfc88b3sysa", tuyaVersion="31")
	DriverSelfTestReport("SetMessageV3_1", generatedTestVector, expected)

	// Testing 3.1 status message
	expected = "000055AA000000000000000A0000007A7B2267774964223A2262666437333363393764316266633838623373797361222C226465764964223A2262666437333363393764316266633838623373797361222C22756964223A2262666437333363393764316266633838623373797361222C2274223A2231373032363731383033227DCA1E0CC60000AA55"
	generatedTestVector = generate_payload("status", data=null, "1702671803", localKey="7ae83ffe1980sa3c".getBytes("UTF-8"), devid="bfd733c97d1bfc88b3sysa", tuyaVersion="31")
	DriverSelfTestReport("StatusMessageV3_1", generatedTestVector, expected)

	// Testing 3.3 set message
	expected = "000055AA000000000000000700000087332E33000000000000000000000000A748E326EB4BA47F40A36295A2F04D508F89C51BC8DCD5F7D0FF72318267DE9F01A4A123B308392F7FB1238EBCD4A47008E1CBEA4ECAE42ED9EBF5EF9A3BDEA8316CA2A375CBED57252A6E06F9990BB56CA9D203DE3F23F774FCC8345A4DF2441DA3D09F12FD1C36D81EE25C709727DF2E5CF2B30000AA55"
	generatedTestVector = generate_payload("set", ["20": true], "1702671803", localKey="7ae83ffe1980sa3c".getBytes("UTF-8"), devid="bfd733c97d1bfc88b3sysa", tuyaVersion="33")
	DriverSelfTestReport("SetMessageV3_3", generatedTestVector, expected)

	// Testing 3.3 status message
	expected = "000055AA000000000000000A00000088D0436FF6B453B07DC2CC8084484A8E3E08E1CBEA4ECAE42ED9EBF5EF9A3BDEA834A1D6E20760F13A0CF9DE1523730E598F89C51BC8DCD5F7D0FF72318267DE9F01A4A123B308392F7FB1238EBCD4A47008E1CBEA4ECAE42ED9EBF5EF9A3BDEA8316CA2A375CBED57252A6E06F9990BB543FF054E84050A495D427D28A8C0F29F0104C4D70000AA55"
	generatedTestVector = generate_payload("status", data=null, "1702671803", localKey="7ae83ffe1980sa3c".getBytes("UTF-8"), devid="bfd733c97d1bfc88b3sysa", tuyaVersion="33")
	DriverSelfTestReport("StatusMessageV3_3", generatedTestVector, expected)


	// Testing 3.4 set message
	expected = "000055AA000000000000000D000000749AC0971A69B046C19DDFEAB6800CBB66A8FC70BDD2FF855511A3A2CBF2955BFC806C9FBFFA10ED709EC2BA4D8EC24609E50317C707468F02A110E429BA321FAA3862640A83699215E1313BA653C6DA0E5F01AADD72E172D7705B0AF82BFCD5E54A92562659A18235AEF0DDB1453BB7070000AA55"
	generatedTestVector = generate_payload("set", ["20": true], "1702671803", localKey="7ae83ffe1980sa3c".getBytes("UTF-8"), devid="bfd733c97d1bfc88b3sysa", tuyaVersion="34")
	DriverSelfTestReport("SetMessageV3_4", generatedTestVector, expected)

	// Testing 3.4 status message
	expected = "000055AA000000000000001000000034A78158A05A786D32FEC14903A94445B47BEA54632DA130BAB31B719A8C21AB419104665404C82C85BDB55DCA068791F60000AA55"
	generatedTestVector = generate_payload("status", data=null, "1702671803", localKey="7ae83ffe1980sa3c".getBytes("UTF-8"), devid="bfd733c97d1bfc88b3sysa", tuyaVersion="34")
	DriverSelfTestReport("StatusMessageV3_4", generatedTestVector, expected)
}

def parse(String description, byte[] decryptKey=state.realLocalKey) {
	if (logEnable) log.debug "Receiving message from device"
	if (logEnable) log.debug(description)

	byte[] msg_byte = hubitat.helper.HexUtils.hexStringToByteArray(description)

	String status = new String(msg_byte, "UTF-8")

	String protocol_version = ""

	status = status[20..-1]

	def cmd = 0

	if (logEnable) log.debug "Raw incoming data: " + status

	if (!status.startsWith("{")) {
		// Encrypted message incoming, decrypt first

		if (logEnable) log.debug "Encrypted message detected"
		if (logEnable) log.debug "Bytes incoming: " + msg_byte.size()

		def message_start = 0

		// Find message type to determine start of message
		def message_type = msg_byte[11].toInteger()
		cmd = message_type

		if (logEnable) log.debug ("Message type: ${message_type}")

		def reduce_end_by = 0

		if (message_type == 4) {
			// Incoming session key negotiation
			message_start = 20
			protocol_version = "3.4"
			reduce_end_by = 28
			unschedule(get_session_timeout)
		} else if (message_type == 7) {
			if (msg_byte.size() > 51) {
				// Incoming control message
				// Find protocol version
				byte[] ver_bytes = [msg_byte[48], msg_byte[49], msg_byte[50]]
				protocol_version = new String(ver_bytes)

				if (protocol_version == "3.1") {
					message_start = 67
				} else if (protocol_version == "3.3" || protocol_version == "3.4") {
					message_start = 63
				}
			} else {
				// Assume protocol 3.3
				protocol_version == "3.3"
			}
		} else if (message_type == 8 && msg_byte.size() > 23) {
			// Incoming status message
			// Find protocol version
			if (settings.tuyaProtVersion != "34") {
				byte[] ver_bytes = [msg_byte[20], msg_byte[21], msg_byte[22]]
				protocol_version = new String(ver_bytes)
			} else if (settings.tuyaProtVersion == "34") {
				// Protocol 3.4 does not include the protocol in clear text anymore, hence need to use driver settings
				protocol_version = "3.4"
			}

			if (logEnable) log.debug("Protocol version: " + protocol_version)

			if (protocol_version == "3.1") {
				message_start = 67
				log.error("Not supported! Please upgrade device firmware to 3.3")
			} else if (protocol_version == "3.3") {
				message_start = 35
			} else if (protocol_version == "3.4") {
				message_start = 20
				reduce_end_by = 28
			} else {
				log.error("Device firmware version not supported, protocol verison" + protocol_version)
			}

		} else if (message_type == 10) {
			// Incoming status message
			message_start = 20

			// Status messages do not contain version information, however v 3.3
			// protocol encrypts status messages, v 3.1 does not
			protocol_version = "3.3"
		} else if (message_type == 16) {
			// Incoming status message, new type in 3.4
			message_start = 20
			reduce_end_by = 28
			protocol_version = "3.4"
		} else if (message_type == 13 && msg_byte.size() > 56) {
			// Incoming encrypted frame, new type in 3.4
			// Incoming command result, new type in 3.4
			message_start = 76
			reduce_end_by = 28
			protocol_version = "3.4"
		}

		// Find end of message by looking for 0xAA55
		def end_of_message = 0
		for (u = message_start; u < msg_byte.size()-1; u++) {
			if (msg_byte[u] == (byte)0xAA && msg_byte[u+1] == (byte)0x55) {
				//msg end found
				if (logEnable) log.debug "End of message: ${u-message_start-6}"
				end_of_message = u-message_start-6
				break
			}
		}

		// Re-assemble the bytes for decoding
		ByteArrayOutputStream output = new ByteArrayOutputStream()
		for (i = message_start; i < end_of_message+message_start-reduce_end_by; i++) {
			output.write(msg_byte[i])
		}

		byte[] payload = output.toByteArray()

		if (logEnable) log.debug "Assembled payload for decrypt: "+ hubitat.helper.HexUtils.byteArrayToHexString(payload)

		def dec_status = ""

		if (protocol_version == "3.1") {
			dec_status = decrypt_bytes(payload, settings.localKey, true)
		} else if (protocol_version == "3.3") {
			if (logEnable) "Using local key for decrypt: $settings.localKey"
			dec_status = decrypt_bytes(payload, settings.localKey, false)
		} else if (protocol_version == "3.4") {
			if (state.sessionKey != null) {
				if (logEnable) "Using session key for decrypt: $state.sessionKey"
				dec_status = decrypt_bytes(payload, state.sessionKey, false)
			} else {
				if (logEnable) "Using local key for decrypt: $settings.localKey"
				dec_status = decrypt_bytes(payload, settings.localKey, false)

			}
		}

		if (logEnable) log.debug "Decryted message: ${dec_status}"

		status = dec_status
	}

	if (logEnable) log.debug "Message type: $cmd"

	if (cmd != 4 && (status != null && status != "")) {
		def jsonSlurper = new groovy.json.JsonSlurper()
		def status_object = [:]

		if (settings.tuyaProtVersion == "34" && (cmd == 13 || cmd == 8)) {
			status = status.substring(15)
			log.info "This is the status: $status"
			status_object = jsonSlurper.parseText(status)
			status_object = status_object.data
		} else {
			status_object = jsonSlurper.parseText(status)
		}

		device_specific_parser(status_object)
		sendEvent(name: "rawMessage", value: status_object.dps)

		//socket_close()

	} else if (cmd == 4) {
		// Generate last step
		remote_nonce = status[0..15]
		if (logEnable) log.debug "Remote nonce: $remote_nonce"

		byte[] b_remote_nonce = remote_nonce.getBytes()

		Mac sha256_hmac = Mac.getInstance("HmacSHA256")
		SecretKeySpec key = new SecretKeySpec(decryptKey as byte[], "HmacSHA256")

		sha256_hmac.init(key)
		sha256_hmac.update(b_remote_nonce, 0, b_remote_nonce.size())
		byte[] digest = sha256_hmac.doFinal()

		log.debug "step3 payload: " + hubitat.helper.HexUtils.byteArrayToHexString(digest)

		byte[] msg = pack_and_send_payload_v34(digest, 5, 2)

		log.debug "message to send: " + hubitat.helper.HexUtils.byteArrayToHexString(msg)

		state.session_step = "step3"

		socket_write(msg)

		byte[] calKey = [0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0]

		// Do final session key calculation
		int i = 0
		for (byte b : '0123456789abcdef'.getBytes()) {
			calKey[i] = b ^ b_remote_nonce[i]
			i++
		}

		if (logEnable) log.debug "XOR'd keys: " + hubitat.helper.HexUtils.byteArrayToHexString(calKey)

		sessKeyHEXString = encrypt(calKey, localKey.replaceAll('&lt;', '<').getBytes("UTF-8"), false)

		log.debug sessKeyHEXString

		byte[] sessKeyByteArray = hubitat.helper.HexUtils.hexStringToByteArray(sessKeyHEXString[0..31])

		//String sessionKey = new String(sessKeyByteArray, "UTF-8")
		state.sessionKey = sessKeyByteArray

		if (logEnable) log.debug "Session key: " + hubitat.helper.HexUtils.byteArrayToHexString(state.sessionKey as byte[])
		if (logEnable) log.debug "Session key (HEX): " + state.sessionKey

		state.session_step = "final"

		if (logEnable) log.debug "Done negotiating session key, send actual message"

		state.haveSession = true

		// Time to send actual message
		runInMillis(100, sendAll)

		return sessKeyHEXString
	} else {
		// Message did not contain data, bulb received unknown command?
		log.error "Device did not understand command"
	}
}

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec;

def generate_payload(command, data=null, timestamp=null, byte[] localkey=state.realLocalKey, devid=settings.devId, tuyaVersion=settings.tuyaProtVersion) {

	switch (tuyaVersion) {
		case "31":
		case "33":
			payloadFormat = "v3.1_v3.3"
			break
		case "34":
			payloadFormat = "v3.4"
			break
	}

	if (state.sessionKey != null) {
		localkey = state.sessionKey
	}

	if (logEnable) log.debug "Using key: " + new String(localkey as byte[], "UTF-8")
	if (logEnable) log.debug "Using key: " + hubitat.helper.HexUtils.byteArrayToHexString(localkey as byte[])

	json_data = payload()[payloadFormat][command]["command"]

	if (json_data.containsKey("gwId")) {
		json_data["gwId"] = devid
	}
	if (json_data.containsKey("devId")) {
		json_data["devId"] = devid
	}
	if (json_data.containsKey("uid")) {
		json_data["uid"] = devid
	}
	if (json_data.containsKey("t")) {

		if (timestamp == null) {
			Date now = new Date()
			json_data["t"] = (now.getTime()/1000).toInteger().toString()
		} else {
			json_data["t"] = timestamp
		}
	}

	if (data != null && data != [:]) {
		if (json_data.containsKey("data")) {
			json_data["data"] = ["dps" : data]
		} else {
			json_data["dps"] = data
		}
	}

	// Clean up json payload for tuya
	def json = new groovy.json.JsonBuilder(json_data)
	json_payload = groovy.json.JsonOutput.toJson(json.toString())
	json_payload = json_payload.replaceAll("\\\\", "")
	json_payload = json_payload.replaceFirst("\"", "")
	json_payload = json_payload[0..-2]

	if (logEnable) log.debug "payload before=" + json_payload

	// Contruct payload, sometimes encrypted, sometimes clear text, and a mix. Depending on the protocol version
	ByteArrayOutputStream contructed_payload = new ByteArrayOutputStream()

	if (tuyaVersion == "31") {
		if (command != "status") {
			encrypted_payload = encrypt(json_payload, localkey)

			if (logEnable) log.debug "Encrypted payload: " + hubitat.helper.HexUtils.byteArrayToHexString(encrypted_payload.getBytes())
			preMd5String = "data=" + encrypted_payload + "||lpv=" + "3.1" + "||" + new String(localkey, "UTF-8")
			if (logEnable) log.debug "preMd5String" + preMd5String
			hexdigest = generateMD5(preMd5String)
			hexdig = new String(hexdigest[8..-9].getBytes("UTF-8"), "ISO-8859-1")
			json_payload = "3.1" + hexdig + encrypted_payload
		}
		contructed_payload.write(json_payload.getBytes())

	} else if (tuyaVersion == "33") {
		encrypted_payload = encrypt(json_payload, localkey as byte[], false)

		if (logEnable) log.debug encrypted_payload

		if (command != "status") {
			contructed_payload.write("3.3\0\0\0\0\0\0\0\0\0\0\0\0".getBytes())
			contructed_payload.write(hubitat.helper.HexUtils.hexStringToByteArray(encrypted_payload))
		} else {
			contructed_payload.write(hubitat.helper.HexUtils.hexStringToByteArray(encrypted_payload))
		}

	} else if (tuyaVersion == "34") {
		if (command != "status") {
			json_payload = "3.4\0\0\0\0\0\0\0\0\0\0\0\0" + json_payload
		}
		encrypted_payload = encrypt(json_payload, localkey as byte[], false)
		contructed_payload.write(hubitat.helper.HexUtils.hexStringToByteArray(encrypted_payload))
	}

	if (logEnable) log.debug "payload after=" + json_payload

	byte[] final_payload = contructed_payload.toByteArray()

	payload_len = contructed_payload.size() + hubitat.helper.HexUtils.hexStringToByteArray(payload()[payloadFormat]["suffix"]).size()

	if (tuyaVersion == "31" || tuyaVersion == "33") {
		payload_len = payload_len + 4 // for CRC32 storage
	} else if (tuyaVersion == "34") {
		// SHA252 is used as data integrity check not CRC32, i.e. need to add 256 bits = 32 bytes to the length
		payload_len = payload_len + 32 // for HMAC (SHA-256) storage
	}

	if (logEnable) log.debug payload_len

	//log.info hubitat.helper.HexUtils.byteArrayToHexString(pack_and_send_payload_v34(json_payload, 1, 3))

	// Start constructing the final message
	output = new ByteArrayOutputStream()
	output.write(hubitat.helper.HexUtils.hexStringToByteArray(payload()[payloadFormat]["prefix_nr"]))
	output.write(state.msgseq >> 8)
	output.write(state.msgseq)
	state.msgseq = state.msgseq + 1
	output.write(hubitat.helper.HexUtils.hexStringToByteArray("000000"))
	output.write(hubitat.helper.HexUtils.hexStringToByteArray(payload()[payloadFormat][command]["hexByte"]))
	output.write(hubitat.helper.HexUtils.hexStringToByteArray("000000"))
	output.write(payload_len)
	output.write(final_payload)

	byte[] buf = output.toByteArray()

	if (tuyaVersion == "34") {
		if (logEnable) log.info "Using HMAC (SHA-256) as checksum"

		Mac sha256_hmac = Mac.getInstance("HmacSHA256")
		SecretKeySpec key = new SecretKeySpec(localkey as byte[], "HmacSHA256")

		sha256_hmac.init(key)
		sha256_hmac.update(buf, 0, buf.size())
		byte[] digest = sha256_hmac.doFinal()

		if (logEnable) log.debug("message HMAC SHA256: " + hubitat.helper.HexUtils.byteArrayToHexString(digest))

		output.write(digest)
	} else {
		if (logEnable) log.info "Using CRC32 as checksum"

		crc32 = CRC32b(buf, buf.size()) & 0xffffffff
		if (logEnable) log.debug buf.size()

		hex_crc = Long.toHexString(crc32)

		if (logEnable) log.debug "HEX crc: $hex_crc : " + hex_crc.size()/2

		// Pad the CRC in case highest byte is 0
		if (hex_crc.size() < 7) {
			hex_crc = "00" + hex_crc
		}
		output.write(hubitat.helper.HexUtils.hexStringToByteArray(hex_crc))
	}

	output.write(hubitat.helper.HexUtils.hexStringToByteArray(payload()[payloadFormat]["suffix"]))

	return output.toByteArray()
}

def get_session(tuyaVersion) {

	if (tuyaVersion.toInteger() <= 33) {
		// Don't need to get session, just send message
		socket_connect()
		return true
	}

	current_session_state = state.session_step

	if (current_session_state == null) {
		current_session_state = "step1"
	}

	switch (current_session_state) {
		case "step1":
			socket_connect()
			state.session_step = "step2"
			state.session_step = "step2"
			socket_write(negotiate_session_key_step1())
			runInMillis(750, get_session_timeout)
			break
		case "final":
			// We have the session, lets send the data
			return true
	}

	return false
}

def get_session_timeout() {
	log.error "Timout in getting session at $state.session_step, no answer from device"

	if (state.session_step == "step2") {
		state.session_step = "step1"
	}

	if (state.session_step == "step3") {
		state.session_step = "step1"
	}
}

byte[] negotiate_session_key_step1() {
	payloadFormat = "v3.4"

	if (logEnable) log.debug("*** Starting session key negotiation ***")
	local_nonce = '0123456789abcdef'
	//remote_nonce = ''
	//local_key = localkey.replaceAll('&lt;', '<')

	cmd = 3
	payload = local_nonce

	encrypted_payload = encrypt(payload, localKey.replaceAll('&lt;', '<').getBytes("UTF-8"), false)

	if (logEnable) log.debug("payload encrypted: " + encrypted_payload)

	encrypted_payload = hubitat.helper.HexUtils.hexStringToByteArray(encrypted_payload)

	def packed_message = new ByteArrayOutputStream()

	packed_message.write(hubitat.helper.HexUtils.hexStringToByteArray(payload()[payloadFormat]["prefix_nr"]))
	packed_message.write(state.msgseq >> 8)
	packed_message.write(state.msgseq)
	state.msgseq = state.msgseq + 1
	packed_message.write(hubitat.helper.HexUtils.hexStringToByteArray("000000"))
	packed_message.write(hubitat.helper.HexUtils.hexStringToByteArray(payload()[payloadFormat]["neg1"]["hexByte"]))
	packed_message.write(hubitat.helper.HexUtils.hexStringToByteArray("000000"))
	packed_message.write(encrypted_payload.size() + 32 + hubitat.helper.HexUtils.hexStringToByteArray(payload()[payloadFormat]["suffix"]).size())
	packed_message.write(encrypted_payload)

	if (logEnable) log.debug hubitat.helper.HexUtils.byteArrayToHexString(packed_message.toByteArray())

	Mac sha256_hmac = Mac.getInstance("HmacSHA256")
	secret = localKey.replaceAll('&lt;', '<')
	SecretKeySpec key = new SecretKeySpec(secret.getBytes("UTF-8"), "HmacSHA256")

	sha256_hmac.init(key)
	sha256_hmac.update(packed_message.toByteArray(), 0, packed_message.size())
	byte[] digest = sha256_hmac.doFinal()

	packed_message.write(digest)
	packed_message.write(hubitat.helper.HexUtils.hexStringToByteArray(payload()[payloadFormat]["suffix"]))

	if (logEnable) log.debug hubitat.helper.HexUtils.byteArrayToHexString(packed_message.toByteArray())

	packed_message.toByteArray()
}

def pack_and_send_payload_v34(byte[] data, Integer cmd, Integer msg_count=0) {
	payloadFormat = "v3.4"

	encrypted_payload = encrypt(data, localKey.replaceAll('&lt;', '<').getBytes("UTF-8"), false)

	if (logEnable) log.debug("payload encrypted: " + encrypted_payload)

	encrypted_payload = hubitat.helper.HexUtils.hexStringToByteArray(encrypted_payload)

	def packed_message = new ByteArrayOutputStream()

	packed_message.write(hubitat.helper.HexUtils.hexStringToByteArray(payload()[payloadFormat]["prefix_nr"]))
	packed_message.write(state.msgseq >> 8)
	packed_message.write(state.msgseq)
	state.msgseq = state.msgseq + 1
	packed_message.write(hubitat.helper.HexUtils.hexStringToByteArray("000000"))
	packed_message.write(cmd)
	packed_message.write(hubitat.helper.HexUtils.hexStringToByteArray("000000"))
	packed_message.write(encrypted_payload.size() + 32 + hubitat.helper.HexUtils.hexStringToByteArray(payload()[payloadFormat]["suffix"]).size())
	packed_message.write(encrypted_payload)

	if (logEnable) log.debug hubitat.helper.HexUtils.byteArrayToHexString(packed_message.toByteArray())

	Mac sha256_hmac = Mac.getInstance("HmacSHA256")
	secret = localKey.replaceAll('&lt;', '<')
	SecretKeySpec key = new SecretKeySpec(secret.getBytes("UTF-8"), "HmacSHA256")

	sha256_hmac.init(key)
	sha256_hmac.update(packed_message.toByteArray(), 0, packed_message.size())
	byte[] digest = sha256_hmac.doFinal()

	packed_message.write(digest)
	packed_message.write(hubitat.helper.HexUtils.hexStringToByteArray(payload()[payloadFormat]["suffix"]))

	if (logEnable) log.debug hubitat.helper.HexUtils.byteArrayToHexString(packed_message.toByteArray())

	packed_message.toByteArray()
}

// Helper functions
def payload()
{
	def payload_dict = [
		"v3.1_v3.3": [
			"status": [
				"hexByte": "0a",
				"command": ["gwId":"", "devId":"", "uid":"", "t":""]
			],
			"set": [
				"hexByte": "07",
				"command": ["devId":"", "uid": "", "t": ""]
			],
			"prefix_nr": "000055aa0000",
			"prefix": "000055aa00000000000000",
			"suffix": "0000aa55"
		],
		"v3.4": [
			"status": [
				"hexByte": "10",
				"command": [:]
			],
			"set": [
				"hexByte": "0d",
				"command": ["protocol":5,"t":"","data":""]
			],
			"neg1" : [
				"hexByte": "03"
			],
			"prefix_nr": "000055aa0000",
			"prefix"   : "000055aa00000000000000",
			"suffix"   : "0000aa55"
		]
	]

	return payload_dict
}

// Huge thank you to MrYutz for posting Groovy AES ecryption drivers for groovy
//https://community.hubitat.com/t/groovy-aes-encryption-driver/31556

import javax.crypto.spec.IvParameterSpec;
import javax.crypto.Cipher

// Encrypt plain text v. 3.1 uses base64 encoding, while 3.3 does not
def encrypt (def plainText, byte[] secret, encodeB64=true) {
	// Encryption is AES in ECB mode, pad using PKCS5Padding as needed
	def cipher = Cipher.getInstance("AES/ECB/PKCS5Padding ")
	SecretKeySpec key = new SecretKeySpec(secret, "AES")

	// Give the encryption engine the encryption key
	cipher.init(Cipher.ENCRYPT_MODE, key)

	def result = ""

	if (encodeB64) {
		result = cipher.doFinal(plainText.getBytes("UTF-8")).encodeBase64().toString()
	} else {
		if (plainText instanceof String) {
			result = cipher.doFinal(plainText.getBytes("UTF-8")).encodeHex().toString()
		} else {
			result = cipher.doFinal(plainText).encodeHex().toString()
		}
	}

	return result
}

// Decrypt ByteArray
def decrypt_bytes (byte[] cypherBytes, def secret, decodeB64=false) {
	if (logEnable) log.debug "*********** Decrypting **************"


	def cipher = Cipher.getInstance("AES/ECB/PKCS5Padding ")

	SecretKeySpec key

	if (secret instanceof String) {
		// Fix key to remove any escaped characters
		secret = secret.replaceAll('&lt;', '<')
		key = new SecretKeySpec(secret.getBytes(), "AES")
	} else {
		key = new SecretKeySpec(secret as byte[], "AES")
	}

	cipher.init(Cipher.DECRYPT_MODE, key)

	if (decodeB64) {
		cypherBytes = cypherBytes.decodeBase64()
	}

	def result = cipher.doFinal(cypherBytes)

	return new String(result, "UTF-8")
}

import java.security.MessageDigest

def generateMD5(String s){
	MessageDigest.getInstance("MD5").digest(s.bytes).encodeHex().toString()
}

def CRC32b(bytes, length) {
	crc = 0xFFFFFFFF

	for (i = 0; i < length; i++) {
		b = Byte.toUnsignedInt(bytes[i])

		crc = crc ^ b
		for (j = 7; j >= 0; j--) {
			mask = -(crc & 1)
			crc = (crc >> 1) ^(0xEDB88320 & mask)
		}
	}

	return ~crc
}

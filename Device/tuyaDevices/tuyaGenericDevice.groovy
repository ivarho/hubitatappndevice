/**
 * IMPORT URL: https://raw.githubusercontent.com/ivarho/hubitatappndevice/master/Device/tuyaDevices/tuyaGenericDevice.groovy
 *
 * Copyright 2020-2023 Ivar Holand
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
	definition(name: "tuya Generic Device", namespace: "iholand", author: "iholand") {
		capability "Actuator"
		capability "Switch"
		capability "Sensor"

		command "status"

		attribute "availableEndpoints", "String"
	}
}

preferences {
	section("URIs") {
		input "ipaddress", "text", title: "Device IP:", required: false
		input "devId", "text", title: "Device ID:", required: false
		input "localKey", "text", title: "Device local key:", required: false
		input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true
		input "endpoint", "text", title: "End point to control: ", required: true
		input "tuyaProtVersion", "enum", title: "Select tuya protocol version: ", required: true, options: [31: "3.1", 33 : "3.3", 34: "3.4 (experimental)"]
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

	sendEvent(name: "switch", value: "off")
}

def parse(String description) {
	if (logEnable) log.debug "Receiving message from device"
	if (logEnable) log.debug(description)

	byte[] msg_byte = hubitat.helper.HexUtils.hexStringToByteArray(description)

	String status = new String(msg_byte, "UTF-8")

	String protocol_version = ""

	status = status[20..-1]

	if (logEnable) log.debug "Raw incoming data: " + status

	if (!status.startsWith("{")) {
		// Encrypted message incoming, decrypt first

		if (logEnable) log.debug "Encrypted message detected"
		if (logEnable) log.debug "Bytes incoming: " + msg_byte.size()

		def message_start = 0

		// Find message type to determine start of message
		def message_type = msg_byte[11].toInteger()

		if (logEnable) log.debug ("Message type: ${message_type}")

		if (message_type == 7) {
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
			byte[] ver_bytes = [msg_byte[20], msg_byte[21], msg_byte[22]]
			protocol_version = new String(ver_bytes)

			if (logEnable) log.debug("Protocol version: " + protocol_version)

			if (protocol_version == "3.1") {
				message_start = 67
				log.error("Not supported! Please upgrade device firmware to 3.3")
			} else if (protocol_version == "3.3" || protocol_version == "3.4") {
				message_start = 35
			} else {
				log.error("Device firmware version not supported, protocol verison" + protocol_version)
			}

		} else if (message_type == 10) {
			// Incoming status message
			message_start = 20

			// Status messages do not contain version information, however v 3.3
			// protocol encrypts status messages, v 3.1 does not
			protocol_version = "3.3"
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
		for (i = message_start; i < end_of_message+message_start; i++) {
			output.write(msg_byte[i])
		}

		byte[] payload = output.toByteArray()

		if (logEnable) log.debug "Assembled payload for decrypt: "+ hubitat.helper.HexUtils.byteArrayToHexString(payload)

		def dec_status = ""

		if (protocol_version == "3.1") {
			dec_status = decrypt_bytes(payload, settings.localKey, true)
		} else if (protocol_version == "3.3" || protocol_version == "3.4") {
			dec_status = decrypt_bytes(payload, settings.localKey, false)
		}

		if (logEnable) log.debug "Decryted message: ${dec_status}"

		status = dec_status
	}

	def jsonSlurper = new groovy.json.JsonSlurper()

	if (status != Null && status != "") {
		def status_object = jsonSlurper.parseText(status)

		sendEvent(name: "availableEndpoints", value: status_object.dps)

		if (status_object.dps[endpoint] == true) {
			sendEvent(name: "switch", value : "on", isStateChange : true)
		} else {
			sendEvent(name: "switch", value : "off", isStateChange : true)
		}
	}

	try {
		interfaces.rawSocket.close()
	} catch (e) {
		log.error "Could not close socket: $e"
	}
}

def socketStatus(socetStatusMsg) {
	log.debug "Socket status message received:" + socetStatusMsg
}

def status() {
	send(generate_payload("status"))
}

def on() {
	send(generate_payload("set", ["${settings.endpoint}":true]))
}

def off() {
	send(generate_payload("set", ["${settings.endpoint}":false]))
}

import hubitat.device.HubAction
import hubitat.device.Protocol

def send(byte[] message) {
	String msg = hubitat.helper.HexUtils.byteArrayToHexString(message)

	if (logEnable) log.debug "Sending message to " + settings.ipaddress + ":" + 6668 + " msg: " + msg

	try {
		//port 6668
		interfaces.rawSocket.connect(settings.ipaddress, 6668, byteInterface: true, readDelay: 500)
		interfaces.rawSocket.sendMessage(msg)
	} catch (e) {
		log.error "Error $e"
	}
}

def generate_payload(command, data=null) {

	String tuyaProtVersionStr = ""

	switch (tuyaProtVersion) {
		case "31":
			tuyaProtVersionStr = "3.1"
			break
		case "33":
			tuyaProtVersionStr = "3.3"
			break
		case "34":
			tuyaProtVersionStr = "3.4"
			break
	}

	def json = new groovy.json.JsonBuilder()

	json_data = payload()["device"][command]["command"]

	if (json_data.containsKey("gwId")) {
		json_data["gwId"] = settings.devId
	}
	if (json_data.containsKey("devId")) {
		json_data["devId"] = settings.devId
	}
	if (json_data.containsKey("uid")) {
		json_data["uid"] = settings.devId
	}
	if (json_data.containsKey("t")) {
		Date now = new Date()
		json_data["t"] = (now.getTime()/1000).toInteger().toString()
		//json_data["t"] = "1602184793" // for testing
	}

	if (data != null) {
		json_data["dps"] = data
	}

	json json_data

	if (logEnable) log.debug tuyaProtVersion

	json_payload = groovy.json.JsonOutput.toJson(json.toString())
	json_payload = json_payload.replaceAll("\\\\", "")
	json_payload = json_payload.replaceFirst("\"", "")
	json_payload = json_payload[0..-2]

	if (logEnable) log.debug "payload before=" + json_payload

	ByteArrayOutputStream output = new ByteArrayOutputStream()

	if (command == "set" && tuyaProtVersion == "31") {
		encrypted_payload = encrypt(json_payload, settings.localKey)

		if (logEnable) log.debug "Encrypted payload: " + hubitat.helper.HexUtils.byteArrayToHexString(encrypted_payload.getBytes())

		preMd5String = "data=" + encrypted_payload + "||lpv=" + tuyaProtVersionStr + "||" + settings.localKey

		if (logEnable) log.debug "preMd5String" + preMd5String

		hexdigest = generateMD5(preMd5String)

		hexdig = new String(hexdigest[8..-9].getBytes("UTF-8"), "ISO-8859-1")

		json_payload = tuyaProtVersionStr + hexdig + encrypted_payload

	} else if (tuyaProtVersion == "33") {
		encrypted_payload = encrypt(json_payload, settings.localKey, false)

		if (logEnable) log.debug encrypted_payload

		if (command != "status" && command != "12") {
			output.write(tuyaProtVersionStr.getBytes())
			output.write("\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000".getBytes())
			output.write(hubitat.helper.HexUtils.hexStringToByteArray(encrypted_payload))
		} else {
			output.write(hubitat.helper.HexUtils.hexStringToByteArray(encrypted_payload))
		}
	}

	if (tuyaProtVersion == "31") {
		output.write(json_payload.getBytes())
	}

	if (logEnable) log.debug "payload after=" + json_payload

	output.write(hubitat.helper.HexUtils.hexStringToByteArray(payload()["device"]["suffix"]))

	byte[] bff = output.toByteArray()

	if (logEnable) log.debug hubitat.helper.HexUtils.byteArrayToHexString(bff)

	postfix_payload = bff

	postfix_payload_hex_len = postfix_payload.size()

	if (logEnable) log.debug postfix_payload_hex_len

	if (logEnable) log.debug "Prefix: " + hubitat.helper.HexUtils.byteArrayToHexString(hubitat.helper.HexUtils.hexStringToByteArray(payload()["device"]["prefix"]))

	output = new ByteArrayOutputStream();

	output.write(hubitat.helper.HexUtils.hexStringToByteArray(payload()["device"]["prefix"]))
	output.write(hubitat.helper.HexUtils.hexStringToByteArray(payload()["device"][command]["hexByte"]))
	output.write(hubitat.helper.HexUtils.hexStringToByteArray("000000"))
	output.write(postfix_payload_hex_len)
	output.write(postfix_payload)

	byte[] buf = output.toByteArray()

	crc32 = CRC32b(buf, buf.size()-8) & 0xffffffff
	if (logEnable) log.debug buf.size()

	hex_crc = Long.toHexString(crc32)

	if (logEnable) log.debug "HEX crc: $hex_crc : " + hex_crc.size()/2

	// Pad the CRC in case highest byte is 0
	if (hex_crc.size() < 7) {
		hex_crc = "00" + hex_crc
	}

	crc_bytes = hubitat.helper.HexUtils.hexStringToByteArray(hex_crc)

	buf[buf.size()-8] = crc_bytes[0]
	buf[buf.size()-7] = crc_bytes[1]
	buf[buf.size()-6] = crc_bytes[2]
	buf[buf.size()-5] = crc_bytes[3]

	return buf
}

// Helper functions
def payload()
{
	def payload_dict = [
		"device": [
			"status": [
				"hexByte": "0a",
				"command": ["devId": "", "gwId": "", "uid":"", "t": ""]
			],
			"set": [
				"hexByte": "07",
				"command": ["devId":"", "uid": "", "t": ""]
			],
			"prefix": "000055aa00000000000000",
			"suffix": "000000000000aa55"
		]
	]

	return payload_dict
}

// Huge thank you to MrYutz for posting Groovy AES ecryption drivers for groovy
//https://community.hubitat.com/t/groovy-aes-encryption-driver/31556

import javax.crypto.spec.SecretKeySpec;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.Cipher

// Encrypt plain text v. 3.1 uses base64 encoding, while 3.3 does not
def encrypt (def plainText, def secret, encodeB64=true) {

	// Fix key to remove any escaped characters
	secret = secret.replaceAll('&lt;', '<')

	// Encryption is AES in ECB mode, pad using PKCS5Padding as needed
	def cipher = Cipher.getInstance("AES/ECB/PKCS5Padding ")
	SecretKeySpec key = new SecretKeySpec(secret.getBytes("UTF-8"), "AES")

	// Give the encryption engine the encryption key
	cipher.init(Cipher.ENCRYPT_MODE, key)

	def result = ""

	if (encodeB64) {
		result = cipher.doFinal(plainText.getBytes("UTF-8")).encodeBase64().toString()
	} else {
		result = cipher.doFinal(plainText.getBytes("UTF-8")).encodeHex().toString()
	}

	return result
}

// Decrypt ByteArray
def decrypt_bytes (byte[] cypherBytes, def secret, decodeB64=false) {
	if (logEnable) log.debug "*********** Decrypting **************"

	// Fix key to remove any escaped characters
	secret = secret.replaceAll('&lt;', '<')

	def cipher = Cipher.getInstance("AES/ECB/PKCS5Padding ")
	SecretKeySpec key = new SecretKeySpec(secret.getBytes(), "AES")

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

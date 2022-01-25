/**
 * Copyright 2020-2022 Ivar Holand
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
	}
}

preferences {
	section("URIs") {
		input "ipaddress", "text", title: "Device IP:", required: false
		input "devId", "text", title: "Device ID:", required: false
		input "localKey", "text", title: "Device local key:", required: false
		input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true
		input "endpoint", "text", title: "End point to control: ", required: true
		input "tuyaProtVersion", "enum", title: "Select tuya protocol version: ", required: true, options: [31: "3.1", 33 : "3.3"]
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

	//def status = msg_byte.toString()

	status = status[20..-1]

	if (logEnable) log.debug status

	if (!status.startsWith("{")) {
		// Encrypted message incoming, decrypt first

		if (logEnable) log.debug "Incoming message: "+ hubitat.helper.HexUtils.byteArrayToHexString(msg_byte)

		if (logEnable) log.debug "Bytes incoming: " + msg_byte.size()

		def end_of_message = 0

		for (u = 63; u < msg_byte.size()-1; u++) {
			if (msg_byte[u] == (byte)0xAA && msg_byte[u+1] == (byte)0x55) {
				//msg end found
				log.debug "End of message: ${u-63-6}"
				end_of_message = u-63-6
				break
			}
		}

		ByteArrayOutputStream output = new ByteArrayOutputStream()
		for (i = 63; i < end_of_message+63; i++) {
			output.write(msg_byte[i])
		}




		//while (missing_bytes--) {
		//	output.write(n++)
		//}

		byte[] payload = output.toByteArray()

		if (logEnable) log.debug "Payload: "+ hubitat.helper.HexUtils.byteArrayToHexString(payload)

		//pad missing



		def dec_status = decrypt_bytes(payload, settings.localKey)
		if (logEnable) log.debug "Decryted message: ${dec_status}"

		status = dec_status
	}

	def jsonSlurper = new groovy.json.JsonSlurper()
		def status_object = jsonSlurper.parseText(status)


	if (status_object.dps[endpoint] == true) {
		sendEvent(name: "switch", value : "on", isStateChange : true)
	} else {
		sendEvent(name: "switch", value : "off", isStateChange : true)
	}

	try {
		interfaces.rawSocket.close()
	} catch (e) {
		log.error "Could not close socket: $e"
	}
}

def payload()
{
	def payload_dict = [
		"device": [
			"status": [
				"hexByte": "0a",
				"command": ["devId": "", "gwId": ""]
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

// do the magic
def encrypt (def plainText, def secret, encodeB64=true) {
	//if (logEnable) log.debug ("Encrypting - ${plainText}","trace")
	// this particular magic sauce pads the payload to 128 bits per chunk
	// even though that shouldn't work with S5Padding....
	def cipher = Cipher.getInstance("AES/ECB/PKCS5Padding ")
	SecretKeySpec key = new SecretKeySpec(secret.getBytes("UTF-8"), "AES")

	// initialize the encryption and get ready for that dirty dirty magic
	cipher.init(Cipher.ENCRYPT_MODE, key)
	// boom goes the dynamite

	def result

	if (encodeB64) {
		result = cipher.doFinal(plainText.getBytes("UTF-8")).encodeBase64().toString()
	} else {
		result = cipher.doFinal(plainText.getBytes("UTF-8")).encodeHex().toString()
	}

	log.debug result

	return result
}

// undo the magic
def decrypt (def cypherText, def secret) {
	//log ("Decrypting - ${cypherText}","trace")
	// this was so much easier to get done than the encryption.  It works.  Don't touch it
	// cereal.  you will regret it.

	if (logEnable) log.debug cypherText

	// drop those beats..or bytes.
	byte[] decodedBytes = cypherText.getBytes()
	if (logEnable) log.debug hubitat.helper.HexUtils.byteArrayToHexString(decodedBytes)
	//log("decodedBytes:   ${decodedBytes}", "trace")
	// no whammy no whammy no whammy.......
	def cipher = Cipher.getInstance("AES/ECB/PKCS5Padding ")
	SecretKeySpec key = new SecretKeySpec(secret.getBytes("UTF-8"), "AES")
	cipher.init(Cipher.DECRYPT_MODE, key)

	if (logEnable) log.debug decodedBytes.size()
	//whammy!
	return new String(cipher.doFinal(decodedBytes), "UTF-8")
}

// undo the magic
def decrypt_bytes (byte[] cypherBytes, def secret) {
	//log ("Decrypting - ${cypherText}","trace")
	// this was so much easier to get done than the encryption.  It works.  Don't touch it
	// cereal.  you will regret it.

	// drop those beats..or bytes.
	//byte[] decodedBytes = cypherText.getBytes("ASCII")
	if (logEnable) log.debug hubitat.helper.HexUtils.byteArrayToHexString(cypherBytes)
	//log("decodedBytes:   ${decodedBytes}", "trace")
	// no whammy no whammy no whammy.......
	def cipher = Cipher.getInstance("AES/ECB/PKCS5Padding ")
	SecretKeySpec key = new SecretKeySpec(secret.getBytes(), "AES")

	//log.debug secret.getBytes("UTF-8")

	cipher.init(Cipher.DECRYPT_MODE, key)

	if (logEnable) log.debug cypherBytes.size()

	//cipher.update(cypherBytes)
	def result = cipher.doFinal(cypherBytes)

	//whammy!
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
		//if (logEnable) log.debug Long.toHexString(~crc & 0xffffffff) + " - " + b
	}

	return ~crc
}

def generate_payload(command, data=null) {

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
		json_data["t"] = (now.getTime()/1000).toInteger()
		//json_data["t"] = "1602184793"
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

	if (command == "set" && tuyaProtVersion == "31") {
		encrypted_payload = encrypt(json_payload, settings.localKey)

		if (logEnable) log.debug encrypted_payload

		preMd5String = "data=" + encrypted_payload + "||lpv=" + "3.1" + "||" + settings.localKey

		if (logEnable) log.debug "preMd5String" + preMd5String

		hexdigest = generateMD5(preMd5String)

		hexdig = new String(hexdigest[8..-9].getBytes("UTF-8"), "ISO-8859-1")

		json_payload = "3.1" + hexdig + encrypted_payload
	} else if (command == "set" && tuyaProtVersion == "33") {
		encrypted_payload = encrypt(json_payload, settings.localKey, false)

		if (logEnable) log.debug encrypted_payload

		json_payload = "3.3" + "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000" + encrypted_payload

	}

	if (logEnable) log.debug "payload after=" + json_payload

	ByteArrayOutputStream output = new ByteArrayOutputStream()

	if (tuyaProtVersion == "31") {
		output.write(json_payload.getBytes())
	} else if (tuyaProtVersion == "33") {
		output.write("3.3".getBytes())
		output.write("\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000".getBytes())
		output.write(hubitat.helper.HexUtils.hexStringToByteArray(encrypted_payload))
	}

	output.write(hubitat.helper.HexUtils.hexStringToByteArray(payload()["device"]["suffix"]))

	byte[] bff = output.toByteArray()

	if (logEnable) log.debug hubitat.helper.HexUtils.byteArrayToHexString(bff)

	postfix_payload = bff

	//if (logEnable) log.debug "Postfix payload: " + postfix_payload

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

	if (logEnable) log.debug "HEX crc: $hex_crc"

	crc_bytes = hubitat.helper.HexUtils.hexStringToByteArray(hex_crc)

	buf[buf.size()-8] = crc_bytes[0]
	buf[buf.size()-7] = crc_bytes[1]
	buf[buf.size()-6] = crc_bytes[2]
	buf[buf.size()-5] = crc_bytes[3]

	return buf
}

import hubitat.device.HubAction
import hubitat.device.Protocol

def status() {
	byte[] buf = generate_payload("status")

	String msg = hubitat.helper.HexUtils.byteArrayToHexString(buf)

	// Needed to use the rawSocket interface to get a response
	interfaces.rawSocket.connect(settings.ipaddress, 6668, byteInterface: true, readDelay: 500)

	try {
		interfaces.rawSocket.sendMessage(msg)
	} catch (e) {
		log.error "Error $e"
	}
}

def on() {

	//sendEvent(name: "switch", value : "on", isStateChange : true)

	def buf = generate_payload("set", ["${settings.endpoint}":true])

	if (logEnable) log.debug hubitat.helper.HexUtils.byteArrayToHexString(buf)

	String msg = hubitat.helper.HexUtils.byteArrayToHexString(buf)

	//port 6668
	interfaces.rawSocket.connect(settings.ipaddress, 6668, byteInterface: true, readDelay: 500)
	interfaces.rawSocket.sendMessage(msg)

	// Check Status
	//runIn(1, status)
}

def off() {
	//sendEvent(name: "switch", value : "off", isStateChange : true)

	def buf = generate_payload("set", ["${settings.endpoint}":false])

	if (logEnable) log.debug hubitat.helper.HexUtils.byteArrayToHexString(buf)

	String msg = hubitat.helper.HexUtils.byteArrayToHexString(buf)

	//port 6668
	interfaces.rawSocket.connect(settings.ipaddress, 6668, byteInterface: true, readDelay: 500)
	interfaces.rawSocket.sendMessage(msg)

	// Check Status
	//runIn(1, status)
}

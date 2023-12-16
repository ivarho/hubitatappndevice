/**
 * IMPORT URL: https://raw.githubusercontent.com/ivarho/hubitatappndevice/master/Device/tuyaDevices/tuyaWifiSiren.groovy
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

import hubitat.device.HubAction
import hubitat.device.Protocol

import javax.crypto.spec.SecretKeySpec;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.Cipher
import java.security.MessageDigest

metadata {
	definition(name: "tuya Wifi Siren", namespace: "iholand", author: "iholand") {
		capability "Alarm"

		attribute "sirenType", "string"
		attribute "sirenLength", "number"

		attribute "availableEndpoints", "String"

		command "status"
		command "setSirenType", [[name:"SirenType*", type: "NUMBER", description: "Select siren type (1-10)"]]
		command "setSirenLength", [[name:"SirenLength*", type: "NUMBER", description: "Select the length of the siren (1-60)"]]
	}
}

preferences {
	section("Setttings") {
		input "ipaddress", "text", title: "Device IP:", required: false
		input "devId", "text", title: "Device ID:", required: false
		input "localKey", "text", title: "Device local key:", required: false
		input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true
		input name: "sirenSound", type: "enum", title: "Select sound for siren:", options : [1: "1", 2: "2", 3: "3", 4: "4", 5: "5", 6: "6", 7: "7", 8: "8", 9: "9", 10: "10"], defaultValue: 1
		input name: "strobeSound", type: "enum", title: "Select sound for strobe:", options : [1: "1", 2: "2", 3: "3", 4: "4", 5: "5", 6: "6", 7: "7", 8: "8", 9: "9", 10: "10"], defaultValue: 1
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

	sendEvent(name: "sirenType", value: "1")
	sendEvent(name: "sirenLength", value: 10)

	sendEvent(name: "alarm", value: "off")
}

def setSirenType(type) {
	log.debug type

	def buf = generate_payload("set", ["102":type.toString()])

	send(buf)
}

def setSirenLength(length) {
	def buf = generate_payload("set", ["103":length])

	send(buf)
}

def device_specific_parser(Object status_object) {

	sendEvent(name: "availableEndpoints", value: status_object.dps)

	if (status_object.dps["104"] == true) {
		sendEvent(name: "alarm", value : "siren", isStateChange : true)
	} else {
		sendEvent(name: "alarm", value : "off", isStateChange : true)
	}

	if (status_object.dps["102"] != null) {
		// Siren type
		sendEvent(name: "sirenType", value: status_object.dps["102"])
	}

	if (status_object.dps["103"] != null) {
		// Siren length
		sendEvent(name: "sirenLength", value: status_object.dps["103"])
	}
}

def afterStatus() {
	log.warn "Running after status"

	status()
}

def status() {
	byte[] buf = generate_payload("status")

	send(buf)
}

def off() {
	def buf = generate_payload("set", ["104":false])

	send(buf)
}

def both() {
	siren()
}

def siren() {
	setSirenType(sirenSound)

	def buf = generate_payload("set", ["104":true])

	send(buf)
}

def strobe() {
	setSirenType(strobeSound)

	def buf = generate_payload("set", ["104":true])

	send(buf)
}

// ************************************ TUYA PROTOCOL FUNCTIONS *************************************

import hubitat.device.HubAction
import hubitat.device.Protocol

def socketStatus(socetStatusMsg) {
	log.debug "Socket status message received:" + socetStatusMsg
}

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
	generatedTestVector = generate_payload("set", ["20": true], "1702671803", localKey="7ae83ffe1980sa3c", devid="bfd733c97d1bfc88b3sysa", tuyaVersion="31")
	DriverSelfTestReport("SetMessageV3_1", generatedTestVector, expected)

	// Testing 3.1 status message
	expected = "000055AA000000000000000A0000007A7B2267774964223A2262666437333363393764316266633838623373797361222C226465764964223A2262666437333363393764316266633838623373797361222C22756964223A2262666437333363393764316266633838623373797361222C2274223A2231373032363731383033227DCA1E0CC60000AA55"
	generatedTestVector = generate_payload("status", data=null, "1702671803", localKey="7ae83ffe1980sa3c", devid="bfd733c97d1bfc88b3sysa", tuyaVersion="31")
	DriverSelfTestReport("StatusMessageV3_1", generatedTestVector, expected)

	// Testing 3.3 set message
	expected = "000055AA000000000000000700000087332E33000000000000000000000000A748E326EB4BA47F40A36295A2F04D508F89C51BC8DCD5F7D0FF72318267DE9F01A4A123B308392F7FB1238EBCD4A47008E1CBEA4ECAE42ED9EBF5EF9A3BDEA8316CA2A375CBED57252A6E06F9990BB56CA9D203DE3F23F774FCC8345A4DF2441DA3D09F12FD1C36D81EE25C709727DF2E5CF2B30000AA55"
	generatedTestVector = generate_payload("set", ["20": true], "1702671803", localKey="7ae83ffe1980sa3c", devid="bfd733c97d1bfc88b3sysa", tuyaVersion="33")
	DriverSelfTestReport("SetMessageV3_3", generatedTestVector, expected)

	// Testing 3.3 status message
	expected = "000055AA000000000000000A00000088D0436FF6B453B07DC2CC8084484A8E3E08E1CBEA4ECAE42ED9EBF5EF9A3BDEA834A1D6E20760F13A0CF9DE1523730E598F89C51BC8DCD5F7D0FF72318267DE9F01A4A123B308392F7FB1238EBCD4A47008E1CBEA4ECAE42ED9EBF5EF9A3BDEA8316CA2A375CBED57252A6E06F9990BB543FF054E84050A495D427D28A8C0F29F0104C4D70000AA55"
	generatedTestVector = generate_payload("status", data=null, "1702671803", localKey="7ae83ffe1980sa3c", devid="bfd733c97d1bfc88b3sysa", tuyaVersion="33")
	DriverSelfTestReport("StatusMessageV3_3", generatedTestVector, expected)


	// Testing 3.4 set message
	expected = "000055AA000000000000000D000000749AC0971A69B046C19DDFEAB6800CBB66A8FC70BDD2FF855511A3A2CBF2955BFC806C9FBFFA10ED709EC2BA4D8EC24609E50317C707468F02A110E429BA321FAA3862640A83699215E1313BA653C6DA0E5F01AADD72E172D7705B0AF82BFCD5E54A92562659A18235AEF0DDB1453BB7070000AA55"
	generatedTestVector = generate_payload("set", ["20": true], "1702671803", localKey="7ae83ffe1980sa3c", devid="bfd733c97d1bfc88b3sysa", tuyaVersion="34")
	DriverSelfTestReport("SetMessageV3_4", generatedTestVector, expected)

	// Testing 3.4 status message
	expected = "000055AA000000000000001000000034A78158A05A786D32FEC14903A94445B47BEA54632DA130BAB31B719A8C21AB419104665404C82C85BDB55DCA068791F60000AA55"
	generatedTestVector = generate_payload("status", data=null, "1702671803", localKey="7ae83ffe1980sa3c", devid="bfd733c97d1bfc88b3sysa", tuyaVersion="34")
	DriverSelfTestReport("StatusMessageV3_4", generatedTestVector, expected)
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

	if (status != null && status != "") {
		def jsonSlurper = new groovy.json.JsonSlurper()
		def status_object = jsonSlurper.parseText(status)
		device_specific_parser(status_object)
		sendEvent(name: "rawMessage", value: status_object.dps)
	} else {
		// Message did not contain data, bulb received unknown command?
		log.error "Device did not understand command"
	}

	try {
		interfaces.rawSocket.close()
	} catch (e) {
		log.error "Could not close socket: $e"
	}
}

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec;

def generate_payload(command, data=null, timestamp=null, localkey=settings.localKey, devid=settings.devId, tuyaVersion=settings.tuyaProtVersion) {

	switch (tuyaVersion) {
		case "31":
		case "33":
			payloadFormat = "v3.1_v3.3"
			break
		case "34":
			payloadFormat = "v3.4"
			break
	}

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

	if (data != null) {
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
			preMd5String = "data=" + encrypted_payload + "||lpv=" + "3.1" + "||" + localkey
			if (logEnable) log.debug "preMd5String" + preMd5String
			hexdigest = generateMD5(preMd5String)
			hexdig = new String(hexdigest[8..-9].getBytes("UTF-8"), "ISO-8859-1")
			json_payload = "3.1" + hexdig + encrypted_payload
		}
		contructed_payload.write(json_payload.getBytes())

	} else if (tuyaVersion == "33") {
		encrypted_payload = encrypt(json_payload, localkey, false)

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
		encrypted_payload = encrypt(json_payload, localkey, false)
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

	// Start constructing the final message
	output = new ByteArrayOutputStream()
	output.write(hubitat.helper.HexUtils.hexStringToByteArray(payload()[payloadFormat]["prefix"]))
	output.write(hubitat.helper.HexUtils.hexStringToByteArray(payload()[payloadFormat][command]["hexByte"]))
	output.write(hubitat.helper.HexUtils.hexStringToByteArray("000000"))
	output.write(payload_len)
	output.write(final_payload)

	byte[] buf = output.toByteArray()

	if (tuyaVersion == "34") {
		if (logEnable) log.info "Using HMAC (SHA-256) as checksum"

		Mac sha256_hmac = Mac.getInstance("HmacSHA256")
		secret = localkey.replaceAll('&lt;', '<')
		SecretKeySpec key = new SecretKeySpec(secret.getBytes("UTF-8"), "HmacSHA256")

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
			"prefix": "000055aa00000000000000",
			"suffix": "0000aa55"
		]
	]

	return payload_dict
}

// Huge thank you to MrYutz for posting Groovy AES ecryption drivers for groovy
//https://community.hubitat.com/t/groovy-aes-encryption-driver/31556

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

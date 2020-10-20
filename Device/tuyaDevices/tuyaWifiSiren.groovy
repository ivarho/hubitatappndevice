/*
 * tuya Wifi Siren Device
 *
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

def parse(String description) {
	if (logEnable) log.debug(description)
	if (logEnable) log.debug(description)
	if (logEnable) log.debug "Parsing incoming message"

	byte[] msg_byte = hubitat.helper.HexUtils.hexStringToByteArray(description)

	String status = new String(msg_byte, "ASCII")

	status = status[20..-1]

	if (logEnable) log.debug status

	if (status.startsWith("{")) {

		def jsonSlurper = new groovy.json.JsonSlurper()
		def status_object = jsonSlurper.parseText(status)


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

	try {
		//interfaces.rawSocket.close()
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

// do the magic
def encrypt (def plainText, def secret) {
	//if (logEnable) log.debug ("Encrypting - ${plainText}","trace")
	// this particular magic sauce pads the payload to 128 bits per chunk
	// even though that shouldn't work with S5Padding....
	def cipher = Cipher.getInstance("AES/ECB/PKCS5Padding ")
	SecretKeySpec key = new SecretKeySpec(secret.getBytes("UTF-8"), "AES")

	// initialize the encryption and get ready for that dirty dirty magic
	cipher.init(Cipher.ENCRYPT_MODE, key)
	// boom goes the dynamite
	def result = cipher.doFinal(plainText.getBytes("UTF-8")).encodeBase64().toString()

	return result
}

// undo the magic
def decrypt (def cypherText, def secret) {
	//log ("Decrypting - ${cypherText}","trace")
	// this was so much easier to get done than the encryption.  It works.  Don't touch it
	// cereal.  you will regret it.

	// drop those beats..or bytes.
	byte[] decodedBytes = cypherText.getBytes("UTF-8")
	//log("decodedBytes:   ${decodedBytes}", "trace")
	// no whammy no whammy no whammy.......
	def cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
	SecretKeySpec key = new SecretKeySpec(secret.getBytes("UTF-8"), "AES")
	cipher.init(Cipher.DECRYPT_MODE, key)

	if (logEnable) log.debug decodedBytes.size()
	//whammy!
	return new String(cipher.doFinal(decodedBytes), "UTF-8")
}

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

	json_payload = groovy.json.JsonOutput.toJson(json.toString())
	json_payload = json_payload.replaceAll("\\\\", "")
	json_payload = json_payload.replaceFirst("\"", "")
	json_payload = json_payload[0..-2]

	if (logEnable) log.debug json_payload

	if (command == "set") {
		encrypted_payload = encrypt(json_payload, settings.localKey)

		if (logEnable) log.debug encrypted_payload

		preMd5String = "data=" + encrypted_payload + "||lpv=" + "3.1" + "||" + settings.localKey

		if (logEnable) log.debug "preMd5String" + preMd5String

		hexdigest = generateMD5(preMd5String)

		hexdig = new String(hexdigest[8..-9].getBytes("UTF-8"), "ISO-8859-1")

		json_payload = "3.1" + hexdig + encrypted_payload
	}

	if (logEnable) log.debug json_payload

	ByteArrayOutputStream output = new ByteArrayOutputStream()

	output.write(json_payload.getBytes())
	output.write(hubitat.helper.HexUtils.hexStringToByteArray(payload()["device"]["suffix"]))

	byte[] bff = output.toByteArray()

	//if (logEnable) log.debug hubitat.helper.HexUtils.byteArrayToHexString(bff)

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

def send(byte[] buf) {
	String msg = hubitat.helper.HexUtils.byteArrayToHexString(buf)

	try {
		//port 6668
		interfaces.rawSocket.connect(settings.ipaddress, 6668, byteInterface: true, readDelay: 500)
		interfaces.rawSocket.sendMessage(msg)
	} catch (e) {
		log.error "Error $e"
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
	sendEvent(name: "switch", value : "off", isStateChange : true)
}

def both() {
	siren()
}

def siren() {
	def buf = generate_payload("set", ["104":true])

	send(buf)

	// Poll for status change
	runIn(1, status)
	runIn(device.currentValue("sirenLength").toInteger(), afterStatus)
}

def strobe() {
	// No strobe
}

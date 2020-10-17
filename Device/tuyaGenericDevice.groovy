/*
 * tuya Device
 *
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
	if (logEnable) log.debug(description)
	log.debug(description)
	log.debug "Here"

	byte[] msg_byte = hubitat.helper.HexUtils.hexStringToByteArray(description)

	String status = new String(msg_byte, "ASCII")

	status = status[20..-10]

	def jsonSlurper = new groovy.json.JsonSlurper()
	def status_object = jsonSlurper.parseText(status)


	if (status_object.dps[endpoint] == true) {
		sendEvent(name: "switch", value : "on", isStateChange : true)
	} else {
		sendEvent(name: "switch", value : "off", isStateChange : true)
	}

	interfaces.rawSocket.close()
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
def encrypt (def plainText, def secret) {
	//log.debug ("Encrypting - ${plainText}","trace")
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

	log.debug decodedBytes.size()
	//whammy!
	return new String(cipher.doFinal(decodedBytes), "UTF-8")
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
		//log.debug Long.toHexString(~crc & 0xffffffff) + " - " + b
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

	log.debug "Sending on command"
	json_payload = groovy.json.JsonOutput.toJson(json.toString())
	json_payload = json_payload.replaceAll("\\\\", "")
	json_payload = json_payload.replaceFirst("\"", "")
	json_payload = json_payload[0..-2]

	log.debug json_payload

	if (command == "set") {
		encrypted_payload = encrypt(json_payload, settings.localKey)

		log.debug encrypted_payload

		preMd5String = "data=" + encrypted_payload + "||lpv=" + "3.1" + "||" + settings.localKey

		log.debug "preMd5String" + preMd5String

		hexdigest = generateMD5(preMd5String)

		hexdig = new String(hexdigest[8..-9].getBytes("UTF-8"), "ISO-8859-1")

		json_payload = "3.1" + hexdig + encrypted_payload
	}

	log.debug json_payload

	ByteArrayOutputStream output = new ByteArrayOutputStream()

	output.write(json_payload.getBytes())
	output.write(hubitat.helper.HexUtils.hexStringToByteArray(payload()["device"]["suffix"]))

	byte[] bff = output.toByteArray()

	//log.debug hubitat.helper.HexUtils.byteArrayToHexString(bff)

	postfix_payload = bff

	//log.debug "Postfix payload: " + postfix_payload

	postfix_payload_hex_len = postfix_payload.size()

	log.debug postfix_payload_hex_len

	log.debug "Prefix: " + hubitat.helper.HexUtils.byteArrayToHexString(hubitat.helper.HexUtils.hexStringToByteArray(payload()["device"]["prefix"]))

	output = new ByteArrayOutputStream();

	output.write(hubitat.helper.HexUtils.hexStringToByteArray(payload()["device"]["prefix"]))
	output.write(hubitat.helper.HexUtils.hexStringToByteArray(payload()["device"][command]["hexByte"]))
	output.write(hubitat.helper.HexUtils.hexStringToByteArray("000000"))
	output.write(postfix_payload_hex_len)
	output.write(postfix_payload)

	byte[] buf = output.toByteArray()

	crc32 = CRC32b(buf, buf.size()-8) & 0xffffffff
	log.debug buf.size()

	hex_crc = Long.toHexString(crc32)

	log.debug "HEX crc: $hex_crc"

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
	interfaces.rawSocket.sendMessage(msg)
}

def on() {

	sendEvent(name: "switch", value : "on", isStateChange : true)

	def buf = generate_payload("set", ["${settings.endpoint}":true])

	log.debug hubitat.helper.HexUtils.byteArrayToHexString(buf)

	//port 6668
	//def hubAction = new hubitat.device.HubAction(hubitat.helper.HexUtils.byteArrayToHexString(buf), hubitat.device.Protocol.RAW_LAN, [type: hubitat.device.HubAction.Type.LAN_TYPE_RAW, encoding: hubitat.device.HubAction.Encoding.HEX_STRING, destinationAddress: "$settings.ipaddress:6668", timeout: 1])
	//sendHubCommand(hubAction)

	sendHubCommand(new HubAction(hubitat.helper.HexUtils.byteArrayToHexString(buf), Protocol.RAW_LAN, [destinationAddress: "$settings.ipaddress:6668", encoding: HubAction.Encoding.HEX_STRING, timeout: 1]))

	// Check Status
	runIn(2, status)
}

def off() {

}

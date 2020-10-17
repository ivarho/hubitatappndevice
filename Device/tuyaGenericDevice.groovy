/*
 * tuya Device
 *
 */
metadata {
	definition(name: "tuya Generic Device", namespace: "iholand", author: "iholand") {
		capability "Actuator"
		capability "Switch"
		capability "Sensor"
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
	// generate an Initial Vector using Random Number and no additional libraries....
	// because they don't exist here....these are not the SecureRandom numbers you are looking for.....
	def IVKey = Long.toUnsignedString(new Random().nextLong(), 16).toUpperCase()
	IvParameterSpec iv = new IvParameterSpec(IVKey.getBytes("UTF-8"))
	// initialize the encryption and get ready for that dirty dirty magic
	cipher.init(Cipher.ENCRYPT_MODE, key)
	// boom goes the dynamite
	def result = cipher.doFinal(plainText.getBytes("UTF-8")).encodeBase64().toString()
	// extract the Initial Vector and make it a string so we can pre-pend to the GoGoGate2
	// encryption string.  Someone way smarter than me did it so I am doing it too...
	// looking at you broadfoot..
	//def ivString = cipher.getIV()
	//ivString = new String(ivString, "UTF-8")
	// prepend the IV to the Result and send it back....still needs to be URLEncoded etc......
	// but we now have a valid payload.  Serioulsy...this was hard.....Python is WAY easier
	// when it comes to some of this manipulation....but at least I didn't have to use a lambda.
	//return ivString+result
	return result
}

import java.security.MessageDigest

def generateMD5(String s){
	MessageDigest.getInstance("MD5").digest(s.bytes).encodeHex().toString()
}

def bin2hex(x) {
	result = ""

	for (y in x) {
		//result = result + y.encodeHex()
	}

	log.debug "Result: " + x.getBytes()
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

def generate_payload(command, data) {

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
		//json_data["t"] = (now.getTime()/1000).toInteger()
		json_data["t"] = "1602184793"
	}

	//json_data["dps"] = ["${settings.endpoint}" : true]
	json_data["dps"] = data

	json json_data

	log.debug "Sending on command"
	json_payload = groovy.json.JsonOutput.toJson(json.toString())
	json_payload = json_payload.replaceAll("\\\\", "")
	json_payload = json_payload.replaceFirst("\"", "")
	json_payload = json_payload[0..-2]

	log.debug json_payload

	encrypted_payload = encrypt(json_payload, settings.localKey)

	log.debug encrypted_payload

	preMd5String = "data=" + encrypted_payload + "||lpv=" + "3.1" + "||" + settings.localKey

	log.debug "preMd5String" + preMd5String

	hexdigest = generateMD5(preMd5String)

	hexdig = new String(hexdigest[8..-9].getBytes("UTF-8"), "ISO-8859-1")

	json_payload = "3.1" + hexdig + encrypted_payload

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
	output.write(hubitat.helper.HexUtils.hexStringToByteArray(payload()["device"]["set"]["hexByte"]))
	output.write(hubitat.helper.HexUtils.hexStringToByteArray("000000"))
	output.write(postfix_payload_hex_len)
	output.write(postfix_payload)

	byte[] buf = output.toByteArray();

	log.debug hubitat.helper.HexUtils.byteArrayToHexString(buf)

	crc32 = CRC32b(buf, buf.size()-8) & 0xffffffff
	log.debug buf.size()

	hex_crc = Long.toHexString(crc32)
	crc_bytes = hubitat.helper.HexUtils.hexStringToByteArray(hex_crc)

	buf[buf.size()-8] = crc_bytes[0]
	buf[buf.size()-7] = crc_bytes[1]
	buf[buf.size()-6] = crc_bytes[2]
	buf[buf.size()-5] = crc_bytes[3]

	return buf
}


def on() {

	def buf = generate_payload("set", ["${settings.endpoint}":true])

	log.debug hubitat.helper.HexUtils.byteArrayToHexString(buf)

	//port 6668
	def hubAction = new hubitat.device.HubAction(hubitat.helper.HexUtils.byteArrayToHexString(buf), hubitat.device.Protocol.LAN, [type: hubitat.device.HubAction.Type.LAN_TYPE_RAW, encoding: hubitat.device.HubAction.Encoding.HEX_STRING, destinationAddress: "$settings.ipaddress:6668", ignoreResponse: false, timeout: 1])
	sendHubCommand(hubAction)

	def response = hubAction.getAction()
	def byteArray_response = hubitat.helper.HexUtils.hexStringToByteArray(response)

	str_response = new String(byteArray_response, "UTF-8")

	log.debug "Recived: ${str_response}"
}

def off() {

}

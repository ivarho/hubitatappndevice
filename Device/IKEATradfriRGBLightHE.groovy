/**
 *  Copyright 2017 Pedro Garcia
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 *  IKEA Trådfri RGB Bulb
 *
 *  Color management is not trivial. IKEA bulbs are using CIE XY color scheme instead of Hue/Saturation. Also the
 *  bulbs do not seem to be able to output "light cyan" color. Maybe it is my fault for not having being able to
 *  identify the correct color scheme (currently assuming sRGB with gamma correction), but cannot either with the
 *  IKEA remote pairing...
 *
 *  This handler is written so that it reports any change in the bulb state (on/off, brightness, color) as an event
 *  immediately to be processed by other apps.
 *
 *  Author: Pedro Garcia
 *  Date: 2017-09-17
 *  Version: 1.1
 *
 *  TO DO:
 *    - Color presets
 *    - Enable debug logging on app settings
 *    - Remove custom code when ST correctly parses both all color attributes and multiple reporting in one message
 *    - Color event should send string with hue and saturation values instead of hex, as per API reference, but when
 *      doing so, the color picker does not get updated
 **/

import com.hubitat.zigbee.DataType
import hubitat.helper.ColorUtils

metadata {
	definition (name: "IKEA Tradfri RGB Light HE", namespace: "puzzle-star", author: "Pedro Garcia") {
		// Hard Capabilities
		capability "Light"
		capability "Switch"
		capability "Switch Level"
		capability "Color Control"

		// Soft Capabilities
		capability "Actuator"
		capability "Configuration"
		capability "Refresh"
		capability "Polling"
		capability "Health Check"

		// Capability Attributes
		attribute "switch", "enum", ["on", "off"]
		attribute "level", "number"
		attribute "hue", "number"
		attribute "saturation", "number"
		attribute "color", "string"

		// Custom Attributes
		attribute "colorName", "string"
		attribute "red", "number"
		attribute "green", "number"
		attribute "blue", "number"

		// Custom Commands
		command "setColorByName", [[name:"Red", type: "ENUM", description: "color preset", constraints: ["Red", "Orange", "Yellow", "Green", "Cyan", "Indigo", "Blue", "Fuchsia", "Bright Pink", "White", "Warm white"]]]
		command "setWhite"
		command "nextColor"
		command "setRed", [[name:"Red", type: "NUMBER", description: "(0 to 100)"]]
		command "onRed"
		command "offRed"
		command "setGreen", [[name:"Green", type: "NUMBER", description: "(0 to 100)"]]
		command "onGreen"
		command "offGreen"
		command "setBlue", [[name:"Blue", type: "NUMBER", description: "(0 to 100)"]]
		command "onBlue"
		command "offBlue"

		// Trådfri RGB bulb
		fingerprint profileId: "0104", inClusters: "0000, 0003, 0004, 0005, 0006, 0008, 0300, 0B05, 1000", outClusters: "0005, 0019, 0020, 1000", manufacturer: "IKEA of Sweden",	model: "TRADFRI bulb E27 CWS opal 600lm", deviceJoinName: "TRADFRI bulb E27 CWS opal 600lm"
	}

	preferences {
		input name: "debugEnabled", type: "bool", title: "Enable debug logging", defaultValue: false, displayDuringSetup: false, required: false
	}
}

def getColorNames() {
	[
	[ name: "Red",         color: "#FF0000", wheel: true ],
	[ name: "Orange",      color: "#FF7F00", wheel: true ],
	[ name: "Yellow",      color: "#FFFF00", wheel: true ],
	[ name: "Green",       color: "#00FF00", wheel: true ],
	[ name: "Cyan",        color: "#00FFFF", wheel: false ], // Not in Tradfri...
	[ name: "Blue",	       color: "#0000FF", wheel: true ],
	[ name: "Indigo",      color: "#9300FF", wheel: true ],
	[ name: "Fuchsia",     color: "#FF00FF", wheel: true ],
	[ name: "Bright Pink", color: "#FF007F", wheel: true ],
	[ name: "White",       color: "#FFFFFF", wheel: false ],
	[ name: "Warm white",  color: "#FFAE54", wheel: false]
	]
}

def logDebug(msg) {
	// log.debug msg
}

def logTrace(msg) {
	// log.trace msg
}

def parseHex4le(hex) {
	Integer.parseInt(hex.substring(2, 4) + hex.substring(0, 2), 16)
}

def parseColorAttribute(id, value) {
	def parsed = false

	if(id == 0x03) {
		// currentColorX
		value = parseHex4le(value)
		logTrace "Parsed ColorX: $value"
		value /= 65536
		parsed = true
		state.colorXReported = true;
		state.colorChanged |= value != colorX
		state.colorX = value
	}
	else if(id == 0x04) {
		// currentColorY
		value = parseHex4le(value)
		logTrace "Parsed ColorY: $value"
		value /= 65536
		parsed = true
		state.colorYReported = true;
		state.colorChanged |= value != colorY
		state.colorY = value
	}
	else {
		logDebug "Not parsing Color cluster attribute $id: $value"
	}

	parsed
}

def parseAttributeList(cluster, list) {
	logTrace "Cluster: $cluster, AttrList: $list"
	def parsed = true

	while(list.length()) {
	def attrId = parseHex4le(list.substring(0, 4))
	def attrType = Integer.parseInt(list.substring(4, 6), 16)
	def attrShift = 0

	if(!attrType) {
		attrType = Integer.parseInt(list.substring(6, 8), 16)
		attrShift = 1
	}

	// if(DataType.isVariableLength(attrType)) {
	//	 logDebug "Not parsing variable length attribute: $list"
	//	 parsed = false
	//	 break
	// }

	def attrLen = DataType.getLength(attrType)
	def attrValue = list.substring(6 + 2*attrShift, 6 + 2*(attrShift+attrLen))

	logTrace "Attr - Id: $attrId($attrLen), Type: $attrType, Value: $attrValue"

	if(cluster == 0x300) {
		parsed &= parseColorAttribute(attrId, attrValue)
	}
	else {
		logDebug "Not parsing cluster $cluster attribute: $list"
		parsed = false;
	}

	list = list.substring(6 + 2*(attrShift+attrLen))
	}

	parsed
}

def parse(String description) {
	logDebug "Parsing : $description"

	if (description.startsWith("catchall")) return

	def events = []
	def event = zigbee.getEvent(description)
	def parsed

	if(event) {
		parsed = true
		events += event
	}
	else {
		def cluster = zigbee.parse(description)

		if(cluster) {
			logTrace "Cluster - $cluster"

			if (cluster.clusterId == 0x0006 && cluster.command == 0x07) {
				if (cluster.data[0] == 0x00) {
					events += createEvent(name: "checkInterval", value: 60 * 12, displayed: false, data: [protocol: "zigbee", hubHardwareId: device.hub.hardwareID])
					parsed = true
				}
				else {
					log.warn "ON/OFF REPORTING CONFIG FAILED- error code:${cluster.data[0]}"
					parsed = true
				}
			}
		}

		if(!parsed) {
			def map = description

			if (description instanceof String)	{
			map = stringToMap(description)
			}

			logTrace "Map - $map"
			def raw = map["read attr - raw"]

			if(raw) {
				def clusterId = Integer.parseInt(Integer.toString(map.cluster), 16)
				def attrList = raw.substring(12)

				parsed = parseAttributeList(clusterId, attrList)

				if(state.colorChanged || (state.colorXReported && state.colorYReported)) {
					state.colorChanged = false;
					state.colorXReported = false;
					state.colorYReported = false;
					logTrace "Color Change: xy ($state.colorX, $state.colorY)"
					def rgb = colorXy2Rgb(state.colorX, state.colorY)
					logTrace "Color Change: RGB ($rgb.red, $rgb.green, $rgb.blue)"
					events += updateColor(rgb)
				}
			}
		}
	}

	if(!parsed) {
	log.warn "DID NOT PARSE MESSAGE for description : $description"
	}

	for(ev in events) {
		logDebug "Event - $ev.name: $ev.value"
		sendEvent(ev)
	}
}

def updateColor(rgb) {
	logTrace "updateColor: RGB ($rgb.red, $rgb.green, $rgb.blue)"
	def events = []

	def hsv = colorRgb2Hsv(rgb.red, rgb.green, rgb.blue)
	hsv.hue = Math.round(hsv.hue * 100).intValue()
	hsv.saturation = Math.round(hsv.saturation * 100).intValue()
	hsv.level = Math.round(hsv.level * 100).intValue()
	logTrace "updateColor: RGB ($hsv.hue, $hsv.saturation, $hsv.level)"

	def colorMatch = getNearestMatch(rgb, false)
	logTrace "updateColor: $colorMatch"

	rgb.red = Math.round(rgb.red * 255).intValue()
	rgb.green = Math.round(rgb.green * 255).intValue()
	rgb.blue = Math.round(rgb.blue * 255).intValue()
	logTrace "updateColor: RGB ($rgb.red, $rgb.green, $rgb.blue)"

	def color = ColorUtils.rgbToHEX([rgb.red, rgb.green, rgb.blue])
	logTrace "updateColor: $color"

	events += createEvent(name: "color", value: color, data: [ hue: hsv.hue, saturation: hsv.saturation, red: rgb.red, green: rgb.green, blue: rgb.blue, hex: color])
	events += createEvent(name: "hue", value: hsv.hue)
	events += createEvent(name: "saturation", value: hsv.saturation)
	events += createEvent(name: "red", value: Math.round(rgb.red * 100/255).intValue())
	events += createEvent(name: "green", value: Math.round(rgb.green * 100/255).intValue())
	events += createEvent(name: "blue", value: Math.round(rgb.blue * 100/255).intValue())

	def colorName = colorMatch.name
	logTrace "colorCheck: $colorMatch.color - $color"
	if(colorMatch.color != color) colorName += "\n[$color]"

	logTrace "colorName: $colorName"

	events += createEvent(name: "colorName", value: colorName)

	events
}

def off() {
	zigbee.off()
}

def on() {
	zigbee.on()
}

def setLevel(value) {
	zigbee.setLevel(value) + zigbee.on()
}

def setColor(red, green, blue, level=null) {
	logDebug "setColor: RGB ($red, $green, $blue)"

	if (level == null) {
		level = device.currentValue("level")
	}

	def colorName = ColorUtils.rgbToHEX([Math.round(red*255).intValue(), Math.round(green*255).intValue(), Math.round(blue*255).intValue()])
	setColorName("Setting Color\n[$colorName]");

	def xy = colorRgb2Xy(red, green, blue);

	logTrace "setColor: xy ($xy.x, $xy.y)"

	def intX = Math.round(xy.x*65536).intValue() // 0..65279
	def intY = Math.round(xy.y*65536).intValue() // 0..65279

	logTrace "setColor: xy ($intX, $intY)"

	// swap bytes so that we send numbers in little-endian notation
	def intXSwapped = ((intX >> 8) & 0xff) | ((intX & 0xff) << 8);
	def intYSwapped = ((intY >> 8) & 0xff) | ((intY & 0xff) << 8);

	def strX = zigbee.convertToHexString(intXSwapped, 2);
	def strY = zigbee.convertToHexString(intYSwapped, 2);

	zigbee.command(0x0300, 0x07, strX, strY, "0a00") + zigbee.setLevel(level) + zigbee.on()
}

def setColor(Map colorMap) {

	logDebug "setColor: $colorMap"
	log.debug "Set Color: $colorMap"

	def rgb

	def level = null

	if(colorMap.containsKey("red") && colorMap.containsKey("green") && colorMap.containsKey("blue")) {
		rgb = [ red : colorMap.red.intValue() / 255, green: colorMap.green.intValue() / 255, blue: colorMap.blue.intValue() / 255 ]
	}
	else if(colorMap.containsKey("hue") && colorMap.containsKey("saturation")) {
		rgb = colorHsv2Rgb(colorMap.hue / 100, colorMap.saturation / 100)
		if (colorMap.containsKey("level")) {
			level = colorMap.level
		}
	}
	else {
		log.warn "Unable to set color $colorMap"
	}

	logTrace "setColor: RGB ($red, $green, $blue)"

	setColor(rgb.red, rgb.green, rgb.blue, level)
}

def setHue(hue) {
	logDebug "setHue: $hue"
	setColor([ hue: hue, saturation: device.currentValue("saturation") ])
}

def setSaturation(saturation) {
	logDebug "setSaturation: $saturation"
	setColor([ hue: device.currentValue("hue"), saturation: saturation ])
}

def setColorName(name){
	logDebug "Color Name: $name"
	sendEvent(name: "colorName", value: name)
}

def setColorByName(name){
	def color
	for (c in getColorNames()) {
		if (c.name.equals(name)) {
			color = c
			break;
		}
	}

	if (color) {
		rgb = ColorUtils.hexToRGB(color.color)
		setColor(rgb[0]/255, rgb[1]/255, rgb[2]/255)
	}
}

def setRed(level) {
	def rgb = getCurrentRGB()
	setColor(level/100, rgb.green, rgb.blue)
}

def setGreen(level) {
	def rgb = getCurrentRGB()
	setColor(rgb.red, level/100, rgb.blue)
}

def setBlue(level) {
	def rgb = getCurrentRGB()
	setColor(rgb.red, rgb.green, level/100)
}

def onRed() {
	def rgb = getCurrentRGB()
	if(state.lastRed) setColor(state.lastRed, rgb.green, rgb.blue)
}

def onGreen() {
	def rgb = getCurrentRGB()
	if(state.lastGreen) setColor(rgb.red, state.lastGreen, rgb.blue)
}

def onBlue() {
	def rgb = getCurrentRGB()
	if(state.lastBlue) setColor(rgb.red, rgb.green, state.lastBlue)
}

def offRed() {
	def rgb = getCurrentRGB()
	if(!rgb.green && !rgb.blue) return
	state.lastRed = rgb.red
	setColor(0, rgb.green, rgb.blue)
}

def offGreen() {
	def rgb = getCurrentRGB()
	if(!rgb.red && !rgb.blue) return
	state.lastGreen = rgb.green
	setColor(rgb.red, 0, rgb.blue)
}

def offBlue() {
	def rgb = getCurrentRGB()
	if(!rgb.red && !rgb.green) return
	state.lastBlue = rgb.blue
	setColor(rgb.red, rgb.green, 0)
}

def getCurrentRGB() {
	def rgb = [ red: device.currentValue("red")/100, green: device.currentValue("green")/100, blue: device.currentValue("blue")/100 ]
	logDebug "Current RGB: $rgb"

	rgb
}

def getNearestMatch(rgb, wheelOnly) {
	def colors = getColorNames()
	def xy = colorRgb2Xy(rgb.red, rgb.green, rgb.blue)
	logTrace "Match: RGB($rgb) - xy($xy)"

	def matched = [index: -1, name: null, color: null, rgb: null, distance: 1]
	def index = -1;

	for(match in colors) {
		index++
		if(wheelOnly && !match.wheel) continue

		def color = ColorUtils.hexToRGB(match.color)
		def red = color[0] / 255
		def green = color[1] / 255
		def blue = color[2] / 255
		def xy2 = colorRgb2Xy(red, green, blue)
		def distance = Math.sqrt(Math.pow(xy.x-xy2.x, 2) + Math.pow(xy.y-xy2.y, 2)) / 1.4142136
		logTrace "Matching: $match.color xy($xy2) vs xy($xy) -> $distance"
		if(distance <= matched.distance) {
			matched.index = index
			matched.distance = distance
			matched.name = match.name
			matched.color = match.color
			matched.rgb = [red: red, green: green, blue: blue]
		}
	}

	logTrace "Match: $matched"

	matched
}

def setWhite() {
	setColor(1, 1, 1)
}

def nextColor() {
	def rgb = getCurrentRGB()
	logTrace "Current color: $rgb"

	def match = getNearestMatch(rgb, true)
	logDebug "Current color match: $match"

	def colors = getColorNames()
	def next = match.index

	while (++next != match.index) {
		if(next >= colors.size()) next = 0
		if(colors[next].wheel) break
	}

	def color = colors[next].color
	logDebug "Next color: $color"

	rgb = ColorUtils.hexToRGB(color)
	setColor(rgb[0]/255, rgb[1]/255, rgb[2]/255)
}

def ping() {
	return zigbee.onOffRefresh()
}

def colorControlRefresh() {
	def commands = []
	commands += zigbee.readAttribute(0x0300, 0x03) // currentColorX
	commands += zigbee.readAttribute(0x0300, 0x04) // currentColorY
	commands
}

def colorControlConfig(min, max, step) {
	def commands = []
	commands += zigbee.configureReporting(0x0300, 0x03, DataType.UINT16, min, max, step) // currentColorX
	commands += zigbee.configureReporting(0x0300, 0x04, DataType.UINT16, min, max, step) // currentColorY
	commands
}

def levelConfig(min, max, step) {
	zigbee.configureReporting(0x0008, 0x0000, DataType.UINT8, min, max, step)
}

def refresh() {
	state.colorChanged = false
	state.colorXReported = false
	state.colorYReported = false
	zigbee.onOffRefresh() + zigbee.levelRefresh() + colorControlRefresh() + zigbee.onOffConfig(0, 300) + levelConfig(0, 300, 1) + colorControlConfig(0, 300, 1)
}

def poll() {
	refresh()
}

def configure() {
	sendEvent(name: "checkInterval", value: 2 * 10 * 60 + 1 * 60, displayed: false, data: [protocol: "zigbee", hubHardwareId: device.hub.hardwareID])
	refresh()
}

def installed() {
	if ((device.currentState("level")?.value == null) || (device.currentState("level")?.value == 0)) {
		sendEvent(name: "level", value: 100)
	}
}

// Color Management functions

def min(first, ... rest) {
	def min = first;
	for(next in rest) {
		if(next < min) min = next
	}

	min
}

def max(first, ... rest) {
	def max = first;
	for(next in rest) {
		if(next > max) max = next
	}

	max
}

def colorGammaAdjust(component) {
	return (component > 0.04045) ? Math.pow((component + 0.055) / (1.0 + 0.055), 2.4) : (component / 12.92)
}

def colorGammaRevert(component) {
	return (component <= 0.0031308) ? 12.92 * component : (1.0 + 0.055) * Math.pow(component, (1.0 / 2.4)) - 0.055;
}

def colorXy2Rgb(x, y) {

	logTrace "< Color xy: ($x, $y)"

	def Y = 1;
	def X = (Y / y) * x;
	def Z = (Y / y) * (1.0 - x - y);

	logTrace "< Color XYZ: ($X, $Y, $Z)"

	// sRGB, Reference White D65
	def M = [
	[	3.2410032, -1.5373990, -0.4986159 ],
	[ -0.9692243,	1.8759300,	0.0415542 ],
	[	0.0556394, -0.2040112,	1.0571490 ]
	]

	def r = X * M[0][0] + Y * M[0][1] + Z * M[0][2]
	def g = X * M[1][0] + Y * M[1][1] + Z * M[1][2]
	def b = X * M[2][0] + Y * M[2][1] + Z * M[2][2]

	def max = max(r, g, b)
	r = colorGammaRevert(r / max)
	g = colorGammaRevert(g / max)
	b = colorGammaRevert(b / max)

	logTrace "< Color RGB: ($r, $g, $b)"

	[red: r, green: g, blue: b]
}

def colorRgb2Xy(r, g, b) {

	logTrace "> Color RGB: ($r, $g, $b)"

	r = colorGammaAdjust(r)
	g = colorGammaAdjust(g)
	b = colorGammaAdjust(b)

	// sRGB, Reference White D65
	// D65	0.31271 0.32902
	//	R	0.64000 0.33000
	//	G	0.30000 0.60000
	//	B	0.15000 0.06000
	def M = [
	[	0.4123866,	0.3575915,	0.1804505 ],
	[	0.2126368,	0.7151830,	0.0721802 ],
	[	0.0193306,	0.1191972,	0.9503726 ]
	]

	def X = r * M[0][0] + g * M[0][1] + b * M[0][2]
	def Y = r * M[1][0] + g * M[1][1] + b * M[1][2]
	def Z = r * M[2][0] + g * M[2][1] + b * M[2][2]

	logTrace "> Color XYZ: ($X, $Y, $Z)"

	def x = X / (X + Y + Z)
	def y = Y / (X + Y + Z)

	logTrace "> Color xy: ($x, $y)"

	[x: x, y: y]
}

def colorHsv2Rgb(h, s) {
	logTrace "< Color HSV: ($h, $s, 1)"

	def r
	def g
	def b

	if (s == 0) {
		r = 1
		g = 1
		b = 1
	}
	else {
		def region = (6 * h).intValue()
		def remainder = 6 * h - region

		def p = 1 - s
		def q = 1 - s * remainder
		def t = 1 - s * (1 - remainder)

		if(region == 0) {
			r = 1
			g = t
			b = p
		}
		else if(region == 1) {
			r = q
			g = 1
			b = p
		}
		else if(region == 2) {
			r = p
			g = 1
			b = t
		}
		else if(region == 3) {
			r = p
			g = q
			b = 1
		}
		else if(region == 4) {
			r = t
			g = p
			b = 1
		}
		else {
			r = 1
			g = p
			b = q
		}
	}

	logTrace "< Color RGB: ($r, $g, $b)"

	[red: r, green: g, blue: b]
}

def colorRgb2Hsv(r, g, b)
{
	logTrace "> Color RGB: ($r, $g, $b)"

	def min = min(r, g, b)
	def max = max(r, g, b)
	def delta = max - min

	def h
	def s
	def v = max

	if (delta == 0) {
		h = 0
		s = 0
	}
	else {
		s = delta / max
		if (r == max) h = ( g - b ) / delta		 // between yellow & magenta
		else if(g == max) h = 2 + ( b - r ) / delta // between cyan & yellow
		else h = 4 + ( r - g ) / delta				// between magenta & cyan
			h /= 6

		if(h < 0) h += 1
	}

	logTrace "> Color HSV: ($h, $s, $v)"

	return [ hue: h, saturation: s, level: v ]
}

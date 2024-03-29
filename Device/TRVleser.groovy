/**
 *  TRV - leser
 *
 *  Copyright 2020-2023 Ivar Holand
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
 */
metadata {
	definition (name: "TRV - leser", namespace: "iholand", author: "Ivar Holand") {
		capability "Sensor"

		attribute "wastetype", "string"
		attribute "wastetype_raw", "string"

		command "updateWasteType"
	}
}

preferences {

	input "areaCode", "text", title: "Area code from trv.no: ", required: true, description: "<small>Open trv.no and go to Tømmeplan, enter your address. After the page with the plan opens, look at the URL/address field, the Area Code is the cryptic sting after .../plan/[id here]. E.g bd946946-a139-43b3-bb51-d125ff9af064</small>"
}

def updated(settings) {
	log.debug "Updated with settings: $settings"

	unschedule()

	updateWasteType()

	schedule("0 0 10 ? * MON", updateWasteType)
}

// parse events into attributes
def parse(description, data) {
	def page_content = description.getData()

	def wasteplan_content_start = page_content.indexOf("id=\"wasteplan-content")
	page_content = page_content[wasteplan_content_start..-1]

	def bins_start = page_content.indexOf("class=\"week")
	page_content = page_content[bins_start..bins_start+1000]

	def tbody_start = page_content.indexOf("<tbody>")
	page_content = page_content[tbody_start..-1]

	def pointer = page_content.indexOf("wastetype icon") + 15
	def end_pointer = page_content.indexOf("href") - 3
	wType = page_content[pointer..end_pointer]

	def img_src = ""

	//if week is Tommefri
	if (wType.startsWith("fri-uke ")) {
		img_http = "<i class=\"material-icons he-bin\" style=\"color: rgb(236,164,30); font-size: 52px;\"></i><br>Tømmefri"
		wType = "tømmefri"
	} else {
		//Find ICON link
		page_content = page_content[end_pointer..-1]
		def img_src_ptr = page_content.indexOf("img src") + 9
		def img_src_end_ptr = page_content.indexOf("style") - 3
		img_src = page_content[img_src_ptr..img_src_end_ptr]
		img_http = "<img src=\"${img_src}\" width=70 style=\"opacity:0.7;\"><br>" + wType.capitalize()
		//img_http = wType.capitalize()
	}

	sendEvent(name: "wastetype", value: "${img_http}", isStateChange: true)
	sendEvent(name: "wastetype_raw", value: wType.capitalize(), isStateChange: true)

	log.debug "Parsing '${wType}'"
}

def updateWasteType() {
	asynchttpGet(parse, [uri: "https://trv.no/plan/${areaCode}/"])
}

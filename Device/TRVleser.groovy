/**
 *  TRV - leser
 *
 *  Copyright 2020 Ivar Holand
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

		command "updateWasteType"
	}
}

preferences {

	input "areaCode", "text", title: "Area code from trv.no: ", required: true
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

	def bins_start = page_content.indexOf("class=\"bins")
	page_content = page_content[bins_start..bins_start+1000]

	def tbody_start = page_content.indexOf("<tbody>")
	page_content = page_content[tbody_start..-1]

	def pointer = page_content.indexOf("wastetype") + 10
	def end_pointer = page_content.indexOf("href") - 4
	wType = page_content[pointer..end_pointer]

	sendEvent(name: "wastetype", value: wType.capitalize(), isStateChange: true)

	log.debug "Parsing '${wType}'"
}

def updateWasteType() {
	asynchttpGet(parse, [uri: "https://trv.no/plan/${areaCode}/"])
}

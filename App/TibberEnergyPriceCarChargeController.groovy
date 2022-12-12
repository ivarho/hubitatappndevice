/**
 *  Tibber Energy Price Car Charge Controller
 *
 *  Copyright 2022 Ivar Holand
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
definition(
	name: "Tibber Energy Price Car Charge Controller",
	namespace: "iholand",
	author: "iholand",
	description: "Use price from Tibber to control car charger",
	category: "Convenience",
	iconUrl: "https://4.bp.blogspot.com/-bpyUPSewQnQ/U7g5bA2FTfI/AAAAAAAAB2o/rAKEELPR_mY/s1600/electric_pcb_color_scaled.png",
	iconX2Url: "https://4.bp.blogspot.com/-bpyUPSewQnQ/U7g5bA2FTfI/AAAAAAAAB2o/rAKEELPR_mY/s1600/electric_pcb_color_scaled.png",
	iconX3Url: "https://4.bp.blogspot.com/-bpyUPSewQnQ/U7g5bA2FTfI/AAAAAAAAB2o/rAKEELPR_mY/s1600/electric_pcb_color_scaled.png"
)

preferences {
	section ("Controls") {
		input "carChargerOutlet", "capability.switch", required: false, title: "Switch to control:"
	}

	/*section("Notifications") {
		input "sendPush", "bool", required: false, title: "Send Push Notification when status changes?"
	}*/

	section("Battey Preferences") {
		input "batterySize", "number", title: "Car battery size (kWh)", required: true, defaultValue: 24
		input "normalChargeAt", "number", title: "Normal battery precentage (%) at start of charge", required: true, defaultValue: 20
		input "chargeCurrent", "number", title: "Charge current (A)", required: true, defaultValue: 16
	}

	section("Other") {
		input "earliestTimeToStartCharge", "time", title: "Earliest start car charger", required: true
		input "fetchUpdatedPrices", "time", title: "When to get updated prices from Tibber", required: true
		input "readyByTime", "time", title: "When must charge be complete", required: true

		input (
			name: "tibber_apikey",
			type: "password",
			title: "API Key",
			description: "Enter the Tibber API key",
			required: true,
			displayDuringSetup: true
		)
	}
}
def installed() {
	log.debug "Installed with settings: ${settings}";
	addSnoozeButton();
	initialize();
	updated()
}
def updated() {
	log.debug "Updated with settings: ${settings}"
	unsubscribe()
	unschedule()
	initialize()

	getPrice()

	planLowestPrice()

	turnOffCarCharger()
}

def initialize() {
	schedule(fetchUpdatedPrices, planLowestPrice);
}

def verifySwitchOn()
{
	if (carChargerOutlet.currentValue('switch').contains('off')) {
		carChargerOutlet.on()
		runIn(5*60, verifySwitchOn)
	}
}

def verifySwitchOff()
{
	if (carChargerOutlet.currentValue('switch').contains('on')) {
		carChargerOutlet.off()
		runIn(5*60, verifySwitchOff)
	}
}

def turnOffCarCharger() {
	def overrideSwitch = getChildDevices()[0]

	if (overrideSwitch == null) {
		carChargerOutlet?.off()
	} else if (overrideSwitch?.currentValue('switch').contains('off')) {
		carChargerOutlet?.off()
		runIn(5*60, verifySwitchOff)
	}
}

def turnOnCarCharger() {
	def overrideSwitch = getChildDevices()[0]

	if (overrideSwitch == null) {
		carChargerOutlet?.on()
	} else if (overrideSwitch?.currentValue('switch').contains('off')) {
		carChargerOutlet?.on()
		runIn(5*60, verifySwitchOn)
		unschedule(turnOnCarCharger)
	}
}

// Car charge model
def energyConsumption (hour, chargeTime, power) {
	return (power - (power/chargeTime)*hour)/1000
}

def planLowestPrice () {

	if (getPrice() == false) {
		log.error "Failed to fetch prices!"

		runIn(15*60, planLowestPrice)

		return
	}

	def lowestEnergyCost = 10000
	def lowestEnergyCostHour = 0

	state.debugInfo = ""

	power = chargeCurrent * 240
	chargeTime = batterySize * ((100 - normalChargeAt)/100) / (power/1000)

	def totalCost = 0

	def calculateFromHour = timeToday(earliestTimeToStartCharge).hours
	def calculateToHour = timeToday(readyByTime).hours

	for (i = calculateFromHour; i < (24+calculateToHour-chargeTime); i++) {
		totalCost = 0

		for (c = 0; c < chargeTime; c++) {
			totalCost += state.eneryPrices[i+c] * energyConsumption(c, chargeTime, power)
		}

		totalCost = totalCost/100

		log.debug "Total cost for hour ${i % 24}: ${totalCost}"
		state.debugInfo += "Total cost for hour ${i % 24}: ${totalCost}\n"

		if (totalCost < lowestEnergyCost) {
			lowestEnergyCost = totalCost
			lowestEnergyCostHour = i
		}
	}

	log.debug "Lowest energy cost to charge car: ${lowestEnergyCost} charging starts at: ${lowestEnergyCostHour % 24}:00"

	unschedule(turnOnCarCharger)
	unschedule(turnOffCarCharger)

	schedule("0 0 ${lowestEnergyCostHour % 24} * * ?", turnOnCarCharger)
	schedule("0 0 8 * * ?", turnOffCarCharger)

	def ControllerSwitch = getChildDevices()[0]
	ControllerSwitch?.setChargerStart(lowestEnergyCostHour % 24);
}

def getPrice() {
	log.debug("getPrice")

	def prices = []

	def returnStatus = true

	if(tibber_apikey == null){
		log.error("API key is not set. Please set it in the settings.")
	} else {
		def params = [
			uri: "https://api.tibber.com/v1-beta/gql",
			headers: ["Content-Type": "application/json;charset=UTF-8" , "Authorization": "Bearer $tibber_apikey"],
			body: graphQLApiQuery()
		]
		try {
			httpPostJson(params) { resp ->
				if(resp.status == 200){
					def today = resp.data.data.viewer.homes[0].currentSubscription.priceInfo.today
					def tomorrow = resp.data.data.viewer.homes[0].currentSubscription.priceInfo.tomorrow

					def priceList = today

					log.debug(priceList)

					tomorrow.each{
						priceList << it
					}

					priceList.each {num ->
						prices << (num.total)*100;
					}
					log.debug(prices);
				} else {
					returnStatus = false
				}
			}
		} catch (e) {
			log.error "something went wrong: $e"
			returnStatus = false
		}
	}

	state.eneryPrices = prices

	return returnStatus
}

def graphQLApiQuery(){
	return '{"query": "{viewer {homes {currentSubscription {priceInfo { current {total currency} today{ total startsAt } tomorrow{ total startsAt }}}}}}", "variables": null, "operationName": null}';
}

def addSnoozeButton(){
	log.debug("Adding child device");
	def ranNum = new Random().nextInt(65000) + 1

	state.childId = "oca-sb-$ranNum"

	def chdevice = addChildDevice("iholand", "Tibber Outlet Controller Slave",  state.childId, null, [name: "Car charger override", componentName: "Car charger override", componentLabel: "Car charger override"]);
}

/**
 *  Tibber Outlet Controller
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
definition(
	name: "Tibber Outlet Controller",
	namespace: "iholand",
	author: "iholand",
	description: "Use price from Tibber to control outlet",
	category: "Convenience",
	iconUrl: "https://4.bp.blogspot.com/-bpyUPSewQnQ/U7g5bA2FTfI/AAAAAAAAB2o/rAKEELPR_mY/s1600/electric_pcb_color_scaled.png",
	iconX2Url: "https://4.bp.blogspot.com/-bpyUPSewQnQ/U7g5bA2FTfI/AAAAAAAAB2o/rAKEELPR_mY/s1600/electric_pcb_color_scaled.png",
	iconX3Url: "https://4.bp.blogspot.com/-bpyUPSewQnQ/U7g5bA2FTfI/AAAAAAAAB2o/rAKEELPR_mY/s1600/electric_pcb_color_scaled.png"

    command "getPrice"
)

preferences {
	section ("Controls") {
		input "switch1", "capability.switch", required: false, title: "Switch to control:"

		input "priceSensor", "capability.sensor", required: true, title: "Price Sensor:"
		input "mustRunConsecutive", "bool", title: "Must run consecutive:"

	}

	section("Notifications") {
		input "sendPush", "bool", required: false, title: "Send Push Notification when status changes?"
	}

	section("Other") {
		//input "resetDayCounterSch", "time", title: "Time of day to fetch price data:", required: true

		input "startChargeCycleSch", "time", title: "Earliest time to turn on outlet", required: true
		input "readyBySch", "time", title: "Time of day when minimum runtime must be complete", required: true

		input "minimumRunTime", "number", title: "Select the minimum runtime each day", required: true

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
	initialize();
	addSnoozeButton();
	planDailyTurnOnSlots();
}
def updated() {
	log.debug "Updated with settings: ${settings}"
	unsubscribe()
	initialize()
	planDailyTurnOnSlots();
	handleSwitch();

	//def children = getChildDevices()
	def ChargeController = getChildDevices()[0]

	ChargeController.off()

	//log.debug "device has ${children.size()} children"
	//children.each { child ->
	//s    log.debug "child ${children[0].displayName} has deviceNetworkId ${children[0].deviceNetworkId}"
	//}
}

def initialize() {
	schedule("0 3 * * * ?", handleSwitch)
	schedule(startChargeCycleSch, planDailyTurnOnSlots);
}

def handleSwitch()
{
	def dateTime = new Date(now())
	//def planUpdatedTime = timeToday(state.planUpdate)
	//def planUpdatedTime = timeToday("2018-09-30T16:02:50.096+0200")

	//def planUpdatedTime = new Date().parse("yyyy-MM-dd'T'HH:mm:ss.SSSZ", "2018-10-01T14:40:50.096+0200")
	def planUpdatedTime = new Date().parse("yyyy-MM-dd'T'HH:mm:ss.SSSZ", state.planUpdate)
	planUpdatedTime = planUpdatedTime.format("yyyy-MM-dd'T'HH:00:00.000Z", location.timeZone)
	planUpdatedTime = new Date().parse("yyyy-MM-dd'T'HH:mm:ss.SSSZ", planUpdatedTime)

	def planAgeHours = (Math.floor((dateTime.getTime() - planUpdatedTime.getTime())/1000/3600)).toInteger();

	def firstCharge = 0;
	for (firstCharge = 0; firstCharge < state.runArray.size(); firstCharge++) {
		if (state.runArray[firstCharge] != 0) {
			break;
		}
	}

	//log.debug((firstCharge + planUpdatedTime.format("HH", location.timeZone).toInteger()) % 24);

	def nextChargeStartsAt = (firstCharge + planUpdatedTime.format("HH", location.timeZone).toInteger()) % 24;

	def ControllerSwitch = getChildDevices()[0]
	ControllerSwitch.setChargerStart(nextChargeStartsAt);

	//chargeIndicator.setChargerStart(nextChargeStartsAt);

	log.debug("Plan updated: " + planUpdatedTime.format("yyyy-MM-dd'T'HH:mm:ss.SSSZ", location.timeZone));
	log.debug("Reset plan each day at: " + startChargeCycleSch);
	log.debug("Time now: " + dateTime.format("yyyy-MM-dd'T'HH:mm:ss.SSSZ", location.timeZone))
	log.debug("Hours since plan: " + planAgeHours);

	def postponeSwitch = getChildDevices()[0]

	if (postponeSwitch == null) {
		log.error("postponeSwitch not found!!")
	}

	log.debug("runArray: " + state.runArray);

	if (state.runArray[planAgeHours] != 0) {
		if (switch1.currentValue('switch').contains('off') && postponeSwitch.currentValue('switch').contains('off')) {
			turnOnCarCharger();
		}
	} else {
		if (switch1.currentValue('switch').contains('on') && postponeSwitch.currentValue('switch').contains('off')) {
			turnOffCarCharger();
		}
	}
}

def turnOffCarCharger() {
	def dateTime = new Date()
	def sensorStateChangedDate = dateTime.format("HH:mm", location.timeZone)

	def priceValue = priceSensor.currentValue("price");
	def priceMinDay = priceSensor.currentValue("priceMinDay");

	switch1.off()

	if (sendPush) {
		sendPush("${sensorStateChangedDate} Elbillader av, strømpris: ${priceValue}, laveste pris: ${priceMinDay}")
	}
}


def turnOnCarCharger() {
	def dateTime = new Date()
	def sensorStateChangedDate = dateTime.format("HH:mm", location.timeZone)

	def priceValue = priceSensor.currentValue("price");
	def priceMinDay = priceSensor.currentValue("priceMinDay");

	if (switch1.currentValue('switch').contains('on')) {
		return;
	}

	switch1.on()

	if (sendPush) {
		sendPush("${sensorStateChangedDate} Elbillader på, strømpris: ${priceValue}, laveste pris: ${priceMinDay}")
	}
}

def resetDayCounter()
{
	state.dayRunTime = 0;
	state.schPointer = 0;

	planDailyTurnOnSlots();
}

def planDailyTurnOnSlots()
{
	def minRuntime = minimumRunTime;

	def priceArray = getPrice();

	log.debug("priceArray: " + priceArray);

	def currentArrayIndex = new Date(now()).format("HH", location.timeZone).toInteger();

	def startTime = currentArrayIndex;
	def endTime = timeToday(readyBySch).format("HH", location.timeZone).toInteger() + 24;

	if (mustRunConsecutive) {
		planTurnOnConsecutive(priceArray, currentArrayIndex, minRuntime, startTime, endTime);
	} else {
		planTurnOnLowest(priceArray, currentArrayIndex, minRuntime, startTime, endTime);
	}

	//state.runArray = runArray;
	def timeDate = new Date(now());
	state.planUpdate = timeDate.format("yyyy-MM-dd'T'HH:mm:ss.SSSZ", location.timeZone)

	log.debug("runArray: " + state.runArray);
}

def planTurnOnLowest(priceArray, currentArrayIndex, minRuntime, startTime, endTime)
{
	def runArray = [];


	for (int i = currentArrayIndex; i < priceArray.size(); i++)
	{
		runArray << 0;
	}

	def localMin = 1000;
	def localMin_index = 0;

	while (minRuntime > 0)
	{
		for (int i = startTime; i < endTime; i++) {
			if (priceArray[i] < localMin) {
				localMin = priceArray[i];
				localMin_index = i;
			}
		}
		runArray[localMin_index - currentArrayIndex] = localMin;
		priceArray[localMin_index] = 1000;
		localMin = 1000;
		minRuntime--;

	}

	state.runArray = runArray;
}

def planTurnOnConsecutive(priceArray, currentArrayIndex, minRuntime, startTime, endTime) {
	def lowestPrice = [];
	def runArray = [];

	def currentIndex = 0;

	for (int x = 0; x < priceArray.size() - minRuntime + 1; x++) {
		def total = 0;
		for (int i = currentIndex; i < currentIndex + minRuntime; i++) {
			total = total + priceArray[i];
		}

		lowestPrice << total;
		currentIndex = currentIndex + 1;
	}

	log.debug(lowestPrice);

	def localMin = 1000;
	def localMin_index = 0;

	for (int i = startTime; i < endTime; i++) {
		if (lowestPrice[i] < localMin) {
			localMin = lowestPrice[i];
			localMin_index = i;
		}
	}

	for (int i = currentArrayIndex; i < priceArray.size(); i++) {

		if ((i >= localMin_index) && (i < localMin_index + minRuntime)) {
			runArray << priceArray[i]
		} else {
			runArray << 0
		}
	}
	log.debug("runArray v2: " + runArray)

	state.runArray = runArray;
}

def getPrice() {
	log.debug("getPrice")

	def prices = [];

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

				}
			}
		} catch (e) {
			log.debug "something went wrong: $e"
		}
	}

	return prices;
}

def graphQLApiQuery(){
	return '{"query": "{viewer {homes {currentSubscription {priceInfo { current {total currency} today{ total startsAt } tomorrow{ total startsAt }}}}}}", "variables": null, "operationName": null}';
}

def addSnoozeButton(){
	log.debug("Adding child device");
	def ranNum = new Random().nextInt(65000) + 1

	state.childId = "oca-sb-$ranNum"

	//addChildDevice(String namespace, String typeName, String deviceNetworkId, hubId, Map properties))
	def chdevice = addChildDevice("iholand", "Tibber Outlet Controller Slave",  state.childId, null, [name: "Tibber Outlet Controller Slave", componentName: "Tibber Outlet Controller Slave", componentLabel: "Tibber Outlet Controller Slave"]);

	//state.snoozeSwitch = chdevice;
}

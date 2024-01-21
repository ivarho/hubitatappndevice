/**
 *  Tibber Energy Price Switch Controller
 *
 *  Copyright 2024 Ivar Holand
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
	name: "Tibber Energy Price Switch Controller",
	namespace: "iholand",
	author: "iholand",
	description: "Use price from Tibber to control a generic switch",
	category: "Convenience",
)

preferences {
	section ("<hr><h2>Controls</h2>") {
		input "controlledSwitch", "capability.switch", required: false, title: "Switch to control:"
	}

	section("<hr><h2>Notifications</h2>") {
		input "notifier", "capability.notification", required: false, title: "Select notification device"
	}

	section("<hr><h2>Timeperiod 1</h2>") {
		input "runTime1", "number", title: "How long within the period do the device need to be on", required: true, defaultValue: 2
		input "earliest1", "time", title: "Earliest turn on switch", required: true
		input "latest1", "time", title: "When must the run time end", required: true
		input "keepOnAfter1", "bool", title: "Keep the switch on after latest schedule<br><small>Nice for hot water tanks etc.</small>"
	}

	section("<hr><h2>Timeperiod 2</h2>") {
		input "useTimeSlot2", "bool", title: "Use timeslot 2"
		input "runTime2", "number", title: "How long within the period do the device need to be on", required: false, defaultValue: 2
		input "earliest2", "time", title: "Earliest turn on switch", required: false
		input "latest2", "time", title: "When must the run time end", required: false
		input "keepOnAfter2", "bool", title: "Keep the switch on after latest schedule<br><small>Nice for hot water tanks etc.</small>"
	}

	section("<hr><h2>Fail safe</h2>") {
		input "failSafeStartTime", "time", title: "In case of failure, when should the switch turn on", required: true
		input "failSafeStopTime", "time", title: "In case of failure, when should the switch turn off", required: true
	}

	section("<hr><h2>Tibber</h2>") {

		input "fetchUpdatedPrices", "time", title: "When to get updated prices from Tibber", required: true

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
	log.debug "Installed with settings: ${settings}"
	addOverrideControl()
	initialize()
	updated()
}
def updated() {
	log.debug "Updated with settings: ${settings}"

	state.errorCount = 0

	unsubscribe()
	unschedule()
	initialize()

	//turnOffSwitch()

	planLowestPrice()
}

def initialize() {
	schedule(fetchUpdatedPrices, planLowestPrice)
}

def verifySwitchOn()
{
	if (controlledSwitch.currentValue('switch').contains('off')) {
		controlledSwitch.on()
		runIn(5*60, verifySwitchOn)
	}
}

def verifySwitchOff()
{
	if (controlledSwitch.currentValue('switch').contains('on')) {
		controlledSwitch.off()
		runIn(5*60, verifySwitchOff)
	}
}

def turnOffSwitch() {
	def overrideSwitch = getChildDevices()[0]

	unschedule(verifySwitchOff)
	unschedule(verifySwitchOn)

	log.debug "This is the current value: " + overrideSwitch.currentValue('switch')

	if (overrideSwitch?.currentValue('switch') == null) {
		controlledSwitch.off()
		runIn(5*60, verifySwitchOff)
	} else if (overrideSwitch.currentValue('switch').contains('off')) {
		controlledSwitch.off()
		runIn(5*60, verifySwitchOff)
	}

	unschedule(turnOffSwitch)

	def ControllerSwitch = getChildDevices()[0]
	ControllerSwitch?.setChargerStart("--:-- <small>(awaiting plan)</small>")
}

def turnOnSwitch() {
	def overrideSwitch = getChildDevices()[0]

	unschedule(verifySwitchOff)
	unschedule(verifySwitchOn)

	if (overrideSwitch?.currentValue('switch') == null) {
		controlledSwitch.on()
		runIn(5*60, verifySwitchOn)
	} else if (overrideSwitch.currentValue('switch').contains('off')) {
		controlledSwitch.on()
		runIn(5*60, verifySwitchOn)
	}

	unschedule(turnOnSwitch)
}

// Car charge model
def energyConsumption (hour, chargeTime, power) {
	return (power - (power/chargeTime)*hour)/1000
}

List getSchedule(earliest, latest, Long runTime, List priceInformation, keepOnAtEnd=false)
{
	if (earliest instanceof String) {
		 earliest = Date.parse("yyyy-MM-DD'T'HH:mm:ss.SSSX", earliest)
	}

	if (latest instanceof String) {
		latest = Date.parse("yyyy-MM-DD'T'HH:mm:ss.SSSX", latest)
	}

	if (endTurnOffTime instanceof String) {
		endTurnOffTime = Date.parse("yyyy-MM-DD'T'HH:mm:ss.SSSX", endTurnOffTime)
	}

	getSchedule(earliest, latest, runTime, priceInformation, keepOnAtEnd)
}

import static java.util.Calendar.*

List getSchedule(Date earliest, Date latest, Long runTime, List _priceInformation, keepOnAtEnd=false)
{
	Date now = new Date()
	List priceInformation = _priceInformation.clone()

	log.debug "****************** BUILD SCHEDULE ***************"

	use (groovy.time.TimeCategory) {
		// Replace date info with todays date
		earliest = earliest.copyWith(date: now[DATE])
		latest = latest.copyWith(date: now[DATE])

		if (latest < earliest) {
			//println "Yes, latest is before earliest, we cross midnight"
			latest += 24.hour
		}

		if (now < earliest) {
			// we are before the range
			//println "before range: earliest start is the earliest start, latest start is the latest start"
		} else if (now >= earliest && now < latest) {
			// we are inside the range
			//println "inside range: earliest start is now, latest start is latest start"
			earliest.set(hourOfDay: now[HOUR_OF_DAY], minute: 0, second: 0)
		} else if (now >= latest) {
			// we are after the range
			//println "after range: earliest start is earliest + 24 hour, latest start is earliest + 24 hours"
			earliest = earliest + 24.hour
			latest = latest + 24.hour
		}

	}

	//log.debug "This is the tibber map: " + state.tibberMap

	//Date earliestTimeToStart = Date.parse("yyyy-MM-DD'T'HH:mm:ss.SSSX", earliestTimeToStartCharge)
	//Date readyBy = Date.parse("yyyy-MM-DD'T'HH:mm:ss.SSSX", readyByTime)

	// Convert String dates to actual dates to make it easier to work with
	priceInformation.each({it->
		Date startsAT

		if (it.startsAt instanceof String) {
			startsAt = Date.parse("yyyy-MM-DD'T'HH:mm:ss.SSSX", it.startsAt)
			it.startsAt = startsAt
		} else {
			startsAt = it.startsAt
		}

		Date endsAt

		use(groovy.time.TimeCategory) {
			endsAt = startsAt + 1.hour
		}

		if (it.endsAt != 'skip') {
			it.endsAt = endsAt
		}
	})

	//println "Earliest to start: " + earliest
	//println "Latest to end: " + latest

	//log.debug "Erliest time to start charge: " + earliestTimeToStartCharge

	// Filter out all times not part of the set timeframe
	def filteredByStartandEnd = priceInformation.findAll({it ->
		it.startsAt >= (earliest) && it.startsAt < (latest)
	})

	//println "This is a filtered list: " + filteredByStartandEnd

	//log.debug(filteredByStartandEnd.sort{it.total})

	if (filteredByStartandEnd.size() < runTime) {
		log.warn "Interval not part of input data, outdated information?"
		return []
	}

	def lowestCostTimeframes = filteredByStartandEnd.sort{it.total}[0..(runTime-1)]

	//log.debug(lowestCostTimeframes)

	lowestCostTimeframesEarliestFirst = lowestCostTimeframes.sort{it.startsAt}

	//log.debug(lowestCostTimeframesEarliestFirst)

	if (keepOnAtEnd == true) {
		// If leave on at end schedule, useful for heating applications
		lowestCostTimeframesEarliestFirst[-1].endsAt = null
		lowestCostTimeframesEarliestFirst.add(['total': 'last run time', 'startsAt': null, 'endsAt': latest])
		log.debug "Keeping schedule on after last"
	}

	// Check if timeperiod is overlapping
	lowestCostTimeframesEarliestFirst.each{it ->
		def thisStartsAt = it.startsAt

		//log.debug "This Start at: " + thisStartsAt
		//log.debug lowestCostTimeframesEarliestFirst.findAll{it.endsAt == thisStartsAt}

		lowestCostTimeframesEarliestFirst.findAll{it.endsAt == thisStartsAt}.each{noStop->
			noStop.endsAt = 'skip'
		}
	}

	//outSchedule += lowestCostTimeframesEarliestFirst

	return lowestCostTimeframesEarliestFirst
}

def planLowestPrice () {

	if (getPrice() == false) {
		log.error "Failed to fetch prices!"

		state.errorCount += state.errorCount

		if (state.errorCount >= 4) {
			// Error trying to set up plan, send notification and perform failsafe

			notifier?.deviceNotification("Could not plan, using fail safe")

			unschedule(turnOnSwitch)
			unschedule(turnOffSwitch)

			schedule(failSafeStartTime, turnOnSwitch)
			schedule(failSafeStopTime, turnOffSwitch)

			def ControllerSwitch = getChildDevices()[0]
			ControllerSwitch?.setChargerStart("fail-safe")

			state.errorCount = 0
		} else {
			// Try to replan
			runIn(20*60, planLowestPrice)
		}


	}

	log.debug "This is the tibber map: " + state.tibberMap

	//return

	// Convert String dates to actual dates to make it easier to work with
	/*state.tibberMap.each({it->
		Date startsAt = Date.parse("yyyy-MM-DD'T'HH:mm:ss.SSSX", it.startsAt)
		Date endsAt

		use(groovy.time.TimeCategory) {
			endsAt = startsAt + 1.hour
		}

		it.startsAt = startsAt
		it.endsAt = endsAt
	})

	Date earliestTimeToStart = Date.parse("yyyy-MM-DD'T'HH:mm:ss.SSSX", earliestTimeToStartCharge)
	Date readyBy = Date.parse("yyyy-MM-DD'T'HH:mm:ss.SSSX", readyByTime)

	use (groovy.time.TimeCategory) {
		def dur = earliestTimeToStart - new Date()
		if (dur.hours < 0) {
			earliestTimeToStart = earliestTimeToStart + 24.hour
			readyBy = readyBy + 24.hour
		}
		log.debug "Diff between now and earliestTimeToStart: " + dur.hours
	}

	log.debug "Erliest time to start charge: " + earliestTimeToStartCharge

	// Filter out all times not part of the set timeframe
	def filteredByStartandEnd = state.tibberMap.findAll({it ->
		it.startsAt >= (earliestTimeToStart) && it.startsAt < (readyBy)
	})

	log.debug(filteredByStartandEnd.sort{it.total})

	def lowestCostTimeframes = filteredByStartandEnd.sort{it.total}[0..(runTime-1)]

	log.debug(lowestCostTimeframes)

	def lowestCostTimeframesEarliestFirst = lowestCostTimeframes.sort{it.startsAt}

	log.debug(lowestCostTimeframesEarliestFirst)

	// Check if timeperiod is overlapping
	lowestCostTimeframesEarliestFirst.each{it ->
		def thisStartsAt = it.startsAt

		log.debug "This Start at: " + thisStartsAt
		log.debug lowestCostTimeframesEarliestFirst.findAll{it.endsAt == thisStartsAt}

		lowestCostTimeframesEarliestFirst.findAll{it.endsAt == thisStartsAt}.each{noStop->
			noStop.endsAt = null
		}
	}*/
// List getSchedule(Date earliest, Date latest, Integer runTime, List _priceInformation, List outSchedule = [], boolean keepOnAtEnd=false)

	List finalSchedule = getSchedule(earliest1, latest1, runTime1, state.tibberMap, keepOnAfter1)

	log.debug "First schedule: " + finalSchedule

	if (useTimeSlot2 == true) {
		List secondSchedule = getSchedule(earliest2, latest2, runTime2, state.tibberMap, keepOnAfter2)

		log.debug "Second schedule: " + secondSchedule

		finalSchedule += secondSchedule
	}

	// Check if timeperiod is overlapping
	finalSchedule.each{it ->
		def thisStartsAt = it.startsAt

		finalSchedule.findAll{it.endsAt == thisStartsAt}.each{noStop->
			noStop.endsAt = 'skip'
		}
	}

	//finalSchedule = finalSchedule.sort{it.startsAt}

	log.debug(finalSchedule)

	// Remove all previous plans
	unschedule(turnOnSwitch)
	unschedule(turnOffSwitch)

	// Schedule plan
	finalSchedule.each{it->
		if (it.startsAt != null) {
			Date now = new Date()
			now.set(minute: 0, second: 0)

			log.debug "Now: $now, it.startsAt: $it.startsAt"

			if (now[HOUR_OF_DAY] == it.startsAt[HOUR_OF_DAY]) {
				turnOnSwitch()
				log.debug "start directly ($it.startsAt, 'turnOnSwitch')"
			} else {
				schedule(it.startsAt, 'turnOnSwitch', ['overwrite': false])
				log.debug "schedule($it.startsAt, 'turnOnSwitch')"
			}


		}
		if (it.endsAt != 'skip') {
			schedule(it.endsAt, 'turnOffSwitch', ['overwrite': false])
			log.debug "schedule($it.endsAt, 'turnOffSwitch')"
		}
	}


	state.errorCount = 0

	def ControllerSwitch = getChildDevices()[0]
	ControllerSwitch?.setChargerStart(lowestEnergyCostHour % 24 + ":00")
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

					if (tomorrow.size <= 0) {
						log.warn("Tomorrows prices are not ready, try again later")
						returnStatus = true
					}

					def priceList = today

					tomorrow.each{
						priceList << it
					}

					state.tibberMap = priceList

					log.debug(priceList.findAll({it ->
						it.startsAt > earliestTimeToStartCharge && it.startsAT < readyByTime}))

					priceList.each {num ->
						prices << (num.total)*100
					}
					log.debug(prices)
				} else {
					returnStatus = false
				}
			}
		} catch (e) {
			log.error "something went wrong: $e"
			returnStatus = false
		}
	}

	state.energyPrices = prices

	return returnStatus
}

def graphQLApiQuery(){
	return '{"query": "{viewer {homes {currentSubscription {priceInfo { current {total currency} today{ total startsAt } tomorrow{ total startsAt }}}}}}", "variables": null, "operationName": null}'
}

def addOverrideControl(){
	log.debug("Adding overrride control device")
	def ranNum = new Random().nextInt(65000) + 1

	state.childId = "oca-sb-$ranNum"

	def chdevice = addChildDevice("iholand", "Tibber Outlet Controller Slave",  state.childId, null, [name: "Tibber Controlled Override Switch", componentName: "Tibber Controlled Override Switch", componentLabel: "Tibber Controlled Override Switch"])
}

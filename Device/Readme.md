# Hubitat (device) Drivers
Here I store all my custom device drivers that I use in my Hubitat smart home solution.

## tuya (Wifi) Devices
These drivers can be found under the  tuyaDevices folder.

## Multisensor
This is a driver for a custom sensor I have made using a ATtiny1607 + AT89RF212B. The hardware
project can be viewed here: https://circuitmaker.com/Projects/Details/Ivar-Holanf/SensorEndNode

The idea of this project was to make an end node that could be used for everything I need form
a sensor in a smart home. It can be customized to measure temperature, moisture, earth moisture
etc. It even controls a cooling system for my bedrooms, where it controls a valve that controls
water flow through a radiator on my air intake.

## Power Meter
This is a specialized version of the Multisensor, while the multisensor focus on temperature and
moisture, the Power Meter focus on power and energy. I use this one in my solar collector system
to report power and energy production from the solar panels.

## TRV-leser
"Leser" means reader in Norwegian, hence the name is TRV reader. This driver is linked to the app
called TRVReminderApp. TRV is the company collecting waste bins in my area. They have a web
page (trv.no) which list what waste they are picking up this week. I need to move my waste bins
to the road for it to be collected.

This device parses the web page and get the name of the waste that is being collected this week.

## NEXA 433 Plug
This is a driver that interface a internal http service I have in my home automation system. I
have a service running on a ATmega4809 + WINC1500 that receives http messages and pass this on
to a 433MHz AM radio on the NEXA protocol. Hence I can control NEXA outlets using this driver.

## TibberOutletControllerSlave
This driver is linked to the app TibberOutletController. TibberOutletController app fetched energy
prices from the energy company, and controls switches based on the current energy price. E.g. I
use this to control my EV charger so that my car only charges when the energy price is on its
cheapest.

This driver allows me to control how the app controls my switches, i.e. I can stop the app from
controlling the switch if I need to do a manual override. I can also postpone the auto control
12, 24 or 48 hours. I use this to postpone charging my EV until Sunday night when I park the car
on Friday after work. Then the battery does not need to have the stress of being 100% fully
charged over the weekend.

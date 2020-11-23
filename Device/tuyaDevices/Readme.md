# tuya Wifi Hubitat device Drivers

I finally managed to make some progress on Tuya devices for Hubitat. This is still work in progress,
but with this device handler I am able to send encrypted messages directly to my tuya Wifi siren turning
it on without any external tools, it is just the Hubitat hub that calculates the message.

What you need to to pair the tuya device with one of the standard tuya apps, and get hold of the IP,
device ID, device key (I used the register at iot.tuya.com and the Linux tools to extract the device key),
and control endpoint. Once you have this information you just add it to the device preferences in Hubitat.

I used a package sniffer with the original tuya app to find out which endpoint to use, the Wifi siren I have
has endpoint 104. But for other switches I think I have seen endpoints around 0-4.

I am posting this half finished work for others to get started with tuya devices on Hubitat.

The tuyaGenericDevice.groovy can be used as a basis for developing support for new devices.
The tuyaWifiSiren.groovy is for the NEO Alarm Siren (Wifi-model)

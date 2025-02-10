# VisionGlassDemo

This project is a Demo application to play with the Vision Glasses SDK and understand better its capabilities.

It's not intended to be used as an actual VR application for that device.

## Setup instructions

In order to build the application, it's needed to copy the Vision Glasses SDK `aar` file into the ```app/libs``` folder.

## Test cases

### Device tracking

In this test case the demo creates Quaternions from data retrieved by the ```startImu``` method and it's applied an Euler transformation using the Quaternionn::toEuler and an [custom transformation](https://en.wikipedia.org/wiki/Conversion_between_quaternions_and_Euler_angles#Quaternion_to_Euler_angles_(in_3-2-1_sequence)_conversion).

The demo prints the values got on realtime.

Observe the data shown when connecting and disconnecting the device from the mobile's USB port.




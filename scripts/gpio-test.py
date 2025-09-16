#!/bin/bash
import RPi.GPIO as GPIO
import time

LED_PIN = 17  # Using BCM pin 17

# Set the pin numbering mode
GPIO.setmode(GPIO.BCM)

# Set up the LED pin as an output
GPIO.setup(LED_PIN, GPIO.OUT)

try:
    while True:
        GPIO.output(LED_PIN, GPIO.HIGH)  # Turn LED on
        time.sleep(1)  # Wait for 1 second
        GPIO.output(LED_PIN, GPIO.LOW)   # Turn LED off
        time.sleep(1)  # Wait for 1 second

except KeyboardInterrupt:
    print("Program terminated by user")

finally:
    GPIO.cleanup()  # Clean up GPIO settings on exit

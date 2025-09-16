#!/bin/bash
import RPi.GPIO as GPIO
import time
import serial

RE_PIN = 17
DE_PIN = 27

ser = serial.Serial(
    port='/dev/serial0',
    baudrate=9600,
    parity=serial.PARITY_NONE,
    stopbits=serial.STOPBITS_ONE,
    bytesize=serial.EIGHTBITS,
    timeout=1
)


all_cmd = bytes.fromhex('01 03 00 1e 00 03 65 cd')
nitro_cmd = bytes.fromhex('01 03 00 1e 00 01 e4 0c')
#nitro_cmd = bytes.fromhex('01 03 00 1e 00 01 b5 cc') #example crc
phos_cmd =  bytes.fromhex('01 03 00 1f 00 01 b5 cc')
pot_cmd = bytes.fromhex('01 03 00 20 00 01 85 c0')

# Set the pin numbering mode
GPIO.setmode(GPIO.BCM)

# Set up the LED pin as an output
GPIO.setup(RE_PIN, GPIO.OUT)
GPIO.setup(DE_PIN, GPIO.OUT)
GPIO.output(DE_PIN, GPIO.LOW)
GPIO.output(RE_PIN, GPIO.LOW)


def print_results(r):
    for _n in r:
        print (hex(_n))
        
def get_data(cmd,bytes_to_read=7):
    GPIO.output(DE_PIN, GPIO.HIGH)
    GPIO.output(RE_PIN, GPIO.HIGH)
    time.sleep(.01)
    r=ser.write(cmd)
    ser.flush()
    print ('write ret:',cmd,r)
    GPIO.output(DE_PIN, GPIO.LOW)
    GPIO.output(RE_PIN, GPIO.LOW)
    res = ser.read(bytes_to_read)
    print_results(res)


try:
    time.sleep(4)
    while True:
        #get_data(nitro_cmd)
        #time.sleep(1)
        get_data(all_cmd,bytes_to_read=11)
        #time.sleep(1)
        #get_data(phos_cmd)
        #time.sleep(1)
        #get_data(pot_cmd)
        time.sleep(1)

except KeyboardInterrupt:
    print("Program terminated by user")

finally:
    GPIO.cleanup()  # Clean up GPIO settings on exit

#!/usr/bin/env python3
import serial
import time
import struct
import RPi.GPIO as GPIO

# RS485 port and GPIO setup
PORT = '/dev/serial0'
BAUDRATE = 9600
TX_ENABLE_PIN = 18  # GPIO18 controls DE/RE

# Raw Modbus request: Read 3 registers starting at 0x001E
modbus_request = bytes.fromhex("01 03 00 1E 00 03 65 CD")

# Setup GPIO
GPIO.setmode(GPIO.BCM)
GPIO.setup(TX_ENABLE_PIN, GPIO.OUT)
GPIO.output(TX_ENABLE_PIN, GPIO.LOW)  # Default to receive mode

def parse_response(response):
    if len(response) < 9:
        print("Incomplete response:", response.hex())
        return

    byte_count = response[2]
    if byte_count != 6:
        print("Unexpected byte count:", byte_count)
        return

    npk_raw = response[3:9]
    nitrogen   = struct.unpack(">H", npk_raw[0:2])[0]
    phosphorus = struct.unpack(">H", npk_raw[2:4])[0]
    potassium  = struct.unpack(">H", npk_raw[4:6])[0]

    print(f"Nitrogen:   {nitrogen} mg/kg")
    print(f"Phosphorus: {phosphorus} mg/kg")
    print(f"Potassium:  {potassium} mg/kg")

def read_npk():
    try:
        with serial.Serial(PORT, BAUDRATE, timeout=1) as ser:
            ser.flushInput()

            # Enable transmit mode
            GPIO.output(TX_ENABLE_PIN, GPIO.HIGH)
            time.sleep(0.02)

            ser.write(modbus_request)
            ser.flush()

            # Switch to receive mode
            GPIO.output(TX_ENABLE_PIN, GPIO.LOW)

            time.sleep(0.01)
            response = ser.read(11)
            parse_response(response)

    except Exception as e:
        print(f"Communication error: {e}")

if __name__ == "__main__":
    try:
        while True:
            read_npk()
            time.sleep(.05)
    except KeyboardInterrupt:
        GPIO.cleanup()
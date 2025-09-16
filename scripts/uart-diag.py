import serial
import time

try:
    ser = serial.Serial('/dev/ttyS0', 9600, timeout=1) # Adjust baudrate as needed
    print("Serial port opened successfully.")

    # Example: Writing data
    ser.write(b"Hello, UART!\n")
    print("Data sent.")

    # Example: Reading data
    while True:
        if ser.in_waiting > 0:
            received_data = ser.readline().decode('utf-8').strip()
            print(f"Received: {received_data}")
        time.sleep(0.1)

except serial.SerialException as e:
    print(f"Error opening serial port: {e}")
except KeyboardInterrupt:
    print("Exiting program.")
finally:
    if 'ser' in locals() and ser.is_open:
        ser.close()
    print("Serial port closed.")

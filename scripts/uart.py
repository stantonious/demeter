import serial
import time

# Configure the serial port
# Use '/dev/ttyS0' for mini UART or '/dev/ttyAMA0' for PL011 UART
# Check your specific Raspberry Pi configuration for the correct port
ser = serial.Serial(
    port='/dev/ttyS0',  # Replace with your serial port
    baudrate=9600,
    parity=serial.PARITY_NONE,
    stopbits=serial.STOPBITS_ONE,
    bytesize=serial.EIGHTBITS,
    timeout=1
)

def send_data(data_to_send):
    """Sends data over the serial port."""
    try:
        ser.write(data_to_send.encode())  # Encode string to bytes
        print(f"Sent: {data_to_send}")
    except serial.SerialException as e:
        print(f"Error sending data: {e}")

def receive_data():
    """Receives data from the serial port."""
    try:
        received_data = ser.readline().decode().strip()  # Read line and decode
        if received_data:
            print(f"Received: {received_data}")
        return received_data
    except serial.SerialException as e:
        print(f"Error receiving data: {e}")
        return None

try:
    while True:
        # Example: Send data
        message_to_send = "Hello from Pi!"
        send_data(message_to_send)

        # Example: Receive data
        received_message = receive_data()

        time.sleep(1)  # Wait for a second before next iteration

except KeyboardInterrupt:
    print("Exiting program.")
finally:
    ser.close()  # Close the serial port
    print("Serial port closed.")

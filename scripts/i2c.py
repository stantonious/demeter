import smbus2
import time

# ADS1115 I2C address (default is 0x48)
ADS1115_ADDRESS = 0x48

# Configuration register address
ADS1115_CONFIG_REG = 0x01

# Conversion register address
ADS1115_CONVERSION_REG = 0x00

# Configuration settings for A0, single-shot, +/-4.096V, 128 SPS
# Example: 0b1100001010000011 (MSB first)
# OS=1 (Start conversion), MUX=000 (AIN0/GND), PGA=001 (+/-4.096V), MODE=1 (Single-shot)
# DR=100 (128 SPS), COMP_MODE=0, COMP_POL=0, COMP_LAT=0, COMP_QUE=11 (Disable comparator)
#CONFIG_VALUE_A0 = 0b1100001010000011
CONFIG_VALUE_A0 = 0b1100001011110011

# O MUX PGA M DRA C C C CQ
#                 M P L
CONFIG_VALUE = (0b1 << 15) | (0b100 << 12) | (0b001 << 9) | (0b1 << 8) | (0b100 << 5) | (0b0 << 4) | (0b0 << 3) | (0b0 << 2) | 0b11  
print ('cfg ',bin(CONFIG_VALUE_A0))


bus = smbus2.SMBus(1) # Use bus 1 for Raspberry Pi

print("Reading ADS1115 values from channel A0 using SMBus, press Ctrl-C to quit...")

while True:
    # Write configuration to start conversion
    print (CONFIG_VALUE.to_bytes(2).hex())
    config_bytes = CONFIG_VALUE.to_bytes(2,byteorder='big')
    print(config_bytes.hex())
    bus.write_i2c_block_data(ADS1115_ADDRESS, ADS1115_CONFIG_REG, config_bytes)

    # Wait for conversion to complete (ADS1115 takes time)
    time.sleep(0.01) # Adjust based on data rate

    # Read the conversion result
    raw_value = bus.read_word_data(ADS1115_ADDRESS, ADS1115_CONVERSION_REG)
    raw_value = ((raw_value & 0xFF) << 8) | (raw_value >> 8) # Swap bytes (SMBus reads LSB first)

    # Convert raw value to voltage (for +/-4.096V range)
    # Max value for 16-bit signed is 32767
    voltage = raw_value * (4.096 / 32767.0)

    print(f"Raw Value (A0): {raw_value}, Voltage (A0): {voltage:.4f}V")
    time.sleep(0.5)
#!/home/bstaley/venv/demeter/bin/python

import dbus
import dbus.mainloop.glib
import dbus.service
from gi.repository import GLib
import threading
import time
import struct
import numpy as np
import serial
import struct
import RPi.GPIO as GPIO
from openai import OpenAI
from ollama import generate
import board
import busio
import adafruit_ads1x15.ads1115 as ADS
from adafruit_ads1x15.analog_in import AnalogIn
import adafruit_bh1750
from config import OPENAI_API_KEY
from PIL import Image, ImageDraw
import io
import httpx

client = OpenAI(api_key=OPENAI_API_KEY)

SERVICE_UUID = "12345678-1234-5678-1234-56789abcdef0"
CHAR_UUID = "12345678-1234-5678-1234-56789abcdef1"
CHAR_VALUE = [dbus.Byte(0x00)]
ADVERTISEMENT_INTERFACE = 'org.bluez.LEAdvertisement1'
DBUS_PROP_IFACE = 'org.freedesktop.DBus.Properties'


# Raw Modbus request: Read 3 registers starting at 0x001E
modbus_request = bytes.fromhex("01 03 00 1E 00 03 65 CD")

# RS485 port and GPIO setup
PORT = '/dev/serial0'
BAUDRATE = 9600
MOISTURE_ADC_DRY = 32768
MOISTURE_ADC_WET = 15073
HUMIDITY_SLOPE = 0.0021658
HUMIDITY_INTERCEPT = -41.94
TX_ENABLE_PIN = 18  # GPIO18 controls DE/RE

# Setup GPIO
GPIO.setmode(GPIO.BCM)
GPIO.setup(TX_ENABLE_PIN, GPIO.OUT)
GPIO.output(TX_ENABLE_PIN, GPIO.LOW)  # Default to receive mode


sensor_val = 0
pot_val = 0.
nit_val = 0.
phr_val = 0.
ph_val = 7.0
sun_val = 8.0
g_plant_type = 'ground cover'
g_humidity_val = 0.0
g_moisture_val = 0.0
g_light_val = 0.0
g_num_suggestions = 0
g_augmented_image_data = bytearray()
g_suggested_plant_name = ""
g_generated_plant_image_data = bytearray()


class Characteristic(dbus.service.Object):
    def __init__(self, bus, index, uuid, flags, service):
        self.path = service.path + f"/char{index}"
        self.bus = bus
        self.uuid = uuid
        self.flags = flags
        self.service = service
        self.notifying = False
        dbus.service.Object.__init__(self, bus, self.path)

    def get_properties(self):
        return {
            "org.bluez.GattCharacteristic1": {
                "UUID": self.uuid,
                "Service": self.service.get_path(),
                "Flags": self.flags,
                "Value": CHAR_VALUE
            }
        }

    def get_path(self):
        return dbus.ObjectPath(self.path)

    @dbus.service.method("org.bluez.GattCharacteristic1",
                         in_signature="a{sv}", out_signature="ay")
    def ReadValue(self, options):
        return [sensor_val]

    @dbus.service.method("org.bluez.GattCharacteristic1",
                         in_signature="aya{sv}", out_signature="")
    def WriteValue(self, value, options):
        global CHAR_VALUE
        CHAR_VALUE = value

    @dbus.service.method("org.bluez.GattCharacteristic1",
                         in_signature="", out_signature="")
    def StartNotify(self):
        if self.notifying:
            return
        GLib.timeout_add_seconds(2, self._notify)
        self.notifying = True

    @dbus.service.method("org.bluez.GattCharacteristic1",
                         in_signature="", out_signature="")
    def StopNotify(self):
        self.notifying = False

    def _notify(self):
        if not self.notifying:
            return False
        self.PropertiesChanged("org.bluez.GattCharacteristic1",
                               {"Value": [(sensor_val + 1) % 255]}, [])
        return True

    @dbus.service.signal("org.freedesktop.DBus.Properties",
                         signature="sa{sv}as")
    def PropertiesChanged(self, interface, changed, invalidated):
        pass


class LightChar(dbus.service.Object):
    def __init__(self, bus, index, uuid, flags, service):
        self.path = service.path + f"/char{index}"
        self.bus = bus
        self.uuid = uuid
        self.flags = flags
        self.service = service
        self.notifying = False
        self.value = 0.0  # Initial float value
        dbus.service.Object.__init__(self, bus, self.path)

    def get_properties(self):
        return {
            "org.bluez.GattCharacteristic1": {
                "UUID": self.uuid,
                "Service": self.service.get_path(),
                "Flags": self.flags,
            }
        }

    def get_path(self):
        return dbus.ObjectPath(self.path)

    @dbus.service.method("org.bluez.GattCharacteristic1",
                         in_signature="a{sv}", out_signature="ay")
    def ReadValue(self, options):
        packed = struct.pack('<f', self.value)
        return [dbus.Byte(b) for b in packed]

    @dbus.service.method("org.bluez.GattCharacteristic1",
                         in_signature="", out_signature="")
    def StartNotify(self):
        if self.notifying:
            return
        self.notifying = True
        GLib.timeout_add_seconds(2, self._notify)

    @dbus.service.method("org.bluez.GattCharacteristic1",
                         in_signature="", out_signature="")
    def StopNotify(self):
        self.notifying = False

    def _notify(self):
        if not self.notifying:
            return False
        self.value = g_light_val
        packed = struct.pack('<f', self.value)
        self.PropertiesChanged("org.bluez.GattCharacteristic1",
                               {"Value": [dbus.Byte(b) for b in packed]}, [])
        return True

    @dbus.service.signal("org.freedesktop.DBus.Properties",
                         signature="sa{sv}as")
    def PropertiesChanged(self, interface, changed, invalidated):
        pass


class PhChar(dbus.service.Object):
    def __init__(self, bus, index, uuid, flags, service):
        self.path = service.path + f"/char{index}"
        self.bus = bus
        self.uuid = uuid
        self.flags = flags
        self.service = service
        self.notifying = False
        self.value = 7.0  # Initial float value
        dbus.service.Object.__init__(self, bus, self.path)

    def get_properties(self):
        return {
            "org.bluez.GattCharacteristic1": {
                "UUID": self.uuid,
                "Service": self.service.get_path(),
                "Flags": self.flags,
            }
        }

    def get_path(self):
        return dbus.ObjectPath(self.path)

    @dbus.service.method("org.bluez.GattCharacteristic1",
                         in_signature="a{sv}", out_signature="ay")
    def ReadValue(self, options):
        packed = struct.pack('<f', self.value)
        return [dbus.Byte(b) for b in packed]

    @dbus.service.method("org.bluez.GattCharacteristic1",
                         in_signature="", out_signature="")
    def StartNotify(self):
        if self.notifying:
            return
        self.notifying = True
        GLib.timeout_add_seconds(2, self._notify)

    @dbus.service.method("org.bluez.GattCharacteristic1",
                         in_signature="", out_signature="")
    def StopNotify(self):
        self.notifying = False

    def _notify(self):
        if not self.notifying:
            return False
        self.value = ph_val
        packed = struct.pack('<f', self.value)
        self.PropertiesChanged("org.bluez.GattCharacteristic1",
                               {"Value": [dbus.Byte(b) for b in packed]}, [])
        return True

    @dbus.service.signal("org.freedesktop.DBus.Properties",
                         signature="sa{sv}as")
    def PropertiesChanged(self, interface, changed, invalidated):
        pass


class HumidChar(dbus.service.Object):
    def __init__(self, bus, index, uuid, flags, service):
        self.path = service.path + f"/char{index}"
        self.bus = bus
        self.uuid = uuid
        self.flags = flags
        self.service = service
        self.notifying = False
        self.value = 0.0  # Initial float value
        dbus.service.Object.__init__(self, bus, self.path)

    def get_properties(self):
        return {
            "org.bluez.GattCharacteristic1": {
                "UUID": self.uuid,
                "Service": self.service.get_path(),
                "Flags": self.flags,
            }
        }

    def get_path(self):
        return dbus.ObjectPath(self.path)

    @dbus.service.method("org.bluez.GattCharacteristic1",
                         in_signature="a{sv}", out_signature="ay")
    def ReadValue(self, options):
        packed = struct.pack('<f', self.value)
        return [dbus.Byte(b) for b in packed]

    @dbus.service.method("org.bluez.GattCharacteristic1",
                         in_signature="", out_signature="")
    def StartNotify(self):
        if self.notifying:
            return
        self.notifying = True
        GLib.timeout_add_seconds(2, self._notify)

    @dbus.service.method("org.bluez.GattCharacteristic1",
                         in_signature="", out_signature="")
    def StopNotify(self):
        self.notifying = False

    def _notify(self):
        if not self.notifying:
            return False
        self.value = g_humidity_val
        packed = struct.pack('<f', self.value)
        self.PropertiesChanged("org.bluez.GattCharacteristic1",
                               {"Value": [dbus.Byte(b) for b in packed]}, [])
        return True

    @dbus.service.signal("org.freedesktop.DBus.Properties",
                         signature="sa{sv}as")
    def PropertiesChanged(self, interface, changed, invalidated):
        pass


class GroundMoistureChar(dbus.service.Object):
    def __init__(self, bus, index, uuid, flags, service):
        self.path = service.path + f"/char{index}"
        self.bus = bus
        self.uuid = uuid
        self.flags = flags
        self.service = service
        self.notifying = False
        self.value = 0.0  # Initial float value
        dbus.service.Object.__init__(self, bus, self.path)

    def get_properties(self):
        return {
            "org.bluez.GattCharacteristic1": {
                "UUID": self.uuid,
                "Service": self.service.get_path(),
                "Flags": self.flags,
            }
        }

    def get_path(self):
        return dbus.ObjectPath(self.path)

    @dbus.service.method("org.bluez.GattCharacteristic1",
                         in_signature="a{sv}", out_signature="ay")
    def ReadValue(self, options):
        packed = struct.pack('<f', self.value)
        return [dbus.Byte(b) for b in packed]

    @dbus.service.method("org.bluez.GattCharacteristic1",
                         in_signature="", out_signature="")
    def StartNotify(self):
        if self.notifying:
            return
        self.notifying = True
        GLib.timeout_add_seconds(2, self._notify)

    @dbus.service.method("org.bluez.GattCharacteristic1",
                         in_signature="", out_signature="")
    def StopNotify(self):
        self.notifying = False

    def _notify(self):
        if not self.notifying:
            return False
        self.value = g_moisture_val
        packed = struct.pack('<f', self.value)
        self.PropertiesChanged("org.bluez.GattCharacteristic1",
                               {"Value": [dbus.Byte(b) for b in packed]}, [])
        return True

    @dbus.service.signal("org.freedesktop.DBus.Properties",
                         signature="sa{sv}as")
    def PropertiesChanged(self, interface, changed, invalidated):
        pass


class SunChar(dbus.service.Object):
    def __init__(self, bus, index, uuid, flags, service):
        self.path = service.path + f"/char{index}"
        self.bus = bus
        self.uuid = uuid
        self.flags = flags
        self.service = service
        self.notifying = False
        self.value = 8.0  # Initial float value
        dbus.service.Object.__init__(self, bus, self.path)

    def get_properties(self):
        return {
            "org.bluez.GattCharacteristic1": {
                "UUID": self.uuid,
                "Service": self.service.get_path(),
                "Flags": self.flags,
            }
        }

    def get_path(self):
        return dbus.ObjectPath(self.path)

    @dbus.service.method("org.bluez.GattCharacteristic1",
                         in_signature="a{sv}", out_signature="ay")
    def ReadValue(self, options):
        packed = struct.pack('<f', self.value)
        return [dbus.Byte(b) for b in packed]

    @dbus.service.method("org.bluez.GattCharacteristic1",
                         in_signature="", out_signature="")
    def StartNotify(self):
        if self.notifying:
            return
        self.notifying = True
        GLib.timeout_add_seconds(2, self._notify)

    @dbus.service.method("org.bluez.GattCharacteristic1",
                         in_signature="", out_signature="")
    def StopNotify(self):
        self.notifying = False

    def _notify(self):
        if not self.notifying:
            return False
        self.value = sun_val
        packed = struct.pack('<f', self.value)
        self.PropertiesChanged("org.bluez.GattCharacteristic1",
                               {"Value": [dbus.Byte(b) for b in packed]}, [])
        return True

    @dbus.service.signal("org.freedesktop.DBus.Properties",
                         signature="sa{sv}as")
    def PropertiesChanged(self, interface, changed, invalidated):
        pass


class NitChar(dbus.service.Object):
    def __init__(self, bus, index, uuid, flags, service):
        self.path = service.path + f"/char{index}"
        self.bus = bus
        self.uuid = uuid
        self.flags = flags
        self.service = service
        self.notifying = False
        self.value = 23.5  # Initial float value
        dbus.service.Object.__init__(self, bus, self.path)

    def get_properties(self):
        return {
            "org.bluez.GattCharacteristic1": {
                "UUID": self.uuid,
                "Service": self.service.get_path(),
                "Flags": self.flags,
            }
        }

    def get_path(self):
        return dbus.ObjectPath(self.path)

    @dbus.service.method("org.bluez.GattCharacteristic1",
                         in_signature="a{sv}", out_signature="ay")
    def ReadValue(self, options):
        packed = struct.pack('<f', self.value)
        return [dbus.Byte(b) for b in packed]

    @dbus.service.method("org.bluez.GattCharacteristic1",
                         in_signature="", out_signature="")
    def StartNotify(self):
        if self.notifying:
            return
        self.notifying = True
        GLib.timeout_add_seconds(2, self._notify)

    @dbus.service.method("org.bluez.GattCharacteristic1",
                         in_signature="", out_signature="")
    def StopNotify(self):
        self.notifying = False

    def _notify(self):
        if not self.notifying:
            return False
        self.value = nit_val
        packed = struct.pack('<f', self.value)
        self.PropertiesChanged("org.bluez.GattCharacteristic1",
                               {"Value": [dbus.Byte(b) for b in packed]}, [])
        return True

    @dbus.service.signal("org.freedesktop.DBus.Properties",
                         signature="sa{sv}as")
    def PropertiesChanged(self, interface, changed, invalidated):
        pass


class KChar(dbus.service.Object):
    def __init__(self, bus, index, uuid, flags, service):
        self.path = service.path + f"/char{index}"
        self.bus = bus
        self.uuid = uuid
        self.flags = flags
        self.service = service
        self.notifying = False
        self.value = 23.5  # Initial float value
        dbus.service.Object.__init__(self, bus, self.path)

    def get_properties(self):
        return {
            "org.bluez.GattCharacteristic1": {
                "UUID": self.uuid,
                "Service": self.service.get_path(),
                "Flags": self.flags,
            }
        }

    def get_path(self):
        return dbus.ObjectPath(self.path)

    @dbus.service.method("org.bluez.GattCharacteristic1",
                         in_signature="a{sv}", out_signature="ay")
    def ReadValue(self, options):
        packed = struct.pack('<f', self.value)
        return [dbus.Byte(b) for b in packed]

    @dbus.service.method("org.bluez.GattCharacteristic1",
                         in_signature="", out_signature="")
    def StartNotify(self):
        if self.notifying:
            return
        self.notifying = True
        GLib.timeout_add_seconds(2, self._notify)

    @dbus.service.method("org.bluez.GattCharacteristic1",
                         in_signature="", out_signature="")
    def StopNotify(self):
        self.notifying = False

    def _notify(self):
        if not self.notifying:
            return False
        self.value = pot_val
        packed = struct.pack('<f', self.value)
        self.PropertiesChanged("org.bluez.GattCharacteristic1",
                               {"Value": [dbus.Byte(b) for b in packed]}, [])
        return True

    @dbus.service.signal("org.freedesktop.DBus.Properties",
                         signature="sa{sv}as")
    def PropertiesChanged(self, interface, changed, invalidated):
        pass


class ImageStatusChar(dbus.service.Object):
    def __init__(self, bus, index, uuid, flags, service):
        self.path = service.path + f"/char{index}"
        self.bus = bus
        self.uuid = uuid
        self.flags = flags
        self.service = service
        self.notifying = False
        self.status = 0  # 0: idle, 1: receiving, 2: success, 3: error
        dbus.service.Object.__init__(self, bus, self.path)

    def get_properties(self):
        return {
            "org.bluez.GattCharacteristic1": {
                "UUID": self.uuid,
                "Service": self.service.get_path(),
                "Flags": self.flags,
            }
        }

    def get_path(self):
        return dbus.ObjectPath(self.path)

    def set_status(self, status):
        if status != self.status:
            self.status = status
            if self.notifying:
                print(f"Image status changed to: {self.status}")
                self.PropertiesChanged("org.bluez.GattCharacteristic1", {
                                       "Value": [dbus.Byte(self.status)]}, [])

    @dbus.service.method("org.bluez.GattCharacteristic1", in_signature="a{sv}", out_signature="ay")
    def ReadValue(self, options):
        return [dbus.Byte(self.status)]

    @dbus.service.method("org.bluez.GattCharacteristic1", in_signature="", out_signature="")
    def StartNotify(self):
        if self.notifying:
            return
        self.notifying = True

    @dbus.service.method("org.bluez.GattCharacteristic1", in_signature="", out_signature="")
    def StopNotify(self):
        self.notifying = False

    @dbus.service.signal("org.freedesktop.DBus.Properties", signature="sa{sv}as")
    def PropertiesChanged(self, interface, changed, invalidated):
        pass


class AugmentedImageProgressChar(dbus.service.Object):
    def __init__(self, bus, index, uuid, flags, service):
        self.path = service.path + f"/char{index}"
        self.bus = bus
        self.uuid = uuid
        self.flags = flags
        self.service = service
        self.notifying = False
        self.progress = 0  # 0-100
        dbus.service.Object.__init__(self, bus, self.path)

    def get_properties(self):
        return {
            "org.bluez.GattCharacteristic1": {
                "UUID": self.uuid,
                "Service": self.service.get_path(),
                "Flags": self.flags,
            }
        }

    def get_path(self):
        return dbus.ObjectPath(self.path)

    def set_progress(self, progress):
        if progress != self.progress:
            self.progress = progress
            if self.notifying:
                print(f"Augmented image progress: {self.progress}%")
                self.PropertiesChanged("org.bluez.GattCharacteristic1", {
                                       "Value": [dbus.Byte(self.progress)]}, [])

    @dbus.service.method("org.bluez.GattCharacteristic1", in_signature="a{sv}", out_signature="ay")
    def ReadValue(self, options):
        return [dbus.Byte(self.progress)]

    @dbus.service.method("org.bluez.GattCharacteristic1", in_signature="", out_signature="")
    def StartNotify(self):
        if self.notifying:
            return
        self.notifying = True

    @dbus.service.method("org.bluez.GattCharacteristic1", in_signature="", out_signature="")
    def StopNotify(self):
        self.notifying = False

    @dbus.service.signal("org.freedesktop.DBus.Properties", signature="sa{sv}as")
    def PropertiesChanged(self, interface, changed, invalidated):
        pass


class ImageUploadProgressChar(dbus.service.Object):
    def __init__(self, bus, index, uuid, flags, service):
        self.path = service.path + f"/char{index}"
        self.bus = bus
        self.uuid = uuid
        self.flags = flags
        self.service = service
        self.progress = 0  # 0-100
        dbus.service.Object.__init__(self, bus, self.path)

    def get_properties(self):
        return {
            "org.bluez.GattCharacteristic1": {
                "UUID": self.uuid,
                "Service": self.service.get_path(),
                "Flags": self.flags,
            }
        }

    def get_path(self):
        return dbus.ObjectPath(self.path)

    @dbus.service.method("org.bluez.GattCharacteristic1", in_signature="aya{sv}")
    def WriteValue(self, value, options):
        if len(value) == 4:
            self.progress = struct.unpack('<i', bytes(value))[0]
            print(f"Original image upload progress: {self.progress}%")
        else:
            print(
                f"Received invalid byte array length for progress: {len(value)}")


class PChar(dbus.service.Object):
    def __init__(self, bus, index, uuid, flags, service):
        self.path = service.path + f"/char{index}"
        self.bus = bus
        self.uuid = uuid
        self.flags = flags
        self.service = service
        self.notifying = False
        self.value = 23.5  # Initial float value
        dbus.service.Object.__init__(self, bus, self.path)

    def get_properties(self):
        return {
            "org.bluez.GattCharacteristic1": {
                "UUID": self.uuid,
                "Service": self.service.get_path(),
                "Flags": self.flags,
            }
        }

    def get_path(self):
        return dbus.ObjectPath(self.path)

    @dbus.service.method("org.bluez.GattCharacteristic1",
                         in_signature="a{sv}", out_signature="ay")
    def ReadValue(self, options):
        packed = struct.pack('<f', self.value)
        return [dbus.Byte(b) for b in packed]

    @dbus.service.method("org.bluez.GattCharacteristic1",
                         in_signature="", out_signature="")
    def StartNotify(self):
        if self.notifying:
            return
        self.notifying = True
        GLib.timeout_add_seconds(2, self._notify)

    @dbus.service.method("org.bluez.GattCharacteristic1",
                         in_signature="", out_signature="")
    def StopNotify(self):
        self.notifying = False

    def _notify(self):
        if not self.notifying:
            return False
        self.value = phr_val
        packed = struct.pack('<f', self.value)
        self.PropertiesChanged("org.bluez.GattCharacteristic1",
                               {"Value": [dbus.Byte(b) for b in packed]}, [])
        return True

    @dbus.service.signal("org.freedesktop.DBus.Properties",
                         signature="sa{sv}as")
    def PropertiesChanged(self, interface, changed, invalidated):
        pass


class ImageChar(dbus.service.Object):
    def __init__(self, bus, index, uuid, flags, service):
        self.path = service.path + f"/char{index}"
        self.bus = bus
        self.uuid = uuid
        self.flags = flags
        self.service = service
        self.image_data = bytearray()
        dbus.service.Object.__init__(self, bus, self.path)

    def get_properties(self):
        return {
            "org.bluez.GattCharacteristic1": {
                "UUID": self.uuid,
                "Service": self.service.get_path(),
                "Flags": self.flags,
            }
        }

    def get_path(self):
        return dbus.ObjectPath(self.path)

    @dbus.service.method("org.bluez.GattCharacteristic1",
                         in_signature="aya{sv}")
    def WriteValue(self, value, options):
        global g_augmented_image_data, g_suggested_plant_name, g_generated_plant_image_data
        chunk = bytes(value)
        if chunk == b'EOT':
            self.service.image_status_char.set_status(1)
            try:
                # Open the user's photo
                user_image = Image.open(io.BytesIO(self.image_data))
                # Generate and download the DALL-E image
                print('curr plant name', g_suggested_plant_name)
                dalle_thread = threading.Thread(target=generate_dalle_image_inpaint, args=(
                    user_image, g_suggested_plant_name,))
                dalle_thread.start()
                dalle_thread.join()

                if not g_generated_plant_image_data:
                    raise Exception("Failed to generate plant image.")

                # Open the generated plant image
                plant_image = Image.open(io.BytesIO(
                    g_generated_plant_image_data)).convert("RGBA")

                output_buffer = io.BytesIO()
                plant_image.resize((512,512)).convert("RGB").save(output_buffer, format="JPEG")
                g_augmented_image_data = output_buffer.getvalue()

                print("Image composition successful.")
                self.service.image_status_char.set_status(2)
                self.service.augmented_image_progress_char.set_progress(0)

            except Exception as e:
                print(f"Error during image composition: {e}")
                self.service.image_status_char.set_status(3)
            finally:
                # Clear all temporary image data
                self.image_data = bytearray()
                g_generated_plant_image_data = bytearray()
        else:
            self.image_data.extend(chunk)
            print(
                f"Received chunk of size {len(chunk)}, total size {len(self.image_data)}")


class IntOffsetChar(dbus.service.Object):
    def __init__(self, bus, index, uuid, flags, service):
        self.path = service.path + f"/char{index}"
        self.bus = bus
        self.uuid = uuid
        self.flags = flags
        self.service = service
        self.value = 0
        dbus.service.Object.__init__(self, bus, self.path)

    def get_properties(self):
        return {
            "org.bluez.GattCharacteristic1": {
                "UUID": self.uuid,
                "Service": self.service.get_path(),
                "Flags": self.flags,
            }
        }

    def get_path(self):
        return dbus.ObjectPath(self.path)

    @dbus.service.method("org.bluez.GattCharacteristic1", in_signature="aya{sv}")
    def WriteValue(self, value, options):
        if len(value) == 4:
            self.value = struct.unpack('<i', bytes(value))[0]
            print(f"Set image request offset to: {self.value}")
        else:
            print(
                f"Received invalid byte array length for offset: {len(value)}")


class AugmentedImageChar(dbus.service.Object):
    def __init__(self, bus, index, uuid, flags, service, request_char):
        self.path = service.path + f"/char{index}"
        self.bus = bus
        self.uuid = uuid
        self.flags = flags
        self.service = service
        self.request_char = request_char
        dbus.service.Object.__init__(self, bus, self.path)

    def get_properties(self):
        return {
            "org.bluez.GattCharacteristic1": {
                "UUID": self.uuid,
                "Service": self.service.get_path(),
                "Flags": self.flags,
            }
        }

    def get_path(self):
        return dbus.ObjectPath(self.path)

    @dbus.service.method("org.bluez.GattCharacteristic1", in_signature="a{sv}", out_signature="ay")
    def ReadValue(self, options):
        offset = self.request_char.value

        if not g_augmented_image_data or offset >= len(g_augmented_image_data):
            print("Image transfer complete or no image available.")
            if g_augmented_image_data:
                self.service.augmented_image_progress_char.set_progress(100)
            return []

        chunk_size = 512
        start_index = offset
        end_index = min(start_index + chunk_size, len(g_augmented_image_data))

        chunk = g_augmented_image_data[start_index:end_index]

        progress = int(end_index * 100 / len(g_augmented_image_data))
        self.service.augmented_image_progress_char.set_progress(progress)

        print(
            f"Reading augmented image chunk (offset: {offset}, size: {len(chunk)})")
        return [dbus.Byte(b) for b in chunk]


current_llm_response = ""
is_generating = False
g_llm_prompt = ""


def update_generating_status(start_time, prompt=''):
    print('status p', prompt)
    global current_llm_response
    while is_generating:
        elapsed = int(time.time() - start_time)
        current_llm_response = f""
        time.sleep(1)


def generate_plant_prompt(
    n_mgkg, p_mgkg, k_mgkg, ph, moisture, sun_intensity,
    lat, lon, plant_type, max_plants=3
):
    print('ptype', plant_type)
    prompt = (
        f"Suggest {max_plants} {plant_type} plant types for a location at ({lat}, {lon}).\n"
        f"Soil composition is: N={n_mgkg}mg/kg, P={p_mgkg}mg/kg, K={k_mgkg}mg/kg, pH={ph}, soil moisture={moisture} %.\n"
        f"Sun intesity: {sun_intensity} lux.\n"
        f"Reply in {max_plants} short bullet points, with the name of the plant types only."
    )
    return prompt


def generate_ollama_response(prompt, llm_status_char):
    global current_llm_response, is_generating
    if is_generating:
        return

    is_generating = True
    llm_status_char.set_status(1)  # Generating
    print('starting ollama req in background')
    start_time = time.time()

    counter_thread = threading.Thread(
        target=update_generating_status, args=(start_time, g_llm_prompt))
    counter_thread.daemon = True
    counter_thread.start()

    try:
        response = generate('tinyllama', prompt).response
        current_llm_response = response
        print('got ollama res in background:', current_llm_response)
    except Exception as e:
        print(f"Error in ollama generation: {e}")
        current_llm_response = "Error generating response."
    finally:
        is_generating = False
        llm_status_char.set_status(2)  # Ready


def generate_chatgpt_response(prompt, llm_status_char):
    global current_llm_response, is_generating, g_suggested_plant_name
    if is_generating:
        return

    is_generating = True
    llm_status_char.set_status(1)  # Generating
    print('starting openai req in background')
    start_time = time.time()

    counter_thread = threading.Thread(
        target=update_generating_status, args=(start_time, g_llm_prompt))
    counter_thread.daemon = True
    counter_thread.start()

    try:
        completion = client.chat.completions.create(
            model="gpt-3.5-turbo",
            messages=[
                {"role": "system", "content": "You are a helpful assistant."},
                {"role": "user", "content": prompt}
            ]
        )
        response = completion.choices[0].message.content
        current_llm_response = response
        print('got openai res in background:', current_llm_response)

        # Parse and store the first suggested plant name
        lines = response.split('\n')
        for line in lines:
            line = line.strip()
            # if line.startswith(('-', '*')):
            if True:
                plant_name = line.lstrip('*- ').strip()
                g_suggested_plant_name = plant_name
                print(f"Stored suggested plant name: {g_suggested_plant_name}")
                break  # Found the first one
    except Exception as e:
        print(f"Error in openai generation: {e}")
        current_llm_response = "Error generating response."
    finally:
        is_generating = False
        llm_status_char.set_status(2)  # Ready


def generate_dalle_image_inpaint(image, plant_name):
    global g_generated_plant_image_data
    print('plant name', plant_name)
    if not plant_name:
        print("No plant name available to generate an image.")
        return

    try:

        print('Generating dalle image')
        mask = Image.new("RGBA", image.size, (0, 0, 0, 255))
        draw = ImageDraw.Draw(mask)
        # (x1,y1,x2,y2)
        draw.rectangle((100, 100, 200, 200), fill=(0, 0, 0, 0))
        img_bytes = io.BytesIO()
        image.convert("RGBA").save('./img.png', format="PNG")
        img_bytes.seek(0)
        mask_bytes = io.BytesIO()
        mask.convert("RGBA").save('./mask.png', format="PNG")
        mask_bytes.seek(0)

        with open('./img.png', 'rb') as image_f, open('./mask.png', 'rb') as mask_f:
            response = client.images.edit(
                model="dall-e-2",
                image=image_f,
                mask=mask_f,
                prompt=f"A clear, high-quality image of a fully grown {plant_name} {g_plant_type} that is firmly planted and part of the natural surroundings.  The view point should be from 6 ft. above and at a 20 degree angle.",
                n=1,  # Number of images to generate
                size="512x512"  # Image resolution
            )
            # The generated image URL is in the response
            image_url = response.data[0].url
            image_url = response.data[0].url
            print(f"Generated image URL: {image_url}")

            # Download the image
            with httpx.stream("GET", image_url) as r:
                image_data = bytearray()
                for chunk in r.iter_bytes():
                    image_data.extend(chunk)
                g_generated_plant_image_data = image_data
                print("Successfully downloaded generated image.")

    except Exception as e:
        print(f"Error generating or downloading DALL-E image: {e}")


def generate_dalle_image(plant_name):
    global g_generated_plant_image_data
    if not plant_name:
        print("No plant name available to generate an image.")
        return

    try:
        print(f"Generating DALL-E image for: {plant_name}")
        response = client.images.generate(
            model="dall-e-3",
            prompt=f"A clear, high-quality image of a {plant_name} plant on a transparent background.",
            size="1024x1024",
            quality="standard",
            n=1,
        )
        image_url = response.data[0].url
        print(f"Generated image URL: {image_url}")

        # Download the image
        with httpx.stream("GET", image_url) as r:
            image_data = bytearray()
            for chunk in r.iter_bytes():
                image_data.extend(chunk)
            g_generated_plant_image_data = image_data
            print("Successfully downloaded generated image.")

    except Exception as e:
        print(f"Error generating or downloading DALL-E image: {e}")


def generate_llm_response(prompt, llm_status_char):
    generate_chatgpt_response(prompt, llm_status_char)
    # if g_llm_backend == 1:
    #    generate_chatgpt_response(prompt, llm_status_char)
    # else:
    #    generate_ollama_response(prompt, llm_status_char)


location_lat = "39.5186"
location_lon = "-104.7614"
sun_amount = "6"
ph_level = "7."
soil_moister_level = "semi-dry"
relative_humidity_level = "19"  # percent


plant_type_mapping = [
    "Vegetable", "Fruit", "Grass", "Ground Cover", "Flower", "Shrub",
    "Perennials", "Annual", "Aquatic", "Succulents", "Bulbs", "Climbers",
    "Vines", "Deciduous", "Biennials", "Houseplants", "Tropicals",
    "Shade Tree", "Fruit Tree", "Evergreen Tree"
]


class PlantTypeChar(dbus.service.Object):
    def __init__(self, bus, index, uuid, flags, service):
        self.path = service.path + f"/char{index}"
        self.bus = bus
        self.uuid = uuid
        self.flags = flags
        self.service = service
        self.value = 0  # Initial integer value
        dbus.service.Object.__init__(self, bus, self.path)

    def get_properties(self):
        return {
            "org.bluez.GattCharacteristic1": {
                "UUID": self.uuid,
                "Service": self.service.get_path(),
                "Flags": self.flags,
            }
        }

    def get_path(self):
        return dbus.ObjectPath(self.path)

    @dbus.service.method("org.bluez.GattCharacteristic1",
                         in_signature="a{sv}", out_signature="ay")
    def ReadValue(self, options):
        # Pack integer as 4-byte little-endian
        packed = struct.pack('<i', self.value)
        return [dbus.Byte(b) for b in packed]

    @dbus.service.method("org.bluez.GattCharacteristic1",
                         in_signature="aya{sv}")
    def WriteValue(self, value, options):
        global g_plant_type
        if len(value) == 4:
            written_value = struct.unpack('<i', bytes(value))[0]
            print(f"Set plant type to: {written_value}")
            if 0 <= written_value < len(plant_type_mapping):
                g_plant_type = plant_type_mapping[written_value]
                print('setting plant type to ', g_plant_type)
            else:
                print(f"Invalid plant type index: {written_value}")
        else:
            print(f"Received invalid byte array length: {len(value)}")


g_llm_backend = 0  # 0 for tinyllm, 1 for chatgpt


class LlmSelectionChar(dbus.service.Object):
    def __init__(self, bus, index, uuid, flags, service):
        self.path = service.path + f"/char{index}"
        self.bus = bus
        self.uuid = uuid
        self.flags = flags
        self.service = service
        self.value = 0  # Initial integer value, 0 for tinyllm
        dbus.service.Object.__init__(self, bus, self.path)

    def get_properties(self):
        return {
            "org.bluez.GattCharacteristic1": {
                "UUID": self.uuid,
                "Service": self.service.get_path(),
                "Flags": self.flags,
            }
        }

    def get_path(self):
        return dbus.ObjectPath(self.path)

    @dbus.service.method("org.bluez.GattCharacteristic1",
                         in_signature="a{sv}", out_signature="ay")
    def ReadValue(self, options):
        packed = struct.pack('<i', self.value)
        return [dbus.Byte(b) for b in packed]

    @dbus.service.method("org.bluez.GattCharacteristic1",
                         in_signature="aya{sv}")
    def WriteValue(self, value, options):
        global g_llm_backend
        if len(value) == 4:
            written_value = struct.unpack('<i', bytes(value))[0]
            print(f"Set llm backend to: {written_value}")
            if written_value == 0:
                g_llm_backend = 0
                print('setting llm backend to tinyllm')
            elif written_value == 1:
                g_llm_backend = 1
                print('setting llm backend to chatgpt')
            self.value = written_value
        else:
            print(
                f"Received invalid byte array length for LLM selection: {len(value)}")


class NumSuggestionsChar(dbus.service.Object):
    def __init__(self, bus, index, uuid, flags, service):
        self.path = service.path + f"/char{index}"
        self.bus = bus
        self.uuid = uuid
        self.flags = flags
        self.service = service
        self.value = g_num_suggestions  # Default to global
        dbus.service.Object.__init__(self, bus, self.path)

    def get_properties(self):
        return {
            "org.bluez.GattCharacteristic1": {
                "UUID": self.uuid,
                "Service": self.service.get_path(),
                "Flags": self.flags,
            }
        }

    def get_path(self):
        return dbus.ObjectPath(self.path)

    @dbus.service.method("org.bluez.GattCharacteristic1",
                         in_signature="a{sv}", out_signature="ay")
    def ReadValue(self, options):
        packed = struct.pack('<i', self.value)
        return [dbus.Byte(b) for b in packed]

    @dbus.service.method("org.bluez.GattCharacteristic1",
                         in_signature="aya{sv}")
    def WriteValue(self, value, options):
        global g_num_suggestions
        if len(value) == 4:
            written_value = struct.unpack('<i', bytes(value))[0]
            if 1 <= written_value <= 5:
                print(f"Set num suggestions to: {written_value}")
                g_num_suggestions = written_value
                self.value = written_value
            else:
                print(
                    f"Invalid number of suggestions: {written_value}. Must be between 1 and 5.")
        else:
            print(
                f"Received invalid byte array length for num suggestions: {len(value)}")


class IntWritableChar(dbus.service.Object):
    def __init__(self, bus, index, uuid, flags, service):
        self.path = service.path + f"/char{index}"
        self.bus = bus
        self.uuid = uuid
        self.flags = flags
        self.service = service
        self.value = 0  # Initial integer value
        dbus.service.Object.__init__(self, bus, self.path)

    def get_properties(self):
        return {
            "org.bluez.GattCharacteristic1": {
                "UUID": self.uuid,
                "Service": self.service.get_path(),
                "Flags": self.flags,
            }
        }

    def get_path(self):
        return dbus.ObjectPath(self.path)

    @dbus.service.method("org.bluez.GattCharacteristic1",
                         in_signature="a{sv}", out_signature="ay")
    def ReadValue(self, options):
        # Pack integer as 4-byte little-endian
        packed = struct.pack('<i', self.value)
        return [dbus.Byte(b) for b in packed]

    @dbus.service.method("org.bluez.GattCharacteristic1",
                         in_signature="aya{sv}")
    def WriteValue(self, value, options):
        # This characteristic is now only used to trigger generation.
        # The llm_status_char will be used for notifications.
        # It is passed in the constructor.
        global g_plant_type
        global g_llm_prompt
        if len(value) == 4:
            written_value = struct.unpack('<i', bytes(value))[0]
            print(f"Set integer value to: {written_value}")
            if written_value == 0:
                self.value = 1  # set offset to 1
                if not is_generating:
                    print('generating', g_plant_type)
                    g_llm_prompt = generate_plant_prompt(nit_val, phr_val, pot_val, ph=7.0, moisture=g_moisture_val, sun_intensity=g_light_val,
                                                         lat=location_lat, lon=location_lon, plant_type=g_plant_type, max_plants=g_num_suggestions)
                    print('sending llm req', g_llm_prompt)
                    # Pass the llm_status_char to the thread
                    thread = threading.Thread(target=generate_llm_response, args=(
                        g_llm_prompt, self.service.llm_status_char))
                    thread.daemon = True
                    thread.start()
                else:
                    print("Generation already in progress, ignoring new request.")
            else:
                self.value = written_value
        else:
            print(f"Received invalid byte array length: {len(value)}")


class LlmStatusChar(dbus.service.Object):
    def __init__(self, bus, index, uuid, flags, service):
        self.path = service.path + f"/char{index}"
        self.bus = bus
        self.uuid = uuid
        self.flags = flags
        self.service = service
        self.notifying = False
        self.status = 0  # 0: idle, 1: generating, 2: ready
        dbus.service.Object.__init__(self, bus, self.path)

    def get_properties(self):
        return {
            "org.bluez.GattCharacteristic1": {
                "UUID": self.uuid,
                "Service": self.service.get_path(),
                "Flags": self.flags,
            }
        }

    def get_path(self):
        return dbus.ObjectPath(self.path)

    def set_status(self, status):
        if status != self.status:
            self.status = status
            if self.notifying:
                self.PropertiesChanged("org.bluez.GattCharacteristic1", {
                                       "Value": [dbus.Byte(self.status)]}, [])

    @dbus.service.method("org.bluez.GattCharacteristic1", in_signature="a{sv}", out_signature="ay")
    def ReadValue(self, options):
        return [dbus.Byte(self.status)]

    @dbus.service.method("org.bluez.GattCharacteristic1", in_signature="", out_signature="")
    def StartNotify(self):
        print('llm status notif')
        if self.notifying:
            return
        self.notifying = True
        GLib.timeout_add_seconds(2, self._notify)

    @dbus.service.method("org.bluez.GattCharacteristic1", in_signature="", out_signature="")
    def StopNotify(self):
        self.notifying = False

    def _notify(self):
        if not self.notifying:
            return False
        packed = struct.pack('<f', self.status)
        print('llm status ', self.status)
        self.PropertiesChanged("org.bluez.GattCharacteristic1",
                               {"Value": [dbus.Byte(b) for b in packed]}, [])
        return True

    @dbus.service.signal("org.freedesktop.DBus.Properties", signature="sa{sv}as")
    def PropertiesChanged(self, interface, changed, invalidated):
        pass


class StringChar(dbus.service.Object):
    def __init__(self, bus, index, uuid, flags, service, suggest_char):
        self.path = service.path + f"/char{index}"
        self.bus = bus
        self.uuid = uuid
        self.flags = flags
        self.service = service
        self.suggest_char = suggest_char
        dbus.service.Object.__init__(self, bus, self.path)

    def get_properties(self):
        return {
            "org.bluez.GattCharacteristic1": {
                "UUID": self.uuid,
                "Service": self.service.get_path(),
                "Flags": self.flags,
            }
        }

    def get_path(self):
        return dbus.ObjectPath(self.path)

    @dbus.service.method("org.bluez.GattCharacteristic1",
                         in_signature="a{sv}", out_signature="ay")
    def ReadValue(self, options):
        offset = self.suggest_char.value
        if offset > 0:
            start_index = offset - 1
            end_index = start_index + 225
            chunk = current_llm_response[start_index:end_index]
            print(
                f"Reading llm response chunk (offset: {offset}): {chunk} total:{current_llm_response}")
            return [dbus.Byte(c) for c in chunk.encode('utf-8')]
        else:
            # Return empty or some default if offset is not set
            return []


class Advertisement(dbus.service.Object):
    def __init__(self, bus, index, service_uuid):
        self.path = f"/org/bluez/example/advertisement{index}"
        self.bus = bus
        self.service_uuid = service_uuid
        dbus.service.Object.__init__(self, bus, self.path)

    def get_properties(self):
        properties = {}
        properties['Type'] = dbus.String('peripheral')
        properties['ServiceUUIDs'] = dbus.Array([self.service_uuid], signature='s')
        properties['LocalName'] = dbus.String('demeter')
        properties['IncludeTxPower'] = dbus.Boolean(True)
        return {ADVERTISEMENT_INTERFACE: properties}

    def get_path(self):
        return dbus.ObjectPath(self.path)

    @dbus.service.method('org.freedesktop.DBus.Properties',
                         in_signature='s', out_signature='a{sv}')
    def GetAll(self, interface):
        if interface != ADVERTISEMENT_INTERFACE:
            raise dbus.exceptions.DBusException(
                'org.freedesktop.DBus.Error.InvalidArgs',
                'Invalid interface passed to GetAll')
        return self.get_properties()[interface]

    @dbus.service.method(ADVERTISEMENT_INTERFACE,
                         in_signature='', out_signature='')
    def Release(self):
        print("Advertisement released")


class Service(dbus.service.Object):
    def __init__(self, bus, index, uuid, primary):
        self.path = f"/org/bluez/example/service{index}"
        self.bus = bus
        self.uuid = uuid
        self.primary = primary
        self.characteristics = []
        self.llm_status_char = None
        self.image_status_char = None
        self.augmented_image_progress_char = None
        dbus.service.Object.__init__(self, bus, self.path)

    def get_properties(self):
        return {
            "org.bluez.GattService1": {
                "UUID": self.uuid,
                "Primary": self.primary
            }
        }

    def get_path(self):
        return dbus.ObjectPath(self.path)


class ADSSensor(threading.Thread):
    def __init__(self):
        super().__init__()
        self.duty_cycle_delay = .05
        self.running = True
        self.i2c = busio.I2C(board.SCL, board.SDA)
        self.ads = ADS.ADS1115(self.i2c)
        self.chan0 = AnalogIn(self.ads, ADS.P0)
        self.chan2 = AnalogIn(self.ads, ADS.P2)

    def stop(self):
        self.running = False

    def duty_cycle(self):
        global g_humidity_val
        global g_moisture_val
        try:
            # Scale raw ADC value to relative humidity percentage
            raw_humidity = self.chan0.value
            humidity_scaled = raw_humidity * HUMIDITY_SLOPE + HUMIDITY_INTERCEPT
            g_humidity_val = max(0.0, min(100.0, humidity_scaled))

            # Scale and invert moisture value based on calibration
            raw_moisture = self.chan2.value
            # Handles the inverted reading (lower ADC value means more moisture)
            moisture_scaled = 100.0 * \
                (MOISTURE_ADC_DRY - raw_moisture) / \
                (MOISTURE_ADC_DRY - MOISTURE_ADC_WET)

            # Clamp the value between 0 and 100
            g_moisture_val = max(0.0, min(100.0, moisture_scaled))
        except Exception as e:
            print(f"ADS sensor error: {e}")

    def run(self):
        while self.running:
            self.duty_cycle()
            time.sleep(self.duty_cycle_delay)


class BH1750Sensor(threading.Thread):
    def __init__(self):
        super().__init__()
        self.duty_cycle_delay = .05
        self.running = True
        self.i2c = busio.I2C(board.SCL, board.SDA)
        self.sensor = adafruit_bh1750.BH1750(self.i2c)

    def stop(self):
        self.running = False

    def duty_cycle(self):
        global g_light_val
        try:
            g_light_val = self.sensor.lux
        except Exception as e:
            print(f"BH1750 sensor error: {e}")

    def run(self):
        while self.running:
            self.duty_cycle()
            time.sleep(self.duty_cycle_delay)


class Application(dbus.service.Object):
    def __init__(self, bus):
        self.path = "/"
        self.services = []
        dbus.service.Object.__init__(self, bus, self.path)

    def get_path(self):
        return dbus.ObjectPath(self.path)

    def add_service(self, service):
        self.services.append(service)

    @dbus.service.method("org.freedesktop.DBus.ObjectManager",
                         out_signature="a{oa{sa{sv}}}")
    def GetManagedObjects(self):
        response = {}
        for service in self.services:
            response[service.get_path()] = service.get_properties()
            for char in service.characteristics:
                response[char.get_path()] = char.get_properties()
        return response


class NpkSensor(threading.Thread):
    def __init__(self):
        super().__init__()
        self.duty_cycle_delay = .05
        self.running = True

    def stop(self):
        self.running = False

    def parse_response(self, response):
        global nit_val
        global pot_val
        global phr_val
        if len(response) < 9:
            # print("Incomplete response:", response.hex())
            return

        byte_count = response[2]
        if byte_count != 6:
            # print("Unexpected byte count:", byte_count)
            return

        npk_raw = response[3:9]
        nit_val = struct.unpack(">H", npk_raw[0:2])[0]
        phr_val = struct.unpack(">H", npk_raw[2:4])[0]
        pot_val = struct.unpack(">H", npk_raw[4:6])[0]

        # print(f"Nitrogen:   {nit_val} mg/kg")
        # print(f"Phosphorus: {phr_val} mg/kg")
        # print(f"Potassium:  {pot_val} mg/kg")

    def read_npk(self):
        try:
            with serial.Serial(PORT, BAUDRATE, timeout=.1) as ser:
                ser.flushInput()

                # Enable transmit mode
                GPIO.output(TX_ENABLE_PIN, GPIO.HIGH)
                time.sleep(0.02)

                ser.write(modbus_request)
                ser.flush()

                # Switch to receive mode
                GPIO.output(TX_ENABLE_PIN, GPIO.LOW)
                time.sleep(0.02)
                response = ser.read(11)
                self.parse_response(response)

        except Exception as e:
            print(f"Communication error: {e}")

    def duty_cycle(self):
        # print ('starting duty cycle')
        try:
            self.read_npk()
        except Exception as e:
            GPIO.cleanup()
            raise e

    def run(self):
        while self.running:
            self.duty_cycle()
            time.sleep(self.duty_cycle_delay)


def main():
    dbus.mainloop.glib.DBusGMainLoop(set_as_default=True)
    bus = dbus.SystemBus()

    app = Application(bus)
    service = Service(bus, 0, SERVICE_UUID, True)
    char = Characteristic(bus, 0, CHAR_UUID, ["read", "notify"], service)
    service.characteristics.append(char)
    nit_char = NitChar(bus, 1,
                       "12345678-1234-5678-1234-56789abcdef2", ["read", "notify"], service)
    service.characteristics.append(nit_char)
    k_char = KChar(bus, 2,
                   "12345678-1234-5678-1234-56789abcdef3", ["read", "notify"], service)
    service.characteristics.append(k_char)
    p_char = PChar(bus, 3,
                   "12345678-1234-5678-1234-56789abcdef4", ["read", "notify"], service)
    service.characteristics.append(p_char)
    int_writable_char = IntWritableChar(bus, 4,
                                        "12345678-1234-5678-1234-56789abcdef5", ["read", "write"], service)
    service.characteristics.append(int_writable_char)
    llm_response_char = StringChar(bus, 5,
                                   "12345678-1234-5678-1234-56789abcdef6", ["read"], service, int_writable_char)
    service.characteristics.append(llm_response_char)
    ph_char = PhChar(bus, 6,
                     "12345678-1234-5678-1234-56789abcdef7", ["read", "notify"], service)
    service.characteristics.append(ph_char)
    humid_char = HumidChar(bus, 7,
                           "12345678-1234-5678-1234-56789abcdef8", ["read", "notify"], service)
    service.characteristics.append(humid_char)
    sun_char = SunChar(bus, 8,
                       "12345678-1234-5678-1234-56789abcdef9", ["read", "notify"], service)
    service.characteristics.append(sun_char)
    llm_status_char = LlmStatusChar(bus, 9,
                                    "12345678-1234-5678-1234-56789abcdeff", ["read", "notify"], service)
    service.characteristics.append(llm_status_char)
    plant_type_char = PlantTypeChar(bus, 10,
                                    "12345678-1234-5678-1234-56789abcdefa", ["write"], service)
    service.characteristics.append(plant_type_char)

    ground_moisture_char = GroundMoistureChar(bus, 11,
                                              "12345678-1234-5678-1234-56789abcdefb", ["read", "notify"], service)
    service.characteristics.append(ground_moisture_char)

    light_char = LightChar(bus, 12,
                           "12345678-1234-5678-1234-56789abcdefc", ["read", "notify"], service)
    service.characteristics.append(light_char)

    llm_selection_char = LlmSelectionChar(bus, 13,
                                          "12345678-1234-5678-1234-56789abcdefd", ["read", "write"], service)
    service.characteristics.append(llm_selection_char)

    num_suggestions_char = NumSuggestionsChar(bus, 14,
                                              "12345678-1234-5678-1234-56789abcdefe", ["read", "write"], service)
    service.characteristics.append(num_suggestions_char)

    image_char = ImageChar(bus, 15,
                           "12345678-1234-5678-1234-56789abcdff0", ["write"], service)
    service.characteristics.append(image_char)

    image_request_char = IntOffsetChar(bus, 16,
                                       "12345678-1234-5678-1234-56789abcdff1", ["write"], service)
    service.characteristics.append(image_request_char)

    augmented_image_char = AugmentedImageChar(bus, 17,
                                              "12345678-1234-5678-1234-56789abcdff2", ["read"], service, image_request_char)
    service.characteristics.append(augmented_image_char)

    image_status_char = ImageStatusChar(bus, 18,
                                        "12345678-1234-5678-1234-56789abcdff3", ["read", "notify"], service)
    service.characteristics.append(image_status_char)

    augmented_image_progress_char = AugmentedImageProgressChar(bus, 19,
                                                               "12345678-1234-5678-1234-56789abcdff4", ["read", "notify"], service)
    service.characteristics.append(augmented_image_progress_char)

    image_upload_progress_char = ImageUploadProgressChar(bus, 20,
                                                         "12345678-1234-5678-1234-56789abcdff5", ["write"], service)
    service.characteristics.append(image_upload_progress_char)

    ad = Advertisement(bus, 0, SERVICE_UUID)

    service.llm_status_char = llm_status_char
    service.image_status_char = image_status_char
    service.augmented_image_progress_char = augmented_image_progress_char
    app.add_service(service)

    adapter_path = "/org/bluez/hci0"
    gatt_manager = dbus.Interface(bus.get_object("org.bluez", adapter_path),
                                  "org.bluez.GattManager1")

    ad_manager = dbus.Interface(bus.get_object("org.bluez", adapter_path),
                                "org.bluez.LEAdvertisingManager1")

    ad_manager.RegisterAdvertisement(ad.get_path(), dbus.Dictionary({}, signature='sv'),
                                     reply_handler=lambda: print(
        "Advertisement registered"),
        error_handler=lambda e: print("Failed to register ad:", e))
    gatt_manager.RegisterApplication(app.get_path(), {},
                                     reply_handler=lambda: print(
                                         "GATT app registered"),
                                     error_handler=lambda e: print(f"Failed to register: {e}"))

    npk = NpkSensor()
    npk.start()

    ads_sensor = ADSSensor()
    ads_sensor.start()

    bh1750_sensor = BH1750Sensor()
    bh1750_sensor.start()

    GLib.MainLoop().run()
    npk.stop()
    ads_sensor.stop()
    bh1750_sensor.stop()


if __name__ == "__main__":
    main()

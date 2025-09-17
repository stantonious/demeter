#!/usr/bin/env python3

import dbus
import dbus.mainloop.glib
import dbus.service
from gi.repository import GLib
import threading
import time
import struct
import numpy as np

SERVICE_UUID = "12345678-1234-5678-1234-56789abcdef0"
CHAR_UUID = "12345678-1234-5678-1234-56789abcdef1"
CHAR_VALUE = [dbus.Byte(0x00)]

sensor_val = 0

def sensor_task():
    global sensor_val
    while 1:
        time.sleep(1)
        sensor_val += 1

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
            print ('ret')
            return False
        print ('notif')
        self.PropertiesChanged("org.bluez.GattCharacteristic1",
                               {"Value": [(sensor_val + 1) % 255]}, [])
        return True

    @dbus.service.signal("org.freedesktop.DBus.Properties",
                         signature="sa{sv}as")
    def PropertiesChanged(self, interface, changed, invalidated):
        pass

class FloatCharacteristic(dbus.service.Object):
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
        self.value += np.random.rand() -.5
        packed = struct.pack('<f', self.value)
        self.PropertiesChanged("org.bluez.GattCharacteristic1",
                               {"Value": [dbus.Byte(b) for b in packed]}, [])
        return True

    @dbus.service.signal("org.freedesktop.DBus.Properties",
                         signature="sa{sv}as")
    def PropertiesChanged(self, interface, changed, invalidated):
        pass

class Service(dbus.service.Object):
    def __init__(self, bus, index, uuid, primary):
        self.path = f"/org/bluez/example/service{index}"
        self.bus = bus
        self.uuid = uuid
        self.primary = primary
        self.characteristics = []
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

def main():
    dbus.mainloop.glib.DBusGMainLoop(set_as_default=True)
    bus = dbus.SystemBus()

    app = Application(bus)
    service = Service(bus, 0, SERVICE_UUID, True)
    char = Characteristic(bus, 0, CHAR_UUID, ["read", "notify"], service)
    float_char = FloatCharacteristic(bus, 1,
    "12345678-1234-5678-1234-56789abcdef2", ["read", "notify"], service)
    service.characteristics.append(float_char)
    service.characteristics.append(char)
    app.add_service(service)

    adapter_path = "/org/bluez/hci0"
    gatt_manager = dbus.Interface(bus.get_object("org.bluez", adapter_path),
                                  "org.bluez.GattManager1")

    gatt_manager.RegisterApplication(app.get_path(), {},
                                     reply_handler=lambda: print("GATT app registered"),
                                     error_handler=lambda e: print(f"Failed to register: {e}"))

    t1 = threading.Thread(target=sensor_task,args=())
    t1.start()
    GLib.MainLoop().run()

if __name__ == "__main__":
    main()
import dbus
import dbus.mainloop.glib
import dbus.service
import random
from gi.repository import GLib

BLUEZ_SERVICE_NAME = 'org.bluez'
GATT_MANAGER_IFACE = 'org.bluez.GattManager1'
GATT_SERVICE_IFACE = 'org.bluez.GattService1'
GATT_CHARACTERISTIC_IFACE = 'org.bluez.GattCharacteristic1'
DBUS_OM_IFACE = 'org.freedesktop.DBus.ObjectManager'

class Application(dbus.service.Object):
    def __init__(self, bus):
        self.path = '/'
        self.services = []
        dbus.service.Object.__init__(self, bus, self.path)

    def get_path(self):
        return dbus.ObjectPath(self.path)

    def add_service(self, service):
        self.services.append(service)

    @dbus.service.method(DBUS_OM_IFACE, out_signature='a{oa{sa{sv}}}')
    def GetManagedObjects(self):
        response = {}
        for service in self.services:
            response[service.get_path()] = service.get_properties()
            for char in service.characteristics:
                response[char.get_path()] = char.get_properties()
        return response

class SensorService(dbus.service.Object):
    def __init__(self, bus, index):
        self.path = f'/org/bluez/example/service{index}'
        self.uuid = '12345678-1234-5678-1234-56789abcdef0'
        self.characteristics = []
        dbus.service.Object.__init__(self, bus, self.path)
        self.characteristics.append(SensorCharacteristic(bus, 0, self))

    def get_path(self):
        return dbus.ObjectPath(self.path)

    def get_properties(self):
        return {
            GATT_SERVICE_IFACE: {
                'UUID': self.uuid,
                'Primary': True,
            }
        }

class SensorCharacteristic(dbus.service.Object):
    def __init__(self, bus, index, service):
        self.path = service.path + f'/char{index}'
        self.uuid = '12345678-1234-5678-1234-56789abcdef1'
        self.service = service
        self.notifying = False
        dbus.service.Object.__init__(self, bus, self.path)

    def get_path(self):
        return dbus.ObjectPath(self.path)

    def get_properties(self):
        return {
            GATT_CHARACTERISTIC_IFACE: {
                'UUID': self.uuid,
                'Service': self.service.get_path(),
                'Flags': ['read', 'notify', 'encrypt-read', 'encrypt-notify']
            }
        }

    @dbus.service.method(GATT_CHARACTERISTIC_IFACE, in_signature='', out_signature='ay')
    def ReadValue(self):
        return self.get_sensor_data()

    @dbus.service.method(GATT_CHARACTERISTIC_IFACE, in_signature='', out_signature='')
    def StartNotify(self):
        if self.notifying:
            return
        self.notifying = True
        self.send_notification()

    def send_notification(self):
        if not self.notifying:
            return
        value = self.get_sensor_data()
        self.PropertiesChanged(GATT_CHARACTERISTIC_IFACE, {'Value': value}, [])
        GLib.timeout_add_seconds(2, self.send_notification)

    @dbus.service.method(GATT_CHARACTERISTIC_IFACE, in_signature='', out_signature='')
    def StopNotify(self):
        self.notifying = False

    def get_sensor_data(self):
        temp = round(random.uniform(20.0, 30.0), 2)
        humidity = round(random.uniform(30.0, 60.0), 2)
        return [dbus.Byte(c) for c in f"{temp},{humidity}".encode()]

    @dbus.service.signal(dbus_interface='org.freedesktop.DBus.Properties', signature='sa{sv}as')
    def PropertiesChanged(self, interface, changed, invalidated):
        pass

def register_app_cb():
    print("✅ GATT application registered")

def register_app_error_cb(error):
    print(f"❌ Failed to register application: {error}")

def main():
    dbus.mainloop.glib.DBusGMainLoop(set_as_default=True)
    bus = dbus.SystemBus()

    adapter_path = '/org/bluez/hci0'
    service_manager = dbus.Interface(bus.get_object(BLUEZ_SERVICE_NAME, adapter_path), GATT_MANAGER_IFACE)

    app = Application(bus)
    sensor_service = SensorService(bus, 0)
    app.add_service(sensor_service)

    service_manager.RegisterApplication(app.get_path(), {}, reply_handler=register_app_cb, error_handler=register_app_error_cb)

    GLib.MainLoop().run()

if __name__ == '__main__':
    main()
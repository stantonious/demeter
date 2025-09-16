
import asyncio
import random
from dbus_next.aio import MessageBus
from dbus_next import Variant, BusType, PropertyAccess
from dbus_next.service import ServiceInterface, method, dbus_property
from dbus_next.introspection import Node

SERVICE_UUID = '12345678-1234-5678-1234-56789abcdef0'
CHAR_UUID = '12345678-1234-5678-1234-56789abcdef1'

class Characteristic(ServiceInterface):
    def __init__(self, index, uuid, flags, service_path):
        super().__init__('org.bluez.GattCharacteristic1')
        self.uuid = uuid
        self.flags = flags
        self.value = bytes([0])
        self.notifying = False
        self.path = f'{service_path}/char{index}'

    @dbus_property(access=PropertyAccess.READ)
    def UUID(self) -> 's':
        return self.uuid

    @dbus_property(access=PropertyAccess.READ)
    def Service(self) -> 'o':
        return self.path.rsplit('/char', 1)[0]

    @dbus_property(access=PropertyAccess.READ)
    def Flags(self) -> 'as':
        return self.flags

    @dbus_property(access=PropertyAccess.READ)
    def Value(self) -> 'ay':
        return self.value

    @method()
    def StartNotify(self):
        if self.notifying:
            return
        self.notifying = True
        asyncio.create_task(self._notify_loop())

    @method()
    def StopNotify(self):
        self.notifying = False

    async def _notify_loop(self):
        while self.notifying:
            self.value = bytes([random.randint(0, 255)])
            self.emit_properties_changed({'Value': Variant('ay', self.value)})
            print(f"🔔 Notifying value: {self.value[0]}")
            await asyncio.sleep(1)

class Service(ServiceInterface):
    def __init__(self, index, uuid, primary=True):
        super().__init__('org.bluez.GattService1')
        self.uuid = uuid
        self.primary = primary
        self.path = f'/org/bluez/example/service{index}'

    @dbus_property(access=PropertyAccess.READ)
    def UUID(self) -> 's':
        return self.uuid

    @dbus_property(access=PropertyAccess.READ)
    def Primary(self) -> 'b':
        return self.primary

async def main():
    bus = await MessageBus(bus_type=BusType.SYSTEM).connect()

    # Create service and characteristic
    service = Service(0, SERVICE_UUID)
    characteristic = Characteristic(0, CHAR_UUID, ['notify'], service.path)

    # Export to D-Bus
    bus.export(service.path, service)
    bus.export(characteristic.path, characteristic)

    # Introspect and register with BlueZ
    introspection = await bus.introspect('org.bluez', '/org/bluez')
    proxy = bus.get_proxy_object('org.bluez', '/org/bluez', introspection)
    gatt_manager = proxy.get_interface('org.bluez.GattManager1')

    await gatt_manager.call_register_application(
        {'/org/bluez/example/service0': {}}, {}
    )

    print("✅ BLE GATT server running with notify characteristic...")
    while True:
        await asyncio.sleep(1)

if __name__ == '__main__':
    asyncio.run(main())
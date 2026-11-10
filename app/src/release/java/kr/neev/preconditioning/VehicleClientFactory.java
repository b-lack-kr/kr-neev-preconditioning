package kr.neev.preconditioning;

final class VehicleClientFactory {
    static VehiclePort create() { return new OemVehicleClient(); }
}

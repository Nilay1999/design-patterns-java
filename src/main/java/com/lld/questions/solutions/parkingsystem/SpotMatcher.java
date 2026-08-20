package com.lld.questions.solutions.parkingsystem;

import java.util.List;
import java.util.Map;

public class SpotMatcher {
    private static final Map<VehicleType, List<SpotType>> COMPATIBLE = Map.of(
            VehicleType.LIGHT, List.of(SpotType.SMALL, SpotType.MEDIUM, SpotType.LARGE),
            VehicleType.MEDIUM, List.of(SpotType.MEDIUM, SpotType.LARGE),
            VehicleType.HEAVY, List.of(SpotType.LARGE));

    public static List<SpotType> compatibleSpots(VehicleType type) {
        return COMPATIBLE.get(type);
    }
}

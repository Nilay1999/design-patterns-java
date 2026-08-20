package com.lld.questions.solutions.elevatorsystem;

import java.util.ArrayList;
import java.util.List;

public class ElevatorSystem {
    private final List<Elevator> elevators;

    public ElevatorSystem(int numElevators) {
        elevators = new ArrayList<>();
        for (int i = 0; i < numElevators; i++) {
            elevators.add(new Elevator(i));
        }
    }

    public void submitExternalRequest(ExternalRequest request) {
        Elevator nearest = elevators.get(0);
        int minDistance = Integer.MAX_VALUE;
        for (Elevator elevator : elevators) {
            int distance = Math.abs(elevator.getCurrentFloor() - request.getFloor());
            if (distance < minDistance) {
                minDistance = distance;
                nearest = elevator;
            }
        }
        nearest.addRequest(request.getFloor());
    }

    public void submitInternalRequest(int elevatorId, InternalRequest request) {
        elevators.get(elevatorId).addRequest(request.getTargetFloor());
    }

    public void step() {
        for (Elevator elevator : elevators) {
            elevator.step();
        }
    }

    public List<Elevator> getElevators() {
        return elevators;
    }
}

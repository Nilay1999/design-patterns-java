package com.lld.questions.solutions.elevatorsystem;

public class Main {
    public static void main(String[] args) {
        ElevatorSystem system = new ElevatorSystem(2);

        system.submitExternalRequest(new ExternalRequest(5, Direction.UP));
        system.submitInternalRequest(0, new InternalRequest(8));
        system.submitExternalRequest(new ExternalRequest(3, Direction.DOWN));

        for (int tick = 1; tick <= 15; tick++) {
            system.step();
            for (Elevator elevator : system.getElevators()) {
                System.out.printf(
                        "tick=%2d elevator=%d floor=%2d dir=%-4s status=%-9s stops=%s%n",
                        tick, elevator.getId(), elevator.getCurrentFloor(),
                        elevator.getDirection(), elevator.getStatus(),
                        elevator.getStops());
            }
        }
    }
}

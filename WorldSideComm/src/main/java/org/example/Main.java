package org.example;

import gpb.WorldUps;

import java.io.IOException;
import java.util.List;


public class Main {
    public static void main(String[] args) {
        String host = "localhost";
        int port = 12345;

        WorldSimulatorClient client = new WorldSimulatorClient(host, port);

        WorldUps.UInitTruck truck = WorldUps.UInitTruck.newBuilder().setId(1).setX(50).setY(30).build();
        try {
            client.connectToWorld(1, List.of(truck));

//            WorldUps.UCommands commands = client.receiveWorldUpdate();

        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                client.disconnect();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
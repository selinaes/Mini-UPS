package org.example;

import gpb.WorldUps;

import java.io.IOException;
import java.util.List;


public class Main {
    public static void main(String[] args) {
        String host = "localhost";
        int port = 12345;
        int amazonPort = 34567;

        long seqNumWorld = 0;
        long seqNumAmazon = 0;

        WorldSimulatorClient client = new WorldSimulatorClient(host, port);

        // Step 0: UInitTruck
        WorldUps.UInitTruck truck = WorldUps.UInitTruck.newBuilder().setId(1).setX(50).setY(30).build();
        try {
            // Step 1: UConnect
            long worldId = client.connectToWorld(List.of(truck));

            // Step 2: With Amazon - UAinitWorld
            ToAmazonClient toAmazonClient = new ToAmazonClient(host, amazonPort);
            toAmazonClient.connectToAmazon();
            toAmazonClient.sendWorldId(worldId, seqNumAmazon++); // return true false - whether connect successfully to same world

            // Step 3: With Amazon - Listen and expect AUcommands

            // Step 4: World - UGoPickup

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
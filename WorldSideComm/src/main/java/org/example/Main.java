package org.example;

import gpb.WorldUps;

import java.io.IOException;
import java.util.List;


public class Main {
    public static void main(String[] args) {
        String host = "localhost";
        int worldPort = 12345;
        int amazonPort = 34567;



        WorldSimulatorClient client = new WorldSimulatorClient(host, worldPort);


        try {
            // Step 2: Listen for connection from amazon. Everything else in server
            ListenAmazonServer listenAmazonServer = new ListenAmazonServer(amazonPort, client);
            listenAmazonServer.run();


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
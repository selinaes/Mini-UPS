package org.example;


import java.io.IOException;


public class Main {
    public static void main(String[] args) {
//        String worldHost = "localhost";
        String worldHost = "vcm-33562.vm.duke.edu";
        int worldPort = 12345;
        int amazonPort = 34567;
        System.out.println(System.getProperty("user.dir"));
        WorldSimulatorClient client = new WorldSimulatorClient(worldHost, worldPort);

        try {
            // Step 1: Listen for connection from amazon. Everything else in server
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
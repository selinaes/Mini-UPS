package org.example;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.List;

import gpb.WorldUps;
public class WorldSimulatorClient {
    private final String host;
    private final int port;
    private Socket socket;

    private InputStream inputStream;
    private OutputStream outputStream;

    public WorldSimulatorClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public long connectToWorld(List<WorldUps.UInitTruck> trucks) throws IOException {
        // Connect to the world using UConnect
        socket = new Socket(host, port);
        inputStream = socket.getInputStream();
        outputStream = socket.getOutputStream();
        WorldUps.UConnect uConnect = WorldUps.UConnect.newBuilder().addAllTrucks(trucks).setIsAmazon(false).build();
        send(uConnect);

        // Wait for UConnected response
        WorldUps.UConnected uConnected = read(WorldUps.UConnected.parser());
        System.out.println("Result of connection: " + uConnected.getResult());
        System.out.println("Connected to world " + uConnected.getWorldid());
        return uConnected.getWorldid();
    }

    public WorldUps.UResponses sendCommands(WorldUps.UCommands uCommands) throws IOException {
        // Send UCommand
        send(uCommands);

        // Wait for UResponse
        return read(WorldUps.UResponses.parser());
    }

    private <T> void send(com.google.protobuf.MessageLite message) throws IOException {
        message.writeDelimitedTo(outputStream);
        outputStream.flush();
    }

    private <T> T read(com.google.protobuf.Parser<T> parser) throws IOException {
        return parser.parseDelimitedFrom(inputStream);
    }

    public WorldUps.UResponses readResponses() throws IOException {
        WorldUps.UResponses uResponses = read(WorldUps.UResponses.parser());
        System.out.println("Received responses: " + uResponses);
        return uResponses;
    }




    public void disconnect() throws IOException {
        if (socket != null) {
            socket.close();
        }
    }
}

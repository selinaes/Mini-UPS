package org.example;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

import gpb.UpsAmazon;

public class TestAmazonClient {

    private final String host;
    private final int port;
    private Socket socket;

    private InputStream inputStream;
    private OutputStream outputStream;

    public TestAmazonClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public void connectToUPS() throws IOException {
        socket = new Socket(host, port);
        inputStream = socket.getInputStream();
        outputStream = socket.getOutputStream();
    }

    // send AUconnectedWorld
    public void sendConnectedWorld() throws IOException {
        UpsAmazon.AUconnectedWorld aUconnectedWorld = UpsAmazon.AUconnectedWorld.newBuilder()
                .setSuccess(true).build();
        send(aUconnectedWorld);
    }

    //generate AUbindUPS

    //generate AUreqPickup

    //generate AUreqDelivery

    //generate AUquery

    //generate AUchangeDestn

    //form AUcommands
    

    //recv UAcommands
    public void recvUACommands() throws IOException {
        UpsAmazon.UAcommands commands = read(UpsAmazon.UAcommands.parser());
        System.out.println("Acked result: " + commands.getAcksList());
    }



    private <T> void send(com.google.protobuf.MessageLite message) throws IOException {
        message.writeDelimitedTo(outputStream);
        outputStream.flush();
    }

    private <T> T read(com.google.protobuf.Parser<T> parser) throws IOException {
        return parser.parseDelimitedFrom(inputStream);
    }

    //main class
    public static void main(String[] args) throws IOException {
        TestAmazonClient client = new TestAmazonClient("localhost", 34567);
        client.connectToUPS();
        client.sendConnectedWorld();
    }


}

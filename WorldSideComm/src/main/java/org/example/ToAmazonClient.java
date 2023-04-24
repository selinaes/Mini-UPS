package org.example;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.List;

import gpb.UpsAmazon;

public class ToAmazonClient {

    private final String host;
    private final int port;
    private Socket socket;

    private InputStream inputStream;
    private OutputStream outputStream;

    public ToAmazonClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public void connectToAmazon() throws IOException {
        // Connect to the world using UConnect
        socket = new Socket(host, port);
        inputStream = socket.getInputStream();
        outputStream = socket.getOutputStream();
    }

    // 没处理success是false
    public boolean sendWorldId(long worldId, long seqNum) throws IOException {
        UpsAmazon.UAinitWorld initWorld = UpsAmazon.UAinitWorld.newBuilder()
                .setWorldID(worldId).setSeqNum(seqNum)
                .build();
        send(initWorld);

        // Wait for AUconnectedWorld response
        UpsAmazon.AUconnectedWorld connectedWorld = read(UpsAmazon.AUconnectedWorld.parser());
        System.out.println("Amazon's result: " + connectedWorld.getSuccess());
        if (connectedWorld.getSuccess() && connectedWorld.getAcksList().contains(seqNum)) {
            return true;
        } else {
            return false;
        }
    }
    
    



    private <T> void send(com.google.protobuf.MessageLite message) throws IOException {
        message.writeDelimitedTo(outputStream);
        outputStream.flush();
    }

    private <T> T read(com.google.protobuf.Parser<T> parser) throws IOException {
        return parser.parseDelimitedFrom(inputStream);
    }


}

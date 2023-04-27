package org.example;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

import gpb.UpsAmazon;
import com.google.protobuf.GeneratedMessageV3;

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
    public UpsAmazon.AUreqPickup generateAUreqPickup(int whID, int destX, int destY, long shipID, int upsID, long seqNum){
        UpsAmazon.AProduct product = UpsAmazon.AProduct.newBuilder().setId(1).setCount(2).setDescription("item").build();
        return UpsAmazon.AUreqPickup.newBuilder().setWhID(whID).setDestinationX(destX)
                .setDestinationY(destY).setShipID(shipID).setUpsID(upsID).setSeqNum(seqNum)
                .addProducts(product).build();
    }

    //generate AUreqDelivery

    //generate AUquery

    //generate AUchangeDestn

    //add to AUcommand
    public UpsAmazon.AUcommands.Builder addToAUcommands(UpsAmazon.AUcommands.Builder builder, GeneratedMessageV3 message){
        if (message instanceof UpsAmazon.AUreqPickup pickup) {
            builder.addPickup(pickup);
        }
        else if (message instanceof UpsAmazon.AUbindUPS bind) {
            builder.addBind(bind);
        }
        else if (message instanceof UpsAmazon.AUquery query) {
            builder.addQuery(query);
        }
        else if (message instanceof UpsAmazon.AUreqDelivery delivery) {
            builder.addDelivery(delivery);
        }
        else if (message instanceof UpsAmazon.AUchangeDestn changeDest) {
            builder.addChangeDest(changeDest);
        }
        return builder;
    }
    

    //recv UAcommands
    public void recvUACommands() throws IOException {
        UpsAmazon.UAcommands commands = read(UpsAmazon.UAcommands.parser());
        System.out.println("Acked result: " + commands.getAcksList());
    }

    public void sendAUcommands(UpsAmazon.AUcommands commands) throws IOException {
        send(commands);
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
        TestAmazonClient testAmazonClient = new TestAmazonClient("localhost", 34567);
        testAmazonClient.connectToUPS();

        testAmazonClient.sendConnectedWorld();

        UpsAmazon.AUreqPickup pickup = testAmazonClient.generateAUreqPickup(1, 50, 50, 1, 1, 1);
        UpsAmazon.AUcommands.Builder builder = UpsAmazon.AUcommands.newBuilder();
        testAmazonClient.addToAUcommands(builder, pickup);
        testAmazonClient.sendAUcommands(builder.build());
    }


}

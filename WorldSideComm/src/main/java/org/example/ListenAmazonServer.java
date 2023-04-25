package org.example;

import org.example.models.Truck;
import org.hibernate.Session;
import org.xml.sax.SAXException;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import javax.xml.parsers.ParserConfigurationException;

import gpb.UpsAmazon;
import gpb.WorldUps;

public class ListenAmazonServer {
    private final ServerSocket serverSocket;
    BlockingQueue<Runnable> workQueue = new LinkedBlockingQueue<Runnable>(100);
    ThreadPoolExecutor executor = new ThreadPoolExecutor(4, 4, 5, TimeUnit.MILLISECONDS, workQueue);
    WorldSimulatorClient worldClient;
    long worldId;
    long seqNumWorld;
    long seqNumAmazon;


    public ListenAmazonServer(int port, WorldSimulatorClient client) throws IOException {
        this.serverSocket = new ServerSocket(port);
        this.worldClient = client;
        this.seqNumAmazon = 0;
        this.seqNumWorld = 0;
    }

    public void run() {
        System.out.println("ListenAmazonServer started");
        while (!Thread.currentThread().isInterrupted()) {
            final Socket client_socket = acceptOrNull();

            if (client_socket == null) {
                continue;
            }
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        // do something with client_socket
                        synchronized (this) {
                            System.out.println("Client amazon accepted");
                        }
                        handleClient(client_socket);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    finally {
                        try {
                            client_socket.close();
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }
                }
            });
        }
    }

    /**
    * Send the wordId to the amazon and check for for the AUconnectedWorld response
    */
    public boolean connectSameWorld(long worldId, Socket clientSock) throws IOException {
        UpsAmazon.UAinitWorld initWorld = UpsAmazon.UAinitWorld.newBuilder()
                .setWorldID(worldId)
                .build();
        send(initWorld, clientSock);

        // Wait for AUconnectedWorld response
        UpsAmazon.AUconnectedWorld connectedWorld = read(UpsAmazon.AUconnectedWorld.parser(), clientSock);
        System.out.println("Amazon's result: " + connectedWorld.getSuccess());
        if (connectedWorld.getSuccess()) {
            return true;
        } else {
            return false;
        }
    }

    /**
    * Send the wordId to the amazon and check for for the AUconnectedWorld response
    */
    public static List<WorldUps.UInitTruck> initTrucks(int number) {
        List<WorldUps.UInitTruck> trucks = new ArrayList<>();
        for (int i = 1; i <= number; i++){
            // Step 0: UInitTruck
            WorldUps.UInitTruck truck = WorldUps.UInitTruck.newBuilder().setId(1).setX(50).setY(30).build();
            // add DB operation, add to DB
            DBoperations.createNewTruck(truck);
            trucks.add(truck);
        }
        return trucks;
    }

    public void handleClient(Socket client_socket) throws IOException {
        List<WorldUps.UInitTruck> trucks = initTrucks(1);

        // Step 1: UConnect
        long worldId = worldClient.connectToWorld(trucks);

       // UAinitWorld, make sure connect to same world
        while (true) {
            boolean result = connectSameWorld(worldId,client_socket);
            if (result) {
                this.worldId = worldId; // not sure need or not
                break;
            }
            worldId = worldClient.connectToWorld(trucks);
        }

        // continuously read from input stream
        while (!Thread.currentThread().isInterrupted()) {
            UpsAmazon.AUcommands aUcommands = read(UpsAmazon.AUcommands.parser(), client_socket); // 需要验证，如果下一条没有会不会出问题
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    // handle each situation with world
                    for (UpsAmazon.AUreqPickup pickup : aUcommands.getPickupList()) {
                        System.out.println("pickup");
                        handlePickup(pickup);
                    }

                    // send back UAcommands
                }
            });
        }


    }

    public void handlePickup(UpsAmazon.AUreqPickup pickup) {
        // retrieve whid
        int whid = pickup.getWhID();

        // in DB, find a truckID that is available. Now just use 1 注意用hibernate session处理DB
        Truck usedTruck = DBoperations.useAvailableTruck();
        int truckID = usedTruck.getTruck_id();
//        int truckID = 1;

        // create a new shipment, save to DB 注意用hibernate session处理DB
        long shipmentID = pickup.getShipID();
        int destX = pickup.getDestinationX();
        int destY = pickup.getDestinationY();
        String status = "created";


        // UGoPickup to world
        long pickupSeqNum = seqNumWorld++;
        WorldUps.UGoPickup uGoPickup = WorldUps.UGoPickup.newBuilder()
                .setTruckid(truckID).setWhid(whid).setSeqnum(pickupSeqNum).build(); // 注意seqnum需要synchronize
        // 注意应该存这个seqnum和对应的信息，uGoPickup全文。这样后面可以继续发直到对方收到
        // 注意改成集成队列，放入某个uCommands的list一起发。现在暂时用单个的
        WorldUps.UCommands uCommands = WorldUps.UCommands.newBuilder().addPickups(uGoPickup).build();
        try {
            worldClient.sendCommands(uCommands);
            // block wait for UResponses with UFinished. 注意放进集成之后就可以写成async的
            WorldUps.UResponses uResponses = worldClient.readResponses();
            if (uResponses.getAcksList().contains(pickupSeqNum)){
                System.out.println("world received UGoPickup");
                // 仍然需要一直接收UResponses，直到收到对应的UFinished，然后再给amazon回复UAtruckArrived
            }
            else {
                System.out.println("world did not receive UGoPickup");
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }


    }

    private <T> void send(com.google.protobuf.MessageLite message, Socket client_socket) throws IOException {
        OutputStream outputStream = client_socket.getOutputStream();
        message.writeDelimitedTo(outputStream);
        outputStream.flush();
    }

    private <T> T read(com.google.protobuf.Parser<T> parser, Socket client_socket) throws IOException {
        InputStream inputStream = client_socket.getInputStream();
        return parser.parseDelimitedFrom(inputStream);
    }

    /**
     * This is a helper method to accept a socket from the ServerSocket or return null if it
     * timeouts.
     *
     * @return the socket accepted from the ServerSocket
     */
    public Socket acceptOrNull() {
        try {
            return serverSocket.accept();
        } catch (IOException ioe) {
            return null;
        }
    }

    // main function
    public static void main(String[] args) throws IOException {
        initTrucks(1);
    }
}







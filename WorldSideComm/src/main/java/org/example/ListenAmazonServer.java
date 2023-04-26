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
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

import javax.xml.parsers.ParserConfigurationException;

import gpb.UpsAmazon;
import gpb.WorldUps;

public class ListenAmazonServer {
    private final ServerSocket serverSocket;
    BlockingQueue<Runnable> workQueue = new LinkedBlockingQueue<Runnable>(100);
    ThreadPoolExecutor executor = new ThreadPoolExecutor(5, 5, 5, TimeUnit.MILLISECONDS, workQueue);
    // Create a ScheduledExecutorService with 1 thread
    ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    WorldSimulatorClient worldClient;
    long worldId;


    public ListenAmazonServer(int port, WorldSimulatorClient client) throws IOException {
        this.serverSocket = new ServerSocket(port);
        this.worldClient = client;
    }

    /**
    * Formed Ucommands message to send to the UPS client to solve
    */
    public WorldUps.UCommands formWorldMessage() {
        if (GlobalVariables.worldMessages.isEmpty()) {
            return null;
        }
        WorldUps.UCommands.Builder uCommandsBuilder = WorldUps.UCommands.newBuilder();
        // Loop through the ConcurrentHashMap
        for (com.google.protobuf.GeneratedMessageV3 message : GlobalVariables.worldMessages.values()) {
            // Add UGoPickup messages
            if (message instanceof WorldUps.UGoPickup pickup) {
                uCommandsBuilder.addPickups(pickup);
            }
            // Add UGoDeliver messages
            else if (message instanceof WorldUps.UGoDeliver delivery) {
                uCommandsBuilder.addDeliveries(delivery);
            }
            // Add UQuery messages
            else if (message instanceof WorldUps.UQuery query) {
                uCommandsBuilder.addQueries(query);
            }
            else {
                System.out.println("Unknown message type: " + message.getClass().getName());
            }
        }

        GlobalVariables.worldAckLock.lock();
        uCommandsBuilder.addAllAcks(GlobalVariables.worldAcks);
        GlobalVariables.worldAcks.clear();
        GlobalVariables.worldAckLock.unlock();

        return uCommandsBuilder.build();
    }

    /**
    * Formed UAcommands message to send to the Amazon client for handling
    */
    public UpsAmazon.UAcommands formAmazonMessage() {
        if (GlobalVariables.amazonMessages.isEmpty()) {
            return null;
        }
        UpsAmazon.UAcommands.Builder UACommandsBuilder = UpsAmazon.UAcommands.newBuilder();
        // Loop through the ConcurrentHashMap
        for (com.google.protobuf.GeneratedMessageV3 message : GlobalVariables.amazonMessages.values()) {
            // Add UGoPickup messages
            if (message instanceof UpsAmazon.UAtruckArrived truckArr) {
                UACommandsBuilder.addTruckArr(truckArr);
            }
            // Add UGoDeliver messages
            else if (message instanceof UpsAmazon.UAstatus status) {
                UACommandsBuilder.addStatus(status);
            }
            // Add UQuery messages
            else if (message instanceof UpsAmazon.UAdelivered delivered) {
                UACommandsBuilder.addDelivered(delivered);
            }
            else if (message instanceof UpsAmazon.UAbindUPSResponse bindRes){
                UACommandsBuilder.addBindUPSResponse(bindRes);
            }
            else if (message instanceof  UpsAmazon.UAchangeResp changeResp) {
                UACommandsBuilder.addChangeResp(changeResp);
            }
            else {
                System.out.println("Unknown message type: " + message.getClass().getName());
            }
        }

        GlobalVariables.amazonAckLock.lock();
        UACommandsBuilder.addAllAcks(GlobalVariables.amazonAcks);
        GlobalVariables.amazonAcks.clear();
        GlobalVariables.amazonAckLock.unlock();
        return UACommandsBuilder.build();
    }

    public void run() {
        System.out.println("ListenAmazonServer started");

        while (!Thread.currentThread().isInterrupted()) {

            final Socket client_socket = acceptOrNull();

            if (client_socket == null) {
                continue;
            }

            // Thread, for each client Amazon handle it
            executor.execute(() -> {
                try {
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
            });
        }
    }

    /**
    * Send the wordId to the amazon and check for the AUconnectedWorld response
    */
    public boolean connectSameWorld(long worldId, Socket clientSock) throws IOException {
        UpsAmazon.UAinitWorld initWorld = UpsAmazon.UAinitWorld.newBuilder()
                .setWorldID(worldId)
                .build();
        send(initWorld, clientSock);

        // Wait for AUconnectedWorld response
        UpsAmazon.AUconnectedWorld connectedWorld = read(UpsAmazon.AUconnectedWorld.parser(), clientSock);
        System.out.println("Amazon's result: " + connectedWorld.getSuccess());
        return connectedWorld.getSuccess();
    }


    /**
    * Handle Client: UConnect from the
    */
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

        // Thread, always listen to World and handle updates
        executor.execute(() -> {
                    worldClient.listenHandleUpdates();
                }
        );

        // Schedule a task to be executed after a certain delay
        int delayInSeconds = 5; // Adjust the delay as needed
        scheduler.schedule(() -> {
            // Call formWorldMessage() and send the message
            WorldUps.UCommands worldMessage = formWorldMessage();
            if (worldMessage == null) {
                return;
            }
            try {
                worldClient.sendCommands(worldMessage);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }, delayInSeconds, TimeUnit.SECONDS);

        // Schedule a task to be executed after a certain delay
        int delayForAmazon = 5; // Adjust the delay as needed
        scheduler.schedule(() -> {
            // Call formWorldMessage() and send the message
            UpsAmazon.UAcommands amazonMessage = formAmazonMessage();
            if (amazonMessage == null) {
                return;
            }
            try {
                send(amazonMessage, client_socket);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }, delayForAmazon, TimeUnit.SECONDS);

        // continuously read from Amazon, handle one by one, collect next-to-send messages into global variable
        while (!Thread.currentThread().isInterrupted()) {
            UpsAmazon.AUcommands aUcommands = read(UpsAmazon.AUcommands.parser(), client_socket); // 需要验证，如果下一条没有会不会出问题
            // handle each situation with world
            for (UpsAmazon.AUreqPickup pickup : aUcommands.getPickupList()) {
                System.out.println("pickup");
                handlePickup(pickup);
            }

            for (UpsAmazon.AUbindUPS bind : aUcommands.getBindList()) {
                System.out.println("bind");
                handleBind(bind);
            }

            for (UpsAmazon.AUreqDelivery delivery : aUcommands.getDeliveryList()) {
                System.out.println("delivery");
                handleDelivery(delivery);
            }

            for (UpsAmazon.AUchangeDestn changeDestn : aUcommands.getChangeDestList()) {
                System.out.println("change");
                handleChangeDest(changeDestn);
            }

            for (UpsAmazon.AUquery query : aUcommands.getQueryList()) {
                System.out.println("query");
                handleQuery(query);
            }

            for (UpsAmazon.Err err: aUcommands.getErrList()) {
                System.out.println("err");
                handleErr(err);
            }

            for (long acks : aUcommands.getAcksList()) {
                System.out.println("disconnect");
                handleAcks(acks);
            }

            if (aUcommands.getDisconnect()) {
                System.out.println("disconnect");
                break;
            }

        }
    }


    public void handlePickup(UpsAmazon.AUreqPickup pickup) {
        // in DB, find a truckID that is available. Now just use 1 注意用hibernate session处理DB
        GlobalVariables.amazonAckLock.lock();
        GlobalVariables.amazonAcks.add(pickup.getSeqNum());
        GlobalVariables.amazonAckLock.unlock();
        Truck usedTruck = DBoperations.useAvailableTruck();
        if (usedTruck == null) {
            long errSeqNum = GlobalVariables.seqNumAmazon.incrementAndGet();
            UpsAmazon.Err error = UpsAmazon.Err.newBuilder()
                    .setOriginseqnum(pickup.getSeqNum())
                    .setErr("No Truck Available")
                    .setSeqnum(errSeqNum)
                    .build();
            GlobalVariables.amazonMessages.put(errSeqNum, error);
            return;
        }
        int truckID = usedTruck.getTruck_id();
        // create a new shipment, save to DB 注意用hibernate session处理DB
        int whid = pickup.getWhID();
        long shipmentID = pickup.getShipID();
        int destX = pickup.getDestinationX();
        int destY = pickup.getDestinationY();
        Integer upsID = null;
        if (pickup.hasUpsID()){
            upsID = pickup.getUpsID();
        }
        DBoperations.createNewShipment(shipmentID, truckID, whid, destX, destY, upsID);


        // form UGoPickup, save to global variable
        long pickupSeqNum = GlobalVariables.seqNumWorld.incrementAndGet();
        WorldUps.UGoPickup uGoPickup = WorldUps.UGoPickup.newBuilder()
                .setTruckid(truckID).setWhid(whid).setSeqnum(pickupSeqNum).build();
        // put into concurrentHashMap
        GlobalVariables.worldMessages.put(pickupSeqNum, uGoPickup);

    }

    public void handleBind(UpsAmazon.AUbindUPS bind) {
        GlobalVariables.amazonAckLock.lock();
        GlobalVariables.amazonAcks.add(bind.getSeqNum());
        GlobalVariables.amazonAckLock.unlock();
        System.out.println("bind");
    }

    public void handleDelivery(UpsAmazon.AUreqDelivery delivery) {
        GlobalVariables.amazonAckLock.lock();
        GlobalVariables.amazonAcks.add(delivery.getSeqNum());
        GlobalVariables.amazonAckLock.unlock();

        System.out.println("delivery");
    }

    public void handleChangeDest(UpsAmazon.AUchangeDestn changeDestn) {
        GlobalVariables.amazonAckLock.lock();
        GlobalVariables.amazonAcks.add(changeDestn.getSeqNum());
        GlobalVariables.amazonAckLock.unlock();
        System.out.println("change");
    }

    public void handleQuery(UpsAmazon.AUquery query) {
        GlobalVariables.amazonAckLock.lock();
        GlobalVariables.amazonAcks.add(query.getSeqNum());
        GlobalVariables.amazonAckLock.unlock();
        System.out.println("query");
    }

    public void handleErr(UpsAmazon.Err err) {
        GlobalVariables.amazonAckLock.lock();
        GlobalVariables.amazonAcks.add(err.getSeqnum());
        GlobalVariables.amazonAckLock.unlock();
        System.out.println("err");
    }

    public void handleAcks(long acks) {
        GlobalVariables.amazonMessages.remove(acks);
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


    /**
     * Send the wordId to the amazon and check for the AUconnectedWorld response
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

    public static void createShipment(){
        long shipmentID = 1;
        int truckID = 1;
        int whid = 1;
        int destX = 50;
        int destY = 50;
        Integer upsID = null;
        DBoperations.createNewShipment(shipmentID, truckID, whid, destX, destY, upsID);
    }

    // main function
    public static void main(String[] args) throws IOException {
//        initTrucks(1);
        createShipment();
    }
}



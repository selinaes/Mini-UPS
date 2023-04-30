package org.example;

import com.google.protobuf.CodedInputStream;
import com.google.protobuf.GeneratedMessageV3;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import org.example.models.Truck;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.example.gpb.UpsAmazon;
import org.example.gpb.WorldUps;

public class ListenAmazonServer {
    private final ServerSocket serverSocket;
    BlockingQueue<Runnable> workQueue = new LinkedBlockingQueue<Runnable>(100);
    ThreadPoolExecutor executor = new ThreadPoolExecutor(5, 5, 5, TimeUnit.MILLISECONDS, workQueue);
    // Create a ScheduledExecutorService
    ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(5);
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
        if (GlobalVariables.worldMessages.isEmpty() && GlobalVariables.worldAcks.isEmpty()) {
            return null;
        }
        WorldUps.UCommands.Builder uCommandsBuilder = WorldUps.UCommands.newBuilder();
        // Loop through the ConcurrentHashMap
        for (com.google.protobuf.GeneratedMessageV3 message : GlobalVariables.worldMessages.values()) {
            System.out.println("adding to worldMessage - type" + message.getClass().getName());
            // Add UGoPickup messages
            if (message instanceof WorldUps.UGoPickup pickup) {
                System.out.println("added pickup to ucommands");
                uCommandsBuilder.addPickups(pickup);
            }
            // Add UGoDeliver messages
            else if (message instanceof WorldUps.UGoDeliver delivery) {
                System.out.println("added delivery to ucommands");
                uCommandsBuilder.addDeliveries(delivery);
            }
            // Add UQuery messages
            else if (message instanceof WorldUps.UQuery query) {
//                System.out.println("added query to ucommands");
                uCommandsBuilder.addQueries(query);
            }
            else {
                System.out.println("Other message type: " + message.getClass().getName());
            }
        }

        GlobalVariables.worldAckLock.lock();
        System.out.println("added world acks to worldmessage" + GlobalVariables.worldAcks);
        uCommandsBuilder.addAllAcks(GlobalVariables.worldAcks);
//        uCommandsBuilder.setSimspeed(50);
        GlobalVariables.worldAcks.clear();
        GlobalVariables.worldAckLock.unlock();

        return uCommandsBuilder.build();
    }

    /**
    * Formed UAcommands message to send to the Amazon client for handling
    */
    public UpsAmazon.UAcommands formAmazonMessage() {
        if (GlobalVariables.amazonMessages.isEmpty() && GlobalVariables.amazonAcks.isEmpty()) {
            return null;
        }
        UpsAmazon.UAcommands.Builder UACommandsBuilder = UpsAmazon.UAcommands.newBuilder();
        // Loop through the ConcurrentHashMap
        for (com.google.protobuf.GeneratedMessageV3 message : GlobalVariables.amazonMessages.values()) {
            System.out.println("message type" + message.getClass().getName());
            if (message instanceof UpsAmazon.UAtruckArrived truckArr) {
                System.out.println("line 91 added truckArr to UAcommands");
                UACommandsBuilder.addTruckArr(truckArr);
            }
            else if (message instanceof UpsAmazon.UAdelivered delivered) {
                System.out.println("line 101 added delivered to UAcommands");
                UACommandsBuilder.addDelivered(delivered);
            }
            else if (message instanceof UpsAmazon.UAbindUPSResponse bindRes){
                System.out.println("line 105 added bindRes to UAcommands");
                UACommandsBuilder.addBindUPSResponse(bindRes);
            }
            else if (message instanceof  UpsAmazon.UAchangeResp changeResp) {
                System.out.println("line 109 added changeResp to UAcommands");
                UACommandsBuilder.addChangeResp(changeResp);
            }
            else if (message instanceof  UpsAmazon.Err err) {
                System.out.println("line 123 added err to UAcommands");
                UACommandsBuilder.addErr(err);
            }
            else {
                System.out.println("Other message type: " + message.getClass().getName());
            }
        }

        GlobalVariables.amazonAckLock.lock();
        System.out.println("added amazon acks to amazonMessage" + GlobalVariables.amazonAcks);
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
    public boolean connectSameWorld(Long worldId, Socket clientSock) throws IOException {
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
        List<WorldUps.UInitTruck> trucks = initTrucks(65);

        // Step 1: UConnect, make sure succeed before proceed
        Long newWorldId = null;
        do {
            newWorldId = worldClient.connectToWorld(trucks);
        } while (newWorldId == null);

       // UAinitWorld, make sure connect to same world
        while (true) {
            boolean result = connectSameWorld(newWorldId,client_socket);
            if (result) {
                this.worldId = newWorldId; // not sure need or not
                break;
            }
            newWorldId = worldClient.connectToWorld(trucks);
        }

        // Thread, always listen to World and handle updates
        executor.execute(() -> {
                    worldClient.listenHandleUpdates();
                }
        );

        // Schedule a task to be executed periodically with a fixed delay
        int delayInSeconds = 1; // Adjust the delay as needed
        scheduler.scheduleAtFixedRate(() -> {
            // Call formWorldMessage() and send the message
            WorldUps.UCommands worldMessage = formWorldMessage();
            if (worldMessage == null) {
                return;
            }
            try {
                System.out.println("Sent to world: \n" + worldMessage.toString());
                worldClient.sendCommands(worldMessage);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }, delayInSeconds, delayInSeconds, TimeUnit.SECONDS);


        // Schedule a task to be executed after a certain delay
        int delayForAmazon = 1; // Adjust the delay as needed
        scheduler.scheduleAtFixedRate(() -> {
            // Call formWorldMessage() and send the message
            UpsAmazon.UAcommands amazonMessage = formAmazonMessage();
            if (amazonMessage == null) {
                return;
            }
            try {
                System.out.println("Sent to amazon: \n" + amazonMessage.toString());
                send(amazonMessage, client_socket);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }, delayForAmazon, delayInSeconds, TimeUnit.SECONDS);

        // Schedule a task to be executed after a certain delay
//        int delayForQuery = 50; // Adjust the delay as needed
//        scheduler.scheduleAtFixedRate(this::addQueryTruck, delayForQuery, delayInSeconds, TimeUnit.SECONDS);

        // on current thread, listen and handle Amazon's message
        receiveHandleAmazon(client_socket);
    }

    public void addQueryTruck(){
        List<Truck> outTrucks = DBoperations.getUsingTrucks();
        for (Truck truck : outTrucks) {
            long seqnum = GlobalVariables.seqNumWorld.incrementAndGet();
            WorldUps.UQuery queryTruck = WorldUps.UQuery.newBuilder()
                    .setTruckid(truck.getTruck_id()).setSeqnum(seqnum)
                    .build();
            GlobalVariables.worldMessages.put(seqnum, queryTruck);
        }
    }

    public void receiveHandleAmazon(Socket client_socket) throws IOException{
        // continuously read from Amazon, handle one by one, collect next-to-send messages into global variable
        while (!Thread.currentThread().isInterrupted()) {
            UpsAmazon.AUcommands aUcommands = read(UpsAmazon.AUcommands.parser(), client_socket);

            if (aUcommands == null){
                System.out.println("AUcommands is null, or lost connection with Amazon");
                break;
            }
//
            // handle each situation with world
            for (UpsAmazon.Err err: aUcommands.getErrList()) {
                System.out.println("err received" + err.getSeqnum());
                handleErr(err);
            }

            for (long acks : aUcommands.getAcksList()) {
                System.out.println("Amazon ack received" + acks);
                handleAmazonAcks(acks);
            }

            for (UpsAmazon.AUreqPickup pickup : aUcommands.getPickupList()) {
                System.out.println("AUreqPickup " + pickup.getSeqNum());
                handlePickup(pickup);
            }

            for (UpsAmazon.AUbindUPS bind : aUcommands.getBindList()) {
                System.out.println("AUbindUPS" + bind.getSeqNum());
                handleBind(bind);
            }

            for (UpsAmazon.AUreqDelivery delivery : aUcommands.getDeliveryList()) {
                System.out.println("AUreqDelivery" + delivery.getSeqNum());
                handleDelivery(delivery);
            }

            for (UpsAmazon.AUchangeDestn changeDestn : aUcommands.getChangeDestList()) {
                System.out.println("AUchangeDestn" + changeDestn.getSeqNum());
                handleChangeDest(changeDestn);
            }


            if (aUcommands.getDisconnect()) {
                System.out.println("disconnect");
                client_socket.close();
                break;
            }

        }
    }


    public void handlePickup(UpsAmazon.AUreqPickup pickup) {
        GlobalVariables.amazonAckLock.lock();
        GlobalVariables.amazonAcks.add(pickup.getSeqNum());
        GlobalVariables.amazonAckLock.unlock();
        if (GlobalVariables.amazonAcked.contains(pickup.getSeqNum())){
            return;
        }
        System.out.println("1st handling pickup: \n" + pickup.toString());
        GlobalVariables.amazonAcked.add(pickup.getSeqNum());
        Truck usedTruck = DBoperations.useAvailableTruck(pickup.getWhID());
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

        List<UpsAmazon.AProduct> products = pickup.getProductsList();

        Integer upsID = null;
        if (pickup.hasUpsID()){
            upsID = pickup.getUpsID();
            String errorReason = DBoperations.validateUpsID(upsID);
            if (errorReason != null){
                upsID = null;
//                long errSeqNum = GlobalVariables.seqNumAmazon.incrementAndGet();
//                UpsAmazon.Err error = UpsAmazon.Err.newBuilder()
//                        .setOriginseqnum(pickup.getSeqNum())
//                        .setErr(errorReason)
//                        .setSeqnum(errSeqNum)
//                        .build();
//                GlobalVariables.amazonMessages.put(errSeqNum, error);
//                return;
            }
        }
        DBoperations.createNewShipment(shipmentID, truckID, whid, destX, destY, upsID);

        DBoperations.addProductsInPackage(shipmentID, products);


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
        if (GlobalVariables.amazonAcked.contains(bind.getSeqNum())){
            return;
        }
        System.out.println("First time handling bind \n" + bind.toString());
        GlobalVariables.amazonAcked.add(bind.getSeqNum());
        // search in DB to see if upsID exist, if so return success
        int upsID = bind.getUpsID();
        int amazonID = bind.getOwnerID();
        System.out.println("Bind parameters: upsID: " + upsID + " amazonID: " + amazonID);
        boolean success = DBoperations.searchUpsIDaddAmazonID(upsID, amazonID);
        if (!success){
            System.out.println("NOT EXIST ID!");
        }
        // form AUbindUPSResponse
        long seqnum = GlobalVariables.seqNumAmazon.incrementAndGet();
        UpsAmazon.UAbindUPSResponse response = UpsAmazon.UAbindUPSResponse.newBuilder()
                .setStatus(success).setOwnerID(amazonID).setUpsID(upsID).setSeqNum(seqnum).build();
        System.out.println("added bindResponse to message: " + response.toString());
        // put response into message list
        GlobalVariables.amazonMessages.put(seqnum, response);
    }

    public void handleDelivery(UpsAmazon.AUreqDelivery delivery) {
        GlobalVariables.amazonAckLock.lock();
        GlobalVariables.amazonAcks.add(delivery.getSeqNum());
        GlobalVariables.amazonAckLock.unlock();
        if (GlobalVariables.amazonAcked.contains(delivery.getSeqNum())){
            return;
        }
        System.out.println("1st Handling delivery" + delivery.toString());
        GlobalVariables.amazonAcked.add(delivery.getSeqNum());
        // get ShipID
        long shipmentID = delivery.getShipID();
        long seqnum = GlobalVariables.seqNumWorld.incrementAndGet();
        // create new UGoDeliver, make UDeliveryLocation first
        WorldUps.UGoDeliver message = DBoperations.makeDeliverMessage(shipmentID, seqnum);

        // add to worldMessage
        GlobalVariables.worldMessages.put(seqnum, message);

    }

    public void handleChangeDest(UpsAmazon.AUchangeDestn changeDestn) {
        GlobalVariables.amazonAckLock.lock();
        GlobalVariables.amazonAcks.add(changeDestn.getSeqNum());
        GlobalVariables.amazonAckLock.unlock();
        if (GlobalVariables.amazonAcked.contains(changeDestn.getSeqNum())){
            return;
        }
        System.out.println("1st Handling changeDest \n" + changeDestn.toString());
        GlobalVariables.amazonAcked.add(changeDestn.getSeqNum());

        // Case 1: if status before delivering, change destination.
        // Case 2: if status delivering, create UAchangeResp fail, save to message list
        boolean success = DBoperations.checkStatusChangeDestination(changeDestn);

        // create UAchangeResp success, save to message list
        long seqnum = GlobalVariables.seqNumAmazon.incrementAndGet();
        UpsAmazon.UAchangeResp response = UpsAmazon.UAchangeResp.newBuilder()
                .setSuccess(success).setSeqNum(seqnum).build();
        // put into message list
        GlobalVariables.amazonMessages.put(seqnum, response);
    }



    public void handleErr(UpsAmazon.Err err) {
        GlobalVariables.amazonAckLock.lock();
        GlobalVariables.amazonAcks.add(err.getSeqnum());
        GlobalVariables.amazonAckLock.unlock();
        if (GlobalVariables.amazonAcked.contains(err.getSeqnum())){
            return;
        }
        System.out.println("1st Handling error \n" + err.toString());
        GlobalVariables.amazonAcked.add(err.getSeqnum());

        // get seqnum, find corresponding message in amazonMessage
        long seqnum = err.getOriginseqnum();
        if (!GlobalVariables.amazonMessages.containsKey(seqnum)){
            System.out.println("Error seqnum not in amazonMessage, not handling");
            return;
        }
        // get message type
        String type = GlobalVariables.amazonMessages.get(seqnum).getDescriptorForType().getName();
        // remove message from amazonMessage
        GeneratedMessageV3 errorMsg = GlobalVariables.amazonMessages.remove(seqnum);
        System.out.println("error message type: " + type);
        System.out.println("error message: " + errorMsg.toString());

    }

    public void handleAmazonAcks(long acks) {
        if (!GlobalVariables.amazonMessages.containsKey(acks)){
            System.out.println("Amazon ack already not in amazonMessage, not handling");
            return;
        }
        System.out.println("Handling Amazon acks: " + acks);
        // Step1. check ack Type, call corresponding method to change necessary status
        String type = GlobalVariables.amazonMessages.get(acks).getDescriptorForType().getName();
        if (type.equals("UAtruckArrived")){
            // change package status to loading
            UpsAmazon.UAtruckArrived truckArrived = (UpsAmazon.UAtruckArrived) GlobalVariables.amazonMessages.get(acks);
            DBoperations.updateTruckStatus(truckArrived.getTruckID(), "loading");
        }

        // Step2. remove ack from amazonAcks
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

    private <T extends GeneratedMessageV3.Builder<?>> Message readNew(T builder, Socket client_socket) throws IOException {
        InputStream inputStream = client_socket.getInputStream();
        CodedInputStream codedInputStream = CodedInputStream.newInstance(inputStream);
        int length = codedInputStream.readRawVarint32();
        int parseLimit = codedInputStream.pushLimit(length);
        builder.mergeFrom(codedInputStream);
        codedInputStream.popLimit(parseLimit);

        return builder.build();
    }

    private <T> T readCoded(com.google.protobuf.Parser<T> parser, Socket client_socket) throws IOException {
        try (InputStream inputStream = client_socket.getInputStream()) {
            CodedInputStream codedInputStream = CodedInputStream.newInstance(inputStream);
            T result = parser.parseFrom(codedInputStream);
            codedInputStream.checkLastTagWas(0); // Ensure the stream has been fully consumed
            return result;
        } catch (InvalidProtocolBufferException e) {
            System.err.println("Error parsing message from input stream: " + e.getMessage());
            throw e;
        }
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
            WorldUps.UInitTruck truck = WorldUps.UInitTruck.newBuilder().setId(i).setX(50).setY(30).build();
            // add DB operation, add to DB
            DBoperations.createNewTruck(truck);
            trucks.add(truck);
        }
        return trucks;
    }

    // helper for test
    public static void createShipment(){
        long shipmentID = 1;
        int truckID = 1;
        int whid = 1;
        int destX = 50;
        int destY = 50;
        Integer upsID = null;
        DBoperations.createNewShipment(shipmentID, truckID, whid, destX, destY, upsID);
    }

    // main function, help test
    public static void main(String[] args) throws IOException {
//        initTrucks(1);
        createShipment();
    }
}



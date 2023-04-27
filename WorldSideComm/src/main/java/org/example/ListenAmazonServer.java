package org.example;

import org.example.models.Truck;
import org.hibernate.Session;
import org.xml.sax.SAXException;
import javax.xml.bind.DatatypeConverter;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

import javax.xml.parsers.ParserConfigurationException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

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


    private static final Logger loggerListenAmazon = LogManager.getLogger("LISTEN_AMAZON");
    private static final Logger loggerSendWorld = LogManager.getLogger("SEND_WORLD");
    private static final Logger loggerSendAmazon = LogManager.getLogger("SEND_AMAZON");

    public ListenAmazonServer(int port, WorldSimulatorClient client) throws IOException {
        this.serverSocket = new ServerSocket(port);
        this.worldClient = client;
    }

    /**
    * Formed Ucommands message to send to the UPS client to solve
    */
    public WorldUps.UCommands formWorldMessage() {
        if (GlobalVariables.worldMessages.isEmpty()) {
            loggerSendWorld.debug("worldMessage list empty");
//            System.out.println("worldMessage list empty");
            return null;
        }
        WorldUps.UCommands.Builder uCommandsBuilder = WorldUps.UCommands.newBuilder();
        // Loop through the ConcurrentHashMap
        for (com.google.protobuf.GeneratedMessageV3 message : GlobalVariables.worldMessages.values()) {
            loggerSendWorld.debug("message type" + message.getClass().getName());
//            System.out.println("message type" + message.getClass().getName());
            // Add UGoPickup messages
            if (message instanceof WorldUps.UGoPickup pickup) {
                loggerSendWorld.debug("added pickup to ucommands line 62");
//                System.out.println("added pickup to ucommands line 49");
                uCommandsBuilder.addPickups(pickup);
            }
            // Add UGoDeliver messages
            else if (message instanceof WorldUps.UGoDeliver delivery) {
                loggerSendWorld.debug("added delivery to ucommands line 68");
//                System.out.println("added delivery to ucommands line 54");
                uCommandsBuilder.addDeliveries(delivery);
            }
            // Add UQuery messages
            else if (message instanceof WorldUps.UQuery query) {
                loggerSendWorld.debug("added query to ucommands line 74");
//                System.out.println("added query to ucommands line 59");
                uCommandsBuilder.addQueries(query);
            }
            else {
                loggerSendWorld.debug("Unknown message type: " + message.getClass().getName());
//                System.out.println();
            }
        }

        GlobalVariables.worldAckLock.lock();
        loggerSendWorld.debug("formed world acks" + GlobalVariables.worldAcks);
//        System.out.println("formed world acks" + GlobalVariables.worldAcks);
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
            System.out.println("message type" + message.getClass().getName());
            // Add UGoPickup messages
            if (message instanceof UpsAmazon.UAtruckArrived truckArr) {
                System.out.println("line 91 added truckArr to UAcommands");
                UACommandsBuilder.addTruckArr(truckArr);
            }
            // Add UGoDeliver messages
            else if (message instanceof UpsAmazon.UAstatus status) {
                System.out.println("line 96 added status to UAcommands");
                UACommandsBuilder.addStatus(status);
            }
            // Add UQuery messages
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
            else {
                System.out.println("Unknown message type: " + message.getClass().getName());
            }
        }

        GlobalVariables.amazonAckLock.lock();
        System.out.println("formed amazon acks" + GlobalVariables.amazonAcks);
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
        List<WorldUps.UInitTruck> trucks = initTrucks(10);

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

        // Schedule a task to be executed periodically with a fixed delay
        int delayInSeconds = 1; // Adjust the delay as needed
        scheduler.scheduleAtFixedRate(() -> {
            // Call formWorldMessage() and send the message
            WorldUps.UCommands worldMessage = formWorldMessage();
            if (worldMessage == null) {
                loggerSendWorld.debug("no message to send to world!");
//                System.out.println("no message to send to world!");
                return;
            }
            try {
                loggerSendWorld.debug("sent message to world!");
//                System.out.println("sent message to world!");
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
                loggerSendAmazon.debug("no message to send to amazon!");
                System.out.println("no message to send to amazon!");
                return;
            }
            try {
                loggerSendAmazon.debug("sent message to amazon!");
                System.out.println("sent message to amazon!");
                send(amazonMessage, client_socket);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }, delayForAmazon, delayInSeconds, TimeUnit.SECONDS);

        // continuously read from Amazon, handle one by one, collect next-to-send messages into global variable
        while (!Thread.currentThread().isInterrupted()) {
            UpsAmazon.AUcommands aUcommands = read(UpsAmazon.AUcommands.parser(), client_socket); // 需要验证，如果下一条没有会不会出问题
            if (aUcommands == null){
                System.out.println("AUcommands is null");
                break;
            }

            // handle each situation with world
            for (UpsAmazon.AUreqPickup pickup : aUcommands.getPickupList()) {
                System.out.println("pickup line 217");
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

        GlobalVariables.amazonAckLock.lock();
        GlobalVariables.amazonAcks.add(pickup.getSeqNum());
        GlobalVariables.amazonAcked.add(pickup.getSeqNum());
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
        GlobalVariables.amazonAcked.add(bind.getSeqNum());
        GlobalVariables.amazonAckLock.unlock();
        System.out.println("bind");
    }

    public void handleDelivery(UpsAmazon.AUreqDelivery delivery) {
        GlobalVariables.amazonAckLock.lock();
        GlobalVariables.amazonAcks.add(delivery.getSeqNum());
        GlobalVariables.amazonAcked.add(delivery.getSeqNum());
        GlobalVariables.amazonAckLock.unlock();

        System.out.println("delivery");
    }

    public void handleChangeDest(UpsAmazon.AUchangeDestn changeDestn) {
        GlobalVariables.amazonAckLock.lock();
        GlobalVariables.amazonAcks.add(changeDestn.getSeqNum());
        GlobalVariables.amazonAcked.add(changeDestn.getSeqNum());
        GlobalVariables.amazonAckLock.unlock();
        System.out.println("change");
    }

    public void handleQuery(UpsAmazon.AUquery query) {
        GlobalVariables.amazonAckLock.lock();
        GlobalVariables.amazonAcks.add(query.getSeqNum());
        GlobalVariables.amazonAcked.add(query.getSeqNum());
        GlobalVariables.amazonAckLock.unlock();
        System.out.println("query");
    }

    public void handleErr(UpsAmazon.Err err) {
        GlobalVariables.amazonAckLock.lock();
        GlobalVariables.amazonAcks.add(err.getSeqnum());
        GlobalVariables.amazonAcked.add(err.getSeqnum());
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
//        byte[] bytes = inputStream.readAllBytes();
//        String hexContent = DatatypeConverter.printHexBinary(bytes);
//        System.out.println("Read content from amazon: \n" + hexContent);
        return parser.parseDelimitedFrom(inputStream);
    }

    private <T extends com.google.protobuf.GeneratedMessageV3> T readNew(com.google.protobuf.Parser<T> parser, Socket socket) throws IOException {
        InputStream inputStream = socket.getInputStream();

        // Read the message length prefix (an integer)
        byte[] lengthPrefix = new byte[4];
        inputStream.read(lengthPrefix);
        int messageLength = ByteBuffer.wrap(lengthPrefix).getInt();

        // Read the entire message into a byte array
        byte[] messageBytes = new byte[messageLength];
        int totalBytesRead = 0;
        while (totalBytesRead < messageLength) {
            int bytesRead = inputStream.read(messageBytes, totalBytesRead, messageLength - totalBytesRead);
            if (bytesRead == -1) {
                throw new IOException("Connection closed before full message received");
            }
            totalBytesRead += bytesRead;
        }

        // Parse the message from the byte array
        return parser.parseFrom(messageBytes);
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



package org.example;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import com.google.protobuf.CodedInputStream;
import com.google.protobuf.GeneratedMessageV3;
import com.google.protobuf.Message;
import org.example.gpb.WorldUps;
import org.example.gpb.UpsAmazon;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.example.models.Shipment;

import javax.mail.MessagingException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class WorldSimulatorClient {
    private final String host;
    private final int port;
    private Socket socket;

    private InputStream inputStream;
    private OutputStream outputStream;

    BlockingQueue<Runnable> workQueue = new LinkedBlockingQueue<Runnable>(100);
    ThreadPoolExecutor executor = new ThreadPoolExecutor(100, 100, 5, TimeUnit.MILLISECONDS, workQueue);

    private static final Logger loggerListenWorld = LogManager.getLogger("LISTEN_WORLD");

    public WorldSimulatorClient(String host, int port) {
        this.host = host;
        this.port = port;
    }


    /**
    * Send Uconnect message to the world and wait for the response from the World Docker server
    * for Uconnected
    * @return worldid if successful, null otherwise
    */
    public Long connectToWorld(List<WorldUps.UInitTruck> trucks) throws IOException {
        // Connect to the world using UConnect
        socket = new Socket(host, port);
        inputStream = socket.getInputStream();
        outputStream = socket.getOutputStream();
        WorldUps.UConnect uConnect = WorldUps.UConnect.newBuilder().addAllTrucks(trucks).setIsAmazon(false).build();
        send(uConnect);

        // Wait for UConnected response
        WorldUps.UConnected uConnected = read(WorldUps.UConnected.parser());

        String result = uConnected.getResult();

        if (result.startsWith("connected")) {
            System.out.println("Result of connection: " + uConnected.getResult());
            System.out.println("Connected to world " + uConnected.getWorldid());
        } else if (result.startsWith("error")) {
            System.out.println("Error connecting to world: " + uConnected.getResult());
            return null;
        } else {
            System.out.println("Unrecognized response: " + uConnected.getResult());
            return null;
        }

        return uConnected.getWorldid();
    }

    /**
    *  Send uCommands to the world
    */
    public void sendCommands(WorldUps.UCommands uCommands) throws IOException {
        // Send UCommand
        send(uCommands);
    }

    /**
    * Send message to the world
    * Applied for UInitTruck, UConnect
    */

    private <T> void send(com.google.protobuf.MessageLite message) throws IOException {
        message.writeDelimitedTo(outputStream);
        outputStream.flush();
    }

    /**
    * Read message from the world Docker
    */
    private <T> T read(com.google.protobuf.Parser<T> parser) throws IOException {
        return parser.parseDelimitedFrom(inputStream);
    }

    private <T> T readCoded(com.google.protobuf.Parser<T> parser) throws IOException {
        CodedInputStream codedInputStream = CodedInputStream.newInstance(inputStream);
        return parser.parseFrom(codedInputStream);
    }

    private <T extends GeneratedMessageV3.Builder<?>> Message readNew(T builder) throws IOException {
        CodedInputStream codedInputStream = CodedInputStream.newInstance(inputStream);
        int length = codedInputStream.readRawVarint32();
        int parseLimit = codedInputStream.pushLimit(length);
        builder.mergeFrom(codedInputStream);
        codedInputStream.popLimit(parseLimit);

        return builder.build();
    }


    /**
    * Read message from the world Docker and return it to UResponse
    */
    public WorldUps.UResponses readResponses() throws IOException {
        WorldUps.UResponses uResponses = read(WorldUps.UResponses.parser());
        LocalDateTime utcDateTime = LocalDateTime.now(ZoneId.of("UTC"));
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        String formattedUtcDateTime = utcDateTime.format(formatter);
        System.out.println("UTC Time: " + formattedUtcDateTime);
        return uResponses;
    }

    /**
     * handle each world UResponse, to send things to amazon
     */
    public void listenHandleUpdates() {
        while (!Thread.currentThread().isInterrupted()) {
            WorldUps.UResponses uResponses;
            try {
                uResponses = readResponses();
                if (uResponses == null){
                    System.out.println("null uResponses, or lost connection");
                    break;
                }
                // handle each situation with world
                for (long acks: uResponses.getAcksList()){
                    executor.execute(() -> {
                        System.out.println("Ack received: " + acks);
                        handleWorldAcks(acks);
                    });
                    // handleWorldAcks(acks);
                }

                for (WorldUps.UErr err: uResponses.getErrorList()){
                    System.out.println("UErr received: " + err.toString());
                    executor.execute(() -> {
                        handleError(err);
                    });
                    // handleError(err);
                }

                for (WorldUps.UFinished completions : uResponses.getCompletionsList()) {
                    System.out.println("UFinished received. status: " + completions.getStatus());
                    executor.execute(() -> {
                        handleCompletions(completions);
                    });
                    // handleCompletions(completions);
                }

                for (WorldUps.UDeliveryMade delivered: uResponses.getDeliveredList()) {
                    executor.execute(() -> {
                        handleDeliveries(delivered);
                    });
                    // handleDeliveries(delivered);
                }

                for (WorldUps.UTruck truckstatus: uResponses.getTruckstatusList()) {
                    executor.execute(() -> {
                        handleTruckStatus(truckstatus);
                    });
                    // handleTruckStatus(truckstatus);
                }

                if (uResponses.getFinished()){
                    break;
                }


                // send back UAcommands

            } catch (IOException e) {
                System.out.println("Error reading responses: " + e.getMessage());
                break;
            }
        }
    }

    public void handleCompletions(WorldUps.UFinished completions){
        long seqNum = completions.getSeqnum();
        GlobalVariables.worldAckLock.lock();
        GlobalVariables.worldAcks.add(seqNum);
        GlobalVariables.worldAckLock.unlock();

        if (GlobalVariables.worldAcked.contains(seqNum)){
            loggerListenWorld.debug("UFinished " + seqNum + " not 1st time handling");
            return;
        }
        System.out.println("1st Handling UFinished \n" + completions.toString());

        GlobalVariables.worldAcked.add(seqNum);

        if (completions.getStatus().equals("IDLE")) {
            // change truck status back to idle, whid still old but not matter, because next time when use idle will update
            int truck_id = completions.getTruckid();
            DBoperations.updateTruckStatus(truck_id, "idle");
        }
        // Arrive warehouse
        else {
            // using truck id to find all ship_id
            int truck_id = completions.getTruckid();
            List<Shipment> shipments = DBoperations.findShipmentsUpdateStatus(truck_id);
            // using ship_id to find WhID
            if (shipments.size() == 0) {
                return;
            }
            int whID = shipments.get(0).getWh_id();
            // for loop to add for amazonMessages to add UATruckArrived
            for (Shipment sh: shipments) {
                long seqnum = GlobalVariables.seqNumAmazon.incrementAndGet();
                UpsAmazon.UAtruckArrived arrived = UpsAmazon.UAtruckArrived.newBuilder().setWhID(whID)
                        .setTruckID(truck_id).setShipID(sh.getShipment_id()).setSeqNum(seqnum).build();
                GlobalVariables.amazonMessages.put(seqnum, arrived);
            }
        }

    }

    public void handleDeliveries(WorldUps.UDeliveryMade delivered) {
        long seqNum = delivered.getSeqnum();
        GlobalVariables.worldAckLock.lock();
        GlobalVariables.worldAcks.add(seqNum);
        GlobalVariables.worldAckLock.unlock();
        if (GlobalVariables.worldAcked.contains(seqNum)){
            loggerListenWorld.debug("UDeliveryMade " + seqNum + " already handled");
            return;
        }
        System.out.println("1st Handling UDeliveryMade \n" + delivered.toString());

        GlobalVariables.worldAcked.add(seqNum);

        long seqnum = GlobalVariables.seqNumAmazon.incrementAndGet();
        UpsAmazon.UAdelivered tosend_delivered = UpsAmazon.UAdelivered.newBuilder().setShipID(delivered.getPackageid()).setSeqNum(seqnum).build();
        GlobalVariables.amazonMessages.put(seqnum, tosend_delivered);
        DBoperations.updateShipStatus(delivered.getPackageid(), "delivered");

//        // send Email
//        EmailSender emailSender = new EmailSender();
//        try {
//            emailSender.sendEmail("jiawei.liu@duke.edu", "Event Notification", "Hello, your package is delivered!");
//        } catch (MessagingException e) {
//            throw new RuntimeException(e);
//        }

    }

    public void handleTruckStatus(WorldUps.UTruck truckstatus){
        long seqNum = truckstatus.getSeqnum();
        GlobalVariables.worldAckLock.lock();
        GlobalVariables.worldAcks.add(truckstatus.getSeqnum());
        GlobalVariables.worldAckLock.unlock();
        if (GlobalVariables.worldAcked.contains(seqNum)){
            // loggerListenWorld.debug("UTruck " + seqNum + " already handled");
            return;
        }
        // System.out.println("1st Handling UTruck \n" + truckstatus.toString());

        GlobalVariables.worldAcked.add(truckstatus.getSeqnum());

        // update truck info
        DBoperations.updateTruckInfo(truckstatus);

    }

    public void handleWorldAcks(long acks){
        // world acked, we can delete it from worldMessages
        if (!GlobalVariables.worldMessages.containsKey(acks)){
            System.out.println(acks + "World ack not 1st time handling");
            return;
        }
        System.out.println("Handling WorldAcks: " + acks);

        String type = GlobalVariables.worldMessages.get(acks).getDescriptorForType().getName();
        System.out.println("World Ack type: " + type);

        if (type.equals("UGoPickup")){
            // change truck status to traveling
            WorldUps.UGoPickup message = (WorldUps.UGoPickup) GlobalVariables.worldMessages.get(acks);
            DBoperations.updateTruckStatus(message.getTruckid(), "traveling");
        }
        else if (type.equals("UGoDeliver")){
            WorldUps.UGoDeliver message = (WorldUps.UGoDeliver) GlobalVariables.worldMessages.get(acks);
            // change truck status to delivering, change shipment status to out for delivery
            List<WorldUps.UDeliveryLocation> locations = message.getPackagesList();
            for (WorldUps.UDeliveryLocation location : locations){
                long ship_id = location.getPackageid();
                DBoperations.updateShipAndTruckStatus(ship_id, "out for delivery", "delivering");
            }


        }

        GlobalVariables.worldMessages.remove(acks);

    }

    public void handleError(WorldUps.UErr err){
        long seqNum = err.getSeqnum();
        GlobalVariables.worldAckLock.lock();
        GlobalVariables.worldAcks.add(err.getSeqnum());
        GlobalVariables.worldAckLock.unlock();
        if (GlobalVariables.worldAcked.contains(seqNum)){
            System.out.println("UErr " + seqNum + "not 1st time handling");
            return;
        }
        System.out.println();
        if (!GlobalVariables.worldMessages.containsKey(err.getOriginseqnum())){
            System.out.println("Origin message already not in worldMessages, not handling");
        }
        System.out.println("1st Handling UErr \n" + err.toString());
        System.out.println("Original seqnum is " + err.getOriginseqnum());
        System.out.println("Original message is " + GlobalVariables.worldMessages.remove(err.getOriginseqnum()).toString());


        GlobalVariables.worldAcked.add(err.getSeqnum());

    }


    public void disconnect() throws IOException {
        if (socket != null) {
            socket.close();
        }
    }
}

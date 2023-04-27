package org.example;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.List;

import gpb.WorldUps;
import gpb.UpsAmazon;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.example.models.Shipment;

public class WorldSimulatorClient {
    private final String host;
    private final int port;
    private Socket socket;

    private InputStream inputStream;
    private OutputStream outputStream;

    private static final Logger loggerListenWorld = LogManager.getLogger("LISTEN_WORLD");

    public WorldSimulatorClient(String host, int port) {
        this.host = host;
        this.port = port;
    }


    /**
    * Send Uconnect message to the world and wait for the response from the World Docker server
    * for Uconnected
    * @return worldid
    */
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

    /**
    * Read message from the world Docker and return it to UResponse
    */
    public WorldUps.UResponses readResponses() throws IOException {
        WorldUps.UResponses uResponses = read(WorldUps.UResponses.parser());
        loggerListenWorld.info("Received responses: " + uResponses);
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
                // handle each situation with world
                for (long acks: uResponses.getAcksList()){
                    loggerListenWorld.debug("line 113 received world acks: " + acks);
                    handleWorldAcks(acks);
                }

                for (WorldUps.UErr err: uResponses.getErrorList()){
                    loggerListenWorld.debug("line 118 received completions");
                    loggerListenWorld.info(err.toString());
                    handleError(err);
                }

                for (WorldUps.UFinished completions : uResponses.getCompletionsList()) {
                    loggerListenWorld.debug("line 91 received completions");
                    loggerListenWorld.info(completions.toString());
                    System.out.println("UFinished received. status: " + completions.getStatus());
                    handleCompletions(completions);
                }

                for (WorldUps.UDeliveryMade delivered: uResponses.getDeliveredList()) {
                    loggerListenWorld.debug("line 101 received UDeliveryMade");
                    loggerListenWorld.info(delivered.toString());
                    handleDeliveries(delivered);
                }

                for (WorldUps.UTruck truckstatus: uResponses.getTruckstatusList()) {
                    loggerListenWorld.debug("line 107 received completions");
                    loggerListenWorld.info(truckstatus.toString());
                    handleTruckStatus(truckstatus);
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
        if (GlobalVariables.worldAcked.contains(seqNum)){
            loggerListenWorld.debug("UFinished " + seqNum + " already handled");
            return;
        }
        System.out.println("Handling UFinished" + completions.toString());
        GlobalVariables.worldAckLock.lock();
        GlobalVariables.worldAcks.add(seqNum);
        GlobalVariables.worldAcked.add(seqNum);
        GlobalVariables.worldAckLock.unlock();
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

    public void handleDeliveries(WorldUps.UDeliveryMade delivered){
        long seqNum = delivered.getSeqnum();
        if (GlobalVariables.worldAcked.contains(seqNum)){
            loggerListenWorld.debug("UDeliveryMade " + seqNum + " already handled");
            return;
        }
        System.out.println("Handling UDeliveryMade" + delivered.toString());
        GlobalVariables.worldAckLock.lock();
        GlobalVariables.worldAcks.add(seqNum);
        GlobalVariables.worldAcked.add(seqNum);
        GlobalVariables.worldAckLock.unlock();
        long seqnum = GlobalVariables.seqNumAmazon.incrementAndGet();
        UpsAmazon.UAdelivered tosend_delivered = UpsAmazon.UAdelivered.newBuilder().setShipID(delivered.getPackageid()).setSeqNum(seqnum).build();
        GlobalVariables.amazonMessages.put(seqnum, tosend_delivered);
        DBoperations.updateShipStatus(delivered.getPackageid(), "delivered");
    }

    public void handleTruckStatus(WorldUps.UTruck truckstatus){
        long seqNum = truckstatus.getSeqnum();
        if (GlobalVariables.worldAcked.contains(seqNum)){
            loggerListenWorld.debug("UTruck " + seqNum + " already handled");
            return;
        }
        System.out.println("Handling UTruck" + truckstatus.toString());
        GlobalVariables.worldAckLock.lock();
        GlobalVariables.worldAcks.add(truckstatus.getSeqnum());
        GlobalVariables.worldAcked.add(truckstatus.getSeqnum());
        GlobalVariables.worldAckLock.unlock();
        System.out.println("handle truck status");
    }

    public void handleWorldAcks(long acks){
        // world acked, we can delete it from worldMessages
        if (!GlobalVariables.worldMessages.containsKey(acks)){
            System.out.println("World ack already not in worldMessages, not handling");
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
        else if (type.equals("UQuery")){
            //似乎不需要做啥？

        }
        loggerListenWorld.debug("Message with ack " + acks + " removed from worldMessages");
        GlobalVariables.worldMessages.remove(acks);

    }

    public void handleError(WorldUps.UErr err){
        long seqNum = err.getSeqnum();
        if (GlobalVariables.worldAcked.contains(seqNum)){
            loggerListenWorld.debug("UErr " + seqNum + " already handled");
            return;
        }
        GlobalVariables.worldAckLock.lock();
        GlobalVariables.worldAcks.add(err.getSeqnum());
        GlobalVariables.worldAcked.add(err.getSeqnum());
        GlobalVariables.worldAckLock.unlock();
    }


    public void disconnect() throws IOException {
        if (socket != null) {
            socket.close();
        }
    }
}

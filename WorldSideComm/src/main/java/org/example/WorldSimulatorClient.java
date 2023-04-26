package org.example;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.List;

import gpb.WorldUps;
import gpb.UpsAmazon;
import org.example.models.Shipment;

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
        System.out.println("Received responses: " + uResponses);
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
                for (WorldUps.UFinished completions : uResponses.getCompletionsList()) {
                    System.out.println("completions");
                    handleCompletions(completions);
                }

                for (WorldUps.UDeliveryMade delivered: uResponses.getDeliveredList()) {
                    System.out.println("deliveries");
                    handleDeliveries(delivered);
                }

                for (WorldUps.UTruck truckstatus: uResponses.getTruckstatusList()) {
                    System.out.println("truckstatus");
                    handleTruckStatus(truckstatus);
                }

                for (long acks: uResponses.getAcksList()){
                    handleAcks(acks);
                }

                for (WorldUps.UErr err: uResponses.getErrorList()){
                    handleError(err);
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
        GlobalVariables.worldAckLock.lock();
        GlobalVariables.worldAcks.add(completions.getSeqnum());
        GlobalVariables.worldAckLock.unlock();
        if (completions.getStatus().equals("idle")) {
            System.out.println("truck all deliveres finished"); // !!!注意这里没有implement！！！
        }
        // Arrive warehouse
        else {
            // using truck id to find all ship_id
            int truck_id = completions.getTruckid();
            List<Shipment> shipments = DBoperations.findShipmentsUpdateStatus(truck_id);
            // using ship_id to find WhID
            int whID = shipments.get(0).getWh_id();
            // for loop to add for amazonMessages to add UATruckArrived
            for (Shipment sh: shipments) {
                long seqNum = GlobalVariables.seqNumAmazon.incrementAndGet();
                UpsAmazon.UAtruckArrived arrived = UpsAmazon.UAtruckArrived.newBuilder().setWhID(whID)
                        .setTruckID(truck_id).setShipID(sh.getShipment_id()).setSeqNum(seqNum).build();
                GlobalVariables.amazonMessages.put(seqNum, arrived);
            }
        }
        System.out.println("handle completions");
    }

    public void handleDeliveries(WorldUps.UDeliveryMade delivered){
        GlobalVariables.worldAckLock.lock();
        GlobalVariables.worldAcks.add(delivered.getSeqnum());
        GlobalVariables.worldAckLock.unlock();
        System.out.println("handle deliveries");
    }

    public void handleTruckStatus(WorldUps.UTruck truckstatus){
        GlobalVariables.worldAckLock.lock();
        GlobalVariables.worldAcks.add(truckstatus.getSeqnum());
        GlobalVariables.worldAckLock.unlock();
        System.out.println("handle truck status");
    }

    public void handleAcks(long acks){
        System.out.println("handle acks");
    }

    public void handleError(WorldUps.UErr err){
        GlobalVariables.worldAckLock.lock();
        GlobalVariables.worldAcks.add(err.getSeqnum());
        GlobalVariables.worldAckLock.unlock();
        System.out.println("handle error");
    }


    public void disconnect() throws IOException {
        if (socket != null) {
            socket.close();
        }
    }
}

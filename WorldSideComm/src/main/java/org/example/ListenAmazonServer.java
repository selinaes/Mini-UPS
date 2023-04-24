package org.example;

import org.hibernate.Session;
import org.xml.sax.SAXException;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import javax.xml.parsers.ParserConfigurationException;

import gpb.UpsAmazon;

public class ListenAmazonServer {
    private final ServerSocket serverSocket;
    BlockingQueue<Runnable> workQueue = new LinkedBlockingQueue<Runnable>(100);
    ThreadPoolExecutor executor = new ThreadPoolExecutor(10, 100, 5, TimeUnit.MILLISECONDS, workQueue);
    //  ArrayList<Client> clients = new ArrayList<>();
    int numClients;


    public ListenAmazonServer(int port) throws IOException {
        this.serverSocket = new ServerSocket(port);
        this.numClients = 0;

    }

    public void run() {
        System.out.println("Server started");
        while (!Thread.currentThread().isInterrupted()) {
            final Socket client_socket = acceptOrNull();

            synchronized (this){
                numClients++;
            }
            if (client_socket == null) {
                continue;
            }
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        // do something with client_socket
                        synchronized (this) {
                            System.out.println("Client accepted" + numClients);
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

    public void handleClient(Socket client_socket) throws IOException {
        // receive AUcommands
        UpsAmazon.AUcommands aUcommands = read(UpsAmazon.AUcommands.parser(), client_socket);

        // handle each situation  with world
        for (UpsAmazon.AUreqPickup pickup : aUcommands.getPickupList()) {
            System.out.println("pickup");
            handlePickup(pickup);
        }

        // send back UAcommands

    }

    public void handlePickup(UpsAmazon.AUreqPickup pickup){
        // retrieve all information

        // create a new shipment, save to DB

        // UGoPickup to world

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
}







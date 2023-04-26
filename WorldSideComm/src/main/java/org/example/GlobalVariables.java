package org.example;

import com.google.protobuf.GeneratedMessageV3;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class GlobalVariables {
    public static AtomicLong seqNumWorld = new AtomicLong(0);
    public static AtomicLong seqNumAmazon = new AtomicLong(0);

    public static ConcurrentHashMap<Long, com.google.protobuf.GeneratedMessageV3> worldMessages = new ConcurrentHashMap<>();
    public static ConcurrentHashMap<Long, GeneratedMessageV3> amazonMessages = new ConcurrentHashMap<>();

    public static CopyOnWriteArrayList<Long> worldAcks = new CopyOnWriteArrayList<>();
    public static CopyOnWriteArrayList<Long> amazonAcks = new CopyOnWriteArrayList<>();

    public static Lock worldAckLock = new ReentrantLock();
    public static Lock amazonAckLock = new ReentrantLock();
}

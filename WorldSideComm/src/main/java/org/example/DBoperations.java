package org.example;

import org.example.models.Truck;
import org.example.models.Shipment;
import org.example.models.ProductsInPackage;
//import org.hibernate.Criteria;
import org.hibernate.LockMode;
import org.hibernate.Session;
import org.hibernate.Transaction;
//import org.hibernate.criterion.Restrictions;
import org.hibernate.dialect.lock.PessimisticEntityLockException;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.Lock;

import jakarta.persistence.LockModeType;
import jakarta.persistence.OptimisticLockException;
import jakarta.persistence.PessimisticLockException;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;

import gpb.WorldUps;


public class DBoperations {
     /**
    * Database operation to create new trucks and insert into database
    */
    public static void createNewTruck(WorldUps.UInitTruck truck) {
        Session session = SessionFactoryWrapper.openSession();
        Lock lock = SessionFactoryWrapper.getLock("truck");
        lock.lock();
        try (session) {
            Transaction tx = session.beginTransaction();

            Truck newTruck = new Truck();
            newTruck.setTruck_id(truck.getId());
            newTruck.setTruck_x(truck.getX());
            newTruck.setTruck_y(truck.getY());
            newTruck.setTruck_status("idle");

            session.merge(newTruck);
            tx.commit();
        } finally {
            lock.unlock();
        }
    }

    /**
    * Get an available truck ("idle") and set it to "travelling"
    * Return the truck which is selected
    */
    public static Truck useAvailableTruck(int targetWH) {
        Session session = SessionFactoryWrapper.openSession();
        Lock lock = SessionFactoryWrapper.getLock("truck");
        lock.lock();
        try (session) {
            Transaction tx = session.beginTransaction();

            CriteriaBuilder cb = session.getCriteriaBuilder();
            CriteriaQuery<Truck> cq = cb.createQuery(Truck.class);
            Root<Truck> root = cq.from(Truck.class);
            // first find traveling + same whid, just use that truck when it arrives
            cq.where(
                    cb.or(
                            cb.equal(root.get("truck_status"), "traveling"),
                            cb.equal(root.get("truck_status"), "arrive warehouse")
                    ),
                    cb.equal(root.get("wh_id"), targetWH )
            );
            List<Truck> trucksToSameWH = session.createQuery(cq).setLockMode(LockModeType.PESSIMISTIC_WRITE).getResultList();
            if(trucksToSameWH.size() != 0){
                return trucksToSameWH.get(0);
            }


            cq.where(
                    cb.equal(root.get("truck_status"), "idle" )
            );
            List<Truck> trucksIdle = session.createQuery(cq).setLockMode(LockModeType.PESSIMISTIC_WRITE).getResultList();
            System.out.println(trucksIdle);
            if (trucksIdle.size() == 0) {
                return null;
            }
            Truck usedTruck = trucksIdle.get(0);
            usedTruck.setWh_id(targetWH);
            session.merge(usedTruck);
            tx.commit();
            return usedTruck;
        }
        finally {
            lock.unlock();
        }
    }

    public static void createNewShipment(long shipID, int truckID, int whID, int destX, int destY, Integer upsUserID) {
        Session session = SessionFactoryWrapper.openSession();
        Lock lock = SessionFactoryWrapper.getLock("shipment");
        lock.lock();
        try (session) {
            Transaction tx = session.beginTransaction();

            Shipment newShipment = new Shipment();
            newShipment.setShipment_id(shipID);
            newShipment.setTruck_id(truckID);
            newShipment.setWh_id(whID);
            newShipment.setDest_x(destX);
            newShipment.setDest_y(destY);
            newShipment.setShipment_status("created");
            if (upsUserID != null){
                newShipment.setUps_userid(upsUserID);
            }

            session.merge(newShipment);
            // 这里需要create productsinpackage instance
            tx.commit();
        } finally {
            lock.unlock();
        }
    }

    public static List<Shipment> findShipmentsUpdateStatus(int truckID) {
        Session session = SessionFactoryWrapper.openSession();
        Lock lock = SessionFactoryWrapper.getLock("shipment");
        lock.lock();
        try (session) {
            Transaction tx = session.beginTransaction();
            // get corresponding truck and update truck status
            Truck tk = session.get(Truck.class, truckID, LockMode.PESSIMISTIC_WRITE);
            tk.setTruck_status("arrive warehouse");
            session.merge(tk);
            // get all shipments associated with this truck_id
            CriteriaBuilder cb = session.getCriteriaBuilder();
            CriteriaQuery<Shipment> cq = cb.createQuery(Shipment.class);
            Root<Shipment> root = cq.from(Shipment.class);
            cq.where(
                    cb.equal(root.get("truck_id"), truckID ) // 可能不止idle 看一下要求
            );
            List<Shipment> shipments = session.createQuery(cq).setLockMode(LockModeType.PESSIMISTIC_WRITE).getResultList();
            // Update shipment_status for each shipment
            for (Shipment sh: shipments) {
                sh.setShipment_status("truck waiting for package");
                session.merge(sh);
            }

            tx.commit();
            return shipments;
        }
        finally {
            lock.unlock();
        }
    }

    public static WorldUps.UGoDeliver makeDeliverMessage(long shipID, long seqnum){
        Session session = SessionFactoryWrapper.openSession();
        Lock lock = SessionFactoryWrapper.getLock("shipment");
        lock.lock();
        try (session) {
            Transaction tx = session.beginTransaction();
            // get corresponding shipment
            Shipment ship = session.get(Shipment.class, shipID, LockMode.PESSIMISTIC_WRITE);

            WorldUps.UDeliveryLocation uDeliveryLocation = WorldUps.UDeliveryLocation.newBuilder()
                    .setPackageid(shipID).setX(ship.getDest_x()).setY(ship.getDest_y()).build();

            WorldUps.UGoDeliver uGoDeliver = WorldUps.UGoDeliver.newBuilder()
                    .addPackages(uDeliveryLocation).setTruckid(ship.getTruck_id()).setSeqnum(seqnum).build();

            tx.commit();
            return uGoDeliver;
        }
        finally {
            lock.unlock();
        }

    }

    public static void updateShipAndTruckStatus(long shipID, String shipStatus, String truckStatus) {
        Session session = SessionFactoryWrapper.openSession();
        Lock lock = SessionFactoryWrapper.getLock("shipment");
        lock.lock();
        Lock lockT = SessionFactoryWrapper.getLock("truck");
        lockT.lock();
        try (session) {
            Transaction tx = session.beginTransaction();
            // get corresponding shipment
            Shipment ship = session.get(Shipment.class, shipID, LockMode.PESSIMISTIC_WRITE);
            ship.setShipment_status(shipStatus);

            Truck truck = session.get(Truck.class, ship.getTruck_id(), LockMode.PESSIMISTIC_WRITE);
            truck.setTruck_status(truckStatus);
            session.merge(truck);
            session.merge(ship);

            tx.commit();
        }
        finally {
            lockT.unlock();
            lock.unlock();
        }
    }

    public static void updateShipStatus(long shipID, String shipStatus) {
        Session session = SessionFactoryWrapper.openSession();
        Lock lock = SessionFactoryWrapper.getLock("shipment");
        lock.lock();
        try (session) {
            Transaction tx = session.beginTransaction();
            // get corresponding shipment
            Shipment ship = session.get(Shipment.class, shipID, LockMode.PESSIMISTIC_WRITE);
            ship.setShipment_status(shipStatus);
            session.merge(ship);

            tx.commit();
        }
        finally {
            lock.unlock();
        }
    }

    public static void updateTruckStatus(int truckID, String truckStatus) {
        Session session = SessionFactoryWrapper.openSession();
        Lock lockT = SessionFactoryWrapper.getLock("truck");
        lockT.lock();
        try (session) {
            Transaction tx = session.beginTransaction();
            // get corresponding truck
            Truck truck = session.get(Truck.class, truckID, LockMode.PESSIMISTIC_WRITE);
            truck.setTruck_status(truckStatus);
            session.merge(truck);

            tx.commit();
        }
        finally {
            lockT.unlock();
        }
    }


}

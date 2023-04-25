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

            session.save(newTruck);
            tx.commit();
        } finally {
            lock.unlock();
        }
    }

    /**
    * Get an available truck ("idle") and set it to "travelling"
    * Return the truck which is selected
    */
    public static Truck useAvailableTruck() {
        Session session = SessionFactoryWrapper.openSession();
        Lock lock = SessionFactoryWrapper.getLock("truck");
        lock.lock();
        try (session) {
            Transaction tx = session.beginTransaction();

            CriteriaBuilder cb = session.getCriteriaBuilder();
            CriteriaQuery<Truck> cq = cb.createQuery(Truck.class);
            Root<Truck> root = cq.from(Truck.class);

            cq.where(
                    cb.equal(root.get("truck_status"), "idle" ) // 可能不止idle 看一下要求
            );
            List<Truck> trucks = session.createQuery(cq).setLockMode(LockModeType.PESSIMISTIC_WRITE).getResultList();
            if (trucks.size() == 0) {
                return null;
            }
            Truck usedTruck = trucks.get(0);
            usedTruck.setTruck_status("traveling");

            session.merge(usedTruck);
            tx.commit();
            return usedTruck;
        }
        finally {
            lock.unlock();
        }
    }

    public static void createNewShipment(Shipment shipment) {
        Session session = SessionFactoryWrapper.openSession();
        Lock lock = SessionFactoryWrapper.getLock("shipment");
        lock.lock();
        try (session) {
            Transaction tx = session.beginTransaction();
            session.save(shipment);
            tx.commit();
        } finally {
            lock.unlock();
        }
    }
}

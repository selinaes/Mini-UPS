package org.example;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class SessionFactoryWrapper {

  public static ConcurrentHashMap<String, Lock> lock;
  private static SessionFactoryWrapper instance;
  private SessionFactory sessionFactory;

  public static synchronized Lock getLock(String type) {
    return lock.get(type);
  }

  private SessionFactoryWrapper() {
    try {

      this.initSessionFactory();
      lock = new ConcurrentHashMap<>();
      lock.put("truck", new ReentrantLock());
      lock.put("shipment", new ReentrantLock());
      lock.put("productsInPackage", new ReentrantLock());

    } catch (Exception e) {
      e.printStackTrace();
      System.err.println(e.getClass().getName() + ": " + e.getMessage());
      System.exit(0);
    }
    System.out.println("Opened database successfully");
  }


  private void initSessionFactory() {
    Configuration configuration = new Configuration();
    configuration.configure("hibernate.cfg.xml");
    sessionFactory = configuration.buildSessionFactory();

  }


  private static synchronized SessionFactory getSessionFactoryInstance() {
    if (instance == null) {
      instance = new SessionFactoryWrapper();
    }
    return instance.sessionFactory;
  }

  public static synchronized Session openSession()  {
    return getSessionFactoryInstance().openSession();
  }

  public static synchronized void shutdown() {
    getSessionFactoryInstance().close();
  }
}

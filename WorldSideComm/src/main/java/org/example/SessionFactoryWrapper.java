package org.example;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;

public class SessionFactoryWrapper {


  private static SessionFactoryWrapper instance;
  private SessionFactory sessionFactory;

  private SessionFactoryWrapper() {
    try {

      this.initSessionFactory();

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

package org.atomhopper.jdbc.delay;

import akka.actor.ActorSystem;
import akka.actor.PoisonPill;
import akka.actor.Props;
import akka.contrib.pattern.ClusterSingletonManager;
import scala.concurrent.duration.Duration;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import java.util.concurrent.TimeUnit;

/**
 * Created by rona6028 on 5/22/14.
 */
public class WebappContextListener implements ServletContextListener {

    private ActorSystem system = ActorSystem.apply();

    @Override
    public void contextInitialized( ServletContextEvent servletContextEvent ) {

        system.actorOf( ClusterSingletonManager.defaultProps( Props.create( ClusterDelayMaster.class ),
                                                              "cluster-singleton",
                                                              PoisonPill.getInstance(),
                                                              "delay-processing" ),
                        "delay-processor" );
    }

    @Override
    public void contextDestroyed( ServletContextEvent servletContextEvent ) {

        system.shutdown();
        system.awaitTermination( Duration.apply( 15, TimeUnit.SECONDS ) );
    }
}

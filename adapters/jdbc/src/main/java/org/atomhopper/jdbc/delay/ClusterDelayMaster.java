
package org.atomhopper.jdbc.delay;

import akka.actor.*;
import akka.contrib.pattern.DistributedPubSubExtension;
import akka.contrib.pattern.DistributedPubSubMediator;

import java.util.Map;
import java.util.SortedSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListSet;

/**
 * Created by rona6028 on 5/21/14.
 */
public class ClusterDelayMaster extends UntypedActor {

    // TODO:  need to check cache to clean out times if the node isn't responding and has old times in our cache
    private static ConcurrentMap<String, SortedSet<IdTime>> DELAY_MAP = new ConcurrentHashMap<String, SortedSet<IdTime>>();

    private ActorRef mediator;


    // TODO:  have workers send master their time every 500 ms
    // TODO:  have workers request DELAY_MAP every 500 ms

    public ClusterDelayMaster() {

        mediator = DistributedPubSubExtension.get( getContext().system() ).mediator();

        // TODO:  register singleton as location that eveyrone sends to
        // TODO;  register everyone else for singleton to push cluster delay too.


    }

    public static long getDelay( String feed ) {

        // TODO:  add buffer time?

        long time = 0;

        if( DELAY_MAP.containsKey( feed ) && !DELAY_MAP.get( feed ).isEmpty() ) {
            time = DELAY_MAP.get( feed ).first().getInsertTimeMS();
        }

       return time == 0 ? 0 : System.currentTimeMillis() - time;
    }

    @Override
    public void preStart() {

        // TODO:  every 500 ms, query nodes for latest delays
        // context.system.scheduler.schedule
    }

    @Override
    public void postStop() {

    }

    @Override
    public void onReceive( Object message ) throws Exception {

        if( message instanceof Message.NodeDelay  ) {

            Message.NodeDelay  map = (Message.NodeDelay )message;

            mergeMap( map.getMap() );

        }
        else if ( message instanceof Message.ClusterDelayRequest ) {

            getSender().tell( new Message.ClusterDelayResponse( DELAY_MAP ), getSelf() );
        }
        else if ( message instanceof Message.ClusterDelayResponse ) {

            DELAY_MAP = ((Message.ClusterDelayResponse)message).getMap();
        }
    }

    private void mergeMap( Map<String, IdTime>  map ) {

        for( String feed: map.keySet() ) {

            DELAY_MAP.putIfAbsent( feed, new ConcurrentSkipListSet<IdTime>( new IdTimeComparator()) );

            DELAY_MAP.get( feed ).add( map.get( feed ) );
        }
    }


    static class Message {

        static class NodeDelay {

            private Map<String, IdTime> map;

            public NodeDelay ( Map<String, IdTime> m ) {

                map = m;
            }

            public Map<String, IdTime> getMap() {

                return map;
            }
        }

        static class ClusterDelayResponse {

            private ConcurrentMap<String, SortedSet<IdTime>> map;

            public ClusterDelayResponse( ConcurrentMap<String, SortedSet<IdTime>> m ) {

                map = m;
            }

            public ConcurrentMap<String, SortedSet<IdTime>> getMap() {

                return map;
            }
        }

        static class ClusterDelayRequest {

        }
    }
}

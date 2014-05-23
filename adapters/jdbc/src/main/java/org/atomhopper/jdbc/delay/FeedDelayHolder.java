package org.atomhopper.jdbc.delay;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Created by rona6028 on 5/20/14.
 */
public class FeedDelayHolder {

    public static FeedDelayHolder FEED_DELAY = new FeedDelayHolder();

    private ConcurrentMap<String, FeedDelay> mapInsertTimes = new ConcurrentHashMap<String, FeedDelay>();

    public void add( String feed, IdTime eId ) {

        verifyExists( feed );
        mapInsertTimes.get( feed ).add( eId );
    }

    // TODO:  probably more efficient way of doing this
    private void verifyExists( String feed ) {

        mapInsertTimes.putIfAbsent( feed, new FeedDelay() );
    }

    public void remove( String feed, IdTime eId ) {

        verifyExists( feed );
        mapInsertTimes.get( feed ).remove( eId );
    }

    public Map<String, IdTime> getDelayMap( String nodeId ) {

        Map<String, IdTime> map = new HashMap<String, IdTime>();

        for( String feed : mapInsertTimes.keySet() ) {

            map.put( feed, new IdTime( nodeId, mapInsertTimes.get( feed ).getInsertTimeMS() ) );
        }

        return map;
    }
}

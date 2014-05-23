package org.atomhopper.jdbc.delay;

import java.util.Comparator;
import java.util.SortedSet;
import java.util.concurrent.ConcurrentSkipListSet;

/**
 * Created by rona6028 on 5/20/14.
 */
public class FeedDelay {

    public SortedSet<IdTime> entryidTimeSet = new ConcurrentSkipListSet<IdTime>( new IdTimeComparator() );



    public void add( IdTime e ) {

        entryidTimeSet.add( e );
    }

    public long getInsertTimeMS() {

        return entryidTimeSet.isEmpty() ? 0 : entryidTimeSet.first().getInsertTimeMS();
    }

    public void remove( IdTime e ) {

        entryidTimeSet.remove( e );
    }
}

package org.atomhopper.jdbc.delay;

import java.util.Comparator;

/**
 * Created by rona6028 on 5/22/14.
 */
public class IdTimeComparator implements Comparator<IdTime> {

    @Override
    public int compare( IdTime entryIdTime, IdTime entryIdTime2 ) {
        return (int)( entryIdTime.getInsertTimeMS() - entryIdTime2.getInsertTimeMS() );
    }
}

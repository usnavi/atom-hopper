package org.atomhopper.jdbc.delay;

import java.util.Comparator;

/**
 * Created by rona6028 on 5/20/14.
 */
public class IdTime {

    private String id;
    private long time;


    public IdTime( String s ) {

        id = s;
        time = System.currentTimeMillis();
    }

    public IdTime( String s, long t ) {

        id = s;
        time = t;
    }

    public String getId() {

        return id;
    }

    public long getInsertTimeMS() {

        return time;
    }

    @Override
    public boolean equals( Object o ) {

        if( this == o ) {
            return true;
        }
        else if( o instanceof IdTime ) {

            return id.equals( ((IdTime)o).getId() );
        }
        else
            return false;
    }

    @Override
    public int hashCode() {

        return id.hashCode();
    }
}

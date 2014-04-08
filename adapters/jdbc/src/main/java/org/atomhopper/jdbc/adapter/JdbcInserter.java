package org.atomhopper.jdbc.adapter;

import org.atomhopper.adapter.ResponseBuilder;
import org.atomhopper.jdbc.model.PersistedEntry;
import org.atomhopper.jdbc.query.PostgreSQLTextArray;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;


/**
 * This class accepts entries into a queue, and then inserts multiple entries into the database
 * at a time at a regular interval, dictated by the waitTime variable.
 *
 * This class is injected into the JdbcFeedPublisher and configured through spring.
 *
 * Entries can be inserted in the following modes:
 *
 * 1) BATCH_UPDATE - Using the JdbcTemplate.batchUpdate( ) method
 * 2) MULTI_ROW - Using a raw SQL string which executes using the multi-row method.
 * 3) SERIAL - Inserts each entry individually.  Used for evaluation purposes.
 *
 * All are included here for now until we determine which one is most efficient for postgres.
 *
 * Clients submit entries with the insert() call and receive a Result object which they can wait upon
 * to get the status of their insertion.  If any exception is encountered is it thrown by the Result object
 * during the waitFor() call.
 *
 * NOTE:  AllowOverrideDate is not implemented.  The current time is used.
 *
 */
public class JdbcInserter implements Runnable {

    private static final Logger LOG = LoggerFactory.getLogger( JdbcInserter.class );

    public enum InsertMode { BATCH_UPDATE, MULTI_ROW, SERIAL }

    private JdbcTemplate jdbcTemplate;
    private BlockingQueue<Result> queue = new LinkedBlockingQueue<Result>();
    private long waitTime = 100;
    private int maxSize = Integer.MAX_VALUE;
    private boolean running = true;
    private boolean allowOverrideDate = false;
    private String insertSQL = JdbcFeedPublisher.INSERT_SQL;
    private InsertMode mode = InsertMode.BATCH_UPDATE;


    public JdbcInserter() {

        Thread inserter = new Thread( this );

        inserter.start();
    }

    public void setMode( InsertMode m ) {

        mode = m;
    }

    public void setMaxSize( int s ) {

        maxSize = s;
    }

    public void setWaitTimeMS( long w ) {

        waitTime = w;
    }

    // TODO:  shutdown cleanly

    public void setAllowOverrideDate(boolean allowOverrideDate) {

        this.allowOverrideDate = allowOverrideDate;

        if ( allowOverrideDate )
            insertSQL = JdbcFeedPublisher.DATE_OVERRIDE_SQL;
    }


    public void setJdbcTemplate( JdbcTemplate jdbcTemplate ) {

        this.jdbcTemplate = jdbcTemplate;
    }

    public Result insert( PersistedEntry entry ) {

        Result result = new Result( entry );

        queue.add( result );

        return result;
    }


    @Override
    public void run() {

        List<Result> inserts = new ArrayList<Result>();

        while( running ) {

            inserts.clear();

            if( jdbcTemplate != null && !queue.isEmpty() ) {

                queue.drainTo( inserts, maxSize );

                callDB( inserts );
            }

            try {
                Thread.sleep( waitTime );
            }
            catch ( InterruptedException e ) {
                LOG.error( "InterruptedException", e );
            }
        }
    }

    private BatchPreparedStatementSetter createBatchSetter( List<Result> inserts ) {

        return allowOverrideDate ? new OverrideBatchPreparedStatementSetter( inserts ) :
              new StandardBatchPreparedStatementSetter( inserts );
    }

    private void callDB( final List<Result> inserts ) {

        if( mode == InsertMode.BATCH_UPDATE ) {

            BatchPreparedStatementSetter batchSetter = createBatchSetter( inserts );

            try {
                jdbcTemplate.batchUpdate( insertSQL, batchSetter );
            } catch (Exception ex) {

                for( Result result : inserts )
                    result.setException( ex );
            }
            finally {

                for( Result result : inserts )
                    result.provideResult();
            }
        }
        else if ( mode == InsertMode.MULTI_ROW ) {

            StringBuilder sb = new StringBuilder();

            if( allowOverrideDate ) {

                sb.append( "INSERT INTO entries (entryid, creationdate, datelastupdated, entrybody, feed, categories) VALUES " );
            }
            else {

                sb.append( "INSERT INTO entries (entryid, entrybody, feed, categories) VALUES " );
            }

            for( int j = 0; j < inserts.size(); j++ ) {

                PersistedEntry pe = inserts.get( j ).getPersistedEntry();

                StringBuilder sbCat = new StringBuilder();
                sbCat.append( "{" );

                for( int i = 0; i < pe.getCategories().length; i++ ) {

                    if( i > 0 )
                        sbCat.append( ", " );

                    sbCat.append( "\"" + pe.getCategories()[ i ] + "\"" );

                }

                sbCat.append( "}" );

                if ( j > 0 )
                    sb.append( ", " );

                if( allowOverrideDate ) {

                    // TODO:  need to figure out the timestampe format
                    sb.append( "( '" + pe.getEntryId() + "', '"
                                     +  "now' , '"
                                     + "now' , '"
                                     + pe.getEntryBody() + "', '"
                                     + pe.getFeed() + "', '"
                                     + sbCat.toString() + "' )" );
                }
                else {

                    sb.append( "( '" + pe.getEntryId() + "', '"
                                     + pe.getEntryBody() + "', '"
                                     + pe.getFeed() + "', '"
                                     + sbCat.toString() + "' )" );
                }
            }

            sb.append( ";" );

            try {
                jdbcTemplate.execute( sb.toString() );
            } catch (Exception ex) {

                for( Result result : inserts )
                    result.setException( ex );
            }
            finally {

                for( Result result : inserts )
                    result.provideResult();
            }
        }
        else {
            for( Result result : inserts ) {

                PersistedEntry persistedEntry = result.getPersistedEntry();

                try {
                    Object[] params = null;
                    if ( allowOverrideDate ) {
                        // we have extra date parameters
                        params = new Object[]{
                              persistedEntry.getEntryId(), persistedEntry.getCreationDate(), persistedEntry.getDateLastUpdated(),
                              persistedEntry.getEntryBody(), persistedEntry.getFeed(), new PostgreSQLTextArray(persistedEntry.getCategories())
                        };
                    } else {
                        params = new Object[]{
                              persistedEntry.getEntryId(), persistedEntry.getEntryBody(), persistedEntry.getFeed(),
                              new PostgreSQLTextArray(persistedEntry.getCategories())
                        };
                    }
                    jdbcTemplate.update(insertSQL, params);

                } catch (Exception ex) {

                    result.setException( ex );
                }
                finally {

                    result.provideResult();
                }
            }
        }
    }

    /**
     * This class is returned to the client after they submit an entry.  They wait on the object using the waitFor()
     * method.  If an error is encountered, the exception is rethrown by the waitFor() method.
     */
    public static class Result {

        private PersistedEntry persistedEntry;
        private Exception exception;
        private CountDownLatch latch = new CountDownLatch( 1 );

        public Result( PersistedEntry pe ) {

            persistedEntry = pe;
        }

        protected PersistedEntry getPersistedEntry() {

            return persistedEntry;
        }

        protected void setException( Exception e ) {

            exception = e;
        }

        protected void provideResult() {

            latch.countDown();
        }

        public void waitFor() throws Exception {

            latch.await();

            if( exception != null ) {

                throw exception;
            }
        }
    }

    public static class StandardBatchPreparedStatementSetter  implements BatchPreparedStatementSetter {

        private List<Result> listInserts;

        public StandardBatchPreparedStatementSetter( List<Result> list ) {

            listInserts = list;
        }

        @Override
        public void setValues( PreparedStatement ps, int i ) throws SQLException {

            PersistedEntry pe = listInserts.get( i ).getPersistedEntry();

            ps.setString( 1, pe.getEntryId() );
            ps.setString( 2, pe.getEntryBody() );
            ps.setString( 3, pe.getFeed() );
            ps.setArray( 4, new PostgreSQLTextArray( pe.getCategories() ) );
        }

        @Override
        public int getBatchSize() {
            return listInserts.size();
        }
    }

    public static class OverrideBatchPreparedStatementSetter implements BatchPreparedStatementSetter {

        private List<Result> listInserts;

        public OverrideBatchPreparedStatementSetter( List<Result> list ) {

            listInserts = list;
        }

        @Override
        public void setValues( PreparedStatement ps, int i ) throws SQLException {

            PersistedEntry pe = listInserts.get( i ).getPersistedEntry();

            ps.setString( 1, pe.getEntryId() );
            ps.setDate( 2, new java.sql.Date( pe.getCreationDate().getTime() ) );
            ps.setDate( 3, new java.sql.Date( pe.getDateLastUpdated().getTime() ) );
            ps.setString( 4, pe.getEntryBody() );
            ps.setString( 5, pe.getFeed() );
            ps.setArray( 6, new PostgreSQLTextArray( pe.getCategories() ) );
        }

        @Override
        public int getBatchSize() {
            return listInserts.size();
        }
    }
}

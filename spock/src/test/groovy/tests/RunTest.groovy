package org.atomhopper.tests

import  org.atomhopper.test.framework.AtomHopperTest

class RunTest extends AtomHopperTest {

    /*
     * This test verifies that we get a 201 & entry in the response body when it posts to a feed.
     * Tests feeds listed in the where list.
     */
    def "post feeds"(String feed, int code ) {

        def headers = ["Content-type":"application/atom+xml"]

        expect:
        def resp = doPostWithToken( properties.getProperty( "ah.endpoint" ) + feed,
                """<?xml version="1.0"?>
             <entry xmlns="http://www.w3.org/2005/Atom">
               <title>Atom Hopper Test</title>
               <author>
                 <name>Atom Test</name>
               </author>
               <content type="text">Atom Hopper deployment test - check out https://github.com/rackerlabs/atom-hopper/wiki/Release-Notes or https://one.rackspace.com/display/CIT/Atom+Hopper+Deployment+-+Release+Notes</content>
               <category term="Atomhopper Post Test" />
             </entry>""",
                headers )

        resp.code == code
        nodeExists( resp.body, "/entry" )

        where:
        feed                | code
        "/demo/events"      | 201
        "/functest1/events" | 201
    }

    /*
     * This test verifies that a feed's response is a 200 & contains a self link, current link & last link.
     * Tests feeds listed in the where list.
     */
    def "get feeds"( String feed, int code ) {

        expect:
        def resp = doGetWithToken( properties.getProperty( "ah.endpoint" ) + feed )
        resp.code == code
        nodeExists( resp.body, "/feed/link[@rel='self']" )
        nodeExists( resp.body, "/feed/link[@rel='current']" )
        nodeExists( resp.body, "/feed/link[@rel='last']" )


        where:
        feed                | code
        "/demo/events"      | 200
        "/functest1/events" | 200
    }
}

package org.atomhopper.test.framework

import org.apache.xpath.XPathAPI
import org.xml.sax.InputSource
import spock.lang.Shared
import spock.lang.Specification

import javax.xml.parsers.DocumentBuilderFactory

abstract class AtomHopperTest extends Specification {

    @Shared token
    @Shared properties
    def httpClient = new SimpleHttpClient()
    def documentBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder()

    def setupSpec() {

        properties = new Properties()
        properties.load(ClassLoader.getSystemResource("test.properties").openStream())

        // get auth token
        token = AuthToken.singleton.getAuthTokenRole( properties.getProperty( "auth.url"),
                properties.getProperty( "auth.user" ),
                properties.getProperty( "auth.key" ) )
    }

    protected def doPostWithToken( String url, String body, Map headers = new HashMap() ) {

        def map = new HashMap( headers )
        map.put( "X-Auth-Token", token.token )

        return httpClient.doPost( url, map, body )
    }

    protected def doGetWithToken( String url, Map headers = new HashMap() ) {

        def map = new HashMap( headers )
        map.put( "X-Auth-Token", token.token )

        return httpClient.doGet( url, map )
    }

    protected def nodeExists( String body, String xPath ) {

        return runXPath( body, xPath ).nodelist().length > 0
    }

    private def runXPath( String body, String xPath ) {

        def root = documentBuilder.parse( new InputSource( new StringReader( body ) ) ).documentElement

        return XPathAPI.eval( root, xPath )

    }
}

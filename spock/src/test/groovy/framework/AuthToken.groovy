package  org.atomhopper.test.framework


import org.apache.xpath.XPathAPI
import org.xml.sax.InputSource

import javax.xml.parsers.DocumentBuilderFactory

class TokenRole {

    def token
    def role
}

class AuthToken {

    static def singleton = new AuthToken()

    private def headerMap = [ "Accept":"application/xml", "Content-type":"application/json" ]

    private def mapTokenRole = new HashMap<String, TokenRole>()

    private AuthToken() { }

    def getAuthTokenRole( String url, String user, String key ) {

        def mapKey = url + "<>" + user

        if ( !mapTokenRole.containsKey( mapKey ) ) {

            synchronized ( this ) {

                if ( !mapTokenRole.containsKey( mapKey ) ) {

                    def httpClient = new SimpleHttpClient()

                    def resp = httpClient.doPost( url,
                            headerMap,
                            "{ \"auth\":{ \"RAX-KSKEY:apiKeyCredentials\":{ \"username\":\"" + user + "\", \"apiKey\":\"" + key + "\" } } }" )

                    if ( resp.getCode() != 200 ) {

                        throw new Exception( "TODO:  failed auth call: " + resp.getCode() )
                    }

                    def tokenRole = new TokenRole()

                    def builder     = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                    def body     = builder.parse( new InputSource( new StringReader( resp.body ) ) ).documentElement

                    tokenRole.token = XPathAPI.eval( body, "/access/token/@id" ).str()
                    tokenRole.role = XPathAPI.eval( body, "/access/user/roles/role[@id=\"1\"]/@name" ).str()

                    mapTokenRole.put( mapKey, tokenRole )
                }
            }

            return mapTokenRole.get( mapKey )
        }
    }
}


package org.atomhopper.test.framework

import org.apache.http.client.HttpClient
import org.apache.http.client.methods.*
import org.apache.http.conn.scheme.Scheme
import org.apache.http.conn.ssl.SSLSocketFactory
import org.apache.http.entity.StringEntity
import org.apache.http.impl.client.DefaultHttpClient
import org.codehaus.groovy.runtime.DefaultGroovyMethods

import javax.net.ssl.SSLContext
import java.nio.charset.Charset

class SimpleHttpClient {

    static Scheme scheme

    static {

        SSLSocketFactory socketFactory = new SSLSocketFactory(
                SSLContext.getDefault(),
                SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);
        scheme = new Scheme("https", 443, socketFactory);
    }


    def makeRequest(HttpUriRequest httpMethod, Map requestHeaders) {

        def HttpClient client

        try {
            client = new DefaultHttpClient()
            client.getConnectionManager().getSchemeRegistry().register( scheme )

            requestHeaders.each { key, value ->
                httpMethod.addHeader(key, value)
            }

            def httpResponse = client.execute(httpMethod)

            def response = new Response( httpResponse.statusLine.statusCode )
            if (httpResponse.entity) {
                response.body = httpResponse.entity.content.getText()
            }

            httpResponse.getAllHeaders().each { h -> response.headers.add( h.name, h.value ) }

            return response
        } finally {
            if (client != null && client.getConnectionManager() != null) {
                client.getConnectionManager().shutdown();
            }
        }
    }

    def doGet(String path, Map headers = new HashMap()) {

        HttpGet httpGet = new HttpGet( path )

        makeRequest(httpGet, headers)
    }


    def doPut(String path, Map headers, String payload) {

        HttpPut httpPut = new HttpPut( path )
        if (payload) {
            httpPut.setEntity(new StringEntity(payload, Charset.forName("UTF-8")))
        }

        makeRequest(httpPut, headers, requestPath)
    }

    def doDelete(String path, Map headers, String payload) {

        EntityEnclosingDelete httpDelete = new EntityEnclosingDelete()
        URI uri = URI.create( path )
        httpDelete.setURI(uri)
        if (payload) {
            httpDelete.setEntity(new StringEntity(payload, Charset.forName("UTF-8")))
        }

        makeRequest(httpDelete, headers)
    }



    def doPost(String path, Map headers, String payload) {

        HttpPost httpPost = new HttpPost( path )
        if (payload) {
            httpPost.setEntity(new StringEntity(payload, Charset.forName("UTF-8")))
        }

        makeRequest(httpPost, headers)
    }
}

class EntityEnclosingDelete extends HttpEntityEnclosingRequestBase {

    @Override
    public String getMethod() {
        return "DELETE";
    }

}

/**
 *
 * A simple name-value pair.
 *
 */
public class Header {

    public String name;
    public String value;

    public Header(String name, String value) {
        this.name = name;
        this.value = value;
    }

    @Override
    public String toString() {
        return this.name + ": " + this.value;
    }
}

/**
 *
 * A collection class for HTTP Headers. This class combines aspects of a list
 * and a map. Lookup is always case-insenitive. A key can be added multiple
 * times with different values, and all of those values will be kept in the same
 * order as entered.
 *
 */
class HeaderCollection {

    List<Header> _headers = new ArrayList<Header>();

    HeaderCollection() {
    }

    HeaderCollection(Map<? extends Object, ? extends Object> map) {
        for (Map.Entry entry : map.entrySet()) {
            _headers.add(new Header(entry.getKey().toString(), entry.getValue().toString()));
        }
    }

    HeaderCollection(List<Header> list) {
        for (Header header : list) {
            _headers.add(header);
        }
    }

    HeaderCollection(HeaderCollection headers) {
        for (Header header : headers._headers) {
            _headers.add(new Header(header.name, header.value));
        }
    }

    boolean contains(String name) {
        for (Header header : _headers) {
            if (name.equalsIgnoreCase(header.name)) {
                return true;
            }
        }

        return false;
    }

    public Object each(Closure closure) {
        return DefaultGroovyMethods.each(_headers, closure);
    }

    public Object eachWithIndex(Closure closure) {
        return DefaultGroovyMethods.eachWithIndex(_headers, closure);
    }

    public int size() {
        return _headers.size();
    }

    public void add(Object name, Object value) {
        add(new Header(name.toString(), value.toString()));
    }

    public void add(String name, String value) {
        add(new Header(name, value));
    }

    public void add(Header header) {
        _headers.add(header);
    }

    public int getCountByName(String name) {

        int count = 0;

        for (Header header : _headers) {
            if (header.name.equalsIgnoreCase(name)) {
                count++;
            }
        }

        return count;
    }

    public List<String> findAll(String name) {

        List<String> values = new ArrayList<String>();

        for (Header header : _headers) {
            if (header.name.equalsIgnoreCase(name)) {
                values.add(header.value);
            }
        }

        return values;
    }

    public void deleteAll(String name) {

        ArrayList<Header> toRemove = new ArrayList<Header>();

        for (Header header : _headers) {
            if (name.equalsIgnoreCase(header.name)) {
                toRemove.add(header);
            }
        }

        _headers.removeAll(toRemove);
    }

    public String[] getNames() {
        ArrayList<String> names = new ArrayList<String>();
        for (Header header : _headers) {
            names.add(header.name);
        }

        return names.toArray(new String[0]);
    }

    public String[] getValues() {
        ArrayList<String> values = new ArrayList<String>();
        for (Header header : _headers) {
            values.add(header.value);
        }

        return values.toArray(new String[0]);
    }

    public Header[] getItems() {
        return _headers.toArray(new Header[0]);
    }

    public String getAt(String name) {
        return getFirstValue(name);
    }

    public String getFirstValue(String name) {
        return getFirstValue(name, null);
    }
    public String getFirstValue(String name, String defaultValue) {
        for (Header header : _headers) {
            if (name.equalsIgnoreCase(header.name)) {
                return header.value;
            }
        }

        return defaultValue;
    }

    public String toString() {
        return _headers.toString();
    }
}

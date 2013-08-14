package org.atomhopper.test.framework

class Response {

    /*
     * A simple HTTP Response, with status code, headers, and
     * body.
     *
     */

    int code
    HeaderCollection headers
    def body

    public Response(code, headers=null, body=null) {

        """Parameters:

        code - A numerical status code. This doesn't have to be a valid HTTP
            status code; for example, values >= 600 are acceptable also, as
            are non-numbers.

        headers - An optional collection of name/value pairs, either a mapping
            object like ``['name': 'value']``, or a HeaderCollection. Defaults
            to an empty map.

        body - An optional request body. Defaults to the empty string. Both
            strings and byte arrays are acceptable. All other types are
            toString'd.
        """

        this.code = code

        if (headers == null) {
            headers = [:]
        }

        if (body == null) {
            body = ""
        } else if (!(body instanceof byte[]) &&
                !(body instanceof String)) {
            body = body.toString()
        }

        this.code = code
        this.headers = new HeaderCollection(headers)
        this.body = body
    }

    @Override
    String toString() {
        sprintf('Response(code=%s, headers=%s, body=%s)', code, headers, body)
    }
}

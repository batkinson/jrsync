package com.github.batkinson.jrsync.zsync;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;


class TestRangeRequestFactory implements RangeRequestFactory {

    RequestHandler handler;

    TestRangeRequestFactory(RequestHandler handler) {
        this.handler = handler;
    }

    @Override
    public RangeRequest create() throws IOException {
        return new TestRangeRequest(handler);
    }
}

class TestRangeRequest implements RangeRequest {

    private static final String CONTENT_TYPE = "Content-Type";

    boolean isClosed;
    private Map<String, String> requestHeaders = new HashMap<>();
    private Response response = Response.DEFAULT;
    private RequestHandler service = new DefaultRequestHandler();

    TestRangeRequest(RequestHandler handler) {
        if (handler != null)
            service = handler;
    }

    @Override
    public int getResponseCode() throws IOException {
        response = service.service(requestHeaders);
        return response.status;
    }

    @Override
    public String getContentType() {
        return response.headers.get(CONTENT_TYPE);
    }

    @Override
    public String getHeader(String name) {
        return response.headers.get(name);
    }

    @Override
    public void setHeader(String name, String value) {
        requestHeaders.put(name, value);
    }

    @Override
    public InputStream getInputStream() throws IOException {
        return response.body;
    }

    @Override
    public void close() throws IOException {
        isClosed = true;
    }
}

class Response {

    final static Response DEFAULT = new Response(
            404, Collections.EMPTY_MAP, new ByteArrayInputStream("".getBytes())
    );

    final int status;
    final Map<String, String> headers;
    final InputStream body;

    Response(int status, Map<String, String> headers, InputStream body) {
        this.status = status;
        this.headers = headers;
        this.body = body;
    }
}

interface RequestHandler {
    Response service(Map<String, String> headers);
}

class DefaultRequestHandler implements RequestHandler {
    @Override
    public Response service(Map<String, String> requestHeaders) {
        return Response.DEFAULT;
    }
}
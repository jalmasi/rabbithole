package org.neo4j.community.console;

import org.slf4j.Logger;
import spark.HaltException;
import spark.Request;
import spark.Response;

import javax.servlet.http.HttpServletRequest;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.util.Map;

import static spark.Spark.halt;

/**
 * @author mh
 * @since 08.04.12
 */
abstract class Route implements spark.Route {

    private static final Logger LOG = org.slf4j.LoggerFactory.getLogger(Route.class);

    public String stop(int status, String message) {
        halt(status, message);
        return message;
    }

    @Override
    public Object handle(Request request, Response response) {
        try {
            doBefore(request,response);
            return doHandle(request, response, service(request));
        } catch (OutOfMemoryError e) {
            reset(request);
            SessionService.cleanSessions();
            return handleException(e);
        } catch (HaltException he) {
            throw he;
        } catch (Exception e) {
            return handleException(e);
        }
    }

    private Object handleException(Throwable e) {
        e.printStackTrace();
        halt(500, e.getMessage());
        return e.getMessage();
    }

    protected abstract Object doHandle(Request request, Response response, Neo4jService service) throws Exception;
    protected void doBefore(Request request, Response response) {
    }

    protected String param(Request request, String param, String defaultValue) {
        String data = request.queryParams(param);
        if (data == null || data.isEmpty()) {
            data = defaultValue;
        } else {
            LOG.warn(param+": "+data);
        }
        return data;
    }
    protected String param(Map input, String param, String defaultValue) {
        if (input==null) return defaultValue;
        String data = (String) input.get(param);
        if (data == null || data.isEmpty()) {
            data = defaultValue;
        } else {
            LOG.warn(param+": "+data);
        }
        return data;
    }

    protected long trace(String msg, long time) {
        long now = System.currentTimeMillis();
        LOG.warn("## " + msg + " took: " + (now - time) + " ms.");
        return now;
    }

    protected String getSessionId(Request request) {
        return SessionService.getSessionId(request.raw());
    }

    protected void reset(Request request) {
        SessionService.reset(request.raw());
    }

    protected Neo4jService service(Request request) {
        return SessionService.getService(request.raw(),true);
    }

    protected String baseUri(HttpServletRequest request, String query, final String path) {
        final String requestURI = request.getRequestURL().toString();
        try {
            final URI uri = new URI(requestURI);
            return new URI(uri.getScheme(),null,uri.getHost(),uri.getPort(), path,query,null).toString();
        } catch (URISyntaxException e) {
            throw new RuntimeException("Error parsing URI from "+requestURI+" query "+query);
        }
    }

    protected String encodeParam(String param, String value) throws UnsupportedEncodingException {
        if (value==null || value.trim().isEmpty()) return "";
        return param + "=" + URLEncoder.encode(value, "UTF-8");
    }
}

package decimill.server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import decimill.parser.CompilerException;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import jviz.JvizException;
import org.json.JSONException;

/**
 * @author David
 */
public class Main {

    /**
     * @param args
     */
    public static void main(String[] args) {

        // Make sure that the number of parameters is exactly one
        if (args.length != 1) {
            System.out.println("Example usage: \"java -jar decimill-server.jar 88\"");
            System.exit(-1);
        }

        int portNumber = 0;

        // Parse first parameter to a port number
        try {
            portNumber = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            System.out.println("Port number must be an integer");
            System.exit(-1);
        }

        try {
            InetSocketAddress addr = new InetSocketAddress(portNumber);
            HttpServer server = HttpServer.create(addr, 0);
            server.createContext("/compile", new CompileRequestHandler());
            server.createContext("/img", new ImgRequestHandler());
            server.setExecutor(null);
            server.start();
        } catch (IOException e) {
            System.out.println(e);
        }
    }

    public static class CompileRequestHandler implements HttpHandler {

        @Override
        public void handle(HttpExchange http) throws IOException {

            System.out.println("Accepting a http request from " + http.getRemoteAddress());

            String requestBody = readRequestBody(http);
            Response response;

            try {
                Request request = Request.parseRequest(requestBody);
                String compiled = "";

                if (request.action.equals("model")) {
                    compiled = Worker.compileModel(
                            request.model.id,
                            request.model.namespace,
                            request.model.text
                    );
                }

                response = new Response("OK", compiled);

            } catch (JSONException | JvizException e) {
                response = new Response("Error", e.getMessage());
            } catch (CompilerException e) {
                response = new CompilerExceptionResponse(e.getMessage(), e.getLine(), e.getCharPos());
            }

            String responseString = response.toString();

            http.sendResponseHeaders(200, responseString.length());
            OutputStream os = http.getResponseBody();
            try (PrintWriter writer = new PrintWriter(os)) {
                writer.write(responseString);
            }
        }

        /**
         * Reads and returns the request body of a HTTP Post query.
         *
         * @param http HttpExchange object
         * @return String with request body
         */
        private String readRequestBody(HttpExchange http) {

            BufferedReader in;
            String requestBody = null;

            try {
                in = new BufferedReader(
                        new InputStreamReader(http.getRequestBody()));
                String line;
                requestBody = "";
                while ((line = in.readLine()) != null) {
                    requestBody += line + "\n";
                }
                in.close();
            } catch (IOException e) {
                System.out.println(e);
            }

            return requestBody;
        }
    }

    public static class ImgRequestHandler implements HttpHandler {

        @Override
        public void handle(HttpExchange http) throws IOException {

            System.out.println("Accepting an image request from " + http.getRemoteAddress());

            // Build image path from URI (trim the "/img/" part)
            String uriString = http.getRequestURI().toString();
            String path = "img/" + uriString.substring(5, uriString.length());

            File imgPath = new File(path);

            try (OutputStream outputStream = http.getResponseBody()) {
                http.sendResponseHeaders(200, imgPath.length());
                Files.copy(imgPath.toPath(), outputStream);
            } catch (IOException e) {
                System.out.println(e);
            }
        }
    }

}

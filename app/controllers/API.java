package controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import play.libs.F;
import play.libs.Json;
import play.libs.ws.WSClient;
import play.libs.ws.WSRequest;
import play.libs.ws.WSResponse;
import play.mvc.BodyParser;
import play.mvc.Controller;
import play.mvc.Result;
import play.mvc.Results;

import javax.inject.Inject;

public class API extends Controller {

    @Inject
    WSClient ws;

    private static final String API_VERSION = "1.0";
    private Config config;

    public F.Promise<Result> points(String n, String s, String w, String e) {
        WSRequest request = ws.url(getSearchUrl());
        F.Promise<WSResponse> responsePromise = request.post(getRequestBody(n, s, w, e));

        play.Logger.debug(getSearchUrl());
        play.Logger.debug(getAddPointUrl());

        return responsePromise.map(response -> ok(getJsonPoints(response.asJson())));
    }

    private JsonNode getJsonPoints(JsonNode response) {
        ObjectNode result = Json.newObject();
        result.put("result", "ok");
        ArrayNode responseArray = (ArrayNode) response.get("hits").get("hits");
        ArrayNode pointsArray = Json.newArray();
        for (JsonNode item : responseArray) {
            pointsArray.add(item.get("_source"));
        }
        result.putArray("points").addAll(pointsArray);
        return result;
    }

    private String getRequestBody(String n, String s, String w, String e) {
        return String.format("{\n" +
                        " \"size\" : 1000," +   // number of documents (points)\n
                        " \"query\":{\n" +
                        "    \"bool\" : {\n" +
                        "        \"must\" : {\n" +
                        "            \"match_all\" : {}\n" +             // search query (empty match all documents)\n
                        "         },\n" +
                        "        \"filter\" : {\n" +
                        "            \"geo_bounding_box\" : {\n" +       // bbox (s,w) -> (n,e)\n"
                        "                \"location\" : {\n" +
                        "                    \"top_left\" : {\n" +
                        "                        \"lat\" : %s,\n" + //n
                        "                        \"lon\" : %s\n" +  //w
                        "                    },\n" +
                        "                    \"bottom_right\" : {\n" +
                        "                        \"lat\" : %s,\n" + //s
                        "                        \"lon\" : %s\n" + //e
                        "                    }\n" +
                        "                }\n" +
                        "            }\n" +
                        "        }\n" +
                        "    }\n" +
                        "  }\n" +
                        "}",
                n, w, s, e);
    }

    @BodyParser.Of(BodyParser.Json.class)
    public F.Promise<Result> addPoint() {

        JsonNode json = request().body().asJson();
        String name = json.findPath("name").textValue();
        double latitude = json.get("location").get("lat").asDouble();
        double longitude = json.get("location").get("lon").asDouble();

        ObjectNode newPoint = Json.newObject();
        newPoint.put("name", name);
        newPoint.put("opening_hours", "");
        newPoint.putObject("location")
                .put("lat", latitude)
                .put("lon", longitude);
        play.Logger.debug(String.format("Name: %s, lat: %f, lon: %f; \n Json: %s", name, latitude, longitude, newPoint));

        WSRequest request = ws.url(getAddPointUrl()).setContentType("application/json");

        F.Promise<WSResponse> responsePromise = request.post(newPoint.toString());

        return responsePromise.map(response -> {
            String result = response.getBody();
            play.Logger.debug(result);
            return ok(response.asJson());
        });
    }

    public F.Promise<Result> status() {
        ObjectNode status = Json.newObject();
        status.put("status", "ok");
        status.put("version", API_VERSION);
        F.Promise<JsonNode> responsePromise = F.Promise.promise(() -> status);
        return responsePromise.map(Results::ok);
    }

    private String getSearchUrl() {
        config = ConfigFactory.load();
        return config.getString("url.search");
    }

    private String getAddPointUrl() {
        config = ConfigFactory.load();
        return config.getString("url.addPoint");
    }

}

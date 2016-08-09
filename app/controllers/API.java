package controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.apache.commons.codec.binary.Hex;
import play.libs.F;
import play.libs.Json;
import play.libs.ws.WSClient;
import play.libs.ws.WSRequest;
import play.libs.ws.WSResponse;
import play.mvc.*;

import javax.inject.Inject;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

public class API extends Controller {

    @Inject
    WSClient ws;

    private static final String API_VERSION = "0.2.1";
    private Config config = ConfigFactory.load();

    public F.Promise<Result> points(String n, String s, String w, String e) {
        WSRequest request = ws.url(getSearchUrl());
        F.Promise<WSResponse> responsePromise = request.post(getRequestBody(n, s, w, e));

        play.Logger.debug(String.format("N - : %s, E - : %s, W - : %s, E - : %s", n, s, w, e));

        return responsePromise.map(response -> ok(getJsonPoints(response.asJson())));
    }

    @BodyParser.Of(BodyParser.Json.class)
    public F.Promise<Result> addPoint() {

        JsonNode json = request().body().asJson();
        play.Logger.debug("AddPoint (Json): "+request().body().asJson().toString());

        String name = json.findPath("name").textValue();
        double latitude = json.get("location").get("lat").asDouble();
        double longitude = json.get("location").get("lon").asDouble();
        float rating = json.get("rating").floatValue();
        String id_token = json.findPath("access_token").textValue();
        String user_name = json.findPath("user_name").textValue();
        String user_id = json.findPath("user_id").textValue();

        latitude = getSixDecimalFormatNumber(latitude);
        longitude = getSixDecimalFormatNumber(longitude);

        ObjectNode newPoint = getNewPointJson(name, "", latitude, longitude);
        ObjectNode cafeInfo = getCafeInfoJson(rating, user_id, user_name);

        play.Logger.debug(String.format("\n Json newPoint: %s \n Json CafeInfo: %s",
                                            newPoint, cafeInfo));

        String id = generateId(name+latitude+longitude);

        WSRequest requestNewPoint = ws.url(getAddPointUrl(id)).setContentType("application/json");
        F.Promise<WSResponse> responsePromise;
        if (isValidToken(id_token)) {
            responsePromise = requestNewPoint.post(newPoint);
            return responsePromise.map(response -> {
                if (response.getStatus() == Http.Status.CREATED && addCafeInfo(id, cafeInfo)) {
                    return ok(response.asJson());
                } else {
                    return badRequest(response.asJson());
                }
            });

        } else {
            responsePromise = null;
            ObjectNode status = Json.newObject();
            status.put("status", "error");
            status.put("point", newPoint);
            status.put("reason", "invalid token");
            return responsePromise.map(wsResponse -> unauthorized(status));
        }
    }

    private boolean addCafeInfo(String id, ObjectNode cafeInfo) {
        WSRequest requestCafeInfo = ws.url(getCafeInfoUrl(id)).setContentType("application/json");
        F.Promise<WSResponse> responsePromise = requestCafeInfo.post(cafeInfo);
        return responsePromise.map(response -> response.getStatus() == Http.Status.CREATED).get(5000);
    }

    public F.Promise<Result> testValidate(String token) {
        WSRequest request = ws.url(getValidateTokenUrl(token));

        F.Promise<WSResponse> responsePromise = request.get();
        return responsePromise.map(response -> {
            play.Logger.debug(response.getBody());
            if (response.getStatus() == 200) {
                return ok(response.getBody());
            } else {
                return unauthorized("fail");
            }
        });
    }

    public F.Promise<Result> status() {
        ObjectNode status = Json.newObject();
        status.put("status", "ok");
        status.put("version", API_VERSION);
        F.Promise<JsonNode> responsePromise = F.Promise.promise(() -> status);
        return responsePromise.map(Results::ok);
    }

    public F.Promise<Result> getCafeInfo(String id) {
        WSRequest request = ws.url(getCafeInfoUrl(id));
        F.Promise<WSResponse> responsePromise = request.get();
        return responsePromise.map(response -> ok(response.asJson()));
    }






    private JsonNode getJsonPoints(JsonNode response) {
        ObjectNode result = Json.newObject();
        result.put("result", "ok");
        ArrayNode responseArray = (ArrayNode) response.get("hits").get("hits");
        ArrayNode pointsArray = Json.newArray();
        for (JsonNode item : responseArray) {
            pointsArray.add(item);
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

    private Boolean isValidToken(String idStringToken)  {
        WSRequest request = ws.url(getValidateTokenUrl(idStringToken));
        F.Promise<WSResponse> responsePromise = request.get();
        return responsePromise.map(response -> response.getStatus() == 200).get(5000);
    }


    private String getSearchUrl() {
        return config.getString("url.search");
    }

    private String getAddPointUrl(String id) {
        return config.getString("url.addPoint")+id;
    }

    private String getValidateTokenUrl(String id_token) {
        return config.getString("url.validateToken")+id_token;
    }

    private String getCafeInfoUrl(String id) {
        return config.getString("url.cafeInfo")+id;
    }



    private String generateId(String entropy) {
        try {
            MessageDigest cript = MessageDigest.getInstance("SHA-1");
            cript.reset();
            cript.update(entropy.getBytes());
            return new String(Hex.encodeHex(cript.digest()));
        } catch (NoSuchAlgorithmException ex) {
            return null;
        }
    }

    private double getSixDecimalFormatNumber(double number) {
        String newNumber = String.format(Locale.US, "%.6f", number);
        return Double.parseDouble(newNumber);
    }

    private ObjectNode getNewPointJson(String name, String opening_hours, double latitude, double longitude) {
        ObjectNode newPoint = Json.newObject();
        newPoint.put("name", name);
        newPoint.put("opening_hours", opening_hours);
        newPoint.putObject("location")
                .put("lat", latitude)
                .put("lon", longitude);
        return newPoint;
    }

    private ObjectNode getCafeInfoJson(float last_rating, String user_id, String user_name) {
        ObjectNode cafeInfo = Json.newObject();
        cafeInfo.put("last_rating", last_rating);
        cafeInfo.putObject("added_by")
                .put("user_id", user_id)
                .put("user_name", user_name);
        return cafeInfo;
    }

}

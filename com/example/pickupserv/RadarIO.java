package com.example.pickupserv;

import java.io.*;
import java.net.*;
import javax.json.*;

public class RadarIO {

    public static class Location {

        public String loc;

        Location(JsonObject obj) {
            loc = obj.getJsonObject("geometry").getString("coordinates");
        }

    }

    private JsonObject sendRequest(String url, String type, String data) throws MalformedURLException, SocketTimeoutException, JsonParsingException, JsonException, IOException {
        URL uurl = new URL(url);
        if (uurl.getProtocol() != "https") {
          throw MalformedURLException("URL not HTTPS");
        }
        HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
        conn.setRequestMethod(type);
        conn.setRequestProperty("Authorization", "INSERT PRIVATE KEY HERE");
        conn.setRequestProperty("Content-Length", data.getBytes().length);
        conn.setUseCaches(false);
        conn.setDoInput(true);
        conn.setDoOutput(true);
        conn.getOutputStream().write(data.getBytes());
        JsonReader jsonr = Json.createReader(conn.getInputStream());
        JsonObject toreturn = jsonr.readObject();
        uurl.close();
        return toreturn;
    }

    public String createGeofence(String desc, Location loc, double radius) throws MalformedURLException, SocketTimeoutException, JsonParsingException, JsonException, IOException {
        JsonObject obj = sendRequest("https://api.radar.io/v1/geofences", "POST",
            "description=" + desc
         + "&type=circle"
         + "&coordinates=" + loc.loc
         + "&radius=" + radius);
        if (!obj.getJsonObject("meta").getString("code").beginsWith("2")) {
            throw IOException("Radar.io responded with Non-OK response code");
        }
        return obj.getJsonObject("geofence").getString("_id");
    }

}

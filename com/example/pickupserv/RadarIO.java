package com.example.pickupserv;

import netscape.javascript.JSObject;

import java.io.*;
import java.net.*;
import java.util.ArrayList;
import javax.json.*;

public class RadarIO {

    public static class Location {

        public String loc;
        public String latlong;

        Location(JsonObject obj) {
            loc = obj.getJsonObject("geometry").getString("coordinates");
            String temp = loc.substring(1, loc.length() - 1);
            temp = loc.split(",");
            latlong = temp[1] + "," + temp[0];
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

    /**
     * Create a new geofence with desc and
     * @param desc
     * @param loc
     * @param radius
     * @return
     * @throws MalformedURLException
     * @throws SocketTimeoutException
     * @throws JsonParsingException
     * @throws JsonException
     * @throws IOException
     */
    public String createGeofence(String desc, Location loc, double radius) throws MalformedURLException,
            SocketTimeoutException, JsonParsingException, JsonException, IOException {
        JsonObject obj = sendRequest("https://api.radar.io/v1/geofences", "POST",
            "description=" + desc
         + "&type=circle"
         + "&coordinates=" + loc.loc
         + "&radius=" + radius);
        if (!obj.getJsonObject("meta").getString("code").startsWith("2")) {
            throw IOException("Radar.io responded with Non-OK response code", obj.getJsonObject("meta").getString("code"));
        }
        return obj.getJsonObject("geofence").getString("_id");
    }

    /**
     * Delete an existing geofence.
     * @param id
     * @param loc
     * @param radius
     * @throws MalformedURLException
     * @throws SocketTimeoutException
     * @throws JsonParsingException
     * @throws JsonException
     * @throws IOException
     */
    public void deleteGeofence(String id) throws MalformedURLException,
            SocketTimeoutException, JsonParsingException, JsonException, IOException {
        JsonObject obj = sendRequest("https://api.radar.io/v1/geofences/" + id, "DELETE");
        if (!obj.getJsonObject("meta").getString("code").startsWith("2")) {
            throw IOException("Radar.io responded with Non-OK response code:", obj.getJsonObject("meta").getString("code"));
        }
    }

    public JsonObject getUser(String deviceId) throws MalformedURLException,
            SocketTimeoutException, JsonParsingException, JsonException, IOException {
        JSObject obj = sendRequest("https://api.radar.io/v1/users/" + deviceId, "GET");
        if (!obj.getJsonObject("meta").getString("code").startsWith("2")) {
            throw IOException("Radar.io responded with Non-OK response code:", obj.getJsonObject("meta").getString("code"));
        }
        return obj;

    }

    public ArrayList<> searchGeofences(int limit, Location currLoc, int radius) {
        String strLocation = currLoc.latlong;
        String strLimit = String.valueOf(limit);
        String strRadius = String.valueOf(radius);
        JSObject obj = sendRequest("https://api.radar.io/v1/search/geofences", "GET", "near=" +
                location + "&radius=" + radius +
                "&limit=" + limit);
        if (!obj.getJsonObject("meta").getString("code").startsWith("2")) {
            throw IOException("Radar.io responded with Non-OK response code:", obj.getJsonObject("meta").getString("code"));
        }
        ArrayList temp = new ArrayList<>();
        for(JSObject geofenceInfo: obj.getJsonObject("geofences")) {
            ArrayList pair = new ArrayList<>();
            pair.add(geofenceInfo.getString("_id"));
            pair.add(geofenceInfo.getString("description"));
            temp.add(pair);
        }
        return temp;
    }

    public ArrayList<JSObject> listUsers(String updatedBefore, String updatedAfter) {
        JSObject obj = sendRequest("https://api.radar.io/v1/users", "GET", "limit=1000" +
                "&updatedBefore=" + updatedBefore + "&updatedAfter=" + updatedAfter);
        if (!obj.getJsonObject("meta").getString("code").startsWith("2")) {
            throw IOException("Radar.io responded with Non-OK response code:", obj.getJsonObject("meta").getString("code"));
        }

        return obj.getJsonObject("users");
    }

    public double getDistance(Location origin, Location destination) {
        JSObject obj = sendRequest("https://api.radar.io/v1/route/distance", "GET",
                "origin=" + origin + "&destination=" + destination.latlong + "&units=metric" + "&modes=foot");

        if (!obj.getJsonObject("meta").getString("code").startsWith("2")) {
            throw IOException("Radar.io responded with Non-OK response code:", obj.getJsonObject("meta").getString("code"));
        }
        double dist = Double.parseDouble(Inteobj.getJsonObject("foot").getJsonObject("distance").getString("value"));
        return dist;
    }
    

}

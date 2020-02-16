package com.example.pickupserv;

import org.json.*;

import java.io.*;
import java.net.*;
import java.util.ArrayList;

public class RadarIO {

    public static class Location {

        public String loc;
        public String latlong;

        Location(JSONObject obj) {
            loc = obj.get("coordinates").toString();
            String temp = loc.substring(1, loc.length() - 1);
            String[] arr = loc.split(",");
            latlong = arr[1] + "," + arr[0];
        }

    }

    private static String en(String str) throws UnsupportedEncodingException {
        return URLEncoder.encode(str, "UTF-8");
    }

    private static JSONObject sendRequest(String url, String type, String data) throws MalformedURLException, SocketTimeoutException, JSONException, IOException {
        URL uurl = new URL(url);
        HttpURLConnection conn = (HttpURLConnection) uurl.openConnection();
        conn.setRequestMethod(type);
        conn.setRequestProperty("Authorization", en("prj_live_sk_dce415dc7a097e9914b98237ae076deb0d689450"));
        conn.setRequestProperty("User-Agent", "curl/7.58.0");
        if (type.equals("POST")) {
            data = en(data);
            conn.setRequestProperty("Content-Length", "" + data.getBytes().length);
            conn.setUseCaches(false);
            conn.setDoInput(true);
            conn.setDoOutput(true);
            conn.getOutputStream().write(data.getBytes());
        }
        String resp;
        try {
            System.out.println("Response: " + conn.getResponseCode());
            StringBuilder sb = new StringBuilder();
            InputStreamReader is = new InputStreamReader(conn.getInputStream());
            char[] buf = new char[8192];
            int count;
            while ((count = is.read(buf)) != -1) {
                sb.append(buf, 0, count);
            }
            System.out.println(sb.toString());
            resp = sb.toString();
        } catch (Throwable e) {
            e.printStackTrace();
            throw e;
        } finally {
            conn.getInputStream().close();
        }
        JSONObject toreturn = new JSONObject(new JSONTokener(resp));
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
     * @throws JSONException
     * @throws IOException
     */
    public static String createGeofence(String desc, Location loc, double radius) throws MalformedURLException,
            SocketTimeoutException, JSONException, IOException {
        JSONObject obj = sendRequest("https://api.radar.io/v1/geofences", "POST",
            "description=" + desc
         + "&type=circle"
         + "&coordinates=" + loc.loc
         + "&radius=" + radius);
        String code = obj.getJSONObject("meta").get("code").toString();
        if (!code.startsWith("2")) {
            throw new IOException("Radar.io responded with Non-OK response code " + code);
        }
        return obj.getJSONObject("geofence").get("_id").toString();
    }

    /**
     * Delete an existing geofence.
     * @param id
     * @param loc
     * @param radius
     * @throws MalformedURLException
     * @throws SocketTimeoutException
     * @throws JSONException
     * @throws IOException
     */
    public static void deleteGeofence(String id) throws MalformedURLException,
            SocketTimeoutException, JSONException, IOException {
        JSONObject obj = sendRequest("https://api.radar.io/v1/geofences/" + en(id), "DELETE", "");
        String code = obj.getJSONObject("meta").get("code").toString();
        if (!code.startsWith("2")) {
            throw new IOException("Radar.io responded with Non-OK response code " + code);
        }
    }

    public static JSONObject getUser(String deviceId) throws MalformedURLException,
            SocketTimeoutException, JSONException, IOException {
        JSONObject obj = sendRequest("https://api.radar.io/v1/users/" + en(deviceId), "GET", "");
        String code = obj.getJSONObject("meta").get("code").toString();
        if (!code.startsWith("2")) {
            throw new IOException("Radar.io responded with Non-OK response code " + code);
        }
        return obj;

    }

    public static ArrayList<String> searchGeofences(int limit, Location currLoc, int radius) throws Throwable {
        JSONObject obj = sendRequest("https://api.radar.io/v1/search/geofences?near=" +
                  en(currLoc.latlong) + "&radius=" + radius +
                  "&limit=" + limit, "GET", "");
        String code = obj.getJSONObject("meta").get("code").toString();
        if (!code.startsWith("2")) {
            throw new IOException("Radar.io responded with Non-OK response code " + code);
        }
        ArrayList<String> temp = new ArrayList<String>();
        for(Object geofenceInfo : obj.getJSONArray("geofences")) {
            temp.add(((JSONObject)geofenceInfo).get("_id").toString());
        }
        return temp;
    }

    public static JSONArray listUsers(String updatedAfter) throws Throwable {
        JSONObject obj = sendRequest("https://api.radar.io/v1/users?limit=1000", "GET", "");
        String code = obj.getJSONObject("meta").get("code").toString();
        if (!code.startsWith("2")) {
            throw new IOException("Radar.io responded with Non-OK response code " + code);
        }
        return obj.getJSONArray("users");
    }

    public static double getDistance(Location origin, Location destination) throws Throwable {
        JSONObject obj = sendRequest("https://api.radar.io/v1/route/distance?origin=" + en(origin.latlong)
                               + "&destination=" + en(destination.latlong) + "&units=metric" + "&modes=foot", "GET", "");
        String code = obj.getJSONObject("meta").get("code").toString();
        if (!code.startsWith("2")) {
            throw new IOException("Radar.io responded with Non-OK response code " + code);
        }
        return Double.parseDouble(obj.getJSONObject("foot").getJSONObject("distance").get("value").toString());
    }

}

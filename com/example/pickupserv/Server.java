package com.example.pickupserv;

import java.io.*;
import java.util.*;
import java.net.ServerSocket;
import java.net.Socket;

public class Server implements Runnable {

    public static listen() throws SecurityException, SocketTimeoutException, IllegalBlockingModeException, IOException {
        ServerSocket serv = new ServerSocket(12345);
        while (!serv.isClosed()) {
            (new Thread(new Server(serv.accept()))).run();
        }
    }

    private Socket client;

    private static class Attendance {

        public String host;
        public int currSize;
        public String[] members;

        Attendance(String host, int capacity) {
            this.host = host;
            members = new String[capacity];
            currSize = 0;
        }

    }

    private static HashMap<String, Attendance> events;
    private static HashMap<String, String> userToEvent;

    private static long lastRefresh;
    private static HashMap<String, double> activeUsers;
    private static HashMap<String, RadarIO.Location> users;

    Server(Socket client) {
        this.client = client;
    }

    private static boolean isDig(int b) {
        return ('0' <= b) && (b <= '9');
    }

    private final long POLL_DELAY = 1000;

    private void refreshUserInfo() {
        long currTime = System.currentTimeMillis();
        if (currTime - lastRefresh > POLL_DELAY) {
            lastRefresh = currTime;
            //TODO:FINISH;
        }
    }

    @Override
    public void run() {
        // Requests: create_geofence, destroy_geofence, update_participant_info, search_events, join_event
        try {
            BufferedReader is = new BufferedReader(client.getInputStream());
            OutputStream os = client.getOutputStream();
            String devId = is.readLine();
            if (devId == null) {
                os.write("Did not expect EOF".getBytes());
                return;
            }
            if (!users.contains(devId)) {
                try {
                    Thread.sleep(POLL_DELAY + 1);
                } catch (InteruptedException e) {
                    e.printStackTrace();
                }
                refreshUserInfo();
            }
            if (!users.contains(devId)) {
                os.write("User not found".getBytes());
                return;
            }
            int b = is.read();
            switch (b) {
            case 'c':
                createGeofence(devId, is, os);
                break;
            case 'd':
                if (!hostToEvent.contains(devId)) {
                    os.write("Error: Host is not hosting an event");
                }
                String geoid = hostToEvent.get(devId);
                RadarIO.deleteGeofence(geoid);
                hostToEvent.remove(devId);
                Attendance att = events.get(geoid);
                for (int i = 0; i < att.currSize; i++) {
                    userToEvent.remove(att.members[i]);
                }
                events.remove(geoid);
                activeUsers.remove(devId);
                os.write(new byte[1] {255});
                break;
            case 'u':
                updateParticipantInfo(devId, is, os);
                break;
            case 's':
                searchEvents(devId, is, os);
                break;
            case 'j':
                joinEvent(devId, is, os);
                break;
            default:
                os.write(("Unsupported request" + (char)b).getBytes());
            }
        } catch (Throwable e) {
            try {
                OutputStream os = client.getOutputStream();
                e.printStackTrace(new PrintWriter(os));
            } catch (Throwable e) {
                e.printStackTrace();
            }
        } finally {
            client.close();
        }
    }

    private void createGeofence(String devId, BufferedReader is, OutputStream os) throws Throwable {
        if (hostToEvent.contains(devId)) {
            os.write("Error: Host is already hosting an event".getBytes());
            return;
        }
        if (userToEvent.contains(devId)) {
            os.write("Error: Host is already participating in an event".getBytes());
            return;
        }
        String radiuss = is.readLine();
        int radius;
        try {
            radius = Integer.parseInt(radiuss);
        } catch (NumberFormatException e) {
            os.write("Radius not an integer".getBytes());
            return;
        }
        if (radius < 50 || radius > 500) {
            os.write("Radius out of range".getBytes());
            return;
        }
        String desc = is.readLine();
        if (desc == null) {
            os.write("Did not expect EOF".getBytes());
            return;
        }
        String cap = is.readLine();
        if (cap == null) {
            os.write("Did not expect EOF".getBytes());
            return;
        }
        int capacity;
        try {
            capacity = Integer.parseInt(capacity);
        } catch (NumberFormatException e) {
            os.write("Capacity not an integer".getBytes());
            return;
        }
        if (capacity < 2) {
            os.write("Capacity out of range".getBytes());
            return;
        }
        String geoid = RadarIO.createGeofence(desc, loc, radius);
        events.put(geoid, new Attendance(devId, capacity));
        hostToEvent.put(devId, geoid);
        activeUsers.put(devId, -1);
        os.write(new byte[1] {255});
    }

    private void updateParticipantInfo(String devId, BufferedReader is, OutputStream os) throws Throwable {
        if (!hostToEvent.contains(devId) && !userToEvent.contains(devId)) {
            os.write("Error: User is not in an event");
        }
        String geoid = hostToEvent.contains(devId) ? hostToEvent.get(devId) : userToEvent.get(devId);
    }

    private void searchEvents(String devId, BufferedReader is, OutputStream os) throws Throwable {
        // TODO: WRITE
    }

    private void joinEvent(String devId, BufferedReader is, OutputStream os) throws Throwable {
        // TODO: WRITE
    }

}

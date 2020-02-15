package com.example.test;

import com.example.pickupserv.*;

public class TestServ {

    public static void main(String[] args) throws Throwable {
        Server.runInBackground();
        Socket client = new Socket("localhost", 12345);
        OutputStreamWriter os = new OutputStreamWriter(client.getOutputStream());
        
    }

}

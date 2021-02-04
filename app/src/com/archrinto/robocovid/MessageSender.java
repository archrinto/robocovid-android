package com.archrinto.robocovid;

import android.os.AsyncTask;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;

public class MessageSender extends AsyncTask<String, Void, Void> {

    Socket s;
    DataOutputStream dos;
    PrintWriter pw;
    String ip = "192.168.1.25";
    Integer port = 54321;

    MessageSender(String ip) {
        this.ip = ip;
    }

    @Override
    protected Void doInBackground(String... voids) {
        String msg = voids[0];

        try {

            s = new Socket(ip, port);
            pw = new PrintWriter(s.getOutputStream());
            pw.write(msg);
            pw.close();
            s.close();

        } catch (IOException e) {
            e.printStackTrace();
        }

        return null;
    }
}

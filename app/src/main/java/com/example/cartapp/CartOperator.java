package com.example.cartapp;

import android.os.Bundle;
import android.os.Handler;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.graphics.Color;
import android.view.Gravity;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;

public class CartOperator extends AppCompatActivity {

    private static final int PORT = 5010;
    private LinearLayout viewLayout;
    private Handler mainHandler = new Handler();
    private ServerSocket serverSocket;
    private Thread serverThread;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cart_operator);

        viewLayout = findViewById(R.id.viewLayout);

        startServer();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        closeServer();
    }

    @Override
    protected void onPause() {
        super.onPause();
        closeServer();
    }

    private void closeServer() {
        try {
            if (serverThread != null) {
                serverThread.interrupt();
            }

            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void startServer() {
        serverThread = new Thread(() -> {
            try {
                serverSocket = new ServerSocket();
                serverSocket.setReuseAddress(true);
                serverSocket.bind(new InetSocketAddress(PORT));

                runOnUiThread(() ->
                        Toast.makeText(this, "Server started on port " + PORT, Toast.LENGTH_SHORT).show()
                );

                while (!Thread.currentThread().isInterrupted()) {
                    Socket client = serverSocket.accept();
                    BufferedReader reader = new BufferedReader(new InputStreamReader(client.getInputStream()));
                    String line;

                    while ((line = reader.readLine()) != null) {
                        String finalLine = line;
                        mainHandler.post(() -> displayRequest(finalLine));
                    }

                    client.close();
                }

            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() ->
                        Toast.makeText(this, "Server error: " + e.getMessage(), Toast.LENGTH_LONG).show()
                );
            }
        });

        serverThread.start();
    }

    private void displayRequest(String message) {
        String[] parts = message.split(",");
        if (parts.length < 3) return;

        String lineNum = parts[0];
        String item = parts[1];
        String qty = parts[2];

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(8, 8, 8, 8);
        row.setGravity(Gravity.CENTER_VERTICAL);

        TextView lineView = createBox("LINE " + lineNum);
        TextView qtyView = createBox(qty);
        TextView itemView = createBox(item);

        row.addView(lineView);
        row.addView(qtyView);
        row.addView(itemView);

        viewLayout.addView(row);
    }

    private TextView createBox(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(18f);
        tv.setTextColor(Color.BLACK);
        tv.setBackgroundColor(Color.LTGRAY);
        tv.setPadding(24, 16, 24, 16);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        lp.setMargins(16, 8, 16, 8);
        tv.setLayoutParams(lp);

        return tv;
    }
}
package com.example.cartapp;

import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.os.StrictMode;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.Socket;

public class CartPicker extends AppCompatActivity {

    EditText lineInput, itemInput, qtyInput;
    Button sendBtn;

    private static final String OPERATOR_IP = "192.168.1.57";
    private static final int PORT = 5010;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_cart_picker);
        this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);

        StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder().permitNetwork().build());

        lineInput = findViewById(R.id.lineInput);
        itemInput = findViewById(R.id.itemInput);
        qtyInput = findViewById(R.id.qtyInput);
        sendBtn = findViewById(R.id.sendBtn);

        sendBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String line = lineInput.getText().toString().trim();
                String item = itemInput.getText().toString().trim();
                String qty = qtyInput.getText().toString().trim();

                if (line.isEmpty() || item.isEmpty() || qty.isEmpty()) {
                    Toast.makeText(CartPicker.this, "Please fill all fields", Toast.LENGTH_SHORT).show();
                    return;
                }

                String message = line + "," + item + "," + qty;
                sendMessage(message);
            }
        });
    }

    private void sendMessage(String message) {
        try {
            Socket socket = new Socket(OPERATOR_IP, PORT);
            OutputStream output = socket.getOutputStream();
            PrintWriter writer = new PrintWriter(output, true);
            writer.println(message);
            socket.close();

            Toast.makeText(this, "Request sent", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Failed to send: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}

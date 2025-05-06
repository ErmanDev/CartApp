package com.example.cartapp;

import android.os.Bundle;
import android.os.Handler;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.widget.Toast;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.content.Context;
import android.content.pm.ActivityInfo;

import androidx.appcompat.app.AppCompatActivity;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CartOperator extends AppCompatActivity {

    private static final int PORT = 5010;
    private static final String SERVICE_NAME = "CartOperator";
    private static final String SERVICE_TYPE = "_cartoperator._tcp.";
    
    private LinearLayout viewLayout;
    private Handler mainHandler = new Handler();
    private ServerSocket serverSocket;
    private Thread serverThread;
    private ToneGenerator toneGenerator;
    private NsdManager nsdManager;
    private NsdManager.RegistrationListener registrationListener;

    // Track row views and their statuses
    private Map<LinearLayout, TextView> rowStatusMap = new HashMap<>();
    private Map<LinearLayout, String> rowLineNumMap = new HashMap<>();
    private Map<LinearLayout, String> rowItemMap = new HashMap<>();
    private Map<LinearLayout, String> rowQtyMap = new HashMap<>();
    private LinearLayout selectedRow = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        setContentView(R.layout.activity_cart_operator);

        viewLayout = findViewById(R.id.viewLayout);
        toneGenerator = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100);
        

        nsdManager = (NsdManager) getSystemService(Context.NSD_SERVICE);
        initializeRegistrationListener();

        startServer();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        closeServer();
        if (toneGenerator != null) {
            toneGenerator.release();
        }
        unregisterService();
    }

    @Override
    protected void onPause() {
        super.onPause();
        closeServer();
        unregisterService();
    }
    
    private void initializeRegistrationListener() {
        registrationListener = new NsdManager.RegistrationListener() {
            @Override
            public void onRegistrationFailed(NsdServiceInfo serviceInfo, int errorCode) {
                runOnUiThread(() -> 
                    Toast.makeText(CartOperator.this, 
                        "Service registration failed: " + errorCode, Toast.LENGTH_SHORT).show()
                );
            }

            @Override
            public void onUnregistrationFailed(NsdServiceInfo serviceInfo, int errorCode) {

            }

            @Override
            public void onServiceRegistered(NsdServiceInfo serviceInfo) {
                runOnUiThread(() -> 
                    Toast.makeText(CartOperator.this, 
                        "Service registered: " + serviceInfo.getServiceName(), Toast.LENGTH_SHORT).show()
                );
            }

            @Override
            public void onServiceUnregistered(NsdServiceInfo serviceInfo) {

            }
        };
    }
    
    private void registerService() {
        NsdServiceInfo serviceInfo = new NsdServiceInfo();
        serviceInfo.setServiceName(SERVICE_NAME);
        serviceInfo.setServiceType(SERVICE_TYPE);
        serviceInfo.setPort(PORT);
        
        nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener);
    }
    
    private void unregisterService() {
        try {
            nsdManager.unregisterService(registrationListener);
        } catch (Exception e) {
            e.printStackTrace();
        }
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

                runOnUiThread(() -> {
                    Toast.makeText(this, "Server started on port " + PORT, Toast.LENGTH_SHORT).show();

                    registerService();
                });

                while (!Thread.currentThread().isInterrupted()) {
                    Socket client = serverSocket.accept();
                    BufferedReader reader = new BufferedReader(new InputStreamReader(client.getInputStream()));
                    String line;

                    while ((line = reader.readLine()) != null) {
                        String finalLine = line;
                        

                        if (finalLine.equals("STATUS_REQUEST")) {

                            sendStatusUpdates(client);
                        } else {

                            mainHandler.post(() -> {
                                displayRequest(finalLine);

                                toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, 150);
                            });
                        }
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
    
    private void sendStatusUpdates(Socket client) {
        try {

            StringBuilder statusUpdates = new StringBuilder();
            
            for (LinearLayout row : rowStatusMap.keySet()) {
                TextView statusView = rowStatusMap.get(row);
                String lineNum = rowLineNumMap.get(row);
                String item = rowItemMap.get(row);
                String qty = rowQtyMap.get(row);
                String status = statusView.getText().toString();
                

                statusUpdates.append(lineNum).append(",")
                             .append(item).append(",")
                             .append(qty).append(",")
                             .append(status).append("|");
            }
            

            PrintWriter writer = new PrintWriter(client.getOutputStream(), true);
            writer.println(statusUpdates.toString());
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void displayRequest(String message) {
        String[] parts = message.split(",");
        if (parts.length < 3) return;

        String lineNum = parts[0].trim();
        String item = parts[1].trim();
        String qty = parts[2].trim();

        // Format the line number to match the picker format (LINE X)
        if (!lineNum.startsWith("LINE ")) {
            lineNum = "LINE " + lineNum;
        }

        // Optional: Status field (PENDING, MOVING, DONE, etc.)
        String status = parts.length > 3 ? parts[3].trim() : "PENDING"; // Default to PENDING if not provided

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(8, 12, 8, 12); // Increased vertical padding
        row.setGravity(Gravity.CENTER_VERTICAL);

        // Set background for the row with rounded corners
        row.setBackgroundResource(R.drawable.row_background);
        int margin = 8;
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        rowParams.setMargins(margin, margin, margin, margin);
        row.setLayoutParams(rowParams);

        TextView lineNumView = createBox(lineNum, Color.BLACK);
        TextView qtyView = createBox(qty, Color.BLACK);
        TextView itemView = createColoredItemBox(item); // Updated to handle item colors
        TextView statusView = createStatusBox(status);

        row.addView(lineNumView);
        row.addView(qtyView);
        row.addView(itemView);
        row.addView(statusView);

        // Store the status view reference and line number for this row
        rowStatusMap.put(row, statusView);
        rowLineNumMap.put(row, lineNum);
        rowItemMap.put(row, item);
        rowQtyMap.put(row, qty);

        // Add click listener for row selection
        row.setOnClickListener(v -> handleRowSelection(row));

        // Add new items at the top for better visibility
        viewLayout.addView(row, 0);
        
        // If status is DONE, remove after 3 seconds
        if (status.equals("DONE")) {
            mainHandler.postDelayed(() -> {
                viewLayout.removeView(row);
                rowStatusMap.remove(row);
                rowLineNumMap.remove(row);
                rowItemMap.remove(row);
                rowQtyMap.remove(row);
            }, 3000);
        }
    }

    private void handleRowSelection(LinearLayout row) {
        // Get status view for this row
        TextView statusView = rowStatusMap.get(row);
        if (statusView == null) return;

        String currentStatus = statusView.getText().toString();
        String lineNum = rowLineNumMap.get(row); // Get the line number for this row

        // Toggle between statuses
        if (currentStatus.equals("PENDING")) {
            // Change to MOVING
            statusView.setText("MOVING");
            statusView.setTextColor(Color.BLUE);
            statusView.setBackgroundColor(Color.WHITE);
            row.setBackgroundResource(R.drawable.row_background);
            Toast.makeText(this, lineNum + " status: MOVING", Toast.LENGTH_SHORT).show();
        } else if (currentStatus.equals("MOVING")) {
            // Change to DONE
            statusView.setText("DONE");
            statusView.setTextColor(Color.GREEN);
            statusView.setBackgroundColor(Color.WHITE);
            row.setBackgroundResource(R.drawable.row_background);
            
            // Remove the row after 3 seconds
            mainHandler.postDelayed(() -> {
                viewLayout.removeView(row);
                rowStatusMap.remove(row);
                rowLineNumMap.remove(row);
                rowItemMap.remove(row);
                rowQtyMap.remove(row);
            }, 3000);
        }
    }

    private TextView createBox(String text, int textColor) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(16f);
        tv.setTypeface(null, android.graphics.Typeface.BOLD);
        tv.setTextColor(textColor);
        tv.setBackgroundColor(Color.TRANSPARENT);
        tv.setPadding(24, 16, 24, 16);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1
        );
        lp.setMargins(4, 4, 4, 4);
        tv.setLayoutParams(lp);
        tv.setGravity(Gravity.CENTER);

        return tv;
    }

    private TextView createColoredItemBox(String itemText) {
        TextView tv = new TextView(this);
        SpannableStringBuilder builder = new SpannableStringBuilder();

        Pattern pattern = Pattern.compile("(.*?)(\\((BLUE|RED)\\))?", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(itemText);

        if (matcher.matches()) {
            String mainText = matcher.group(1);
            String colorTag = matcher.group(3);

            builder.append(mainText);

            if (colorTag != null) {
                int color = colorTag.equalsIgnoreCase("BLUE") ? Color.BLUE : Color.RED;
                SpannableString coloredPart = new SpannableString(" (" + colorTag + ")");
                coloredPart.setSpan(new ForegroundColorSpan(color), 0, coloredPart.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                builder.append(coloredPart);
            }
        } else {
            builder.append(itemText);
        }

        tv.setText(builder);
        tv.setTextSize(16f);
        tv.setTypeface(null, android.graphics.Typeface.BOLD);
        tv.setTextColor(Color.BLACK);
        tv.setBackgroundColor(Color.TRANSPARENT);
        tv.setPadding(24, 16, 24, 16);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 2
        );
        lp.setMargins(4, 4, 4, 4);
        tv.setLayoutParams(lp);
        tv.setGravity(Gravity.CENTER);

        return tv;
    }

    private TextView createStatusBox(String status) {
        TextView tv = new TextView(this);
        tv.setText(status);
        tv.setTextSize(16f); // Increased size
        tv.setTypeface(null, android.graphics.Typeface.BOLD);
        
        // Set color based on status
        if (status.equalsIgnoreCase("MOVING")) {
            tv.setTextColor(Color.BLUE);
            tv.setBackgroundColor(Color.WHITE);
        } else if (status.equalsIgnoreCase("PENDING")) {
            tv.setTextColor(Color.YELLOW);
            tv.setBackgroundColor(Color.BLACK);
        } else if (status.equalsIgnoreCase("DONE")) {
            tv.setTextColor(Color.GREEN);
            tv.setBackgroundColor(Color.WHITE);
        } else {
            tv.setTextColor(Color.YELLOW); // Default color
            tv.setBackgroundColor(Color.BLACK); // Default background
        }
        
        tv.setPadding(24, 16, 24, 16);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        lp.setMargins(4, 4, 4, 4);
        tv.setLayoutParams(lp);
        tv.setGravity(Gravity.CENTER);

        return tv;
    }
}
package com.example.cartapp;

import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.StrictMode;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import android.content.SharedPreferences;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.net.wifi.WifiManager;
import android.text.format.Formatter;
import android.app.AlertDialog;
import android.view.inputmethod.EditorInfo;
import android.text.InputType;
import android.content.Context;
import android.media.AudioManager;
import android.media.MediaPlayer;

public class CartPicker extends AppCompatActivity {

    private Button line1Btn, line2Btn, line3Btn, line4Btn, line5Btn;
    private String selectedLine = "";
    private Button sendBtn, decreaseBtn, increaseBtn;
    private Button btnCard, btnTape, btnCrayon, btnBlade, btnRubberBond;
    private TextView lineTextView, qtyDisplay;
    private LinearLayout cardsLayout, utilityLayout;
    private String selectedItem = "";
    private String selectedColor = ""; // Added for color selection

    private static final String OPERATOR_IP_PREF = "operator_ip";
    private static final String OPERATOR_SERVICE_TYPE = "_cartoperator._tcp.";
    private String operatorIp;
    private static final int PORT = 5010;
    private static final int STATUS_FETCH_INTERVAL = 3000; // 3 seconds

    private List<RequestItem> pendingRequests = new ArrayList<>();
    private Handler handler = new Handler(Looper.getMainLooper());
    private int currentQuantity = 1;
    private boolean isStatusFetchingActive = false;
    private Thread statusFetchThread;
    private NsdManager nsdManager;
    private NsdManager.DiscoveryListener discoveryListener;
    private NsdManager.ResolveListener resolveListener;
    private boolean isResolving = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_cart_picker);
        this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);

        StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder().permitNetwork().build());

        // Initialize views
        line1Btn = findViewById(R.id.line1Btn);
        line2Btn = findViewById(R.id.line2Btn);
        line3Btn = findViewById(R.id.line3Btn);
        line4Btn = findViewById(R.id.line4Btn);
        line5Btn = findViewById(R.id.line5Btn);
        lineTextView = findViewById(R.id.lineTextview);
        sendBtn = findViewById(R.id.sendBtn);
        cardsLayout = findViewById(R.id.cardsLayout);
        utilityLayout = findViewById(R.id.utilityLayout);
        decreaseBtn = findViewById(R.id.decreaseBtn);
        increaseBtn = findViewById(R.id.increaseBtn);
        qtyDisplay = findViewById(R.id.qtyDisplay);
        
        // Initialize item buttons
        btnCard = findViewById(R.id.btnCard);
        btnTape = findViewById(R.id.btnTape);
        btnCrayon = findViewById(R.id.btnCrayon);
        btnBlade = findViewById(R.id.btnBlade);
        btnRubberBond = findViewById(R.id.btnRubberBond);

        // Initialize NSD manager
        nsdManager = (NsdManager) getSystemService(Context.NSD_SERVICE);
        initializeDiscoveryListener();
        initializeResolveListener();

        // Load saved IP or start discovery
        loadOperatorIp();

        // Set up line button click listeners
        View.OnClickListener lineButtonListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                resetLineButtonBackgrounds();
                v.setBackgroundColor(Color.BLACK);
                ((Button) v).setTextColor(Color.WHITE);
                selectedLine = ((Button) v).getText().toString();
                lineTextView.setText(selectedLine);
            }
        };

        line1Btn.setOnClickListener(lineButtonListener);
        line2Btn.setOnClickListener(lineButtonListener);
        line3Btn.setOnClickListener(lineButtonListener);
        line4Btn.setOnClickListener(lineButtonListener);
        line5Btn.setOnClickListener(lineButtonListener);

        // Set default line
        line1Btn.performClick();

        // Set up quantity controls
        decreaseBtn.setOnClickListener(v -> {
            if (currentQuantity > 1) {
                currentQuantity--;
                qtyDisplay.setText(String.valueOf(currentQuantity));
            }
        });

        increaseBtn.setOnClickListener(v -> {
            currentQuantity++;
            qtyDisplay.setText(String.valueOf(currentQuantity));
        });

        // Item button click listener
        View.OnClickListener itemButtonListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                resetItemButtonBackgrounds();
                v.setBackgroundColor(Color.BLACK);
                ((Button) v).setTextColor(Color.WHITE);

                String itemName = ((Button) v).getText().toString();
                if (itemName.equalsIgnoreCase("Card")) {
                    showColorSelectionDialog(itemName);
                } else {
                    selectedItem = itemName;
                    selectedColor = ""; // Clear color for non-card items
                    Toast.makeText(CartPicker.this, "Selected: " + selectedItem, Toast.LENGTH_SHORT).show();
                }
            }
        };

        btnCard.setOnClickListener(itemButtonListener);
        btnTape.setOnClickListener(itemButtonListener);
        btnCrayon.setOnClickListener(itemButtonListener);
        btnBlade.setOnClickListener(itemButtonListener);
        btnRubberBond.setOnClickListener(itemButtonListener);

        // Handle send button click
        sendBtn.setOnClickListener(v -> {
            String line = selectedLine;
            String qty = String.valueOf(currentQuantity);

            if (line.isEmpty()) {
                Toast.makeText(CartPicker.this, "Please select a valid line (Line 1-5)", Toast.LENGTH_SHORT).show();
                return;
            }

            if (selectedItem.isEmpty()) {
                Toast.makeText(CartPicker.this, "Please select an item", Toast.LENGTH_SHORT).show();
                return;
            }

            String message = line + "," + selectedItem + "," + qty;
            boolean sent = sendMessage(message);

            if (sent) {
                RequestItem request = new RequestItem(line, selectedItem, qty, new Date(), selectedColor);
                pendingRequests.add(request);
                addRequestCard(request);
                
                simulateRequestProcessing(request);

                // Reset selections
                selectedItem = "";
                selectedColor = "";
                resetItemButtonBackgrounds();
            }
        });
        
        startStatusFetching();
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopStatusFetching();
        stopDiscovery();
    }
    
    private void loadOperatorIp() {
        SharedPreferences prefs = getSharedPreferences("CartAppPrefs", MODE_PRIVATE);
        operatorIp = prefs.getString(OPERATOR_IP_PREF, null);
        
        if (operatorIp != null) {
            verifyOperatorConnection();
        } else {
            startDiscovery();
        }
    }
    
    private void verifyOperatorConnection() {
        new Thread(() -> {
            try {
                Socket socket = new Socket(operatorIp, PORT);
                socket.close();
                runOnUiThread(() -> Toast.makeText(CartPicker.this, 
                    "Connected to operator at " + operatorIp, Toast.LENGTH_SHORT).show());
            } catch (Exception e) {
                runOnUiThread(() -> {
                    Toast.makeText(CartPicker.this, 
                        "Could not connect to operator, starting discovery", Toast.LENGTH_SHORT).show();
                    startDiscovery();
                });
            }
        }).start();
    }
    
    private void initializeDiscoveryListener() {
        discoveryListener = new NsdManager.DiscoveryListener() {
            @Override
            public void onStartDiscoveryFailed(String serviceType, int errorCode) {
                runOnUiThread(() -> {
                    Toast.makeText(CartPicker.this, 
                        "Discovery failed to start: " + errorCode, Toast.LENGTH_SHORT).show();
                    showManualIpDialog();
                });
            }

            @Override
            public void onStopDiscoveryFailed(String serviceType, int errorCode) {}

            @Override
            public void onDiscoveryStarted(String serviceType) {
                runOnUiThread(() -> Toast.makeText(CartPicker.this, 
                    "Searching for operator...", Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onDiscoveryStopped(String serviceType) {}

            @Override
            public void onServiceFound(NsdServiceInfo service) {
                if (!service.getServiceType().equals(OPERATOR_SERVICE_TYPE)) {
                    return;
                }
                
                if (!isResolving) {
                    isResolving = true;
                    nsdManager.resolveService(service, resolveListener);
                }
            }

            @Override
            public void onServiceLost(NsdServiceInfo service) {}
        };
    }
    
    private void initializeResolveListener() {
        resolveListener = new NsdManager.ResolveListener() {
            @Override
            public void onResolveFailed(NsdServiceInfo serviceInfo, int errorCode) {
                runOnUiThread(() -> {
                    Toast.makeText(CartPicker.this, 
                        "Failed to resolve service: " + errorCode, Toast.LENGTH_SHORT).show();
                    showManualIpDialog();
                });
                isResolving = false;
            }

            @Override
            public void onServiceResolved(NsdServiceInfo serviceInfo) {
                operatorIp = serviceInfo.getHost().getHostAddress();
                
                SharedPreferences.Editor editor = getSharedPreferences("CartAppPrefs", MODE_PRIVATE).edit();
                editor.putString(OPERATOR_IP_PREF, operatorIp);
                editor.apply();
                
                runOnUiThread(() -> {
                    Toast.makeText(CartPicker.this, 
                        "Found operator at " + operatorIp, Toast.LENGTH_SHORT).show();
                    stopDiscovery();
                });
                
                isResolving = false;
            }
        };
    }
    
    private void startDiscovery() {
        try {
            nsdManager.discoverServices(OPERATOR_SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener);
        } catch (Exception e) {
            e.printStackTrace();
            showManualIpDialog();
        }
    }
    
    private void stopDiscovery() {
        try {
            nsdManager.stopServiceDiscovery(discoveryListener);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private void showManualIpDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Enter Operator IP");
        builder.setMessage("Could not automatically find the operator. Please enter the IP address manually.");
        
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setHint("e.g. 192.168.1.100");
        builder.setView(input);
        
        builder.setPositiveButton("Connect", (dialog, which) -> {
            String ip = input.getText().toString().trim();
            if (!ip.isEmpty()) {
                operatorIp = ip;
                
                SharedPreferences.Editor editor = getSharedPreferences("CartAppPrefs", MODE_PRIVATE).edit();
                editor.putString(OPERATOR_IP_PREF, operatorIp);
                editor.apply();
                
                Toast.makeText(CartPicker.this, 
                    "Connecting to " + operatorIp, Toast.LENGTH_SHORT).show();
            }
        });
        
        builder.setNegativeButton("Cancel", (dialog, which) -> {
            dialog.cancel();
            handler.postDelayed(() -> showManualIpDialog(), 5000);
        });
        
        builder.show();
    }
    
    private void startStatusFetching() {
        isStatusFetchingActive = true;
        statusFetchThread = new Thread(() -> {
            while (isStatusFetchingActive) {
                if (operatorIp != null) {
                    fetchStatusUpdates();
                }
                try {
                    Thread.sleep(STATUS_FETCH_INTERVAL);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    break;
                }
            }
        });
        statusFetchThread.start();
    }
    
    private void stopStatusFetching() {
        isStatusFetchingActive = false;
        if (statusFetchThread != null) {
            statusFetchThread.interrupt();
            statusFetchThread = null;
        }
    }
    
    private void fetchStatusUpdates() {
        try {
            Socket socket = new Socket(operatorIp, PORT);
            OutputStream output = socket.getOutputStream();
            PrintWriter writer = new PrintWriter(output, true);
            
            writer.println("STATUS_REQUEST");
            
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            String response = reader.readLine();
            
            if (response != null && !response.isEmpty()) {
                processStatusUpdates(response);
            }
            
            socket.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private void processStatusUpdates(String response) {
        String[] updates = response.split("\\|");
        
        for (String update : updates) {
            String[] parts = update.split(",");
            if (parts.length >= 4) {
                String line = parts[0].trim();
                String item = parts[1].trim();
                String qty = parts[2].trim();
                String status = parts[3].trim();
                
                updatePendingRequestStatus(line, item, qty, status);
            }
        }
    }
    
    private void updatePendingRequestStatus(String line, String item, String qty, String status) {
        for (int i = 0; i < utilityLayout.getChildCount(); i++) {
            View child = utilityLayout.getChildAt(i);
            RequestItem request = (RequestItem) child.getTag();
            
            if (request != null && 
                request.getLine().equals(line) && 
                request.getItem().equals(item) && 
                request.getQuantity().equals(qty)) {
                
                final View cardView = child;
                final String newStatus = status;
                
                runOnUiThread(() -> {
                    TextView statusText = cardView.findViewById(R.id.statusText);
                    statusText.setText(newStatus);
                    
                    if (newStatus.equals("MOVING")) {
                        statusText.setTextColor(Color.BLUE);
                        statusText.setBackgroundColor(Color.WHITE);
                        cardView.setBackgroundResource(R.drawable.row_background_selected);
                    } else if (newStatus.equals("PENDING")) {
                        statusText.setTextColor(Color.YELLOW);
                        statusText.setBackgroundColor(Color.BLACK);
                        cardView.setBackgroundResource(R.drawable.row_background);
                    } else if (newStatus.equals("DONE")) {
                        statusText.setTextColor(Color.GREEN);
                        statusText.setBackgroundColor(Color.WHITE);
                        cardView.setBackgroundResource(R.drawable.row_background);
                        
                        handler.postDelayed(() -> utilityLayout.removeView(cardView), 3000);
                    }
                });
                break;
            }
        }
    }

    private boolean sendMessage(String message) {
        if (operatorIp == null) {
            Toast.makeText(this, "Operator IP not set. Please wait for connection.", Toast.LENGTH_SHORT).show();
            return false;
        }
        
        try {
            Socket socket = new Socket(operatorIp, PORT);
            OutputStream output = socket.getOutputStream();
            PrintWriter writer = new PrintWriter(output, true);
            writer.println(message);
            socket.close();

            Toast.makeText(this, "Request sent to operator", Toast.LENGTH_SHORT).show();
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Failed to send: " + e.getMessage(), Toast.LENGTH_LONG).show();
            return false;
        }
    }

    private void addRequestCard(RequestItem request) {
        LayoutInflater inflater = LayoutInflater.from(this);
        View cardView = inflater.inflate(R.layout.request_card_item, cardsLayout, false);

        TextView itemNameText = cardView.findViewById(R.id.itemNameText);
        TextView quantityText = cardView.findViewById(R.id.quantityText);
        TextView timestampText = cardView.findViewById(R.id.timestampText);
        TextView statusText = cardView.findViewById(R.id.statusText);

        itemNameText.setText(request.getItem());
        quantityText.setText("Qty: " + request.getQuantity());

        // Set color for card items
        if (request.getItem().contains("Card") && request.getColor() != null) {
            int color = Color.BLACK;
            switch (request.getColor()) {
                case "Red": color = Color.RED; break;
                case "Blue": color = Color.BLUE; break;
                case "Green": color = Color.GREEN; break;
                case "Yellow": color = Color.YELLOW; break;
            }
            itemNameText.setTextColor(color);
        }

        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
        timestampText.setText("Sent: " + sdf.format(request.getTimestamp()));
        
        statusText.setText("PENDING");
        statusText.setTextColor(Color.YELLOW);
        statusText.setBackgroundColor(Color.BLACK);

        cardView.setTag(request);
        cardsLayout.addView(cardView, 0);
    }

    private void moveRequestToPending(RequestItem request) {
        // Find and remove from request column
        for (int i = 0; i < cardsLayout.getChildCount(); i++) {
            View child = cardsLayout.getChildAt(i);
            if (child.getTag() == request) {
                cardsLayout.removeView(child);
                break;
            }
        }

        // Create new card in pending column
        LayoutInflater inflater = LayoutInflater.from(this);
        View cardView = inflater.inflate(R.layout.request_card_item, utilityLayout, false);

        TextView itemNameText = cardView.findViewById(R.id.itemNameText);
        TextView quantityText = cardView.findViewById(R.id.quantityText);
        TextView timestampText = cardView.findViewById(R.id.timestampText);
        TextView statusText = cardView.findViewById(R.id.statusText);

        itemNameText.setText(request.getItem());
        quantityText.setText("Qty: " + request.getQuantity());

        // Set color for card items
        if (request.getItem().contains("Card") && request.getColor() != null) {
            int color = Color.BLACK;
            switch (request.getColor()) {
                case "Red": color = Color.RED; break;
                case "Blue": color = Color.BLUE; break;
                case "Green": color = Color.GREEN; break;
                case "Yellow": color = Color.YELLOW; break;
            }
            itemNameText.setTextColor(color);
        }

        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
        timestampText.setText("Processing since: " + sdf.format(new Date()));
        
        statusText.setText("PENDING");
        statusText.setTextColor(Color.YELLOW);
        statusText.setBackgroundColor(Color.BLACK);

        cardView.setTag(request);
        utilityLayout.addView(cardView, 0);
        
        cardView.setOnClickListener(new View.OnClickListener() {
            private long lastClickTime = 0;
            
            @Override
            public void onClick(View v) {
                long clickTime = System.currentTimeMillis();
                if (clickTime - lastClickTime < 300) {
                    TextView statusText = cardView.findViewById(R.id.statusText);
                    statusText.setText("DONE");
                    statusText.setTextColor(Color.GREEN);
                    statusText.setBackgroundColor(Color.WHITE);
                    
                    String message = request.getLine() + "," + request.getItem() + "," + 
                                    request.getQuantity() + ",DONE";
                    sendMessage(message);
                    
                    handler.postDelayed(() -> utilityLayout.removeView(cardView), 3000);
                }
                lastClickTime = clickTime;
            }
        });
    }

    private void simulateRequestProcessing(final RequestItem request) {
        handler.postDelayed(() -> moveRequestToPending(request), 
            5000 + new Random().nextInt(5000));
    }

    private void resetItemButtonBackgrounds() {
        btnCard.setBackgroundColor(Color.WHITE);
        btnTape.setBackgroundColor(Color.WHITE);
        btnCrayon.setBackgroundColor(Color.WHITE);
        btnBlade.setBackgroundColor(Color.WHITE);
        btnRubberBond.setBackgroundColor(Color.WHITE);
        
        btnCard.setTextColor(Color.BLACK);
        btnTape.setTextColor(Color.BLACK);
        btnCrayon.setTextColor(Color.BLACK);
        btnBlade.setTextColor(Color.BLACK);
        btnRubberBond.setTextColor(Color.BLACK);
    }

    private void resetLineButtonBackgrounds() {
        line1Btn.setBackgroundColor(Color.WHITE);
        line2Btn.setBackgroundColor(Color.WHITE);
        line3Btn.setBackgroundColor(Color.WHITE);
        line4Btn.setBackgroundColor(Color.WHITE);
        line5Btn.setBackgroundColor(Color.WHITE);
        
        line1Btn.setTextColor(Color.BLACK);
        line2Btn.setTextColor(Color.BLACK);
        line3Btn.setTextColor(Color.BLACK);
        line4Btn.setTextColor(Color.BLACK);
        line5Btn.setTextColor(Color.BLACK);
    }

    private void showColorSelectionDialog(String itemName) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Select a Color for " + itemName);

        String[] colors = {"Red", "Blue", "Green", "Yellow"};

        builder.setItems(colors, (dialog, which) -> {
            selectedColor = colors[which];
            selectedItem = itemName + " (" + selectedColor + ")";
            Toast.makeText(CartPicker.this, "Selected: " + selectedItem, Toast.LENGTH_SHORT).show();
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> {
            selectedItem = "";
            selectedColor = "";
            resetItemButtonBackgrounds();
            dialog.dismiss();
        });

        builder.show();
    }

    private static class RequestItem {
        private String line;
        private String item;
        private String quantity;
        private Date timestamp;
        private String color;

        public RequestItem(String line, String item, String quantity, Date timestamp, String color) {
            this.line = line;
            this.item = item;
            this.quantity = quantity;
            this.timestamp = timestamp;
            this.color = color;
        }

        public String getLine() { return line; }
        public String getItem() { return item; }
        public String getQuantity() { return quantity; }
        public Date getTimestamp() { return timestamp; }
        public String getColor() { return color; }
    }
}
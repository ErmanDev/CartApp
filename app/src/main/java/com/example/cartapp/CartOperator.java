package com.example.cartapp;

import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.widget.Button;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

public class CartOperator extends AppCompatActivity {

    private Button buttonOn, buttonOff;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_cart_operator);

        this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);

        buttonOff = findViewById(R.id.ButtonOff);
        buttonOn = findViewById(R.id.ButtonOn);
        int onColor = ContextCompat.getColor(this, R.color.buttonOn);
        int offColor = ContextCompat.getColor(this, R.color.buttonOff);

        buttonOn.setBackgroundColor(onColor);
        buttonOff.setBackgroundColor(offColor);

    
    }


}

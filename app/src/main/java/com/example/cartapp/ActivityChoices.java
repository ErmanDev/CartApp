package com.example.cartapp;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

public class ActivityChoices extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_choices);

        Button cartOperatorButton = findViewById(R.id.cartOperator);
        Button pickerButton = findViewById(R.id.pickButton);


        cartOperatorButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(ActivityChoices.this, CartOperator.class);
                startActivity(intent);
            }
        });

        pickerButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent (ActivityChoices.this, CartPicker.class);
                startActivity(intent);
            }
        });
    }
}

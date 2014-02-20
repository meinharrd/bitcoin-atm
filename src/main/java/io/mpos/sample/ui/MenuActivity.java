package io.mpos.sample.ui;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import io.mpos.Mpos;
import io.mpos.sample.R;

/**
 * Display a sample menu for selecting the sample activity (e.g. charge sample).
 */
public class MenuActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_menu);

        Button button_charge = (Button) findViewById(R.id.button_charge_sample);
        Button button_refund = (Button) findViewById(R.id.button_refund_sample);

        button_charge.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // start activity
                Intent intent = new Intent(getApplicationContext(), ChargeActivity.class);
                startActivity(intent);
            }
        });

        button_refund.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // start activity
                Intent intent = new Intent(getApplicationContext(), RefundActivity.class);
                startActivity(intent);
            }
        });

        TextView tvVersion = (TextView) findViewById(R.id.about_tv_sdk_version);
        tvVersion.setText(Mpos.getVersion());

        ImageView imageView = (ImageView) findViewById(R.id.about_image_logo);
        imageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                onLogoOrUrlClicked();
            }
        });

        TextView textViewCopyrightAndUrl = (TextView) findViewById(R.id.about_tv_url);
        textViewCopyrightAndUrl.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                onLogoOrUrlClicked();
            }
        });
    }

    void onLogoOrUrlClicked() {
        Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse("http://www.payworksmobile.com/"));
        startActivity(i);
    }

}

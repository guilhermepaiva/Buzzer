package com.parse.buzzer;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;

/**
* Activity a tela de cadastro do usuário.
*/
public class SignUpOrLogInActivity extends Activity {

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_sign_up_or_log_in);

    // botão de login
    ((Button) findViewById(R.id.logInButton)).setOnClickListener(new OnClickListener() {
      public void onClick(View v) {
        // inicia uma intent pra activity de login
        startActivity(new Intent(SignUpOrLogInActivity.this, LoginActivity.class));
      }
    });

    // botão de signup
    ((Button) findViewById(R.id.signUpButton)).setOnClickListener(new OnClickListener() {
      public void onClick(View v) {
        // inicia uma intent para a activity de signup
        startActivity(new Intent(SignUpOrLogInActivity.this, SignUpActivity.class));
      }
    });
  }
}

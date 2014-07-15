package com.parse.buzzer;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import com.parse.ParseUser;

/**
 * activity que inicia uma intent tanto pra logged (MainActivity) ou logged out (SignUpOrLoginActivity).
*/
public class DispatchActivity extends Activity {

  public DispatchActivity() {
  }

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    // checa se existe info do current user
    if (ParseUser.getCurrentUser() != null) {
      // inicia uma intenet pra logged in activity
      startActivity(new Intent(this, MainActivity.class));
    } else {
      // inicia uma intenet pra logged out activity
      startActivity(new Intent(this, SignUpOrLogInActivity.class));
    }
  }

}
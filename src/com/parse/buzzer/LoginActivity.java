package com.parse.buzzer;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import com.parse.LogInCallback;
import com.parse.ParseException;
import com.parse.ParseUser;

/**
* activity que mostra a tela de login pra o usuário
*/
public class LoginActivity extends Activity {
  // referências UI
  private EditText usernameView;
  private EditText passwordView;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    setContentView(R.layout.activity_login);

    // seta o login form
    usernameView = (EditText) findViewById(R.id.username);
    passwordView = (EditText) findViewById(R.id.password);

    // seta o submit button
    findViewById(R.id.action_button).setOnClickListener(new View.OnClickListener() {
      public void onClick(View view) {
        // valida os dados do login
        boolean validationError = false;
        StringBuilder validationErrorMessage =
            new StringBuilder(getResources().getString(R.string.error_intro));
        if (isEmpty(usernameView)) {
          validationError = true;
          validationErrorMessage.append(getResources().getString(R.string.error_blank_username));
        }
        if (isEmpty(passwordView)) {
          if (validationError) {
            validationErrorMessage.append(getResources().getString(R.string.error_join));
          }
          validationError = true;
          validationErrorMessage.append(getResources().getString(R.string.error_blank_password));
        }
        validationErrorMessage.append(getResources().getString(R.string.error_end));

        // If there is a validation error, display the error
        if (validationError) {
          Toast.makeText(LoginActivity.this, validationErrorMessage.toString(), Toast.LENGTH_LONG)
              .show();
          return;
        }

        // seta o progress dialog
        final ProgressDialog dlg = new ProgressDialog(LoginActivity.this);
        dlg.setTitle("Por favor aguarde.");
        dlg.setMessage("Conectando. Por favor aguarde.");
        dlg.show();
        // cahma o método de login do Parse
        ParseUser.logInInBackground(usernameView.getText().toString(), passwordView.getText()
            .toString(), new LogInCallback() {

          @Override
          public void done(ParseUser user, ParseException e) {
            dlg.dismiss();
            if (e != null) {
              // exibe uma mensagem de erro
              Toast.makeText(LoginActivity.this, e.getMessage(), Toast.LENGTH_LONG).show();
            } else {
              // inicia uma intent para a dispatch activity
              Intent intent = new Intent(LoginActivity.this, DispatchActivity.class);
              intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
              startActivity(intent);
            }
          }
        });
      }
    });
  }

  /*método auxiliar para verificar se o campo está vazio*/
  private boolean isEmpty(EditText etText) {
    if (etText.getText().toString().trim().length() > 0) {
      return false;
    } else {
      return true;
    }
  }
}


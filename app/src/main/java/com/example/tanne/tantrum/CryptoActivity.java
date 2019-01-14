package com.example.tanne.tantrum;

import android.content.Intent;
import android.support.annotation.NonNull;
import android.support.design.widget.BottomNavigationView;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import java.io.UnsupportedEncodingException;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;

import javax.crypto.Cipher;

import static android.util.Base64.decode;
import static android.util.Base64.encodeToString;
import static java.nio.charset.StandardCharsets.UTF_8;

public class CryptoActivity extends AppCompatActivity implements BottomNavigationView.OnNavigationItemSelectedListener {

    private PrivateKey m_PrivateKey;
    private PublicKey m_PublicKey;

    private EditText m_InputText;
    private EditText m_ResultText1;
    private EditText m_ResultText2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_crypto);

        m_InputText = findViewById(R.id.inputText);
        m_ResultText1 = findViewById(R.id.resultText1);
        m_ResultText2 = findViewById(R.id.resultText2);

        BottomNavigationView navigation = findViewById(R.id.navigation);
        navigation.setOnNavigationItemSelectedListener(this);
        Menu menu = navigation.getMenu();
        MenuItem menuItem = menu.getItem(2);
        menuItem.setChecked(true);
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
            case R.id.navigation_home:
                Intent intent = new Intent(this, MainActivity.class);
                startActivity(intent);
                return true;
            case R.id.navigation_binding:
                Intent intent2 = new Intent(this, BindingActivity.class);
                startActivity(intent2);
                return true;
            case R.id.navigation_crypto:
                return true;
        }
        return false;
    }

    public void decrypt(View view) {
        findKeys();
        if(m_PrivateKey == null)
        {
            Toast.makeText(this, "Private key was not found", Toast.LENGTH_LONG).show();
            return;
        }
        if(m_PublicKey == null)
        {
            Toast.makeText(this, "Public key was not found", Toast.LENGTH_LONG).show();
            return;
        }

        if(m_ResultText1.getText() == null)
        {
            return;
        }

        // Receiving side
        String input = m_ResultText1.getText().toString();

        if(input == null)
        {
            Toast.makeText(this, "Please provide input string", Toast.LENGTH_LONG).show();
            return;
        }

        byte[] data;
        try
        {
            data = decode(input, android.util.Base64.DEFAULT);
        }catch (Exception e)
        {
            Toast.makeText(this, "Decoding failed:"+ e.getMessage(), Toast.LENGTH_LONG).show();
            return;
        }
        //Decrypt data
        String decryptedText;
        try
        {
            Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            cipher.init(Cipher.DECRYPT_MODE, m_PrivateKey);
            byte[] decryptedBytes = cipher.doFinal(data);
            decryptedText = new String(decryptedBytes, UTF_8);
        }
        catch (Exception e)
        {
            Toast.makeText(this, "Decryption failed:"+ e.getMessage(), Toast.LENGTH_LONG).show();
            return;
        }

        m_ResultText2.setText(decryptedText);
    }

    public void encrypt(View view) {
        findKeys();
        if(m_PrivateKey == null)
        {
            Toast.makeText(this, "Private key was not found", Toast.LENGTH_LONG).show();
            return;
        }
        if(m_PublicKey == null)
        {
            Toast.makeText(this, "Public key was not found", Toast.LENGTH_LONG).show();
            return;
        }

        String input = m_InputText.getText().toString();

        if(input == null)
        {
            Toast.makeText(this, "Please provide input string", Toast.LENGTH_LONG).show();
            return;
        }

        byte[] inputBytes;
        try {
            inputBytes = input.getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
            Toast.makeText(this, "Encryption failed:" + e.getMessage(), Toast.LENGTH_LONG).show();
            return;
        }

        byte[] encryptedBytes;
        try
        {
            Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            cipher.init(Cipher.ENCRYPT_MODE, m_PublicKey);
            encryptedBytes = cipher.doFinal(inputBytes);
        }
        catch (Exception e)
        {
            Toast.makeText(this, "Encryption failed:" + e.getMessage(), Toast.LENGTH_LONG).show();
            return;
        }

        String encodedString = encodeToString(encryptedBytes, android.util.Base64.DEFAULT);
        m_ResultText1.setText(encodedString);
    }

    public void sign(View view) {
        findKeys();
        if(m_PrivateKey == null)
        {
            Toast.makeText(this, "Private key was not found", Toast.LENGTH_LONG).show();
            return;
        }
        if(m_PublicKey == null)
        {
            Toast.makeText(this, "Public key was not found", Toast.LENGTH_LONG).show();
            return;
        }

        String input = m_InputText.getText().toString();

        if(input == null)
        {
            Toast.makeText(this, "Please provide input string", Toast.LENGTH_LONG).show();
            return;
        }

        byte[] signatureBytes;
        try {
            signatureBytes = input.getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
            Toast.makeText(this, "Encryption failed:" + e.getMessage(), Toast.LENGTH_LONG).show();
            return;
        }

        // signature
        byte[] result;
        try
        {
            Signature sig = Signature.getInstance("SHA256withRSA");
            sig.initSign(m_PrivateKey);
            sig.update(signatureBytes);
            result = sig.sign();
        }
        catch (Exception e) {
            Toast.makeText(this, "Signing failed:" + e.getMessage(), Toast.LENGTH_LONG).show();
            return;
        }

        String encodedString = encodeToString(result, android.util.Base64.DEFAULT);
        m_ResultText1.setText(encodedString);
    }

    public void verify(View view) {
        findKeys();
        if(m_PrivateKey == null)
        {
            Toast.makeText(this, "Private key was not found", Toast.LENGTH_LONG).show();
            return;
        }
        if(m_PublicKey == null)
        {
            Toast.makeText(this, "Public key was not found", Toast.LENGTH_LONG).show();
            return;
        }

        // Receiving side
        String input = m_ResultText1.getText().toString();

        if(input == null)
        {
            Toast.makeText(this, "Please provide input string", Toast.LENGTH_LONG).show();
            return;
        }

        byte[] data = decode(input, android.util.Base64.DEFAULT);


        byte[] plaintextBytes;
        try {
            plaintextBytes = m_InputText.getText().toString().getBytes(UTF_8);
        } catch (Exception e) {
            Toast.makeText(this, "Verification failed:"+ e.getMessage(), Toast.LENGTH_LONG).show();
            Log.e("CRYPTO",e.getMessage());
            Log.e("CRYPTO",Log.getStackTraceString(e));
            return;
        }

        boolean result;
        try
        {
            Signature publicSignature = Signature.getInstance("SHA256withRSA");
            publicSignature.initVerify(m_PublicKey);
            publicSignature.update(plaintextBytes);

            result = publicSignature.verify(data);
        }
        catch (Exception e)
        {
            Toast.makeText(this, "Verification failed:"+ e.getMessage(), Toast.LENGTH_LONG).show();
            Log.e("CRYPTO",e.getMessage());
            Log.e("CRYPTO",Log.getStackTraceString(e));
            return;
        }

        m_ResultText2.setText("" + result);
    }

    private void findKeys()
    {
        if(m_PublicKey != null && m_PrivateKey != null)
        {
            return;
        }

        KeyStore store;
        try {
            store = KeyStore.getInstance("AndroidKeyStore");
        }catch (Exception e) {
            Log.e("CRYPTO",e.getMessage());
            Log.e("CRYPTO",Log.getStackTraceString(e));
            return;
        }

        try {
            store.load(null);
        }
        catch (Exception e) {
            Log.e("CRYPTO",e.getMessage());
            Log.e("CRYPTO",Log.getStackTraceString(e));
            return;
        }

        String alias = "my_key";
        try {
            if(!store.containsAlias(alias))
            {
                Toast.makeText(this, "Please create the key before", Toast.LENGTH_LONG).show();
                return;
            }
        } catch (Exception e) {
            Log.e("CRYPTO",e.getMessage());
            Log.e("CRYPTO",Log.getStackTraceString(e));
            return;
        }

        // Retrieve the keys
        KeyStore.PrivateKeyEntry privateKeyEntry;
        try {
            privateKeyEntry = (KeyStore.PrivateKeyEntry)store.getEntry(alias, null);
        } catch (Exception e) {
            Log.e("CRYPTO",e.getMessage());
            Log.e("CRYPTO",Log.getStackTraceString(e));
            return;
        }

        PrivateKey privateKey = privateKeyEntry.getPrivateKey();
        PublicKey publicKey = privateKeyEntry.getCertificate().getPublicKey();

        m_PrivateKey = privateKey;
        m_PublicKey = publicKey;
    }

    public void clear(View view) {
        m_InputText.setText("");
        m_ResultText1.setText("");
        m_ResultText2.setText("");
    }
}

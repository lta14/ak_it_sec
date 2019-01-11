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
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.util.Base64;

import javax.crypto.Cipher;

import static java.nio.charset.StandardCharsets.UTF_8;

public class CryptoActivity extends AppCompatActivity implements BottomNavigationView.OnNavigationItemSelectedListener {

    private PrivateKey m_PrivateKey;
    private PublicKey m_PublicKey;

    private EditText m_InputText;
    private EditText m_ResultText1;
    private EditText m_ResultText2;

    private byte[] m_EncryptedBytes;
    private byte[] m_DecryptedBytes;
    private byte[] m_SignatureBytes;

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

        findKeys();
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

        if(m_EncryptedBytes == null)
        {
            Toast.makeText(this, "Please provide encrypted text string", Toast.LENGTH_LONG).show();
            return;
        }

        // byte[] ivBytes = m_HashMap.get("iv");

        //Decrypt data
        String decryptedText;
        try
        {
            Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            //GCMParameterSpec spec = new GCMParameterSpec(128, ivBytes);
            cipher.init(Cipher.DECRYPT_MODE, m_PrivateKey);
            m_DecryptedBytes = cipher.doFinal(m_EncryptedBytes);
            decryptedText = new String(m_DecryptedBytes, UTF_8);
        }
        catch (Exception e)
        {
            Toast.makeText(this, "Decryption failed:"+ e.getMessage(), Toast.LENGTH_LONG).show();
            Log.e("CRYPTO",e.getMessage());
            Log.e("CRYPTO",Log.getStackTraceString(e));
            return;
        }

        m_ResultText2.setText(decryptedText);
        m_EncryptedBytes = null;
    }

    public void encrypt(View view) {
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

        try
        {
            Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            cipher.init(Cipher.ENCRYPT_MODE, m_PublicKey);
            m_EncryptedBytes = cipher.doFinal(inputBytes);
        }
        catch (Exception e)
        {
            Toast.makeText(this, "Encryption failed:" + e.getMessage(), Toast.LENGTH_LONG).show();
            return;
        }

        String result = new String(m_EncryptedBytes, UTF_8);
        m_ResultText1.setText(result);
    }

    public void sign(View view) {
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

        try {
            m_SignatureBytes = input.getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
            Toast.makeText(this, "Encryption failed:" + e.getMessage(), Toast.LENGTH_LONG).show();
            return;
        }

        // signature
        try
        {
            Signature sig = Signature.getInstance("SHA256withRSA");
            sig.initSign(m_PrivateKey);
            sig.update(m_SignatureBytes);
            m_SignatureBytes = sig.sign();
        }
        catch (Exception e) {
            Toast.makeText(this, "Signing failed:" + e.getMessage(), Toast.LENGTH_LONG).show();
            return;
        }

        String result = new String(m_SignatureBytes, UTF_8);
        m_ResultText1.setText(result);
    }

    public void verify(View view) {
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

        if(m_SignatureBytes == null)
        {
            Toast.makeText(this, "Please provide a signature", Toast.LENGTH_LONG).show();
            return;
        }

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

            //byte[] signatureBytes = Base64.getDecoder().decode(signature);

            result = publicSignature.verify(m_SignatureBytes);
        }
        catch (Exception e)
        {
            Toast.makeText(this, "Verification failed:"+ e.getMessage(), Toast.LENGTH_LONG).show();
            Log.e("CRYPTO",e.getMessage());
            Log.e("CRYPTO",Log.getStackTraceString(e));
            return;
        }

        m_ResultText2.setText("" + result);
        m_SignatureBytes = null;
    }

    private void findKeys()
    {
        KeyStore store;
        try {
            store = KeyStore.getInstance("AndroidKeyStore");
        }catch (Exception e) {
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
            return;
        }

        try {
            store.load(null);
        }
        catch (Exception e) {
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
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
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
            return;
        }

        // Retrieve the keys
        KeyStore.PrivateKeyEntry privateKeyEntry;
        try {
            privateKeyEntry = (KeyStore.PrivateKeyEntry)store.getEntry(alias, null);
        } catch (Exception e) {
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
            return;
        }

        PrivateKey privateKey = privateKeyEntry.getPrivateKey();
        PublicKey publicKey = privateKeyEntry.getCertificate().getPublicKey();

        if(privateKey == null)
        {
            Toast.makeText(this, "Private key was not found", Toast.LENGTH_LONG).show();
            return;
        }
        if(publicKey == null)
        {
            Toast.makeText(this, "Public key was not found", Toast.LENGTH_LONG).show();
            return;
        }
        m_PrivateKey = privateKey;
        m_PublicKey = publicKey;
    }

    public void clear(View view) {
        m_InputText.setText("");
        m_ResultText1.setText("");
        m_ResultText2.setText("");

        m_EncryptedBytes = null;
        m_DecryptedBytes = null;
        m_SignatureBytes = null;
    }
}

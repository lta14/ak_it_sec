package com.example.tanne.tantrum;

import android.content.Intent;
import android.os.Bundle;
import android.security.KeyPairGeneratorSpec;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.support.annotation.NonNull;
import android.support.design.widget.BottomNavigationView;
import android.support.v7.app.AppCompatActivity;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.Volley;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.StringWriter;
import java.math.BigInteger;
import java.security.InvalidAlgorithmParameterException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Calendar;

import javax.security.auth.x500.X500Principal;
import org.spongycastle.asn1.x500.X500Name;
import org.spongycastle.openssl.jcajce.JcaPEMWriter;
import org.spongycastle.operator.ContentSigner;
import org.spongycastle.operator.OperatorCreationException;
import org.spongycastle.operator.jcajce.JcaContentSignerBuilder;
import org.spongycastle.pkcs.PKCS10CertificationRequest;
import org.spongycastle.pkcs.PKCS10CertificationRequestBuilder;
import org.spongycastle.pkcs.jcajce.JcaPKCS10CertificationRequestBuilder;

public class MainActivity extends AppCompatActivity {
    private TextView m_TextMessage;
    private EditText m_UrlText;
    private EditText m_TokenText;

    private CryptoModel m_Model;


    //qr code scanner object
    private IntentIntegrator qrScan;

    public final static int ANY_PURPOSE = KeyProperties.PURPOSE_ENCRYPT |
            KeyProperties.PURPOSE_DECRYPT | KeyProperties.PURPOSE_SIGN |
            KeyProperties.PURPOSE_VERIFY;

    private BottomNavigationView.OnNavigationItemSelectedListener mOnNavigationItemSelectedListener
            = new BottomNavigationView.OnNavigationItemSelectedListener() {

        @Override
        public boolean onNavigationItemSelected(@NonNull MenuItem item) {
            switch (item.getItemId()) {
                case R.id.navigation_home:
                    m_TextMessage.setText(R.string.title_home);
                    return true;
                case R.id.navigation_dashboard:
                    m_TextMessage.setText(R.string.title_dashboard);
                    return true;
                case R.id.navigation_notifications:
                    m_TextMessage.setText(R.string.title_notifications);
                    return true;
            }
            return false;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        m_TextMessage = (TextView) findViewById(R.id.message);

        m_UrlText = (EditText) findViewById(R.id.urlText);
        m_TokenText = (EditText) findViewById(R.id.tokenText);

        BottomNavigationView navigation = (BottomNavigationView) findViewById(R.id.navigation);
        navigation.setOnNavigationItemSelectedListener(mOnNavigationItemSelectedListener);

        //intializing scan object
        qrScan = new IntentIntegrator(this);
    }

    //Getting the scan results
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        IntentResult result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
        if (result != null)
        {
            //{"url":"https://apps.egiz.gv.at/cryptobinding/","token":561vr85pvv2famg9lec1pfg5do"}

            //if qrcode has nothing in it
            if (result.getContents() == null)
            {
                Toast.makeText(this, "Result Not Found", Toast.LENGTH_LONG).show();
            } else
            {
                //if qr contains data
                try {
                    //converting the data to json
                    JSONObject obj = new JSONObject(result.getContents());
                    //setting values to textviews
                    m_UrlText.setText(obj.getString("url"));
                    m_TokenText.setText(obj.getString("token"));
                } catch (JSONException e) {
                    e.printStackTrace();
                    //if control comes here
                    //that means the encoded format not matches
                    //in this case you can display whatever data is available on the qrcode
                    //to a toast
                    Toast.makeText(this, result.getContents(), Toast.LENGTH_LONG).show();
                }
            }
        }
        else
        {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }


    public void scan(View view) {
        qrScan.initiateScan();
    }

    public void request(View view) {
        m_Model = null;
        String url = m_UrlText.getText().toString();
        String token = m_TokenText.getText().toString();
        if(url == "" || token == "")
        {
            Toast.makeText(this, "insert url and token", Toast.LENGTH_LONG).show();
            return;
        }

        // Instantiate the RequestQueue.
        RequestQueue queue = Volley.newRequestQueue(this);

        // create uri :
        String uri = String.format(url+"/binding/info" + "?token=%1$s&appId=%2$s",
                token,
                "lukas.tanner");

        CustomRequest request = new CustomRequest(Request.Method.GET,
                uri, new Response.Listener<CryptoModel>() {
                    @Override
                    public void onResponse(CryptoModel response) {
                        m_Model = response;
                        }
                }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                //mTextView.setText("That didn't work!");
            }
        });

        // Add the request to the RequestQueue.
        queue.add(request);
    }

    public void test(View view) {
        if(m_Model == null)
        {
            Toast.makeText(this, "model is null", Toast.LENGTH_LONG).show();
            return;
        }
        if(m_Model.getM_AuthToken()== null)
        {
            Toast.makeText(this, "auth token is null", Toast.LENGTH_LONG).show();
            return;
        }
        if(m_Model.getM_Subject()== null)
        {
            Toast.makeText(this, "subject is null", Toast.LENGTH_LONG).show();
            return;
        }

        KeyStore keyStore;
        try {
            keyStore = KeyStore.getInstance("AndroidKeyStore");
        }catch (Exception e) {
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
            return;
        }

        try {
            keyStore.load(null);
        }
        catch (Exception e) {
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
            return;
        }

        String alias = "my_key";
        try {
            if(keyStore.containsAlias(alias))
            {
                keyStore.deleteEntry(alias);
            }
        } catch (Exception e) {
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
            return;
        }


        KeyPairGenerator generator;
        try {
            generator = KeyPairGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_RSA, "AndroidKeyStore");
            //TODO: setSubject needed?

            generator.initialize(new KeyGenParameterSpec.Builder(
                    alias, ANY_PURPOSE)
                    .setDigests(KeyProperties.DIGEST_SHA256)
                    .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
                    .build());
        } catch (Exception e) {
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
            return;
        }

        generator.generateKeyPair();

        // Retrieve the keys
        KeyStore.PrivateKeyEntry privateKeyEntry;
        try {
            privateKeyEntry = (KeyStore.PrivateKeyEntry)keyStore.getEntry(alias, null);
        } catch (Exception e) {
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
            return;
        }

        //TODO: (PrivateKey)keystore.getKey(alias, privateKeyPassword)); give password
        PrivateKey privateKey = privateKeyEntry.getPrivateKey();
        PublicKey publicKey = privateKeyEntry.getCertificate().getPublicKey();

        //TODO: cast to RSAPrivateKey?

        Toast.makeText(this, "private key = " + privateKey.toString(), Toast.LENGTH_LONG).show();
        Toast.makeText(this, "public key = " + publicKey.toString(), Toast.LENGTH_LONG).show();


        //PrivateKey privateKey = ((KeyStore.PrivateKeyEntry) entry).getPrivateKey();
        //PublicKey publicKey = keyStore.getCertificate(alias).getPublicKey();

        /*
        I was referring to the privateKey. The public key raw data is accesible using
        byte publickey[] = keyStore.getCertificate(alias).getPublicKey().getEncoded();.
        You will need to convert it to base64 to send it to server as String
         */

        //X500Principal subject = new X500Principal (m_Model.getM_Subject());
        X500Name x500Name = new X500Name(m_Model.getM_Subject());
        ContentSigner signGen;
        try {
            signGen = new JcaContentSignerBuilder("SHA256withRSA").build(privateKey);
        } catch (Exception e) {
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
            return;
        }
        if(signGen == null)
        {
            Toast.makeText(this, "Signer creation failed", Toast.LENGTH_LONG).show();
            return;
        }

        PKCS10CertificationRequestBuilder builder = new JcaPKCS10CertificationRequestBuilder(x500Name, publicKey);
        PKCS10CertificationRequest csr = builder.build(signGen);

        if(csr == null)
        {
            Toast.makeText(this, "CSR creation failed", Toast.LENGTH_LONG).show();
            return;
        }

        //write certification request
        String csrString = null;
        try {
            csrString = csrToString(csr);
        } catch (IOException e) {
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
            return;
        }

        Toast.makeText(this, csrString, Toast.LENGTH_LONG).show();
    }

    private String csrToString(PKCS10CertificationRequest csr) throws IOException{
        StringWriter w = new StringWriter();
        JcaPEMWriter p = new JcaPEMWriter(w);
        p.writeObject(csr);
        p.close();
        return w.toString();
    }



}

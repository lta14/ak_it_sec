package com.example.tanne.tantrum;

import android.content.Intent;
import android.os.Bundle;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.support.annotation.NonNull;
import android.support.design.widget.BottomNavigationView;
import android.support.v7.app.AppCompatActivity;
import android.util.Base64;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.Map;

import org.spongycastle.asn1.x500.X500Name;
import org.spongycastle.openssl.jcajce.JcaPEMWriter;
import org.spongycastle.operator.ContentSigner;
import org.spongycastle.operator.jcajce.JcaContentSignerBuilder;
import org.spongycastle.pkcs.PKCS10CertificationRequest;
import org.spongycastle.pkcs.PKCS10CertificationRequestBuilder;
import org.spongycastle.pkcs.jcajce.JcaPKCS10CertificationRequestBuilder;

import javax.net.ssl.TrustManagerFactory;

public class MainActivity extends AppCompatActivity {
    private TextView m_TextMessage;
    private EditText m_UrlText;
    private EditText m_TokenText;
    private EditText m_RequestSuccess;
    private ImageView m_ShowSuccess;

    private CryptoModel m_Model;

    private RequestQueue m_Queue;

    private String m_Cert;


    private KeyStore m_Keystore;

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

        m_TextMessage = findViewById(R.id.message);
        m_UrlText = findViewById(R.id.urlText);
        m_TokenText = findViewById(R.id.tokenText);
        m_RequestSuccess = findViewById(R.id.requestSuccess);
        m_ShowSuccess = findViewById(R.id.showSuccess);

        BottomNavigationView navigation = findViewById(R.id.navigation);
        navigation.setOnNavigationItemSelectedListener(mOnNavigationItemSelectedListener);

        //intializing scan object
        qrScan = new IntentIntegrator(this);

        // Instantiate the RequestQueue.
        m_Queue = Volley.newRequestQueue(this);
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
                try {
                    JSONObject obj = new JSONObject(result.getContents());
                    m_UrlText.setText(obj.getString("url"));
                    m_TokenText.setText(obj.getString("token"));
                } catch (JSONException e) {
                    e.printStackTrace();
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

        // create uri :
        String uri = String.format(url+"/binding/info" + "?token=%1$s&appId=%2$s",
                token,
                "lukas.tanner");

        CustomRequest request = new CustomRequest(Request.Method.GET,
                uri, new Response.Listener<CryptoModel>() {
                    @Override
                    public void onResponse(CryptoModel response) {
                        m_Model = response;
                        m_RequestSuccess.setText("Successfully received data");
                        }
                }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                m_RequestSuccess.setText("That didn't work!");
            }
        });

        // Add the request to the RequestQueue.
        m_Queue.add(request);
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


        try {
            m_Keystore = KeyStore.getInstance("AndroidKeyStore");
        }catch (Exception e) {
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
            return;
        }

        try {
            m_Keystore.load(null);
        }
        catch (Exception e) {
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
            return;
        }

        String alias = "my_key";
        try {
            if(m_Keystore.containsAlias(alias))
            {
                m_Keystore.deleteEntry(alias);
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
            privateKeyEntry = (KeyStore.PrivateKeyEntry)m_Keystore.getEntry(alias, null);
        } catch (Exception e) {
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
            return;
        }

        //TODO: (PrivateKey)keystore.getKey(alias, privateKeyPassword)); give password
        PrivateKey privateKey = privateKeyEntry.getPrivateKey();
        PublicKey publicKey = privateKeyEntry.getCertificate().getPublicKey();

        //PrivateKey privateKey = ((KeyStore.PrivateKeyEntry) entry).getPrivateKey();
        //PublicKey publicKey = keyStore.getCertificate(alias).getPublicKey();

        /*
        I was referring to the privateKey. The public key raw data is accesible using
        byte publickey[] = keyStore.getCertificate(alias).getPublicKey().getEncoded();.
        You will need to convert it to base64 to send it to server as String
         */

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
        String csrString;
        try {
            csrString = csrToString(csr);
        } catch (IOException e) {
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
            return;
        }

        postCsr(csrString, m_Model.getM_AuthToken());
    }

    private String csrToString(PKCS10CertificationRequest csr) throws IOException{
        StringWriter w = new StringWriter();
        JcaPEMWriter p = new JcaPEMWriter(w);
        p.writeObject(csr);
        p.close();
        return w.toString();
    }

    private void postCsr(final String csr, final String authToken)
    {
        String url = m_UrlText.getText().toString();
        if(url == "")
        {
            Toast.makeText(this, "url is empty", Toast.LENGTH_LONG).show();
            return;
        }

        url = url + "/binding/signV2";

        StringRequest postRequest = new StringRequest(Request.Method.POST, url,
                new Response.Listener<String>()
                {
                    @Override
                    public void onResponse(String response) {
                        // response
                        Log.wtf("Response", response);

                        try {
                            JSONObject jsonObject = new JSONObject(response);

                            String cert = jsonObject.getString("cert");
                            m_Cert = cert;
                            storeCertInKeystore(cert);


                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }
                },
                new Response.ErrorListener()
                {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        // error
                        Log.wtf("Error.Response", error.getMessage());
                    }
                }) {
            @Override
            protected Map<String, String> getParams()
            {
                Map<String, String>  params = new HashMap<String, String>();
                params.put("csrEncoded", Base64.encodeToString(csr.getBytes(), Base64.NO_WRAP));
                params.put("appId", "lukas.tanner");

                return params;
            }

            @Override
            public Map<String, String> getHeaders()  {
                HashMap<String, String> headers = new HashMap<>();
                headers.put("x-auth-token", authToken);
                return headers;
            }
        };

        m_Queue.add(postRequest);
    }

    private void storeCertInKeystore(String cert) {


        Testing(cert);

        String adapted = "-----BEGIN CERTIFICATE-----\n" + cert + "\n-----END CERTIFICATE-----";





        String hiho = Base64.encodeToString(adapted.getBytes(), Base64.NO_WRAP);
        //Toast.makeText(this, hiho, Toast.LENGTH_LONG).show();

        String test = "-----BEGIN CERTIFICATE-----\n" +
                  "MIIGdjCCBF6gAwIBAgIIO0HBPxdtR7gwDQYJKoZIhvcNAQELBQAwgZwxCzAJBgNV\n"+
                  "BAYTAkFUMQ0wCwYDVQQIEwRXaWVuMQ0wCwYDVQQHEwRXaWVuMRkwFwYDVQQKExBC\n"+
                  "dW5kZXNrYW56bGVyYW10MQswCQYDVQQLEwJJVDEiMCAGA1UEAxQZQktBX0NyeXB0\n"+
                  "b19CaW5kaW5nX1Jvb3RDQTEjMCEGCSqGSIb3DQEJARYUemVydGlmaWthdEBia2Eu\n"+
                  "Z3YuYXQwHhcNMTkwMTEwMTEyMDI2WhcNMTkwMTExMTEyNTI2WjBpMRkwFwYDVQQD\n"+
                  "DBBTb25qYSBNdXN0ZXJmcmF1MRUwEwYDVQQKDAxsdWthcy50YW5uZXIxNTAzBgNV\n"+
                  "BC4TLE56cDJ5Z0J6emdXRklzK3Y4czd5eVA1TUlKNlNUNkN2S1hTK3pZd2pkUFk9\n"+
                  "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAwrm0oxalPgXMlWn7quo1\n"+
                  "78qQbCxNfdCdq6tKLEXllv9En2dcInykaxNnhuDX7-QzgLC0gLK3b2VNZtkK91tt\n"+
                  "DmgFxrMajrHVIIHuhWFP65ajbNrNZtzpmNvVlwl3r-N4e9YVpYEFJlLdhWNhNaxS\n"+
                  "JjFqO0VxUttmKGTxACMoKsHodczBD5lUlKoPsNKKAhMZjHmzipQTUa4KPBP0pI8A\n"+
                  "2-P6xz4iQaHfaqdbXMfysB9vONf5br1ph0ha6Sthgj-GVZbWEdFzLrnCAjEJLxD-\n"+
                  "gWyR4_OkWJlE9ed7SuKZ4ADgnBMLh63g-quCNxrYVGxb9OVZe9BaJxCVHcldKPqM\n"+
                  "1wIDAQABo4IB7DCCAegwPQYDVR0fBDYwNDAyoDCgLoYsaHR0cHM6Ly9hcHBzLmVn\n"+
                  "aXouZ3YuYXQvY3J5cHRvYmluZGluZy9jYS9jcmwwgdEGA1UdIwSByTCBxoAUc1B7\n"+
                  "HqujJ4eUC8ZPvz7Hj5U_zwmhgaKkgZ8wgZwxCzAJBgNVBAYTAkFUMQ0wCwYDVQQI\n"+
                  "EwRXaWVuMQ0wCwYDVQQHEwRXaWVuMRkwFwYDVQQKExBCdW5kZXNrYW56bGVyYW10\n"+
                  "MQswCQYDVQQLEwJJVDEiMCAGA1UEAxQZQktBX0NyeXB0b19CaW5kaW5nX1Jvb3RD\n"+
                  "QTEjMCEGCSqGSIb3DQEJARYUemVydGlmaWthdEBia2EuZ3YuYXSCCQCiNxFUU14N\n"+
                  "ozAdBgNVHQ4EFgQUS6nwm2cci7RpR0U4nMafP2qTYwwwDAYDVR0TAQH_BAIwADAL\n"+
                  "BgNVHQ8EBAMCBLAwSQYIKwYBBQUHAQEEPTA7MDkGCCsGAQUFBzAChi1odHRwczov\n"+
                  "L2FwcHMuZWdpei5ndi5hdC9jcnlwdG9iaW5kaW5nL2NhL2NlcnQwTgYUaYP5pa6f\n"+
                  "n-Kah4uWiKC09pWwiWAENhY0aHR0cHM6Ly9kZXYuYS1zaXQuYXQvY3J5cHRvYmlu\n"+
                  "ZGluZy9tYW5hZ2VtZW50L3Jldm9rZTANBgkqhkiG9w0BAQsFAAOCAgEAsBgHKnK4\n"+
                  "_Ur55fE9i39Y1qDVLfW3jfCT6LP-SkKss8-FF5BtOSp0UOLKgNDYbCFNQpd0sV8l\n"+
                  "04qbWZ7PZl2FrV8PeJOEHFjSXrgidPul92LcIxhGClosSbyfKCxNj9C5BrfRnGQf\n"+
                  "UWxuIGKN3-l7k99w-XG0ALmN-iw4cbC1fJu6L7JqK9EW0A7mA7UIJfoPAfaYF6Kh\n"+
                  "MoNFQHBOkhZ7tAtogrbNUoV8fT7x6KDm8LK7-QJvpu3rLbbDQFyTVqAkHeEefPEj\n"+
                  "IFAbhVb5leb98i04Pzzt0XrDk9BDHcqw6Br-Ai9k9l4DTGooMHosm9WpwMgOCKs5\n"+
                  "2Zi8e7Nqk3TknM51LWXHikVRyygouRW5xIJ9Wdek6nKESIoaBlDEGo2QvPfgSIhi\n"+
                  "t5OYXqx55wLRSH8378HaSh5VjhSsU6OduIZdPF5svFTAXKugHT8093v4ZxJ5-0AR\n"+
                  "_UbLTWUJV6OPLvdK9Ry-8J7DDTiHMI8ZCJF1kwoKUgZPcDNWXMQVILQNMVJBYypj\n"+
                  "E-vTJkqv_RpcOzcUjlk1GUEEKmglifdd0CCZTqMvhRzIHsCcO-7L_JFkmP_RHTyK\n"+
                  "DiFCYEKuiMLL3w6okuYRXNiP7GphECzW8I9rEQlmpeBEAeVuL1r9IdSmm7cs_A7u\n"+
                  "elj68kfiM-XK1Wf6gxZeWXd0nnNvwNdq0SI=\n"+
                   "-----END CERTIFICATE-----";
        byte[] certBytes = test.getBytes();

        //byte[] certBytes = hiho.getBytes();
        CertificateFactory certificateFactory;
        try {
            certificateFactory = CertificateFactory.getInstance("X.509", "BC");
        } catch (Exception e) {
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
            Log.wtf("MEGA", "CertificateFactory");
            return;
        }
        X509Certificate certificate;
        try {
            certificate = (X509Certificate)certificateFactory.generateCertificate(new ByteArrayInputStream(certBytes));
        } catch (CertificateException e) {
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
            Log.wtf("MEGA", "generate certificate failed: " + e.getMessage() );
            return;
        }

        if(certificate == null)
        {
            Log.wtf("MEGA", "Certificate is null");
            return;
        }

        /*
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
        */

        String certAlias = "my_super_cert";

        try {
            m_Keystore.setCertificateEntry(certAlias, certificate);
        } catch (Exception e) {
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
            return;
        }

        m_ShowSuccess.setVisibility(View.VISIBLE);
    }

    private void Testing(String cert)
    {
        String test = "-----BEGIN CERTIFICATE-----\n" +
                "MIIGdjCCBF6gAwIBAgIIO0HBPxdtR7gwDQYJKoZIhvcNAQELBQAwgZwxCzAJBgNV\n"+
                "BAYTAkFUMQ0wCwYDVQQIEwRXaWVuMQ0wCwYDVQQHEwRXaWVuMRkwFwYDVQQKExBC\n"+
                "dW5kZXNrYW56bGVyYW10MQswCQYDVQQLEwJJVDEiMCAGA1UEAxQZQktBX0NyeXB0\n"+
                "b19CaW5kaW5nX1Jvb3RDQTEjMCEGCSqGSIb3DQEJARYUemVydGlmaWthdEBia2Eu\n"+
                "Z3YuYXQwHhcNMTkwMTEwMTEyMDI2WhcNMTkwMTExMTEyNTI2WjBpMRkwFwYDVQQD\n"+
                "DBBTb25qYSBNdXN0ZXJmcmF1MRUwEwYDVQQKDAxsdWthcy50YW5uZXIxNTAzBgNV\n"+
                "BC4TLE56cDJ5Z0J6emdXRklzK3Y4czd5eVA1TUlKNlNUNkN2S1hTK3pZd2pkUFk9\n"+
                "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAwrm0oxalPgXMlWn7quo1\n"+
                "78qQbCxNfdCdq6tKLEXllv9En2dcInykaxNnhuDX7-QzgLC0gLK3b2VNZtkK91tt\n"+
                "DmgFxrMajrHVIIHuhWFP65ajbNrNZtzpmNvVlwl3r-N4e9YVpYEFJlLdhWNhNaxS\n"+
                "JjFqO0VxUttmKGTxACMoKsHodczBD5lUlKoPsNKKAhMZjHmzipQTUa4KPBP0pI8A\n"+
                "2-P6xz4iQaHfaqdbXMfysB9vONf5br1ph0ha6Sthgj-GVZbWEdFzLrnCAjEJLxD-\n"+
                "gWyR4_OkWJlE9ed7SuKZ4ADgnBMLh63g-quCNxrYVGxb9OVZe9BaJxCVHcldKPqM\n"+
                "1wIDAQABo4IB7DCCAegwPQYDVR0fBDYwNDAyoDCgLoYsaHR0cHM6Ly9hcHBzLmVn\n"+
                "aXouZ3YuYXQvY3J5cHRvYmluZGluZy9jYS9jcmwwgdEGA1UdIwSByTCBxoAUc1B7\n"+
                "HqujJ4eUC8ZPvz7Hj5U_zwmhgaKkgZ8wgZwxCzAJBgNVBAYTAkFUMQ0wCwYDVQQI\n"+
                "EwRXaWVuMQ0wCwYDVQQHEwRXaWVuMRkwFwYDVQQKExBCdW5kZXNrYW56bGVyYW10\n"+
                "MQswCQYDVQQLEwJJVDEiMCAGA1UEAxQZQktBX0NyeXB0b19CaW5kaW5nX1Jvb3RD\n"+
                "QTEjMCEGCSqGSIb3DQEJARYUemVydGlmaWthdEBia2EuZ3YuYXSCCQCiNxFUU14N\n"+
                "ozAdBgNVHQ4EFgQUS6nwm2cci7RpR0U4nMafP2qTYwwwDAYDVR0TAQH_BAIwADAL\n"+
                "BgNVHQ8EBAMCBLAwSQYIKwYBBQUHAQEEPTA7MDkGCCsGAQUFBzAChi1odHRwczov\n"+
                "L2FwcHMuZWdpei5ndi5hdC9jcnlwdG9iaW5kaW5nL2NhL2NlcnQwTgYUaYP5pa6f\n"+
                "n-Kah4uWiKC09pWwiWAENhY0aHR0cHM6Ly9kZXYuYS1zaXQuYXQvY3J5cHRvYmlu\n"+
                "ZGluZy9tYW5hZ2VtZW50L3Jldm9rZTANBgkqhkiG9w0BAQsFAAOCAgEAsBgHKnK4\n"+
                "_Ur55fE9i39Y1qDVLfW3jfCT6LP-SkKss8-FF5BtOSp0UOLKgNDYbCFNQpd0sV8l\n"+
                "04qbWZ7PZl2FrV8PeJOEHFjSXrgidPul92LcIxhGClosSbyfKCxNj9C5BrfRnGQf\n"+
                "UWxuIGKN3-l7k99w-XG0ALmN-iw4cbC1fJu6L7JqK9EW0A7mA7UIJfoPAfaYF6Kh\n"+
                "MoNFQHBOkhZ7tAtogrbNUoV8fT7x6KDm8LK7-QJvpu3rLbbDQFyTVqAkHeEefPEj\n"+
                "IFAbhVb5leb98i04Pzzt0XrDk9BDHcqw6Br-Ai9k9l4DTGooMHosm9WpwMgOCKs5\n"+
                "2Zi8e7Nqk3TknM51LWXHikVRyygouRW5xIJ9Wdek6nKESIoaBlDEGo2QvPfgSIhi\n"+
                "t5OYXqx55wLRSH8378HaSh5VjhSsU6OduIZdPF5svFTAXKugHT8093v4ZxJ5-0AR\n"+
                "_UbLTWUJV6OPLvdK9Ry-8J7DDTiHMI8ZCJF1kwoKUgZPcDNWXMQVILQNMVJBYypj\n"+
                "E-vTJkqv_RpcOzcUjlk1GUEEKmglifdd0CCZTqMvhRzIHsCcO-7L_JFkmP_RHTyK\n"+
                "DiFCYEKuiMLL3w6okuYRXNiP7GphECzW8I9rEQlmpeBEAeVuL1r9IdSmm7cs_A7u\n"+
                "elj68kfiM-XK1Wf6gxZeWXd0nnNvwNdq0SI=\n"+
                "-----END CERTIFICATE-----";

        Log.wtf("CERT1", test);

        String hiho = Base64.encodeToString(cert.getBytes(), Base64.NO_WRAP);

        String adapted = "-----BEGIN CERTIFICATE-----\n" + hiho + "\n-----END CERTIFICATE-----";

        Log.wtf("CERT1", adapted);

        //String hiho2 = Base64.(cert, Base64.NO_WRAP);

        byte[] testing = Base64.decode(cert, Base64.NO_WRAP);

        String str = new String(testing);

        Log.wtf("CERT1", str);


    }


}

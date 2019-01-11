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
import android.widget.LinearLayout;
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
import com.squareup.okhttp.OkHttpClient;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.security.KeyManagementException;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

import org.spongycastle.asn1.x500.X500Name;
import org.spongycastle.openssl.jcajce.JcaPEMWriter;
import org.spongycastle.operator.ContentSigner;
import org.spongycastle.operator.jcajce.JcaContentSignerBuilder;
import org.spongycastle.pkcs.PKCS10CertificationRequest;
import org.spongycastle.pkcs.PKCS10CertificationRequestBuilder;
import org.spongycastle.pkcs.jcajce.JcaPKCS10CertificationRequestBuilder;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
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
    private KeyStore m_Trustore;

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

    public void getCertificate(View view) {
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

        byte[] certBytes = Base64.decode(cert, Base64.URL_SAFE);

        CertificateFactory certificateFactory;
        try {
            certificateFactory = CertificateFactory.getInstance("X.509", "BC");
        } catch (Exception e) {
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
            Log.wtf("Certificate", "CertificateFactory");
            return;
        }
        X509Certificate certificate;
        try {
            certificate = (X509Certificate)certificateFactory.generateCertificate(new ByteArrayInputStream(certBytes));
        } catch (CertificateException e) {
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
            Log.wtf("Certificate", "generate certificate failed: " + e.getMessage() );
            return;
        }

        if(certificate == null)
        {
            Toast.makeText(this, "Certificate could not be obtained", Toast.LENGTH_LONG).show();
            Log.wtf("Certificate", "Certificate is null");
            return;
        }


        try {
            //keyStore = KeyStore.getInstance("BKS");
            m_Trustore = KeyStore.getInstance("pkcs12");
            //KeyStore keystore = KeyStore.getInstance("JKS");
        }catch (Exception e) {
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
            Log.wtf("Certificate1", e.getMessage());
            return;
        }

        try {
            m_Trustore.load(null);
        }
        catch (Exception e) {
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
            Log.wtf("Certificate2", e.getMessage());
            return;
        }

        String certAlias = "my_super_cert";
        try {
            if(m_Trustore.containsAlias(certAlias))
            {
                m_Trustore.deleteEntry(certAlias);
            }
        } catch (Exception e) {
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
            Log.wtf("Certificate3", e.getMessage());
            return;
        }

        // Retrieve the keys
        KeyStore.PrivateKeyEntry privateKeyEntry;
        try {
            privateKeyEntry = (KeyStore.PrivateKeyEntry)m_Keystore.getEntry("my_key", null);
        } catch (Exception e) {
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
            Log.wtf("Certificate4", e.getMessage());
            return;
        }

        PrivateKey privateKey = privateKeyEntry.getPrivateKey();

        try {
            m_Trustore.setKeyEntry(certAlias, privateKey, "mypassword".toCharArray(), new X509Certificate[]{certificate});
        } catch (KeyStoreException e) {
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
            Log.wtf("Certificate5", e.getMessage());
            return;
        }

        /*
        try {
            m_Keystore.setCertificateEntry(certAlias, certificate);
        } catch (Exception e) {
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
            return;
        }
        */

        m_ShowSuccess.setVisibility(View.VISIBLE);
    }

    public void useCertificate() {
        if(m_Keystore == null)
        {
            Toast.makeText(this,"Keystore not initialized", Toast.LENGTH_LONG).show();
            return;
        }
        if(m_Trustore == null)
        {
            Toast.makeText(this, "Truststore not initialized", Toast.LENGTH_LONG).show();
            return;
        }

        try
        {
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(m_Trustore);

            //KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            //kmf.init(m_Keystore, "".toCharArray());
        }
        catch(Exception e)
        {
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
            Log.wtf("CAStore", e.getMessage());
        }

        Toast.makeText(this, "Success", Toast.LENGTH_LONG).show();


        /*
        OkHttpClient client = new OkHttpClient();
        KeyStore keyStore = m_Keystore;
        KeyStore trustStore = App.getInstance().getKeyStoreUtil().getTrustStore();

        TrustManagerFactory tmf = null;
        try {
            tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        try {
            tmf.init(trustStore);
        } catch (KeyStoreException e) {
            e.printStackTrace();
        }

        KeyManagerFactory kmf = null;
        try {
            kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        kmf.init(keyStore, keyStorePassword);

        SSLContext sslCtx = null;
        try {
            sslCtx = SSLContext.getInstance("TLS");
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        try {
            sslCtx.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
        } catch (KeyManagementException e) {
            e.printStackTrace();
        }
        client.setSslSocketFactory(sslCtx.getSocketFactory());
        client.setHostnameVerifier(org.apache.http.conn.ssl.SSLSocketFactory.STRICT_HOSTNAME_VERIFIER);
        */
        }


}

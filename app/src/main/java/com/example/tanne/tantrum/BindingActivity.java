package com.example.tanne.tantrum;

import android.content.Intent;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.support.annotation.NonNull;
import android.support.design.widget.BottomNavigationView;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import org.apache.commons.io.IOUtils;
import org.json.JSONException;
import org.json.JSONObject;
import org.spongycastle.asn1.x500.X500Name;
import org.spongycastle.openssl.jcajce.JcaPEMWriter;
import org.spongycastle.operator.ContentSigner;
import org.spongycastle.operator.jcajce.JcaContentSignerBuilder;
import org.spongycastle.pkcs.PKCS10CertificationRequest;
import org.spongycastle.pkcs.PKCS10CertificationRequestBuilder;
import org.spongycastle.pkcs.jcajce.JcaPKCS10CertificationRequestBuilder;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.Map;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;

public class BindingActivity extends AppCompatActivity implements BottomNavigationView.OnNavigationItemSelectedListener {

    private EditText m_UrlText;
    private EditText m_TokenText;
    private EditText m_RequestSuccess;
    private ImageView m_ShowSuccess;
    private WebView m_WebView;
    private CryptoModel m_Model;
    private Button m_ScanButton;

    private RequestQueue m_Queue;

    private String m_Cert;
    private String m_ServiceProviders;

    private KeyStore m_Keystore;
    private KeyStore m_Trustore;

    //qr code scanner object
    private IntentIntegrator qrScan;

    public final static int ANY_PURPOSE = KeyProperties.PURPOSE_ENCRYPT |
            KeyProperties.PURPOSE_DECRYPT | KeyProperties.PURPOSE_SIGN |
            KeyProperties.PURPOSE_VERIFY;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_binding);

        m_UrlText = findViewById(R.id.urlText);
        m_TokenText = findViewById(R.id.tokenText);
        m_RequestSuccess = findViewById(R.id.requestSuccess);
        m_ShowSuccess = findViewById(R.id.showSuccess);
        m_ScanButton = findViewById(R.id.scanBtn);
        m_WebView = findViewById(R.id.webview);

        m_WebView.getSettings().setJavaScriptEnabled(true);

        BottomNavigationView navigation = findViewById(R.id.navigation);
        navigation.setOnNavigationItemSelectedListener(this);

        Menu menu = navigation.getMenu();
        MenuItem menuItem = menu.getItem(1);
        menuItem.setChecked(true);

        //intializing scan object
        qrScan = new IntentIntegrator(this);
        qrScan.setDesiredBarcodeFormats(IntentIntegrator.QR_CODE_TYPES);

        // Instantiate the RequestQueue.
        m_Queue = Volley.newRequestQueue(this);

        m_ScanButton.requestFocus();
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

            generator.initialize(new KeyGenParameterSpec.Builder(
                    alias, ANY_PURPOSE)
                    .setDigests(KeyProperties.DIGEST_NONE, KeyProperties.DIGEST_SHA256)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_PKCS1)
                    .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
                    .setKeySize(2048)
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

        PrivateKey privateKey = privateKeyEntry.getPrivateKey();
        PublicKey publicKey = privateKeyEntry.getCertificate().getPublicKey();

        ContentSigner signGen;
        try {
            signGen = new JcaContentSignerBuilder("SHA256withRSA").build(privateKey);
        } catch (Exception e) {
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
            Log.e("CERT",e.getMessage());
            Log.e("CERT",Log.getStackTraceString(e));
            return;
        }
        if(signGen == null)
        {
            Toast.makeText(this, "Signer creation failed", Toast.LENGTH_LONG).show();
            return;
        }

        X500Name x500Name = new X500Name(m_Model.getM_Subject());
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
            m_Trustore = KeyStore.getInstance("pkcs12");
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
                Log.wtf("Certficate", "Delete Cert");
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
            return;
        }

        PrivateKey privateKey = privateKeyEntry.getPrivateKey();
        if(privateKey == null)
        {
            Log.wtf("Certificate", "privatekey is null");
            return;
        }

        try {
            m_Trustore.setKeyEntry(certAlias, privateKey, null, new X509Certificate[]{certificate});
        } catch (Exception e) {
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
            Log.wtf("Cert", e.getMessage());
            return;
        }

        m_ShowSuccess.setVisibility(View.VISIBLE);
    }

    public void useCertificate(View view) {
        if (m_Trustore == null) {
            Toast.makeText(this, "Truststore not initialized", Toast.LENGTH_LONG).show();
            return;
        }

        KeyManagerFactory kmf;
        try {
            kmf = KeyManagerFactory.getInstance("X509");
            kmf.init(m_Trustore, null);
        } catch (Exception e) {
            Toast.makeText(this,
                    "Could not create TrustManagerFactory/KeyManagerFactory", Toast.LENGTH_LONG).show();
            return;
        }

        SSLContext context;
        try {
            context = SSLContext.getInstance("TLS");
            context.init(kmf.getKeyManagers(), null, new java.security.SecureRandom());
        } catch (Exception e) {
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
            return;
        }

        final SSLSocketFactory factory = context.getSocketFactory();
        m_ServiceProviders = null;
        Thread thread = new Thread(new Runnable() {

            @Override
            public void run() {
                try  {
                    connectToServiceProvider(factory);
                } catch (Exception e) {
                    Log.e("CERT",Log.getStackTraceString(e));
                }
            }
        });

        thread.start();
        Toast.makeText(this, "Load further ServiceProviders", Toast.LENGTH_SHORT).show();
        try {
            thread.join();  // wait for thread to finish
        } catch (InterruptedException e) {
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
            Log.wtf("Cert", e.getMessage());
            return;
        }

        if(m_ServiceProviders == null)
        {
            Toast.makeText(this, "Could not retrieve ServiceProviders", Toast.LENGTH_LONG).show();
            return;
        }

        Toast.makeText(this, "Success", Toast.LENGTH_LONG).show();

        //TODO: obtain data
        m_WebView.loadDataWithBaseURL("", m_ServiceProviders, "text/html", "UTF-8", "");
    }

    private void connectToServiceProvider(SSLSocketFactory factory)
    {
        String result;
        HttpURLConnection urlConnection = null;

        try {
            URL requestedUrl = new URL(m_Model.getM_AppContentUrl());
            urlConnection = (HttpURLConnection) requestedUrl.openConnection();
            if(urlConnection instanceof HttpsURLConnection) {
                ((HttpsURLConnection)urlConnection)
                        .setSSLSocketFactory(factory);
            }
            urlConnection.connect();
            urlConnection.setRequestMethod("GET");
            urlConnection.setConnectTimeout(2000);
            urlConnection.setReadTimeout(2000);
            int lastResponseCode = urlConnection.getResponseCode();
            InputStream in = urlConnection.getInputStream();
            result = IOUtils.toString(in, StandardCharsets.UTF_8);
            String lastContentType = urlConnection.getContentType();
            Log.wtf("CERT", "Code:"+lastResponseCode);
            Log.wtf("CERT", "Contenttype:"+lastContentType);
            Log.wtf("CERT", result);

            m_ServiceProviders = result;
        }
        catch (Exception e)
        {
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
            Log.e("CERT",Log.getStackTraceString(e));
        } finally {
            if(urlConnection != null) {
                urlConnection.disconnect();
            }
        }
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
            case R.id.navigation_home:
                Intent intent = new Intent(this, MainActivity.class);
                startActivity(intent);
                return true;
            case R.id.navigation_binding:
                return true;
            case R.id.navigation_crypto:
                Intent intent2 = new Intent(this, CryptoActivity.class);
                startActivity(intent2);
                return true;
        }
        return false;
    }
}

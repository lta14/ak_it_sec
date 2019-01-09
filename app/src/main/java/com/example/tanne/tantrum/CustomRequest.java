package com.example.tanne.tantrum;


import com.android.volley.NetworkResponse;
import com.android.volley.ParseError;
import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.toolbox.HttpHeaderParser;

import org.json.JSONException;
import org.json.JSONObject;

public class CustomRequest extends Request {
    private Response.Listener listener;

    public CustomRequest(int method, String url, Response.Listener listener, Response.ErrorListener errorListener) {
        super(method, url, errorListener);
        this.listener = listener;
    }

    protected Response parseNetworkResponse(NetworkResponse response) {
        try {
            String jsonString = new String(response.data);


            JSONObject jsonObject = new JSONObject(jsonString);

            String subject = jsonObject.getString("subject");
            String userName = jsonObject.getString("userName");
            String appId = jsonObject.getString("appId");
            String appContentUrl = jsonObject.getString("appContentUrl");
            String keyType = jsonObject.getString("keyType");
            String keySize = jsonObject.getString("keySize");
            String authValiditySeconds = jsonObject.getString("authValiditySeconds");
            String certValidityHours = jsonObject.getString("certValidityHours");
            String issuedTimestamp = jsonObject.getString("issuedTimestamp");

            String authToken = response.headers.get("x-auth-token");

            CryptoModel result = new CryptoModel();
            result.setM_Subject(subject);
            result.setM_UserName(userName);
            result.setM_AppId(appId);
            result.setM_AppContentUrl(appContentUrl);
            result.setM_KeyType(keyType);
            result.setM_KeySize(keySize);
            result.setM_AuthValiditySeconds(authValiditySeconds);
            result.setM_CertValidityHours(certValidityHours);
            result.setM_Timestamp(issuedTimestamp);
            result.setM_AuthToken(authToken);

            return Response.success(result, HttpHeaderParser.parseCacheHeaders(response));
        } catch (JSONException e) {
            e.printStackTrace();
            return Response.error(new ParseError(e));
        }
    }
    /**
     This is called on the main thread with the object you returned in
     parseNetworkResponse(). You should be invoking your callback interface
     from here
     **/
    @Override
    protected void deliverResponse(Object response) {
        listener.onResponse(response);
    }
}

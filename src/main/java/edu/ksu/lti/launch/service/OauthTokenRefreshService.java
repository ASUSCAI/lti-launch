package edu.ksu.lti.launch.service;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import edu.ksu.lti.launch.exception.OauthTokenRequiredException;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.LinkedList;
import java.util.List;

/**
 * Created by alexanda on 7/26/16.
 */
@Service
public class OauthTokenRefreshService {

    private static Logger LOG = Logger.getLogger(OauthTokenRefreshService.class);

    @Autowired
    private LtiLaunchKeyService launchKeyService;
    @Autowired
    private OauthTokenService oauthTokenService;
    @Autowired
    private String canvasDomain;

    public String getRefreshedOauthToken(String eid) throws IOException {
        HttpPost canvasRequest = createRefreshCanvasRequest(eid);
        HttpClient client = HttpClientBuilder.create().build();
        HttpResponse response = client.execute(canvasRequest);

        if (response.getStatusLine() == null || response.getStatusLine().getStatusCode() == 401) {
            LOG.warn("Refresh failed. Redirect to oauth flow");
            throw new OauthTokenRequiredException();
        } else {
            JsonObject responseContent = new Gson().fromJson(EntityUtils.toString(response.getEntity()), JsonObject.class);
            String accessToken = responseContent.get("access_token").getAsString();
            LOG.debug("Refreshed access token for eid " + eid + ": " + accessToken);
            oauthTokenService.updateToken(eid, accessToken);
            return accessToken;
        }
    }

    private HttpPost createRefreshCanvasRequest(String eid) {
        try {
            URI uri = new URIBuilder()
                    .setScheme("https")
                    .setHost(canvasDomain)
                    .setPath("/login/oauth2/token")
                    .build();
            HttpPost canvasRequest = new HttpPost(uri);
            List<NameValuePair> paramList = new LinkedList<>();
            paramList.add(new BasicNameValuePair("grant_type", "refresh_token"));
            paramList.add(new BasicNameValuePair("client_id", launchKeyService.findOauthClientId()));
            paramList.add(new BasicNameValuePair("client_secret", launchKeyService.findOauthClientSecret()));
            paramList.add(new BasicNameValuePair("refresh_token", oauthTokenService.getRefreshToken(eid)));
            UrlEncodedFormEntity entity = new UrlEncodedFormEntity(paramList);

            canvasRequest.setEntity(entity);
            return canvasRequest;
        } catch (URISyntaxException e) {
            throw new RuntimeException("Invalid uri for canvas when requesting refresh oauthToken", e);
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException("Invalid encoding for canvas when requesting refresh oauthToken", e);
        }
    }
}

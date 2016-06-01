package org.hopestarter.wallet.server_api;

import android.content.Context;

import com.amazonaws.mobileconnectors.s3.transfermanager.Upload;

import org.hopestarter.wallet.Constants;
import org.hopestarter.wallet.data.UserInfoPrefs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Response;
import retrofit2.Retrofit;

/**
 * Created by Adrian on 11/02/2016.
 */
public class ServerApi {
    private final Retrofit mApiRetrofit;
    private final IServerApi mApiImpl;
    private final Context mContext;
    private String mAuthHeaderValue;

    public ServerApi(Context context) {
        mContext = context;
        Interceptor interceptor = new Interceptor() {
            private final Logger log = LoggerFactory.getLogger(ServerApi.class);
            @Override
            public okhttp3.Response intercept(Chain chain) throws IOException {
                Request request = chain.request();
                log.debug("Requesting " + request.method() + " " + request.url().toString());
                okhttp3.Response response = chain.proceed(request);
                log.debug("Response code " + Integer.toString(response.code()) + " from " +request.url().toString());
                return response;
            }
        };

        OkHttpClient client = new OkHttpClient.Builder()
                .addNetworkInterceptor(interceptor)
                .build();

        mApiRetrofit = new Retrofit.Builder()
                .client(client)
                .baseUrl(Constants.API_BASE_URL)
                .addConverterFactory(new TokenResponseConverterFactory())
                .addConverterFactory(new UserInfoResponseConverterFactory())
                .addConverterFactory(new UserInfoRequestConverterFactory())
                .addConverterFactory(new UploadImageResponseConverterFactory())
                .addConverterFactory(new OutboundLocationMarkConverterFactory())
                .build();

        mApiImpl = mApiRetrofit.create(IServerApi.class);

        updateAuthHeaderValue();

    }

    public void updateAuthHeaderValue() {
        mAuthHeaderValue = "Bearer " + mContext
                .getSharedPreferences(UserInfoPrefs.PREF_FILE, Context.MODE_PRIVATE)
                .getString(UserInfoPrefs.TOKEN, "");
    }

    public String getToken(String username, String password) throws IOException, AuthenticationFailed, UnexpectedServerResponseException {
        Call<TokenResponse> call = mApiImpl.getToken("password", username, password, "set-location update-profile");
        Response<TokenResponse> response = call.execute();
        if (response.isSuccess()) {
            TokenResponse tokenResp = response.body();
            return tokenResp.getAccessToken();
        } else {
            switch (response.code()) {
                case 401:
                    throw new AuthenticationFailed();
                default:
                    throw new UnexpectedServerResponseException(response.code());
            }
        }
    }

    public UserInfo getUserInfo() throws NoTokenException, IOException, ForbiddenResourceException,
            UnexpectedServerResponseException, AuthenticationFailed {

        if (mAuthHeaderValue.isEmpty()) {
            throw new NoTokenException("No token has been retrieved before. Try authenticating with the server first.");
        }

        Call<UserInfo> call = mApiImpl.getUserInfo(mAuthHeaderValue);
        Response<UserInfo> response = call.execute();
        if (response.isSuccess()) {
            return response.body();
        } else {
            switch (response.code()) {
                case 401:
                    throw new AuthenticationFailed();
                case 403:
                    throw new ForbiddenResourceException();
                default:
                    throw new UnexpectedServerResponseException(response.code());
            }
        }
    }

    public UserInfo setUserInfo(String firstName, String lastName, String pictureUri) throws NoTokenException, IOException, AuthenticationFailed, ForbiddenResourceException, UnexpectedServerResponseException {
        if (mAuthHeaderValue.isEmpty()) {
            throw new NoTokenException("No token has been retrieved before. Try authenticating with the server first.");
        }

        UserInfo info = new UserInfo(firstName, lastName, pictureUri);

        Call<UserInfo> call = mApiImpl.setUserInfo(mAuthHeaderValue, info);
        Response<UserInfo> response = call.execute();
        if (response.isSuccess()) {
            return response.body();
        } else {
            switch (response.code()) {
                case 401:
                    throw new AuthenticationFailed();
                case 403:
                    throw new ForbiddenResourceException();
                default:
                    throw new UnexpectedServerResponseException(response.code());
            }
        }
    }

    public UploadImageResponse requestImageUpload() throws NoTokenException, IOException, AuthenticationFailed, ForbiddenResourceException, UnexpectedServerResponseException {
        if (mAuthHeaderValue.isEmpty()) {
            throw new NoTokenException("No token has been retrieved before. Try authenticating with the server first.");
        }

        Call<UploadImageResponse> call = mApiImpl.requestImageUpload(mAuthHeaderValue);
        Response<UploadImageResponse> response = call.execute();

        if (response.isSuccess()) {
            return response.body();
        } else {
            switch (response.code()) {
                case 401:
                    throw new AuthenticationFailed();
                case 403:
                    throw new ForbiddenResourceException();
                default:
                    throw new UnexpectedServerResponseException(response.code());
            }
        }
    }

    public void uploadLocationMark(OutboundLocationMark locationMark) throws NoTokenException, IOException, AuthenticationFailed, ForbiddenResourceException, UnexpectedServerResponseException {
        if (mAuthHeaderValue.isEmpty()) {
            throw new NoTokenException("No token has been retrieved before. Try authenticating with the server first.");
        }

        Call<ResponseBody> call = mApiImpl.uploadLocationMark(mAuthHeaderValue, locationMark);
        Response<ResponseBody> response = call.execute();

        if (!response.isSuccess()) {
            switch (response.code()) {
                case 401:
                    throw new AuthenticationFailed();
                case 403:
                    throw new ForbiddenResourceException();
                default:
                    throw new UnexpectedServerResponseException(response.code());
            }
        }
    }

}
package org.hopestarter.wallet.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.CoordinatorLayout;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.amazonaws.mobileconnectors.s3.transferutility.TransferListener;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferObserver;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferState;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferUtility;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.makeramen.roundedimageview.RoundedImageView;

import org.hopestarter.wallet.WalletApplication;
import org.hopestarter.wallet.data.UserInfoPrefs;
import org.hopestarter.wallet.server_api.AuthenticationFailed;
import org.hopestarter.wallet.server_api.BucketInfo;
import org.hopestarter.wallet.server_api.ForbiddenResourceException;
import org.hopestarter.wallet.server_api.NoTokenException;
import org.hopestarter.wallet.server_api.OutboundLocationMark;
import org.hopestarter.wallet.server_api.Point;
import org.hopestarter.wallet.server_api.ServerApi;
import org.hopestarter.wallet.server_api.UnexpectedServerResponseException;
import org.hopestarter.wallet.server_api.UploadImageResponse;
import org.hopestarter.wallet.util.ResourceUtils;
import org.hopestarter.wallet_test.R;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public class ProfileFragment extends Fragment {

    private static final String TAG = ProfileFragment.class.getName();
    private static final Logger log = LoggerFactory.getLogger(ProfileFragment.class);
    private static final int POST_UPDATE_REQ_CODE = 0;
    private UpdatesFragment mUpdatesFragment;
    private TextView mNumUpdates;
    private Button mPostBtn;
    private TextView mUserNameView;
    private TextView mUserEthnicityView;
    private TextView mProfileDonations;
    private String mFirstName;
    private String mLastName;
    private String mEthnicity;
    private String mFullName;
    private RelativeLayout mProfileLayout;
    private Uri mProfilePicture;
    private RoundedImageView mProfilePictureView;

    private RequestListener mImageLoaderListener = new RequestListener() {
        @Override
        public boolean onException(Exception e, Object model, Target target, boolean isFirstResource) {
            log.error("Failed loading image", e);
            return false;
        }

        @Override
        public boolean onResourceReady(Object resource, Object model, Target target, boolean isFromMemoryCache, boolean isFirstResource) {
            return false;
        }
    };

    public ProfileFragment() {
        // Required empty public constructor
    }

    public static ProfileFragment newInstance() {
        ProfileFragment fragment = new ProfileFragment();
        Bundle args = new Bundle();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SharedPreferences prefs = getActivity().getSharedPreferences(UserInfoPrefs.PREF_FILE, Context.MODE_PRIVATE);
        mFirstName = prefs.getString(UserInfoPrefs.FIRST_NAME, getString(R.string.unnamed_first_name));
        mLastName = prefs.getString(UserInfoPrefs.LAST_NAME, getString(R.string.unnamed_last_name));
        mEthnicity = prefs.getString(UserInfoPrefs.ETHNICITY, getString(R.string.no_ethnicity_ethnicity));

        Uri profilePictureSource = Uri.parse(
                prefs.getString(
                    UserInfoPrefs.PROFILE_PIC,
                    ResourceUtils.resIdToUri(
                            getActivity(), R.drawable.avatar_placeholder
                    ).toString()
                )
        );

        if (profilePictureSource.getScheme() == null || profilePictureSource.getScheme().isEmpty()) {
            mProfilePicture = Uri.fromFile(new File(profilePictureSource.getPath()));
        } else {
            mProfilePicture = profilePictureSource;
        }

        mFullName = mFirstName + " " + mLastName;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_profile, container, false);

        mNumUpdates = (TextView)rootView.findViewById(R.id.profile_updates);

        mUpdatesFragment = UpdatesFragment.newInstance();

        mPostBtn = (Button)rootView.findViewById(R.id.post_photo_update);

        mPostBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                launchCreateNewUpdateActivity();
            }
        });

        getChildFragmentManager().beginTransaction()
                .add(R.id.updates_fragment_container, mUpdatesFragment).commit();

        mUserNameView = (TextView)rootView.findViewById(R.id.profile_full_name);
        mUserEthnicityView = (TextView)rootView.findViewById(R.id.profile_ethnicity);
        mProfileDonations = (TextView)rootView.findViewById(R.id.profile_received_donations);
        mProfilePictureView = (RoundedImageView)rootView.findViewById(R.id.profile_image_view);

        mProfileLayout = (RelativeLayout)rootView.findViewById(R.id.profile_layout);
        mProfileLayout.setClickable(true);
        mProfileLayout.setOnTouchListener(new View.OnTouchListener() {
            private float lastY;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                log.debug("Profile layout onTouchListener.onTouch called");
                final int action = event.getAction() & MotionEvent.ACTION_MASK;
                switch (action) {
                    case MotionEvent.ACTION_DOWN:
                        log.debug("ACTION DOWN event received");
                        lastY = event.getRawY();
                        break;
                    case MotionEvent.ACTION_MOVE:
                        log.debug("ACTION DOWN event received");
                        return moveView(event);
                }
                return false;
            }

            private boolean moveView(MotionEvent event) {
                float dy = event.getRawY() - lastY;
                float newTop = mProfileLayout.getTop() + dy;

                lastY = event.getRawY();

                if (newTop < -mProfileLayout.getHeight()) {
                    dy = dy - (newTop + mProfileLayout.getHeight());
                }

                if (newTop > 0) {
                    dy = dy - newTop;
                }

                if (dy != 0) {
                    CoordinatorLayout.LayoutParams layoutParams = (CoordinatorLayout.LayoutParams) mProfileLayout.getLayoutParams();
                    layoutParams.topMargin = Double.valueOf(Math.floor(mProfileLayout.getTop() + dy)).intValue();
                    mProfileLayout.setLayoutParams(layoutParams);
                    mProfileLayout.getParent().requestLayout();
                    return true;
                }

                return false;
            }
        });

        mProfileLayout.setOnClickListener(new View.OnClickListener() {
            private int taps;

            private Runnable resetRunnable = new Runnable() {
                @Override
                public void run() {
                    taps = 0;
                }
            };

            @Override
            public void onClick(View v) {
                taps++;
                if (taps == 10) {
                    taps = 0;
                    String receiveAddress = ((WalletApplication)getActivity().getApplication()).getWallet().currentReceiveAddress().toString();

                    ClipboardManager clipboardManager = (ClipboardManager)getActivity().getSystemService(Context.CLIPBOARD_SERVICE);
                    ClipData data = ClipData.newPlainText("address", receiveAddress);
                    clipboardManager.setPrimaryClip(data);
                    Toast.makeText(getActivity(), "Address copied to the clipboard", Toast.LENGTH_SHORT).show();

                    AlertDialog addressDialog = new AlertDialog.Builder(getActivity())
                            .setTitle("Bitcoin address")
                            .setMessage(receiveAddress)
                            .create();
                    addressDialog.show();
                }
                mProfileLayout.removeCallbacks(resetRunnable);
                mProfileLayout.postDelayed(resetRunnable, 1000);
            }
        });

        feedFakeData();
        feedData();
        updateNumberOfUpdates();
        return rootView;
    }

    private void feedData() {
        mUserNameView.setText(mFullName);
        mUserEthnicityView.setText(mEthnicity);
        Glide.with(this).load(mProfilePicture).centerCrop().listener(mImageLoaderListener).into(mProfilePictureView);
    }

    private void feedFakeData() {
        Uri path = Uri.parse("android.resource://org.hopestarter.wallet_test/" + R.drawable.test_image);
        Uri path2 = Uri.parse("android.resource://org.hopestarter.wallet_test/" + R.drawable.test_image2);

        UpdateInfo info = new UpdateInfo.Builder()
                .setUserName("Muhammad Erbil")
                .setEthnicity("Syrian")
                .setLocation("Samothrace, Greece")
                .setUpdateDateMillis(System.currentTimeMillis() - 4 * 60 * 1000)
                .setPictureUri(path)
                .setMessage("The island we landed on was called Samothrace. We were so thankful to be there. We thought we'd reached safety.")
                .setUpdateViews(344)
                .build();

        UpdateInfo info2 = new UpdateInfo.Builder()
                .setUserName("Muhammad Erbil")
                .setEthnicity("Syrian")
                .setLocation("Samothrace, Greece")
                .setUpdateDateMillis(System.currentTimeMillis() - 16 * 60 * 1000)
                .setPictureUri(path2)
                .setMessage("Syrian refugees are freezing to death as snow covers the region.")
                .setUpdateViews(688)
                .build();

        mUpdatesFragment.addAll(new UpdateInfo[]{info, info2});

        NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(Locale.US);
        mProfileDonations.setText(currencyFormat.format(46.30));
    }

    private void launchCreateNewUpdateActivity() {
        Intent activityIntent = new Intent(getActivity(), CreateNewUpdateActivity.class);
        startActivityForResult(activityIntent, POST_UPDATE_REQ_CODE);
    }

    private void updateNumberOfUpdates() {
        mNumUpdates.setText(String.format(Locale.US, "%d", mUpdatesFragment.getNumberOfUpdates()));
    }

    @Override
    public void onActivityResult(int reqCode, int resCode, Intent data) {
        switch(reqCode) {
            case POST_UPDATE_REQ_CODE:
                if (resCode == Activity.RESULT_OK) {
                    final ProgressDialog dialog = ProgressDialog.show(getActivity(), "Uploading update", "Please wait..", true, false);
                    final UpdateInfo update = new UpdateInfo.Builder()
                            .setUserName(mFullName)
                            .setPictureUri(data.getData())
                            .setProfilePictureUri(mProfilePicture)
                            .setMessage(data.getStringExtra(CreateNewUpdateActivity.EXTRA_RESULT_MESSAGE))
                            .setUpdateDateMillis(System.currentTimeMillis())
                            .setEthnicity(mEthnicity)
                            .setLocation("Unknown")
                            .build();

                    LocationMarkUploader uploader = new LocationMarkUploader(getActivity());
                    uploader.setListener(new LocationMarkUploader.UploaderListener() {
                        @Override
                        public void onUploadCompleted(Exception ex) {
                            dialog.dismiss();
                            if (ex == null) {
                                mUpdatesFragment.add(update);
                                updateNumberOfUpdates();
                            } else {
                                log.error("Couldn't upload location marker", ex);
                                AlertDialog errorDialog = new AlertDialog.Builder(getActivity())
                                        .setIcon(R.drawable.ic_error_24dp)
                                        .setTitle("A problem just happened")
                                        .setMessage("Couldn't send update to server")
                                        .create();
                                errorDialog.show();
                            }
                        }
                    });

                    uploader.execute(update);
                }
                break;
            default:
                super.onActivityResult(reqCode, resCode, data);
        }
    }

    public static class LocationMarkUploader extends AsyncTask<UpdateInfo, Integer, Exception> {
        public interface UploaderListener {
            // If this method is called and ex == null everything went fine
            void onUploadCompleted(Exception ex);
        }

        private Activity mContext;
        private UploaderListener mListener;

        public LocationMarkUploader(@NonNull Activity context) {
            mContext = context;
        }

        @Override
        protected Exception doInBackground(UpdateInfo... params) {
            ServerApi serverApi = new ServerApi(mContext);

            for(UpdateInfo updateInfo : params) {
                try {
                    URI pictureUri = null;

                    if (updateInfo.getPictureUri() != null) {
                        UploadImageResponse uploadInfo = serverApi.requestImageUpload();

                        AmazonS3 amazonClient = new AmazonS3Client(uploadInfo.getCredentials());

                        Region region = Region.getRegion(Regions.fromName(uploadInfo.getBucket().getRegion()));
                        amazonClient.setRegion(region);

                        TransferUtility transferUtility = new TransferUtility(amazonClient, mContext);

                        BucketInfo bucket = uploadInfo.getBucket();

                        File pictureFile = new File(updateInfo.getPictureUri().getPath());

                        StringBuilder uriBuilder = new StringBuilder();

                        StringBuilder keyBuilder = new StringBuilder();

                        UUID fileUUID = UUID.randomUUID();

                        keyBuilder.append(bucket.getPrefix()).append(fileUUID.toString()).append(pictureFile.getName());

                        uriBuilder.append("s3://")
                                .append(bucket.getName()).append("/")
                                .append(keyBuilder.toString());

                        URI s3PictureUri = URI.create(uriBuilder.toString());

                        final AtomicBoolean taskFinished = new AtomicBoolean(false);

                        TransferObserver observer = transferUtility.upload(bucket.getName(), keyBuilder.toString(), pictureFile);
                        observer.setTransferListener(new TransferListener() {
                            @Override
                            public void onStateChanged(int id, TransferState state) {
                                if (state.equals(TransferState.FAILED) ||
                                        state.equals(TransferState.CANCELED) ||
                                        state.equals(TransferState.COMPLETED)) {
                                    synchronized (taskFinished) {
                                        taskFinished.set(true);
                                        taskFinished.notify();
                                    }
                                }
                            }

                            @Override
                            public void onProgressChanged(int id, long bytesCurrent, long bytesTotal) {

                            }

                            @Override
                            public void onError(int id, Exception ex) {
                                log.error("An error has occurred while uploading picture to S3", ex);
                            }
                        });

                        synchronized (taskFinished) {
                            while (!taskFinished.get()) {
                                taskFinished.wait();
                            }
                        }

                        observer.cleanTransferListener();

                        TransferState transferState = observer.getState();

                        if (transferState == TransferState.COMPLETED) {
                            pictureUri = s3PictureUri;
                        }
                    }

                    Date updateDate = new Date(updateInfo.getUpdateDateMillis());
                    Point point = new Point("point", new float[] {1000, 1000});

                    ArrayList<URI> pictures = null;

                    if (pictureUri != null) {
                        pictures = new ArrayList<>();
                        pictures.add(pictureUri);
                    }

                    OutboundLocationMark locationMark = new OutboundLocationMark(updateDate, point, pictures, updateInfo.getMessage());
                    serverApi.uploadLocationMark(locationMark);
                } catch (Exception e) {
                    return e;
                }
            }

            return null; // Everything went well :)
        }

        @Override
        protected void onProgressUpdate(Integer... values) {

        }

        @Override
        protected void onPostExecute(Exception e) {
            if (mListener != null) {
                mListener.onUploadCompleted(e);
            }
        }

        public void setListener(@NonNull UploaderListener listener) {
            mListener = listener;
        }

        public void removeListener() {
            mListener = null;
        }
    }
}
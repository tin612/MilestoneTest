package com.hasbrain.milestonetest;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import com.facebook.AccessToken;
import com.facebook.CallbackManager;
import com.facebook.FacebookCallback;
import com.facebook.FacebookException;
import com.facebook.GraphRequest;
import com.facebook.GraphResponse;
import com.facebook.HttpMethod;
import com.facebook.login.LoginManager;
import com.facebook.login.LoginResult;
import com.hasbrain.milestonetest.model.FacebookImage;
import com.hasbrain.milestonetest.model.FacebookPhotoResponse;
import com.hasbrain.milestonetest.model.MarshMallowPermission;
import com.hasbrain.milestonetest.model.converter.FacebookImageDeserializer;
import com.hasbrain.milestonetest.model.converter.FacebookPhotoResponseDeserializer;
import com.squareup.picasso.Picasso;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;

import android.graphics.BitmapFactory;
import android.net.Uri;

import android.graphics.drawable.Drawable;

import android.os.Bundle;
import android.os.Handler;
import android.provider.MediaStore;
import android.support.annotation.StringDef;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import android.widget.AbsListView;

import android.widget.Button;

import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import butterknife.Bind;
import butterknife.ButterKnife;


public class MainActivity extends AppCompatActivity {

    public static final String TYPE_UPLOADED = "uploaded";
    public static final String TYPE_TAGGED = "tagged";
    public static final String PUBLISH_ACTIONS_PERMISSION = "publish_actions";
    private static final int REQUEST_IMAGE = 0x1;

    private static final String TAG = "MainActivity";

    @Bind(R.id.rv_photos)
    RecyclerView rvPhotos;
    @Bind(R.id.swipe_refresh_layout)
    SwipeRefreshLayout swipeRefreshLayout;
    @Bind(R.id.fab)
    FloatingActionButton floatingActionButton;
    private Gson gson;
    private CallbackManager callbackManager;
    private FacebookPhotoResponse facebookPhotoResponse;
    MarshMallowPermission permission = new MarshMallowPermission(this);
    private int visibleItemCount;
    private int totalItemCount;
    private int fistVisibleItem;
    private boolean loading = true;
    private List<FacebookImage> list = new ArrayList<>();
    private FacebookImageAdapter adapter;
    private LinearLayoutManager linearLayoutManager;
    private String strAfter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setTitle("Your facebook photos");
        ButterKnife.bind(this);
        rvPhotos.setHasFixedSize(true);
        linearLayoutManager = new LinearLayoutManager(this);
        rvPhotos.setLayoutManager(linearLayoutManager);
        scroll(linearLayoutManager);
        swipeRefreshLayout.setEnabled(false);
        floatingActionButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openCameraForImage();
            }
        });

        getUserPhotos(TYPE_UPLOADED, null);
        gson = new GsonBuilder()
                .registerTypeAdapter(FacebookImage.class, new FacebookImageDeserializer())
                .registerTypeAdapter(FacebookPhotoResponse.class, new FacebookPhotoResponseDeserializer())
                .create();
    }
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu items for use in the action bar
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override

    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle presses on the action bar items
        switch (item.getItemId()) {
            case R.id.action_bookmark:
                Intent book = new Intent(MainActivity.this,BookMark.class);

                startActivity(book);
                return true;
            case R.id.action_signout:
                LoginManager.getInstance().logOut();
                Intent splash = new Intent(MainActivity.this,SplashActivity.class);
                startActivity(splash);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override

    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (REQUEST_IMAGE == requestCode && resultCode == RESULT_OK) {
            Bitmap bitmapData = data.getParcelableExtra("data");
            if (bitmapData != null) {
                Uri tempUri = getImageUri(MainActivity.this, bitmapData);
                String res = getRealPathFromUri(tempUri);
                bitmapData = decodeSampleBitmapFromFile(res);
                uploadPhotoToFacebook(bitmapData);
            }
        } else {
            if (callbackManager != null) {
                callbackManager.onActivityResult(requestCode, resultCode, data);
            }

        }
    }

    // TODO: decode Bitmap from File
    public Bitmap decodeSampleBitmapFromFile(String res) {
        // The first decode with inJustDecodeBounds = true to check dimensions
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(res, options);

        options.inSampleSize = calculateInSampleSize(options, 500, 500);
        options.inScaled = false;
        options.inDither = false;
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;

        // Decode bitmap with inSampleSize set
        options.inJustDecodeBounds = false;
        return BitmapFactory.decodeFile(res, options);
    }

    public static int calculateInSampleSize(
            BitmapFactory.Options options, int reqWidth, int reqHeight) {
        // Raw height and width of image
        final int height = options.outHeight;
        final int width = options.outWidth;

        Log.d(TAG, "calculateInSampleSize: " + height + width);
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {

            final int halfHeight = height / 2;
            final int halfWidth = width / 2;

            // Calculate the largest inSampleSize value that is a power of 2 and keeps both
            // height and width larger than the requested height and width.
            while ((halfHeight / inSampleSize) > reqHeight
                    && (halfWidth / inSampleSize) > reqWidth) {
                inSampleSize *= 2;
            }
        }

        return inSampleSize;
    }

    // TODO: Get the actual Path
    private String getRealPathFromUri(Uri tempUri) {
        String result = null;
        Cursor cursor = getContentResolver().query(tempUri, null, null, null, null);
        if (cursor != null) {
            cursor.moveToFirst();
            int idx = cursor.getColumnIndex(MediaStore.Images.ImageColumns.DATA);
            result = cursor.getString(idx);
            cursor.close();
        }
        return result;
    }

    // TODO: Get the Uri from the Bitmap
    private Uri getImageUri(Context applicationContext, Bitmap thumbnail) {
//        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
//        thumbnail.compress(Bitmap.CompressFormat.JPEG, 100, bytes);
        String path = MediaStore.Images.Media.insertImage(applicationContext.getContentResolver(), thumbnail, "Title", null);

        return Uri.parse(path);
    }

    private void openCameraForImage() {
        if (!permission.checkPermissionForCamera()) {
            permission.requestPermissionForCamera();
        } else {
            if (!permission.checkPermissionForExternalStorage())
                permission.requestPermissionForExternalStorage();
            else {
                Intent openCameraForImageIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                startActivityForResult(openCameraForImageIntent, REQUEST_IMAGE);
            }
        }
    }

    private void uploadPhotoToFacebook(final Bitmap imageBitmap) {
        AccessToken currentAccessToken = AccessToken.getCurrentAccessToken();
        if (currentAccessToken.getPermissions().contains(PUBLISH_ACTIONS_PERMISSION)) {
            doUploadPhotoToFacebook(imageBitmap, currentAccessToken);
        } else {
            callbackManager = CallbackManager.Factory.create();
            LoginManager loginManager = LoginManager.getInstance();
            loginManager.registerCallback(callbackManager, new FacebookCallback<LoginResult>() {
                @Override
                public void onSuccess(LoginResult loginResult) {
                    if (loginResult.getRecentlyGrantedPermissions().contains(PUBLISH_ACTIONS_PERMISSION)) {
                        doUploadPhotoToFacebook(imageBitmap, loginResult.getAccessToken());
                    }
                }

                @Override
                public void onCancel() {

                }

                @Override
                public void onError(FacebookException error) {

                }
            });
            loginManager.logInWithPublishPermissions(this, Collections.singletonList(PUBLISH_ACTIONS_PERMISSION));

        }
    }

    private void doUploadPhotoToFacebook(Bitmap imageFile, AccessToken currentAccessToken) {
        GraphRequest graphRequest = GraphRequest
                .newUploadPhotoRequest(currentAccessToken, "me/photos", imageFile,
                        "Upload from hasBrain Milestone test", null, new GraphRequest.Callback() {
                            @Override
                            public void onCompleted(GraphResponse response) {
                                if (response.getError() != null) {
                                    Toast.makeText(MainActivity.this, "Image upload error " + response.getError().getErrorMessage(), Toast.LENGTH_LONG).show();
                                } else {
                                    Toast.makeText(MainActivity.this, "Upload image success", Toast.LENGTH_LONG).show();
                                    getUserPhotos(TYPE_UPLOADED, null);
                                }
                            }
                        });
        graphRequest.executeAsync();
    }

    private void getUserPhotos(@PHOTO_TYPE String photoType, final String after) {

        swipeRefreshLayout.setRefreshing(true);

        AccessToken accessToken = AccessToken.getCurrentAccessToken();
        Bundle parameters = new Bundle();
        parameters.putString("fields", "id,name,images,picture,created_time,width,height");
        parameters.putString("type", photoType);
        if (after != null) {
            parameters.putString("after", after);
        }
        GraphRequest graphRequest = new GraphRequest(accessToken, "me/photos", parameters, HttpMethod.GET);
        graphRequest.setCallback(new GraphRequest.Callback() {
            @Override
            public void onCompleted(GraphResponse response) {
                Log.d("hasBrain", "Graph response " + response.toString());
                FacebookPhotoResponse facebookPhotoResponse = gson
                        .fromJson(response.getRawResponse(), FacebookPhotoResponse.class);

                list = facebookPhotoResponse.getData();
                strAfter = facebookPhotoResponse.getAfter();
                displayPhotos(list);

            }
        });
        graphRequest.executeAsync();

        //TODO: Bug 2
        swipeRefreshLayout.setRefreshing(false);
    }

    private void displayPhotos(List<FacebookImage> data) {
        adapter = new FacebookImageAdapter(getLayoutInflater(), Picasso.with(this), data);
        rvPhotos.setAdapter(adapter);
    }

    @StringDef({TYPE_UPLOADED, TYPE_TAGGED})
    public @interface PHOTO_TYPE {

    }

    public void scroll(final LinearLayoutManager manager) {

        rvPhotos.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);

                if (newState == AbsListView.OnScrollListener.SCROLL_STATE_TOUCH_SCROLL) {
                    loading = true;
                }
            }

            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);

                visibleItemCount = manager.getChildCount();
                totalItemCount = manager.getItemCount();
                fistVisibleItem = manager.findFirstVisibleItemPosition();

                if (fistVisibleItem == 0) { // Pull to refresh
                    swipeRefreshLayout.setEnabled(true);
                    swipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
                        @Override
                        public void onRefresh() {
                            swipeRefreshLayout.setRefreshing(true);

                            new Handler().postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    //Load data
                                    getUserPhotos(TYPE_UPLOADED, null);
                                }
                            }, 3000);

                        }
                    });
                } else { // Scroll content
                    swipeRefreshLayout.setEnabled(false);
                }

                if (loading && (visibleItemCount + fistVisibleItem) == totalItemCount) { // LoadMore
                    if (totalItemCount % 25 == 0) { // Check for condition to show LoadMore
                        loading = false;
                        list.add(null);
                        adapter.notifyItemInserted(list.size());
                        new Handler().postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                // Load more
                                getUserPhotos(TYPE_UPLOADED, strAfter);

                            }
                        }, 3000);
                    }
                }
            }
        });

    }

    static class FacebookImageVH extends RecyclerView.ViewHolder {
        private boolean clicked = false;
        @Bind(R.id.iv_facebook_photo)
        ImageView ivFacebookPhoto;
        @Bind(R.id.tv_image_name)
        TextView tvImageName;
        @Bind(R.id.tv_image_time)
        TextView tvImageTime;
        @Bind(R.id.resTextView)
        TextView rsTextView;
        private Picasso picasso;
        @Bind(R.id.bookmarkBtn)
        Button bookmark;
        public FacebookImageVH(Picasso picasso, View itemView) {
            super(itemView);
            this.picasso = picasso;
            ButterKnife.bind(this, itemView);
            bookmark.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (clicked) {
                        bookmark.setBackgroundResource(R.drawable.unbook);
                        clicked = false;
                    } else {
                        bookmark.setBackgroundResource(R.drawable.booked);
                        clicked = true;
                    }

                }
            });
        }

        public void bind(FacebookImage facebookImage) {
            picasso.load(facebookImage.getImageUrl()).resize(1024, 1024).into(ivFacebookPhoto);
            tvImageName.setText(facebookImage.getName());
            tvImageTime.setText(facebookImage.getCreatedTime());
            rsTextView.setText("Resolution " + String.valueOf(facebookImage.getWidth()) + "px x " + String.valueOf(facebookImage.getHeight())+"px");
        }
    }

    public static class ProgressVH extends RecyclerView.ViewHolder {
        @Bind(R.id.progressBar)
        ProgressBar progressBar;

        public ProgressVH(View itemView) {
            super(itemView);
            ButterKnife.bind(this, itemView);
        }

        public void bind() {
            progressBar.setIndeterminate(true);
        }
    }

    private static class FacebookImageAdapter extends RecyclerView.Adapter {

        private LayoutInflater layoutInflater;
        private Picasso picasso;
        private List<FacebookImage> facebookImages;
        private final int VIEW_ITEM = 1;
        private final int VIEW_PROG = 0;

        public FacebookImageAdapter(LayoutInflater layoutInflater, Picasso picasso,
                                    List<FacebookImage> facebookImages) {
            this.layoutInflater = layoutInflater;
            this.picasso = picasso;
            this.facebookImages = facebookImages;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            RecyclerView.ViewHolder vh;

            // Return a new holder instance
            if (viewType == VIEW_ITEM) {
                // Inflate the custom layout
                View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_facebook_photo, parent, false);

                vh = new FacebookImageVH(picasso, v);
            } else {
                // Inflate the custom layout
                View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.progressbar_item, parent, false);

                vh = new ProgressVH(v);
            }

            return vh;
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            if (holder instanceof FacebookImageVH) {
                ((FacebookImageVH) holder).bind(facebookImages.get(position));
            } else {
                ((ProgressVH) holder).bind();
            }
        }

        @Override
        public int getItemCount() {
            return facebookImages != null ? facebookImages.size() : 0;
        }

        @Override
        public int getItemViewType(int position) {
            return facebookImages.get(position) != null ? VIEW_ITEM : VIEW_PROG;
        }
    }
}

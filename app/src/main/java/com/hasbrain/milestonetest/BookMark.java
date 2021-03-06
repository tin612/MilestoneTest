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
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
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
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Collections;
import java.util.List;

import butterknife.Bind;
import butterknife.ButterKnife;


public class BookMark extends AppCompatActivity implements SwipeRefreshLayout.OnRefreshListener {

    public static final String TYPE_UPLOADED = "uploaded";
    public static final String TYPE_TAGGED = "tagged";
    public static final String PUBLISH_ACTIONS_PERMISSION = "publish_actions";
    private static final int REQUEST_IMAGE = 0x1;


    @Bind(R.id.rv_photos)
    RecyclerView rvPhotos;
    @Bind(R.id.swipe_refresh_layout)
    SwipeRefreshLayout swipeRefreshLayout;
    @Bind(R.id.fab)
    FloatingActionButton floatingActionButton;
    private Gson gson;
    private CallbackManager callbackManager;
    MarshMallowPermission permission = new MarshMallowPermission(this);
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setTitle("Your facebook photos");
        ButterKnife.bind(this);
        swipeRefreshLayout.setOnRefreshListener(this);
        rvPhotos.setLayoutManager(new LinearLayoutManager(this));


        getUserPhotos(TYPE_UPLOADED, null);
        gson = new GsonBuilder()
                .registerTypeAdapter(FacebookImage.class, new FacebookImageDeserializer())
                .registerTypeAdapter(FacebookPhotoResponse.class, new FacebookPhotoResponseDeserializer())
                .create();
    }

    @Override
    public void onRefresh() {
        getUserPhotos(TYPE_UPLOADED, null);
    }


    private void getUserPhotos(@PHOTO_TYPE String photoType, final String after) {
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
                displayPhotos(facebookPhotoResponse.getData());
            }
        });
        graphRequest.executeAsync();
    }

    private void displayPhotos(List<FacebookImage> data) {
        rvPhotos.setAdapter(new FacebookImageAdapter(getLayoutInflater(), Picasso.with(this), data));
    }

    @StringDef({TYPE_UPLOADED, TYPE_TAGGED})
    public @interface PHOTO_TYPE {

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
            picasso.load(facebookImage.getImageUrl()).into(ivFacebookPhoto);
            tvImageName.setText(facebookImage.getName());
            tvImageTime.setText(facebookImage.getCreatedTime());
            rsTextView.setText("Resolution " + String.valueOf(facebookImage.getWidth()) + "px x " + String.valueOf(facebookImage.getHeight())+"px");
        }
    }

    private static class FacebookImageAdapter extends RecyclerView.Adapter<FacebookImageVH> {

        private LayoutInflater layoutInflater;
        private Picasso picasso;
        private List<FacebookImage> facebookImages;

        public FacebookImageAdapter(LayoutInflater layoutInflater, Picasso picasso,
                                    List<FacebookImage> facebookImages) {
            this.layoutInflater = layoutInflater;
            this.picasso = picasso;
            this.facebookImages = facebookImages;
        }

        @Override
        public FacebookImageVH onCreateViewHolder(ViewGroup parent, int viewType) {
            View itemView = layoutInflater.inflate(R.layout.item_facebook_photo, parent, false);
            return new FacebookImageVH(picasso, itemView);
        }

        @Override
        public void onBindViewHolder(FacebookImageVH holder, int position) {
            holder.bind(facebookImages.get(position));
        }

        @Override
        public int getItemCount() {
            return facebookImages != null ? facebookImages.size() : 0;
        }
    }
}

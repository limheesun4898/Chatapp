package com.example.user.chatapp;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.renderscript.ScriptGroup;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.RecyclerView.AdapterDataObserver;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.firebase.ui.database.FirebaseRecyclerAdapter;
import com.firebase.ui.database.FirebaseRecyclerOptions;
import com.google.android.gms.appinvite.AppInviteInvitation;
import com.google.android.gms.auth.api.Auth;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;

import de.hdodenhof.circleimageview.CircleImageView;

public class MainActivity extends AppCompatActivity implements GoogleApiClient.OnConnectionFailedListener {
    private FirebaseAuth mFirebaseAuth;
    private FirebaseUser mFirebaseUser;
    //사용자 이름과 사진
    private String mUsername;
    private String mPhotoUrl;

    LinearLayout mainlayout;

    private GoogleApiClient mGoogleApiClicent;
    private DatabaseReference mFirebaseDatabaseReference;
    private EditText mMessageEditText;
    public static final String MESSAGE_CHILD = "messages";
    private FirebaseRecyclerAdapter<ChatMessage, MessageViewHolder> mFirebaseAdapter;
    //앱 초대
    private static final int REQUEST_INVITE=1000;

    public static class MessageViewHolder extends RecyclerView.ViewHolder {
        TextView messageTextView;
        ImageView messageImageView;
        TextView messengerTextView;
        CircleImageView messengerImageView;

        public MessageViewHolder(View itemView) {
            super(itemView);
            messageTextView = itemView.findViewById(R.id.messageTextView);
            messageImageView = itemView.findViewById(R.id.messageImageView);
            messengerTextView = itemView.findViewById(R.id.messengerTextView);
            messengerImageView = itemView.findViewById(R.id.messengerImageView);
        }
    }

    private RecyclerView mMessageRecyclerView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mMessageRecyclerView = findViewById(R.id.message_recycler_view);

        mFirebaseAuth = FirebaseAuth.getInstance();
        mFirebaseUser = mFirebaseAuth.getCurrentUser();

        mFirebaseDatabaseReference = FirebaseDatabase.getInstance().getReference();
        mMessageEditText = findViewById(R.id.message_edit);

        mainlayout = (LinearLayout)findViewById(R.id.mainlayout);
        mainlayout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(mMessageEditText.getWindowToken(),0);
            }
        });

        if (mFirebaseUser == null) {
            startActivity(new Intent(this, SignInActivity.class));
            finish();
            return;
        } else {
            mUsername = mFirebaseUser.getDisplayName();
            if (mFirebaseUser.getPhotoUrl() != null) {
                mPhotoUrl = mFirebaseUser.getPhotoUrl().toString();
            }
        }

        mGoogleApiClicent = new GoogleApiClient.Builder(this)
                .enableAutoManage(this, this)
                .addApi(Auth.GOOGLE_SIGN_IN_API)
                .build();

        findViewById(R.id.send_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mMessageEditText.getText().toString()==null) {
                    Toast.makeText(MainActivity.this, "입력하세요.", Toast.LENGTH_SHORT).show();
                }else {
                    ChatMessage chatMessage = new
                            ChatMessage(mMessageEditText.getText().toString(), mUsername, mPhotoUrl, null);
                    mFirebaseDatabaseReference.child(MESSAGE_CHILD)
                            .push().setValue(chatMessage);
                    mMessageEditText.setText("");
                }
            }
        });

        Query query = mFirebaseDatabaseReference.child(MESSAGE_CHILD);
        FirebaseRecyclerOptions<ChatMessage> options =
                new FirebaseRecyclerOptions.Builder<ChatMessage>()
                        .setQuery(query, ChatMessage.class)
                        .build();

        mFirebaseAdapter = new FirebaseRecyclerAdapter<ChatMessage, MessageViewHolder>(options) {
            @Override
            protected void onBindViewHolder(MessageViewHolder holder, int position, ChatMessage model) {
                holder.messageTextView.setText(model.getText());
                holder.messengerTextView.setText(model.getName());

                if (model.getPhotoUrl() == null) {
                    holder.messengerImageView.setImageDrawable(ContextCompat.getDrawable
                            (MainActivity.this, R.drawable.ic_account_circle_black_24dp));
                } else {
                    Glide.with(MainActivity.this)
                            .load(model.getPhotoUrl())
                            .into(holder.messengerImageView);
                }
            }

            @Override
            public MessageViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
                View view = LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.item_message, parent, false);
                return new MessageViewHolder(view);
            }
        };
        mMessageRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        mMessageRecyclerView.setAdapter(mFirebaseAdapter);
    }

    @Override
    protected void onStart() {
        super.onStart();
        mFirebaseAdapter.startListening();
    }

    @Override
    protected void onStop() {
        super.onStop();
        mFirebaseAdapter.stopListening();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.sign_out_menu:
                mFirebaseAuth.signOut();
                Auth.GoogleSignInApi.signOut(mGoogleApiClicent);
                mUsername = "";
                startActivity(new Intent(this, SignInActivity.class));
                finish();
                return true;
            case R.id.invitation_menu:
                sendInvitation();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

    }
    private void sendInvitation(){
        Intent intent = new AppInviteInvitation.IntentBuilder("초대 제목")
                .setMessage("채팅앱에 초대합니다.")
                .setCallToActionText("채팅에 참여하기")
                .build();
        startActivityForResult(intent, REQUEST_INVITE);
    }
}

package vn.something.barberfinal.ui.fragments;

import static android.content.Context.MODE_PRIVATE;

import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.facebook.GraphRequest;
import com.facebook.GraphResponse;
import com.facebook.HttpMethod;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.FieldPath;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import vn.something.barberfinal.BookingDetail;
import vn.something.barberfinal.DataModel.Appointment;
import vn.something.barberfinal.R;
import vn.something.barberfinal.adapter.CardAdapterBooking;
import vn.something.barberfinal.services.QrCodeServices;
import vn.something.barberfinal.services.ServerService;

public class PendingFragment extends Fragment implements CardAdapterBooking.OnItemClickListener{
    private RecyclerView recyclerView;
    private CardAdapterBooking cardAdapter;
    private TextView emptyText;
    private List<Appointment> dataList = new ArrayList<>();

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {

        View root = inflater.inflate(R.layout.pending_fragment_tab, container, false);

        emptyText = root.findViewById(R.id.empty_text);
        recyclerView = root.findViewById(R.id.recyclerViewBookingCard);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        emptyText.setVisibility(View.VISIBLE);
        recyclerView.setVisibility(View.GONE);
        getAppointments();
        final SwipeRefreshLayout pullToRefresh = root.findViewById(R.id.pullToRefresh);
        pullToRefresh.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                getAppointments();
                pullToRefresh.setRefreshing(false);
            }
        });

        return root;
    }

    private void getAppointments(){
        dataList = new ArrayList<>();
        String shopId = getActivity().getSharedPreferences("ShopPrefs", 0).getString("shopId",null);
        CollectionReference appointmentsRef = FirebaseFirestore.getInstance().collection("shops").document(shopId).collection("appointments");

        appointmentsRef.whereEqualTo("status","PENDING").get().addOnSuccessListener(queryDocumentSnapshots -> {
            for (QueryDocumentSnapshot item: queryDocumentSnapshots
                 ) {
                Appointment appointment = item.toObject(Appointment.class);
                appointment.setAppointmentId(item.getId());
                dataList.add(appointment);

                emptyText.setVisibility(View.GONE);
                recyclerView.setVisibility(View.VISIBLE);
            }
            prepareCardView();
        }).addOnFailureListener(onfailuer -> {
            if(dataList == null || dataList.isEmpty()){
                emptyText.setVisibility(View.VISIBLE);
                recyclerView.setVisibility(View.GONE);
            }

        });
    }
    private void prepareCardView(){
        cardAdapter = new CardAdapterBooking(dataList, this);
        recyclerView.setAdapter(cardAdapter);
        ItemTouchHelper itemTouchHelper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {
            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                return false;
            }
            @Override
            public int getMovementFlags(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
                return makeMovementFlags(0, ItemTouchHelper.RIGHT);
            }
            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                int position = viewHolder.getAdapterPosition();
                Appointment item = dataList.get(position);

                if (direction == ItemTouchHelper.RIGHT ) {
                    dataList.remove(position);
                    cardAdapter.notifyItemRemoved(position);
                    Toast.makeText(getContext(), "Đã chuyển lịch hẹn : " + item.getShortId(), Toast.LENGTH_SHORT).show();
                    //push data to firebase or some shit, change swipe interface with button because conflict with tab view
                    // save to local db first then push to firebase later
                    item.setStatus("UPCOMING");

                    String shopId = getActivity().getSharedPreferences("ShopPrefs", 0).getString("shopId",null);
                    CollectionReference appointmentsRef = FirebaseFirestore.getInstance().collection("shops").document(shopId).collection("appointments");

                    appointmentsRef.document(item.getAppointmentId()).set(item);

                }
            }
        });

        // Attach the ItemTouchHelper to the RecyclerView
        itemTouchHelper.attachToRecyclerView(recyclerView);
    }
    @Override
    public void onItemClick(int position) {
        Appointment clickedItem = dataList.get(position);

        Intent intent = new Intent(getContext(), BookingDetail.class);
        intent.putExtra("item", clickedItem);
        startActivity(intent);
    }

    @Override
    public void onDeclineClick(int position) {
        Appointment item = dataList.get(position);
        dataList.remove(position);
        cardAdapter.notifyItemRemoved(position);
        item.setStatus("CANCELLED");
        String shopId = getActivity().getSharedPreferences("ShopPrefs", 0).getString("shopId",null);
        CollectionReference appointmentsRef = FirebaseFirestore.getInstance().collection("shops").document(shopId).collection("appointments");

        appointmentsRef.document(item.getAppointmentId()).set(item);

    }
    public void handlerDrawback() throws Exception {
        uploadImage(null);
    }
    @Override
    public void onAcceptClick(int position) {
        Appointment item = dataList.get(position);
        dataList.remove(position);
        cardAdapter.notifyItemRemoved(position);
        Toast.makeText(getContext(), "Đã chuyển lịch hẹn: " + item.getShortId(), Toast.LENGTH_SHORT).show();

        item.setStatus("UPCOMING");

        String shopId = getActivity().getSharedPreferences("ShopPrefs", 0).getString("shopId",null);
        CollectionReference appointmentsRef = FirebaseFirestore.getInstance().collection("shops").document(shopId).collection("appointments");

        appointmentsRef.document(item.getAppointmentId()).set(item);
        String message = new StringBuilder("Quán đã xác nhận lịch hẹn. \n").append("Bạn đến quán vào ngày ")
                        .append(item.getDate()).append(" lúc ")
                        .append(item.getTime()).append("\n")
                        .append("ID: \n").append("    ").append(item.getShortId()).toString();
        sendMessageToUser(item.getMessengerUserId(), message );
        //appointment id to qr code bitmap
        String apid = item.getAppointmentId();
        ServerService.sendQRConfirm(shopId,item.getMessengerUserId(),apid);


    }

    public void sendMessageToUser(String psid, String message) {
        String pageAccessToken = getContext().getSharedPreferences("ShopPrefs", MODE_PRIVATE).getString("shopPageToken", null);
        String pageId = getContext().getSharedPreferences("ShopPrefs", MODE_PRIVATE).getString("shopId", null);
        if (pageAccessToken == null) {
            Log.e("MessengerAPI", "Page access token is null.");
            return;
        }
        String url = "/" + pageId + "/messages";
        Bundle params = new Bundle();
        params.putString("access_token", pageAccessToken);
        JSONObject recipientObject = new JSONObject();
        JSONObject messageObject = new JSONObject();
        try {
            recipientObject.put("id", psid);
            messageObject.put("text", message);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        params.putString("recipient", recipientObject.toString());
        params.putString("message", messageObject.toString());
        GraphRequest request = new GraphRequest(
                null,
                url,
                params,
                HttpMethod.POST,
                new GraphRequest.Callback() {
                    @Override
                    public void onCompleted(GraphResponse response) {
                        if (response.getError() != null) {
                            Log.e("MessengerAPI", "Error: " + response.getError().getErrorMessage());
                        } else {
                            Log.d("MessengerAPI", "Message sent successfully!");
                        }
                    }
                });

        request.executeAsync();
    }
    private static String uploadImage(Bitmap bitmap) throws Exception{
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, baos);
        byte[] imageBytes = baos.toByteArray();

        GraphRequest request = GraphRequest.newPostRequest(
                null,
                "/me/message_attachments",
                new JSONObject("{\"message\": {\"attachment\": {\"type\":\"image\", \"payload\":{}}}}"),
                new GraphRequest.Callback(){
                    @Override
                    public void onCompleted(GraphResponse response) {
                        try {
                            if (response.getError() == null) {
                                JSONObject data = response.getJSONObject();
                                String attachmentId = data.getString("attachment_id");
                                Log.d("TAG", "Image Upload success! attachment_id: "+ attachmentId);

                            } else {
                                Log.e("TAG", "Image Upload Error! " + response.getError().getErrorMessage());
                                throw new Exception("Image Upload Error! " + response.getError().getErrorMessage());
                            }
                        } catch (Exception e){
                            Log.e("TAG", "Image Upload Error! "+ e.getMessage());
                        }
                    }
                });
        Bundle params = new Bundle();
        params.putByteArray("filedata", imageBytes);
        request.setParameters(params);
        GraphResponse response = request.executeAndWait();
        if (response.getError() == null) {
            JSONObject data = response.getJSONObject();
            return data.getString("attachment_id");
        } else {
            Log.e("TAG", "Image Upload Error! "+ response.getError().getErrorMessage());
            throw new Exception("Image Upload Error! " + response.getError().getErrorMessage());
        }
    }

    private static void sendMessengerMessage(String imageUrl, String recipientId) {
        JSONObject messagePayload = new JSONObject();
        JSONObject attachmentPayload = new JSONObject();
        try{
            attachmentPayload.put("type", "image");
            attachmentPayload.put("payload", new JSONObject().put("attachment_id", imageUrl));
            messagePayload.put("message", new JSONObject().put("attachment", attachmentPayload));
            messagePayload.put("recipient", new JSONObject().put("id", recipientId));
        } catch(Exception e){
            Log.e("TAG", "Error creating payload "+e.getMessage());
            return;
        }

        GraphRequest request = new GraphRequest(
                null,
                "/me/messages",
                null,
                com.facebook.HttpMethod.POST,
                new GraphRequest.Callback() {
                    @Override
                    public void onCompleted(GraphResponse response) {
                        if(response.getError() != null){
                            Log.e("TAG", "Error sending message: "+ response.getError().getErrorMessage());
                        } else{
                            Log.d("TAG", "Message sent successfully!");
                        }

                    }
                });
        request.executeAsync();

    }



}

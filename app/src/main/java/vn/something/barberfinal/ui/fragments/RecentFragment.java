package vn.something.barberfinal.ui.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.SearchView;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import vn.something.barberfinal.BookingDetail;
import vn.something.barberfinal.DataModel.Appointment;
import vn.something.barberfinal.R;
import vn.something.barberfinal.adapter.CardAdapterBooking;
import vn.something.barberfinal.adapter.RecentCardAdapter;

public class RecentFragment extends Fragment implements RecentCardAdapter.OnItemClickListener{
    private RecyclerView recyclerView;
    private RecentCardAdapter cardAdapter;
    private TextView emptyText;
    private List<Appointment> dataList = new ArrayList<>();
    SearchView searchView;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
            ViewGroup container, Bundle savedInstanceState) {

        View root = inflater.inflate(R.layout.recent_fragment_tab, container, false);

        emptyText = root.findViewById(R.id.empty_text);
        recyclerView = root.findViewById(R.id.recyclerViewBookingCardrecent);
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
        searchView = root.findViewById(R.id.search_view);


        return root;
    }
    private void getAppointments(){
        dataList = new ArrayList<>();
        String shopId = getActivity().getSharedPreferences("ShopPrefs", 0).getString("shopId",null);
        CollectionReference appointmentsRef = FirebaseFirestore.getInstance().collection("shops").document(shopId).collection("appointments");

        appointmentsRef.whereIn("status", Arrays.asList("FINISHED", "CANCELLED")).get().addOnSuccessListener(queryDocumentSnapshots -> {
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
        cardAdapter = new RecentCardAdapter(dataList, this);
        recyclerView.setAdapter(cardAdapter);
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {

                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {

                cardAdapter.filter(newText);
                return true;
            }
        });


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

    @Override
    public void onAcceptClick(int position) {

    }
}

package com.mobiled.android.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.mobiled.android.base.comman.ViewUtil;
import com.mobiled.android.base.model.HardwareGroup;
import com.mobiled.android.databinding.ListItemBottomImageBinding;
import com.mobiled.android.databinding.RcvListGroupBinding;
import com.mobiled.android.model.ExpandableListItem;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class RcvExpandableListAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    ArrayList<ExpandableListItem> items = new ArrayList<>();

    private RcvChildListAdapter.AdapterItemListener itemListener;

    public RcvExpandableListAdapter() {
        items.add(new ExpandableListItem("Devices (5)"));
        items.add(new ExpandableListItem("Group (5)"));
    }

    public RcvExpandableListAdapter(ArrayList<ExpandableListItem> items) {
        this.items = items;
    }

    public void bindCallback(RcvChildListAdapter.AdapterItemListener itemListener) {
        this.itemListener = itemListener;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater layoutInflater = LayoutInflater.from(parent.getContext());
        if (viewType == 0) {
            return new GroupItemViewHolder(RcvListGroupBinding.inflate(layoutInflater, parent, false));
        }
        return new ImageItemViewHolder(ListItemBottomImageBinding.inflate(layoutInflater, parent, false).getRoot());
    }


    RecyclerView.ViewHolder expandedHolder = null;
    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int _position) {
        int position = holder.getBindingAdapterPosition();
        if (getItemViewType(position) == 0) {
            ExpandableListItem item = items.get(position);
            RcvListGroupBinding viewBinding = ((GroupItemViewHolder) holder).viewBinding;

            viewBinding.listTitle.setText(item.title + " (" + item.childItems.size() + ")");

            RcvChildListAdapter adapter = new RcvChildListAdapter(item.childItems);
            adapter.bindCallback(itemListener);
            viewBinding.childItems.setLayoutManager(new LinearLayoutManager(holder.itemView.getContext()));
            viewBinding.childItems.setItemViewCacheSize(item.childItems.size());
            viewBinding.childItems.setAdapter(adapter);

            viewBinding.viewRoot.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (viewBinding.childItems.getAdapter().getItemCount() == 0) {
                        if (item.isExpanded) ViewUtil.collapse(viewBinding.childItems);
                        item.isExpanded = false;
                        expandedHolder = null;
                        return;
                    }
                    //clearMainThread(expandedHolder);
                    if (item.isExpanded) {
                        expandedHolder = null;
                        ViewUtil.collapse(viewBinding.childItems);
                    } else {
                        expandedHolder = holder;
                        //adapter.setMainHandler(new Handler(Looper.getMainLooper()));
                        ViewUtil.expand(viewBinding.childItems);
                    }
                    item.isExpanded = !ViewUtil.toggleArrow(viewBinding.ivExpand, item.isExpanded);
                }


            });
        }
    }

    private void clearMainThread(RecyclerView.ViewHolder expandedHolder) {
        if(expandedHolder!=null)
        {
            if(getItemViewType(expandedHolder.getBindingAdapterPosition())==0) {
                RcvListGroupBinding viewBinding = ((GroupItemViewHolder) expandedHolder).viewBinding;
                if(viewBinding.childItems.getAdapter() instanceof RcvChildListAdapter)
                {
                    ((RcvChildListAdapter) viewBinding.childItems.getAdapter()).getMainHandler().removeCallbacksAndMessages(null);
                    ((RcvChildListAdapter) viewBinding.childItems.getAdapter()).setMainHandler(null);
                }

            }
        }
    }

    @Override
    public int getItemViewType(int position) {
        if (position == items.size()) {
            return 1;
        }
        return 0;
    }

    @Override
    public int getItemCount() {
        return items.size() + 1;
    }

    public void setItems(@NotNull ArrayList<ExpandableListItem> groupItems) {
        items = groupItems;
        notifyDataSetChanged();
    }

    public void removeGroup(@NotNull HardwareGroup hardwareGroup) {
        if (items.get(1).childItems != null) {
            List<Object> hardwareGroups = new ArrayList<>();
            for (int i = 0; i < (items.get(1).childItems).size(); i++) {
                HardwareGroup item = (HardwareGroup) items.get(1).childItems.get(i);
                if (item.getRowId() != hardwareGroup.getRowId()) {
                    hardwareGroups.add(item);
                }
            }
            items.get(1).childItems = hardwareGroups;
        }
        notifyDataSetChanged();
    }

    public ArrayList<ExpandableListItem> getItems() {
        return items;
    }

    class GroupItemViewHolder extends RecyclerView.ViewHolder {
        RcvListGroupBinding viewBinding;

        public GroupItemViewHolder(@NonNull RcvListGroupBinding itemView) {
            super(itemView.getRoot());
            viewBinding = itemView;
        }
    }

    class ImageItemViewHolder extends RecyclerView.ViewHolder {
        public ImageItemViewHolder(@NonNull View itemView) {
            super(itemView);
        }
    }


}

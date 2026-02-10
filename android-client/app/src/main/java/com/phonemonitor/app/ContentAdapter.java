package com.phonemonitor.app;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.util.ArrayList;
import java.util.List;

/**
 * 知识库内容列表适配器
 */
public class ContentAdapter extends RecyclerView.Adapter<ContentAdapter.ViewHolder> {

    public interface OnItemClickListener {
        void onClick(ContentItem item, int position);
        void onLongClick(ContentItem item, int position);
        void onFavoriteClick(ContentItem item, int position);
    }

    private List<ContentItem> items = new ArrayList<>();
    private OnItemClickListener listener;

    public ContentAdapter(OnItemClickListener listener) {
        this.listener = listener;
    }

    public void setItems(List<ContentItem> newItems) {
        this.items = newItems;
        notifyDataSetChanged();
    }

    public void removeItem(int position) {
        if (position >= 0 && position < items.size()) {
            items.remove(position);
            notifyItemRemoved(position);
        }
    }

    public void restoreItem(ContentItem item, int position) {
        items.add(position, item);
        notifyItemInserted(position);
    }

    public ContentItem getItem(int position) {
        return items.get(position);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_content, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ContentItem item = items.get(position);

        // Type emoji + title
        String title = item.title != null && !item.title.isEmpty()
                ? item.title : item.getPreview(40);
        holder.tvTitle.setText(item.getTypeEmoji() + " " + title);

        // 如果是链接且标题为空或是 URL，尝试异步获取标题
        if (TitleFetcher.shouldFetchTitle(item.url) && 
            (item.title == null || item.title.isEmpty() || item.title.startsWith("http"))) {
            holder.tvTitle.setText(item.getTypeEmoji() + " 加载中…");
            TitleFetcher.fetch(item.url, new TitleFetcher.Callback() {
                @Override
                public void onSuccess(String fetchedTitle) {
                    item.title = fetchedTitle;
                    holder.tvTitle.setText(item.getTypeEmoji() + " " + fetchedTitle);
                    // 更新数据库
                    new Thread(() -> {
                        android.content.Context ctx = holder.itemView.getContext();
                        KnowledgeDb.getInstance(ctx).updateTitle(item.id, fetchedTitle);
                    }).start();
                }

                @Override
                public void onError(String error) {
                    // 失败时显示 URL 或预览
                    String fallback = item.url != null && !item.url.isEmpty() 
                            ? item.url : item.getPreview(40);
                    holder.tvTitle.setText(item.getTypeEmoji() + " " + fallback);
                }
            });
        }

        // Preview
        holder.tvPreview.setText(item.getPreview(100));
        holder.tvPreview.setVisibility(
                item.content != null && !item.content.isEmpty() ? View.VISIBLE : View.GONE);

        // Time + source + ID
        holder.tvMeta.setText("#" + item.id + " · " + item.getRelativeTime() + 
                " · " + item.getFullTimestamp() + " " + item.getSourceEmoji());

        // Favorite
        holder.ivFavorite.setImageResource(
                item.isFavorite ? android.R.drawable.btn_star_big_on : android.R.drawable.btn_star_big_off);

        // Tags
        holder.chipGroupTags.removeAllViews();
        if (item.tags != null && !item.tags.trim().isEmpty()) {
            holder.chipGroupTags.setVisibility(View.VISIBLE);
            for (String tag : item.tags.split(",")) {
                tag = tag.trim();
                if (tag.isEmpty()) continue;
                Chip chip = new Chip(holder.chipGroupTags.getContext());
                chip.setText(tag);
                chip.setTextSize(10);
                chip.setChipMinHeight(24);
                chip.setClickable(false);
                holder.chipGroupTags.addView(chip);
            }
        } else {
            holder.chipGroupTags.setVisibility(View.GONE);
        }

        // Click listeners
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onClick(item, position);
        });
        holder.itemView.setOnLongClickListener(v -> {
            if (listener != null) listener.onLongClick(item, position);
            return true;
        });
        holder.ivFavorite.setOnClickListener(v -> {
            if (listener != null) listener.onFavoriteClick(item, position);
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvTitle, tvPreview, tvMeta;
        ImageView ivFavorite;
        ChipGroup chipGroupTags;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tv_item_title);
            tvPreview = itemView.findViewById(R.id.tv_item_preview);
            tvMeta = itemView.findViewById(R.id.tv_item_meta);
            ivFavorite = itemView.findViewById(R.id.iv_item_favorite);
            chipGroupTags = itemView.findViewById(R.id.chip_group_tags);
        }
    }
}

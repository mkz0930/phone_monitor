package com.phonemonitor.app;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;

import java.util.List;

/**
 * 知识库浏览界面
 */
public class KnowledgeActivity extends AppCompatActivity implements ContentAdapter.OnItemClickListener {

    private KnowledgeDb db;
    private ContentAdapter adapter;
    private RecyclerView rvContents;
    private SwipeRefreshLayout swipeRefresh;
    private LinearLayout layoutEmpty;
    private TextView tvCount;
    private TextInputEditText etSearch;
    private ChipGroup chipGroupFilter;

    private String currentFilter = "all";  // all/note/article/link/code/fav
    private String currentSearch = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_knowledge);

        db = KnowledgeDb.getInstance(this);

        // Toolbar
        MaterialToolbar toolbar = findViewById(R.id.toolbar_knowledge);
        toolbar.setNavigationOnClickListener(v -> finish());

        // Views
        rvContents = findViewById(R.id.rv_contents);
        swipeRefresh = findViewById(R.id.swipe_refresh);
        layoutEmpty = findViewById(R.id.layout_empty);
        tvCount = findViewById(R.id.tv_count);
        etSearch = findViewById(R.id.et_search);
        chipGroupFilter = findViewById(R.id.chip_group_filter);

        // RecyclerView
        adapter = new ContentAdapter(this);
        rvContents.setLayoutManager(new LinearLayoutManager(this));
        rvContents.setAdapter(adapter);

        // Swipe to delete
        new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
            @Override
            public boolean onMove(RecyclerView rv, RecyclerView.ViewHolder vh, RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public void onSwiped(RecyclerView.ViewHolder vh, int direction) {
                int pos = vh.getAdapterPosition();
                ContentItem item = adapter.getItem(pos);
                adapter.removeItem(pos);

                Snackbar.make(rvContents, "已删除: " + item.getPreview(20), Snackbar.LENGTH_LONG)
                        .setAction("撤销", v -> {
                            adapter.restoreItem(item, pos);
                            updateCount();
                        })
                        .addCallback(new Snackbar.Callback() {
                            @Override
                            public void onDismissed(Snackbar snackbar, int event) {
                                if (event != DISMISS_EVENT_ACTION) {
                                    db.deleteContent(item.id);
                                }
                            }
                        })
                        .show();
                updateCount();
            }
        }).attachToRecyclerView(rvContents);

        // Pull to refresh
        swipeRefresh.setOnRefreshListener(() -> {
            loadContents();
            swipeRefresh.setRefreshing(false);
        });

        // Search
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                currentSearch = s.toString().trim();
                loadContents();
            }
        });

        // Filter chips
        chipGroupFilter.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) {
                currentFilter = "all";
            } else {
                int id = checkedIds.get(0);
                if (id == R.id.chip_all) currentFilter = "all";
                else if (id == R.id.chip_note) currentFilter = "note";
                else if (id == R.id.chip_article) currentFilter = "article";
                else if (id == R.id.chip_link) currentFilter = "link";
                else if (id == R.id.chip_code) currentFilter = "code";
                else if (id == R.id.chip_fav) currentFilter = "fav";
            }
            loadContents();
        });

        // FAB
        FloatingActionButton fab = findViewById(R.id.fab_add);
        fab.setOnClickListener(v -> {
            AddContentDialog dialog = new AddContentDialog();
            dialog.setOnContentAddedListener(id -> loadContents());
            dialog.show(getSupportFragmentManager(), "add_content");
        });

        loadContents();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadContents();
    }

    private void loadContents() {
        List<ContentItem> items;

        if (!currentSearch.isEmpty()) {
            items = db.searchContents(currentSearch);
        } else if ("fav".equals(currentFilter)) {
            items = db.getAllContents(500, 0);
            items.removeIf(item -> !item.isFavorite);
        } else if (!"all".equals(currentFilter)) {
            items = db.getContentsByType(currentFilter);
        } else {
            items = db.getAllContents(500, 0);
        }

        adapter.setItems(items);
        updateCount();

        layoutEmpty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
        rvContents.setVisibility(items.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void updateCount() {
        int total = db.getContentCount();
        tvCount.setText("共 " + total + " 条");
    }

    // ==================== Item Click Handlers ====================

    @Override
    public void onClick(ContentItem item, int position) {
        // Show detail in a dialog for now
        String titleText = item.getTypeEmoji() + " " + 
                (item.title != null ? item.title : "内容详情");
        String metaInfo = "#" + item.id + " · " + item.createdAt + " " + item.getSourceEmoji();
        
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(titleText)
                .setMessage(metaInfo + "\n\n" + item.content)
                .setPositiveButton("关闭", null)
                .setNeutralButton("复制", (d, w) -> {
                    android.content.ClipboardManager cm = (android.content.ClipboardManager)
                            getSystemService(CLIPBOARD_SERVICE);
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("content", item.content));
                    Toast.makeText(this, "✅ 已复制", Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    @Override
    public void onLongClick(ContentItem item, int position) {
        PopupMenu popup = new PopupMenu(this, rvContents.findViewHolderForAdapterPosition(position).itemView);
        popup.getMenu().add("📋 复制");
        popup.getMenu().add("🗑️ 删除");
        if (item.url != null && !item.url.isEmpty()) {
            popup.getMenu().add("🔗 打开链接");
        }

        popup.setOnMenuItemClickListener(menuItem -> {
            String title = menuItem.getTitle().toString();
            if (title.contains("复制")) {
                android.content.ClipboardManager cm = (android.content.ClipboardManager)
                        getSystemService(CLIPBOARD_SERVICE);
                cm.setPrimaryClip(android.content.ClipData.newPlainText("content", item.content));
                Toast.makeText(this, "✅ 已复制", Toast.LENGTH_SHORT).show();
            } else if (title.contains("删除")) {
                db.deleteContent(item.id);
                loadContents();
                Toast.makeText(this, "🗑️ 已删除", Toast.LENGTH_SHORT).show();
            } else if (title.contains("打开")) {
                try {
                    startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW,
                            android.net.Uri.parse(item.url)));
                } catch (Exception e) {
                    Toast.makeText(this, "❌ 无法打开链接", Toast.LENGTH_SHORT).show();
                }
            }
            return true;
        });
        popup.show();
    }

    @Override
    public void onFavoriteClick(ContentItem item, int position) {
        db.toggleFavorite(item.id);
        item.isFavorite = !item.isFavorite;
        adapter.notifyItemChanged(position);
    }
}

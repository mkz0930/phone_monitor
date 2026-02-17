package com.phonemonitor.app;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
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

        // Sync menu
        toolbar.inflateMenu(R.menu.menu_knowledge);
        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_sync) {
                syncToFeishu();
                return true;
            }
            return false;
        });

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
        // Open edit dialog
        EditContentDialog dialog = new EditContentDialog();
        dialog.setContentItem(item);
        dialog.setOnContentUpdatedListener(id -> loadContents());
        dialog.show(getSupportFragmentManager(), "edit_content");
    }

    @Override
    public void onLongClick(ContentItem item, int position) {
        // Find view holder safely
        RecyclerView.ViewHolder vh = rvContents.findViewHolderForAdapterPosition(position);
        View anchor = (vh != null) ? vh.itemView : rvContents;

        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenu().add(0, 1, 0, "📋 复制内容");
        popup.getMenu().add(0, 2, 0, "📤 分享内容");
        if (item.url != null && !item.url.isEmpty()) {
            popup.getMenu().add(0, 3, 0, "🔗 打开链接");
        }
        popup.getMenu().add(0, 4, 0, "🗑️ 删除");

        popup.setOnMenuItemClickListener(menuItem -> {
            switch (menuItem.getItemId()) {
                case 1: // Copy
                    android.content.ClipboardManager cm = (android.content.ClipboardManager)
                            getSystemService(CLIPBOARD_SERVICE);
                    android.content.ClipData clip = android.content.ClipData.newPlainText(
                            "content", item.content);
                    cm.setPrimaryClip(clip);
                    Toast.makeText(this, "✅ 已复制到剪贴板", Toast.LENGTH_SHORT).show();
                    return true;
                
                case 2: // Share
                    android.content.Intent shareIntent = new android.content.Intent();
                    shareIntent.setAction(android.content.Intent.ACTION_SEND);
                    shareIntent.putExtra(android.content.Intent.EXTRA_TEXT, item.content);
                    shareIntent.setType("text/plain");
                    startActivity(android.content.Intent.createChooser(shareIntent, "分享到..."));
                    return true;

                case 3: // Open Link
                    try {
                        startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW,
                                android.net.Uri.parse(item.url)));
                    } catch (Exception e) {
                        Toast.makeText(this, "❌ 无法打开链接: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                    return true;

                case 4: // Delete
                    new androidx.appcompat.app.AlertDialog.Builder(this)
                        .setTitle("确认删除？")
                        .setMessage("确定要删除这条内容吗？此操作无法撤销。")
                        .setPositiveButton("删除", (d, w) -> {
                            db.deleteContent(item.id);
                            loadContents(); // Reload to refresh list
                            Toast.makeText(this, "🗑️ 已删除", Toast.LENGTH_SHORT).show();
                        })
                        .setNegativeButton("取消", null)
                        .show();
                    return true;
            }
            return false;
        });
        popup.show();
    }

    @Override
    public void onFavoriteClick(ContentItem item, int position) {
        db.toggleFavorite(item.id);
        item.isFavorite = !item.isFavorite;
        adapter.notifyItemChanged(position);
    }

    // ==================== Feishu Sync ====================

    private void syncToFeishu() {
        SharedPreferences prefs = getSharedPreferences("phone_monitor_prefs", MODE_PRIVATE);
        String appId = prefs.getString("feishu_app_id", "");
        String appSecret = prefs.getString("feishu_app_secret", "");
        String chatId = prefs.getString("feishu_sync_chat_id", "");

        if (appId.isEmpty() || appSecret.isEmpty()) {
            Toast.makeText(this, "⚠️ 请先配置飞书 App ID / Secret", Toast.LENGTH_SHORT).show();
            return;
        }
        if (chatId.isEmpty()) {
            Toast.makeText(this, "⚠️ 请先配置同步群聊 ID (feishu_sync_chat_id)", Toast.LENGTH_SHORT).show();
            return;
        }

        List<ContentItem> unsynced = db.getUnsyncedContents();
        if (unsynced.isEmpty()) {
            Toast.makeText(this, "✅ 所有内容已同步", Toast.LENGTH_SHORT).show();
            return;
        }

        Toast.makeText(this, "📤 同步 " + unsynced.size() + " 条到飞书…", Toast.LENGTH_SHORT).show();

        new Thread(() -> {
            FeishuBotApi api = new FeishuBotApi(appId, appSecret);
            int success = 0;
            int fail = 0;

            for (ContentItem item : unsynced) {
                StringBuilder msg = new StringBuilder();
                msg.append(item.getTypeEmoji()).append(" ").append(item.title != null ? item.title : "无标题");
                msg.append("\n━━━━━━━━━━━━━━━━━━\n");
                msg.append(item.content);
                if (item.tags != null && !item.tags.isEmpty()) {
                    msg.append("\n🏷️ ").append(item.tags);
                }
                msg.append("\n⏰ ").append(item.createdAt);

                boolean sent = api.sendText(chatId, msg.toString());
                if (sent) {
                    db.markSynced(item.id);
                    success++;
                } else {
                    fail++;
                }

                // Rate limit: 100ms between messages
                try { Thread.sleep(100); } catch (InterruptedException ignored) {}
            }

            final int s = success, f = fail;
            runOnUiThread(() -> {
                if (f == 0) {
                    Toast.makeText(this, "✅ 已同步 " + s + " 条", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "⚠️ 成功 " + s + " 条，失败 " + f + " 条", Toast.LENGTH_SHORT).show();
                }
                loadContents();
            });
        }).start();
    }
}

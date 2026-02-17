package com.phonemonitor.app;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputEditText;

/**
 * 编辑内容对话框
 */
public class EditContentDialog extends DialogFragment {

    public interface OnContentUpdatedListener {
        void onContentUpdated(long id);
    }

    private OnContentUpdatedListener listener;
    private ContentItem item;

    public void setContentItem(ContentItem item) {
        this.item = item;
    }

    public void setOnContentUpdatedListener(OnContentUpdatedListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        View view = LayoutInflater.from(getContext()).inflate(R.layout.dialog_add_content, null);

        TextInputEditText etTitle = view.findViewById(R.id.et_add_title);
        TextInputEditText etContent = view.findViewById(R.id.et_add_content);
        TextInputEditText etTags = view.findViewById(R.id.et_add_tags);
        ChipGroup chipType = view.findViewById(R.id.chip_group_type);

        // Pre-fill fields
        if (item != null) {
            etTitle.setText(item.title);
            etContent.setText(item.content);
            etTags.setText(item.tags);

            // Select correct type chip
            if (item.type != null) {
                switch (item.type) {
                    case "note":    chipType.check(R.id.chip_type_note); break;
                    case "article": chipType.check(R.id.chip_type_article); break;
                    case "link":    chipType.check(R.id.chip_type_link); break;
                    case "code":    chipType.check(R.id.chip_type_code); break;
                    case "other":   chipType.check(R.id.chip_type_other); break;
                }
            }
        }

        return new AlertDialog.Builder(requireContext())
                .setTitle("✏️ 编辑内容")
                .setView(view)
                .setPositiveButton("保存", (dialog, which) -> {
                    String content = etContent.getText() != null
                            ? etContent.getText().toString().trim() : "";
                    if (content.isEmpty()) {
                        Toast.makeText(getContext(), "⚠️ 内容不能为空", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    String title = etTitle.getText() != null
                            ? etTitle.getText().toString().trim() : "";
                    String tags = etTags.getText() != null
                            ? etTags.getText().toString().trim() : "";

                    // Auto-generate title if empty
                    if (title.isEmpty()) {
                        String type = item != null ? item.type : "note";
                        title = ContentClassifier.generateTitle(content, type);
                    }

                    KnowledgeDb db = KnowledgeDb.getInstance(requireContext());
                    boolean ok = db.updateContent(item.id, title, content, tags);

                    if (ok) {
                        Toast.makeText(getContext(), "✅ 已更新", Toast.LENGTH_SHORT).show();
                        if (listener != null) listener.onContentUpdated(item.id);
                    } else {
                        Toast.makeText(getContext(), "❌ 更新失败", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("取消", null)
                .create();
    }
}

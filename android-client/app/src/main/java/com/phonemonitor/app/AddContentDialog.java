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
 * 手动添加内容对话框
 */
public class AddContentDialog extends DialogFragment {

    public interface OnContentAddedListener {
        void onContentAdded(long id);
    }

    private OnContentAddedListener listener;

    public void setOnContentAddedListener(OnContentAddedListener listener) {
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

        return new AlertDialog.Builder(requireContext())
                .setTitle("✍️ 添加内容")
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

                    // Get selected type
                    String type = "note";
                    int checkedId = chipType.getCheckedChipId();
                    if (checkedId != View.NO_ID) {
                        Chip chip = view.findViewById(checkedId);
                        if (chip != null) {
                            String chipText = chip.getText().toString();
                            if (chipText.contains("文章")) type = "article";
                            else if (chipText.contains("链接")) type = "link";
                            else if (chipText.contains("代码")) type = "code";
                            else if (chipText.contains("其他")) type = "other";
                        }
                    }

                    // Auto-classify if type is note and content looks different
                    if ("note".equals(type)) {
                        String autoType = ContentClassifier.classifyContent(content);
                        if (!"note".equals(autoType)) type = autoType;
                    }

                    // Auto-generate title if empty
                    if (title.isEmpty()) {
                        title = ContentClassifier.generateTitle(content, type);
                    }

                    String url = ContentClassifier.extractUrl(content);

                    KnowledgeDb db = KnowledgeDb.getInstance(requireContext());
                    long id = db.insertContent(title, content, url, type, "manual", tags);

                    if (id > 0) {
                        Toast.makeText(getContext(), "✅ 已保存", Toast.LENGTH_SHORT).show();
                        if (listener != null) listener.onContentAdded(id);
                    } else {
                        Toast.makeText(getContext(), "❌ 保存失败", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("取消", null)
                .create();
    }
}

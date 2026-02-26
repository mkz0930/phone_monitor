# Nightly Workflow Build Task

## Context
Horse currently uses the "Second Brain" feature in the Phone Monitor app to capture knowledge. Syncing this knowledge to Feishu currently happens via a manual "Sync" button in KnowledgeActivity.

## Requirement
Modify `KnowledgeActivity.java` to support an "Auto-sync" mode. When enabled, any new content added to the database (either via `AddContentDialog` or via the clipboard monitor/server sync) should be automatically pushed to Feishu.

## Plan
1.  **Add a toggle menu item** to `menu_knowledge.xml` for "Auto-sync".
2.  **Save the toggle state** in `SharedPreferences`.
3.  **Implement the logic** in `KnowledgeActivity`:
    *   Add a method `checkAutoSync()`.
    *   Trigger this method after content is added or when the activity resumes.
    *   Optionally, the "Auto-sync" could also be integrated into the background service (`FeishuSender` or a new worker), but starting with UI-driven auto-sync is a "small" improvement.
4.  **Refactor `syncToFeishu()`** to accept a list of items or handle single items more efficiently.

## Verification
1.  Enable "Auto-sync" in the menu.
2.  Add a new note.
3.  Verify it syncs to Feishu immediately.

/*
 * Copyright 2018-present MongoDB, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.mongodb.stitch.android.examples.todosync;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.SearchView;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.mongodb.stitch.android.core.Stitch;
import com.mongodb.stitch.android.core.StitchAppClient;
import com.mongodb.stitch.android.services.mongodb.remote.RemoteMongoClient;
import com.mongodb.stitch.android.services.mongodb.remote.RemoteMongoCollection;
import com.mongodb.stitch.core.auth.providers.anonymous.AnonymousCredential;
import com.mongodb.stitch.core.internal.common.BsonUtils;
import com.mongodb.stitch.core.services.mongodb.remote.ChangeEvent;
import com.mongodb.stitch.core.services.mongodb.remote.OperationType;
import com.mongodb.stitch.core.services.mongodb.remote.sync.ChangeEventListener;
import com.mongodb.stitch.core.services.mongodb.remote.sync.DefaultSyncConflictResolvers;
import com.mongodb.stitch.core.services.mongodb.remote.sync.SyncDeleteResult;


import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bson.BsonObjectId;
import org.bson.BsonRegularExpression;
import org.bson.BsonValue;
import org.bson.Document;
import org.bson.codecs.configuration.CodecRegistries;
import org.bson.types.ObjectId;

public class TodoListActivity extends AppCompatActivity {
  private static final String TAG = TodoListActivity.class.getSimpleName();

  private final ListUpdateListener listUpdateListener = new ListUpdateListener();
  private final ItemUpdateListener itemUpdateListener = new ItemUpdateListener();
  private TodoAdapter todoAdapter;
  private RemoteMongoCollection<TodoItem> items;
  private String userId;
  private Set<BsonValue> syncedIds;

  @Override
  protected void onCreate(final Bundle savedInstanceState) {
      super.onCreate(savedInstanceState);
      setContentView(R.layout.activity_todo_list);

      // TODO:
      // 1. Instantiate the Stitch client
      // final StitchAppClient client =

      // 2. Instantiate a RemoteMongoClient
      // final RemoteMongoClient mongoClient =

      // 3. Set up the items collection
      // items =

      // 4. Configure sync to be remote wins on the collection, meaning any conflict
      // that occurs should choose the remote version as the resolution.
      /* items.sync().configure(
              DefaultSyncConflictResolvers.remoteWins(),
              itemUpdateListener,
              (documentId, error) -> Log.e(TAG, error.getLocalizedMessage()));
      */

      // Set up recycler view for to-do items
      final RecyclerView todoRecyclerView = findViewById(R.id.rv_todo_items);
      final RecyclerView.LayoutManager todoLayoutManager = new LinearLayoutManager(this);
      todoRecyclerView.setLayoutManager(todoLayoutManager);

      // Set up adapter
      todoAdapter = new TodoAdapter(
              new ArrayList<>(),
              new TodoAdapter.ItemUpdater() {
                  @Override
                  public void updateChecked(final ObjectId itemId, final boolean isChecked) {
                      final Document updateDoc =
                              new Document("$set", new Document(TodoItem.Fields.CHECKED, isChecked));

                      if (isChecked) {
                          updateDoc.append("$currentDate", new Document(TodoItem.Fields.DONE_DATE, true));
                      } else {
                          updateDoc.append("$unset", new Document(TodoItem.Fields.DONE_DATE, ""));
                      }

                      items.sync().updateOne(new Document("_id", itemId), updateDoc);
                  }

                  @Override
                  public void updateTask(final ObjectId itemId, final String currentTask) {
                      showEditItemDialog(itemId, currentTask);
                  }
              });
      todoRecyclerView.setAdapter(todoAdapter);

      // Start with the current local items
      todoAdapter.replaceItems(getItems(), false);

      doLogin();
  }

  @Override
  public boolean onPrepareOptionsMenu(final Menu menu) {
    // Only enable options besides login when logged in
    final boolean loggedIn = Stitch.getDefaultAppClient().getAuth().isLoggedIn();
    for (int i = 0; i < menu.size(); i++) {
      if (menu.getItem(i).getItemId() == R.id.login_action) {
        continue;
      }
      menu.getItem(i).setEnabled(loggedIn);
    }
    return true;
  }

  @Override
  public boolean onCreateOptionsMenu(final Menu menu) {
    final MenuInflater inflater = getMenuInflater();
    inflater.inflate(R.menu.todo_menu, menu);

    final SearchView searchView =
        (SearchView) menu.findItem(R.id.search_items_action).getActionView();
    searchView.setOnQueryTextListener(
        new SearchView.OnQueryTextListener() {
          @Override
          public boolean onQueryTextSubmit(final String query) {
            return false;
          }

          @Override
          public boolean onQueryTextChange(final String query) {
            todoAdapter.replaceItems(getItemsWithRegexFilter(query), true);
            return true;
          }
        });

    searchView.setOnCloseListener(
        () -> {
          todoAdapter.replaceItems(getItems(), true);
          return false;
        });

    return super.onCreateOptionsMenu(menu);
  }

  @Override
  public boolean onOptionsItemSelected(final MenuItem item) {
    switch (item.getItemId()) {
      case R.id.login_action:
        doLogin();
        return true;
      case R.id.add_todo_item_action:
        showAddItemDialog();
        return true;
      case R.id.clear_checked_action:
        clearCheckedTodoItems();
        return true;
      case R.id.clear_all_action:
        clearAllTodoItems();
        return true;
      default:
        return super.onOptionsItemSelected(item);
    }
  }

  private void doLogin() {
      Stitch.getDefaultAppClient().getAuth().loginWithCredential(new AnonymousCredential())
              .addOnSuccessListener(user -> {
                  userId = user.getId();
                  invalidateOptionsMenu();
                  Toast.makeText(TodoListActivity.this, "Logged in", Toast.LENGTH_SHORT).show();
              })
              .addOnFailureListener(e -> {
                  invalidateOptionsMenu();
                  Log.d(TAG, "error logging in", e);
                  Toast.makeText(TodoListActivity.this, "Failed logging in", Toast.LENGTH_SHORT).show();
              });
  }

  private class ListUpdateListener implements ChangeEventListener<Document> {
    @Override
    public void onEvent(final BsonValue documentId, final ChangeEvent<Document> event) {
      if (!event.hasUncommittedWrites()) {
        todoAdapter.replaceItems(getItemsFromServer(), false);
      }
    }
  }

  private class ItemUpdateListener implements ChangeEventListener<TodoItem> {
    @Override
    public void onEvent(final BsonValue documentId, final ChangeEvent<TodoItem> event) {
      if (event.getOperationType() == OperationType.DELETE) {
        todoAdapter.removeItemById(event.getDocumentKey().getObjectId("_id").getValue());
        return;
      }
      todoAdapter.updateOrAddItem(event.getFullDocument());
    }
  }

  private void showAddItemDialog() {
    final AlertDialog.Builder builder = new AlertDialog.Builder(this);
    builder.setTitle("Add Item");

    final View view = getLayoutInflater().inflate(R.layout.edit_item_dialog, null);
    final EditText input = view.findViewById(R.id.et_todo_item_task);

    builder.setView(view);

    // Set up the buttons
    builder.setPositiveButton(
        "Add",
        (dialog, which) -> addTodoItem(input.getText().toString()));
    builder.setNegativeButton(
        "Cancel",
        (dialog, which) -> dialog.cancel());

    builder.show();
  }

  private void showEditItemDialog(final ObjectId itemId, final String currentTask) {
    final AlertDialog.Builder builder = new AlertDialog.Builder(this);
    builder.setTitle("Edit Item");

    final View view = getLayoutInflater().inflate(R.layout.edit_item_dialog, null);
    final EditText input = view.findViewById(R.id.et_todo_item_task);

    input.setText(currentTask);
    input.setSelection(input.getText().length());

    builder.setView(view);

    // Set up the buttons
    builder.setPositiveButton(
        "Update",
        (dialog, which) -> updateTodoItemTask(itemId, input.getText().toString()));
    builder.setNegativeButton(
        "Cancel",
        (dialog, which) -> dialog.cancel());

    builder.show();
  }

  private Task<List<TodoItem>> getItemsFromServer() {
    // Filter out uncommitted, deleted items at the time of this refresh
    // During the Stitch Mobile Sync beta, this type of code is necessary but not completely
    // sufficient to guarantee that we always show a view of items that are consistent remotely and
    // locally. In the future, sync will handle this type of logic more correctly and safely.
    return items.sync().find().into(new ArrayList<>())
        .continueWithTask(localTask -> {
          if (!localTask.isSuccessful()) {
            return Tasks.forResult(Collections.emptyList());
          }

          final Map<ObjectId, TodoItem> localItems = new HashMap<>();
          for (final TodoItem item : localTask.getResult()) {
            localItems.put(item.getId(), item);
          }
          return items.find().into(new ArrayList<>())
              .continueWith(remoteTask -> {
                if (!remoteTask.isSuccessful()) {
                  return Collections.emptyList();
                }
                final List<TodoItem> remoteItems = remoteTask.getResult();
                for (final TodoItem item : remoteItems) {
                  items.sync().syncOne(new BsonObjectId(item.getId()));
                }

                // this may visually override any uncommitted writes that happened locally, but
                // when those specific items are updated or those writes are committed, the UI
                // should update to the correct state.
                return remoteItems;
              });
        });
  }

  private Task<List<TodoItem>> getItems() {
    return items.sync().find().into(new ArrayList<>());
  }

  private Task<List<TodoItem>> getItemsWithRegexFilter(final String regex) {
    return items.sync().find(
        new Document(
            TodoItem.Fields.TASK,
            new Document().append("$regex",
                new BsonRegularExpression(regex)).append("$options", "i")))
        .into(new ArrayList<>());
  }

  private void addTodoItem(final String task) {
    final TodoItem newItem = new TodoItem(new ObjectId(), userId, task, false, null);
    items.sync().insertOne(newItem)
        .addOnSuccessListener(result -> {
          todoAdapter.updateOrAddItem(newItem);
        })
        .addOnFailureListener(e -> Log.e(TAG, "failed to insert todo item", e));
  }

  private void updateTodoItemTask(final ObjectId itemId, final String newTask) {
    final BsonObjectId docId = new BsonObjectId(itemId);
    items.sync().updateOne(
        new Document("_id", docId),
        new Document("$set", new Document(TodoItem.Fields.TASK, newTask)))
        .addOnSuccessListener(result -> {
          items.sync().find(new Document("_id", docId)).first()
              .addOnSuccessListener(item -> {
                if (item == null) {
                  return;
                }
                todoAdapter.updateOrAddItem(item);
              })
              .addOnFailureListener(e -> Log.e(TAG, "failed to find todo item", e));
        })
        .addOnFailureListener(e -> Log.e(TAG, "failed to insert todo item", e));
  }

  private void clearCheckedTodoItems() {
    final List<Task<SyncDeleteResult>> tasks = new ArrayList<>();
    getItems().addOnSuccessListener(todoItems -> {
      for (final TodoItem item : todoItems) {
        if (item.isChecked()) {
          tasks.add(items.sync().deleteOne(new Document("_id", item.getId())));
        }
      }
      Tasks.whenAllComplete(tasks)
          .addOnCompleteListener(task -> todoAdapter.replaceItems(getItems(), true));
    });
  }

  private void clearAllTodoItems() {
    final List<Task<SyncDeleteResult>> tasks = new ArrayList<>();
    getItems().addOnSuccessListener(todoItems -> {
      for (final TodoItem item : todoItems) {
        tasks.add(items.sync().deleteOne(new Document("_id", item.getId())));
      }
      Tasks.whenAllComplete(tasks)
          .addOnCompleteListener(task -> todoAdapter.clearItems());
    });
  }
}

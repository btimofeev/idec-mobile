package vit01.idecmobile;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.StrictMode;
import android.support.design.widget.TextInputEditText;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.Toast;

import com.mikepenz.google_material_typeface_library.GoogleMaterial;
import com.mikepenz.iconics.IconicsDrawable;

import java.io.File;
import java.util.ArrayList;

import vit01.idecmobile.Core.Config;
import vit01.idecmobile.Core.DraftMessage;
import vit01.idecmobile.Core.DraftStorage;
import vit01.idecmobile.Core.IIMessage;
import vit01.idecmobile.Core.Sender;
import vit01.idecmobile.Core.SimpleFunctions;
import vit01.idecmobile.Core.Station;

public class DraftEditor extends AppCompatActivity {
    Spinner compose_stations;
    TextInputEditText compose_echoarea, compose_to, compose_subj, compose_repto, compose_msg;
    DraftMessage message;
    File fileToSave;
    ArrayList<String> station_names = new ArrayList<>();
    ArrayAdapter<String> spinnerAdapter;
    int nodeindex = 0;
    String outbox_id;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_draft_editor);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setTitle("Написать");

        getControls();
        Intent incoming = getIntent();

        DraftStorage.initStorage();

        nodeindex = incoming.getIntExtra("nodeindex", 0);
        outbox_id = Config.values.stations.get(nodeindex).outbox_storage_id;

        String task = incoming.getStringExtra("task");

        if (task.equals("new_in_echo")) {
            message = new DraftMessage();
            message.echo = incoming.getStringExtra("echoarea");
            fileToSave = DraftStorage.newMessage(outbox_id, message);
        } else if (task.equals("new_answer")) {
            message = new DraftMessage();
            IIMessage to_which = (IIMessage) incoming.getSerializableExtra("message");
            message.echo = to_which.echo;
            message.to = to_which.from;
            message.subj = SimpleFunctions.subjAnswer(to_which.subj);
            message.repto = to_which.id;

            if (incoming.getBooleanExtra("quote", false)) {
                message.msg = SimpleFunctions.quoteAnswer(to_which.msg, message.to, Config.values.oldQuote);
            }
            fileToSave = DraftStorage.newMessage(outbox_id, message);
        } else if (task.equals("edit_existing")) {
            fileToSave = (File) incoming.getSerializableExtra("file");
            message = DraftStorage.readFromFile(fileToSave);
        }

        if (fileToSave == null) {
            Toast.makeText(DraftEditor.this, "Не удалось создать/открыть файл", Toast.LENGTH_SHORT).show();
            SimpleFunctions.debug("Проблема с созданием/открытием файла!");
            finish();
        }

        if (!Config.values.defaultEditor) {
            Intent intent = new Intent();
            intent.setAction(android.content.Intent.ACTION_VIEW);
            intent.setDataAndType(Uri.fromFile(fileToSave), "text/plain");

            startActivity(intent);
            finish();
        }

        installValues();
        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_compose, menu);

        Context context = getApplicationContext();

        menu.findItem(R.id.action_compose_send).setIcon(new IconicsDrawable
                (context, GoogleMaterial.Icon.gmd_send).actionBar().color(Color.WHITE));
        menu.findItem(R.id.action_compose_delete).setIcon(new IconicsDrawable
                (context, GoogleMaterial.Icon.gmd_delete).actionBar().color(Color.WHITE));
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_compose_send) {
            fetchValues();
            saveMessage();

            new Thread(new Runnable() {
                @Override
                public void run() {
                    boolean sent = Sender.sendOneMessage(DraftEditor.this,
                            Config.values.stations.get(nodeindex), fileToSave);

                    final String statusText = (sent) ? "Сообщение отправлено" : "Ошибка отправки!";
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(getApplicationContext(), statusText, Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }).start();

            finish();
        } else if (id == R.id.action_compose_delete) {
            Toast.makeText(DraftEditor.this, "Удаляем черновик", Toast.LENGTH_SHORT).show();
            boolean r = fileToSave.delete();
            if (!r) {
                Toast.makeText(DraftEditor.this, "Удалить не получилось!", Toast.LENGTH_SHORT).show();
            } else fileToSave = null;
            finish();
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        fetchValues();
        saveMessage();
        super.onBackPressed();
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    public void getControls() {
        compose_stations = (Spinner) findViewById(R.id.compose_stations);
        compose_echoarea = (TextInputEditText) findViewById(R.id.compose_echoarea);
        compose_to = (TextInputEditText) findViewById(R.id.compose_to);
        compose_subj = (TextInputEditText) findViewById(R.id.compose_subj);
        compose_repto = (TextInputEditText) findViewById(R.id.compose_repto);
        compose_msg = (TextInputEditText) findViewById(R.id.compose_msg);

        compose_stations.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (nodeindex != position) {
                    String secondOutbox_id = Config.values.stations.get(position).outbox_storage_id;
                    File newDirectory = DraftStorage.getStationStorageDir(secondOutbox_id);

                    File newFile = new File(newDirectory, fileToSave.getName());
                    boolean renamed = fileToSave.renameTo(newFile);

                    if (!renamed) {
                        Toast.makeText(DraftEditor.this, "Переместить на другую станцию не получилось!", Toast.LENGTH_SHORT).show();
                    }

                    fileToSave = newFile;
                    nodeindex = position;
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });
    }

    public void installValues() {
        for (Station station : Config.values.stations) {
            station_names.add(station.nodename);
        }
        spinnerAdapter = new ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, station_names);
        compose_stations.setAdapter(spinnerAdapter);
        compose_stations.setSelection(nodeindex);

        compose_echoarea.setText(message.echo);
        compose_to.setText(message.to);
        compose_subj.setText(message.subj);
        compose_repto.setText((message.repto != null) ? message.repto : "");
        compose_msg.setText(message.msg);
    }

    public void fetchValues() {
        message.echo = compose_echoarea.getText().toString();
        message.to = compose_to.getText().toString();
        message.subj = compose_subj.getText().toString();

        String repto = compose_repto.getText().toString();
        message.repto = (repto.equals("") ? null : repto);
        message.msg = compose_msg.getText().toString();
    }

    public void saveMessage() {
        boolean result = DraftStorage.writeToFile(fileToSave, message);
        if (!result) {
            SimpleFunctions.debug("Проблемсы!");
            Toast.makeText(DraftEditor.this, "Файл как-то не сохранён. Сожалею :(", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
    }
}
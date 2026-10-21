package org.localpilot.android;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.Collator;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** On-device launcher picker. Selecting an app grants no input permission. */
public final class GamePickerActivity extends Activity {
    private final ExecutorService loader = Executors.newSingleThreadExecutor();
    private final List<Entry> applications = new ArrayList<>();
    private final List<Entry> visible = new ArrayList<>();
    private final AppAdapter adapter = new AppAdapter();
    private EditText search;
    private TextView status;
    private TextView empty;
    private Button toggle;
    private boolean showAll;
    private boolean loading;
    private int loadGeneration;

    @Override public void onCreate(Bundle savedState) {
        super.onCreate(savedState);
        showAll = savedState != null && savedState.getBoolean("show_all", false);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(12), dp(16), dp(8));
        setContentView(content);

        TextView title = new TextView(this);
        title.setText("Установленные игры");
        title.setTextSize(24);
        content.addView(title);

        TextView help = new TextView(this);
        help.setText("Выберите игру на этом телефоне. Она откроется; если служба LocalPilot включена, она подготовит привязку только к её окну. Ввод разрешается отдельно в панели. Автоигра доступна только для настроенного профиля; выбор приложения сам по себе её не включает.");
        content.addView(help);

        search = new EditText(this);
        search.setSingleLine(true);
        search.setHint("Название или пакет приложения");
        search.setContentDescription("Поиск установленной игры");
        content.addView(search);

        LinearLayout actions = new LinearLayout(this);
        toggle = new Button(this);
        toggle.setOnClickListener(view -> {
            showAll = !showAll;
            filter();
        });
        actions.addView(toggle, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        Button refresh = new Button(this);
        refresh.setText("Обновить");
        refresh.setOnClickListener(view -> loadApplications());
        actions.addView(refresh);
        content.addView(actions);

        status = new TextView(this);
        content.addView(status);

        FrameLayout results = new FrameLayout(this);
        ListView list = new ListView(this);
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, position, id) -> launch(visible.get(position)));
        results.addView(list, new FrameLayout.LayoutParams(-1, -1));
        empty = new TextView(this);
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(dp(12), dp(12), dp(12), dp(12));
        results.addView(empty, new FrameLayout.LayoutParams(-1, -1));
        list.setEmptyView(empty);
        content.addView(results, new LinearLayout.LayoutParams(-1, 0, 1));

        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { filter(); }
            @Override public void afterTextChanged(Editable text) {}
        });
        if (savedState != null) search.setText(savedState.getString("search", ""));
        loadApplications();
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        out.putBoolean("show_all", showAll);
        out.putString("search", search.getText().toString());
        super.onSaveInstanceState(out);
    }

    private void loadApplications() {
        loading = true;
        int generation = ++loadGeneration;
        applications.clear();
        filter();
        loader.execute(() -> {
            List<Entry> found = new ArrayList<>();
            boolean failed = false;
            try {
                PackageManager manager = getPackageManager();
                Set<String> launchers = new HashSet<>();
                Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
                for (ResolveInfo entry : manager.queryIntentActivities(home, 0)) {
                    if (entry.activityInfo != null) launchers.add(entry.activityInfo.packageName);
                }
                Intent launcher = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
                Map<String, Entry> unique = new LinkedHashMap<>();
                for (ResolveInfo entry : manager.queryIntentActivities(launcher, 0)) {
                    if (Thread.currentThread().isInterrupted()) return;
                    if (entry.activityInfo == null || !entry.activityInfo.enabled) continue;
                    ApplicationInfo app = entry.activityInfo.applicationInfo;
                    String packageName = entry.activityInfo.packageName;
                    if (app == null || !app.enabled || excluded(packageName, launchers)) continue;
                    CharSequence name = app.loadLabel(manager);
                    String label = name == null || name.length() == 0 ? packageName : name.toString();
                    unique.putIfAbsent(packageName, new Entry(packageName, label,
                            app.category == ApplicationInfo.CATEGORY_GAME));
                }
                found.addAll(unique.values());
                Collator collator = Collator.getInstance();
                found.sort((left, right) -> {
                    int compared = collator.compare(left.label, right.label);
                    return compared == 0 ? left.packageName.compareTo(right.packageName) : compared;
                });
            } catch (RuntimeException error) {
                failed = true;
            }
            boolean error = failed;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed() || generation != loadGeneration) return;
                loading = false;
                applications.addAll(found);
                filter();
                if (error) {
                    status.setText("Android не вернул список приложений. Нажмите «Обновить».");
                    empty.setText("Список недоступен. Игру можно открыть самостоятельно и привязать через панель.");
                }
            });
        });
    }

    private boolean excluded(String packageName, Set<String> launchers) {
        if (packageName == null || launchers.contains(packageName) || packageName.equals(getPackageName())
                || packageName.equals("org.localpilot.android") || packageName.equals("org.localpilot.android.dev")) return true;
        String lower = packageName.toLowerCase(Locale.ROOT);
        return lower.equals("android") || lower.endsWith(".settings") || lower.endsWith(".systemui")
                || lower.contains("packageinstaller") || lower.contains("permissioncontroller")
                || lower.equals("com.android.launcher") || lower.equals("com.android.launcher2")
                || lower.equals("com.android.launcher3") || lower.equals("com.google.android.apps.nexuslauncher");
    }

    private void filter() {
        if (search == null || toggle == null || empty == null) return;
        String query = search.getText().toString().trim().toLowerCase(Locale.ROOT);
        visible.clear();
        int games = 0;
        for (Entry entry : applications) {
            if (entry.game) games++;
            if (!showAll && !entry.game) continue;
            if (!query.isEmpty() && !entry.label.toLowerCase(Locale.ROOT).contains(query)
                    && !entry.packageName.toLowerCase(Locale.ROOT).contains(query)) continue;
            visible.add(entry);
        }
        toggle.setText(showAll ? "Только игры" : "Все приложения");
        if (loading) {
            status.setText("Читаю список приложений на телефоне…");
            empty.setText("Загрузка…");
        } else {
            status.setText("Показано: " + visible.size() + ". Android пометил как игры: " + games + ".");
            empty.setText(showAll ? "Подходящих приложений нет. Измените поиск или нажмите «Обновить»."
                    : "Игра может не иметь категории. Нажмите «Все приложения» и найдите её по названию.");
        }
        adapter.notifyDataSetChanged();
    }

    private void launch(Entry entry) {
        try {
            PackageManager manager = getPackageManager();
            Intent intent = manager.getLaunchIntentForPackage(entry.packageName);
            ComponentName component = intent == null ? null : intent.resolveActivity(manager);
            if (component == null || !entry.packageName.equals(component.getPackageName())) {
                toast("У игры нет доступного окна запуска. Обновите список.");
                return;
            }
            intent.setComponent(component);
            intent.setPackage(entry.packageName);
            startActivity(intent);
        } catch (ActivityNotFoundException | SecurityException error) {
            toast("Android не разрешил открыть игру. Список можно обновить.");
            return;
        }
        getSharedPreferences(MainActivity.PREF, MODE_PRIVATE).edit()
                .putString("selected_game_package", entry.packageName)
                .putString("selected_game_label", entry.label).apply();
        PilotService service = PilotService.instance;
        if (service != null) {
            service.selectInstalledTarget(entry.packageName, entry.label);
        } else {
            toast("Игра открыта. Для подготовки управления включите службу LocalPilot.");
        }
        finish();
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private void toast(String text) { Toast.makeText(this, text, Toast.LENGTH_LONG).show(); }

    @Override protected void onDestroy() {
        ++loadGeneration;
        loader.shutdownNow();
        super.onDestroy();
    }

    private static final class Entry {
        final String packageName;
        final String label;
        final boolean game;
        Entry(String packageName, String label, boolean game) {
            this.packageName = packageName;
            this.label = label;
            this.game = game;
        }
    }

    private final class AppAdapter extends BaseAdapter {
        @Override public int getCount() { return visible.size(); }
        @Override public Entry getItem(int position) { return visible.get(position); }
        @Override public long getItemId(int position) { return position; }
        @Override public View getView(int position, View old, ViewGroup parent) {
            LinearLayout row;
            if (old instanceof LinearLayout) {
                row = (LinearLayout) old;
            } else {
                row = new LinearLayout(GamePickerActivity.this);
                row.setOrientation(LinearLayout.VERTICAL);
                row.setPadding(dp(8), dp(12), dp(8), dp(12));
                TextView label = new TextView(GamePickerActivity.this);
                label.setTextSize(18);
                row.addView(label);
                TextView packageName = new TextView(GamePickerActivity.this);
                packageName.setTextSize(12);
                row.addView(packageName);
            }
            Entry entry = getItem(position);
            ((TextView) row.getChildAt(0)).setText(entry.label);
            ((TextView) row.getChildAt(1)).setText(entry.packageName);
            return row;
        }
    }
}

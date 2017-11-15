package onmap.co.il.remote.localization;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.SparseArray;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import io.reactivex.Observable;
import io.reactivex.functions.BiFunction;
import io.reactivex.subjects.PublishSubject;
import onmap.co.il.remote.localization.models.LocalizedValue;
import onmap.co.il.remote.localization.storage.LocaleStorage;
import onmap.co.il.remote.localization.storage.RealmLocaleStorage;

public class ViewBinder {

    public static final String TAG = ViewBinder.class.getSimpleName();
    private final PublishSubject<Map<String, TextView>> onViewPoolCreated = PublishSubject.create();
    private final SparseArray<String> viewIdKey;
    private Map<String, TextView> viewPool = new HashMap<>();

    public ViewBinder(Context context, Class aClass, ConfigMap config) {
        viewIdKey = getViewIdKey(context, aClass);//Todo move from constructor
        update(config);

        LocaleStorage storage = RealmLocaleStorage.getInstance();

        storage.get()
                .withLatestFrom(onViewPoolCreated, convert())
                .map(Map::entrySet)
                .flatMap(Observable::fromIterable)
                .subscribe(this::setText);

        storage.onLocalizationChanged()
                .withLatestFrom(onViewPoolCreated, convert())
                .map(Map::entrySet)
                .flatMap(Observable::fromIterable)
                .subscribe(this::setText);

    }

    private void update(ConfigMap config) {
        SparseArray<String> configMap = config.getMap();
        int size = configMap.size();
        for (int i = 0; i < size; i++) {
            int key = configMap.keyAt(i);
            String value = configMap.get(key);
            viewIdKey.append(key, value);

        }
    }

    private void setText(Map.Entry<TextView, String> entry) {
        new Handler(Looper.getMainLooper()).post(() -> {
            TextView textView = entry.getKey();
            String value = entry.getValue();
            if (value != null) {
                textView.setText(value);
            }
        });

    }

    @NonNull
    private BiFunction<Map<String, List<LocalizedValue>>, Map<String, TextView>, Map<TextView, String>> convert() {
        return (dataMap, viewPool) -> {

            Map<TextView, String> map = new HashMap<>();

            Set<Map.Entry<String, TextView>> entries = viewPool.entrySet();
            for (Map.Entry<String, TextView> entry : entries) {
                String key = entry.getKey();
                List<LocalizedValue> localizedValues = dataMap.get(key);
                if (localizedValues == null) {
                    localizedValues = dataMap.get(key.replace("_", "."));
                }
                if (localizedValues != null) {
                    String text = getLocalizedText(localizedValues);
                    map.put(entry.getValue(), text);
                }
            }

            return map;
        };
    }

    @Nullable
    private String getLocalizedText(List<LocalizedValue> localizedValues) {
        String locale = Locale.getDefault().getLanguage();
        for (int i = 0; i < localizedValues.size(); i++) {
            LocalizedValue value = localizedValues.get(i);
            if (value.getLanguage().equals(locale)) {
                return value.getValue();
            }
        }
        return null;
    }

    private SparseArray<String> getViewIdKey(Context context, Class aClass) {
        SparseArray<String> idKeyMap = new SparseArray<>();
        Field[] fields = aClass.getFields();
        for (Field field : fields) {
            try {
                String name = field.getName();
                int fieldValue = getId(context, name);
                idKeyMap.append(fieldValue, name);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return idKeyMap;
    }

    private int getId(Context context, String name) {
        int id = context.getResources().getIdentifier(name, "id", context.getPackageName());
        if (id == 0) {
            id = context.getResources().getIdentifier(name.replace("_", "."), "id", context.getPackageName());
        }
        return id;
    }

    public void bind(Activity activity) {
        viewPool = new HashMap<>();
        View root = getRootView(activity);
        addToViewPool(root);
        onViewPoolCreated.onNext(viewPool);
    }

    public void unbind(Activity activity) {
        viewPool.clear();
    }

    private View getRootView(Activity activity) {
        return activity.getWindow().getDecorView().findViewById(android.R.id.content);
    }

    private void addToViewPool(View root) {

        if (root instanceof ViewGroup) {
            ViewGroup rootViewGroup = (ViewGroup) root;
            int childCount = rootViewGroup.getChildCount();
            for (int i = 0; i < childCount; i++) {
                View childAt = rootViewGroup.getChildAt(i);
                viewPool(childAt, viewPool);
                addToViewPool(childAt);
            }
        }

    }

    private void viewPool(View childAt, Map<String, TextView> viewPool) {
        if (childAt instanceof TextView) {
            TextView textView = (TextView) childAt;
            String key = keyById(textView.getId());
            viewPool.put(key, textView);
        }
    }

    private String keyById(int viewId) {
        return viewIdKey.get(viewId);
    }
}
/*
   Copyright 2019 Total Pave Inc.

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
*/

package com.totalpave.cordova.inset;

import android.app.Activity;
import android.content.res.Configuration;
import android.content.Context;
import android.graphics.Color;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.RoundedCorner;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.WindowInsets;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.LOG;
import org.apache.cordova.PluginResult.Status;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;
import java.lang.NumberFormatException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;

public class Inset extends CordovaPlugin {
    public static final int DEFAULT_INSET_MASK = WindowInsetsCompat.Type.displayCutout() | WindowInsetsCompat.Type.systemBars();
    public static final boolean DEFAULT_INCLUDE_ROUNDED_CORNERS = true;
    private final Object $insetCacheLock = new Object();
    private  boolean $isGestureOnly = false;
    private JSONObject $lastInsetJson;
    public static class WebviewMask {
        private WebviewMask() {}

        public static final int CAPTION_BAR                 = 1;
        public static final int DISPLAY_CUTOUT              = 1 << 1;
        public static final int IME                         = 1 << 2;
        public static final int MANDATORY_SYSTEM_GESTURES   = 1 << 3;
        public static final int NAVIGATION_BARS             = 1 << 4;
        public static final int STATUS_BARS                 = 1 << 5;
        public static final int SYSTEM_BARS                 = 1 << 6;
        public static final int SYSTEM_GESTURES             = 1 << 7;
        public static final int TAPPABLE_ELEMENT            = 1 << 8;
    }

    public static class ListenerConfiguration {
        public Integer mask;
        public boolean includeRoundedCorners = DEFAULT_INCLUDE_ROUNDED_CORNERS;
        public boolean stable = false;      
        public boolean includeIme = false;   
    }

    public static class Listener {
        private final Context $context;
        private final CallbackContext $callback;
        private JSONObject $currentInset;
        private final int $mask;

        private final boolean $stable;
        private final boolean $includeIme;
        private final boolean $includeRoundedCorners;
        private final UUID $id;

        public Listener(Context context, CallbackContext callback, ListenerConfiguration config) {
            $id = UUID.randomUUID();
            $context = context;
            $callback = callback;
            $stable = config.stable;
            $includeIme = config.includeIme;
            if (config.mask == null) {
                $mask = DEFAULT_INSET_MASK;
            }
            else {
                $mask = $mapMask(config.mask);
            }
            $includeRoundedCorners = config.includeRoundedCorners;
        }

        public String getID() {
            return $id.toString();
        }

        public void onInsetUpdate(WindowInsetsCompat insetProvider) {
            try {
                float density = $context.getResources().getDisplayMetrics().density;

                Insets sys = $stable
                        ? insetProvider.getInsetsIgnoringVisibility($mask)
                        : insetProvider.getInsets($mask);

                double top=0, right=0, bottom=0, left=0;
                top = sys.top / density;
                right = sys.right / density;
                bottom = sys.bottom / density;
                left = sys.left / density;

                double tl=0,tr=0,bl=0,br=0;
                if ($includeRoundedCorners && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    WindowInsets wi = insetProvider.toWindowInsets();
                    if (wi != null) {
                        RoundedCorner c;
                        if ((c = wi.getRoundedCorner(RoundedCorner.POSITION_TOP_LEFT)) != null)  tl = c.getRadius()/density;
                        if ((c = wi.getRoundedCorner(RoundedCorner.POSITION_TOP_RIGHT)) != null) tr = c.getRadius()/density;
                        if ((c = wi.getRoundedCorner(RoundedCorner.POSITION_BOTTOM_LEFT)) != null) bl = c.getRadius()/density;
                        if ((c = wi.getRoundedCorner(RoundedCorner.POSITION_BOTTOM_RIGHT)) != null) br = c.getRadius()/density;
                    }
                }
                top    = Math.max(top,    Math.max(tl, tr));

                int navBottom   = insetProvider.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;
                int gestBottom  = insetProvider.getInsets(WindowInsetsCompat.Type.systemGestures()).bottom;
                int tapBottom   = insetProvider.getInsets(WindowInsetsCompat.Type.tappableElement()).bottom;
                int cutBottom   = insetProvider.getInsets(WindowInsetsCompat.Type.displayCutout()).bottom;

                int gestureZone = Math.max(gestBottom, tapBottom);
                int smartBottomPx = (gestureZone > 0 ? gestureZone : navBottom);

                smartBottomPx = Math.max(smartBottomPx, cutBottom);


                bottom = smartBottomPx / density;
                left   = Math.max(left,   Math.max(tl, bl));
                right  = Math.max(right,  Math.max(tr, br));

                if ($includeIme && insetProvider.isVisible(WindowInsetsCompat.Type.ime())) {
                    Insets ime = insetProvider.getInsets(WindowInsetsCompat.Type.ime());
                    bottom = Math.max(bottom, ime.bottom / density);
                }

                JSONObject data = new JSONObject();
                data.put("top", top);
                data.put("right", right);
                data.put("bottom", bottom);
                data.put("left", left);
                data.put("unit", "dp");

                $currentInset = data;

                JSONObject update = new JSONObject();
                update.put("type", "update");
                update.put("id", getID());
                update.put("data", $currentInset);

                PluginResult response = new PluginResult(Status.OK, update);
                response.setKeepCallback(true);
                $callback.sendPluginResult(response);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        private int $mapMask(int webviewMask) {
            int insetTypeMask = 0;

            if ((webviewMask & WebviewMask.CAPTION_BAR) != 0) {
                insetTypeMask |= WindowInsetsCompat.Type.captionBar();
            }

            if ((webviewMask & WebviewMask.DISPLAY_CUTOUT) != 0) {
                insetTypeMask |= WindowInsetsCompat.Type.displayCutout();
            }

            if ((webviewMask & WebviewMask.IME) != 0) {
                insetTypeMask |= WindowInsetsCompat.Type.ime();
            }

            if ((webviewMask & WebviewMask.MANDATORY_SYSTEM_GESTURES) != 0) {
                insetTypeMask |= WindowInsetsCompat.Type.mandatorySystemGestures();
            }

            if ((webviewMask & WebviewMask.NAVIGATION_BARS) != 0) {
                insetTypeMask |= WindowInsetsCompat.Type.navigationBars();
            }

            if ((webviewMask & WebviewMask.STATUS_BARS) != 0) {
                insetTypeMask |= WindowInsetsCompat.Type.statusBars();
            }

            if ((webviewMask & WebviewMask.SYSTEM_BARS) != 0) {
                insetTypeMask |= WindowInsetsCompat.Type.systemBars();
            }

            if ((webviewMask & WebviewMask.SYSTEM_GESTURES) != 0) {
                insetTypeMask |= WindowInsetsCompat.Type.systemGestures();
            }

            if ((webviewMask & WebviewMask.TAPPABLE_ELEMENT) != 0) {
                insetTypeMask |= WindowInsetsCompat.Type.tappableElement();
            }

            return insetTypeMask;
        }
    }

    private ArrayList<Listener> $listeners;
    private HashMap<String, Listener> $listenerMap;
    private final Object $listenerLock = new Object();
    private JSONObject buildInsetJson(WindowInsetsCompat insets) {
        try {
            float density = cordova.getActivity().getResources().getDisplayMetrics().density;

 
            Insets sys = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout()
            );

            int navBottom  = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;
            int gestBottom = Math.max(
                    insets.getInsets(WindowInsetsCompat.Type.systemGestures()).bottom,
                    insets.getInsets(WindowInsetsCompat.Type.tappableElement()).bottom
            );
            int cutBottom  = insets.getInsets(WindowInsetsCompat.Type.displayCutout()).bottom;
            int smartBottomPx = Math.max((gestBottom > 0 ? gestBottom : navBottom), cutBottom);

            JSONObject j = new JSONObject();
            j.put("top",    sys.top    / density);
            j.put("right",  sys.right  / density);
            j.put("bottom", !$isGestureOnly ? 0 : smartBottomPx / density);
            j.put("left",   sys.left   / density);
            return j;
        } catch (JSONException e) {
            return null;
        }
    }

    @Override
    protected void pluginInitialize() {
        if ($listeners == null) $listeners = new ArrayList<>();
        if ($listenerMap == null) $listenerMap = new HashMap<>();
        final Activity act = cordova.getActivity();
        act.runOnUiThread(() -> {

            WindowCompat.setDecorFitsSystemWindows(act.getWindow(), false);
            final View root = act.findViewById(android.R.id.content);


            ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
                synchronized ($insetCacheLock) {
                    $lastInsetJson = buildInsetJson(insets);
                    Insets navBars   = insets.getInsets(WindowInsetsCompat.Type.navigationBars());
                    Insets gestures  = insets.getInsets(WindowInsetsCompat.Type.systemGestures());
                    int navBottom  = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;
                    int gestBottom = Math.max(
                            insets.getInsets(WindowInsetsCompat.Type.systemGestures()).bottom,
                            insets.getInsets(WindowInsetsCompat.Type.tappableElement()).bottom
                    );
                    boolean hasNavBarPixels = navBars.bottom > 0;            
                    $isGestureOnly   = !hasNavBarPixels && gestures.bottom > 0;
                    
                    synchronized ($listenerLock) {
                        for (Listener listener : $listeners) {
                            listener.onInsetUpdate(insets);
                        }
                    }
                }
                return insets;
            });


            Handler handler = new Handler(Looper.getMainLooper());
            Runnable requestInsets = new Runnable() {
                @Override
                public void run() {
                    WindowInsetsCompat insets = ViewCompat.getRootWindowInsets(root);
                    if (insets != null) {
                        final int typesBase = WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout();

                        
                        Insets navBars   = insets.getInsets(WindowInsetsCompat.Type.navigationBars());
                        Insets gestures  = insets.getInsets(WindowInsetsCompat.Type.systemGestures());
                        boolean hasNavBarPixels = navBars.bottom > 0;          
                        $isGestureOnly   = !hasNavBarPixels && gestures.bottom > 0;
                    
                    
  
                        synchronized ($insetCacheLock) {
                            $lastInsetJson = buildInsetJson(insets);
                            synchronized ($listenerLock) {
                                for (Listener listener : $listeners) {
                                    listener.onInsetUpdate(insets);
                                }
                            }
                        }
                    } else {
                        ViewCompat.requestApplyInsets(root);
                        handler.postDelayed(this, 50);
                    }
                }
            };

            handler.post(requestInsets);
        });
    }


    private void $createNewListener(CallbackContext callback, JSONArray args) {
        ListenerConfiguration config = new ListenerConfiguration();


        try {
            if (args != null && args.length() > 0 && !args.isNull(0)) {
                JSONObject params = args.optJSONObject(0);
                if (params != null) {
                    if (params.has("mask")) config.mask = params.getInt("mask");
                    if (params.has("includeRoundedCorners")) config.includeRoundedCorners = params.getBoolean("includeRoundedCorners");
                    if (params.has("stable")) config.stable = params.getBoolean("stable");
                    if (params.has("includeIme")) config.includeIme = params.getBoolean("includeIme");
                }
            }
        } catch (JSONException ignored) {
 
        }

        Listener listener = new Listener(cordova.getActivity(), callback, config);
        synchronized ($listenerLock) {
            $listeners.add(listener);
            $listenerMap.put(listener.getID(), listener);
        }


        try {
            JSONObject responseData = new JSONObject();
            responseData.put("type", "init");
            responseData.put("id", listener.getID());
            responseData.put("data", $lastInsetJson);
            PluginResult initRes = new PluginResult(Status.OK, responseData);
            initRes.setKeepCallback(true);
            callback.sendPluginResult(initRes);
        } catch (JSONException e) {
            callback.error(e.getMessage());
            return;
        }

            JSONObject cached;
            synchronized ($insetCacheLock) {
                cached = $lastInsetJson;
            }

            if (cached != null) {
                try {
                    JSONObject update = new JSONObject();
                    update.put("type", "update");
                    update.put("id", listener.getID());
                    update.put("data", cached);
                    PluginResult upd = new PluginResult(Status.OK, update);
                    upd.setKeepCallback(true);
                    callback.sendPluginResult(upd);
                } catch (JSONException ignored) {}
            } else {
                cordova.getActivity().runOnUiThread(() -> {

                    View root = cordova.getActivity().findViewById(android.R.id.content);
                    Handler handler = new Handler(Looper.getMainLooper());
                    Runnable checkInsets = new Runnable() {
                        @Override
                        public void run() {
                            WindowInsetsCompat now = ViewCompat.getRootWindowInsets(root);
                            if (now != null) {
                                listener.onInsetUpdate(now);
                            } else {
            
                                handler.postDelayed(this, 50);
                            }
                        }
                    };
                    handler.post(checkInsets);
                });
            }
    }

    @Override
    public void initialize(org.apache.cordova.CordovaInterface cordova,
                           org.apache.cordova.CordovaWebView webView) {
        super.initialize(cordova, webView);
        WindowCompat.setDecorFitsSystemWindows(cordova.getActivity().getWindow(), false);
    }
    @Override public void onDestroy() {
        synchronized ($listenerLock) {
            $listeners.clear();
            $listenerMap.clear();
        }
        super.onDestroy();
    }

    private void $freeListener(CallbackContext callback, JSONArray args) throws JSONException {
        String id = args.getString(0);

        synchronized ($listenerLock) {
            Listener listener = $listenerMap.remove(id);
            if (listener != null) {
                $listeners.remove(listener);
            }
        }

        callback.success();
    }

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callback) throws JSONException, NumberFormatException {
        if (action.equals("create")) {
            $createNewListener(callback, args);
            return true;
        }
        else if (action.equals("delete")) {
            $freeListener(callback, args);
            return true;
        }

        return false;
    }
}

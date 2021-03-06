package com.tombarrasso.android.wp7bar;

/*
 * BarService.java
 *
 * Copyright (C) 2011 Thomas James Barrasso
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// Android Packages
import android.content.Context;
import android.content.res.Resources;
import android.app.Activity;
import android.app.Service;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.IBinder;
import android.os.Binder;
import android.view.View;
import android.view.WindowManager;
import android.graphics.PixelFormat;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.util.Log;
import android.graphics.Color;
import android.util.DisplayMetrics;
import android.content.ComponentName;
import android.view.View.OnLongClickListener;
import android.content.BroadcastReceiver;
import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.util.SparseArray;
import android.view.accessibility.AccessibilityEvent;
import android.os.Handler;
import android.os.Message;

// UI Packages
import com.tombarrasso.android.wp7ui.statusbar.*;
import com.tombarrasso.android.wp7ui.widget.WPDigitalClock;
import com.tombarrasso.android.wp7ui.extras.MonitorActivityThread;

// Java Packages
import java.util.List;
import java.util.ArrayList;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * This is a {@link Service} designed to open a {@link Window}
 * of type {@link TYPE_SYSTEM_ALERT}. Such a window with the
 * {@link FLAG_LAYOUT_IN_SCREEN} flag displays above all other
 * windows. This provides a window with which we can add views
 * to, ie. a {@link StatusBarView}. This service displays a
 * notification in default system status bar, and when expanded
 * you can click an ongoining {@link Notification} you are taken
 * to {@link HomeActivity} which allows you to stop the service,
 * remove the status bar, or edit settings. Includes built-in
 * support for screen on/ off and unlock intents to disallow
 * expansion in a lockscreen (this may vary based on the lockscreen
 * currently used).<br /><br />
 * <u>Change Log:</u>
 * <b>Version 1.01</b>
 * <ul>
 *	<li>Now using {@link setForeground}/ {@link startForeground} to ensure that the {@link Service} remains running even in low-memory conditions.</li>
 *	<li>System status bar height determination is now moved to {@link StatucBarView} and using a {@link Map} for the fallback for all pixel densities.</li>
 *	<li>{@link WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY} used when "click to drop" is disabled.</li>
 *	<li>Added support for WidgetLocker, NoLock, No Lock Screen, Ripple Lock, and Agile Lock.</li>
 * </ul>
 * <b>Version 1.02</b>
 * <ul>
 *	<li>Fixed a {@link NullPointerException} in {@link ScreenReceiver}.</li>
 *	<li>Added (then commented out) {@link AccessibilityService} stuff.</li>
 * </ul>
 *
 * @author		Thomas James Barrasso <contact @ tombarrasso.com>
 * @since		10-28-2011
 * @version		1.02
 * @category	{@link Service}
 */

public final class BarService extends Service
{
	public static final String TAG = BarService.class.getSimpleName(),
							   	  PACKAGE = BarService.class.getPackage().getName();

	/**
	 * Sticky {@link Intent} used to notify other applications
	 * when StatusBar+ has been enabled and disabled.
	 */
	public static final String ACTION_ENABLED = PACKAGE + ".intent.action.ENABLED",
							   ACTION_DISABLED = PACKAGE + ".intent.action.DISABLED";

	// Intents for enabled/ disabled actions.
	private static final Intent ENABLED_INTENT = new Intent(ACTION_ENABLED),
								DISABLED_INTENT = new Intent(ACTION_DISABLED);

	// Action for when WidgetLocker is locked/ unlocked.
	private static final String ACTION_WIDGETLOCKER_UNLOCKED = 
		"com.teslacoilsw.widgetlocker.intent.UNLOCKED";
	private static final String ACTION_WIDGETLOCKER_LOCKED = 
		"com.teslacoilsw.widgetlocker.intent.LOCKED";

	// Action for when NoLock is unlocked.
	private static final String ACTION_NOLOCK_UNLOCKED =
		"org.jraf.android.nolock.ACTION_UNLOCKED";
	// Action for when NoLock is locked.
	private static final String ACTION_NOLOCK_LOCKED =
		"org.jraf.android.nolock.ACTION_LOCKED";
	// Action for when No Lock Screen's state changes.
	private static final String ACTION_NOLOCKSCREEN_LOCKSTATE =
		"com.futonredemption.nokeyguard.lockstate";
	// Action for when Ripple/ Agile lock is unlocked.
	private static final String ACTION_RIPPLELOCK_UNLOCKED = 
		"com.nanoha.UNLOCKED";

	// Unique Identification Number for the Notification.
    // We use it on Notification start, and to cancel it.
    private static final int NOTIFICATION = R.string.service_started;
	public static final int FLAG_ALLOW_LOCK_WHILE_SCREEN_ON = 0x00000001;

	// We'll need these things later.
	private NotificationManager mNM;
	private StatusBarView mBarView;
	private Preferences mPrefs;

	// Initialize the intent filter statically.
	private static final IntentFilter mFilter =
		new IntentFilter(Intent.ACTION_SCREEN_ON);
	private static final IntentFilter mLockFilter =
		new IntentFilter(Intent.ACTION_USER_PRESENT);
	static {
        mFilter.addAction(Intent.ACTION_SCREEN_OFF);
		mLockFilter.addAction(ACTION_WIDGETLOCKER_UNLOCKED);
		mLockFilter.addAction(ACTION_NOLOCK_UNLOCKED);
		mLockFilter.addAction(ACTION_NOLOCKSCREEN_LOCKSTATE);
		mLockFilter.addAction(ACTION_RIPPLELOCK_UNLOCKED);
	};

	private final ScreenReceiver mScreenReceiver = new ScreenReceiver();
	private final PresenceReceiver mPresenceReceiver = new PresenceReceiver();

	public static boolean wasScreenOn = true;

	/**
     * @see IStatusBarService
	 *
	 * Implementation of the public API for accessing and
	 * controlling the custom status bar. Bind to the remote
	 * service and attempt to call any of these methods.
     */
    public final IStatusBarService.Stub mBinder =
		new IStatusBarService.Stub()
	{
		/**
		 * Makes the status bar invisible.
	 	 */
		public void hide()
		{
			mBarView.setVisibility(View.GONE);
		}

		/**
		 * Makes the status bar visible.
		 */
		public void show()
		{
			mBarView.setVisibility(View.VISIBLE);
		}

		/**
		 * Toggles between {@link show} and {@link hide}.
		 */
		public void toggle()
		{
			if (mBarView.getVisibility() == View.VISIBLE)
				hide();
			else
				show();
		}

		/**
		 * Drops the status bar icons.
		 */
		public void drop()
		{
			mBarView.drop();
		}

		/**
		 * @return True if the icons are dropped.
		 */
		public boolean isDropped()
		{
			return mBarView.isDropped();
		}

		/**
		 * Completly removes the status bar from
		 * the SYSTEM ALERT WINDOW.
		 */
		public void destroy()
		{
			destroyStatusBar();
		}

		/**
		 * Creates a status bar and adds it to a
		 * SYSTEM ALERT WINDOW above the default
		 * status bar.
	 	 */
		public void create()
		{
			createStatusBar();
		}

		/**
		 * Disables the status bar's expansion.
		 */
		public void disableExpand()
		{
			mBarView.setExpand(false);
		}

		/**
		 * Enables the status bar's expansion.
		 */
		public void enableExpand()
		{
			mBarView.setExpand(true);
		}

		/**
		 * @return The color of the icons.
		 */
		public int getIconColor()
		{
			return mPrefs.getIconColor();
		}

		/**
		 * @return The background color of the {@limk StatusBarView}.
		 */
		public int getBackgroundColor()
		{
			return mBarView.getBackgroundColor();
		}

		/**
		 * @return The height of the {@link StatusBarView}.
		 */
		public int getHeight()
		{
			return mBarView.getHeight();
		}

		/**
		 * Makes the background color transparent.
		 */
		public void makeBackgroundTransparent()
		{
			mBarView.setBackgroundColor(Color.TRANSPARENT);
		}

		/**
		 * Sets the background color of the status bar
		 * to the user's current preference.
		 */
		public void restoreBackgroundColor()
		{
			mBarView.setBackgroundColor(mPrefs.getBackgroundColor());
		}
    };

	// Reflected methods for entering the foreground.
	private static final Class[] mStartForegroundSignature = new Class[] {
        int.class, Notification.class};
    private static final Class[] mStopForegroundSignature = new Class[] {
        boolean.class};
	private static final Class mClass = Service.class;
    
    private static Method mStartForeground;
    private static Method mStopForeground;
	private static Method mSetForeground;

	// Obtain methods in a static context for effeciancy.
	static {
		getForegroundMethods();
	};

	/**
	 * Obtain {@link startForeground} and such {@link Method}s
	 * using Reflection to avoid compatiblity issues. These
	 * methods were introduced in Android 2.1+ API 7, and removed
	 * in Android 3.0+ API 11 (without warning.).
	 */
	public static final void getForegroundMethods()
	{
		try
		{
            mStartForeground = mClass.getMethod("startForeground",
                    mStartForegroundSignature);
        }
		catch (NoSuchMethodException e)
		{
            // Running on an older platform.
            mStartForeground = null;
        }

		try
		{
            mStopForeground = mClass.getMethod("stopForeground",
                    mStopForegroundSignature);
        }
		catch (NoSuchMethodException e)
		{
            // Running on an older platform.
            mStopForeground = null;
        }

		try
		{
            mSetForeground = mClass.getMethod("setForeground", mStopForegroundSignature);
        }
		catch (NoSuchMethodException e)
		{
            // Running on an older platform.
            mSetForeground = null;
        }
	}

    /**
     * This is a wrapper around the new startForeground method, using the older
     * APIs if it is not available.
     */
    private final void startForegroundCompat(
		int id, Notification notification)
	{
        // If we have the new startForeground API, then use it.
        if (mStartForeground != null)
		{
            try
			{
                mStartForeground.invoke(this, new Object[] { Integer.valueOf(id), notification });
            }
			catch (InvocationTargetException e)
			{
                // Should not happen.
                Log.w(TAG, "Unable to invoke startForeground", e);
            }
			catch (IllegalAccessException e)
			{
                // Should not happen.
                Log.w(TAG, "Unable to invoke startForeground", e);
            }
            return;
        }
        
        // Fall back on the old API.
        callSetForeground(true);
        mNM.notify(id, notification);
    }
    
    /**
     * This is a wrapper around the new stopForeground method, using the older
     * APIs if it is not available.
     */
    private final void stopForegroundCompat(int id)
	{
        // If we have the new stopForeground API, then use it.
        if (mStopForeground != null)
		{
            try
			{
                mStopForeground.invoke(this, new Object[] { Boolean.TRUE });
            }
			catch (InvocationTargetException e)
			{
                // Should not happen.
                Log.w(TAG, "Unable to invoke stopForeground", e);
            }
			catch (IllegalAccessException e)
			{
                // Should not happen.
                Log.w(TAG, "Unable to invoke stopForeground", e);
            }
            return;
        }
        
        // Fall back on the old API.  Note to cancel BEFORE changing the
        // foreground state, since we could be killed at that point.
        mNM.cancel(id);
        callSetForeground(false);
    }

   /**
	* Will call the "setForeground" method if available using reflection,
	* since the method has been removed completely in Android 3.0 and
	* above.
	* @param foreground should service run as a foreground service?
	*/
	public final void callSetForeground(boolean foreground)
	{
		try
		{
			mSetForeground.invoke(this, new Object[] { (Boolean) foreground });
		}
		catch (IllegalAccessException e)
		{
			// Should not happen.
			Log.w(TAG, "Unable to invoke setForeground", e);
		}
		catch (InvocationTargetException e)
		{
			// Should not happen.
			Log.w(TAG, "Unable to invoke setForeground", e);
		}
	}

	/**
	 * Removes the {@link StatusBarView} from the {@link Window}
	 * and detached all indicator listeners.
	 */
	private final void destroyStatusBar()
	{
		// Remove the view from the window.
		if(mBarView != null)
	    {
			final WindowManager mWM = (WindowManager) getSystemService(WINDOW_SERVICE);
	        mWM.removeView(mBarView);
	        mBarView = null;
	    }

		removeListeners();
	}

	/**
	 * Removes all {@link BroadcastReceivers} and such
	 * to prevent them from being leaked.
	 */
	private void removeListeners()
	{
		// Make an array list with all listeners.
		final ArrayList<StateListener> mListeners =
			new ArrayList<StateListener>();

		final Context mContext = getApplicationContext();		

		// Only add listeners that have been used before.
		if (BatteryListener.hasInitialised())
			 mListeners.add((StateListener) BatteryListener.getInstance(mContext));
		if (BluetoothListener.hasInitialised())
			 mListeners.add((StateListener) BluetoothListener.getInstance(mContext));
		if (PhoneListener.hasInitialised())
			 mListeners.add((StateListener) PhoneListener.getInstance(mContext));
		if (RingerListener.hasInitialised())
			 mListeners.add((StateListener) RingerListener.getInstance(mContext));
		if (TimeListener.hasInitialised())
			 mListeners.add((StateListener) TimeListener.getInstance(mContext));
		if (WifiListener.hasInitialised())
			 mListeners.add((StateListener) WifiListener.getInstance(mContext));
		if (LanguageListener.hasInitialised())
			 mListeners.add((StateListener) LanguageListener.getInstance(mContext));
		
		// Close all listeners.
		// Check against null and use parameter-less
		// method to avoid unsafe/ unchecked warning.
		for (StateListener mListener : mListeners)
			if (mListener != null)
				mListener.close();
	}

	// Flags used in the creation of a new window.
	private static final int mFlags = 
		WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
		WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
		WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
		WindowManager.LayoutParams.FLAG_TOUCHABLE_WHEN_WAKING |
		WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH |
		WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;

	/**
	 * Creates the {@link StatusBarView} and adds it
	 * to its own {@link Window} of {@link TYPE_SYSTEM_ERROR}.
	 */
	private final void createStatusBar()
	{
		// Attach this View using WindowManager.
		final WindowManager mWM = (WindowManager) getSystemService(WINDOW_SERVICE);
		final LayoutInflater mLI = (LayoutInflater)
			getSystemService(Context.LAYOUT_INFLATER_SERVICE);

		if (mBarView == null)
		{
			// Use a TYPE_SYSTEM_OVERLAY when click to drop is
			// disabled. This allows it to hover above even the
			// system lockscreen, but it cannot consume touch events.
			final WindowManager.LayoutParams mParams =
				new WindowManager.LayoutParams(
					WindowManager.LayoutParams.FILL_PARENT,
					StatusBarView.getSystemStatusBarHeight(this),
					((mPrefs.isDropEnabled()) ?
					WindowManager.LayoutParams.TYPE_SYSTEM_ERROR : 
           			WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY),
					((mPrefs.isDropEnabled()) ? mFlags : (mFlags |
					WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)),
					PixelFormat.TRANSLUCENT);

			// Be sure that we are starting at (0, 0).
			mParams.gravity = Gravity.TOP | Gravity.FILL_HORIZONTAL;

			// Add the window title noticable in HierarchyViewer.
			mParams.setTitle(getString(R.string.window_title));
			mParams.packageName = PACKAGE;

			// Inflate the status bar layout.
			mBarView = (StatusBarView) mLI.inflate(R.layout.statusbar, null);

			// Set the colors based on the user's preferences.
			// These NEED to happen before we add them to the
			// {@link Window}. If not we will get {@link RemoteException}
			// because once the views are added we cannot manipulate
			// them, like changing their color/ background color.
			mBarView.setBackgroundColor(mPrefs.getBackgroundColor());
			mBarView.setAllColors(mPrefs.getIconColor());

			// Set whether the icons should drop or not.
			if (!mPrefs.isDropEnabled())
				mBarView.setDropAllowed(false);

			// Set whether swipe to display the system notifications or not.
			if (!mPrefs.isSwipeEnabled())
				mBarView.setExpand(false);
		
			// Use the drop duration saved in Preferences.
			mBarView.setDropDuration(mPrefs.getDropDuration());

			final ArrayList<String> mIconKeys = Preferences.getIconKeys();
			for (int i = 0, e = mBarView.getChildCount(); i < e; ++i)
			{
				final View mChild = mBarView.getChildAt(i);

				for (String mKey : mIconKeys)
				{
					final boolean mKeyEnabled = mPrefs.getBoolean(mKey, true);
					if (!mKeyEnabled)
					{
						// Hide all icons that are set to do so.
						if ((Preferences.KEY_ICON_SIGNAL.equals(mKey) &&
							mChild instanceof SignalView))
							mChild.setVisibility(View.GONE);
						else if ((Preferences.KEY_ICON_DATA.equals(mKey) &&
							mChild instanceof DataView))
							mChild.setVisibility(View.GONE);
						else if ((Preferences.KEY_ICON_CARRIER.equals(mKey) &&
							mChild instanceof CarrierView))
							mChild.setVisibility(View.GONE);
						else if ((Preferences.KEY_ICON_ROAMING.equals(mKey) &&
							mChild instanceof RoamingView))
							mChild.setVisibility(View.GONE);
						else if ((Preferences.KEY_ICON_WIFI.equals(mKey) &&
							mChild instanceof WifiView))
							mChild.setVisibility(View.GONE);
						else if ((Preferences.KEY_ICON_BLUETOOTH.equals(mKey) &&
							mChild instanceof BluetoothView))
							mChild.setVisibility(View.GONE);
						else if ((Preferences.KEY_ICON_RINGER.equals(mKey) &&
							mChild instanceof RingerView))
							mChild.setVisibility(View.GONE);
						else if ((Preferences.KEY_ICON_LANGUAGE.equals(mKey) &&
							mChild instanceof LanguageView))
							mChild.setVisibility(View.GONE);
						else if ((Preferences.KEY_ICON_BATTERY_PERCENT
							.equals(mKey) && mChild instanceof BatteryPercent))
							mChild.setVisibility(View.GONE);
						else if ((Preferences.KEY_ICON_BATTERY.equals(mKey) &&
							mChild instanceof BatteryView))
							mChild.setVisibility(View.GONE);
						else if ((Preferences.KEY_ICON_TIME.equals(mKey) &&
							mChild instanceof WPDigitalClock))
							mChild.setVisibility(View.GONE);
					}
				}
			}

			mWM.addView(mBarView, mParams);
		}
	}

	// Packages to ignore changes to.
	/*private static final String[] EXCLUDED_PACKAGES =
	{
		"android.widget.",
		"com.android.internal.view.",
		"com.tombarrasso.android.wp7ui."
	};

	private boolean mPrevFullscreen = false;
	private String mPrevActivity = null;

	// Hide/ show the status bar based on whether or
	// not the current window is full screen,
	@Override
    public void onAccessibilityEvent(AccessibilityEvent event)
	{
		final boolean mFullscreen = event.isFullScreen();
		final String mActivity = "" + event.getClassName();

		Log.v(TAG, mActivity + " = " + Boolean.toString(mFullscreen));

		// Check against exclusions first.
		for (final String mPackage : EXCLUDED_PACKAGES)
			if (mActivity.startsWith(mPackage)) return;

		if (mPrevFullscreen != mFullscreen)
		{
			if (mFullscreen)
				mBarView.setVisibility(View.GONE);
			else
				mBarView.setVisibility(View.VISIBLE);

			mPrevFullscreen = mFullscreen;
			mPrevActivity = mActivity;
		}
	}

	@Override
    public void onInterrupt()
	{

	}

	public static final int TYPE_WINDOW_CONTENT_CHANGED = 0x00000800;
	*/
	/**
     * Sets the {@link AccessibilityServiceInfo} which informs the system how to
     * handle this {@link AccessibilityService}.
     *
     * @param feedbackType The type of feedback this service will provide.
     * <p>
     *   Note: The feedbackType parameter is an bitwise or of all
     *   feedback types this service would like to provide.
     * </p>
     */
	/*
    private void setServiceInfo(int feedbackType)
	{
		final AccessibilityServiceInfo info = new AccessibilityServiceInfo();
		// We are interested in all types of accessibility events.
		info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED |
							TYPE_WINDOW_CONTENT_CHANGED;
		// We want to provide specific type of feedback.
		info.feedbackType = feedbackType;
		// We want to receive events in a certain interval.
		// info.notificationTimeout = EVENT_NOTIFICATION_TIMEOUT_MILLIS;
		// We want to receive accessibility events only from certain packages.
		// info.packageNames = PACKAGE_NAMES;
		setServiceInfo(info);
    }


	@Override
    public void onServiceConnected()
	{
        if (isInfrastructureInitialized) return;

		create();

		// Claim the events with which to listen to.
		setServiceInfo(AccessibilityServiceInfo.FEEDBACK_GENERIC);

		// We are in an initialized state now.
        isInfrastructureInitialized = true;
	}*/

	/** Flag if the infrastructure is initialized. */
    // private boolean isInfrastructureInitialized;

	private boolean isCreated = false;

	/**
	 * Creates the status bar and applies all necessary
	 * API calls, info, etc. Can only be called once
	 * per initialization.
	 */
	protected void create()
	{
		if (isCreated) return;

		// Remove the disabled intent and
		// broadcast the enabled intent.
		removeStickyBroadcast(DISABLED_INTENT);
		sendStickyBroadcast(ENABLED_INTENT);
	
		// Get an instance of the preferences.
		mPrefs = Preferences.getInstance(this);

		// Start monitoring when apps are opened.
		startMonitorThread();

		// Don't bother listening for screen on/ off
		// events unless the setting is enabled.
		if (mPrefs.isUsingBlacklist() || mPrefs.isExpandDisabled())
		{
			// Listen for screen on/ off.
			registerReceiver(mScreenReceiver, mFilter);
		}

		// Don't bother listening to unlock
		// events unless the setting is enabled.
		if (mPrefs.isExpandDisabled())
		{
			// Listen for unlock.
			registerReceiver(mPresenceReceiver, mLockFilter);
		}

        // Display a notification about us starting.
		// We put an icon in the status bar.
        showNotification();
		createStatusBar();

		isCreated = true;
	}

	@Override
	public void onCreate()
	{
		super.onCreate();

		create();
	}

	private boolean isDestroyed = false;
	
	/*@Override
    public boolean onUnbind(Intent intent)
	{
        if (isInfrastructureInitialized) {

            destroy();

            // We are not in an initialized state anymore.
            isInfrastructureInitialized = false;
        }

        return false;
    }*/

	/**
	 * Destroys the status bar, removes all listeners, etc.
	 * It can only be called once per initialization.
	 */
	private void destroy()
	{
		if (isDestroyed) return;

		// Remove the disabled intent and
		// broadcast the enabled intent.
		removeStickyBroadcast(ENABLED_INTENT);
		sendStickyBroadcast(DISABLED_INTENT);

		destroyStatusBar();

		// Stop running in the foreground and
		// cancel the status bar notification.
		stopForegroundCompat(NOTIFICATION);

		// Kill activity monitoring system.
		if (mThread != null)
			mThread.interrupt();

		isDestroyed = true;
	}

    @Override
    public void onDestroy()
	{
		destroy();

		super.onDestroy();
	}

	// Detects when the user has turned the screen on/ off.
	public final class ScreenReceiver extends BroadcastReceiver
	{
		@Override
		public void onReceive(Context context, Intent intent)
		{
			// Lets be safe, these can (and have) occured
			// when the status bar is just removed/ added
			// and the screen has just been turned on/ off.
			if (intent == null ||
				mPrefs == null ||
				mBarView == null) return;

			final String mAction = intent.getAction();
			if (mAction == null) return;

			// Disable status bar expansion when the screen is off.
		    if (mAction.equals(Intent.ACTION_SCREEN_OFF))
			{
				if (mPrefs.isExpandDisabled())
					mBarView.setExpand(false);

		        wasScreenOn = false;
		    }
			else if (mAction.equals(Intent.ACTION_SCREEN_ON))
			{
		        wasScreenOn = true;
		    }
			
			// Allow the {@link Thread} to sleep.
			if (mThread != null) mThread.setScreen(wasScreenOn);
		}
	}

	// Detects when the user has exited the lock screen.
	public class PresenceReceiver extends BroadcastReceiver
	{
		@Override
		public void onReceive(Context context, Intent intent)
		{
			// Let's be safe (see ScreenReciever)
			if (intent == null ||
				mPrefs == null ||
				mBarView == null) return;
			final String mAction = intent.getAction();
			if (mAction == null) return;

			// Make the status bar expandable when the device was unlocked.
			if ((mAction.equals(Intent.ACTION_USER_PRESENT) ||
				mAction.equals(ACTION_WIDGETLOCKER_UNLOCKED) ||
				mAction.equals(ACTION_NOLOCK_UNLOCKED) ||
				mAction.equals(ACTION_RIPPLELOCK_UNLOCKED)) || (
				mAction.equals(ACTION_NOLOCKSCREEN_LOCKSTATE) &&
				intent.getBooleanExtra("isActive", false)
				))
			{
				if (mPrefs.isExpandDisabled())
					mBarView.setExpand(true);
			}
		}
	}

	/**
	 * Bind to an instance of {@link IStatusBarService} remotely.
	 */
	@Override
    public IBinder onBind(Intent intent)
	{
        return mBinder;
    }

	private MonitorActivityThread mThread;

	/**
	 * Starts the {@link Thread} that monitors
	 * when an {@link Activity} is opened/ launched.
	 */
	private final void startMonitorThread()
	{
		// Only monitor {@link Activity}s if
		// the setting is enabled to do so.
		if (!mPrefs.isUsingBlacklist()) return;

		if (mThread != null)
                mThread.interrupt();
        
		mThread = new MonitorActivityThread(this);
		mThread.setActivityStartingListener(new MonitorActivityHandler(this));
		mThread.start();
    }

	@Override
	public int onStartCommand(Intent intent, int flags, int startId)
	{
        // We want this service to continue running until it is explicitly
        // stopped, so return sticky.
        return START_STICKY;
    }

	/**
     * Show a notification while this service is running.
     */
    private void showNotification()
	{
		// Get Notification and Activity Managers.
		if (mNM == null)
			mNM = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        final CharSequence mTitle = getText(R.string.notification_marquee);

        // Set the icon, scrolling text and timestamp.
        final Notification mNotif = new Notification(
			R.drawable.notification, mTitle, System.currentTimeMillis());
		
		// It is an ongoing serviec.
		mNotif.flags = Notification.FLAG_ONGOING_EVENT;

        // The PendingIntent to launch our activity if
		// the user selects this notification.
        final PendingIntent mIntent = PendingIntent.getActivity(this, 0,
                new Intent(this, HomeActivity.class), 0);

        // Set the info for the views that show in the notification panel.
        mNotif.setLatestEventInfo(this,
			getText(R.string.bar_service), mTitle, mIntent);

		// Notify the user and enter foreground.
		startForegroundCompat(NOTIFICATION, mNotif);
    }
}

package com.tombarrasso.android.wp7bar;

/*
 * HomeActivity.java
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
import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.content.Context;
import android.content.ServiceConnection;
import android.content.ComponentName;
import android.view.View;
import android.util.Log;
import android.app.Dialog;
import android.os.IBinder;
import android.os.RemoteException;
import android.widget.Checkable;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.text.method.LinkMovementMethod;
import android.graphics.Color;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;

// Java Packages
import java.lang.IllegalArgumentException;

// UI Packages
import com.tombarrasso.android.wp7ui.extras.Changelog;
import com.tombarrasso.android.wp7ui.app.WPDialog;
import com.tombarrasso.android.wp7ui.app.WPActivity;
import com.tombarrasso.android.wp7ui.WPTheme;
import com.tombarrasso.android.wp7ui.widget.WPThemeView;
import com.tombarrasso.android.wp7ui.widget.WPPivotControl;

// Color Picker Packages
import afzkl.development.mColorPicker.ColorPickerDialog;
import afzkl.development.mColorPicker.views.ColorPickerView.OnColorChangedListener;

/**
 * This {@link Activity} manages the preferences for the
 * applications and talks to the GUI. It handles click
 * events and starts the corresponding service or change in UI.
 *
 * @author		Thomas James Barrasso <contact @ tombarrasso.com>
 * @since		10-13-2011
 * @version		1.0
 * @category	{@link Activity}
 */

public class HomeActivity extends WPActivity
{
	public static final String TAG = HomeActivity.class.getSimpleName(),
							   PACKAGE = HomeActivity.class.getPackage().getName();

	// George Orwell would be proud.
	private static final int DIALOG_CHANGELOG = 1984;
	private final Intent mServiceIntent = new Intent();

	// Pivot screen iDs.
	private static final int SCREEN_ONE = 0,
								SCREEN_TWO = 1;

	// Views for settings and such.
	private View mEnableToggle,
				 mExpandToggle,
				 mBootToggle,
				 mDropToggle,
				 mBackColorView,
				 mIconColorView,
				 mBackgroundDisplay,
				 mIconDisplay,
				 mIconToggle,
				 mAppsToggle,
				 mAppHideToggle,
				 mSwipeToggle,
				 mChangeLog;
	private TextView mAbout;
	private Spinner mDropSpinner;
	private WPPivotControl mPivot;

	// Preferences and service,
	private Preferences mPrefs;
	private boolean mIsBound = false;

	/**
	 * Recursively set the text of all {@link TextView}s.
	 */
	public final void setTextColor(View mGroup, int color)
	{
		if (mGroup == null) return;

		if (mGroup instanceof ViewGroup)
		{
			final ViewGroup mVGroup = (ViewGroup) mGroup;
			for (int i = 0, e = mVGroup.getChildCount(); i < e; ++i)
				setTextColor(mVGroup.getChildAt(i), color);
		}
		else if (mGroup instanceof TextView)
		{
			((TextView) mGroup).setTextColor(color);
		}
	}

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
		// This NEEDS to be in the initial activity
		// to avoid themeColor being null.
		WPTheme.setDefaultThemeColor();
	
		super.shouldRemoveStatusBarListeners(false);

		mServiceIntent.setClassName(BarService.PACKAGE, BarService.PACKAGE + "." + BarService.TAG);
		mPrefs = Preferences.getInstance(this);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

		// Set up the pivot.
		mPivot = (WPPivotControl) findViewById(R.id.homePivot);
		mPivot.setTab(SCREEN_ONE, R.string.settings)
			  .setTab(SCREEN_TWO, R.string.about);
	
		// Set light/ dark based on the theme.
		final View mRoot = findViewById(R.id.root);
		if (WPTheme.isDark() && mRoot != null)
		{
			mRoot.setBackgroundColor(Color.BLACK);
			setTextColor(mRoot, Color.WHITE);
		}
		else if (mRoot != null)
		{
			mRoot.setBackgroundColor(Color.WHITE);
			setTextColor(mRoot, Color.BLACK);
		}

		// Get rid of the overscroll glow.
		WPThemeView.setOverScrollMode(mRoot, WPThemeView.OVER_SCROLL_NEVER);

		// Find the toggle for the status bar.
		mEnableToggle = findViewById(R.id.enable_toggle);
		mExpandToggle = findViewById(R.id.expand_toggle);
		mBootToggle = findViewById(R.id.boot_toggle);
		mDropToggle = findViewById(R.id.drop_toggle);
		mBackColorView = findViewById(R.id.background_color_preference);
		mIconColorView = findViewById(R.id.icon_color_preference);
		mIconDisplay = findViewById(R.id.icon_pref);
		mBackgroundDisplay = findViewById(R.id.background_pref);
		mIconToggle = findViewById(R.id.icon_toggle);
		mAppsToggle = findViewById(R.id.apps_toggle);
		mAppHideToggle = findViewById(R.id.hide_toggle);
		mSwipeToggle = findViewById(R.id.swipe_toggle);
		mDropSpinner = (Spinner) findViewById(R.id.drop_spinner);
		mChangeLog = findViewById(R.id.changelog);
		mAbout = (TextView) findViewById(R.id.about_description);

		// Set spinner for drop duration.
		final ArrayAdapter<CharSequence> mAdapter = ArrayAdapter.createFromResource(
            this, R.array.drop_times, android.R.layout.simple_spinner_item);
		mAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		mDropSpinner.setAdapter(mAdapter);

		// Set the listener for when the spinner changes.
		final int[] mDurations = getResources().getIntArray(R.array.drop_durations);
		
		// Set the default value of the spinner.
		final int mDuration = mPrefs.getDropDuration();
		int i = 0;
		for (i = 0; i < mDurations.length; ++i)
			if (mDurations[i] == mDuration)
				break;
		mDropSpinner.setOnItemSelectedListener(mSpinnerListener);

		// Set the spinner's default selection.
		mDropSpinner.setSelection(i);

		mAppsToggle.setEnabled(mPrefs.isUsingBlacklist());

		// Set listener for icon and app buttons.
		mIconToggle.setOnClickListener(new LaunchClickListener(IconActivity.class, HomeActivity.this));
		mAppsToggle.setOnClickListener(new LaunchClickListener(BlacklistActivity.class, HomeActivity.this));

		// Set the display colors from preferences.
		mBackgroundDisplay.setBackgroundColor(mPrefs.getBackgroundColor());
		mIconDisplay.setBackgroundColor(mPrefs.getIconColor());

		// Set Click Listeners
		mBackColorView.setOnClickListener(mColorClick);
		mIconColorView.setOnClickListener(mColorClick);

		// Set initially whether or not the service is running.
		if (mEnableToggle instanceof Checkable)
			((Checkable) mEnableToggle).setChecked((mIsBound) ? mIsBound : mPrefs.isServiceRunning());

		// Set initially whether or not swipe is enabled.
		if (mSwipeToggle instanceof Checkable)
			((Checkable) mSwipeToggle).setChecked(mPrefs.isSwipeEnabled());

		if (!mPrefs.isDropEnabled())
		{
			// If drop is disabled then we do not intercept touch
			// events so swipe to display system notifications
			// cannot be controlled.
			mPrefs.setSwipe(true);

			// Set initially whether or not swipe is enabled.
			if (mSwipeToggle instanceof Checkable)
				((Checkable) mSwipeToggle).setChecked(true);

			// Disable swipe if "Click to drop" is disabled.
			mSwipeToggle.setEnabled(false);
		}

		// Set initially whether or expansion is automatically disabled.
		if (mExpandToggle instanceof Checkable)
			((Checkable) mEnableToggle).setChecked((mIsBound) ? mIsBound : mPrefs.isServiceRunning());
		
		// Set initially whether or not to set on boot.
		if (mBootToggle instanceof Checkable)
			((Checkable) mBootToggle).setChecked(mPrefs.isSetOnBoot());

		// Set initially whether or not to set on boot.
		if (mDropToggle instanceof Checkable)
			((Checkable) mDropToggle).setChecked(mPrefs.isDropEnabled());

		// Set initially whether or not to use a blacklist.
		if (mAppHideToggle instanceof Checkable)
			((Checkable) mAppHideToggle).setChecked(mPrefs.isUsingBlacklist());

		// Set these listeners AFTER determing the initial values,
		// lest we end up with an infinite loop!

		// If it is a check box listen for its changes.
		if (mEnableToggle instanceof CompoundButton)
			((CompoundButton) mEnableToggle).setOnCheckedChangeListener(mCheckListener);

		// If it is a check box listen for its changes.
		if (mBootToggle instanceof CompoundButton)
			((CompoundButton) mBootToggle).setOnCheckedChangeListener(mBootListener);

		// If it is a check box listen for its changes.
		if (mExpandToggle instanceof CompoundButton)
			((CompoundButton) mExpandToggle).setOnCheckedChangeListener(mExpandListener);

		// If it is a check box listen for its changes.
		if (mDropToggle instanceof CompoundButton)
			((CompoundButton) mDropToggle).setOnCheckedChangeListener(mDropListener);

		// If it is a check box listen for its changes.
		if (mAppHideToggle instanceof CompoundButton)
			((CompoundButton) mAppHideToggle).setOnCheckedChangeListener(mBlacklistListener);

		// If it is a check box listen for its changes.
		if (mSwipeToggle instanceof CompoundButton)
			((CompoundButton) mSwipeToggle).setOnCheckedChangeListener(mSwipeListener);

		// Display Change Log.
		final Changelog mChangelog = new Changelog(this);
		if (mChangelog.firstRun())
		{
			// Attach listener to show vibration dialog.
			showDialog(DIALOG_CHANGELOG);
		}

		// Make description links clickable.
		mAbout.setMovementMethod(LinkMovementMethod.getInstance());

		// When clicked, display the change log.
		mChangeLog.setOnClickListener(
			new View.OnClickListener()
			{
				@Override
				public void onClick(View mView)
				{
					showDialog(DIALOG_CHANGELOG);
				}			
			}
		);
    }

	public static final SpinnerListener mSpinnerListener = new SpinnerListener();
	public static final class SpinnerListener implements OnItemSelectedListener
	{
		public void onItemSelected(AdapterView<?> parent, View view, int pos, long id)
		{
			final Preferences mPrefs = Preferences.getInstance(parent.getContext());
			final int[] mDurations = parent.getContext()
				.getResources().getIntArray(R.array.drop_durations);
			mPrefs.setDropDuration(mDurations[pos]);
		}

		public void onNothingSelected(AdapterView parent) {}
	}

	// Create dialog boxes!
	protected Dialog onCreateDialog(int id)
    {
    	final WPDialog mDialog = new WPDialog(this);
    	
    	switch(id)
    	{
			case DIALOG_CHANGELOG:
			{
				// Get the dialog for the change log.
				final Changelog mChangeLog = new Changelog(this);
				return mChangeLog.getLogDialog();
			}
		}

		return mDialog;
	}
	
	// Listener for when the button that takes the
	// user to the Activity to hide/ show icons is clicked.
	private static final class LaunchClickListener implements View.OnClickListener
	{
		private final Class mClass;
		private final Context mContext;

		public LaunchClickListener(Class mClass, Context mContext)
		{
			this.mClass = mClass;
			this.mContext = mContext;
		}

		@Override
		public void onClick(View mView)
		{
			// Launch the IconActivity.
			mContext.startActivity(new Intent(mContext, mClass));
		}
	};

	/**
	 * Listener for when a background color change has occured.
	 */
	private final OnColorChangedListener mBackgroundListener = 
		new OnColorChangedListener()
	{
		@Override
		public void onColorChanged(int color)
		{
			// Change the little box displaying the color,
			// and update the settings accordingly.
			mPrefs.setBackgroundColor(color);
			mBackgroundDisplay.setBackgroundColor(color);
		}
	};

	/**
	 * Listener for when an icon color change has occured.
	 */
	private final OnColorChangedListener mIconListener = 
		new OnColorChangedListener()
	{
		@Override
		public void onColorChanged(int color)
		{
			// Change the little box displaying the color,
			// and update the settings accordingly.
			mPrefs.setIconColor(color);
			mIconDisplay.setBackgroundColor(color);
		}
	};

	/**
	 * Listener for when one of the two color
	 * preferences has been clicked.
	 */
	private final View.OnClickListener mColorClick = 
		new View.OnClickListener()
	{
		@Override
		public void onClick(View mView)
		{
			// Background Color
			if (mView.equals(mBackColorView))
			{
				final ColorPickerDialog dialog = new ColorPickerDialog(HomeActivity.this, mPrefs.getBackgroundColor());
				dialog.setOnColorChangedListener(mBackgroundListener);
				dialog.show();
			}
			// Icon Color
			else if (mView.equals(mIconColorView))
			{
				final ColorPickerDialog dialog = new ColorPickerDialog(HomeActivity.this, mPrefs.getIconColor());
				dialog.setOnColorChangedListener(mIconListener);
				dialog.show();
			}
		}
	};

	/**
	 * Listener for when the checkbox is checked/ unchecked.
 	 */
	private final OnCheckedChangeListener mBlacklistListener = 
		new OnCheckedChangeListener()
	{
		public void onCheckedChanged(
			CompoundButton buttonView, boolean isChecked)
		{
			mAppsToggle.setEnabled(isChecked);
			mPrefs.setUsingBlacklist(isChecked);
		}
	};

	/**
	 * Listener for when the checkbox is checked/ unchecked.
 	 */
	private final OnCheckedChangeListener mSwipeListener = 
		new OnCheckedChangeListener()
	{
		public void onCheckedChanged(
			CompoundButton buttonView, boolean isChecked)
		{
			mPrefs.setSwipe(isChecked);
		}
	};

	/**
	 * Listener for when the checkbox is checked/ unchecked.
 	 */
	private final OnCheckedChangeListener mCheckListener =
		new OnCheckedChangeListener()
	{
		public void onCheckedChanged(
			CompoundButton buttonView, boolean isChecked)
		{
			// Toggle the service based on the checkbox.
			final boolean mServiceRunning = mPrefs.isServiceRunning();
			if (!mServiceRunning)
			{
				startService(mServiceIntent);
				bindService(mServiceIntent, mConnection, 0);
				mIsBound = true;
			}
			else
			{
				// Surrounded in the case that the service
				// has not actually been bound to.
				try {
					// Remember to destroy our resources.
					if (mConnection != null) {
						final IStatusBarService mService =
							mConnection.getService();
						if (mService != null) mService.destroy();
						if (mIsBound) unbindService(mConnection);
					}
					stopService(mServiceIntent);
					mIsBound = false;
				}
				catch(IllegalArgumentException e) {}
				catch(RemoteException re) {}

				if (mConnection != null)
					mConnection.nullifyService();
			}
		}
	};

	/**
	 * Listener for when the checkbox is checked/ unchecked.
 	 */
	private final OnCheckedChangeListener mBootListener =
		new OnCheckedChangeListener()
	{
		public void onCheckedChanged(
			CompoundButton buttonView, boolean isChecked)
		{
			mPrefs.setOnBoot(isChecked);
		}
	};

	/**
	 * Listener for when the checkbox is checked/ unchecked.
 	 */
	private final OnCheckedChangeListener mExpandListener =
		new OnCheckedChangeListener()
	{
		public void onCheckedChanged(
			CompoundButton buttonView, boolean isChecked)
		{
			mPrefs.setExpandDisabled(isChecked);
		}
	};

	/**
	 * Listener for when the checkbox is checked/ unchecked.
 	 */
	private final OnCheckedChangeListener mDropListener =
		new OnCheckedChangeListener()
	{
		public void onCheckedChanged(
			CompoundButton buttonView, boolean isChecked)
		{
			mPrefs.setDrop(isChecked);

			if (!isChecked)
			{
				// If drop is disabled then we do not intercept touch
				// events so swipe to display system notifications
				// cannot be controlled.
				mPrefs.setSwipe(true);

				// Set initially whether or not swipe is enabled.
				if (mSwipeToggle instanceof Checkable)
					((Checkable) mSwipeToggle).setChecked(true);
			}

			mSwipeToggle.setEnabled(isChecked);
		}
	};

	@Override
	public void onDestroy()
	{
		super.onDestroy();

		if (mConnection.getService() != null)
		{
			// Unbind the Service.
			try {
				unbindService(mConnection);
			}
			catch(IllegalArgumentException e) {}
			mIsBound = false;

			if (mConnection != null)
				mConnection.nullifyService();
		}
	}

	private static final BarServiceConnection mConnection =
		new BarServiceConnection();

	/**
     * Class for interacting with the main interface of the service.
     */
    public static final class BarServiceConnection implements ServiceConnection
	{
		private IStatusBarService mService = null;
		
		/**
		 * @return An instance of {@link IStatusBarService}
		 * or null if none exists.
		 */
		public final IStatusBarService getService()
		{
			return mService;
		}

		/**
	 	 * Sets {@link mService} to null.
		 */
		public final void nullifyService()
		{
			mService = null;
		}

        public void onServiceConnected(ComponentName className,
                IBinder service) {
            // This is called when the connection with the service has been
            // established, giving us the service object we can use to
            // interact with the service.  We are communicating with our
            // service through an IDL interface, so get a client-side
            // representation of that from the raw service object.
            mService = IStatusBarService.Stub.asInterface(service);
        }

        public void onServiceDisconnected(ComponentName className) {
            // This is called when the connection with the service has been
            // unexpectedly disconnected -- that is, its process crashed.
            mService = null;
        }
    };

}

/*
 * Copyright (C) 2019 Veli Tasalı
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */

package com.genonbeta.TrebleShot.service;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentValues;
import android.content.Intent;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.collection.ArrayMap;

import com.genonbeta.CoolSocket.CoolSocket;
import com.genonbeta.TrebleShot.R;
import com.genonbeta.TrebleShot.app.Service;
import com.genonbeta.TrebleShot.config.AppConfig;
import com.genonbeta.TrebleShot.config.Keyword;
import com.genonbeta.TrebleShot.database.AccessDatabase;
import com.genonbeta.TrebleShot.exception.AssigneeNotFoundException;
import com.genonbeta.TrebleShot.exception.ConnectionNotFoundException;
import com.genonbeta.TrebleShot.exception.DeviceNotFoundException;
import com.genonbeta.TrebleShot.exception.TransferGroupNotFoundException;
import com.genonbeta.TrebleShot.object.NetworkDevice;
import com.genonbeta.TrebleShot.object.TextStreamObject;
import com.genonbeta.TrebleShot.object.TransferGroup;
import com.genonbeta.TrebleShot.object.TransferObject;
import com.genonbeta.TrebleShot.util.AppUtils;
import com.genonbeta.TrebleShot.util.CommunicationBridge;
import com.genonbeta.TrebleShot.util.CommunicationNotificationHelper;
import com.genonbeta.TrebleShot.util.DynamicNotification;
import com.genonbeta.TrebleShot.util.FileUtils;
import com.genonbeta.TrebleShot.util.HotspotUtils;
import com.genonbeta.TrebleShot.util.NetworkDeviceLoader;
import com.genonbeta.TrebleShot.util.NetworkUtils;
import com.genonbeta.TrebleShot.util.NotificationUtils;
import com.genonbeta.TrebleShot.util.NsdDiscovery;
import com.genonbeta.TrebleShot.util.TransferUtils;
import com.genonbeta.TrebleShot.util.UpdateUtils;
import com.genonbeta.android.database.SQLQuery;
import com.genonbeta.android.database.SQLiteDatabase;
import com.genonbeta.android.database.exception.ReconstructionFailedException;
import com.genonbeta.android.framework.io.DocumentFile;
import com.genonbeta.android.framework.io.StreamInfo;
import com.genonbeta.android.framework.util.Interrupter;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.StreamCorruptedException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;

import fi.iki.elonen.NanoHTTPD;

public class CommunicationService extends Service
{
	public static final String TAG = "CommunicationService";

	public static final String ACTION_FILE_TRANSFER = "com.genonbeta.TrebleShot.action.FILE_TRANSFER";
	public static final String ACTION_CLIPBOARD = "com.genonbeta.TrebleShot.action.CLIPBOARD";
	public static final String ACTION_CANCEL_INDEXING = "com.genonbeta.TrebleShot.action.CANCEL_INDEXING";
	public static final String ACTION_DEVICE_APPROVAL = "com.genonbeta.TrebleShot.action.DEVICE_APPROVAL";
	public static final String ACTION_END_SESSION = "com.genonbeta.TrebleShot.action.END_SESSION";
	public static final String ACTION_START_TRANSFER = "com.genonbeta.intent.action.START_TRANSFER";
	public static final String ACTION_STOP_TRANSFER = "com.genonbeta.TrebleShot.transaction.action.CANCEL_JOB";
	public static final String ACTION_TOGGLE_FAST_MODE = "com.genonbeta.TrebleShot.transaction.action.TOGGLE_FAST_MODE";
	public static final String ACTION_REVOKE_ACCESS_PIN = "com.genonbeta.TrebleShot.transaction.action.REVOKE_ACCESS_PIN";
	public static final String ACTION_TOGGLE_HOTSPOT = "com.genonbeta.TrebleShot.transaction.action.TOGGLE_HOTSPOT";
	public static final String ACTION_REQUEST_HOTSPOT_STATUS = "com.genonbeta.TrebleShot.transaction.action.REQUEST_HOTSPOT_STATUS";
	public static final String ACTION_HOTSPOT_STATUS = "com.genonbeta.TrebleShot.transaction.action.HOTSPOT_STATUS";
	public static final String ACTION_DEVICE_ACQUAINTANCE = "com.genonbeta.TrebleShot.transaction.action.DEVICE_ACQUAINTANCE";
	public static final String ACTION_SERVICE_STATUS = "com.genonbeta.TrebleShot.transaction.action.SERVICE_STATUS";
	public static final String ACTION_TASK_STATUS_CHANGE = "com.genonbeta.TrebleShot.transaction.action.TASK_STATUS_CHANGE";
	public static final String ACTION_TASK_RUNNING_LIST_CHANGE = "com.genonbeta.TrebleShot.transaction.action.TASK_RUNNNIG_LIST_CHANGE";
	public static final String ACTION_REQUEST_TASK_STATUS_CHANGE = "com.genonbeta.TrebleShot.transaction.action.REQUEST_TASK_STATUS_CHANGE";
	public static final String ACTION_REQUEST_TASK_RUNNING_LIST_CHANGE = "com.genonbeta.TrebleShot.transaction.action.REQUEST_TASK_RUNNING_LIST_CHANGE";
	public static final String ACTION_INCOMING_TRANSFER_READY = "com.genonbeta.TrebleShot.transaction.action.INCOMING_TRANSFER_READY";
	public static final String ACTION_FAST_MODE_STATUS = "com.genonbeta.TrebleShot.transaction.action.FAST_MODE_STATUS";
	public static final String ACTION_REQUEST_FAST_MODE_STATUS = "com.genonbeta.TrebleShot.transaction.action.REQUEST_FAST_MODE_STATUS";
	public static final String ACTION_TOGGLE_WEBSHARE = "com.genonbeta.TrebleShot.transaction.action.TOGGLE_WEBSHARE";
	public static final String ACTION_WEBSHARE_STATUS = "com.genonbeta.TrebleShot.transaction.action.WEBSHARE_STATUS";
	public static final String ACTION_REQUEST_WEBSHARE_STATUS = "com.genonbeta.TrebleShot.transaction.action.REQUEST_WEBSHARE_STATUS";

	public static final String EXTRA_DEVICE_ID = "extraDeviceId";
	public static final String EXTRA_STATUS_STARTED = "extraStatusStarted";
	public static final String EXTRA_CONNECTION_ADAPTER_NAME = "extraConnectionAdapterName";
	public static final String EXTRA_REQUEST_ID = "extraRequestId";
	public static final String EXTRA_CLIPBOARD_ID = "extraTextId";
	public static final String EXTRA_GROUP_ID = "extraGroupId";
	public static final String EXTRA_IS_ACCEPTED = "extraAccepted";
	public static final String EXTRA_CLIPBOARD_ACCEPTED = "extraClipboardAccepted";
	public static final String EXTRA_HOTSPOT_ENABLED = "extraHotspotEnabled";
	public static final String EXTRA_HOTSPOT_DISABLING = "extraHotspotDisabling";
	public static final String EXTRA_HOTSPOT_NAME = "extraHotspotName";
	public static final String EXTRA_HOTSPOT_KEY_MGMT = "extraHotspotKeyManagement";
	public static final String EXTRA_HOTSPOT_PASSWORD = "extraHotspotPassword";
	public static final String EXTRA_TASK_CHANGE_TYPE = "extraTaskChangeType";
	public static final String EXTRA_TASK_LIST_RUNNING = "extraTaskListRunning";
	public static final String EXTRA_DEVICE_LIST_RUNNING = "extraDeviceListRunning";
	public static final String EXTRA_ENABLE = "extraEnable";
	public static final String EXTRA_TRANSFER_TYPE = "extraTransferType";

	public static final int TASK_STATUS_ONGOING = 0;
	public static final int TASK_STATUS_STOPPED = 1;

	private List<ProcessHolder> mActiveProcessList = new ArrayList<>();
	private CommunicationServer mCommunicationServer = new CommunicationServer();
	private WebShareServer mWebShareServer = null;
	private Map<Long, Interrupter> mOngoingIndexList = new ArrayMap<>();
	private ExecutorService mSelfExecutor = Executors.newFixedThreadPool(10);
	private NsdDiscovery mNsdDiscovery;
	private CommunicationNotificationHelper mNotificationHelper;
	private WifiManager.WifiLock mWifiLock;
	private MediaScannerConnection mMediaScanner;
	private HotspotUtils mHotspotUtils;

	private boolean mDestroyApproved = false;
	private boolean mFastMode = false;
	private boolean mPinAccess = false;

	@Override
	public IBinder onBind(Intent intent)
	{
		return null;
	}

	@Override
	public void onCreate()
	{
		super.onCreate();

		mNotificationHelper = new CommunicationNotificationHelper(getNotificationUtils());
		mNsdDiscovery = new NsdDiscovery(getApplicationContext(), getDatabase(), getDefaultPreferences());
		mMediaScanner = new MediaScannerConnection(this, null);
		mHotspotUtils = HotspotUtils.getInstance(this);
		mWifiLock = ((WifiManager) getApplicationContext()
				.getSystemService(Service.WIFI_SERVICE)
		)
				.createWifiLock(TAG);

		mMediaScanner.connect();
		mNsdDiscovery.registerService();

		if (getWifiLock() != null)
			getWifiLock().acquire();

		updateServiceState(getDefaultPreferences().getBoolean("trust_always", false));

		if (!AppUtils.checkRunningConditions(this) || !mCommunicationServer.start())
			stopSelf();

		if (getHotspotUtils() instanceof HotspotUtils.OreoAPI && Build.VERSION.SDK_INT >= 26)
			((HotspotUtils.OreoAPI) getHotspotUtils()).setSecondaryCallback(new WifiManager.LocalOnlyHotspotCallback()
			{
				@RequiresApi(api = Build.VERSION_CODES.O)
				@Override
				public void onStarted(WifiManager.LocalOnlyHotspotReservation reservation)
				{
					super.onStarted(reservation);

					sendHotspotStatus(reservation.getWifiConfiguration());

					if (getDefaultPreferences().getBoolean("hotspot_trust", false))
						updateServiceState(true);
				}
			});


		try {
			mWebShareServer = new WebShareServer(this, AppConfig.SERVER_PORT_WEBSHARE);
			mWebShareServer.setAsyncRunner(new WebShareServer.BoundRunner(
					Executors.newFixedThreadPool(AppConfig.WEB_SHARE_CONNECTION_MAX)));
		} catch (Throwable t) {
			Log.e(TAG, "Failed to start Web Share Server");
		}
	}

	@Override
	public int onStartCommand(Intent intent, int flags, final int startId)
	{
		super.onStartCommand(intent, flags, startId);

		if (intent != null)
			Log.d(TAG, "onStart() : action = " + intent.getAction());

		if (intent != null && AppUtils.checkRunningConditions(this)) {
			if (ACTION_FILE_TRANSFER.equals(intent.getAction())) {
				final String deviceId = intent.getStringExtra(EXTRA_DEVICE_ID);
				final long groupId = intent.getLongExtra(EXTRA_GROUP_ID, -1);
				final int notificationId = intent.getIntExtra(NotificationUtils.EXTRA_NOTIFICATION_ID, -1);
				final boolean isAccepted = intent.getBooleanExtra(EXTRA_IS_ACCEPTED, false);

				getNotificationHelper().getUtils().cancel(notificationId);

				try {
					final NetworkDevice device = new NetworkDevice(deviceId);
					getDatabase().reconstruct(device);

					TransferGroup group = new TransferGroup(groupId);
					getDatabase().reconstruct(group);

					TransferGroup.Assignee assignee = new TransferGroup.Assignee(groupId, deviceId,
							TransferObject.Type.INCOMING);
					getDatabase().reconstruct(assignee);

					final NetworkDevice.Connection connection = new NetworkDevice.Connection(assignee);
					getDatabase().reconstruct(connection);

					CommunicationBridge.connect(getDatabase(), new CommunicationBridge.Client.ConnectionHandler()
					{
						@Override
						public void onConnect(CommunicationBridge.Client client)
						{
							try {
								CoolSocket.ActiveConnection activeConnection = client.communicate(device, connection);

								activeConnection.reply(new JSONObject()
										.put(Keyword.REQUEST, Keyword.REQUEST_RESPONSE)
										.put(Keyword.TRANSFER_GROUP_ID, groupId)
										.put(Keyword.TRANSFER_IS_ACCEPTED, isAccepted)
										.toString());

								activeConnection.receive();
								activeConnection.getSocket().close();
							} catch (Exception e) {
								e.printStackTrace();
							}
						}
					});

					if (isAccepted)
						startTransferAsClient(groupId, deviceId, TransferObject.Type.INCOMING);
					else
						getDatabase().remove(group);
				} catch (Exception e) {
					e.printStackTrace();

					if (isAccepted)
						getNotificationHelper().showToast(R.string.mesg_somethingWentWrong);
				}
			} else if (ACTION_DEVICE_APPROVAL.equals(intent.getAction())) {
				String deviceId = intent.getStringExtra(EXTRA_DEVICE_ID);
				boolean isAccepted = intent.getBooleanExtra(EXTRA_IS_ACCEPTED, false);
				int notificationId = intent.getIntExtra(NotificationUtils.EXTRA_NOTIFICATION_ID, -1);

				getNotificationHelper().getUtils().cancel(notificationId);

				NetworkDevice device = new NetworkDevice(deviceId);

				try {
					getDatabase().reconstruct(device);
					device.isRestricted = !isAccepted;
					getDatabase().update(device);
				} catch (Exception e) {
					e.printStackTrace();
					return START_NOT_STICKY;
				}
			} else if (ACTION_CANCEL_INDEXING.equals(intent.getAction())) {
				int notificationId = intent.getIntExtra(NotificationUtils.EXTRA_NOTIFICATION_ID, -1);
				long groupId = intent.getLongExtra(EXTRA_GROUP_ID, -1);

				getNotificationHelper().getUtils().cancel(notificationId);

				Interrupter interrupter = getOngoingIndexList().get(groupId);

				if (interrupter != null)
					interrupter.interrupt();
			} else if (ACTION_CLIPBOARD.equals(intent.getAction()) && intent.hasExtra(EXTRA_CLIPBOARD_ACCEPTED)) {
				int notificationId = intent.getIntExtra(NotificationUtils.EXTRA_NOTIFICATION_ID, -1);
				long clipboardId = intent.getLongExtra(EXTRA_CLIPBOARD_ID, -1);
				boolean isAccepted = intent.getBooleanExtra(EXTRA_CLIPBOARD_ACCEPTED, false);

				TextStreamObject textStreamObject = new TextStreamObject(clipboardId);

				getNotificationHelper().getUtils().cancel(notificationId);

				try {
					getDatabase().reconstruct(textStreamObject);

					if (isAccepted) {
						((ClipboardManager) getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("receivedText", textStreamObject.text));
						Toast.makeText(this, R.string.mesg_textCopiedToClipboard, Toast.LENGTH_SHORT).show();
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			} else if (ACTION_END_SESSION.equals(intent.getAction())) {
				stopSelf();
			} else if (ACTION_START_TRANSFER.equals(intent.getAction()) && intent.hasExtra(EXTRA_GROUP_ID)
					&& intent.hasExtra(EXTRA_DEVICE_ID) && intent.hasExtra(EXTRA_TRANSFER_TYPE)) {
				long groupId = intent.getLongExtra(EXTRA_GROUP_ID, -1);
				String deviceId = intent.getStringExtra(EXTRA_DEVICE_ID);
				String typeString = intent.getStringExtra(EXTRA_TRANSFER_TYPE);

				try {
					TransferObject.Type type = TransferObject.Type.valueOf(typeString);
					ProcessHolder process = findProcessById(groupId, deviceId, type);

					if (process == null)
						startTransferAsClient(groupId, deviceId, type);
					else
						Toast.makeText(this, getString(R.string.mesg_groupOngoingNotice, process.object.name), Toast.LENGTH_SHORT).show();
				} catch (Exception e) {
					e.printStackTrace();
				}
			} else if (ACTION_STOP_TRANSFER.equals(intent.getAction()) && intent.hasExtra(EXTRA_GROUP_ID)
					&& intent.hasExtra(EXTRA_DEVICE_ID) && intent.hasExtra(EXTRA_TRANSFER_TYPE)) {
				int notificationId = intent.getIntExtra(NotificationUtils.EXTRA_NOTIFICATION_ID, -1);
				long groupId = intent.getLongExtra(EXTRA_GROUP_ID, -1);
				String deviceId = intent.getStringExtra(CommunicationService.EXTRA_DEVICE_ID);
				String typeString = intent.getStringExtra(EXTRA_TRANSFER_TYPE);

				try {
					TransferObject.Type type = TransferObject.Type.valueOf(typeString);
					ProcessHolder processHolder = findProcessById(groupId, deviceId, type);

					if (processHolder == null) {
						notifyTaskStatusChange(groupId, deviceId, type, TASK_STATUS_STOPPED);
						getNotificationHelper().getUtils().cancel(notificationId);
					} else {
						processHolder.notification = getNotificationHelper().notifyStuckThread(processHolder);

						if (!processHolder.interrupter.interrupted()) {
							processHolder.interrupter.interrupt(true);
						} else {
							try {
								if (processHolder.activeConnection != null
										&& processHolder.activeConnection.getSocket() != null)
									processHolder.activeConnection.getSocket().close();
							} catch (IOException e) {
								// do nothing
							}

							try {
								if (processHolder.activeConnection != null && processHolder.activeConnection.getSocket() != null)
									processHolder.activeConnection.getSocket().close();
							} catch (IOException e) {
								// do nothing
							}
						}
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			} else if (ACTION_TOGGLE_FAST_MODE.equals(intent.getAction())) {
				updateServiceState(!mFastMode);
			} else if (ACTION_TOGGLE_HOTSPOT.equals(intent.getAction())
					&& (Build.VERSION.SDK_INT < 23 || Settings.System.canWrite(this))) {
				setupHotspot();
			} else if (ACTION_REQUEST_HOTSPOT_STATUS.equals(intent.getAction())) {
				sendHotspotStatus(getHotspotUtils().getConfiguration());
			} else if (ACTION_SERVICE_STATUS.equals(intent.getAction())
					&& intent.hasExtra(EXTRA_STATUS_STARTED)) {
				boolean startRequested = intent.getBooleanExtra(EXTRA_STATUS_STARTED, false);

				mDestroyApproved = !startRequested && !hasOngoingTasks() && (mWebShareServer == null
						|| !mWebShareServer.isAlive()
				);

				if (mDestroyApproved)
					new Handler(Looper.getMainLooper()).postDelayed(new Runnable()
					{
						@Override
						public void run()
						{
							if (mDestroyApproved
									&& !getHotspotUtils().isStarted()
									&& !hasOngoingTasks()
									&& getDefaultPreferences().getBoolean("kill_service_on_exit", false)) {
								stopSelf();
								Log.d(TAG, "onStartCommand(): Destroy state has been applied");
							}
						}
					}, 3000);
			} else if (ACTION_REQUEST_TASK_STATUS_CHANGE.equals(intent.getAction())
					&& intent.hasExtra(EXTRA_GROUP_ID)
					&& intent.hasExtra(EXTRA_DEVICE_ID)
					&& intent.hasExtra(EXTRA_TRANSFER_TYPE)) {
				long groupId = intent.getLongExtra(EXTRA_GROUP_ID, -1);
				String deviceId = intent.getStringExtra(EXTRA_DEVICE_ID);
				String typeString = intent.getStringExtra(EXTRA_TRANSFER_TYPE);

				try {
					TransferObject.Type type = TransferObject.Type.valueOf(typeString);

					notifyTaskStatusChange(groupId, deviceId, type, isProcessRunning(
							groupId, deviceId, type) ? TASK_STATUS_STOPPED : TASK_STATUS_ONGOING);
				} catch (Exception e) {
					e.printStackTrace();
				}
			} else if (ACTION_REQUEST_TASK_RUNNING_LIST_CHANGE.equals(intent.getAction())) {
				notifyTaskRunningListChange();
			} else if (ACTION_REVOKE_ACCESS_PIN.equals(intent.getAction())) {
				revokePinAccess();
				refreshServiceState();
			} else if (ACTION_REQUEST_FAST_MODE_STATUS.equals(intent.getAction())) {
				sendFastModeStatus();
			} else if (ACTION_REQUEST_WEBSHARE_STATUS.equals(intent.getAction())) {
				sendWebShareStatus();
			} else if (ACTION_TOGGLE_WEBSHARE.equals(intent.getAction())) {
				if (intent.hasExtra(EXTRA_ENABLE))
					setWebShareEnabled(intent.getBooleanExtra(EXTRA_ENABLE,
							false), true);
				else
					toggleWebShare();
			}
		}

		return START_STICKY;
	}

	@Override
	public void onDestroy()
	{
		super.onDestroy();

		mCommunicationServer.stop();
		mMediaScanner.disconnect();
		mNsdDiscovery.unregisterService();

		{
			ContentValues values = new ContentValues();

			values.put(AccessDatabase.FIELD_TRANSFERGROUP_ISSHAREDONWEB, 0);
			getDatabase().update(new SQLQuery.Select(AccessDatabase.TABLE_TRANSFERGROUP)
					.setWhere(String.format("%s = ?", AccessDatabase.FIELD_TRANSFERGROUP_ISSHAREDONWEB),
							String.valueOf(1)), values);
		}

		setWebShareEnabled(false, false);
		sendFastModeStatus();

		if (getHotspotUtils().unloadPreviousConfig()) {
			getHotspotUtils().disable();
			Log.d(TAG, "onDestroy(): Stopping hotspot (previously started)");
		}

		if (getWifiLock() != null && getWifiLock().isHeld()) {
			getWifiLock().release();
			Log.d(TAG, "onDestroy(): Releasing Wi-Fi lock");
		}

		revokePinAccess();
		stopForeground(true);

		synchronized (getOngoingIndexList()) {
			for (Interrupter interrupter : getOngoingIndexList().values()) {
				interrupter.interrupt(true);
				Log.d(TAG, "onDestroy(): Ongoing indexing stopped: " + interrupter.toString());
			}
		}

		synchronized (getActiveProcessList()) {
			for (ProcessHolder processHolder : getActiveProcessList()) {
				processHolder.interrupter.interrupt(true);
				Log.d(TAG, "onDestroy(): Killing process: " + processHolder.toString());
			}
		}
	}

	public synchronized void addProcess(ProcessHolder processHolder)
	{
		getActiveProcessList().add(processHolder);
	}

	public synchronized void removeProcess(ProcessHolder processHolder)
	{
		getActiveProcessList().remove(processHolder);
	}

	private void handleTransferRequest(final long groupId, final String jsonIndex, final NetworkDevice device,
									   final NetworkDevice.Connection connection, final boolean fastMode)
	{
		getSelfExecutor().submit(new Runnable()
		{
			@Override
			public void run()
			{
				final JSONArray jsonArray;
				final Interrupter interrupter = new Interrupter();
				TransferGroup group = new TransferGroup(groupId);
				TransferGroup.Assignee assignee = new TransferGroup.Assignee(
						group, device, TransferObject.Type.INCOMING, connection);
				final DynamicNotification notification = getNotificationHelper().notifyPrepareFiles(group, device);

				notification.setProgress(0, 0, true);

				try {
					jsonArray = new JSONArray(jsonIndex);
				} catch (Exception e) {
					notification.cancel();
					e.printStackTrace();
					return;
				}

				notification.setProgress(0, 0, false);
				boolean usePublishing = false;

				try {
					getDatabase().reconstruct(group);
					usePublishing = true;
				} catch (Exception e) {
					e.printStackTrace();
				}

				getDatabase().publish(group);
				getDatabase().publish(assignee);

				synchronized (getOngoingIndexList()) {
					getOngoingIndexList().put(group.id, interrupter);
				}

				long uniqueId = System.currentTimeMillis(); // The uniqueIds
				List<TransferObject> pendingRegistry = new ArrayList<>();

				for (int i = 0; i < jsonArray.length(); i++) {
					if (interrupter.interrupted())
						break;

					try {
						if (!(jsonArray.get(i) instanceof JSONObject))
							continue;

						JSONObject requestIndex = jsonArray.getJSONObject(i);

						if (requestIndex != null
								&& requestIndex.has(Keyword.INDEX_FILE_NAME)
								&& requestIndex.has(Keyword.INDEX_FILE_SIZE)
								&& requestIndex.has(Keyword.INDEX_FILE_MIME)
								&& requestIndex.has(Keyword.TRANSFER_REQUEST_ID)) {

							TransferObject transferObject = new TransferObject(
									requestIndex.getLong(Keyword.TRANSFER_REQUEST_ID),
									groupId,
									requestIndex.getString(Keyword.INDEX_FILE_NAME),
									"." + (uniqueId++) + "." + AppConfig.EXT_FILE_PART,
									requestIndex.getString(Keyword.INDEX_FILE_MIME),
									requestIndex.getLong(Keyword.INDEX_FILE_SIZE),
									TransferObject.Type.INCOMING);

							if (requestIndex.has(Keyword.INDEX_DIRECTORY))
								transferObject.directory = requestIndex.getString(Keyword.INDEX_DIRECTORY);

							pendingRegistry.add(transferObject);
						}
					} catch (JSONException e) {
						e.printStackTrace();
					}
				}

				SQLiteDatabase.ProgressUpdater progressUpdater = new SQLiteDatabase.ProgressUpdater()
				{
					long lastNotified = System.currentTimeMillis();

					@Override
					public void onProgressChange(int total, int current)
					{
						if ((System.currentTimeMillis() - lastNotified) > 1000) {
							lastNotified = System.currentTimeMillis();
							notification.updateProgress(total, current, false);
						}
					}

					@Override
					public boolean onProgressState()
					{
						return !interrupter.interrupted();
					}
				};

				if (pendingRegistry.size() > 0) {
					if (usePublishing)
						getDatabase().publish(pendingRegistry, progressUpdater);
					else
						getDatabase().insert(pendingRegistry, progressUpdater);
				}

				notification.cancel();

				synchronized (getOngoingIndexList()) {
					getOngoingIndexList().remove(group.id);
				}

				if (interrupter.interrupted())
					getDatabase().remove(group);
				else if (pendingRegistry.size() > 0) {
					sendBroadcast(new Intent(ACTION_INCOMING_TRANSFER_READY)
							.putExtra(EXTRA_GROUP_ID, groupId)
							.putExtra(EXTRA_DEVICE_ID, device.id));

					if (fastMode)
						try {
							startTransferAsClient(group.id, device.id,
									TransferObject.Type.INCOMING);
						} catch (Exception e) {
							e.printStackTrace();
						}
					else
						getNotificationHelper().notifyTransferRequest(device, group,
								TransferObject.Type.INCOMING, pendingRegistry);
				}
			}
		});
	}

	public void handleTransferAsReceiver(ProcessHolder processHolder)
	{
		addProcess(processHolder);
		notifyTaskStatusChange(processHolder.group.id, processHolder.device.id, processHolder.type,
				TASK_STATUS_ONGOING);
		notifyTaskRunningListChange();

		android.database.sqlite.SQLiteDatabase database = getDatabase().getWritableDatabase();
		boolean retry = false;

		try {
			while (processHolder.activeConnection.getSocket().isConnected()) {
				if (processHolder.interrupter.interrupted())
					break;

				try {
					processHolder.object = TransferUtils.fetchFirstValidIncomingTransfer(
							CommunicationService.this, processHolder.group.id);

					if (processHolder.object == null) {
						Log.d(TAG, "handleTransferAsReceiver(): Exiting because there is no " +
								"pending file instance left");
						break;
					} else
						Log.d(TAG, "handleTransferAsReceiver(): Starting to receive " +
								processHolder.object.name);

					processHolder.currentFile = FileUtils.getIncomingTransactionFile(
							getApplicationContext(), processHolder.object, processHolder.group);
					StreamInfo streamInfo = StreamInfo.getStreamInfo(getApplicationContext(),
							processHolder.currentFile.getUri());
					long currentSize = processHolder.currentFile.length();

					getNotificationHelper().notifyFileTransaction(processHolder);

					{
						JSONObject reply = new JSONObject()
								.put(Keyword.TRANSFER_REQUEST_ID, processHolder.object.id)
								.put(Keyword.RESULT, true);

						if (currentSize > 0)
							reply.put(Keyword.SKIPPED_BYTES, currentSize);

						Log.d(TAG, "handleTransferAsReceiver(): reply: " + reply.toString());
						processHolder.activeConnection.reply(reply.toString());
					}

					{
						JSONObject response = new JSONObject(processHolder.activeConnection.receive().response);
						Log.d(TAG, "handleTransferAsReceiver(): receive: " + response.toString());

						if (!response.getBoolean(Keyword.RESULT)) {
							if (response.has(Keyword.TRANSFER_JOB_DONE)
									&& !response.getBoolean(Keyword.TRANSFER_JOB_DONE)) {
								processHolder.interrupter.interrupt(true);
								Log.d(TAG, "handleTransferAsReceiver(): Transfer should be closed, babe!");
								break;
							} else if (response.has(Keyword.FLAG) && Keyword.FLAG_GROUP_EXISTS.equals(response.getString(Keyword.FLAG))) {
								if (response.has(Keyword.ERROR) && response.getString(Keyword.ERROR).equals(Keyword.ERROR_NOT_FOUND)) {
									processHolder.object.setFlag(TransferObject.Flag.REMOVED);
									Log.d(TAG, "handleTransferAsReceiver(): Sender says it does not have the file defined");
								} else if (response.has(Keyword.ERROR) && response.getString(Keyword.ERROR).equals(Keyword.ERROR_NOT_ACCESSIBLE)) {
									processHolder.object.setFlag(TransferObject.Flag.INTERRUPTED);
									Log.d(TAG, "handleTransferAsReceiver(): Sender says it can't open the file");
								} else if (response.has(Keyword.ERROR) && response.getString(Keyword.ERROR).equals(Keyword.ERROR_UNKNOWN)) {
									processHolder.object.setFlag(TransferObject.Flag.INTERRUPTED);
									Log.d(TAG, "handleTransferAsReceiver(): Sender says an unknown error occurred");
								}
							}
						} else {
							long sizeChanged = response.has(Keyword.SIZE_CHANGED)
									? response.getLong(Keyword.SIZE_CHANGED)
									: -1;
							boolean sizeActuallyChanged = sizeChanged > -1
									&& processHolder.object.size != sizeChanged;
							boolean canContinue = !sizeActuallyChanged || currentSize < 1;

							if (sizeActuallyChanged) {
								Log.d(TAG, "handleTransferAsReceiver(): Sender says the file has a new size");
								processHolder.object.size = response.getLong(Keyword.SIZE_CHANGED);
							}

							if (canContinue) {

							} else {
								Log.d(TAG, "handleTransferAsReceiver(): The change may broke the previous file which has a length. Cannot take the risk.");
								processHolder.object.setFlag(TransferObject.Flag.REMOVED);
							}
						}
					}
				} catch (Exception e) {
					e.printStackTrace();
					retry = true;

					if (!processHolder.recoverInterruptions) {
						TransferUtils.recoverIncomingInterruptions(CommunicationService.this, processHolder.group.id);
						processHolder.recoverInterruptions = true;
					}

					break;
				} finally {
					if (processHolder.object != null) {
						Log.d(TAG, "handleTransferAsReceiver(): Updating file instances to "
								+ processHolder.object.getFlag().toString());
						getDatabase().update(processHolder.object);
					}
				}
			}

			try {
				DocumentFile savePath = FileUtils.getSavePath(getApplicationContext(), processHolder.group);
				boolean areFilesDone = getDatabase().getFirstFromTable(TransferUtils.createIncomingSelection(
						processHolder.group.id, TransferObject.Flag.DONE, false)) == null;
				boolean jobDone = !processHolder.interrupter.interrupt() && areFilesDone;

				processHolder.activeConnection.reply(new JSONObject()
						.put(Keyword.RESULT, false)
						.put(Keyword.TRANSFER_JOB_DONE, jobDone)
						.toString());
				Log.d(TAG, "handleTransferAsReceiver(): reply: done ?? " + jobDone);

				if (!retry)
					if (processHolder.interrupter.interruptedByUser()) {
						processHolder.notification.cancel();
						Log.d(TAG, "handleTransferAsReceiver(): Removing notification an error is already notified");
					} else if (processHolder.interrupter.interrupted()) {
						getNotificationHelper().notifyReceiveError(processHolder);
						Log.d(TAG, "handleTransferAsReceiver(): Some files was not received");
					} else {
						getNotificationHelper().notifyFileReceived(processHolder, processHolder.device, savePath);
						Log.d(TAG, "handleTransferAsReceiver(): Notify user");
					}
			} catch (Exception e) {
				e.printStackTrace();
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			removeProcess(processHolder);
			notifyTaskStatusChange(processHolder.group.id, processHolder.assignee.deviceId,
					processHolder.type, TASK_STATUS_STOPPED);
			notifyTaskRunningListChange();

			Log.d(TAG, "We have exited");

			if (retry && processHolder.attemptsLeft > 0 && !processHolder.interrupter.interrupted()) {
				try {
					startTransferAsClient(processHolder);
					processHolder.attemptsLeft--;
				} catch (Exception e) {
					Log.d(TAG, "handleTransferAsReceiver(): Restart is requested, but transfer instance failed to reconstruct");
				}
			}
		}
	}

	public void handleTransferAsSender(ProcessHolder processHolder)
	{
		addProcess(processHolder);
		notifyTaskStatusChange(processHolder.group.id, processHolder.device.id, processHolder.type,
				TASK_STATUS_ONGOING);
		notifyTaskRunningListChange();

		try {
			while (processHolder.activeConnection.getSocket().isConnected()) {
				CoolSocket.ActiveConnection.Response response = processHolder.activeConnection.receive();
				Log.d(TAG, "handleTransferAsSender(): receive: " + response.response);
				JSONObject request = new JSONObject(response.response);

				if (request.has(Keyword.RESULT) && !request.getBoolean(Keyword.RESULT)) {
					if (request.has(Keyword.TRANSFER_JOB_DONE) && !request.getBoolean(Keyword.TRANSFER_JOB_DONE))
						processHolder.interrupter.interrupt(true);

					Log.d(TAG, "handleTransferAsSender(): Receiver notified that the transfer " +
							"has stopped with interruption=" + processHolder.interrupter.interrupted());
					return;
				} else if (processHolder.interrupter.interrupted()) {
					processHolder.activeConnection.reply(new JSONObject()
							.put(Keyword.RESULT, false)
							.put(Keyword.TRANSFER_JOB_DONE, false)
							.toString());

					Log.d(TAG, "handleTransferAsSender(): Exiting because the interruption " +
							"has been triggered");

					// Wait for the next response to ensure no error occurs.
					continue;
				}

				try {
					Log.d(TAG, "handleTransferAsSender(): " + processHolder.type.toString());

					long skippedBytes = 0;
					processHolder.object = new TransferObject(processHolder.group.id,
							request.getInt(Keyword.TRANSFER_REQUEST_ID), processHolder.type);

					getDatabase().reconstruct(processHolder.object);

					if (request.has(Keyword.SKIPPED_BYTES)) {
						skippedBytes = request.getLong(Keyword.SKIPPED_BYTES);
						Log.d(TAG, "SeamlessServes.onConnected(): Has skipped bytes: " + skippedBytes);
					}

					processHolder.currentFile = FileUtils.fromUri(getApplicationContext(),
							Uri.parse(processHolder.object.file));
					long fileSize = processHolder.currentFile.length();
					InputStream inputStream = getContentResolver().openInputStream(
							processHolder.currentFile.getUri());
					JSONObject reply = new JSONObject()
							.put(Keyword.RESULT, true);

					if (fileSize >= 0 && fileSize != processHolder.object.size) {
						reply.put(Keyword.SIZE_CHANGED, fileSize);
						processHolder.object.size = fileSize;
					}

					getNotificationHelper().notifyFileTransaction(processHolder);

					Log.d(TAG, "handleTransferAsSender(): Proceeding to send with reply: " +
							reply.toString());
					processHolder.activeConnection.reply(reply.toString());

					/*
					int readLength;
					byte[] buffer = new byte[AppConfig.BUFFER_LENGTH_DEFAULT];
					OutputStream outputStream = processHolder.activeConnection.getSocket()
							.getOutputStream();

					while ((readLength = inputStream.read(buffer)) != -1) {
						outputStream.write(buffer, 0, readLength);
						outputStream.flush();
					}*/
				} catch (ReconstructionFailedException e) {
					Log.d(TAG, "handleTransferAsSender(): File not found");

					processHolder.activeConnection.reply(new JSONObject()
							.put(Keyword.RESULT, false)
							.put(Keyword.ERROR, Keyword.ERROR_NOT_FOUND)
							.put(Keyword.FLAG, Keyword.FLAG_GROUP_EXISTS)
							.toString());

					processHolder.object.putFlag(processHolder.device.id, TransferObject.Flag.REMOVED);
					getDatabase().update(processHolder.object);
				} catch (FileNotFoundException | StreamCorruptedException | StreamInfo.FolderStateException e) {
					Log.d(TAG, "handleTransferAsSender(): File is not accessible ? " + processHolder.object.name);

					processHolder.activeConnection.reply(new JSONObject()
							.put(Keyword.RESULT, false)
							.put(Keyword.ERROR, Keyword.ERROR_NOT_ACCESSIBLE)
							.put(Keyword.FLAG, Keyword.FLAG_GROUP_EXISTS)
							.toString());

					processHolder.object.putFlag(processHolder.device.id, TransferObject.Flag.INTERRUPTED);
					getDatabase().update(processHolder.object);
				} catch (Exception e) {
					e.printStackTrace();

					processHolder.activeConnection.reply(new JSONObject()
							.put(Keyword.RESULT, false)
							.put(Keyword.ERROR, Keyword.ERROR_UNKNOWN)
							.put(Keyword.FLAG, Keyword.FLAG_GROUP_EXISTS)
							.toString());

					processHolder.object.putFlag(processHolder.device.id, TransferObject.Flag.INTERRUPTED);
					getDatabase().update(processHolder.object);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
			processHolder.interrupter.interrupt();
		} finally {
			if (processHolder.interrupter.interruptedByUser())
				if (processHolder.notification != null)
					processHolder.notification.cancel();
			else if (processHolder.interrupter.interrupted())
				mNotificationHelper.notifyConnectionError(processHolder, null);

			synchronized (getActiveProcessList()) {
				removeProcess(processHolder);

				if (processHolder.group.id != 0 && processHolder.device.id != null)
					notifyTaskStatusChange(processHolder.group.id, processHolder.device.id,
							processHolder.type, TASK_STATUS_STOPPED);

				notifyTaskRunningListChange();
			}
		}
	}

	public boolean hasOngoingTasks()
	{
		return mCommunicationServer.getConnections().size() > 0
				|| getOngoingIndexList().size() > 0
				|| getActiveProcessList().size() > 0
				|| mHotspotUtils.isStarted();
	}

	public ProcessHolder findProcessById(long groupId, @Nullable String deviceId, TransferObject.Type type)
	{
		synchronized (getActiveProcessList()) {
			for (ProcessHolder processHolder : getActiveProcessList())
				if (processHolder.group.id == groupId && type.equals(processHolder.type)
						&& (deviceId == null || deviceId.equals(processHolder.device.id)))
					return processHolder;
		}

		return null;
	}

	public synchronized List<ProcessHolder> getActiveProcessList()
	{
		return mActiveProcessList;
	}

	public HotspotUtils getHotspotUtils()
	{
		return mHotspotUtils;
	}

	public CommunicationNotificationHelper getNotificationHelper()
	{
		return mNotificationHelper;
	}

	public synchronized Map<Long, Interrupter> getOngoingIndexList()
	{
		return mOngoingIndexList;
	}

	public ExecutorService getSelfExecutor()
	{
		return mSelfExecutor;
	}

	public WifiManager.WifiLock getWifiLock()
	{
		return mWifiLock;
	}

	public boolean isProcessRunning(long groupId, String deviceId, TransferObject.Type type)
	{
		return findProcessById(groupId, deviceId, type) != null;
	}

	public void notifyTaskStatusChange(long groupId, String deviceId, TransferObject.Type type,
									   int state)
	{
		Intent intent = new Intent(ACTION_TASK_STATUS_CHANGE)
				.putExtra(EXTRA_TASK_CHANGE_TYPE, state)
				.putExtra(EXTRA_GROUP_ID, groupId)
				.putExtra(EXTRA_DEVICE_ID, deviceId)
				.putExtra(EXTRA_TRANSFER_TYPE, type.toString());

		sendBroadcast(intent);
	}

	public void notifyTaskRunningListChange()
	{
		List<Long> taskList = new ArrayList<>();
		ArrayList<String> deviceList = new ArrayList<>();

		synchronized (getActiveProcessList()) {
			for (ProcessHolder processHolder : getActiveProcessList()) {
				if (processHolder.group != null && processHolder.device != null) {
					taskList.add(processHolder.group.id);
					deviceList.add(processHolder.device.id);
				}
			}
		}

		long[] taskArray = new long[taskList.size()];

		for (int i = 0; i < taskList.size(); i++)
			taskArray[i] = taskList.get(i);

		sendBroadcast(new Intent(ACTION_TASK_RUNNING_LIST_CHANGE)
				.putExtra(EXTRA_TASK_LIST_RUNNING, taskArray)
				.putStringArrayListExtra(EXTRA_DEVICE_LIST_RUNNING, deviceList));
	}

	public void refreshServiceState()
	{
		updateServiceState(mFastMode);
	}

	public void revokePinAccess()
	{
		getDefaultPreferences().edit()
				.putInt(Keyword.NETWORK_PIN, -1)
				.apply();
	}

	public void sendHotspotStatusDisabling()
	{
		sendBroadcast(new Intent(ACTION_HOTSPOT_STATUS)
				.putExtra(EXTRA_HOTSPOT_ENABLED, false)
				.putExtra(EXTRA_HOTSPOT_DISABLING, true));
	}

	public void sendHotspotStatus(WifiConfiguration wifiConfiguration)
	{
		Intent statusIntent = new Intent(ACTION_HOTSPOT_STATUS)
				.putExtra(EXTRA_HOTSPOT_ENABLED, wifiConfiguration != null)
				.putExtra(EXTRA_HOTSPOT_DISABLING, false);

		if (wifiConfiguration != null) {
			statusIntent.putExtra(EXTRA_HOTSPOT_NAME, wifiConfiguration.SSID)
					.putExtra(EXTRA_HOTSPOT_PASSWORD, wifiConfiguration.preSharedKey)
					.putExtra(EXTRA_HOTSPOT_KEY_MGMT, NetworkUtils.getAllowedKeyManagement(
							wifiConfiguration));
		}

		sendBroadcast(statusIntent);
	}

	public void sendWebShareStatus()
	{
		sendBroadcast(new Intent(ACTION_WEBSHARE_STATUS)
				.putExtra(EXTRA_STATUS_STARTED, mWebShareServer.isAlive()));
	}

	public void setupHotspot()
	{
		boolean isEnabled = !getHotspotUtils().isEnabled();
		boolean overrideFastMode = getDefaultPreferences().getBoolean("hotspot_trust", false);

		// On Oreo devices, we will use platform specific code.
		if (overrideFastMode && (!isEnabled || Build.VERSION.SDK_INT < 26)) {
			updateServiceState(isEnabled);
			Log.d(TAG, "setupHotspot(): Start with Fast Mode");
		}

		if (isEnabled)
			getHotspotUtils().enableConfigured(AppUtils.getHotspotName(this), null);
		else {
			getHotspotUtils().disable();

			if (Build.VERSION.SDK_INT >= 26)
				sendHotspotStatusDisabling();
		}
	}

	public void sendFastModeStatus()
	{
		sendBroadcast(new Intent(ACTION_FAST_MODE_STATUS)
				.putExtra(EXTRA_STATUS_STARTED, mFastMode));
	}

	public void startTransferAsClient(long groupId, String deviceId, TransferObject.Type type) throws TransferGroupNotFoundException,
			DeviceNotFoundException, ConnectionNotFoundException, AssigneeNotFoundException
	{
		ProcessHolder processHolder = new ProcessHolder();
		processHolder.type = type;
		processHolder.group = new TransferGroup(groupId);

		try {
			getDatabase().reconstruct(processHolder.group);
		} catch (ReconstructionFailedException e) {
			throw new TransferGroupNotFoundException();
		}

		processHolder.device = new NetworkDevice(deviceId);

		try {
			getDatabase().reconstruct(processHolder.device);
		} catch (ReconstructionFailedException e) {
			throw new DeviceNotFoundException();
		}

		processHolder.assignee = new TransferGroup.Assignee(processHolder.group, processHolder.device,
				processHolder.type);

		try {
			getDatabase().reconstruct(processHolder.assignee);
		} catch (ReconstructionFailedException e) {
			throw new AssigneeNotFoundException();
		}

		processHolder.connection = new NetworkDevice.Connection(processHolder.assignee);

		try {
			getDatabase().reconstruct(processHolder.connection);
		} catch (ReconstructionFailedException e) {
			throw new ConnectionNotFoundException();
		}

		Log.d(TAG, "startTransferAsClient(): With deviceId=" + processHolder.device.id + " groupId="
				+ processHolder.group.id + " adapter=" + processHolder.assignee.connectionAdapter);
		startTransferAsClient(processHolder);
	}

	private void startTransferAsClient(final ProcessHolder holder)
	{
		CommunicationBridge.connect(getDatabase(), new CommunicationBridge.Client.ConnectionHandler()
		{
			@Override
			public void onConnect(CommunicationBridge.Client client)
			{
				try {
					holder.activeConnection = client.communicate(holder.device, holder.connection);

					{
						JSONObject reply = new JSONObject()
								.put(Keyword.REQUEST, Keyword.REQUEST_TRANSFER_JOB)
								.put(Keyword.TRANSFER_GROUP_ID, holder.group.id)
								.put(Keyword.TRANSFER_TYPE, holder.type.toString());

						holder.activeConnection.reply(reply.toString());
						Log.d(TAG, "startTransferAsClient(): reply: " + reply.toString());
					}

					{
						CoolSocket.ActiveConnection.Response response = holder.activeConnection.receive();
						JSONObject responseJSON = new JSONObject(response.response);

						Log.d(TAG, "startTransferAsClient(): " + holder.type.toString()
								+ "; About to start with " + response.response);

						if (responseJSON.getBoolean(Keyword.RESULT)) {
							holder.attemptsLeft = 2;

							if (TransferObject.Type.INCOMING.equals(holder.type)) {
								handleTransferAsReceiver(holder);
							} else if (TransferObject.Type.OUTGOING.equals(holder.type)) {
								holder.activeConnection.reply(":)");
								handleTransferAsSender(holder);
								holder.activeConnection.reply("(:");
							}

							try {
								CoolSocket.ActiveConnection.Response lastResponse
										= holder.activeConnection.receive();

								Log.d(TAG, "startTransferAsClient(): Final response before " +
										"exit: " + lastResponse.response);
							} catch (Exception e) {
								e.printStackTrace();
							}
						} else {
							getNotificationHelper().notifyConnectionError(holder, responseJSON.has(
									Keyword.ERROR) ? responseJSON.getString(Keyword.ERROR) : null);
						}
					}
				} catch (Exception e) {
					e.printStackTrace();
					mNotificationHelper.notifyConnectionError(holder, null);
				}
			}
		});
	}

	public void updateServiceState(boolean activateFastMode)
	{
		boolean broadcastStatus = mFastMode != activateFastMode;
		mFastMode = activateFastMode;
		mPinAccess = getDefaultPreferences().getInt(Keyword.NETWORK_PIN, -1) != -1;

		if (broadcastStatus)
			sendFastModeStatus();

		startForeground(CommunicationNotificationHelper.SERVICE_COMMUNICATION_FOREGROUND_NOTIFICATION_ID,
				getNotificationHelper().getCommunicationServiceNotification(mFastMode, mPinAccess,
						mWebShareServer != null && mWebShareServer.isAlive()).build());
	}

	public void setWebShareEnabled(boolean enable, boolean updateServiceState)
	{
		boolean enabled = mWebShareServer.isAlive();

		if (enable != enabled) {
			if (enable)
				try {
					mWebShareServer.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);
				} catch (IOException e) {
					e.printStackTrace();
				}
			else
				mWebShareServer.stop();
		}

		if (updateServiceState)
			updateServiceState(mFastMode);
		sendWebShareStatus();
	}

	public void toggleWebShare()
	{
		setWebShareEnabled(!mWebShareServer.isAlive(), true);
	}

	public class CommunicationServer extends CoolSocket
	{
		CommunicationServer()
		{
			super(AppConfig.SERVER_PORT_COMMUNICATION);
			setSocketTimeout(AppConfig.DEFAULT_SOCKET_TIMEOUT_LARGE);
		}

		@Override
		protected void onConnected(final ActiveConnection activeConnection)
		{
			// check if the same address has other connections and limit that to 5
			if (getConnectionCountByAddress(activeConnection.getAddress()) > 5)
				return;

			try {
				ActiveConnection.Response clientRequest = activeConnection.receive();
				JSONObject responseJSON = analyzeResponse(clientRequest);
				JSONObject replyJSON = new JSONObject();

				if (responseJSON.has(Keyword.REQUEST)
						&& Keyword.BACK_COMP_REQUEST_SEND_UPDATE.equals(responseJSON.getString(Keyword.REQUEST))) {
					activeConnection.reply(replyJSON.put(Keyword.RESULT, true).toString());

					getSelfExecutor().submit(new Runnable()
					{
						@Override
						public void run()
						{
							try {
								UpdateUtils.sendUpdate(getApplicationContext(), activeConnection.getClientAddress());
							} catch (IOException e) {
								e.printStackTrace();
							}
						}
					});

					return;
				}

				boolean result = false;
				boolean shouldContinue = false;

				final int networkPin = getDefaultPreferences().getInt(Keyword.NETWORK_PIN, -1);
				final boolean isSecureConnection = networkPin != -1
						&& responseJSON.has(Keyword.DEVICE_SECURE_KEY)
						&& responseJSON.getInt(Keyword.DEVICE_SECURE_KEY) == networkPin;

				String deviceSerial = null;

				AppUtils.applyDeviceToJSON(CommunicationService.this, replyJSON);

				if (responseJSON.has(Keyword.HANDSHAKE_REQUIRED) && responseJSON.getBoolean(Keyword.HANDSHAKE_REQUIRED)) {
					pushReply(activeConnection, replyJSON, true);

					if (!responseJSON.has(Keyword.HANDSHAKE_ONLY) || !responseJSON.getBoolean(Keyword.HANDSHAKE_ONLY)) {
						if (responseJSON.has(Keyword.DEVICE_INFO_SERIAL))
							deviceSerial = responseJSON.getString(Keyword.DEVICE_INFO_SERIAL);

						clientRequest = activeConnection.receive();
						responseJSON = analyzeResponse(clientRequest);
						replyJSON = new JSONObject();
					} else {
						return;
					}
				}

				if (deviceSerial != null) {
					NetworkDevice device = new NetworkDevice(deviceSerial);

					try {
						getDatabase().reconstruct(device);

						if (isSecureConnection)
							device.isRestricted = false;

						if (!device.isRestricted)
							shouldContinue = true;
					} catch (Exception e1) {
						e1.printStackTrace();

						device = NetworkDeviceLoader.load(true, getDatabase(), activeConnection.getClientAddress(), null);

						if (device == null)
							throw new Exception("Could not reach to the opposite server");

						device.isTrusted = false;

						if (isSecureConnection)
							device.isRestricted = false;

						getDatabase().publish(device);

						shouldContinue = true;

						if (device.isRestricted)
							getNotificationHelper().notifyConnectionRequest(device);
					}

					final NetworkDevice.Connection connection = NetworkDeviceLoader.processConnection(
							getDatabase(), device, activeConnection.getClientAddress());
					final boolean isFastModeAvailable = (mFastMode && device.isTrusted)
							|| (isSecureConnection && getDefaultPreferences().getBoolean("qr_trust", false));

					if (!shouldContinue)
						replyJSON.put(Keyword.ERROR, Keyword.ERROR_NOT_ALLOWED);
					else if (responseJSON.has(Keyword.REQUEST)) {
						if (isSecureConnection && !mPinAccess)
							// Probably pin access has just been activated, so we should update
							// the service state.
							refreshServiceState();

						switch (responseJSON.getString(Keyword.REQUEST)) {
							case (Keyword.REQUEST_TRANSFER):
								if (responseJSON.has(Keyword.FILES_INDEX) && responseJSON.has(Keyword.TRANSFER_GROUP_ID)
										&& getOngoingIndexList().size() < 1) {
									final long groupId = responseJSON.getLong(Keyword.TRANSFER_GROUP_ID);
									final String jsonIndex = responseJSON.getString(Keyword.FILES_INDEX);

									result = true;

									handleTransferRequest(groupId, jsonIndex, device, connection,
											isFastModeAvailable);
								}
								break;
							case (Keyword.REQUEST_RESPONSE):
								if (responseJSON.has(Keyword.TRANSFER_GROUP_ID)) {
									int groupId = responseJSON.getInt(Keyword.TRANSFER_GROUP_ID);
									boolean isAccepted = responseJSON.getBoolean(Keyword.TRANSFER_IS_ACCEPTED);

									TransferGroup group = new TransferGroup(groupId);
									TransferGroup.Assignee assignee = new TransferGroup.Assignee(
											group, device, TransferObject.Type.OUTGOING);

									try {
										getDatabase().reconstruct(group);
										getDatabase().reconstruct(assignee);

										if (!isAccepted)
											getDatabase().remove(assignee);

										result = true;
									} catch (Exception ignored) {
									}
								}
								break;
							case (Keyword.REQUEST_CLIPBOARD):
								if (responseJSON.has(Keyword.TRANSFER_CLIPBOARD_TEXT)) {
									TextStreamObject textStreamObject = new TextStreamObject(
											AppUtils.getUniqueNumber(), responseJSON.getString(Keyword.TRANSFER_CLIPBOARD_TEXT));

									getDatabase().publish(textStreamObject);
									getNotificationHelper().notifyClipboardRequest(device, textStreamObject);

									result = true;
								}
								break;
							case (Keyword.REQUEST_ACQUAINTANCE):
								sendBroadcast(new Intent(ACTION_DEVICE_ACQUAINTANCE)
										.putExtra(EXTRA_DEVICE_ID, device.id)
										.putExtra(EXTRA_CONNECTION_ADAPTER_NAME, connection.adapterName));

								result = true;
								break;
							case (Keyword.REQUEST_HANDSHAKE):
								result = true;
								break;
							case (Keyword.REQUEST_TRANSFER_JOB):
								if (responseJSON.has(Keyword.TRANSFER_GROUP_ID)) {
									int groupId = responseJSON.getInt(Keyword.TRANSFER_GROUP_ID);
									String typeValue = responseJSON.getString(Keyword.TRANSFER_TYPE);

									try {
										TransferObject.Type type = TransferObject.Type.valueOf(typeValue);
										TransferGroup group = new TransferGroup(groupId);
										getDatabase().reconstruct(group);

										Log.d(CommunicationService.TAG, "CommunicationServer.onConnected(): "
												+ "groupId=" + groupId + " typeValue=" + typeValue);

										if (!isProcessRunning(groupId, device.id, type)) {
											ProcessHolder processHolder = new ProcessHolder();
											processHolder.activeConnection = activeConnection;
											processHolder.group = group;
											processHolder.device = device;
											processHolder.assignee = new TransferGroup.Assignee(
													group, device, type);

											getDatabase().reconstruct(processHolder.assignee);
											pushReply(activeConnection, new JSONObject(), true);
											Log.d(TAG, "CommunicationServer.onConnected(): " +
													"Reply sent for the connection");

											result = true;

											if (TransferObject.Type.INCOMING.equals(type)) {
												processHolder.type = TransferObject.Type.OUTGOING;
												handleTransferAsSender(processHolder);
											} else if (TransferObject.Type.OUTGOING.equals(type)) {
												processHolder.type = TransferObject.Type.INCOMING;
												Log.d(TAG, "CommunicationServer.onConnected(): "
														+ activeConnection.receive().response);
												handleTransferAsReceiver(processHolder);
												Log.d(TAG, "CommunicationServer.onConnected(): "
														+ activeConnection.receive().response);
											} else
												result = false;
										} else
											responseJSON.put(Keyword.ERROR, Keyword.ERROR_NOT_ACCESSIBLE);
									} catch (Exception e) {
										responseJSON.put(Keyword.ERROR, Keyword.ERROR_NOT_FOUND);
									}
								}
								break;
						}
					}
				}

				pushReply(activeConnection, replyJSON, result);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		JSONObject analyzeResponse(ActiveConnection.Response response) throws JSONException
		{
			return response.totalLength > 0 ? new JSONObject(response.response) : new JSONObject();
		}

		void pushReply(ActiveConnection activeConnection, JSONObject reply, boolean result)
				throws JSONException, TimeoutException, IOException
		{
			activeConnection.reply(reply
					.put(Keyword.RESULT, result)
					.toString());
		}
	}

	public static class ProcessHolder
	{
		// Native objects
		public Interrupter interrupter = new Interrupter();

		// Static objects
		public CoolSocket.ActiveConnection activeConnection;
		public NetworkDevice device;
		public TransferGroup group;
		public TransferGroup.Assignee assignee;
		public NetworkDevice.Connection connection;
		public TransferObject.Type type;

		// Changing objects
		public DynamicNotification notification;
		public TransferObject object;
		public DocumentFile currentFile;
		public long currentBytes; // moving
		public long totalBytes;
		public long completedBytes;
		public long timeStarted;
		public int timePassed;
		public int completedCount;
		public int totalCount;

		// Informative objects
		public boolean recoverInterruptions = false;
		public int attemptsLeft = 2;
	}
}

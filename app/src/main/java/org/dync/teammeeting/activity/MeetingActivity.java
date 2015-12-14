package org.dync.teammeeting.activity;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.dync.rtk.util.JNetwork.JniceEvent;
import org.dync.rtk.util.JniceClient;
import org.dync.rtk.util.RtkAudioManager;
import org.dync.rtk.util.RtkM2MClient;
import org.dync.rtk.util.RtkPPClient.RtkPPClientEvents;
import org.dync.rtk.util.RtkPeerConnection.PeerConnectionParameters;
import org.dync.rtk.util.RtkVideoRender;
import org.dync.teammeeting.R;
import org.dync.teammeeting.adapter.ChatMessageAdapter;
import org.dync.teammeeting.app.TeamMeetingApp;
import org.dync.teammeeting.helper.MeetingAnim;
import org.dync.teammeeting.helper.MeetingAnim.AnimationEndListener;
import org.dync.teammeeting.ui.PopupWindowCustom;
import org.dync.teammeeting.ui.PopupWindowCustom.OnPopupWindowClickListener;
import org.dync.teammeeting.utils.ChatMessage;
import org.dync.teammeeting.utils.ChatMessage.Type;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.w3c.dom.Text;
import org.webrtc.IceCandidate;
import org.webrtc.RendererCommon.ScalingType;
import org.webrtc.SessionDescription;
import org.webrtc.StatsReport;
import org.webrtc.VideoRenderer;
import org.webrtc.VideoRendererGui;
import org.webrtc.VideoTrack;

import android.animation.Animator;
import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.nineoldandroids.view.ViewHelper;
import com.nineoldandroids.view.ViewPropertyAnimator;
import com.ypy.eventbus.EventBus;

/**
 * @author zhangqilu
 * org.dync.teammeeting.activity MeetingActivity
 * create at 2015-12-11 5:02:32
 */

public class MeetingActivity extends Activity implements JniceEvent, RtkPPClientEvents
{
	// Local preview screen position before call is connected.
	private static final boolean mDebug = TeamMeetingApp.mIsDebug;
	private static final String TAG = "MeetingActivity";

	private static final int ANIMATOR_TANSLATION = 0X01;
	// Local preview screen position before call is connected.
	private static final int LOCAL_X_CONNECTING = 0;
	private static final int LOCAL_Y_CONNECTING = 0;
	private static final int LOCAL_WIDTH_CONNECTING = 100;
	private static final int LOCAL_HEIGHT_CONNECTING = 100;

	// Remote video screen position
	private static final int REMOTE_X = 5;
	private static final int REMOTE_Y = 70;
	private static final int REMOTE_WIDTH = 30;
	private static final int REMOTE_HEIGHT = 26;

	private JniceClient mJNice;
	private RtkM2MClient mRtkClient;
	private RtkAudioManager mRtkAudioManager = null;
	private String mSubJniceId;
	private String mRoomID;
	private MeetingAnim mMettingAnim;
	private ImageButton mChatButton, mInviteButton;
	private LinearLayout mControlLayout;
	private RelativeLayout mTopbarLayout;
	private ImageButton mVoiceButton, mCameraButton, mHangUpButton, mSwitchCameraButton,
			mCameraOffButton;
	private boolean mMeetingCameraFlag = true, mMeetingCameraOffFlag = true,
			mMeetingVideoFlag = true, mMeetingVoiceFlag;

	private GLSurfaceView mVideoView;
	private TextView mTvRoomName;
	// self UserId
	private String mSelfUserId;

	private VideoRenderer mLocalRenderer;
	private VideoRenderer.Callbacks mLocalCallbacks;
	private VideoRenderer mRemoteRenderer;
	private VideoRenderer.Callbacks mPeerCallbacks;
	// key peerid value VideoRenderer.Callbacks 
	private Map<String, RtkVideoRender> mAllRendersMap = new Hashtable<String, RtkVideoRender>();

	private int[] mRemotepositionX = new int[5];
	private String mCurrentDisplayPeerId = null;

	private int mPeerCount = 0;
	private int mRemoteLeaveIndex = 6;

	private int mScreenWidth = 0, mScreenHeight = 0;

	private String mBsNow, mBsBefore;

	double mTsNow, mTsBefore;
	private String mBitrate;

	private Map<String, String> mAllPeerId = new Hashtable<String, String>();
	private PopupWindowCustom mPopupWindowCustom;

	// Left distance of this control button relative to its parent
	int mLeftDistanceCameraBtn;
	int mLeftDistanceHangUpBtn;
	int mLeftDistanceVoiceBtn;

	// chating 
	private RelativeLayout mChatLayout;
	private ImageButton mChatClose;
	private Button mSendMessage;
	private ListView mChatView;
	private EditText mMsg;
	private List<ChatMessage> mDatas = new ArrayList<ChatMessage>();
	private ChatMessageAdapter mAdapter;
	

	private Handler mUiHandler = new Handler()
	{
		public void handleMessage(Message msg)
		{
			switch (msg.what)
			{
			case ANIMATOR_TANSLATION:

				mVoiceButton.setVisibility(View.VISIBLE);
				mHangUpButton.setVisibility(View.VISIBLE);
				mSwitchCameraButton.setVisibility(View.GONE);
				mCameraOffButton.setVisibility(View.GONE);

				break;

			default:
				break;
			}

		}

	};

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_meeting);

		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
	
		mRtkClient = new RtkM2MClient(this);
		mJNice = new JniceClient(getApplicationContext(), this, "123.59.68.21", 8088);// "192.168.7.45",
																						// 8088
		initView();
	}

	/* Init UI */
	private void initView()
	{

		mMettingAnim = new MeetingAnim();
		mMettingAnim.setAnimEndListener(mAnimationEndListener);

		DisplayMetrics dm = new DisplayMetrics();
		getWindowManager().getDefaultDisplay().getMetrics(dm);
		mScreenWidth = dm.widthPixels;
		mScreenHeight = dm.heightPixels;

		// Create UI controls.

		mTopbarLayout = (RelativeLayout) findViewById(R.id.rl_meeting_topbar);
		mControlLayout = (LinearLayout) findViewById(R.id.ll_meeting_control);

		mChatButton = (ImageButton) findViewById(R.id.imgbtn_chat);
		mInviteButton = (ImageButton) findViewById(R.id.imgbtn_invite);
		mTvRoomName = (TextView) findViewById(R.id.tv_room_name);
		String roomName = getIntent().getStringExtra("meetingName");
		mTvRoomName.setText(roomName);
		
		mVoiceButton = (ImageButton) findViewById(R.id.meeting_voice);
		mCameraButton = (ImageButton) findViewById(R.id.meeting_camera);
		mHangUpButton = (ImageButton) findViewById(R.id.meeting_hangup);
		mSwitchCameraButton = (ImageButton) findViewById(R.id.meeting_camera_switch);
		mCameraOffButton = (ImageButton) findViewById(R.id.meeting_camera_off);

		mInviteButton.setOnClickListener(mOnClickListener);
		mChatButton.setOnClickListener(mOnClickListener);
		mVoiceButton.setOnClickListener(mOnClickListener);
		mCameraButton.setOnClickListener(mOnClickListener);
		mHangUpButton.setOnClickListener(mOnClickListener);
		mSwitchCameraButton.setOnClickListener(mOnClickListener);
		mCameraOffButton.setOnClickListener(mOnClickListener);

		mVideoView = (GLSurfaceView) findViewById(R.id.glview_call);
		// Create video renderers.
		VideoRendererGui.setView(mVideoView, new Runnable()
		{
			@Override
			public void run()
			{
				// TODO Auto-generated method stub
				initPeerConnectionVideoParameters();
			}
		});

		mLocalCallbacks = VideoRendererGui.create(LOCAL_X_CONNECTING, LOCAL_Y_CONNECTING,
				LOCAL_WIDTH_CONNECTING, LOCAL_HEIGHT_CONNECTING, ScalingType.SCALE_ASPECT_FILL,
				false);
		
		//Chat ui inint
		mChatLayout = (RelativeLayout) findViewById(R.id.rl_chating);
		mSendMessage = (Button) findViewById(R.id.btn_chat_send);
		mChatClose = (ImageButton) findViewById(R.id.imgbtn_back);
		mChatView = (ListView) findViewById(R.id.listView_chat);
		mMsg = (EditText) findViewById(R.id.et_chat_msg);
		mSendMessage.setOnClickListener(mOnClickListener);
		mChatClose.setOnClickListener(mOnClickListener);
		
		mAdapter = new ChatMessageAdapter(this, mDatas);
		mChatView.setAdapter(mAdapter);

	}


	@Override
	public boolean onTouchEvent(MotionEvent event)
	{

		switch (event.getAction())
		{
		case MotionEvent.ACTION_DOWN:
			
			break;
		case MotionEvent.ACTION_UP:
			
			float translationY = ViewHelper.getTranslationY(mControlLayout);
			if (translationY == 0){
				
				ViewPropertyAnimator.animate(mControlLayout).translationY(mControlLayout.getHeight());
				ViewPropertyAnimator.animate(mTopbarLayout).translationY(-mTopbarLayout.getHeight());

			}
			else{

				ViewPropertyAnimator.animate(mControlLayout).translationY(0f);
				ViewPropertyAnimator.animate(mTopbarLayout).translationY(0f);
			}
			


			break;

		default:
			break;
		}
		return super.onTouchEvent(event);
	}

	@Override
	public void onConfigurationChanged(Configuration newConfig)
	{


		if (mPopupWindowCustom != null)
		{
			mPopupWindowCustom.dismiss();
			mPopupWindowCustom = null;
		}
		

		
		ViewPropertyAnimator.animate(mControlLayout).translationY(0f);
		ViewPropertyAnimator.animate(mTopbarLayout).translationY(0f);
		

		super.onConfigurationChanged(newConfig);
	}

	@Override
	public void onWindowFocusChanged(boolean hasFocus)
	{
		 measureLeftDistance();
		super.onWindowFocusChanged(hasFocus);
	}

	/**
	 * Measuring the distance button
	 */
	private void measureLeftDistance()
	{
		mLeftDistanceCameraBtn = mCameraButton.getLeft() + mCameraButton.getWidth() / 2;
		mLeftDistanceHangUpBtn = mHangUpButton.getLeft() + mHangUpButton.getWidth() / 2;
		mLeftDistanceVoiceBtn = mVoiceButton.getLeft() + mVoiceButton.getWidth() / 2;
	}

	private AnimationEndListener mAnimationEndListener = new AnimationEndListener()
	{
		@Override
		public void onAnimationEnd(Animator arg0)
		{
			mVoiceButton.setVisibility(View.VISIBLE);
			mHangUpButton.setVisibility(View.VISIBLE);
			mSwitchCameraButton.setVisibility(View.GONE);
			mCameraOffButton.setVisibility(View.GONE);
			mMettingAnim.alphaAnimator(mVoiceButton, 1.0f, 1.0f, 100);
			mMettingAnim.alphaAnimator(mHangUpButton, 1.0f, 1.0f, 100);
		}
	};

	private OnPopupWindowClickListener mPopupWindowListener = new OnPopupWindowClickListener()
	{
		@Override
		public void onPopupClickListener(View view)
		{
			switch (view.getId())
			{
			case R.id.ibtn_close:
				mPopupWindowCustom.dismiss();
				break;
			case R.id.ibtn_message:
				mPopupWindowCustom.dismiss();
				break;
			case R.id.ibtn_weixin:
				mPopupWindowCustom.dismiss();
				break;
			case R.id.tv_copy:
				mPopupWindowCustom.dismiss();
				break;
			case R.id.btn_copy:
				mPopupWindowCustom.dismiss();
				break;

			default:
				break;
			}
		}
	};

	/* set button clickListener */
	OnClickListener mOnClickListener = new OnClickListener()
	{
		@Override
		public void onClick(View mView)
		{

			switch (mView.getId())
			{
				case R.id.imgbtn_invite:
	
					mPopupWindowCustom = new PopupWindowCustom(MeetingActivity.this, mInviteButton,
							mTopbarLayout, mPopupWindowListener);
					break;
	
				case R.id.meeting_camera:
	
					if (!mMeetingCameraOffFlag)
					{
						mCameraButton.setImageResource(R.drawable.btn_camera_on);
						mMeetingCameraOffFlag = true;
						return;
					}
	
					if (mMeetingCameraFlag)
					{
						mCameraButton.setImageResource(R.drawable.btn_camera_back);
						mVoiceButton.setVisibility(View.GONE);
						mHangUpButton.setVisibility(View.GONE);
						mSwitchCameraButton.setVisibility(View.VISIBLE);
						mCameraOffButton.setVisibility(View.VISIBLE);
	
						mMettingAnim.rotationOrApaha(mCameraButton, mMeetingCameraFlag);
						mMettingAnim.translationAlphaAnimator(mSwitchCameraButton,
								(mLeftDistanceCameraBtn - mLeftDistanceHangUpBtn), 0, 400, true);
	
						mMettingAnim.translationAlphaAnimator(mCameraOffButton,
								(mLeftDistanceCameraBtn - mLeftDistanceVoiceBtn), 0, 400, true);
	
					} else
					{
						mCameraButton.setImageResource(R.drawable.btn_camera_on);
						mMettingAnim.rotationOrApaha(mCameraButton, mMeetingCameraFlag);
						mMettingAnim.translationAlphaAnimator(mSwitchCameraButton, 0,
								(mLeftDistanceCameraBtn - mLeftDistanceHangUpBtn), 300, false);
						mMettingAnim.translationAlphaAnimator(mCameraOffButton, 0,
								(mLeftDistanceCameraBtn - mLeftDistanceVoiceBtn), 300, false);
	
					}
	
					mMeetingCameraFlag = !mMeetingCameraFlag;
					break;
				case R.id.meeting_hangup:
					finish();
					break;
				case R.id.meeting_voice:
	
	
					if (mMeetingVoiceFlag)
					{
						mVoiceButton.setImageResource(R.drawable.btn_voice_off);
						mRtkClient.setLocalVoiceDisabled();
					} else
					{
						mVoiceButton.setImageResource(R.drawable.btn_voice_on);
						mRtkClient.setLocalVoiceEnabled();
					}
					mMeetingVoiceFlag = !mMeetingVoiceFlag;
	
					break;
				case R.id.meeting_camera_switch:
	
					mRtkClient.switchCamera();
	
					break;
				case R.id.meeting_camera_off:
					mCameraButton.setImageResource(R.drawable.btn_camera_off_select);
					mMettingAnim.rotationOrApaha(mCameraButton, mMeetingCameraFlag);
					mMettingAnim.translationAlphaAnimator(mSwitchCameraButton, 0,
							(mLeftDistanceCameraBtn - mLeftDistanceHangUpBtn), 300, false);
					mMettingAnim.translationAlphaAnimator(mCameraOffButton, 0,
							(mLeftDistanceCameraBtn - mLeftDistanceVoiceBtn), 300, false);
					mMeetingCameraOffFlag = false;
					mMeetingCameraFlag = true;
					break;
				case R.id.imgbtn_chat:
					mChatLayout.setVisibility(View.VISIBLE);
					mTopbarLayout.setVisibility(View.GONE);
					mControlLayout.setVisibility(View.GONE);
					break;
				case R.id.btn_chat_send:
					sendMessageChat();
					
	
					break;
				case R.id.imgbtn_back:
					mChatLayout.setVisibility(View.GONE);
					mTopbarLayout.setVisibility(View.VISIBLE);
					mControlLayout.setVisibility(View.VISIBLE);
					
	
					break;
	
				}
		}
	};

	private void sendMessageChat()
	{
		final String msg = mMsg.getText().toString();
		if (TextUtils.isEmpty(msg))
		{
			Toast.makeText(this, R.string.str_content_empty, Toast.LENGTH_SHORT).show();
			return;
		}

		ChatMessage to = new ChatMessage(Type.OUTPUT, msg);
		to.setDate(new Date());
		mDatas.add(to);

		mAdapter.notifyDataSetChanged();
		mChatView.setSelection(mDatas.size() - 1);

		mMsg.setText("");

		//close soft keyboard
		InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
		if (imm.isActive()){
			imm.toggleSoftInput(InputMethodManager.SHOW_IMPLICIT,
					InputMethodManager.HIDE_NOT_ALWAYS);
		}

	}

	/**
	 * OnTouchListener
	 */
	private View.OnTouchListener mOnTouchListener = new View.OnTouchListener()
	{
		int startX;
		int startY;

		@Override
		public boolean onTouch(View v, MotionEvent event)
		{
			// TODO Auto-generated method stub
			if (v.getId() == R.id.meet_parent)
			{

				if (event.getAction() == MotionEvent.ACTION_UP)
				{

					startX = (int) event.getX();
					startY = (int) event.getY();
					int positionStart = (100 - REMOTE_WIDTH * mPeerCount) / 2;
					int positionEnd = 100 - positionStart;
					if (mDebug)
					{
						Log.e(TAG, " startX " + startX + " startY " + startY + " positionStart "
								+ positionStart * mScreenWidth / 100 + " positionEnd "
								+ positionEnd * mScreenWidth / 100);
					}

					if (startY > mScreenHeight * 7 / 10
							&& startX > positionStart * mScreenWidth / 100
							&& startX < positionEnd * mScreenWidth / 100)
					{
						// update RtkVideoRender
						Iterator<Entry<String, RtkVideoRender>> iter = mAllRendersMap.entrySet()
								.iterator();
						while (iter.hasNext())
						{
							Entry<String, RtkVideoRender> entry = iter.next();
							String peerId = entry.getKey();
							RtkVideoRender render = entry.getValue();
							if (render.getCallbacks() == mLocalCallbacks)
								continue;

							int count = render.getScrnIndex() - 1;
							if (mRemotepositionX[count] * mScreenWidth / 100 < startX
									&& startX < (mRemotepositionX[count] + REMOTE_WIDTH)
											* mScreenWidth / 100)
							{

								switchVideoRenderer(peerId, mCurrentDisplayPeerId);
								break;
							}
						}

						return true;
					} else
					// View  VISIBLE  INVISIBLE
					{
						if (mDebug)
						{
							Log.e(TAG, " ");
						}
						if (mControlLayout.getVisibility() == View.VISIBLE)
						{
							mControlLayout.setVisibility(View.GONE);

							return true;
						} else if (mControlLayout.getVisibility() == View.GONE)
						{
							mControlLayout.setVisibility(View.VISIBLE);

							return true;
						} else
						{
							return false;
						}
					}
				}
				return true;
			} else
			{
				return false;
			}
		}
	};

	@Override
	public void onPause()
	{
		super.onPause();
		mVideoView.onPause();
		mRtkClient.stopVideoSource();
	}

	@Override
	public void onResume()
	{
		super.onResume();
		mVideoView.onResume();
		mRtkClient.startVideoSource();
	}

	@Override
	protected void onDestroy()
	{
		// TODO Auto-generated method stub
		super.onDestroy();
		// Close RtkAudioManager

		if (mRtkAudioManager != null)
		{
			mRtkAudioManager.close();
			mRtkAudioManager = null;
		}

		mJNice.Close();
		mJNice = null;

		{// Close all
			if (mRtkClient != null)
			{
				mRtkClient.close();
				mRtkClient = null;
			}
		}
		EventBus.getDefault().unregister(this);
	}

	private void initPeerConnectionVideoParameters()
	{
		PeerConnectionParameters peerConnectionParameters = new PeerConnectionParameters(true,
				false, false, 640, 480, 30, 384, "H264", true, 32, "OPUS", true);
		mLocalRenderer = new VideoRenderer(mLocalCallbacks);
		mRtkClient.createPeerConnectionFactory(MeetingActivity.this,
				VideoRendererGui.getEGLContext(), peerConnectionParameters, mLocalRenderer);
	}
	
	/**
	 * updateSubVideoView
	 */
	private void updateSubVideoView()
	{
		int positionStart = (100 - REMOTE_WIDTH * mPeerCount) / 2;
		Iterator<Entry<String, RtkVideoRender>> iter = mAllRendersMap.entrySet().iterator();

		while (iter.hasNext())
		{
			Entry<String, RtkVideoRender> entry = iter.next();
			String peerIdTemp = entry.getKey();
			RtkVideoRender render = entry.getValue();
			if (render.getCallbacks() == mLocalCallbacks)
				continue;

			int count = render.getScrnIndex();
			if (mDebug)
			{
				Log.e(TAG, "count " + count + " mRemoteLeaveIndex " + mRemoteLeaveIndex);
			}
			if (mRemoteLeaveIndex < count && mRemoteLeaveIndex != 0)
			{
				render.setScrnIndex(count - 1);
				count = count - 2;
			} else
			{
				count = count - 1;
			}
			mRemotepositionX[count] = positionStart + REMOTE_WIDTH * (count);

			VideoRendererGui.update(render.getCallbacks(), mRemotepositionX[count], REMOTE_Y,
					REMOTE_WIDTH, REMOTE_HEIGHT, ScalingType.SCALE_ASPECT_FIT, false);
			if (mDebug)
			{
				Log.e(TAG, " updateVideoView count " + count + " mPeerId " + peerIdTemp);
			}
		}
		mRemoteLeaveIndex = 6;
	}
	
	/**
	 * addRemoteVideoRenderer
	 * 
	 * @param peerId
	 * @return RtkVideoRender
	 */
	public RtkVideoRender addRemoteVideoRenderer(String peerId)
	{

		RtkVideoRender rtkRemoteRender = mAllRendersMap.get(peerId);
		if (null == rtkRemoteRender)
		{
			int subScrnSize = mAllRendersMap.size();
			VideoRenderer.Callbacks rendererCallback = VideoRendererGui.create(REMOTE_WIDTH,
					REMOTE_Y, REMOTE_WIDTH, REMOTE_HEIGHT, ScalingType.SCALE_ASPECT_FIT, false);
			rtkRemoteRender = new RtkVideoRender(peerId, rendererCallback, null);
			rtkRemoteRender.setScrnIndex(subScrnSize);
			mAllRendersMap.put(peerId, rtkRemoteRender);
		}

		updateSubVideoView();
		return rtkRemoteRender;
	}
	
	/**
	 * switchVideoRenderer
	 * 
	 * @param selectId
	 * @param currentId
	 */
	private void switchVideoRenderer(String selectId, String currentId)
	{
		RtkVideoRender selectRender = mAllRendersMap.get(selectId);
		if (null == currentId)
		{
			//Big Screen image has been removed, switch selectId corresponding images to the screen
			if (selectRender != null)
			{
				//remove the original small screen
				VideoRendererGui.remove(selectRender.getCallbacks());
				// Settings Big screen
				selectRender.setCallbacks(mLocalCallbacks);
				selectRender.setScrnIndex(0);
			}
		} else
		{
			// swap size screen images
			RtkVideoRender currentRender = mAllRendersMap.get(currentId);
			{	
				// exchangeCallbacks(Equivalent to exchange the View)
				RtkVideoRender.switchCallbacks(selectRender, currentRender);
				currentRender.setScrnIndex(selectRender.getScrnIndex());
				selectRender.setScrnIndex(0);
			}
		}

		mCurrentDisplayPeerId = selectId;
	}

	//When the remote big screen leaving the meeting Delete the remote big screen
	private void removeRemoteVideoRenderer(String peerId)
	{
		{
			//Need to determine whether remove the Render big screen
			RtkVideoRender remoteRender = mAllRendersMap.get(peerId);
			if (null == remoteRender)
			{
				updateSubVideoView();
				return;
			}
			if (remoteRender.getCallbacks() == mLocalCallbacks)
			{

				// takes up big screen, needs to be reassigned to other small screen
				// the default will be the first switch to the big screen
				String switchId = null;
				Iterator<Entry<String, RtkVideoRender>> iter = mAllRendersMap.entrySet().iterator();
				while (iter.hasNext())
				{
					// find in addition to removing PeerId first ID
					Entry<String, RtkVideoRender> entry = iter.next();
					switchId = entry.getKey();
					if (!switchId.equals(peerId))
						break;
				}
				if (switchId != null)
				{
					RtkVideoRender remotesSwitchRender = mAllRendersMap.get(switchId);
					mRemoteLeaveIndex = remotesSwitchRender.getScrnIndex();
					switchVideoRenderer(switchId, null);
				}
			} else
			{
				mRemoteLeaveIndex = remoteRender.getScrnIndex();
				// remove the small screen directly
				VideoRendererGui.remove(remoteRender.getCallbacks());
				remoteRender.close();
			}

			mAllRendersMap.remove(peerId);
			updateSubVideoView();
		}
	}

	/**
	 * sendMessage
	 * 
	 * @param message
	 */
	private void sendMessage(String message)
	{
		if (mJNice != null)
		{
			Iterator<Entry<String, String>> iter = mAllPeerId.entrySet().iterator();
			while (iter.hasNext())
			{
				Entry<String, String> entry = iter.next();
				String peerId = entry.getKey();
				mJNice.SendMessage(mRoomID, peerId, message);
			}
		}
	}

	/**
	 * get the Frame Rate, Actual Bitrate and Remote Candidate Type from report
	 * 
	 * @param reports
	 *            the video report
	 */
	private void updateEncoderStatistics(StatsReport[] reports)
	{
		String fps = null;
		String targetBitrate = null;
		String actualBitrate = null;
		String recvByte = null;
		String sendByte = null;
		String bytesReceived;
		String reveivedHeight;
		Double reveivedTime;
		for (StatsReport report : reports)
		{

			bytesReceived = null;
			reveivedHeight = null;
			if (report.type.equals("ssrc"))
			{
				reveivedTime = report.timestamp;
				Map<String, String> reportMap = new HashMap<String, String>();

				for (StatsReport.Value value : report.values)
				{
					reportMap.put(value.name, value.value);

					if (value.name.equals("googFrameHeightReceived"))
					{
						reveivedHeight = value.value;
						if (bytesReceived != null)
						{
							break;
						}
					}

					if (value.name.equals("bytesReceived"))
					{
						bytesReceived = value.value;

					}
				}

				if (bytesReceived != null && reveivedHeight != null)
				{
					mBsNow = bytesReceived;
					mTsNow = reveivedTime;

					if (mBsBefore == null || mTsBefore == 0.0)
					{
						// Skip this round
						mBsBefore = mBsNow;
						mTsBefore = mTsNow;
					} else
					{
						// Calculate bitrate

						long tempBit = (Integer.parseInt(mBsNow) - Integer.parseInt(mBsBefore));
						if (tempBit < 0)
							continue;
						Double tempTime = (mTsNow - mTsBefore);
						// Log.e(TAG,
						// " tempBit "+tempBit+" tempTime "+tempTime);
						long bitRate = Math.round(tempBit * 8 / tempTime);
						mBitrate = bitRate + "KB/S";

						mBsBefore = mBsNow;
						mTsBefore = mTsNow;
						mUiHandler.sendEmptyMessage(0x01);
					}
				}

			}

		}

	}

	/*
	 * Implements for JniceEvent.
	 */
	@Override
	public void OnJoinOk(String userId)
	{
		// TODO Auto-generated method stub
		mSelfUserId = userId;
		if (mDebug)
			Log.e(TAG, "OnJoinOk userId " + userId);
		//Default on the local image set in the big screen , so mCurrentDisplayPeerId default ID for yourself
		mCurrentDisplayPeerId = userId;
		// Add to RendersMap
		RtkVideoRender localRender = new RtkVideoRender(userId, mLocalCallbacks, mLocalRenderer);
		localRender.setVideoTrack(mRtkClient.getLocalVideoTrack());
		mAllRendersMap.put(userId, localRender);

		mJNice.Publish();

	}

	@Override
	public void OnJoinNeedPwd(String roomId)
	{
		// TODO Auto-generated method stub
		if (mDebug)
			Log.e(TAG, "OnJoinNeedPwd");
	}

	@Override
	public void OnJoinFailed(int status, String errInfo)
	{
		// TODO Auto-generated method stub
		if (mDebug)
			Log.e(TAG, "OnJoinFailed:" + status);

	}

	@Override
	public void OnLeave(String info)
	{
		// TODO Auto-generated method stub
		if (mDebug)
			Log.e(TAG, "OnLeave ");
		finish();
	}

	@Override
	public void OnSysError(String errInfo)
	{
		// TODO Auto-generated method stub
		if (mDebug)
			Log.e(TAG, "OnSysError ");
	}

	@Override
	public void OnPublishAck(String svrId, String jniceId, String channelId)
	{
		if (mDebug)
			Log.e(TAG, "OnPublishAck svrId " + svrId);
		// TODO Auto-generated method stub
		mRtkClient.createPeerConnection(jniceId, 0);
		mRtkClient.sendOffer(jniceId);
	}

	@Override
	public void OnPublishOk(String jniceId, String answer)
	{
		// TODO Auto-generated method stub
		if (mDebug)
			Log.e(TAG, "OnPublishOk jniceId " + jniceId);
		JSONTokener jsonParser = new JSONTokener(answer);
		try
		{
			final JSONObject json = (JSONObject) jsonParser.nextValue();
			final String type = json.has("type") ? json.getString("type") : "";
			final String sdp = json.has("sdp") ? json.getString("sdp") : "";

			if (type != null && type.length() > 0)
			{
				SessionDescription.Type jtype = SessionDescription.Type.fromCanonicalForm(type);
				if (type.equals("answer"))
				{
					mRtkClient.recvAnswer(jniceId, new SessionDescription(jtype, sdp));
				}
			} else
			{
				IceCandidate candidate = new IceCandidate(json.getString("sdpMid"),
						json.getInt("sdpMLineIndex"), json.getString("candidate"));
				mRtkClient.recvCandidate(jniceId, candidate);
			}
		} catch (JSONException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	@Override
	public void OnSubscribeOk(String jniceId, String offer)
	{
		// TODO Auto-generated method stub
		if (mDebug)
			Log.e(TAG, "OnSubscribeOk jniceId " + jniceId);
		mSubJniceId = jniceId;
		mRtkClient.createPeerConnection(jniceId, 1);
		if (offer != null && offer.length() > 0)
		{
			JSONTokener jsonParser = new JSONTokener(offer);
			try
			{
				final JSONObject json = (JSONObject) jsonParser.nextValue();
				final String type = json.has("type") ? json.getString("type") : "";
				final String sdp = json.has("sdp") ? json.getString("sdp") : "";

				if (type != null && type.length() > 0)
				{
					SessionDescription.Type jtype = SessionDescription.Type.fromCanonicalForm(type);
					if (type.equals("offer"))
					{
						mRtkClient.recvOffer(jniceId, new SessionDescription(jtype, sdp));
					}
				} else
				{
					IceCandidate candidate = new IceCandidate(json.getString("sdpMid"),
							json.getInt("sdpMLineIndex"), json.getString("candidate"));
					mRtkClient.recvCandidate(jniceId, candidate);
				}
			} catch (JSONException e)
			{
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		} else
		{
			mRtkClient.sendOffer(jniceId);
		}
	}

	@Override
	public void OnSubscirbeAnswer(String jniceId, String answer)
	{
		if (mDebug)
			Log.e(TAG, "OnSubscirbeAnswer jniceId " + jniceId);
		// TODO Auto-generated method stub
		JSONTokener jsonParser = new JSONTokener(answer);
		try
		{
			final JSONObject json = (JSONObject) jsonParser.nextValue();
			final String type = json.has("type") ? json.getString("type") : "";
			final String sdp = json.has("sdp") ? json.getString("sdp") : "";

			if (type != null && type.length() > 0)
			{
				SessionDescription.Type jtype = SessionDescription.Type.fromCanonicalForm(type);
				if (type.equals("answer"))
				{
					mRtkClient.recvAnswer(jniceId, new SessionDescription(jtype, sdp));
				}
			}
		} catch (JSONException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	@Override
	public void OnPeerTrickle(String jniceId, String trickle)
	{
		// TODO Auto-generated method stub
		JSONTokener jsonParser = new JSONTokener(trickle);
		try
		{
			final JSONObject json = (JSONObject) jsonParser.nextValue();
			if (json.has("sdpMid") && json.has("sdpMLineIndex") && json.has("candidate"))
			{
				IceCandidate candidate = new IceCandidate(json.getString("sdpMid"),
						json.getInt("sdpMLineIndex"), json.getString("candidate"));
				mRtkClient.recvCandidate(jniceId, candidate);
			}
		} catch (JSONException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	/*
	 * Implements for RtkPPClientEvents.
	 */
	@Override
	public void onOutgoingMessage(String peerId, String message)
	{
		// TODO Auto-generated method stubLog.e(TAG,
		// "OnMemberLeave userId "+userId);
		mJNice.SendSdpInfo(peerId, message);
		if (mDebug)
			Log.e(TAG, "onOutgoingMessage peerId " + peerId);
	}

	@Override
	public VideoRenderer onCreatPeerRender(String peerId)
	{
		// TODO Auto-generated method stub

		if (mDebug)
		{
			Log.e(TAG, "onCreatPeerRender");
		}

		// create RemoteRender Can not display
		RtkVideoRender rtkRemoteRender = addRemoteVideoRenderer(peerId);

		return rtkRemoteRender.getVideoRenderer();
	}

	@Override
	public void onCreateVideoTrack(String peerId, VideoTrack videoTrack)
	{
		// TODO Auto-generated method stub
		if (mDebug)
			Log.e(TAG, "onCreateVideoTrack peerId " + peerId);
		RtkVideoRender rtkRemoteRender = mAllRendersMap.get(peerId);
		if (rtkRemoteRender != null)
		{
			rtkRemoteRender.setVideoTrack(videoTrack);
		}

		if (mAllRendersMap.size() == 2 && !peerId.endsWith(mCurrentDisplayPeerId))
		{
			//If is the first to enter the meeting will automatically switch to the big screen
			switchVideoRenderer(peerId, mCurrentDisplayPeerId);
		}
	}

	@Override
	public void onUpdatePeerRender(String peerId)
	{
		// TODO Auto-generated method stub
		if (mDebug)
			Log.e(TAG, "onUpdatePeerRender peerId " + peerId);
	}

	@Override
	public void onRemovePeerRender(String peerId)
	{
		// TODO Auto-generated method stub
		if (mDebug)
			Log.e(TAG, "onRemovePeerRender peerId " + peerId);

		removeRemoteVideoRenderer(peerId);
	}

	@Override
	public void onPeerDataChannelOpened(String peerId)
	{
		// TODO Auto-generated method stub
		if (mDebug)
			Log.e(TAG, "onPeerDataChannelOpened peerId " + peerId);
	}

	@Override
	public void onPeerDataChannelRecvMessage(String peerId, String message)
	{
		// TODO Auto-generated method stub
		if (mDebug)
			Log.e(TAG, "onPeerDataChannelRecvMessage peerId " + peerId);
	}

	@Override
	public void onPeerDataChannelClosed(String peerId)
	{
		// TODO Auto-generated method stub
		if (mDebug)
			Log.e(TAG, "onPeerDataChannelClosed peerId " + peerId);
	}

	@Override
	public void onPeerEncoderStatistics(StatsReport[] reports)
	{
		// TODO Auto-generated method stub
		// Log.e(TAG, "onPeerEncoderStatistics  ");
		updateEncoderStatistics(reports);
	}

	@Override
	public void OnMemberJoin(String userId, String nickName)
	{
		// TODO Auto-generated method stub
		mAllPeerId.put(userId, nickName);
		mPeerCount++;
		if (mDebug)
			Log.e(TAG, "OnMemberJoin userId " + userId + " nickName " + nickName + " mPeerCount "
					+ mPeerCount);

	}

	@Override
	public void OnMemberLeave(String userId)
	{
		// TODO Auto-generated method stub

		mPeerCount--;
		mAllPeerId.remove(userId);
		if (mDebug)
			Log.e(TAG, "OnMemberLeave userId " + userId + " mPeerCount " + mPeerCount);
		if (mPeerCount == 0)
		{
			// DropevaApp.getDropevaApp().showToast(R.string.str_call_end);
			if (mJNice != null)
			{
				mJNice.Leave();
			}
		}

	}

	@Override
	public void OnChannelOpen(String userId, String svrId, String channelId)
	{
		// TODO Auto-generated method stub
		if (mDebug)
			Log.e(TAG, "OnChannelOpen userId " + userId);

		if (!mSelfUserId.equals(userId))
			mJNice.Subscribe(svrId, channelId);

	}

	@Override
	public void OnChannelClose(String userId, String channelId)
	{
		// TODO Auto-generated method stub
		if (mDebug)
			Log.e(TAG, "OnChannelClose userId " + userId);
		if (mSubJniceId != null)
		{
			mJNice.Unsubscribe(mSubJniceId);
			mRtkClient.destroyPeerConnection(mSubJniceId);
			mSubJniceId = null;
		}
	}

	@Override
	public void OnReceiveMessage(String userId, String content)
	{

	}

}

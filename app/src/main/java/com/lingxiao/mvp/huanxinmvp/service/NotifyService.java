package com.lingxiao.mvp.huanxinmvp.service;

import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.BitmapFactory;
import android.media.AudioManager;
import android.media.RingtoneManager;
import android.media.SoundPool;
import android.net.Uri;
import android.os.Binder;
import android.os.IBinder;
import android.os.Vibrator;
import android.support.v4.app.NotificationCompat;

import com.hyphenate.EMError;
import com.hyphenate.EMMessageListener;
import com.hyphenate.chat.EMClient;
import com.hyphenate.chat.EMMessage;
import com.hyphenate.chat.EMTextMessageBody;
import com.lingxiao.mvp.huanxinmvp.MainActivity;
import com.lingxiao.mvp.huanxinmvp.R;
import com.lingxiao.mvp.huanxinmvp.event.ExitEvent;
import com.lingxiao.mvp.huanxinmvp.global.ContentValue;
import com.lingxiao.mvp.huanxinmvp.model.ContactsModel;
import com.lingxiao.mvp.huanxinmvp.model.ContactsModel_Table;
import com.lingxiao.mvp.huanxinmvp.receiver.CallReceiver;
import com.lingxiao.mvp.huanxinmvp.receiver.NotificationBroadcastReceiver;
import com.lingxiao.mvp.huanxinmvp.utils.LogUtils;
import com.lingxiao.mvp.huanxinmvp.utils.SpUtils;
import com.lingxiao.mvp.huanxinmvp.utils.UIUtils;
import com.lingxiao.mvp.huanxinmvp.view.ChatActivity;
import com.raizlabs.android.dbflow.sql.language.SQLite;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.List;

public class NotifyService extends Service {
    private IBinder mBinder = new NotifyBinder();
    private Context mContext;
    private SoundPool soundPool;
    private int foregroundSound;
    private int backgroundSound;
    private int callAcceptSound; //来电播放语音
    private CallReceiver mCallReceiver;
    public NotifyService() {
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mContext = this;
        initSoundPool();
        initGetMsgListener();
        regCallReceiver();
        EventBus.getDefault().register(this);
        LogUtils.i("NotifyService启动");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }


    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }


    public class NotifyBinder extends Binder{
        public NotifyService getService(){
            return NotifyService.this;
        }
    }
    private void sendNotify(EMMessage msg){
        ContactsModel model = SQLite
                .select()
                .from(ContactsModel.class)
                .where(ContactsModel_Table.contactUserName.eq(msg.getUserName()))
                .querySingle();

  /*      Notification.Builder builder = new Notification.Builder(getApplicationContext());
        //设置小图标
        builder.setSmallIcon(R.mipmap.message);
        //设置标题
        builder.setContentTitle("您有一条新的消息");
        EMTextMessageBody body = (EMTextMessageBody) msg.getBody();
        //内容加进来
        builder.setContentText(body.getMessage());
        //设置大图标
        builder.setLargeIcon(BitmapFactory.decodeResource(getResources(),R.mipmap.ic_launcher));
        if (model.exists()){
            builder.setContentInfo("来自"+model.getContactUserName());
        }else {
            builder.setContentInfo("来自"+msg.getUserName());
        }



        //创建要打开的activity意图
        Intent main = new Intent(getApplicationContext(), MainActivity.class);
        Intent chat = new Intent(getApplicationContext(), ChatActivity.class);

        chat.putExtra("name",msg.getUserName());
        Intent[] intents = new Intent[]{main,chat};
        //延期意图  处理通知的点击事件
        PendingIntent pendingIntent = PendingIntent.getActivities(
                getApplicationContext(),
                1,intents,
                PendingIntent.FLAG_UPDATE_CURRENT);
        //给通知设置点击事件
        builder.setContentIntent(pendingIntent);
        //点击之后消失
        builder.setAutoCancel(true);
        builder.setDeleteIntent(pendingIntent);
        //创建notifiction
        Notification notification = builder.build();
        //发送通知
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        manager.notify(1,notification);
        //启动为前台服务
        startForeground(1,notification);*/

        Intent intentClick = new Intent(this, NotificationBroadcastReceiver.class);
        intentClick.setAction("notification_clicked");
        intentClick.putExtra(NotificationBroadcastReceiver.TYPE, 1);
        intentClick.putExtra("username",msg.getUserName());
        PendingIntent pendingIntentClick = PendingIntent.getBroadcast(this, 0, intentClick, PendingIntent.FLAG_ONE_SHOT);

        Intent intentCancel = new Intent(this, NotificationBroadcastReceiver.class);
        intentCancel.setAction("notification_cancelled");
        intentCancel.putExtra(NotificationBroadcastReceiver.TYPE, 1);
        PendingIntent pendingIntentCancel = PendingIntent.getBroadcast(this, 0, intentCancel, PendingIntent.FLAG_ONE_SHOT);

        EMTextMessageBody body = (EMTextMessageBody) msg.getBody();
        Uri defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this)
                .setSmallIcon(R.mipmap.message)
                .setLargeIcon(BitmapFactory.decodeResource(getResources(),R.mipmap.ic_launcher))
                .setContentTitle("您有一条新的消息")
                .setContentText(body.getMessage())
                .setSound(defaultSoundUri)
                .setContentIntent(pendingIntentClick)
                .setDeleteIntent(pendingIntentCancel);
        //这就是那个type，相同的update，不同add
        //发送通知
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        manager.notify(1, notificationBuilder.build());

    }

    /**
     * 初始化消息监听
     */
    private void initGetMsgListener() {
        EMClient.getInstance().chatManager().addMessageListener(new EMMessageListener() {
            @Override
            public void onMessageReceived(List<EMMessage> list) {
                //收到消息
                EventBus.getDefault().post(list);
                boolean messageRemind = SpUtils.getBoolean(mContext,ContentValue.MESSAGE_REMIND,true);
                boolean messageVoice = SpUtils.getBoolean(mContext,ContentValue.MESSAGE_VOICE,true);
                boolean messageShake = SpUtils.getBoolean(mContext,ContentValue.MESSAGE_SNAKE,true);
                if (messageRemind){
                    //语音提示
                    if (messageVoice){
                        if (isInBackground()){
                            soundPool.play(backgroundSound,1,1,0,0,1);
                            sendNotify(list.get(0));
                        }else {
                            /**
                             * Play a sound from a sound ID.
                             *
                             * @param soundID     通过load方法返回的音频
                             * @param leftVolume  左声道的音量
                             * @param rightVolume 右声道的音量
                             * @param priority    优先级，值越大，优先级越高
                             * @param loop        循环的次数：0为不循环，-1为循环
                             * @param rate        指定速率，正常位1，为地位0.5，最高位2
                             */
                            soundPool.play(foregroundSound,1,1,0,0,1);
                        }
                    }
                    //震动
                    if (messageShake){
                        Vibrator vibrator = (Vibrator)mContext
                                .getSystemService(mContext.VIBRATOR_SERVICE);
                        vibrator.vibrate(1000); //震动时间
                    }
                }

            }

            @Override
            public void onCmdMessageReceived(List<EMMessage> list) {

            }

            @Override
            public void onMessageRead(List<EMMessage> list) {

            }

            @Override
            public void onMessageDelivered(List<EMMessage> list) {

            }

            @Override
            public void onMessageChanged(EMMessage emMessage, Object o) {

            }
        });
    }

    /**
     *判断当前应用是否处于后台
     */
    private boolean isInBackground(){
        ActivityManager manager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        List<ActivityManager.RunningTaskInfo> runTacks = manager.getRunningTasks(50);
        ActivityManager.RunningTaskInfo info = runTacks.get(0);
        //获取栈顶的activity 的包名
        ComponentName componentName = info.topActivity;
        //判断包名是否相等
        if (componentName.getPackageName().equals(getPackageName())){
            return false;
        }
        return true;
    }

    private void initSoundPool() {
        //soundpool 构造 第一个参数 这个池子中管理几个音频
        //第二个参数 音频的类型 一般传入AudioManager.STREAM_MUSIC
        //第三个参数 声音的采样频率 但是 没有用默认值使用0
        soundPool = new SoundPool(2, AudioManager.STREAM_MUSIC,0);
        foregroundSound = soundPool.load(getApplicationContext(), R.raw.duan, 1);
        backgroundSound = soundPool.load(getApplicationContext(), R.raw.yulu,1);

    }

    /**
     * 注册电话监听
     */
    private void regCallReceiver() {
        IntentFilter callFilter = new
                IntentFilter(EMClient.getInstance()
                .callManager()
                .getIncomingCallBroadcastAction());
        mCallReceiver = new CallReceiver();
        registerReceiver(mCallReceiver, callFilter);
    }
    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mCallReceiver != null){
            unregisterReceiver(mCallReceiver);
        }
        //如果service被销毁了，将status重置为-1
        UIUtils.setAppStatus(-1);
        EventBus.getDefault().unregister(this);
        LogUtils.i("销毁了service");
    }

    /**
     * 监听用户注销事件 如果注销，则销毁自己
     * @param event
     */
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onGetExitEvent(ExitEvent event){
        if (event.exitType == EMError.USER_ALREADY_EXIST){
            stopSelf();
            LogUtils.i("收到消息需要销毁了service");
        }
    }
}

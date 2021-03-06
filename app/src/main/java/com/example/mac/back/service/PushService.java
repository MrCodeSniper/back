package com.example.mac.back.service;

import android.app.IntentService;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.Nullable;
import android.util.Log;

import com.example.mac.back.data.ClientThread;
import com.example.mac.back.utils.NotifyUtils;
import com.example.mac.back.utils.RemoveHZ;
import com.orhanobut.logger.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Arrays;

/**
 * Created by mac on 2018/3/21.
 */

public class PushService extends IntentService {

//    private Socket clientSocket = null;
//    StringBuffer s=new StringBuffer();

    /**心跳频率*/
    private static final long HEART_BEAT_RATE = 10 * 1000;//3s
    /**服务器ip地址*/
    public static final String HOST = "192.168.1.101";// "192.168.1.21";//
    /**服务器端口号*/
    public static final int PORT = 30001;
    /**服务器消息回复广播*/
    public static final String MESSAGE_ACTION="message_ACTION";
    /**服务器心跳回复广播*/
    public static final String HEART_BEAT_ACTION="heart_beat_ACTION";

    private long sendTime = 0L;

    private Socket so;

    private WeakReference<Socket> mSocket;

    Handler handler= new Handler() {
        public void handleMessage(android.os.Message msg) {
            if (msg.what == 0x123) {
                //s.append("\n" + msg.obj.toString());
                Logger.e(msg.obj.toString());
                NotifyUtils notifyUtils=new NotifyUtils(getApplicationContext(),msg.obj.toString());
                notifyUtils.show();
             //   s=new StringBuffer();
            }else if(msg.what == 0x666){
                Logger.e("收到心跳表示socket还在不需要初始化");
            }
        };
    };

    /**读线程*/
    private ClientThread mReadThread;

    /**心跳任务，不断重复调用自己*/
    private Runnable heartBeatRunnable = new Runnable() {
        @Override
        public void run() {
            if (System.currentTimeMillis() - sendTime >= HEART_BEAT_RATE) {
                sendMsg("HeartBeat");//就发送一个\r\n过去 如果发送失败，就重新初始化一个socket
            }
            handler.postDelayed(this, HEART_BEAT_RATE);
        }
    };




    public PushService() {
        super("push thread");
    }



    @Override
    protected void onHandleIntent( Intent intent) {
            new InitSocketThread().start();//初始化socket线程
    }

    public interface UpdateUI{
        void updateUI();
}




    ////////////////////////////////////////////////////////////////////////////////////////////////////

    class InitSocketThread extends Thread {
        @Override
        public void run() {
            super.run();
            initSocket();
        }
    }


    private void initSocket() {//初始化Socket
        try {
            so = new Socket(HOST, PORT);
            mSocket = new WeakReference<Socket>(so);
            mReadThread = new ClientThread(so);
            mReadThread.start();
            handler.postDelayed(heartBeatRunnable, HEART_BEAT_RATE);//初始化成功后，就准备发送心跳包
        } catch (UnknownHostException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    ////////////////////////////////////////////////////////////////////////////////////////////////////

    class ClientThread extends Thread{//

        //该线程所处理的Socket对应的输入流
        BufferedReader br = null;

        private WeakReference<Socket> mWeakSocket;
        private boolean isStart = true;

        private Socket socket;

        public ClientThread(Socket s) throws IOException {
//            mWeakSocket = new WeakReference<Socket>(s);
         //   br = new BufferedReader(new InputStreamReader(s.getInputStream()));
            this.socket=s;
        }

        @Override
        public void run() {
//            Socket socket = mWeakSocket.get();
            if (null != socket) {
                try {
                    String content = null;
                    br = new BufferedReader(new InputStreamReader(socket.getInputStream()));
//                    InputStream is = socket.getInputStream();
//                    byte[] buffer = new byte[1024 * 4];
//                    int length = 0;
//                    while ((content = br.readLine()) != null) {
//                        //每当读到来自服务器的数据后，发送消息通知程序界面显示数据
//                        Message msg = new Message();
//                        msg.what = 0x123;
//                        msg.obj = content;
//                        handler.sendMessage(msg);
//                    }
                    while (!socket.isClosed() && !socket.isInputShutdown()
                            && isStart && (content = br.readLine()) != null) {
                            Message msg = new Message();
                            msg.obj =content;
                            //收到服务器过来的消息，就通过Broadcast发送出去
                            if(content.equals("HeartBeat——返回自服务器")){//处理心跳回复
                                msg.what = 0x666;
                                handler.sendMessage(msg);
                            }else{
//                                //其他消息回复
//                                Intent intent=new Intent(MESSAGE_ACTION);
//                                intent.putExtra("message", message);
//                                mLocalBroadcastManager.sendBroadcast(intent);
                                msg.what = 0x123;
                                handler.sendMessage(msg);
                            }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }

            }
        }

        public void release() {
            isStart = false;
            releaseLastSocket(socket);
        }

    }


    /**
     * 心跳机制判断出socket已经断开后，就销毁连接方便重新创建连接
     * @param mSocket
     */
    private void releaseLastSocket(Socket mSocket) {
        try {
            if (null != mSocket) {
//                Socket sk = mSocket.get();
                if (!mSocket.isClosed()) {
                    mSocket.close();
                }
//                ms = null;
                mSocket = null;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    @Override
    public void onDestroy() {
        super.onDestroy();
        Logger.e("服务销毁了");
//        handler.removeCallbacks(heartBeatRunnable);
//        mReadThread.release();
//        releaseLastSocket(mSocket);
    }

    public void sendMsg(final String msg) {//向服务端写入HeartBeat,若服务器没有回复就是断开不成功

        if (null == so ) {
            return;
        }
        if (!so.isClosed() && !so.isOutputShutdown()) {
            new AsyncTask<Void, Void, Boolean>() {
                @Override
                protected Boolean doInBackground(Void... voids) {
                    try {
                        OutputStream os = so.getOutputStream();
                        String message = msg + "\r\n";
                        os.write(message.getBytes());
                        os.flush();
                    } catch (IOException e) {
                        e.printStackTrace();
                        return false;
                    }
                    return true;
                }

                @Override
                protected void onPostExecute(Boolean aBoolean) {
                    super.onPostExecute(aBoolean);
                    sendTime = System.currentTimeMillis();//每次发送成数据，就改一下最后成功发送的时间，节省心跳间隔时间
                    if (!aBoolean) {
                        Logger.e("初始化socket");
                        handler.removeCallbacks(heartBeatRunnable);
                        mReadThread.release();
                        releaseLastSocket(so);
                        new InitSocketThread().start();
                    }
                }
            }.execute();
        } else {
            return;
        }
    }


}

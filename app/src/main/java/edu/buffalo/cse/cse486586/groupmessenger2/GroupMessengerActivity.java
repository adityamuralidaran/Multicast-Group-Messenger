package edu.buffalo.cse.cse486586.groupmessenger2;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.telephony.TelephonyManager;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.TreeMap;

public class GroupMessengerActivity extends Activity {

    static final String TAG = GroupMessengerActivity.class.getSimpleName();
    static final String[] Ports_Array = {"11108","11112","11116","11120","11124"};
    static final List<String> REMOTE_PORTS = new ArrayList<String>(Arrays.asList(Ports_Array));
    public static int Total_Ports = 5;
    public static String Failed_AVD = new String("NONE");
    static final int SERVER_PORT = 10000;
    public static int seqNumber = 0;
    public static int GlobalSeqNumber = 0;
    static final String  KEY = "key";
    static final String  VALUE = "value";
    public static String my_port = new String ();

    // Priority Queue for proposed message
    public static PriorityQueue<JSONObject> Proposed_Queue = new PriorityQueue<JSONObject>(100, new Comparator<JSONObject>() {
        @Override
        public int compare(JSONObject obj1, JSONObject obj2) {
            try {
                if (obj1.getInt("agree") < obj2.getInt("agree")) return -1;
                if (obj1.getInt("agree") > obj2.getInt("agree")) return 1;

                return (obj1.getInt("avd") - obj2.getInt("avd"));
            }
            catch(JSONException e){
                Log.e(TAG, "Proposed Queue Comparator Exception");
                return 0;
            }
        }
    });

    // Hold back queue to hold message and ensure FIFO delivery.
    public static PriorityQueue<JSONObject> Holdback_Queue = new PriorityQueue<JSONObject>(100, new Comparator<JSONObject>() {
        @Override
        public int compare(JSONObject obj1, JSONObject obj2) {
            try {
                if (obj1.getInt("agree") < obj2.getInt("agree")) return -1;
                if (obj1.getInt("agree") > obj2.getInt("agree")) return 1;

                return (obj1.getInt("avd") - obj2.getInt("avd"));
            }
            catch(JSONException e){
                Log.e(TAG, "Proposed Queue Comparator Exception");
                return 0;
            }
        }
    });

    private static Uri buildUri(String scheme, String authority) {
        Uri.Builder uriBuilder = new Uri.Builder();
        uriBuilder.authority(authority);
        uriBuilder.scheme(scheme);
        return uriBuilder.build();
    }
    static final Uri mUri = buildUri("content", "edu.buffalo.cse.cse486586.groupmessenger2.provider");


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_messenger);

        TelephonyManager tel = (TelephonyManager) this.getSystemService(Context.TELEPHONY_SERVICE);
        String portStr = tel.getLine1Number().substring(tel.getLine1Number().length() - 4);
        final String myPort = String.valueOf((Integer.parseInt(portStr) * 2));
        my_port = myPort;
        try {
            /*
             * Create a server socket as well as a thread (AsyncTask) that listens on the server
             * port.
             *
             * AsyncTask is a simplified thread construct that Android provides. Please make sure
             * you know how it works by reading
             * http://developer.android.com/reference/android/os/AsyncTask.html
             */
            ServerSocket serverSocket = new ServerSocket(SERVER_PORT);
            new ServerTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, serverSocket);
        } catch (IOException e) {
            /*
             * Log is a good way to debug your code. LogCat prints out all the messages that
             * Log class writes.
             *
             * Please read http://developer.android.com/tools/debugging/debugging-projects.html
             * and http://developer.android.com/tools/debugging/debugging-log.html
             * for more information on debugging.
             */
            Log.e(TAG, "Can't create a ServerSocket");
            return;
        }


        TextView tv = (TextView) findViewById(R.id.textView1);
        tv.setMovementMethod(new ScrollingMovementMethod());
        

        findViewById(R.id.button1).setOnClickListener(
                new OnPTestClickListener(tv, getContentResolver()));
        

        final EditText editText = (EditText) findViewById(R.id.editText1);
        findViewById(R.id.button4).setOnClickListener(new View.OnClickListener()        {
            @Override
            public void onClick (View v){
                String msg = editText.getText().toString() + "\n";
                editText.getText().clear(); // Changed the way to reset the input box.
                new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, msg, myPort);
                return;
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.activity_group_messenger, menu);
        return true;
    }

    // Server Class
    private class ServerTask extends AsyncTask<ServerSocket, String, Void> {

        @Override
        protected Void doInBackground(ServerSocket... sockets) {

            try {
                ServerSocket serverSocket = sockets[0];

                while (true) {
                    Socket newSocket = serverSocket.accept();
                    DataInputStream inputStream = new DataInputStream(newSocket.getInputStream());
                    String strReceived = inputStream.readUTF().trim();
                    JSONObject obj = new JSONObject(strReceived);
                    String type = (String) obj.get("type");
                    String avd = (String) obj.get("avd");
                    String pro = (String) obj.get("pro");
                    // Message from same AVD.
                    if(type.equals("A")||type.equals("F")) {
                        publishProgress(strReceived);
                    }
                    //type - 'M', Initial message that is sent to AVDs to get proposals.
                    if (avd.equals(my_port) && type.equals("M")) {
                        //Log.e(TAG, "M - msg from same AVD" + obj.toString());
                        Iterator<JSONObject> it = Proposed_Queue.iterator();
                        while(it.hasNext()) {
                            JSONObject currObj = it.next();
                            String currPro = (String) currObj.get("pro");
                            String currAvd = (String) currObj.get("avd");
                            if(currAvd.equals(avd) && currPro.equals(pro)){
                                //Proposed_Queue.remove(currObj);
                                currObj.put("type", "P");
                                //Proposed_Queue.add(currObj);
                                //Log.e(TAG, "M - msg from same AVD pushed to queue" + currObj.toString());
                                DataOutputStream outputStream = new DataOutputStream(newSocket.getOutputStream());
                                outputStream.writeUTF(currObj.toString());
                                outputStream.flush();
                                break;
                            }
                        }
                    }
                    //type - 'M', Initial message that is sent to AVDs to get proposals.
                    if (!avd.equals(my_port) && type.equals("M")) {
                        //Log.e(TAG, "M - msg received initially" + obj.toString());
                        int getSeq = Integer.parseInt((String) obj.get("agree"));
                        if (getSeq > seqNumber)
                            seqNumber = getSeq;
                        obj.put("type", "P");
                        obj.put("agree", Integer.toString(seqNumber));
                        seqNumber++;
                        Proposed_Queue.add(obj);
                        //Log.e(TAG, "M - msg that is to be proposed" + obj.toString());
                        DataOutputStream outputStream = new DataOutputStream(newSocket.getOutputStream());
                        outputStream.writeUTF(obj.toString());
                        outputStream.flush();
                    }
                    inputStream.close();
                    newSocket.close();
                }
                //serverSocket.close();
            }
            catch (IOException e) {
                Log.e(TAG, "Server Socket IOException");
                e.printStackTrace();
            }
            catch (JSONException e){
                Log.e(TAG, "Server Task JSON Exception");
            }

            return null;
        }

        protected void onProgressUpdate(String...strings) {
            try {
            /*
             * The following code displays what is received in doInBackground().
             */
                String strReceived = strings[0].trim();
                JSONObject obj = new JSONObject(strReceived);
                String type = (String) obj.get("type");
                String avd = (String) obj.get("avd");
                String pro = (String) obj.get("pro");

                // type - 'A', stands for agreed message sequence that can be deliver to all the AVDs.
                if(type.equals("A")){
                    //Log.e(TAG, "A - msg recived for agreement" + obj.toString());
                    // Iterator to iterate Proposed_Queue
                    Iterator<JSONObject> it = Proposed_Queue.iterator();
                    while(it.hasNext()) {
                        JSONObject currObj = it.next();
                        String currPro = (String) currObj.get("pro");
                        String currAvd = (String) currObj.get("avd");
                        if(currAvd.equals(avd) && currPro.equals(pro)){
                            Proposed_Queue.remove(currObj);
                            Holdback_Queue.add(obj);
                            break;
                        }
                    }
                    // Iterator to iterate Holdback_Queue
                    Iterator<JSONObject> it_hold = Holdback_Queue.iterator();
                    while(it_hold.hasNext()){
                        JSONObject proposedHead = Proposed_Queue.peek();
                        JSONObject holdbackHead = Holdback_Queue.peek();

                        //Log.e(TAG, "A - holdback head" + holdbackHead.toString());

                        int holdbackAgree = Integer.parseInt((String) holdbackHead.get("agree"));
                        int proposeAgree = -1;
                        int holdback_avd = Integer.parseInt((String)holdbackHead.get("avd"));
                        int proposed_avd = -1;
                        if(proposedHead != null) {
                            //Log.e(TAG, "A - propose head" + proposedHead.toString());
                            proposeAgree = Integer.parseInt((String) proposedHead.get("agree"));
                            proposed_avd = Integer.parseInt((String)proposedHead.get("avd"));
                        }
                        //else{
                        //    Log.e(TAG, "A - propose head is null");
                        //}
                        // condition to deliver message in total order and to resolve conflict in it.
                        if(holdbackAgree < proposeAgree || proposedHead == null || (holdbackAgree == proposeAgree && holdback_avd < proposed_avd)){
                            holdbackHead = Holdback_Queue.poll();
                            String msg = (String)holdbackHead.get("msg");
                            String seq = Integer.toString(GlobalSeqNumber);
                            GlobalSeqNumber++;
                            TextView tv = (TextView) findViewById(R.id.textView1);
                            tv.append("\n\t\t" + msg); // This is one way to display a string.
                            ContentValues vals = new ContentValues();
                            vals.put(KEY,seq);
                            vals.put(VALUE,msg);
                            //Log.e(TAG, "A - seq and message inserted" + seq + msg);
                            getContentResolver().insert(mUri,vals);
                        }

                        else {
                            break;
                        }
                    }
                }

                //type - 'F', To remove the failure port number.
                if (type.equals("F")) {
                    //Log.e(TAG, "F - Failure message received " + obj.toString());
                    String failAvd = (String) obj.get("failavd");
                    if(!Failed_AVD.equals(failAvd)){
                        Failed_AVD = failAvd;
                        Total_Ports--;
                    }
                    Iterator<JSONObject> it = Proposed_Queue.iterator();
                    while (it.hasNext()) {
                        JSONObject currObj = it.next();
                        String currAvd = (String) currObj.get("avd");
                        if (currAvd.equals(failAvd)) {
                            Proposed_Queue.remove(currObj);
                            continue;
                        }
                    }
                }
            /*
             * The following code creates a file in the AVD's internal storage and stores a file.
             *
             * For more information on file I/O on Android, please take a look at
             * http://developer.android.com/training/basics/data-storage/files.html

            */
                String filename = "SimpleMessengerOutput";
                String string = strReceived + "\n";
                FileOutputStream outputStream;

                try {
                    outputStream = openFileOutput(filename, Context.MODE_PRIVATE);
                    outputStream.write(string.getBytes());
                    outputStream.close();
                } catch (Exception e) {
                    Log.e(TAG, "File write failed");
                }

            }
            catch (JSONException e){
                Log.e(TAG, "failed in onProgressUpdate - JSON Exception");
            }
            catch(Exception e){
                Log.e(TAG, "failed in onProgressUpdate ");
                e.printStackTrace();
            }

            return;
        }

    }

    // Client Class
    private class ClientTask extends AsyncTask<String, Void, Void> {

        @Override
        protected Void doInBackground(String... msgs) {
            try {
                int replyCount = 0;
                int finalSeq = -1;
                // Message construct. 'avd' and 'pro' together will be an unique identifier for every message.
                JSONObject mainObj = new JSONObject().put("type","M") // 'type' - type of message
                        .put("avd",msgs[1]) // 'avd' - port of message initiating AVD
                        .put("msg",msgs[0]) // 'msg' - message content
                        .put("pro",Integer.toString(seqNumber)) // 'pro' - proposed sequence number by message initiator.
                                                                     // (will never be changed)
                        .put("agree",Integer.toString(seqNumber));// 'agree' - sequence number that each AVD agree on.
                                                                        // (finally will contain the largest of all proposed sequence number)

                Proposed_Queue.add(new JSONObject(mainObj.toString()));
                seqNumber++;
                for(String port:REMOTE_PORTS) {
                    if(!port.equals(Failed_AVD)) {
                        try {
                            Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                                    Integer.parseInt(port));
                            socket.setSoTimeout(1000);
                            String strToSend = mainObj.toString();
                            DataOutputStream outputStream = new DataOutputStream(socket.getOutputStream());
                            outputStream.writeUTF(strToSend);
                            outputStream.flush();

                            DataInputStream inputStream = new DataInputStream(socket.getInputStream());
                            String strReceived = inputStream.readUTF().trim();
                            inputStream.close();
                            replyCount++;
                            JSONObject obj = new JSONObject(strReceived);
                            String type = (String) obj.get("type");
                            String avd = (String) obj.get("avd");
                            String pro = (String) obj.get("pro");
                            //type - 'P', stands for Messages of type proposal to decide on the sequence number.
                            if (type.equals("P")) {

                                //Log.e(TAG, "P - msg that is proposed" + obj.toString());
                                Iterator<JSONObject> it = Proposed_Queue.iterator();
                                while (it.hasNext()) {
                                    JSONObject currObj = it.next();
                                    String currPro = (String) currObj.get("pro");
                                    String currAvd = (String) currObj.get("avd");
                                    if (currAvd.equals(avd) && currPro.equals(pro)) {
                                        finalSeq = Math.max(Integer.parseInt((String) obj.get("agree")), Integer.parseInt((String) currObj.get("agree")));
                                        Proposed_Queue.remove(currObj);
                                        currObj.put("agree", Integer.toString(finalSeq));
                                        Proposed_Queue.add(currObj);
                                        //Log.e(TAG, "P - msg updated after proposal" + currObj.toString());
                                        break;
                                    }
                                }
                            }
                            //Log.e(TAG, "Client side - message sent to : " + port);
                        } catch (IOException e) {
                            Failed_AVD = port;
                            Total_Ports--;
                            JSONObject failObj = new JSONObject().put("type", "F") // 'type' - type of message
                                    .put("avd", msgs[1]) // 'avd' - port of message initiating AVD
                                    .put("pro", Integer.toString(-1)) // 'pro' - proposed sequence number by message initiator.
                                    .put("failavd", Failed_AVD);
                            new SendMessage().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, failObj.toString(), "F");
                            Log.e(TAG, "Socket time out exception - Failure handling " + Failed_AVD);
                            //continue;
                        }
                    }
                }

                if (replyCount >= Total_Ports) {
                    if (finalSeq + 1 > seqNumber)
                        seqNumber = finalSeq + 1;
                    mainObj.put("type", "A");
                    mainObj.put("agree", Integer.toString(finalSeq));
                    //Log.e(TAG, "P - msg sent as agreement" + mainObj.toString());
                    new SendMessage().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, mainObj.toString(), "P");
                }

                //socket.close();
            }
            catch (JSONException e){
                Log.e(TAG, "ClientTask JSON Exception");
            }

            return null;
        }
    }

    private class SendMessage extends AsyncTask<String, Void, Void> {

        @Override
        protected Void doInBackground(String... msgs) {
            try {
                if(msgs[1].equals("P")){
                    for(String port:REMOTE_PORTS) {
                        try {
                            Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                                    Integer.parseInt(port));
                            String msgToSend = msgs[0];
                            socket.setSoTimeout(1000);
                            DataOutputStream outputStream = new DataOutputStream(socket.getOutputStream());
                            outputStream.writeUTF(msgToSend);
                            outputStream.flush();
                        }
                        catch (IOException e){
                            Failed_AVD = port;
                            Total_Ports--;
                            JSONObject failObj = new JSONObject().put("type", "F") // 'type' - type of message
                                    .put("avd", msgs[1]) // 'avd' - port of message initiating AVD
                                    .put("pro", Integer.toString(-1)) // 'pro' - proposed sequence number by message initiator.
                                    .put("failavd", Failed_AVD);
                            new SendMessage().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, failObj.toString(), "F");
                            Log.e(TAG, "Socket time out exception - Failure handling - Send message 'P'" + Failed_AVD);
                            continue;
                        }
                    }
                }
                if(msgs[1].equals("F")){
                    for (String port : REMOTE_PORTS) {
                        try {
                            Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                                    Integer.parseInt(port));
                            String msgToSend = msgs[0];
                            socket.setSoTimeout(1000);
                            DataOutputStream outputStream = new DataOutputStream(socket.getOutputStream());
                            outputStream.writeUTF(msgToSend);
                            outputStream.flush();
                        }
                        catch (IOException e){
                            Failed_AVD = port;
                            Total_Ports--;
                            JSONObject failObj = new JSONObject().put("type", "F") // 'type' - type of message
                                    .put("avd", msgs[1]) // 'avd' - port of message initiating AVD
                                    .put("pro", Integer.toString(-1)) // 'pro' - proposed sequence number by message initiator.
                                    .put("failavd", Failed_AVD);
                            new SendMessage().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, failObj.toString(), "F");
                            Log.e(TAG, "'F' - Socket time out exception - Failure handling in send message class");
                            continue;
                        }
                    }
                }
                //socket.close();
            }
            catch (JSONException e){
                Log.e(TAG, "SendMessage JSON Exception");
            }
            return null;
        }
    }
}

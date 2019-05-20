package dev.decapitated.android_client;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.SystemClock;
import android.support.v4.app.NotificationCompat;
import android.util.Base64;
import android.view.MotionEvent;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import io.socket.client.IO;
import io.socket.client.Socket;

public class PortalService extends Service {

    private MediaProjectionManager mediaManager;
    private MediaProjection mediaProjection;
    private ImageReader imageReader;
    private VirtualDisplay virtualDisplay;
    private Point displaySize;
    private int dpi;

    private Socket socket;
    private boolean sharing = false;
    boolean started = false;

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private ScheduledFuture<?> cappedHandle;

    @Override
    public void onCreate(){
        Toast.makeText(this, "Service", Toast.LENGTH_SHORT).show();
        createNotificationChannel();
        Notification builder = new Notification.Builder(this, "chMe")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("Portal")
                .setContentText("Portal is running.")
                .build();
        builder.flags |= Notification.FLAG_FOREGROUND_SERVICE;

        startForeground(1995, builder);
        //Get permission for media projection
        mediaManager = (MediaProjectionManager)getSystemService(android.content.Context.MEDIA_PROJECTION_SERVICE);
    }

    private void createNotificationChannel() {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "chName";
            String description = "chDescription";
            int importance = NotificationManager.IMPORTANCE_HIGH;
            NotificationChannel channel = new NotificationChannel("chMe", name, importance);
            channel.setDescription(description);
            // Register the channel with the system; you can't change the importance
            // or other notification behaviors after this
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Bundle extras = intent.getExtras();
        Intent data = (Intent) extras.get("data");
        displaySize = (Point) extras.get("size");
        dpi = extras.getInt("dpi");
        imageReader = ImageReader.newInstance(displaySize.x, displaySize.y, PixelFormat.RGBA_8888, 2);
        mediaProjection = mediaManager.getMediaProjection(-1, data);
        virtualDisplay = mediaProjection.createVirtualDisplay("service", displaySize.x,
                displaySize.y, dpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, imageReader.getSurface(),
                null, null);
        serverThread.start();
        return START_STICKY;
    }


    Thread sendThread = new Thread(() -> {
        cappedHandle = scheduler.scheduleAtFixedRate(() -> {
            if(sharing){
                Image image = imageReader.acquireLatestImage();

                if (image != null) {
                    final Image.Plane[] planes = image.getPlanes();
                    int pixelStride = planes[0].getPixelStride();
                    int rowStride = planes[0].getRowStride();
                    int rowPadding = rowStride - pixelStride * image.getWidth();

                    ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                    Bitmap bitmap = Bitmap.createBitmap((image.getWidth() + rowPadding / pixelStride), image.getHeight(), Bitmap.Config.ARGB_8888);
                    bitmap.copyPixelsFromBuffer(buffer);

                    byte[] byteArray;
                    if (bitmap != null) {
                        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 50, byteArrayOutputStream);
                        byteArray = byteArrayOutputStream.toByteArray();
                        String encoded = Base64.encodeToString(byteArray, Base64.DEFAULT);
                        socket.emit("frame", encoded);
                    }
                    image.close();
                }
            }
        },0,1000/30, TimeUnit.MILLISECONDS);
    });

    Thread serverThread = new Thread(()->{
        try {
            socket = IO.socket("http://127.0.0.1:5555");

            socket.on(Socket.EVENT_CONNECT, args -> {
                socket.emit("started");
            });
            socket.on(Socket.EVENT_DISCONNECT, args -> {
                sharing = false;
                socket.close();
                stopSelf();
            });
            socket.on("start", args -> {
                if(!started){
                    sendThread.start();
                    started = true;
                }
                sharing = true;
            });

            socket.on("stop", args -> {
                sharing = false;
            });

            /*socket.on("mouse-click", args -> {
                int x = (int)args[0];
                int y = (int)args[1];
                MotionEvent.obtain(SystemClock.uptimeMillis(), SystemClock.uptimeMillis(), MotionEvent., x, y, 0);

            });*/

            socket.connect();
        } catch (URISyntaxException e) {}
    });

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}

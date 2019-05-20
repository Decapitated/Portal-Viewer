package dev.decapitated.android_client;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Point;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.DisplayMetrics;
import android.view.Display;

public class MainActivity extends AppCompatActivity {

    private MediaProjectionManager mediaManager;
    private Display display;
    private Point displaySize = new Point();
    private DisplayMetrics displayMetrics = new DisplayMetrics();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        display = getWindowManager().getDefaultDisplay();
        display.getRealSize(displaySize);
        display.getRealMetrics(displayMetrics);
        //Get permission for media projection
        mediaManager = (MediaProjectionManager)getSystemService(android.content.Context.MEDIA_PROJECTION_SERVICE);
        Intent recordPerm = mediaManager.createScreenCaptureIntent();
        startActivityForResult(recordPerm, 1000);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == RESULT_OK) {
            //mediaProjection = mediaManager.getMediaProjection(resultCode, data);
            /*virtualDisplay = mediaProjection.createVirtualDisplay("screen", displayMetrics.widthPixels,
                    displayMetrics.heightPixels, displayMetrics.densityDpi,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, imageReader.getSurface(),
                    null, null);*/
            startPortal(data);
        }else{
            System.exit(-1);
        }
    }
    private void startPortal(Intent data){
        Intent temp = new Intent(this, PortalService.class);
        temp.putExtra("data", data);
        temp.putExtra("size", displaySize);
        temp.putExtra("dpi", displayMetrics.densityDpi);
        startService(temp);
    }
}
